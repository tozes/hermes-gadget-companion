package com.tozes.hermescompanion.core.ble

import com.tozes.hermescompanion.core.LinkEvent
import com.tozes.hermescompanion.core.LinkFailure
import com.tozes.hermescompanion.core.ReplyAssembler
import com.tozes.hermescompanion.core.SendException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One connection to a gadget's console over GATT, without the radio: the
 * connect sequence, pairing, the serial operation queue, replies, and how
 * each way of failing maps to a [LinkFailure]. `BluetoothGadgetLink` in the
 * app feeds it the `BluetoothGatt` callbacks (on the main thread) and does
 * the scanning; this part is tested on the JVM with a fake [GattPort].
 *
 * Connecting goes: connect (retrying Android's status 133 twice), request a
 * larger MTU, discover services, subscribe to replies, then pair if the
 * phone isn't bonded to the gadget, and write a first command (`phone`).
 * That write needs an encrypted link, so its reply is what proves the
 * pairing worked; then the link is connected.
 *
 * Which failure a dropped pairing means is told from timing, as on iOS: the
 * gadget refuses pairing at once when its pairing window is closed, while a
 * wrong code or a cancelled dialog takes longer than [Timing.refusedWithin].
 * A phone that was already bonded and is refused for its keys gets
 * [LinkFailure.PAIRING_REMOVED]: the gadget forgot it.
 *
 * Every call must be made on the thread [scope] runs on.
 */
class GattSession(
    private val port: GattPort,
    private val scope: CoroutineScope,
    private val onEvent: (LinkEvent) -> Unit,
    private val timing: Timing = Timing(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    data class Timing(
        /** Android never gives up on a connect by itself; this covers connecting through subscribing. */
        val connect: Duration = 15.seconds,
        /** The gadget closes pairing two minutes after it opens. */
        val pairing: Duration = 120.seconds,
        /** The gadget answers within 3 s or says `@error device busy`. */
        val reply: Duration = 10.seconds,
        /** One GATT operation other than a write: MTU, discovery, subscribing. */
        val operation: Duration = 10.seconds,
        /** A bonded phone's first reply comes back quickly: only say "enter the code" when it doesn't. */
        val passkeyHint: Duration = 800.milliseconds,
        /**
         * A gadget whose pairing is closed refuses as soon as pairing starts;
         * a wrong code takes longer than this to type.
         */
        val refusedWithin: Duration = 3.seconds,
        /** Status 133 on connect is often transient: try again this many times, after [retryDelay]. */
        val connectRetries: Int = 2,
        val retryDelay: Duration = 600.milliseconds,
    )

    enum class Phase { IDLE, CONNECTING, DISCOVERING, AWAITING_PASSKEY, CONNECTED }

    /** The MTU asked for: 244 bytes of reply per notification. */
    val requestedMtu = 247

    var phase = Phase.IDLE
        private set

    /** The phone was bonded to the gadget when this connection started. */
    private var bondedAtStart = false
    private var address: String? = null
    private var probeStarted: TimeMark? = null
    private val queue = GattOperationQueue()
    private val assembler = ReplyAssembler()
    private var pendingReply: CompletableDeferred<String>? = null
    private var bondWaiter: CompletableDeferred<BondState>? = null
    private var connectJob: Job? = null
    private var phaseTimeout: Job? = null
    private var passkeyHint: Job? = null

    /** A write was refused for want of pairing: the dialog was cancelled or the code was wrong. */
    private class NotPaired : Exception()

    /** The gadget no longer has this phone's keys. */
    private class PairingRemoved : Exception()

    /** Ends the connect sequence with [failure]. */
    private class Failure(val failure: LinkFailure) : Exception()

    // Driving the session

    fun connect(address: String) {
        teardown()
        this.address = address
        bondedAtStart = port.isBonded(address)
        phase = Phase.CONNECTING
        onEvent(LinkEvent.Connecting)
        startPhaseTimeout(timing.connect, LinkFailure.UNREACHABLE)
        connectJob = scope.launch { runConnect(address) }
    }

    /** The app asked to disconnect: no event. */
    fun disconnect() {
        teardown()
    }

    /** The radio went off or away: the stack drops connections without a callback. */
    fun radioLost() {
        if (phase != Phase.IDLE) lost(status = null)
    }

    suspend fun send(line: String): String {
        if (phase != Phase.CONNECTED) throw SendException.NotConnected()
        return try {
            write(line, timing.reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SendException) {
            throw e
        } catch (_: Exception) {
            throw SendException.NotConnected()
        }
    }

    // BluetoothGatt callbacks, on the main thread

    fun onConnectionStateChange(status: Int, connected: Boolean) {
        if (phase == Phase.CONNECTING && queue.current == GattOperationQueue.Operation.CONNECT) {
            // The connect loop decides whether to retry.
            val result = if (connected && status == GattStatus.SUCCESS) GattStatus.SUCCESS else if (status == GattStatus.SUCCESS) -1 else status
            queue.complete(GattOperationQueue.Operation.CONNECT, result)
            return
        }
        if (!connected) lost(status)
    }

    fun onMtuChanged(status: Int) {
        queue.complete(GattOperationQueue.Operation.MTU, status)
    }

    fun onServicesDiscovered(status: Int) {
        queue.complete(GattOperationQueue.Operation.DISCOVER, status)
    }

    fun onDescriptorWrite(status: Int) {
        queue.complete(GattOperationQueue.Operation.SUBSCRIBE, status)
    }

    fun onCharacteristicWrite(status: Int) {
        queue.complete(GattOperationQueue.Operation.WRITE, status)
    }

    fun onNotification(bytes: ByteArray) {
        for (reply in assembler.append(bytes)) {
            // A reply nobody waits for (after a timeout) is dropped.
            pendingReply?.complete(reply)
        }
    }

    fun onBondStateChanged(state: BondState) {
        if (state != BondState.BONDING) bondWaiter?.complete(state)
    }

    // Connecting

    private suspend fun runConnect(address: String) {
        try {
            connectWithRetries(address)
            phase = Phase.DISCOVERING
            try {
                queue.perform(GattOperationQueue.Operation.MTU, timing.operation) { port.requestMtu(requestedMtu) }
            } catch (_: GattOperationQueue.OperationFailed) {
                // A larger MTU only saves notifications; the default works too.
                currentCoroutineContext().ensureActive()
            }
            val discovered = queue.perform(GattOperationQueue.Operation.DISCOVER, timing.operation) { port.discoverServices() }
            if (discovered != GattStatus.SUCCESS || !port.hasGadgetService()) throw Failure(LinkFailure.UNREACHABLE)
            val subscribed = queue.perform(GattOperationQueue.Operation.SUBSCRIBE, timing.operation) { port.enableReplyNotifications() }
            if (subscribed != GattStatus.SUCCESS) throw Failure(LinkFailure.UNREACHABLE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            return fail(e.failure)
        } catch (_: GattOperationQueue.OperationFailed) {
            return fail(LinkFailure.UNREACHABLE)
        }
        probe(address)
    }

    private suspend fun connectWithRetries(address: String) {
        var attempt = 0
        while (true) {
            val status = queue.perform(GattOperationQueue.Operation.CONNECT, timing.connect) { port.connect(address) }
            if (status == GattStatus.SUCCESS) return
            port.close()
            if (status != GattStatus.ERROR || attempt >= timing.connectRetries) throw Failure(LinkFailure.UNREACHABLE)
            attempt += 1
            delay(timing.retryDelay)
        }
    }

    /** Pairs if needed, then writes the first command, which only a paired link can. */
    private suspend fun probe(address: String) {
        phase = Phase.AWAITING_PASSKEY
        probeStarted = timeSource.markNow()
        startPhaseTimeout(timing.pairing, LinkFailure.PAIRING_TIMED_OUT)
        try {
            if (!port.isBonded(address)) {
                // Android shows its pairing dialog; the code is on the gadget's screen.
                onEvent(LinkEvent.AwaitingPasskey)
                val waiter = CompletableDeferred<BondState>()
                bondWaiter = waiter
                if (!port.createBond(address)) return fail(LinkFailure.PAIRING_REJECTED)
                if (waiter.await() != BondState.BONDED) return fail(refusalFailure())
            } else {
                passkeyHint = scope.launch {
                    delay(timing.passkeyHint)
                    if (phase == Phase.AWAITING_PASSKEY) onEvent(LinkEvent.AwaitingPasskey)
                }
            }
            write("phone", timing.pairing)
            if (phase != Phase.AWAITING_PASSKEY) return
            phase = Phase.CONNECTED
            phaseTimeout?.cancel()
            passkeyHint?.cancel()
            onEvent(LinkEvent.Connected)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PairingRemoved) {
            fail(LinkFailure.PAIRING_REMOVED)
        } catch (_: NotPaired) {
            fail(LinkFailure.PAIRING_REJECTED)
        } catch (_: Exception) {
            // A timeout or a dropped link: teardown has already reported a drop.
            if (phase == Phase.AWAITING_PASSKEY) fail(LinkFailure.PAIRING_TIMED_OUT)
        } finally {
            bondWaiter = null
        }
    }

    /** Pairing ended without a bond: refused at once (window closed), or after a wrong code. */
    private fun refusalFailure(): LinkFailure {
        val elapsed = probeStarted?.elapsedNow() ?: Duration.ZERO
        return if (elapsed < timing.refusedWithin) LinkFailure.PAIRING_TIMED_OUT else LinkFailure.PAIRING_REJECTED
    }

    // Writing

    private suspend fun write(line: String, timeout: Duration): String {
        if (pendingReply != null) throw SendException.NotConnected()
        assembler.reset()
        val reply = CompletableDeferred<String>()
        pendingReply = reply
        try {
            return withTimeoutOrNull(timeout) {
                val status = queue.perform(GattOperationQueue.Operation.WRITE, timeout) { port.writeCommand(line.encodeToByteArray()) }
                if (status != GattStatus.SUCCESS) throw writeFailure(status)
                // The reply follows as notifications.
                reply.await()
            } ?: throw SendException.TimedOut()
        } catch (e: GattOperationQueue.OperationFailed) {
            throw if (e.timedOut) SendException.TimedOut() else SendException.NotConnected()
        } finally {
            if (pendingReply === reply) pendingReply = null
        }
    }

    private fun writeFailure(status: Int): Exception = when {
        status !in GattStatus.writeNeedsPairing -> SendException.NotConnected()
        // Bonded before this connection, yet refused: the gadget dropped our keys.
        bondedAtStart -> PairingRemoved()
        else -> NotPaired()
    }

    // Ending

    private fun startPhaseTimeout(duration: Duration, failure: LinkFailure) {
        phaseTimeout?.cancel()
        phaseTimeout = scope.launch {
            delay(duration)
            fail(failure)
        }
    }

    /** Ends a connect that didn't work and reports why. */
    private fun fail(failure: LinkFailure) {
        if (phase == Phase.IDLE) return
        teardown()
        onEvent(LinkEvent.Failed(failure))
    }

    /** The link went away without the app asking; [status] is the disconnect reason, if any. */
    private fun lost(status: Int?) {
        val was = phase
        val elapsed = probeStarted?.elapsedNow() ?: Duration.ZERO
        teardown()
        when (was) {
            Phase.IDLE -> Unit
            Phase.CONNECTED -> onEvent(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST))
            Phase.CONNECTING, Phase.DISCOVERING -> onEvent(LinkEvent.Failed(LinkFailure.UNREACHABLE))
            Phase.AWAITING_PASSKEY -> {
                val failure = when {
                    bondedAtStart && status in GattStatus.disconnectForKeys -> LinkFailure.PAIRING_REMOVED
                    // The gadget drops the link both when its pairing window is closed
                    // (at once) and after a wrong code (once it's typed).
                    elapsed < timing.refusedWithin -> LinkFailure.PAIRING_TIMED_OUT
                    else -> LinkFailure.PAIRING_REJECTED
                }
                onEvent(LinkEvent.Failed(failure))
            }
        }
    }

    private fun teardown() {
        val jobs = listOfNotNull(phaseTimeout, passkeyHint, connectJob)
        phaseTimeout = null
        passkeyHint = null
        connectJob = null
        phase = Phase.IDLE
        probeStarted = null
        pendingReply?.completeExceptionally(SendException.NotConnected())
        pendingReply = null
        bondWaiter?.cancel()
        bondWaiter = null
        queue.failAll("disconnected")
        assembler.reset()
        // Always close a GATT client that was opened: Android has only a few.
        if (address != null) port.close()
        address = null
        // Last: one of these may be the coroutine calling teardown.
        jobs.forEach { it.cancel() }
    }
}
