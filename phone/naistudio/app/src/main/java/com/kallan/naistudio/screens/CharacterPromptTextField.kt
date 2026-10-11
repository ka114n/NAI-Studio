package com.kallan.naistudio.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.ui.RefOutlinedTextField

/** Plain editing; the scrollable role drawer follows the caret when the IME changes its viewport. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CharacterPromptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    var editor by remember { mutableStateOf(TextFieldValue(value)) }
    val selection = TextRange(editor.selection.start.coerceIn(0, value.length),
        editor.selection.end.coerceIn(0, value.length))
    val fieldValue = if (editor.text == value) editor else editor.copy(text = value,
        selection = selection, composition = null)
    SideEffect { if (editor != fieldValue) editor = fieldValue }
    var focused by remember { mutableStateOf(false) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val cursorRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val decorationOffset = with(density) { Offset(12.dp.toPx(), 10.dp.toPx()) }

    RefOutlinedTextField(
        value = fieldValue,
        onValueChange = { updated ->
            editor = updated
            if (updated.text != value) onValueChange(updated.text)
        },
        placeholder = { Text(placeholder) },
        textStyle = textStyle,
        minLines = minLines,
        onTextLayout = { textLayout = it },
        modifier = modifier.bringIntoViewRequester(cursorRequester)
            .onFocusChanged { focused = it.isFocused },
    )
    LaunchedEffect(focused, fieldValue, textLayout, imeBottomPx) {
        if (!focused) return@LaunchedEffect
        withFrameNanos { }
        val layout = textLayout ?: return@LaunchedEffect
        if (layout.layoutInput.text.text != fieldValue.text) return@LaunchedEffect
        val offset = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
        cursorRequester.bringIntoView(layout.getCursorRect(offset).translate(decorationOffset))
    }
}
