package com.tozes.hermescompanion.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** Opens the hidden debug menu; provided by the root screen. */
val LocalDebugMenu = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Opens the debug menu on a one-second press, or with the TalkBack action
 * "Debug menu". Attached to every screen's title.
 */
fun Modifier.debugMenuTrigger(open: (() -> Unit)?): Modifier {
    if (open == null) return this
    return this
        .pointerInput(open) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val released = withTimeoutOrNull(1000) { waitForUpOrCancellation() }
                if (released == null) open()
            }
        }
        .semantics { customActions = listOf(CustomAccessibilityAction("Debug menu") { open(); true }) }
}

/** The status bar's height above a bar drawn edge to edge, and the side cutouts. */
private val barInsets: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

/**
 * The device's top bar (`Ui::draw_top`): a title on the left, then an
 * optional battery and a coloured link dot with a short label on the right.
 */
@Composable
fun TopBar(
    title: String,
    linkColor: Color? = null,
    linkLabel: String? = null,
    spokenState: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.bar)
            .windowInsetsPadding(barInsets)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelText(title.uppercase(), heading = true, modifier = Modifier.weight(1f, fill = false).debugMenuTrigger(LocalDebugMenu.current))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = spokenState ?: linkLabel.orEmpty() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (linkColor != null) Dot(linkColor)
            if (linkLabel != null) PixelText(linkLabel, color = Palette.dim)
        }
    }
}

/**
 * A navigation bar in the gadget's bar colour: a back button, the screen's
 * title in pixel caps (centred), and actions on the right.
 */
@Composable
fun GadgetTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String? = null,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Palette.bar)
            .windowInsetsPadding(barInsets)
            .heightIn(min = 56.dp),
    ) {
        if (onBack != null) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = backDescription, onClick = onBack)
                    .semantics(mergeDescendants = true) { contentDescription = backDescription }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelText("<", color = Palette.accent)
                if (backLabel != null) Text(backLabel, color = Palette.accent, style = Type.body)
            }
        }
        PixelText(
            title.uppercase(),
            heading = true,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).debugMenuTrigger(LocalDebugMenu.current),
        )
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * A screen's scrolling content on the gadget's background, with the usual
 * margins, clear of the navigation bar and the keyboard, and pull to refresh
 * when [onRefresh] is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    spacing: Int = 24,
    /** False when something below (a hint line, an input bar) keeps clear of the navigation bar instead. */
    bottomInsets: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
    Box(modifier.fillMaxSize().then(if (bottomInsets) Modifier.navigationBarsPadding().imePadding() else Modifier)) {
        if (onRefresh == null) {
            scroll()
        } else {
            val state = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                state = state,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = state,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = Palette.bar,
                        color = Palette.accent,
                    )
                },
            ) { scroll() }
        }
    }
}
