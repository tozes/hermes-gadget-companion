package com.tozes.hermescompanion.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Joining Wi-Fi: save the network, then watch `status` until the gadget is
 * on the network and its phase moves on to Hermes.
 *
 * Call it from the main thread, like [GadgetStore].
 */
class WifiJoin(
    val joinTimeout: Duration = 30.seconds,
    val hermesTimeout: Duration = 30.seconds,
    val pollInterval: Duration = 1.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    sealed interface Step {
        data object Editing : Step
        data object Saving : Step
        data object Joining : Step
        data object ReachingHermes : Step

        /** Online; [approved] is false while Hermes still has to approve the gadget. */
        data class Done(val approved: Boolean) : Step

        /** On Wi-Fi, but Hermes never answered. */
        data object NoHermes : Step
        data class Failed(val message: String) : Step

        /** Saved for later with `wifi add`; the gadget joins it when it's in range. */
        data object Saved : Step
    }

    enum class Field { SSID, PASSWORD }

    /** A refused `set` or `wifi add`, shown under its field. */
    data class FieldError(val field: Field, val message: String)

    data class State(val step: Step = Step.Editing, val fieldError: FieldError? = null) {
        val isRunning: Boolean get() = step == Step.Saving || step == Step.Joining || step == Step.ReachingHermes

        fun error(field: Field): String? = fieldError?.takeIf { it.field == field }?.message
    }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    val step: Step get() = mutableState.value.step
    val isRunning: Boolean get() = mutableState.value.isRunning

    fun error(field: Field): String? = mutableState.value.error(field)

    /** Bumped by each run and by [reset], so an abandoned run stops writing. */
    private var runId = 0

    private fun set(step: Step, fieldError: FieldError? = mutableState.value.fieldError) {
        mutableState.value = State(step, fieldError)
    }

    fun reset() {
        runId += 1
        set(Step.Editing, fieldError = null)
    }

    /**
     * Saves the network and follows the join. An empty password keeps the
     * saved one unless [clearPassword] (an open network) is set. Cancelling
     * the calling coroutine, or [reset], abandons the run.
     */
    suspend fun run(store: GadgetStore, ssid: String, password: String, clearPassword: Boolean) {
        val id = begin()
        val ssidError = store.save(SettingKey.WIFI_SSID, ssid)
        if (!isCurrent(id)) return
        if (ssidError != null) return set(Step.Editing, FieldError(Field.SSID, ssidError))
        if (password.isNotEmpty() || clearPassword) {
            val passwordError = store.save(SettingKey.WIFI_PASSWORD, if (clearPassword) "" else password)
            if (!isCurrent(id)) return
            if (passwordError != null) return set(Step.Editing, FieldError(Field.PASSWORD, passwordError))
        }
        set(Step.Joining)
        follow(store, ssid, id) {
            store.refreshStatus() && store.state.value.status?.network == true
        }
    }

    /**
     * Saves a network for later (firmware that keeps several networks). The
     * gadget doesn't try it now, so the password isn't checked yet.
     */
    suspend fun save(store: GadgetStore, ssid: String, password: String) {
        val id = begin()
        val error = store.addWifi(ssid, password)
        if (!isCurrent(id)) return
        if (error != null) return set(Step.Editing, FieldError(field(error), error))
        set(Step.Saved)
    }

    /**
     * Joins a saved network now, after saving it when [newPassword] is set (a
     * new network, or a new password; empty for an open network), on firmware
     * that keeps several networks. Follows the join until the gadget reports
     * that network as its current one, then waits for Hermes as [run] does.
     */
    suspend fun join(store: GadgetStore, ssid: String, newPassword: String?) {
        val id = begin()
        if (newPassword != null) {
            val error = store.addWifi(ssid, newPassword)
            if (!isCurrent(id)) return
            if (error != null) return set(Step.Editing, FieldError(field(error), error))
        }
        val error = store.joinWifi(ssid)
        if (!isCurrent(id)) return
        if (error != null) return set(Step.Failed(error))
        set(Step.Joining)
        val others = (store.state.value.wifiNetworks?.networks?.size ?: 0) > 1
        follow(store, ssid, id, fallsBack = others) {
            store.loadWifiNetworks() == null && store.state.value.wifiNetworks?.current == ssid
        }
    }

    private fun begin(): Int {
        runId += 1
        set(Step.Saving, fieldError = null)
        return runId
    }

    /**
     * From [Step.Joining]: waits until [onNetwork], then for Hermes. [fallsBack]:
     * the gadget has other saved networks to go back to if this one fails.
     */
    private suspend fun follow(store: GadgetStore, ssid: String, id: Int, fallsBack: Boolean = false, onNetwork: suspend () -> Boolean) {
        when (poll(joinTimeout, store, id, onNetwork)) {
            PollOutcome.MET -> Unit
            PollOutcome.CANCELLED -> return
            PollOutcome.LOST -> return set(Step.Failed(LOST_MESSAGE))
            PollOutcome.TIMED_OUT -> {
                val back = if (fallsBack) " It goes back to its other saved networks." else ""
                return set(Step.Failed("$ssid didn't connect. Check the network name and password; the gadget joins 2.4 GHz networks only.$back"))
            }
        }
        set(Step.ReachingHermes)
        val online = poll(hermesTimeout, store, id) {
            store.refreshStatus() && store.state.value.status?.phase == GadgetStatus.Phase.Online
        }
        when (online) {
            PollOutcome.MET -> set(Step.Done(approved = store.state.value.status?.paired != false))
            PollOutcome.CANCELLED -> Unit
            PollOutcome.LOST -> set(Step.Failed(LOST_MESSAGE))
            PollOutcome.TIMED_OUT -> set(Step.NoHermes)
        }
    }

    private suspend fun isCurrent(id: Int): Boolean = id == runId && currentCoroutineContext().isActive

    private enum class PollOutcome { MET, TIMED_OUT, LOST, CANCELLED }

    /**
     * Runs [check] until it holds. [check] asks the gadget again each time,
     * and must count a skipped or failed refresh as not met.
     */
    private suspend fun poll(timeout: Duration, store: GadgetStore, id: Int, check: suspend () -> Boolean): PollOutcome {
        val deadline = timeSource.markNow() + timeout
        while (deadline.hasNotPassedNow()) {
            delay(pollInterval)
            if (!isCurrent(id)) return PollOutcome.CANCELLED
            val state = store.state.value
            if (state.connectionLost || state.stage != GadgetState.Stage.CONNECTED) return PollOutcome.LOST
            val met = check()
            if (!isCurrent(id)) return PollOutcome.CANCELLED
            if (store.state.value.connectionLost) return PollOutcome.LOST
            if (met) return PollOutcome.MET
        }
        return PollOutcome.TIMED_OUT
    }

    companion object {
        private const val LOST_MESSAGE =
            "The Bluetooth connection dropped, so the app can't tell whether the gadget joined. Reconnect from the home screen to check."

        /** Which field a refused `wifi add` belongs under. */
        private fun field(error: String): Field = if (error.lowercase().contains("password")) Field.PASSWORD else Field.SSID
    }
}
