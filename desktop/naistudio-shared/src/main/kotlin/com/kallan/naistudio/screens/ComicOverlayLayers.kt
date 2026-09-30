package com.kallan.naistudio.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.COMIC_DEFAULT_TEXT_COLOR
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBubble
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicLayerStack
import com.kallan.naistudio.models.ComicOverlayMetrics
import com.kallan.naistudio.models.ComicPanelCorner
import com.kallan.naistudio.models.ComicText
import com.kallan.naistudio.models.ComicTextAlign
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.FontLibrary
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.BubbleStyle
import com.kallan.naistudio.ui.EyeIcon
import com.kallan.naistudio.ui.EyeOffIcon
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.MergeDownIcon
import com.kallan.naistudio.ui.bubbleOutline
import com.kallan.naistudio.ui.bubblePath
import com.kallan.naistudio.ui.bubbleStyleOf
import com.kallan.naistudio.ui.bubbleTailFraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity

/**
 * **高级漫画 · ④对话气泡 + ⑤文本与字体**（`docs/43-方案-高级漫画模式.md` §9.1 / §9.2）。
 *
 * 这一批只做"气泡 / 文本"这两层（生成、队列、拼页导出是别的批次 ✓）。
 *
 * ## 三块，各管各的
 *
 *  1. [ComicOverlayToolbar] —— 顶部那排工具：**选气泡样式** / 文本 / 格子（点一下切"这一下拖动是什么意思"）；
 *  2. [ComicOverlayLayer] —— 画布上那一层：按**图层叠**从下往上画气泡与文本，选中态、
 *     四角把手、尾巴把手都在这里；
 *  3. [ComicOverlaySidebar] + [ComicOverlayEditor] —— 右侧：图层列表（显隐 / 排序 / 删除）+
 *     字体库（导入 / 删除）；双击气泡或文本打开属性编辑框。
 *
 * ## 两条硬规矩（这仓库的老教训，别踩 ✗）
 *
 *  · **手势（`pointerInput` / `clickable`）必须排在 `offset{}` / `size()` 之后** ✗ ——
 *    排前面的话命中矩形钉在原点，整页都会被它吃掉；
 *  · **拖动 / 缩放过程一帧都不写盘** ✓ —— 每帧写会把 `prefs.json` 磨穿，
 *    界面在手势结束（`onDragEnd` / `onDragCancel`）调一次 `commitComicBoard()` ✓。
 *
 * ## 尾巴为什么要有"比例"这一步
 *
 * 气泡轮廓（`ui/BubbleShapes.kt`）吃的是 `tailTarget`，单位得和 `size` 一致 ——
 * 而 `Shape` 的 lambda 里给的 `size` 是**像素**（还跟着窗口缩放变）。所以模型里存**绝对坐标**
 * （底板像素 ✓ 换窗口不漂移），画的时候换算成"相对泡身的比例"再乘回去 ✓（见 `bubbleTailFraction`）。
 */

/** 页面上"这一下拖动 / 点击"是什么意思（工具栏那一排选的就是它 ✓）。 */
enum class ComicCanvasTool {
    /** 拖矩形 = 新建一格（原来的行为 ✓，默认） */
    PANEL,

    /** 拖矩形 / 点一下 = 放一个气泡（样式取 [ComicOverlayUiState.bubbleStyle]） */
    BUBBLE,

    /** 点一下 / 拖矩形 = 放一段独立文本 */
    TEXT,
}

/**
 * 气泡 / 文本这块的**纯界面状态**（不落盘 —— 关掉再打开没必要记得"上次选中了哪个气泡" ✓）。
 *
 * 用一个小类装起来，是因为它要被**三处**读写（工具栏 / 画布 / 侧栏），
 * 一个个 `remember {}` 传下去会变成一串参数 ✗。
 */
@Stable
open class ComicOverlayUiState {
    var tool by mutableStateOf(ComicCanvasTool.PANEL)
    var bubbleStyle by mutableStateOf(BubbleStyle.ROUND)

    /** 选中的气泡 / 文本（= 它的图层 id ✓）。null = 没选。 */
    var selectedId by mutableStateOf<String?>(null)

    /** 正在打字 / 调属性的那个（非 null 时弹编辑框）。 */
    var editingId by mutableStateOf<String?>(null)

    /**
     * 正在拖**画布上的把手**（四角 / 尾巴尖）。
     *
     * 为什么要这个旗标：把手是页面 Box 的兄弟节点，拖它的时候页面那一层的手势也会被触发
     * （然后就会"顺手新建一格 / 一个气泡"✗）。子节点先拿到事件，所以把手在 `onDragStart`
     * 里立旗，页面那一层看到旗子就跳过这一下 ✓。
     */
    var handleDragging by mutableStateOf(false)

    /**
     * **这一下按下时按着"平移键"**（按住空格 + 左键，或鼠标中键 ✓）——
     * 由画布那一层在**按下那一刻**判定（`PointerEventPass.Initial` ✓，见 `ComicCanvasArea` 的 ② ✓）。
     *
     * 为什么要"记下按下的那一刻"，而不是哪一层现读"此刻空格还按着没" ✗：
     * 松开空格 / 松开中键之后才抬起的那一次 **tap** 必须也认得自己属于平移 ——
     * 否则它会顺手在纸上落一个气泡 ✗（中键点一下也会落 ✓，实测就是这么回事）。
     *
     * 谁看它：画布上**所有"拖 / 点 = 改内容"的手势**都看 —— 纸（新建格子 ✓）、
     * 格子的拖动与四角缩放、气泡 / 文本的拖动与缩放的把手、尾巴把手 ✓。
     * 看到就**整段让开** ✓：不然"按住空格在气泡上拖"会两件事一起发生
     *（气泡跟着跑 + 画布也平移 ✗）。
     */
    var canvasPanGesture by mutableStateOf(false)

