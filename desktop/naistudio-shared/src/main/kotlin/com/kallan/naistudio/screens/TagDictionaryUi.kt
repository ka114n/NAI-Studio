package com.kallan.naistudio.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.*
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.NaiSkinTokens
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.LocalPromptHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class TagColors(val text: Color, val muted: Color, val accent: Color, val field: Color, val border: Color)
private data class TagTextPlacement(val layout: TextLayoutResult, val prefixLength: Int)
private data class DictionarySearchResult(
    val query: String = "",
    val matches: List<UserTagDictionary.Match> = emptyList(),
    val loading: Boolean = false, val failed: Boolean = false,
)

internal fun tagDragTopLeft(pointer: Offset, origin: Offset, grabOffset: Offset): Offset = pointer - origin - grabOffset

/** Shared only within this generation screen; never persisted with prompts or settings. */
@Stable
internal class TagDragTrashState {
    var owner by mutableStateOf<String?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var bounds by mutableStateOf(Rect.Zero)
    val visible: Boolean get() = owner != null
    val hovered: Boolean get() = contains(pointer)
    fun start(field: String, position: Offset) { bounds = Rect.Zero; pointer = position; owner = field }
    fun move(field: String, position: Offset) { if (owner == field) pointer = position }
    fun stop(field: String) { if (owner == field) { owner = null; bounds = Rect.Zero } }
    fun contains(position: Offset): Boolean {
        if (!visible || bounds.width <= 0f || bounds.height <= 0f) return false
        return (position - bounds.center).getDistance() <= minOf(bounds.width, bounds.height) / 2f
    }
}

private val LocalTagDragTrash = compositionLocalOf<TagDragTrashState?> { null }

@Composable
internal fun TagDragScope(trash: TagDragTrashState = remember { TagDragTrashState() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTagDragTrash provides trash, content = content)
}

/** Overlay consumes no layout height, so the toolbar and prompt cards never move during a drag. */
@Composable
internal fun TagTrashToolbar(state: AppState, targetField: String, content: @Composable () -> Unit) {
    val trash = LocalTagDragTrash.current
    Box(Modifier.fillMaxWidth()) {
        content()
        if (trash?.owner == targetField) {
            Box(Modifier.matchParentSize().zIndex(10f)) {
                TagTrashTarget(state, trash, Modifier.align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (-56).dp).wrapContentSize(Alignment.TopEnd, unbounded = true))
            }
        }
    }
}

