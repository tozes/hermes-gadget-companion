package com.tozes.hermescompanion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyAssemblerTest {
    private fun bytes(text: String, nul: Boolean = true): ByteArray = text.encodeToByteArray() + if (nul) byteArrayOf(0) else byteArrayOf()

    @Test
    fun wholeReplyInOneChunk() {
        val assembler = ReplyAssembler()
        assertEquals(listOf("@ok volume"), assembler.append(bytes("@ok volume")))
        assertFalse(assembler.hasPartialReply)
    }

    @Test
    fun piecesAreJoinedAtTheNul() {
        val assembler = ReplyAssembler()
        assertEquals(emptyList(), assembler.append(bytes("@status {\"name\":", nul = false)))
        assertTrue(assembler.hasPartialReply)
        assertEquals(emptyList(), assembler.append(bytes("\"Nox\"", nul = false)))
        assertEquals(listOf("""@status {"name":"Nox"}"""), assembler.append(bytes("}")))
        assertFalse(assembler.hasPartialReply)
    }

    @Test
    fun endOfOneReplyAndStartOfTheNext() {
        val assembler = ReplyAssembler()
        assertEquals(emptyList(), assembler.append(bytes("@ok vol", nul = false)))
        assertEquals(listOf("@ok volume"), assembler.append(bytes("ume") + bytes("@error dev", nul = false)))
        assertEquals(listOf("@error device busy"), assembler.append(bytes("ice busy")))
    }

    @Test
    fun severalRepliesInOneChunk() {
        val assembler = ReplyAssembler()
        assertEquals(listOf("@ok a", "@ok b", "@ok c"), assembler.append(bytes("@ok a") + bytes("@ok b") + bytes("@ok c")))
    }

    @Test
    fun emptyReply() {
        val assembler = ReplyAssembler()
        assertEquals(listOf(""), assembler.append(byteArrayOf(0)))
        assertEquals(listOf("", ""), assembler.append(byteArrayOf(0, 0)))
    }

    @Test
    fun emptyChunk() {
        val assembler = ReplyAssembler()
        assertEquals(emptyList(), assembler.append(byteArrayOf()))
        assertFalse(assembler.hasPartialReply)
    }

    @Test
    fun multiLineReply() {
        val assembler = ReplyAssembler()
        val log = Fixture.reply("diag-log")
        val data = bytes(log)
        assertEquals(emptyList(), assembler.append(data.copyOfRange(0, 20)))
        assertEquals(listOf(log), assembler.append(data.copyOfRange(20, data.size)))
    }

    @Test
    fun utf8SplitAcrossChunks() {
        val assembler = ReplyAssembler()
        val reply = "@value {\"key\":\"name\",\"value\":\"Café ☕\"}"
        val data = bytes(reply)
        // Split inside "é" (2 bytes) and inside "☕" (3 bytes).
        val e = data.indexOf(0xC3.toByte())
        val cup = data.indexOf(0xE2.toByte())
        val chunks = listOf(data.copyOfRange(0, e + 1), data.copyOfRange(e + 1, cup + 2), data.copyOfRange(cup + 2, data.size))
        val replies = chunks.flatMap { assembler.append(it) }
        assertEquals(listOf(reply), replies)
    }

    @Test
    fun resetDropsAPartialReply() {
        val assembler = ReplyAssembler()
        assertEquals(emptyList(), assembler.append(bytes("@status {\"na", nul = false)))
        assembler.reset()
        assertFalse(assembler.hasPartialReply)
        assertEquals(listOf("@ok volume"), assembler.append(bytes("@ok volume")))
    }
}
