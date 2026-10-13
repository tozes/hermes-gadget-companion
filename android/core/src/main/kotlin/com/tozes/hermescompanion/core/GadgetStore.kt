package com.tozes.hermescompanion.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Everything the screens show, as one immutable snapshot of [GadgetStore]. */
data class GadgetState(
    val radio: RadioState,
    val isScanning: Boolean = false,
    /** A scan ran for the store's scan duration and stopped on its own. */
    val scanEnded: Boolean = false,
    val found: List<DiscoveredGadget> = emptyList(),
    val stage: Stage = Stage.FINDING,
    val pairing: PairingProgress = PairingProgress.Connecting,
    val gadget: DiscoveredGadget? = null,
    val status: GadgetStatus? = null,
    /** When the last status arrived, in milliseconds since the epoch. */
    val statusTime: Long? = null,
    val statusError: String? = null,
    val isRefreshing: Boolean = false,
    val isLoadingSettings: Boolean = false,
    /** The link dropped while connected. */
    val connectionLost: Boolean = false,
    /** The last `get` value of each setting; secrets are `<set>` or empty. */
    val settings: Map<SettingKey, String> = emptyMap(),
    /** The saved Wi-Fi networks from the last `wifi list`. */
    val wifiNetworks: WifiNetworkList? = null,
    /**
     * The firmware has no `wifi` command: it keeps one network, set with
     * `set wifi_ssid` and `set wifi_pass`.
     */
    val keepsOneNetwork: Boolean = false,
    val console: List<ConsoleEntry> = emptyList(),
    /** The name of a gadget that was just forgotten, for a note on the find screen. */
    val forgottenName: String? = null,
    /** The app is showing the demo: a mock gadget, not real Bluetooth. */
    val isDemo: Boolean = false,
) {
    enum class Stage { FINDING, PAIRING, CONNECTED }

    sealed interface PairingProgress {
        data object Connecting : PairingProgress
        data object AwaitingPasskey : PairingProgress
        data class Failed(val failure: LinkFailure) : PairingProgress
    }

    /** Settings that haven't been read, for example after `@error device busy`. */
    val missingSettings: Boolean get() = settings.size < SettingKey.entries.size
}

/**
 * The app's state, observed by every screen. It drives a [GadgetLink],
 * sends console lines one at a time, and keeps what the replies say.
 *
 * Call it from one thread (the main thread in the app); [scope] runs its
 * timers and commands and should use that thread's dispatcher.
 */
