package com.tozes.hermescompanion.core

/** One line of console scrollback. */
data class ConsoleEntry(
    val id: Int,
    val kind: Kind,
    val text: String,
    /** Typed in the console, rather than sent by the app's own screens. */
    val typed: Boolean,
) {
    enum class Kind {
        /** A line sent to the gadget. */
        COMMAND,
        REPLY,

        /** A note from the app, such as a dropped link. */
        NOTE,
    }
}
