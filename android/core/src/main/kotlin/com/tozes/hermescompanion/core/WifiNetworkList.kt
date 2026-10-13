package com.tozes.hermescompanion.core

import kotlinx.serialization.Serializable

/**
 * The gadget's saved Wi-Fi networks, from `wifi list` (`@wifi`). The first
 * one is the network that last worked, and the gadget tries it first.
 *
 * Only a network's name is required, like [GadgetStatus]: other firmware may
 * leave a field out, and that shouldn't hide the whole list.
 */
@Serializable
data class WifiNetworkList(
    val networks: List<Network> = emptyList(),
    /** How many networks the gadget keeps. */
    val max: Int = DEFAULT_MAX,
    /** The network the gadget is on now, if any. */
    val current: String? = null,
) {
    @Serializable
    data class Network(
        val ssid: String,
        /**
         * The gadget has joined it with its saved password. A network saved
         * with `wifi add` stays untested until the gadget first joins it.
         */
        val tested: Boolean = false,
        /** Saved without a password. */
        val open: Boolean = false,
    )

    val isFull: Boolean get() = networks.size >= max

    fun contains(ssid: String): Boolean = networks.any { it.ssid == ssid }

    companion object {
        /** The firmware keeps five (`WifiNetworks::kMax` in the SDK). */
        const val DEFAULT_MAX = 5
    }
}

/**
 * Console arguments that may hold spaces and quotes (network names and
 * passwords), by the rules of the gadget's console (`hg/console_args.hpp`
 * in the SDK): double quotes group, a backslash keeps the next backslash,
 * quote or space.
 */
object ConsoleArgument {
    fun quoted(value: String): String {
        val out = StringBuilder("\"")
        for (character in value) {
            if (character == '\\' || character == '"') out.append('\\')
            out.append(character)
        }
        return out.append('"').toString()
    }

    /** Splits a line the way the gadget does. */
    fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val argument = StringBuilder()
        var inArgument = false
        var quoted = false
        var escaped = false
        for (character in line) {
            if (escaped) {
                if (character == '\\' || character == '"' || character == ' ') argument.append(character)
                escaped = false
            } else if (character == '\\') {
                escaped = true
                inArgument = true
            } else if (quoted) {
                if (character == '"') {
                    out += argument.toString()
                    argument.clear()
                    inArgument = false
                    quoted = false
                } else {
                    argument.append(character)
                }
            } else if (character == ' ') {
                if (inArgument) out += argument.toString()
                argument.clear()
                inArgument = false
            } else if (character == '"' && !inArgument) {
                inArgument = true
                quoted = true
            } else {
                argument.append(character)
                inArgument = true
            }
        }
        if (inArgument) out += argument.toString()
        return out
    }
}
