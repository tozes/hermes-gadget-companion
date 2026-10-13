package com.tozes.hermescompanion.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.Indicator
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The round indicator beside a header's headline, as the firmware draws it
 * (`Ui::draw_indicator`): a spinner while busy, a ring while pairing, a
 * pulsing dot when ready, a red "!" on errors. Decorative: the headline next
 * to it says the same.
 */
@Composable
fun StatusIndicator(kind: Indicator, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val animated = kind is Indicator.Spinner || kind is Indicator.Pulse
    val reduceMotion = rememberReduceMotion()
    var frame by remember { mutableIntStateOf(0) }
    if (animated && !reduceMotion) {
        LaunchedEffect(kind) {
            while (true) {
                delay(100)
                frame += 1
            }
        }
    }
    when (kind) {
        Indicator.Alert -> Badge("!", Palette.red, modifier, size)
        Indicator.Question -> Badge("?", Palette.accent, modifier, size)
        else -> Canvas(modifier.size(size).clearAndSetSemantics {}) {
            val r = this.size.minDimension / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
            when (kind) {
                is Indicator.Spinner -> {
                    val dot = maxOf(2.dp.toPx(), r / 5)
                    for (i in 0 until 8) {
                        val age = ((frame % 8) - i + 8) % 8
                        val angle = i * Math.PI / 4
                        val color = when {
                            age == 0 -> kind.tone.color
                            age < 3 -> Palette.dim
                            else -> Palette.faint
                        }
                        val at = Offset(center.x + ((r - dot) * sin(angle)).toFloat(), center.y - ((r - dot) * cos(angle)).toFloat())
                        drawCircle(color, radius = dot, center = at)
                    }
                }
                is Indicator.Ring -> {
                    val line = maxOf(2.dp.toPx(), this.size.minDimension / 9)
                    drawCircle(kind.tone.color, radius = r - line / 2, center = center, style = Stroke(line))
                    drawCircle(kind.tone.color, radius = this.size.minDimension / 6, center = center)
                }
                is Indicator.Pulse -> {
                    // A triangle wave over 24 frames, like the firmware's tri().
                    val p = frame % 24
                    val tri = if (p < 12) p else 24 - p
                    drawCircle(kind.tone.color, radius = this.size.minDimension * (8 + tri / 3f) / 24, center = center)
                }
                is Indicator.Dot -> drawCircle(kind.tone.color, radius = this.size.minDimension / 3, center = center)
            }
        }
    }
}

@Composable
private fun Badge(mark: String, fill: Color, modifier: Modifier, size: Dp) {
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawCircle(fill) }
        PixelText(mark, scale = maxOf(1, (size.value / 10).toInt()), color = Palette.background)
    }
}

/**
 * The firmware's battery icon (`Ui::draw_battery`): an outline with a
 * terminal nub, filled to the level; green while charging, red when low.
 * Sizes are in font pixels times [scale] dp.
 */
@Composable
fun BatteryGlyph(percent: Int, modifier: Modifier = Modifier, charging: Boolean = false, low: Boolean = false, scale: Float = 2f) {
    val width = (9 * scale + scale).dp
    val height = (5 * scale + 1).dp
    Canvas(modifier.size(width, height).clearAndSetSemantics {}) {
        val s = scale.dp.toPx()
        val bodyWidth = 9 * s
        val bodyHeight = size.height
        val t = maxOf(1.dp.toPx(), s / 2)
        val inner = bodyWidth - 4 * t
        val clamped = percent.coerceIn(0, 100)
        val level = maxOf(if (clamped > 0) 1.dp.toPx() else 0f, inner * clamped / 100f)
        val fill = when {
            charging -> Palette.green
            low -> Palette.red
            else -> Palette.text
        }
        drawRect(Palette.dim, size = Size(bodyWidth, bodyHeight))
        drawRect(Palette.bar, topLeft = Offset(t, t), size = Size(bodyWidth - 2 * t, bodyHeight - 2 * t))
        drawRect(fill, topLeft = Offset(2 * t, 2 * t), size = Size(level, bodyHeight - 4 * t))
        // The terminal nub, centred on the body's right side.
        drawRect(Palette.dim, topLeft = Offset(bodyWidth, (bodyHeight - 2 * s) / 2), size = Size(s, 2 * s))
    }
}

/**
 * A Wi-Fi wedge drawn like [BatteryGlyph]: the same dim outline and dark
 * inside, filled when the gadget is on Wi-Fi and left empty when it isn't.
 * It's as tall as the battery.
 */
@Composable
fun WifiGlyph(connected: Boolean, modifier: Modifier = Modifier, scale: Float = 2f) {
    val width = (9 * scale).dp
    val height = (5 * scale + 1).dp
    Canvas(modifier.size(width, height).clearAndSetSemantics {}) {
        val t = maxOf(1.dp.toPx(), scale.dp.toPx() / 2)
        val apex = Offset(size.width / 2, size.height)
        val radius = size.height
        // A quarter circle opening upwards from the bottom middle, like the
        // Wi-Fi fan. Each layer sits inside the last, as in the battery:
        // outline, dark inside, then the fill.
        drawPath(wedge(apex, radius), Palette.dim)
        drawPath(wedge(Offset(apex.x, apex.y - t * 1.4f), radius - 2.4f * t), Palette.bar)
        if (connected) drawPath(wedge(Offset(apex.x, apex.y - t * 2.8f), radius - 4.8f * t), Palette.text)
    }
}

private fun wedge(apex: Offset, radius: Float): Path = Path().apply {
    val r = maxOf(radius, 0f)
    moveTo(apex.x, apex.y)
    arcTo(Rect(apex.x - r, apex.y - r, apex.x + r, apex.y + r), startAngleDegrees = -135f, sweepAngleDegrees = 90f, forceMoveTo = false)
    close()
}

/** Four stepped bars for a gadget's Bluetooth signal, lit up to [level] (1..4). */
@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier, scale: Float = 2f) {
    Canvas(modifier.size((4 * 2 * scale + 3 * scale).dp, (9 * scale).dp).clearAndSetSemantics {}) {
        val s = scale.dp.toPx()
        for (bar in 1..4) {
            val barHeight = (bar * 2 + 1) * s
            val x = (bar - 1) * 3 * s
            drawRect(if (bar <= level) Palette.green else Palette.faint, topLeft = Offset(x, size.height - barHeight), size = Size(2 * s, barHeight))
        }
    }
}

/** A small filled circle, like the device's link dot. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) { drawCircle(color) }
}
