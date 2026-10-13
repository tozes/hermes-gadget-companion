package com.tozes.hermescompanion.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.GadgetState.PairingProgress
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.LinkFailure
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.HeaderBand
import com.tozes.hermescompanion.style.HintLine
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.TopBar

/**
 * Pairing: connecting, waiting for the passkey that Android asks for, and
 * the ways it fails. The code is on the gadget's screen; the app never sees it.
 */
@Composable
fun PairingScreen(state: GadgetState) {
    val store = LocalStore.current
    val name = state.gadget?.name ?: "the gadget"
    val pairing = state.pairing
    val failed = pairing is PairingProgress.Failed
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        TopBar(title = name, linkColor = if (failed) Palette.red else Palette.yellow, linkLabel = "PAIR", spokenState = "Pairing")
        ScreenContent(Modifier.weight(1f), spacing = 28, bottomInsets = false) {
            HeaderBand(
                indicator = when (pairing) {
                    PairingProgress.Connecting -> Indicator.Spinner(Tone.ACCENT)
                    PairingProgress.AwaitingPasskey -> Indicator.Ring(Tone.ACCENT)
                    is PairingProgress.Failed -> Indicator.Alert
                },
                headline = headline(pairing, name, state.isDemo),
                color = if (failed) Palette.red else Palette.accent,
                detail = detail(pairing, name, state.isDemo),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pairing is PairingProgress.Failed) {
                    GadgetButton("Try again") { store.retryPairing() }
                    GadgetButton("Back to gadgets", kind = ButtonKind.SECONDARY) { store.backToFinding() }
                } else {
                    GadgetButton("Cancel", kind = ButtonKind.SECONDARY) { store.backToFinding() }
                }
            }
        }
        HintLine(hint(pairing, state.isDemo), Modifier.navigationBarsPadding())
    }
}

private fun headline(pairing: PairingProgress, name: String, demo: Boolean) = when (pairing) {
    PairingProgress.Connecting -> "Connecting to $name"
    PairingProgress.AwaitingPasskey -> if (demo) "Pairing" else "Enter the code"
    is PairingProgress.Failed -> when (pairing.failure) {
        LinkFailure.PAIRING_REJECTED -> "Pairing failed"
        LinkFailure.PAIRING_TIMED_OUT -> "Pairing timed out"
        LinkFailure.PAIRING_REMOVED -> "Pair again"
        LinkFailure.UNREACHABLE -> "Can't reach $name"
        LinkFailure.CONNECTION_LOST -> "Connection lost"
    }
}

private fun detail(pairing: PairingProgress, name: String, demo: Boolean) = when (pairing) {
    PairingProgress.Connecting -> "Keep your phone near the gadget."
    PairingProgress.AwaitingPasskey -> if (demo) {
        "With a real gadget, your phone asks for the six digits shown on $name's screen. In the demo, pairing finishes by itself."
    } else {
        "Your phone is asking for a pairing code. Type the six digits shown on $name's screen. Only the gadget and your phone see the code, so it never appears in this app."
    }
    is PairingProgress.Failed -> when (pairing.failure) {
        LinkFailure.PAIRING_REJECTED ->
            "The code didn't match, or the request was cancelled. Check the six digits on $name's screen and try again."
        LinkFailure.PAIRING_TIMED_OUT ->
            "Pairing on $name closes two minutes after you open it. On the gadget, open device settings > Phone app again, then try again."
        LinkFailure.PAIRING_REMOVED ->
            "$name no longer knows this phone. In Settings > Connected devices, open $name and choose Forget or Unpair. Then open device settings > Phone app on the gadget and try again."
        LinkFailure.UNREACHABLE -> "Move closer and check that the gadget is on."
        LinkFailure.CONNECTION_LOST -> "The gadget went away before pairing finished. Move closer and try again."
    }
}

private fun hint(pairing: PairingProgress, demo: Boolean) = when (pairing) {
    PairingProgress.Connecting -> "Connecting"
    PairingProgress.AwaitingPasskey -> if (demo) "No code in the demo" else "The code is on the gadget"
    is PairingProgress.Failed -> "Pairing stays open 2 minutes"
}
