package com.tozes.hermescompanion.screens.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.SettingKey
import com.tozes.hermescompanion.core.SettingValue
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.core.WifiJoin
import com.tozes.hermescompanion.core.WifiNetworkList
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.screens.home.Navigate
import com.tozes.hermescompanion.screens.home.Route
import com.tozes.hermescompanion.screens.home.rememberRefresh
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.GadgetTextField
import com.tozes.hermescompanion.style.HeaderBand
import com.tozes.hermescompanion.style.HintLine
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PanelDivider
import com.tozes.hermescompanion.style.PanelRow
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.ScreenContent
import com.tozes.hermescompanion.style.SectionLabel
import com.tozes.hermescompanion.style.StatusIndicator
import com.tozes.hermescompanion.style.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val GadgetState.joinName: String get() = status?.name ?: "The gadget"

/** Wi-Fi: the gadget's saved networks, or the one-network form on firmware without the `wifi` command. */
@Composable
fun WifiSetupScreen(state: GadgetState, navigate: Navigate, back: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    suspend fun load() {
        loading = true
        loadError = store.loadWifiNetworks()
        loading = false
    }
    LaunchedEffect(Unit) {
        if (store.state.value.wifiNetworks == null && !store.state.value.keepsOneNetwork) load()
    }
    val networks = state.wifiNetworks
    when {
        state.keepsOneNetwork -> OneNetworkWifiScreen(state, navigate, back)
        networks != null -> WifiNetworksScreen(state, networks, navigate)
        else -> ScreenContent {
            val error = loadError
            if (error != null) {
                HeaderBand(Indicator.Alert, "Couldn't read Wi-Fi", Palette.red, error)
                GadgetButton("Try again", busy = loading, enabled = !loading && !state.connectionLost) { scope.launch { load() } }
            } else {
                HeaderBand(
                    Indicator.Spinner(Tone.ACCENT), "Reading networks", Palette.accent,
                    "Asking ${state.status?.name ?: "the gadget"} which networks it keeps.",
                )
            }
        }
    }
}

/**
 * The networks the gadget keeps, first one tried first. Tap a network to
 * join it now or forget it; add more on their own screen.
 */
@Composable
private fun WifiNetworksScreen(state: GadgetState, list: WifiNetworkList, navigate: Navigate) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val join = remember { WifiJoin() }
    val joinState by join.state.collectAsStateWithLifecycle()
    var task by remember { mutableStateOf<Job?>(null) }
    // The network whose actions are showing, then the one being joined.
    var chosen by remember { mutableStateOf<WifiNetworkList.Network?>(null) }
    var showingActions by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val name = state.joinName
    val (refreshing, refresh) = rememberRefresh { store.loadWifiNetworks() }

    fun start(network: WifiNetworkList.Network) {
        if (join.isRunning) return
        chosen = network
        task?.cancel()
        task = scope.launch { join.join(store, network.ssid, newPassword = null) }
    }
    fun finish() {
        task?.cancel()
        join.reset()
        scope.launch { store.loadWifiNetworks() }
    }
    fun forget(network: WifiNetworkList.Network) {
        forgetting = network.ssid
        scope.launch {
            actionError = store.forgetWifi(network.ssid)
            forgetting = null
        }
    }

    ScreenContent(refreshing = refreshing, onRefresh = refresh) {
        val step = joinState.step
        if (step == WifiJoin.Step.Editing) {
            val current = list.current
            HeaderBand(
                Indicator.Dot(if (current != null) Tone.GREEN else Tone.RED),
                current?.let { "On $it" } ?: "Not on Wi-Fi",
                detail = "$name keeps up to ${list.max} networks and joins whichever is in range, starting with the one that worked last. 2.4 GHz only.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Saved networks")
                if (list.networks.isNotEmpty()) {
                    val enabled = !state.connectionLost && forgetting == null
                    Panel(Modifier.alpha(if (enabled) 1f else 0.45f)) {
                        list.networks.forEachIndexed { index, network ->
                            if (index > 0) PanelDivider()
                            NetworkRow(network, list, enabled, forgetting == network.ssid) {
                                actionError = null
                                chosen = network
                                showingActions = true
                            }
                        }
                    }
                } else {
                    BodyText("None yet. Add the network $name should use.")
                }
                actionError?.let { BodyText(it, color = Palette.red, style = Type.footnote) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GadgetButton("Add a network", enabled = !list.isFull && !state.connectionLost) { navigate(Route.ADD_WIFI) }
                if (list.isFull) HintLine("${list.max} saved. Forget one to add another.")
            }
        } else {
            WifiJoinHeader(step, chosen?.ssid.orEmpty(), name)
            WifiProgress(step)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    WifiJoin.Step.Saving, WifiJoin.Step.Joining, WifiJoin.Step.ReachingHermes ->
                        GadgetButton("Stop watching", kind = ButtonKind.SECONDARY) { finish() }
                    WifiJoin.Step.NoHermes -> {
                        GadgetButton("Check the server") { navigate(Route.SETTINGS) }
                        GadgetButton("Back to networks", kind = ButtonKind.SECONDARY) { finish() }
                    }
                    else -> GadgetButton("Back to networks") { finish() }
                }
            }
        }
    }

    val network = chosen
    if (showingActions && network != null) {
        val isCurrent = network.ssid == list.current
        AlertDialog(
            onDismissRequest = { showingActions = false },
            containerColor = Palette.bar,
            title = { Text(network.ssid, color = Palette.text) },
            text = {
                Text(
                    if (isCurrent) {
                        "$name is on this network. Forgetting it leaves it, and $name moves to another saved network."
                    } else {
                        "Joining moves it to the top of the list. If it doesn't work, $name goes back to its other networks."
                    },
                    color = Palette.dim,
                )
            },
            confirmButton = {
                Row {
                    if (!isCurrent) {
                        TextButton(onClick = {
                            showingActions = false
                            start(network)
                        }) { Text("Join now", color = Palette.accent) }
                    }
                    TextButton(onClick = {
                        showingActions = false
                        forget(network)
                    }) { Text("Forget", color = Palette.red) }
                }
            },
            dismissButton = { TextButton(onClick = { showingActions = false }) { Text("Cancel", color = Palette.accent) } },
        )
    }
}