@Composable
private fun TagTrashTarget(state: AppState, trash: TagDragTrashState, modifier: Modifier) {
    val colors = tagColors()
    val red = MaterialTheme.colorScheme.error
    val opening by animateFloatAsState(if (trash.hovered) 1f else 0f, tween(140), label = "tagTrashLid")
    val description = RuntimeText.text(state.settings.language, "common.delete")
    Surface(shape = CircleShape, color = if (trash.hovered) MaterialTheme.colorScheme.errorContainer else colors.field,
        border = BorderStroke(if (trash.hovered) 2.dp else 1.dp, red), shadowElevation = 4.dp,
        modifier = modifier.size(48.dp).onGloballyPositioned { trash.bounds = it.boundsInRoot() }
            .semantics { contentDescription = description }) {
        Canvas(Modifier.padding(12.dp)) {
            val stroke = 1.8.dp.toPx()
            val body = Path().apply {
                moveTo(size.width * .23f, size.height * .37f)
                lineTo(size.width * .29f, size.height * .87f)
                quadraticBezierTo(size.width * .5f, size.height * .95f, size.width * .71f, size.height * .87f)
                lineTo(size.width * .77f, size.height * .37f)
            }
            drawPath(body, red, style = Stroke(stroke, cap = StrokeCap.Round))
            for (x in listOf(.42f, .58f)) drawLine(red, Offset(size.width * x, size.height * .46f),
                Offset(size.width * x, size.height * .77f), stroke, StrokeCap.Round)
            withTransform({
                translate(top = -size.height * .12f * opening)
                rotate(-32f * opening, pivot = Offset(size.width * .2f, size.height * .28f))
            }) {
                drawLine(red, Offset(size.width * .17f, size.height * .28f),
                    Offset(size.width * .83f, size.height * .28f), stroke, StrokeCap.Round)
                val handle = Path().apply {
                    moveTo(size.width * .38f, size.height * .26f)
                    lineTo(size.width * .4f, size.height * .12f)
                    lineTo(size.width * .6f, size.height * .12f)
                    lineTo(size.width * .62f, size.height * .26f)
                }
                drawPath(handle, red, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun tagColors() = TagColors(NaiSkinTokens.text(), NaiSkinTokens.muted(),
    NaiSkinTokens.accent(), NaiSkinTokens.field(), NaiSkinTokens.border())

@Composable
internal fun TagDictionaryTool(state: AppState) {
    val t: (String) -> String = { RuntimeText.text(state.settings.language, it) }
    val colors = tagColors()
    val clipboard = LocalClipboardManager.current
    var queryInput by remember { mutableStateOf(TextFieldValue()) }
    val query = queryInput.text
    var searchRevision by remember { mutableIntStateOf(0) }
    val queryFocus = LocalFocusManager.current
    var queryFocused by remember { mutableStateOf(false) }
    fun prepareNextSearch() {
        queryInput = queryInput.copy(selection = TextRange(0, queryInput.text.length), composition = null)
    }
    // Select after the pointer's focus/cursor update, not in its synchronous callback.
    LaunchedEffect(queryFocused) { if (queryFocused) prepareNextSearch() }
    var en by remember { mutableStateOf("") }
    var zh by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var bulkOpen by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<UserTagDictionary.Match?>(null) }
    var deleteCustomOnly by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<UserTagDictionary.Match?>(null) }
    var editedEnglish by remember { mutableStateOf("") }
    var editedChinese by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf("") }
    fun editEntry(entry: UserTagDictionary.Match) {
        editedEnglish = entry.tag
        editedChinese = entry.translations.joinToString("\n")
        editError = ""
        editingEntry = entry
    }
    val dictionary = state.bilingualTagDictionary
    val custom = remember(state.settings.customTagDictionaryJson) { UserTagDictionary.decode(state.settings.customTagDictionaryJson) }
    var resultLimit by remember(query, dictionary) { mutableIntStateOf(100) }
    val searchResult by produceState(DictionarySearchResult(), query, dictionary, resultLimit, searchRevision) {
        // Capture an immutable request; a new query cancels the previous producer.
        val requestQuery = query
        val requestDictionary = dictionary
        val requestLimit = resultLimit
        value = DictionarySearchResult(query = requestQuery, loading = requestQuery.isNotBlank())
        if (requestQuery.isNotBlank()) {
            delay(120)
            value = try {
                DictionarySearchResult(query = requestQuery, matches = withContext(Dispatchers.IO) {
                    UserTagDictionary.search(requestQuery, requestDictionary, requestLimit + 1)
                })
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
              catch (error: Exception) { DictionarySearchResult(query = requestQuery, failed = true) }
        }
    }
    // Never render a previous query's empty/result state while the new job starts.
    val currentSearchResult = searchResult.takeIf { it.query == query }
        ?: DictionarySearchResult(query = query, loading = query.isNotBlank())
    val results = currentSearchResult.matches
    LaunchedEffect(Unit) { state.prepareTagDictionary(requestDownload = true) }
    SectionCard(t("dictionary.title")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.tagDictionaryDownloading) {
                Text("${t("dictionary.downloading")} ${state.tagDictionaryDownloadProgress}%", color = colors.muted)
                LinearProgressIndicator(progress = { state.tagDictionaryDownloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
            } else if (!state.tagDictionaryInstalled) {
                Text(t("dictionary.notDownloaded"), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { state.prepareTagDictionary(requestDownload = true) }) { Text(t("dictionary.download")) }
            }
            dictionary.primary?.let {
                Text("${t("dictionary.mainCount")}: ${it.count}", color = colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.tagDictionaryPreparing) Text(t("dictionary.loading"), color = colors.muted, style = MaterialTheme.typography.bodySmall)
            if (state.tagDictionaryError) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("dictionary.failed"), Modifier.weight(1f), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { state.prepareTagDictionary(requestDownload = true) }) { Text(t("common.retry")) }
            }
            OutlinedTextField(queryInput, { queryInput = it }, label = { Text(t("dictionary.query")) },
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchRevision++; prepareNextSearch(); queryFocus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-query"))
                    .onFocusChanged { queryFocused = it.isFocused })
            if (query.isNotBlank()) {
                LazyColumn(Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        currentSearchResult.loading || state.tagDictionaryPreparing -> item { Text(t("dictionary.searching"), color = colors.muted) }
                        currentSearchResult.failed -> item { Text(t("dictionary.searchFailed"), color = MaterialTheme.colorScheme.error) }
                        results.isEmpty() -> item { Text(t("dictionary.missing"), color = colors.muted) }
                    }
                    items(results.take(resultLimit), key = { it.tag }) { result ->
                        Surface(color = colors.field, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(result.tag, Modifier.weight(1f), color = colors.text)
                                    IconButton(onClick = { editEntry(result) }) {
                                        Icon(Icons.Outlined.Edit, t("dictionary.edit"), tint = colors.accent)
                                    }
                                    IconButton(onClick = { pendingDeletion = result; deleteCustomOnly = false }) {
                                        Icon(Icons.Outlined.Delete, t("dictionary.deleteTitle"), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (result.translations.isNotEmpty()) Text(result.translations.joinToString(" / "), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(result.tag)); message = "common.copied"; prepareNextSearch(); queryFocus.clearFocus() }) { Text(t("common.copy")) }
                                    TextButton(enabled = !state.settings.promptLockPositive,
                                        onClick = { state.setParam { it.copy(positivePrompt = appendTag(it.positivePrompt, result.tag)) }; prepareNextSearch(); queryFocus.clearFocus() }) {
                                        Text(t("dictionary.apply"))
                                    }
                                }
                            }
                        }
                    }
                    if (results.size > resultLimit) item {
                        if (resultLimit < 2000) TextButton(onClick = { resultLimit += 100 }) { Text(t("dictionary.more")) }
                        else Text(t("dictionary.refine"), color = colors.muted)
                    }
                }
            }
        }
    }
    SectionCard(t("dictionary.add")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(en, { en = it }, label = { Text(t("dictionary.en")) }, singleLine = true, modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-en")))
            Text(t("dictionary.aliasHint"), color = colors.muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(zh, { zh = it }, label = { Text(t("dictionary.zh")) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-zh")))
            Row {
                TextButton(onClick = {
                    val aliases = UserTagDictionary.aliases(zh)
                    if (aliases.isNotEmpty() && aliases.all { UserTagDictionary.valid(en.trim(), it) }) {
                        state.supplementTagDictionary(mapOf(TagZhProtocol.key(en) to aliases), editingTag)
                        en = ""; zh = ""; editingTag = null; message = "dictionary.saved"
                    } else message = "dictionary.invalid"
                }) { Text(t("common.save")) }
                if (editingTag != null) TextButton(onClick = { en = ""; zh = ""; editingTag = null }) { Text(t("common.cancel")) }
                TextButton(onClick = { bulkOpen = !bulkOpen }) { Text(t("dictionary.import")) }
            }
            if (bulkOpen) {
                OutlinedTextField(pasted, { pasted = it }, label = { Text(t("dictionary.importHint")) },
                    minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-pasted")))
                TextButton(onClick = {
                    runCatching { UserTagDictionary.parseImport(pasted) }.fold(
                        onSuccess = { state.supplementTagDictionary(it); pasted = ""; message = "dictionary.saved" },
                        onFailure = { message = "dictionary.invalidImport" },
                    )
                }) { Text(t("common.save")) }
            }
            if (message.isNotEmpty()) Text(t(message), color = colors.text, style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionCard("${t("dictionary.custom")} (${custom.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = custom.isNotEmpty(), onClick = {
                clipboard.setText(AnnotatedString(custom.toSortedMap().entries.flatMap { entry -> entry.value.map { "${entry.key}\t$it" } }.joinToString("\n")))
                message = "common.copied"
            }) { Text(t("dictionary.export")) }
            val matches = remember(custom, query) { custom.entries.filter { it.key.contains(query, true) || it.value.any { alias -> alias.contains(query, true) } }.sortedBy { it.key } }
            if (matches.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().height(240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(matches, key = { it.key }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, color = colors.text)
                                Text(entry.value.joinToString(" / "), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { en = entry.key; zh = entry.value.joinToString("\n"); editingTag = entry.key }) { Text(t("dictionary.edit")) }
                            IconButton(onClick = { pendingDeletion = UserTagDictionary.Match(entry.key, entry.value); deleteCustomOnly = true }) {
                                Icon(Icons.Outlined.Delete, t("common.delete"), tint = colors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDeletion?.let { entry ->
        AlertDialog(
            containerColor = NaiSkinTokens.sidebar(),
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)),
            onDismissRequest = { pendingDeletion = null },
            title = { Text(t("dictionary.deleteTitle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.tag)
                    Text(entry.translations.joinToString(" / "))
                    Text(t(if (deleteCustomOnly) "dictionary.deleteCustomConfirm" else "dictionary.deleteConfirm"))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteCustomOnly) state.removeSupplementalTag(entry.tag) else state.deleteDictionaryTag(entry.tag)
                    if (editingTag == entry.tag) { en = ""; zh = ""; editingTag = null }
                    pendingDeletion = null
                }) { Text(t("common.delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text(t("common.cancel")) } },
        )
    }

    editingEntry?.let { entry ->
        AlertDialog(
            containerColor = NaiSkinTokens.sidebar(),
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(20.dp)),
            onDismissRequest = { editingEntry = null },
            title = { Text(t("dictionary.editEntry")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editedEnglish, { editedEnglish = it; editError = "" },
                        label = { Text(t("dictionary.en")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-edit-en")))
                    OutlinedTextField(editedChinese, { editedChinese = it; editError = "" },
                        label = { Text(t("dictionary.zh")) }, minLines = 2, maxLines = 6,
                        modifier = Modifier.fillMaxWidth().then(tagInputGuard(state, "dictionary-edit-zh")))
                    Text(t("dictionary.editHint"), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                    if (editError.isNotEmpty()) Text(t(editError), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val aliases = UserTagDictionary.aliases(editedChinese)
                    val key = TagZhProtocol.key(editedEnglish)
                    when {
                        aliases.isEmpty() || aliases.any { !UserTagDictionary.valid(editedEnglish.trim(), it) } -> editError = "dictionary.invalid"
                        key != entry.tag && dictionary.containsTag(key) -> editError = "dictionary.duplicate"
                        else -> {
                            state.updateDictionaryEntry(entry.tag, editedEnglish.trim(), aliases)
                            if (editingTag == entry.tag) { en = ""; zh = ""; editingTag = null }
                            editingEntry = null
                            message = "dictionary.saved"
                        }
                    }
                }) { Text(t("common.save")) }
            },
            dismissButton = { TextButton(onClick = { editingEntry = null }) { Text(t("common.cancel")) } },
        )
    }

}

private fun appendTag(prompt: String, added: String): String =
    if (prompt.isBlank()) added.trim() else prompt.trimEnd() + ", " + added.trim()

@Composable
internal fun TagModeButton(state: AppState, onActivate: () -> Unit) {
    val t: (String) -> String = { RuntimeText.text(state.settings.language, it) }
    val colors = tagColors()
    val focusManager = LocalFocusManager.current
    OutlinedButton(onClick = { focusManager.clearFocus(); onActivate(); state.setSettings { it.copy(positiveTagCardMode = !it.positiveTagCardMode) } },
        shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.field),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
        Text(t(if (state.settings.positiveTagCardMode) "prompt.textMode" else "prompt.tagMode"),
            color = colors.accent, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BilingualTagEditor(state: AppState, value: String, readOnly: Boolean, onActivate: () -> Unit, onValueChange: (String) -> Unit, fieldKey: String = "positive") {
    val colors = tagColors()
    val t: (String) -> String = { RuntimeText.text(state.settings.language, it) }
    val tags = remember(value) { PromptTagCards.parse(value) }
    val dictionary = state.bilingualTagDictionary
    val currentTags by rememberUpdatedState(tags)
    val currentValue by rememberUpdatedState(value)
    val currentChange by rememberUpdatedState(onValueChange)
    val activate by rememberUpdatedState(onActivate)
    val bounds = remember { mutableMapOf<Int, Rect>() }
    val textPlacements = remember { mutableMapOf<Int, TagTextPlacement>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val trash = LocalTagDragTrash.current
    var dragged by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf(TextFieldValue()) }
    var editSnapshot by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val weightStepPx = with(density) { 16.dp.toPx() }
    val latestStepPx by rememberUpdatedState(weightStepPx)
    val paddingPx = with(density) { Offset(10.dp.toPx(), 8.dp.toPx()) }
    val highlight = LocalPromptHighlight.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall
    val translationStyle = MaterialTheme.typography.labelSmall
    val fuzzyMatching = state.settings.translateFuzzyMatching
    val englishTags = remember(tags, dictionary, fuzzyMatching) {
        tags.associate { tag -> tag.body to (com.kallan.naistudio.models.PromptTranslate
            .chineseCandidates(tag.body, dictionary, fuzzyMatching).firstOrNull() ?: tag.body) }
    }
    fun english(tag: PromptTagCards.Tag) = englishTags[tag.body] ?: tag.body
    fun chinese(tag: PromptTagCards.Tag): String? = dictionary.translation(TagZhProtocol.key(english(tag))) ?: if (english(tag) != tag.body) tag.body else null
    fun tone(tag: PromptTagCards.Tag): Color = when (PromptWeight.kindOf(tag.weight)) {
        PromptWeightKind.NORMAL -> colors.text
        PromptWeightKind.STRONG -> highlight.strong
        PromptWeightKind.WEAK -> highlight.weak
        PromptWeightKind.NEGATIVE -> highlight.negative
    }.takeUnless { it == Color.Unspecified } ?: colors.accent
    fun fill(tag: PromptTagCards.Tag) = if (PromptWeight.kindOf(tag.weight) == PromptWeightKind.NORMAL) colors.field
        else lerp(colors.field, tone(tag), (0.13 + kotlin.math.abs(tag.weight - 1.0).coerceAtMost(1.0) * 0.08).toFloat())
    fun commitEdit(): String {
        val id = editing ?: return currentValue
        editing = null
        if (!readOnly && currentValue == editSnapshot) {
            if (id == -1) {
                if (draft.text.isBlank()) return currentValue
                val updated = appendTag(currentValue, draft.text)
                currentChange(updated)
                return updated
            }
            val changed = PromptTagCards.rename(currentTags, id, draft.text)
            if (changed != currentTags) {
                val updated = PromptTagCards.render(changed)
                currentChange(updated)
                return updated
            }
        }
        return currentValue
    }
    fun beginEdit(tag: PromptTagCards.Tag, cursor: Int = tag.body.length) {
        val previousId = editing
        val previousCount = currentTags.size
        val updated = commitEdit()
        val updatedTags = PromptTagCards.parse(updated)
        val offset = if (previousId != null && previousId >= 0 && previousId < tag.id) updatedTags.size - previousCount else 0
        val selected = updatedTags.getOrNull(tag.id + offset) ?: return
        activate()
        editSnapshot = updated
        draft = TextFieldValue(selected.body, TextRange(cursor.coerceIn(0, selected.body.length)))
        editing = selected.id
    }
    fun beginNewTag() {
        editSnapshot = commitEdit()
        activate()
        draft = TextFieldValue("")
        editing = -1
    }
    DisposableEffect(trash, fieldKey) { onDispose { trash?.stop(fieldKey) } }
    LaunchedEffect(readOnly) { if (readOnly) { editing = null; dragged = null; trash?.stop(fieldKey) } }
    LaunchedEffect(Unit) { state.prepareTagDictionary() }
    LaunchedEffect(value) {
        val ids = tags.map { it.id }.toSet()
        bounds.keys.retainAll(ids)
        textPlacements.keys.retainAll(ids)
    }
    val target = if (dragged == null) null else bounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        val measuredWidths = remember(tags, dictionary, editing, draft.text, textMeasurer, labelStyle, translationStyle) {
            tags.associate { tag ->
                val body = if (editing == tag.id) draft.text else english(tag)
                val enWidth = textMeasurer.measure(PromptTagCards.displayLabel(tag, body), labelStyle).size.width
                val zhWidth = chinese(tag)?.let { textMeasurer.measure(it, translationStyle).size.width } ?: 0
                tag.id to maxOf(enWidth, zhWidth)
            } + (-1 to textMeasurer.measure(draft.text.ifEmpty { "tag" }, labelStyle).size.width)
        }
        fun cardWidth(id: Int) = maxOf(40.dp, with(density) { (measuredWidths[id] ?: 0).toDp() } + 20.dp).coerceAtMost(maxWidth)
        val cardHeight = with(density) {
            (textMeasurer.measure("Ag中", labelStyle).size.height + textMeasurer.measure("Ag中", translationStyle).size.height).toDp()
        } + 18.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.tagDictionaryPreparing) Text(t("dictionary.loading"), color = colors.muted, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = if (tags.isEmpty()) 24.dp else 0.dp)
                .pointerInput(readOnly) {
                    if (!readOnly) detectTapGestures(onTap = {
                        commitEdit()
                        activate()
                        focusManager.clearFocus()
                    })
                }) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        key(tag.id) {
                            val en = english(tag)
                            val zh = chinese(tag)
                            val label = PromptTagCards.displayLabel(tag, en)
                            val isEditing = editing == tag.id
                            Surface(shape = RoundedCornerShape(8.dp), color = fill(tag),
                                border = BorderStroke(if (target == tag.id || dragged == tag.id || isEditing) 2.dp else 1.dp,
                                    if (target == tag.id || dragged == tag.id || isEditing) colors.accent else if (PromptWeight.kindOf(tag.weight) == PromptWeightKind.NORMAL) colors.border else tone(tag).copy(alpha = 0.65f)),
                                modifier = Modifier.width(cardWidth(tag.id)).height(cardHeight).graphicsLayer { alpha = if (dragged == tag.id) 0.3f else 1f }
                                    .onGloballyPositioned { bounds[tag.id] = it.boundsInRoot() }
                                    .semantics(mergeDescendants = !isEditing) {
                                        if (readOnly) disabled() else if (!isEditing) {
                                            customActions = listOf(
                                                CustomAccessibilityAction(t("prompt.tagDecrease")) { currentChange(PromptTagCards.render(PromptTagCards.adjust(currentTags, tag.id, -0.1))); true },
                                                CustomAccessibilityAction(t("prompt.tagIncrease")) { currentChange(PromptTagCards.render(PromptTagCards.adjust(currentTags, tag.id, 0.1))); true },
                                            )
                                            onClick(t("dictionary.edit")) { beginEdit(tag); true }
                                        }
                                    }
                                    .pointerInput(tag.id, readOnly, isEditing) {
                                        if (!readOnly && !isEditing) awaitEachGesture {
                                            val down = awaitFirstDown()
                                            down.consume()
                                            val snapshot = currentValue
                                            val startingTags = currentTags
                                            val emittedValues = mutableSetOf(snapshot)
                                            val downInRoot = down.position + (bounds[tag.id]?.topLeft ?: origin)
                                            var mode = 0 // 0 pending, 1 weight slide, 2 held drag
                                            var previousSteps = 0
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (change.isConsumed || currentValue !in emittedValues) break
                                                    pointer = change.position + (bounds[tag.id]?.topLeft ?: origin)
                                                    trash?.move(fieldKey, pointer)
                                                    if (!change.pressed) {
                                                        if (mode == 0) {
                                                            change.consume()
                                                            val placement = textPlacements[tag.id]
                                                            val clicked = placement?.layout?.getOffsetForPosition(change.position - paddingPx) ?: tag.body.length
                                                            beginEdit(currentTags.first { it.id == tag.id }, (clicked - (placement?.prefixLength ?: 0)).coerceAtLeast(0))
                                                        }
                                                        else if (mode == 2) {
                                                            change.consume()
                                                            val destination = bounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                                            val changed = if (trash?.owner == fieldKey && trash.contains(pointer)) startingTags.filter { it.id != tag.id }
                                                                else if (destination != null) PromptTagCards.move(startingTags, tag.id, destination) else startingTags
                                                            if (changed != startingTags) currentChange(PromptTagCards.render(changed))
                                                        }
                                                        break
                                                    }
                                                    val distance = pointer - downInRoot
                                                    if (mode == 0 && distance.getDistance() > viewConfiguration.touchSlop) {
                                                        if (change.uptimeMillis - down.uptimeMillis >= 200L) {
                                                            mode = 2; grabOffset = down.position; dragged = tag.id; activate(); trash?.start(fieldKey, pointer)
                                                        } else if (kotlin.math.abs(distance.x) >= kotlin.math.abs(distance.y)) {
                                                            mode = 1; activate()
                                                        } else break // Let the prompt panel scroll vertically.
                                                    }
                                                    if (mode != 0) change.consume()
                                                    if (mode == 1) {
                                                        val signedSlop = if (distance.x >= 0f) viewConfiguration.touchSlop else -viewConfiguration.touchSlop
                                                        val steps = ((distance.x - signedSlop) / latestStepPx).toInt()
                                                        if (steps != previousSteps) {
                                                            previousSteps = steps
                                                            val updated = PromptTagCards.render(PromptTagCards.adjust(startingTags, tag.id, steps * 0.1))
                                                            emittedValues += updated
                                                            currentChange(updated)
                                                        }
                                                    }
                                                }
                                            } finally { dragged = null; trash?.stop(fieldKey) }
                                        }
                                    }) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (isEditing) {
                                        TagCardInput(state, "$fieldKey:${tag.id}", draft, { draft = it }, PromptTagCards.displayLabel(tag, "").removeSuffix("::"),
                                            colors.copy(text = tone(tag)), activate, { commitEdit() }, { editing = null })
                                    } else Text(label, color = tone(tag), style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = {
                                        textPlacements[tag.id] = TagTextPlacement(it, if (label == en) 0 else label.length - en.length - 2)
                                    })
                                    if (!zh.isNullOrBlank()) Text(zh, color = colors.muted, style = translationStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (!readOnly) {
                        if (editing == -1) Surface(shape = RoundedCornerShape(8.dp), color = colors.field,
                            border = BorderStroke(2.dp, colors.accent), modifier = Modifier.width(cardWidth(-1)).height(cardHeight)) {
                            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                TagCardInput(state, "$fieldKey:new", draft, { draft = it }, "", colors, activate, { commitEdit() }, { editing = null })
                            }
                        } else OutlinedIconButton(onClick = { beginNewTag() }, modifier = Modifier.size(cardHeight),
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border)) {
                            Icon(Icons.Outlined.Add, t("common.add"), tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

        }
        val moving = tags.firstOrNull { it.id == dragged }
        if (moving != null) Surface(shape = RoundedCornerShape(8.dp), color = fill(moving), border = BorderStroke(2.dp, tone(moving)),
            modifier = Modifier.offset {
                val topLeft = tagDragTopLeft(pointer, origin, grabOffset)
                IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            }
                .zIndex(2f).width(cardWidth(moving.id)).height(cardHeight)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(PromptTagCards.displayLabel(moving, english(moving)), color = tone(moving), style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                chinese(moving)?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.muted, style = translationStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun TagCardInput(state: AppState, inputKey: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    prefix: String, colors: TagColors, onActivate: () -> Unit, onCommit: () -> Unit, onCancel: () -> Unit) {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) Text(prefix, color = colors.text, style = MaterialTheme.typography.bodySmall)
        BasicTextField(value, onValueChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.text), cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(); focusManager.clearFocus() }),
            modifier = Modifier.weight(1f).focusRequester(requester).then(tagInputGuard(state, "tag-card-edit-$inputKey", onActivate))
                .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) onCommit() }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false
                    else when (event.key) {
                        Key.Enter -> if (value.composition == null) { onCommit(); focusManager.clearFocus(); true } else false
                        Key.Escape -> { onCancel(); focusManager.clearFocus(); true }
                        else -> false
                    }
                })
        if (prefix.isNotEmpty()) Text("::", color = LocalPromptHighlight.current.closing, style = MaterialTheme.typography.bodySmall)
    }
    LaunchedEffect(Unit) { requester.requestFocus() }
}

@Composable
internal fun tagInputGuard(state: AppState, key: String, onActivate: () -> Unit = {}): Modifier {
    DisposableEffect(state, key) { onDispose { state.reportTextInputFocus(key, false) } }
    return Modifier.onFocusChanged {
        state.reportTextInputFocus(key, it.isFocused)
        if (it.isFocused) onActivate()
    }
}
