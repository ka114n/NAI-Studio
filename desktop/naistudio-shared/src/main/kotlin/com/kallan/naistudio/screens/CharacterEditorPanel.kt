package com.kallan.naistudio.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.ComicLayout
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.models.PromptField
import com.kallan.naistudio.models.TextFieldTarget
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.ExpandCornersIcon
import com.kallan.naistudio.ui.CanvasSelectIcon
import com.kallan.naistudio.ui.HelpBadgeWithPopup
import com.kallan.naistudio.ui.SegmentedCapsule
import com.kallan.naistudio.ui.LocalNaiSkin
import com.kallan.naistudio.ui.LocalPanelBackgroundColor
import com.kallan.naistudio.ui.NaiSkin
import com.kallan.naistudio.ui.NaiSkinTokens
import com.kallan.naistudio.ui.contrastRatio
import com.kallan.naistudio.ui.followFingerForSelection
import com.kallan.naistudio.ui.trackTextInputFocus

/**
 * 角色分区 / 漫画分格编辑器。
 *
 * **移植自 ComfyUI 节点** `novelai-genytools` 的 `NAICharacterRegionEditorV2`
 * （`web/js/settings.js`）。控制项与源码一一对应，但按手机窄栏做了两处布局适配：
 *
 *  · **头行拆成两行**。节点是单行 8 个控件，塞进 340dp 宽的抽屉会把删除键挤出屏幕
 *    （之前就是这个毛病，点不到删除）。现在：
 *      第一行 `☑启用` `角色 N` `🗑删除`
 *      第二行 `☐AI决定位置` `▾展开/▴收起`
 *  · **位置编辑器改成弹窗方格**。节点是一张浮动弹窗，里面每个**已启用**的角色有一个
 *    编号标记，可在方格内自由拖动；这里就是同一套行为（拖动会把该角色的 `ai_choice` 关掉，
 *    即改为手动坐标，与源码 `setDroppedPosition` 一致）。
 *
 * ## 漫画模式（0.2.79 新增）
 *
 * 画布模式决定显示角色分区还是漫画分镜。两种模式**用的是同一种东西**
 * （同一个 [com.kallan.naistudio.models.CharCaptionItem] 形状），但**分别存储、互不干扰**：
 *  · 角色模式 → `extras.charCaptions`，坐标是手拖的 `x/y`；
 *  · 漫画模式 → `extras.comicPanels`，坐标**由版式派生**（见 [ComicLayout]），不手拖。
 *
 * 切换画布不搬运、不转换、不复制两份角色数据。
 *
 * 已按要求移除：`复制`、`↑`、`↓`（这三个改成标题旁的「编辑」里统一调顺位）、以及自定义名称。
 */
