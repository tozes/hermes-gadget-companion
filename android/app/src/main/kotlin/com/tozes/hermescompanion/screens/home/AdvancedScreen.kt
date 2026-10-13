package com.tozes.hermescompanion.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.GadgetStatus
import com.tozes.hermescompanion.core.batteryDescription
import com.tozes.hermescompanion.core.hermesDescription
import com.tozes.hermescompanion.core.isLow
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.screens.common.DemoNote
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.InfoRow
import com.tozes.hermescompanion.style.NavigationRow
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.Type
import kotlinx.coroutines.launch

/**
 * Details and tools most people don't need: the gadget's status in full
 * (server, IDs, voltage), the console, and forgetting the gadget.
 */
@Composable
fun AdvancedScreen(state: GadgetState, navigate: Navigate) {
    val store = LocalStore.current
    val (refreshing, refresh) = rememberRefresh { store.refreshStatus() }
    ScreenContent(refreshing = refreshing, onRefresh = refresh) {
        if (state.isDemo) DemoNote()
        state.status?.let { status ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Gadget")
                StatusDetails(status)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Tools")
            Panel {
                NavigationRow("Console", "Type console commands", enabled = !state.connectionLost) { navigate(Route.CONSOLE) }
            }
        }
        ForgetGadgetSection(state)
    }
}

/** The gadget at a glance: power, connection, firmware and board, from `status`. */
@Composable
private fun StatusDetails(status: GadgetStatus) {
    Panel {
        InfoRow("Battery", batteryDescription(status), valueColor = if (status.power?.isLow == true) Palette.red else Palette.text)
        PanelDivider()
        InfoRow(
            "Wi-Fi", if (status.network == true) "Connected" else "Not connected",
            valueColor = if (status.network == true) Palette.text else Palette.red,
        )
        PanelDivider()
        InfoRow("Hermes", hermesDescription(status))
        status.server?.takeIf { it.isNotEmpty() }?.let { server ->
            PanelDivider()
            InfoRow("Server", server, monospaced = true, breaksAfter = setOf('/', '.'))
        }
        PanelDivider()
        InfoRow("Firmware", status.firmware ?: "Unknown")
        PanelDivider()
        InfoRow("Board", status.board ?: "Unknown", monospaced = true)
        status.deviceId?.let { id ->
            PanelDivider()
            InfoRow("Device ID", id, monospaced = true)
        }
        status.phone?.paired?.let { phones ->
            PanelDivider()
            InfoRow("Phones", if (phones == 1) "1 paired" else "$phones paired")
        }
        if (status.displaySleeping == true) {
            PanelDivider()
            InfoRow("Display", "Asleep")
        }
    }
}

/** Forget this gadget (`phone forget`), after a confirmation that says what it does. */
@Composable
private fun ForgetGadgetSection(state: GadgetState) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val name = state.gadgetName
    var confirming by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GadgetButton("Forget $name", kind = ButtonKind.DESTRUCTIVE, busy = forgetting, enabled = !forgetting && !state.connectionLost) {
            confirming = true
        }
        error?.let { BodyText(it, color = Palette.red, style = Type.footnote) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Palette.bar,
            title = { Text("Forget $name?", color = Palette.text) },
            text = {
                Text(
                    "$name removes every phone paired with it, not only this one, and disconnects. To use the app with it again, open Phone app on the gadget and pair.",
                    color = Palette.dim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    forgetting = true
                    error = null
                    scope.launch {
                        error = store.forget()
                        forgetting = false
                    }
                }) { Text("Forget $name", color = Palette.red) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel", color = Palette.accent) } },
        )
    }
}
