package com.tozes.hermescompanion.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.BatteryDisplay
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.SettingKey
import com.tozes.hermescompanion.core.SettingValue
import com.tozes.hermescompanion.core.TalkMode
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.core.screenTimeoutDescription
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.screens.home.rememberRefresh
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.GadgetTextField
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PixelSegmented
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.SegmentOption
import com.tozes.hermescompanion.style.StatusIndicator
import com.tozes.hermescompanion.style.Type
import com.tozes.hermescompanion.style.sideButtonWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where a setting's last save stands. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState

    /** The gadget's `@error` message. */
    data class Failed(val message: String) : SaveState
}

/** Runs a save and shows "saved" for a moment afterwards. */
suspend fun MutableState<SaveState>.run(save: suspend () -> String?) {
    value = SaveState.Saving
    val error = save()
    if (error != null) {
        value = SaveState.Failed(error)
        return
    }
    value = SaveState.Saved
    delay(2000)
    if (value == SaveState.Saved) value = SaveState.Idle
}

/**
 * The gadget's settings, read with `get` and written with `set`. Each one
 * saves on its own, and the gadget's `@error` shows under that setting.
 */
@Composable
fun SettingsScreen(state: GadgetState) {
    val store = LocalStore.current
    LaunchedEffect(Unit) { if (store.state.value.missingSettings) store.loadSettings() }
    val (refreshing, refresh) = rememberRefresh { store.loadSettings(all = true) }
    ScreenContent(refreshing = refreshing, onRefresh = refresh) {
        if (state.isLoadingSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(Indicator.Spinner(Tone.ACCENT), size = 16.dp)
                Spacer(Modifier.width(10.dp))
                PixelText("Reading settings", color = Palette.dim)
            }
        } else if (state.missingSettings) {
            BodyText("Some settings didn't load. Pull down to try again.", color = Palette.red, style = Type.footnote)
        }
        val enabled = !state.connectionLost
        Section("Gadget", enabled) {
            TextSettingRow(state, SettingKey.NAME, "Name", "Gadget name", "Shown on the gadget and when your phone looks for it.")
        }
        Section("Sound and talking", enabled) {
            SliderSettingRow(state, SettingKey.VOLUME, "Volume", SettingKey.volumeRange)
            PanelDivider()
            ChoiceSettingRow(
                state, SettingKey.TALK_MODE, "Talk mode",
                TalkMode.entries.map { SegmentOption(it.rawValue, it.title, "${it.title} to talk") },
            ) { TalkMode.of(it)?.explanation }
        }
        Section("Screen", enabled) {
            SliderSettingRow(state, SettingKey.BRIGHTNESS, "Brightness", SettingKey.brightnessRange)
            PanelDivider()
            ScreenTimeoutRow(state)
            PanelDivider()
            ChoiceSettingRow(
                state, SettingKey.BATTERY_DISPLAY, "Battery indicator",
                BatteryDisplay.entries.map { SegmentOption(it.rawValue, it.title, it.spokenTitle) },
            ) { "Shown at the top of the gadget's screen, when the board reports a battery level." }
        }
        Section("Hermes", enabled) {
            TextSettingRow(
                state, SettingKey.SERVER, "Server", "wss://host:port/path",
                "The gadget reconnects when the address or token changes.", monospaced = true, keyboardType = KeyboardType.Uri,
            )
            PanelDivider()
            SecretSettingRow(state, SettingKey.TOKEN, "Access token")
        }
    }
}

@Composable
private fun Section(title: String, enabled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(title)
        // A dropped link leaves the values readable, but not editable.
        Panel(Modifier.alpha(if (enabled) 1f else 0.45f), content = content)
    }
}

/**
 * One setting inside a panel: a pixel label with the save state beside it,
 * the control, and the gadget's error underneath the control it belongs to.
 */
