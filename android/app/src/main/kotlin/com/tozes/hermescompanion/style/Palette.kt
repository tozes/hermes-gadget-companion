package com.tozes.hermescompanion.style

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import com.tozes.hermescompanion.core.Tone

/** The gadget's colours, from the firmware renderer (`ui.cpp`), as in the iOS app's `Palette`. */
object Palette {
    val background = Color(10, 14, 20)
    val bar = Color(24, 31, 42)
    val text = Color(232, 238, 242)
    val dim = Color(132, 146, 160)
    val faint = Color(52, 62, 76)
    val accent = Color(242, 179, 61)
    val green = Color(61, 214, 140)
    val blue = Color(90, 169, 242)
    val red = Color(242, 95, 92)
    val yellow = Color(242, 201, 76)
    val greenDim = Color(28, 92, 62)
    val blueDim = Color(52, 96, 140)
    val accentDim = Color(110, 82, 30)
    val redDim = Color(122, 44, 44)

    /** Dark text on amber, from the site (`--accent-ink`). */
    val accentInk = Color(26, 18, 3)
}

val Tone.color: Color
    get() = when (this) {
        Tone.TEXT -> Palette.text
        Tone.DIM -> Palette.dim
        Tone.FAINT -> Palette.faint
        Tone.ACCENT -> Palette.accent
        Tone.ACCENT_INK -> Palette.accentInk
        Tone.GREEN -> Palette.green
        Tone.RED -> Palette.red
        Tone.YELLOW -> Palette.yellow
    }

/**
 * Material 3 underneath, dark only, in the gadget's colours: the system
 * pieces (sliders, switches, dialogs, text fields) pick these up.
 */
@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Palette.accent,
        onPrimary = Palette.accentInk,
        primaryContainer = Palette.accentDim,
        onPrimaryContainer = Palette.text,
        secondary = Palette.accent,
        onSecondary = Palette.accentInk,
        tertiary = Palette.blue,
        background = Palette.background,
        onBackground = Palette.text,
        surface = Palette.bar,
        onSurface = Palette.text,
        surfaceVariant = Palette.bar,
        onSurfaceVariant = Palette.dim,
        surfaceContainer = Palette.bar,
        surfaceContainerHigh = Palette.bar,
        surfaceContainerHighest = Palette.faint,
        surfaceContainerLow = Palette.bar,
        surfaceContainerLowest = Palette.background,
        outline = Palette.faint,
        outlineVariant = Palette.faint,
        error = Palette.red,
        onError = Palette.background,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(handleColor = Palette.accent, backgroundColor = Palette.accent.copy(alpha = 0.35f)),
            content = content,
        )
    }
}
