package com.kallan.naistudio.screens

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Character prompt editing has no dictionary queries or candidate popup. */
@Composable
internal fun CharacterPromptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder) }, textStyle = textStyle,
        minLines = minLines, modifier = modifier)
}
