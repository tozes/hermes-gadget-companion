package com.tozes.hermescompanion.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tozes.hermescompanion.core.ConsoleEntry
import com.tozes.hermescompanion.core.GadgetState
import com.tozes.hermescompanion.screens.LocalStore
import com.tozes.hermescompanion.style.GadgetTopBar
import com.tozes.hermescompanion.style.Palette
import com.tozes.hermescompanion.style.PixelText
import com.tozes.hermescompanion.style.Type
import kotlinx.coroutines.launch

private val quickCommands = listOf("status", "phone", "get volume", "diag", "help")
private val lineStyle = Type.footnote.merge(Type.mono)

/** A raw terminal for developers: type a console line, see the reply. */
@Composable
fun ConsoleScreen(state: GadgetState, back: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var line by rememberSaveable { mutableStateOf("") }
    var showAppLines by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val entries = if (showAppLines) state.console else state.console.filter { it.typed }
    val enabled = !state.connectionLost

    fun send(text: String) {
        val command = text.trim()
        if (command.isEmpty()) return
        line = ""
        scope.launch { store.send(command, typed = true) }
    }

    Column(Modifier.fillMaxSize().background(Palette.background).imePadding()) {
        GadgetTopBar("Console", onBack = back) {
            Box {
                Box(
                    Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Console options") { menu = true }
                        .semantics { contentDescription = "Console options" },
                    contentAlignment = Alignment.Center,
                ) { PixelText("...", color = Palette.accent) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.bar) {
                    DropdownMenuItem(
                        text = { Text("Show the app's own commands", color = Palette.text) },
                        trailingIcon = { if (showAppLines) PixelText("x", color = Palette.accent) },
                        onClick = {
                            showAppLines = !showAppLines
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear", color = Palette.red) },
                        onClick = {
                            store.clearConsole()
                            menu = false
                        },
                    )
                }
            }
        }
        Scrollback(entries, Modifier.weight(1f))
        QuickBar(enabled) { send(it) }
        InputBar(line, { line = it }, enabled) { send(line) }
    }
}

@Composable
private fun Scrollback(entries: List<ConsoleEntry>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty()) list.animateScrollToItem(entries.lastIndex)
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entries.isEmpty()) {
            item { Text("Type a command such as status or get volume. Replies start with @.", color = Palette.dim, style = lineStyle) }
        }
        items(entries, key = { it.id }) { ConsoleLine(it) }
    }
}

/**
 * One scrollback line: commands after a prompt, replies coloured by their
 * tag (`@ok` green, `@error` red), notes from the app dimmed.
 */
@Composable
private fun ConsoleLine(entry: ConsoleEntry) {
    val prefix = when (entry.kind) {
        ConsoleEntry.Kind.COMMAND -> if (entry.typed) ">" else "app>"
        ConsoleEntry.Kind.REPLY -> " "
        ConsoleEntry.Kind.NOTE -> "#"
    }
    val spokenKind = when (entry.kind) {
        ConsoleEntry.Kind.COMMAND -> if (entry.typed) "Sent" else "App sent"
        ConsoleEntry.Kind.REPLY -> "Reply"
        ConsoleEntry.Kind.NOTE -> "Note"
    }
    val color = when (entry.kind) {
        ConsoleEntry.Kind.COMMAND -> if (entry.typed) Palette.text else Palette.dim
        ConsoleEntry.Kind.NOTE -> Palette.dim
        ConsoleEntry.Kind.REPLY -> when {
            entry.text.startsWith("@ok") -> Palette.green
            entry.text.startsWith("@error") -> Palette.red
            entry.text.startsWith("@") -> Palette.text
            else -> Palette.dim
        }
    }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$spokenKind: ${entry.text}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            prefix,
            Modifier.clearAndSetSemantics {},
            color = if (entry.kind == ConsoleEntry.Kind.COMMAND && entry.typed) Palette.accent else Palette.dim,
            style = lineStyle,
        )
        SelectionContainer(Modifier.weight(1f)) { Text(entry.text, color = color, style = lineStyle) }
    }
}

/**
 * The quick commands, spread across the width when they fit, and scrolling
 * sideways (from inside the margins) when they don't.
 */
@Composable
private fun QuickBar(enabled: Boolean, send: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (enabled) 1f else 0.45f)) {
        val padding = with(density) { 20.dp.roundToPx() }
        val spacing = with(density) { 6.dp.roundToPx() }
        val needed = quickCommands.sumOf { measurer.measure(it, lineStyle).size.width + padding } + spacing * (quickCommands.size - 1)
        val fits = needed <= constraints.maxWidth - with(density) { 32.dp.roundToPx() }
        val row = if (fits) {
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        } else {
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        }
        Row(row, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (command in quickCommands) {
                Box(
                    (if (fits) Modifier.weight(1f) else Modifier)
                        .heightIn(min = 48.dp)
                        .background(Palette.bar, RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Sends $command") { send(command) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(command, color = Palette.text, style = lineStyle, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun InputBar(line: String, onChange: (String) -> Unit, enabled: Boolean, send: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.bar)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelText("gadget>", color = Palette.accent, modifier = Modifier.clearAndSetSemantics {})
        BasicTextField(
            value = line,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = Type.body.merge(Type.mono).copy(color = Palette.text),
            cursorBrush = SolidColor(Palette.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .focusRequester(focus)
                .semantics { contentDescription = "Console command" },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (line.isEmpty()) Text("command", color = Palette.dim, style = Type.body.merge(Type.mono))
                    field()
                }
            },
        )
        val canSend = enabled && line.isNotBlank()
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(enabled = canSend, role = Role.Button) { send() }
                .clearAndSetSemantics {
                    contentDescription = "Send"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) { PixelText("Send", color = if (line.isEmpty()) Palette.dim else Palette.accent) }
    }
}
