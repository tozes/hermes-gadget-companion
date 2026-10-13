package com.tozes.hermescompanion.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.style.BodyText
import com.tozes.hermescompanion.style.ButtonKind
import com.tozes.hermescompanion.style.GadgetButton
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.Panel
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.Type

/** Says the app is in the demo, with the way out. */
@Composable
fun DemoNote(detail: String = "The gadgets here are pretend, so you can look around without one. Nothing is sent over Bluetooth.") {
    val store = LocalStore.current
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelText("Demo", color = Palette.accent)
            BodyText(detail, style = Type.subheadline)
            GadgetButton("Leave the demo", kind = ButtonKind.SECONDARY) { store.endDemo() }
        }
    }
}
