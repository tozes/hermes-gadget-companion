package com.tozes.hermescompanion

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tozes.hermescompanion.core.MockScenario
import com.tozes.hermescompanion.screens.RootScreen
import com.tozes.hermescompanion.style.HermesTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark only: light icons on the gadget's dark bars, drawn edge to edge.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            MockScenario.of(intent.getStringExtra(MockScenario.PREFERENCE_KEY))?.let(model::launchWith)
        }
        setContent {
            HermesTheme {
                RootScreen(model)
            }
        }
    }
}
