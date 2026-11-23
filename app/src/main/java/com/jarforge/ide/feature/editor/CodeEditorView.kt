package com.jarforge.ide.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarforge.ide.ui.theme.JarforgeColors

private const val MIN_GUTTER_DIGITS = 3

private val EditorVerticalPadding = 10.dp
private val GutterStartPadding = 8.dp
private val GutterEndPadding = 10.dp
private val TextStartPadding = 10.dp
private val TextEndPadding = 12.dp

/**
 * The code editing surface: line-number gutter, current-line highlight and the text field.
 *
 * - The text field owns text, cursor, selection and vertical scrolling (through the hoisted
 *   [scrollState]); nothing here changes the text or intercepts touches.
 * - The gutter and the highlight are only drawn. Both read the text layout, the scroll
 *   position and the selection during the draw phase, so they follow every change without
 *   recomposing the editor.
 * - Soft-wrapped lines get one number, at their first visual line.
 *
 * The keyboard inset is NOT handled here: the parent applies imePadding() to this view and
 * the symbol toolbar together. [interactionSource] lets the parent observe the field's focus.
 *
 * Pressing Enter continues the indentation ([AutoIndentTransformation]); everything else the
 * user types or pastes is left as it is.
 *
 * The selected [language] chooses a visual-only syntax transformation. This is
 * visual only: the text in [textState] is never changed, and only colors are applied, so
 * wrapping, line numbers and the cursor are not affected.
 *
 * This is the single place the editor surface lives; later stages
 * extend it without touching the screen or the ViewModel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditorView(
    textState: TextFieldState,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    language: EditorLanguage = EditorLanguage.PLAIN
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // The same style is used for the text and for the numbers, with fixed line metrics
    // (Trim.None, Center), so every line has identical geometry and numbers sit exactly
    // on the baseline of their text line.
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = JarforgeColors.TextPrimary,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val normalNumberStyle = remember(codeStyle) { codeStyle.copy(color = EditorColors.GutterText) }
    val currentNumberStyle = remember(codeStyle) {
        codeStyle.copy(color = EditorColors.GutterTextCurrent)
    }

    // Reads the latest text layout. Reading it while drawing subscribes the draw to layout changes.
    var layoutProvider by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val indexCache = remember { LineIndexCache() }

    // Width of the gutter follows the number of logical lines (not wrapped lines, so a
    // narrower text area can never feed back into the gutter width).
    val gutterDigits by remember {
        derivedStateOf {
            val text = layoutProvider?.invoke()?.layoutInput?.text?.text
            val lines = if (text != null) indexCache.get(text).lineCount else 1
            maxOf(MIN_GUTTER_DIGITS, lines.toString().length)
        }
    }
    val digitsWidthPx = remember(gutterDigits, normalNumberStyle, measurer) {
        measurer.measure("9".repeat(gutterDigits), normalNumberStyle).size.width
    }
    val gutterWidth = with(density) { digitsWidthPx.toDp() } + GutterStartPadding + GutterEndPadding

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(JarforgeColors.EditorBackground)
            .padding(vertical = EditorVerticalPadding)
            .clipToBounds()
            .drawBehind {
                drawCurrentLineHighlight(
                    layout = layoutProvider?.invoke(),
                    indexCache = indexCache,
                    selection = textState.selection,
                    scroll = scrollState.value.toFloat()
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
        ) {
            drawLineNumbers(
                layout = layoutProvider?.invoke(),
                indexCache = indexCache,
                selection = textState.selection,
                scroll = scrollState.value.toFloat(),
                measurer = measurer,
                normalStyle = normalNumberStyle,
                currentStyle = currentNumberStyle,
                rightPadding = GutterEndPadding.toPx()
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(EditorColors.GutterDivider)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = TextStartPadding, end = TextEndPadding)
        ) {
            if (textState.text.isEmpty()) {
                Text(
                    text = "Empty file — tap here to start typing...",
                    style = codeStyle,
                    color = JarforgeColors.TextSecondary
                )
            }
            BasicTextField(
                state = textState,
                textStyle = codeStyle,
                cursorBrush = SolidColor(JarforgeColors.Blue),
                scrollState = scrollState,
                interactionSource = interactionSource,
                inputTransformation = AutoIndentTransformation.then(Utf8FileSizeTransformation),
                outputTransformation = when (language) {
                    EditorLanguage.JAVA -> JavaHighlightTransformation
                    EditorLanguage.TOML -> TomlHighlightTransformation
                    EditorLanguage.MARKDOWN -> MarkdownHighlightTransformation
                    EditorLanguage.JSON -> JsonHighlightTransformation
                    EditorLanguage.PLAIN -> null
                },
                onTextLayout = { getResult ->
                    if (layoutProvider !== getResult) {
                        layoutProvider = getResult
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Highlights the whole logical line that holds the cursor (all its wrapped visual lines).
 * Nothing is drawn while text is selected, so the selection stays easy to read.
 */
private fun DrawScope.drawCurrentLineHighlight(
    layout: TextLayoutResult?,
    indexCache: LineIndexCache,
    selection: TextRange,
    scroll: Float
) {
    if (layout == null || !selection.collapsed) return

    // Everything is derived from the text the layout was built from, so it always matches.
    val index = indexCache.get(layout.layoutInput.text.text)
    val line = index.lineOfOffset(selection.end)
    val start = index.lineStart(line)
    val lastCharOffset = maxOf(start, index.lineEnd(line) - 1)

    val firstVisual = layout.getLineForOffset(start)
    val lastVisual = layout.getLineForOffset(lastCharOffset)
    val top = layout.getLineTop(firstVisual) - scroll
    val bottom = layout.getLineBottom(lastVisual) - scroll

    drawRect(
        color = EditorColors.CurrentLine,
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top)
    )
}

/** Draws the numbers of the visible logical lines, right-aligned, at their first visual line. */
private fun DrawScope.drawLineNumbers(
    layout: TextLayoutResult?,
    indexCache: LineIndexCache,
    selection: TextRange,
    scroll: Float,
    measurer: TextMeasurer,
    normalStyle: TextStyle,
    currentStyle: TextStyle,
    rightPadding: Float
) {
    if (layout == null || layout.lineCount == 0) return

    val index = indexCache.get(layout.layoutInput.text.text)
    val currentLine = if (selection.collapsed) index.lineOfOffset(selection.end) else -1

    val lastVisualLine = layout.lineCount - 1
    val firstVisible = layout.getLineForVerticalPosition(scroll).coerceIn(0, lastVisualLine)
    val lastVisible = layout.getLineForVerticalPosition(scroll + size.height)
        .coerceIn(0, lastVisualLine)

    for (visual in firstVisible..lastVisible) {
        val start = layout.getLineStart(visual)
        val logical = index.lineOfOffset(start)
        // A wrapped continuation line does not start a logical line: no number.
        if (index.lineStart(logical) != start) continue

        val label = measurer.measure(
            (logical + 1).toString(),
            if (logical == currentLine) currentStyle else normalStyle
        )
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = size.width - rightPadding - label.size.width,
                y = layout.getLineTop(visual) - scroll
            )
        )
    }
}