@Composable
private fun SettingField(label: String, state: SaveState, note: String? = null, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelText(label.uppercase(), color = Palette.dim, heading = true, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            when (state) {
                SaveState.Saving -> {
                    StatusIndicator(Indicator.Spinner(Tone.ACCENT), size = 14.dp)
                    PixelText("saving", color = Palette.dim)
                }
                SaveState.Saved -> PixelText("saved", color = Palette.green)
                else -> Unit
            }
        }
        control()
        if (state is SaveState.Failed) {
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "Error: ${state.message}" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelText("!", color = Palette.red, modifier = Modifier.padding(top = 3.dp))
                BodyText(state.message, color = Palette.red, style = Type.footnote)
            }
        } else if (note != null) {
            BodyText(note, style = Type.footnote)
        }
    }
}

/** A text setting (name, server): edit, then Save or Done. Errors from the gadget appear under the field. */
@Composable
private fun TextSettingRow(
    state: GadgetState,
    key: SettingKey,
    label: String,
    prompt: String,
    note: String?,
    monospaced: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val save = remember { mutableStateOf<SaveState>(SaveState.Idle) }
    val loaded = state.settings[key]
    var draft by remember { mutableStateOf(loaded.orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    // The gadget trims values, so compare what it would keep.
    val trimmed = draft.trim()
    val dirty = loaded != null && trimmed != loaded
    // Don't overwrite what the user is typing.
    LaunchedEffect(loaded) { if (loaded != null && !editing) draft = loaded }
    fun submit() {
        if (!dirty) {
            draft = loaded.orEmpty()
            editing = false
            return
        }
        scope.launch {
            save.run {
                store.save(key, trimmed).also { error ->
                    if (error == null) {
                        draft = trimmed
                        editing = false
                    }
                }
            }
        }
    }
    SettingField(label, save.value, note) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GadgetTextField(
                draft, { draft = it; editing = true }, label, prompt,
                Modifier.weight(1f),
                enabled = loaded != null && !state.connectionLost,
                error = (save.value as? SaveState.Failed)?.message,
                monospaced = monospaced,
                keyboardType = keyboardType,
                capitalization = if (key == SettingKey.NAME) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                onImeAction = ::submit,
            )
            if (dirty) {
                GadgetButton("Save", sideButtonWidth.widthIn(max = 140.dp), enabled = save.value != SaveState.Saving, onClick = ::submit)
            }
        }
    }
}

/** A percentage setting (volume, brightness), saved when the slider is released. */
@Composable
private fun SliderSettingRow(state: GadgetState, key: SettingKey, label: String, range: IntRange) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val save = remember { mutableStateOf<SaveState>(SaveState.Idle) }
    val saved = state.settings[key]?.toIntOrNull()
    var value by remember { mutableFloatStateOf(saved?.toFloat() ?: range.first.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved != null && !dragging) value = saved.toFloat() }
    SettingField(label, save.value) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Slider(
                value = value,
                onValueChange = {
                    dragging = true
                    value = it.roundToInt().toFloat()
                },
                onValueChangeFinished = {
                    dragging = false
                    val new = value.roundToInt()
                    if (new != saved) {
                        scope.launch {
                            save.run {
                                store.save(key, new.toString()).also { error ->
                                    // Show what the gadget kept, not the value it refused.
                                    if (error != null && saved != null) value = saved.toFloat()
                                }
                            }
                        }
                    }
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = saved != null && !state.connectionLost,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.accent,
                    activeTrackColor = Palette.accent,
                    inactiveTrackColor = Palette.faint,
                    disabledThumbColor = Palette.dim,
                    disabledActiveTrackColor = Palette.dim,
                    disabledInactiveTrackColor = Palette.faint,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            Row(Modifier.width(56.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.End) {
                PixelText(if (saved == null) "--" else "${value.roundToInt()}%")
            }
        }
    }
}

