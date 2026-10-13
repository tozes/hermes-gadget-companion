package com.tozes.hermescompanion.core

/** The gadget's setting keys (`get <key>` / `set <key> <value>`). */
enum class SettingKey(val rawValue: String) {
    NAME("name"),
    SERVER("server"),
    TOKEN("token"),
    TALK_MODE("talk_mode"),
    VOLUME("volume"),
    BRIGHTNESS("brightness"),
    SCREEN_TIMEOUT("screen_timeout"),
    BATTERY_DISPLAY("battery_display"),
    WIFI_SSID("wifi_ssid"),
    WIFI_PASSWORD("wifi_pass");

    /** `get` masks these as `<set>`. */
    val isSecret: Boolean get() = this == TOKEN || this == WIFI_PASSWORD

    companion object {
        fun of(raw: String): SettingKey? = entries.firstOrNull { it.rawValue == raw }

        val volumeRange = 0..100
        val brightnessRange = 5..100
        val screenTimeoutRange = 0..3600
    }
}

enum class TalkMode(val rawValue: String) {
    HOLD("hold"),
    TAP("tap");

    val title: String
        get() = when (this) {
            HOLD -> "Hold"
            TAP -> "Tap"
        }

    val explanation: String
        get() = when (this) {
            HOLD -> "Hold the screen while you talk."
            TAP -> "Tap once to start talking, again to stop."
        }

    companion object {
        fun of(raw: String?): TalkMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class BatteryDisplay(val rawValue: String) {
    ICON("icon"),
    PERCENTAGE("percentage"),
    BOTH("both"),
    OFF("off");

    val title: String
        get() = when (this) {
            ICON -> "Icon"
            PERCENTAGE -> "%"
            BOTH -> "Both"
            OFF -> "Off"
        }

    val spokenTitle: String
        get() = when (this) {
            ICON -> "Icon"
            PERCENTAGE -> "Percentage"
            BOTH -> "Icon and percentage"
            OFF -> "Off"
        }
}
