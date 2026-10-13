package com.tozes.hermescompanion.core

import kotlinx.serialization.json.Json

/**
 * One reply from the gadget's console. Machine-readable replies start with
 * `@` and a tag; JSON tags are decoded, anything else is kept as text.
 */
sealed interface Reply {
    data class Status(val status: GadgetStatus) : Reply
    data class Value(val value: SettingValue) : Reply
    data class Ok(val message: String) : Reply
    data class Error(val message: String) : Reply
    data class Phone(val phone: PhoneState) : Reply
    data class Wifi(val list: WifiNetworkList) : Reply

    /** `@help`, `@diag`, a log dump, or JSON that didn't decode. */
    data class Other(val tag: String, val body: String) : Reply

    val isError: Boolean get() = this is Error

    /** The message of an `@error`, or a description of a reply that wasn't expected. */
    val failureMessage: String?
        get() = when (this) {
            is Error -> message
            is Other -> if (tag.isEmpty()) "Unexpected reply: $body" else "Unexpected reply: @$tag"
            else -> null
        }

    companion object {
        /**
         * Lenient like the Swift decoder: unknown keys are ignored, missing
         * optional fields stay null, and a field that is required (a setting's
         * `key` and `value`, a network's `ssid`) or of the wrong type makes the
         * reply text.
         */
        internal val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun parse(text: String): Reply {
            val trimmed = text.trim()
            if (!trimmed.startsWith("@")) return Other("", trimmed)
            val head = trimmed.substring(1)
            val space = head.indexOf(' ').let { if (it < 0) head.length else it }
            val tag = head.substring(0, space)
            val body = head.substring(space).trimSpaces()
            return when (tag) {
                "ok" -> Ok(body)
                "error" -> Error(body)
                "status" -> decode<GadgetStatus>(body)?.let(::Status) ?: Other(tag, body)
                "value" -> decode<SettingValue>(body)?.let(::Value) ?: Other(tag, body)
                "phone" -> decode<PhoneState>(body)?.let(::Phone) ?: Other(tag, body)
                "wifi" -> decode<WifiNetworkList>(body)?.let(::Wifi) ?: Other(tag, body)
                else -> Other(tag, body)
            }
        }

        private inline fun <reified T> decode(body: String): T? =
            try {
                json.decodeFromString<T>(body)
            } catch (_: IllegalArgumentException) {
                // SerializationException is an IllegalArgumentException.
                null
            }
    }
}

/** Trims spaces and tabs, but not newlines, like Swift's `.whitespaces`. */
internal fun String.trimSpaces(): String = trim { it == ' ' || it == '\t' || Character.getType(it) == Character.SPACE_SEPARATOR.toInt() }
