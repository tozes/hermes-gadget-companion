package com.tozes.hermescompanion.core

import java.io.ByteArrayOutputStream

/** The gadget's Bluetooth service (SDK `docs/protocol.md`, "Phone app over Bluetooth"). */
object GadgetService {
    const val SERVICE = "dc530001-7a2a-400f-b8f6-e87185576192"

    /** Write with response: one console line. Needs a paired (encrypted) link. */
    const val COMMAND = "dc530002-7a2a-400f-b8f6-e87185576192"

    /** Notify: the reply, in pieces, ending with a 0x00 byte. */
    const val REPLY = "dc530003-7a2a-400f-b8f6-e87185576192"

    /** The standard Client Characteristic Configuration descriptor, written to subscribe. */
    const val CLIENT_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
}

/**
 * Joins reply notifications into whole replies: each reply ends with one NUL
 * byte, and one notification may carry the end of a reply and the start of
 * the next. Bytes are kept until the NUL, so a character split across
 * notifications decodes whole.
 */
class ReplyAssembler {
    private val buffer = ByteArrayOutputStream()

    /** Adds a notification's bytes; returns the replies it completed, in order. */
    fun append(chunk: ByteArray): List<String> {
        val replies = mutableListOf<String>()
        for (byte in chunk) {
            if (byte == 0.toByte()) {
                replies += buffer.toByteArray().decodeToString()
                buffer.reset()
            } else {
                buffer.write(byte.toInt())
            }
        }
        return replies
    }

    /** Drops a partial reply, e.g. when the link changes. */
    fun reset() {
        buffer.reset()
    }

    val hasPartialReply: Boolean get() = buffer.size() > 0
}
