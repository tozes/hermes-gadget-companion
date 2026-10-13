package com.tozes.hermescompanion.core

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every shared reply sample in `fixtures/replies/` (see fixtures/README.md). */
class FixturesTest {
    @Test
    fun theFixturesAreThere() {
        val names = Fixture.all().map { it.first }
        assertTrue(names.size >= 20, "found $names in ${Fixture.dir}")
        assertTrue("status-new-gadget" in names)
    }

    /** Each file parses to the kind of reply its name says. */
    @TestFactory
    fun everyFixtureParsesAsItsName(): List<DynamicTest> = Fixture.all().map { (name, text) ->
        DynamicTest.dynamicTest(name) {
            val reply = Reply.parse(text)
            when {
                name.startsWith("status-") -> assertIs<Reply.Status>(reply)
                name.startsWith("value-") -> assertIs<Reply.Value>(reply)
                name.startsWith("ok-") -> assertIs<Reply.Ok>(reply)
                name.startsWith("error-") -> assertIs<Reply.Error>(reply)
                name.startsWith("phone") -> assertIs<Reply.Phone>(reply)
                name.startsWith("wifi-") -> assertIs<Reply.Wifi>(reply)
                name == "help" || name == "diag" -> assertEquals(name, assertIs<Reply.Other>(reply).tag)
                name == "diag-log" -> assertEquals("", assertIs<Reply.Other>(reply).tag)
                else -> error("$name: name it for its tag, or add it here")
            }
        }
    }

    /** As the gadget sends them: no NUL inside, no trailing newline. */
    @TestFactory
    fun everyFixtureIsOneReply(): List<DynamicTest> = Fixture.all().map { (name, text) ->
        DynamicTest.dynamicTest(name) {
            assertFalse('\u0000' in text)
            assertFalse(text.endsWith("\n"))
            // Through the assembler in small notifications, it comes back whole.
            val assembler = ReplyAssembler()
            val replies = (text.encodeToByteArray() + 0).toList().chunked(20).flatMap { assembler.append(it.toByteArray()) }
            assertEquals(listOf(text), replies)
        }
    }

    /** Only the made-up values: no real hosts or device IDs. */
    @TestFactory
    fun everyFixtureIsMadeUp(): List<DynamicTest> = Fixture.all().map { (name, text) ->
        DynamicTest.dynamicTest(name) {
            for (host in Regex("""wss?://([^:/"]+)""").findAll(text).map { it.groupValues[1] }) {
                assertTrue(host.endsWith("example.com"), "$name: $host")
            }
            for (id in Regex("""hg-[0-9a-f]{16}""").findAll(text).map { it.value }) {
                assertTrue(id in listOf("hg-5a1c9e2f7b3d4086", "hg-0c41aa17d2e94b10"), "$name: $id")
            }
        }
    }
}
