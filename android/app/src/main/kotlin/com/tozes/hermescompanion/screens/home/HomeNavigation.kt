package com.tozes.hermescompanion.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.screens.console.ConsoleScreen
import com.tozes.hermescompanion.screens.settings.SettingsScreen
import com.tozes.hermescompanion.screens.wifi.AddWifiNetworkScreen
import com.tozes.hermescompanion.screens.wifi.WifiSetupScreen
import com.tozes.hermescompanion.style.GadgetTopBar
import com.tozes.hermescompanion.style.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** The screens under a connected gadget, as the iOS app's navigation stack. */
object Route {
    const val HOME = "home"
    const val WIFI = "wifi"
    const val ADD_WIFI = "wifi/add"
    const val SETTINGS = "settings"
    const val ADVANCED = "advanced"
    const val CONSOLE = "console"
}

/** The navigation stack, for screens that open others. */
typealias Navigate = (String) -> Unit

/**
 * The connected gadget: the home screen and the screens it opens. System
 * back on the home screen goes back to the gadget list, like the Gadgets
 * button, and disconnects.
 */
@Composable
fun HomeNavigation(state: GadgetState) {
    val store = LocalStore.current
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val onHome = (entry?.destination?.route ?: Route.HOME) == Route.HOME
    BackHandler(enabled = onHome) { store.backToFinding() }

    // Keeps status fresh while the home screen shows, and loads settings once.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (store.state.value.missingSettings) store.loadSettings()
            while (true) {
                delay(POLL_INTERVAL)
                if (navigation.currentDestination?.route == Route.HOME) store.refreshStatus()
            }
        }
    }

    val navigate: Navigate = { route -> navigation.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { navigation.popBackStack() }
    NavHost(navigation, startDestination = Route.HOME, modifier = Modifier.fillMaxSize().background(Palette.background)) {
        composable(Route.HOME) { HomeScreen(state, navigate) }
        composable(Route.WIFI) { Screen("Wi-Fi", back) { WifiSetupScreen(state, navigate, back) } }
        composable(Route.ADD_WIFI) { Screen("Add network", back) { AddWifiNetworkScreen(state, navigate, back) } }
        composable(Route.SETTINGS) { Screen("Settings", back) { SettingsScreen(state) } }
        composable(Route.ADVANCED) { Screen("Advanced", back) { AdvancedScreen(state, navigate) } }
        composable(Route.CONSOLE) { ConsoleScreen(state, back) }
    }
}

private val POLL_INTERVAL = 15.seconds

/** A screen under the home screen: the bar with its title and back, then its content. */
@Composable
private fun Screen(title: String, back: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        GadgetTopBar(title, onBack = back)
        content()
    }
}

/**
 * A pull-to-refresh action: whether it's running, and how to start it.
 * The indicator shows until [action] returns.
 */
@Composable
fun rememberRefresh(action: suspend () -> Unit): Pair<Boolean, () -> Unit> {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    return refreshing to {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                try {
                    action()
                } finally {
                    refreshing = false
                }
            }
        }
    }
}
