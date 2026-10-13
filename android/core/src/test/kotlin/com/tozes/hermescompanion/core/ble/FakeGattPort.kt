package com.tozes.hermescompanion.core.ble

import kotlin.test.fail

/**
 * A [GattPort] the test drives: it records each operation the session
 * starts, fails the test if one starts while another waits for its callback
 * (Android drops it), and the test answers through the session's callbacks.
 */
class FakeGattPort(var bonded: Boolean = true) : GattPort {
    /** Every call, in order: "connect", "mtu 247", "discover", "subscribe", "write status", "bond", "close". */
    val calls = mutableListOf<String>()

    /** The operation waiting for its callback; the test clears it by answering. */
    var inFlight: String? = null
        private set

    var hasService = true
    var startsMtu = true
    var startsBond = true

    val connects: Int get() = calls.count { it == "connect" }
    val closes: Int get() = calls.count { it == "close" }
    val writes: List<String> get() = calls.filter { it.startsWith("write ") }.map { it.removePrefix("write ") }

    private fun start(name: String): Boolean {
        inFlight?.let { fail("$name started while $it was in flight") }
        inFlight = name
        calls += name
        return true
    }

    /** The test answered the operation in flight. */
    fun answered() {
        inFlight = null
    }

    override fun connect(address: String) = start("connect")

    override fun requestMtu(mtu: Int): Boolean {
        if (!startsMtu) return false
        return start("mtu $mtu")
    }

    override fun discoverServices() = start("discover")

    override fun hasGadgetService() = hasService

    override fun enableReplyNotifications() = start("subscribe")

    override fun writeCommand(bytes: ByteArray) = start("write " + bytes.decodeToString())

    override fun isBonded(address: String) = bonded

    override fun createBond(address: String): Boolean {
        calls += "bond"
        return startsBond
    }

    override fun close() {
        inFlight = null
        calls += "close"
    }
}
