package com.tozes.hermescompanion.core

import java.util.Locale

/** The level and charging state for a battery icon. */
data class BatteryGauge(val percent: Int, val charging: Boolean, val low: Boolean)

/** "84%, charging, USB power", or null when the board doesn't report a battery. */
val GadgetStatus.Power.summary: String?
    get() {
        val percent = batteryPercent
        if (available == false || batteryPresent == false || percent == null) {
            return if (externalPower == true) "USB power, no battery" else null
        }
        val parts = mutableListOf("$percent%")
        if (charging == true) parts += "charging"
        parts += if (externalPower == true) "USB power" else "on battery"
        return parts.joinToString(", ")
    }

/** The device turns its battery red at this level or below, without USB power. */
const val LOW_BATTERY_PERCENT = 10

/** Low as the device shows it: 10% or less, without USB power. */
val GadgetStatus.Power.isLow: Boolean
    get() = (batteryPercent ?: 100) <= LOW_BATTERY_PERCENT && externalPower != true

/** The level and charging state for a battery icon, when there is a battery. */
val GadgetStatus.Power.gauge: BatteryGauge?
    get() {
        val percent = batteryPercent
        if (available == false || batteryPresent == false || percent == null) return null
        return BatteryGauge(percent, charging == true, isLow)
    }

val GadgetStatus.Power.voltage: String?
    get() = batteryMv?.let { String.format(Locale.ROOT, "%.2f V", it / 1000.0) }
