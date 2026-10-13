package com.tozes.hermescompanion.core

import kotlinx.coroutines.test.TestScope
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

/**
 * Saved Wi-Fi networks: the `wifi` command's replies, the store's calls,
 * joining one, and the mock that acts out the firmware.
 */
class WifiNetworksTest {
    private val link = FakeGadgetLink()
    private val now = Duration.ZERO
    private val twoSaved = Fixture.reply("wifi-list")

    // Replies and arguments

    @Test
    fun parsesTheList() {
        val list = assertIs<Reply.Wifi>(Reply.parse(twoSaved)).list
        assertEquals(
            listOf(
                WifiNetworkList.Network("Lisbon Loft", tested = true, open = false),
                WifiNetworkList.Network("""Pocket "5G"""", tested = false, open = true),
            ),
            list.networks,
        )
        assertEquals(5, list.max)
        assertEquals("Lisbon Loft", list.current)
        assertFalse(list.isFull)
        assertTrue(list.contains("Lisbon Loft"))
    }

    @Test
    fun listWithoutCurrentNetwork() {
        val list = assertIs<Reply.Wifi>(Reply.parse(Fixture.reply("wifi-none"))).list
        assertNull(list.current)
        assertTrue(list.networks.isEmpty())
    }

    @Test
    fun missingFieldsGetDefaults() {
        val list = assertIs<Reply.Wifi>(Reply.parse(Fixture.reply("wifi-minimal"))).list
        assertEquals(listOf(WifiNetworkList.Network("A", tested = false, open = false)), list.networks)
        assertEquals(5, list.max)
        assertNull(list.current)
        val empty = assertIs<Reply.Wifi>(Reply.parse("@wifi {}"), "an empty object is not a wifi list").list
        assertTrue(empty.networks.isEmpty())
    }

    @Test
    fun aNetworkWithoutANameIsNotAList() {
        val other = assertIs<Reply.Other>(Reply.parse("""@wifi {"networks":[{"tested":true}],"max":5}"""), "decoded a network with no name")
        assertEquals("wifi", other.tag)
    }

    /** The same cases as the firmware's `console args` test. */
    @Test
    fun argumentsSplitLikeTheGadget() {
        assertEquals(listOf("add", "Home", "secret"), ConsoleArgument.split("add  Home   secret"))
        assertEquals(listOf("add", "My Home", "pass word"), ConsoleArgument.split("""add "My Home" "pass word""""))
        assertEquals(listOf("add", "", """a b"c""", """d\e"""), ConsoleArgument.split("""add "" a\ b\"c d\\e"""))
        assertEquals(listOf("""x"y"""", "a", "b"), ConsoleArgument.split("""x"y" "a"b"""))
        for (value in listOf("", "plain", "two  spaces", """q"u\o"te\""", " edge ")) {
            assertEquals(listOf(value), ConsoleArgument.split(ConsoleArgument.quoted(value)))
        }
    }

    // The store

    /** A store connected to Nox: `status` answers [status], [respond] the rest. */
    private fun TestScope.store(status: String = statusSample, respond: (String) -> String): GadgetStore {
        link.respond = { line -> if (line == "status") status else respond(line) }
        return connectedStore(link)
    }

    @Test
    fun loadsTheList() = runTest {
        val store = store { twoSaved }
        assertNull(store.loadWifiNetworks())
        assertEquals(2, store.s.wifiNetworks?.networks?.size)
        assertFalse(store.s.keepsOneNetwork)
    }

    @Test
    fun olderFirmwareKeepsOneNetwork() = runTest {
        val store = store { Fixture.reply("error-unknown-command") }
        assertNull(store.loadWifiNetworks())
        assertTrue(store.s.keepsOneNetwork)
        assertNull(store.s.wifiNetworks)
    }

    @Test
    fun addQuotesNameAndPasswordThenRereadsTheList() = runTest {
        val store = store { line -> if (line.startsWith("wifi add")) "@ok wifi add" else twoSaved }
        assertNull(store.addWifi("""Pocket "5G"""", password = """pa\ss word"""))
        assertEquals(listOf("""wifi add "Pocket \"5G\"" "pa\\ss word"""", "wifi list"), link.sent.takeLast(2))
        assertEquals(2, store.s.wifiNetworks?.networks?.size)
        // An open network: no password argument at all.
        store.addWifi("Cafe", password = "")
        assertEquals(listOf("""wifi add "Cafe"""", "wifi list"), link.sent.takeLast(2))
    }

    @Test
    fun refusalsComeBackAsMessages() = runTest {
        val store = store { "@error no saved network Cafe" }
        assertEquals("no saved network Cafe", store.joinWifi("Cafe"))
        assertEquals("no saved network Cafe", store.forgetWifi("Cafe"))
        assertEquals(listOf("""wifi join "Cafe"""", """wifi forget "Cafe""""), link.sent.takeLast(2))
    }

    @Test
    fun aNewConnectionForgetsTheList() = runTest {
        val store = store { twoSaved }
        store.loadWifiNetworks()
        store.backToFinding()
        assertNull(store.s.wifiNetworks)
    }

    // Joining and saving

    private fun TestScope.join(timeout: Duration = 2.seconds) =
        WifiJoin(joinTimeout = timeout, hermesTimeout = timeout, pollInterval = 1.milliseconds, timeSource = testScheduler.timeSource)

    private fun list(current: String?): String {
        val field = current?.let { ""","current":"$it"""" } ?: ""
        return """@wifi {"networks":[{"ssid":"Pocket","tested":false,"open":false},{"ssid":"Lisbon Loft","tested":true,"open":false}],"max":5$field}"""
    }

    private val online = Fixture.reply("status-online")

    @Test
    fun joinsANewNetworkUntilItIsCurrent() = runTest {
        // The gadget stays on its old network for a moment after `wifi join`.
        val lists = mutableListOf(list("Lisbon Loft"), list("Lisbon Loft"), list(null), list("Pocket"))
        val store = store(status = online) { line ->
            when {
                line.startsWith("wifi add") -> "@ok wifi add"
                line.startsWith("wifi join") -> "@ok wifi join"
                lists.size > 1 -> lists.removeAt(0)
                else -> lists[0]
            }
        }
        val join = join()
        join.join(store, "Pocket", newPassword = "hotspot-pass")
        assertEquals(WifiJoin.Step.Done(approved = true), join.step)
        assertTrue("""wifi add "Pocket" "hotspot-pass"""" in link.sent)
        assertTrue("""wifi join "Pocket"""" in link.sent)
        assertEquals("Pocket", store.s.wifiNetworks?.current)
        // It polled past the lists that still showed the old network.
        assertTrue(link.sent.count { it == "wifi list" } >= 4)
    }

    @Test
    fun joinsASavedNetworkWithoutSavingItAgain() = runTest {
        val store = store(status = online) { line -> if (line.startsWith("wifi join")) "@ok wifi join" else list("Pocket") }
        val join = join()
        join.join(store, "Pocket", newPassword = null)
        assertEquals(WifiJoin.Step.Done(approved = true), join.step)
        assertFalse(link.sent.any { it.startsWith("wifi add") })
    }

    @Test
    fun aJoinThatNeverLandsSaysTheGadgetGoesBack() = runTest {
        val store = store { line -> if (line.startsWith("wifi join")) "@ok wifi join" else list("Lisbon Loft") }
        store.loadWifiNetworks()
        val join = join(timeout = 50.milliseconds)
        join.join(store, "Pocket", newPassword = null)
        val failed = assertIs<WifiJoin.Step.Failed>(join.step, "expected a failure")
        assertTrue(failed.message.startsWith("Pocket didn't connect."))
        assertTrue(failed.message.endsWith("It goes back to its other saved networks."))
    }

    @Test
    fun aRefusedPasswordShowsUnderThePasswordField() = runTest {
        val store = store { Fixture.reply("error-wifi-password") }
        val join = join()
        join.join(store, "Pocket", newPassword = "short")
        assertEquals(WifiJoin.Step.Editing, join.step)
        assertTrue(join.error(WifiJoin.Field.PASSWORD)?.startsWith("Use an 8..63") == true)
        assertNull(join.error(WifiJoin.Field.SSID))
        assertFalse(link.sent.any { it.startsWith("wifi join") })
    }

    @Test
    fun savesForLater() = runTest {
        val store = store { line -> if (line.startsWith("wifi add")) "@ok wifi add" else list("Lisbon Loft") }
        val join = join()
        join.save(store, "Pocket", password = "hotspot-pass")
        assertEquals(WifiJoin.Step.Saved, join.step)
        assertFalse(join.isRunning)
        assertFalse(link.sent.any { it.startsWith("wifi join") })
        assertEquals(true, store.s.wifiNetworks?.contains("Pocket"))
    }

    @Test
    fun aFullListIsRefusedUnderTheName() = runTest {
        val store = store { Fixture.reply("error-wifi-full") }
        val join = join()
        join.save(store, "Sixth", password = "")
        assertEquals(WifiJoin.Step.Editing, join.step)
        assertEquals("5 networks saved; forget one first", join.error(WifiJoin.Field.SSID))
    }

    // The mock

    @Test
    fun mockListsAddsJoinsAndForgets() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        val start = assertIs<Reply.Wifi>(Reply.parse(device.handle("wifi", now)), "no list").list
        assertEquals(listOf("Lisbon Loft", "Pocket Hotspot"), start.networks.map { it.ssid })
        assertEquals("Lisbon Loft", start.current)
        assertEquals("@ok wifi add", device.handle("""wifi add "Cafe Wi-Fi" "espresso1"""", now))
        assertTrue(device.network) // saving a network doesn't leave the current one
        assertEquals("@ok wifi join", device.handle("""wifi join "Cafe Wi-Fi"""", now))
        assertFalse(device.network)
        assertEquals("Cafe Wi-Fi", device.settings["wifi_ssid"])
        device.tick(now + 3.seconds)
        val joined = assertIs<Reply.Wifi>(Reply.parse(device.handle("wifi list", now + 3.seconds)), "no list").list
        assertEquals("Cafe Wi-Fi", joined.current)
        assertEquals(WifiNetworkList.Network("Cafe Wi-Fi", tested = true, open = false), joined.networks.first())
        assertEquals("@ok wifi forget", device.handle("""wifi forget "Pocket Hotspot"""", now))
        assertEquals(listOf("Cafe Wi-Fi", "Lisbon Loft"), device.networks.map { it.ssid })
    }

    @Test
    fun mockRefusesLikeTheFirmware() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertTrue(device.handle("wifi add Home short", now).startsWith("@error Use an 8..63"))
        assertEquals(Fixture.reply("error-wifi-password"), device.handle("wifi add Home short", now))
        assertTrue(device.handle("""wifi add """"", now).startsWith("@error Network name"))
        assertEquals("@error no saved network Elsewhere", device.handle("wifi join Elsewhere", now))
        for (i in 0 until 3) assertEquals("@ok wifi add", device.handle("wifi add Net$i", now))
        assertEquals(Fixture.reply("error-wifi-full"), device.handle("wifi add Net3", now))
        assertTrue(device.handle("wifi add", now).startsWith("@error usage"))
        assertTrue(device.handle("wifi rename a", now).startsWith("@error usage"))
    }

    @Test
    fun mockSetWifiSsidPutsANetworkFirstAndKeepsTheOthers() {
        val device = MockDevice.nox(MockScenario.ONLINE)
        assertEquals("@ok wifi_ssid", device.handle("set wifi_ssid Office", now))
        assertEquals("@ok wifi_pass", device.handle("set wifi_pass password2", now))
        assertEquals(listOf("Office", "Lisbon Loft", "Pocket Hotspot"), device.networks.map { it.ssid })
        assertEquals("password2", device.networks[0].password)
        assertFalse(device.network)
        assertEquals("@ok wifi_ssid", device.handle("set wifi_ssid", now))
        assertEquals(listOf("Lisbon Loft", "Pocket Hotspot"), device.networks.map { it.ssid })
        assertEquals("Lisbon Loft", device.settings["wifi_ssid"])
    }
}