@Composable
private fun NetworkRow(network: WifiNetworkList.Network, list: WifiNetworkList, enabled: Boolean, forgetting: Boolean, onClick: () -> Unit) {
    val open = if (network.open) ", open" else ""
    val (state, color) = when {
        network.ssid == list.current -> "On now$open" to Palette.green
        network.tested -> "Saved$open" to Palette.dim
        else -> "Saved, not tested$open" to Palette.accent
    }
    PanelRow(onClick, enabled = enabled, description = "${network.ssid}, $state", clickLabel = "Join or forget") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(network.ssid, color = Palette.text, style = Type.body)
                PixelText(state.uppercase(), color = color)
            }
            Spacer(Modifier.width(8.dp))
            if (forgetting) StatusIndicator(Indicator.Spinner(Tone.ACCENT), size = 16.dp) else PixelText(">", color = Palette.dim)
        }
    }
}

/**
 * Adds a network to the gadget's list: join it now, or save it for later
 * (a phone hotspot, the office) without the gadget trying it yet.
 */
@Composable
fun AddWifiNetworkScreen(state: GadgetState, navigate: Navigate, done: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val join = remember { WifiJoin() }
    val joinState by join.state.collectAsStateWithLifecycle()
    var task by remember { mutableStateOf<Job?>(null) }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var openNetwork by rememberSaveable { mutableStateOf(false) }
    val passwordField = remember { FocusRequester() }
    val name = state.joinName
    val network = ssid.trim()
    val alreadySaved = state.wifiNetworks?.contains(network) == true
    // A password to save: typed, or none for an open network.
    val hasNewPassword = openNetwork || password.isNotEmpty()
    // A saved network can be joined again with its stored password.
    val canSubmit = network.isNotEmpty() && (hasNewPassword || alreadySaved) && !state.connectionLost
    val step = joinState.step

    fun joinNow() {
        if (!canSubmit || join.isRunning) return
        focus.clearFocus()
        task?.cancel()
        val newPassword = if (hasNewPassword) (if (openNetwork) "" else password) else null
        task = scope.launch { join.join(store, network, newPassword) }
    }
    fun saveForLater() {
        if (!canSubmit || join.isRunning) return
        focus.clearFocus()
        task?.cancel()
        val chosen = if (openNetwork) "" else password
        task = scope.launch { join.save(store, network, chosen) }
    }

    ScreenContent {
        if (step == WifiJoin.Step.Editing) {
            HeaderBand(
                Indicator.Dot(Tone.ACCENT), "Add a network",
                detail = "Join it now, or save it for later: $name uses it when it loses its current network and this one is in range.",
            )
        } else {
            WifiJoinHeader(step, network, name)
        }
        val formEnabled = !joinState.isRunning && !state.connectionLost && step != WifiJoin.Step.Saved
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WifiField("Network name", joinState.error(WifiJoin.Field.SSID)) {
                    GadgetTextField(
                        ssid, { ssid = it }, "Network name", "Wi-Fi network",
                        enabled = formEnabled,
                        error = joinState.error(WifiJoin.Field.SSID),
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordField.requestFocus() },
                    )
                }
                WifiField("Password", joinState.error(WifiJoin.Field.PASSWORD)) {
                    GadgetTextField(
                        password, { password = it }, "Password", if (alreadySaved) "Saved; type to replace" else "Wi-Fi password",
                        Modifier.focusRequester(passwordField),
                        enabled = formEnabled && !openNetwork,
                        error = joinState.error(WifiJoin.Field.PASSWORD),
                        secret = true,
                        imeAction = ImeAction.Go,
                        onImeAction = ::joinNow,
                    )
                }
                OpenNetworkSwitch(openNetwork, formEnabled) { openNetwork = it }
                if (alreadySaved) {
                    BodyText("$network is already saved. Saving it again replaces its password.", style = Type.footnote)
                }
            }
        }
        if (step != WifiJoin.Step.Editing && step != WifiJoin.Step.Saved) WifiProgress(step)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                WifiJoin.Step.Editing, is WifiJoin.Step.Failed -> {
                    GadgetButton(if (step == WifiJoin.Step.Editing) "Join now" else "Try again", enabled = canSubmit, onClick = ::joinNow)
                    GadgetButton("Save for later", kind = ButtonKind.SECONDARY, enabled = canSubmit && hasNewPassword, onClick = ::saveForLater)
                }
                WifiJoin.Step.Saving, WifiJoin.Step.Joining, WifiJoin.Step.ReachingHermes ->
                    GadgetButton("Stop watching", kind = ButtonKind.SECONDARY) {
                        task?.cancel()
                        join.reset()
                    }
                is WifiJoin.Step.Done, WifiJoin.Step.Saved -> GadgetButton("Done", onClick = done)
                WifiJoin.Step.NoHermes -> {
                    GadgetButton("Check the server") { navigate(Route.SETTINGS) }
                    GadgetButton("Done", kind = ButtonKind.SECONDARY, onClick = done)
                }
            }
        }
    }
}