    /**
     * **这一下按下时按着 Ctrl 吗** —— 「**图画要按住 ctrl 才能拖动**」（用户 2026-09-22 ✓）。
     *
     * 与 [canvasPanGesture] **同一条纪律**：由画布那一层在**按下那一刻**判定 ✓
     * （`PointerEventPass.Initial` ✓，见 `ComicCanvasArea` 的 ② ✓）——
     * 指针协程不会因为重组而重启，现读"此刻 Ctrl 还按着没"会在"按着 Ctrl 按下、
     * 松开 Ctrl 再拖"这种顺序下判错 ✗。
     *
     * 谁看它：画布上**一切"动图画"的手势**（格子的拖动、四角缩放 ✓）。
     * 没按 Ctrl ⇒ 整段让开 ✓（此时左键在图上拖动**只平移画布** ✓，图画一步都不动 ✓）。
     */
    var canvasCtrlGesture by mutableStateOf(false)

    /** 字体库的**版本号**：导入 / 删除之后 +1，下拉与列表据此重建 ✓。 */
    var fontRevision by mutableStateOf(0)
}


/** 图层种类 → i18n 键（图层列表里显示的名字按**种类**取，而不是存盘里那个中文名 ✓）。 */
fun comicLayerNameKey(kind: ComicLayer.Kind): String = when (kind) {
    ComicLayer.Kind.BASE -> "comic.layer.base"
    ComicLayer.Kind.GENERATED -> "comic.layer.generated"
    ComicLayer.Kind.BUBBLE -> "comic.layer.bubble"
    ComicLayer.Kind.TEXT -> "comic.layer.text"
    // 绘画层（2026-09-20 第 ⑬ 批）：走新加的 `comic.board.paintLayer`（「绘画层 / Paint layer」✓）
    ComicLayer.Kind.PAINT -> "comic.board.paintLayer"
}


/**
 * **画布那一排工具**（第 ⑮ 批起；**第 ㊼ 批精简** ✓）。
 *
 * ⚠️ **第 ㊼ 批（2026-09-21）**：用户口径 ——
 *   「**删除画笔和压感功能,删除气泡功能,删除字体功能,只留新建底板,图层,格子功能**」✓
 *
 * ⇒ 原先那排「气泡 / 文本 / 格子」三个工具切换 + 四个气泡样式**全部删掉** ✓，
 *   只留：**格子工具 + 「新建格子」开关 + 重置视图** ✓。
 *
 * ⚠️ 「新建格子」那个**开关**（[ComicModeUiState.newPanelMode] ✓）留着 ✗ ——
 *   用户 2026-09-20 点名「格子需要点击生成新格后才能在画布上进行拉新格子」✓，
 *   它是"允许拉"的闸门 ✓，与气泡那套无关 ✓。
 */
@Composable
fun ComicOverlayToolbar(
    page: ComicBoardPage?,
    ui: ComicModeUiState,
    t: (String) -> String,
    onResetView: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ToolChip(
                label = t("comic.toolPanel"),
                selected = ui.tool == ComicCanvasTool.PANEL,
                onClick = { ui.tool = ComicCanvasTool.PANEL },
            )
            ToolChip(
                label = t("comic.board.newPanel"),
                selected = ui.newPanelMode,
                onClick = { ui.newPanelMode = !ui.newPanelMode },
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onResetView, shape = RoundedCornerShape(50)) {
                Text(
                    t("comic.board.viewReset"),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Text(
                if (page == null) t("comic.noBase") else t("comic.overlayHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp).padding(start = 4.dp),
            )
        }
    }
}

/**
 * 一颗"选中就变实心"的工具按钮（`FilterChip` 在 material3 上是实验 API，这里用两个稳定控件拼 ✓）。
 *
 * ⚠️ `internal`（不是 `private`）：`ComicModeScreen.kt` 的 `ComicPaintToolbar` **复用它**画那八个工具 ✓
 * —— 新造一颗长得一样、行为却不完全一样的"工具芯片"只会让两处慢慢走散 ✗。
 */
/** 漫画墨色（**内容色、不跟主题走** ✓ —— 与 `ComicPaper` 同一个口径 ✓）。第 ㊼ 批补回：删气泡/文本时被连带删掉，但图层行还在用 ✓。 */
private val ComicInk = Color(0xFF1A1A1A)

@Composable
internal fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(50)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}


// ---------------------------------------------------------------------------
// ③ 侧栏：**画布编辑器 + 图层列表**（⑯ 起）；字体库已经搬去右列（[ComicFontPanelBody] ✓）
// ---------------------------------------------------------------------------

/**
 * **图层**那一块的外壳（一个圆角面板 + 自己会滚 —— 免得窄窗口里把旁边挤爆 ✗）。
 *
 * ⚠️ 里面那两块列表各自带 `heightIn(max = …)`：外层是 `verticalScroll`，
 * 给子节点的**最大高度是无界的**，不自己钉住的话内层滚动区会长到天上去 ✗。
 *
 * ⚠️ ⑯ 字体库**不在这一块里了** ✗（用户 2026-09-20 第 1 条：「字体放到右边」✓）——
 * 它搬去右列自成一块（[ComicFontPanelBody] ✓），这一块现在只剩**画布编辑器 + 图层** ✓。
 * 那一块自己带滚动，所以**它不能塞进这里**（滚动套滚动 = 无界高度 ✗，见 [ComicFontPanelBody] ✓）。
 */
@Composable
fun OverlaySidebarSurface(
    state: AppState,
    page: ComicBoardPage?,
    overlayUi: ComicOverlayUiState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            ComicOverlaySidebar(state = state, page = page, ui = overlayUi, t = t)
        }
    }
}

