package com.tozes.hermescompanion.core

import com.tozes.hermescompanion.core.GadgetState.PairingProgress
import com.tozes.hermescompanion.core.GadgetState.Stage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GadgetStoreTest {
    private val link = FakeGadgetLink()

    /** A store connected to Nox, with the first `status` answered; other lines get `@ok`. */
    private fun TestScope.connectedNox(): GadgetStore {
        link.respond = { line -> if (line == "status") statusSample else "@ok" }
        val store = connectedStore(link)
        assertEquals("Nox", store.s.status?.name)
        return store
    }

    // Scanning

    @Test
    fun scanStartsOnceTheRadioIsOn() = runTest {
        val link = FakeGadgetLink(RadioState.UNKNOWN)
        val store = GadgetStore(link, backgroundScope)
        store.startScan()
        assertFalse(store.s.isScanning)
        assertFalse(link.isScanning)
        link.emit(LinkEvent.Radio(RadioState.POWERED_ON))
        assertEquals(RadioState.POWERED_ON, store.s.radio)
        assertTrue(store.s.isScanning)
        assertTrue(link.isScanning)
    }

    @Test
    fun stopScan() = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.startScan()
        store.stopScan()
        assertFalse(store.s.isScanning)
        assertFalse(link.isScanning)
        assertFalse(store.s.scanEnded)
    }

    @Test
    fun scanEndsAfterItsDuration() = runTest {
        val store = GadgetStore(link, backgroundScope, scanDuration = 20.milliseconds)
        store.startScan()
        assertTrue(store.s.isScanning)
        advanceTimeBy(21.milliseconds)
        assertTrue(store.s.scanEnded)
        assertFalse(store.s.isScanning)
        assertFalse(link.isScanning)
        // A radio coming back on doesn't restart a scan that ended by itself.
        link.emit(LinkEvent.Radio(RadioState.POWERED_ON))
        assertFalse(store.s.isScanning)
    }

    @Test
    fun scanStoppedEarlyDoesNotReportEnded() = runTest {
        val store = GadgetStore(link, backgroundScope, scanDuration = 20.milliseconds)
        store.startScan()
        store.stopScan()
        advanceTimeBy(60.milliseconds)
        assertFalse(store.s.scanEnded)
    }

    @Test
    fun defaultScanDuration() = runTest {
        assertEquals(20.seconds, GadgetStore(link, backgroundScope).scanDuration)
    }

    @Test
    fun discoveryUpdatesById() = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.startScan()
        link.emit(LinkEvent.Discovered(nox))
        link.emit(LinkEvent.Discovered(kitchen))
        val closer = nox.copy(rssi = -40)
        link.emit(LinkEvent.Discovered(closer))
        assertEquals(listOf(closer, kitchen), store.s.found)
        assertEquals(4, store.s.found.first().signalLevel)
    }

    @Test
    fun discoveryIsIgnoredWhenNotScanning() = runTest {
        val store = GadgetStore(link, backgroundScope)
        link.emit(LinkEvent.Discovered(nox))
        assertTrue(store.s.found.isEmpty())
    }

    @Test
    fun radioOffEndsTheScanAndClearsTheList() = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.startScan()
        link.emit(LinkEvent.Discovered(nox))
        link.emit(LinkEvent.Radio(RadioState.POWERED_OFF))
        assertFalse(store.s.isScanning)
        assertFalse(link.isScanning)
        assertTrue(store.s.found.isEmpty())
        // The scan picks up again when the radio does.
        link.emit(LinkEvent.Radio(RadioState.POWERED_ON))
        assertTrue(store.s.isScanning)
    }

    // Connecting

    @Test
    fun connectPairAndConnect() = runTest {
        link.respond = { line -> if (line == "status") statusSample else "@ok" }
        val store = GadgetStore(link, backgroundScope)
        store.startScan()
        store.connect(nox)
        assertFalse(link.isScanning)
        assertEquals(nox, link.target)
        assertEquals(Stage.PAIRING, store.s.stage)
        assertEquals(PairingProgress.Connecting, store.s.pairing)
        assertEquals(nox, store.s.gadget)

        link.emit(LinkEvent.Connecting)
        assertEquals(PairingProgress.Connecting, store.s.pairing)
        link.emit(LinkEvent.AwaitingPasskey)
        assertEquals(PairingProgress.AwaitingPasskey, store.s.pairing)
        assertEquals(Stage.PAIRING, store.s.stage)

        link.emit(LinkEvent.Connected)
        assertEquals(Stage.CONNECTED, store.s.stage)
        runCurrent()
        assertNotNull(store.s.status)
        assertEquals(listOf("status"), link.sent)
        assertEquals("Nox", store.s.status?.name)
        assertNotNull(store.s.statusTime)
        assertTrue(store.s.console.any { it.kind == ConsoleEntry.Kind.NOTE && it.text == "Connected to Nox" })
    }

    @ParameterizedTest
    @EnumSource(LinkFailure::class)
    fun failureWhilePairing(failure: LinkFailure) = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.connect(nox)
        link.emit(LinkEvent.AwaitingPasskey)
        link.emit(LinkEvent.Failed(failure))
        assertEquals(Stage.PAIRING, store.s.stage)
        assertEquals(PairingProgress.Failed(failure), store.s.pairing)
        assertFalse(store.s.connectionLost)
    }

    @ParameterizedTest
    @EnumSource(LinkFailure::class)
    fun disconnectWhilePairing(failure: LinkFailure) = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.connect(nox)
        link.emit(LinkEvent.Disconnected(failure))
        assertEquals(PairingProgress.Failed(failure), store.s.pairing)
    }

    @Test
    fun retryAfterAFailure() = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.connect(nox)
        link.emit(LinkEvent.Failed(LinkFailure.PAIRING_REJECTED))
        store.retryPairing()
        assertEquals(PairingProgress.Connecting, store.s.pairing)
        assertEquals(nox, link.target)
    }

    @Test
    fun backToFinding() = runTest {
        val store = GadgetStore(link, backgroundScope)
        store.connect(nox)
        store.backToFinding()
        assertEquals(Stage.FINDING, store.s.stage)
        assertNull(store.s.gadget)
        assertNull(link.target)
        assertTrue(store.s.isScanning)
    }

    @Test
    fun aNewConnectionStartsAFreshConsole() = runTest {
        val store = connectedNox()
        store.backToFinding()
        store.connect(nox)
        link.emit(LinkEvent.Connected)
        runCurrent()
        assertNotNull(store.s.status)
        assertEquals(1, store.s.console.count { it.text == "Connected to Nox" })
    }

    @Test
    fun linkDroppedWhileConnected() = runTest {
        val store = connectedNox()
        link.emit(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST))
        assertTrue(store.s.connectionLost)
        assertEquals(Stage.CONNECTED, store.s.stage)
        assertEquals("Connection lost", store.s.console.last().text)
        // Nothing is asked of a link that's gone.
        assertFalse(store.refreshStatus())
        assertEquals(listOf("status"), link.sent)

        store.reconnect()
        assertEquals(Stage.PAIRING, store.s.stage)
        assertFalse(store.s.connectionLost)
        // Reconnecting keeps the console, drop and all.
        assertTrue(store.s.console.any { it.text == "Connection lost" })
        assertEquals(nox, link.target)
    }

    @Test
    fun failedEventWhileConnectedIsADrop() = runTest {
        val store = connectedNox()
        link.emit(LinkEvent.Failed(LinkFailure.CONNECTION_LOST))
        assertTrue(store.s.connectionLost)
    }

    @Test
    fun disconnectTheAppAskedForIsNotADrop() = runTest {
        val store = connectedNox()
        link.emit(LinkEvent.Disconnected(null))
        assertFalse(store.s.connectionLost)
        assertEquals(Stage.CONNECTED, store.s.stage)
    }

    // Commands

    @Test
    fun commandsGoOneAtATime() = runTest {
        val store = connectedNox()
        link.holdReplies = true
        val first = async { store.send("get volume") }
        runCurrent()
        assertEquals(1, link.unanswered)
        val second = async { store.send("get brightness") }
        runCurrent()
        // The second line waits for the first reply.
        assertEquals(listOf("status", "get volume"), link.sent)

        link.answer(Fixture.reply("value-volume"))
        assertEquals(Reply.Value(SettingValue("volume", "70")), first.await())
        runCurrent()
        assertEquals(1, link.unanswered)
        assertEquals(listOf("status", "get volume", "get brightness"), link.sent)
        link.answer("""@value {"key":"brightness","value":"80"}""")
        assertEquals(Reply.Value(SettingValue("brightness", "80")), second.await())
        assertEquals("70", store.s.settings[SettingKey.VOLUME])
        assertEquals("80", store.s.settings[SettingKey.BRIGHTNESS])
    }

    @Test
    fun staleReplyAfterADropIsIgnored() = runTest {
        val store = connectedNox()
        link.holdReplies = true
        val reply = async { store.send("get volume") }
        runCurrent()
        assertEquals(1, link.unanswered)
        link.emit(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST))
        link.answer(Fixture.reply("value-volume"))
        assertEquals(Reply.Error("not connected"), reply.await())
        assertNull(store.s.settings[SettingKey.VOLUME])
        assertFalse(store.s.console.any { it.kind == ConsoleEntry.Kind.REPLY && it.text.contains("volume") })
    }

    @Test
    fun noReply() = runTest {
        val store = connectedNox()
        link.respond = { throw SendException.TimedOut() }
        assertEquals(Reply.Error("no reply from the gadget"), store.send("status"))
        assertEquals("No reply", store.s.console.last().text)
    }

    @Test
    fun notConnected() = runTest {
        val store = GadgetStore(link, backgroundScope)
        link.respond = { throw SendException.NotConnected() }
        assertEquals(Reply.Error("not connected"), store.send("status"))
    }

    @Test
    fun sentAndReceivedLinesAreLogged() = runTest {
        val store = connectedNox()
        store.send("say hi", typed = true)
        val last = store.s.console.takeLast(2)
        assertEquals(listOf(ConsoleEntry.Kind.COMMAND, ConsoleEntry.Kind.REPLY), last.map { it.kind })
        assertEquals(listOf("say hi", "@ok"), last.map { it.text })
        assertEquals(listOf(true, true), last.map { it.typed })
    }

    @Test
    fun newlinesBecomeSpaces() = runTest {
        val store = connectedNox()
        store.send("say one\ntwo")
        assertEquals("say one two", link.sent.last())
    }

    @Test
    fun linesOver256BytesAreRefused() = runTest {
        val store = connectedNox()
        val long = "a".repeat(257)
        val reply = store.send(long)
        assertEquals(Reply.Error("too long: the gadget takes lines up to 256 bytes"), reply)
        assertFalse(long in link.sent)
        assertEquals("Not sent: lines are limited to 256 bytes", store.s.console.last().text)

        // The limit is in bytes, not characters: this is 131 characters, 258 bytes.
        val accented = "say " + "é".repeat(127)
        assertTrue(store.send(accented).isError)
        assertFalse(accented in link.sent)
        assertEquals(listOf("status"), link.sent)
    }

    @Test
    fun lineOfExactly256BytesIsSent() = runTest {
        val store = connectedNox()
        val line = "say " + "a".repeat(252)
        assertEquals(Reply.Ok(""), store.send(line))
        assertEquals(line, link.sent.last())
    }

    // Settings

    @Test
    fun setUpdatesSettings() = runTest {
        val store = connectedNox()
        link.respond = { line -> "@ok " + line.split(" ")[1] }
        assertNull(store.save(SettingKey.VOLUME, "55"))
        assertEquals("set volume 55", link.sent.last())
        assertEquals("55", store.s.settings[SettingKey.VOLUME])
    }

    @Test
    fun secretsAreMasked() = runTest {
        val store = connectedNox()
        assertNull(store.save(SettingKey.WIFI_PASSWORD, "correct horse"))
        assertEquals("set wifi_pass correct horse", link.sent.last())
        assertEquals(SettingValue.MASKED_SECRET, store.s.settings[SettingKey.WIFI_PASSWORD])
        assertNull(store.save(SettingKey.TOKEN, ""))
        assertEquals("", store.s.settings[SettingKey.TOKEN])
    }

    /** The gadget gets secrets as typed; the console only ever shows them hidden. */
    @Test
    fun secretsStayOutOfTheConsole() = runTest {
        val store = connectedNox()
        store.save(SettingKey.WIFI_PASSWORD, "correct horse")
        store.save(SettingKey.TOKEN, "tok-123")
        store.send("wifi add \"Lisbon Loft\" \"hotspot pass\"", typed = true)
        assertEquals(
            listOf("set wifi_pass correct horse", "set token tok-123", "wifi add \"Lisbon Loft\" \"hotspot pass\""),
            link.sent.takeLast(3),
        )
        val commands = store.s.console.filter { it.kind == ConsoleEntry.Kind.COMMAND }.map { it.text }
        assertEquals(
            listOf("set wifi_pass <hidden>", "set token <hidden>", "wifi add \"Lisbon Loft\" <hidden>"),
            commands.takeLast(3),
        )
        assertTrue(store.s.console.none { "correct horse" in it.text || "tok-123" in it.text || "hotspot pass" in it.text })
    }

    @Test
    fun ordinaryLinesAreLoggedAsTyped() {
        assertEquals("set volume 40", ConsoleArgument.redacted("set volume 40"))
        assertEquals("set token", ConsoleArgument.redacted("set token"))
        assertEquals("wifi add Open", ConsoleArgument.redacted("wifi add Open"))
        assertEquals("get wifi_pass", ConsoleArgument.redacted("get wifi_pass"))
    }

    @Test
    fun renamingUpdatesTheStatusAndGadget() = runTest {
        val store = connectedNox()
        assertNull(store.save(SettingKey.NAME, " Desk "))
        assertEquals("set name Desk", link.sent.last())
        assertEquals("Desk", store.s.settings[SettingKey.NAME])
        assertEquals("Desk", store.s.status?.name)
        assertEquals("Desk", store.s.gadget?.name)
    }

    @Test
    fun settingTypedInTheConsoleIsKept() = runTest {
        val store = connectedNox()
        store.send("set talk_mode tap", typed = true)
        assertEquals("tap", store.s.settings[SettingKey.TALK_MODE])
    }

    @Test
    fun errorIsReturnedToTheCaller() = runTest {
        val store = connectedNox()
        link.respond = { Fixture.reply("error-screen-timeout") }
        assertEquals("screen_timeout must be 0..3600 seconds", store.save(SettingKey.SCREEN_TIMEOUT, "9999"))
        assertNull(store.s.settings[SettingKey.SCREEN_TIMEOUT])
    }

    @Test
    fun unexpectedReplyToASave() = runTest {
        val store = connectedNox()
        link.respond = { "@phone {}" }
        assertEquals("The gadget didn't confirm.", store.save(SettingKey.VOLUME, "10"))
    }

    @Test
    fun loadSettingsReadsEveryKey() = runTest {
        val store = connectedNox()
        link.respond = { line -> """@value {"key":"${line.split(" ")[1]}","value":"x"}""" }
        store.loadSettings()
        assertEquals(SettingKey.entries.map { "get ${it.rawValue}" }, link.sent.drop(1))
        assertFalse(store.s.missingSettings)
    }

    @Test
    fun busyGadgetLeavesSettingsMissing() = runTest {
        val store = connectedNox()
        link.respond = { line -> if (line == "get volume") "@error device busy" else """@value {"key":"${line.split(" ")[1]}","value":"1"}""" }
        store.loadSettings()
        assertTrue(store.s.missingSettings)
        assertNull(store.s.settings[SettingKey.VOLUME])
        // Only what's missing is asked again.
        val before = link.sent.size
        link.respond = { Fixture.reply("value-volume") }
        store.loadSettings()
        assertEquals(listOf("get volume"), link.sent.drop(before))
        assertFalse(store.s.missingSettings)
    }

    @Test
    fun statusErrorIsKept() = runTest {
        val store = connectedNox()
        link.respond = { Fixture.reply("error-busy") }
        assertFalse(store.refreshStatus())
        assertEquals("device busy", store.s.statusError)
        assertEquals("Nox", store.s.status?.name)
    }

    @Test
    fun phoneReplyUpdatesTheStatus() = runTest {
        val store = connectedNox()
        link.respond = { """@phone {"paired":2,"connected":true,"pairing":true}""" }
        store.send("phone")
        assertEquals(PhoneState(paired = 2, connected = true, pairing = true), store.s.status?.phone)
    }

    // Forgetting

    @Test
    fun phoneForgetReturnsToFinding() = runTest {
        val store = connectedNox()
        link.respond = { "@ok phone forget" }
        assertNull(store.forget())
        assertEquals("phone forget", link.sent.last())
        assertEquals(Stage.FINDING, store.s.stage)
        assertEquals("Nox", store.s.forgottenName)
        assertNull(store.s.gadget)
        assertNull(store.s.status)
        assertNull(link.target)
    }

    @Test
    fun refusedForgetStaysConnected() = runTest {
        val store = connectedNox()
        link.respond = { Fixture.reply("error-busy") }
        assertEquals("device busy", store.forget())
        assertEquals(Stage.CONNECTED, store.s.stage)
        assertNull(store.s.forgottenName)
    }

    @Test
    fun connectingClearsTheForgottenNote() = runTest {
        val store = connectedNox()
        store.send("phone forget")
        assertEquals("Nox", store.s.forgottenName)
        store.connect(kitchen)
        assertNull(store.s.forgottenName)
    }

    // Replacing the link

    @Test
    fun onlyTheDemoScenarioIsTheDemo() = runTest {
        val store = GadgetStore(link, backgroundScope)
        assertFalse(store.s.isDemo)
        store.replaceLink(MockGadgetLink(MockScenario.DEMO, backgroundScope))
        assertTrue(store.s.isDemo)
        store.replaceLink(MockGadgetLink(MockScenario.ONLINE, backgroundScope))
        assertFalse(store.s.isDemo)
    }

    @Test
    fun replaceLinkStartsOver() = runTest {
        val store = connectedNox()
        val other = FakeGadgetLink()
        store.replaceLink(other)
        assertNull(link.target)
        assertNull(link.onEvent)
        assertEquals(Stage.FINDING, store.s.stage)
        assertNull(store.s.gadget)
        assertNull(store.s.status)
        assertTrue(store.s.console.isEmpty())
        assertTrue(other.isScanning)
        // Events from the old link no longer reach the store.
        link.emit(LinkEvent.Connected)
        assertEquals(Stage.FINDING, store.s.stage)
        other.emit(LinkEvent.Discovered(kitchen))
        assertEquals(listOf(kitchen), store.s.found)
    }

    // Beyond the Swift suite

    @Test
    fun startAndEndTheDemo() = runTest {
        val real = FakeGadgetLink()
        val store = GadgetStore(link, backgroundScope, makeRealLink = { real })
        store.startDemo()
        assertTrue(store.s.isDemo)
        assertTrue(store.link is MockGadgetLink)
        store.endDemo()
        assertFalse(store.s.isDemo)
        assertTrue(store.link === real)
        assertTrue(real.isScanning)
    }

    @Test
    fun consoleKeepsTheLast500Lines() = runTest {
        val store = connectedNox()
        repeat(300) { store.send("say $it") }
        assertEquals(500, store.s.console.size)
        assertEquals("@ok", store.s.console.last().text)
        assertEquals("say 299", store.s.console[store.s.console.size - 2].text)
    }
}
