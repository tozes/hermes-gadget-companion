package com.tozes.hermescompanion.core

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WifiJoinTest {
    private val link = FakeGadgetLink()

    /**
     * A store connected to Nox: `set` lines get `@ok <key>` unless [refuse]
     * names the key, and `status` follows [statuses] after the first (offline) one.
     */
    private fun TestScope.store(statuses: List<String>, refuse: Map<String, String> = emptyMap()): GadgetStore {
        val queue = (listOf(OFFLINE) + statuses).toMutableList()
        link.respond = { line ->
            val words = line.split(" ")
            if (words.first() == "set" && words.size > 1) {
                val key = words[1]
                refuse[key]?.let { "@error $it" } ?: "@ok $key"
            } else if (line != "status") {
                "@error unknown command (try: help)"
            } else if (queue.size > 1) {
                queue.removeAt(0)
            } else {
                queue[0]
            }
        }
        return connectedStore(link)
    }

    /** Quick polls and short timeouts, on the test's virtual clock; a run that succeeds ends well before them. */
    private fun TestScope.join(timeout: Duration = 2.seconds) =
        WifiJoin(joinTimeout = timeout, hermesTimeout = timeout, pollInterval = 1.milliseconds, timeSource = testScheduler.timeSource)

    private val setLines: List<String> get() = link.sent.filter { it.startsWith("set ") }

    @Test
    fun joinsAndReachesHermes() = runTest {
        val store = store(listOf(OFFLINE, CONNECTING, HANDSHAKE, ONLINE))
        val join = join()
        join.run(store, "Lisbon Loft", "correct horse", clearPassword = false)
        assertEquals(WifiJoin.Step.Done(approved = true), join.step)
        assertFalse(join.isRunning)
        assertEquals(listOf("set wifi_ssid Lisbon Loft", "set wifi_pass correct horse"), setLines)
        assertEquals("Lisbon Loft", store.s.settings[SettingKey.WIFI_SSID])
        assertEquals(SettingValue.MASKED_SECRET, store.s.settings[SettingKey.WIFI_PASSWORD])
    }

    @Test
    fun onlineButNotApproved() = runTest {
        val store = store(listOf(CONNECTING, status(network = true, phase = "online", paired = false)))
        val join = join()
        join.run(store, "Lisbon Loft", "pw", clearPassword = false)
        assertEquals(WifiJoin.Step.Done(approved = false), join.step)
    }

    @Test
    fun emptyPasswordKeepsTheSavedOne() = runTest {
        val store = store(listOf(ONLINE))
        val join = join()
        join.run(store, "Lisbon Loft", "", clearPassword = false)
        assertEquals(listOf("set wifi_ssid Lisbon Loft"), setLines)
        assertEquals(WifiJoin.Step.Done(approved = true), join.step)
    }

    @Test
    fun openNetworkClearsThePassword() = runTest {
        val store = store(listOf(ONLINE))
        val join = join()
        join.run(store, "Cafe", "ignored", clearPassword = true)
        assertEquals(listOf("set wifi_ssid Cafe", "set wifi_pass"), setLines.map { it.trim() })
        assertEquals("", store.s.settings[SettingKey.WIFI_PASSWORD])
    }

    @Test
    fun refusedNetworkName() = runTest {
        val store = store(listOf(ONLINE), refuse = mapOf("wifi_ssid" to "wifi_ssid too long"))
        val join = join()
        join.run(store, "x".repeat(40), "pw", clearPassword = false)
        assertEquals(WifiJoin.Step.Editing, join.step)
        assertEquals("wifi_ssid too long", join.error(WifiJoin.Field.SSID))
        assertNull(join.error(WifiJoin.Field.PASSWORD))
        assertEquals(1, setLines.size)
    }

    @Test
    fun refusedPassword() = runTest {
        val store = store(listOf(ONLINE), refuse = mapOf("wifi_pass" to "wifi_pass too long"))
        val join = join()
        join.run(store, "Lisbon Loft", "pw", clearPassword = false)
        assertEquals(WifiJoin.Step.Editing, join.step)
        assertEquals("wifi_pass too long", join.error(WifiJoin.Field.PASSWORD))
        assertNull(join.error(WifiJoin.Field.SSID))
        // Only the status from connecting: no polling.
        assertEquals(1, link.sent.count { it == "status" })
    }

    @Test
    fun networkNeverComesUp() = runTest {
        val store = store(listOf(OFFLINE))
        val join = join(timeout = 50.milliseconds)
        join.run(store, "Lisbon Loft", "pw", clearPassword = false)
        val failed = assertIs<WifiJoin.Step.Failed>(join.step)
        assertTrue(failed.message.startsWith("Lisbon Loft didn't connect."))
    }

    @Test
    fun hermesNeverAnswers() = runTest {
        val store = store(listOf(CONNECTING))
        val join = join(timeout = 50.milliseconds)
        join.run(store, "Lisbon Loft", "pw", clearPassword = false)
        assertEquals(WifiJoin.Step.NoHermes, join.step)
    }

    @Test
    fun failedRefreshesDontCount() = runTest {
        // The status from before the join says online; busy replies mustn't
        // let that stale status pass for a new one.
        link.respond = { line -> if (line == "status") ONLINE else "@ok" }
        val store = connectedStore(link)
        assertEquals(true, store.s.status?.network)
        link.respond = { line -> if (line == "status") Fixture.reply("error-busy") else "@ok wifi_ssid" }
        val join = join(timeout = 50.milliseconds)
        join.run(store, "Lisbon Loft", "", clearPassword = false)
        assertIs<WifiJoin.Step.Failed>(join.step)
    }

    @Test
    fun busyRepliesInBetweenAreSkipped() = runTest {
        val store = store(listOf(CONNECTING, ONLINE))
        var count = 0
        val answer = link.respond
        link.respond = { line ->
            count += 1
            if (line == "status" && count % 2 == 0) "@error device busy" else answer(line)
        }
        val join = join()
        join.run(store, "Lisbon Loft", "", clearPassword = false)
        assertEquals(WifiJoin.Step.Done(approved = true), join.step)
        assertNull(store.s.statusError)
    }

    @Test
    fun linkDropsWhileJoining() = runTest {
        val store = store(listOf(OFFLINE))
        val join = join()
        val run = launch { join.run(store, "Lisbon Loft", "pw", clearPassword = false) }
        runCurrent()
        assertEquals(WifiJoin.Step.Joining, join.step)
        link.emit(LinkEvent.Disconnected(LinkFailure.CONNECTION_LOST))
        run.join()
        val failed = assertIs<WifiJoin.Step.Failed>(join.step)
        assertTrue(failed.message.startsWith("The Bluetooth connection dropped"))
    }

    @Test
    fun resetAbandonsTheRun() = runTest {
        val store = store(listOf(OFFLINE))
        val join = join()
        val run = launch { join.run(store, "Lisbon Loft", "pw", clearPassword = false) }
        runCurrent()
        assertEquals(WifiJoin.Step.Joining, join.step)
        join.reset()
        run.join()
        assertEquals(WifiJoin.Step.Editing, join.step)
        assertFalse(join.isRunning)
    }

    @Test
    fun cancellingAbandonsTheRun() = runTest {
        val store = store(listOf(OFFLINE))
        val join = join()
        val run = launch { join.run(store, "Lisbon Loft", "pw", clearPassword = false) }
        runCurrent()
        assertEquals(WifiJoin.Step.Joining, join.step)
        run.cancel()
        run.join()
        // The run stops without writing an outcome.
        assertEquals(WifiJoin.Step.Joining, join.step)
    }

    @Test
    fun defaultTimings() {
        val join = WifiJoin()
        assertEquals(30.seconds, join.joinTimeout)
        assertEquals(30.seconds, join.hermesTimeout)
        assertEquals(1.seconds, join.pollInterval)
        assertEquals(WifiJoin.Step.Editing, join.step)
    }

    companion object {
        private fun status(network: Boolean, phase: String, paired: Boolean = true) =
            """@status {"name":"Nox","network":$network,"phase":"$phase","paired":$paired}"""

        val OFFLINE = status(network = false, phase = "no_network")
        val CONNECTING = status(network = true, phase = "connecting")
        val HANDSHAKE = status(network = true, phase = "handshake")
        val ONLINE = status(network = true, phase = "online")
    }
}