/** A setting with a few fixed values (talk mode, battery indicator), saved on tap. */
@Composable
private fun ChoiceSettingRow(
    state: GadgetState,
    key: SettingKey,
    label: String,
    options: List<SegmentOption<String>>,
    note: (String?) -> String?,
) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val save = remember { mutableStateOf<SaveState>(SaveState.Idle) }
    var pending by remember { mutableStateOf<String?>(null) }
    val saved = state.settings[key]
    val shown = pending ?: saved
    SettingField(label, save.value, note(shown)) {
        PixelSegmented(options, shown, enabled = saved != null && save.value != SaveState.Saving && !state.connectionLost) { value ->
            if (value == shown) return@PixelSegmented
            pending = value
            scope.launch {
                save.run { store.save(key, value) }
                pending = null
            }
        }
    }
}

/**
 * Screen timeout: the device menu's presets, or any number of seconds up to
 * an hour. The gadget checks the range and answers `@error` otherwise.
 */
@Composable
private fun ScreenTimeoutRow(state: GadgetState) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val save = remember { mutableStateOf<SaveState>(SaveState.Idle) }
    var custom by remember { mutableStateOf("") }
    val saved = state.settings[SettingKey.SCREEN_TIMEOUT]
    val presets = listOf(
        SegmentOption("0", "Off", "Always on"),
        SegmentOption("30", "30s", "30 seconds"),
        SegmentOption("60", "1m", "1 minute"),
        SegmentOption("120", "2m", "2 minutes"),
        SegmentOption("300", "5m", "5 minutes"),
    )
    fun submit(value: String) {
        if (value == saved) return
        scope.launch {
            save.run { store.save(SettingKey.SCREEN_TIMEOUT, value).also { if (it == null) custom = "" } }
        }
    }
    val enabled = saved != null && !state.connectionLost
    SettingField("Screen timeout", save.value, screenTimeoutDescription(saved)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelSegmented(presets, saved, enabled = enabled && save.value != SaveState.Saving) { submit(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GadgetTextField(
                    custom, { custom = it }, "Custom timeout in seconds", "Other: 0 to 3600 seconds",
                    Modifier.weight(1f),
                    enabled = enabled,
                    error = (save.value as? SaveState.Failed)?.message,
                    monospaced = true,
                    keyboardType = KeyboardType.Number,
                    onImeAction = { if (custom.isNotEmpty()) submit(custom) },
                )
                if (custom.isNotEmpty()) {
                    GadgetButton("Save", sideButtonWidth.widthIn(max = 140.dp), enabled = save.value != SaveState.Saving) { submit(custom) }
                }
            }
        }
    }
}

/** The Hermes access token: write-only. The gadget only says whether one is set. */
@Composable
private fun SecretSettingRow(state: GadgetState, key: SettingKey, label: String) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val save = remember { mutableStateOf<SaveState>(SaveState.Idle) }
    var draft by remember { mutableStateOf("") }
    val isSet = state.settings[key] == SettingValue.MASKED_SECRET
    fun submit(value: String) {
        scope.launch { save.run { store.save(key, value).also { if (it == null) draft = "" } } }
    }
    SettingField(label, save.value, if (isSet) "A token is saved. Type a new one to replace it." else "No token saved.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GadgetTextField(
                draft, { draft = it }, label, if (isSet) "Saved" else "Paste the token",
                Modifier.weight(1f),
                enabled = !state.connectionLost,
                error = (save.value as? SaveState.Failed)?.message,
                monospaced = true,
                secret = true,
                imeAction = ImeAction.Done,
                onImeAction = { if (draft.isNotEmpty()) submit(draft) },
            )
            if (draft.isNotEmpty()) {
                GadgetButton("Save", sideButtonWidth.widthIn(max = 140.dp), enabled = save.value != SaveState.Saving) { submit(draft) }
            } else if (isSet) {
                GadgetButton("Clear", sideButtonWidth.widthIn(max = 140.dp), kind = ButtonKind.SECONDARY, enabled = save.value != SaveState.Saving) { submit("") }
            }
        }
    }
}
