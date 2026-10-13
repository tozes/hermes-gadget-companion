package com.tozes.hermescompanion.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.tozes.hermescompanion.core.Mascot
import com.tozes.hermescompanion.core.MascotFrame
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class MascotMood {
    IDLE,

    /** Alternates the talk and idle frames, as the device does while speaking. */
    TALKING,
}

/**
 * The mascot, pixel for pixel: white 1-bit art at the largest whole-pixel
 * scale that fits, blinking every few seconds like the device. Still when
 * the system's animations are off.
 */
@Composable
fun MascotView(modifier: Modifier = Modifier, mood: MascotMood = MascotMood.IDLE, color: Color = Palette.text) {
    var frame by remember { mutableStateOf(MascotFrame.IDLE) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(mood, reduceMotion) {
        frame = MascotFrame.IDLE
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            when (mood) {
                MascotMood.IDLE -> {
                    // The device blinks for two of every 45 frames at 10 fps.
                    delay(4300)
                    frame = MascotFrame.BLINK
                    delay(200)
                    frame = MascotFrame.IDLE
                }
                MascotMood.TALKING -> {
                    delay(200)
                    frame = if (frame == MascotFrame.TALK) MascotFrame.IDLE else MascotFrame.TALK
                }
            }
        }
    }
    Canvas(
        modifier.clearAndSetSemantics {
            contentDescription = "Hermes, a girl with headphones, drawn in white pixels"
            role = Role.Image
        },
    ) {
        val side = minOf(size.width, size.height)
        // Device pixels throughout: each art pixel is a whole number of them.
        val pick = Mascot.pick(frame, side, 1f) ?: return@Canvas
        val pixel = pick.pixel
        val drawn = pick.bitmap.size * pixel
        val x = ((size.width - drawn) / 2).roundToInt().toFloat()
        val y = ((size.height - drawn) / 2).roundToInt().toFloat()
        for (run in pick.bitmap.runs) {
            drawRect(color, topLeft = Offset(x + run.x * pixel, y + run.y * pixel), size = Size(run.width * pixel, pixel))
        }
    }
}
