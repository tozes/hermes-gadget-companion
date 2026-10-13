package com.tozes.hermescompanion.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.StatusSummary
import com.tozes.hermescompanion.core.gauge
import com.tozes.hermescompanion.core.wifiSummary
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.style.BatteryGlyph
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.GadgetTopBar
import com.tozes.hermescompanion.style.Hero
import com.tozes.hermescompanion.style.HintLine
import com.tozes.hermescompanion.style.NavigationRow
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.StatusIndicator
import com.tozes.hermescompanion.style.Type
import com.tozes.hermescompanion.style.WifiGlyph
import com.tozes.hermescompanion.style.color
import java.util.Date

/** The gadget's name, as the screens under it show it. */
val GadgetState.gadgetName: String get() = status?.name ?: gadget?.name ?: "Gadget"

/** The connected gadget at a glance, with the way to every other screen. */
@Composable
fun HomeScreen(state: GadgetState, navigate: Navigate) {
    val store = LocalStore.current
    val name = state.gadgetName
    val lost = state.connectionLost
    val (refreshing, refresh) = rememberRefresh { store.refreshStatus() }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        GadgetTopBar(
            title = name,
            // Back to the gadget list: leaves this gadget and scans again.
            onBack = store::backToFinding,
            backLabel = "Gadgets",
            backDescription = "Back to gadgets. Disconnects from $name and looks for gadgets nearby.",
        ) { GadgetGlyphs(state) }
        ScreenContent(refreshing = refreshing, onRefresh = refresh) {
            if (lost) ConnectionLostBanner(name, reconnect = store::reconnect, leave = store::backToFinding)
            Header(state, name)
            NextStep(state, name, navigate)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Set up")
                Panel {
                    NavigationRow("Wi-Fi", wifiSummary(state), enabled = !lost) { navigate(Route.WIFI) }
                    PanelDivider()
                    NavigationRow("Settings", "Name, sound, screen, Hermes server", enabled = !lost) { navigate(Route.SETTINGS) }
                }
                Panel {
                    NavigationRow("Advanced", "Status details, console, forget this gadget") { navigate(Route.ADVANCED) }
                }
            }
            HintLine(updatedHint(state))
        }
    }
}

/** The mascot with the connection state under her, as on the find screen. */
@Composable
private fun Header(state: GadgetState, name: String) {
    val summary = if (state.connectionLost) StatusSummary.connectionLost(name) else StatusSummary.of(state.status)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero(summary.headline, summary.indicator, summary.headlineTone.color, summary.detail)
        state.statusError?.let { BodyText("Couldn't refresh: $it", color = Palette.red, style = Type.footnote, centered = true) }
    }
}

@Composable
private fun NextStep(state: GadgetState, name: String, navigate: Navigate) {
    if (state.connectionLost) return
    when (val next = StatusSummary.of(state.status).next) {
        StatusSummary.NextStep.SetUpWifi -> GadgetButton("Set up Wi-Fi") { navigate(Route.WIFI) }
        is StatusSummary.NextStep.ApproveOnHermes -> ApprovalPanel(name, next.code)
        null -> Unit
    }
}

/**
 * The gadget's Wi-Fi and battery, like its own top bar. Whether it's online
 * is the header's to show, once. A dropped link leaves the last status
 * behind: it isn't shown as current.
 */
@Composable
private fun GadgetGlyphs(state: GadgetState) {
    if (state.connectionLost) return
    val network = state.status?.network
    val gauge = state.status?.power?.gauge
    val spoken = listOfNotNull(
        network?.let { if (it) "Wi-Fi connected" else "No Wi-Fi" },
        gauge?.let { "Battery ${it.percent} percent" + if (it.charging) ", charging" else "" },
    ).joinToString(". ")
    Row(
        Modifier.padding(end = 8.dp).clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (network != null) WifiGlyph(network)
        if (gauge != null) BatteryGlyph(gauge.percent, charging = gauge.charging, low = gauge.low)
    }
}

@Composable
private fun updatedHint(state: GadgetState): String {
    val time = state.statusTime ?: return "Pull down to refresh"
    return "Updated " + DateFormat.getTimeFormat(LocalContext.current).format(Date(time))
}

/** Shown on the home screen when the Bluetooth link drops. */
@Composable
private fun ConnectionLostBanner(name: String, reconnect: () -> Unit, leave: () -> Unit) {
    Panel(Modifier.border(1.dp, Palette.redDim, RoundedCornerShape(10.dp))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusIndicator(Indicator.Alert, size = 18.dp)
                PixelText("Connection lost", color = Palette.red, heading = true)
            }
            BodyText("$name is out of reach or turned off. What's below is from the last status.", style = Type.subheadline)
            GadgetButton("Reconnect", onClick = reconnect)
            GadgetButton("Back to gadgets", kind = ButtonKind.SECONDARY, onClick = leave)
        }
    }
}

/**
 * Waiting for Hermes: the command to run on the Hermes host, with the
 * gadget's pairing code, as the gadget's own pairing screen shows it.
 */
@Composable
private fun ApprovalPanel(name: String, code: String?) {
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelText(code?.let { "Pairing code $it" } ?: "Waiting for approval", color = Palette.accent, wraps = true)
            BodyText("On the Hermes host, run:", style = Type.subheadline)
            SelectionContainer(
                Modifier.fillMaxWidth().background(Palette.background, RoundedCornerShape(6.dp)).padding(12.dp),
            ) {
                Text("hermes pairing approve gadget ${code ?: "<code>"}", color = Palette.text, style = Type.callout.merge(Type.mono))
            }
            BodyText(
                if (code == null) "The code is on $name's screen." else "$name moves on by itself once it's approved. Pull down to check.",
                style = Type.footnote,
            )
        }
    }
}
