package com.tozes.hermescompanion.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A [GadgetLink] that acts out a [MockScenario] with realistic delays: two
 * gadgets nearby, the phone's passkey pairing, and console replies from
 * [MockDevice]. Its timers run in [scope], which should run on the main thread.
 */
class MockGadgetLink(
    val scenario: MockScenario,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    timeSource: TimeSource = TimeSource.Monotonic,
) : GadgetLink {
    override var onEvent: ((LinkEvent) -> Unit)? = null
    override var radio: RadioState = RadioState.UNKNOWN
        private set

    private val devices = mutableMapOf(NOX_ID to MockDevice.nox(scenario), KITCHEN_ID to MockDevice.kitchen())
    private val bonded = mutableSetOf<String>()
    private var connected: String? = null
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var dropJob: Job? = null

    /** A connect is under way: connecting or waiting for the passkey. */
    private var isConnecting = false
    private var commandCount = 0
    private val started = timeSource.markNow()

    init {
        // A real radio starts unknown and reports its state a moment later.
        scope.launch {
            delay(400.milliseconds)
            setRadio(scenario.radio)
        }
    }

    // Debug actions

    fun setRadio(state: RadioState) {
        radio = state
        if (state != RadioState.POWERED_ON) {
            scanJob?.cancel()
            if (connected != null || isConnecting) drop()
        }
        onEvent?.invoke(LinkEvent.Radio(state))
    }

    /** Hermes approves Nox, as `hermes pairing approve` would. */
    fun approveOnHermes() {
        devices[NOX_ID]?.approve()
    }

    /** The link drops, as if the gadget went out of range. */
    fun drop() {
        connectJob?.cancel()
        dropJob?.cancel()
        val id = connected
        if (id == null) {
            if (isConnecting) {
                isConnecting = false
                onEvent?.invoke(LinkEvent.Failed(LinkFailure.CONNECTION_LOST))
            }
            return
        }
        devices[id]?.phoneConnected = false
        connected = null
        onEvent?.invoke(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST))
    }

    // GadgetLink

    override fun startScan() {
        if (radio != RadioState.POWERED_ON) return
        scanJob?.cancel()
        if (scenario == MockScenario.NOTHING_NEARBY) return
        scanJob = scope.launch {
            val seen = mutableListOf<Pair<String, Int>>()
            for ((id, rssi, wait) in listOf(Triple(NOX_ID, -52, 900.milliseconds), Triple(KITCHEN_ID, -74, 1300.milliseconds))) {
                delay(wait)
                seen += id to rssi
                advertise(id, rssi)
            }
            // Signal strength wanders while the scan runs.
            while (isActive) {
                delay(1500.milliseconds)
                for ((id, rssi) in seen) advertise(id, rssi + random.nextInt(-4, 5))
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun connect(gadget: DiscoveredGadget) {
        connectJob?.cancel()
        isConnecting = true
        connectJob = scope.launch {
            onEvent?.invoke(LinkEvent.Connecting)
            delay(900.milliseconds)
            if (radio != RadioState.POWERED_ON || devices[gadget.id] == null) {
                finishConnecting(LinkEvent.Failed(LinkFailure.UNREACHABLE))
                return@launch
            }
            if (gadget.id !in bonded) {
                onEvent?.invoke(LinkEvent.AwaitingPasskey)
                when (scenario) {
                    MockScenario.PAIRING_REJECTED -> {
                        delay(4.seconds)
                        finishConnecting(LinkEvent.Failed(LinkFailure.PAIRING_REJECTED))
                        return@launch
                    }
                    MockScenario.PAIRING_TIMES_OUT -> {
                        delay(8.seconds)
                        finishConnecting(LinkEvent.Failed(LinkFailure.PAIRING_TIMED_OUT))
                        return@launch
                    }
                    else -> {
                        // Long enough to read the instructions and type six digits.
                        delay(4.seconds)
                        bonded += gadget.id
                    }
                }
            }
            connected = gadget.id
            devices[gadget.id]?.phoneConnected = true
            finishConnecting(LinkEvent.Connected)
            if (scenario == MockScenario.FLAKY) scheduleDrop(60.seconds)
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        isConnecting = false
        dropJob?.cancel()
        connected?.let { devices[it]?.phoneConnected = false }
        connected = null
    }

    override suspend fun send(line: String): String {
        val id = connected ?: throw SendException.NotConnected()
        commandCount += 1
        if (scenario == MockScenario.FLAKY && commandCount % 4 == 0) {
            delay(3.seconds)
            return "@error device busy"
        }
        delay(random.nextInt(120, 421).milliseconds)
        val device = devices[id]
        if (connected != id || device == null) throw SendException.NotConnected()
        val reply = device.handle(line, now())
        if (line.trimSpaces() == "phone forget") {
            // The gadget drops every bond, then the connected phone.
            bonded -= id
            scheduleDrop(300.milliseconds)
        }
        return reply
    }

    // Private

    private fun now(): Duration = started.elapsedNow()

    private fun finishConnecting(event: LinkEvent) {
        isConnecting = false
        onEvent?.invoke(event)
    }

    private fun advertise(id: String, rssi: Int) {
        val device = devices[id] ?: return
        onEvent?.invoke(LinkEvent.Discovered(DiscoveredGadget(id, device.settings["name"] ?: "Hermes", rssi)))
    }

    private fun scheduleDrop(after: Duration) {
        dropJob?.cancel()
        dropJob = scope.launch {
            delay(after)
            drop()
        }
    }

    companion object {
        const val NOX_ID = "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E01"
        const val KITCHEN_ID = "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E02"
    }
}
