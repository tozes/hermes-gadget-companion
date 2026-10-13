package com.tozes.hermescompanion.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.io.File

/**
 * The reply samples in `fixtures/replies/` at the repository root, shared
 * with the Swift tests (see fixtures/README.md). Gradle passes the directory
 * as the `fixtures.replies` system property.
 */
object Fixture {
    val dir: File = File(checkNotNull(System.getProperty("fixtures.replies")) { "run the tests with Gradle: it sets fixtures.replies" })

    /** A reply exactly as the gadget sends it, without the NUL: `reply("error-busy")`. */
    fun reply(name: String): String = File(dir, "$name.txt").readBytes().decodeToString()

    /** Every fixture, by name without `.txt`, in name order. */
    fun all(): List<Pair<String, String>> =
        dir.listFiles { file -> file.extension == "txt" }.orEmpty().sortedBy { it.name }.map { it.nameWithoutExtension to it.readBytes().decodeToString() }
}

/**
 * A [GadgetLink] the test drives by hand: it records what the store asks of
 * the link, the test sends its events with [emit], and replies come from
 * [respond] at once, or wait until the test answers them when [holdReplies]
 * is set. Nothing sleeps.
 */
class FakeGadgetLink(radio: RadioState = RadioState.POWERED_ON) : GadgetLink {
    override var onEvent: ((LinkEvent) -> Unit)? = null
    override var radio: RadioState = radio
        private set

    /** Whether the store has the link scanning. */
    var isScanning = false
        private set

    /** The gadget the store last asked to connect to, until it disconnects. */
    var target: DiscoveredGadget? = null
        private set

    /** Every line the link was asked to write, in order. */
    val sent = mutableListOf<String>()

    /** The reply to each line, unless [holdReplies] is set. */
    var respond: (String) -> String = { "@ok" }
    var holdReplies = false
    private val waiting = mutableListOf<CompletableDeferred<String>>()

    // Driving the store

    fun emit(event: LinkEvent) {
        if (event is LinkEvent.Radio) radio = event.state
        onEvent?.invoke(event)
    }

    /** Lines written and not yet answered (with [holdReplies]). */
    val unanswered: Int get() = waiting.size

    /** Answers the oldest held line. */
    fun answer(text: String) {
        waiting.removeAt(0).complete(text)
    }

    fun fail(error: SendException) {
        waiting.removeAt(0).completeExceptionally(error)
    }

    // GadgetLink

    override fun startScan() {
        isScanning = true
    }

    override fun stopScan() {
        isScanning = false
    }

    override fun connect(gadget: DiscoveredGadget) {
        target = gadget
    }

    override fun disconnect() {
        target = null
    }

    override suspend fun send(line: String): String {
        sent += line
        if (holdReplies) {
            val reply = CompletableDeferred<String>()
            waiting += reply
            return reply.await()
        }
        return respond(line)
    }
}

/** The store's current state. */
val GadgetStore.s: GadgetState get() = state.value

/**
 * A store whose link reached `Connected` to Nox and was asked for the first
 * `status`; `link.respond` should answer it. Its coroutines run in the test's
 * background scope, on virtual time.
 */
fun TestScope.connectedStore(link: FakeGadgetLink): GadgetStore {
    val store = GadgetStore(link, backgroundScope)
    store.connect(nox)
    link.emit(LinkEvent.Connected)
    runCurrent()
    return store
}

/** The real `@status` sample from a gadget (sanitized). */
val statusSample: String get() = Fixture.reply("status-new-gadget")

val nox = DiscoveredGadget(MockGadgetLink.NOX_ID, "Nox", -52)
val kitchen = DiscoveredGadget(MockGadgetLink.KITCHEN_ID, "Kitchen", -74)
