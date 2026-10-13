package com.tozes.hermescompanion.core

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReplyTest {
    @Test
    fun status() {
        val status = assertIs<Reply.Status>(Reply.parse(statusSample)).status
        assertEquals("Nox", status.name)
        assertEquals(GadgetStatus.Phase.NoNetwork, status.phase)
        assertEquals(PhoneState(paired = 1, connected = true, pairing = false), status.phone)
    }

    @Test
    fun value() {
        assertEquals(Reply.Value(SettingValue("volume", "70")), Reply.parse(Fixture.reply("value-volume")))
    }

    @Test
    fun maskedSecretValue() {
        assertEquals(Reply.Value(SettingValue("wifi_pass", SettingValue.MASKED_SECRET)), Reply.parse(Fixture.reply("value-secret")))
    }

    @Test
    fun ok() {
        assertEquals(Reply.Ok("volume"), Reply.parse(Fixture.reply("ok-volume")))
    }

    @Test
    fun errors() {
        val validation = Reply.parse(Fixture.reply("error-screen-timeout"))
        assertEquals(Reply.Error("screen_timeout must be 0..3600 seconds"), validation)
        assertTrue(validation.isError)
        assertEquals("screen_timeout must be 0..3600 seconds", validation.failureMessage)
        assertEquals(Reply.Error("device busy"), Reply.parse(Fixture.reply("error-busy")))
    }

    @Test
    fun phone() {
        assertEquals(Reply.Phone(PhoneState(paired = 1, connected = true, pairing = false)), Reply.parse(Fixture.reply("phone")))
    }

    @Test
    fun unknownJsonFieldsAreIgnored() {
        assertEquals(Reply.Phone(PhoneState(paired = 2, connected = false, pairing = true)), Reply.parse(Fixture.reply("phone-extra-fields")))
        assertEquals(Reply.Value(SettingValue("name", "Nox")), Reply.parse(Fixture.reply("value-extra-fields")))
    }

    @Test
    fun trailingNewlineAndSpacesAreTrimmed() {
        assertEquals(Reply.Ok("volume"), Reply.parse("  @ok volume \r\n"))
    }

    @Test
    fun helpAndDiagAreKeptAsText() {
        val help = Fixture.reply("help")
        assertEquals(Reply.Other("help", help.removePrefix("@help ")), Reply.parse(help))
        assertEquals("Unexpected reply: @help", Reply.parse(help).failureMessage)
        assertEquals(Reply.Other("diag", """{"build":"0.2.0","heap_free":182344}"""), Reply.parse(Fixture.reply("diag")))
    }

    @Test
    fun plainTextIsKept() {
        val reply = Reply.parse("hello there")
        assertEquals(Reply.Other("", "hello there"), reply)
        assertEquals("Unexpected reply: hello there", reply.failureMessage)
        assertFalse(reply.isError)
    }

    @Test
    fun logDumpIsKeptWhole() {
        val log = Fixture.reply("diag-log")
        assertTrue(log.endsWith("\n@log end"))
        assertEquals(Reply.Other("", log), Reply.parse(log))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "@status {not json",
            """@status ["online"]""",
            "@value {}",
            """@value {"key":"volume"}""",
            """@phone {"paired":"one"}""",
            "@status",
        ],
    )
    fun malformedJsonIsKeptAsText(text: String) {
        val other = assertIs<Reply.Other>(Reply.parse(text), "decoded $text")
        assertTrue(other.tag in listOf("status", "value", "phone"))
        assertEquals("Unexpected reply: @${other.tag}", Reply.parse(text).failureMessage)
    }

    @Test
    fun emptyOkAndError() {
        assertEquals(Reply.Ok(""), Reply.parse("@ok"))
        assertEquals(Reply.Error(""), Reply.parse("@error"))
    }
}
