package com.jarforge.ide.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jarforge.ide.R
import com.jarforge.ide.ui.theme.JarforgeColors

@Composable
fun JarforgeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIconRes: Int? = null,
    onClear: (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else null,
        leadingIcon = if (leadingIconRes != null) {
            { Icon(painter = painterResource(leadingIconRes), contentDescription = null) }
        } else null,
        trailingIcon = if (onClear != null && value.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear"
                    )
                }
            }
        } else null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JarforgeColors.Blue,
            unfocusedBorderColor = JarforgeColors.Border,
            focusedContainerColor = JarforgeColors.Panel,
            unfocusedContainerColor = JarforgeColors.Panel,
            cursorColor = JarforgeColors.Blue,
            focusedLeadingIconColor = JarforgeColors.TextSecondary,
            unfocusedLeadingIconColor = JarforgeColors.TextSecondary,
            focusedTrailingIconColor = JarforgeColors.TextSecondary,
            unfocusedTrailingIconColor = JarforgeColors.TextSecondary,
            focusedPlaceholderColor = JarforgeColors.TextSecondary,
            unfocusedPlaceholderColor = JarforgeColors.TextSecondary
        )
    )
}
