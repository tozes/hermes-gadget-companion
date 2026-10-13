package com.tozes.hermescompanion.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tozes.hermescompanion.AppViewModel
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.core.GadgetStore
import com.tozes.hermescompanion.screens.debug.DebugMenuSheet
import com.tozes.hermescompanion.screens.find.FindScreen
import com.tozes.hermescompanion.screens.home.HomeNavigation
import com.tozes.hermescompanion.screens.pairing.PairingScreen
import com.tozes.hermescompanion.style.LocalDebugMenu
import com.tozes.hermescompanion.style.Palette

/** The store, for every screen. */
val LocalStore = staticCompositionLocalOf<GadgetStore> { error("no store") }

/** The view model, for the screens that switch links (the debug menu, the demo). */
val LocalModel = staticCompositionLocalOf<AppViewModel> { error("no model") }

/** Shows the screen for where the app is: finding a gadget, pairing, or connected. */
@Composable
fun RootScreen(model: AppViewModel) {
    val state by model.store.state.collectAsStateWithLifecycle()
    var debugMenu by rememberSaveable { mutableStateOf(false) }
    val openDebugMenu = remember { { debugMenu = true } }
    CompositionLocalProvider(LocalStore provides model.store, LocalModel provides model, LocalDebugMenu provides openDebugMenu) {
        Box(Modifier.fillMaxSize().background(Palette.background)) {
            Crossfade(targetState = state.stage, animationSpec = tween(250), label = "stage") { stage ->
                when (stage) {
                    GadgetState.Stage.FINDING -> FindScreen(state)
                    GadgetState.Stage.PAIRING -> {
                        // System back cancels pairing, like the Cancel button.
                        BackHandler { model.store.backToFinding() }
                        PairingScreen(state)
                    }
                    GadgetState.Stage.CONNECTED -> HomeNavigation(state)
                }
            }
        }
        if (debugMenu) DebugMenuSheet(state, onDismiss = { debugMenu = false })
    }
}