/**
 * Wi-Fi setup on firmware that keeps one network (`set wifi_ssid` and
 * `set wifi_pass`): network name and password, then progress until the
 * gadget is on the network and reaching Hermes.
 */
@Composable
private fun OneNetworkWifiScreen(state: GadgetState, navigate: Navigate, done: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val join = remember { WifiJoin() }
    val joinState by join.state.collectAsStateWithLifecycle()
    var task by remember { mutableStateOf<Job?>(null) }
    var ssid by rememberSaveable { mutableStateOf(state.settings[SettingKey.WIFI_SSID].orEmpty()) }
    var password by remember { mutableStateOf("") }
    var openNetwork by rememberSaveable { mutableStateOf(false) }
    val passwordField = remember { FocusRequester() }
    val name = state.joinName
    val passwordSaved = state.settings[SettingKey.WIFI_PASSWORD] == SettingValue.MASKED_SECRET
    val step = joinState.step

    fun start() {
        val network = ssid.trim()
        if (network.isEmpty() || join.isRunning) return
        focus.clearFocus()
        task?.cancel()
        task = scope.launch { join.run(store, network, password, clearPassword = openNetwork) }
    }

    ScreenContent {
        if (step == WifiJoin.Step.Editing) {
            val online = state.status?.network == true
            HeaderBand(
                Indicator.Dot(if (online) Tone.GREEN else Tone.RED),
                if (online) "On Wi-Fi" else "Join a network",
                detail = "$name joins 2.4 GHz Wi-Fi networks. It keeps the network for next time.",
            )
        } else {
            WifiJoinHeader(step, ssid, name)
        }
        val formEnabled = !joinState.isRunning && !state.connectionLost
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WifiField("Network name", joinState.error(WifiJoin.Field.SSID)) {
                    GadgetTextField(
                        ssid, { ssid = it }, "Network name", "Wi-Fi network",
                        enabled = formEnabled,
                        error = joinState.error(WifiJoin.Field.SSID),
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordField.requestFocus() },
                    )
                }
                WifiField("Password", joinState.error(WifiJoin.Field.PASSWORD)) {
                    GadgetTextField(
                        password, { password = it }, "Password", if (passwordSaved) "Saved; type to replace" else "Wi-Fi password",
                        Modifier.focusRequester(passwordField),
                        enabled = formEnabled && !openNetwork,
                        error = joinState.error(WifiJoin.Field.PASSWORD),
                        secret = true,
                        imeAction = ImeAction.Go,
                        onImeAction = ::start,
                    )
                }
                OpenNetworkSwitch(openNetwork, formEnabled) { openNetwork = it }
            }
        }
        if (step != WifiJoin.Step.Editing) WifiProgress(step)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                WifiJoin.Step.Editing, is WifiJoin.Step.Failed -> GadgetButton(
                    if (step == WifiJoin.Step.Editing) "Join network" else "Try again",
                    enabled = ssid.isNotBlank() && !state.connectionLost,
                    onClick = ::start,
                )
                WifiJoin.Step.Saving, WifiJoin.Step.Joining, WifiJoin.Step.ReachingHermes ->
                    GadgetButton("Stop watching", kind = ButtonKind.SECONDARY) {
                        task?.cancel()
                        join.reset()
                    }
                is WifiJoin.Step.Done, WifiJoin.Step.Saved -> GadgetButton("Done", onClick = done)
                WifiJoin.Step.NoHermes -> {
                    GadgetButton("Check the server") { navigate(Route.SETTINGS) }
                    GadgetButton("Edit network", kind = ButtonKind.SECONDARY) { join.reset() }
                }
            }
        }
    }
}