class GadgetStore(
    link: GadgetLink,
    private val scope: CoroutineScope,
    /** How long a scan runs before it stops on its own. */
    val scanDuration: Duration = 20.seconds,
    /** The real Bluetooth link, for [endDemo]. */
    private val makeRealLink: () -> GadgetLink = { error("no real link to go back to") },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var link: GadgetLink = link
        private set

    private val mutableState = MutableStateFlow(GadgetState(radio = link.radio, isDemo = isDemo(link)))
    val state: StateFlow<GadgetState> = mutableState.asStateFlow()

    private var wantsScan = false
    private var scanTimer: Job? = null
    private var queueTail: Job? = null
    private var nextEntryId = 0

    /** Bumped whenever the link changes or drops, so stale replies are ignored. */
    private var generation = 0

    init {
        attach()
    }

    private inline fun update(change: GadgetState.() -> GadgetState) = mutableState.update(change)

    private val current get() = mutableState.value

    // Links

    /** Swaps in another link (the debug menu's mock scenarios) and starts over. */
    fun replaceLink(newLink: GadgetLink) {
        link.onEvent = null
        link.stopScan()
        link.disconnect()
        scanTimer?.cancel()
        generation += 1
        link = newLink
        update {
            copy(radio = newLink.radio, isScanning = false, scanEnded = false, found = emptyList(), forgottenName = null, console = emptyList())
        }
        resetConnection()
        update { copy(gadget = null, stage = GadgetState.Stage.FINDING, isDemo = isDemo(newLink)) }
        attach()
        // The find screen may already be showing, so it won't ask again.
        startScan()
    }

    /** Swaps in pretend gadgets to explore the app with. Never saved across launches. */
    fun startDemo() = replaceLink(MockGadgetLink(MockScenario.DEMO, scope))

    /** Leaves the demo for real Bluetooth. */
    fun endDemo() = replaceLink(makeRealLink())

    private fun attach() {
        link.onEvent = { event -> handle(event) }
    }

    // Scanning

    fun startScan() {
        wantsScan = true
        update { copy(scanEnded = false) }
        if (current.radio == RadioState.POWERED_ON) beginScan()
    }

    fun stopScan() {
        wantsScan = false
        endScan()
    }

    private fun beginScan() {
        if (current.isScanning) return
        update { copy(found = emptyList(), isScanning = true) }
        link.startScan()
        scanTimer?.cancel()
        scanTimer = scope.launch {
            delay(scanDuration)
            if (!current.isScanning) return@launch
            scanTimer = null
            wantsScan = false
            update { copy(scanEnded = true) }
            endScan()
        }
    }

    private fun endScan() {
        scanTimer?.cancel()
        scanTimer = null
        if (current.isScanning) link.stopScan()
        update { copy(isScanning = false) }
    }

    // Connecting

    /**
     * Connects from the gadget list. Each new connection starts with an empty
     * console; reconnecting after a drop keeps it.
     */
    fun connect(target: DiscoveredGadget) {
        if (current.stage == GadgetState.Stage.FINDING) update { copy(console = emptyList()) }
        stopScan()
        update { copy(forgottenName = null) }
        resetConnection()
        update { copy(gadget = target, pairing = GadgetState.PairingProgress.Connecting, stage = GadgetState.Stage.PAIRING) }
        link.connect(target)
    }

    fun retryPairing() {
        val gadget = current.gadget ?: return backToFinding()
        connect(gadget)
    }

    /** Reconnects after the link dropped. */
    fun reconnect() = retryPairing()

    /** Leaves the pairing screen or the gadget and scans again. */
    fun backToFinding() {
        link.disconnect()
        resetConnection()
        update { copy(gadget = null, stage = GadgetState.Stage.FINDING) }
        startScan()
    }

    private fun resetConnection() {
        generation += 1
        queueTail = null
        update {
            copy(
                status = null, statusTime = null, statusError = null, isRefreshing = false, connectionLost = false,
                settings = emptyMap(), wifiNetworks = null, keepsOneNetwork = false,
            )
        }
    }

    private fun handle(event: LinkEvent) {
        when (event) {
            is LinkEvent.Radio -> {
                update { copy(radio = event.state) }
                if (event.state == RadioState.POWERED_ON) {
                    if (wantsScan) beginScan()
                } else {
                    endScan()
                    update { copy(found = emptyList()) }
                }
            }
            is LinkEvent.Discovered -> {
                if (!current.isScanning) return
                update {
                    val index = found.indexOfFirst { it.id == event.gadget.id }
                    copy(found = if (index >= 0) found.toMutableList().also { it[index] = event.gadget } else found + event.gadget)
                }
            }
            LinkEvent.Connecting -> update { copy(pairing = GadgetState.PairingProgress.Connecting) }
            LinkEvent.AwaitingPasskey -> update { copy(pairing = GadgetState.PairingProgress.AwaitingPasskey) }
            LinkEvent.Connected -> {
                update { copy(stage = GadgetState.Stage.CONNECTED, connectionLost = false) }
                note("Connected to ${current.gadget?.name ?: "gadget"}")
                scope.launch { refreshStatus() }
            }
            is LinkEvent.Failed -> failed(event.failure)
            is LinkEvent.Disconnected -> event.failure?.let(::failed)
        }
    }

    private fun failed(failure: LinkFailure) {
        when (current.stage) {
            GadgetState.Stage.PAIRING -> update { copy(pairing = GadgetState.PairingProgress.Failed(failure)) }
            GadgetState.Stage.CONNECTED -> linkDropped()
            GadgetState.Stage.FINDING -> Unit
        }
    }

    private fun linkDropped() {
        generation += 1
        queueTail = null
        update { copy(isRefreshing = false, connectionLost = true) }
        note("Connection lost")
    }

    // Commands

    /** Sends one console line after any line already in flight, and returns its reply. */
    suspend fun send(line: String, typed: Boolean = false): Reply {
        val previous = queueTail
        val task = scope.async {
            previous?.join()
            perform(line, typed)
        }
        queueTail = task
        return task.await()
    }

    private suspend fun perform(line: String, typed: Boolean): Reply {
        val started = generation
        val clean = line.replace("\n", " ")
        log(ConsoleEntry.Kind.COMMAND, ConsoleArgument.redacted(clean), typed)
        if (clean.encodeToByteArray().size > MAX_LINE_BYTES) {
            note("Not sent: lines are limited to $MAX_LINE_BYTES bytes", typed)
            return Reply.Error("too long: the gadget takes lines up to $MAX_LINE_BYTES bytes")
        }
        return try {
            val text = link.send(clean)
            if (started != generation) return Reply.Error("not connected")
            log(ConsoleEntry.Kind.REPLY, text, typed)
            Reply.parse(text).also { absorb(it, clean) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SendException.TimedOut) {
            note("No reply", typed)
            Reply.Error("no reply from the gadget")
        } catch (_: Exception) {
            note("Not connected", typed)
            Reply.Error("not connected")
        }
    }

    /** Keeps what a reply says, whether the app or the console sent the line. */
    private fun absorb(reply: Reply, line: String) {
        when (reply) {
            is Reply.Status -> update { copy(status = reply.status, statusTime = clock(), statusError = null) }
            is Reply.Value -> SettingKey.of(reply.value.key)?.let { key ->
                update { copy(settings = settings + (key to reply.value.value)) }
            }
            is Reply.Phone -> update { copy(status = status?.copy(phone = reply.phone)) }
            is Reply.Wifi -> update { copy(wifiNetworks = reply.list) }
            is Reply.Ok -> {
                val words = splitWords(line.trimSpaces(), maxSplits = 2)
                val key = words.getOrNull(1)?.let(SettingKey::of)
                if (words.size >= 2 && words[0] == "set" && key != null) {
                    recordSaved(key, if (words.size > 2) words[2].trimSpaces() else "")
                } else if (words == listOf("phone", "forget")) {
                    forgotten()
                }
            }
            else -> Unit
        }
    }

    private fun recordSaved(key: SettingKey, value: String) {
        val kept = if (key.isSecret && value.isNotEmpty()) SettingValue.MASKED_SECRET else value
        update { copy(settings = settings + (key to kept)) }
        if (key == SettingKey.NAME && value.isNotEmpty()) {
            update { copy(status = status?.copy(name = value), gadget = gadget?.copy(name = value)) }
        }
    }

    /** The gadget dropped every phone: leave it and say so on the find screen. */
    private fun forgotten() {
        val name = current.status?.name ?: current.gadget?.name ?: "The gadget"
        link.disconnect()
        resetConnection()
        update { copy(gadget = null, stage = GadgetState.Stage.FINDING, forgottenName = name) }
    }

    /** Asks for `status`; returns whether a new one arrived. */
    suspend fun refreshStatus(): Boolean {
        if (current.stage != GadgetState.Stage.CONNECTED || current.connectionLost) return false
        update { copy(isRefreshing = true) }
        try {
            val reply = send("status")
            if (reply is Reply.Status) return true
            update { copy(statusError = reply.failureMessage) }
            return false
        } finally {
            update { copy(isRefreshing = false) }
        }
    }

    /** Reads every setting the store doesn't have yet (or all of them, when [all]). */
    suspend fun loadSettings(all: Boolean = false) {
        if (current.isLoadingSettings) return
        update { copy(isLoadingSettings = true) }
        try {
            for (key in SettingKey.entries) {
                if (!all && current.settings[key] != null) continue
                if (current.stage != GadgetState.Stage.CONNECTED || current.connectionLost) return
                send("get ${key.rawValue}")
            }
        } finally {
            update { copy(isLoadingSettings = false) }
        }
    }

    /** Sets a value; returns the gadget's error message, or null when it was saved. */
    suspend fun save(key: SettingKey, value: String): String? {
        val clean = value.replace("\n", " ").trimSpaces()
        val reply = send("set ${key.rawValue} $clean")
        return if (reply is Reply.Ok) null else reply.failureMessage ?: "The gadget didn't confirm."
    }

    // Wi-Fi networks

    /**
     * Reads the saved networks ([GadgetState.wifiNetworks]). Returns an error message, or
     * null when they arrived or the firmware keeps one network ([GadgetState.keepsOneNetwork]).
     */
    suspend fun loadWifiNetworks(): String? {
        val reply = send("wifi list")
        return when {
            reply is Reply.Wifi -> null
            reply is Reply.Error && reply.message.startsWith("unknown command") -> {
                update { copy(keepsOneNetwork = true) }
                null
            }
            else -> reply.failureMessage ?: "The gadget didn't answer."
        }
    }

    /** Saves a network without joining it; an empty password saves an open network. */
    suspend fun addWifi(ssid: String, password: String): String? {
        var line = "wifi add " + ConsoleArgument.quoted(ssid)
        if (password.isNotEmpty()) line += " " + ConsoleArgument.quoted(password)
        return changeWifi(line)
    }

    /** Moves a saved network first; the gadget leaves its network and joins it. */
    suspend fun joinWifi(ssid: String): String? = changeWifi("wifi join " + ConsoleArgument.quoted(ssid))

    suspend fun forgetWifi(ssid: String): String? = changeWifi("wifi forget " + ConsoleArgument.quoted(ssid))

    private suspend fun changeWifi(line: String): String? {
        val reply = send(line)
        if (reply !is Reply.Ok) return reply.failureMessage ?: "The gadget didn't confirm."
        loadWifiNetworks()
        return null
    }

    /**
     * `phone forget`: the gadget removes every paired phone and drops the link.
     * Returns an error message, or null when it's done (the store goes back to finding).
     */
    suspend fun forget(): String? {
        val reply = send("phone forget")
        return if (reply is Reply.Ok) null else reply.failureMessage ?: "The gadget didn't confirm."
    }

    // Console

    fun clearConsole() = update { copy(console = emptyList()) }

    private fun note(text: String, typed: Boolean = true) = log(ConsoleEntry.Kind.NOTE, text, typed)

    private fun log(kind: ConsoleEntry.Kind, text: String, typed: Boolean) {
        nextEntryId += 1
        val entry = ConsoleEntry(nextEntryId, kind, text, typed)
        update { copy(console = (console + entry).takeLast(CONSOLE_LIMIT)) }
    }

    companion object {
        const val MAX_LINE_BYTES = 256
        private const val CONSOLE_LIMIT = 500

        private fun isDemo(link: GadgetLink) = (link as? MockGadgetLink)?.scenario == MockScenario.DEMO
    }
}

/**
 * Splits on single spaces like Swift's `split(separator: " ", maxSplits:)`:
 * empty pieces are skipped, and after [maxSplits] pieces the rest of the line
 * is the last one, leading spaces and all.
 */
internal fun splitWords(line: String, maxSplits: Int): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < line.length) {
        if (line[index] == ' ') {
            val appended = index > start
            if (appended) out += line.substring(start, index)
            start = index + 1
            if (appended && out.size == maxSplits) break
        }
        index += 1
    }
    if (start < line.length) out += line.substring(start)
    return out
}
