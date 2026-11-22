package com.jarforge.ide.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarforge.ide.R
import com.jarforge.ide.ui.theme.JarforgeColors

/** A key of the symbol toolbar. What each key does is decided where it is handled. */
sealed interface EditorKey {
    val label: String
    val description: String

    /** Inserts [text] at the cursor, replacing the selection. */
    data class Symbol(val text: String, override val description: String) : EditorKey {
        override val label: String get() = text
    }

    /** Inserts [open] and [close] around the cursor or the selection. */
    data class SymbolPair(
        val open: String,
        val close: String,
        override val description: String
    ) : EditorKey {
        override val label: String get() = open + close
    }

    /** Inserts one indentation level, replacing the selection. */
    data object Tab : EditorKey {
        override val label: String = "Tab"
        override val description: String = "Insert ${EditorTextOps.INDENT.length} spaces"
    }

    /** Moves the cursor like the hardware arrow key. Never changes the text. */
    data object CursorLeft : EditorKey {
        override val label: String = "←"
        override val description: String = "Move cursor left"
    }

    data object CursorRight : EditorKey {
        override val label: String = "→"
        override val description: String = "Move cursor right"
    }
}

private val KeyGroups: List<List<EditorKey>> = listOf(
    listOf(EditorKey.CursorLeft, EditorKey.CursorRight),
    listOf(EditorKey.Tab),
    listOf(
        EditorKey.SymbolPair("{", "}", "Braces"),
        EditorKey.SymbolPair("(", ")", "Parentheses"),
        EditorKey.SymbolPair("[", "]", "Brackets"),
        EditorKey.SymbolPair("\"", "\"", "Double quotes"),
        EditorKey.SymbolPair("'", "'", "Single quotes")
    ),
    listOf(
        EditorKey.Symbol(";", "Semicolon"),
        EditorKey.Symbol(".", "Dot"),
        EditorKey.Symbol(",", "Comma"),
        EditorKey.Symbol(":", "Colon"),
        EditorKey.Symbol("=", "Equals"),
        EditorKey.Symbol("<", "Less than"),
        EditorKey.Symbol(">", "Greater than"),
        EditorKey.Symbol("+", "Plus"),
        EditorKey.Symbol("-", "Minus"),
        EditorKey.Symbol("*", "Asterisk"),
        EditorKey.Symbol("/", "Slash"),
        EditorKey.Symbol("!", "Exclamation mark"),
        EditorKey.Symbol("&", "Ampersand"),
        EditorKey.Symbol("|", "Vertical bar")
    )
)

private val ToolbarHeight = 44.dp

/**
 * One compact row above the keyboard: Undo and Redo on the left, then the scrollable
 * symbol keys. Stateless: it only reports what was pressed.
 *
 * No key can take focus ([focusProperties] with canFocus = false), so pressing one never
 * moves focus away from the text field and never hides the keyboard.
 */
@Composable
fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onKey: (EditorKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JarforgeColors.Panel)
    ) {
        HorizontalDivider(color = JarforgeColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolbarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIconKey(
                iconRes = R.drawable.ic_undo,
                description = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )
            ToolbarIconKey(
                iconRes = R.drawable.ic_redo,
                description = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )
            ToolbarDivider()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyGroups.forEachIndexed { index, group ->
                    if (index > 0) ToolbarDivider()
                    group.forEach { key ->
                        ToolbarKey(
                            label = key.label,
                            description = key.description,
                            onClick = { onKey(key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarKey(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(ToolbarHeight)
            .widthIn(min = 40.dp)
            .focusProperties { canFocus = false }
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = JarforgeColors.TextPrimary
            )
        )
    }
}

@Composable
private fun ToolbarIconKey(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ToolbarHeight)
            .focusProperties { canFocus = false }
            .clickable(
                enabled = enabled,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = if (enabled) {
                JarforgeColors.TextPrimary
            } else {
                JarforgeColors.TextSecondary.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(22.dp)
            .background(JarforgeColors.Border)
    )
}
