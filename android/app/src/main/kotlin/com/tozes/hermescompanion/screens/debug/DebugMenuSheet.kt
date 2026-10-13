package com.tozes.hermescompanion.screens.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.MockGadgetLink
import com.tozes.hermescompanion.core.MockScenario
import com.tozes.hermescompanion.core.RadioState
import com.tozes.hermescompanion.screens.LocalModel
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.Dot
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.Type
import kotlinx.coroutines.launch

/** The hidden debug menu: switch mock scenarios and trigger mock events. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuSheet(state: GadgetState, onDismiss: () -> Unit) {
    val model = LocalModel.current
    val store = model.store
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mock = store.link as? MockGadgetLink
    fun choose(scenario: MockScenario?) {
        model.choose(scenario)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Palette.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                PixelText("DEBUG", heading = true, modifier = Modifier.align(Alignment.Center))
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button) { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } },
                    contentAlignment = Alignment.Center,
                ) { Text("Done", color = Palette.accent, style = Type.body) }
            }
            BodyText(
                "Pick a mock scenario to try a screen without a gadget, or real Bluetooth to talk to one. The choice is kept across launches, except the demo; " +
                    "adb shell am start -n com.tozes.hermescompanion/.MainActivity -e mockScenario <name> sets a scenario for one launch.",
                style = Type.footnote,
            )
            if (mock != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Events")
                    GadgetButton("Approve on Hermes", kind = ButtonKind.SECONDARY) {
                        mock.approveOnHermes()
                        scope.launch { store.refreshStatus() }
                    }
                    GadgetButton("Drop the connection", kind = ButtonKind.SECONDARY) { mock.drop() }
                    val on = state.radio == RadioState.POWERED_ON
                    GadgetButton(if (on) "Turn Bluetooth off" else "Turn Bluetooth on", kind = ButtonKind.SECONDARY) {
                        mock.setRadio(if (on) RadioState.POWERED_OFF else RadioState.POWERED_ON)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Scenarios")
                Panel {
                    ScenarioRow("Real Bluetooth", "Find and pair with gadgets nearby.", null, selected = mock == null) { choose(null) }
                    MockScenario.entries.forEach { scenario ->
                        PanelDivider()
                        ScenarioRow(scenario.title, scenario.detail, scenario.rawValue, selected = mock?.scenario == scenario) { choose(scenario) }
                    }
                }
            }
            Box(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun ScenarioRow(title: String, detail: String, raw: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(if (selected) Palette.accent else Palette.faint, Modifier.padding(top = 6.dp), size = 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Palette.text, style = Type.body)
            BodyText(detail, style = Type.footnote)
            if (raw != null) Text(raw, color = Palette.accent, style = Type.caption.merge(Type.mono))
        }
    }
}
