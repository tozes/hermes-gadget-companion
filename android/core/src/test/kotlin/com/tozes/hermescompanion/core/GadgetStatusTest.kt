package com.tozes.hermescompanion.core

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GadgetStatusTest {
    private fun decode(json: String): GadgetStatus = Reply.json.decodeFromString(json)

    @Test
    fun realSample() {
        val status = assertIs<Reply.Status>(Reply.parse(statusSample)).status
        assertEquals("hg-5a1c9e2f7b3d4086", status.deviceId)
        assertEquals("Nox", status.name)
        assertEquals("esp32s3-touch-amoled-1.75c", status.board)
        assertEquals("0.2.0", status.firmware)
        assertEquals(GadgetStatus.Phase.NoNetwork, status.phase)
        assertEquals("offline", status.screen)
        assertEquals(false, status.paired)
        assertNull(status.pairingCode)
        assertEquals("wss://hermes.example.com:8765/gadget", status.server)
        assertEquals(false, status.network)
        assertEquals(false, status.displaySleeping)
        val power = assertNotNull(status.power)
        assertEquals(true, power.available)
        assertEquals(true, power.batteryPresent)
        assertEquals(4126, power.batteryMv)
        assertEquals(84, power.batteryPercent)
        assertEquals(true, power.charging)
        assertEquals(true, power.externalPower)
        assertEquals(PhoneState(paired = 1, connected = true, pairing = false), status.phone)
    }

    @Test
    fun emptyObjectDecodes() {
        assertEquals(GadgetStatus(), decode("{}"))
    }

    @Test
    fun missingOptionalFields() {
        val status = assertIs<Reply.Status>(Reply.parse(Fixture.reply("status-no-battery"))).status
        assertEquals("Kitchen", status.name)
        assertEquals(GadgetStatus.Phase.Online, status.phase)
        assertNull(status.network)
        assertNull(status.phone)
        assertEquals(GadgetStatus.Power(available = false), status.power)
        assertNull(status.power?.batteryPercent)
    }

    @Test
    fun pairingCode() {
        val status = assertIs<Reply.Status>(Reply.parse(Fixture.reply("status-awaiting-approval"))).status
        assertEquals("YGJEQNXU", status.pairingCode)
    }

    @ParameterizedTest
    @MethodSource("phaseCases")
    fun phases(raw: String, phase: GadgetStatus.Phase) {
        assertEquals(phase, decode("""{"phase":"$raw"}""").phase)
        assertEquals(raw, phase.rawValue)
    }

    @Test
    fun phaseRoundTrips() {
        val status = GadgetStatus(name = "Nox", phase = GadgetStatus.Phase.Other("updating"))
        val json = Reply.json.encodeToString(GadgetStatus.serializer(), status)
        assertEquals(status, decode(json))
    }

    companion object {
        @JvmStatic
        fun phaseCases(): List<Arguments> = listOf(
            Arguments.of("boot", GadgetStatus.Phase.Boot),
            Arguments.of("no_network", GadgetStatus.Phase.NoNetwork),
            Arguments.of("connecting", GadgetStatus.Phase.Connecting),
            Arguments.of("handshake", GadgetStatus.Phase.Handshake),
            Arguments.of("online", GadgetStatus.Phase.Online),
            Arguments.of("updating", GadgetStatus.Phase.Other("updating")),
        )
    }
}
