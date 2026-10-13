package com.tozes.hermescompanion.style

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.tozes.hermescompanion.core.PixelFont
import kotlin.math.roundToInt

/**
 * The size of one font pixel, in whole device pixels, for [scale] dp per
 * font pixel. The scale steps up at large font sizes, as the iOS app's does
 * at the accessibility text sizes.
 */
fun pixelSize(scale: Int, density: Density): Int {
    val stepped = when {
        density.fontScale >= 1.8f -> scale + 2
        density.fontScale >= 1.3f -> scale + 1
        else -> scale
    }
    return maxOf(1, (stepped * density.density).roundToInt())
}

/**
 * Text in the gadget's 5x7 pixel font, drawn on a canvas at a whole number
 * of device pixels per font pixel, so every pixel is square and sharp.
 *
 * Use it for headlines, labels, hints and buttons. Long text, inputs and
 * anything that must reflow belong in the system font. TalkBack reads the
 * original string. Text the font can't draw (non-ASCII names) falls back to
 * the system monospaced font.
 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    scale: Int = 2,
    color: Color = Palette.text,
    centered: Boolean = false,
    wraps: Boolean = false,
    heading: Boolean = false,
) {
    val glyphs = remember(text) { PixelFont.normalize(text) }
    val density = LocalDensity.current
    val pixel = pixelSize(scale, density)
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = text
        if (heading) this.heading()
    }
    if (!PixelFont.canRender(glyphs)) {
        Text(
            text = text,
            modifier = modifier.then(semantics),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = with(density) { (7 * pixel).toDp().toSp() },
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = if (wraps) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val px = pixel.toFloat()
    Spacer(
        modifier
            .then(semantics)
            .layout { measurable, constraints ->
                val available = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else null
                val lines = PixelFont.lines(glyphs, available, px, wraps)
                val width = PixelFont.width(lines.maxOf { it.length }) * pixel
                val height = PixelFont.height(lines.size) * pixel
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.constrainWidth(width), constraints.constrainHeight(height)),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .drawBehind {
                val lines = PixelFont.lines(glyphs, size.width, px, wraps)
                val columns = (size.width / px).toInt()
                PixelFont.forEachRun(lines, columns, centered) { x, y, w ->
                    drawRect(color, topLeft = Offset(x * px, y * px), size = Size(w * px, px))
                }
            },
    )
}

/** The system's "Remove animations" (animator duration scale 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
