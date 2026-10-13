package com.tozes.hermescompanion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the screens show, from the logic the iOS app keeps in its views. */
class PresentationTest {
    private fun status(text: String) = assertIs<Reply.Status>(Reply.parse(text)).status

    @Test
    fun summaryForEachPhase() {
        assertEquals("Reading status", StatusSummary.of(null).headline)
        assertEquals("Asking the gadget how it is.", StatusSummary.of(null).detail)
        val newGadget = StatusSummary.of(status(statusSample))
        assertEquals("No Wi-Fi", newGadget.headline)
        assertEquals(Tone.RED, newGadget.headlineTone)
        assertEquals(StatusSummary.NextStep.SetUpWifi, newGadget.next)
        val online = StatusSummary.of(status(Fixture.reply("status-online")))
        assertEquals("Online", online.headline)
        assertEquals(Indicator.Pulse(Tone.GREEN), online.indicator)
        assertNull(online.next)
        val waiting = StatusSummary.of(status(Fixture.reply("status-awaiting-approval")))
        assertEquals("Waiting for approval", waiting.headline)
        assertEquals(StatusSummary.NextStep.ApproveOnHermes("YGJEQNXU"), waiting.next)
        assertEquals("Starting up", StatusSummary.of(GadgetStatus(phase = GadgetStatus.Phase.Boot)).headline)
        assertEquals("Connecting", StatusSummary.of(GadgetStatus(phase = GadgetStatus.Phase.Connecting)).headline)
        assertEquals("Signing in", StatusSummary.of(GadgetStatus(phase = GadgetStatus.Phase.Handshake)).headline)
        val other = StatusSummary.of(GadgetStatus(name = "Nox", phase = GadgetStatus.Phase.Other("updating")))
        assertEquals("updating", other.headline)
        assertEquals("Nox reports phase \"updating\".", other.detail)
        assertEquals("Not connected", StatusSummary.connectionLost("Nox").headline)
    }

    @Test
    fun wifiSummaries() {
        val base = GadgetState(radio = RadioState.POWERED_ON)
        assertEquals("Not set up", wifiSummary(base))
        assertEquals("Connected", wifiSummary(base.copy(status = GadgetStatus(network = true))))
        val oneNetwork = base.copy(settings = mapOf(SettingKey.WIFI_SSID to "Lisbon Loft"))
        assertEquals("Lisbon Loft, not connected", wifiSummary(oneNetwork))
        assertEquals("On Lisbon Loft", wifiSummary(oneNetwork.copy(status = GadgetStatus(network = true))))
        val list = assertIs<Reply.Wifi>(Reply.parse(Fixture.reply("wifi-list"))).list
        assertEquals("On Lisbon Loft · 2 networks saved", wifiSummary(base.copy(wifiNetworks = list)))
        assertEquals("Not connected · 1 network saved", wifiSummary(base.copy(wifiNetworks = WifiNetworkList(listOf(WifiNetworkList.Network("A"))))))
    }

    @Test
    fun statusDetails() {
        val sample = status(statusSample)
        assertEquals("84%, charging, USB power (4.13 V)", batteryDescription(sample))
        assertEquals("Not reported by this board", batteryDescription(status(Fixture.reply("status-no-battery"))))
        assertEquals("Offline", hermesDescription(sample))
        assertEquals("Online and approved", hermesDescription(status(Fixture.reply("status-online"))))
        assertEquals("Online, waiting for approval", hermesDescription(status(Fixture.reply("status-awaiting-approval"))))
    }

    @Test
    fun power() {
        val onBattery = status(Fixture.reply("status-online")).power!!
        assertEquals("76%, on battery", onBattery.summary)
        assertEquals(BatteryGauge(76, charging = false, low = false), onBattery.gauge)
        assertEquals("3.96 V", onBattery.voltage)
        val low = GadgetStatus.Power(available = true, batteryPresent = true, batteryPercent = 9, externalPower = false)
        assertTrue(low.isLow)
        assertTrue(low.gauge!!.low)
        assertFalse(low.copy(externalPower = true).isLow)
        assertEquals(LOW_BATTERY_PERCENT, 10)
        assertFalse(low.copy(batteryPercent = 11).isLow)
        assertNull(GadgetStatus.Power(available = false).gauge)
        assertEquals("USB power, no battery", GadgetStatus.Power(available = false, externalPower = true).summary)
    }

    @Test
    fun serverWrapsAfterSlashesAndDots() {
        assertEquals("wss:/​/​hermes.​example.​com:8765/​gadget", wrappable("wss://hermes.example.com:8765/gadget", setOf('/', '.')))
    }

