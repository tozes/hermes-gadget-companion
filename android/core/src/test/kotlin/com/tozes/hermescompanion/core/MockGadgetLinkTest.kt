package com.tozes.hermescompanion.core

import com.tozes.hermescompanion.core.GadgetState.PairingProgress
import com.tozes.hermescompanion.core.GadgetState.Stage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The mock scenarios end to end, through the store, on virtual time. */
class MockGadgetLinkTest {
    private fun TestScope.store(scenario: MockScenario): GadgetStore {
        val link = MockGadgetLink(scenario, backgroundScope, Random(1), testScheduler.timeSource)
        return GadgetStore(link, backgroundScope)
    }

    @Test
    fun theDemoFindsTwoGadgetsAndPairsWithoutACode() = runTest {
        val store = store(MockScenario.DEMO)
        assertEquals(RadioState.UNKNOWN, store.s.radio)
        store.startScan()
        // The radio comes on at 0.4 s; Nox shows up 0.9 s later, Kitchen 1.3 s after that.
        advanceTimeBy(2.7.seconds)
        assertEquals(RadioState.POWERED_ON, store.s.radio)
        assertEquals(listOf("Nox", "Kitchen"), store.s.found.map { it.name })
        store.connect(store.s.found.first())
        advanceTimeBy(1.seconds)
        assertEquals(PairingProgress.AwaitingPasskey, store.s.pairing)
        advanceTimeBy(5.seconds)
        assertEquals(Stage.CONNECTED, store.s.stage)
        assertEquals("Online", StatusSummary.of(store.s.status).headline)
    }

    @Test
    fun nothingNearbyEndsTheScan() = runTest {
        val store = store(MockScenario.NOTHING_NEARBY)
        store.startScan()
        advanceTimeBy(21.seconds)
        assertTrue(store.s.found.isEmpty())
        assertTrue(store.s.scanEnded)
    }

    @Test
    fun bluetoothDeniedIsUnauthorized() = runTest {
        val store = store(MockScenario.BLUETOOTH_DENIED)
        advanceTimeBy(1.seconds)
        assertEquals(RadioState.UNAUTHORIZED, store.s.radio)
    }

    @Test
    fun pairingRejected() = runTest {
        val store = store(MockScenario.PAIRING_REJECTED)
        advanceTimeBy(1.seconds)
        store.connect(nox)
        advanceTimeBy(6.seconds)
        assertEquals(PairingProgress.Failed(LinkFailure.PAIRING_REJECTED), store.s.pairing)
    }

    @Test
    fun forgettingDropsThePhone() = runTest {
        val store = store(MockScenario.ONLINE)
        advanceTimeBy(1.seconds)
        store.connect(nox)
        advanceTimeBy(6.seconds)
        assertEquals(Stage.CONNECTED, store.s.stage)
        assertEquals(null, store.forget())
        assertEquals(Stage.FINDING, store.s.stage)
        assertEquals("Nox", store.s.forgottenName)
        // Pairing again asks for the code: the gadget dropped the bond.
        store.connect(nox)
        advanceTimeBy(1.seconds)
        assertEquals(PairingProgress.AwaitingPasskey, store.s.pairing)
    }

    @Test
    fun theFlakyLinkDropsAfterAMinute() = runTest {
        val store = store(MockScenario.FLAKY)
        advanceTimeBy(1.seconds)
        store.connect(nox)
        advanceTimeBy(6.seconds)
        assertEquals(Stage.CONNECTED, store.s.stage)
        advanceTimeBy(61.seconds)
        runCurrent()
        assertTrue(store.s.connectionLost)
    }
}
