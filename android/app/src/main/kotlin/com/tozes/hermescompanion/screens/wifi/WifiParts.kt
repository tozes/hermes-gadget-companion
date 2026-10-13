package com.tozes.hermescompanion.screens.wifi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.core.WifiJoin
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.HeaderBand
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.StatusIndicator
import com.tozes.hermescompanion.style.Type

/**
 * The header while a Wi-Fi join or save runs or has finished. Each screen
 * draws its own header while the user is still editing.
 */
@Composable
fun WifiJoinHeader(step: WifiJoin.Step, ssid: String, name: String) {
    when (step) {
        WifiJoin.Step.Editing -> Unit
        WifiJoin.Step.Saving -> HeaderBand(Indicator.Spinner(Tone.ACCENT), "Saving", Palette.accent, "Sending the network to $name.")
        WifiJoin.Step.Joining -> HeaderBand(Indicator.Spinner(Tone.ACCENT), "Joining $ssid", Palette.accent, "This can take up to half a minute.")
        WifiJoin.Step.ReachingHermes -> HeaderBand(
            Indicator.Spinner(Tone.ACCENT), "Reaching Hermes", Palette.accent, "On Wi-Fi. Connecting to the Hermes server.",
        )
        is WifiJoin.Step.Done -> if (step.approved) {
            HeaderBand(Indicator.Pulse(Tone.GREEN), "Online", detail = "$name is on $ssid and connected to Hermes.")
        } else {
            HeaderBand(
                Indicator.Ring(Tone.ACCENT), "Waiting for approval", Palette.accent,
                "$name is on $ssid and reached Hermes. Approve it on the Hermes host to finish; the home screen shows the command.",
            )
        }
        WifiJoin.Step.NoHermes -> HeaderBand(
            Indicator.Alert, "Hermes isn't answering", Palette.red,
            "$name is on $ssid, but the Hermes server didn't answer. Check the server address in Settings.",
        )
        is WifiJoin.Step.Failed -> HeaderBand(Indicator.Alert, "Couldn't join", Palette.red, step.message)
        WifiJoin.Step.Saved -> HeaderBand(
            Indicator.Dot(Tone.GREEN), "Saved",
            detail = "$name can join $ssid when it loses its current network. It shows as not tested until it first joins.",
        )
    }
}

private enum class Mark { WAITING, ACTIVE, DONE, FAILED }

/** The three stages of joining, each with the device's indicator for its state. */
@Composable
fun WifiProgress(step: WifiJoin.Step) {
    // Which stage is current, and whether it failed.
    val (current, failed) = when (step) {
        WifiJoin.Step.Editing, WifiJoin.Step.Saving -> 0 to false
        WifiJoin.Step.Joining -> 1 to false
        is WifiJoin.Step.Failed -> 1 to true
        WifiJoin.Step.ReachingHermes -> 2 to false
        WifiJoin.Step.NoHermes -> 2 to true
        is WifiJoin.Step.Done, WifiJoin.Step.Saved -> 3 to false
    }
    fun mark(stage: Int) = when {
        stage < current -> Mark.DONE
        stage > current -> Mark.WAITING
        failed -> Mark.FAILED
        else -> Mark.ACTIVE
    }
    Panel {
        ProgressRow("Save the network", mark(0))
        PanelDivider()
        ProgressRow("Join Wi-Fi", mark(1))
        PanelDivider()
        ProgressRow("Reach Hermes", mark(2))
    }
}

@Composable
private fun ProgressRow(title: String, mark: Mark) {
    val spoken = when (mark) {
        Mark.WAITING -> "Not started"
        Mark.ACTIVE -> "In progress"
        Mark.DONE -> "Done"
        Mark.FAILED -> "Failed"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = "$title, $spoken" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (mark) {
            Mark.WAITING -> StatusIndicator(Indicator.Dot(Tone.FAINT), size = 18.dp)
            Mark.ACTIVE -> StatusIndicator(Indicator.Spinner(Tone.ACCENT), size = 18.dp)
            Mark.DONE -> StatusIndicator(Indicator.Dot(Tone.GREEN), size = 18.dp)
            Mark.FAILED -> StatusIndicator(Indicator.Alert, size = 18.dp)
        }
        PixelText(
            title,
            color = when (mark) {
                Mark.WAITING -> Palette.dim
                Mark.FAILED -> Palette.red
                else -> Palette.text
            },
        )
    }
}

/** A Wi-Fi form field: a pixel label over the input, and the gadget's refusal under it. */
@Composable
fun WifiField(label: String, error: String?, input: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelText(label.uppercase(), color = Palette.dim, modifier = Modifier.clearAndSetSemantics {})
        input()
        if (error != null) {
            BodyText(error, Modifier.clearAndSetSemantics { contentDescription = "Error: $error" }, color = Palette.red, style = Type.footnote)
        }
    }
}

/** "Open network, no password": a switch in the gadget's colours. */
@Composable
fun OpenNetworkSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Open network, no password", Modifier.weight(1f), color = Palette.text, style = Type.body)
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.accentInk,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.dim,
                uncheckedTrackColor = Palette.background,
                uncheckedBorderColor = Palette.faint,
            ),
        )
    }
}
