package com.tozes.hermescompanion.screens.find

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.tozes.hermescompanion.core.DiscoveredGadget
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.RadioState
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.link.BluetoothGadgetLink
import com.tozes.hermescompanion.link.BluetoothPermissions
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.screens.common.DemoNote
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.Dot
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.Hero
import com.tozes.hermescompanion.style.HintLine
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PanelRow
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.SignalBars
import com.tozes.hermescompanion.style.StatusIndicator
import com.tozes.hermescompanion.style.TopBar
import com.tozes.hermescompanion.style.Type

/** Find a gadget: scan, list what's nearby, and explain how to make one appear. */
@Composable
fun FindScreen(state: GadgetState) {
    val store = LocalStore.current
    val context = LocalContext.current
    val bluetooth = store.link as? BluetoothGadgetLink

    // Ask for the Bluetooth permissions here, where they're needed; a refusal
    // shows as "Bluetooth not allowed", with the way to Settings.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        bluetooth?.permissionResult(results.values.all { it })
    }
    LaunchedEffect(bluetooth) {
        if (bluetooth != null && !bluetooth.hasPermissions()) request.launch(BluetoothPermissions.required)
    }
    // Back from Settings, the permission or the radio may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { bluetooth?.refresh() }
    LifecycleStartEffect(Unit) {
        store.startScan()
        onStopOrDispose { store.stopScan() }
    }

    Column(Modifier.fillMaxSize().background(Palette.background)) {
        TopBar(title = "Hermes", linkColor = radioColor(state), linkLabel = radioLabel(state), spokenState = radioSpoken(state))
        ScreenContent(Modifier.weight(1f), spacing = 28, bottomInsets = false) {
            FindHero(state, locationOff = bluetooth != null && BluetoothPermissions.locationServicesOff(context))
            if (state.isDemo) {
                DemoNote("These gadgets are pretend, so you can look around without one. Pick one to see pairing; there's no code to type.")
            }
            state.forgottenName?.let { ForgottenNote(it) }
            if (state.radio == RadioState.POWERED_ON) {
                Gadgets(state)
            } else if (state.radio == RadioState.UNAUTHORIZED) {
                GadgetButton("Open Settings") {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            if (!state.isDemo) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Make a gadget appear")
                    PairingSteps()
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("No gadget yet?")
                    GadgetButton("Try the demo", kind = ButtonKind.SECONDARY) { store.startDemo() }
                }
            }
        }
        HintLine(hint(state), Modifier.navigationBarsPadding())
    }
}

@Composable
private fun FindHero(state: GadgetState, locationOff: Boolean) {
    when (state.radio) {
        RadioState.UNKNOWN -> Hero("Starting Bluetooth", Indicator.Spinner(Tone.ACCENT), Palette.accent)
        RadioState.POWERED_OFF -> Hero(
            "Bluetooth is off", Indicator.Alert, Palette.red,
            "Turn on Bluetooth in Quick Settings or Settings to find your gadget.",
        )
        RadioState.UNAUTHORIZED -> Hero(
            "Bluetooth not allowed", Indicator.Alert, Palette.red,
            if (BluetoothPermissions.required.size == 1) {
                "Hermes uses Bluetooth to pair with your gadget, and Android 10 and 11 call that Location. Allow it in Settings, under Permissions."
            } else {
                "Hermes uses Bluetooth to pair with your gadget. Allow Nearby devices in Settings, under Permissions."
            },
        )
        RadioState.UNSUPPORTED -> Hero("No Bluetooth LE", Indicator.Alert, Palette.red, "This device can't connect to a gadget.")
        RadioState.POWERED_ON -> when {
            state.isScanning && state.found.isEmpty() -> Hero(
                "Looking for gadgets", Indicator.Spinner(Tone.ACCENT), Palette.accent,
                "Open Phone app on your gadget and it shows up here.",
            )
            state.found.isEmpty() -> Hero(
                "No gadgets found", Indicator.Question, Palette.text,
                "A gadget only shows up while its Phone app pairing is open, or once it's paired with this phone." +
                    if (locationOff) " Android 10 and 11 also need Location turned on to find it." else "",
            )
            else -> Hero(
                if (state.found.size == 1) "Found a gadget" else "Found ${state.found.size} gadgets",
                detail = "Pick yours to pair with it.",
            )
        }
    }
}

