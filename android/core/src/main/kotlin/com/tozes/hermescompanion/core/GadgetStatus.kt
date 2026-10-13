package com.tozes.hermescompanion.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The JSON of `@status`. Every field is optional and unknown fields are
 * ignored, so older and newer firmware both decode.
 */
@Serializable
data class GadgetStatus(
    @SerialName("device_id") val deviceId: String? = null,
    val name: String? = null,
    val board: String? = null,
    val firmware: String? = null,
    val phase: Phase? = null,
    val screen: String? = null,
    /** Approved by Hermes (not the phone pairing). */
    val paired: Boolean? = null,
    /** Shown while the device waits for approval. */
    @SerialName("pairing_code") val pairingCode: String? = null,
    val server: String? = null,
    val network: Boolean? = null,
    @SerialName("display_sleeping") val displaySleeping: Boolean? = null,
    val power: Power? = null,
    val phone: PhoneState? = null,
) {
    @Serializable(with = PhaseSerializer::class)
    sealed interface Phase {
        val rawValue: String

        data object Boot : Phase { override val rawValue = "boot" }
        data object NoNetwork : Phase { override val rawValue = "no_network" }
        data object Connecting : Phase { override val rawValue = "connecting" }
        data object Handshake : Phase { override val rawValue = "handshake" }
        data object Online : Phase { override val rawValue = "online" }
        data class Other(override val rawValue: String) : Phase

        companion object {
            fun of(raw: String): Phase = when (raw) {
                "boot" -> Boot
                "no_network" -> NoNetwork
                "connecting" -> Connecting
                "handshake" -> Handshake
                "online" -> Online
                else -> Other(raw)
            }
        }
    }

    @Serializable
    data class Power(
        val available: Boolean? = null,
        @SerialName("battery_present") val batteryPresent: Boolean? = null,
        @SerialName("battery_mv") val batteryMv: Int? = null,
        @SerialName("battery_percent") val batteryPercent: Int? = null,
        val charging: Boolean? = null,
        @SerialName("external_power") val externalPower: Boolean? = null,
    )
}

internal object PhaseSerializer : KSerializer<GadgetStatus.Phase> {
    override val descriptor = PrimitiveSerialDescriptor("GadgetStatus.Phase", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = GadgetStatus.Phase.of(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: GadgetStatus.Phase) = encoder.encodeString(value.rawValue)
}

/** The phone side of the gadget's Bluetooth: `@phone {...}` and `status.phone`. */
@Serializable
data class PhoneState(
    /** How many phones are paired (up to three). */
    val paired: Int? = null,
    val connected: Boolean? = null,
    /** Whether Phone app pairing is open on the gadget. */
    val pairing: Boolean? = null,
)

/** `@value {"key": ..., "value": ...}`. Secrets come back as `<set>`. */
@Serializable
data class SettingValue(val key: String, val value: String) {
    companion object {
        const val MASKED_SECRET = "<set>"
    }
}
