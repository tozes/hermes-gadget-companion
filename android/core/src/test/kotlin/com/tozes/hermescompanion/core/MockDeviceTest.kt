package com.tozes.hermescompanion.core

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The console behaviour the mock scenarios rely on, checked against the
 * firmware's replies (`App::console` and its tests in the SDK).
 */
class MockDeviceTest {
    private val now = Duration.ZERO

    @Test
    fun getReturnsTheValue() {
        val device = MockDevice.nox(MockScenario.NEW_GADGET)
        assertEquals(Fixture.reply("value-volume"), device.handle("get volume", now))
        assertEquals(Reply.Value(SettingValue("name", "Nox")), Reply.parse(device.handle("get name", now)))
    }

    @Test
    fun secretsAreMasked() {
        val kitchen = MockDevice.kitchen()
        assertEquals(Fixture.reply("value-secret"), kitchen.handle("get wifi_pass", now))
        assertEquals("""@value {"key":"token","value":"<set>"}""", kitchen.handle("get token", now))
        // An empty secret reads back empty, not masked.
        val nox = MockDevice.nox(MockScenario.NEW_GADGET)
        assertEquals("""@value {"key":"wifi_pass","value":""}""", nox.handle("get wifi_pass", now))
    }

    @Test
    fun setThenGet() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals(Fixture.reply("ok-volume"), device.handle("set volume 55", now))
        assertEquals("""@value {"key":"volume","value":"55"}""", device.handle("get volume", now))
        assertEquals("@ok name", device.handle("set name  Desk Lamp ", now))
        assertEquals("""@value {"key":"name","value":"Desk Lamp"}""", device.handle("get name", now))
    }

    @Test
    fun valuesAreJsonEscaped() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        device.handle("""set name say "hi" \ bye""", now)
        assertEquals(Reply.Value(SettingValue("name", """say "hi" \ bye""")), Reply.parse(device.handle("get name", now)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["get nonsense", "set nonsense 1", "get", "set"])
    fun unknownKey(line: String) {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals("@error unknown key", device.handle(line, now))
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "3601", "12345", "00001", "abc", "1.5", "６０"])
    fun screenTimeoutOutOfRange(value: String) {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals(Fixture.reply("error-screen-timeout"), device.handle("set screen_timeout $value", now))
        assertEquals("60", device.settings["screen_timeout"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "30", "3600", "0030", ""])
    fun screenTimeoutInRange(value: String) {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals("@ok screen_timeout", device.handle("set screen_timeout $value", now))
        assertEquals(value, device.settings["screen_timeout"])
    }

    @Test
    fun batteryDisplay() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals("@error battery_display must be icon, percentage, both or off", device.handle("set battery_display maybe", now))
        assertEquals("icon", device.settings["battery_display"])
        for (value in BatteryDisplay.entries.map { it.rawValue }) {
            assertEquals("@ok battery_display", device.handle("set battery_display $value", now))
        }
        assertEquals("off", device.settings["battery_display"])
    }

    @Test
    fun phoneForget() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        device.phoneConnected = true
        assertEquals("@ok phone pairing open", device.handle("phone pair", now))
        assertEquals("@ok phone forget", device.handle("phone forget", now))
        assertEquals(Reply.Phone(PhoneState(paired = 0, connected = false, pairing = false)), Reply.parse(device.handle("phone", now)))
        assertEquals("@error usage: phone [pair [close] | forget]", device.handle("phone bogus", now))
    }

    @Test
    fun unknownCommand() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals(Fixture.reply("error-unknown-command"), device.handle("dance", now))
    }

    @Test
    fun statusDecodes() {
        val device = MockDevice.nox(MockScenario.NEW_GADGET)
        val status = assertIs<Reply.Status>(Reply.parse(device.handle("status", now))).status
        assertEquals("hg-5a1c9e2f7b3d4086", status.deviceId)
        assertEquals(GadgetStatus.Phase.NoNetwork, status.phase)
        assertEquals(false, status.network)
        assertEquals(84, status.power?.batteryPercent)
        assertNull(status.pairingCode)
    }

    /** What "Try the demo" opens on: set up, approved, on battery, two saved networks. */
    @Test
    fun demoIsASetUpGadget() {
        val device = MockDevice.nox(MockScenario.DEMO)
        val status = assertIs<Reply.Status>(Reply.parse(device.handle("status", now))).status
        assertEquals(GadgetStatus.Phase.Online, status.phase)
        assertEquals(true, status.paired)
        assertEquals(true, status.network)
        assertEquals(76, status.power?.batteryPercent)
        assertEquals(false, status.power?.charging)
        assertEquals(false, status.power?.externalPower)
        val list = assertIs<Reply.Wifi>(Reply.parse(device.handle("wifi list", now))).list
        assertEquals(listOf("Lisbon Loft", "Pocket Hotspot"), list.networks.map { it.ssid })
        assertEquals(true, list.networks.all { it.tested })
        assertEquals("Lisbon Loft", list.current)
    }

    @Test
    fun awaitingApprovalShowsThePairingCode() {
        val device = MockDevice.nox(MockScenario.AWAITING_APPROVAL)
        val status = assertIs<Reply.Status>(Reply.parse(device.handle("status", now))).status
        assertEquals(GadgetStatus.Phase.Online, status.phase)
        assertEquals(false, status.paired)
        assertEquals(MockDevice.PAIRING_CODE, status.pairingCode)
        device.approve()
        val approved = assertIs<Reply.Status>(Reply.parse(device.handle("status", now))).status
        assertEquals(true, approved.paired)
        assertNull(approved.pairingCode)
    }

    @Test
    fun boardWithoutBattery() {
        val kitchen = MockDevice.kitchen()
        val status = assertIs<Reply.Status>(Reply.parse(kitchen.handle("status", now))).status
        assertEquals(GadgetStatus.Power(available = false), status.power)
    }

    /** Network and phase after [seconds] of a join started at [now]. */
    private fun phase(device: MockDevice, seconds: Double): Pair<Boolean?, GadgetStatus.Phase?> {
        val status = (Reply.parse(device.handle("status", now + seconds.seconds)) as? Reply.Status)?.status
        return status?.network to status?.phase
    }

    @Test
    fun wifiJoinComesOnline() {
        val device = MockDevice.nox(MockScenario.NEW_GADGET)
        assertEquals("@ok wifi_ssid", device.handle("set wifi_ssid Lisbon Loft", now))
        assertEquals(false to GadgetStatus.Phase.NoNetwork, phase(device, 1.0))
        assertEquals(true to GadgetStatus.Phase.Connecting, phase(device, 3.0))
        assertEquals(true to GadgetStatus.Phase.Handshake, phase(device, 5.0))
        assertEquals(true to GadgetStatus.Phase.Online, phase(device, 6.0))
    }

    @Test
    fun wifiThatFailsNeverJoins() {
        val device = MockDevice.nox(MockScenario.WIFI_FAILS)
        device.handle("set wifi_ssid Lisbon Loft", now)
        assertEquals(false to GadgetStatus.Phase.NoNetwork, phase(device, 60.0))
    }

    @Test
    fun hermesUnreachableStaysConnecting() {
        val device = MockDevice.nox(MockScenario.HERMES_UNREACHABLE)
        device.handle("set wifi_ssid Lisbon Loft", now)
        assertEquals(true to GadgetStatus.Phase.Connecting, phase(device, 60.0))
    }

    // Beyond the Swift suite

    @Test
    fun helpMatchesTheFixture() {
        assertEquals(Fixture.reply("help"), MockDevice.nox(MockScenario.ONLINE).handle("help", now))
    }
}