    @Test
    fun screenTimeoutWords() {
        assertEquals("The screen stays on.", screenTimeoutDescription("0"))
        assertEquals("The screen sleeps after 30 seconds without a touch.", screenTimeoutDescription("30"))
        assertEquals("The screen sleeps after 1 minute without a touch.", screenTimeoutDescription("60"))
        assertEquals("The screen sleeps after 1 minute, 30 seconds without a touch.", screenTimeoutDescription("90"))
        assertEquals("The screen sleeps after 60 minutes without a touch.", screenTimeoutDescription("3600"))
        assertNull(screenTimeoutDescription(null))
        assertNull(screenTimeoutDescription(""))
    }

    @Test
    fun signalLevels() {
        assertEquals(listOf(4, 4, 3, 2, 1), listOf(-40, -55, -56, -80, -81).map { DiscoveredGadget("x", "x", it).signalLevel })
        assertEquals("fair", DiscoveredGadget("x", "x", -75).signalDescription)
    }

    // The pixel font

    @Test
    fun theFontHasEveryPrintableCharacter() {
        assertEquals(95, PixelFontGlyphs.table.size)
        assertTrue(PixelFontGlyphs.table.all { it.split(' ').size == 7 })
        // 'A' has ink; ' ' has none.
        assertTrue(PixelFont.glyph('A').any { it != 0 })
        assertTrue(PixelFont.glyph(' ').all { it == 0 })
        // Characters without a glyph draw as '?'.
        assertTrue(PixelFont.glyph('é').contentEquals(PixelFont.glyph('?')))
    }

    @Test
    fun fontMetrics() {
        assertEquals(0, PixelFont.width(0))
        assertEquals(59, PixelFont.width(10))
        assertEquals(7, PixelFont.height(1))
        assertEquals(16, PixelFont.height(2))
        // 10 columns at scale 2 need 118 px.
        assertEquals(10, PixelFont.columns(118f, 2f))
        assertEquals(9, PixelFont.columns(117f, 2f))
        assertEquals(1, PixelFont.columns(0f, 2f))
    }

    @Test
    fun fitAndWrap() {
        assertEquals("Lisbon", PixelFont.fit("Lisbon", 6))
        assertEquals("Lisb..", PixelFont.fit("Lisbon Loft", 6))
        assertEquals("Li", PixelFont.fit("Lisbon", 2))
        assertEquals(listOf("Looking for", "gadgets"), PixelFont.wrap("Looking for gadgets", 12))
        assertEquals(listOf("abcde", "fgh x"), PixelFont.wrap("abcdefgh x", 5))
        assertEquals(listOf("abcde", "fgh", "xyz12"), PixelFont.wrap("abcdefgh xyz12", 5))
        assertEquals(listOf("one", "", "two"), PixelFont.wrap("one\n\ntwo", 10))
        assertEquals(listOf("a b"), PixelFont.lines("a b", null, 2f, wraps = true))
    }

    @Test
    fun normalizeAndCanRender() {
        assertEquals("It's \"on\" - 2...3 > x.", PixelFont.normalize("It’s “on” — 2…3 → x·"))
        assertTrue(PixelFont.canRender("Hermes 01"))
        assertFalse(PixelFont.canRender("Café"))
    }

    @Test
    fun runsCoverTheGlyphPixels() {
        var pixels = 0
        PixelFont.forEachRun(listOf("I"), width = 5, centered = false) { _, _, w -> pixels += w }
        val expected = PixelFont.glyph('I').sumOf { Integer.bitCount(it) }
        assertEquals(expected, pixels)
        var firstX = -1
        PixelFont.forEachRun(listOf("I"), width = 15, centered = true) { x, _, _ -> if (firstX < 0) firstX = x }
        assertTrue(firstX >= 5)
    }

    // The mascot

    @Test
    fun everyMascotFrameDecodes() {
        assertEquals(12, Mascot.bitmaps.size)
        for (frame in MascotFrame.entries) {
            assertEquals(listOf(64, 96, 144, 192), Mascot.bitmaps.filter { it.frame == frame }.map { it.size }.sorted())
        }
        assertTrue(Mascot.bitmaps.all { it.runs.isNotEmpty() && it.runs.all { run -> run.x + run.width <= it.size } })
    }

    @Test
    fun mascotPicksWholePixels() {
        val big = assertNotNull(Mascot.pick(MascotFrame.IDLE, side = 168f, displayScale = 3f))
        // 504 device pixels: 192 px art at 2x fills 384, 144 at 3x fills 432; both are within 3/4 of the best.
        assertEquals(192, big.bitmap.size)
        assertEquals(2f / 3f, big.pixel)
        val small = assertNotNull(Mascot.pick(MascotFrame.BLINK, side = 32f, displayScale = 1f))
        assertEquals(64, small.bitmap.size)
        assertEquals(0.5f, small.pixel)
    }
}