@Composable
fun CharacterEditorPanel(state: AppState, modifier: Modifier = Modifier) {
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }
    val rf: (String, Map<String, Any?>) -> String = { key, args ->
        RuntimeText.format(language, key, args)
    }

    val params = state.params
    val limit = params.maxCharacterPrompts
    val comic = state.extras.comicMode
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val items = state.extras.activeItems

    var showReorder by remember { mutableStateOf(false) }
    var showPositionGrid by remember { mutableStateOf(false) }
    var showLayoutPicker by remember { mutableStateOf(false) }
    // 「按剧情分镜」不再是弹窗：剧情框就直接摆在画布左边（用户要求）。
    // 剧情文本存在 AppState.comicPlot：页面切走再回来不丢（HorizontalPager 只组合相邻页）。
    Column(modifier.fillMaxWidth()) {
        // 角色模式：计数行 + 编辑（同行右侧）。漫画模式两样都不显示（用户要求）。
        if (!comic) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${t("char.title")}（${items.size}/$limit）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showReorder = true }, enabled = items.size > 1) {
                    Text(t("char.edit"))
                }
            }
        }

        if (limit == 0) {
            Text(
                t("char.unsupported"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        // 模式切换**不在这里**：用户要求它放到「角色图鉴」按钮上面，
        // 由 GenerateScreen 用 CharacterComicModeSwitch 摆。
        if (referenceSkin && !comic) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReferenceAddCharacterButton(
                    label = t("char.add"),
                    enabled = items.size < limit,
                    onClick = { state.addCharacter() },
                    modifier = Modifier.weight(1.1f),
                )
                ReferenceEditPositionButton(
                    label = t("char.editPosition"),
                    enabled = items.any { it.enabled },
                    onClick = { showPositionGrid = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = if (comic) 0.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { state.addCharacter() },
                    enabled = items.size < limit,
                    modifier = Modifier.weight(1f),
                ) { Text(if (comic) t("comic.add") else t("char.add")) }
                OutlinedButton(
                    onClick = {
                        if (comic) showLayoutPicker = true else showPositionGrid = true
                    },
                    enabled = if (comic) true else items.any { it.enabled },
                    modifier = Modifier.weight(1f),
                ) {
                    // 漫画模式下这一颗就是**唯一的版式入口**，所以带上当前版式名
                    Text(
                        if (comic) {
                            "${t("comic.layout")}：${ComicLayout.nameOf(state.extras.comicLayout)}"
                        } else {
                            t("char.editPosition")
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (comic) {
            // 阅读顺序就在「添加分格」下面（用户要求）
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("comic.order"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedCapsule(
                    options = listOf(
                        NaiOption(t("comic.orderRtl"), ComicLayout.ORDER_RTL),
                        NaiOption(t("comic.orderLtr"), ComicLayout.ORDER_LTR),
                    ),
                    selected = state.extras.comicOrder,
                    modifier = Modifier.width(170.dp),
                    onSelect = { state.setComicOrder(it) },
                )
            }

            ComicComposer(
                state = state,
                t = t,
                rf = rf,
            )
        }

        if (items.isEmpty()) {
            Text(
                if (comic) t("comic.empty") else t("char.empty"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            return@Column
        }

        // 这里**不能**再加 verticalScroll：外层抽屉已经是竖向滚动的，嵌套会崩
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items.forEachIndexed { index, _ ->
                CharacterCard(state = state, index = index, t = t)
            }
        }
    }

    if (showReorder) {
        ReorderDialog(state = state, t = t, onDismiss = { showReorder = false })
    }
    if (showPositionGrid) {
        PositionGridDialog(state = state, t = t, onDismiss = { showPositionGrid = false })
    }
    if (showLayoutPicker) {
        ComicLayoutDialog(state = state, t = t, rf = rf, onDismiss = { showLayoutPicker = false })
    }
}

/** Full-width, soft-accent reference action with restrained desktop hover/press feedback. */
@Composable
private fun ReferenceAddCharacterButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val primary = NaiSkinTokens.accent()
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            pressed -> NaiSkinTokens.accentSoft(0.95f)
            hovered -> NaiSkinTokens.subtleHover()
            else -> NaiSkinTokens.accentSoft()
        },
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "referenceAddCharacterBackground",
    )
    val panelBackground = LocalPanelBackgroundColor.current ?: NaiSkinTokens.panel()
    val compositeBackground = background.compositeOver(panelBackground)
    val blackWhiteFallback = if (
        contrastRatio(Color.Black, compositeBackground) >= contrastRatio(Color.White, compositeBackground)
    ) Color.Black else Color.White
    val foreground = when {
        contrastRatio(primary, compositeBackground) >= 4.5f -> primary
        contrastRatio(MaterialTheme.colorScheme.onPrimary, compositeBackground) >= 4.5f ->
            MaterialTheme.colorScheme.onPrimary
        contrastRatio(MaterialTheme.colorScheme.onSurface, compositeBackground) >= 4.5f ->
            MaterialTheme.colorScheme.onSurface
        else -> blackWhiteFallback
    }
    val pressOffset by animateDpAsState(
        targetValue = if (enabled && pressed) 1.dp else 0.dp,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "referenceAddCharacterPressOffset",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        // The contrast calculation above targets this composite. Draw it opaque so
        // wallpaper/root gradients cannot change the pixels behind the foreground.
        color = compositeBackground.copy(alpha = 1f),
        contentColor = foreground,
        border = BorderStroke(
            1.dp,
            if (enabled) NaiSkinTokens.borderStrong() else NaiSkinTokens.border(),
        ),
        modifier = modifier
            .heightIn(min = 40.dp)
            .offset(y = pressOffset),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun ReferenceEditPositionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val panelBackground = LocalPanelBackgroundColor.current ?: NaiSkinTokens.panel()
    val overlay by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Black.copy(alpha = 0.04f)
            pressed -> NaiSkinTokens.accentSoft()
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "referenceEditPositionBackground",
    )
    val background = overlay.compositeOver(panelBackground)
    val pressOffset by animateDpAsState(
        targetValue = if (enabled && pressed) 1.dp else 0.dp,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "referenceEditPositionPressOffset",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        color = background.copy(alpha = 1f),
        contentColor = if (enabled) NaiSkinTokens.accent() else NaiSkinTokens.faint().copy(alpha = 0.62f),
        border = BorderStroke(
            1.dp,
            if (enabled) NaiSkinTokens.borderStrong() else NaiSkinTokens.border(),
        ),
        modifier = modifier
            .heightIn(min = 40.dp)
            .offset(y = pressOffset),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(CanvasSelectIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 漫画模式的编辑区：
 *
 *  · **顶部**：页条（`◀ 第 N/M 页 ▶` + 加页 / 删页 + 这一页的 AI 概要）；
 *  · **左边一列**：剧情框（按剧情分镜，不再是弹窗）+ 主按钮 + 整页风格词框；
 *  · **右边**：缩小后的版式预览画布（可点击进位置编辑），下面**两个勾选框**：
 *    「自动加风格词」与「狂暴模式」。两个框左对齐 —— 不然文字长短不同会被
 *    居中对齐错开，看着像没对齐。
 *
 * 主按钮是**一个**，行为由「狂暴模式」勾选框决定：
 * 不勾 = 剧情框里那段剧情拆成当前这一页；勾上 = 整部剧情自动分页成多页。
 *
 * 阅读顺序在更外面（`CharacterEditorPanel` 里），不在这个函数里。
 */
@Composable
private fun ComicComposer(
    state: AppState,
    t: (String) -> String,
    rf: (String, Map<String, Any?>) -> String,
) {
    val extras = state.extras
    val pages = extras.comicPages
    val pageIndex = extras.comicPageSafeIndex
    // 逐页出图是**会花额度**的一步，所以先弹窗把张数说清楚，用户点了才排队
    var showBerserkConfirm by remember { mutableStateOf(false) }
    // 能出图的页数（空页不算）—— 按钮上显示的张数就是它
    val generatablePages = pages.count { page ->
        page.panels.any { it.enabled && it.prompt.isNotBlank() }
    }
    // 狂暴模式的进度文案：分页 / 逐页分镜 两个阶段
    val berserkProgress = when (state.berserkStage) {
        "plan" -> t("comic.berserkPlanning")
        "storyboard" -> rf(
            "comic.berserkStoryboarding",
            mapOf(
                "current" to (state.berserkDone + 1).coerceAtMost(state.berserkTotal.coerceAtLeast(1)),
                "total" to state.berserkTotal.coerceAtLeast(1),
            ),
        )
        else -> t("comic.berserkRunning")
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // ---- 页条：多页漫画的翻页 / 加页 / 删页 ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = { state.switchComicPage(pageIndex - 1) },
                enabled = pageIndex > 0 && !state.berserkBusy,
            ) {
                Text("◀", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                rf(
                    "comic.pageOf",
                    mapOf("current" to pageIndex + 1, "total" to pages.size.coerceAtLeast(1)),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(
                onClick = { state.switchComicPage(pageIndex + 1) },
                enabled = pageIndex < pages.lastIndex && !state.berserkBusy,
            ) {
                Text("▶", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { state.addComicPage() },
                enabled = !state.berserkBusy,
            ) {
                Text(t("comic.pageAdd"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            TextButton(
                onClick = { state.removeComicPage(pageIndex) },
                enabled = pages.size > 1 && !state.berserkBusy,
            ) {
                Text(t("comic.pageRemove"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        // AI 给这一页的概要：让用户能在出图前核对"这一页到底在讲什么"
        val summary = pages.getOrNull(pageIndex)?.summary.orEmpty()
        if (summary.isNotBlank()) {
            Text(
                rf("comic.pageSummary", mapOf("summary" to summary)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = extras.comicBerserk,
                onCheckedChange = { state.setComicBerserk(it) },
                enabled = !state.berserkBusy && !state.storyboardBusy,
            )
            Text(
                t("comic.berserk"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            HelpBadgeWithPopup(text = t("comic.berserkHint"), alignEnd = true)
        }

        // ---- 狂暴模式：勾上时，进行中显示进度 + 取消；规划出多页后给出图按钮 ----
        // 不勾的话这一整块都不出现 —— 那时主按钮与生成栏各出一张，就是"只跑一页"。
        if (state.berserkBusy) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    berserkProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { state.cancelBerserk() }) {
                    Text(t("comic.berserkCancel"), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else if (state.storyboardBusy) {
            Text(
                t("comic.storyboardRunning"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // 只有狂暴模式打开、并且真的规划出了多页，才给"一次跑完"的入口。
        // 关闭时主按钮与生成栏各出一张—— 也就是"只跑一页"。
        if (extras.comicBerserk && !state.berserkBusy && pages.size > 1) {
            OutlinedButton(
                onClick = { showBerserkConfirm = true },
                enabled = generatablePages > 0 && !state.storyboardBusy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(
                    rf("comic.berserkGenerate", mapOf("count" to generatablePages)),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // （原来这里还有一段内联长注释：勾上/不勾各一句。用户 2026-09-16 要求收进
        //  「狂暴模式」旁边那个「!」小圆标里 —— 见上面那行 `HelpBadgeWithPopup`。）
    }

    if (showBerserkConfirm) {
        AlertDialog(
            onDismissRequest = { showBerserkConfirm = false },
            title = { Text(t("comic.berserkConfirmTitle")) },
            text = { Text(rf("comic.berserkConfirmBody", mapOf("count" to generatablePages))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBerserkConfirm = false
                        state.startBerserkGeneration()
                    },
                ) { Text(t("common.confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { showBerserkConfirm = false }) { Text(t("common.cancel")) }
            },
        )
    }
}

/**
 * 版式预览画布：画出版式**真实的矩形**与按阅读顺序编号的锚点。
 *
 * 现在**可以点**（用户要求）：点一下就和角色模式的「编辑角色位置」一样弹出位置编辑窗，
 * 锚点可以随意拖动。所以这里画的是**生效坐标**——手拖过的格用它自己的 x/y，
 * 没动过的仍按版式派生。
 */
/** The same storyboard layout used by the editor, scaled to the main canvas. */
@Composable
internal fun StoryboardLayoutPreview(state: AppState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val imageW = state.params.width
        val imageH = state.params.height
        val ratio = if (imageW > 0 && imageH > 0) {
            imageW.toFloat() / imageH.toFloat()
        } else 1f
        val pageWidth = minOf(maxWidth - 32.dp, (maxHeight - 32.dp) * ratio)
            .coerceAtLeast(1.dp)
        ComicLayoutCanvas(
            state,
            modifier = Modifier.width(pageWidth),
            canvasRatio = ratio,
        )
    }
}

@Composable
private fun ComicLayoutCanvas(
    state: AppState,
    modifier: Modifier = Modifier,
    canvasRatio: Float = if (state.params.height > 0) state.params.width.toFloat() / state.params.height else 1f,
) {
    val extras = state.extras
    val activeIndices = extras.comicPanels.indices.filter { extras.comicPanels[it].enabled }
    val actives = activeIndices.map { extras.comicPanels[it] }
    val textMeasurer = rememberTextMeasurer()
    val rects = ComicLayout.orderedRects(extras.comicLayout, extras.comicOrder, actives.size)
    val derived = ComicLayout.anchorsFor(extras.comicLayout, extras.comicOrder, actives.size)
    val anchors = actives.mapIndexed { index, panel ->
        if (panel.useCoords) panel.x to panel.y else derived.getOrNull(index) ?: (0.5 to 0.5)
    }
    val latestAnchors by rememberUpdatedState(anchors)
    val latestIndices by rememberUpdatedState(activeIndices)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.small

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(canvasRatio)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, outlineColor, shape)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val radius = maxOf(30.dp.toPx(), minOf(size.width, size.height) * 0.06f)
                    val hit = latestAnchors.indexOfFirst { (x, y) ->
                        (down.position - Offset(
                            (x * size.width).toFloat(),
                            (y * size.height).toFloat(),
                        )).getDistance() <= radius
                    }
                    val itemIndex = latestIndices.getOrNull(hit)
                    if (itemIndex == null) return@awaitEachGesture
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            state.updateCharacter(itemIndex) {
                                it.copy(
                                    useCoords = true,
                                    x = (change.position.x / size.width).coerceIn(0f, 1f).toDouble(),
                                    y = (change.position.y / size.height).coerceIn(0f, 1f).toDouble(),
                                )
                            }
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        drawComicPanelGrid(gridColor)
        drawComicRects(
            rects = rects,
            anchors = anchors,
            outlineColor = outlineColor,
            markerColor = markerColor,
            textMeasurer = textMeasurer,
        )
    }
}

/** 20% 网格底纹（与节点弹窗的底纹一致）。 */
private fun DrawScope.drawComicPanelGrid(lineColor: Color) {
    val stepX = size.width / 5f
    for (i in 1..4) {
        drawLine(lineColor, Offset(stepX * i, 0f), Offset(stepX * i, size.height), 1f)
    }
    val stepY = size.height / 5f
    for (i in 1..4) {
        drawLine(lineColor, Offset(0f, stepY * i), Offset(size.width, stepY * i), 1f)
    }
}

/**
 * 画版式矩形 + 编号锚点。
 *
 * 编号用**阅读顺序**（[ComicLayout.orderedRects] 已经排好序），
 * 所以弹层缩略图上的编号和真正发给 NovelAI 的锚点顺序是同一套逻辑。
 */
private fun DrawScope.drawComicRects(
    rects: List<ComicLayout.Rect>,
    anchors: List<Pair<Double, Double>>,
    outlineColor: Color,
    markerColor: Color,
    textMeasurer: TextMeasurer,
) {
    val corner = 6.dp.toPx()
    val stroke = 1.5.dp.toPx()

    rects.forEach { rect ->
        val topLeft = Offset((rect.x * size.width).toFloat(), (rect.y * size.height).toFloat())
        val rectSize = Size((rect.w * size.width).toFloat(), (rect.h * size.height).toFloat())
        drawRoundRect(
            color = outlineColor.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
    }

    // ⚠️ 标记尺寸必须**由 dp 转 px**，不能写死像素：
    // 早先是 `radius = 8f` / `fontSize = 9.sp`，那个 8 是**原始像素**，
    // 在 3 倍屏上只有 2.7dp —— 小到几乎看不见（用户反馈"数字与点都有点小"）。
    // 同时让尺寸跟着画布走：面板里的版式预览很大、弹层缩略图很小，写死一个值两头不讨好。
    val radius = (size.minDimension * 0.05f).coerceIn(9.dp.toPx(), 17.dp.toPx())
    val style = TextStyle(
        // 白字：标记本身就是 primary / tertiary 的实心圆，
        // 和角色分区那个位置网格（PositionGrid）里的白字保持同一套观感。
        color = Color.White,
        fontSize = (radius * 0.9f).toSp(),
        fontWeight = FontWeight.Bold,
    )

    anchors.forEachIndexed { index, (ax, ay) ->
        val center = Offset((ax * size.width).toFloat(), (ay * size.height).toFloat())
        drawCircle(color = markerColor, radius = radius, center = center)
        val text = "${index + 1}"
        val label = textMeasurer.measure(text, style)
        drawText(
            textMeasurer = textMeasurer,
            text = text,
            topLeft = Offset(
                center.x - label.size.width / 2f,
                center.y - label.size.height / 2f,
            ),
            style = style,
        )
    }
}

/** 单个角色卡：两行头 + 展开后的正负提示词。点名字可改名。漫画模式下多一个「类型」下拉。 */
@Composable
private fun CharacterCard(state: AppState, index: Int, t: (String) -> String) {
    val comic = state.extras.comicMode
    val character = state.extras.activeItems.getOrNull(index) ?: return
    var renaming by remember { mutableStateOf(false) }
    var roleMenu by remember { mutableStateOf(false) }
    val fallbackName = if (comic) "格 ${index + 1}" else "角色 ${index + 1}"
    val displayName = character.name.ifBlank { fallbackName }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(7.dp)) {
            // 第一行：启用 + 名称（可点击改名） + 类型（漫画） + 删除
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = character.enabled,
                    onCheckedChange = { value ->
                        state.updateCharacter(index) { it.copy(enabled = value) }
                    },
                )
                if (renaming) {
                    // 出现即自动聚焦：否则点完名字还要再点一次输入框才能打字
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    OutlinedTextField(
                        value = character.name,
                        onValueChange = { value -> state.updateCharacter(index) { it.copy(name = value) } },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            // 角色改名框：**要登记焦点**（空格闸门 `AppState.textInputFocused` 读它）——
                            // 名字里带空格很常见（"white cat"），漏了在这个框里就打不出空格。
                            // key 带 index：几个角色同时可改名时不能同名。
                            .trackTextInputFocus(state, "char.rename:$index"),
                    )
                    TextButton(onClick = {
                        // 留空就回落到按序号显示
                        if (character.name.isBlank()) {
                            state.updateCharacter(index) { it.copy(name = fallbackName) }
                        }
                        renaming = false
                    }) { Text("✓") }
                } else {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(displayName) {
                                detectTapGestures(onTap = { renaming = true })
                            },
                    )
                }
                if (comic) {
                    Box {
                        TextButton(onClick = { roleMenu = true }) {
                            Text("${t(roleLabelKey(character.panelRole))} ▾")
                        }
                        DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                            PANEL_ROLE_ORDER.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(t(roleLabelKey(role))) },
                                    onClick = {
                                        roleMenu = false
                                        applyPanelRole(state, index, role)
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { state.removeCharacter(index) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = t("char.delete"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 第二行：AI决定位置（仅角色模式） + 展开/收起
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (comic) {
                    Text(
                        t("comic.positionByLayout"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Checkbox(
                        checked = !character.useCoords,
                        onCheckedChange = { aiChoice ->
                            state.updateCharacter(index) { it.copy(useCoords = !aiChoice) }
                        },
                    )
                    Text(
                        t("char.aiChoice"),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = {
                    state.updateCharacter(index) { it.copy(expanded = !it.expanded) }
                }) {
                    Text(if (character.expanded) t("char.collapse") else t("char.expand"))
                }
            }

            if (character.expanded) {
                OutlinedTextField(
                    value = character.prompt,
                    // 走 `setCharacterPrompt` 而不是直接 updateCharacter：这样框里的改动
                    // 会进**这个框自己的**撤销栈（用户 2026-09-19 给每个文本框配了一条工具栏，
                    // 撤销/重做必须对得起自己那一条）
                    onValueChange = { value -> state.setCharacterPrompt(index, false, value) },
                    placeholder = { Text(if (comic) t("comic.panelPromptHint") else t("char.positiveHint")) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .followFingerForSelection()
                        // 漫画模式下这个框就是"这一格的画面提示词"，标签也跟着换
                        .onFocusChanged {
                            state.onTextFieldFocus(
                                TextFieldTarget.CharacterPrompt(index, comic),
                                it.isFocused,
                            )
                        },
                )
                // 这个框自己那条工具栏（用户 2026-09-19：提示词 / 角色 / 分镜每个文本框一个）
                PromptActionBar(
                    state = state,
                    t = t,
                    targetField = PromptField.charKey(index, negative = false),
                )
                OutlinedTextField(
                    value = character.negativePrompt,
                    onValueChange = { value ->
                        state.setCharacterPrompt(index, true, value)
                    },
                    placeholder = { Text(t("char.negativeHint")) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .followFingerForSelection()
                        .onFocusChanged {
                            state.onTextFieldFocus(
                                TextFieldTarget.CharacterNegative(index, comic),
                                it.isFocused,
                            )
                        },
                )
                PromptActionBar(
                    state = state,
                    t = t,
                    targetField = PromptField.charKey(index, negative = true),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 漫画「类型」：只影响**空提示词**的建议前缀，绝不改写用户写过的内容
// ---------------------------------------------------------------------------

/**
 * 剧情框右上角那个「放大」图标离框边的距离，**上下左右共用这一个值**
 * （用户 2026-09-16 要求"离框的宽高一致"）。
 *
 * 之前是让图标在一个 36dp 的触摸目标里垂直居中：右边距 3dp、上边距却有 7dp，
 * 两个方向不一样，看着就是歪的。改成显式同值内边距之后，改手感只需要动这一个数。
 */
private val PLOT_EXPAND_INSET = 8.dp

/** 下拉里的顺序。 */
private val PANEL_ROLE_ORDER = listOf("", "scene", "text", "prop")

private fun roleLabelKey(role: String): String = when (role) {
    "scene" -> "comic.roleScene"
    "text" -> "comic.roleText"
    "prop" -> "comic.roleProp"
    else -> "comic.roleCharacter"
}

/** 新建/切换类型时给出的建议前缀（依据参考实现 v5-architect 技能的槽位命名规范）。 */
private fun rolePrefix(role: String): String = when (role) {
    "scene" -> "scenery, "
    "text" -> "text, speech bubble \"\""
    "prop" -> "prop, "
    else -> "girl, "
}

private fun applyPanelRole(state: AppState, index: Int, role: String) {
    state.updateCharacter(index) { item ->
        // 只在提示词还是**空的**时候填建议前缀；写过内容就一字不改
        if (item.prompt.isBlank()) {
            item.copy(panelRole = role, prompt = rolePrefix(role))
        } else {
            item.copy(panelRole = role)
        }
    }
}

/** 「编辑」：调整顺位（角色/分格通用）。 */
@Composable
private fun ReorderDialog(state: AppState, t: (String) -> String, onDismiss: () -> Unit) {
    val comic = state.extras.comicMode
    val items = state.extras.activeItems

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("char.reorderTitle")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    t("char.reorderHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                items.forEachIndexed { index, _ ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (comic) "格 ${index + 1}" else "角色 ${index + 1}",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { state.moveCharacter(index, -1) },
                            enabled = index > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = { state.moveCharacter(index, 1) },
                            enabled = index < items.size - 1,
                        ) { Text("↓") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("common.confirm")) }
        },
    )
}

/**
 * 「编辑角色位置」：弹出的方格。
 *
 * 与节点弹窗一致：只有**已启用**的角色会出现，每个是一个带编号的圆点，
 * 在方格内自由拖动即可定位（拖动会把该角色切成手动坐标，即 `use_coords = true`）。
 */
@Composable
private fun PositionGridDialog(state: AppState, t: (String) -> String, onDismiss: () -> Unit) {
    // 角色模式与漫画模式共用这一个窗口（漫画模式也从这里编辑分格位置）
    val comic = state.extras.comicMode
    val characters = state.extras.activeItems

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (comic) t("comic.panelGridTitle") else t("char.gridTitle")) },
        text = {
            Column {
                Text(
                    if (comic) t("comic.positionHint") else t("char.positionHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (characters.none { it.enabled }) {
                    Text(
                        t("char.gridEmpty"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    PositionGrid(state = state)
                    // 图例：对应节点的 renderCoordLegend()，显示「序号. 名字」与坐标
                    // 坐标同样走**生效位置**，否则画布上点在哪、下面写的数对不上。
                    val legendPositions = effectivePositions(state)
                    Column(Modifier.padding(top = 8.dp)) {
                        characters.forEachIndexed { index, character ->
                            if (!character.enabled) return@forEachIndexed
                            val position = legendPositions.getOrNull(index) ?: (character.x to character.y)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${index + 1}. " + character.name.ifBlank {
                                        if (comic) "格 ${index + 1}" else "角色 ${index + 1}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "X %.3f · Y %.3f".format(position.first, position.second),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("common.confirm")) }
        },
    )
}

/**
 * 「选择版式」：列出全部版式模板，每项一个**真实矩形**的缩略预览。
 *
 * 缩略图上的编号就是阅读顺序（[ComicLayout.orderedRects] 同一套逻辑），
 * 所以「左右对半」的 1 在右边（日漫右起）。
 * 每项还会预告**这个版式会怎么改分格数量** —— 只承诺删得掉的空格，不骗用户。
 */
@Composable
private fun ComicLayoutDialog(
    state: AppState,
    t: (String) -> String,
    rf: (String, Map<String, Any?>) -> String,
    onDismiss: () -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val extras = state.extras
    val current = extras.comicPanels.size
    val outlineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("comic.layoutTitle")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    t("comic.layoutHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                // 两列排布：每行两个版式
                ComicLayout.templates.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { template ->
                            val selected = template.id == extras.comicLayout
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) markerColor else MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .clickable {
                                        state.applyComicLayout(template.id)
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                            ) {
                                val count = template.panelCount ?: current
                                val rects = ComicLayout.orderedRects(template.id, extras.comicOrder, count)
                                val anchors = ComicLayout.anchorsFor(template.id, extras.comicOrder, count)
                                Canvas(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(832f / 1216f)
                                        .background(surfaceColor, MaterialTheme.shapes.extraSmall),
                                ) {
                                    drawComicRects(
                                        rects = rects,
                                        anchors = anchors,
                                        outlineColor = outlineColor,
                                        markerColor = markerColor,
                                        textMeasurer = textMeasurer,
                                    )
                                }
                                Text(
                                    t(layoutNameKey(template.id)),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    layoutDeltaLabel(template, current, state, rf),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // 奇数个模板时补一个空位，避免最后一个被拉伸
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("common.confirm")) }
        },
    )
}

private fun layoutNameKey(id: String): String = "comic.layout.$id"

/**
 * 预告这个版式会怎么改分格数量。
 * **如实计算**：只承诺删得掉的空格，有内容的格不会被动。
 */
private fun layoutDeltaLabel(
    template: ComicLayout.Template,
    current: Int,
    state: AppState,
    rf: (String, Map<String, Any?>) -> String,
): String {
    val target = template.panelCount
        ?: return rf("comic.followExisting", mapOf("count" to current))

    return when {
        target > current -> rf(
            "comic.layoutMore",
            mapOf("count" to target, "added" to (target - current)),
        )
        target < current -> {
            val removable = ComicLayout.removableTail(
                state.extras.comicPanels.map { it.prompt },
                target,
            )
            val kept = current - target - removable
            when {
                removable > 0 && kept > 0 -> rf(
                    "comic.layoutFewerKept",
                    mapOf("count" to target, "removable" to removable, "kept" to kept),
                )
                removable > 0 -> rf(
                    "comic.layoutFewer",
                    mapOf("count" to target, "removable" to removable),
                )
                else -> "${target} 格 · 留 ${kept}"
            }
        }
        else -> "${target} 格"
    }
}

/**
 * 位置方格：每个**已启用**的角色是一个带编号的圆点，可自由拖动。
 *
 * ⚠️ 位置手势：**必须自己在 Initial 阶段接管并消费**，否则外层那个垂直滚动容器
 * 会把纵向拖动先吃掉 → 表现就是"角色位置拖不动"（只有横向能拖一点、或者完全不动）。
 * 早先这里是两个 pointerInput（detectTapGestures + detectDragGestures），
 * 谁先跑到阈值谁赢，在滚动容器里必输。现在合成一个手势：
 *   · 按下就找最近的角色（命中才接管）；
 *   · 移动即 consume + 更新 x/y；
 *   · 松手时若几乎没动，当作"点一下"跳到该位置。
 */
@Composable
private fun PositionGrid(state: AppState) {
    // 与 PositionGridDialog 一样：走**当前模式那一份列表**，漫画模式下就是分格
    val characters = state.extras.activeItems
    val params = state.params
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val markerFill = MaterialTheme.colorScheme.primary
    val markerManual = MaterialTheme.colorScheme.tertiary

    // 抓取半径：**必须 dp 转 px**。
    // 早先写的是 `dragGrabRadius = 30f`，那个 30 是**原始像素** ——
    // 在 3 倍屏上只有 10dp，而标记本身直径 30dp（半径 15dp），
    // 也就是"能抓住的范围比圆点还小"，所以很难对准（用户反馈"可点击范围有点小"）。
    // 现在按 dp 给，并且比标记半径再宽出一圈手指容差。
    val density = LocalDensity.current
    val grabRadiusPx = with(density) { 26.dp.toPx() }
    // 手势里要拿到**最新的**位置：pointerInput 的 key 只有 size，拖过之后 x/y 变了
    // 但 key 没变，lambda 里捕获的会是旧列表 → "拖一次就拖不动了"。
    val positions = effectivePositions(state)
    val latestPositions by rememberUpdatedState(positions)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(
                if (params.width > 0 && params.height > 0) {
                    params.width.toFloat() / params.height.toFloat()
                } else {
                    1f
                },
            )
            // ⚠️ 先 clip 再 background：只给 border 圆角的话方形底色会从四个角露出来
            //（用户反馈"画布是圆角的但背景白色是方形的会突出"）
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .drawBehind {
                // 20% 网格，和节点弹窗的底纹一致
                val step = size.width / 5f
                for (i in 1..4) {
                    drawLine(lineColor, Offset(step * i, 0f), Offset(step * i, size.height), 1f)
                }
                val stepY = size.height / 5f
                for (i in 1..4) {
                    drawLine(lineColor, Offset(0f, stepY * i), Offset(size.width, stepY * i), 1f)
                }
            }
            .pointerInput(characters.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // ⚠️ 用**最新的**生效位置做命中测试：这个 pointerInput 的 key 只有 size，
                    // 拖过一次后 x/y 变了但 key 没变 → 早先一直拿旧坐标判定，于是"拖一次就再也拖不动"。
                    // 命中测试必须和下面画标记用的是**同一份**坐标，否则画在一处、抓在另一处。
                    val current = latestPositions
                    // 拖动**必须在角色标记附近**才开始（半径见 grabRadiusPx）：
                    // 早先用的是"最近的角色"且半径写成了像素，在空白处离得近也会误抓、
                    // 真要点圆点又抓不住。
                    val hit = nearestEnabledIndex(
                        current, down.position, size.width, size.height, grabRadiusPx,
                    )
                    if (hit < 0) return@awaitEachGesture
                    var moved = false
                    var last = down.position
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > 4f) moved = true
                        last = change.position
                        change.consume()
                        state.updateCharacter(hit) {
                            it.copy(
                                useCoords = true,
                                x = (last.x / size.width).coerceIn(0f, 1f).toDouble(),
                                y = (last.y / size.height).coerceIn(0f, 1f).toDouble(),
                            )
                        }
                        if (!change.pressed) break
                    }
                    if (!moved) {
                        // 点一下也跳到该位置（保持原来的"点击定位"手感）
                        state.updateCharacter(hit) {
                            it.copy(
                                useCoords = true,
                                x = (last.x / size.width).coerceIn(0f, 1f).toDouble(),
                                y = (last.y / size.height).coerceIn(0f, 1f).toDouble(),
                            )
                        }
                    }
                }
            },
    ) {
        characters.forEachIndexed { index, character ->
            if (!character.enabled) return@forEachIndexed
            // 生效位置：漫画模式下没手拖过的分格就是版式锚点，换了版式这里立刻跟着变
            val position = positions.getOrNull(index) ?: return@forEachIndexed
            // 34dp（原来是 30dp）：既好看一点，也更好瞄
            val marker = 34.dp
            Box(
                Modifier
                    .offset(
                        x = maxWidth * position.first.toFloat() - marker / 2,
                        y = maxHeight * position.second.toFloat() - marker / 2,
                    )
                    .size(marker)
                    .clip(CircleShape)
                    .background(if (character.useCoords) markerManual else markerFill)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 每个角色的**生效位置**：漫画模式下没被手拖过（`useCoords == false`）的分格
 * 走版式派生锚点，其余用存下来的 `x/y`。`null` = 该角色已禁用。
 *
 * ⚠️ 三处必须是同一套逻辑：[ComicLayoutCanvas] 的预览缩略图、[PositionGrid] 的画布、
 * 以及真正发出去的 payload（`NaiApi.centerOf`）。
 * 早先只有预览用了派生锚点，画布画的却是存下来的 `x/y` ——
 * 于是"换了版式，点开画布锚点没跟着重置"（用户反馈）。
 *
 * ⚠️ 锚点下标是「已启用里的第几个」，不是整份列表里的下标 —— `NaiApi` 就是这么编的。
 */
private fun effectivePositions(state: AppState): List<Pair<Double, Double>?> {
    val characters = state.extras.activeItems
    val comic = state.extras.comicMode
    val derived = if (comic) {
        ComicLayout.anchorsFor(
            state.extras.comicLayout,
            state.extras.comicOrder,
            characters.count { it.enabled },
        )
    } else {
        emptyList()
    }
    var seen = 0
    return characters.map { item ->
        if (!item.enabled) {
            null
        } else {
            val index = seen++
            if (comic && !item.useCoords) {
                derived.getOrNull(index) ?: (item.x to item.y)
            } else {
                item.x to item.y
            }
        }
    }
}

/** 找离触点最近的已启用角标；超出命中半径返回 -1。`positions` 里 `null` 表示已禁用。 */
private fun nearestEnabledIndex(
    positions: List<Pair<Double, Double>?>,
    offset: Offset,
    padWidth: Int,
    padHeight: Int,
    hitRadius: Float,
): Int {
    if (padWidth <= 0 || padHeight <= 0) return -1
    var best = -1
    var bestDistance = Float.MAX_VALUE
    positions.forEachIndexed { index, position ->
        if (position == null) return@forEachIndexed
        val cx = (position.first.toFloat() * padWidth)
        val cy = (position.second.toFloat() * padHeight)
        val distance = kotlin.math.hypot(cx - offset.x, cy - offset.y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return if (bestDistance <= hitRadius) best else -1
}
