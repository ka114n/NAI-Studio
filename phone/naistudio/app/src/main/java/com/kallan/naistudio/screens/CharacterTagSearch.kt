package com.kallan.naistudio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.models.UserTagDictionary
import com.kallan.naistudio.state.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class CharacterCandidates(
    val query: String = "", val matches: List<UserTagDictionary.Match> = emptyList(),
    val loading: Boolean = false, val failed: Boolean = false,
)

/** Standalone character-section search; selecting a candidate creates an expanded card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CharacterTagSearch(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val view = LocalView.current
    val ref = LocalRef.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var visibleHeightPx by remember { mutableIntStateOf(view.height) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val requester = remember { BringIntoViewRequester() }
    val candidateScroll = rememberScrollState()
    // Use the actual dialog window's visible frame, rather than subtracting IME twice after resize.
    LaunchedEffect(view, imeBottomPx) {
        withFrameNanos { }
        val frame = android.graphics.Rect()
        view.getWindowVisibleDisplayFrame(frame)
        visibleHeightPx = frame.height().coerceAtLeast(0)
    }
    val candidateHeight = with(density) { (visibleHeightPx / 3).toDp() }.coerceIn(80.dp, 240.dp)
    val dictionary = state.bilingualTagDictionary
    LaunchedEffect(Unit) { state.prepareTagDictionary() }
    val result by produceState(CharacterCandidates(), query, dictionary) {
        val request = query
        val snapshot = dictionary
        value = CharacterCandidates(query = request, loading = request.isNotBlank())
        if (request.isNotBlank()) {
            delay(150)
            value = try {
                CharacterCandidates(request, withContext(Dispatchers.IO) {
                    UserTagDictionary.search(request, snapshot, limit = 24, charactersOnly = true)
                })
            } catch (error: CancellationException) { throw error }
              catch (error: Exception) { CharacterCandidates(request, failed = true) }
        }
    }
    val current = result.takeIf { it.query == query } ?: CharacterCandidates(query, loading = true)
    val showCandidates = focused && query.isNotBlank() && !dismissed
    LaunchedEffect(query) { candidateScroll.scrollTo(0) }
    LaunchedEffect(showCandidates, panelHeightPx, imeBottomPx) {
        if (showCandidates) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester)
        .onSizeChanged { panelHeightPx = it.height }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; dismissed = false },
            placeholder = { Text(t("anima.characterTagSearch")) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { dismissed = false }),
            modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "character-tag-search"))
                .onFocusChanged { focused = it.isFocused; if (focused) dismissed = false }
                ,
        )
        // Same-window content stays clipped to the IME-resized drawer; no popup can cover the keyboard.
        if (showCandidates) {
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = candidateHeight),
            shape = RoundedCornerShape(12.dp),
            color = ref.field,
            border = BorderStroke(1.dp, ref.border),
        ) {
        Column(Modifier.fillMaxWidth().verticalScroll(candidateScroll)) {
            when {
                !state.tagDictionaryInstalled -> DropdownMenuItem(
                    text = { Text(t("dictionary.notDownloaded")) },
                    onClick = { dismissed = true; state.prepareTagDictionary(requestDownload = true) },
                )
                current.loading || state.tagDictionaryPreparing -> DropdownMenuItem(
                    text = { Text(t("dictionary.searching")) }, onClick = {}, enabled = false,
                )
                current.failed -> DropdownMenuItem(
                    text = { Text(t("dictionary.searchFailed")) }, onClick = {}, enabled = false,
                )
                current.matches.isEmpty() -> DropdownMenuItem(
                    text = { Text(t("anima.noCharacterCandidates")) }, onClick = {}, enabled = false,
                )
                else -> current.matches.forEach { candidate ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(candidate.tag)
                                candidate.translations.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
                                    Text(it.joinToString(" / "), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = {
                            if (state.addCharacterFromDictionary(candidate)) {
                                query = ""
                                dismissed = true
                                focus.clearFocus()
                            }
                        },
                    )
                }
            }
        }
        }
        }
    }
}