@Composable
private fun Gadgets(state: GadgetState) {
    val store = LocalStore.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.found.isNotEmpty()) {
            SectionLabel("Nearby") {
                if (state.isScanning) StatusIndicator(Indicator.Spinner(Tone.ACCENT), size = 14.dp)
            }
            Panel {
                state.found.forEachIndexed { index, gadget ->
                    if (index > 0) PanelDivider()
                    GadgetRow(gadget) { store.connect(gadget) }
                }
            }
        }
        if (!state.isScanning) {
            GadgetButton("Scan again", kind = if (state.found.isEmpty()) ButtonKind.PRIMARY else ButtonKind.SECONDARY) { store.startScan() }
        }
    }
}

/** A gadget found while scanning: its advertised name and signal. */
@Composable
private fun GadgetRow(gadget: DiscoveredGadget, onClick: () -> Unit) {
    PanelRow(
        onClick,
        minHeight = 60,
        description = "${gadget.name}, signal ${gadget.signalDescription}",
        clickLabel = "Connect and pair",
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(Palette.green)
            Spacer(Modifier.width(14.dp))
            PixelText(gadget.name, scale = 3, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SignalBars(gadget.signalLevel)
            Spacer(Modifier.width(14.dp))
            PixelText(">", color = Palette.dim)
        }
    }
}

@Composable
private fun ForgottenNote(name: String) {
    Panel(Modifier.clearAndSetSemantics { contentDescription = "$name forgot this phone. ${forgottenDetail(name)}" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelText("$name forgot this phone", color = Palette.accent, wraps = true)
            BodyText(forgottenDetail(name), style = Type.subheadline)
        }
    }
}

private fun forgottenDetail(name: String) =
    "If you pair again and your phone doesn't ask for a code, first unpair $name in Settings, under Connected devices."

/** How to make a gadget appear: numbered steps, like the project site's cards. */
@Composable
private fun PairingSteps() {
    val steps = listOf(
        "On the gadget, open device settings: hold the title bar for a second. Round screens show a SETTINGS label under the status dot instead; with buttons, hold TALK and CANCEL together.",
        "Choose Phone app. Pairing stays open for two minutes.",
        "Pick the gadget here, then type the six-digit code from its screen when your phone asks.",
    )
    Panel {
        steps.forEachIndexed { index, step ->
            if (index > 0) PanelDivider()
            Row(
                Modifier.padding(16.dp).clearAndSetSemantics { contentDescription = "Step ${index + 1}. $step" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelText("%02d".format(index + 1), color = Palette.accent, modifier = Modifier.padding(top = 3.dp))
                BodyText(step, color = Palette.text, style = Type.subheadline)
            }
        }
    }
}

// Top bar and hint

private fun radioColor(state: GadgetState) = when {
    state.isDemo -> Palette.accent
    state.radio == RadioState.POWERED_ON -> Palette.green
    state.radio == RadioState.UNKNOWN -> Palette.yellow
    else -> Palette.red
}

private fun radioLabel(state: GadgetState) = when {
    state.isDemo -> "DEMO"
    state.radio == RadioState.POWERED_ON -> if (state.isScanning) "SCAN" else "BT"
    state.radio == RadioState.UNKNOWN -> "BT"
    else -> "NO BT"
}

private fun radioSpoken(state: GadgetState) = if (state.isDemo) {
    "Demo"
} else {
    when (state.radio) {
        RadioState.POWERED_ON -> if (state.isScanning) "Bluetooth on, scanning" else "Bluetooth on"
        RadioState.UNKNOWN -> "Bluetooth starting"
        RadioState.POWERED_OFF -> "Bluetooth off"
        RadioState.UNAUTHORIZED -> "Bluetooth not allowed"
        RadioState.UNSUPPORTED -> "Bluetooth unavailable"
    }
}

private fun hint(state: GadgetState) = when (state.radio) {
    RadioState.POWERED_ON -> if (state.isScanning) "Scanning for 20 seconds" else "Pairing stays open 2 minutes"
    RadioState.POWERED_OFF -> "Bluetooth is off"
    RadioState.UNAUTHORIZED -> "Bluetooth needs permission"
    else -> "Hermes Gadget"
}