/**
 * ⑯ **字体库那一块**（用户 2026-09-20 第 1 条：「**字体放到右边**」✓）。
 *
 * 右边那一列（`ComicModeScreen` 的宿主 ✓）原来是两块（底板 / 格子 ✓），现在**变三块**：
 * **底板 / 格子 / 字体**，各自 `weight(1f)` ✓（= **有界高度** ✓）。
 *
 * 外壳照 [ComicBasePanelBody] / [ComicPanelsPanelBody] 一个模子 ✓：
 * 圆角 + `surfaceContainerLow` 底色 + `padding(10.dp)` ✓；**外面没有 `verticalScroll`** ✗ ——
 * 滚的是里面那个字体列表自己 ✓（[FontLibrarySection] 的列表带 `verticalScroll` ✓，
 * 外面再套一层就是"可滚组件拿到无界高度"→ 当场
 * `IllegalStateException: Vertically scrollable component was measured with an infinity
 * maximum height constraints` ✗，第 ⑧ 批踩过 ✓）。
 *
 * `weight(1f)` 一路传进 [FontLibrarySection]（它的根 `Column` 用收到的 `modifier` ✓）——
 * 于是那一块的**高度由这里的父 `Column` 决定** ✓（有界 ✓），
 * 字体列表在里面 `weight(1f)` 吃掉标题行剩下的部分 ✓。
 */
/**
 * **画布编辑器 + 图层**（④⑤ 那一批的图层部分；⑯ 起最上面多了画布编辑器）。
 *
 * 图层列表**从上往下显示 = 从最上面那一层往下**（和用户脑子里的"叠"一致 ✓），
 * 但叠本身是"下标 0 = 最底下"，所以这里只是把列表倒过来显示 ✓。
 *
 * ## ⑯ 起点（用户 2026-09-20 第 2 条：「**画布编辑器的各个功能…放在图层上面**」✓）
 *
 * 这个 `Column` 的**第一项**是 [BrushPanel]（第 ㉓ 批起 = **SAI 笔刷面板**：
 * 第 ㉔ 批（用户「**去除分类** · **编辑器只留原来有的**」✓）之后这一格是：**7 颗工具一行 +
 * 16 个预设（真预览）+ 预览条 + 参数区 + 大小选择盘 + 色环 + 撤销/重做**——
 * 「放在图层上面」= **排在图层列表上方** 就是这么落的；
 * 它原来在**画布上方**那一条，已经**撤掉了**（只留一处）。
 *
 * ## 用户 2026-09-20 那一版（**PS 式图层面板 + 每层缩略图** ✓）
 *
 * 参考图是 Photoshop 的图层面板，逐条落地成三块：
 *  1. [LayerPanelHeader] **顶部两行**：**混合模式**（只读显示「正常」，这一版不做真混合 ✗）
 *     + **不透明度**（一根滑杆，作用在**选中的那一层**上 ✓，拖的过程不写盘 ✓）；
 *  2. [LayerActionRow] **一排操作**：**新建图层** ✓ + **向下合并（压平位图层，图标 ✓，第 ⑱ 批搬进来的）**
 *     + **删除** ✓ + 原有的**上移 / 下移**
 *     （参考图里没有 ↑↓，但仓库的图层排序只有这两颗入口 ✗ —— 功能不能丢，
 *     所以挪到这一排，和删除并排 ✓；参考图里的"新建组 / 调整图层 / 蒙版 / 链接 / 效果"
 *     这一版**一律不画** ✓，不做假按钮 ✗）；
 *  3. [OverlayLayerRow] **每一层一行**：👁 眼睛（显隐）+ **缩略图** + 名字 + 不透明度百分比 ✓
 *     （参考图第二行那个「锁定」，这一版**不做** ✓，不画 ✓）。
 *
 * ⚠️ **第 ⑱ 批（2026-09-20）**：图层列表**下面**原来还有一排收尾入口（压平 / 导出这一页 /
 * 导出 PSD 三颗文字按钮 ✗）—— 用户「**那三个按钮删除**」✓ **整排已删** ✓（去向见 [LayerActionRow] 的说明 ✓）。
 *
 * 缩略图的口径见 [OverlayLayerThumbnail] ✓（⚠️ **绝不用 `Modifier.border(…, 自定义 Shape)`** ✗）。
 */
