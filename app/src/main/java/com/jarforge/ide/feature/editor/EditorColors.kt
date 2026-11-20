package com.jarforge.ide.feature.editor

import androidx.compose.ui.graphics.Color
import com.jarforge.ide.ui.theme.JarforgeColors

/** Colors used only inside the code editor. Built on the approved app palette. */
internal object EditorColors {
    val CurrentLine: Color = JarforgeColors.Blue.copy(alpha = 0.10f)
    val GutterText: Color = Color(0xFF5F6B7C)
    val GutterTextCurrent: Color = JarforgeColors.TextPrimary
    val GutterDivider: Color = JarforgeColors.Border
}
