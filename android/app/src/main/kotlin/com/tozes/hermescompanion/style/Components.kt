package com.tozes.hermescompanion.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tozes.hermescompanion.core.Indicator
import com.tozes.hermescompanion.core.PixelFont
import com.tozes.hermescompanion.core.Tone
import com.tozes.hermescompanion.core.wrappable

/** Body text sizes, in sp so they follow the user's font size. */
object Type {
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
}

private val panelShape = RoundedCornerShape(10.dp)

/** A rounded panel in the bar colour: the app's only card surface. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Palette.bar, panelShape), content = content)
}

/** A faint divider between rows inside a panel. */
@Composable
fun PanelDivider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Palette.faint))
}

/** A section label: dim pixel caps over a faint rule, like the header band's divider. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelText(text.uppercase(), color = Palette.dim, heading = true, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.faint))
    }
}

/** The dim hint line the gadget keeps at the bottom of every screen. */
@Composable
fun HintLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        PixelText(text, color = Palette.dim, centered = true, wraps = true)
    }
}

/** Long text in the system font, dimmed by default. */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.dim,
    style: TextStyle = Type.body,
    centered: Boolean = false,
) {
    Text(text, modifier, color = color, style = style, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}

/** The header band: status indicator and headline, with an optional dim detail. */
@Composable
fun HeaderBand(indicator: Indicator, headline: String, color: Color = Palette.text, detail: String? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusIndicator(indicator, size = 22.dp)
            PixelText(headline, scale = 3, color = color, wraps = true, heading = true)
        }
        if (detail != null) BodyText(detail)
    }
}

/**
 * The device's hero layout (`Ui::draw_hero`): the mascot, a headline in the
 * state's colour, and a dim detail underneath, all centred.
 */
@Composable
fun Hero(
    headline: String,
    indicator: Indicator? = null,
    color: Color = Palette.text,
    detail: String? = null,
    mascotSide: Int = 168,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MascotView(Modifier.size(mascotSide.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (indicator != null) StatusIndicator(indicator, size = 18.dp)
            PixelText(headline, scale = 3, color = color, centered = true, wraps = true, heading = true)
        }
        if (detail != null) BodyText(detail, centered = true)
    }
}

enum class ButtonKind {
    /** Amber with dark ink: the one thing to do next. */
    PRIMARY,

    /** The bar colour: secondary actions. */
    SECONDARY,

    /** Dim red: removing or forgetting something. */
    DESTRUCTIVE,
}

/**
 * A full-width button with a pixel-font label, shaped like the device's
 * answer buttons (`Ui::draw_hero`): a rounded fill, the label centred.
 */
@Composable
fun GadgetButton(
    title: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val fill = when (kind) {
        ButtonKind.PRIMARY -> Palette.accent
        ButtonKind.SECONDARY -> Palette.bar
        ButtonKind.DESTRUCTIVE -> Palette.redDim
    }
    val ink = if (kind == ButtonKind.PRIMARY) Palette.accentInk else Palette.text
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (pressed) Color.Black.copy(alpha = 0.18f).compositeOver(fill) else fill, RoundedCornerShape(8.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Button
                if (!enabled) disabled()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) StatusIndicator(Indicator.Spinner(if (kind == ButtonKind.PRIMARY) Tone.ACCENT_INK else Tone.TEXT), size = 16.dp)
        PixelText(title, color = ink, centered = true, wraps = true)
    }
}

/**
 * A row in a panel, highlighted while pressed. [description] replaces what
 * TalkBack reads for the row's contents.
 */
@Composable
fun PanelRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Int = 56,
    description: String? = null,
    clickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .background(if (pressed) Palette.faint else Color.Transparent)
            .clickable(interaction, indication = null, enabled = enabled, onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/**
 * A row in a panel that opens another screen: a pixel title, a dim one-line
 * summary, and a chevron drawn in the pixel font.
 */
@Composable
fun NavigationRow(title: String, summary: String?, enabled: Boolean = true, onClick: () -> Unit) {
    PanelRow(onClick, enabled = enabled, description = listOfNotNull(title, summary).joinToString(", ")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelText(title)
                if (summary != null) BodyText(summary, style = Type.subheadline)
            }
            Spacer(Modifier.width(8.dp))
            PixelText(">", color = Palette.dim)
        }
    }
}

/**
 * A label and value inside a panel, the labels in one column so the values
 * line up. Stacks vertically at large font sizes.
 *
 * [breaksAfter]: characters after which a long value may wrap, so an
 * address breaks after `/` or `.` rather than inside its port number.
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Palette.text,
    monospaced: Boolean = false,
    breaksAfter: Set<Char> = emptySet(),
) {
    val large = LocalDensity.current.fontScale >= 1.3f
    val shown = if (breaksAfter.isEmpty()) value else wrappable(value, breaksAfter)
    val valueText: @Composable (Modifier) -> Unit = { modifier ->
        val style = if (monospaced) Type.body.merge(Type.mono) else Type.body
        if (breaksAfter.isEmpty()) {
            SelectionContainer(modifier) { Text(shown, color = valueColor, style = style) }
        } else {
            Text(shown, modifier, color = valueColor, style = style)
        }
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .clearAndSetSemantics { contentDescription = "$label, $value" }
    if (large) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelText(label.uppercase(), color = Palette.dim)
            valueText(Modifier)
        }
    } else {
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // One column for every label, wide enough for the longest ("DEVICE ID") at scale 2.
            Box(Modifier.width(labelColumnWidth()).padding(top = 4.dp)) { PixelText(label.uppercase(), color = Palette.dim) }
            valueText(Modifier.weight(1f))
        }
    }
}

@Composable
private fun labelColumnWidth() = with(LocalDensity.current) { (PixelFont.width(10) * pixelSize(2, this)).toDp() }

/** A segmented choice with pixel-font labels; the selected segment is amber. */
@Composable
fun <T> PixelSegmented(
    options: List<SegmentOption<T>>,
    selection: T?,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val segments: @Composable (Modifier) -> Unit = { itemModifier ->
        for (option in options) {
            val selected = option.value == selection
            Box(
                itemModifier
                    .heightIn(min = 48.dp)
                    .background(if (selected) Palette.accent else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(option.value) }
                    .clearAndSetSemantics {
                        contentDescription = option.spoken ?: option.title
                        role = Role.RadioButton
                        this.selected = selected
                        if (!enabled) disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                PixelText(option.title, color = if (selected) Palette.accentInk else Palette.text, centered = true)
            }
        }
    }
    val container = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.45f)
        .background(Palette.background, RoundedCornerShape(9.dp))
        .padding(4.dp)
    if (stacked) {
        Column(container, verticalArrangement = Arrangement.spacedBy(4.dp)) { segments(Modifier.fillMaxWidth()) }
    } else {
        Row(container, horizontalArrangement = Arrangement.spacedBy(4.dp)) { segments(Modifier.weight(1f)) }
    }
}

data class SegmentOption<T>(val value: T, val title: String, val spoken: String? = null)

/** A bordered box for a text field on the gadget's background, outlined red when its value was refused. */
fun Modifier.gadgetInput(invalid: Boolean = false): Modifier = this
    .fillMaxWidth()
    .heightIn(min = 48.dp)
    .background(Palette.background, RoundedCornerShape(8.dp))
    .border(1.dp, if (invalid) Palette.red else Palette.faint, RoundedCornerShape(8.dp))

/** A minimum width for the Save and Clear buttons beside a field. */
val sideButtonWidth = Modifier.widthIn(min = 104.dp)
