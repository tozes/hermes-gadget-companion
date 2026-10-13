package com.tozes.hermescompanion.core.ble

import com.tozes.hermescompanion.core.LinkEvent
import com.tozes.hermescompanion.core.LinkFailure
import com.tozes.hermescompanion.core.SendException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Bluetooth link's connect sequence, pairing, replies and failure mapping,
 * against a fake GATT on virtual time. What only a phone and a gadget can
 * show (Android's real callbacks and statuses) is listed in the README.
 */
class GattSessionTest {
    private val port = FakeGattPort()
    private val events = mutableListOf<LinkEvent>()

    private fun TestScope.session() = GattSession(port, backgroundScope, { events += it }, timeSource = testScheduler.timeSource)

    private val failures get() = events.filterIsInstance<LinkEvent.Failed>().map { it.failure }

    // Answering the operation in flight, as Android's callbacks would

    private fun TestScope.answerConnect(session: GattSession, status: Int = GattStatus.SUCCESS, connected: Boolean = status == GattStatus.SUCCESS) {
        port.answered()
        session.onConnectionStateChange(status, connected)
        runCurrent()
    }

    private fun TestScope.answer(session: GattSession, callback: GattSession.(Int) -> Unit, status: Int = GattStatus.SUCCESS) {
        port.answered()
        session.callback(status)
        runCurrent()
    }

    private fun TestScope.reply(session: GattSession, text: String) {
        session.onNotification(text.encodeToByteArray() + 0)
        runCurrent()
    }

    /** Connects up to the first write (`phone`), which is then in flight. */
    private fun TestScope.connectToProbe(session: GattSession) {
        session.connect(ADDRESS)
        runCurrent()
        assertEquals("connect", port.inFlight)
        answerConnect(session)
        assertEquals("mtu 247", port.inFlight)
        answer(session, GattSession::onMtuChanged)
        assertEquals("discover", port.inFlight)
        answer(session, GattSession::onServicesDiscovered)
        assertEquals("subscribe", port.inFlight)
        answer(session, GattSession::onDescriptorWrite)
    }

    private fun TestScope.connected(): GattSession {
        val session = session()
        connectToProbe(session)
        answer(session, GattSession::onCharacteristicWrite)
        reply(session, PHONE)
        assertEquals(GattSession.Phase.CONNECTED, session.phase)
        events.clear()
        return session
    }

    // Connecting

    @Test
    fun connectsSubscribesAndProbesWhenBonded() = runTest {
        val session = session()
        connectToProbe(session)
        assertEquals("write phone", port.inFlight)
        assertEquals(listOf("connect", "mtu 247", "discover", "subscribe", "write phone"), port.calls)
        answer(session, GattSession::onCharacteristicWrite)
        assertEquals(GattSession.Phase.AWAITING_PASSKEY, session.phase)
        reply(session, PHONE)
        assertEquals(listOf(LinkEvent.Connecting, LinkEvent.Connected), events)
        assertEquals(GattSession.Phase.CONNECTED, session.phase)
    }

    @Test
    fun status133IsRetriedTwiceThenUnreachable() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        repeat(2) {
            answerConnect(session, GattStatus.ERROR)
            assertEquals(null, port.inFlight)
            advanceTimeBy(601.milliseconds)
            assertEquals("connect", port.inFlight)
        }
        answerConnect(session, GattStatus.ERROR)
        assertEquals(3, port.connects)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
        // Each failed attempt's client is closed.
        assertTrue(port.closes >= 3)
    }

    @Test
    fun status133ThenAConnection() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session, GattStatus.ERROR)
        advanceTimeBy(601.milliseconds)
        answerConnect(session)
        assertEquals("mtu 247", port.inFlight)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun otherConnectErrorsAreNotRetried() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session, 0x3e)
        assertEquals(1, port.connects)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
    }

    @Test
    fun connectGivesUpAfter15Seconds() = runTest {
        val session = session()
        session.connect(ADDRESS)
        advanceTimeBy(14.9.seconds)
        assertTrue(failures.isEmpty())
        advanceTimeBy(0.2.seconds)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
        assertEquals("close", port.calls.last())
        assertEquals(GattSession.Phase.IDLE, session.phase)
    }

    @Test
    fun discoveryTimeIsPartOfTheConnectTimeout() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session)
        answer(session, GattSession::onMtuChanged)
        // Discovery never answers.
        advanceTimeBy(16.seconds)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
    }

    @Test
    fun aGattWithoutTheServiceIsUnreachable() = runTest {
        port.hasService = false
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session)
        answer(session, GattSession::onMtuChanged)
        answer(session, GattSession::onServicesDiscovered)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
        assertFalse("subscribe" in port.calls)
    }

    @Test
    fun aRefusedSubscriptionIsUnreachable() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session)
        answer(session, GattSession::onMtuChanged)
        answer(session, GattSession::onServicesDiscovered)
        answer(session, GattSession::onDescriptorWrite, status = 0x03)
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
    }

    @Test
    fun anMtuRequestThatDoesNotStartIsSkipped() = runTest {
        port.startsMtu = false
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session)
        assertEquals("discover", port.inFlight)
    }

    @Test
    fun aDropWhileDiscoveringIsUnreachable() = runTest {
        val session = session()
        session.connect(ADDRESS)
        runCurrent()
        answerConnect(session)
        answer(session, GattSession::onMtuChanged)
        session.onConnectionStateChange(0x08, connected = false)
        runCurrent()
        assertEquals(listOf(LinkFailure.UNREACHABLE), failures)
    }

    // Pairing

    @Test
    fun anUnbondedPhonePairsBeforeTheFirstWrite() = runTest {
        port.bonded = false
        val session = session()
        connectToProbe(session)
        assertEquals("bond", port.calls.last())
        assertEquals(LinkEvent.AwaitingPasskey, events.last())
        assertTrue(port.writes.isEmpty())
        session.onBondStateChanged(BondState.BONDING)
        advanceTimeBy(20.seconds)
        port.bonded = true
        session.onBondStateChanged(BondState.BONDED)
        runCurrent()
        assertEquals(listOf("phone"), port.writes)
        answer(session, GattSession::onCharacteristicWrite)
        reply(session, PHONE)
        assertEquals(LinkEvent.Connected, events.last())
    }

    @Test
    fun pairingRefusedAtOnceMeansTheWindowClosed() = runTest {
        port.bonded = false
        val session = session()
        connectToProbe(session)
        session.onBondStateChanged(BondState.BONDING)
        advanceTimeBy(1.seconds)
        session.onBondStateChanged(BondState.NONE)
        runCurrent()
        assertEquals(listOf(LinkFailure.PAIRING_TIMED_OUT), failures)
        assertEquals("close", port.calls.last())
    }

    @Test
    fun pairingThatFailsAfterACodeIsRejected() = runTest {
        port.bonded = false
        val session = session()
        connectToProbe(session)
        session.onBondStateChanged(BondState.BONDING)
        advanceTimeBy(12.seconds)
        session.onBondStateChanged(BondState.NONE)
        runCurrent()
        assertEquals(listOf(LinkFailure.PAIRING_REJECTED), failures)
    }

    @Test
    fun pairingTimesOutAfterTwoMinutes() = runTest {
        port.bonded = false
        val session = session()
        connectToProbe(session)
        session.onBondStateChanged(BondState.BONDING)
        advanceTimeBy(119.seconds)
        assertTrue(failures.isEmpty())
        advanceTimeBy(2.seconds)
        assertEquals(listOf(LinkFailure.PAIRING_TIMED_OUT), failures)
    }

    @Test
    fun aBondThatCannotStartIsRejected() = runTest {
        port.bonded = false
        port.startsBond = false
        val session = session()
        connectToProbe(session)
        assertEquals(listOf(LinkFailure.PAIRING_REJECTED), failures)
    }

    @Test
    fun aBondedPhoneRefusedForItsKeysMustPairAgain() = runTest {
        val session = session()
        connectToProbe(session)
        answer(session, GattSession::onCharacteristicWrite, status = GattStatus.INSUFFICIENT_ENCRYPTION)
        assertEquals(listOf(LinkFailure.PAIRING_REMOVED), failures)
    }

    @Test
    fun aBondedPhoneDroppedForMissingKeysMustPairAgain() = runTest {
        val session = session()
        connectToProbe(session)
        session.onConnectionStateChange(GattStatus.PIN_OR_KEY_MISSING, connected = false)
        runCurrent()
        assertEquals(listOf(LinkFailure.PAIRING_REMOVED), failures)
    }

    @Test
    fun aNewBondRefusedOnWriteIsRejected() = runTest {
        port.bonded = false
        val session = session()
        connectToProbe(session)
        port.bonded = true
        session.onBondStateChanged(BondState.BONDED)
        runCurrent()
        answer(session, GattSession::onCharacteristicWrite, status = GattStatus.AUTH_FAIL)
        assertEquals(listOf(LinkFailure.PAIRING_REJECTED), failures)
    }

    @Test
    fun aDropWhilePairingIsToldByItsTiming() = runTest {
        port.bonded = false
        val quick = session()
        connectToProbe(quick)
        advanceTimeBy(500.milliseconds)
        quick.onConnectionStateChange(0x13, connected = false)
        runCurrent()
        assertEquals(listOf(LinkFailure.PAIRING_TIMED_OUT), failures)

        events.clear()
        val slow = session()
        connectToProbe(slow)
        advanceTimeBy(30.seconds)
        slow.onConnectionStateChange(0x13, connected = false)
        runCurrent()
        assertEquals(listOf(LinkFailure.PAIRING_REJECTED), failures)
    }

    @Test
    fun aBondedPhoneSaysEnterTheCodeOnlyWhenTheReplyIsSlow() = runTest {
        val quick = session()
        connectToProbe(quick)
        answer(quick, GattSession::onCharacteristicWrite)
        advanceTimeBy(300.milliseconds)
        reply(quick, PHONE)
        assertFalse(LinkEvent.AwaitingPasskey in events)

        events.clear()
        val slow = session()
        connectToProbe(slow)
        advanceTimeBy(900.milliseconds)
        assertEquals(LinkEvent.AwaitingPasskey, events.last())
    }

    // Commands

    @Test
    fun sendJoinsTheReplyNotifications() = runTest {
        val session = connected()
        val reply = async { session.send("status") }
        runCurrent()
        assertEquals("write status", port.inFlight)
        answer(session, GattSession::onCharacteristicWrite)
        session.onNotification("@status {\"name\":".encodeToByteArray())
        session.onNotification("\"Nox\"}".encodeToByteArray() + 0)
        assertEquals("""@status {"name":"Nox"}""", reply.await())
    }

    @Test
    fun aReplyBeforeTheWriteCallbackStillArrives() = runTest {
        val session = connected()
        val reply = async { session.send("phone") }
        runCurrent()
        session.onNotification(PHONE.encodeToByteArray() + 0)
        answer(session, GattSession::onCharacteristicWrite)
        assertEquals(PHONE, reply.await())
    }

    @Test
    fun noReplyIn10SecondsTimesOutAndALateOneIsDropped() = runTest {
        val session = connected()
        val reply = async { runCatching { session.send("status") } }
        runCurrent()
        answer(session, GattSession::onCharacteristicWrite)
        advanceTimeBy(10.1.seconds)
        assertTrue(reply.await().exceptionOrNull() is SendException.TimedOut)
        reply(session, "@status {}")
        // The next command gets its own reply.
        val next = async { session.send("get volume") }
        runCurrent()
        answer(session, GattSession::onCharacteristicWrite)
        reply(session, "@ok volume")
        assertEquals("@ok volume", next.await())
    }

    @Test
    fun sendBeforeConnectingFails() = runTest {
        val session = session()
        assertFailsWith<SendException.NotConnected> { session.send("status") }
    }

    @Test
    fun aRefusedWriteWhileConnectedIsNotConnected() = runTest {
        val session = connected()
        val reply = async { runCatching { session.send("status") } }
        runCurrent()
        answer(session, GattSession::onCharacteristicWrite, status = 0x03)
        assertTrue(reply.await().exceptionOrNull() is SendException.NotConnected)
    }

    // Ending

    @Test
    fun aDropWhileConnectedIsConnectionLost() = runTest {
        val session = connected()
        val reply = async { runCatching { session.send("status") } }
        runCurrent()
        session.onConnectionStateChange(0x08, connected = false)
        runCurrent()
        assertEquals(listOf<LinkEvent>(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST)), events)
        assertTrue(reply.await().exceptionOrNull() is SendException.NotConnected)
        assertEquals("close", port.calls.last())
        assertEquals(GattSession.Phase.IDLE, session.phase)
    }

    @Test
    fun theRadioGoingOffIsADrop() = runTest {
        val session = connected()
        session.radioLost()
        assertEquals(listOf<LinkEvent>(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST)), events)
    }

    @Test
    fun disconnectingReportsNothingAndClosesTheGatt() = runTest {
        val session = connected()
        session.disconnect()
        runCurrent()
        assertTrue(events.isEmpty())
        assertEquals("close", port.calls.last())
        // A late callback from the closed client changes nothing.
        session.onConnectionStateChange(0x16, connected = false)
        assertTrue(events.isEmpty())
    }

    @Test
    fun connectingAgainClosesTheOldClientFirst() = runTest {
        val session = connected()
        session.connect(ADDRESS)
        assertEquals(listOf("close"), port.calls.takeLast(1))
        runCurrent()
        assertEquals("connect", port.inFlight)
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val PHONE = """@phone {"paired":1,"connected":true,"pairing":false}"""
    }
}