@Composable
fun ComicOverlaySidebar(
    state: AppState,
    page: ComicBoardPage?,
    ui: ComicOverlayUiState,
    t: (String) -> String,
) {
    Column(Modifier.fillMaxWidth()) {
        // ⚠️ **第 ㊽ 批（2026-09-21）**：用户口径「**删除画笔和压感功能**」✓ ——
        //    这一格原来挂的 `BrushPanel`（SAI 笔刷面板：工具 / 预设 / 参数 / 大小盘 / 色环 ✓）**整块删掉** ✓。
        //    这一块现在**直接从图层列表开始** ✓（图层这一侧的口径见 [ComicOverlaySidebar] 的说明 ✓）。

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("comic.layers"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (page == null) {
                    t("comic.noBase")
                } else {
                    RuntimeText.format(
                        state.settings.language,
                        "comic.layerCount",
                        mapOf("count" to page.layers.size),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))

        // ---- ① 顶部：混合模式（只读）+ 不透明度（选中那一层）----
        LayerPanelHeader(state = state, page = page, ui = ui, t = t)
        Spacer(Modifier.height(6.dp))

        // ---- ② 图层操作那一排（新建图层 / 上移 / 下移 / 删除）----
        LayerActionRow(state = state, page = page, ui = ui, t = t)
        Spacer(Modifier.height(6.dp))

        // ---- ③ 每一层一行（PS 式：眼睛 + 缩略图 + 名字 + 不透明度%）----
        val layers = page?.layers.orEmpty()
        if (layers.isEmpty()) {
            Text(
                t("comic.layerEmpty"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 一行的内容变多了（眼睛 + 缩略图 + 名字 + 百分比 ✓）→ 列表上限从 180dp 抬到 232dp，
            // 否则一屏只看得到两层半，翻起来很难受 ✗
            Column(Modifier.fillMaxWidth().heightIn(max = 232.dp).verticalScroll(rememberScrollState())) {
                // 倒过来显示：最上面那层排第一 ✓
                layers.asReversed().forEach { layer ->
                    OverlayLayerRow(state, layer, ui, t)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }

        // ⚠️ 2026-09-20 第 ⑱ 批：原来这儿那一排收尾入口（**压平位图层** / **导出这一页** /
        // **导出 PSD** 三颗文字按钮 ✗）**整排撤掉了** ✓（用户：「**那三个按钮删除**」✓）——
        //  · **压平** → 改成一颗**向下合并图标按钮**，搬到上面 [LayerActionRow] 的
        //    「新建图层」**旁边** ✓（用户：「压平位图层做成向下合并按钮，放在新建图层旁边」✓）；
        //  · **导出这一页 PNG** / **导出 PSD** → 搬进**「文件」菜单** ✓
        //    （用户：「导出默认导出 psd，按钮放在文件里」✓）—— 见 `StudioShell.kt` 的 `WindowMenuBar` ✓。

        // ⑯ 用户 2026-09-20 第 1 条：「**字体放到右边**」✓ —— 字体库（`FontLibrarySection`）
        // **从这里摘掉了** ✗，现在挂在**右列**（宿主的第三块，[ComicFontPanelBody] ✓）。
    }
}

/**
 * 图层面板**顶部那一行**（不透明度 ✓）。
 *
 *  · **混合模式那一行已删** ✓（用户 2026-09-22：「**图层的混合模式删除（没有功能）**」✓ ——
 *    它只是个只读的「正常」✗，`ComicLayer` 里根本没有 blend 字段 ✓）；
 *  · **不透明度**：作用在**选中的那一层**上 ✓（没选就退到最上面那一层 —— 底板也能调），
 *    拖的过程只喂内存（`setComicLayerOpacityLive` ✓），松手 `onValueChangeFinished` 才落一次盘 ✓
 *    （每帧写会把 `prefs.json` 磨穿 ✗，和拖动格子那条规矩一样 ✓）。
 */
@Composable
private fun LayerPanelHeader(
    state: AppState,
    page: ComicBoardPage?,
    ui: ComicOverlayUiState,
    t: (String) -> String,
) {
    val layers = page?.layers.orEmpty()
    // 选中的那一层；没选中 → 退到最上面那一层（用户点滑杆时脑子里想的通常就是它 ✓）
    val target = layers.firstOrNull { it.id == ui.selectedId } ?: layers.lastOrNull()
    val opacity = target?.opacity ?: 1f

    Column(Modifier.fillMaxWidth()) {
        // ⛔ **混合模式那一行删了** ✓（用户 2026-09-22：「**图层的混合模式删除（没有功能）**」✓）——
        //    它本来就是个**只读**的「正常」，占着一行还让人以为能点（"点了没反应"最招人烦 ✓）。
        //    ⚠️ 真要做混合模式时**再加回来** ✓（`ComicLayer` 里目前**没有** blend 字段 ✗ ——
        //    也就是说这一行从头到尾没接过任何数据 ✓，删掉不丢任何功能 ✓）。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("comic.board.layer.opacity"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Slider(
                value = opacity,
                onValueChange = { value -> target?.let { state.setComicLayerOpacityLive(it.id, value) } },
                onValueChangeFinished = { state.commitComicBoard() },
                enabled = target != null,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Text(
                "${(opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 图层操作那一排（用户 2026-09-20 给的 PS 参考图第三行）。
 *
 * **只画真的能用的** ✓：参考图里有七颗（新建图层 / 新建组 / 调整图层 / 蒙版 / 链接 / 效果 / 删除），
 * 这一版只做**新建图层**与**删除** ✓，另外把仓库原有的**上移 / 下移**挪到同一排
 * （参考图里没有 ↑↓，但图层排序只有这两颗入口 —— 少一颗功能就没了 ✗）。
 * 其余五颗**一个都不画** ✓（不做点了没反应的按钮 ✗）。
 *
 * 「新建图层」的口径：**默认就是「绘画层」** ✓（用户 2026-09-20：「**新建图层默认是绘画层**」✓）——
 * 建页大小的透明层 + 立刻聚焦开始画 ✓，见 [AppState.createComicPaintLayer] ✓。
 * 气泡 / 文本那两条入口**没删** ✗（它们各有自己的按钮 / 工具 ✓，见 [ComicOverlayToolbar] ✓）。
 *
 * ## ⚠️ 第 ⑱ 批（2026-09-20）：**「压平位图层」搬进来了** ✓
 *
 * 用户口径：「**压平位图层做成如图的向下合并按钮，放在新建图层旁边**」✓ ——
 *  · 它原来是**文字按钮**（`压平位图层` 四个字 ✗），住在图层列表**下面**那一排收尾入口里
 *    （`OverlayLayerActions` ✗，那一排连同「导出这一页」/「导出 PSD」**整排已删** ✓）；
 *  · 现在是一颗 **[MergeDownIcon]**（两块错开的方框 + 一个向下的粗箭头 ✓，自绘见 `AppIcons.kt` ✓）
 *    的**图标按钮** ✓，紧跟「新建图层」**右边**（同一排 ✓ —— 和 ↑ / ↓ / 垃圾桶 一样走
 *    [LayerGlyphButton] ✓，尺寸口径完全一致 ✓）；
 *  · 点它 = 现成的 [AppState.flattenComicLayers] ✓（**逻辑一个字没动** ✗）。
 *
 * ## 三条"别让用户点了才发现不行"的界面口径（**压平专属** ✓，其余按钮不变 ✗）
 *
 *  1. **选不中时灰掉** ✓：[ComicLayerStack.canFlatten] 为假（**空叠 / 全隐藏** ✗）→ 灰 ✓；
 *     没有底板（`page == null`）/ 正在导出·压平中 → 一样灰 ✓；
 *  2. **灰掉要给原因** ✓：红字写在**这一排正下方**（`comic.board.layer.flattenNothing` ✓，
 *     用户 2026-09-26 的口径：禁用要说明原因 ✗）——
 *     ⚠️ 原来那句「压平会把底板+生成层合成一张…」的**说明文案**（`comic.board.layer.flattenHint`）
 *     按用户 2026-09-20「**图二注释删除**」**中英两张表都已删掉** ✓，这里那处 `else` 分支也跟着删了 ✓；
 *  3. 忙的时候（`comicExporting` / `comicFlattening` / `comicPsdExporting` 任一 ✓）全灰 ✓
 *     —— 同一页别并发跑两次 ✗。
 *
 * ⚠️ **只动这一排** ✗：[OverlayLayerRow] 的行结构一个字节都没碰 ✓
 *（第 ⑬ 批的接手说明里点名的约束 ✓）。
 */
@Composable
private fun LayerActionRow(
    state: AppState,
    page: ComicBoardPage?,
    ui: ComicOverlayUiState,
    t: (String) -> String,
) {
    val layers = page?.layers.orEmpty()
    val selectedId = ui.selectedId
    val selected = layers.firstOrNull { it.id == selectedId }
    val index = layers.indexOfFirst { it.id == selectedId }
    // 底板层不给排、不给删（`moveComicLayer` / `removeComicLayer` 里各有一条守卫 ✓）——
    // 界面这边先把按钮关掉，别让用户点了才发现不行 ✗
    val reorderable = selected != null && selected.kind != ComicLayer.Kind.BASE
    val canUp = reorderable && index >= 0 && index < layers.size - 1
    val canDown = reorderable && index > 1
    val canDelete = selected != null && selected.kind != ComicLayer.Kind.BASE

    // ⑱ 压平（向下合并）那一条：能不能合 + 忙不忙 ✓（判据与原来 `OverlayLayerActions` 里**一模一样** ✓，
    // 一个字没改 ✗ —— 只有摆的位置变了 ✓）。
    val canFlatten = ComicLayerStack(layers).canFlatten()
    val flattenBusy = state.comicExporting || state.comicFlattening || state.comicPsdExporting

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OutlinedButton(
                onClick = {
                    // ⑮ 用户 2026-09-20：「**新建图层默认是绘画层**」✓ ——
                    // 这颗按钮就是 [AppState.createComicPaintLayer]（页大小、透明底 ✓，
                    // 建完即**聚焦并开始编辑** ✓ —— 见那边的说明 ✓）。
                    // ⚠️ 选中态这边也跟一次 ✓（图层列表里那一行要亮起来 ✓ —— "建完就立刻可选可画" ✓）。
                    val created = state.createComicPaintLayer()
                    if (created != null) ui.selectedId = created
                },
                enabled = page != null,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    t("comic.board.layerNew"),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // ⑱ **向下合并**（= 压平位图层）：紧挨「新建图层」右边 ✓ —— 用户 2026-09-20
            // 「压平位图层做成如图的向下合并按钮，放在新建图层旁边」✓。
            // ⚠️ 动作走的是**现成的** [AppState.flattenComicLayers] ✓（不新写逻辑 ✗）。
            LayerGlyphButton(
                icon = MergeDownIcon,
                contentDescription = t("comic.board.layer.flatten"),
                enabled = page != null && canFlatten && !flattenBusy,
                // 19dp（其余三颗走默认 17dp ✓）：这颗图形最密，用户问的就是"18~20dp 像不像图一" ✓
                iconSize = 19.dp,
            ) {
                state.flattenComicLayers()
            }
            Spacer(Modifier.weight(1f))
            LayerGlyphButton(Icons.Filled.KeyboardArrowUp, t("comic.layerUp"), canUp) {
                selectedId?.let { state.moveComicLayer(it, +1) }
            }
            LayerGlyphButton(Icons.Filled.KeyboardArrowDown, t("comic.layerDown"), canDown) {
                selectedId?.let { state.moveComicLayer(it, -1) }
            }
            LayerGlyphButton(Icons.Filled.Delete, t("comic.layerDelete"), canDelete) {
                selectedId?.let { id ->
                    // ⑯ 用户 2026-09-20 第 3 条：「图层的删除,点击垃圾桶删除后自动选择下一图层,
                    // 这样可以连续删除图层」✓ —— **选下一层 + 能画就顺手开好会话**这一整套
                    // 全在 [deleteComicLayerAndSelectNext] 里 ✓。
                    // ⚠️ **界面上删层的垃圾桶只有这一颗** ✓（我核过：`OverlayLayerRow` 那一行里
                    //    只有"眼睛 / 缩略图 / 名字 / 不透明度%"四样 ✗，**没有**第二颗桶 ✓；
                    //    下面 `FontLibrarySection` 里那颗是**删字体**的 ✓，与图层无关 ✓）——
                    //    所以第 3 条要改的入口就是这一处 ✓（见回报里的说明 ✓）。
                    deleteComicLayerAndSelectNext(state = state, ui = ui, layerId = id)
                }
            }
        }
        // ⑱ **灰掉的原因**（红字 ✓，就在这一排正下方 = "那一排附近" ✓）：
        // 压平不吃「气泡 / 文本」那两种矢量层，但真正会灰的情形只有一种 ——
        // **空叠 / 全隐藏**（`canFlatten == false` ✓，用户 2026-09-26 的口径：禁用要说出原因 ✓）。
        // ⚠️ 原来这里是一对 `if / else`：可压时显示那句"压平会把底板+生成层合成一张…"的说明 ✗
        //（`comic.board.layer.flattenHint` —— 用户 2026-09-20「**图二注释删除**」✗）——
        // **说明那一支连同键一起删掉了** ✓，只剩这条原因 ✓。
        if (page != null && !canFlatten) {
            Spacer(Modifier.height(3.dp))
            Text(
                t("comic.board.layer.flattenNothing"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * **删一层 → 自动选中"下一层"**（用户 2026-09-20 第 3 条：「**图层的删除,点击垃圾桶删除后自动选择下一
 * 图层,这样可以连续删除图层**」✓）。
 *
 * ## "下一层"的定义（= **按图层列表的显示顺序** ✓）
 *
 * 图层列表是**倒序显示**的（`layers.asReversed()` ✓ —— 叠里**最上面那层排第一** ✓，
 * 见 [ComicOverlaySidebar] 那段说明 ✓），所以：
 *  · 被删的那一层在叠里下标 `at` → 它在**列表里的下一个** = 叠里 **`at - 1`** 那一层 ✓
 *    （显示顺序里紧跟它后面 = 叠里比它低一层 ✓）；
 *  · 删的是**列表最后一个**（显示顺序的末尾 = 叠里下标 `0`，最底下那层）→ 取**上一个**
 *    （= 叠里下标 `1` ✓）；
 *  · 删完**列表空了** → **清空选中** ✓ 并且 `commitComicLayerPaint()` 收口 ✓
 *    （别留一个指向已删层的会话 ✗）。
 *
 * ## 连续删（**安静**是重点 ✓）
 *
 * 新选中的那一层如果**可画**（BASE / GENERATED / PAINT ✓，判据用现成的
 * [AppState.canComicPaintLayer] ✓）就顺手 `beginComicLayerPaint(新id)` ✓ ——
 * 于是下一颗垃圾桶还能接着删、接着画 ✓；
 * **不可画**（气泡 / 文本 ✗）**只选中、不开会话** ✓：
 * 这里**故意不调** `beginComicLayerPaint`（它走 `refuseComicPaint` 会**弹 toast** ✓）——
 * 连着删几个的时候会被 toast 刷屏 ✗（用户点名不要 ✓）。"删掉的那层没了、新选中的这层是矢量层"
 * 这两件事都**不需要再解释** ✓（用户是奔着删去的，不是奔着画去的 ✓）。
 *
 * ⚠️ 删除本身**落盘**由 [AppState.removeComicLayer] 负责 ✓（内部 `persistComicBoard()` ✓，
 * 气泡 / 文本还连模型一起删 ✓）；**选中态是纯界面状态、不写盘** ✓
 *（`ui.selectedId` 住在 [ComicOverlayUiState] 里，一个字节都不落盘 ✓）。
 */
private fun deleteComicLayerAndSelectNext(
    state: AppState,
    ui: ComicOverlayUiState,
    layerId: String,
) {
    // 先把"下一层"算出来（**删之前**算 ✓ —— 删完列表就变了 ✓）
    val layers = state.comicBoardPage?.layers.orEmpty()
    val at = layers.indexOfFirst { it.id == layerId }
    val nextId = if (at < 0) {
        null
    } else {
        layers.getOrNull(at - 1)?.id ?: layers.getOrNull(at + 1)?.id
    }

    // 被删的那一层正好是选中的 / 正在打字的 → 先落空（**不 commit**：见下面 removed 之后那段 ✓）
    if (ui.selectedId == layerId) ui.selectedId = null
    if (ui.editingId == layerId) ui.editingId = null

    if (!state.removeComicLayer(layerId)) return

    // 删完列表空了（没得选）→ 清空选中 + 收口会话 ✓
    val next = layers.firstOrNull { it.id == nextId }
    if (next == null) {
        state.commitComicLayerPaint()
        return
    }
    ui.selectedId = next.id
    if (state.canComicPaintLayer(next.id)) {
        // 顺手开好会话（"点一层 = 立刻能画"和点图层行是同一个口径 ✓）——
        // 它内部会**先 commit 上一段** ✓（切换图层前落盘那一条 ✓）。
        state.beginComicLayerPaint(next.id)
    }
    // 不可画（气泡 / 文本）：到此为止 ✓ —— **不 toast、不 commit** ✓
    //（`beginComicLayerPaint` 会 toast ✗；commit 会把"另一层"的会话收掉，那不是用户这一下要的事 ✓）。
}

/**
 * 图层操作那一排里的一颗小图标按钮（比 `IconButton` 紧凑 —— 48dp 的触摸目标会把这一排撑得很高 ✗）。
 *
 * @param iconSize 图标本身的边长，**默认 17dp**（↑ / ↓ / 垃圾桶 那三颗走默认值 ✓）。
 *   ⚠️ 第 ⑱ 批给「向下合并」那颗单独开这个口子 ✓：它的图形**信息量最大**
 *   （两块方框 + 一根粗箭头），17dp 下缩得太密 ✗ —— 它传 **19dp** ✓
 *   （用户问的正是"18~20dp 下像不像图一" ✓）。**其余三颗一个字节都没动** ✗。
 */
@Composable
private fun LayerGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    iconSize: Dp = 17.dp,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ⚠️ 第 ⑱ 批（2026-09-20）：这里原来有一个 `OverlayLayerActions`（图层列表**下面**那一排：
// 「压平位图层」+「导出这一页」+「导出 PSD」**三颗文字按钮** ✗）—— 用户「**那三个按钮删除**」✓，
// **整排已删** ✓，别照着旧版本再补回来 ✗。三条去向：
//  · **压平位图层** → 一颗 `MergeDownIcon`（向下合并 ✓）的**图标按钮**，
//    搬进上面 [LayerActionRow] 的「新建图层」**旁边** ✓（原因那行红字也跟过去了 ✓）；
//  · **导出这一页 PNG** → **「文件」菜单**的「导出这一页」✓（`StudioShell.kt` 的 `WindowMenuBar` ✓）；
//  · **导出 PSD** → 同一条菜单的「导出 PSD」✓（**默认导出** ✓，快捷键 `Ctrl+Shift+S` ✓）。

/**
 * 图层列表里的一行 —— **用户 2026-09-20 那版 PS 式一行**：
 * `👁 眼睛` + **缩略图** + **名字** + **不透明度百分比** ✓（选中行高亮 ✓）。
 *
 *  · **显隐**：`setComicLayerVisible` ✓（隐藏 ≠ 删除 —— 还在盘上，随时能开回来 ✓）；
 *  · **缩略图**：见 [OverlayLayerThumbnail] ✓；
 *  · **不透明度**：这一行只显示百分比 ✓，滑杆在面板顶部（[LayerPanelHeader] ✓，
 *    作用在**选中的那一层**上 —— 和参考图一样 ✓）；
 *  · **选中 / 排序 / 删除**：点这一行 = 选中 ✓；上移 / 下移 / 删除在面板那一排
 *    （[LayerActionRow] ✓）—— 参考图的行里除了眼睛和缩略图没有别的按钮 ✓。
 *    ⚠️ ⑯ 用户 2026-09-20 第 3 条里说的「图层行那颗垃圾桶」**在界面上并不存在** ✓ ——
 *    这一行从头到尾只有 👁 + 缩略图 + 名字 + 不透明度% ✗（删层的那颗桶在 [LayerActionRow] ✓）。
 *    所以"删完自动选下一层"只落在那一颗上 ✓（那一颗两条链路都覆盖：工具栏的桶 = 这里唯一入口 ✓）。
 *
 *  · **底板层**：**可以选中**（PS 里背景层也是可选的 —— 顶部那根不透明度滑杆要能作用到它 ✓），
 *    但**不给排、不给删**（`moveComicLayer` / `removeComicLayer` 里各有一条守卫 ✓）；
 *  · **生成层**：名字后面缀一个 `#序号`（格子序号 ✓）—— 同一格生成过好几次会留下好几层"生成层"，
 *    不缀序号就分不清谁是谁 ✗（序号是语言无关的 `#n`，不用新文案 ✓）。
 */
@Composable
private fun OverlayLayerRow(
    state: AppState,
    layer: ComicLayer,
    ui: ComicOverlayUiState,
    t: (String) -> String,
) {
    val selected = ui.selectedId == layer.id
    // 生成层的 `#第几格`（分不清哪一层是那一格生成的时候最有用 ✓）
    val panelSuffix = if (layer.kind == ComicLayer.Kind.GENERATED && layer.panelId != null) {
        val at = state.comicBoardPanels.indexOfFirst { it.id == layer.panelId }
        if (at >= 0) " #${at + 1}" else ""
    } else {
        ""
    }
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable {
            ui.selectedId = layer.id
            // ⑭ 用户 2026-09-20：「**画布编辑器是跟随图层啊,选中图层就可以画啊,和 ps 逻辑一样**」✓ ——
            //    点一层 = **把"改哪一层"换成它** ✓。
            // ⑰ 用户 2026-09-20（这一批那三句）：「**不需要点图层确定状态** · **选图层只是确定修改的层**」✓ ——
            //    所以这一下**不再是"进入编辑"的前提** ✗（进这一页就已经由
            //    `AppState.autoFocusComicPaintLayer` 自动聚焦了一层 ✓，见 `ComicCanvasHost` 那条 effect ✓）。
            //  · **位图层**（底板 / 绘画层 / **生成层**）→ 会话当场换到它 ✓
            //    （生成层先 materialize 成页大小像素 ✓，见 `AppState.beginComicLayerPaint` ✓）；
            //  · **气泡 / 文本**（矢量层 ✗）→ **只选中、什么都不开、也不弹 toast** ✓
            //    （`beginComicLayerPaint` 会走 `refuseComicPaint` 弹一句 ✓ —— 连着点几行会被 toast 刷屏 ✗，
            //     用户点名不要 ✓；和上面「删层后自动选下一层」那条口径**逐字一致** ✓：
            //     判据用现成的 `canComicPaintLayer` ✓）。**焦点保持在原来那一层** ✓
            //    （那条自动聚焦的 effect 见到"已经聚焦着一层、它还能画"就早退 ✓ —— 不会被这一下顶掉 ✓）。
            if (state.canComicPaintLayer(layer.id)) state.beginComicLayerPaint(layer.id)
        },
    ) {
        Row(
            Modifier.padding(start = 1.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- 👁 眼睛（显隐）----
            // ⚠️ 不用 `IconButton`：它自带 48dp 的触摸目标，会把这一行撑成两倍高 ✗
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { state.setComicLayerVisible(layer.id, !layer.visible) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (layer.visible) EyeIcon else EyeOffIcon,
                    contentDescription = if (layer.visible) t("comic.layerHide") else t("comic.layerShow"),
                    tint = if (layer.visible) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            // ---- 缩略图 ----
            OverlayLayerThumbnail(state = state, layer = layer)
            Spacer(Modifier.width(6.dp))
            // ---- 名字 ----
            Text(
                text = t(comicLayerNameKey(layer.kind)) + panelSuffix,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // ---- 不透明度百分比（这一层自己的 ✓）----
            Text(
                "${(layer.opacity * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 图层缩略图那一格的边长（PS 那套一行大约 36px；窄栏里 32dp 也看得清 ✓）。 */
private val LayerThumbSize = 32.dp

/**
 * **每一层的缩略图**（用户 2026-09-20：「图层用如图样式，**每一层的略缩图**」✓）。
 *
 * 四种层各画各的：
 *  · **底板 / 生成层**（有 `imagePath`）→ **真缩略图** ✓：走现成的
 *    `ui/FileImage(path, maxDimension = 64, useThumbnail = true)` ✓ —— 和图库同一套
 *    （`Thumbnails.fileFor` 的**磁盘缩略图缓存** ✓，不会为了 32px 读一张几 MB 的原图 ✗）。
 *    没有图的（空白画布 / 还没出图的生成层）→ 画一格"纸" ✓（**不读盘** ✓）；
 *  · **气泡层** → **不读图**：`bubblePath` + `drawPath`（填白 + 描边）✓ ——
 *    ⚠️ **绝不用 `Modifier.border(宽, 色, 自定义 Shape)`** ✗（电脑端会抛
 *    `Failed to Image::makeFromBitmap` —— 见 `ui/ComicBubbleShape.kt` 头上那段 ✓），
 *    这里连 `bubbleOutline` 都不走，直接 `Canvas` + `drawPath` ✓（同一个 `bubblePath` ✓）；
 *  · **文本层** → 画一个「T」✓（字形就是最准的图标 ✓）。
 *
 * ⚠️ 描边用的是 `drawBehind` + `drawRect(…, Stroke)` —— **标准 `drawRect` 不是 `border`** ✓，
 * 没有位图那一环 ✓（这一格同时也是"缩略图没出来时的底" ✓）。
 */
@Composable
private fun OverlayLayerThumbnail(state: AppState, layer: ComicLayer) {
    val density = LocalDensity.current
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val page = state.comicBoardPage
    Box(
        Modifier
            .size(LayerThumbSize)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(color = line.copy(alpha = 0.35f), style = Stroke(width = 1f))
            },
        contentAlignment = Alignment.Center,
    ) {
        when (layer.kind) {
            // 位图层（底板 / 生成层 / **绘画层**）：有图就真缩略图，没图画一格"纸" ✓
            ComicLayer.Kind.BASE, ComicLayer.Kind.GENERATED, ComicLayer.Kind.PAINT -> {
                // ⚠️ **第 ㊷ 批（2026-09-21）**：用户口径 ——
                //    「**图层需要每画一笔都更新图层的略缩图，略缩图为画布（如画布是 a4，略缩图就是 a4 大小。）**」
                //    「**略缩图的画面上的图像和画布对应**（如有根线在画布的右上角，那根线也在略缩图的右上角）」
                //
                // 原来只读盘（`layer.imagePath` ✓），而那条路**只在收工时写** ✗ ⇒ 画的当中缩略图是旧的 ✗。
                // 这里先问"**这一层正在被编辑吗**" ✓：是 ⇒ 用**活像素**现缩一张 ✓
                //（整页等比 ✓ ⇒ 右上角的线还在右上角 ✓）；
                // 不是（或还没开始画）⇒ 回落读盘 ✓（老路一个字没动 ✓）。
                //
                // ⚠️ **现读 `state.comicPaintFrameTick`** ✓（与视口同一个纪律 ✓）：
                //    每颗笔尖它都 +1 ⇒ 缩略图跟着重画 ✓（**不触发整页重组** ✗）。
                //
                // ⛔ **第 ㊻ 批（2026-09-21）修**：上一批这一段**只写了注释、没有真的读那个值** ✗ ——
                //    Compose 靠"**读到快照状态**"才知道要重组 ✓；只在注释里提一句它**什么都不会发生** ✗。
                //    实机症状正是用户报的「**还是只有建新图层时才更新**」✓ ——
                //    建新图层会改**图层列表**（那是别的快照状态 ✓）⇒ 顺手把这一块重组了一次 ✓
                //    ⇒ 看起来"只有那时才更新" ✓（自洽 ✓）。
                //    **修法**：把值**真的读出来**并参与下面那次调用 ✓（读进局部变量就足够触发订阅 ✓）。
                val liveTick = state.comicPaintFrameTick
                val liveFloatTick = state.comicPaintFloatTick
                @Suppress("UNUSED_EXPRESSION")
                run { liveTick; liveFloatTick }
                val live = state.comicPaintLiveThumbnail(layer.id, with(density) { LayerThumbSize.roundToPx() })
                val path = layer.imagePath?.takeIf { it.isNotBlank() }
                if (live != null) {
                    Image(
                        bitmap = live,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().background(ComicThumbPaper),
                    )
                } else if (path != null) {
                    FileImage(
                        path = path,
                        maxDimension = 64,
                        useThumbnail = true,
                        // ⛔ **第 ㊿i 批（2026-09-22）修**：这里原来是 `ContentScale.Crop` ✗ ——
                        //    而**聚焦那一层**走上面那条活像素路，用的是 `Fit` ✓ ⇒
                        //    同一层"聚焦前 Crop、聚焦后 Fit" ⇒ **一点它就换个样子** ✗
                        //    （用户报的「切换图层图片还会动 / 对不齐」就有这一条 ✓）。
                        //    口径按用户第 ㊷ 批定的来：**缩略图 = 整页等比缩小**（A4 的缩略图还是 A4 ✓，
                        //    右上角的线还在右上角 ✓）⇒ 两条路都必须是 `Fit` ✓。
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 空白底板（还没有图）/ 还没出图的生成层 / 新建了还没画的绘画层：
                    // 画一格"纸"，**不读盘** ✓
                    Box(Modifier.fillMaxSize().background(ComicThumbPaper))
                }
            }

            // ⚠️ **第 ㊼ 批**：气泡 / 文本那两支**已删** ✓（用户口径「删除气泡功能」✓）——
            //    这里留一个 `else` 只是让 `when` 穷尽 ✓（那两种层的行本来也不会再出现在列表里 ✓）。
            else -> Box(Modifier.fillMaxSize().background(ComicThumbPaper))
        }
    }
}

/** 缩略图里那格"空纸"的底色（**漫画是内容、不跟主题走** —— 和底板那张纸 `ComicPaper` 同一个口径 ✓）。 */
private val ComicThumbPaper = Color(0xFFFCFCFA)
