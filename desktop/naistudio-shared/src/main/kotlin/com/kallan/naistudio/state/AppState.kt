package com.kallan.naistudio.state

// ⚠️ 这个 Bitmap 是**流式预览帧**（服务端推来的去噪中间稿，见 generationPreview）。
// 手机上是 android.graphics.Bitmap，暂时还留着 —— 要搬到共用树得先把"位图"抽一层
// （电脑端是 skia 的 ImageBitmap），属于下一步的工作。
import com.kallan.naistudio.models.INFINITE_FRAME_MAX_AREA
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
// ---- 第 ㉜a 批：显示链走 skiko（层真源 = `Surface` ✓，见 `ImageEditSession` 头上一整段 ✓）----
// ⚠️ `Rect` 这个名字**上面已经被 Compose 的 `androidx.compose.ui.geometry.Rect` 占了** ✗ ——
//    所以 skia 那个用别名，免得两边打架 ✓（本文件里 Compose 的 Rect 用得很多 ✓）。
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.TagDictionary
import com.kallan.naistudio.models.AnlasCost
import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.BackupCodec
import com.kallan.naistudio.services.AnimaCharacter
import com.kallan.naistudio.services.AnimaDexApi
import com.kallan.naistudio.services.AnimaDexLabels
import com.kallan.naistudio.services.AnimaDexProtocol
import com.kallan.naistudio.services.AnimaFacet
import com.kallan.naistudio.services.AnimaSeries
import com.kallan.naistudio.models.CharCaptionItem
import com.kallan.naistudio.models.BrushShape
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.StrokeInputCoalesceStats
import com.kallan.naistudio.models.StrokeInputCoalescer
import com.kallan.naistudio.models.CANVAS_MAX_LONG_SIDE
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.isBrushTool
import com.kallan.naistudio.models.COMIC_DEFAULT_STYLE
import com.kallan.naistudio.models.DEFAULT_BLUR_INTENSITY
import com.kallan.naistudio.models.DEFAULT_CANVAS_COLOR
import com.kallan.naistudio.models.ConversationTurn
import com.kallan.naistudio.models.DEFAULT_FILL_TOLERANCE
import com.kallan.naistudio.models.FloatingSelection
import com.kallan.naistudio.models.FocusedInpaint
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.SelectionOps
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.StatusLine
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.models.StrokeStabilizer
import com.kallan.naistudio.models.StrokeDabStats
import com.kallan.naistudio.models.ComicLayout
import com.kallan.naistudio.models.ComicPage
import com.kallan.naistudio.models.ComicStoryboard
// ---- 高级漫画模式（docs/43 · M1）：自由矩形格子的那一套（和上面旧漫画模式**并存** ✓）----
import com.kallan.naistudio.models.COMIC_BASE_LAYER_NAME
import com.kallan.naistudio.models.COMIC_PAINT_LAYER_NAME
import com.kallan.naistudio.models.ComicBoardMetrics
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicBubble
import com.kallan.naistudio.models.ComicBubbleStyles
import com.kallan.naistudio.models.ComicCost
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicLayerStack
// 无限画布（第 ㊿k 批 ✓，方案 `docs/69` ✓）：几何 / 像素搬运都在 models 里（纯逻辑 ✓ 有单测 ✓）
import com.kallan.naistudio.models.InfiniteCanvas
import com.kallan.naistudio.models.InfiniteCanvasHistory
import com.kallan.naistudio.models.InfiniteCanvasPixels
import com.kallan.naistudio.models.capInfiniteFrameArea
import com.kallan.naistudio.models.InfiniteSide
import com.kallan.naistudio.models.ComicOverlayMetrics
import com.kallan.naistudio.models.ComicPanel
import com.kallan.naistudio.models.ComicPanelCorner
import com.kallan.naistudio.models.ComicPanelTemplates
import com.kallan.naistudio.models.ComicRunEntry
import com.kallan.naistudio.models.ComicRunPlan
import com.kallan.naistudio.models.ComicText
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.GenerationQueue
import com.kallan.naistudio.models.clampedTo
import com.kallan.naistudio.models.canBeDirectlyManipulated
import com.kallan.naistudio.models.movedBy
import com.kallan.naistudio.models.normalizedOverlays
import com.kallan.naistudio.models.objectAt
import com.kallan.naistudio.models.ComicObjectHit
import com.kallan.naistudio.models.withBubbles
import com.kallan.naistudio.models.withLayers
import com.kallan.naistudio.models.withTexts
import com.kallan.naistudio.models.normalizePanelOrder
import com.kallan.naistudio.models.resizedBy
import com.kallan.naistudio.models.withPanelMoved
import com.kallan.naistudio.models.GenerateExtras
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.GenerateParamsCodec
import com.kallan.naistudio.models.HistoryGroup
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.LlmContext
import com.kallan.naistudio.models.LlmMemoryKind
import com.kallan.naistudio.models.LlmPresetEntry
import com.kallan.naistudio.models.LlmMemoryRules
import com.kallan.naistudio.models.LlmTurn
import com.kallan.naistudio.models.LlmConfig
import com.kallan.naistudio.models.MASK_BRUSH_MAX_PIXELS
import com.kallan.naistudio.models.MASK_BRUSH_MIN_PIXELS
import com.kallan.naistudio.models.DEFAULT_MASK_BRUSH_PIXELS
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.MaskSketch
import com.kallan.naistudio.models.MaskStroke
import com.kallan.naistudio.models.MaskExpand
import com.kallan.naistudio.models.NaiText
import com.kallan.naistudio.models.NibAngleControl
import com.kallan.naistudio.models.PixelMask
import com.kallan.naistudio.models.PromptField
import com.kallan.naistudio.models.PromptTranslate
import com.kallan.naistudio.models.promptOf
import com.kallan.naistudio.models.withPrompt
import com.kallan.naistudio.models.withOverrides
import com.kallan.naistudio.models.StylePreset
import com.kallan.naistudio.models.StylePresetLibrary
import com.kallan.naistudio.models.StPreset
import com.kallan.naistudio.models.StPresetRef
import com.kallan.naistudio.models.UsageStats
import com.kallan.naistudio.models.TagCodexDoc
import com.kallan.naistudio.models.TagCodexEntry
import com.kallan.naistudio.models.TagCodexProtocol
import com.kallan.naistudio.models.TagCodexQuery
import com.kallan.naistudio.models.TagCodexSummary
import com.kallan.naistudio.models.TextFieldTarget
import com.kallan.naistudio.ui.ThemeWipe
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.models.AuthQuota
import com.kallan.naistudio.models.AuthState
import com.kallan.naistudio.models.AuthUser
import com.kallan.naistudio.services.AccountSummary
import com.kallan.naistudio.services.AuthApi
import com.kallan.naistudio.services.AuthException
import com.kallan.naistudio.services.AuthMe
import com.kallan.naistudio.services.BaiduTranslate
import com.kallan.naistudio.services.ComicComposer
import com.kallan.naistudio.services.ComicPageExporter
import com.kallan.naistudio.services.DirectorTools
import com.kallan.naistudio.services.ImageMetadata
import com.kallan.naistudio.services.ImageMetadataReader
import com.kallan.naistudio.services.InpaintComposite
import com.kallan.naistudio.services.InpaintSize
import com.kallan.naistudio.services.LlmApi
import com.kallan.naistudio.services.LlmException
import com.kallan.naistudio.services.MaskCodec
import com.kallan.naistudio.services.NaiApi
import com.kallan.naistudio.services.GatewayQuota
import com.kallan.naistudio.services.NaiHttpException
import com.kallan.naistudio.services.NaiPreview
import com.kallan.naistudio.services.OfficialUpscale
import com.kallan.naistudio.services.PngMetadata
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.services.PsdWriter
// 「提示词」那一栏的编号：迁移老设置时的过滤用（见 `load()` 里 `detachedPanels` 那段）
import com.kallan.naistudio.screens.TAB_PROMPTS
import com.kallan.naistudio.platform.ImageIo
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import com.kallan.naistudio.platform.PREVIEW_MAX_DIMENSION
import com.kallan.naistudio.platform.NativeImage
import com.kallan.naistudio.platform.PenSample
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.Storage
import com.kallan.naistudio.services.TagCodexApi
import com.kallan.naistudio.models.TagZhProtocol
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------------------
// 数位板 / 触控笔（用户 2026-09-20：「compose-stylus 先接这个」✓）
// ---------------------------------------------------------------------------

/**
 * 压力 → 笔刷直径的**下限系数**：`直径 = 滑杆值 × (0.25 + 0.75 × pressure)` ✓（用户口径 ✓，线性 ✓）。
 *
 * 为什么不留到 0：最轻的一笔也该看得见 ✓ —— 拿 0 去乘半径会得到"细得看不见的一条线"，
 * 用户会以为工具坏了 ✗。
 */
private const val PEN_BRUSH_MIN_FACTOR = 0.25f

/**
 * 压感曲线：**1 = 线性** ✓（口径同 [ImageEditOps.DEFAULT_PRESSURE_CURVE] ✓）。
 *
 * 逐点压感那一批（用户 2026-09-20：「**逐点压感和旋转做**」✓）把"压力 → 半径"整个搬进了
 * `ImageEditSession` / [ImageEditOps.brushRadiusPixels]（**每个笔尖各算各的** ✓），
 * 这里只留"默认参数是哪一档"这一个来源 ✓ —— `0.25` + `1.0` 加起来
 * **正好等于参数化之前那条老公式** ✓（`0.25 + 0.75 × p` ✓，**默认手感没变** ✓）。
 */
private const val PEN_PRESSURE_CURVE = 1f

/**
 * 状态行**最短更新间隔**（毫秒 ✓）。
 *
 * 数位板一秒能报上百个点 ✗ —— 这行字一秒变几次就够了 ✓（用户口径：别每帧 ✗）。
 */
private const val PEN_STATUS_MIN_INTERVAL_MS = 100L

/** 没有压感时"压力"那一格显示的占位（**不是 0、也不是 1** ✓ —— 见 [PenSample.pressure] 的 ⚠️ ✓）。 */
private const val PEN_NO_VALUE = "—"

/**
 * 两位小数（**共用树里不能用 `String.format`** ✗ —— 那是 JVM 的，手写一个 ✓）。
 */
private fun twoDecimals(value: Float): String {
    val scaled = (value.coerceIn(0f, 1f) * 100f).roundToInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

/** 预扣报价。`amount` 为 null 表示还没拿到报价。 */
data class AnlasQuote(
    val amount: Int? = null,
    val balance: Int? = null,
    val insufficient: Boolean = false,
    /** 与参考实现同名：officialQuote / formulaQuote / pendingQuote。 */
    val source: String = "pendingQuote",
)

/**
 * 高级漫画「跑这一页」的**跑前报价单**（第 ③ 批）。
 *
 * 用户口径 `docs/43` §5 风险 1：「逐格生成 = 多次付费调用 → **跑前报总消耗并确认**」✓。
 * 所以这里是**确认框唯一的依据**：逐格清单（[ComicCost.Estimate.lines]）+ 合计 ✓。
 *
 * @param unpricedSizes 官方报价**没拿到**的那几种尺寸。这些格子按 0 计过（[ComicCost] 的合计是
 *   一个 Int），所以界面上必须**如实标"未知"** ✗ —— 不能把"没问到价"显示成"不要钱"✓。
 */
data class ComicRunQuote(
    val estimate: ComicCost.Estimate,
    val unpricedSizes: Set<Pair<Int, Int>> = emptySet(),
) {
    val hasUnknown: Boolean get() = unpricedSizes.isNotEmpty()

    /** 这一格的价格有没有问到（确认框逐行显示用 ✓）。 */
    fun priced(line: ComicCost.Line): Boolean = (line.width to line.height) !in unpricedSizes
}

/** 排队中的一次生成（参数是排队那一刻的快照）。 */
data class QueuedJob(
    val params: GenerateParams,
    val extras: GenerateExtras,
    val total: Int,
    val intervalSeconds: Int,
)

/** 一次性弹窗提示（外壳负责弹 Toast 并消费掉）。 */
data class ToastEvent(val message: String, val isError: Boolean = false)

/**
 * "当前这张是某张图的 2× 放大版" 时记下**放大前**那张。
 * 图生图 / 遮罩重绘一律用放大前的图（坐标口径不变），预览则显示放大后的。
 */
data class UpscaleSource(val path: String, val width: Int, val height: Int)

/**
 * **一块活笔画** —— ⚠️ 第 ㉜a 批**整条删掉了** ✗：笔现在**直接画在层表面上**
 * （`ImageEditSession.layerSurfaceForDisplay()` ✓），显示那一半每帧从表面**读可见区**
 * 到视口分辨率（见 [AppState.comicPaintViewportFrame] ✓）⇒ **没有小块叠加这回事了** ✓。
 *
 * 删掉的东西（`docs/54` 里有逐条清单 ✓）：`ComicPaintLivePatch` / `LIVE_PATCH_CAP` /
 * `comicPaintLivePatches` / `syncComicPaintLivePatches` / `clearComicPaintLivePatches` /
 * `rebuildComicPaintBitmapInBackground` / `comicPaintRebuildPending` / `comicPaintBitmapJob` /
 * `comicPaintImage()` / `ImageEditSession` 里"活笔画缓冲 → 抬笔合层"那一支（含 `LiveStrokeLayer` 的显示用途）✓。
 */

/**
 * 应用状态。
 *
 * **行为对齐参考实现** `mobile/lib/state/app_state.dart`。
 *
 * 几个与参考实现一致、但容易被改错的点：
 *  · `status` 存的是**已经解析好的文案**（参考实现用 `_rt()`/`_rf()` 在赋值时就解析），
 *    不是 i18n key；只有 `common.ready` 会在 [displayStatus] 里按当前语言重取。
 *  · `setToken` **先联网校验成功才落盘**，校验失败不保存。
 *  · 账号刷新失败时**保留上一次的真实余额**，只标记 stale，绝不编造 0。
 *  · 参数持久化有"每工具开关"：关掉的工具每次启动回落到默认值。
 *  · 余额刷新失败**不能**把已经保存成功的图翻成失败。
 */
class AppState(private val platform: Platform) : ViewModel() {

    private val storage = Storage(platform)

    /** 图片能力（遮罩编解码等要用）。界面层走 LocalImageIo，这里给状态层自己用。 */
    val images: ImageIo get() = platform.images
    private val api = NaiApi()
    private val authApi = AuthApi()

    // ---------------------------------------------------------------- 状态

    private var paramsState by mutableStateOf(GenerateParams())

    /**
     * 生成参数。**写入口只有一个** —— 所有改参数的地方（手打、垃圾桶、AI 翻译/优化、
     * 撤销/重做、从图片元数据导入、备份恢复……）都走这个 setter，
     * 于是"锁住的提示词改不动"这件事**只需要在这里判一次**，
     * 不用去每一个调用点补 guard（补漏一处就是一个能改动的后门）。
     */
    var params: GenerateParams
        get() = paramsState
        private set(value) {
            paramsState = enforcePromptLocks(value)
        }
    var extras by mutableStateOf(GenerateExtras())
        private set
    var settings by mutableStateOf(AppSettings())
        private set

    /**
     * 装载期间临时关掉提示词锁。
     *
     * 起因：锁在 [params] 的 setter 里生效，而"读盘 / 恢复备份"也是**赋值**。
     * 要是不关，读盘那一刻就会把刚读出来的提示词当成"一次非法修改"钉回**默认空串**——
     * 表现就是"锁着提示词重启一次，内容没了"。所以装载路径显式挂起，装完再恢复。
     */
    private var promptLocksSuspended = false

    /**
     * 把锁住的提示词钉回当前值。
     *
     * 以**调用前的 [paramsState]** 为准（setter 还没写进去），所以无论外面传进来什么，
     * 锁住的那几项都保持原样。
     */
    private fun enforcePromptLocks(next: GenerateParams): GenerateParams {
        if (promptLocksSuspended) return next
        var out = next
        if (settings.promptLockStyle) out = out.copy(stylePrompt = paramsState.stylePrompt)
        if (settings.promptLockPositive) out = out.copy(positivePrompt = paramsState.positivePrompt)
        if (settings.promptLockNegative) out = out.copy(negativePrompt = paramsState.negativePrompt)
        return out
    }

    /** 这条提示词锁没锁（见 [PromptField]）。 */
    fun isPromptLocked(which: String): Boolean = when (PromptField.orDefault(which)) {
        PromptField.STYLE -> settings.promptLockStyle
        PromptField.NEGATIVE -> settings.promptLockNegative
        else -> settings.promptLockPositive
    }

    /** 开 / 关某一条提示词的锁。锁是设置的一部分，所以直接落盘。 */
    fun setPromptLock(which: String, on: Boolean) {
        val next = when (PromptField.orDefault(which)) {
            PromptField.STYLE -> settings.copy(promptLockStyle = on)
            PromptField.NEGATIVE -> settings.copy(promptLockNegative = on)
            else -> settings.copy(promptLockPositive = on)
        }
        if (next == settings) return
        settings = next
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setSettings(next) } }
        toast(if (on) rt("prompt.locked") else rt("prompt.unlocked"))
    }

    var account by mutableStateOf(AccountSummary(hasToken = false))
        private set
    var hasToken by mutableStateOf(false)
        private set

    // ------------------------------------------------------ 自建账号（托管模式）

    /** 登录态。托管模式（settings.hostedMode）下，LoggedOut 会被外壳门禁拦在登录页。 */
    var authState by mutableStateOf<AuthState>(AuthState.LoggedOut)
        private set

    /** 登录/注册进行中（登录页显示 loading）。 */
    var authBusy by mutableStateOf(false)
        private set

    /**
     * 是否走托管模式（配了服务器地址且开了开关）。
     * **纯净版（`local` 风味）恒为 false**：即使设置里残留 `hostedMode=true` / 账号服务器地址，
     * 也不会走账号体系，永远本地直连 NovelAI（登录门禁也因此不会出现）。
     */
    val hosted: Boolean
        get() = platform.hostedEdition &&
            settings.hostedMode &&
            settings.accountServerUrl.isNotBlank()

    val isLoggedIn: Boolean get() = authState is AuthState.LoggedIn

    /**
     * 登录页的「我已有 NovelAI API，跳过」：用户自带 NovelAI token → **切回 BYO 直连模式**。
     * 关掉账号模式（持久化）：门禁随之消失，设置页恢复自填 token 的入口。
     * 没有 API 的用户则只能登录使用（不给这个出口就是了）。
     */
    fun useOwnNovelAiToken() = setSettings { it.copy(hostedMode = false) }

    /** 「我的」页头像点击（未登录时）：服务器地址未配置则提示，否则开账号模式——门禁自动切到登录页。 */
    fun beginLogin() {
        if (settings.accountServerUrl.isBlank()) {
            status = rt("account.serverNotSet")
            return
        }
        setSettings { it.copy(hostedMode = true) }
    }

    /** 托管模式且未登录 → 外壳应显示登录门禁页。 */
    val showLoginGate: Boolean get() = hosted && authState is AuthState.LoggedOut

    /**
     * 生图/账号请求实际用的**凭证**与**基址**：
     *  · 托管模式 = 本账号 accessToken + 账号服务器（生图走代理）
     *  · 否则     = NovelAI token + NovelAI image 基址（老的自填直连）
     */
    /**
     * 生图/账号请求实际用的**凭证**与**基址**：
     *  · 托管模式 = 本账号 accessToken + 账号服务器（生图走代理）
     *  · 第三方网关 = **网关那把 Key + 网关站点地址**（和官方**各存各的**，切换不丢）
     *  · 否则     = NovelAI token + NovelAI image 基址（老的自填直连）
     */
    private fun effectiveToken(): String = when {
        hosted -> storage.getAccessToken()
        settings.useThirdPartyApi -> storage.getGatewayToken()
        else -> storage.getToken()
    }

    private fun effectiveImageBase(): String = when {
        hosted -> settings.accountServerUrl
        settings.useThirdPartyApi -> settings.thirdPartyBaseUrl
        else -> settings.imageBaseUrl
    }

    // -----------------------------------------------------------------------
    // **第三方网关兼容**（用户 2026-09-26：https://github.com/fangchen2003/service-tools）
    //
    // 那个项目（NAI Gate）对客户端而言就是一个"能改地址的 NovelAI"：站点根地址填进
    // 「NAI API」那张卡的接口地址、`nai-...` 虚拟 Key 当 token 填进来就行，**不需要另做协议**。
    // 这里只补两件它做不到、又必须让用户知道的事（细节见 `NaiApi` 里那一大段）：
    //  ① 它**没开 img2img / 局部重绘** ⇒ 进这些模式时先提示，别让用户白等一次请求；
    //  ② 它的 V5 日额度在另一条接口上 ⇒ 拉回来给「我的」那张卡显示。
    // -----------------------------------------------------------------------

    /**
     * 现在这条路是不是自托管网关（而不是 NovelAI 官方）。
     *
     * ⚠️ **判据是那个显式开关 `settings.useThirdPartyApi`，不是"地址像不像第三方"** ——
     * 早前一版用后者，结果用户点「第三方」时地址还是空的、按那条判据算官方 ⇒
     * 开关刚点亮就熄了、界面毫无反应（真机上就是"手机端第三方无法点击"）。
     * 详见 `AppSettings.useThirdPartyApi` 的说明。
     */
    val usingGateway: Boolean
        get() = !hosted && settings.useThirdPartyApi

    /** 网关自己那块配额（V5 今天还剩几张 / 本月 Anlas）；没拉到或不是网关 ⇒ null。 */
    var gatewayQuota by mutableStateOf<GatewayQuota?>(null)
        private set

    /**
     * 拉一次网关配额。**只在用网关时发**，失败静默（这块是附加信息，不是主流程）。
     * 调用时机：设置里改完地址之后、以及「我的」那张卡刷新余额时。
     */
    fun refreshGatewayQuota() {
        if (!usingGateway) {
            gatewayQuota = null
            return
        }
        val token = effectiveToken()
        if (token.isBlank()) return
        viewModelScope.launch {
            gatewayQuota = api.fetchGatewayQuota(token, effectiveImageBase())
        }    }

    /**
     * 进图生图 / 遮罩重绘之前问一句：**这条路上这两样是不通的**（网关没实现）。
     *
     * 返回 true = 放行。用网关时返回 false 并弹一句人话，调用方直接 return。
     * ⚠️ 判据收在这一处，别散到各按钮上 —— 以后网关支持了，改这一个地方。
     */
    private fun gatewayAllows(action: String): Boolean {
        if (!usingGateway || !api.gatewayUnsupportedAction(action)) return true
        toast(
            rf("gateway.unsupported", mapOf("action" to action)),
            isError = true,
        )
        return false
    }

    /**
     * 网关模式下的**站点地址填了没**（用户 2026-09-26：「第三方**也不使用官方 api** 啊」）。
     *
     * ⚠️ 这一条是**必须**的：所有请求最终都过 `NaiApi.normalizeBase(base, DEFAULT_IMAGE_BASE)`，
     * 而那条兜底在地址为空时会回落成 **NovelAI 官方地址**。网关模式下地址没填就成了
     * "拿虚拟 Key 去打官方" —— 必然 401，而且用户完全看不出为什么。
     * 所以**发请求之前先在这里拦住**，给一句"请先填站点地址"。
     * 返回 true = 放行。
     */
    private fun gatewayAddressReady(): Boolean {
        if (!usingGateway) return true
        if (settings.thirdPartyBaseUrl.isNotBlank()) return true
        toast(rt("gateway.needBaseUrl"), isError = true)
        return false
    }

    var history by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    /**
     * **临时图库**：本次会话生成出来的图片（运行条右边那个按钮）。
     *
     * 刻意只放在内存里、不落盘：进程一被杀（后台把 App 关掉）就自然清空，
     * 不需要额外的「清理」逻辑，也不会污染真正的图库历史。
     */
    val sessionImages = androidx.compose.runtime.mutableStateListOf<HistoryItem>()

    /** 临时图库每张图的生成耗时（毫秒），按 [HistoryItem.id] 关联；同样只在内存里，随进程清空。 */
    val sessionImageDurationMs = androidx.compose.runtime.mutableStateMapOf<String, Long>()

    /**
     * **本次会话（这次启动）开始的时刻**（毫秒）——「临时图库」抽屉用它把"本次启动之后产生的
     * 东西"从 [history] 里筛出来（用户 2026-09-22：「临时」= 本次启动之后）。
     *
     * 构造时记一次即可：`AppState` 是随进程建的，进程被杀（后台关掉软件）就重建一个新的。
     * 用它而不是用 [sessionImages] 的原因：抽屉要按「已生成 / 已导入」分成两栏，
     * 而 [sessionImages] 只装生成类的结果、并且顺序/批次信息不如 [history] 完整。
     */
    val sessionStartedAt: Long = System.currentTimeMillis()

    /**
     * [sessionStartedAt] 的 **ISO8601 文本**（`yyyy-MM-dd'T'HH:mm:ss.SSS`，与 [HistoryItem.createdAt]
     * 同一格式，见 `Storage.iso8601`）——临时图库直接拿它和 `createdAt` 做**字典序**比较
     * （同格式、零填充 ⇒ 字典序 = 时间序），不必逐条解析时间。
     *
     * ⚠️ 这里写死 `Locale.US`：`Storage.iso8601` 也是 US，两边必须同源，否则月份/上下午的
     * 本地化格式会让字典序失真。
     */
    val sessionStartIso: String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(sessionStartedAt))

    /**
     * **同一次生成产出的一组图**：`HistoryItem.id` → 批次号（本会话内唯一）。
     *
     * 为什么需要它：`HistoryItem` 落盘结构里**没有**批次字段（`groupId` 是已废弃的"历史分组"，
     * 恒为 null，见 `persistGeneratedImages`），而临时图库要求"同一批合成一项 + 角标显示张数"。
     * 这样记录**不动任何落盘结构**（`HistoryItem` 的 JSON 一个字节都不改）：
     * 批次号只住内存，和临时图库本身一样，进程一没就清空。
     *
     * 哪里写入：批量生成的那条循环（`startGeneration` 每个批次一个号）、导演工具的一次执行、
     * 以及任何一次 [persistGeneratedImages]（没传批次号就当场开一个新的 —— 一次请求回来多张
     * 也算同一批）。见 [newSessionBatchId]。
     */
    val sessionBatchIds = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /**
     * 开一个新的会话内批次号。
     *
     * 用"毫秒时间 + 进程内自增序号"：同一毫秒里连开多个（比如一次请求回来多张、
     * 紧接着又来一次）也不会撞号 —— 理由和 `HistoryItem.id` 里拼 `saveSequence` 是同一个。
     */
    private var sessionBatchSeq = 0L
    private fun newSessionBatchId(): String = "batch-${System.currentTimeMillis()}-${++sessionBatchSeq}"

    var groups by mutableStateOf<List<HistoryGroup>>(emptyList())
        private set
    var current by mutableStateOf<HistoryItem?>(null)
        private set

    /**
     * "当前的图是某张图的 2× 放大版" 时，记下**放大前**那张（路径 + 尺寸）。
     * 预览显示放大后的，但**图生图 / 遮罩重绘用放大前的**（尺寸和坐标口径都不变）。
     * 任何一次新的生成/选中都会把它清掉。
     */
    var upscaleSource by mutableStateOf<UpscaleSource?>(null)
        private set

    /** 风格预设库（分组 + 预设）。 */
    var styleLibrary by mutableStateOf(StylePresetLibrary())
        private set

    var busy by mutableStateOf(false)
        private set
    private var statusState by mutableStateOf("")

    /**
     * 状态行文案（**已经解析好的文本**，不是 i18n key）。
     *
     * 赋值时**自动进 [statusHistory]** —— 底部抽屉的「日志模式」要显示"刚刚都发生了什么"，
     * 而全项目有 90 多处 `status = …`，与其挨个加记录，不如在属性上收口（一处记全）。
     */
    var status: String
        get() = statusState
        private set(value) {
            if (statusState == value) return
            statusState = value
            if (value.isBlank()) return
            appendStatusLine(value)
        }

    /**
     * 往状态历史里记一行。
     *
     * [detail] 非空 ⇒ 抽屉「日志」里这一行**可展开**（用户 2026-09-22：
     * 「已翻译并填入…」要能下拉看到"词典命中了多少 tag / 调用了几次翻译」）。
     */
    private fun appendStatusLine(text: String, detail: List<String> = emptyList()) {
        statusHistory.add(StatusLine(System.currentTimeMillis(), text, detail))
        while (statusHistory.size > StatusLine.MAX_LINES) statusHistory.removeAt(0)
    }

    /** 状态行历史（**最新在最后**）。抽屉「日志模式」里"界面日志"那一半。 */
    val statusHistory = androidx.compose.runtime.mutableStateListOf<StatusLine>()

    /**
     * AI 交互记录（**最新在最后**）—— 抽屉「对话模式」。
     * 纯展示：不参与请求，也不维护多轮 history（用户 2026-09-16 口径）。
     */
    val conversation = androidx.compose.runtime.mutableStateListOf<ConversationTurn>()

    private var conversationSeq = 0L

    /**
     * 底部抽屉（对话 / 日志）是否展开。
     *
     * 放在状态层而不是界面里：宽屏和窄屏是**两套布局**（各有一个运行条），
     * 抽屉只有一个 —— 共享一个状态最省事，也免得两处各记一份再飘。
     */
    var statusDrawerOpen by mutableStateOf(false)
        private set

    fun openStatusDrawer() {
        statusDrawerOpen = true
    }

    fun closeStatusDrawer() {
        statusDrawerOpen = false
    }

    fun clearConversation() {
        conversation.clear()
    }

    fun clearStatusHistory() {
        statusHistory.clear()
    }

    /**
     * 记一条 AI 交互（**纯展示**）。LLM 调用与出图调用都走它。
     *
     * [kindKey] 是 i18n key（`conversation.kind.*`），界面按当前语言翻。
     */
    fun recordTurn(
        kindKey: String,
        provider: String,
        model: String,
        systemPrompt: String = "",
        userPrompt: String = "",
        imageCount: Int = 0,
        response: String = "",
        ok: Boolean = true,
        error: String = "",
        durationMs: Long = 0L,
        extra: String = "",
    ) {
        conversation.add(
            ConversationTurn(
                id = ++conversationSeq,
                atMillis = System.currentTimeMillis(),
                kindKey = kindKey,
                provider = provider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageCount = imageCount,
                toolsOffered = 0,
                response = response,
                ok = ok,
                error = error,
                durationMs = durationMs,
                extra = extra,
            ),
        )
        while (conversation.size > ConversationTurn.MAX_TURNS) conversation.removeAt(0)
    }

    var batchCount by mutableStateOf(1)
        private set
    var batchIntervalSeconds by mutableStateOf(0)
        private set

    /**
     * 排队中的生成任务：生成过程中再点一次主按钮就 +1。
     * 每一项带的是**排队那一刻**的参数/预设/张数快照，之后改参数不影响已排好的。
     */
    var queuedJobs by mutableStateOf<List<QueuedJob>>(emptyList())
        private set

    var generationQuote by mutableStateOf<AnlasQuote?>(null)
        private set
    var quoteLoading by mutableStateOf(false)
        private set
    var lastAnlasSpent by mutableStateOf<Int?>(null)
        private set

    /**
     * 流式预览位图（"模糊 → 清晰"的那张图）。
     *
     * 参考实现这里存的是**原始字节**（直接给 `Image.memory`）；本项目存**已解码的降采样位图**，
     * 因为硬约束是"UI 层不持有 base64 / 原始 PNG 字节"。同一时刻只有这一张，
     * 新帧到达即替换，旧位图交给 GC。
     */
    var generationPreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var generationPreviewProgress by mutableStateOf(0f)
        private set
    var generationPreviewStep by mutableStateOf(0)
        private set
    var generationPreviewTotalSteps by mutableStateOf(0)
        private set

    /** 漫画画布只展示本次漫画生成的帧与对应页的成图，避免混入普通模式的旧工作图。 */
    private val comicCanvasImages = mutableStateMapOf<Int, String>()
    var comicCanvasGenerating by mutableStateOf(false)
        private set
    private var comicCanvasGeneratingPage by mutableIntStateOf(-1)
    val comicCanvasPreviewRunning: Boolean
        get() = comicCanvasGenerating && comicCanvasGeneratingPage == extras.comicPageIndex

    fun comicCanvasImagePath(): String? = comicCanvasImages[extras.comicPageIndex]

    var booted by mutableStateOf(false)
        private set

    /** 一次性导航请求：图库点「使用此图图生图」后回到文生图页（外壳监听这个计数器）。 */
    var navigateToGenerateTick by mutableIntStateOf(0)
        private set

    /**
     * 图库的搜索词 + 顶栏搜索胶囊是否展开。
     *
     * 放在 AppState（而不是图库页的局部状态）是因为搜索框已经**搬到顶栏** ——
     * 和角色图鉴一样：右上角放大镜 → 胶囊滑出，顶栏与页面必须共用同一份状态。
     */
    var galleryQuery by mutableStateOf("")
        private set

    var gallerySearchOpen by mutableStateOf(false)
        private set

    fun updateGalleryQuery(query: String) {
        galleryQuery = query
    }

    fun toggleGallerySearch() {
        gallerySearchOpen = !gallerySearchOpen
    }

    fun closeGallerySearch() {
        gallerySearchOpen = false
    }

    /** 正面提示词左下角「角色图鉴」按钮弹出的抽屉是否打开。 */
    var animaSheetOpen by mutableStateOf(false)
        private set
    /** 抽屉是"选角色建分区"模式（从「角色」栏进来的）。 */
    var animaSheetForCharacters by mutableStateOf(false)
        private set

    fun openAnimaSheet() {
        animaSheetForCharacters = false
        animaSheetOpen = true
    }

    /** 「角色」栏里那枚按钮：同一个抽屉，但点角色名 = 建一个新角色分区。 */
    fun openAnimaSheetForCharacters() {
        animaSheetForCharacters = true
        animaSheetOpen = true
    }

    fun closeAnimaSheet() {
        animaSheetOpen = false
    }

    // -------------------------------------------------------------- 工具目录

    /** 当前选中的工具（工具页顶部胶囊 + 侧边栏「工具」分组里高亮的那一项）。 */
    var selectedTool by mutableStateOf(ToolCatalog.defaultId)
        private set

    /**
     * 一次性请求：**生成页**要打开的面板（`director` / `characters`）。
     *
     * 侧边栏点「导演台 / 角色分区」这类住在生成页的工具时，外壳先翻页、再置位这个值；
     * 生成页看到它就打开对应面板，然后立刻 [consumeGenerateAction] 清掉（不然每次重组都会重开）。
     */
    var pendingGenerateAction by mutableStateOf<String?>(null)
        private set

    /** 记下当前工具（工具页用它决定渲染哪张卡片）。 */
    fun selectTool(id: String) {
        selectedTool = ToolCatalog.byId(id).id
    }

    /**
     * 请求打开生成页上的某个面板（侧边栏 / 工具页点「导演台」「角色分区」时用）。
     * 顺便把当前工具也记上，回到工具页时停在同一个工具。
     */
    fun requestGenerateAction(toolId: String, action: String) {
        selectTool(toolId)
        pendingGenerateAction = action
    }

    fun consumeGenerateAction() {
        pendingGenerateAction = null
    }

    /** 请求翻回生成页（工具页点了住在生成页的工具时用；外壳监听这个计数器）。 */
    fun requestGeneratePage() {
        navigateToGenerateTick++
    }

    // ------------------------------------------------ 工具浮动窗口（主窗口内浮一块）

    /**
     * **侧边栏点工具 = 在主窗口里浮出一扇窗口**，不再翻到工具页（用户 2026-09-20：
     * 「工具这些功能不用跳转页面，出现一个和提示词框差不多的新窗口，要有 X 键」）。
     *
     * 这里只记"哪几扇开着"（工具 id）。每扇窗口的**位置**由外壳 `StudioShell` 按 id 记盘
     * （键 `tools.window.<id>.x/y`）—— 和分离面板一个道理：那儿才量得到容器多大、能把位置夹住。
     */
    var openToolWindows: Set<String> by mutableStateOf(emptySet())
        private set

    /** 「哪几个工具在浮动窗口里」的落盘键（逗号分隔的工具 id）。 */
    private val KEY_TOOL_WINDOWS = "tools.window.set"

    fun isToolWindowOpen(id: String): Boolean = id in openToolWindows

    /**
     * 打开一扇工具窗口。
     *
     * ⚠️ **已经开着就什么都不做**（不重置位置）：侧边栏那一项同时也是"它开着呢"的高亮依据，
     * 用户再点一下是想回到那扇窗口，而不是让它跳回默认落点。
     */
    fun openToolWindow(id: String) {
        val toolId = ToolCatalog.byId(id).id
        if (toolId in openToolWindows) return
        openToolWindows = openToolWindows + toolId
        persistToolWindows()
    }

    /** 关上那一扇（窗口标题条右上角那颗 X）。 */
    fun closeToolWindow(id: String) {
        if (id in openToolWindows) {
            openToolWindows = openToolWindows - id
            persistToolWindows()
        }
    }

    private fun persistToolWindows() {
        platform.kv.edit()
            .putString(KEY_TOOL_WINDOWS, openToolWindows.joinToString(","))
            .apply()
    }

    // ------------------------------------------------ 窗口顶部菜单（电脑）

    /**
     * 「风格预设」面板（提示词栏里那颗「选择预设」胶囊，以及**菜单「文件 → 预设」**）开着没开着。
     *
     * 原来它是 `GenerateScreen` 里的一个 `rememberSaveable`，菜单在 `StudioShell`（更外层）
     * 够不着 —— 和「历史提示词」抽屉同一回事，所以也挪进状态层（用户 2026-09-19）。
     */
    var styleSheetOpen by mutableStateOf(false)
        private set

    fun openStyleSheet() {
        styleSheetOpen = true
    }

    fun closeStyleSheet() {
        styleSheetOpen = false
    }

    /**
     * 「视图 → 重排面板」：一次性请求把**浮动的**提示词栏 / 底栏吸附回原位（外壳监听这个计数器）。
     *
     * 面板的位置状态住在 `GenerateScreen`（那里才有内容格的尺寸），所以在菜单和面板之间
     * 又需要一个"计数器信箱"——和 `navigateToGenerateTick` 同一个套路。
     */
    var panelRedockTick by mutableIntStateOf(0)
        private set

    fun requestPanelRedock() {
        panelRedockTick++
    }

    /**
     * 「分离 …」：哪几个面板正在独立窗口里 —— ⛔ **功能已去掉**（用户 2026-09-24 ✓）。
     *
     * ⚠️ 钉成**恒空集** ✓：布局那边到处 `isPanelDetached(tab)` / `tab in detachedPanels` ✓，
     * 空集 ⇒ 那些浮动分支**永远不成立** ✓ = 用户要的"固定的、按现在的形态" ✓。
     * 盘上老设置里那串（`panels.detached.set` ✓）**一律不读** ✓（不然老用户升级后还飘着 ✗，
     * 而且菜单里已经没地方收回来了 ✗✗）。
     */
    val detachedPanels: Set<Int> = emptySet()

    /** 「显示提示词栏 / 显示底栏」的落盘键（true = 显示，缺省也是显示）。 */
    private val KEY_SHOW_PROMPT_PANEL = "panels.show.prompt"
    private val KEY_SHOW_BOTTOM_BAR = "panels.show.bottom"

    fun isPanelDetached(tab: Int): Boolean = tab in detachedPanels

    // ------------------------------------------------ 「窗口 → 显示 …」：两块主面板也能藏起来

    /**
     * **提示词栏 / 底栏整个藏起来**（用户 2026-09-19：「窗口选项里添加显示某某窗口的选项，
     * 可以隐藏窗口」）。
     *
     * 藏起来时画布**不吃那两块让位的边距**（否则会白留一条），也不参与命中测试
     *（见 `GenerateScreen` 宽屏分支：`contentPadding` 与 `beginPanelGesture`）。
     * 状态落盘（`panels.show.prompt` / `panels.show.bottom`），下次启动照旧。
     */
    var promptPanelHidden by mutableStateOf(false)
        private set

    var bottomBarHidden by mutableStateOf(false)
        private set

    /** 「显示提示词栏」开关（菜单勾选项）。 */
    fun togglePromptPanel() {
        promptPanelHidden = !promptPanelHidden
        platform.kv.edit().putBoolean(KEY_SHOW_PROMPT_PANEL, !promptPanelHidden).apply()
    }

    /** 「显示底栏」开关。 */
    fun toggleBottomBar() {
        bottomBarHidden = !bottomBarHidden
        platform.kv.edit().putBoolean(KEY_SHOW_BOTTOM_BAR, !bottomBarHidden).apply()
    }

    // ------------- ⛔ **分离操作面板：已经去掉**（用户 2026-09-24：「窗口的分离功能去掉，
    //               以后不需要分离了，中控底栏变成固定的，按现在的形态」✓）

    /**
     * **中控台（原「提示词栏」）是不是浮动的窗口** —— ⛔ **恒为 false** ✗（永不浮动 ✓）。
     *
     * 用户 2026-09-26 那版口径是「只有在窗口选项中的分离操控面板中选择分离底栏/中控台，
     * 这底栏和中控台才分离」✓；**2026-09-24（本轮）用户把整个功能去掉了** ✓：
     * 「窗口的分离功能去掉，以后不需要分离了，**中控底栏变成固定的，按现在的形态**」✓。
     *
     * ⚠️ 为什么**不删**这个属性、而是钉成 false ✗：布局那边（`GenerateScreen`）用
     * `if (promptFloating) … else …` 分了两条路 ✓ —— 删属性要动那几十处分支 ✓（风险大、收益零 ✓）。
     * 钉成 false 之后，**走的永远是"停靠"那条路** ✓ = 用户说的"按现在的形态" ✓；
     * 盘上老设置里那几个键也**一律不读** ✓（不然老用户一升级还是浮动的 ✗）。
     */
    val promptPanelFloating: Boolean = false

    /** **底栏是不是浮动的窗口** —— 同上，⛔ 恒为 false ✓。 */
    val bottomBarFloating: Boolean = false

    /**
     * 分离 / 收回某一栏 —— ⛔ **去掉了**（用户 2026-09-24 ✓）。
     *
     * ⚠️ 现在**什么都不做** ✗（不是"忘了接线" ✓）：那一栏的分离入口（菜单项 / 浮动框的「收回」
     * 按钮所在的浮动框本身 ✓）都已经不存在了 ✓；留着这个空函数是因为 `GenerateScreen` 里
     * 那几处 `onRedock` / 「收回」按钮还在那份（**永远不会被组合到**的）浮动分支里 ✓ ——
     * 拆掉整条浮动分支风险大、收益零 ✓（见 `promptPanelFloating` 的说明 ✓）。
     */
    fun toggleDetachedPanel(tab: Int) = Unit

    /** 收回某一栏 —— 同上，⛔ 去掉（空实现 ✓）。 */
    fun closeDetachedPanel(tab: Int) = Unit

    // -------------------------------------------- 面板矩形上报（工具浮动窗口吸附要用）

    /**
     * **生成页那几块面板**在**窗口客户区坐标**下的矩形（用户 2026-09-21：「还要和其他窗口吸附啊」）。
     *
     * 为什么要有这张表：工具浮动窗口的 `windowOffsets` / `windowSizes` 住在外壳最外层 `Box`
     * 里，原点就是**窗口客户区左上角**；而生成页的面板在**内容区**里（内容区原点被侧栏宽度
     * 和菜单栏高度推开了）。所以**不能**拿它们的 `offsetState` 数值直接和工具窗口比 —— 两边
     * 差着一个固定偏移 ✗。那边在面板根节点上挂
     * `Modifier.onGloballyPositioned { it.boundsInWindow() }`：`boundsInWindow()` 给的就是
     * **窗口客户区坐标**，和工具窗口那套同源，吸起来**不需要任何偏移换算** ✅。
     *
     * key 的口径（上报方见 `GenerateScreen` 宽屏分支）：
     *  · `"prompt"` = 提示词栏；
     *  · `"bottom"` = 底栏；
     *  · `"detach:<tab>"` = 「窗口 → 分离操作面板 → 分离 …」分离出来的那一块（`<tab>` 是 `TAB_*`）。
     */
    val panelRects: Map<String, Rect> get() = panelRectsState
    private val panelRectsState = mutableStateMapOf<String, Rect>()

    /**
     * 面板矩形上报：`null`（或退化成一条线 / 一个点）表示这块**现在没画** → 把旧值删掉。
     *
     * ⚠️ 不删会留下一枚**过期矩形**：面板藏起来 / 被收回之后，工具窗口照旧会往那块已经不在
     * 那儿的边上吸 —— 表现就是"吸到空气"（用户报过的那类症状）。所以 `GenerateScreen` 那边
     * 上报时一律带着"这块现在可见吗"：不可见就报 `null`（`onGloballyPositioned` 里按可见性
     * 判断，可见性翻转时再拿缓存的坐标重报 / 清除一次 —— 只清不重报的话，"藏起来再放出来"
     * 之后那块就再也吸不上了）。
     */
    fun reportPanelRect(key: String, rect: Rect?) {
        if (rect == null || rect.width <= 0f || rect.height <= 0f) {
            panelRectsState.remove(key)
        } else {
            panelRectsState[key] = rect
        }
    }

    /** 一次性弹窗提示（导入/导出这类"一次性结果"用；外壳弹完就清掉）。 */
    var toastEvent by mutableStateOf<ToastEvent?>(null)
        private set

    fun consumeToast() {
        toastEvent = null
    }

    private fun toast(
        message: String,
        isError: Boolean = false,
        detail: List<String> = emptyList(),
    ) {
        // 状态栏也写一份：弹窗会消失，状态栏留着还能回头看
        if (detail.isEmpty()) {
            status = message
        } else {
            // ⚠️ 带明细时**每次都单独记一行**：连续两次「已翻译并填入…」文字一模一样，
            //    走 `status` 的去重会把第二次吞掉 ⇒ 明细就看不见了 ✗（用户要看的正是这一次的数）
            statusState = message
            appendStatusLine(message, detail)
        }
        toastEvent = ToastEvent(message, isError)
    }

    // ------------------------------------------------------ 图生图（导入底图）

    /** 最初导入 / 被指定为底图的那张（设置选"一直用最开始的图片"时每次都用它）。 */
    var i2iOriginalPath by mutableStateOf<String?>(null)
        private set

    /**
     * **当前底图** —— 预览区显示的就是它。
     * 生成一次之后由 `settings.i2iSourceMode` 决定：换成刚生成的那张，还是回到最初那张。
     */
    var i2iBasePath by mutableStateOf<String?>(null)
        private set

    /** 图生图模式开着 = 有底图（此时预览区显示它、主按钮变成"使用当前图片图生图"）。 */
    val img2imgActive: Boolean get() = i2iBasePath != null

    /**
     * **预览区显示的那张**（图生图模式下）。
     * 它和 [i2iBasePath] 刻意分开：生成完预览一定切到**刚生成的那张**，
     * 而下一轮真正送去生成的底图按设置（`i2iSourceMode`）决定用结果还是最初那张。
     * 遮罩涂在"看得见的那张"上，所以遮罩/「重做」也跟着它走。
     */
    var i2iDisplayPath by mutableStateOf<String?>(null)
        private set

    /** 预览图（= 遮罩网格基准）的像素尺寸。 */
    var i2iDisplayWidth by mutableIntStateOf(0)
        private set
    var i2iDisplayHeight by mutableIntStateOf(0)
        private set

    /**
     * **编辑用**那张图：如果当前是 2× 放大版，就回落到它的**放大前**那一张。
     * （遮挡重绘/图生图的坐标与尺寸都按它算，避免拿 2× 图去涂却按原图发请求。）
     */
    private val editSource: UpscaleSource?
        get() = if (current?.feature == "upscale") upscaleSource else null

    /** 当前"工作图"的像素尺寸（遮罩网格、叠加层对齐都用它）。 */
    val workImageWidth: Int
        get() = when {
            img2imgActive -> i2iDisplayWidth
            editSource != null -> editSource!!.width
            else -> current?.width ?: 0
        }
    val workImageHeight: Int
        get() = when {
            img2imgActive -> i2iDisplayHeight
            editSource != null -> editSource!!.height
            else -> current?.height ?: 0
        }

    /**
     * 当前"工作图"：**图生图模式下就是预览区显示的那张**；否则是最近生成的那张，
     * 但若那张是**官方 2× 放大版**，则回落到放大前的那张（局部重绘 / 图生图都用它）。
     */
    val workImagePath: String?
        get() = when {
            img2imgActive -> i2iDisplayPath ?: i2iBasePath
            editSource != null -> editSource!!.path
            else -> current?.filePath
        }

    // -------------------------------------------------- 局部重绘（遮罩闸门）

    /**
     * 遮罩模式（运行条上那支铅笔）。**它是按钮语义的开关**：
     *  · 打开 → 直接在生成页的图片上涂，涂上即刻生效（没有"确定"这一步），按钮变「重做」
     *  · 关掉 → 回到文生图，按钮变「生成图片」，按提示词生成新图（遮罩保留但不再参与）
     *
     * 语义对齐 ComfyUI「NAI 流式审查缓存」节点的那道人工闸门，但交互是本 App 自己的：
     * 不跳新界面、不需要放行确认。
     */
    var maskMode by mutableStateOf(false)
        private set

    /** 遮罩绑定的图片路径（换图自动失效；同一张图上可以反复改）。 */
    var maskSourcePath by mutableStateOf<String?>(null)
        private set

    /** 涂改是就地进行的，用版本号驱动叠加层重建。 */
    var maskVersion by mutableIntStateOf(0)
        private set

    /** 已涂面积占比（0..100，四舍五入），供运行条显示。 */
    var maskPaintedPercent by mutableIntStateOf(0)
        private set

    /** 笔刷直径，**单位是原图像素**（1..350）。 */
    var maskBrushPixels by mutableStateOf(DEFAULT_MASK_BRUSH_PIXELS)
        private set

    /** 笔尖形状：true = 圆形（默认），false = 方形。 */
    var maskRoundBrush by mutableStateOf(true)
        private set

    /** 当前是橡皮还是画笔。 */
    var maskErasing by mutableStateOf(false)
        private set

    /** 有没有可撤销的笔迹。 */
    var maskCanUndo by mutableStateOf(false)
        private set

    /** 当前图上存在有效遮罩（不管在不在遮罩模式，都要画在图上给用户看）。 */
    val maskVisible: Boolean
        get() = maskPaintedPercent > 0 && maskSourcePath != null && maskSourcePath == workImagePath

    /** 遮罩参与请求（= 遮罩模式 + 有遮罩）。只有这时按钮才是「重做」。 */
    val maskActive: Boolean
        get() = maskMode && maskVisible

    /** 遮罩草图（非可观察，配对 [maskVersion] 使用）。 */
    private var maskSketch: MaskSketch? = null

    // ---- 聚焦重绘（官方 Focused Inpainting，做在**遮罩层**里）----

    /**
     * 「聚焦重绘」的框工具是否激活（遮罩工具条上那颗虚线框）。
     *
     * 激活后：在图上**拖一个矩形** = 圈出"要重绘的那一小块"（框里可以照常涂遮罩，
     * 不涂就是整框重绘）；发请求前这一块会被**放大到约 100 万像素**，所以细节更多。
     */
    var maskFocusTool by mutableStateOf(false)
        private set

    /** 聚焦框（归一化矩形）；null = 没框，走普通遮罩重绘。 */
    var maskFocusRect by mutableStateOf<SelectionShape.Rect?>(null)
        private set

    fun chooseMaskFocusTool(on: Boolean) {
        if (maskFocusTool == on) return
        maskFocusTool = on
        // 关掉框工具时把框留着（用户可能只是想回去刷遮罩），想清掉就再点一次旁边的"清框"
        status = if (on) rt("mask.focusOn") else rt("mask.focusToolOff")
    }

    fun updateMaskFocus(rect: SelectionShape.Rect?) {
        maskFocusRect = rect
        if (rect != null) {
            logInfo("MaskFocus", "frame set: (${rect.left},${rect.top})-(${rect.right},${rect.bottom})")
        }
    }

    fun clearMaskFocus() {
        maskFocusRect = null
        logInfo("MaskFocus", "frame cleared")
        status = rt("mask.focusCleared")
    }

    /** 当前有没有可用的聚焦框（状态行/按钮上用）。 */
    val maskFocusActive: Boolean get() = maskFocusTool || maskFocusRect != null

    /** 聚焦框的像素尺寸（状态行显示 `300×300`）。 */
    fun maskFocusSize(): Pair<Int, Int> {
        val rect = maskFocusRect ?: return 0 to 0
        val dims = workImagePath?.let { MaskCodec.imageSize(images, it) } ?: return 0 to 0
        val bounds = SelectionGeometry.bounds(rect, dims.first, dims.second) ?: return 0 to 0
        return bounds.w to bounds.h
    }

    /**
     * **聚焦框自动算出来的目标尺寸**（用户 2026-09-26 口径：「长宽自动计算……1024×1024
     * 像素以下自动计算最高像素」）。
     *
     * = `InpaintSize.forRect(框的原图像素宽, 框的原图像素高)`：等比 → 面积顶到 1024×1024
     * 以下的最大值 → 两边对齐到 64 的倍数（宽高**可以**超过 1024，只有总像素不能超 ✓）。
     *
     * ⚠️ **只读**：界面拿它显示（「聚焦：1792×576（3:1 ✓）」），**没有**任何入口让用户改这个数
     * ✗ —— 用户原话是"自动计算"。
     *
     * ⚠️ 和真正发出去的**请求尺寸**不是同一个数：请求图是"框 + 上下文圈"那一整块裁剪，
     * 它的尺寸见 [maskFocusPlan]（`Plan.requestW/requestH`），比这里大一圈。两个数都会显示出来。
     */
    fun maskFocusTargetSize(): Pair<Int, Int> {
        val (w, h) = maskFocusSize()
        if (w <= 0 || h <= 0) return 0 to 0
        return InpaintSize.forRect(w.toFloat(), h.toFloat())
    }

    /**
     * 聚焦重绘的**方案**（裁剪矩形 + 请求尺寸 + 框在请求里的位置）。
     * 没框、或框太小算不出方案时返回 null（那就退回普通遮罩重绘）。
     */
    fun maskFocusPlan(): FocusedInpaint.Plan? {
        val rect = maskFocusRect ?: return null
        val path = workImagePath ?: return null
        val dims = MaskCodec.imageSize(images, path) ?: return null
        val frame = SelectionGeometry.bounds(rect, dims.first, dims.second) ?: return null
        if (frame.isEmpty) return null
        return FocusedInpaint.plan(
            frame = frame,
            contextPixels = settings.inpaintFocusContext,
            sourceWidth = dims.first,
            sourceHeight = dims.second,
            // 放大倍率 / 免费档这两项**已经不参与尺寸计算**了（保留参数是因为 plan 的旧口径
            // 还要给单测用；界面上它们不再决定大小，见下面 autoSizeByRect 的注释）
            scale = settings.inpaintFocusScale,
            limitToFreeTier = settings.inpaintFocusFreeTierOnly,
            // 用户 2026-09-26：「聚焦修改的长宽**自动计算**」—— 请求尺寸由 `InpaintSize.forRect`
            // 按「等比 + 总面积 ≤1024×1024 + 64 倍数」自动算（宽高可超 1024，总像素不行 ✓）。
            // 喂进去的是**裁剪块**（框 + 上下文圈）的像素尺寸：那才是真正被裁出来送出去的一块；
            // 按"只有框"的比例定会让加了上下文的裁剪块被各向异性拉伸（见 FocusedInpaint.plan 的注释）。
            autoSizeByRect = true,
        )
    }

    private var cancelRequested = false
    private var generateJob: Job? = null
    private var bootedOnce = false

    /** `load()` 里那次读盘的任务：第一次 LLM 调用要**等它读完**（见 [llmChatRecorded]）。 */
    private var loadJob: kotlinx.coroutines.Job? = null

    /** 预览帧的单槽位管道；生成期间才存在。 */
    private var previewChannel: Channel<NaiPreview>? = null

    // ------------------------------------------------------------ 文案助手

    private fun rt(key: String): String = RuntimeText.text(settings.language, key)

    private fun rf(key: String, args: Map<String, Any?>): String =
        RuntimeText.format(settings.language, key, args)

    /**
     * 与参考实现一致：只有"就绪"这种无参文案会在展示时按当前语言重取，
     * 其余状态是生成当时就定格的文本。
     */
    val displayStatus: String
        get() {
            val readyZh = RuntimeText.text("zh-CN", RuntimeText.READY)
            val readyEn = RuntimeText.text("en-US", RuntimeText.READY)
            return if (status == readyZh || status == readyEn) rt(RuntimeText.READY) else status
        }

    // ---------------------------------------------------------------- 启动

    // ------------------------------------------------ 开屏与"开屏期间预热抽屉"
    // 用户 2026-09-16：冷启动后第一次拉底栏卡。前几轮把每帧成本（实时模糊）和一次性读盘
    // 挪走之后**还是卡** —— 剩下的就是"抽屉子树第一次组合"那一下。
    // 于是加开屏：**不是为了看两秒，是为了有个空窗期把那次组合做掉**。

    /** 开屏是否已经露过一次。**只认进程级首次** —— 转屏 / 重建 Activity 不该再开一次。 */
    var splashDone by mutableStateOf(false)
        private set

    fun markSplashDone() {
        splashDone = true
    }

    /** 请求"趁开屏把抽屉瞬时开合一次"（由 `MainActivity` 在读盘完成后发起）。 */
    var panelPrewarmRequested by mutableStateOf(false)
        private set

    /** 预热是否已经做完（`GenerateScreen` 放开抽屉后置位，`MainActivity` 据此收开屏）。 */
    var panelPrewarmed by mutableStateOf(false)
        private set

    fun requestPanelPrewarm() {
        panelPrewarmRequested = true
    }

    /**
     * 预热收工。
     *
     * ⚠️ 顺带把请求位清掉：`GenerateScreen` 的预热是挂在 `panelPrewarmRequested` 上的
     * `LaunchedEffect`，不清位的话它不会重跑（这是我们要的），但留着 true 会让
     * "万一 GenerateScreen 被重建"时又白预热一次。
     */
    fun markPanelPrewarmed() {
        panelPrewarmed = true
        panelPrewarmRequested = false
    }

    /**
     * 让出**一帧**（错峰加载用，见 [load] 里的说明）。
     *
     * `viewModelScope` 的协程上下文里**没有** `MonotonicFrameClock`，直接 `withFrameNanos`
     * 会抛 "No MonotonicFrameClock is available in this coroutine context"。借
     * [AndroidUiDispatcher.CurrentThread]（它同时是 CoroutineDispatcher **和** MonotonicFrameClock）
     * 就能拿到帧时钟 —— 这也是在非组合代码里等一帧的标准写法。
     *
     * ⚠️ 只能在主线程调用（[load] 就在主线程）；
     * ⚠️ 这是**优化**，不是功能：万一拿不到帧时钟，也绝不能影响启动，所以整个兜住了。
     */
    private suspend fun yieldFrame() = platform.yieldFrame()

    fun load(startWithEmptyCanvas: Boolean = false) {
        dropLegacyLlmMemoryFile()
        bootedOnce = true
        warmPromptTranslationIndex()

        // ⛔ 「哪几栏被分离出来了」**不读了** ✗（用户 2026-09-24 把分离功能整个去掉 ✓，
        //    见 `detachedPanels` 的说明 ✓）—— 老设置里那串留着不管它 ✓。
        // ⛔ 「中控台 / 底栏是停靠还是浮动」**也不读了** ✗（同上 ✓，恒为停靠 ✓）。

        // 「哪几个工具在浮动窗口里」：照旧浮出来（每扇的位置由 `StudioShell` 按 id 记）
        // ⚠️ 按目录过滤一遍：目录里删掉的工具（或更早版本留下的 id）不该浮出一扇空窗口
        openToolWindows = platform.kv.getString(KEY_TOOL_WINDOWS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { id -> ToolCatalog.entries.any { it.id == id } }
            .toSet()

        // 「显示提示词栏 / 显示底栏」：缺省显示（盘上没写过就是 true）
        promptPanelHidden = !platform.kv.getBoolean(KEY_SHOW_PROMPT_PANEL, true)
        bottomBarHidden = !platform.kv.getBoolean(KEY_SHOW_BOTTOM_BAR, true)

        // 共用画布的缩放 / 平移：**同步**读回来 ✓（界面第一帧就可能要用 ✓）——
        // ⚠️ 见 `canvasViewTouched` 的说明：这一读就是"重启后普通 ↔ 无限画布对得上"的关键 ✗。
        seedCanvasViewFromStore()

        loadJob = viewModelScope.launch {
            try {
                // ---- 错峰加载（用户 2026-09-16：「刚开应用拉底栏顿」）----
                // 这一段原本是"同步读盘 → 立刻写大 state"连着来，三个大 state 挤在同一帧里落地，
                // 等于一帧之内把生成页整棵子树重算好几遍；用户第一次下拉只要撞上这几帧就顿。
                // 两条改法：
                //  ① **同步读盘一律挪到 IO**（storage 那几个 get* 都是非挂起函数，直接在
                //     `viewModelScope`（Main）里调就是主线程读盘 + 解析）；
                //  ② **每落地一段就让出一帧**（`yieldFrame()`），让 Compose 把这一帧画完再唤醒下一批。
                // 读盘后做两条一次性升级：
                //  ① 把（第三方 GPL 来源的）旧默认文案换成新版原创文案 —— 纯内容判定，
                //     只换"没被用户动过"的，见 migrateLegacyRuleTexts；
                //  ② 漫画两段按版本号升级，见 migrateComicRules。
                settings = withContext(Dispatchers.IO) {
                    val onDisk = storage.getSettings()
                    val rulesFixed = migrateLegacyRuleTexts(onDisk)
                    // 只有真换了文案才回写一次，避免每次启动都写盘
                    if (rulesFixed != onDisk) storage.setSettings(rulesFixed)
                    migrateComicRules(rulesFixed)
                }
                // 安装包自带的那份上下文：默认加载（读盘/解析都在 IO 里，见函数注释）
                loadBundledLlmContext()
                // 无限画布那一档：把上次的状态接着摆回来 ✓（模式 + 框 + 快照记账 ✓ ——
                // **像素不在这里读** ✗，几十 MB 的解码等真进这一档时 `ensureInfiniteCanvas` 再做 ✓）
                // 桌面默认从空白普通画布开始，但快照、框和来源路径仍恢复到内存，
                // 供用户本次主动切换到无限画布时继续使用；这里不删记录、不落盘。
                restoreInfiniteCanvasState(startWithEmptyCanvas = startWithEmptyCanvas)
                // 每日统计：把盘上那一份读回来 ✓（**只读、不写** ✓ —— 没出图的启动不该产生写盘 ✓）
                usage = UsageStats.decode(settings.usageStats)
                // **提示词词典**（用户 2026-09-24：「补充词典库」✓）——
                // 打包在 `src/main/resources/dictionary.tsv` ✓（39,577 条 ✓，见 `models/TagDictionary.kt` ✓）。
                // ⚠️ 放在**后台**读 + 解析 ✓（十几 MB 文本扫一遍 ✓，主线程做会卡首帧 ✗）；
                // 读不到就保持**空表** ✓（补全整个不出现 ✓，**不是崩溃** ✗ —— 和"词典没生效"一个意思 ✓）。
                runCatching {
                    val raw = withContext(Dispatchers.IO) {
                        javaClass.getResourceAsStream("/dictionary.tsv")
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                    if (raw != null) tagDictionary = TagDictionary.parse(raw)
                }
                status = rt(RuntimeText.READY)
                yieldFrame()

                if (settings.persistGenerateParams) {
                    // ⚠️ 读盘期间挂起提示词锁：这里也是赋值，不挂起的话锁着的那几条
                    // 会被 setter 钉回**默认空串**（盘上的内容反而没了）。
                    promptLocksSuspended = true
                    try {
                        params = withContext(Dispatchers.IO) { storage.getParams() }
                    } finally {
                        promptLocksSuspended = false
                    }
                    // 角色分区与漫画分格是**两份独立存储**，这里分别读、合成一份 extras
                    val comic = loadComicState()
                    val characters = withContext(Dispatchers.IO) { storage.getCharacterPrompts() }
                    extras = GenerateExtras(characters).copy(
                        comicMode = comic.comicMode,
                        comicPanels = comic.comicPanels,
                        comicLayout = comic.comicLayout,
                        comicOrder = comic.comicOrder,
                        comicStylePrompt = comic.comicStylePrompt,
                        comicStyle = comic.comicStyle,
                        comicPages = comic.comicPages,
                        comicPageIndex = comic.comicPageIndex,
                        comicBerserk = comic.comicBerserk,
                        // ⚠️ 剧情**必须一起搬过来**：`loadComicState()` 明明读出来了
                        // （`comicPlot = settings.plot`），这里漏了一行，于是盘上有剧情、
                        // 重启后界面上却是空的 —— 用户 2026-09-18 报的"漫画剧情删后台就没了"。
                        comicPlot = comic.comicPlot,
                    ).ensureComicPages()
                    // 盘上的模式就是用户选的那份意图，记下来供「提示词」页压制后恢复
                    comicModeIntent = extras.comicMode
                    batchIntervalSeconds = settings.batchIntervalSeconds.coerceIn(0, 3600)
                    yieldFrame()
                }

                // Migrate the old independent comic toggle to the canvas mode after all
                // storyboard data is loaded. The desktop starts on a normal empty canvas.
                if (extras.comicMode != (canvasMode == 2)) setComicMode(canvasMode == 2)

                // 用户锁定了风格 / 负面词时，用设置里的存档覆盖参数
                if (settings.lockStylePrompt) params = params.copy(stylePrompt = settings.savedStylePrompt)
                if (settings.lockNegativePrompt) params = params.copy(negativePrompt = settings.savedNegativePrompt)

                // 图库那两页才用的东西（首屏看不见）—— 读盘照旧挪 IO，读完让一帧再落地。
                val loadedGroups = withContext(Dispatchers.IO) { storage.getGroups() }
                val loadedHistory = withContext(Dispatchers.IO) { storage.getHistory() }
                yieldFrame()
                groups = loadedGroups
                history = loadedHistory
                current = if (startWithEmptyCanvas) null else history.firstOrNull()

                // 数据集模式的选择器已经移除，统一按 anime 走（furry 数据集标签不再前置）
                if (settings.modelMode != "anime") {
                    settings = settings.copy(modelMode = "anime")
                    storage.setSettings(settings)
                }

                // 关掉「记住 Director 参数」→ 导演台每次启动都回落到默认值
                if (!settings.persistDirectorParams) {
                    settings = settings.copy(
                        directorTool = "bg-removal",
                        augmentDefry = 0.0,
                        augmentColorizePrompt = "",
                        augmentEmotion = "happy",
                        augmentEmotionLevel = 0.0,
                        augmentKeepTextBubbles = false,
                    )
                    storage.setSettings(settings)
                }

                // ⚠️ 分组**没了**（用户 2026-09-24：「不是在分组里新加预设」）——
                // 原来这里"没有分组就种一个 Default 分组"，现在**什么都不用种**：
                // 预设是一张平铺的表，空库就是空列表，界面显示"还没有预设"即可。
                styleLibrary = withContext(Dispatchers.IO) { storage.getStylePresets() }
                yieldFrame()

                migratePresetStylePromptOnce()

                if (hosted) {
                    // 托管模式：有 accessToken 就乐观置为“已登录”，让门禁先放行、避免闪一下登录页；
                    // 真实账号资料 / 额度由 bootAuth() 在启动后拉取，token 失效才回落登录页。
                    val access = withContext(Dispatchers.IO) { storage.getAccessToken() }
                    if (access.isNotEmpty()) {
                        hasToken = true
                        authState = AuthState.LoggedIn(AuthUser(id = "", email = ""))
                        account = AccountSummary(hasToken = true)
                    } else {
                        authState = AuthState.LoggedOut
                    }
                } else {
                    // ⚠️ 这一读要走 Keystore 解密，**必须**在 IO 上（原来是在主线程解的）
                    val token = withContext(Dispatchers.IO) { storage.getToken() }
                    hasToken = token.isNotEmpty()
                    if (hasToken) {
                        // 先放一个"有 token"的占位，真实账号请求走启动路径之外——
                        // 参考实现的注释：等它会让启动卡住（没有代理时甚至无限等）
                        account = AccountSummary(hasToken = true)
                    }
                }
            } catch (e: Exception) {
                status = rf("status.bootReadFailed", mapOf("error" to cleanError(e)))
            } finally {
                booted = true
                if (hosted) bootAuth()
                refreshAccountAtBoot()
            }
        }
    }
    /**
     * 一次性迁移：清掉旧版「应用所选」写进风格提示词框的预设内容。
     *
     * 旧版本把勾选的预设**替换写入**风格提示词；新版本改成生成时才并入，
     * 那些残留会让同一条预设被发送两次。只在残留内容与「当前启用的预设」逐字相符时才清空 ——
     * 用户自己手写或改过的风格提示词一律不动。
     */
    private suspend fun migratePresetStylePromptOnce() {
        val flag = "preset_style_migration_v1"
        if (storage.getFlag(flag)) return
        storage.setFlag(flag, true)

        val legacy = params.stylePrompt
        if (legacy.isBlank()) return
        val active = activeStylePresetPrompts()
        if (active.isEmpty()) return

        val merged = active.fold("") { acc, next -> NaiText.merge(acc, next) }
        if (normalizeTags(legacy) != normalizeTags(merged)) return

        params = params.copy(stylePrompt = "")
        storage.setParams(params)
        status = rt("status.presetStyleCleared")
    }

    /** 把提示词按逗号切成标签再规范化，用来做「是不是同一串标签」的比较。 */
    private fun normalizeTags(text: String): String = text
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

    private fun refreshAccountAtBoot() {
        viewModelScope.launch {
            val token = effectiveToken()
            if (token.isEmpty()) return@launch
            val fresh = api.fetchAccount(token, effectiveImageBase())
            account = mergeAccountKeepingLast(fresh)
            if (fresh.stale) status = rt("status.accountSyncStale")
        }
    }

    /**
     * 账号刷新失败时保留上一次的真实值（只标记 stale）。
     * 参考实现的注释大意：一次失败不能把上次的真实余额换成编造的 0。
     */
    private fun mergeAccountKeepingLast(fresh: AccountSummary): AccountSummary {
        if (!fresh.stale) return fresh
        val previous = account
        return if (previous.hasToken && previous.anlasBalance != null) {
            previous.copy(stale = true)
        } else {
            fresh
        }
    }

    // ------------------------------------------------------ 自建账号：登录 / 注册 / 登出

    /** 启动时（托管模式）拉一次 /auth/me 填真实资料与额度；401 会尝试续期，续期失败则回落登录页。 */
    private fun bootAuth() {
        viewModelScope.launch {
            val me = fetchMeWithRetry() ?: return@launch
            authState = AuthState.LoggedIn(me.user, me.quota)
            hasToken = true
        }
    }

    /**
     * 登录。成功返回 null；失败返回给用户看的错误文案。
     * 成功后：存会话 token（Keystore）、置登录态、拉额度与账号（经代理 /user/data）。
     */
    suspend fun login(email: String, password: String): String? =
        authenticate { authApi.login(settings.accountServerUrl, email, password) }

    /** 注册（可带邀请码）。成功返回 null；失败返回错误文案。成功即视为已登录。 */
    suspend fun register(email: String, password: String, inviteCode: String?): String? =
        authenticate { authApi.register(settings.accountServerUrl, email, password, inviteCode) }

    private suspend fun authenticate(call: suspend () -> com.kallan.naistudio.services.AuthSession): String? {
        if (settings.accountServerUrl.isBlank()) return rt("account.serverNotSet")
        authBusy = true
        return try {
            val session = call()
            storage.setAuthTokens(session.tokens.accessToken, session.tokens.refreshToken)
            hasToken = true
            authState = AuthState.LoggedIn(session.user)
            // 拉额度 + 账号信息（经代理），失败也不影响“已登录”
            refreshQuota()
            refreshAccountAtBoot()
            null
        } catch (e: AuthException) {
            e.message ?: rt("account.loginFailed")
        } catch (e: Exception) {
            cleanError(e)
        } finally {
            authBusy = false
        }
    }

    /** 登出：通知服务端使 refreshToken 失效（尽力而为），清本地会话，回登录态。 */
    fun logout() {
        val server = settings.accountServerUrl
        val access = storage.getAccessToken()
        if (server.isNotBlank() && access.isNotEmpty()) {
            viewModelScope.launch { authApi.logout(server, access) }
        }
        forceLogoutLocal()
        status = rt("account.loggedOut")
    }

    /**
     * 只清本地会话态（服务端会话由续期失败/过期自然处理）。
     * 顺带把 `hostedMode` 拨回 false：登出即回落 API 模式，门禁不会把用户拽回登录页。
     */
    private fun forceLogoutLocal() {
        storage.clearAuthTokens()
        authState = AuthState.LoggedOut
        hasToken = false
        account = AccountSummary(hasToken = false)
        setSettings { it.copy(hostedMode = false) }
    }

    /** 刷新账号资料与额度（设置页/账号按钮可调）。 */
    fun refreshQuota() {
        viewModelScope.launch {
            val me = fetchMeWithRetry() ?: return@launch
            authState = AuthState.LoggedIn(me.user, me.quota)
        }
    }

    /** 调 /auth/me；401 时用 refreshToken 续期一次再重试；续期失败则本地登出。网络失败返回 null（保留登录态）。 */
    private suspend fun fetchMeWithRetry(): AuthMe? {
        val server = settings.accountServerUrl
        if (server.isBlank() || storage.getAccessToken().isEmpty()) return null
        return try {
            authApi.me(server, storage.getAccessToken())
        } catch (e: AuthException) {
            when {
                e.statusCode == 401 && refreshAccessTokenOnce() ->
                    runCatching { authApi.me(server, storage.getAccessToken()) }.getOrNull()
                e.statusCode == 401 -> {
                    forceLogoutLocal()
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 用 refreshToken 换新 access（并轮换 refresh），成功返回 true。 */
    private suspend fun refreshAccessTokenOnce(): Boolean {
        val server = settings.accountServerUrl
        val refresh = storage.getRefreshToken()
        if (server.isBlank() || refresh.isEmpty()) return false
        return try {
            val t = authApi.refresh(server, refresh)
            storage.setAuthTokens(t.accessToken, t.refreshToken)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- 参数

    /** 改参数的**唯一入口**，负责质量预设与开关的联动、锁定项回写。 */
    // -----------------------------------------------------------------------
    // 正面提示词：撤销 / 重做（用户要求加那对回退按钮）
    // -----------------------------------------------------------------------

    /**
     * 三个提示词框**各有一对**撤销 / 重做栈（用户 2026-09-16 要求：按钮跟着选中的框走）。
     *
     * 栈里存的是**改动前**的整段文本。连续打字会**合并成一步**
     * （[PROMPT_UNDO_COALESCE_MS] 窗口内的改动不重复入栈），否则打 20 个字就得按 20 次撤销。
     */
    private val promptUndo = mutableMapOf<String, ArrayDeque<String>>()
    private val promptRedo = mutableMapOf<String, ArrayDeque<String>>()

    /** 合并窗口也**按框分开**：在风格框打完字立刻去正面框打字，不该被并成一步。 */
    private val lastPromptEditAt = mutableMapOf<String, Long>()

    /**
     * 每个框的按钮可用状态。
     *
     * ⚠️ **必须是 Compose 状态**。栈本身是普通 `ArrayDeque`，改它**不会触发重组**，
     * 于是按钮的灰/亮只能等别的原因引起重组（比如切一次栏目）才刷新 ——
     * 用户报的"不能实时撤销，要切换一下栏位才能操作这两个按钮"就是这个原因。
     */
    private val promptUndoFlag = mutableStateMapOf<String, Boolean>()
    private val promptRedoFlag = mutableStateMapOf<String, Boolean>()

    private fun stackOf(
        map: MutableMap<String, ArrayDeque<String>>,
        field: String,
    ): ArrayDeque<String> = map.getOrPut(field) { ArrayDeque() }

    /**
     * **最近一次改动的那个框**（提示词三条 / 剧情 / 角色与分镜的格子）。
     *
     * 给顶部菜单的「编辑 → 撤销 / 重做 / 清空这个框」用（用户 2026-09-19 要在窗口顶部加菜单）：
     * 菜单离文本框很远，不像那几条工具栏"挨着谁就作用于谁"，只能认"最后动过的那个框"。
     * [recordPromptEdit] 每记一笔就更新它。
     */
    var lastEditField: String by mutableStateOf(PromptField.POSITIVE)
        private set

    /** [field] 这个框能不能撤销。 */
    fun canUndoPrompt(field: String = PromptField.POSITIVE): Boolean =
        promptUndoFlag[PromptField.stackKey(field)] == true
    /** [field] 这个框能不能重做。 */
    fun canRedoPrompt(field: String = PromptField.POSITIVE): Boolean =
        promptRedoFlag[PromptField.stackKey(field)] == true

    /** 栈一变就同步按钮状态。 */
    private fun syncPromptUndoState(field: String) {
        promptUndoFlag[field] = stackOf(promptUndo, field).isNotEmpty()
        promptRedoFlag[field] = stackOf(promptRedo, field).isNotEmpty()
    }

    /** 撤销 [field] 这个框的最近一次改动。 */
    fun undoPrompt(field: String = PromptField.POSITIVE) {
        val key = PromptField.stackKey(field)
        val undo = stackOf(promptUndo, key)
        if (undo.isEmpty()) return
        stackOf(promptRedo, key).addLast(promptTextFor(key))
        val previous = undo.removeLast()
        // 置 0：撤销之后的第一次输入必须**另起一步**，不能被合并窗口吞掉
        lastPromptEditAt[key] = 0L
        syncPromptUndoState(key)
        applyPromptText(key, previous)
    }

    /** 重做 [field] 这个框最近被撤销掉的那次改动。 */
    fun redoPrompt(field: String = PromptField.POSITIVE) {
        val key = PromptField.stackKey(field)
        val redo = stackOf(promptRedo, key)
        if (redo.isEmpty()) return
        stackOf(promptUndo, key).addLast(promptTextFor(key))
        val next = redo.removeLast()
        lastPromptEditAt[key] = 0L
        syncPromptUndoState(key)
        applyPromptText(key, next)
    }

    /**
     * 读**任意一个**有身份的框的文本（提示词三条 / 剧情 / 角色与分镜的格子）。
     *
     * 撤销、重做、翻译、优化这四条路都要"先读原文"，而它们的框不再只有提示词三条
     * （用户 2026-09-19：每个文本框各配一条工具栏），所以读法收在这一个地方。
     */
    private fun promptTextFor(field: String): String = when {
        PromptField.isPlot(field) -> comicPlot

        PromptField.isChar(field) -> {
            val item = extras.activeItems.getOrNull(PromptField.charIndex(field))
            when {
                item == null -> ""
                PromptField.charNegative(field) -> item.negativePrompt
                else -> item.prompt
            }
        }

        else -> params.promptOf(field)
    }

    private fun applyPromptText(field: String, text: String) {
        if (PromptField.isPlot(field)) {
            // 剧情：写回 comicPlot（自带 600ms 防抖落盘）。
            // 挂起撤销记录 —— 这一笔是"撤销/重做"自己在写，再记一笔会永远撤不完。
            comicPlotUndoSuspended = true
            try {
                comicPlot = text
            } finally {
                comicPlotUndoSuspended = false
            }
            return
        }
        if (PromptField.isChar(field)) {
            // 角色 / 分镜格子：走 updateCharacter（它自己不记撤销，正是这里要的）
            val index = PromptField.charIndex(field)
            val negative = PromptField.charNegative(field)
            updateCharacter(index) {
                if (negative) it.copy(negativePrompt = text) else it.copy(prompt = text)
            }
            return
        }
        params = params.withPrompt(field, text)
        val snapshot = params
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(snapshot) } }
    }

    /** 每改一次 [field] 这个框就记一笔（带合并窗口）。 */
    private fun recordPromptEdit(field: String, previous: String) {
        val key = PromptField.stackKey(field)
        lastEditField = key
        val now = System.currentTimeMillis()
        if (now - (lastPromptEditAt[key] ?: 0L) > PROMPT_UNDO_COALESCE_MS) {
            val undo = stackOf(promptUndo, key)
            undo.addLast(previous)
            while (undo.size > PROMPT_UNDO_MAX) undo.removeFirst()
            // 新的一次编辑会把"重做"这条路砍断（常规编辑器的行为）
            stackOf(promptRedo, key).clear()
        }
        lastPromptEditAt[key] = now
        syncPromptUndoState(key)
    }

    /**
     * 三个框里谁的文本变了就记谁。
     *
     * ⚠️ 锁着的那几个**不记**：下面 [params] 的 setter 会把它们钉回去，等于根本没变 ——
     * 记了就会在撤销栈里灌空条目，点几下垃圾桶就能把栈填满。
     */
    private fun recordPromptEdits(before: GenerateParams, after: GenerateParams) {
        if (!settings.promptLockStyle && after.stylePrompt != before.stylePrompt) {
            recordPromptEdit(PromptField.STYLE, before.stylePrompt)
        }
        if (!settings.promptLockPositive && after.positivePrompt != before.positivePrompt) {
            recordPromptEdit(PromptField.POSITIVE, before.positivePrompt)
        }
        if (!settings.promptLockNegative && after.negativePrompt != before.negativePrompt) {
            recordPromptEdit(PromptField.NEGATIVE, before.negativePrompt)
        }
    }

    fun setParam(update: (GenerateParams) -> GenerateParams) {
        val before = params
        val updated = update(before)

        // 三个框各记各的（用户要求：撤销按钮跟着选中的框走）。
        // 锁着的那几个下面 setter 会钉回去，等于根本没变 —— 别白记一笔。
        recordPromptEdits(before, updated)

        var next = updated
        val qualityPresetChanged = updated.qualityPreset != before.qualityPreset
        val qualityToggleChanged = updated.qualityToggle != before.qualityToggle
        next = when {
            qualityPresetChanged -> next.copy(qualityToggle = next.qualityPreset != "none")
            qualityToggleChanged -> next.copy(qualityPreset = if (next.qualityToggle) "standard" else "none")
            else -> next
        }

        // V5 之外才有意义的质量档位与透明背景
        if (!next.isV5) {
            if (next.qualityPreset == "light") next = next.copy(qualityPreset = "standard")
            next = next.copy(qualityToggle = next.qualityPreset != "none", transparentBackground = false)
        }

        params = next
        if (settings.lockStylePrompt) {
            settings = settings.copy(savedStylePrompt = next.stylePrompt)
            storage.setSettings(settings)
        }
        if (settings.lockNegativePrompt) {
            settings = settings.copy(savedNegativePrompt = next.negativePrompt)
            storage.setSettings(settings)
        }

        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(next) } }
    }

    private companion object {
        /** 这个窗口内的连续打字合并成一次撤销。 */
        const val PROMPT_UNDO_COALESCE_MS = 800L

        /** 撤销栈上限，防止一直开着占内存。 */
        const val PROMPT_UNDO_MAX = 50

        /** 剧情框落盘防抖：打字通常 200-400ms 间隙，600ms 足够把连续输入合成一次写盘。 */
        const val COMIC_PLOT_DEBOUNCE_MS = 600L

        /** 统一焦点标记里"画布编辑器那些框"用的 key（见 `reportCanvasTextFocus`）。 */
        const val CANVAS_TEXT_INPUT_KEY = "canvas:text"

        // ---- 共用画布视图的**跨重启**存储（见 [canvasViewTouched] / [seedCanvasViewFromStore]）----
        //
        // ⚠️ 键名与"生图页那套摆放"**共用同一组** ✗ 别另起一套：
        //    普通那一档一直用它记"上次停在哪儿、放大到几倍" ✓，
        //    无限画布要的正是**同一份**（两档显示的是同一张画布 ✓）——
        //    各记各的才会出现"重启后切档对不上" ✗（2026-09-25 用户报的就是这个 ✓）。
        const val KEY_CANVAS_VIEW_SCALE = "canvas.preview.scale"
        const val KEY_CANVAS_VIEW_X = "canvas.preview.x"
        const val KEY_CANVAS_VIEW_Y = "canvas.preview.y"

        /** 缩放存成整数（`prefs.json` 里只有 int）⇒ 乘 1000 保三位小数 ✓。 */
        const val CANVAS_VIEW_SCALE_FACTOR = 1000f

        /** 视图写盘防抖：拖动时每帧都在改 ⇒ 停下来再写 ✓（同生图页那条"别把盘磨穿"的规矩 ✓）。 */
        const val CANVAS_VIEW_SAVE_DEBOUNCE_MS = 400L
    }

    /**
     * 把与"列表"有关的东西落盘。
     *
     * ⚠️ **两份列表各存各的 key**（`character_prompts_v1` / `comic_panels_v1`），
     * 写哪一份都不会碰另一份 —— 这就是"两个模式分别存储、互不干扰"的落点。
     * 漫画模式下顺带把四项设置（开关/版式/阅读顺序/风格词）也存了。
     */
    fun markCharacterChanged() {
        val snapshot = extras
        if (snapshot.comicMode) comicCanvasImages.remove(snapshot.comicPageIndex)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (snapshot.comicMode) {
                    storage.setComicPanels(snapshot.comicPanels)
                    storage.setComicSettings(persistableComicSettings(snapshot))
                } else {
                    storage.setCharacterPrompts(snapshot.charCaptions)
                }
            }
        }
    }

    /**
     * 落盘用的漫画设置：**模式一律取用户那份意图**，不取运行时镜像。
     *
     * ## 为什么不能照 [GenerateExtras] 原样写
     *
     * 停在「提示词」页时镜像里的 `comicMode` 被压成 `false`（见 [setPromptTabActive]），
     * 而写盘有好几条**异步 / 防抖**的路径：剧情输入有 600ms 防抖（[flushComicPlot]）、
     * 导出备份是协程里读的。要是照镜像写，用户在漫画页敲完剧情、600ms 内划到提示词栏，
     * 那次防抖落盘就会把盘上的 `mode` 抹成 false —— 重启之后漫画模式没了。
     *
     * 所以盘上的 `mode` 只反映"用户选了什么"，运行时镜像只影响"这次请求用哪种"。
     */
    private fun persistableComicSettings(snapshot: GenerateExtras) =
        snapshot.copy(comicMode = comicModeIntent).toComicSettings()

    /** 切换模式时两份都要落盘（开关本身就是漫画设置的一部分）。 */
    private fun persistComicState(snapshot: GenerateExtras) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                storage.setComicPanels(snapshot.comicPanels)
                storage.setComicSettings(persistableComicSettings(snapshot))
            }
        }
    }

    /**
     * 把盘上的漫画两段提示词升级到当前默认文案（见 [PromptRules.COMIC_RULES_VERSION]）。
     *
     * ## 为什么必须做
     *
     * 那两段规则是**存在盘上**的：`AppSettings.toJson()` 永远把它们整段写进设置。
     * 所以只改默认常量，老安装永远拿不到新文案 —— 表现就是"我明明改了提示词，
     * 行为却一点没变"。这在 0.2.105 之前就踩过一次（改了 `style` 相关条款，机器上没反应）。
     *
     * ## 为什么不能无条件覆盖
     *
     * 用户可以手改这两段。所以只在**能确认没被改过**时才升级：
     * 交给 [PromptRules.isUntouchedRules] 判断 —— 命中历史默认值的特征串才算没改过。
     * 手改过的留着不动。
     */
    private suspend fun migrateComicRules(current: AppSettings): AppSettings {
        if (current.comicRulesVersion >= PromptRules.COMIC_RULES_VERSION) return current

        var next = current.copy(comicRulesVersion = PromptRules.COMIC_RULES_VERSION)
        if (PromptRules.isUntouchedRules(current.comicStoryboardRules, PromptRules.COMIC_STORYBOARD_RULES)) {
            next = next.copy(comicStoryboardRules = PromptRules.COMIC_STORYBOARD_RULES)
        }
        if (PromptRules.isUntouchedRules(current.comicPagePlanRules, PromptRules.COMIC_PAGE_PLAN_RULES)) {
            next = next.copy(comicPagePlanRules = PromptRules.COMIC_PAGE_PLAN_RULES)
        }
        if (next != current) {
            withContext(Dispatchers.IO) { storage.setSettings(next) }
        }
        return next
    }

    /**
     * 把盘上（或备份里）**旧文案**的优化 / 反推 6 段规则换成当前默认值。
     *
     * ## 为什么必须做
     *
     * 这 6 段（优化 ×3 模式 + 反推 ×3 模式）是**存在盘上**的：`AppSettings.toJson()`
     * 永远整段写进设置，备份文件也带着它们。本项目早期的默认文案来自一个 GPL-3.0 的
     * 第三方插件、与 MIT 不兼容，现已全部重写；只改默认常量的话，老安装与老备份里
     * 留下的仍是旧文案 —— 表现就是"我明明换了文案，用户那边一点没变"，
     * 而且旧文案会一直跟着用户设备与备份走。
     *
     * ## 怎么做到"不动用户手改的"
     *
     * 交给 [PromptRules.upgradedRuleText]：只有命中旧文案特征串
     * （[PromptRules.PROMPT_RULES_LEGACY_MARKERS]，都是旧文案自己的小节标题／标签词）才替换成
     * 当前默认值，用户自己写的规则文本一个字都不碰。判定**只看内容、不看版本号** ——
     * 于是同一份逻辑在**启动加载**与**导入备份**两条路上都成立，而且幂等
     * （换成新版后再跑一次，新版不含任何特征串，不会被二次判成旧文案）。
     *
     * 注意两点：
     *  · 翻译规则（`PromptRules.TRANSLATE_RULES`）不落盘、只有那一个常量，无需迁移；
     *  · `LlmApi` 里工具说明那两个模板同样不落盘（每次请求现拼），也不用迁移。
     *
     * 纯函数（不落盘）：写盘交给调用方，免得启动时多写一次。
     */
    private fun migrateLegacyRuleTexts(current: AppSettings): AppSettings = current.copy(
        optimizeRulesTag = PromptRules.upgradedRuleText(current.optimizeRulesTag, PromptRules.DEFAULT_OPTIMIZE_RULES),
        optimizeRulesNatural = PromptRules.upgradedRuleText(
            current.optimizeRulesNatural,
            PromptRules.OPTIMIZE_NATURAL_RULES,
        ),
        optimizeRulesMixed = PromptRules.upgradedRuleText(current.optimizeRulesMixed, PromptRules.OPTIMIZE_MIXED_RULES),
        reverseRulesTag = PromptRules.upgradedRuleText(current.reverseRulesTag, PromptRules.DEFAULT_REVERSE_RULES),
        reverseRulesNatural = PromptRules.upgradedRuleText(
            current.reverseRulesNatural,
            PromptRules.REVERSE_NATURAL_RULES,
        ),
        reverseRulesMixed = PromptRules.upgradedRuleText(current.reverseRulesMixed, PromptRules.REVERSE_MIXED_RULES),
    )

    /**
     * 本地已存的漫画设置与分格（启动时读一次）。
     * 返回的是"要并入 extras 的片段"，不直接改状态。
     */
    suspend fun loadComicState(): GenerateExtras {
        val panels = withContext(Dispatchers.IO) { storage.getComicPanels() }
        val settings = withContext(Dispatchers.IO) { storage.getComicSettings() }

        // 一次性迁移：0.2.103 之前 LLM 的输出会覆盖整页风格词，存量安装里那个值
        // 可能是 AI 写的。用户要求"不给 AI 修改权限、回退到默认"，所以升级后退一次。
        // **只退一次**（凭 styleReverted 标记）—— 否则用户手改的风格词每次启动都会被冲掉。
        val needsRevert = !settings.styleReverted
        val style = if (needsRevert) COMIC_DEFAULT_STYLE else settings.style
        if (needsRevert) {
            withContext(Dispatchers.IO) {
                storage.setComicSettings(settings.copy(style = style, styleReverted = true))
            }
        }

        return GenerateExtras(
            comicMode = settings.mode,
            comicPanels = panels,
            comicLayout = settings.layout,
            comicOrder = settings.order,
            comicStylePrompt = settings.stylePrompt,
            comicStyle = style,
            comicPages = settings.pages,
            comicPageIndex = settings.pageIndex,
            comicBerserk = settings.berserk,
            comicPlot = settings.plot,
        )
    }

    /**
     * 用户在「角色」页选的那份漫画模式**意图**。
     *
     * ## 为什么不直接用 [GenerateExtras.comicMode]
     *
     * 用户要求：**停在「提示词」页点生成 = 按提示词走角色模式**。所以那一页期间
     * [GenerateExtras.comicMode] 会被临时压成 `false`。但那是"这次请求用哪种"的
     * 运行时镜像，**用户在「角色」页选的那份意图不能被这次压制弄丢** ——
     * 切回「角色」页要原样恢复。两者分开存，这件事才有地方放。
     *
     * 与 [AppSettings.mode] 的分工：那个是**盘上的持久化副本**，只在启动、备份恢复
     * 这类"重新装载"的时刻读；[setComicMode] 只写盘、不回写内存里的 `settings`，
     * 所以跑起来之后不能再信它。
     */
    private var comicModeIntent: Boolean = false

    /**
     * 切换角色模式 / 漫画模式。
     *
     * 只翻一个布尔值 —— **不搬运、不转换、不复制**数据。
     * 首次进漫画模式时按当前版式补一份**空分格**（分格是独立数据，不派生自角色列表）。
     *
     * 顺带 [GenerateExtras.ensureComicPages]：老数据没有页表，进模式时补一页，
     * 这样页条、狂暴模式等所有多页逻辑都不用再判空。
     */
    fun setComicMode(on: Boolean) {
        comicModeIntent = on
        applyComicMode(on, persist = true)
    }

    /**
     * 「提示词」页是不是正开着（生成页抽屉拉起来、且停在提示词栏时由界面呼叫）。
     *
     * 用户要求：**在「提示词」页点生成图片，就按提示词来、走角色模式** —— 之前这里
     * 有个 bug：漫画模式是存在设置里的全局开关，`NaiApi` 组装 payload 时只读它、
     * 根本不管你在哪一栏，于是在「角色」页开过漫画之后，切到「提示词」页点生成
     * 发出去的还是漫画页。
     *
     * ⚠️ **不落盘**：压的是运行时镜像，用户选的意图留在 [comicModeIntent] 里，
     * 切回「角色」页原样恢复。也正因为不落盘，进程被杀掉也不会丢掉用户的漫画模式。
     */
    fun setPromptTabActive(active: Boolean) {
        // 在「提示词」页 → 强制角色模式；在别的栏 → 回到用户自己选的那份
        applyComicMode(if (active && canvasMode != 2) false else comicModeIntent, persist = false)
    }

    /**
     * 把模式落到 [GenerateExtras] 上。
     *
     * @param persist 用户自己按开关 = true（写盘）；界面切栏造成的临时压制 = false。
     *   两者**必须分开**：否则切一下栏就把盘上的漫画模式改掉了，重启后用户的设定没了。
     */
    private fun applyComicMode(on: Boolean, persist: Boolean) {
        if (extras.comicMode == on) return
        var next = extras.copy(comicMode = on)
        if (on && next.comicPanels.isEmpty()) {
            val count = ComicLayout.panelCountOf(next.comicLayout)
                ?: next.charCaptions.size.takeIf { it > 0 } ?: 2
            next = next.copy(comicPanels = blankPanels(1, count))
        }
        next = next.ensureComicPages()
        extras = next
        if (persist) persistComicState(next)
    }

    // -------------------------------------------------------------- 漫画页

    /**
     * 换到第 [index] 页。会先把**当前页的改动写回页表**再装载目标页，
     * 所以不会出现"改了页 1 又翻到页 2，页 1 的改动没了"。
     */
    fun switchComicPage(index: Int) {
        if (!extras.comicMode) return
        val next = extras.withComicPageAt(index)
        if (next == extras) return
        extras = next
        persistComicState(next)
    }

    /** 加一页（默认版式 2 格空面板），并切过去。 */
    fun addComicPage() {
        if (!extras.comicMode) return
        val synced = extras.ensureComicPages()
        val layout = ComicLayout.DEFAULT
        val page = ComicPage(
            layout = layout,
            order = synced.comicOrder,
            panels = blankPanels(1, ComicLayout.panelCountOf(layout) ?: 2),
        )
        val pages = synced.comicPages + page
        // 活副本与页表**按构造就是一致的**，不用再走 withComicPage
        val next = synced.copy(
            comicPages = pages,
            comicPageIndex = pages.lastIndex,
            comicPanels = page.panels,
            comicLayout = page.layout,
            comicOrder = page.order,
        )
        extras = next
        persistComicState(next)
    }

    /** 删掉第 [index] 页。**永远留至少一页**（没有"零页漫画"这种东西）。 */
    fun removeComicPage(index: Int) {
        if (!extras.comicMode) return
        val synced = extras.ensureComicPages()
        if (synced.comicPages.size <= 1) {
            toast(rt("comic.pageLast"), isError = true)
            return
        }
        val target = index.coerceIn(0, synced.comicPages.lastIndex)
        val pages = synced.comicPages.toMutableList().also { it.removeAt(target) }
        val next = synced.copy(comicPages = pages).withComicPageAt(target.coerceAtMost(pages.lastIndex))
        extras = next
        comicCanvasImages.clear()
        persistComicState(next)
    }

    /** 整份页表一起换掉（狂暴模式规划完、备份导入）。 */
    private fun replaceComicPages(pages: List<ComicPage>, index: Int = 0) {
        if (pages.isEmpty()) return
        val target = index.coerceIn(0, pages.lastIndex)
        val page = pages[target]
        extras = extras.copy(
            comicPages = pages,
            comicPageIndex = target,
            comicPanels = page.panels,
            comicLayout = page.layout,
            comicOrder = page.order,
        )
        comicCanvasImages.clear()
        persistComicState(extras)
    }

    private fun blankPanels(from: Int, to: Int): List<CharCaptionItem> =
        (from..to).map { CharCaptionItem(name = "根$it") }

    /**
     * 选版式：改版式 + **把分格数量自动对齐到这个版式**。
     *
     * 缩减时**只删末尾的空格，绝不静默丢弃有内容的格** —— 这是"已有的人物保留"的
     * 同一条底线：宁可多留一个格，也不替你删掉写过的提示词。
     */
    fun applyComicLayout(layoutId: String) {
        if (!extras.comicMode) return
        val target = ComicLayout.panelCountOf(layoutId)
        var next = extras.copy(comicLayout = layoutId)

        if (target != null) {
            val panels = next.comicPanels
            val resized = when {
                panels.size == target -> panels
                panels.size < target -> panels + blankPanels(panels.size + 1, target)
                else -> panels.dropLast(ComicLayout.removableTail(panels.map { it.prompt }, target))
            }
            // 选版式 → **所有分格的手动坐标一并清掉**，重新按版式排。
            // 用户要求："版式预览里的锚点在选择版式时会重置按照版式的锚点来"。
            // 否则手拖过的格会一直留着旧坐标，换了版式那些格纹丝不动，看着像版式没生效。
            // 走 withComicPage：这一页的版式与分格要一起写回页表，切页后才不会丢。
            next = next.withComicPage(
                panels = resized.map { it.copy(useCoords = false) },
                layout = layoutId,
            )
        } else {
            next = next.withComicPage(layout = layoutId)
        }

        extras = next
        comicCanvasImages.remove(next.comicPageIndex)
        persistComicState(next)

        val name = ComicLayout.nameOf(layoutId)
        val kept = if (target == null) 0 else next.comicPanels.size - target
        when {
            target == null -> toast(rf("comic.layoutApplied", mapOf("name" to name)))
            kept > 0 -> toast(rf("comic.layoutKept", mapOf("name" to name, "count" to "$kept")))
            else -> toast(rf("comic.layoutApplied", mapOf("name" to name)))
        }
    }

    fun setComicOrder(order: String) {
        if (!extras.comicMode) return
        // 阅读顺序是**每一页各自的**（存在 ComicPage 上），所以走 withComicPage
        extras = extras.withComicPage(order = ComicLayout.normalizeOrder(order))
        markCharacterChanged()
    }

    fun setComicStylePrompt(on: Boolean) {
        if (!extras.comicMode) return
        extras = extras.copy(comicStylePrompt = on)
        markCharacterChanged()
    }

    fun addCharacter() {
        val list = extras.activeItems
        if (list.size >= params.maxCharacterPrompts) return
        // 默认名字与节点一致：角色 N / 格 N（之后可以点击改名）
        val name = if (extras.comicMode) "根${list.size + 1}" else "角色 ${list.size + 1}"
        extras = extras.withActiveItems(list + CharCaptionItem(name = name))
        markCharacterChanged()
    }

    /**
     * 从角色图鉴「建一个新角色分区」：名字用角色名、提示词用那串可直接用的 trigger。
     * （生成页「角色」栏里那枚角色图鉴按钮点角色名时用它。漫画模式下同样落到分格列表。）
     */
    fun addCharacterFromAnima(character: AnimaCharacter) {
        val list = extras.activeItems
        if (list.size >= params.maxCharacterPrompts) {
            toast(rt("char.limitReached"))
            return
        }
        extras = extras.withActiveItems(
            list + CharCaptionItem(name = character.name, prompt = character.trigger),
        )
        markCharacterChanged()
        toast(rf("char.createdFromAnima", mapOf("name" to character.name)))
    }

    /** 相邻交换。`direction` -1 上移、+1 下移。用于「编辑」里的顺位调整。 */
    fun moveCharacter(index: Int, direction: Int) {
        val list = extras.activeItems.toMutableList()
        val target = index + direction
        if (index !in list.indices || target !in list.indices) return
        val moved = list[index]
        list[index] = list[target]
        list[target] = moved
        extras = extras.withActiveItems(list)
        markCharacterChanged()
    }

    fun removeCharacter(index: Int) {
        val list = extras.activeItems.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        extras = extras.withActiveItems(list)
        markCharacterChanged()
    }

    fun updateCharacter(index: Int, update: (CharCaptionItem) -> CharCaptionItem) {
        val list = extras.activeItems.toMutableList()
        if (index !in list.indices) return
        list[index] = update(list[index])
        extras = extras.withActiveItems(list)
        markCharacterChanged()
    }

    /** 恢复示例/清空分格：只在漫画模式下用。 */
    fun clearComicPanels() {
        if (!extras.comicMode) return
        // 走 withComicPage：清的是**当前这一页**，页表里那一页也要跟着清
        extras = extras.withComicPage(panels = emptyList())
        markCharacterChanged()
    }

    // -----------------------------------------------------------------------
    // 按剧情分镜：一段剧情 → 风格 + 每一格的提示词 + 版式（可选直接生图）
    // -----------------------------------------------------------------------

    /** 分镜生成中（挡住重复点击）。 */
    var storyboardBusy by mutableStateOf(false)
        private set

    /**
     * 漫画剧情框的文本（「按剧情分镜」与狂暴模式共用）。
     *
     * **真源是 `extras.comicPlot`**（这个属性只是快捷读法）：持久化在
     * `comic_settings_v1` 的 `plot` 键里，与正面提示词同一个口径 ——
     * 只要用户不改，切页面 / 杀进程 / 重启都不丢。
     * 0.2.116 及之前是纯内存态（切页就丢，用户报了 bug），0.2.117 起落盘。
     *
     * 写入侧自带防抖（600ms 无新输入才落盘）：别在每敲一个字都写 SharedPreferences。
     * 不另设 `fun setComicPlot()` —— 与属性隐式 setter 的 JVM 签名冲突（上轮踩过）。
     */
    var comicPlot: String
        get() = extras.comicPlot
        set(value) {
            if (extras.comicPlot == value) return
            // 撤销栈：剧情框也记一笔（工具栏那排按钮按"当前聚焦的框"分派，
            // 用户 2026-09-18 要求它在剧情框里也能用）。
            if (!comicPlotUndoSuspended) recordPromptEdit(PromptField.PLOT, extras.comicPlot)
            extras = extras.copy(comicPlot = value)
            comicPlotDirty = true
            comicPlotSaveJob?.cancel()
            comicPlotSaveJob = viewModelScope.launch {
                delay(COMIC_PLOT_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    storage.setComicSettings(persistableComicSettings(extras))
                }
                comicPlotDirty = false
            }
        }

    /** 撤销/重做正在写回剧情时挂起"记一笔"，否则撤销永远撤不完（见 [applyPromptText]）。 */
    private var comicPlotUndoSuspended = false

    /** 剧情落盘防抖任务（输入停顿 [COMIC_PLOT_DEBOUNCE_MS] 后写一次）。 */
    private var comicPlotSaveJob: Job? = null

    /**
     * 剧情**放大编辑页**是否打开（用户 2026-09-16 要求）。
     *
     * 剧情框那一格只有三行高，超长剧情没法检查；点框右上角那个"四角"图标就把抽屉
     * 整块换成一个大文本框，铺满来看。
     *
     * 状态放在这里（而不是某个 Composable 内部）是因为**要换掉的是整个抽屉的内容**
     * （`ControlPanel` 那一层），而那个按钮长在抽屉内容的最深处，隔着好几层组合。
     */
    var plotEditorOpen by mutableStateOf(false)
        private set

    fun openPlotEditor() {
        plotEditorOpen = true
    }

    fun closePlotEditor() {
        plotEditorOpen = false
    }

    /**
     * 「收起底部抽屉」的一次性请求（**外壳的返回键**发的）。
     *
     * 抽屉的开合状态（`panelTarget` / `panelFraction`）住在 `GenerateScreen` 里，外壳够不着；
     * 而返回键只能在外壳处理（它还要管另外四页、侧边栏、遮罩……）。所以照
     * `pendingGenerateAction` 的老办法：外壳发一个一次性请求，生成页收到就 `panelTarget = 0f`。
     *
     * 不这么做的话，抽屉开着按返回会直接落到"两次退出"那条分支上 —— 用户报的就是这个。
     */
    var panelCollapseRequested by mutableStateOf(false)
        private set

    fun requestPanelCollapse() {
        panelCollapseRequested = true
    }

    fun consumePanelCollapseRequest() {
        panelCollapseRequested = false
    }

    // ---------------------------------------------------------------- 主题切换动画

    /**
     * 正在播的「圆形扩散」主题切换；`null` = 没有在播。
     *
     * 为什么这状态放在 `AppState`（而不是某个 Composable 里）：**扩散遮罩必须盖住全屏**，
     * 包括侧边栏 —— 所以它渲染在 `MainActivity` 最外层，而按开关的地方在侧边栏里面，
     * 中间隔着好几层。放这儿是两边都能拿到的最短路径。
     */
    var themeWipe by mutableStateOf<ThemeWipe?>(null)
        private set

    /** 直接切主题，不播动画。抓屏失败时的兜底走这条。 */
    fun setTheme(value: String) {
        if (settings.theme == value) return
        settings = settings.copy(theme = value)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setSettings(settings) } }
    }

    /**
     * 开始一次主题扩散动画。[snapshot] 是**切换前**那一帧的整窗截图，
     * [originX] / [originY] 是按钮中心的窗口坐标（px），也就是圆心。
     *
     * ## 顺序很讲究
     *
     * **先切主题、再设遮罩**，而且这两步在**同一次状态写入**里（同一帧，只重组一次）——
     * 于是"新主题"和"铺满的旧截图"同时上屏：用户看到的还是旧画面（遮罩就是旧画面），
     * 而底下已经悄悄换成新主题了。接下来只要把遮罩挖开，就是新主题在扩散。
     *
     * 反过来的话（先铺遮罩、下一帧再切主题）中间会露出一帧新主题，看着就是闪一下。
     */
    fun startThemeWipe(
        snapshot: ImageBitmap,
        originX: Float,
        originY: Float,
        dark: Boolean,
        /** 截图在窗口客户区里的物理像素偏移（窗口有一部分在屏幕外时非零）。 */
        snapshotOffsetX: Int = 0,
        snapshotOffsetY: Int = 0,
        /** 窗口客户区的物理像素尺寸（把截图换算到逻辑坐标用）。0 = 按整屏铺满。 */
        windowWidthPx: Int = 0,
        windowHeightPx: Int = 0,
    ) {
        // 正在播就忽略，免得连点两下叠出两个圆
        if (themeWipe != null) return
        setTheme(if (dark) "dark" else "light")
        themeWipe = ThemeWipe(
            snapshot = snapshot,
            originX = originX,
            originY = originY,
            snapshotOffsetX = snapshotOffsetX,
            snapshotOffsetY = snapshotOffsetY,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
        )
    }

    /** 扩散播完 → 撤掉遮罩。主题在 [startThemeWipe] 里就已经切好了，这里只收尾。 */
    fun finishThemeWipe() {
        themeWipe = null
    }

    /**
     * 剧情**改了还没落盘**。
     *
     * 不能用"剧情非空"来代替这件事：清空剧情也是一次改动，而清空之后
     * `extras.comicPlot` 就是空串了 —— 早先 [flushComicPlot] 的判据正是
     * `isNotEmpty()`，于是"清空剧情 → 导出备份"会把那次清空吞掉，
     * 盘上留着旧剧情，重启又回来了。
     */
    private var comicPlotDirty = false

    /** 剧情防抖还没落盘时立即写掉（分镜 / 狂暴 / 备份导出前调用）。 */
    private fun flushComicPlot() {
        comicPlotSaveJob?.cancel()
        comicPlotSaveJob = null
        if (comicPlotDirty) {
            comicPlotDirty = false
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    storage.setComicSettings(persistableComicSettings(extras))
                }
            }
        }
    }

    /** 手改风格词（默认值见 [COMIC_DEFAULT_STYLE]）。**只有用户能改它**。 */
    fun setComicStyle(text: String) {
        if (!extras.comicMode) return
        extras = extras.copy(comicStyle = text)
        markCharacterChanged()
    }

    /**
     * 狂暴模式开关。
     *
     * 打开：剧情框里放整部剧情，主按钮变成"自动分页 + 逐页分镜"，多页可一次跑完。
     * 关闭：只处理当前这一页。
     */
    fun setComicBerserk(on: Boolean) {
        if (!extras.comicMode) return
        extras = extras.copy(comicBerserk = on)
        persistComicState(extras)
    }

    /**
     * **按剧情分镜**：把用户写的一段剧情交给 LLM，拿回"整页风格 + 每一格的提示词"，
     * 直接填进分格列表并按格数自动挑版式。
     *
     * ⚠️ **这一步不花钱**（只改本地数据 + 一次 LLM 调用）。生图才是花钱的那一步，
     * 只有在设置里打开了「分镜后自动生图」时才会跟着触发。
     */
    fun generateComicStoryboard(plot: String) {
        if (!extras.comicMode) return
        flushComicPlot()
        val text = plot.trim()
        if (text.isEmpty()) {
            toast(rt("comic.storyboardEmptyPlot"), isError = true)
            return
        }
        val cfg = settings.llmConfigForStoryboard()
        if (!cfg.configured) {
            toast(rt("llm.notConfigured"), isError = true)
            return
        }
        if (storyboardBusy) return

        viewModelScope.launch {
            // 新一轮分镜：**清空这一类用途的记忆**（上一部剧情不该污染这一部）；
            // 同一轮里的逐页调用共用记忆 —— 那正是用户要的"记得前面几页"。
            clearLlmMemory("storyboard")
            storyboardBusy = true
            status = rt("comic.storyboardRunning")
            try {
                val raw = llmChatRecorded(
                    kindKey = "conversation.kind.storyboard",
                    cfg = cfg,
                    systemPrompt = settings.comicStoryboardRules,
                    userPrompt = text,
                )
                val board = ComicStoryboard.parse(raw)
                applyStoryboard(board)
                status = rt(RuntimeText.READY)
                toast(rf("comic.storyboardDone", mapOf("count" to board.panels.size)))

                // 一条龙：设置里打开了就直接生图（这是**唯一**会自动扣 Anlas 的地方，
                // 所以默认是关的，并且文案里写清了）
                if (settings.comicAutoGenerate) {
                    generate()
                }
            } catch (e: ComicStoryboard.StoryboardException) {
                status = rt(RuntimeText.READY)
                toast(rf("comic.storyboardFailed", mapOf("error" to (e.message ?: ""))), isError = true)
            } catch (e: Exception) {
                status = rt(RuntimeText.READY)
                toast(rf("comic.storyboardFailed", mapOf("error" to (e.message ?: ""))), isError = true)
            } finally {
                storyboardBusy = false
            }
        }
    }

    /** 把分镜结果落到状态里：分格列表 + 版式 + 风格词。 */
    private fun applyStoryboard(board: ComicStoryboard.Storyboard) {
        val panels = board.panels.map { panel ->
            CharCaptionItem(
                name = panel.name.ifBlank { "根" },
                prompt = panel.prompt,
                panelRole = panel.role,
            )
        }
        val layout = ComicStoryboard.defaultLayoutFor(panels.size)
        extras = extras.withComicPage(panels = panels, layout = layout)
        comicCanvasImages.remove(extras.comicPageIndex)
        // ⚠️ 这里**只动分格和版式**，不碰「自动加风格词」那个开关。
        // 早先这里有一句 `.copy(comicStylePrompt = true)`，本意是"分镜出来了就该用上"，
        // 结果是：用户关掉开关 → 点一次「按剧情分镜」→ 开关自己又亮了，
        // 看起来就是"这个开关记不住"（用户反馈）。开关归用户管，AI 侧不许改。
        // 顺带说明为什么不读 `board.style`：整页风格词只归用户管，不给 AI 修改权限。
        // 分格与设置是两份存储，这里要一起落盘
        persistComicState(extras)
    }

    // -----------------------------------------------------------------------
    // 狂暴漫画模式：整部剧情 → AI 自动分页 → 逐页分镜 → 一次全部跑完
    // -----------------------------------------------------------------------

    /** 规划 + 逐页分镜进行中（这一段**不花 Anlas**，纯 LLM）。 */
    var berserkBusy by mutableStateOf(false)
        private set

    /** 当前阶段：`plan` 正在分页 / `storyboard` 正在逐页写分镜 / 空串 = 没在跑。 */
    var berserkStage by mutableStateOf("")
        private set

    /** 已完成的页数（分镜阶段用）。 */
    var berserkDone by mutableStateOf(0)
        private set

    /** 规划出来的总页数（分镜阶段用）。0 = 还没规划出来。 */
    var berserkTotal by mutableStateOf(0)
        private set

    /** 取消请求（下一个循环边界生效）。普通布尔即可 —— 不需要触发重组。 */
    private var berserkCancelled = false

    fun cancelBerserk() {
        if (!berserkBusy) return
        berserkCancelled = true
        status = rt("comic.berserkCancelling")
    }

    /**
     * **狂暴模式第一二段**：整部剧情 → 分页规划 → 逐页分镜。
     *
     * 由编辑区右列那个「狂暴模式」勾选框决定要不要走这条路：不勾时主按钮走的是
     * [generateComicStoryboard]（一段剧情 = 一页），勾上才走这里（整部剧情 = 多页）。
     *
     * ⚠️ **全程不花 Anlas**（只有 LLM 调用）。真正花额度的是 [startBerserkGeneration]，
     * 它是页条下面一个独立按钮、点了先弹确认；只有设置里的「分镜后自动生图」才会
     * 把这一步接在后面自动跑。这里绝不默认偷偷触发。
     *
     * 两段式的理由见 [PromptRules.COMIC_PAGE_PLAN_RULES]：先让模型把整部作品的
     * 页数与节奏定下来，再让它一页一页地专心写分格 —— 一次吐几十格质量会掉、还容易截断。
     */
    fun generateBerserkComic(plot: String, maxPages: Int = settings.comicMaxPages) {
        if (!extras.comicMode) return
        flushComicPlot()
        val text = plot.trim()
        if (text.isEmpty()) {
            toast(rt("comic.storyboardEmptyPlot"), isError = true)
            return
        }
        // 两段用的是**两套**配置（第一段"分页规划"、第二段"分镜"），都得先配好——
        // 否则第一段跑完、第二段才报没配置，用户白等一场。各自留空时都回落到正面提示词那套。
        val planCfg = settings.llmConfigForPlan()
        val boardCfg = settings.llmConfigForStoryboard()
        if (!planCfg.configured || !boardCfg.configured) {
            toast(rt("llm.notConfigured"), isError = true)
            return
        }
        if (berserkBusy || storyboardBusy) return

        val cap = maxPages.coerceIn(ComicStoryboard.MIN_PAGES, ComicStoryboard.MAX_PAGES)

        viewModelScope.launch {
            // 狂暴模式：分页规划与逐页分镜各自一类记忆，开跑时都清空（理由同上）
            clearLlmMemory("pageplan", "storyboard")
            berserkBusy = true
            berserkCancelled = false
            berserkStage = "plan"
            berserkDone = 0
            berserkTotal = 0
            status = rt("comic.berserkPlanning")
            var produced = 0
            try {
                // ---- 第一段：整部剧情 → 分页规划 ----
                val planRaw = llmChatRecorded(
                    kindKey = "conversation.kind.pageplan",
                    cfg = planCfg,
                    systemPrompt = settings.comicPagePlanRules,
                    userPrompt = text,
                )
                val plan = ComicStoryboard.parsePagePlan(planRaw, cap)
                berserkTotal = plan.pages.size

                // ---- 第二段：一页一次，逐页分镜 ----
                berserkStage = "storyboard"
                val pages = ArrayList<ComicPage>(plan.pages.size)
                plan.pages.forEachIndexed { index, planned ->
                    if (berserkCancelled) return@forEachIndexed
                    berserkDone = index
                    status = rf(
                        "comic.berserkStoryboarding",
                        mapOf("current" to index + 1, "total" to plan.pages.size),
                    )
                    val boardRaw = llmChatRecorded(
                        kindKey = "conversation.kind.storyboard",
                        cfg = boardCfg,
                        systemPrompt = settings.comicStoryboardRules,
                        userPrompt = berserkPageRequest(text, planned, index, plan.pages.size),
                    )
                    val board = ComicStoryboard.parse(boardRaw)
                    pages += ComicPage(
                        layout = planned.layout,
                        order = extras.comicOrder,
                        summary = planned.summary,
                        panels = board.panels.map { panel ->
                            CharCaptionItem(
                                name = panel.name.ifBlank { "根" },
                                prompt = panel.prompt,
                                panelRole = panel.role,
                            )
                        },
                    )
                    produced = pages.size

                    // ⭐ 用户 2026-09-18：「狂暴模式出一张分镜就提交一次生图请求」——
                    // 这一页分镜一出来就**立刻**把它排进生成队列（不用等后面几页跑完）。
                    // ⚠️ 这一步开始花额度/Anlas，所以仍然受设置里「分镜后自动生图」那个开关约束；
                    // 关掉它就还是"只出分镜、不生图"。
                    if (settings.comicAutoGenerate && !berserkCancelled) {
                        replaceComicPages(pages, index) // 让页表/界面跟上（也落盘，中途退出不丢）
                        enqueueComicPageGeneration(pages, index)
                    }
                }

                if (pages.isEmpty()) {
                    throw ComicStoryboard.StoryboardException(
                        if (berserkCancelled) "已取消" else "一页都没有生成出来",
                    )
                }

                // ⚠️ 不读 `plan.style`：整页风格词只归用户管（用户要求不给 AI 修改权限）。
                // 同样**不碰**「自动加风格词」那个开关 —— 它归用户管，跑一次分镜
                // 就把它改回 true 会让用户觉得"开关记不住"（与 applyStoryboard 同一个 bug）。
                extras = extras.withComicPage()
                replaceComicPages(pages, 0)

                berserkDone = pages.size
                status = rt(RuntimeText.READY)
                toast(rf("comic.berserkPlanned", mapOf("count" to pages.size)))

                // ⚠️ 这里**不再**批量排图：用户 2026-09-18 要求「出一张分镜就提交一次生图请求」，
                // 每一页在循环里（分镜一解析出来）就已经排进队列了，见循环里的
                // `enqueueComicPageGeneration`。
            } catch (e: ComicStoryboard.StoryboardException) {
                status = rt(RuntimeText.READY)
                toast(rf("comic.berserkFailed", mapOf("error" to (e.message ?: ""))), isError = true)
            } catch (e: Exception) {
                status = rt(RuntimeText.READY)
                toast(rf("comic.berserkFailed", mapOf("error" to (e.message ?: ""))), isError = true)
            } finally {
                if (berserkCancelled && produced > 0) {
                    toast(rf("comic.berserkPartial", mapOf("count" to produced)))
                }
                berserkBusy = false
                berserkStage = ""
                berserkTotal = 0
                berserkDone = 0
                berserkCancelled = false
            }
        }
    }

    /**
     * 拼第二段的用户提示词：**全篇剧情给上下文 + 这一页的概要 + 版式与格数**。
     *
     * 全篇也要给：同一页里角色的状态、前后呼应都依赖上文，只给一段概要
     * 模型会写出和前后页对不上的画面。
     */
    private fun berserkPageRequest(
        plot: String,
        page: ComicStoryboard.PlannedPage,
        index: Int,
        total: Int,
    ): String = buildString {
        appendLine("【全篇剧情】")
        appendLine(plot)
        appendLine()
        appendLine("【本次只画第 ${index + 1} 页 / 共 $total 页】")
        appendLine("这一页要讲的内容：${page.summary.ifBlank { "（模型未给概要，按全篇剧情推进）" }}")
        appendLine("版式：${ComicLayout.nameOf(page.layout)}，共 ${page.panelCount} 格")
        appendLine()
        append(
            "请只输出**这一页**的分格 JSON，panels 数组必须正好有 ${page.panelCount} 个元素，" +
                "不要输出其它页的内容。",
        )
    }

    /**
     * 把已经规划好的每一页**排进生成队列**。
     *
     * ⚠️ **这一步会消耗生成额度 / Anlas**，所以它单独是一个函数、单独一个按钮，
     * 绝不在分镜阶段自动调用（除非用户在设置里显式打开了「分镜后自动生图」）。
     *
     * 每一页排一个 [QueuedJob]，各自带**自己那一页的** `comicPanels` / `comicLayout`，
     * 于是 `NaiApi` 组装 payload 时读到的锚点与提示词都是对的 —— 队列机制本来就是
     * "每一张各自带参数快照"，这里只是把"张"换成了"页"。
     */
    fun startBerserkGeneration() {
        if (!extras.comicMode) return
        val synced = extras.withComicPage()
        val pages = synced.comicPages
        if (pages.isEmpty()) {
            toast(rt("comic.berserkNotPlanned"), isError = true)
            return
        }
        extras = synced

        val presets = activeStylePresetPrompts()
        val jobs = pages.mapIndexedNotNull { index, page ->
            // 空页 / 全禁用的页不排 —— 否则会白烧一张图的额度
            if (page.panels.none { it.enabled && it.prompt.isNotBlank() }) return@mapIndexedNotNull null
            QueuedJob(
                params = params,
                extras = synced.forComicPage(pages, index).copy(stylePresetPrompts = presets, stylePresetNegatives = activeStylePresetNegatives()),
                total = 1,
                intervalSeconds = 0,
            )
        }
        if (jobs.isEmpty()) {
            toast(rt("comic.berserkEmpty"), isError = true)
            return
        }

        queuedJobs = queuedJobs + jobs
        status = rf("comic.berserkQueued", mapOf("count" to jobs.size))

        if (!busy) {
            val first = queuedJobs.first()
            queuedJobs = queuedJobs.drop(1)
            startGeneration(first)
        }
    }

    /**
     * 把**某一页**排进生成队列 —— 狂暴模式「出一张分镜就提交一次生图请求」用它（用户 2026-09-18）。
     *
     * 与 [startBerserkGeneration] 里那一张的口径完全一致：同一份 `stylePresetPrompts`（风格库），
     * 同样跳过"空页 / 全禁用"的页（免得白烧额度）；区别只在于**只排这一页**。
     *
     * 不忙就直接开跑：于是"分镜还在往下出、上一页的图已经在生成了"，两件事并行。
     */
    private fun enqueueComicPageGeneration(pages: List<ComicPage>, index: Int) {
        val page = pages.getOrNull(index) ?: return
        if (page.panels.none { it.enabled && it.prompt.isNotBlank() }) return
        val job = QueuedJob(
            params = params,
            extras = extras.forComicPage(pages, index).copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives()),
            total = 1,
            intervalSeconds = 0,
        )
        queuedJobs = queuedJobs + job
        status = rf("comic.berserkQueued", mapOf("count" to 1))
        if (!busy) {
            val first = queuedJobs.first()
            queuedJobs = queuedJobs.drop(1)
            startGeneration(first)
        }
    }

    fun setSettings(update: (AppSettings) -> AppSettings) {
        settings = update(settings)
        val snapshot = settings
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setSettings(snapshot) } }
    }

    // 注意：不能叫 setBatchCount —— 会与 batchCount 属性的 setter 撞 JVM 签名
    fun updateBatchCount(value: Int) { batchCount = value.coerceIn(1, 999) }

    fun updateBatchIntervalSeconds(value: Int) {
        batchIntervalSeconds = value.coerceIn(0, 3600)
        setSettings { it.copy(batchIntervalSeconds = batchIntervalSeconds) }
    }

    // ------------------------------------------------------------- Token

    /** 先校验、成功才保存。返回 null 表示成功，否则返回错误文案。 */
    /**
     * 校验并保存 token。**存哪一把、拿哪个地址去验，取决于当前是不是网关模式** ——
     * 官方的 `nai_token` 和网关的 `gateway_token` **各存各的**（用户 2026-09-26 要求），
     * 来回切的时候两边都留着，不用重填。
     */
    suspend fun setToken(token: String): String? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return "Token 不能为空"
        // 网关模式下地址没填就别验了：`verifyToken` 会回落到官方地址，拿虚拟 Key 必然 401，
        // 那个报错会把人往"Key 不对"的方向带（其实是地址没填）。先说清楚。
        if (!gatewayAddressReady()) return rt("gateway.needBaseUrl")
        return try {
            val summary = api.verifyToken(trimmed, effectiveImageBase())
            if (usingGateway) storage.setGatewayToken(trimmed) else storage.setToken(trimmed)
            hasToken = true
            account = summary
            if (usingGateway) refreshGatewayQuota()
            null
        } catch (e: NaiHttpException) {
            if (e.statusCode == 401) rt("error.tokenInvalid") else cleanError(e)
        } catch (e: Exception) {
            cleanError(e)
        }
    }

    fun clearToken() {
        // 只清**当前这一档**的 Key：网关模式下别把官方那把也抹了（反过来也一样）——
        // "分开存储"的意义就在这儿。
        if (usingGateway) storage.clearGatewayToken() else storage.clearToken()
        hasToken = false
        account = AccountSummary(hasToken = false)
        gatewayQuota = null
        status = rt("settings.tokenCleared")
    }

    fun refreshAnlas() {
        viewModelScope.launch {
            val token = effectiveToken()
            if (token.isEmpty()) {
                status = rt("error.tokenRequired")
                return@launch
            }
            try {
                account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase()))
                status = if (account.stale) rt("status.accountSyncStale") else rt("status.anlasRefreshed")
            } catch (e: Exception) {
                status = rf("status.anlasRefreshFailed", mapOf("error" to cleanError(e)))
            }
        }
    }

    // -------------------------------------------------------------- 报价

    /**
     * 拉一次官方预扣报价。
     *
     * 注意：**只在真正生成前调一次**。之前这里还给参数改动挂了 350ms 防抖，
     * 但报价已经不在界面上显示了，那样等于每改一次参数就发一次无用请求，所以去掉了。
     */
    private suspend fun refreshGenerationQuote() {
        val token = effectiveToken()
        if (token.isEmpty() || !account.hasToken) {
            generationQuote = null
            return
        }
        quoteLoading = true
        try {
            val price = api.requestOfficialGenerationPrice(token, params, effectiveImageBase())
            val balance = account.anlasBalance
            generationQuote = if (price != null) {
                AnlasQuote(
                    amount = price,
                    balance = balance,
                    insufficient = balance != null && price > balance,
                    source = "officialQuote",
                )
            } else {
                AnlasQuote(amount = null, balance = balance, insufficient = false, source = "pendingQuote")
            }
        } catch (e: Exception) {
            generationQuote = AnlasQuote(amount = null, balance = account.anlasBalance, source = "pendingQuote")
        } finally {
            quoteLoading = false
        }
    }

    // -------------------------------------------------------------- 生成

    fun cancelGeneration() {
        cancelRequested = true
        api.cancelActiveGeneration()
        // 取消当前这张的同时把排队的一起清掉（否则取消完还会接着发下一张）
        queuedJobs = emptyList()
        status = rt("generate.cancelling")
    }

    /**
     * 接收一帧流式预览。规则与参考实现一致：
     * 非生成中、或用户已请求取消时，丢弃这一帧。
     *
     * 节流已经在解码器里做过（约 110ms 一帧，final 帧无条件回调），这里不再重复节流。
     */
    private fun handleGenerationPreview(preview: NaiPreview) {
        if (!busy || cancelRequested) return
        previewChannel?.trySend(preview)
    }

    /** 与参考实现一致：置空预览要同时清掉进度三项，否则进度条会停在旧值上。 */
    private fun clearGenerationPreview() {
        generationPreview = null
        generationPreviewProgress = 0f
        generationPreviewStep = 0
        generationPreviewTotalSteps = 0
    }

    /**
     * 开始一次生成。`job` 为空 = 用当前界面上的参数；非空 = 用队列里那份**当时快照的参数**
     * （排队时点几次就是几张，各自带自己那一刻的参数/预设/张数）。
     */
    fun generate() = startGeneration(null)

    private fun startGeneration(job: QueuedJob?) {
        if (busy) return
        val token = effectiveToken()
        if (token.isEmpty()) {
            status = rt("error.tokenRequired")
            return
        }
        val initialParams = job?.params ?: params
        // 预设提示词在发起请求这一瞬间快照，之后用户再改勾选不会影响本次生成。
        val initialExtras = job?.extras ?: extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives())
        val comicRunPage = initialExtras.comicPageIndex.takeIf { initialExtras.comicMode }

        // ⚠️ 正面提示词只有**角色模式**下才是必须的（用户要求去掉漫画模式的这条限制）。
        // 漫画模式的画面内容在每一格的提示词里，整页风格词 + 负面词就够出图了；
        // 之前这里一刀切地拦，于是在漫画模式下把正面提示词留空就点不动生成。
        if (!initialExtras.comicMode && initialParams.positivePrompt.trim().isEmpty()) {
            status = rt("error.positiveRequired")
            return
        }

        val initialTotal = job?.total ?: batchCount.coerceIn(1, 999)
        val initialInterval = job?.intervalSeconds
            ?: (if (initialTotal > 1) batchIntervalSeconds.coerceIn(0, 3600) else 0)
        val initialSeed = resolveSeed(initialParams)

        cancelRequested = false

        // 单槽位预览管道：CONFLATED 只保留最新一帧，消费慢时自动丢中间帧，
        // 既不排队也不积压。解码在 Default 线程，状态写入回到主线程。
        val pipeline = openPreviewPipeline()

        generateJob = viewModelScope.launch {
            busy = true
            if (comicRunPage != null) {
                comicCanvasImages.remove(comicRunPage)
                comicCanvasGeneratingPage = comicRunPage
                comicCanvasGenerating = true
            }
            lastAnlasSpent = null
            // 参考实现的第 1 个清空点：生成开始
            clearGenerationPreview()
            status = rt("status.readingCharge")

            var completed = 0
            var failed = 0
            var lastError = ""
            var anlasBefore: Int? = null

            try {
                anlasBefore = snapshotAnlasBefore(token)

                // 预扣报价；拿不到价格不阻断生成，只是不显示
                refreshGenerationQuote()
                val quote = generationQuote
                if (quote?.insufficient == true) {
                    status = rf(
                        "status.insufficientThisRun",
                        mapOf("amount" to quote.amount, "balance" to quote.balance),
                    )
                }

                // 这一轮批量生成**共用一个批次号**：临时图库据此把同一批合成一项、角标显示张数
                //（这一轮是逐张发请求、逐张落盘的，光看落盘时间分辨不出"是不是同一批"）。
                val batchId = newSessionBatchId()
                for (index in 0 until initialTotal) {
                    if (cancelRequested) break

                    if (index > 0 && initialInterval > 0) {
                        status = rf(
                            "status.batchInterval",
                            mapOf("seconds" to initialInterval, "current" to index + 1, "total" to initialTotal),
                        )
                        if (!waitForInterval(initialInterval)) break
                    }

                    val seed = seededFor(initialParams, initialSeed, index)
                    status = rf(
                        "status.generatingImage",
                        mapOf(
                            "current" to completed + failed + 1,
                            "total" to initialTotal,
                            "queued" to queuedJobs.size,
                        ),
                    )

                    // 参考实现的第 2 个清空点：每个任务发请求之前
                    clearGenerationPreview()

                    // 每张图单独计时：批量场景下后面几张不会因为排在前面而显得耗时虚高
                    val genStart = System.currentTimeMillis()

                    // 流式优先：能出中间稿就有"模糊→清晰"的预览；服务端不支持时
                    // generateStreaming 返回 null，自动回退到非流式
                    var images: List<ByteArray>? = null
                    if (settings.streamPreviewEnabled) {
                        images = api.generateStreaming(
                            token = token,
                            params = initialParams,
                            seed = seed,
                            extras = initialExtras,
                            modelMode = settings.modelMode,
                            imageBaseUrl = effectiveImageBase(),
                            onPreview = ::handleGenerationPreview,
                        )
                    }
                    val images2 = images ?: api.generate(
                        token = token,
                        params = initialParams,
                        seed = seed,
                        extras = initialExtras,
                        modelMode = settings.modelMode,
                        imageBaseUrl = effectiveImageBase(),
                    )
                    if (images2.isEmpty()) throw IllegalStateException(rt("error.apiNoImages"))

                    val saved = persistGeneratedImages(
                        images = images2,
                        params = initialParams,
                        seed = seed,
                        feature = "t2i",
                        durationMs = System.currentTimeMillis() - genStart,
                        // 同一轮批量 = 同一批（临时图库角标用）
                        batchId = batchId,
                    )
                    completed += saved.size
                    if (comicRunPage != null) {
                        saved.firstOrNull()?.filePath?.let { comicCanvasImages[comicRunPage] = it }
                    }
                    // 设置里开了「生图后自动官方 2× 放大」就逐张放大（付费，按像素 1–4 Anlas）
                    runCatching { autoUpscaleIfEnabled(saved, token) }

                    // 参考实现的第 3 个清空点：保存完成后立刻置空，换成最终图文件
                    clearGenerationPreview()

                    // 图片此刻已经落盘，余额刷新出岔子不能把成功的图翻成失败
                    runCatching {
                        account = mergeAccountKeepingLast(
                            api.fetchAccount(token, effectiveImageBase()),
                        )
                    }
                }
            } catch (e: Exception) {
                failed += 1
                lastError = cleanError(e)
            } finally {
                // 再刷一次余额算实扣；失败也无所谓，下次自然刷新会修正
                val after = runCatching {
                    api.fetchAccount(token, effectiveImageBase()).anlasBalance
                }.getOrNull()
                lastAnlasSpent = if (anlasBefore != null && after != null) {
                    maxOf(0, anlasBefore - after)
                } else {
                    null
                }
                // 每日统计：**这一笔实扣的点数** ✓（图数与 tag 字数在落库那一步已经记过了 ✓）
                recordUsage(images = 0, anlas = lastAnlasSpent ?: 0, tagChars = 0)

                val spentText = spentText(lastAnlasSpent)
                status = when {
                    cancelRequested -> rf("status.generationCancelled", mapOf("spent" to spentText))
                    failed > 0 -> rf(
                        "status.generationFailedSome",
                        mapOf(
                            "completed" to completed,
                            "failed" to failed,
                            "spent" to spentText,
                            "error" to lastError,
                        ),
                    )
                    else -> rf("status.generationDone", mapOf("completed" to completed, "spent" to spentText))
                }

                busy = false
                if (comicRunPage != null) comicCanvasGenerating = false
                cancelRequested = false
                // 参考实现的第 4 个清空点：整轮结束兜底
                clearGenerationPreview()
            }
        }

        // 生成协程结束后收掉预览管道；如果队列里还有，就接着发下一个
        generateJob?.invokeOnCompletion {
            closePreviewPipeline(pipeline)
            val next = queuedJobs.firstOrNull()
            if (next != null && !cancelRequested) {
                queuedJobs = queuedJobs.drop(1)
                // 队列里的每一张都用自己的参数快照（排队那一刻的）
                startGeneration(next)
            }
        }
    }

    // -------------------------------------------------------------- 官方 2× 放大

    /**
     * 手动放大：**生成页那个「2x放大」胶囊**与**图库长按弹窗**都走这里。
     * 传 null = 放大"当前工作图"（放大版会继续回落到放大前，不会越放越小）。
     */
    fun officialUpscale(path: String? = null) {
        if (busy) {
            toast(rt("status.busyPleaseWait"), isError = true)
            return
        }
        val sourcePath = path ?: workImagePath
        if (sourcePath == null) {
            toast(rt("upscale.needImage"), isError = true)
            return
        }
        val token = effectiveToken()
        if (token.isEmpty()) {
            toast(rt("error.tokenRequired"), isError = true)
            return
        }
        val dims = MaskCodec.imageSize(images, sourcePath)
        if (dims == null) {
            toast(rt("error.inpaintSourceMissing"), isError = true)
            return
        }
        val price = OfficialUpscale.price(dims.first, dims.second)
        if (price == null) {
            toast(
                rf(
                    "upscale.tooLarge",
                    mapOf(
                        "w" to dims.first,
                        "h" to dims.second,
                        "max" to OfficialUpscale.MAX_INPUT_PIXELS,
                    ),
                ),
                isError = true,
            )
            return
        }

        viewModelScope.launch {
            busy = true
            status = rf("upscale.running", mapOf("cost" to price))
            try {
                val saved = runOfficialUpscale(
                    sourcePath = sourcePath,
                    sourceWidth = dims.first,
                    sourceHeight = dims.second,
                    cost = price,
                    token = token,
                )
                if (saved != null) {
                    toast(rf("upscale.done", mapOf("cost" to price, "w" to saved.width, "h" to saved.height)))
                    runCatching { account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase())) }
                }
            } catch (e: Exception) {
                toast(rf("upscale.failed", mapOf("error" to cleanError(e))), isError = true)
            } finally {
                busy = false
            }
        }
    }

    /**
     * 真正做一次官方放大并落盘。
     *
     * **两张都留**：放大前那张本来就在图库里，这里只把放大版另存成一条新记录
     * （`feature = "upscale"`），并把 [upscaleSource] 记为放大前那张 ——
     * 于是预览显示放大版，而图生图/遮罩重绘仍用放大前的。
     *
     * @return 新保存的放大版条目；失败抛异常由调用方报错
     */
    private suspend fun runOfficialUpscale(
        sourcePath: String,
        sourceWidth: Int,
        sourceHeight: Int,
        cost: Int,
        token: String,
    ): HistoryItem? {
        // 记放大自己的耗时（临时图库右上角那个角标要用）
        val startedAt = System.currentTimeMillis()
        val (outWidth, outHeight) = OfficialUpscale.outputSize(sourceWidth, sourceHeight)
        val png = withContext(Dispatchers.Default) {
            val image = MaskCodec.decodeFull(images, sourcePath)
                ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
            val bytes = images.pngBytes(image)
            image.recycle()
            bytes
        }
        val upscaled = api.officialUpscale(
            token = token,
            imagePng = png,
            imageBaseUrl = effectiveImageBase(),
        )
        // 放大版的参数沿用放大前那条记录（图库里点开能看到同一套提示词/种子）
        val sourceItem = history.firstOrNull { it.filePath == sourcePath } ?: current
        // 命名：**放大前那张的名字 + 2x**（一眼能对上；重名时 Storage 会自动加序号）
        val sourceStem = sourcePath.substringAfterLast('/').substringBeforeLast('.')
        val item = storage.saveImage(
            bytes = upscaled,
            params = sourceItem?.let { GenerateParamsCodec.fromJson(it.params) } ?: params,
            seed = sourceItem?.seed ?: resolveSeed(params),
            settings = settings,
            groups = groups,
            feature = "upscale",
            model = sourceItem?.model,
            width = outWidth,
            height = outHeight,
            groupId = null,
            stemOverride = "${sourceStem}2x",
            // 父子关系落盘：图库据此把两张并成同一个格子（放大版不单独占格）
            upscaleOfPath = sourcePath,
        )
        history = listOf(item) + history
        sessionImages.add(0, item)
        // 临时图库角标：放大这张图花了多久（与生成类同一个口径）
        sessionImageDurationMs[item.id] = System.currentTimeMillis() - startedAt
        current = item
        upscaleSource = UpscaleSource(sourcePath, sourceWidth, sourceHeight)
        maskMode = false
        status = rf("upscale.done", mapOf("cost" to cost, "w" to outWidth, "h" to outHeight))
        return item
    }

    /**
     * 生成后自动放大（设置里那个开关）。**逐张**放大并落盘；单张失败只记状态、不影响整批结果。
     * @return 放大成功的张数
     */
    private suspend fun autoUpscaleIfEnabled(saved: List<HistoryItem>, token: String): Int {
        if (!settings.officialUpscaleAfterGenerate) return 0
        var done = 0
        var spent = 0
        for (item in saved) {
            val price = OfficialUpscale.price(item.width, item.height) ?: continue
            val result = runCatching {
                runOfficialUpscale(
                    sourcePath = item.filePath,
                    sourceWidth = item.width,
                    sourceHeight = item.height,
                    cost = price,
                    token = token,
                )
            }.getOrNull()
            if (result != null) {
                done += 1
                spent += price
            }
        }
        // 自动放大走完之后，**预览还给生成结果本身**（放大版作为额外条目留在图库/临时图库里）：
        // 这样 current 不是放大版 → 图生图/遮罩重绘天然就用放大前那张，没有隐藏状态。
        saved.firstOrNull()?.let { current = it }
        upscaleSource = null
        if (done > 0) status = rf("upscale.autoDone", mapOf("count" to done, "cost" to spent))
        return done
    }

    // -------------------------------------------------------------- 导演工具（6 件套）

    /**
     * 跑一次导演工具（移除背景 / 清理杂物 / 提取线稿 / 转换草图 / 自动上色 / 修改表情）。
     *
     * 参数**全部取自设置**（`directorTool` / `augmentDefry` / `augmentColorizePrompt` /
     * `augmentEmotion` / `augmentEmotionLevel` / `augmentKeepTextBubbles`）——
     * 这样导演台里的选择自动进"参数记忆"，关掉「记住 Director 参数」就在启动时回落默认值。
     *
     * 作用对象是**当前工作图**（与 2× 放大同一个口径）；结果按 `feature = "director-<id>"`
     * 存进图库，并把预览切到结果（移除背景有多张时用「混合结果」那张）。
     */
    fun runDirectorTool() {
        val toolId = settings.directorTool
        val prompt = if (toolId == "emotion") "" else settings.augmentColorizePrompt
        val defry = if (toolId == "emotion") {
            settings.augmentEmotionLevel.toInt()
        } else {
            settings.augmentDefry.toInt()
        }
        val emotion = settings.augmentEmotion
        val keepTextBubbles = settings.augmentKeepTextBubbles
        if (busy) {
            toast(rt("status.busyPleaseWait"), isError = true)
            return
        }
        val tool = DirectorTools.byId(toolId)
        val sourcePath = workImagePath
        if (sourcePath == null) {
            toast(rt("director.needImage"), isError = true)
            return
        }
        val token = effectiveToken()
        if (token.isEmpty()) {
            toast(rt("error.tokenRequired"), isError = true)
            return
        }
        val dims = MaskCodec.imageSize(images, sourcePath)
        if (dims == null) {
            toast(rt("error.inpaintSourceMissing"), isError = true)
            return
        }

        viewModelScope.launch {
            busy = true
            status = rf("director.running", mapOf("tool" to tool.name))
            val startedAt = System.currentTimeMillis()
            try {
                // 尺寸按插件的像素预算来（超上限缩小、过小放大）
                val (reqW, reqH) = DirectorTools.budgetSize(dims.first, dims.second)
                val png = withContext(Dispatchers.Default) {
                    val image = MaskCodec.decodeFull(images, sourcePath)
                        ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                    val scaled = images.scale(image, reqW, reqH, smooth = true)
                    val bytes = images.pngBytes(scaled)
                    if (scaled !== image) image.recycle()
                    scaled.recycle()
                    bytes
                }
                val emotionPrompt = if (tool.hasEmotion) {
                    DirectorTools.emotionPrompt(emotion, prompt)
                } else {
                    prompt
                }
                val results = api.directorTool(
                    token = token,
                    reqType = DirectorTools.reqTypeOf(tool, keepTextBubbles),
                    imagePng = png,
                    width = reqW,
                    height = reqH,
                    prompt = if (tool.hasPrompt || tool.hasEmotion) emotionPrompt else null,
                    defry = if (tool.hasDefry) defry else null,
                    imageBaseUrl = effectiveImageBase(),
                )
                if (results.isEmpty()) throw IllegalStateException(rt("error.apiNoImages"))

                // 移除背景回 3 张（masked / generated / blend）：**混合结果**当预览与当前图，
                // 三张都存进图库（名字带在条目名里），不丢东西
                val blend = results.firstOrNull { it.first.contains("blend", ignoreCase = true) }
                    ?: results.last()
                val ordered = listOf(blend) + results.filterNot { it === blend }
                val savedItems = ArrayList<HistoryItem>(ordered.size)
                for ((_, bytes) in ordered) {
                    savedItems.add(
                        storage.saveImage(
                            bytes = bytes,
                            params = params,
                            seed = resolveSeed(params),
                            settings = settings,
                            groups = groups,
                            feature = "director-${tool.id}",
                            width = reqW,
                            height = reqH,
                            groupId = null,
                        ),
                    )
                }
                history = savedItems + history
                sessionImages.addAll(0, savedItems)
                // 临时图库角标：这次导演工具花了多久（多张结果共用同一耗时）
                sessionImageDurationMs.let { map ->
                    val elapsed = System.currentTimeMillis() - startedAt
                    savedItems.forEach { map[it.id] = elapsed }
                }
                // 一次导演工具 = 一批（临时图库把这几张合成一项 + 角标张数）
                val directorBatch = newSessionBatchId()
                savedItems.forEach { sessionBatchIds[it.id] = directorBatch }
                current = savedItems.first()
                upscaleSource = null
                maskMode = false
                toast(rf("director.done", mapOf("tool" to tool.name, "count" to savedItems.size)))
                runCatching { account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase())) }
            } catch (e: Exception) {
                toast(rf("director.failed", mapOf("error" to cleanError(e))), isError = true)
            } finally {
                busy = false
            }
        }
    }

    // -------------------------------------------------------------- 自定义保存目录（SAF）

    /**
     * 记下用户通过系统文件夹选择器选的目录。
     *
     * 关键一步是 `takePersistableUriPermission`：SAF 返回的授权默认只在本次进程有效，
     * 不"持久化"的话**重启 App 后就写不进去了**（表现：设置里还显示着目录，但图不再另存过去）。
     */
    fun setOutputFolder(ref: String?) {
        if (ref == null) {
            setSettings { it.copy(imageOutputTreeUri = "") }
            status = rt("storage.folderCleared")
            return
        }
        // ⚠️ 手机上必须**重新握一次**持久授权：SAF 给的授权默认只在本次进程有效，
        // 不握的话重启后设置里显示着目录、图却不再另存过去。电脑上是个普通路径，直接 true。
        val ok = platform.takePersistablePermission(ref)
        setSettings { it.copy(imageOutputTreeUri = ref) }
        status = if (ok) rt("storage.folderSet") else rt("storage.folderSetNoPerm")
    }

    /** 当前目录的显示名（URI 末段解码，失败就给原文）。 */
    fun outputFolderLabel(): String {
        val raw = settings.imageOutputTreeUri.trim()
        if (raw.isEmpty()) return ""
        val tail = raw.substringAfterLast('/').substringAfterLast(':')
        return runCatching { java.net.URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
    }

    // -------------------------------------------------------------- LLM 辅助（翻译 / 优化）

    /** LLM 调用进行中（那三个小图标要显示忙状态）。 */
    var llmBusy by mutableStateOf(false)
        private set

    private val llm = LlmApi()

    /** 内置工具 `another_llm` 能调用的"用途"清单（对应节点里能被别的 LLM 调用的那些节点）。 */
    private val LLM_TOOL_PURPOSES = listOf(
        LlmMemoryKind.STORYBOARD,
        LlmMemoryKind.PAGE_PLAN,
        LlmMemoryKind.REVERSE,
    )

    /**
     * 当前设置对应的对话参数，每次调用时现取。
     *
     * 用户可配的现在**只剩「思考程度」**（2026-09-16：温度 / Top-P / 最大输出 tokens
     * 三个滑条都删了）。温度固定 0.3 —— 见 [LlmApi.AiParams] 里"删控件 ≠ 改行为"的说明。
     *
     * [cfg] 决定「思考程度」发不发：**只对主 API 生效**（用户 2026-09-16 要求）。
     * 分功能 AI（`cfg.overridden`）一律当作 `"off"` —— 也就是不带 `reasoning_effort`。
     * 理由见 [LlmConfig.overridden]：那个字段很多服务不认识，会直接报 400。
     */
    private fun aiParams(cfg: LlmConfig = settings.llmConfig()) = LlmApi.AiParams.of(
        reasoningEffort = if (cfg.overridden) "off" else settings.llmReasoningEffort,
        // 温度从设置取（默认 0.3 = 原来的写死值；见 AppSettings.llmTemperature）
        temperature = settings.llmTemperature,
    )

    /**
     * **LLM 多轮记忆**：按用途分桶（分镜 / 分页规划 / 优化 / 反推 / 翻译），每桶只留最近 N 轮。
     *
     * 用户 2026-09-16："记忆和保留轮数也使用，这些功能在**分镜和狂暴模式**很好用" ——
     * 逐页写分镜时，后面几页必须记得前面几页写了什么，否则人物和剧情各写各的。
     *
     * 只在内存里（跨重启的持久化 = 节点里的 `historical_record`，本轮不做）。
     * 关掉记忆（`settings.llmMemoryEnabled = false`）时一律不发历史 = 原来的"全新一次调用"。
     */
    private val llmMemory = mutableMapOf<String, MutableList<LlmTurn>>()

    /**
     * **`is_locked` 的结果缓存**（源码靠 ComfyUI 节点缓存实现"参数没变就直接返回上轮结果"）。
     * key 见 [LlmMemoryRules.lockKey]：用途 + 模型 + 两个提示词 + 温度 + 输出上限 + 图数。
     */
    private val llmLockedResults = mutableMapOf<String, String>()

    private fun llmMemoryFile() = java.io.File(platform.paths.filesDir, "llm_memory.json")

    /**
     */
    /**
     * **记忆不落盘**（用户 2026-09-17："分镜那些退出软件会清后台的，只保留当前会话的记忆"）。
     *
     * 早先是按节点的 `historical_record` 语义把记忆写进 `llm_memory.json`、启动时读回来；
     * 现在改成**只活在当前进程**：退出应用（后台被清）之后不再带回上一轮的对话。
     * 这里只做一件事：把老版本留下的那个文件删掉（免得看着像"还在用"）。
     */
    private fun dropLegacyLlmMemoryFile() {
        runCatching { llmMemoryFile().delete() }
    }


    /** 清空全部记忆（抽屉「清空」）。记忆只在内存里，清掉就没了。 */
    fun clearLlmMemory() {
        llmMemory.clear()
        llmLockedResults.clear()
        // 顺手把老版本留下的落盘文件删掉（记忆已经不再写盘）
        runCatching { llmMemoryFile().delete() }
    }

    /** 记忆里现在有多少轮（界面显示 / 测试用）。 */
    fun llmMemoryRounds(kindKey: String): Int = llmMemory[kindKey]?.size ?: 0

    fun llmMemoryTotalRounds(): Int = llmMemory.values.sumOf { it.size }



    /** 某一类用途的记忆清空（**新一轮**分镜/狂暴模式开始时用：上一部剧情不该污染这一部）。 */
    private fun clearLlmMemory(vararg kinds: String) {
        kinds.forEach { llmMemory.remove(it) }
        llmLockedResults.clear()
    }

    /**
     * **统一的 LLM 调用入口**：发请求 + **记一条对话记录**（成功/失败都记），
     * 并且（记忆开着时）**带上最近 N 轮历史、把这一轮追加进记忆**。
     */
    // ------------------------------------------- 导入上下文（节点 `user_history` / `historical_record`）

    /**
     * Optional bundled conversation context. The public source keeps this asset empty
     * so first launch does not import private or example conversation history.
     */
    private suspend fun loadBundledLlmContext() {
        val raw = withContext(Dispatchers.IO) {
            runCatching {
                javaClass.classLoader?.getResourceAsStream("llm_context.json")
                    ?.use { String(it.readBytes(), Charsets.UTF_8) }
            }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: return
        val turns = com.kallan.naistudio.models.LlmContext.parse(raw)
        if (turns.isEmpty()) return
        // ⚠️ **只在"完全没有上下文"时才导入自带那份**（用户 2026-09-18：
        // 「关闭每次都清空上下文，上下文保留，只有没有上下文时才导入预设上下文」）。
        // 之前是"只要盘的上下文跟自带那份不一样就覆盖" ✗ —— 那等于每次开 App 都
        // 把用户手上那份上下文冲回这 12 轮。现在有上下文就原样保留，只补空档。
        if (settings.llmUserHistoryJson.isNotBlank()) return
        val json = com.kallan.naistudio.models.LlmContext.toUserHistoryJson(turns)
        setSettings { it.copy(llmUserHistoryJson = json, llmContextSource = "") }
    }

    /**
     * 现有记忆的 JSON（导出用；导回去就是"导入上下文"）。
     *
     * @param kind 只导出某一个用途（用户 2026-09-17："导出记忆时可以选择导出哪里的记忆"）；
     *   null / 空 = **全部**。
     */
    fun llmMemoryExportJson(kind: String? = null): String {
        val picked = kind?.takeIf { it.isNotBlank() }
        return LlmContext.toMemoryJson(
            if (picked == null) llmMemory else mapOf(picked to llmMemory[picked].orEmpty()),
        )
    }

    /** 导出到文件（电脑线：`ref` 是路径）。 */
    fun exportLlmMemoryFile(ref: String?, kind: String? = null) {
        if (ref.isNullOrBlank()) return
        val ok = runCatching {
            java.io.File(ref).writeText(llmMemoryExportJson(kind))
            true
        }.getOrDefault(false)
        status = rt(if (ok) "llm.contextExported" else "llm.contextBad")
    }

    // ------------------------------------------------- LLM 预设（用户自建，2026-09-17）

    /** 新建或更新一条预设（同一个 id 就覆盖）。 */
    fun upsertLlmPreset(entry: LlmPresetEntry) {
        setSettings { current ->
            val exists = current.llmPresets.any { it.id == entry.id }
            val next = if (exists) {
                current.llmPresets.map { if (it.id == entry.id) entry else it }
            } else {
                current.llmPresets + entry
            }
            current.copy(llmPresets = next)
        }
        status = rf("llm.presetSaved", mapOf("name" to entry.name))
    }

    // ------------------------------------------- SillyTavern 预设（2026-09-17，手机线先行）

    /** 预设正文的落盘目录（**不进 prefs**：ST 预设动辄几百 KB）。 */
    private fun stPresetDir(): java.io.File =
        java.io.File(platform.paths.filesDir, "st_presets").apply { mkdirs() }

    /** 已解析的预设缓存（id → 解析结果；导入时解析一次，之后直接用）。 */
    private val stPresetCache = mutableMapOf<String, StPreset.Preset?>()

    /**
     * 桌面版导入入口：用户选了一个 `.json` 文件 → 读该路径的文本 → [importStPreset]。
     *
     * 手机线走 SAF 的 `Uri`（`importStPresetUri`）；**电脑线没有 SAF**，界面直接给文件路径，
     * 名字用 `File(path).name`（Windows 的反斜杠在这一步就剥掉了）。
     */
    fun importStPresetFromFile(path: String) {
        val text = runCatching { java.io.File(path).readText() }.getOrNull()
        if (text == null) {
            toast(rt("st.importFailed"), isError = true)
            return
        }
        importStPreset(text, java.io.File(path).name)
    }

    /**
     * **导出当前选中的预设**（用户 2026-09-18：「在导入预设旁边加一个导出预设」）。
     *
     * 原样把盘上那份 `.json` 的**字节**写到用户选的位置 —— 不重新序列化，导出的文件跟导入的
     * 一模一样（注释、键顺序、没启用的条目都保留），方便搬去手机线或备份。
     */
    fun exportStPresetToFile(path: String) {
        val ref = settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }
        if (ref == null) {
            toast(rt("st.exportNone"), isError = true)
            return
        }
        // ⚠️ AWT 的保存框**不会**自动补扩展名：用户只打文件名就会存出一个没后缀的文件，
        // 再导入时认不出来。所以这里统一补 `.json`。
        val target = if (path.endsWith(".json", ignoreCase = true)) path else "$path.json"
        val ok = runCatching {
            java.io.File(target).writeBytes(java.io.File(stPresetDir(), ref.fileName).readBytes())
            true
        }.getOrDefault(false)
        toast(rt(if (ok) "st.exported" else "st.exportFailed"), isError = !ok)
    }

    /**
     * 导入一份 ST 预设：解析 → **原样落盘** → 记一条引用。
     *
     * ⚠️ **不做自动瘦身**（用户 2026-09-18：「这个预设功能和之前不一样，用下午 2 点那个预设系统」）：
     * 导入进来就是文件本来的样子（134 条也在），`查看`里逐条开关 / 改正文 / `恢复默认` ——
     * 就是 1.1.29（下午两点前最后一版）那套，**没有**自动删条目、也没有"删条目"按钮。
     * `.orig` 留的是**刚导入的原文**，所以「恢复默认」= 回到导入那一刻。
     *
     * 解析失败（不是 ST 预设 / 没有 prompts）会明确报错，不会存进去半个空壳。
     */
    fun importStPreset(json: String, fileName: String) {
        // 名字 = **文件名**（用户 2026-09-18：不要显示 SAF 的 `document:123456`，要显示文件名）。
        // 手机线的 `importStPresetUri` 已经查过 DISPLAY_NAME；电脑线由调用方传文件名，
        // 这里再兜一层（`\\` 那一步是给 Windows 路径兜的），老调用/老名字都清干净。
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .removePrefix("document:")
            .ifBlank { "预设" }
        val preset = StPreset.parse(json, name)
        if (preset == null || preset.allEntries.isEmpty()) {
            toast(rt("st.importFailed"), isError = true)
            return
        }
        val id = "st-" + System.currentTimeMillis().toString(36)
        val stored = "$id.json"
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val file = java.io.File(stPresetDir(), stored)
                    file.writeText(json)
                    // 留一份原始副本：用户删乱了可以「恢复默认」（= 回到刚导入的原文）
                    file.copyTo(java.io.File(stPresetDir(), "$stored.orig"), overwrite = true)
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                toast(rt("st.importFailed"), isError = true)
                return@launch
            }
            stPresetCache[id] = preset
            setSettings { current ->
                current.copy(
                    stPresetRefs = current.stPresetRefs + StPresetRef(
                        id = id,
                        name = name,
                        fileName = stored,
                        entryCount = preset.entries.size,
                        estimatedTokens = preset.estimatedTokens,
                        hasOriginal = true,
                    ),
                    stPresetId = id,
                )
            }
            status = rf(
                "st.imported",
                mapOf(
                    "name" to name,
                    "n" to preset.entries.size.toString(),
                    "tokens" to preset.estimatedTokens.toString(),
                ),
            )
        }
    }

    /** 删除一份 ST 预设（连同落盘文件与各功能的指向）。 */
    fun removeStPreset(id: String) {
        val ref = settings.stPresetRefs.firstOrNull { it.id == id }
        stPresetCache.remove(id)
        runCatching {
            if (ref != null) {
                java.io.File(stPresetDir(), ref.fileName).delete()
                java.io.File(stPresetDir(), ref.fileName + ".orig").delete()
            }
        }
        setSettings { current ->
            fun keep(selected: String) = if (selected == id) "" else selected
            current.copy(
                stPresetRefs = current.stPresetRefs.filterNot { it.id == id },
                stPresetId = keep(current.stPresetId),
                stPresetTranslate = keep(current.stPresetTranslate),
                stPresetOptimize = keep(current.stPresetOptimize),
                stPresetStoryboard = keep(current.stPresetStoryboard),
                stPresetPlan = keep(current.stPresetPlan),
                stPresetReverse = keep(current.stPresetReverse),
            )
        }
    }

    /** 全局启用/停用某份预设（`""` = 不用）。 */
    fun selectStPreset(id: String) {
        setSettings { it.copy(stPresetId = id) }
    }

    /**
     * 某个用途这次要用的 ST 预设（解析结果）：先查内存缓存，没有再从盘上读一次并解析。
     * 读/解析失败都返回 null（**绝不能因为预设文件把一次正常请求搞挂**）。
     */
    private fun stPresetFor(kindKey: String): StPreset.Preset? {
        val id = settings.stPresetIdFor(kindKey)
        if (id.isBlank()) return null
        stPresetCache[id]?.let { return it }
        val ref = settings.stPresetRefs.firstOrNull { it.id == id } ?: return null
        val json = runCatching { java.io.File(stPresetDir(), ref.fileName).readText() }.getOrNull()
        // 应用用户的"逐条停用"（自定义）：停用的条目不进请求
        val parsed = json?.let {
            StPreset.parse(it, ref.name)
                ?.withOverrides(ref.enabledEntries, ref.disabledEntries)
        }
        stPresetCache[id] = parsed
        return parsed
    }

    /** 读一份预设的**全部条目**给"只读查看器"用（含停用的）。 */
    fun stPresetEntriesForView(id: String): List<StPreset.Entry> {
        val ref = settings.stPresetRefs.firstOrNull { it.id == id } ?: return emptyList()
        val json = runCatching { java.io.File(stPresetDir(), ref.fileName).readText() }.getOrNull() ?: return emptyList()
        return StPreset.parse(json, ref.name)
            ?.withOverrides(ref.enabledEntries, ref.disabledEntries)
            ?.allEntries
            .orEmpty()
    }

    // ---- 逐条自定义（用户 2026-09-18："在手机上开放预设条目自定义"）----

    /** 重新算这条预设的"启用条数 / ≈tokens"（停用之后列表上显示的数字要跟着变）。 */
    private fun refreshStPresetStats(id: String) {
        val ref = settings.stPresetRefs.firstOrNull { it.id == id } ?: return
        val json = runCatching { java.io.File(stPresetDir(), ref.fileName).readText() }.getOrNull() ?: return
        val parsed = StPreset.parse(json, ref.name) ?: return
        val effective = parsed.withOverrides(ref.enabledEntries, ref.disabledEntries)
        setSettings { current ->
            current.copy(
                stPresetRefs = current.stPresetRefs.map { item ->
                    if (item.id != id) {
                        item
                    } else {
                        item.copy(
                            entryCount = effective.entries.size,
                            estimatedTokens = effective.estimatedTokens,
                        )
                    }
                },
            )
        }
    }


    /** 逐条启用/停用（存的是"停用清单"，所以默认状态=预设原样）。 */
    fun setStPresetEntryEnabled(id: String, entryId: String, enabled: Boolean) {
        if (entryId.isBlank()) return
        stPresetCache.remove(id) // 过滤结果变了，缓存作废
        setSettings { current ->
            current.copy(
                stPresetRefs = current.stPresetRefs.map { ref ->
                    if (ref.id != id) {
                        ref
                    } else {
                        // 双向：开 → 记进 enabled 清单并从未启用清单里去掉；关 → 反之
                        ref.copy(
                            enabledEntries = if (enabled) {
                                (ref.enabledEntries + entryId).distinct()
                            } else {
                                ref.enabledEntries - entryId
                            },
                            disabledEntries = if (enabled) {
                                ref.disabledEntries - entryId
                            } else {
                                (ref.disabledEntries + entryId).distinct()
                            },
                        )
                    }
                },
            )
        }
        refreshStPresetStats(id)
    }

    /** 改某一条的正文（写回落盘文件；原始副本不动，随时可"恢复默认"）。 */
    fun updateStPresetEntryContent(id: String, entryId: String, text: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ref = settings.stPresetRefs.firstOrNull { it.id == id }
                        ?: return@runCatching false
                    val file = java.io.File(stPresetDir(), ref.fileName)
                    val root = org.json.JSONObject(file.readText())
                    val prompts = root.optJSONArray("prompts") ?: return@runCatching false
                    var changed = false
                    for (index in 0 until prompts.length()) {
                        val item = prompts.optJSONObject(index) ?: continue
                        if (item.optString("identifier") == entryId) {
                            item.put("content", text)
                            changed = true
                            break
                        }
                    }
                    if (!changed) return@runCatching false
                    file.writeText(root.toString())
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                toast(rt("st.editFailed"), isError = true)
                return@launch
            }
            stPresetCache.remove(id)
            setSettings { current ->
                current.copy(
                    stPresetRefs = current.stPresetRefs.map {
                        if (it.id == id) it.copy(hasEdits = true) else it
                    },
                )
            }
            refreshStPresetStats(id)
            status = rt("st.saved")
        }
    }

    /** 恢复默认：用导入时留的原始副本覆盖回去（连同停用清单与编辑标记一起清）。 */
    fun resetStPreset(id: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ref = settings.stPresetRefs.firstOrNull { it.id == id }
                        ?: return@runCatching false
                    val dir = stPresetDir()
                    val original = java.io.File(dir, ref.fileName + ".orig")
                    if (!original.isFile) return@runCatching false
                    original.copyTo(java.io.File(dir, ref.fileName), overwrite = true)
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                toast(rt("st.resetFailed"), isError = true)
                return@launch
            }
            stPresetCache.remove(id)
            setSettings { current ->
                current.copy(
                    stPresetRefs = current.stPresetRefs.map {
                        if (it.id == id) {
                            it.copy(
                                enabledEntries = emptyList(),
                                disabledEntries = emptyList(),
                                hasEdits = false,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
            refreshStPresetStats(id)
            status = rt("st.reset")
        }
    }

    /** 删掉一条预设，并把各功能里指向它的选择清掉（否则会"选着一个不存在的预设"）。 */
    fun removeLlmPreset(id: String) {
        setSettings { current ->
            fun keep(selected: String) = if (selected == id) "" else selected
            current.copy(
                llmPresets = current.llmPresets.filterNot { it.id == id },
                llmTranslatePreset = keep(current.llmTranslatePreset),
                llmOptimizePreset = keep(current.llmOptimizePreset),
                llmStoryboardPreset = keep(current.llmStoryboardPreset),
                llmPlanPreset = keep(current.llmPlanPreset),
                llmReversePreset = keep(current.llmReversePreset),
            )
        }
    }

    private suspend fun llmChatRecorded(
        kindKey: String,
        cfg: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        imageDataUrl: String? = null,
        transform: (String) -> String = { it },
    ): String {
        // 「新开应用第一次对话也要把上下文发过去」：正常由界面启动时调 load()，
        // 但用户点得快时那次读盘可能还没落地 —— 这里**等它读完**再发，免得第一句白问。
        if (!bootedOnce) load()
        loadJob?.join()
        // 总开关已去除（用户 2026-09-17）：AI 功能恒可用，不再有"整体关闭"拦截。
        val started = System.currentTimeMillis()
        // 记忆（节点里的 `is_memory` + `conversation_rounds`）
        val memoryKey = kindKey.substringAfterLast('.')
        val rounds = settings.llmHistoryRounds
        // 源码语义（llm.py:1651-1653）：**关掉记忆 = 清空历史**（不是"留着不发"）。
        if (!settings.llmMemoryEnabled && LlmMemoryRules.CLEAR_ON_DISABLE) {
            if (llmMemory.containsKey(memoryKey)) {
                llmMemory.remove(memoryKey)
            }
        }
        // `is_locked`（llm.py:1611-1617）：参数没变就直接返回上轮结果，不发请求。
        val lockKey = LlmMemoryRules.lockKey(
            kindKey = kindKey, model = cfg.model, systemPrompt = systemPrompt,
            userPrompt = userPrompt, temperature = settings.llmTemperature,
            maxLength = settings.llmMaxLength, imageCount = if (imageDataUrl != null) 1 else 0,
        )
        if (settings.llmIsLocked) {
            llmLockedResults[lockKey]?.let { cached ->
                recordTurn(
                    kindKey = kindKey, provider = "llm", model = cfg.model,
                    systemPrompt = systemPrompt, userPrompt = userPrompt,
                    imageCount = if (imageDataUrl != null) 1 else 0,
                    response = cached, extra = "is_locked：参数没变，直接用上轮结果（未发请求）",
                )
                return cached
            }
        }
        val extraJson = runCatching {
            if (settings.llmExtraParameters.isBlank()) null
            else org.json.JSONObject(settings.llmExtraParameters)
        }.getOrNull()
        val history = if (settings.llmMemoryEnabled) {
            // ⚠️ 用 contextWindow：**拒答/被拦的那些轮不计入上下文**（用户 2026-09-18 要求）
            LlmMemoryRules.contextWindow(llmMemory[memoryKey].orEmpty(), rounds).map {
                LlmApi.Turn(user = it.user, assistant = it.assistant)
            }
        } else {
            emptyList()
        }
        val memoryNote = if (settings.llmMemoryEnabled) {
            " · 记忆 ${history.size}/${rounds} 轮"
        } else {
            " · 记忆关"
        }

        // 本次请求会带上多少轮"导入的上下文"（用户 2026-09-17：第一次对话也要带上）
        val contextTurns = settings.llmUserHistoryJson.takeIf { it.isNotBlank() }
            ?.let { LlmContext.parse(it).size } ?: 0
        val contextNote = if (contextTurns > 0) " · 上下文 $contextTurns 轮" else ""
        // 图片走哪条路（源码 539-576）：有 ImgBB key → 先上传取 URL；没有 → base64 直接发
        var dataUrlForRequest = imageDataUrl
        var imageUrlForRequest: String? = null
        if (imageDataUrl != null && settings.llmImgbbKey.isNotBlank()) {
            imageUrlForRequest = llm.uploadToImgbb(
                settings.llmImgbbKey,
                imageDataUrl.substringAfter("base64,", imageDataUrl),
            )
            dataUrlForRequest = null
        }
        // **全部输入口按节点传过去**（对话格式由 LlmApi.messages 负责，见那边的逐条对照）
        val tools = llmToolsFor(memoryKey)
        val request = LlmApi.ChatRequest(
            baseUrl = cfg.url,
            apiKey = cfg.key,
            model = cfg.model,
            systemPrompt = systemPrompt,
            systemPromptInput = settings.llmSystemPromptInput,
            userPrompt = userPrompt,
            history = history,
            userHistoryJson = settings.llmUserHistoryJson.takeIf { it.isNotBlank() },
            fileContent = settings.llmFileContent.takeIf { it.isNotBlank() },
            tools = tools,
            toolsInSysPrompt = settings.llmToolsInSysPrompt,
            imageDataUrl = dataUrlForRequest,
            imageUrl = imageUrlForRequest,
            params = aiParams(cfg),
            extra = extraJson,
            stream = settings.llmStream,
            // SillyTavern 预设（按用途取；没选就是 null，行为与以前完全一样）
            stPreset = stPresetFor(memoryKey),
        )
        return try {
            val result = llm.send(
                req = request,
                toolDispatcher = { call -> dispatchLlmTool(call, memoryKey) },
            )
            val out = transform(result.content)
            // 模型拒答时单独说一句人话：内容是**服务商的内容策略**在拦，不是 App 拦的
            //（不改变返回值 —— 只在状态行解释；上层照常把它当"这一轮的回复"处理）
            if (ComicStoryboard.looksLikeRefusal(out)) status = rt("llm.refusedByProvider")
            if (settings.llmMemoryEnabled) {
                // 追加 + 落盘（源码把整份历史写回 temp/<id>.json；我们按用途写一个文件）
                LlmMemoryRules.append(
                    turns = llmMemory.getOrPut(memoryKey) { mutableListOf() },
                    turn = LlmTurn(user = userPrompt, assistant = out),
                )
            }
            // `is_locked` 的缓存：下次参数完全一样就直接返回它
            llmLockedResults[lockKey] = out
            val toolNote = if (result.toolCalls.isNotEmpty()) " · 工具 ${result.toolCalls.size} 次" else ""
            recordTurn(
                kindKey = kindKey,
                provider = "llm",
                model = cfg.model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageCount = if (imageDataUrl != null) 1 else 0,
                response = out,
                durationMs = System.currentTimeMillis() - started,
                extra = "temperature ${settings.llmTemperature}$memoryNote$toolNote$contextNote",
            )
            out
        } catch (e: Exception) {
            recordTurn(
                kindKey = kindKey,
                provider = "llm",
                model = cfg.model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageCount = if (imageDataUrl != null) 1 else 0,
                ok = false,
                error = e.message ?: "失败",
                durationMs = System.currentTimeMillis() - started,
                extra = "temperature ${settings.llmTemperature}$memoryNote$contextNote",
            )
            throw e
        }
    }

    /**
     * 节点的 `tools`：用户自己填的 JSON（设置里那段）+ **内置的 `another_llm`**。
     *
     * 内置那个只在"这个用途不是主脑"时提供 —— 对应源码 `1607-1610`：
     * `main_brain == "disable"` 的 LLM 会被登记进 `llm_tools_list`，供别的 LLM 调用。
     */
    private fun llmToolsFor(memoryKey: String): List<LlmApi.ToolSpec> {
        val list = mutableListOf<LlmApi.ToolSpec>()
        runCatching {
            val raw = settings.llmToolsJson.trim()
            if (raw.isEmpty()) return@runCatching
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val one = arr.optJSONObject(i) ?: continue
                val fn = one.optJSONObject("function") ?: one
                val name = fn.optString("name")
                if (name.isBlank()) continue
                list.add(
                    LlmApi.ToolSpec(
                        name = name,
                        description = fn.optString("description"),
                        parametersJson = fn.optJSONObject("parameters")?.toString()
                            ?: """{"type":"object","properties":{}}""",
                    ),
                )
            }
        }
        // 只有"不是主脑"的用途才把自己登记成工具（并且不能自己调自己）
        // ⚠️ **内置 `another_llm` 工具已停用**（用户 2026-09-18 报 400）：
        // 它以前是"只要 `llmMainBrain` 是关的（默认就是关的）就**每次请求都带上 `tools`**"，
        // 而 DeepSeek **思考模式下带 `tools` 的请求必须把历史轮次的 `reasoning_content` 完整回传**
        // （官方《思考模式》文档：带 tools 不回传就 400；不带 tools 则无需回传）。
        // 我们历史里的 assistant 只有 `content`（自带上下文那 12 轮是 gemini 存的，根本没有 reasoning）
        // → 于是分镜这类调用直接 400 ✗。
        // 控制它的「主脑」开关也已按用户要求从界面删掉，所以它现在是"没人能关的默认开启" ✗。
        // 要用回来得先把 `reasoning_content` 也存进记忆/上下文并回传，再给个显式开关。
        return list
    }

    /**
     * **执行一次工具调用**（源码 `dispatch_tool` 的等价物）。
     *
     * 内置 `another_llm`：按 `id` 找到那个用途的规则与配置，拿 `question` 再问一次它。
     * 其余工具我们没有实现 → 返回 null，让 [LlmApi.send] 回"Tool not found"（与源码同款文案）。
     */
    private suspend fun dispatchLlmTool(call: LlmApi.ToolCall, callerKey: String): String? {
        if (call.name != "another_llm") return null
        val args = runCatching { org.json.JSONObject(call.arguments) }.getOrNull() ?: return null
        val target = args.optString("id").trim()
        val question = args.optString("question").ifBlank { args.optString("prompt") }
        if (target.isEmpty() || target == callerKey || question.isEmpty()) {
            return "找不到对应的智能助手"
        }
        val rules = when (target) {
            LlmMemoryKind.STORYBOARD -> settings.comicStoryboardRules
            LlmMemoryKind.PAGE_PLAN -> settings.comicPagePlanRules
            LlmMemoryKind.REVERSE -> reverseRules()
            else -> ""
        }
        val cfg = when (target) {
            LlmMemoryKind.STORYBOARD -> settings.llmConfigForStoryboard()
            LlmMemoryKind.PAGE_PLAN -> settings.llmConfigForPlan()
            else -> settings.llmConfig()
        }
        if (!cfg.configured) return "那个智能助手没有配置 API"
        val reply = llm.send(
            LlmApi.ChatRequest(
                baseUrl = cfg.url, apiKey = cfg.key, model = cfg.model,
                systemPrompt = rules.ifBlank { "你是一个强大的人工智能助手。" },
                userPrompt = question,
                params = aiParams(cfg),
            ),
        ).content
        return "你调用的智能助手的问答是：" + reply + "\n请根据以上回答，回答用户的问题。"
    }

    private val baiduTranslate = BaiduTranslate()

    /** 最近用过的正面提示词（来自图库历史，去重、按时间倒序）。给"时钟"图标用。 */
    fun recentPrompts(limit: Int = 30): List<String> = history
        .asSequence()
        .map { it.prompt.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(limit)
        .toList()

    /** 当前模式下的优化规则 / 反推规则（设置里按模式分别保存）。 */
    private fun optimizeRules(): String = when (settings.optimizeRuleMode) {
        PromptRules.MODE_NATURAL -> settings.optimizeRulesNatural
        PromptRules.MODE_MIXED -> settings.optimizeRulesMixed
        else -> settings.optimizeRulesTag
    }.ifBlank { PromptRules.defaultOptimizeRules(settings.optimizeRuleMode) }

    private fun reverseRules(): String = when (settings.reverseRuleMode) {
        PromptRules.MODE_NATURAL -> settings.reverseRulesNatural
        PromptRules.MODE_MIXED -> settings.reverseRulesMixed
        else -> settings.reverseRulesTag
    }.ifBlank { PromptRules.defaultReverseRules(settings.reverseRuleMode) }

    /**
     * 「翻译后能不能换回原文」的账，**按框分开记**（用户 2026-09-16 要求：
     * 按钮跟着选中的框走）。值是 `原文 to 译文`。
     */
    private val translateRevert = mutableMapOf<String, Pair<String, String>>()

    /** Translation files and derived indexes are prepared once in the user cache. */
    private val promptTranslationRepository by lazy {
        com.kallan.naistudio.models.PromptTranslationRepository(
            java.io.File(platform.paths.filesDir, "tagcodex-cache"),
        ) { path -> tagCodex.json(path) }
    }
    private var promptTranslationWarmupJob: Job? = null

    private fun warmPromptTranslationIndex() {
        if (promptTranslationWarmupJob?.isActive == true) return
        val codexId = tagCodexId
        promptTranslationWarmupJob = viewModelScope.launch {
            try {
                promptTranslationRepository.prepare(codexId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("PromptTranslation", "Index preparation failed: ${e.message}")
            }
        }
    }

    /** A first click can join startup preparation, but must not wait indefinitely. */
    private suspend fun preparedPromptTranslationDict(): PromptTranslate.Dict {
        return try {
            withTimeoutOrNull(8000) {
                promptTranslationRepository.prepare(tagCodexId)
            } ?: PromptTranslate.Dict(emptyMap(), emptyMap())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarn("PromptTranslation", "Using translation service without dictionary: ${e.message}")
            PromptTranslate.Dict(emptyMap(), emptyMap())
        }
    }

    /**
     * 混合翻译这一次的**明细**（抽屉「日志」里那条可展开的「已翻译并填入…」）。
     *
     * ⚠️ `outcome.trace.size` 才是**真正发出去的请求数** —— `outcome.batches` 只是分批数：
     *    行数对不上时管道会**二分重试**，请求数会比批数多（见 `PromptTranslate.translateBatch`）。
     *    所以"调用了几次翻译"报的是前者，批数附在后面。
     */
    private fun translateDetail(outcome: PromptTranslate.Outcome): List<String> = buildList {
        add(rf("log.translateDetail.dict", mapOf("n" to outcome.dictHits.toString())))
        if (outcome.apiHits > 0) {
            add(rf("log.translateDetail.api", mapOf("n" to outcome.apiHits.toString())))
        }
        if (outcome.misses > 0) {
            add(rf("log.translateDetail.miss", mapOf("n" to outcome.misses.toString())))
        }
        add(
            rf(
                "log.translateDetail.calls",
                mapOf("n" to outcome.trace.size.toString(), "b" to outcome.batches.toString()),
            ),
        )
    }

    /**
     * **翻译提示词**：中文 → 英文（内容本来就是英文时反向翻成中文），结果写回 [field] 那个框。
     * **同一个按钮再点一次 = 回到翻译前的原文**（账按框分开记，互不干扰）。
     *
     * 后端按设置里的「翻译服务」选：**LLM**（LLM API）或**百度翻译**（百度 API）。
     * system prompt 用常量 [PromptRules.TRANSLATE_RULES]（本项目原创文案；里面的
     * `{src_lang}` / `{dst_lang}` 占位符由 [PromptRules.translateRules] 替换）。
     */
    fun translatePrompt(field: String = PromptField.POSITIVE) {
        // 剧情框也能翻（用户 2026-09-17）：它不是提示词，读写的都是 `comicPlot`；
        // 角色 / 分镜的框同理（用户 2026-09-19 给每个文本框各配了一条工具栏）
        val key = PromptField.stackKey(field)
        val text = promptTextFor(key).trim()
        if (text.isEmpty()) {
            toast(rt("llm.emptyPrompt"), isError = true)
            return
        }
        if (blockedByPromptLock(key)) return
        // 再点一次 = 撤销翻译，回到原文
        val revert = translateRevert[key]
        if (revert != null && text == revert.second) {
            applyPromptText(key, revert.first)
            translateRevert.remove(key)
            toast(rt("llm.translateReverted"))
            return
        }

        val chinese = LlmApi.looksChinese(text)
        val srcLang = if (chinese) "简体中文" else "英文"
        val dstLang = if (chinese) "英文" else "简体中文"

        // ⚠️ **第 ㊿h 批（2026-09-22）**：翻译不再"整串丢给后端"，改成
        //    **词典优先 + 查不到的一批交翻译**（网页原型 `translate/pipeline.js` 的 Kotlin 版）：
        //      · 词典命中的**一个字符都不外发**（省额度 + tag 更准）；
        //      · 只有落词典外的段进批，**一次请求**装完（用户要的"一次性交给百度"）；
        //      · 回填前**校验行数**，不符就二分重试 —— 宁可某条没翻，**绝不串位**。
        //    ⚠️ **UI 一个像素都没动**：还是同一个「翻译」按钮、同一条撤销账。
        if (settings.translateService == "baidu") {
            val appId = settings.baiduTranslateAppId.trim()
            val secret = settings.baiduTranslateSecret.trim()
            if (appId.isEmpty() || secret.isEmpty()) {
                toast(rt("llm.baiduNotConfigured"), isError = true)
                return
            }
            if (llmBusy) return
            viewModelScope.launch {
                llmBusy = true
                // ⚠️ 这是**百度翻译**那条路，状态行不能再说"正在调用 LLM" ——
                //    用户 2026-09-22 报的正是这个：「手机点翻译提示调用 llm，明明设置是百度翻译」。
                status = rt("llm.runningBaidu")
                try {
                    val dict = preparedPromptTranslationDict()
                    val tp = PromptTranslate.Params()
                    val outcome = PromptTranslate.run(text, dict, tp) { lines ->
                        // 同批同方向（`planBatches` 保证）⇒ 用"批里含不含中文"判断方向可靠
                        val toEn = lines.any { l -> LlmApi.looksChinese(l) }
                        baiduTranslate.translateLines(
                            appId = appId,
                            secretKey = secret,
                            lines = lines,
                            from = "auto",
                            to = if (toEn) "en" else "zh",
                        )
                    }
                    val rendered = PromptTranslate.render(outcome.items, tp)
                    // 中文进 ⇒ 出英文 tag；英文进 ⇒ 出中文（与旧行为一致）
                    val result = if (chinese) rendered.tags else rendered.zh
                    applyPromptResult(
                        result, "llm.translateDone", key,
                        rememberOriginal = text,
                        detail = translateDetail(outcome),
                    )
                } catch (e: Exception) {
                    // 同理：这条路失败也不是"LLM 调用失败"
                    toast(rf("llm.baiduFailed", mapOf("error" to (e.message ?: ""))), isError = true)
                } finally {
                    llmBusy = false
                }
            }
            return
        }

        runLlm(
            cfg = settings.llmConfigForTranslate(),
            system = PromptRules.translateRules(srcLang, dstLang),
            user = text,
            doneKey = "llm.translateDone",
            field = key,
            rememberOriginal = text,
        )
    }

    /** 兼容旧调用点：不指定框 = 正面提示词。 */
    fun translatePositivePrompt() = translatePrompt(PromptField.POSITIVE)

    /**
     * **LLM 优化提示词**：把一句话描述扩写成 danbooru 风格的英文标签串，结果写回 [field] 那个框。
     * system prompt 用设置里可编辑的「优化规则」（默认值见 [PromptRules.defaultOptimizeRules]，本项目原创文案）。
     */
    fun optimizePrompt(field: String = PromptField.POSITIVE) {
        val key = PromptField.stackKey(field)
        val text = promptTextFor(key).trim()
        if (text.isEmpty()) {
            toast(rt("llm.emptyPrompt"), isError = true)
            return
        }
        if (blockedByPromptLock(key)) return
        runLlm(
            cfg = settings.llmConfigForOptimize(),
            system = optimizeRules(),
            user = text,
            doneKey = "llm.optimizeDone",
            field = key,
        )
    }

    /** 兼容旧调用点：不指定框 = 正面提示词。 */
    fun optimizePositivePrompt() = optimizePrompt(PromptField.POSITIVE)

    /**
     * 把模型输出清洗（去思考块/引号）后写进 [field] 那个框；翻译/优化共用。
     *
     * @param rememberOriginal 传了就把"翻译前的原文"记下来，供**再点一次翻译时换回原文**
     *   （记账也是按框分的）。
     * @param detail 抽屉「日志」里这一条**可展开的明细**（混合翻译会填"词典命中多少 / 调用几次"）。
     */
    private suspend fun applyPromptResult(
        raw: String,
        doneKey: String,
        field: String,
        rememberOriginal: String? = null,
        detail: List<String> = emptyList(),
    ) {
        // ⚠️ `stackKey`：剧情框要原样保留 PLOT（用 `orDefault` 会折成正面提示词，
        // 于是"在剧情框里翻译/优化"的结果写进了提示词 —— 用户 2026-09-18 报的 bug）。
        val key = PromptField.stackKey(field)
        val cleaned = PromptRules.cleanModelOutput(raw)
        if (cleaned.isEmpty()) throw LlmException(rt("llm.emptyResult"))
        when {
            // 剧情：写回 comicPlot（自带防抖落盘 + setter 自己记一笔撤销）
            PromptField.isPlot(key) -> comicPlot = cleaned

            // 角色 / 分镜的框：`setCharacterPrompt` 会先记一笔撤销再写
            PromptField.isChar(key) -> setCharacterPrompt(
                index = PromptField.charIndex(key),
                negative = PromptField.charNegative(key),
                text = cleaned,
            )

            else -> {
                val before = params.promptOf(key)
                params = params.withPrompt(key, cleaned)
                // AI 改写也算一次改动，撤销按钮要能回到改写前
                if (before != cleaned) recordPromptEdit(key, before)
                withContext(Dispatchers.IO) { storage.setParams(params) }
            }
        }
        if (rememberOriginal != null) {
            translateRevert[key] = rememberOriginal to cleaned
        }
        toast(rt(doneKey), detail = detail)
    }

    /**
     * 把一段文本直接写进某个提示词框（历史提示词的「用这一条」用它）。
     *
     * 走这里而不是 `setParam`，是因为历史抽屉现在要按**当前那一条工具栏**落笔 ——
     * 每个文本框各有一条工具栏之后，历史抽屉可能是从角色 / 分镜 / 剧情的框上打开的
     * （用户 2026-09-19），所以三种框都要认。
     */
    fun setPromptField(field: String, text: String) {
        if (PromptField.isPlot(field)) {
            comicPlot = text
            return
        }
        if (PromptField.isChar(field)) {
            setCharacterPrompt(
                index = PromptField.charIndex(field),
                negative = PromptField.charNegative(field),
                text = text,
            )
            return
        }
        val key = PromptField.orDefault(field)
        if (blockedByPromptLock(key)) return
        val before = params.promptOf(key)
        if (before == text) return
        params = params.withPrompt(key, text)
        recordPromptEdit(key, before)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
    }

    /**
     * **角色 / 漫画格子那个文本框的输入口**（用户 2026-09-19：每个文本框各配一条工具栏）。
     *
     * 原来界面直接 `updateCharacter { copy(prompt = …) }` —— 那样改的东西**不进撤销栈**，
     * 于是新配上去的撤销/重做在角色与分镜的框上按下去毫无反应。这里补上记账，
     * 合并窗口与提示词三条同一套（连续打字算一步）。
     */
    fun setCharacterPrompt(index: Int, negative: Boolean, text: String) {
        val key = PromptField.charKey(index, negative)
        val before = promptTextFor(key)
        if (before == text) return
        recordPromptEdit(key, before)
        updateCharacter(index) {
            if (negative) it.copy(negativePrompt = text) else it.copy(prompt = text)
        }
    }

    /**
     * **清掉指定的那个框**（每条工具栏上的垃圾桶）。
     *
     * 每个文本框各有一条工具栏之后，垃圾桶不该再靠"当前焦点是谁"猜（点垃圾桶不会给框焦点，
     * 猜错就会清掉另一个框）。所以按**这条工具栏挂在哪个框上**直接清：
     *  · 提示词三条各清各的（锁着的那条不动，与 [clearPrompts] 一个口径）；
     *  · 角色 / 分镜 / 剧情各清各的；
     *  · 本来就空的框点了不弹提示（免得"点了没反应"看着像坏了）。
     */
    fun clearPromptField(field: String) {
        if (!isPromptLockedField(field) && promptTextFor(field).isEmpty()) return
        setPromptField(field, "")
        toast(rt("prompt.cleared"))
    }

    /**
     * **只清提示词里的某一条**（风格 / 正面 / 负面）。
     *
     * ⚠️ 用户 2026-09-24：「生成图片上的**对文本框的垃圾桶还是对有些文本框不生效**，
     * 怀疑是**文本框未统一指针**」—— 根因就在这里：三个提示词框过去注册的是同一个
     * `TextFieldTarget.Prompt`，垃圾桶分不清用户站在哪一条，只能**一次清三条**；
     * 而正面 / 负面现在是**切换着看**的，于是"清掉看不见的那两条"看起来就是垃圾桶不生效。
     *
     * 锁着的那一条**跳过并如实说一句** —— 与 [clearPrompts] 同一条纪律。
     *
     * @param field `PromptField.STYLE / POSITIVE / NEGATIVE`
     */
    fun clearOnePrompt(field: String) {
        val locked = when (field) {
            PromptField.STYLE -> settings.promptLockStyle
            PromptField.POSITIVE -> settings.promptLockPositive
            PromptField.NEGATIVE -> settings.promptLockNegative
            else -> false
        }
        if (locked) {
            toast(rt("prompt.clearAllLocked"), isError = true)
            return
        }
        val before = params
        params = when (field) {
            PromptField.STYLE -> before.copy(stylePrompt = "")
            PromptField.POSITIVE -> before.copy(positivePrompt = "")
            PromptField.NEGATIVE -> before.copy(negativePrompt = "")
            else -> before
        }
        if (params == before) return
        recordPromptEdits(before, params)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rt("prompt.cleared"))
    }

    /** 这个框上有没有锁（只有提示词三条有锁；锁着的不许清）。 */
    private fun isPromptLockedField(field: String): Boolean {
        if (PromptField.isPlot(field) || PromptField.isChar(field)) return false
        return isPromptLocked(PromptField.orDefault(field))
    }

    /**
     * 跑一次 LLM，把结果写回 [field] 那个框（翻译 / AI 优化共用）。
     *
     * [cfg] 由调用方从设置里取 —— 每个功能可以有自己的那套接口/密钥/模型
     * （见 [AppSettings.llmConfigForTranslate]），留空的项目自动回落到共用那一套。
     */
    private fun runLlm(
        cfg: LlmConfig,
        system: String,
        user: String,
        doneKey: String,
        field: String = PromptField.POSITIVE,
        rememberOriginal: String? = null,
    ) {
        if (!cfg.configured) {
            toast(rt("llm.notConfigured"), isError = true)
            return
        }
        if (llmBusy) return
        viewModelScope.launch {
            llmBusy = true
            status = rt("llm.running")
            try {
                val result = llmChatRecorded(
                    kindKey = "conversation.kind.optimize",
                    cfg = cfg,
                    systemPrompt = system,
                    userPrompt = user,
                )
                applyPromptResult(result, doneKey, field, rememberOriginal)
            } catch (e: Exception) {
                toast(rf("llm.failed", mapOf("error" to (e.message ?: ""))), isError = true)
            } finally {
                llmBusy = false
            }
        }
    }

    /**
     * [field] 那个框锁着就拦下这次改写（AI 翻译 / 优化 / 反推导入 / 历史填入共用），
     * 并弹一句说明是哪个框。
     *
     * 就算不拦，[params] 的 setter 也会把值钉回去 —— 但那样用户只看到"点了没反应"，
     * 所以这里先讲清楚为什么不动。
     */
    private fun blockedByPromptLock(field: String): Boolean {
        // 剧情框没有锁（三把锁只管提示词那三条），别拿正面提示词的锁拦它
        if (PromptField.isPlot(field)) return false
        // 角色 / 分镜的框同样没有锁（用户 2026-09-19 给它们各配了工具栏）
        if (PromptField.isChar(field)) return false
        val key = PromptField.orDefault(field)
        if (!isPromptLocked(key)) return false
        toast(rf("prompt.lockBlocked", mapOf("field" to rt(promptFieldNameKey(key)))), isError = true)
        return true
    }

    /** 某个框在界面上叫什么（用于"XXX 已锁定"这类提示）。 */
    private fun promptFieldNameKey(field: String): String = when (PromptField.orDefault(field)) {
        PromptField.STYLE -> "generate.stylePrompt"
        PromptField.NEGATIVE -> "generate.negativePrompt"
        else -> "generate.positivePrompt"
    }

    /**
     * **一键清空提示词**：风格 + 正面 + 负面三条一起清（正面提示词那排图标里的垃圾桶）。
     *
     * 锁住的那几条**跳过不清**（用户要求：锁上后完全不能改，垃圾桶也清不掉）；
     * 三条全锁着就只弹提示、什么都不动。
     * 走的是 [params] 的 setter，所以"跳过锁住的"这件事由那一处统一保证。
     */
    fun clearPrompts() {
        if (settings.promptLockStyle && settings.promptLockPositive && settings.promptLockNegative) {
            toast(rt("prompt.clearAllLocked"), isError = true)
            return
        }
        val before = params
        params = before.copy(stylePrompt = "", positivePrompt = "", negativePrompt = "")
        if (params == before) return
        // 清空也要进撤销栈：手滑点一下不至于把写好的词永久弄丢
        recordPromptEdits(before, params)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rt("prompt.cleared"))
    }

    /**
     * 清空漫画剧情框（剧情框右下角那个垃圾桶）。
     *
     * **只清剧情文本**，分格提示词不动 —— 那是"按剧情分镜"的产物，清掉一次就等于
     * 白跑一次 LLM（用户确认过）。走 [comicPlot] 的 setter，所以照样有防抖落盘。
     */
    fun clearComicPlot() {
        if (extras.comicPlot.isEmpty()) return
        comicPlot = ""
        toast(rt("comic.plotCleared"))
    }

    // ------------------------------------------------ 底栏作用对象：当前聚焦的文本框

    /**
     * 底部常驻栏（垃圾桶 + 「作用于 X」）现在作用在哪个文本框上 —— **谁拿到焦点谁登记**。
     *
     * `null` = 还没人登记（用户还没点过任何输入框），垃圾桶退回 [clearPrompts]，
     * 和改动前完全一致。
     */
    var textFieldTarget: TextFieldTarget? by mutableStateOf(null)
        private set

    /**
     * 文本框焦点变化时登记 / 注销。
     *
     * ⚠️ **只有"当前持有者"失焦才注销**：焦点从一个框搬到另一个框时，旧的
     * `onFocusChanged(false)` 有可能**后**到，无条件置空会把新框刚登记的目标抹掉 ——
     * 现象就是"点进去第一次按没反应，第二次才行"。
     *
     * @param focusKey 统一焦点标记（[textInputFocused]）里这个框的 key。
     *   **默认按 target 取**（角色 / 剧情 / 漫画风格那些框，一个 target 就是一个框）；
     *   但**同一个 target 对应好几个框时必须显式传**（提示词的风格 / 正面 / 负面三条都报
     *   `TextFieldTarget.Prompt`）—— 重名会让"一条失焦"把"另一条还在聚焦"记成没人聚焦，
     *   那一瞬间按下空格就会被当成"拖图"吃掉，框里打不出空格。
     */
    fun onTextFieldFocus(
        target: TextFieldTarget,
        focused: Boolean,
        focusKey: String = "field:$target",
    ) {
        if (focused) {
            textFieldTarget = target
        } else if (textFieldTarget == target) {
            textFieldTarget = null
        }
        // 统一标记（空格那把闸门读它，见 `textInputFocused`）。
        // ⚠️ 用**带 key 的登记**而不是直接写布尔：焦点从一个框搬到另一个框时，旧框的
        // `onFocusChanged(false)` 有可能**后**到，直接置 false 会把新框刚登记的焦点抹掉。
        reportTextInputFocus(focusKey, focused)
    }

    /**
     * **底栏垃圾桶**：清掉当前聚焦的那个文本框。
     *
     * 三条提示词走 [clearPrompts]（一次清三条、跳过锁住的）；角色 / 漫画那几个框各清各的。
     * 先看目标再动手，所以不会出现"在角色框里按了，结果提示词被清空"。
     *
     * ⚠️ 2026-09-19 起工具栏变成"每个文本框各一条"，垃圾桶改走 [clearPromptField]（明说清哪个框，
     * 不再靠焦点猜）—— 这个方法留着是给"以后还有共享入口"用的，当前界面上没有调用点。
     */
    fun clearFocusedTextField() {
        when (val target = textFieldTarget) {
            // 还没登记（没点过任何输入框）⇒ 保持老行为：三条一起清
            null -> clearPrompts()

            // ⚠️ 用户 2026-09-24：提示词三个框**各清各的**（原来一律 `clearPrompts()` 清三条，
            //    而正面 / 负面现在是切换着看的 ⇒ "清掉看不见的那两条"看起来就是垃圾桶坏了）。
            is TextFieldTarget.Prompt -> clearOnePrompt(target.field)
            is TextFieldTarget.CharacterPrompt -> {
                updateCharacter(target.index) { it.copy(prompt = "") }
                toast(rt("prompt.cleared"))
            }

            is TextFieldTarget.CharacterNegative -> {
                updateCharacter(target.index) { it.copy(negativePrompt = "") }
                toast(rt("prompt.cleared"))
            }

            TextFieldTarget.Plot -> clearComicPlot()

            TextFieldTarget.ComicStyle -> {
                if (extras.comicStyle.isEmpty()) return
                setComicStyle("")
                toast(rt("prompt.cleared"))
            }
        }
    }

    // ------------------------------------------------ 历史提示词抽屉

    /**
     * 「历史提示词」抽屉现在给**哪个框**开着（`null` = 关着）。
     *
     * 用户 2026-09-19 把工具栏拆成"每个文本框一条"之后，时钟图标会长在提示词三条之外
     * 的地方（角色 / 分镜的格子上）。抽屉的开合状态如果还留在 `GenerateScreen` 里，
     * 角色面板那几个卡片就得一层层往上回调才能开它 —— 所以挪进 [AppState]，
     * 谁都能 `openPromptHistory(field)`，而落笔（「用这一条」）用的是同一个 field。
     */
    var promptHistoryField: String? by mutableStateOf(null)
        private set

    fun openPromptHistory(field: String) {
        promptHistoryField = PromptField.stackKey(field)
    }

    fun closePromptHistory() {
        promptHistoryField = null
    }

    // ------------------------------------------------ 工具页：图片反推提示词

    /** 工具页选中的待反推图片（复制进私有目录后的本地路径）。 */
    var reverseImagePath by mutableStateOf<String?>(null)
        private set

    /** 反推结果（显示在工具页的输出框里）。 */
    var reverseResult by mutableStateOf<String?>(null)
        private set

    /** 反推进行中。 */
    var reverseBusy by mutableStateOf(false)
        private set

    /** 工具页选中一张图（复制进 `filesDir/reverse/`，之后按路径使用）。 */
    fun selectReverseImage(ref: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { copyImageToPrivate(ref, "reverse") }
            if (saved == null) {
                toast(rt("error.importFailed"), isError = true)
                return@launch
            }
            reverseImagePath = saved
            reverseResult = null
            status = rt("reverse.selected")
        }
    }

    /** 清掉工具页的选择与结果。 */
    fun clearReverseImage() {
        reverseImagePath = null
        reverseResult = null
    }

    /** 开始反推：用设置里「反推模式」对应的规则跑一次视觉 LLM，结果留在工具页。 */
    fun runReverseForTool() {
        val path = reverseImagePath
        if (path == null) {
            toast(rt("reverse.needImage"), isError = true)
            return
        }
        val cfg = settings.llmConfigForReverse()
        if (!cfg.configured) {
            toast(rt("llm.notConfigured"), isError = true)
            return
        }
        if (reverseBusy) return
        viewModelScope.launch {
            reverseBusy = true
            status = rt("llm.reversing")
            try {
                val dataUrl = withContext(Dispatchers.Default) { imageDataUrl(path) }
                val result = llmChatRecorded(
                    kindKey = "conversation.kind.reverse",
                    cfg = cfg,
                    systemPrompt = reverseRules(),
                    userPrompt = rt("llm.reverseAsk"),
                    imageDataUrl = dataUrl,
                    transform = { PromptRules.cleanModelOutput(it) },
                )
                val cleaned = PromptRules.cleanModelOutput(result)
                if (cleaned.isEmpty()) throw LlmException(rt("llm.emptyResult"))
                reverseResult = cleaned
                status = rt("reverse.done")
            } catch (e: Exception) {
                toast(rf("llm.failed", mapOf("error" to (e.message ?: ""))), isError = true)
            } finally {
                reverseBusy = false
            }
        }
    }

    /** 把反推结果填进正面提示词框。 */
    fun importReverseResultToPrompt() {
        val text = reverseResult?.trim().orEmpty()
        if (text.isEmpty()) {
            toast(rt("reverse.noResult"), isError = true)
            return
        }
        if (blockedByPromptLock(PromptField.POSITIVE)) return
        params = params.copy(positivePrompt = text)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rt("reverse.imported"))
    }

    // ------------------------------------------------ 工具页：角色图鉴（AnimaDex）

    /**
     * 角色图鉴（AnimaDex，3.6 万动漫角色索引）。
     *
     * 数据源全公开、不用登录：手机直接请求 <https://animadex.net>（App 不需要代理，
     * 网页版当初需要只是因为浏览器跨域）。**不使用 LoRA 相关字段**（用户要求去掉）。
     */
    var animaQuery by mutableStateOf("")
        private set

    /** 排序（服务端合法值见 [AnimaDexProtocol.SORTS]）。 */
    var animaSort by mutableStateOf("count")
        private set

    /** 已选筛选：facet key → value（空字符串表示没选）。 */
    var animaFilters by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var animaPage by mutableStateOf(1)
        private set
    var animaTotal by mutableStateOf(0)
        private set
    var animaPages by mutableStateOf(1)
        private set
    var animaResults by mutableStateOf<List<AnimaCharacter>>(emptyList())
        private set
    var animaFacets by mutableStateOf<List<AnimaFacet>>(emptyList())
        private set
    var animaBusy by mutableStateOf(false)
        private set
    var animaError by mutableStateOf<String?>(null)
        private set

    /** 详情面板当前打开的角色（null = 关着）。 */
    var animaDetail by mutableStateOf<AnimaCharacter?>(null)
        private set

    /**
     * 视图：`characters`（角色，默认）或 `copyrights`（作品）。
     * 对应站点上的 `?mode=characters` / `?mode=copyrights`。
     */
    var animaMode by mutableStateOf("characters")
        private set

    /** 作品视图的结果（点一个作品 = 回到角色视图并按该作品筛选）。 */
    var animaSeries by mutableStateOf<List<AnimaSeries>>(emptyList())
        private set

    /** 网格每行几个（齿轮里改，2~4）。 */
    var animaColumns by mutableStateOf(2)
        private set

    /**
     * 角色图鉴的**工具设置持久记忆**（每行几个 / 视图 / 排序 / 只显示有图的）。
     *
     * 用独立的 SharedPreferences，**不动 AppSettings 与备份格式**（那会牵扯备份兼容性），
     * 只存这一小撮展示偏好；下次进工具时还是上次那样。
     */
    private val animaPrefs = platform.prefs("animadex_tool")

    init {
        animaColumns = animaPrefs.getInt("columns", 2).coerceIn(2, 4)
        animaMode = animaPrefs.getString("mode", "characters") ?: "characters"
        animaSort = animaPrefs.getString("sort", "count") ?: "count"
    }

    private fun rememberAnimaPrefs() {
        animaPrefs.edit()
            .putInt("columns", animaColumns)
            .putString("mode", animaMode)
            .putString("sort", animaSort)
            .apply()
    }

    /** 顶栏的**胶囊搜索框**是否展开（点右上角放大镜切换，带滑出动画）。 */
    var animaSearchOpen by mutableStateOf(false)
        private set

    fun animaToggleSearch() {
        animaSearchOpen = !animaSearchOpen
    }

    fun animaCloseSearch() {
        animaSearchOpen = false
    }

    private var animaSeed: Int? = null
    private var animaFacetsLoaded = false

    /** 角色图鉴的网络层（无状态，随便建）。 */
    private val anima = AnimaDexApi()

    fun animaSetMode(mode: String) {
        if (mode == animaMode) return
        animaMode = mode
        rememberAnimaPrefs()
        animaPage = 1
        animaSearch(reset = true)
    }

    fun animaSetColumns(columns: Int) {
        animaColumns = columns.coerceIn(2, 4)
        rememberAnimaPrefs()
    }

    /** 设置面板里改过的东西都记下来（下次进来还是这样）。 */
    private fun animaRemember() = rememberAnimaPrefs()

    /**
     * 点角色名下的**作品**：切回角色视图并按这个作品筛选。
     * （`copyright` 筛选值必须用服务端的英文 slug —— 发中文会搜不到。）
     */
    fun animaFilterByCopyright(value: String, label: String) {
        if (value.isBlank()) return
        animaMode = "characters"
        animaFilters = animaFilters + ("copyright" to value)
        animaQuery = ""
        animaDetail = null
        animaSearch(reset = true)
        toast(rf("anima.filteredBySeries", mapOf("name" to AnimaDexLabels.series(value, label))))
    }

    fun animaSetQuery(query: String) {
        animaQuery = query
    }

    fun animaSetSort(sort: String) {
        animaSort = sort
        rememberAnimaPrefs()
        if (sort == "random") animaSeed = (1..999999).random()
        animaSearch(reset = true)
    }

    fun animaToggleFilter(key: String, value: String) {
        animaFilters = if (animaFilters[key] == value) {
            animaFilters - key
        } else {
            animaFilters + (key to value)
        }
        animaSearch(reset = true)
    }

    fun animaClearFilters() {
        animaFilters = emptyMap()
        animaSearch(reset = true)
    }

    fun animaGoPage(page: Int) {
        if (page < 1 || (animaPages > 0 && page > animaPages) || animaBusy) return
        animaPage = page
        animaSearch()
    }

    fun animaOpenDetail(character: AnimaCharacter) {
        animaDetail = character
    }

    fun animaCloseDetail() {
        animaDetail = null
    }

    /** 首次进入工具时拉筛选表（只拉一次）。 */
    fun animaEnsureFacets() {
        if (animaFacetsLoaded || animaBusy) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { anima.facets() } }
                .onSuccess { json ->
                    animaFacets = AnimaDexProtocol.parseFacets(json)
                    animaFacetsLoaded = animaFacets.isNotEmpty()
                }
        }
    }

    /**
     * 搜索。
     * @param reset true = 回到第 1 页（改关键词/筛选/排序时用）
     */
    fun animaSearch(reset: Boolean = false) {
        if (reset) animaPage = 1
        viewModelScope.launch {
            animaBusy = true
            animaError = null
            try {
                if (animaMode == "copyrights") {
                    val json = withContext(Dispatchers.IO) {
                        anima.series(animaQuery, animaSort, animaPage)
                    }
                    val (total, list) = AnimaDexProtocol.parseSeries(json)
                    animaSeries = list
                    animaTotal = total
                    // 作品视图服务端也是每页 36
                    animaPages = ((total + AnimaDexProtocol.PAGE_SIZE - 1) / AnimaDexProtocol.PAGE_SIZE)
                        .coerceAtLeast(1)
                    return@launch
                }
                val json = withContext(Dispatchers.IO) {
                    anima.search(animaQuery, animaSort, animaPage, animaFilters, animaSeed)
                }
                val page = AnimaDexProtocol.parseSearch(json)
                animaResults = page.results
                animaTotal = page.total
                animaPages = page.pages
                animaPage = page.page
            } catch (e: Exception) {
                animaError = cleanError(e)
                animaResults = emptyList()
                animaSeries = emptyList()
            } finally {
                animaBusy = false
            }
        }
    }

    /**
     * 把角色的提示词**追加**到 [field] 那个提示词框（不覆盖已经写好的内容）。
     * 提示词栏角色图鉴的详情面板「填入提示词」用它 —— 默认落到正面提示词。
     */
    fun animaAppendPrompt(text: String, field: String = PromptField.POSITIVE) {
        val key = PromptField.orDefault(field)
        val trigger = text.trim()
        if (trigger.isEmpty()) return
        if (blockedByPromptLock(key)) return
        val current = params.promptOf(key).trim()
        if (current.contains(trigger, ignoreCase = true)) {
            toast(rt("anima.alreadyInPrompt"))
            return
        }
        val merged = if (current.isEmpty()) trigger else "$current, $trigger"
        params = params.withPrompt(key, merged)
        recordPromptEdit(key, current)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rf("anima.appended", mapOf("text" to trigger.take(40))))
    }

    // ------------------------------------------------ 工具页：画师超市（法典图鉴）

    /**
     * 「画师超市」—— 读了**法典图鉴**（AgIzT/NovelAI-Tag）的整本 JSON 放内存里本地筛。
     *
     * ⚠️ 为什么不沿用角色图鉴那套"服务端分页 + facets"：法典图鉴**没有检索接口**，
     * 它就是把整本 JSON（画师词典 2.9 MB / 2688 条）一次拉下来在浏览器里筛。
     * 我们也一样 —— 换来的是搜索零延迟、可以反复筛不用来回请求。
     *
     * ⚠️ 数据是**在线读、本地缓存**，不打包进 APK：那份汇编的授权写明"未经许可不得再分发"
     *（代码 MIT，但词条与数据汇编另有归属，见 [TagCodexProtocol.LICENSE_NOTE]）。
     */
    /**
     * ⚠️ **第 ㊿ 批**：给它一个**本地缓存目录** ✓ —— 用户口径是
     * 「**不能下载在app或软件里不用每次下载吗**」⇒ 下过一次就存着 ✓、之后不再联网 ✓。
     *
     * ⚠️ 为什么不打包进包 ✗：授权写明"数据汇编未经许可不得再分发" ✓
     *（[TagCodexProtocol.LICENSE_NOTE] ✓，设置页也对用户承诺过"没有打包进 App" ✓）。
     * 缓存在**用户自己的机器上** ✓ 两件事都不违反 ✓，而体验与打包**没有区别** ✓。
     */
    private val tagCodex = TagCodexApi(
        cacheDir = File(platform.paths.filesDir, "tagcodex-cache"),
    )

    var tagCodexBusy by mutableStateOf(false)
        private set
    var tagCodexError by mutableStateOf<String?>(null)
        private set

    /** 可选的法典列表（默认剔除 NSFW 与外链本；NSFW 由 [tagCodexShowNsfw] 开关决定）。 */
    var tagCodexList by mutableStateOf<List<TagCodexSummary>>(emptyList())
        private set

    /**
     * 画师超市：**是否把 NSFW 法典也列出来**（用户 2026-09-17 要求做成开关，默认关）。
     *
     * 关着的时候和以前一样只列常规本；打开后 NSFW 那两本也进列表。
     * 偏好存在工具自己的 `tagcodex_tool` 里（和每行几个、上次开哪本同一套）。
     */
    var tagCodexShowNsfw by mutableStateOf(false)
        private set

    /** 法典列表的**原始 JSON**（开关一变就本地重解析，不用重新联网）。 */
    private var tagCodexRawList: org.json.JSONArray? = null

    /** 当前打开的是哪一本。 */
    var tagCodexId by mutableStateOf(TagCodexProtocol.DEFAULT_CODEX)
        private set

    /** 当前这本的全部内容（`null` = 还没加载）。 */
    var tagCodexDoc by mutableStateOf<TagCodexDoc?>(null)
        private set

    var tagCodexQuery by mutableStateOf("")
    /** 目录树下钻路径；空 = 不按目录筛。 */
    var tagCodexPath by mutableStateOf<List<String>>(emptyList())
    /** 只看某一次更新（`updateFilters` 的 id）。 */
    var tagCodexUpdate by mutableStateOf("")
    /** 只看本次新增。 */
    var tagCodexNewOnly by mutableStateOf(false)

    /** 网格每行几个（齿轮里改，2~4）。 */
    var tagCodexColumns by mutableStateOf(2)
        private set

    /**
     * 画师超市的**工具设置持久记忆**（每行几个 / 上次开的是哪一本）。
     *
     * 与角色图鉴（`animaPrefs`）同一套做法：独立的 SharedPreferences，
     * **不动 AppSettings 与备份格式** —— 那会牵扯备份兼容性，
     * 而这里只是一小撮展示偏好，不值得为它升一次备份格式。
     */
    private val tagCodexPrefs = platform.prefs("tagcodex_tool")

    init {
        tagCodexColumns = tagCodexPrefs.getInt("columns", 2).coerceIn(2, 4)
        tagCodexShowNsfw = tagCodexPrefs.getBoolean("showNsfw", false)
        // 上次开的是哪一本也记着：常用的那本不用每次重挑
        tagCodexId = tagCodexPrefs.getString("codex", null)
            ?.takeIf { it.isNotBlank() }
            ?: TagCodexProtocol.DEFAULT_CODEX
    }

    private fun rememberTagCodexPrefs() {
        tagCodexPrefs.edit()
            .putInt("columns", tagCodexColumns)
            .putString("codex", tagCodexId)
            .putBoolean("showNsfw", tagCodexShowNsfw)
            .apply()
    }

    /** 齿轮里改列数：存下来，下次进工具还是这样。 */
    fun tagCodexSetColumns(columns: Int) {
        tagCodexColumns = columns.coerceIn(2, 4)
        rememberTagCodexPrefs()
    }

    /**
     * 切 NSFW 开关：**本地重解析**列表（原始 JSON 已经缓存），不联网；
     * 若当前开着的那本正好被隐藏了（它就是 NSFW 本）→ 回到默认那本。
     */
    fun tagCodexSetShowNsfw(on: Boolean) {
        if (tagCodexShowNsfw == on) return
        tagCodexShowNsfw = on
        tagCodexRawList?.let {
            tagCodexList = TagCodexProtocol.parseCodexes(it, includeNsfw = on)
        }
        if (!on && tagCodexList.none { it.id == tagCodexId }) {
            tagCodexPath = emptyList()
            tagCodexUpdate = ""
            tagCodexNewOnly = false
            tagCodexLoad(TagCodexProtocol.DEFAULT_CODEX)
        }
        rememberTagCodexPrefs()
    }

    /**
     * 当前筛选结果。**派生值**，不额外存状态 —— 筛的是内存里那份列表，
     * 没有网络往返，所以每次重组重算完全没问题（2688 条的字符串匹配在手机上是一瞬间）。
     */
    val tagCodexFiltered: List<TagCodexEntry>
        get() = tagCodexDoc?.let { doc ->
            TagCodexQuery.filter(
                entries = doc.entries,
                query = tagCodexQuery,
                pathPrefix = tagCodexPath,
                updateId = tagCodexUpdate,
                newOnly = tagCodexNewOnly,
            )
        }.orEmpty()

    val tagCodexHasFilters: Boolean
        get() = tagCodexQuery.isNotBlank() || tagCodexPath.isNotEmpty() ||
            tagCodexUpdate.isNotEmpty() || tagCodexNewOnly

    /** 首次进工具页时加载（列表 + 当前这本 + 图片基址）。已有内容就不重复拉。 */
    fun tagCodexEnsureLoaded() {
        if (tagCodexBusy || tagCodexDoc != null) return
        tagCodexLoad(tagCodexId)
    }

    // ------------------------------------------- 第 ㊿ 批：tag 的中文对照（词表）

    /**
     * **当前这本的两片中文对照词表** ✓（通用层在前、本书分片在后 ✓ —— 查表顺序就是它 ✓）。
     *
     * 口径照原网页 `assets/app/tag-zh-core.js` ✓：优先级 **`m` > `d` > `a`** ✓，
     * 同级**先 core 再书分片** ✓（`LOOKUP_ORDER` + `lookupTagZh` ✓）。
     */
    private var tagCodexZhShards by mutableStateOf<List<TagZhProtocol.Shard>>(emptyList())

    /** 词表备好了吗 ✓ —— 界面据此决定要不要画中文小字 ✓（没备好就只显示英文 ✓）。 */
    val tagCodexZhReady: Boolean get() = tagCodexZhShards.isNotEmpty()

    /**
     * **查一个 tag 的中文** ✓（没有就 `null` ✓ —— 那一格**只显示英文** ✓，不显空占位 ✗）。
     *
     * @param text tag 原文（**可带权重语法** ✓ —— 查表键在 [TagZhProtocol.key] 里剥 ✓）。
     */
    fun tagCodexZh(text: String): String? =
        TagZhProtocol.lookup(tagCodexZhShards, TagZhProtocol.key(text))

    /**
     * 把 `text` 按 tag 切成段、逐段配好中文 ✓（界面直接照着画 ✓）。
     *
     * ⚠️ **拼回 `lead + text + sep` 就是原文** ✓（一个字符都不丢 ✓）——
     * 这就是"**复制不带翻译**"的地基 ✓：复制走的永远是原文 ✓，中文只是**另画一行** ✓。
     */
    fun tagCodexZhPieces(text: String): List<Pair<TagZhProtocol.Piece, String?>> =
        TagZhProtocol.split(text).map { it to tagCodexZh(it.text) }

    /**
     * 拉当前这本的两片词表 ✓（**失败静默** ✗ —— 没有对照就是"不显示中文" ✓，不影响查词条 ✓）。
     */
    private suspend fun tagCodexLoadZh(codexId: String) {
        val loaded = withContext(Dispatchers.IO) {
            val out = mutableListOf<TagZhProtocol.Shard>()
            for (path in listOf(TagZhProtocol.CORE_PATH, TagZhProtocol.shardPath(codexId))) {
                runCatching { TagZhProtocol.parseShard(tagCodex.json(path)) }
                    .getOrNull()
                    ?.let { if (!it.isEmpty) out.add(it) }
            }
            out
        }
        tagCodexZhShards = loaded
    }

    /** 强制重拉当前这本（下拉里的「刷新」）。 */
    fun tagCodexReload() {
        if (tagCodexBusy) return
        tagCodexLoad(tagCodexId)
    }

    private fun tagCodexLoad(codexId: String) {        viewModelScope.launch {
            tagCodexBusy = true
            tagCodexError = null
            try {
                // 列表只在第一次拉；之后切本不用再要
                if (tagCodexList.isEmpty()) {
                    val array = withContext(Dispatchers.IO) { tagCodex.codexes() }
                    // 原始 JSON 留着：NSFW 开关一变就本地重解析，不用重新联网
                    tagCodexRawList = array
                    tagCodexList = TagCodexProtocol.parseCodexes(array, includeNsfw = tagCodexShowNsfw)
                }
                val media = withContext(Dispatchers.IO) { tagCodex.media() }
                val json = withContext(Dispatchers.IO) { tagCodex.doc(codexId) }
                val summary = tagCodexList.firstOrNull { it.id == codexId }
                    ?: TagCodexSummary(id = codexId, title = codexId)
                tagCodexDoc = TagCodexProtocol.parseDoc(
                    json = json,
                    summary = summary,
                    media = TagCodexProtocol.parseMedia(media),
                )
                tagCodexId = codexId
                // ⚠️ **第 ㊿ 批**：词条到手之后，顺手把那两片**中文对照词表**也备好 ✓
                //（通用层 + 本书分片 ✓）—— 同一条缓存 + 三级降级 ✓，下过一次就不再联网 ✓。
                // ⚠️ **失败不影响主流程** ✗：对照表没有就是"这一页不显示中文" ✓
                //（站点也是这个降级 ✓ —— 对照表晚到 / 挂了，tag 照旧能看能复制 ✓）。
                tagCodexLoadZh(codexId)
                warmPromptTranslationIndex()
                // 只在**成功加载之后**才记住：加载失败不该把一本打不开的书存成默认
                rememberTagCodexPrefs()
            } catch (e: Exception) {
                tagCodexError = cleanError(e)
                tagCodexDoc = null
            } finally {
                tagCodexBusy = false
            }
        }
    }

    /** 换一本。会清掉目录下钻路径（路径是随书走的），并把选择记下来。 */
    fun tagCodexSelect(id: String) {        if (id == tagCodexId && tagCodexDoc != null) return
        tagCodexPath = emptyList()
        tagCodexUpdate = ""
        tagCodexNewOnly = false
        tagCodexLoad(id)
    }

    fun tagCodexSetPath(path: List<String>) {
        tagCodexPath = path
    }

    fun tagCodexSetUpdate(id: String) {
        tagCodexUpdate = if (tagCodexUpdate == id) "" else id
    }

    fun tagCodexClearFilters() {
        tagCodexQuery = ""
        tagCodexPath = emptyList()
        tagCodexUpdate = ""
        tagCodexNewOnly = false
    }

    /**
     * 缩略图 / 原图地址。
     *
     * ⚠️ **第 ㊿g 批（2026-09-22）修**：用户给链接 `?c=artist_nai45_personal` 报图出不来 ✓。
     *
     * **根因**：图片目录**不一定是法典 id** ✗ ——
     * 条目自带 `assetCodexId` ✓（站点 `media.js` 用的就是它 ✓），我只用了法典 id ✗。
     * 实测 `artist_nai45_personal` 5468 条里 **2449 条**指到别处 ✓
     *（`artist_300` 等 4 个 ✓）；**`nai45_community_pack` 全本 6855 条都指到别处** ✓
     * ⇒ 那一本的图**整本裂** ✗。
     *
     * **修法**：**条目自带的 `assetCodexId` 优先** ✓，空了才回落法典 id ✓
     *（另外 7 本一条都没有 ✓ ⇒ 行为完全不变 ✓）。
     */
    fun tagCodexImageUrl(entry: TagCodexEntry, original: Boolean = false): String {
        val doc = tagCodexDoc ?: return ""
        val file = if (original) entry.original.ifBlank { entry.image } else entry.image
        return TagCodexProtocol.imageUrl(
            media = doc.media,
            // ⚠️ **条目自己的图片目录优先** ✓（空 ⇒ 回落法典 id ✓）
            codexId = entry.assetCodexId.ifBlank { tagCodexId },
            file = file,
            assetRev = entry.assetRev,
            original = original,
        )
    }

    /**
     * 把画师串**追加**到生成页的正面提示词（不覆盖已经写好的内容）。
     * 与 [animaAppendPrompt] 同一套行为，只是文案换成画师超市的。
     */
    fun tagCodexAppendPrompt(text: String) {
        val tag = text.trim()
        if (tag.isEmpty()) return
        val current = params.positivePrompt.trim()
        if (current.contains(tag, ignoreCase = true)) {
            toast(rt("tagcodex.alreadyInPrompt"))
            return
        }
        val merged = if (current.isEmpty()) tag else "$current, $tag"
        params = params.copy(positivePrompt = merged)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rf("tagcodex.appended", mapOf("text" to tag.take(40))))
    }

    // ------------------------------------------------ 工具页：元数据（图片元数据）

    /** 工具页选中的待解析图片（复制进私有目录后的本地路径）。 */
    var metadataImagePath by mutableStateOf<String?>(null)
        private set

    /** 解析出来的元数据（null = 还没解析或这张图里没有）。 */
    var metadataResult by mutableStateOf<ImageMetadata?>(null)
        private set

    /** 解析进行中。 */
    var metadataBusy by mutableStateOf(false)
        private set

    /** 工具页选中一张图 → 复制进 `filesDir/metadata/` 并**立刻解析**元数据。 */
    fun selectMetadataImage(ref: String) {
        viewModelScope.launch {
            metadataBusy = true
            status = rt("metadata.parsing")
            try {
                val path = withContext(Dispatchers.IO) { copyImageToPrivate(ref, "metadata") }
                if (path == null) {
                    toast(rt("error.importFailed"), isError = true)
                    return@launch
                }
                metadataImagePath = path
                val parsed = withContext(Dispatchers.IO) {
                    runCatching { java.io.File(path).readBytes() }
                        .getOrNull()
                        ?.let { ImageMetadataReader.read(it) }
                }
                metadataResult = parsed
                status = if (parsed == null) {
                    rt("metadata.noData")
                } else {
                    rf("metadata.parsed", mapOf("source" to rt("metadata.source.${parsed.source.id}")))
                }
            } finally {
                metadataBusy = false
            }
        }
    }

    /** 清掉工具页的选择与结果。 */
    fun clearMetadataImage() {
        metadataImagePath = null
        metadataResult = null
    }

    /** 把解析出来的提示词（正 + 负）填进生成参数。 */
    fun importMetadataPrompts() {
        val meta = metadataResult
        if (meta == null || !meta.hasPrompt) {
            toast(rt("metadata.noData"), isError = true)
            return
        }
        params = params.copy(
            positivePrompt = meta.positivePrompt.ifBlank { params.positivePrompt },
            negativePrompt = meta.negativePrompt.ifBlank { params.negativePrompt },
        )
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rt("metadata.imported"))
    }

    /** 把能认出来的生成参数（尺寸 / 步数 / CFG / 采样器 / 种子 / 模型）整套套用到生成页。 */
    fun applyMetadataParams() {
        val restored = metadataResult?.params
        if (restored == null) {
            toast(rt("metadata.noData"), isError = true)
            return
        }
        // 只覆盖"这张图里真的写了"的项：提示词为空就保留用户当前输入的那一条
        params = restored.copy(
            positivePrompt = restored.positivePrompt.ifBlank { params.positivePrompt },
            negativePrompt = if (restored.negativePrompt.isBlank()) {
                params.negativePrompt
            } else {
                restored.negativePrompt
            },
        )
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
        toast(rt("metadata.applied"))
    }

    /** 图片 → `data:image/jpeg;base64,…`（长边压到 1024，省流量也省 token）。 */
    private fun imageDataUrl(path: String): String {
        val image = MaskCodec.decodeFull(images, path)
            ?: throw LlmException(rt("error.inpaintSourceMissing"))
        val longest = maxOf(image.width, image.height)
        val scaled = if (longest > 1024) {
            val ratio = 1024f / longest
            images.scale(
                image,
                (image.width * ratio).toInt().coerceAtLeast(1),
                (image.height * ratio).toInt().coerceAtLeast(1),
                smooth = true,
            )
        } else {
            image
        }
        val bytes = images.jpegBytes(scaled, 88)
        if (scaled !== image) image.recycle()
        scaled.recycle()
        return "data:image/jpeg;base64," +
            java.util.Base64.getEncoder().encodeToString(bytes)
    }

    // -------------------------------------------------------------- 备份 / 恢复

    /**
     * 把图库里的一张图**另存为**用户选定的文件（`UiHost.rememberFileCreator` 给的 ref）。
     *
     * 为什么要有：图库里的图躺在 App 私有目录里，用户想"把这张图存到桌面/发给别人"
     * 之前是**没有入口**的 —— 只有"生成时顺手另存到某个文件"那条间接路径。
     * 电脑上这是看图应用的基本功能（右键 → 另存为），手机上（SAF 建文件）同样成立。
     *
     * 口径：
     *  · **拷贝字节**，不重编码 —— PNG 原样过去，画质无损，也不会因为解码器差异变形；
     *  · 原图留在图库里（另存不是搬走，图库/缩略图/遮罩编辑都要按私有路径工作）。
     *  · 失败只留一句提示，不影响已有文件。
     */
    fun exportImageFile(path: String, targetRef: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val source = File(path)
                    if (!source.isFile) error("本地图片不存在")
                    platform.openOutput(targetRef)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out) }
                        out.flush()
                    } ?: error("打不开目标文件")
                }.isSuccess
            }
            if (ok) {
                toast(rt("status.imageExported"))
            } else {
                toast(rt("status.imageExportFailed"), isError = true)
            }
        }
    }

    /**
     * 把备份**内嵌进一张自选图片**再导出（和 ComfyUI / NovelAI 把参数写进 PNG 一样）：
     * 图片照常能看，备份 JSON 跟在 PNG 的 `tEXt` 块里（关键字 `naistudio-backup`）。
     */
    fun exportBackupToImage(imageRef: String, targetRef: String) {
        viewModelScope.launch {
            status = rt("status.backupExporting")
            val json = BackupCodec.encode(
                bundle = currentBundle(),
                appVersion = platform.appVersion,
                exportedAt = java.time.Instant.now().toString(),
                pretty = false,
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val sourceBytes = platform.openInput(imageRef)
                        ?.use { it.readBytes() }
                        ?: error("读不到选中的图片")
                    // PNG 直接用原字节（不重编码，画质无损）；其它格式交给平台层转 PNG
                    //（手机是 BitmapFactory / 电脑 ImageIO，都先降采样到长边 ≤ 4096 免得 OOM）
                    val png = if (PngMetadata.isPng(sourceBytes)) {
                        sourceBytes
                    } else {
                        platform.images.toPng(sourceBytes) ?: error("这张图片解不开")
                    }
                    val embedded = PngMetadata.embedText(png, json) ?: error("这张图片不是有效的 PNG")
                    platform.openOutput(targetRef)?.use { out ->
                        out.write(embedded)
                        out.flush()
                    } ?: error("打不开目标文件")
                }.isSuccess
            }
            if (ok) {
                toast(rt("status.backupExportedToImage"))
            } else {
                toast(rt("status.backupExportFailed"), isError = true)
            }
        }
    }

    /** 当前要备份的内容。 */
    private fun currentBundle(): BackupCodec.Bundle = BackupCodec.Bundle(
        settings = settings,
        params = params,
        styleLibrary = styleLibrary,
        charCaptions = extras.charCaptions,
        // 漫画那一节与角色分区**分别存放**（老版本读它是未知键，直接忽略）
        comic = BackupCodec.ComicBundle(
            settings = persistableComicSettings(extras),
            panels = extras.comicPanels,
        ),
    )

    /**
     * 导出备份：设置 + 当前生成参数 + 风格预设 + 角色分区，打成一份带版本号的 JSON。
     * **token 不在里面**（它单独存在 Keystore 里，永远不会被导出）。
     */
    fun exportBackup(ref: String) {
        viewModelScope.launch {
            status = rt("status.backupExporting")
            val text = BackupCodec.encode(
                bundle = currentBundle(),
                appVersion = platform.appVersion,
                exportedAt = java.time.Instant.now().toString(),
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    platform.openOutput(ref)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("打不开目标文件")
                }.isSuccess
            }
            if (ok) {
                toast(rt("status.backupExported"))
            } else {
                toast(rt("status.backupExportFailed"), isError = true)
            }
        }
    }

    /**
     * 导入备份：**先整体校验格式，再一次性套用**（格式不对就完全不动本地数据）。
     * 只覆盖备份里存在的小节。
     */
    fun importBackup(ref: String) {
        viewModelScope.launch {
            status = rt("status.backupImporting")
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    platform.openInput(ref)
                        ?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                toast(rt("status.backupImportFailed"), isError = true)
                return@launch
            }
            // 两种来源都认：①纯 JSON 文件；②**内嵌了备份的图片**（PNG 的 tEXt 块）
            val text = if (PngMetadata.isPng(bytes)) {
                val embedded = PngMetadata.extractBackupJson(bytes)
                if (embedded == null) {
                    toast(rt("status.backupImageNoData"), isError = true)
                    return@launch
                }
                embedded
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            val bundle = try {
                BackupCodec.decode(text)
            } catch (e: BackupCodec.BackupException) {
                toast(rf("status.backupInvalid", mapOf("reason" to (e.message ?: ""))), isError = true)
                return@launch
            }

            // 设置（含提示词优化/反推规则、LLM 与百度密钥、导演工具参数、保存目录…）：
            // **按字段覆盖** —— 备份里写了哪个键就盖哪个键，没写的保持本机现值，
            // 这样老备份缺的新字段不会被重置成默认值。
            val restoredSettings = bundle.settings?.let { decoded ->
                val overlay = bundle.settingsOverlay
                if (overlay == null) {
                    decoded
                } else {
                    AppSettings.fromJson(
                        BackupCodec.mergeSettingsJson(settings.toJson(), overlay),
                    )
                }
            }
            restoredSettings?.let { imported ->
                // 备份里可能带着旧文案（第三方 GPL 来源那段）：走**与启动同一条**内容迁移，
                // 否则"导入老备份"会把刚清掉的旧文案又带回用户设备（幂等，跑几次结果一样）。
                val restored = migrateLegacyRuleTexts(imported)
                settings = restored
                withContext(Dispatchers.IO) { storage.setSettings(restored) }
                // 自定义保存目录是 SAF 授权：换机/重装后那份授权不一定还在，重新握一次
                val folder = restored.imageOutputTreeUri.trim()
                if (folder.isNotEmpty() && !reacquireOutputFolder(folder)) {
                    status = rt("storage.folderSetNoPerm")
                }
            }
            bundle.params?.let { restored ->
                // 恢复备份是**整套替换**，属于"装载"不是"改动"，锁在这条路上让开
                // （挂起只包住这一句，紧跟着的落实盘不受影响）
                promptLocksSuspended = true
                try {
                    params = restored
                } finally {
                    promptLocksSuspended = false
                }
                withContext(Dispatchers.IO) { storage.setParams(restored) }
            }
            bundle.styleLibrary?.let { restored ->
                styleLibrary = restored
                withContext(Dispatchers.IO) { storage.setStylePresets(restored) }
            }
            bundle.charCaptions?.let { restored ->
                extras = extras.copy(charCaptions = restored)
                withContext(Dispatchers.IO) { storage.setCharacterPrompts(restored) }
            }
            // 漫画那一节：设置与分格一起恢复（缺省时不动本机的漫画数据）
            bundle.comic?.let { restored ->
                extras = extras.copy(
                    comicMode = restored.settings.mode,
                    comicPanels = restored.panels,
                    comicLayout = restored.settings.layout,
                    comicOrder = restored.settings.order,
                    comicStylePrompt = restored.settings.stylePrompt,
                    // ⚠️ 别漏了这一项：`setComicSettings` 会把 style 写到盘上，
                    // 但内存里的 extras 也得同步，否则**导入完当下界面显示的还是旧风格词**，
                    // 要重启才对上（之前就漏了它）。
                    comicStyle = restored.settings.style,
                    // 多页（狂暴模式）：老备份没有 pages → 空表 → ensureComicPages 补一页
                    comicPages = restored.settings.pages,
                    comicPageIndex = restored.settings.pageIndex,
                    comicBerserk = restored.settings.berserk,
                    // ⚠️ 剧情也要同步（和上面 `comicStyle` 同一个坑：落盘了但内存没同步，
                    // 表现就是"导入备份后剧情框还是空的"）。
                    comicPlot = restored.settings.plot,
                ).ensureComicPages()
                comicModeIntent = extras.comicMode
                withContext(Dispatchers.IO) {
                    storage.setComicPanels(restored.panels)
                    storage.setComicSettings(restored.settings)
                }
            }
            // 批量张数等运行时状态本来就不会进备份，这里不用再动
            val summary = bundle.summary
            val sections = buildList {
                if (summary.hasSettings) add(rt("backup.section.settings"))
                if (summary.hasParams) add(rt("backup.section.params"))
                if (summary.stylePresets > 0 || summary.styleGroups > 0) {
                    add(
                        rf(
                            "backup.section.styles",
                            mapOf(
                                "groups" to summary.styleGroups,
                                "presets" to summary.stylePresets,
                            ),
                        ),
                    )
                }
                if (summary.characters > 0) {
                    add(rf("backup.section.characters", mapOf("count" to summary.characters)))
                }
                if (summary.comicPanels > 0 || summary.comicPages > 0) {
                    // 多页漫画只报"本页几格"是在骗人，所以页数 >1 时换一条文案
                    if (summary.comicPages > 1) {
                        add(
                            rf(
                                "backup.section.comic.pages",
                                mapOf("pages" to summary.comicPages, "count" to summary.comicPanels),
                            ),
                        )
                    } else {
                        add(rf("backup.section.comic", mapOf("count" to summary.comicPanels)))
                    }
                }
            }
            toast(rf("status.backupImported", mapOf("sections" to sections.joinToString("、"))))
        }
    }

    /**
     * 把设置里存的「自定义保存目录」URI 的持久化授权重新握一次。
     *
     * 备份里存的是 tree URI 字符串；换设备 / 卸载重装 / 清数据之后系统那份授权不一定还在，
     * 不重新 `takePersistableUriPermission` 的话设置里显示着目录、但图不再另存过去。
     *
     * @return true = 授权可用
     */
    private fun reacquireOutputFolder(uriString: String): Boolean =
        platform.takePersistablePermission(uriString)

    // -------------------------------------------------------------- 排队

    /**
     * 生成中再点一次主按钮 = **按此刻的参数排一张**。
     * 排队时把参数/预设/张数都拍成快照，之后改参数不会影响已经排好的那些。
     */
    fun queueCurrentParams() {
        if (!extras.comicMode && params.positivePrompt.trim().isEmpty()) {
            status = rt("error.positiveRequired")
            return
        }
        val job = QueuedJob(
            params = params,
            extras = extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives()),
            total = batchCount.coerceIn(1, 999),
            intervalSeconds = if (batchCount > 1) batchIntervalSeconds.coerceIn(0, 3600) else 0,
        )
        queuedJobs = queuedJobs + job
        status = rf("status.queued", mapOf("count" to queuedJobs.size))
    }

    /** 清空排队（长按主按钮时连同当前生成一起取消）。 */
    fun clearQueue() {
        if (queuedJobs.isEmpty()) return
        queuedJobs = emptyList()
        status = rt("status.queueCleared")
    }

    /** 一次生成/重做期间的预览管道。 */
    private class PreviewPipeline(val channel: Channel<NaiPreview>, val consumer: Job)

    private fun openPreviewPipeline(): PreviewPipeline {
        val channel = Channel<NaiPreview>(Channel.CONFLATED)
        previewChannel = channel
        val consumer = viewModelScope.launch {
            for (preview in channel) {
                generationPreviewProgress = preview.progress.coerceIn(0f, 1f)
                generationPreviewStep = preview.currentStep
                generationPreviewTotalSteps = preview.totalSteps
                // final 帧不需要解码成预览位图 —— 它马上要以文件形式显示
                if (!preview.isFinal) {
                    val bitmap = withContext(Dispatchers.Default) {
                        images.decodeSampled(preview.image, PREVIEW_MAX_DIMENSION)
                    }
                    // 状态层不再持有 Android 位图：交给平台层转成 Compose 的 ImageBitmap
                    // （界面直接就能画，两边通用）
                    bitmap?.let { images.toComposeImage(it) }?.let { generationPreview = it }
                }
            }
        }
        return PreviewPipeline(channel, consumer)
    }

    private fun closePreviewPipeline(pipeline: PreviewPipeline) {
        previewChannel = null
        pipeline.channel.close()
        pipeline.consumer.cancel()
    }

    // ------------------------------------------------------------ 遮罩 / 重做

    /**
     * 铅笔开关。打开时给当前这张图建一张草图（换过图就重开一张）；
     * 关掉时**退出模式并把涂的遮罩、聚焦框一起删掉**（用户 2026-09-18 深夜：
     * 「退出遮罩模式时删除遮罩和聚焦框」）。
     *
     * ⚠️ 早先这里是"只退出、遮罩保留"（退出后图上还留着涂过的区域）—— 那一版的口径作废。
     * 现在退出 = 清空，所以下次进来是从白纸开始；程序内部**不是**走这个函数的退出路径
     * （放大 / 导演工具 / [resetMaskState]）不受影响，重绘请求也只在模式内发出，不会误清。
     */
    fun toggleMaskMode() {
        if (maskMode) {
            maskMode = false
            endMaskStroke()
            // 顺序：先把草图清空、再把聚焦框扔掉，最后统一给"已退出"的状态文案
            // （这两步各自也会写 status，会被下面这行覆盖）
            clearMask()
            clearMaskFocus()
            status = rt("mask.modeOff")
            return
        }
        // 工作图：图生图导入的图优先，否则最近生成的那张 —— 导入的图也能涂遮罩
        val sourcePath = workImagePath
        if (sourcePath == null) {
            status = rt("mask.needImage")
            return
        }
        // ⚠️ 用户 2026-09-26：「遮罩模式和画布编辑器可以同时打开，UI 会遮盖」→**只能开一个** ✓。
        // 反方向（进编辑器时关遮罩）在 `openCanvasEditor()` 里已经有了 ✓，这里补上这一半：
        // 进遮罩就把编辑器收掉（**丢弃这次编辑**，和编辑器自己的返回/叉一致 —— 想留改动得点「完成」✓）。
        if (canvasEditorOpen) closeCanvasEditor(commit = false)
        ensureSketch(sourcePath)
        if (maskSketch == null) {
            status = rt("mask.needImage")
            return
        }
        maskMode = true
        status = if (maskPaintedPercent > 0) {
            rf("mask.modeOnPainted", mapOf("percent" to maskPaintedPercent))
        } else {
            rt("mask.modeOn")
        }
    }

    /** 笔刷直径（原图像素，1..350）。 */
    fun setMaskBrush(pixels: Int) {
        maskBrushPixels = pixels.coerceIn(MASK_BRUSH_MIN_PIXELS, MASK_BRUSH_MAX_PIXELS)
    }

    /** 笔尖形状：true = 圆形（默认），false = 方形。 */
    fun setMaskBrushRound(round: Boolean) {
        maskRoundBrush = round
    }

    /** 切画笔 / 橡皮（函数名刻意和属性 setter 区分开，避免 JVM 签名冲突）。 */
    fun setMaskEraser(erasing: Boolean) {
        maskErasing = erasing
    }

    /** 手指按下：开一笔（笔刷参数在这一笔内固定）。 */
    fun startMaskStroke() {
        val sketch = maskSketch ?: return
        sketch.beginStroke(
            erasing = maskErasing,
            square = !maskRoundBrush,
            brushPixels = maskBrushPixels,
        )
    }

    /** 手指移动：把这一段落上去 —— **涂上即生效**，没有"确定"这一步。 */
    fun paintMask(point: MaskPoint) {
        val sketch = maskSketch ?: return
        sketch.extend(point)
        syncMaskState(sketch)
    }

    /** 手指抬起：把这一笔提交进撤销历史。 */
    fun endMaskStroke() {
        val sketch = maskSketch ?: return
        sketch.endStroke()
        syncMaskState(sketch)
    }

    fun undoMask() {
        val sketch = maskSketch ?: return
        if (sketch.undo()) {
            syncMaskState(sketch)
            status = rf("mask.undoDone", mapOf("percent" to maskPaintedPercent))
        }
    }

    /** 清空遮罩（保留在遮罩模式里，可以接着涂）。 */
    fun clearMask() {
        val sketch = maskSketch
        if (sketch == null) {
            maskPaintedPercent = 0
            maskVersion++
            return
        }
        sketch.clear()
        syncMaskState(sketch)
        status = rt("mask.cleared")
    }

    /**
     * 界面直接画的笔迹（矢量）：已提交的 + 手指正在画的那一笔。
     * 配合 [maskVersion] 使用 —— 每次涂改都会让版本号 +1，界面据此重画描边。
     */
    fun maskStrokesSnapshot(): List<MaskStroke> = maskSketch?.visibleStrokes() ?: emptyList()

    /** 给某个文件建遮罩草图（网格 = 该图像的像素尺寸，导入的图也走这里）。 */
    private fun ensureSketch(path: String) {
        if (maskSketch != null && maskSourcePath == path) return
        val dims = MaskCodec.imageSize(images, path)
        if (dims == null) {
            maskSketch = null
            return
        }
        maskSketch = MaskSketch(maxOf(1, dims.first), maxOf(1, dims.second))
        maskSourcePath = path
        maskPaintedPercent = 0
        maskCanUndo = false
        maskVersion++
    }

    private fun syncMaskState(sketch: MaskSketch) {
        maskPaintedPercent = if (sketch.isEmpty) {
            0
        } else {
            // 至少显示 1%，免得"涂了一小块但显示 0%"让人以为没生效
            maxOf(1, Math.round(sketch.selectedCount.toFloat() * 100f / (sketch.width.toFloat() * sketch.height.toFloat())))
        }
        maskCanUndo = sketch.canUndo
        maskVersion++
    }

    // ------------------------------------------------- 画布编辑器（运行条那颗铅笔）

    /**
     * **画布编辑器**：官方 Canvas 那一套（画笔/橡皮/油漆桶/选择/套索/吸管/模糊/图章 +
     * HSV 调整 + 调整画布大小）的落地处。
     *
     * 和「遮罩」是**两件独立的事**（用户 2026-09-16 明确）：
     *  · 遮罩（运行条那颗 ⛶）= 只涂一张黑白选区，告诉服务端重绘哪一块，**图本身不动**；
     *  · 画布编辑器（铅笔）= 改**图本身的像素**，点「完成」之后这张图变成图生图底图。
     *
     * 会话期间像素住在内存里（[ImageEditSession]），完成时编码成一张**派生图**落盘，
     * 之后照旧走 `workImagePath` 那条管线（局部重绘 / 图生图都认路径）。
     */
    var canvasEditorOpen by mutableStateOf(false)
        private set

    /** 当前工具（官方那 8 个，顺序 [CanvasTool] 里定死）。 */
    var canvasTool by mutableStateOf(CanvasTool.DRAW)
        private set

    /** 笔刷直径（**原图像素**，和遮罩那套各自独立）。 */
    var canvasBrushPixels by mutableIntStateOf(ImageEditOps.DEFAULT_BRUSH_PIXELS)
        private set

    /** 笔尖形状：圆 / 软圆 / 方。 */
    var canvasBrushShape by mutableStateOf(BrushShape.ROUND)
        private set

    /** 当前颜色（ARGB）。 */
    var canvasColor by mutableIntStateOf(DEFAULT_CANVAS_COLOR)
        private set

    /** 油漆桶的容忍度（0..255，官方那个滑杆）。 */
    var canvasFillTolerance by mutableIntStateOf(DEFAULT_FILL_TOLERANCE)
        private set

    /** 模糊强度（0..100）。 */
    var canvasBlurIntensity by mutableIntStateOf(DEFAULT_BLUR_INTENSITY)
        private set

    /** 仿制图章的源点（图片像素坐标）+ 有没有定过。 */
    var canvasCloneSourceX by mutableIntStateOf(0)
        private set
    var canvasCloneSourceY by mutableIntStateOf(0)
        private set
    var canvasCloneReady by mutableStateOf(false)
        private set

    /** 当前**选区**（矩形/套索，归一化坐标）；null = 没有框。 */
    var canvasSelection by mutableStateOf<SelectionShape?>(null)
        private set

    /** 选区里的像素**已经抬起来了**（可以拖/缩/转，还没落回）。 */
    var canvasSelectionFloating by mutableStateOf(false)
        private set

    /** 浮动块本体（界面算缩放锚点、旋转角要用）。 */
    val canvasFloating: FloatingSelection? get() = canvasSession?.floatingSelection

    var canvasCanUndo by mutableStateOf(false)
        private set
    var canvasCanRedo by mutableStateOf(false)
        private set

    /**
     * 这次编辑会话**有没有真的改过像素**。
     *
     * ⚠️ 判据只有**一个**：[com.kallan.naistudio.models.ImageEditSession.hasChanges]
     * （"发生过编辑操作"这个布尔，不做逐像素比对）。编辑器顶栏的「完成」和
     * [closeCanvasEditor]`(commit = true)` 都只读它 —— 谁都不许再写第二份判断。
     *
     * 只是个转发 getter（不是 Compose 状态）：调用点都在**点击那一刻**读一次，
     * 不参与重组，所以不用再维护一个会和会话走散的镜像字段。
     */
    val canvasHasChanges: Boolean get() = canvasSession?.hasChanges == true

    /** 画布尺寸（编辑器标题栏上显示 `832×1216`）。 */
    var canvasWidth by mutableIntStateOf(0)
        private set
    var canvasHeight by mutableIntStateOf(0)
        private set

    /** 像素版本号：每改一次 +1，界面据此重建显示用的位图。 */
    var canvasRevision by mutableIntStateOf(0)
        private set

    private var canvasSession: ImageEditSession? = null

    /** 打开编辑器时的**源图**路径；null = 空白画布（官方 Paint New Image）。 */
    private var canvasSourcePath: String? = null

    /** 进编辑器那一刻的像素（「重置」要回到它）。 */
    private var canvasOriginalPixels: IntArray? = null

    private var canvasBitmap: ImageBitmap? = null
    private var canvasBitmapRevision = -1

    /** 浮动选区那块的显示位图（和主画布同一个版本号口径）。 */
    private var canvasFloatBitmap: ImageBitmap? = null
    private var canvasFloatBitmapRevision = -1

    /** 有没有正在画的一笔（指针还按着）。 */
    val canvasDrawing: Boolean get() = canvasSession?.isDrawing == true

    // ---- 选区（三期：矩形 / 套索，抬起来能移动 / 缩放 / 旋转）----

    /** 画一个框（还没抬像素）。拖出来的过程中会反复调它。 */
    fun updateCanvasSelection(shape: SelectionShape?) {
        // 换框之前先把上一块落回（没动过的会被丢掉，见 session.beginSelection）
        if (canvasSelectionFloating) commitCanvasSelection()
        canvasSelection = shape
        // ⚠️ 选区**留在画面上**并且"只在选区内工作"（用户 2026-09-16 要求）：
        // 这里把形状告诉会话，之后所有画笔/油漆桶/模糊/图章都会被它裁掉外面的部分。
        // 换工具**不清**它 —— 用户就是要在选好之后换个画笔进去画。
        canvasSession?.setSelectionClip(shape)
    }

    /** 清掉选区（Esc / 换到选择类工具以外又想重新框时用）。 */
    fun clearCanvasSelection() {
        if (canvasSelectionFloating) commitCanvasSelection()
        canvasSelection = null
        canvasSession?.setSelectionClip(null)
    }

    /**
     * **退格 / 删除键**：把选区里的图像删掉（清成透明）。
     *
     * 这是"挖透明通道"最直接的入口 —— 生成回来之后可以直接当底图再发重绘，见 `docs/25` §36。
     */
    fun eraseCanvasSelection() {
        val session = canvasSession ?: return
        val shape = canvasSelection ?: return
        if (session.clearSelectionRegion(shape)) {
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
            status = rt("canvas.selectionDeleted")
        }
    }

    /** 把选区里的像素抬起来（第一次移动/缩放/旋转时调）。 */
    fun liftCanvasSelection(): Boolean {
        val session = canvasSession ?: return false
        if (canvasSelectionFloating) return true
        val shape = canvasSelection ?: return false
        if (!session.beginSelection(shape)) return false
        canvasSelectionFloating = true
        cancelCanvasBitmapCache()
        canvasRevision++
        return true
    }

    fun translateCanvasSelection(deltaX: Float, deltaY: Float) {
        val session = canvasSession ?: return
        if (!canvasSelectionFloating) return
        session.translateSelection(deltaX, deltaY)
        cancelCanvasBitmapCache()
        canvasRevision++
    }

    fun scaleCanvasSelection(factorX: Float, factorY: Float, anchorX: Float, anchorY: Float) {
        val session = canvasSession ?: return
        if (!canvasSelectionFloating) return
        session.scaleSelection(factorX, factorY, anchorX, anchorY)
        cancelCanvasBitmapCache()
        canvasRevision++
    }

    fun rotateCanvasSelection(deltaDegrees: Float) {
        val session = canvasSession ?: return
        if (!canvasSelectionFloating) return
        session.rotateSelection(deltaDegrees)
        cancelCanvasBitmapCache()
        canvasRevision++
    }

    /**
     * **落回**（换工具 / 回车 / 点框外都会调）。
     *
     * 落回之后选区框要**跟着挪到新位置** —— 否则用户看到的是"图动完了、框还留在原地"，
     * 想接着微调就得重新框一次。所以这里按"积木干了多少"把框整体平移。
     */
    fun commitCanvasSelection() {
        val session = canvasSession ?: return
        val floating = session.floatingSelection
        if (floating == null) {
            canvasSelectionFloating = false
            return
        }
        val dx = floating.dx
        val dy = floating.dy
        val shape = canvasSelection
        if (session.commitSelection()) {
            if (shape != null && (dx != 0f || dy != 0f)) {
                val width = canvasWidth.coerceAtLeast(1)
                val height = canvasHeight.coerceAtLeast(1)
                val moved = SelectionOps.shiftedShape(shape, dx / width, dy / height)
                canvasSelection = moved
                // 框挪了 → 裁剪区也得跟着挪（不然"只在选区内工作"还停在老地方）
                session.setSelectionClip(moved)
            }
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
        }
        canvasSelectionFloating = false
    }

    /** 「取消」：像素回到抬起前，框留着（用户多半是想重来一次）。 */
    fun cancelCanvasSelection() {
        val session = canvasSession ?: return
        if (session.cancelSelection()) {
            cancelCanvasBitmapCache()
            canvasRevision++
        }
        canvasSelectionFloating = false
    }

    /** 框的像素尺寸（参数行上显示 `120×80`）。 */
    fun canvasSelectionSize(): Pair<Int, Int> {
        val shape = canvasSelection ?: return 0 to 0
        return SelectionOps.sizeOf(shape, canvasWidth, canvasHeight)
    }

    /**
     * **画布编辑器里那块选区，如果拿去局部重绘，会被自动算成多大**（用户 2026-09-26 口径）。
     *
     * = `InpaintSize.forRect(选区的原图像素宽, 原图像素高)`：等比 → 面积顶到 1024×1024
     * 以下的最大值 → 两边对齐 64（宽高可超 1024、总像素不能超 ✓）。
     *
     * ⚠️ 只用于**只读显示**（参数行上「局部重绘尺寸 1792×576（3:1 ✓）」）——
     * 画布编辑器本身不生成（它是像素编辑器：选区只用来移动 / 缩放 / 裁剪笔刷），
     * 所以这里**只是**把"这块区域按新口径会算成多大"告诉用户，没有任何手改入口 ✗。
     */
    fun canvasSelectionInpaintSize(): Pair<Int, Int> {
        val (w, h) = canvasSelectionSize()
        if (w <= 0 || h <= 0) return 0 to 0
        return InpaintSize.forRect(w.toFloat(), h.toFloat())
    }

    /**
     * 「**按住空格 + 左键拖动 = 平移画布**」（用户 2026-09-16 要求）。
     *
     * 住在 `AppState` 而不是编辑器内部：按键是**窗口层**收的（走 `EditorShortcuts` 信箱），
     * 而用这个标记的是画布的指针处理 —— 两边隔着一层，放这儿是两边都能够到的最短路径。
     * （另外中键拖动一直都能平移，这个是给没中键/嫌中键别扭的人。）
     */
    var canvasSpacePan by mutableStateOf(false)
        private set

    /** 空格按下/抬起（编辑器关掉时也会被复位成 false）。 */
    fun holdCanvasSpace(held: Boolean) {
        if (canvasSpacePan == held) return
        canvasSpacePan = held
    }

    /**
     * **Ctrl 按着没按着** —— 「**图画要按住 ctrl 才能拖动**」（用户 2026-09-22 ✓）。
     *
     * 与 [canvasSpacePan] **同一个来源、同一个理由**：Ctrl 的按下 / 抬起也是**窗口层**收的
     *（`Main.kt` 的 `onPreviewKeyEvent` ✓ —— 只记状态、**不吃掉** ⇒ `Ctrl+Z` 那些组合键一个都不受影响 ✓），
     * 而用它的地方是**画布的指针处理** ✓ —— 两边隔着一层，放这儿是两边都够得着的最短路径 ✓。
     *
     * ⚠️ 为什么不用指针事件自带的 `event.keyboardModifiers.isCtrlPressed` ✗：
     *    实测**按着 Ctrl 拖也判不出来**（用户 2026-09-22 报的「按住 ctrl 不能拖动」✓）——
     *    键盘那条路是**已经在用**的（空格 + 拖动每天都用 ✓）⇒ 换成它 ✓。
     *    指针那份留着做兜底（两处取"或" ✓）。
     *
     * ⚠️ 已知小缺口（与 [canvasSpacePan] 同款 ✓）：**在窗口失焦时松开 Ctrl** ⇒ 这个标记会停在
     *    true 直到下一次按下 / 抬起 ✓（没做窗口失焦复位 ✓）。影响很小：拖一下图画就自动复位 ✓，
     *    不会卡成不能用 ✓。
     */
    var canvasCtrlDown by mutableStateOf(false)
        private set

    /** Ctrl 按下/抬起。 */
    fun holdCanvasCtrl(held: Boolean) {
        if (canvasCtrlDown == held) return
        canvasCtrlDown = held
    }

    /**
     * 编辑器里**有没有文本输入框正拿着焦点**（笔刷大小、调整画布的宽高、色值输入）。
     *
     * 为什么要这个：键位是从**窗口层**收的（`EditorShortcuts`），一旦改成"抢在焦点节点之前"
     * 收键（`onPreviewKeyEvent`，为了不漏掉空格的抬起），字母键就会把输入框里的打字抢走。
     * 所以输入框拿到焦点时，编辑器**一个键都不吃**（见 `onCanvasShortcut` 开头）。
     *
     * ⚠️ 2026-09-22 起它只是 [textInputFocused] 的**画布那一份**：真正的闸门是统一标记，
     * 这个字段留着是因为编辑器的字母键只关心"画布里的框"。
     */
    var canvasTextFocused by mutableStateOf(false)
        private set

    fun reportCanvasTextFocus(focused: Boolean) {
        // ⚠️ 刻意**不**做"值没变就早退"：写同一个布尔不会触发重组（`mutableStateOf` 走结构相等），
        //    但早退会让统一标记那条 key 在"值碰巧已经对了"的边角里漏删 —— 宁可多写一次。
        canvasTextFocused = focused
        reportTextInputFocus(CANVAS_TEXT_INPUT_KEY, focused)
    }

    /**
     * **统一标记：现在有没有任何一个文本输入框拿着焦点**（用户 2026-09-22：「去除空格的焦点击功能」）。
     *
     * ## 它是给谁用的
     *
     * 入口 `Main.kt` 的 `onPreviewKeyEvent`：**空格在没有输入框拿着焦点时被就地消费掉**，
     * 于是焦点控件（`clickable` 把 Space 的按下当 Press、抬起当 onClick）再也收不到它 ——
     * 「空格只剩按住 = 拖图」。输入框拿着焦点时**不消费**，空格照旧是打字。
     *
     * ## 为什么是"带 key 的登记"而不是一个布尔
     *
     * 焦点在框与框之间搬家时，旧框的 `onFocusChanged(false)` 有可能**后**到（Compose 不保证顺序），
     * 一个布尔就会被后到的 false 抹成"没人聚焦"。这里按 **key 登记集合**：每个框一个稳定 key，
     * 谁失焦只删自己那条 —— 顺序怎么来都不会串。
     *
     * ## 谁在往里写（见各处 `Modifier.reportTextInputFocus(state, key)`）
     *
     * 提示词三条（`PromptTextField`）、角色 / 漫画剧情 / 漫画风格（`CharacterEditorPanel`）、
     * 参数面板的「文件名前缀」、导演工具提示词、临时图库抽屉的搜索框、图库搜索框、
     * 图鉴搜索框、设置 / 账号 / 预设那一堆文本框，以及画布编辑器自己的框。
     *
     * ⚠️ **以后新加文本框一定要登记**：漏登记的框里按空格会打不出空格（详见回报里的口径）。
     */
    var textInputFocused by mutableStateOf(false)
        private set

    /** 谁现在拿着焦点（key 见各处调用点）。只在焦点回调里读写，不参与重组。 */
    private val textInputFocusKeys = mutableSetOf<String>()

    /**
     * 某个文本输入框获得 / 失去焦点。
     *
     * @param key 这个框的稳定标识（同一个框永远传同一个；不同框不能重名 —— 重名会让
     *   "一个框失焦"把"另一个框还在聚焦"的那条一起删掉）。
     */
    fun reportTextInputFocus(key: String, focused: Boolean) {
        if (focused) textInputFocusKeys.add(key) else textInputFocusKeys.remove(key)
        val anyFocused = textInputFocusKeys.isNotEmpty()
        if (textInputFocused != anyFocused) textInputFocused = anyFocused
        // 焦点进了输入框就当作"不在平移模式"：否则空格会被当成平移、又打不出空格
        if (focused) canvasSpacePan = false
    }

    /** 画布长边上限：再大就先等比降下来（4K 图的 ARGB 是 64 MB，笔刷还要按帧重建位图）。 */
    val canvasMaxLongSide: Int get() = CANVAS_MAX_LONG_SIDE

    /**
     * 打开画布编辑器。
     *
     * 有工作图就编辑它；**没有图就开一张空白画布**（尺寸取当前生成参数）——
     * 这是官方的 "Paint New Image"：画个草图 → 完成 → 当图生图底图。
     */
    fun openCanvasEditor() {
        if (canvasEditorOpen) return
        // 遮罩和画布是两件事：进编辑器先把遮罩涂画模式关掉（遮罩本身保留，图上还看得见）
        if (maskMode) toggleMaskMode()
        val session = runCatching { loadCanvasSession() }.getOrNull() ?: return
        // ⚠️ 第 ㉜a 批：`deferStrokeCommit` 那个开关**整条删掉了** ✗ ——
        // 笔现在**直接画在层表面上**（`ImageEditSession` ✓），所有会话（漫画页 + 这个画布编辑器）
        // 都是"写透"的 ✓；`CanvasEditor` 的显示读 `session.pixels`，而每颗笔尖之后都会回读同步 ✓，
        // 所以**画的时候照样看得见笔迹** ✓（不再需要那条"活笔画缓冲"的分支 ✓）。
        canvasSession = session
        canvasWidth = session.width
        canvasHeight = session.height
        canvasEditorOpen = true
        syncCanvasHistory()
        canvasRevision++
        status = rt("canvas.opened")
    }

    /**
     * 关闭编辑器。
     *
     * @param commit true = 把改动用成**底图**（写一张派生图并切过去）；false = 整个丢弃。
     *
     * ⚠️ **一个像素都没动过时，commit = true 也不落盘、不切底图**（2026-09-19 用户报
     * "什么都没改，点完成还是图生图，白花一次生成"）：没有改动却把当前图设成图生图底图，
     * 下一轮生成就会白跑一次图生图。判据见 [canvasHasChanges]，只有一处。
     */
    fun closeCanvasEditor(commit: Boolean) {
        val session = canvasSession
        canvasEditorOpen = false
        canvasSession = null
        canvasBitmap = null
        canvasBitmapRevision = -1
        canvasCanUndo = false
        canvasCanRedo = false
        // 关掉编辑器别把"空格按住"的状态留着 —— 下次进来会以为自己在平移模式
        canvasSpacePan = false
        // 图章源点、HSV 预览也一起清（都是这次会话里的东西）
        if (session != null) session.cancelHsv()
        clearCanvasCloneSource()
        // 走同一个入口：把统一标记（`textInputFocused`）里画布那条也一起清掉
        reportCanvasTextFocus(false)
        // 改过才提交；没改过就直接关掉（像素没变，没有东西需要落盘，也不该切底图）
        if (commit && session != null && session.hasChanges) commitCanvasSession(session)
        canvasSourcePath = null
        canvasOriginalPixels = null
        canvasWidth = 0
        canvasHeight = 0
    }

    /** 换工具（函数名刻意避开 `canvasTool` 的 JVM setter，见遮罩那套同名注释）。 */
    fun chooseCanvasTool(tool: CanvasTool) {
        canvasSession?.endStroke()
        // 离开选区类工具时把飘着的那块**落回** —— 不然切了工具它还悬在半空
        val leavingSelection = canvasTool != tool &&
            (canvasTool == CanvasTool.SELECT || canvasTool == CanvasTool.LASSO)
        if (leavingSelection) commitCanvasSelection()
        canvasTool = tool
        // ⚠️ 选区**不在这里清**（用户 2026-09-16 要求）：选好一块之后换成画笔进去画、
        // 或者换模糊/油漆桶接着处理，都是正常用法；选区会一直留着（并把操作裁在选区里），
        // 想取消就按 Esc 或重新框一个。
    }

    /** 笔刷直径（原图像素，1..600）。 */
    fun setCanvasBrush(pixels: Int) {
        canvasBrushPixels = ImageEditOps.normalizedBrushPixels(pixels)
    }

    /** 笔尖形状。 */
    fun chooseCanvasBrushShape(shape: BrushShape) {
        canvasBrushShape = shape
    }

    /** 当前颜色。 */
    fun chooseCanvasColor(color: Int) {
        canvasColor = color
    }

    /** 油漆桶的容忍度（0..255）。 */
    fun chooseCanvasFillTolerance(value: Int) {
        canvasFillTolerance = value.coerceIn(0, 255)
    }

    /** 模糊强度（0..100）。 */
    fun chooseCanvasBlurIntensity(value: Int) {
        canvasBlurIntensity = value.coerceIn(0, 100)
    }

    /**
     * 仿制图章的**源点**（图片像素坐标）。界面在 `Alt+点击` 时写它。
     *
     * 存的是"点下去那一刻的源点"，真正用的是**源点相对落笔点的偏移**
     * —— 在 [beginCanvasStroke] 里算好、整笔固定（和 Photoshop 一样：
     * 源点跟着笔走，但如果每段都重算偏移，笔一动源点也动，等于没偏移）。
     */
    fun setCanvasCloneSource(x: Int, y: Int) {
        canvasCloneSourceX = x
        canvasCloneSourceY = y
        canvasCloneReady = true
        status = rt("canvas.cloneSourceSet")
    }

    /** 清掉图章源点（换工具 / 关编辑器时）。 */
    private fun clearCanvasCloneSource() {
        canvasCloneReady = false
        canvasCloneSourceX = 0
        canvasCloneSourceY = 0
    }

    /** 油漆桶：在点到的位置灌一次（颜色 = 当前色，容忍度 = 当前容忍度）。 */
    fun fillCanvasAt(x: Int, y: Int) {
        val session = canvasSession ?: return
        if (session.fillAt(x, y, canvasColor, canvasFillTolerance)) {
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
            status = rt("canvas.filled")
        }
    }

    // ---- HSV 调整（对话框里实时预览；Apply 才算一步）----

    fun previewCanvasHsv(hue: Float, saturation: Float, value: Float) {
        val session = canvasSession ?: return
        session.previewHsv(hue, saturation, value)
        cancelCanvasBitmapCache()
        canvasRevision++
    }

    fun commitCanvasHsv() {
        val session = canvasSession ?: return
        session.commitHsv()
        cancelCanvasBitmapCache()
        canvasRevision++
        syncCanvasHistory()
        status = rt("canvas.hsvApplied")
    }

    fun cancelCanvasHsv() {
        val session = canvasSession ?: return
        session.cancelHsv()
        cancelCanvasBitmapCache()
        canvasRevision++
    }

    /**
     * 调整画布大小：内容按 ([left],[top]) 放到新画布上。
     *
     * 尺寸变了 → 画布/位图/历史都得跟着重置（会话里那步会清历史），
     * 这里把界面上显示的两个数同步过去。
     */
    fun applyCanvasResize(newWidth: Int, newHeight: Int, left: Int, top: Int) {
        val session = canvasSession ?: return
        session.resizeCanvas(newWidth, newHeight, left, top)
        canvasWidth = session.width
        canvasHeight = session.height
        cancelCanvasBitmapCache()
        canvasRevision++
        syncCanvasHistory()
        status = rt("canvas.resized")
    }

    /** 指针按下（图片像素坐标）：开一笔。 */
    fun beginCanvasStroke(x: Float, y: Float) {
        val session = canvasSession ?: return
        // 还飘着一块选区就先落回：不然画的东西会被"飘着的那块"盖住，看着像没画上
        if (canvasSelectionFloating) commitCanvasSelection()
        val mode = when (canvasTool) {
            CanvasTool.ERASE -> StrokeMode.ERASE
            CanvasTool.BLUR -> StrokeMode.BLUR
            CanvasTool.CLONE -> StrokeMode.CLONE
            else -> StrokeMode.PAINT
        }
        if (mode == StrokeMode.CLONE && !canvasCloneReady) {
            // 没定源点就刷等于把附近的像素原地盖回去（看着像没反应），明说一句
            status = rt("canvas.cloneNeedSource")
            return
        }
        session.beginStroke(
            x = x,
            y = y,
            spec = StrokeSpec(
                mode = mode,
                brushPixels = canvasBrushPixels,
                shape = canvasBrushShape,
                color = canvasColor,
                blurIntensity = canvasBlurIntensity,
                // 源偏移 = 源点 - 落笔点；整笔固定
                cloneOffsetX = if (mode == StrokeMode.CLONE) (canvasCloneSourceX - x).toInt() else 0,
                cloneOffsetY = if (mode == StrokeMode.CLONE) (canvasCloneSourceY - y).toInt() else 0,
            ),
        )
        canvasRevision++
    }

    fun canvasStrokeTo(x: Float, y: Float) {
        val session = canvasSession ?: return
        if (!session.isDrawing) return
        session.strokeTo(x, y)
        canvasRevision++
    }

    fun endCanvasStroke() {
        val session = canvasSession ?: return
        session.endStroke()
        syncCanvasHistory()
    }

    fun undoCanvas() {
        val session = canvasSession ?: return
        if (session.undo()) {
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
        }
    }

    fun redoCanvas() {
        val session = canvasSession ?: return
        if (session.redo()) {
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
        }
    }

    /** 「清除」：清空画布内容（**可撤销**，和「重置」不是一回事）。 */
    fun clearCanvas() {
        val session = canvasSession ?: return
        if (session.clear()) {
            cancelCanvasBitmapCache()
            canvasRevision++
            syncCanvasHistory()
            status = rt("canvas.cleared")
        }
    }

    /** 吸管：取当前像素的颜色。 */
    fun pickCanvasColor(x: Int, y: Int) {
        val session = canvasSession ?: return
        canvasColor = session.colorAt(x, y)
        // 吸管工具吸完就该能画了；其它工具下（Ctrl+点击快捷吸管）保持原工具不动
        if (canvasTool == CanvasTool.PICKER) canvasTool = CanvasTool.DRAW
        status = rf("canvas.colorPicked", mapOf("color" to colorHex(canvasColor)))
    }

    /** 「重置」：回到刚打开编辑器时的那张图（历史一并清掉）。 */
    fun resetCanvas() {
        val session = canvasSession ?: return
        val original = canvasOriginalPixels ?: return
        session.replaceAll(original)
        // 像素确实回到了进编辑器那一刻 → "改过"这个判断也一并回到"没改过"
        // （否则重置完点「完成」还会白走一次图生图）
        session.markClean()
        cancelCanvasBitmapCache()
        canvasRevision++
        syncCanvasHistory()
        status = rt("canvas.resetApplied")
    }

    /**
     * 编辑器显示用的位图（按 [canvasRevision] 缓存）。
     *
     * 每画一笔都要重建这么一张，所以**不能走 PNG 编解码**（`toComposeImage` 是
     * 编码成 PNG 再解回来，一张 832×1216 要几十毫秒，涂起来会拖手）；
     * 走的是 `composeImageOf`（直接从 ARGB 建图）。
     */
    fun canvasImage(): ImageBitmap? {
        val session = canvasSession ?: return null
        val cached = canvasBitmap
        if (cached != null && canvasBitmapRevision == canvasRevision) return cached
        val built = images.composeImageOf(session.pixels, session.width, session.height)
        canvasBitmap = built
        canvasBitmapRevision = canvasRevision
        return built
    }

    private fun cancelCanvasBitmapCache() {
        canvasBitmap = null
        canvasBitmapRevision = -1
        canvasFloatBitmap = null
        canvasFloatBitmapRevision = -1
    }

    /** 被抬起来那块像素的显示位图（按 [canvasRevision] 缓存，和主位图一个口径）。 */
    fun canvasFloatingImage(): ImageBitmap? {
        val session = canvasSession ?: return null
        val floating = session.floatingSelection ?: return null
        val cached = canvasFloatBitmap
        if (cached != null && canvasFloatBitmapRevision == canvasRevision) return cached
        val built = images.composeImageOf(floating.pixels, floating.patchWidth, floating.patchHeight)
        canvasFloatBitmap = built
        canvasFloatBitmapRevision = canvasRevision
        return built
    }

    private fun syncCanvasHistory() {
        val session = canvasSession
        canvasCanUndo = session?.canUndo == true
        canvasCanRedo = session?.canRedo == true
    }

    /** 读盘 / 开空白画布，做出这次编辑会话。解不开返回 null（外面按失败提示）。 */
    private fun loadCanvasSession(): ImageEditSession? {
        val source = workImagePath
        if (source == null) {
            val width = params.width.coerceAtLeast(64)
            val height = params.height.coerceAtLeast(64)
            val blank = IntArray(width * height) { ImageEditOps.TRANSPARENT }
            canvasSourcePath = null
            canvasOriginalPixels = blank.copyOf()
            return ImageEditSession(width, height, blank)
        }
        val decoded = images.decodeFull(source)
        if (decoded == null) {
            status = rt("canvas.loadFailed")
            return null
        }
        var image: NativeImage = decoded
        val longest = maxOf(decoded.width, decoded.height)
        if (longest > CANVAS_MAX_LONG_SIDE) {
            val ratio = CANVAS_MAX_LONG_SIDE.toFloat() / longest.toFloat()
            val scaled = images.scale(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                smooth = true,
            )
            if (scaled !== decoded) decoded.recycle()
            image = scaled
            status = rf("canvas.downscaled", mapOf("side" to CANVAS_MAX_LONG_SIDE))
        }
        val pixels = images.pixelsOf(image)
        image.recycle()
        canvasSourcePath = source
        canvasOriginalPixels = pixels.copyOf()
        return ImageEditSession(image.width, image.height, pixels)
    }

    /**
     * 把编辑结果落成一张**派生图**并切成底图。
     *
     * ## 为什么文件名是"按源图算"的确定值
     *
     * 判断"图上那张遮罩还有效"用的是 `maskSourcePath == workImagePath`。
     * 要是每次编辑都产生一个新文件名，用户涂过遮罩之后一进编辑器再出来，
     * 遮罩就会被当成"换图了"清掉。所以同一张源图永远落到同一个派生路径上（原地覆盖）。
     */
    private fun commitCanvasSession(session: ImageEditSession) {
        val source = canvasSourcePath
        val dir = java.io.File(platform.paths.filesDir, "canvas").apply { mkdirs() }
        val name = if (source == null) {
            "blank-${session.width}x${session.height}.png"
        } else {
            "edit-${source.hashCode().toUInt().toString(16)}.png"
        }
        val target = java.io.File(dir, name)
        val native = images.fromArgb(session.pixels, session.width, session.height)
        val bytes = images.pngBytes(native)
        native.recycle()
        val written = runCatching {
            platform.openOutput(target.absolutePath)?.use { out -> out.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            status = rt("canvas.saveFailed")
            return
        }
        val path = target.absolutePath
        // 尺寸没变的编辑：遮罩还能用（同一张图、同一套像素坐标），把它指到新文件上；
        // 尺寸变了（或本来就是空白画布）：遮罩的网格对不上，只能清掉。
        // ⚠️ 判据要拿**遮罩自己的草图尺寸**比，不能拿 `canvasWidth`：
        // 那个是会话尺寸、恒等于自己，等于没判。
        val keepMask = source != null &&
            maskSourcePath == source &&
            maskSketch?.width == session.width &&
            maskSketch?.height == session.height
        i2iOriginalPath = i2iOriginalPath ?: source
        i2iBasePath = path
        i2iDisplayPath = path
        i2iDisplayWidth = session.width
        i2iDisplayHeight = session.height
        if (keepMask) {
            maskSourcePath = path
        } else {
            resetMaskState()
        }
        status = rt("canvas.committed")
    }

    private fun colorHex(color: Int): String =
        "#%06X".format(color and 0xFFFFFF)

    /**
     * 运行条那一下该做什么。
     *
     * **只在这里判定一次**：按钮文案、按钮可用性、点击分派都从它取 —— 免得三处各写一份再飘。
     *
     * ⚠️ 这里踩过一个真坑（2026-09-16，用户报"画了框还是全图重绘"）：
     * 分派原先只看 `maskActive`，而 `maskActive = maskMode && maskVisible`，
     * `maskVisible` 又要求**涂过遮罩**（`maskPaintedPercent > 0`）——
     * 于是"**只画了聚焦框、框里没涂**"（官方口径里完全合法：整框重绘）会掉到
     * `img2imgActive` / `generate()` 那条路上去：**发出去的是文生图/图生图**，
     * 当然"全图重绘"，而且日志里连一条 `Inpaint start:` 都不会有。
     */
    enum class RunAction { INPAINT, IMG2IMG, GENERATE }

    // ------------------------------------------------------------------
    // **提示词词典**（用户 2026-09-24：「补充词典库」✓）—— 见 `models/TagDictionary.kt` ✓。
    //
    // ⚠️ **只读** ✗（启动时加载一次 ✓，界面拿它出候选 ✓，没有任何写入口 ✓）；
    // ⚠️ 加载失败 = **空表** ✓（补全不出现 ✓）——**不是**崩溃、也**不是**静默写坏什么东西 ✓。
    // ------------------------------------------------------------------

    /** 词典（39,577 条 ✓；加载完成前是空的 ✓）。 */
    var tagDictionary by mutableStateOf<List<TagDictionary.Entry>>(emptyList())
        private set

    /**
     * **按已经打出来的那一截给候选** ✓（界面每次重组调一次 ✓）。
     *
     * ⚠️ 一次调用是**一趟线性扫描**（39,577 条 ✓，几十微秒 ✓）—— 只在输入框那一小块重组时跑 ✓，
     * 不进 `derivedStateOf` 也不需要缓存 ✓（缓存反而要处理失效 ✓，不划算 ✓）。
     */
    fun tagSuggestions(query: String, limit: Int = 8): List<TagDictionary.Entry> =
        if (tagDictionary.isEmpty()) emptyList() else TagDictionary.suggest(tagDictionary, query, limit)

    val runAction: RunAction
        get() = when {            // 涂过遮罩 → 重绘；**只有聚焦框、一点没涂** → 也是重绘（整框重绘）
            maskActive || (maskMode && maskFocusRect != null) -> RunAction.INPAINT
            img2imgActive -> RunAction.IMG2IMG
            else -> RunAction.GENERATE
        }

    // ------------------------------------------------------------------
    // **出图积分预测**（用户 2026-09-24：「给双端加上积分预测功能，显示在生成按钮上，
    //   显示 [数字] 闪电图标」✓）—— 公式在 `models/AnlasCost.kt` ✓（照参考仓库抄的 ✓）。
    //
    // ⚠️ 这一块**只读** ✓（不改任何状态 ✓）：界面拿它算按钮上那个数字 ✓。
    //    算一次是几十次浮点运算 ✓，随便重组都无所谓 ✓（不用缓存 ✓）。
    // ------------------------------------------------------------------

    /** 现在是不是 Opus 档 ✓（免费额度那条要它 ✓）。 */
    private val opusNow: Boolean
        get() = account.tierLevel == AnlasCost.OPUS_TIER

    /** V5 的免费池透支了没 ✓（透支就不再抵那一张 ✓）。 */
    private val opusQuotaExhaustedNow: Boolean
        get() = account.opusUsage?.isNegative == true

    /**
     * **这次按主按钮大概要几点** ✓（`null` = 算不出来 / 现在不该显示 ✓）。
     *
     * 走的档位与 `runAction` **同一处判定** ✓（按钮上写着重做、算的却是文生图那种自相矛盾 ✗ 不会发生 ✓）：
     *  · 无限画布那一档：按**当前聚焦框**那套尺寸算 ✓（框还没拖 ⇒ `null` ✓ 不显示 ✓）；
     *  · 局部重绘：尺寸 = 遮罩 / 聚焦框算出来的请求尺寸 ✓（拿不到就用整图折中 ✓）；
     *  · 图生图 / 文生图：就是当前的宽高 ✓。
     */
    val runAnlasEstimate: Int?
        get() {
            if (!hasToken) return null
            if (canvasMode == 1) {
                val plan = infiniteFramePlan()?.second ?: return null
                val cost = AnlasCost.estimate(
                    width = plan.requestW,
                    height = plan.requestH,
                    steps = params.steps,
                    model = settings.inpaintModel,
                    smea = params.smea,
                    smeaDyn = params.smeaDyn,
                    strength = settings.inpaintStrength,
                    isOpus = opusNow,
                    opusQuotaExhausted = opusQuotaExhaustedNow,
                )
                return cost.takeIf { it != AnlasCost.INVALID }
            }
            val action = runAction
            val w: Int
            val h: Int
            val model: String
            val strength: Double
            when (action) {
                RunAction.INPAINT -> {
                    // 遮罩模式下的请求尺寸：优先用**聚焦框**那一套（`maskFocusPlan` ✓ 和真正发出去的
                    // 是同一个数 ✓）；没框就退回"按涂到的范围算"的那个数 ✓
                    //（`InpaintSize.forRect` ✓ —— 与参数行上显示的那个口径同一处 ✓）。
                    val plan = maskFocusPlan()
                    val (fw, fh) = maskFocusSize()
                    val computed = if (fw > 0 && fh > 0) {
                        InpaintSize.forRect(fw.toFloat(), fh.toFloat())
                    } else {
                        null
                    }
                    w = plan?.requestW ?: computed?.first ?: workImageWidth
                    h = plan?.requestH ?: computed?.second ?: workImageHeight
                    model = settings.inpaintModel
                    strength = settings.inpaintStrength
                }
                RunAction.IMG2IMG -> {
                    w = i2iDisplayWidth
                    h = i2iDisplayHeight
                    model = params.model
                    strength = settings.i2iStrength
                }
                RunAction.GENERATE -> {
                    w = params.width
                    h = params.height
                    model = params.model
                    strength = 1.0
                }
            }
            if (w <= 0 || h <= 0) return null
            val cost = AnlasCost.estimate(
                width = w,
                height = h,
                steps = params.steps,
                model = model,
                smea = params.smea,
                smeaDyn = params.smeaDyn,
                strength = strength,
                isOpus = opusNow,
                opusQuotaExhausted = opusQuotaExhaustedNow,
            )
            return cost.takeIf { it != AnlasCost.INVALID }
        }

    /**
     * 运行条上那个按钮的入口：
     *  · 生成中 → 排队
     *  · 局部重绘（有遮罩**或有聚焦框**）→ 「重做」
     *  · 图生图模式（导入了图片）→ 「使用当前图片图生图」
     *  · 否则 → 文生图
     */
    fun generateOrInpaint() {
        // ⚠️ **无限画布那一档排在最前** ✓（用户 2026-09-22 点的单 ✓）：
        //    这一档里"遮罩开没开 / 有没有导入图"很可能还留着**上一次**的旧值 ✓（用户是从普通模式切过来的 ✓），
        //    按它们分派会发出一次文生图 / 图生图 ✗ —— 跟"按框拓展"半点关系都没有 ✗
        //    （点了"拓展生成"却重画一张整图，正是这类 bug 最难查的样子 ✓）。
        if (canvasMode == 1) {
            runInfiniteFrame()
            return
        }
        if (canvasMode == 2) {
            // Generate storyboard panels, regardless of stale focus or img2img state.
            if (busy) queueCurrentParams() else if (gatewayAddressReady()) generate()
            return
        }
        if (busy) {
            queueCurrentParams()
            return
        }
        when (runAction) {
            // ⚠️ 第三方网关（NAI Gate）**没开局部重绘 / 图生图** ⇒ 先拦、给一句人话，
            //    别让用户等到网关回一个 4xx 才知道（见 `gatewayAllows` 与 `NaiApi` 尾部说明）。
            //    地址没填也先拦住（否则会回落到官方地址，见 `gatewayAddressReady`）。
            RunAction.INPAINT -> if (gatewayAddressReady() && gatewayAllows("infill")) inpaintRegion()
            RunAction.IMG2IMG -> if (gatewayAddressReady() && gatewayAllows("img2img")) runImg2Img()
            RunAction.GENERATE -> if (gatewayAddressReady()) generate()
        }
    }

    // ----------------------------------------------------------- 图生图

    /** 导入一张图当图生图的底图（复制进 App 私有目录，之后按路径使用）。 */
    fun importImage(ref: String) {
        viewModelScope.launch {
            status = rt("status.importingImage")
            val saved = withContext(Dispatchers.IO) { copyImportedImage(ref) }
            if (saved == null) {
                status = rt("error.importFailed")
                return@launch
            }
            i2iOriginalPath = saved
            setI2iBase(saved)
            // 预览显示的那张也要一并设上（含像素尺寸）：否则 workImageWidth/Height 停在 0，
            // 遮罩坐标映射（fitRect）拿不到宽高比，导入图上就没法涂遮罩。
            // 与图库那条路径 useImageAsI2iBase() 保持一致。
            setI2iDisplay(saved)
            status = rt("status.imageImported")
        }
    }

    /**
     * **拖放进来的图片**：载入为图生图底图，并请求切回文生图页（用户 2026-09-19）。
     *
     * 和 [importImage] 是同一条路（复制进私有目录 → 底图 + 预览一起设上），
     * 只多一步"切回生图页"—— 拖放可能发生在任何一页。
     */
    fun importDroppedImage(ref: String) {
        importImage(ref)
        requestGeneratePage()
    }

    /**
     * 把某张图直接设为图生图底图（图库长按弹窗里的「使用此图图生图」走这里）。
     * 同时把它记成"最开始的图片"，这样"一直用最开始那张"模式下每次都用它。
     */
    fun useImageAsI2iBase(path: String, width: Int, height: Int) {
        i2iOriginalPath = path
        i2iBasePath = path
        i2iDisplayWidth = maxOf(1, width)
        i2iDisplayHeight = maxOf(1, height)
        i2iDisplayPath = path
        resetMaskState()
        status = rt("status.imageImported")
        navigateToGenerateTick++
    }

    /** 换**底图** = 下一轮真正送去生成的那张（不影响预览显示）。 */
    private fun setI2iBase(path: String) {
        i2iBasePath = path
    }

    /** 换**预览图** = 屏幕上显示、遮罩涂在上面的那张（换图会让已有遮罩失效）。 */
    private fun setI2iDisplay(path: String) {
        val dims = MaskCodec.imageSize(images, path)
        i2iDisplayWidth = dims?.first ?: 0
        i2iDisplayHeight = dims?.second ?: 0
        i2iDisplayPath = path
        resetMaskState()
    }

    /** 退出图生图模式（预览区右上角那个叉）：回到普通文生图。 */
    fun exitImg2Img() {
        if (i2iBasePath == null) return
        i2iBasePath = null
        i2iOriginalPath = null
        i2iDisplayPath = null
        i2iDisplayWidth = 0
        i2iDisplayHeight = 0
        resetMaskState()
        status = rt("status.img2imgOff")
    }

    private fun resetMaskState() {
        maskMode = false
        maskSketch = null
        maskSourcePath = null
        maskPaintedPercent = 0
        maskCanUndo = false
        // 换了图 → 聚焦框也没意义了
        maskFocusRect = null
        maskFocusTool = false
        maskVersion++
    }

    /** 把相册/文件里的图复制到私有目录（原样复制，后续都用 BitmapFactory 按内容解码）。 */
    private fun copyImportedImage(ref: String): String? = copyImageToPrivate(ref, "imported")

    /**
     * 把相册/文件里的图复制到私有目录的某个子目录（原样复制，不重编码）。
     *
     * @param subdir 子目录名，如 `imported`（图生图底图）、`reverse`（工具页反推用图）
     * @return 复制成功后的绝对路径；读不到或解不开则返回 null
     */
    private fun copyImageToPrivate(ref: String, subdir: String): String? = try {
        val dir = java.io.File(platform.paths.filesDir, subdir).apply { mkdirs() }
        val target = java.io.File(dir, "${System.currentTimeMillis()}.png")
        val copied = platform.openInput(ref)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied || MaskCodec.imageSize(images, target.absolutePath) == null) {
            target.delete()
            null
        } else {
            target.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    /**
     * 图生图（对齐 ComfyUI「NAI 图生图采样器」节点）：
     * 底图 = 导入的那张；强度默认 0.70、附加噪声 0.00、尺寸策略默认"保持输入尺寸"（64 对齐）。
     * 和重做一样**优先走流式**（节点里 streaming = true），不支持时回落非流式。
     */
    fun runImg2Img() {
        if (busy) return
        val sourcePath = i2iBasePath
        if (sourcePath == null) {
            status = rt("error.workbenchRequired")
            return
        }
        val token = effectiveToken()
        if (token.isEmpty()) {
            status = rt("error.tokenRequired")
            return
        }
        if (params.positivePrompt.trim().isEmpty()) {
            status = rt("error.positiveRequired")
            return
        }
        val sourceDims = MaskCodec.imageSize(images, sourcePath)
        if (sourceDims == null) {
            status = rt("error.inpaintSourceMissing")
            return
        }
        // 尺寸策略：保持输入尺寸（64 对齐）或使用核心参数的宽高
        val (requestWidth, requestHeight) = when (settings.i2iSizeMode) {
            "core" -> MaskCodec.requestSize(params.width, params.height)
            else -> MaskCodec.requestSize(sourceDims.first, sourceDims.second)
        }
        val taskParams = params.copy(width = requestWidth, height = requestHeight)
        val strength = settings.i2iStrength
        val noise = settings.i2iNoise
        val seed = resolveSeed(params)
        val initialExtras = extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives())

        cancelRequested = false
        val pipeline = openPreviewPipeline()

        generateJob = viewModelScope.launch {
            busy = true
            lastAnlasSpent = null
            var anlasBefore: Int? = null
            status = rt("status.i2iPreparing")
            // 从点击图生图这一刻算起（含底图准备），到拿到结果图为止
            val genStart = System.currentTimeMillis()
            try {
                anlasBefore = snapshotAnlasBefore(token)

                val basePng = withContext(Dispatchers.Default) {
                    val bitmap = MaskCodec.decodeFull(images, sourcePath)
                        ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                    val bytes = MaskCodec.basePng(images, bitmap, requestWidth, requestHeight)
                    bitmap.recycle()
                    bytes
                }

                clearGenerationPreview()
                status = rt("status.i2iRunning")

                // 图生图**不走流式**：带内联底图的请求体是 MB 级，发给流式端点会把连接挂住
                // （真机上卡死过一次）。参考实现也是这条口径：只有 action=generate 且无附件才流式。
                val images = api.img2img(
                    token = token,
                    params = taskParams,
                    seed = seed,
                    baseImagePng = basePng,
                    strength = strength,
                    noise = noise,
                    imageBaseUrl = effectiveImageBase(),
                    extras = initialExtras,
                    modelMode = settings.modelMode,
                )
                if (images.isEmpty()) throw IllegalStateException(rt("error.apiNoImages"))

                val saved = persistGeneratedImages(
                    images = images,
                    params = taskParams,
                    seed = seed,
                    feature = "i2i",
                    width = requestWidth,
                    height = requestHeight,
                    durationMs = System.currentTimeMillis() - genStart,
                )

                // 生成完**预览回到刚生成的那张**（这是用户要的：图上立刻看到结果）；
                // 而**下一轮的底图**按设置决定 —— 用刚生成的结果，还是一直用最开始那张。
                val resultPath = saved.first().filePath
                setI2iDisplay(resultPath)
                if (settings.i2iSourceMode == "original") {
                    setI2iBase(i2iOriginalPath ?: resultPath)
                } else {
                    setI2iBase(resultPath)
                }

                runCatching {
                    account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase()))
                }
                lastAnlasSpent = if (anlasBefore != null && account.anlasBalance != null) {
                    maxOf(0, anlasBefore!! - account.anlasBalance!!)
                } else {
                    null
                }
                // 每日统计：**这一笔实扣的点数** ✓（图数 / tag 字数在落库那一步已记 ✓）
                recordUsage(images = 0, anlas = lastAnlasSpent ?: 0, tagChars = 0)
                status = rf("status.i2iDone", mapOf("spent" to spentText(lastAnlasSpent)))
            } catch (e: Exception) {
                status = rf("status.i2iFailed", mapOf("error" to cleanError(e)))
            } finally {
                busy = false
                cancelRequested = false
                clearGenerationPreview()
                closePreviewPipeline(pipeline)
            }
        }
    }

    /**
     * 重做（局部重绘 / infill）。
     *
     * 用**当前这张图**当底图、用图上涂的遮罩圈出要重做的地方，提示词沿用正面提示词框。
     * 请求口径与参考实现一致：纯 JSON + base64、`action="infill"`、尺寸按源图 64 对齐；
     * **优先走流式**（和文生图一样有"模糊→清晰"的渐进预览），服务端不支持时自动回落非流式。
     *
     * 注意这**是一次真实的付费生成**（Opus 正常尺寸走订阅额度，但别假设），完成后显示实扣。
     */
    fun inpaintRegion() {
        if (busy) return
        val token = effectiveToken()
        if (token.isEmpty()) {
            status = rt("error.tokenRequired")
            return
        }
        // 重做的底图 = 当前**工作图**（图生图导入的图优先），不一定是"最近生成的那张"
        val sourcePath = workImagePath
        if (sourcePath == null) {
            status = rt("mask.needImage")
            return
        }
        val sketch = maskSketch
        val mask = sketch?.snapshot()
        // 聚焦重绘：有框就允许**遮罩为空** —— 官方口径"框里留空 = 整框重绘"
        val focusPlan = maskFocusPlan()
        // ⚠️ 这条路走没走，必须能从日志里看出来（用户报过"画了框还是全图重绘"，
        // 而 exe 没有控制台、界面又只能看到结论）。日志在 `%APPDATA%\NAI Studio\app.log`。
        logInfo(
            "Inpaint",
            "start: rect=" + (maskFocusRect?.let {
                "(${"%.3f".format(it.left)},${"%.3f".format(it.top)})-(${"%.3f".format(it.right)},${"%.3f".format(it.bottom)})"
            } ?: "null") +
                " tool=${maskFocusTool} plan=" + (focusPlan?.let {
                    "crop ${it.cropW}x${it.cropH}@${it.cropX},${it.cropY} request ${it.requestW}x${it.requestH} scale ${"%.2f".format(it.scale)}"
                } ?: "null") +
                " maskPainted=${mask?.selectedCount ?: 0}",
        )
        if (focusPlan == null && (mask == null || mask.isEmpty())) {
            status = rt("mask.empty")
            return
        }
        // 提示词沿用正面提示词框：为空时与文生图同样拦下，避免发出没有提示词的请求
        if (params.positivePrompt.trim().isEmpty()) {
            status = rt("error.positiveRequired")
            return
        }

        val sourceDims = MaskCodec.imageSize(images, sourcePath)
        if (sourceDims == null) {
            status = rt("error.inpaintSourceMissing")
            return
        }
        // 尺寸取**源图**（64 对齐 + 面积上限），不套用生成页当前的宽高：重做的是这张图本身
        // （聚焦重绘时用**裁剪+放大之后**的尺寸，见 focusPlan）
        val (requestWidth, requestHeight) = focusPlan?.let { it.requestW to it.requestH }
            ?: MaskCodec.requestSize(sourceDims.first, sourceDims.second)
        val inpaintModel = settings.inpaintModel
        val strength = settings.inpaintStrength
        val noise = settings.inpaintNoise
        val expandPixels = settings.inpaintMaskExpand
        val feather = settings.inpaintMaskFeather
        val edgeProtection = settings.inpaintEdgeProtection
        val pasteBack = settings.inpaintAddOriginalImage
        val taskParams = params.copy(model = inpaintModel, width = requestWidth, height = requestHeight)
        val seed = resolveSeed(params)
        val initialExtras = extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives())

        cancelRequested = false
        // 重做也要流式预览：和 generate() 用同一套管道
        val pipeline = openPreviewPipeline()

        generateJob = viewModelScope.launch {
            busy = true
            lastAnlasSpent = null
            var anlasBefore: Int? = null
            status = rt("status.inpaintPreparing")
            // 从点击重做这一刻算起（含遮罩膨胀/编码准备），到拿到服务端返回的图为止；
            // 本地回贴（pasteBack）是纯本地处理，不计入这段耗时
            val genStart = System.currentTimeMillis()
            try {
                anlasBefore = snapshotAnlasBefore(token)

                // 位图准备放后台线程：全尺寸解码 + 缩放 + 遮罩膨胀 + 吸附潜空间网格 + PNG 编码
                val prepared = withContext(Dispatchers.Default) {
                    if (focusPlan != null) {
                        // ---- 聚焦重绘：裁剪（含上下文）→ 放大到请求尺寸 → 蒙版搬过去 ----
                        val full = MaskCodec.decodeFull(images, sourcePath)
                            ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                        val fullPixels = images.pixelsOf(full)
                        val fullWidth = full.width
                        full.recycle()
                        val cropped = IntArray(focusPlan.cropW * focusPlan.cropH)
                        for (row in 0 until focusPlan.cropH) {
                            System.arraycopy(
                                fullPixels,
                                (focusPlan.cropY + row) * fullWidth + focusPlan.cropX,
                                cropped,
                                row * focusPlan.cropW,
                                focusPlan.cropW,
                            )
                        }
                        val cropImage = images.fromArgb(cropped, focusPlan.cropW, focusPlan.cropH)
                        val basePng = MaskCodec.basePng(images, cropImage, focusPlan.requestW, focusPlan.requestH)
                        cropImage.recycle()
                        // 蒙版：框里涂过就搬涂过的，没涂就整框（官方"leave it empty"那条）
                        val fullMask = mask ?: PixelMask(
                            maxOf(1, fullWidth),
                            maxOf(1, sourceDims.second),
                            ByteArray(maxOf(1, fullWidth) * maxOf(1, sourceDims.second)),
                        )
                        val focusMask = FocusedInpaint.maskIntoRequest(fullMask, focusPlan, fillWholeFrame = true)
                            ?: throw IllegalStateException(rt("mask.empty"))
                        val expandedMask = MaskExpand.dilate(focusMask, expandPixels)
                        val requestMask = MaskCodec.inpaintMask(
                            expandedMask, focusPlan.requestW, focusPlan.requestH,
                        ) ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
                        PreparedInpaint(
                            basePng = basePng,
                            maskPng = MaskCodec.maskPng(
                                images, requestMask, focusPlan.requestW, focusPlan.requestH,
                            ),
                            requestMask = requestMask,
                        )
                    } else {
                        val fullMask = mask ?: throw IllegalStateException(rt("mask.empty"))
                        val base = MaskCodec.decodeFull(images, sourcePath)
                            ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                        val basePng = MaskCodec.basePng(images, base, requestWidth, requestHeight)
                        base.recycle()
                        // mask_expand：把遮罩膨胀几像素，接缝落在选区外面（也补偿后面吸附带来的收缩）
                        val expanded = MaskExpand.dilate(fullMask, expandPixels)
                        // 吸附 8px 潜空间网格（节点口径）：不吸附的话服务端那边的重绘边界会和画的线错开，
                        // 表现就是"涂区外圈留一圈半重绘的带"
                        val requestMask = MaskCodec.inpaintMask(expanded, requestWidth, requestHeight)
                            ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
                        PreparedInpaint(
                            basePng = basePng,
                            maskPng = MaskCodec.maskPng(images, requestMask, requestWidth, requestHeight),
                            requestMask = requestMask,
                        )
                    }
                }

                clearGenerationPreview()
                status = rt("status.inpaintRunning")

                var images: List<ByteArray>? = null
                var usedModel = inpaintModel
                var fellBackToPlain = false
                if (settings.streamPreviewEnabled) {
                    try {
                        val streamed = api.inpaintStreaming(
                            token = token,
                            params = taskParams,
                            seed = seed,
                            baseImagePng = prepared.basePng,
                            maskPng = prepared.maskPng,
                            strength = strength,
                            noise = noise,
                            imageBaseUrl = effectiveImageBase(),
                            extras = initialExtras,
                            modelMode = settings.modelMode,
                            onPreview = ::handleGenerationPreview,
                        )
                        if (streamed != null) {
                            images = streamed.first
                            usedModel = streamed.second
                        } else {
                            fellBackToPlain = true
                        }
                    } catch (e: NaiHttpException) {
                        // 4xx = 请求本身被拒（没出图），退回非流式再试一次；
                        // 其它错误（网络中断、5xx）直接抛，避免重复扣费
                        if (e.statusCode in 400..499) fellBackToPlain = true else throw e
                    }
                }

                if (images == null) {
                    if (fellBackToPlain && settings.streamPreviewEnabled) {
                        status = rt("generate.streamUnavailable")
                    }
                    val plain = api.inpaint(
                        token = token,
                        params = taskParams,
                        seed = seed,
                        baseImagePng = prepared.basePng,
                        maskPng = prepared.maskPng,
                        strength = strength,
                        noise = noise,
                        imageBaseUrl = effectiveImageBase(),
                        extras = initialExtras,
                        modelMode = settings.modelMode,
                    )
                    images = plain.first
                    usedModel = plain.second
                }
                if (images.isEmpty()) throw IllegalStateException(rt("error.inpaintNoImages"))
                val genDurationMs = System.currentTimeMillis() - genStart

                // 本地回贴（add_original_image）：未遮罩区域用原图逐像素贴回，
                // 服务端重编码带来的轻微变化就不会漏到结果里。
                // 用**吸附后的请求尺寸遮罩**当 alpha —— 与服务端真正重绘的区域一致；
                // 而且尺寸天然对得上（之前用源图尺寸的遮罩会因尺寸不等而静默跳过回贴）。
                val finalImages = when {
                    // 聚焦重绘：把返回图缩回裁剪尺寸，**只把框那一块**贴回原图（上下文那圈丢掉）
                    focusPlan != null -> {
                        // ⚠️ 这里 `images` 这个名字被上面那个"服务端返回的图"占了，
                        // 所以图片能力要用 `this@AppState.images`（老代码也是这么绕的）。
                        val io = this@AppState.images
                        withContext(Dispatchers.Default) {
                            val plan = focusPlan
                            val source = MaskCodec.decodeFull(io, sourcePath)
                            if (source == null) {
                                images
                            } else {
                                val canvasPixels = io.pixelsOf(source)
                                val sourceW = source.width
                                val sourceH = source.height
                                source.recycle()
                                images.map { bytes ->
                                    runCatching {
                                        val generated = io.decodeBytes(bytes) ?: return@runCatching bytes
                                        val scaled = io.scale(generated, plan.cropW, plan.cropH, smooth = true)
                                        if (scaled !== generated) generated.recycle()
                                        val generatedPixels = io.pixelsOf(scaled)
                                        scaled.recycle()
                                        // 每张都从原图复制一份（一次返回多张时互不污染）
                                        val working = canvasPixels.copyOf()
                                        FocusedInpaint.pasteBack(
                                            canvas = working,
                                            sourceWidth = sourceW,
                                            sourceHeight = sourceH,
                                            generated = generatedPixels,
                                            plan = plan,
                                        )
                                        val composed = io.fromArgb(working, sourceW, sourceH)
                                        val png = io.pngBytes(composed)
                                        composed.recycle()
                                        png
                                    }.getOrNull() ?: bytes
                                }
                            }
                        }
                    }

                    pasteBack -> withContext(Dispatchers.Default) {
                        images.map { bytes ->
                            runCatching {
                                InpaintComposite.composePng(this@AppState.images, 
                                    generatedPng = bytes,
                                    originalPng = prepared.basePng,
                                    mask = prepared.requestMask,
                                    feather = feather,
                                    edgeProtection = edgeProtection,
                                )
                            }.getOrNull() ?: bytes
                        }
                    }

                    else -> images
                }
                logInfo(
                    "Inpaint",
                    "done: focus=${focusPlan != null} final=${finalImages.size} requestDims=" +
                        (if (focusPlan != null) {
                            "${focusPlan.requestW}x${focusPlan.requestH}"
                        } else {
                            "${requestWidth}x$requestHeight"
                        }),
                )

                persistGeneratedImages(
                    images = finalImages,
                    params = taskParams,
                    seed = seed,
                    feature = "inpaint",
                    model = usedModel,
                    // 聚焦重绘落盘的是**整张原图**（框那块已经贴回去了），尺寸按源图记
                    width = if (focusPlan != null) sourceDims.first else requestWidth,
                    height = if (focusPlan != null) sourceDims.second else requestHeight,
                    durationMs = genDurationMs,
                )
                // 出图后**默认清空遮罩，但不退出遮罩模式**：接着涂下一处就行
                maskSourcePath = current?.filePath
                maskSketch?.clear()
                maskPaintedPercent = 0
                maskCanUndo = false
                maskVersion++

                runCatching {
                    account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase()))
                }
                lastAnlasSpent = if (anlasBefore != null && account.anlasBalance != null) {
                    maxOf(0, anlasBefore!! - account.anlasBalance!!)
                } else {
                    null
                }
                // 每日统计：**这一笔实扣的点数** ✓（图数 / tag 字数在落库那一步已记 ✓）
                recordUsage(images = 0, anlas = lastAnlasSpent ?: 0, tagChars = 0)
                val note = if (usedModel != inpaintModel) {
                    rf("status.inpaintFallback", mapOf("model" to usedModel))
                } else {
                    ""
                }
                status = if (focusPlan != null) {
                    // 把"走了聚焦那条路"说出来（用户报过"画了框还是全图重绘"，靠这句话就能一眼分辨）
                    rf(
                        "status.inpaintDoneFocused",
                        mapOf(
                            "note" to note,
                            "spent" to spentText(lastAnlasSpent),
                            "rw" to focusPlan.requestW,
                            "rh" to focusPlan.requestH,
                        ),
                    )
                } else {
                    rf("status.inpaintDone", mapOf("note" to note, "spent" to spentText(lastAnlasSpent)))
                }
            } catch (e: Exception) {
                status = rf("status.inpaintFailed", mapOf("error" to cleanError(e)))
            } finally {
                busy = false
                cancelRequested = false
                // 参考实现的第 4 个清空点：整轮结束兜底
                clearGenerationPreview()
                closePreviewPipeline(pipeline)
            }
        }
    }

    /** 重做请求要用到的三块位图（底图、发出去的遮罩 PNG、**吸附后的请求尺寸遮罩**）。 */
    private class PreparedInpaint(
        val basePng: ByteArray,
        val maskPng: ByteArray,
        val requestMask: PixelMask,
    )

    // ==================================================================
    // **每日使用统计**（用户 2026-09-23：「记录每天跑图的数量、消耗的积分、tag 的字数」✓）
    //
    // 存在 `prefs.json` 里的一小段文本 ✓（`AppSettings.usageStats` ✓，几十字节 / 天 ✓），
    // 一并进备份 ✓（它和"历史 / 设置"同生共死 ✓，不该另开一个文件 ✓）。
    // 纯逻辑（累加 / 序列化 / 排格子）在 `models/UsageStats.kt` ✓，那一份有单测 ✓。
    // ==================================================================

    /** 从盘上读回来的那份统计 ✓（每次出图就地累加 ✓，见 [recordUsage] ✓）。 */
    var usage by mutableStateOf(UsageStats())
        private set

    /**
     * **高级漫画那一套界面状态**（工具选中 / 选中哪一格 / 画布缩放平移 ✓）。
     *
     * ⚠️ 为什么放这儿 ✗ 不放界面本地 `remember` ✓：并进主页之后，**同一份状态要被两处用** ✓ ——
     *  · 中间那块**画布**（`ComicCanvasHost` ✓：气泡 / 文本怎么落、落哪儿 ✓）；
     *  · 中控台里那条**工具条**（`ComicOverlayToolbar` ✓：选"圆泡"还是"旁白框" ✓）。
     * 两处各 `remember` 一份的话，工具条上选了"惊叫泡"、画布那边**还是旧工具** ✗
     *（点了没反应那一族 ✓）⇒ 提到这里共用一份 ✓。
     */
    val comicUi = com.kallan.naistudio.screens.ComicModeUiState()

    // ==================================================================
    // **画布通用**（用户 2026-09-23：「普通和无限画布和漫画还不是一个画布，我要画布通用」✓，
    // 选的是「**共用同一张画布内容 + 同一套缩放平移**」✓）
    //
    // 这一组就是那"**同一套缩放平移**" ✓：三档各自把本地值初始化成这里的值 ✓、
    // 变动时**推回**这里 ✓ —— 于是"在无限画布里放大到 2 倍、切回普通，还是 2 倍、还在同一个位置" ✓。
    // ⚠️ 为什么不做成"三档共用同一个 MutableState 实例" ✗：那要改四十多处赋值点 ✓（风险大），
    // 而"本地镜像 + 单向推回 + 挂载时读回"能拿到**完全一样的手感** ✓（切档就是一次挂载 ✓）。
    // ==================================================================

    /** 共用画布的缩放（1 = 原始大小 ✓）。 */
    var canvasViewScale by mutableFloatStateOf(1f)

    /** 共用画布的平移（屏幕 px ✓，和生图页原来那个"图片偏移"同一个量 ✓）。 */
    var canvasViewX by mutableFloatStateOf(0f)
    var canvasViewY by mutableFloatStateOf(0f)

    /**
     * 这个会话里**有没有人真动过**共用视图 ✓。
     *
     * ⚠️ 这一个标志**不能省** ✗：生图页那套缩放平移原来是用 `platform.kv` 记的 ✓（跨重启 ✓），
     * 而共用视图只在**内存**里 ✓。挂载时若**不分青红皂白**拿共用值去盖 ✓，就会把用户上次
     * 留在盘上的缩放**冲成 1 倍** ✗（"我明明记得放大过"那一族 ✗）。所以口径是：
     *  · 这个会话里**动过** ⇒ 以共用值为准 ✓（这才是"画布通用"要的效果 ✓）；
     *  · 没动过 ⇒ 各档照旧读自己那份（生图页读盘 ✓ / 无限画布用默认 ✓）。
     *
     * ⚠️⚠️ **2026-09-25 修**（用户：「电脑：**每次重启**，普通和无限画布切换**图像对不上**，
     * 需要手动调整才实现无缝切换」✗）—— 上面那条"没动过 ⇒ 无限画布用默认"**就是根因** ✗：
     *  · 普通那一档跨重启读的是 `canvas.preview.*`（上次停在哪儿、放大几倍 ✓）；
     *  · 无限画布**没有任何跨重启记忆**，一律回到默认 `1 倍 / 偏移 0` ✓；
     *  · ⇒ 一重启两档的**缩放平移就对不上**（显示的是同一张画布，可视图不同 ✗），
     *    用户只能每次手动拖回去 ✗。而且**每次重启都复现**（`touched` 天然是 false ✓）。
     *
     * 修法（[seedCanvasViewFromStore] + [holdCanvasView] 的落盘 ✓）：
     * **让三档共用同一份跨重启存储** ⇒ 无论上次是哪一档在动，"重启后切档"都从同一个值出发 ✓，
     * 两档天然对得上 ✓（内容本来就是同一张画布 ✓，现在视图也是同一个 ✓）。
     */
    var canvasViewTouched by mutableStateOf(false)
        private set

    /** 视图写盘的防抖任务（`holdCanvasView` 每帧都会被调 ⇒ 不能每帧写盘 ✗）。 */
    private var canvasViewSaveJob: Job? = null

    /**
     * 启动时把共用视图**从盘上读回来** ✓（和生图页那套摆放同一组键 ✓）。
     *
     * ⚠️ 必须在 [load] 的**同步前奏**里调 ✗（不能等 `loadJob` 那个协程 ✓）：
     *    界面第一帧就可能按这个值摆图 ✓，晚一步就是"打开瞬间跳一下" ✗。
     */
    private fun seedCanvasViewFromStore() {
        canvasViewScale = platform.kv.getInt(
            KEY_CANVAS_VIEW_SCALE,
            CANVAS_VIEW_SCALE_FACTOR.roundToInt(),
        ).toFloat() / CANVAS_VIEW_SCALE_FACTOR
        canvasViewX = platform.kv.getInt(KEY_CANVAS_VIEW_X, 0).toFloat()
        canvasViewY = platform.kv.getInt(KEY_CANVAS_VIEW_Y, 0).toFloat()
        // ⚠️ 只搬值、**不动 `canvasViewTouched`** ✗：它表达的是"这个会话里有人动过" ✓。
        //    这里搬完就代表"共用值已经等于盘上那份" ✓ ⇒ 各档无论走哪条分支读到的都一样 ✓。
    }

    /**
     * 三档把自己的当前视图推回来 ✓（**只在用户真动了之后**推 ✓，见 [canvasViewTouched] ✓）。
     *
     * ⚠️ 顺带**防抖落盘** ✓（用户 2026-09-25 那条"重启后对不上"的修法之一 ✓）：
     *    拖动时这个方法**每帧**都被调（`snapshotFlow` ✓），每帧写盘会把 `prefs.json` 磨穿 ✗
     *    ⇒ 停下来 400ms 才写一次 ✓（和生图页那条同源 ✓）。
     *    落盘之后**任何一档**的调整都能跨重启 ✓ —— 这正是原来缺的那一环 ✗
     *   （原先只有生图页那一档往盘上写 ✓，在无限画布里拖完重启就丢 ✗）。
     */
    fun holdCanvasView(scale: Float, x: Float, y: Float) {
        canvasViewScale = scale
        canvasViewX = x
        canvasViewY = y
        canvasViewTouched = true
        canvasViewSaveJob?.cancel()
        canvasViewSaveJob = viewModelScope.launch {
            delay(CANVAS_VIEW_SAVE_DEBOUNCE_MS)
            flushCanvasViewToStore()
        }
    }

    /**
     * 把当前共用视图**立刻**写盘 ✓（不含防抖 ✓）。
     *
     * 两个调用点：防抖到点之后 ✓、以及 [chooseCanvasMode] 切档时 ✓ ——
     * 后者的意义是"拖完视图马上切档"这条路上不会因为防抖还没到点就丢 ✓
     *（用户报的正是**切档**时对不上 ✓，所以切档那一刻顺手钉一次盘最稳 ✓）。
     */
    private fun flushCanvasViewToStore() {
        platform.kv.edit()
            .putInt(KEY_CANVAS_VIEW_X, canvasViewX.roundToInt())
            .putInt(KEY_CANVAS_VIEW_Y, canvasViewY.roundToInt())
            .putInt(KEY_CANVAS_VIEW_SCALE, (canvasViewScale * CANVAS_VIEW_SCALE_FACTOR).roundToInt())
            .apply()
    }

    /**
     * **把某张图铺成"共用画布"**（用户 2026-09-23 选的「新生成的图直接铺回这张共用画布」✓）。
     *
     * 于是在「普通」那一档看到的那张图，和切到「无限画布 / 漫画」看到的**是同一张** ✓
     * —— 三档只有工具不同 ✓，不是三块各画各的 ✗。
     *
     * ⚠️ 读不出来（文件不在 / 解不开）就**什么都不做** ✗ 不抛 ✓：画布还留着上一张，
     * 比"画布突然空了"好 ✓。
     *
     * @return true = 真铺上去了 ✓；false = 这张读不出来，画布**没动** ✓。
     */
    fun adoptImageAsCanvas(path: String): Boolean {
        val decoded = runCatching { images.decodeFull(path) }.getOrNull() ?: return false
        val w = decoded.width
        val h = decoded.height
        val pixels = runCatching { images.pixelsOf(decoded) }.getOrNull()
        decoded.recycle()
        if (pixels == null || w <= 0 || h <= 0) return false
        resetInfiniteCanvas(pixels, w, h)
        return true
    }

    /**
     * **记一笔**（落库那一刻记"图数 + tag 字数" ✓，各条流程收尾时补"这一笔实扣的点数" ✓）。
     *
     * ⚠️ **点数用"实扣"** ✗ 不用估算 ✓：`lastAnlasSpent` 是拿服务端余额前后差算的 ✓
     *（`snapshotAnlasBefore` + 事后刷新 ✓）；拿不到就记 0 ✓ ——
     * **宁可少记也不编数** ✓（用户拿这张表看自己花了多少 ✓，假数字比没有更坏 ✗）。
     *
     * ⚠️ 图数与点数**分两笔记** ✗ 不合成一笔 ✓：图数在落库那里才数得准 ✓，
     * 点数要到流程收尾（刷新账号之后 ✓）才算得出来 ✓ —— 两者时间点不同 ✓。
     */
    private fun recordUsage(images: Int, anlas: Int, tagChars: Int) {
        val today = java.time.LocalDate.now().toString()
        val next = usage.plus(today, images = images, anlas = anlas, tagChars = tagChars)
        usage = next
        setSettings { it.copy(usageStats = next.encode()) }
    }

    /**
     * 保存一次生成产出的所有图片，并更新历史 / 当前图 / 临时图库。
     *
     * 这一块在文生图 / 图生图 / 重做三条流程里**逐行相同**（只有 feature、model、尺寸不同），
     * 抽出来复用；返回本次保存的条目，让各流程按需接着做自己的收尾（如文生图累加 completed、
     * 重做清空遮罩）。行为与原先三处完全一致：
     *  · model / width / height 传 null 时，[Storage.saveImage] 回落到 params 自带的值（文生图口径）；
     *  · 历史插到最前（索引 0 = 最新）、current 指向首张、临时图库同样插到最前。
     */
    private suspend fun persistGeneratedImages(
        images: List<ByteArray>,
        params: GenerateParams,
        seed: Long,
        feature: String,
        model: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        /**
         * 本次产出的这几张属于**哪一个批次**（临时图库据此把同一批合成一项 + 角标张数）。
         * 传 null = 这次调用自己开一个新批次 —— 批量生成那条循环会把它**整轮共用**地传进来。
         */
        batchId: String? = null,
    ): List<HistoryItem> {
        status = rt("status.savingImage")
        val saved = ArrayList<HistoryItem>(images.size)
        for (bytes in images) {
            saved.add(
                storage.saveImage(
                    bytes = bytes,
                    params = params,
                    seed = seed,
                    settings = settings,
                    groups = groups,
                    feature = feature,
                    model = model,
                    width = width,
                    height = height,
                    // 历史分组功能已移除：图片按 <base>/<日期>/ 直接存放
                    groupId = null,
                ),
            )
        }
        // **出一张图 = 一条"对话记录"**（抽屉「对话模式」用）：模型 / 提示词 / 尺寸 / 出了几张。
        // 挂在这里是因为文生图、图生图、局部重绘、放大**全部**都会走 persistGeneratedImages。
        if (saved.isNotEmpty()) {
            recordTurn(
                kindKey = "conversation.kind." + when (feature) {
                    "i2i" -> "img2img"
                    "inpaint" -> "inpaint"
                    "upscale" -> "upscale"
                    "director" -> "director"
                    else -> "text2img"
                },
                provider = "novelai",
                model = model ?: params.model,
                userPrompt = params.positivePrompt,
                imageCount = saved.size,
                response = rt("conversation.imageReply").replace("{n}", saved.size.toString()),
                durationMs = durationMs ?: 0L,
                extra = buildString {
                    append("${width ?: params.width}×${height ?: params.height}")
                    append(" · steps ${params.steps}")
                    append(" · seed ${seed}")
                },
            )
        }

        // 历史插到最前面，与参考实现一致（索引 0 = 最新）
        history = saved + history
        current = saved.first()
        // 新图不是放大版了 —— 清掉"放大前那张"的记录（否则会拿旧图当工作图）
        upscaleSource = null
        // 本次会话的临时图库：只存在内存里，进程被杀（后台关掉软件）就没了
        sessionImages.addAll(0, saved)
        // 同一批的记账（临时图库的"合成一项 + 角标张数"）：没传批次号就当场开一个，
        // 于是一次请求回来多张（ZIP 里多图）也天然算同一批。
        val batch = batchId ?: newSessionBatchId()
        saved.forEach { sessionBatchIds[it.id] = batch }
        if (durationMs != null) {
            // **这一批每张都记**：同一次请求产出的图是一起回来的，耗时相同；
            // 早前只给 firstOrNull() 记，批量时只有第一张有角标（用户反馈"有的图没有时间"）。
            saved.forEach { sessionImageDurationMs[it.id] = durationMs }
        }
        // **每日统计**（用户 2026-09-23：「记录每天跑图的数量、消耗的积分、tag 的字数」✓）——
        // ⚠️ 图数 / tag 字数记在**落库这一刻** ✓（这里才知道真存了几张 ✓）；
        //    点数要到各条流程**收尾**（刷新账号之后 ✓）才补一笔 ✓（见 `recordUsage` 的说明 ✓）。
        recordUsage(
            images = saved.size,
            anlas = 0,
            tagChars = params.positivePrompt.length + params.negativePrompt.length,
        )
        return saved
    }

    /**
     * 生成前拉一次最新账号并记下当前余额（用于事后算实扣）。
     * 三条流程的开头都做这件事，抽出来去重。失败时 [mergeAccountKeepingLast] 会保留上次真实值。
     */
    private suspend fun snapshotAnlasBefore(token: String): Int? {
        account = mergeAccountKeepingLast(api.fetchAccount(token, effectiveImageBase()))
        return account.anlasBalance
    }

    private fun spentText(amount: Int?): String =
        if (amount == null) rt("status.actualSpentUnknown")
        else rf("status.actualSpent", mapOf("amount" to amount))

    /** 固定种子模式下批量生成时逐张派生的种子，与参考实现一致。 */
    private fun seededFor(params: GenerateParams, initialSeed: Long, index: Int): Long {
        if (params.seedMode == "random" || initialSeed <= 0) return api.randomSeed()
        return ((initialSeed - 1 + index) % 4_294_967_295L) + 1
    }

    private fun resolveSeed(params: GenerateParams): Long =
        if (params.seedMode != "random" && params.seed > 0) params.seed else api.randomSeed()

    /** 分片等待，期间可被取消打断。 */
    private suspend fun waitForInterval(seconds: Int): Boolean {
        var remaining = seconds * 1000L
        while (remaining > 0) {
            if (cancelRequested) return false
            val slice = minOf(250L, remaining)
            delay(slice)
            remaining -= slice
        }
        return !cancelRequested
    }

    // ---------------------------------------------------------- 预设库
    //
    // ⚠️ 用户 2026-09-24 改版（逐条 ✓）：**一条预设存正面 + 负面** ✓、**分组去掉、平铺** ✓、
    // 开关打开时**正面 → 风格提示词** ✓、**负面 → 负面提示词** ✓。模型见 `models/StylePresets.kt` ✓。

    private fun persistStyleLibrary(updated: StylePresetLibrary) {
        styleLibrary = updated
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setStylePresets(updated) } }
    }

    /** **新加一条空预设** ✓（名字 / 正文都空着 ✓ —— 用户在展开的文本框里填 ✓）。 */
    fun addStylePreset() {
        persistStyleLibrary(
            styleLibrary.copy(
                presets = styleLibrary.presets + StylePreset(
                    id = System.currentTimeMillis().toString() + "-" + (styleLibrary.presets.size),
                    name = "",
                    positive = "",
                    negative = "",
                    createdAt = java.time.Instant.now().toString(),
                ),
            ),
        )
    }

    fun updateStylePreset(id: String, update: (StylePreset) -> StylePreset) {
        persistStyleLibrary(
            styleLibrary.copy(presets = styleLibrary.presets.map { if (it.id == id) update(it) else it }),
        )
    }

    fun deleteStylePreset(id: String) {
        persistStyleLibrary(styleLibrary.copy(presets = styleLibrary.presets.filterNot { it.id == id }))
    }

    /**
     * **「储存当前预设」** —— 把现在输入框里的词**存成一条新预设**。
     *
     * 口径（用户追问时补的那句）：**正面取「风格提示词」**（「正面到风格」）、
     * **负面取「负面提示词」**（「负面进负面」）。
     *
     * ⚠️ 两样**都空**时**如实拒绝**、不静默存一条空的 —— 存一条点开什么都没有的预设，
     * 用户只会以为"按钮坏了"。
     *
     * @return true = 真存下了；false = 没东西可存（调用方给一句提示）。
     */
    fun saveCurrentAsPreset(): Boolean {
        val positive = params.stylePrompt.trim()
        val negative = params.negativePrompt.trim()
        if (positive.isEmpty() && negative.isEmpty()) return false
        persistStyleLibrary(
            styleLibrary.copy(
                presets = styleLibrary.presets + StylePreset(
                    id = System.currentTimeMillis().toString() + "-" + (styleLibrary.presets.size),
                    name = "",
                    positive = positive,
                    negative = negative,
                    createdAt = java.time.Instant.now().toString(),
                ),
            ),
        )
        return true
    }

    /** **整表内相邻交换**（分组没了 ⇒ 不再分"组内"）。`direction`：-1 上移 / +1 下移。 */
    fun moveStylePreset(preset: StylePreset, direction: Int) {
        val list = styleLibrary.presets
        val index = list.indexOfFirst { it.id == preset.id }
        val target = index + direction
        if (index < 0 || target !in list.indices) return
        val ordered = list.toMutableList()
        ordered[index] = list[target]
        ordered[target] = preset
        persistStyleLibrary(styleLibrary.copy(presets = ordered))
    }

    /**
     * 当前生效的**正面**预设文字（**正面 → 风格提示词**）。
     *
     * 这些内容**不会**写进输入框，只在生成时并入请求 ——
     * 所以勾选 / 取消是可逆的，用户手写的词也始终原样保留。
     */
    fun activeStylePresetPrompts(): List<String> = styleLibrary.activePositive()

    /** 当前生效的**负面**预设文字（**负面 → 负面提示词**）。 */
    fun activeStylePresetNegatives(): List<String> = styleLibrary.activeNegative()
    // ------------------------------------------------------- 历史与分组

    fun selectImage(item: HistoryItem) { current = item }

    fun deleteHistory(id: String) {
        viewModelScope.launch {
            // 级联：删掉一张原图时，它的官方 2× 放大版（可能不止一张）一起删，
            // 免得图库剩下一格"没有父图的放大版"；反过来删放大版不动原图。
            val target = history.firstOrNull { it.id == id }
            val children = target?.let { base ->
                history.filter { it.upscaleOfPath == base.filePath }
            }.orEmpty()
            val ids = listOf(id) + children.map { it.id }
            withContext(Dispatchers.IO) { ids.forEach { storage.deleteHistory(it) } }
            history = history.filterNot { it.id in ids }
            sessionImages.removeAll { it.id in ids }
            // 会话内的两张记账表（批次 / 耗时）也跟着清 —— 否则删掉的图还留着幽灵条目
            ids.forEach { gone ->
                sessionBatchIds.remove(gone)
                sessionImageDurationMs.remove(gone)
            }
            if (current?.id in ids) current = history.firstOrNull()
        }
    }

    fun renameHistory(item: HistoryItem, newName: String) {
        viewModelScope.launch {
            try {
                val renamed = withContext(Dispatchers.IO) { storage.renameHistoryFile(item, newName) }
                val updated = history.map { if (it.id == item.id) renamed else it }
                withContext(Dispatchers.IO) { storage.writeHistory(updated) }
                history = updated
                if (current?.id == item.id) current = renamed
            } catch (e: Exception) {
                status = cleanError(e)
            }
        }
    }

    /** 把一张历史图的参数载回生成页。 */
    fun reuseParams(item: HistoryItem) {
        val restored = GenerateParamsCodec.fromJson(item.params)
        setParam { restored }
        status = rt("gallery.paramsRestored")
    }

    fun createGenerationGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val groupsUpdated = groups + HistoryGroup(
            id = System.currentTimeMillis().toString(),
            name = trimmed,
            createdAt = java.time.Instant.now().toString(),
        )
        groups = groupsUpdated
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.writeGroups(groupsUpdated) } }
        setSettings { it.copy(generationGroupId = groupsUpdated.last().id) }
    }

    fun setGenerationGroup(id: String) = setSettings { it.copy(generationGroupId = id) }

    fun setActiveHistoryGroup(id: String) = setSettings { it.copy(activeHistoryGroupId = id) }

    // ==================================================================
    // 高级漫画模式（`docs/43-方案-高级漫画模式.md` · **M1：底板 + 格子**）
    // ==================================================================
    //
    // 这一块**只做骨架**：底板（新建画布 / 导入图像）+ 格子（模板铺 / 手拖 / 选中 / 拖动 /
    // 四角缩放 / 删除 / 调序号）+ 落盘读回。
    // ⚠️ **不接生成管线** ✗：聚焦重绘调用、队列、气泡、文本、图层、拼页导出都是后面批次的事。
    //
    // ## 落盘为什么**不走** `setSettings`
    //
    // `AppSettings` 是一个巨大的 data class（每个字段都进 `app_settings` 那个键、也进备份），
    // 把一个页表塞进去会连带改 `AppSettings` / `BackupCodec` 的字段与备份结构 ——
    // 而别的任务正在同时改那两个大文件附近的东西 ✗。
    // 所以**自己开两个键**（`comic.page.current` / `comic.pages`），走 `platform.kv`，
    // 和 `tools.window.set` / `panels.show.prompt` 那几样"外壳自己的状态"一个口径 ✓。
    //
    // ## 什么时候写盘
    //
    // **拖动 / 缩放过程中一帧都不写**（每帧写会把 `prefs.json` 磨穿 ✗ —— 见侧栏宽度那条注释）：
    // 那些方法只改内存里的 state，界面在**手势结束**时调一次 [commitComicBoard] 落盘 ✓。
    // 新建 / 删格 / 换序 / 铺模板 / 换底板这些**一次性动作**在方法内部直接落盘 ✓。

    /**
     * 高级漫画的**全部页**（M1 只有"当前页"这个概念，页条留给后面的批次）。
     *
     * ⚠️ **必须是引用相等策略**（`referentialEqualityPolicy` ✓），不能用默认的结构相等 ✗：
     * `updateComicBoardPage` 每次都 `comicBoardPages = comicBoardPages.map { … }`（**新列表** ✓），
     * 但 `ComicLayer` 里那两样是**可变字段**（`var visible` / `var opacity` ✓）——
     * `ComicLayerStack.setVisible/setOpacity` 改的是**同一个实例**，所以新旧两页
     * **结构上完全相等**（`data class` 的 `equals` 逐字段比，比的是同一个对象的当前值 ✓）⇒
     * 默认策略下这次赋值会被当成"没变"**直接丢掉** ✗，界面**一次都不重组**：
     * 表现就是"点了眼睛，图层列表和画布一点反应都没有" ✗（2026-09-20 第 ⑫ 批发现 ✓）。
     * 换成引用相等之后，"map 出来的新列表 ≠ 旧列表" ⇒ 一定通知 ✓（多出来的重组只发生在
     * 用户动作上，不是每帧 ✓）。气泡 / 文本 / 格子都是**不可变** `data class` ✓，本来就不靠这条 ✓。
     */
    var comicBoardPages by mutableStateOf<List<ComicBoardPage>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
        private set

    /** 当前页 id（落盘键 `comic.page.current`）。null = 还没有任何页。 */
    var comicBoardCurrentId by mutableStateOf<String?>(null)
        private set

    /**
     * 盘上的东西是否已经读回来过。
     *
     * 为什么**不在 [load] 里读**：`load()` 是既有方法，动它会牵扯启动那一段的错峰加载 ✗。
     * 改成"第一次进「高级漫画」页时读一次"（[ensureComicBoardLoaded]），
     * 代价只是"没进过那一页的话内存里是空的"—— 界面本来也不会去读它 ✓。
     */
    private var comicBoardLoaded = false

    /** 当前页（`comicBoardCurrentId` 找不到就退回第一页 —— 盘上被手改过也不至于白屏）。 */
    val comicBoardPage: ComicBoardPage?
        get() = comicBoardPages.firstOrNull { it.id == comicBoardCurrentId }
            ?: comicBoardPages.firstOrNull()

    /** 当前页的格子，**已按生成序号排好**（界面与以后的队列都按这个顺序 ✓）。 */
    val comicBoardPanels: List<ComicPanel>
        get() = comicBoardPage?.orderedPanels().orEmpty()

    /** 第一次进「高级漫画」页时调一次（幂等）：把两个键读回内存。 */
    fun ensureComicBoardLoaded() {
        if (comicBoardLoaded) return
        comicBoardLoaded = true
        comicBoardPages = ComicBoardStore.decodePages(
            platform.kv.getString(ComicBoardStore.KEY_PAGES, null),
        )
        val stored = platform.kv.getString(ComicBoardStore.KEY_CURRENT, null).orEmpty()
        comicBoardCurrentId = comicBoardPages.firstOrNull { it.id == stored }?.id
            ?: comicBoardPages.firstOrNull()?.id
    }

    /** 两个键一起写（分开写会出现"当前页指向一个已经不在表里的 id"的中间态 ✗）。 */
    private fun persistComicBoard() {
        platform.kv.edit()
            .putString(ComicBoardStore.KEY_PAGES, ComicBoardStore.encodePages(comicBoardPages))
            .putString(ComicBoardStore.KEY_CURRENT, comicBoardCurrentId.orEmpty())
            .apply()
    }

    /** 只改**当前页**（别的页原样保留）。 */
    private fun updateComicBoardPage(transform: (ComicBoardPage) -> ComicBoardPage) {
        val id = comicBoardCurrentId ?: return
        comicBoardPages = comicBoardPages.map { if (it.id == id) transform(it) else it }
        // ⚠️ **第 ㊻ 批（2026-09-21）**：用户报「**删除图层时还是只有和画布交互画布才出现画面**」✓
        //
        // 这里推一个"**图层表动过了**"的计数 ✓ —— 界面那一块（`ComicModeScreen` 的位图层 `forEach` ✓）
        // **读它**才算订阅 ✓。以前那里既没读任何状态 ✗、`forEach` 也没有 `key()` ✗
        // ⇒ 删层之后 Compose 按位置复用子节点、`FileImage` 又按 `path` 缓存 ✓
        // ⇒ 照旧显示旧组合 ✓，要点一下才刷新 ✓。
        comicBoardRevision++
    }

    /**
     * **图层表 / 页面结构的版本号**（第 ㊻ 批加 ✓）—— 每次 [updateComicBoardPage] +1 ✓。
     *
     * 界面读它 = 订阅"图层增删 / 换序 / 改尺寸" ✓（读一次就够 ✓，值本身不用参与计算 ✓）。
     */
    var comicBoardRevision by mutableIntStateOf(0)
        private set

    /**
     * 换底板（**新建画布** / **导入图像**两条路都走这里）。
     *
     * 页已经在就**就地换**：格子留着（底板换了、格子还是那些自由矩形），
     * 但要按新尺寸 [clampedTo] 夹回去 —— 换成小画布时原来贴边的格子不会跑到页外 ✓。
     * 没有页（第一次用）才新建一页。
     *
     * @param basePath null = 空白画布（用户口径里的"自建画布"✓）
     */
    fun setComicBoardBase(
        width: Int,
        height: Int,
        basePath: String? = null,
        name: String? = null,
    ) {
        // 换底板 = 换页尺寸 → 正在进行的那一段绘画会话必须**先收工** ✓
        //（收工写盘用的是**旧页尺寸**的像素 ✓ —— 先写再换，一个像素都不丢 ✓）
        commitComicLayerPaint()
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val current = comicBoardPage
        if (current == null) {
            val page = ComicBoardPage(
                id = "comic-page-" + System.currentTimeMillis().toString(),
                name = name ?: rf("comic.pageName", mapOf("index" to 1)),
                basePath = basePath,
                baseWidth = safeWidth,
                baseHeight = safeHeight,
                // 新页也要有那一层"底板" ✓（图层叠是拼页导出的唯一真相来源 ✓：少了它，
                // 导出来只有气泡 / 文本，底板那张图凭空不见 ✗）
            ).normalizedOverlays()
            comicBoardPages = comicBoardPages + page
            comicBoardCurrentId = page.id
        } else {
            val minSide = ComicBoardMetrics.minPanelSide(safeWidth)
            updateComicBoardPage { page ->
                page.copy(
                    name = name ?: page.name,
                    basePath = basePath,
                    baseWidth = safeWidth,
                    baseHeight = safeHeight,
                    panels = page.panels.map { it.clampedTo(safeWidth, safeHeight, minSide) },
                    // 气泡 / 文本跟着一起夹回去（换成小画布时，原来摆在右边 / 底部的气泡
                    // 会整块跑到页外 —— 那就再也点不中了 ✗）
                    bubbles = page.bubbles.map { it.clampedTo(safeWidth, safeHeight, minSide) },
                    texts = page.texts.map { it.clampedTo(safeWidth, safeHeight, minSide) },
                    // 叠里那一层"底板"的图**跟着换** ✓ —— 换了底板却不更新它，拼页导出还在用旧图 ✗
                    layers = page.layers.map { layer ->
                        if (layer.kind == ComicLayer.Kind.BASE) layer.copy(imagePath = basePath) else layer
                    },
                ).normalizedOverlays()
            }
        }
        persistComicBoard()
    }

    /**
     * 导入一张图当底板。
     *
     * 口径和别处一致 ✓：选图走宿主能力（`LocalUiHost.rememberImagePicker`，界面那边发起），
     * 回来的是 ref（手机 `content://` / 电脑普通路径）；这里**先复制进私有目录**
     * （复用图生图那条 [copyImageToPrivate]，ref 一旦失效底板就没了 ✗），
     * 再从复制件上读真实像素当底板尺寸。
     */
    fun importComicBoardBase(ref: String) {
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) { copyImageToPrivate(ref, "comic") }
            val size = if (copied == null) {
                null
            } else {
                withContext(Dispatchers.IO) { MaskCodec.imageSize(images, copied) }
            }
            if (copied == null || size == null) {
                toast(rt("comic.importFailed"), isError = true)
                return@launch
            }
            setComicBoardBase(width = size.first, height = size.second, basePath = copied)
            toast(rt("comic.baseImported"))
        }
    }

    /**
     * 在底板上**手拖**新建一格（坐标单位 = 底板像素）。
     *
     * @return 新格子的 id；太小（没到 [ComicBoardMetrics.minPanelSide]）或还没有底板时返回 null，
     *   界面据此决定要不要切"选中"。
     */
    fun addComicPanel(x: Float, y: Float, w: Float, h: Float): String? {
        val page = comicBoardPage ?: return null
        val minSide = ComicBoardMetrics.minPanelSide(page.baseWidth)
        if (w < minSide || h < minSide) return null

        val panelW = w.coerceAtMost(page.baseWidth.toFloat())
        val panelH = h.coerceAtMost(page.baseHeight.toFloat())
        val panel = ComicPanel(
            id = "comic-panel-" + System.currentTimeMillis().toString() + "-" + page.panels.size,
            x = x.coerceIn(0f, (page.baseWidth - panelW).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (page.baseHeight - panelH).coerceAtLeast(0f)),
            w = panelW,
            h = panelH,
            order = page.panels.size,
        )
        updateComicBoardPage { it.withPanels(it.panels + panel) }
        persistComicBoard()
        return panel.id
    }

    /**
     * 一键铺模板（`2 格竖排 / 3 格横排 / 4 格田字 / 6 格 2×3`）。
     *
     * ⚠️ **替换**当前页原有的格子（用户口径是"先用模板快速铺好，再手拖微调"✓）——
     * 已经有格子时该不该继续，由界面弹一次确认 ✓（状态层不弹窗）。
     */
    fun applyComicPanelTemplate(templateId: String) {
        val page = comicBoardPage ?: return
        val template = ComicPanelTemplates.byId(templateId) ?: return
        val prefix = "comic-panel-" + System.currentTimeMillis().toString()
        updateComicBoardPage {
            it.withPanels(
                ComicPanelTemplates.build(template, it.baseWidth, it.baseHeight, prefix)
                    .normalizePanelOrder(),
            )
        }
        persistComicBoard()
    }

    /** 删掉一格，并把剩下的序号重新压成 0..n-1（不留空洞）。 */
    fun deleteComicPanel(panelId: String) {
        updateComicBoardPage { page ->
            page.withPanels(page.panels.filterNot { it.id == panelId }.normalizePanelOrder())
        }
        persistComicBoard()
    }

    /**
     * 清空**高级漫画**当前页的格子（保留底板）。
     *
     * ⚠️ 名字里必须有 `Board`：旧漫画模式那边已经有一个 [clearComicPanels]（第 2074 行附近，
     * 清的是 `extras.comicPanels`）—— 同名同签名就是 `Conflicting overloads` ✗（实测踩过）。
     */
    fun clearComicBoardPanels() {
        updateComicBoardPage { it.withPanels(emptyList()) }
        persistComicBoard()
    }

    /** 序号上移 / 下移（`direction` 为 -1 上移、+1 下移）。 */
    fun moveComicPanelOrder(panelId: String, direction: Int) {
        updateComicBoardPage { page -> page.withPanels(page.panels.withPanelMoved(panelId, direction)) }
        persistComicBoard()
    }

    /**
     * 拖动一格（位移单位 = **底板像素**）。
     *
     * ⚠️ **不落盘**：手势每帧都会调它（见这一节开头"什么时候写盘"）。
     */
    fun moveComicPanelBy(panelId: String, dx: Float, dy: Float) {
        updateComicBoardPage { page ->
            page.withPanels(
                page.panels.map { panel ->
                    if (panel.id == panelId) {
                        panel.movedBy(dx, dy, page.baseWidth, page.baseHeight)
                    } else {
                        panel
                    }
                },
            )
        }
    }

    /** 拽四角缩放一格（位移单位 = **底板像素**）。同样**不落盘**。 */
    fun resizeComicPanelBy(panelId: String, corner: ComicPanelCorner, dx: Float, dy: Float) {
        updateComicBoardPage { page ->
            page.withPanels(
                page.panels.map { panel ->
                    if (panel.id == panelId) {
                        panel.resizedBy(corner, dx, dy, page.baseWidth, page.baseHeight)
                    } else {
                        panel
                    }
                },
            )
        }
    }

    /** 拖动 / 缩放**结束**时落一次盘（一次手势只写一次 ✓）。 */
    fun commitComicBoard() {
        persistComicBoard()
    }

    // ------------------------------------------------------------------
    // 第 ㊿k 批（2026-09-22）：**无限画布**（方案 `docs/69` ✓）
    //   · 口径：生图后四边各一个 `+` 长大一块（512 px ✓）；聚焦生成可随意画框 ✓；
    //     请求尺寸 ≤ 免费档上限 ✓ 且**倍率往上取整**（不靠缩小降画质 ✓）；
    //   · 本步做到"**文档几何 + 拓展 + 像素搬运**" ✓（纯逻辑已有单测 ✓）；界面在下一步 ✓；
    //   · ⚠️ 像素**暂不落盘** ✓（先把交互跑通 ✓），落盘在方案的"第 5 步" ✓。
    // ------------------------------------------------------------------

    /**
     * **画布模式**（0 = 普通 / 1 = 无限画布 / 2 = 漫画 ✓）——
     * 用户 2026-09-22：「**无限画布按钮移到中控右边栏里**」✓。
     *
     * ⚠️ 放在 AppState 而不是界面本地 `remember`：开关长在**右边栏**（`ControlPanel` ✓）、
     *    画布长在**左栏**（生成页宽屏那块 ✓），中间隔着好几层调用点 ✗ ——
     *    本地状态传不过去，硬传要把参数穿过四层 ✓。这是**纯界面状态**（不落盘 ✓）。
     */
    var canvasMode by mutableStateOf(0)
        private set
    /** 桌面快捷键请求打开导演台；生成页接收后清零。 */
    var directorShortcutOpen by mutableStateOf(false)

    /**
     * 切画布模式 ✓。
     *
     * ⚠️ **切换只换界面，画布一个字都不动** ✗✗（用户 2026-09-24：「画布切换模式时还是会切换画布，
     * 这不过是复制画布，不行，**不是同一张画布**，我要切换时**只改 ui，画布不变**」✓）。
     *
     * 所以这里**不再收盘** ✗：以前是 `if (mode != 1) closeInfiniteCanvas()` ✓ ——
     * 那是"三块各画各的"时代的省内存办法 ✓（一张 4096² 的位图很占 ✓），
     * 代价是**切档 = 把画布扔掉，切回来再按工作图重建** ✓ —— 用户一眼就看穿了（"这不过是复制画布"✗）。
     * 现在：**像素一直留着**（那才是画布本身 ✓），只放掉**派生出来的显示位图** ✓
     *（[releaseInfiniteCanvasBitmap] ✓，进漫画那一档时放 ✓ —— 那一档画的是它自己的底板 ✓）。
     */
    fun chooseCanvasMode(mode: Int) {
        if (canvasMode == mode) return
        if (mode == 2) {
            // 旧漫画页的剧情与整页风格词不再有独立输入框；仅在新栏为空时迁入。
            val oldPlot = comicPlot.trim()
            val oldStyle = extras.comicStyle.trim().takeUnless { it == COMIC_DEFAULT_STYLE }
            if (params.positivePrompt.isBlank() && oldPlot.isNotEmpty()) {
                setParam { it.copy(positivePrompt = oldPlot) }
                comicPlot = ""
            }
            if (params.stylePrompt.isBlank() && !oldStyle.isNullOrEmpty()) {
                setParam { it.copy(stylePrompt = oldStyle) }
                extras = extras.copy(comicStyle = COMIC_DEFAULT_STYLE)
            }
        }
        // 铺回共用画布（工作图 / 图库 / 上传那份"镜像"要跟画布一致 ✓）——
        // ⚠️ 这一步**只动文件** ✗ 不动画布 ✓：它把画布编成一张 PNG 让 `current` 指过去 ✓，
        //    三档显示的都是**同一张画布** ✓（普通那一档直接画画布位图 ✓，见 `GenerateScreen` ✓）。
        if (canvasMode == 1 && mode != 1) flushInfiniteCanvasIntoWorkImage()
        canvasMode = mode
        // The comic canvas is the storyboard/character-partition workflow.
        setComicMode(mode == 2)
        // 漫画那一档画的是"它自己的底板"，画布位图（几十 MB 的派生品 ✓）先放掉 ✓；像素留着 ✓
        if (mode == 2) releaseInfiniteCanvasBitmap()
        // 换档也是"用户意图"的一部分 ⇒ 落盘（下次开 App 还停在这一档 ✓，见 `docs/72` ✓）
        persistInfiniteState()
        // ⚠️ 顺手把**共用视图**也钉一次盘 ✓：用户常常"拖完画布马上切档" ✓，
        //    那时防抖的 400ms 还没到点 ⇒ 不钉这一下就会丢 ✓
        //   （"重启后切档对不上"那条，切档路径也占一份 ✓）。
        if (canvasViewTouched) flushCanvasViewToStore()
    }

    /** 画布几何 ✓（`null` = 还没开这一档 ✓）。 */
    var infiniteCanvas by mutableStateOf<InfiniteCanvas?>(null)
        private set

    /**
     * 这一档**正在跑一次聚焦生成** ✓（主按钮据此显示进度 ✓）。
     *
     * ⚠️ **不占**全局 `busy` ✗ —— 见 [runInfiniteFrame] 的注释（占了的话主按钮再点会变成排队文生图 ✗）。
     */
    var infiniteRunning by mutableStateOf(false)
        private set

    /** 画布像素（`width * height` 的 ARGB ✓）。 */
    private var infinitePixels: IntArray = IntArray(0)

    /**
     * 画布内容的**版本号**（开画布 / 拓展 / 贴回结果都 +1 ✓）——
     * 显示那张位图据此作废 ✓（见 [infiniteCanvasImage] ✓）。
     */
    private var infiniteRevision = 0
    private var infiniteImageCache: ImageBitmap? = null
    private var infiniteImageBuilt = Int.MIN_VALUE

    /**
     * **保住显示位图底下那两块 native 内存** ✗✗ 见下面 [infiniteCanvasImage] 的注释 ——
     * 这一条是 2026-09-22 那次"进无限画布就崩溃"的根因 ✓，**不许删** ✗。
     */
    private var infiniteImageHold: Any? = null

    /**
     * 画布那张**显示用位图** ✓（按 [infiniteRevision] 缓存 ✓）。
     *
     * ⚠️ **必须缓存** ✗：不缓存的话每次重组都要把 4096² 的 ARGB 转一遍位图（67 MB 级 ✓），
     * 界面当场卡死 ✗ —— 这与 `comics` 那边"整页 PNG 帧卡飞"是同一类坑（`docs/68` §四 ✓）。
     */
    fun infiniteCanvasImage(): ImageBitmap? {
        val canvas = infiniteCanvas ?: return null
        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0 || infinitePixels.size < w * h) return null
        infiniteImageCache?.let { if (infiniteImageBuilt == infiniteRevision) return it }
        val built = runCatching {
            val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
            val bytes = ByteArray(w * h * 4)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asIntBuffer().put(infinitePixels, 0, w * h)
            val bitmap = Bitmap()
            if (!bitmap.allocPixels(info)) return null
            if (!bitmap.installPixels(info, bytes, w * 4)) return null
            val image = Image.makeFromBitmap(bitmap)
            val out = image.toComposeImageBitmap()
            // ⛔⛔ **一个字都不许 close** ✗✗（2026-09-22 用户报"进无限画布就崩溃"的根因 ✓）：
            //    `toComposeImageBitmap()` 是**零拷贝**包住这张 Skia 图的 ✓ —— 它读的就是
            //    `bitmap` 的像素内存、而内存又指向 `bytes` 这个 JVM 数组 ✓。
            //    原来这里 `image.close(); bitmap.close()` 一关，Compose 下一帧去画就是
            //    **use-after-free** ✗ ⇒ **native 段错误**：JVM 连异常都来不及抛 ✓，
            //    所以 `app.log` 干干净净、进程直接没了 ✓（我按日志查了半天才回过味来 ✓）。
            //    代价：这一份像素要留到下次重建（3072²≈37MB / 4096²≈67MB ✓）——
            //    它本来就是缓存里的当前画布 ✓，不留白不留 ✓。
            infiniteImageHold = listOf(bitmap, image, bytes)
            out
        }.getOrNull()
        infiniteImageCache = built
        infiniteImageBuilt = infiniteRevision
        return built
    }

    /** 只读快照（界面画它 / 单测读它 ✓）—— 别拿去改 ✗（要改走 [extendInfiniteCanvas] ✓）。 */
    fun infiniteCanvasPixels(): IntArray = infinitePixels

    /**
     * **聚焦框**（**文档坐标** ✓）—— 用户 2026-09-22 的新口径：
     * 「**给一个可以调大小的框，用户想要拓展就将框移到画像外想要拓展的部分**」✓。
     *
     * ⚠️ 用**文档坐标**而不是画布像素 ✗✗：框**允许整个在画布外** ✓ ——
     * 那正是"往那边拓展"的表达方式 ✓；换成画布像素坐标就会被画布边界夹住 ✗，
     * "把框移到画像外"这件事**根本画不出来** ✗。
     */
    var infiniteFrame by mutableStateOf<EditRect?>(null)
        private set

    /** 改框（`null` = 清掉 ✓）。⚠️ 名字**不能**叫 `setInfiniteFrame` ✗ —— 那和属性的 setter 撞 JVM 签名 ✓ */
    fun updateInfiniteFrame(rect: EditRect?) {
        // 用户 2026-09-22：「**无限画布模式拖动框没限制**」✓ ⇒ 在这里就把框夹进
        // 「平台还装得下」的范围 ✓（判据与真正生成时的 `expandedToInclude` **同一处** ✓，
        // 见 `InfiniteCanvas.clampFrame` ✓）—— "拖到装不下、点了生成才报上限"是事后诸葛亮 ✗。
        // ⚠️ 夹在这个**唯一入口**上 ✗ 不在界面里夹：界面有好几处会改框 ✓，漏一处就等于没夹 ✓。
        if (rect == null) {
            infiniteFrame = null
            return
        }
        val canvas = infiniteCanvas
        infiniteFrame = if (canvas == null) rect else canvas.clampFrame(rect)
    }

    /**
     * 用户 2026-09-29：「可自己设置拉框的长宽」✓ —— 以**框中心**为准改尺寸；
     * 总像素 ≤ 1024×1024（超了就退**另一边** ✓，[keepWidth] = 刚改的是宽）；请求尺寸照旧自动算 ✓。
     */
    fun setInfiniteFrameSize(width: Int, height: Int, keepWidth: Boolean) {
        val f = infiniteFrame ?: return
        var w = width.coerceIn(64, 4096)
        var h = height.coerceIn(64, 4096)
        if (w.toLong() * h > INFINITE_FRAME_MAX_AREA) {
            if (keepWidth) {
                h = (INFINITE_FRAME_MAX_AREA / w).coerceAtLeast(64)
            } else {
                w = (INFINITE_FRAME_MAX_AREA / h).coerceAtLeast(64)
            }
        }
        val next = EditRect(f.x + f.w / 2 - w / 2, f.y + f.h / 2 - h / 2, w, h)
        updateInfiniteFrame(infiniteCanvas?.clampFrame(next) ?: next)
        commitInfiniteCanvas()
    }

    /**
     * 画布**存盘那一份快照**的路径（`files/infinite/canvas-<时间戳>.png` ✓）与
     * "这张画布是从哪张工作图开的"（[ensureInfiniteCanvas] 据此判断要不要接着用 ✓）。
     *
     * ## 为什么要存这一份（用户马上就会撞上的那件事 ✓）
     *
     * 画布是**内存里**的一张位图 ✓；切回「普通」模式时 `chooseCanvasMode` 会把它**收掉** ✓（省内存 ✓）——
     * 不收的话，用户手一滑切个模式，刚才拓展了半天的画布就**没了** ✗（最伤人的那种"白干" ✗）。
     *
     * 所以每跑完一次聚焦生成，就把**整张画布**编成 PNG 落一份 ✓：
     *  · 切回无限画布 ⇒ 直接接着用 ✓（判据见 [infiniteCanvasSource] ✓）；
     *  · ⚠️ 老快照**顺手删掉** ✗ 不能留：一张 4096² 的 PNG 有十几 MB ✓，每次拓展留一份就是灾难 ✓。
     *  · ⚠️ 原点（`originX/originY`）**单独存一份小文件** ✓ —— 只存像素的话，往左 / 上长过的画布
     *    读回来原点会变成 0 ⇒ **文档坐标整体错位** ✗（框对不上画 ✗）。
     */
    private var infiniteCanvasFile: String? = null
    /**
     * 这张画布**是从哪张工作图开的** ✓（`workImagePath` 的快照 ✓）。
     *
     * 判据（**可预期**，不是"猜" ✓）：
     *  · 现在的工作图 == 开画布时那张 ⇒ 中间没生成过新图 ⇒ **接着用存下来的画布** ✓；
     *  · 不相等 ⇒ 用户生成了新图 ⇒ **以新图为准重开画布** ✓（新图是更新的意图 ✓），
     *    老快照删掉 ✓。
     */
    private var infiniteCanvasSource: String? = null

    /**
     * 画布上**有"只活在无限画布这一档"的改动** ✓（四边 `+` 拓展 ✓ / 撤销重做换了内容 ✓）。
     *
     * ⚠️ 为什么需要它：这两条路**都不经过"生成落库"** ✗ ⇒ `current`（= 工作图 ✓）还是旧的 ✗，
     * 于是"切到普通 / 漫画看到的不是同一张画布" ✓（用户 2026-09-23：「**我要画布通用**」正是冲它来的 ✓）。
     * 落库那条路（聚焦生成 ✓）会把它清掉 ✓ —— 那一刻产物**就是画布本身** ✓，两边天然一致 ✓。
     */
    private var infiniteCanvasDirty = false

    /** "铺回共用画布"那次编码（后台 ✓）；切档连着点只留最后一次 ✓。 */
    private var canvasFlushJob: Job? = null

    /**
     * **把无限画布的内容"铺回"共用画布**（= 让普通 / 漫画也看到同一张 ✓，用户 2026-09-23「我要画布通用」✓）。
     *
     * 什么时候调：**离开无限画布那一档**时（`chooseCanvasMode` ✓）——只有那一刻才需要，
     * 而且那一刻 `+` 拓展 / 撤销重做都已经尘埃落定 ✓（中途每点一次 `+` 就编码一遍是白烧 CPU ✗）。
     *
     * 干了什么：把整张画布编成一张 PNG（后台线程 ✓）⇒ 把 `current` 指到它 ✓ ⇒
     * `workImagePath` 于是就是这张画布 ✓ ⇒ 普通那一档的预览、图生图 / 局部重绘的底图、
     * 漫画的「新建底板」**全都拿到同一张画布** ✓✓。
     *
     * ⚠️ **不进历史、不进图库** ✗（`history` 一律不动 ✓）：它本来就是"那张画布" ✓，
     * 往图格里塞一张"看着一模一样的画布"是用户没要的噪音 ✗。
     * ⚠️ 存的名字带 `shared` 前缀 ✓ ⇒ 快照 GC（按路径点名删 ✓）够不着它 ✓（见 [encodeInfiniteCanvas] ✓）。
     * ⚠️ 图生图 / 编辑会话里**一步都不动** ✗：那一刻工作图是用户手选的那张底图 ✓，
     * 拿画布顶掉它等于把人在编辑的底图换了 ✗。
     * ⚠️ 失败**如实说一句** ✓（状态行 ✓）不静默 ✓；脏标记还回去 ⇒ 下次离开还会再试 ✓。
     */
    fun flushInfiniteCanvasIntoWorkImage() {
        if (!infiniteCanvasDirty) return
        if (img2imgActive || editSource != null) return
        val canvas = infiniteCanvas ?: return
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0 || infinitePixels.size < width * height) return
        // 主线程拷一份像素（几十 ms ✓）；⚠️ 不能把 `infinitePixels` 交给后台 ✗ ——
        // 切档之后 `closeInfiniteCanvas` 会把它换成空数组 ✓，后台读到就是"空画布" ✗。
        // `canvas` 本身是**不可变**的 ✓（`extended()` 返回新的 ✓）⇒ 直接带进后台没问题 ✓，
        // 原点（`originX/originY` ✓）也就跟着过去了 ✓。
        val pixels = infinitePixels.copyOf(width * height)
        infiniteCanvasDirty = false
        status = rt("infinite.flushBusy")
        canvasFlushJob?.cancel()
        canvasFlushJob = viewModelScope.launch {
            val path = withContext(Dispatchers.Default) {
                encodeInfiniteCanvas(canvas = canvas, pixels = pixels, prefix = "shared")
            }
            if (path == null) {
                infiniteCanvasDirty = true
                status = rt("infinite.flushFailed")
                return@launch
            }
            val now = System.currentTimeMillis()
            current = HistoryItem(
                id = "canvas-$now",
                filePath = path,
                date = java.time.LocalDate.now().toString(),
                createdAt = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    java.util.Locale.US,
                ).format(java.util.Date(now)),
                seed = 0L,
                model = params.model,
                width = width,
                height = height,
                prompt = "",
                feature = "canvas",
            )
            // 新图不是放大版了（和 `persistGeneratedImages` 同一条口径 ✓）
            upscaleSource = null
            infiniteCanvasFile = path
            infiniteCanvasSource = path
            persistInfiniteState()
            status = rt("infinite.flushDone")
        }
    }

    /**
     * **撤销 / 重做栈**（`docs/72` ✓，纯逻辑 + 单测见 `models/InfiniteCanvasHistory.kt` ✓）。
     *
     * ⚠️ **不跨进程** ✗：盘上只留"当前那一张"快照 ✓，历史跟着进程走 ✓（`restoreInfiniteCanvasState` 会清 ✓）。
     */
    private val infiniteHistory = InfiniteCanvasHistory()

    /**
     * **用一张刚出来的图开画布** ✓（无限画布模式的第一步：出图 ⇒ 铺进画布 ✓）。
     *
     * 尺寸超过平台上限（理论上出图尺寸不会超 ✓）时**如实夹取** ✓ —— 夹取只裁不多，
     * 绝不"先缩再放大"（那是用户点名不要的降画质 ✗）。
     */
    fun resetInfiniteCanvas(pixels: IntArray, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || pixels.size < width * height) {
            infiniteCanvas = null
            infinitePixels = IntArray(0)
            return
        }
        val canvas = InfiniteCanvas.create(width, height, platform.infiniteCanvasMaxSide)
        infinitePixels = if (canvas.width == width && canvas.height == height) {
            pixels.copyOf(width * height)
        } else {
            InfiniteCanvasPixels.blit(
                src = pixels,
                srcW = width,
                srcH = height,
                dstW = canvas.width,
                dstH = canvas.height,
                dx = 0,
                dy = 0,
            )
        }
        infiniteCanvas = canvas
        infiniteRevision++
    }

    /**
     * 关掉这一档（切回普通模式 / 退出时 ✓）。
     *
     * ⚠️ **盘上那份快照留着** ✓（见 [infiniteCanvasFile] ✓）—— 收掉的只是内存里这张位图 ✓，
     * 切回来还能接着用 ✓；不然"手滑切个模式白干半天" ✗。
     */
    fun closeInfiniteCanvas() {
        infiniteCanvas = null
        infinitePixels = IntArray(0)
        infiniteImageCache = null
        // 收档时把那两块 native 内存也放开 ✓（**只在真收档时放** ✗ 不能在 `infiniteCanvasImage` 里放 ✓）
        infiniteImageHold = null
        infiniteImageBuilt = Int.MIN_VALUE
        infiniteRevision++
    }

    /**
     * **进"无限画布"这一档时把画布铺好** ✓（幂等 ✓，已有画布就什么都不做 ✓）。
     *
     * 口径（`docs/69` §一 第 1 条「和生图一样的界面」✓）：
     *  · 上一轮存下的**快照**还在、且**中间没生成过新图**（`infiniteCanvasSource == workImagePath` ✓，
     *    或工作图**还没读出来** ✓）⇒ **接着用** ✓ —— 切个模式来回一次、关掉 App 再开，
     *    都不该把拓展成果丢掉 ✓（启动竞态那一条见下面函数里的注释 ✓）；
     *  · 手上**已经有图**（刚生成的 / 图生图导入的 ✓）⇒ 直接当画布底 ✓（`workImagePath` ✓）；
     *  · 还没有图 ⇒ 先给一张**空白**（1024×1024 透明 ✓）—— 用户接着就能框着生成 ✓；
     *  · 图读不出来 ⇒ 也退回空白 ✓（**绝不崩、也绝不静默什么都不给** ✗）。
     *
     * ⚠️ 这里**不碰**生图页原来那套预览状态（`generationPreview` / `workImagePath` ✓）——
     * 两档各看各的 ✓，切回去还是原来那张 ✓。
     */
    fun ensureInfiniteCanvas() {
        if (infiniteCanvas != null) return
        // ① 上一轮存下的**画布快照**还在、而且**中间没生成过新图** ⇒ 接着用 ✓
        //    ⚠️ 判据里那条 `workImagePath == null` 是**启动竞态**的护栏 ✗ 不是随手加的：
        //    启动时 `current`（= 工作图）是**异步读盘**恢复的 ✓，界面可能先组合出来并调到这儿 ✓ ——
        //    那一刻工作图还是 null，若硬按"来源 != 工作图 ⇒ 重开"判，就会拿**空白**把用户存下的
        //    画布**冲掉** ✗✗（这正是最不该发生的那种数据丢失 ✗）。工作图还没有 ⇒ 以存下的画布为准 ✓。
        val snapshot = infiniteCanvasFile
        if (snapshot != null && (workImagePath == null || infiniteCanvasSource == workImagePath)) {
            val restored = loadInfiniteCanvasFile(snapshot)
            if (restored) return
        }
        // ② 以"现在的工作图"为准重开 ✓（老快照到这一步已经没用了 ⇒ 删掉 ✓ 别占盘 ✗）
        clearInfiniteSnapshot()
        val path = workImagePath
        if (path != null && adoptImageAsCanvas(path)) {
            infiniteCanvasSource = path
            return
        }
        // 空白底（透明 ✓）：尺寸给个方形基准，之后靠拓展 / 聚焦生成长大 ✓
        val side = 1024
        resetInfiniteCanvas(IntArray(side * side), side, side)
        infiniteCanvasSource = null
    }

    /**
     * **放掉派生出来的显示位图** ✓（**画布像素留着** ✗ —— 那才是画布本身 ✓）。
     *
     * 什么时候调：进漫画那一档时 ✓（那一档画的是它自己的底板 ✓，用不着这张位图 ✓）。
     * 一张 4096² 的 `ImageBitmap` 是几十 MB ✓，留着只为"切回来快一点"就不值了 ✓
     *（切回来时按 [infiniteRevision] 重建一次 ✓，见 [infiniteCanvasImage] 的缓存 ✓）。
     */
    fun releaseInfiniteCanvasBitmap() {
        infiniteImageCache = null
        infiniteImageHold = null
        infiniteImageBuilt = Int.MIN_VALUE
        infiniteRevision++
    }

    /**
     * **三档共用同一张画布**：画布没有就建一张 ✓、**有但不是这张工作图就换成这张** ✓
     *（用户 2026-09-23：「新生图铺回共用画布」✓；2026-09-24：「切换时只改 ui，画布不变」✓）。
     *
     * 什么时候调：① 进某一档时（外面那个 `LaunchedEffect(canvasMode)` ✓）；
     * ② 普通那一档**有图**时（工作图一变 ⇒ 画布换成新的那张 ✓）。
     *
     * ⚠️ 两条**绝不冲掉**画布的护栏 ✗：
     *  · `infiniteCanvasSource == 工作图` ⇒ 就是它 ✓ 什么都不做 ✓（**切档走的正是这条路** ✓
     *    —— 所以"切档"这一步对画布是**完全无操作** ✓，这正是用户要的 ✓）；
     *  · `infiniteCanvasDirty`（画布上有只在无限画布里做过的改动 ✓：撤销 / 重做 ✓）⇒ 也不动 ✗
     *    —— 拿工作图覆盖会把用户刚撤销出来的结果冲掉 ✓。
     */
    fun syncSharedCanvas() {
        ensureInfiniteCanvas()
        val path = workImagePath ?: return
        if (infiniteCanvasSource == path) return
        if (infiniteCanvasDirty) return
        if (adoptImageAsCanvas(path)) {
            infiniteCanvasSource = path
            // 画布刚被换成"就是这张工作图" ⇒ 那面"只在无限画布里改过"的旗子也该落下了 ✓
            infiniteCanvasDirty = false
            // 这张快照已经过时了（新画布 == 工作图 ✓）⇒ 删掉 ✓ 别让下次开 App 又按它还原 ✓
            clearInfiniteSnapshot()
        }
    }

    /** 把盘上那份快照读回画布 ✓；读不出来（文件没了 / 解不开 / 超上限）⇒ false ✓，调用方退回工作图 ✓。 */
    private fun loadInfiniteCanvasFile(path: String): Boolean {
        val decoded = decodeInfiniteSnapshot(path) ?: return false
        applyDecodedCanvas(decoded)
        return true
    }

    /**
     * 只**解码**一张快照 ✓（纯 CPU + 读盘 ✓，**不写任何状态** ✗ ⇒ 可以在 `Dispatchers.Default` 上调 ✓；
     * 写状态那一步交给 [applyDecodedCanvas] 在主线程做 ✓ —— 两头分开，后台线程就碰不到 Compose 状态 ✓）。
     */
    private fun decodeInfiniteSnapshot(path: String): DecodedInfiniteCanvas? {
        val decoded = runCatching { images.decodeFull(path) }.getOrNull() ?: return null
        val w = decoded.width
        val h = decoded.height
        val pixels = runCatching { images.pixelsOf(decoded) }.getOrNull()
        decoded.recycle()
        if (pixels == null || w <= 0 || h <= 0) return null
        val maxSide = platform.infiniteCanvasMaxSide
        if (w > maxSide || h > maxSide) return null
        val origin = readInfiniteOrigin(path)
        return DecodedInfiniteCanvas(
            pixels = pixels.copyOf(w * h),
            width = w,
            height = h,
            originX = origin?.first ?: 0,
            originY = origin?.second ?: 0,
        )
    }

    /**
     * 把解码好的画布**摆上去** ✓（主线程 ✓）。
     *
     * ⚠️ 直接摆原点 ✗ **不能**走 `resetInfiniteCanvas`：那个只把原点当 0 ✓ —— 那是"新开一张"的口径 ✓，
     * 拿它还原往左 / 上长过的画布会把**文档坐标整体挪位** ✗（框和画对不上 ✓）。
     */
    private fun applyDecodedCanvas(decoded: DecodedInfiniteCanvas) {
        infinitePixels = decoded.pixels
        infiniteCanvas = InfiniteCanvas(
            width = decoded.width,
            height = decoded.height,
            originX = decoded.originX,
            originY = decoded.originY,
            maxSide = platform.infiniteCanvasMaxSide,
        )
        infiniteRevision++
    }

    /** 解码好的一张画布 ✓（像素 + 尺寸 + 原点 ✓）。 */
    private class DecodedInfiniteCanvas(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
    )

    /** 快照 PNG 旁边那份"原点"小文件（`canvas-<时间戳>.origin`，内容 `originX,originY` ✓）。 */
    private fun infiniteOriginFile(pngPath: String): File =
        File(pngPath.removeSuffix(".png") + ".origin")

    /** 读那份小文件；没有 / 读坏了 ⇒ null ✓（调用方按原点 0 处理 —— 那是没往左 / 上长过的常见情形 ✓）。 */
    private fun readInfiniteOrigin(pngPath: String): Pair<Int, Int>? = runCatching {
        val raw = platform.openInput(infiniteOriginFile(pngPath).absolutePath)?.use { it.readBytes() }
            ?: return@runCatching null
        val parts = String(raw, Charsets.UTF_8).trim().split(',')
        if (parts.size != 2) {
            null
        } else {
            parts[0].trim().toInt() to parts[1].trim().toInt()
        }
    }.getOrNull()

    /** 把老快照（PNG + 原点小文件）从盘上删掉并把两个记账字段清空 ✓
     *  （**只删自己目录里 `canvas-` 开头的** ✗ 别人的文件一个都不动 ✓）；撤销栈也一起清 ✓。 */
    private fun clearInfiniteSnapshot() {
        deleteInfiniteSnapshots(infiniteHistory.clear())
        deleteInfiniteSnapshot(infiniteCanvasFile)
        refreshInfiniteHistoryFlags()
        infiniteCanvasFile = null
        infiniteCanvasSource = null
    }

    /** 删一张快照（PNG + 原点小文件 ✓）—— 名字不以 `canvas-` 开头的一律不碰 ✓。 */
    private fun deleteInfiniteSnapshot(path: String?) {
        val target = path?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val file = File(target)
            if (file.name.startsWith("canvas-")) {
                file.delete()
                infiniteOriginFile(target).delete()
            }
        }
    }

    /** 删一串快照 ✓（撤销栈挤出去的 / 重做线断掉的 ✓ 都要真删 ✗ 不然盘上留一串回不去的图 ✓）。 */
    private fun deleteInfiniteSnapshots(entries: List<InfiniteCanvasHistory.Entry>) {
        entries.forEach { deleteInfiniteSnapshot(it.path) }
    }

    /** 两栈的可用状态同步给界面 ✓（栈里有货按钮才亮 ✓ —— "亮着点了没反应"最烦 ✗）。 */
    private fun refreshInfiniteHistoryFlags() {
        infiniteCanUndo = infiniteHistory.canUndo
        infiniteCanRedo = infiniteHistory.canRedo
    }

    /**
     * **把这一档"下次还想要的东西"落盘** ✓（模式 / 快照路径 / 来源工作图 / 框 ✓）。
     *
     * ⚠️ **只在"一步做完"时调** ✗：框的拖动过程中会一路调 [updateInfiniteFrame] ✓，
     * 每帧写一次盘就是拿磁盘换流畅 ✗（界面在**手势结束**时调一次 [commitInfiniteCanvas] ✓，与漫画同一个口径 ✓）。
     */
    private fun persistInfiniteState() {
        val snapshot = infiniteCanvasFile ?: ""
        val source = infiniteCanvasSource ?: ""
        val frame = infiniteFrame
        setSettings { current ->
            current.copy(
                canvasMode = canvasMode,
                infiniteCanvasSnapshot = snapshot,
                infiniteCanvasSource = source,
                infiniteFrameX = frame?.x ?: 0,
                infiniteFrameY = frame?.y ?: 0,
                infiniteFrameW = frame?.w ?: 0,
                infiniteFrameH = frame?.h ?: 0,
            )
        }
    }

    /**
     * **框拖完了** ✓（界面在手势结束时调一次 ✓）：把框 + 这一档的记账落盘 ✓。
     * 拖动过程中不写盘（见 [persistInfiniteState] 的注释 ✓）。
     */
    fun commitInfiniteCanvas() {
        if (canvasMode != 1) return
        persistInfiniteState()
    }

    /** 启动时把上次那一档接着摆回来 ✓（**只摆记账、不读像素** ✗ —— 几十 MB 的解码等真进这一档再做 ✓）。 */
    private fun restoreInfiniteCanvasState(startWithEmptyCanvas: Boolean = false) {
        canvasMode = if (startWithEmptyCanvas) 0 else settings.canvasMode
        infiniteCanvasFile = settings.infiniteCanvasSnapshot.takeIf { it.isNotBlank() }
        infiniteCanvasSource = settings.infiniteCanvasSource.takeIf { it.isNotBlank() }
        val w = settings.infiniteFrameW
        val h = settings.infiniteFrameH
        infiniteFrame = if (w > 0 && h > 0) {
            capInfiniteFrameArea(EditRect(settings.infiniteFrameX, settings.infiniteFrameY, w, h))
        } else {
            null
        }
        // ⚠️ 撤销 / 重做栈**不跨进程** ✓：盘上那张快照没带历史 ✓，
        //    硬摆一个空不了又撤不动的栈只会让按钮"亮着点了没反应" ✗
        infiniteHistory.clear()
        refreshInfiniteHistoryFlags()
    }

    /**
     * **撤销上一步拓展** ✓（用户 2026-09-22 的口径：一次拓展是**花钱 + 破坏性**的 ✓ ——
     * 贴错了只能再花钱盖回去，而且未必盖得回来 ✗ ⇒ 撤销是这一档能不能放心用的分界线 ✓）。
     */
    fun undoInfiniteCanvas() = swapInfiniteHistory(undo = true)

    /** **重做**（撤销撤过头了 ✓）。 */
    fun redoInfiniteCanvas() = swapInfiniteHistory(undo = false)

    /**
     * 撤销 / 重做的共同实现：**一次只换一张快照** ✓。
     *
     * ⚠️ 解码放在 `Dispatchers.Default`（4096² 的 PNG 要读一两秒 ✓），**状态只在主线程写** ✓；
     * ⚠️ 读不出来（文件被删了 / 坏了）⇒ **把栈原样倒回去** ✓ 再如实报一句 ✗ ——
     * 不然栈和画布就**对不上**了（下一次撤销会撤到一张根本没生效的图上 ✗）。
     */
    private fun swapInfiniteHistory(undo: Boolean) {
        if (infiniteRunning) return
        val canvas = infiniteCanvas ?: return
        val currentPath = infiniteCanvasFile ?: run {
            toast(rt(if (undo) "infinite.undoEmpty" else "infinite.redoEmpty"))
            return
        }
        val current = InfiniteCanvasHistory.Entry(currentPath, canvas.originX, canvas.originY)
        val swap = (if (undo) infiniteHistory.undo(current) else infiniteHistory.redo(current))
        if (swap == null) {
            toast(rt(if (undo) "infinite.undoEmpty" else "infinite.redoEmpty"))
            return
        }
        refreshInfiniteHistoryFlags()
        infiniteRunning = true
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.Default) { decodeInfiniteSnapshot(swap.restore.path) }
            if (decoded == null) {
                // 倒回栈（这一步等于"没发生过" ✓）
                if (undo) infiniteHistory.redo(swap.restore) else infiniteHistory.undo(swap.restore)
                refreshInfiniteHistoryFlags()
                toast(rt(if (undo) "infinite.undoFailed" else "infinite.redoFailed"), isError = true)
            } else {
                applyDecodedCanvas(decoded)
                infiniteCanvasFile = swap.restore.path
                deleteInfiniteSnapshots(swap.evicted)
                persistInfiniteState()
                // 撤销 / 重做换了画布内容 ⇒ 工作图又对不上了 ✓（离开这一档时铺回去 ✓）
                infiniteCanvasDirty = true
                status = rt(if (undo) "infinite.undoDone" else "infinite.redoDone")
            }
            infiniteRunning = false
        }
    }

    /** 界面那颗「撤销 / 重做」的可用状态 ✓。 */
    var infiniteCanUndo by mutableStateOf(false)
        private set
    var infiniteCanRedo by mutableStateOf(false)
        private set

    /**
     * **把整张画布编成一张 PNG 存到盘上** ✓（`files/infinite/canvas-<时间戳>.png` ✓，返回路径 ✓）。
     *
     * ⚠️ 必须**在贴回之后**拍 ✗：先拍就少了这一次的成果 ✓（用户切回来看到的是"拓展前的画布"✗）。
     * ⚠️ 这一步是**纯 CPU**（ARGB → PNG 编码 ✓，4096² 要一两秒 ✓）⇒ 调用方一定在
     * `Dispatchers.Default` 上调 ✓，别卡界面 ✓。
     *
     * @param prefix 文件名前缀 ✓。历史快照用默认的 `canvas`（那一族**会被 GC 删** ✓，
     *   见 `deleteInfiniteSnapshot` ✓）；而"铺回共用画布"那份用 `shared` ✓ ——
     *   ⚠️ 它同时是 `current.filePath` ✓（工作图 ✓），**绝不能被快照 GC 删掉** ✗，
     *   名字分开就是为了让 GC 的删除名单（按路径点名 ✓）永远够不着它 ✓。
     */
    private fun encodeInfiniteCanvas(
        canvas: InfiniteCanvas,
        pixels: IntArray,
        prefix: String = "canvas",
    ): String? {
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return runCatching {
            // ⚠️ **不要再 copy 一份** ✗（2026-09-24 改 ✓）：这一份像素在 4096² 时是 **64 MB** ✓，
            //    而"切档时铺回"和"贴回之后存快照"都会走到这儿 ✓ —— 多拷一份就是"明明有内存却编不出来" ✓
            //    （编码本身还要再占一两份 ✓）。数组正好是 w*h 就原样交过去 ✓（`fromArgb` 自己会拷 ✓），
            //    只有"更大/更小"这种异常形状才截一份 ✓。
            val exact = if (pixels.size == width * height) pixels else pixels.copyOf(width * height)
            val native = images.fromArgb(exact, width, height)
            val bytes = images.pngBytes(native)
            native.recycle()
            val dir = File(platform.paths.filesDir, "infinite").apply { if (!exists()) mkdirs() }
            val stamp = System.currentTimeMillis()
            val target = File(dir, "$prefix-$stamp.png")
            // ⚠️ 写盘走 `platform.openOutput` ✗ 不用 `File.writeBytes` ✓（与漫画那几张 scratch 同一个口径 ✓，
            //    手机端文件出口本来就不一定是裸路径 ✓）
            val written = platform.openOutput(target.absolutePath)?.use { it.write(bytes) } != null
            if (!written) return null
            // ⚠️ **原点必须一起存** ✗（理由见 `infiniteCanvasFile` 的注释 ✓）
            val originBytes = "${canvas.originX},${canvas.originY}".toByteArray(Charsets.UTF_8)
            platform.openOutput(File(dir, "$prefix-$stamp.origin").absolutePath)
                ?.use { it.write(originBytes) }
            target.absolutePath
        }.getOrNull()
    }

    /**
     * **往某一方向长一块**（四边把手点一下走这里 ✓）。
     *
     * @return true = 长成了 ✓；**false = 到平台上限** ✓（调用方**如实提示** ✗ 不静默 ——
     *   "点了没反应"是最难查的那种坏 ✗）
     */
    fun extendInfiniteCanvas(side: InfiniteSide, tiles: Int = 1): Boolean {
        val current = infiniteCanvas ?: return false
        val grown = current.extended(side, tiles) ?: return false
        val (dx, dy) = current.pasteOffsetFor(grown)
        infinitePixels = InfiniteCanvasPixels.blit(
            src = infinitePixels,
            srcW = current.width,
            srcH = current.height,
            dstW = grown.width,
            dstH = grown.height,
            dx = dx,
            dy = dy,
        )
        infiniteCanvas = grown
        infiniteRevision++
        // 这些像素**没经过生成落库** ⇒ 工作图还是旧的 ✓（离开这一档时铺回去 ✓，见那个函数 ✓）
        infiniteCanvasDirty = true
        return true
    }

    /**
     * **框算出来的请求方案**（界面显示"将请求 …" ✓ / 生成时照它裁剪 ✓ —— **同一处算** ✓）。
     *
     * ⚠️ 算之前**先把画布在心里扩到装得下这个框** ✓（`expandedToInclude` ✓）：框通常整个在画布外
     * （那正是"往那边拓展"的意思 ✓），直接把它喂给 `FocusedInpaint.plan` 会被 `clampTo` 夹成空矩形 ✗。
     * 这里只**算**、不搬一个像素 ✓ —— 真搬像素在 [generateInfiniteFrame] 里 ✓。
     *
     * @return `(展开后的画布, 方案)`；没框 / 算不出（太小、到上限）⇒ **null** ✓
     */
    fun infiniteFramePlan(
        contextPixels: Int = settings.infiniteContext,
    ): Pair<InfiniteCanvas, FocusedInpaint.Plan>? {
        val canvas = infiniteCanvas ?: return null
        val frame = infiniteFrame ?: return null
        val grown = canvas.expandedToInclude(
            frame.x.toFloat(),
            frame.y.toFloat(),
            frame.w.toFloat(),
            frame.h.toFloat(),
        ) ?: return null
        // ⚠️ 换算必须用 **grown** 的 `pixelX/pixelY` ✗ 不能用 `canvas` 的：往左 / 上长过之后原点变了 ✓
        //    （这正是 `InfiniteCanvas` 里"这一对换算是唯一一处"那条口径的用法 ✓）
        val framePx = EditRect(
            x = grown.pixelX(frame.x.toFloat()).roundToInt(),
            y = grown.pixelY(frame.y.toFloat()).roundToInt(),
            w = frame.w,
            h = frame.h,
        ).clampTo(grown.width, grown.height)
        if (framePx.isEmpty) return null
        // 用户 2026-09-29：「拉框在中心，上下文是围绕拉框的」⇒ 裁剪块 = 框四周**各留一圈** ✓。
        // ⚠️ 不再夹进画布 ✗（原来贴着画布边时上下文只剩一侧）：在"框 + 两圈"那块虚拟画布上算，
        //    再平移回画布坐标 ✓；伸出画布的那部分按透明（→ 白）送 ✓，贴回仍只贴框 ✓。
        // 上下文圈 = 画布底部那条「上下文」滑条（`settings.infiniteContext` ✓，用户 2026-09-29）——
        // 框外留一圈只做参考 ✓；不留的话拓展出来的那一块和旁边**接不上**（模型看不到已有的画 ✗）。
        val context = contextPixels.coerceIn(0, 512)
        val local = FocusedInpaint.plan(
            frame = EditRect(context, context, framePx.w, framePx.h),
            contextPixels = context,
            sourceWidth = framePx.w + context * 2,
            sourceHeight = framePx.h + context * 2,
            // 尺寸全自动算：等比 + **总面积 ≤1024×1024 = 1MP** + 两边保 64 ✓（`InpaintSize.forRect` ✓）
            // ⚠️ 用户口径「请求尺寸**限制在最大免费档上限内**」✓ +「**不靠缩小倍率牺牲画质**」✓
            //    —— `forRect` 是**顶到上限**取最大：小框自动放大 ✓、大框才缩 ✓，两头都不留余量 ✓
            autoSizeByRect = true,
        ) ?: return null
        return grown to local.copy(cropX = framePx.x - context, cropY = framePx.y - context)
    }

    /** 界面上那一行"将请求 …"（没框 / 算不出 ⇒ null ✓，界面据此不画那一行 ✓）。 */
    fun infinitePlanText(contextPixels: Int = settings.infiniteContext): String? {
        val plan = infiniteFramePlan(contextPixels)?.second ?: return null
        return rf("infinite.planInfo", mapOf("w" to plan.requestW, "h" to plan.requestH))
    }

    /**
     * **跑一次聚焦生成**（无限画布那一档的主按钮走这里 ✓，方案 `docs/69` §四 ✓）。
     *
     * 一次点击的完整链路（每一步都是现成的零件 ✓，这里只负责按顺序串起来 ✓）：
     *
     *  1. **扩画布**：把文档扩到装得下这个框 ✓（`expandedToInclude` ✓）——
     *     老像素**原样搬**（`InfiniteCanvasPixels.blit` ✓，不重采样 ⇒ **画质一个字不降** ✓，
     *     这正是用户那句「不要降低画质缩减倍率」的地基 ✓）；到上限就**如实说一句**再收手 ✗ 不静默 ✗；
     *  2. **算方案**：[infiniteFramePlan]（框 → 请求尺寸 ✓，总面积 ≤1MP ✓）；
     *  3. **裁剪 + 蒙版**：抠出"框 + 上下文圈"当底图 ✓；蒙版交给现成的
     *     `maskIntoRequest(fillWholeFrame = true)`（官方"框里留空 = 整框重绘"那条口径 ✓ 一行新算法都不加 ✓）；
     *  4. **请求**：`NaiApi.inpaint`（`action=infill`，和「重做」同一个端点、同一套 payload ✓）；
     *  5. **落库**：`persistGeneratedImages`（历史 + 当前图，一条不落 ✓）；
     *  6. **贴回**：`FocusedInpaint.pasteBack` **只贴框那一块** ✓（上下文那圈丢掉 —— 它只是给模型看的 ✓）——
     *     于是"没画到的地方"仍然是**透明** ✓，看起来就是用户要的那块"凸出一块" ✓。
     *
     * @return 落库那张图的路径（已经进图库 ✓）；失败抛异常，由 [runInfiniteFrame] 记到状态栏 ✓
     */
    private suspend fun generateInfiniteFrame(
        start: InfiniteCanvas,
        frameDoc: EditRect,
    ): String {
        // ---- ⓪ 先把"上一步"的快照备好（撤销要回到的就是它 ✓）----
        // ⚠️ 必须在**扩画布之前** ✗：撤销要连"画布长大"一起撤 ✓ —— 不然撤完画布还是大的、
        //    只是框里那块没了 ✓，那不是用户理解的"撤销上一步" ✗。
        // ⚠️ 盘上那张快照本来就 == 当前画布 ✓（画布只在"贴回 / 撤销 / 重开"时变 ✓，三处都会同步记账 ✓）
        //    ⇒ 直接**沿用**它 ✓，省掉一次整张 PNG 编码 ✓；手上还没有快照（第一次拓展 ✓ /
        //    刚从工作图重开 ✓）才现编一张 ✓ —— 不编的话这一步就没得撤 ✗。
        val beforePath = infiniteCanvasFile ?: withContext(Dispatchers.Default) {
            encodeInfiniteCanvas(start, infinitePixels)
        }
        val previousPath = infiniteCanvasFile

        // ---- ① 扩画布（够不到 ⇒ 到上限了，如实说 ✓）----
        val grown = start.expandedToInclude(
            frameDoc.x.toFloat(),
            frameDoc.y.toFloat(),
            frameDoc.w.toFloat(),
            frameDoc.h.toFloat(),
        ) ?: throw IllegalStateException(rt("infinite.limitReached"))
        if (grown != start) {
            val (dx, dy) = start.pasteOffsetFor(grown)
            infinitePixels = InfiniteCanvasPixels.blit(
                src = infinitePixels,
                srcW = start.width,
                srcH = start.height,
                dstW = grown.width,
                dstH = grown.height,
                dx = dx,
                dy = dy,
            )
            infiniteCanvas = grown
            infiniteRevision++
        }
        val canvas = infiniteCanvas ?: throw IllegalStateException(rt("infinite.empty"))

        // ---- ② 方案（和界面那一行**同一处算** ✓；框 → 画布像素那一步在 `infiniteFramePlan` 里 ✓）----
        val plan = infiniteFramePlan()?.second
            ?: throw IllegalStateException(rt("infinite.frameTooSmall"))

        val inpaintModel = settings.inpaintModel
        val taskParams = params.copy(
            model = inpaintModel,
            width = plan.requestW,
            height = plan.requestH,
        )
        val seed = resolveSeed(params)
        val initialExtras = extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives())
        val genStart = System.currentTimeMillis()

        // ---- ③ 裁剪 + 蒙版（位图活儿放后台 ✓，与「重做」那条路同一套操作 ✓）----
        val prepared = withContext(Dispatchers.Default) {
            val crop = IntArray(plan.cropW * plan.cropH)
            // 数一下框里"已经画过"的像素有几个 —— 决定下面的强度（见 strength 那段注释 ✓）。
            // ⚠️ 框的范围**从 `plan.frameInCrop` 反推** ✓ 不再自己换算一次（`cropX + frameInCrop.x` ✓）——
            //    本仓库"两处各写一遍坐标换算"的记录很难看（见 `InfiniteCanvas` 那条口径 ✓）。
            val frameLeft = plan.cropX + plan.frameInCrop.x
            val frameTop = plan.cropY + plan.frameInCrop.y
            val frameRight = frameLeft + plan.frameInCrop.w
            val frameBottom = frameTop + plan.frameInCrop.h
            var drawnInFrame = 0
            val source = infinitePixels
            for (row in 0 until plan.cropH) {
                val dstRow = row * plan.cropW
                val gy = plan.cropY + row
                val rowIn = gy >= 0 && gy < canvas.height
                for (col in 0 until plan.cropW) {
                    val gx = plan.cropX + col
                    // 裁剪块可以伸出画布（上下文围着框 ✓）⇒ 画布外 = 透明 ✓
                    val argb = if (rowIn && gx >= 0 && gx < canvas.width) source[gy * canvas.width + gx] else 0
                    // ⚠️ 透明像素铺**白**再送 ✗ 不能把 alpha=0 原样发出去：服务端对"全透明底图"
                    //    没有定义（实测会出现整块灰噪 ✓）。白底 = 这张画布上"还没画"那块的替身 ✓，
                    //    和漫画空白画布用的是同一个替身（`comicBlankPixel` ✓）。
                    if ((argb ushr 24) != 0) {
                        crop[dstRow + col] = argb
                        if (gx >= frameLeft && gx < frameRight &&
                            gy >= frameTop && gy < frameBottom
                        ) {
                            drawnInFrame++
                        }
                    } else {
                        crop[dstRow + col] = comicBlankPixel
                    }
                }
            }
            val cropImage = images.fromArgb(crop, plan.cropW, plan.cropH)
            val basePng = MaskCodec.basePng(images, cropImage, plan.requestW, plan.requestH)
            cropImage.recycle()
            // 本批还没有"框里再涂一小块"的界面（随遮罩那批接 ✓）⇒ 交给现成的
            // `maskIntoRequest(fillWholeFrame = true)` 做整框重绘（官方"留空 = 整框重绘" ✓）
            val emptyMask = PixelMask(plan.cropW, plan.cropH, ByteArray(plan.cropW * plan.cropH))
            val focusMask = FocusedInpaint.maskIntoRequest(emptyMask, plan, fillWholeFrame = true)
                ?: throw IllegalStateException(rt("mask.empty"))
            // 吸附 8px 潜空间网格（与「重做」同一口径 ✓，免得重绘边界和画布错开 ✓）
            val requestMask = MaskCodec.inpaintMask(focusMask, plan.requestW, plan.requestH)
                ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
            PreparedInfiniteFrame(
                basePng = basePng,
                maskPng = MaskCodec.maskPng(images, requestMask, plan.requestW, plan.requestH),
                drawnInFrame = drawnInFrame,
            )
        }

        // 强度：框里**一点画都没有**（往空白处拓展 ✓）⇒ 按全重绘 1.0 ✓ ——
        // 留着用户设的强度（例如 0.7）会把白底透出来，看着就是"灰蒙蒙一片没画" ✗；
        // 框**压到了已有画面** ⇒ 按用户设的强度走 ✓，保住原来的笔触（他最在意的那件事 ✓）。
        val strength = if (prepared.drawnInFrame == 0) 1.0 else settings.inpaintStrength
        val noise = settings.inpaintNoise

        // 取消请求按在"发出去之前"这一道闸上 ✓ —— 请求一旦发出，服务端照收照算 ✓，
        // 这里只能拦住"已经排队还没发"的那一次 ✓（和批量那条路同一个口径 ✓）。
        if (cancelRequested) throw kotlinx.coroutines.CancellationException("cancelled")

        val (returned, usedModel) = api.inpaint(
            token = effectiveToken(),
            params = taskParams,
            seed = seed,
            baseImagePng = prepared.basePng,
            maskPng = prepared.maskPng,
            strength = strength,
            noise = noise,
            imageBaseUrl = effectiveImageBase(),
            extras = initialExtras,
            modelMode = settings.modelMode,
        )
        if (returned.isEmpty()) throw IllegalStateException(rt("error.inpaintNoImages"))

        // ---- ⑤ 落库（历史 + 当前图 ✓；宽度记**请求尺寸**：这一块真的是这么多像素 ✓）----
        // ⚠️ 这一档的产物是**画布本身** ✓，不是"普通模式那张工作图" ✗ —— `persistGeneratedImages`
        //    会把 `current` 换成刚存的那张（这里是一小块裁剪 ✓），切回普通模式预览就变成"一小块" ✗。
        //    图照样进历史 / 图库 ✓（`history` 那一份不动 ✓），这里只把"当前工作图"**还原**回去 ✓。
        val currentBefore = current
        val workBefore = workImagePath
        val saved = persistGeneratedImages(
            images = returned,
            params = taskParams,
            seed = seed,
            feature = "infinite",
            model = usedModel,
            width = plan.requestW,
            height = plan.requestH,
            durationMs = System.currentTimeMillis() - genStart,
        )
        // ⚠️ 手上**原来就没有工作图**时（从一张空白画布起步 ✓）**别还原成 null** ✗ ——
        //    还原回去的话 `workImagePath` 就是空的 ✓ ⇒ 普通那一档看不到画布（"还没有图" ✗）、
        //    也没法把这张画布再铺回共用画布 ✓（用户 2026-09-24 报的"生成完图画没变"沾这条 ✓）。
        if (currentBefore != null) current = currentBefore

        // ---- ⑥ 贴回画布：**只贴框那一块** ✓（返回图先缩回裁剪尺寸，和「重做」那条路一致 ✓）----
        // ⚠️⚠️ **贴回成功 = 画面立刻刷新，和"存不存得下那张整图快照"无关** ✗✗（2026-09-24 修 ✓）：
        //    以前 `infiniteRevision++`（= 让显示那张位图作废、重画 ✓）写在 `if (snapshotPath != null)` 里 ✓
        //    —— 于是"贴回成功了、但整张画布的 PNG 编码失败"（大画布上很常见：编码要额外再占
        //    一两份 w*h*4 的内存 ✓）时：像素明明已经贴进去了 ✓，**屏幕上却一个字都不变** ✗✗
        //    ⇒ 用户看到的就是「**画框生成后图画没有拓展**」✓。
        //    现在拆成两件事：**贴回**（决定画面 ✓）与**存快照**（决定"切档 / 重启还在不在" ✓）✗。
        val pasted = withContext(Dispatchers.Default) {
            val io = images
            val decoded = io.decodeBytes(returned.first()) ?: return@withContext false
            val scaled = io.scale(decoded, plan.cropW, plan.cropH, smooth = true)
            if (scaled !== decoded) decoded.recycle()
            val pixels = runCatching { io.pixelsOf(scaled) }.getOrNull()
            scaled.recycle()
            if (pixels == null) return@withContext false
            FocusedInpaint.pasteBack(
                canvas = infinitePixels,
                sourceWidth = canvas.width,
                sourceHeight = canvas.height,
                generated = pixels,
                plan = plan,
            ) != null
        }
        if (!pasted) {
            // 贴不回去**如实说** ✗（以前只有状态行里一行明细 ✓，等于静默 ✓）：
            // 图已经进图库了 ✓，画布没变 ✓ —— 这两句都得让用户看见 ✓。
            toast(rt("infinite.detail.pasteSkipped"), isError = true)
            status = rt("infinite.detail.pasteSkipped")
            appendStatusLine(rt("infinite.detail.pasteSkipped"), emptyList())
            return saved.firstOrNull()?.filePath
                ?: throw IllegalStateException(rt("error.inpaintNoImages"))
        }
        // 贴进去了 ⇒ **立刻让画面跟着变** ✓（这一步不许被任何编码结果挡住 ✗）。
        // ⚠️ 这一对（版本号 +1 / 脏标记）**必须在这里** ✗ 不能等到"快照编码成功"那一步：
        //    贴回之前 `infiniteCanvas` 可能已经换了（`expandedToInclude` ✓，那个分支自己也 +1 过 ✓），
        //    这一次 +1 是替**贴回的像素**作废显示缓存 ✓ —— 两处都 +1 只是多重建一次位图 ✓（幂等 ✓）。
        //  · 脏标记 = 画布上有了"只在这一档里存在"的内容 ✓ ⇒ 离开这一档时铺回共用画布 ✓
        //   （`flushInfiniteCanvasIntoWorkImage` ✓）。
        infiniteRevision++
        infiniteCanvasDirty = true
        // 存快照：整张画布编一张 PNG（纯 CPU ✓，编不出来也不影响画面 ✓ —— 只是"切档 / 重启还在不在"差一档 ✓）
        val snapshotPath = withContext(Dispatchers.Default) {
            encodeInfiniteCanvas(canvas, infinitePixels)
        }
        if (snapshotPath != null) {
            // ---- 上一步进撤销栈 ✓，这一步成为"当前" ✓ ----
            // ⚠️ 老快照若**被撤销栈收下了**就**绝不能删** ✗（删了撤销就撤到一张不存在的图上 ✗）；
            //    没收下（编码失败、或那条就是新版自己 ✓）才删 ✓
            val beforeEntry = beforePath?.let {
                InfiniteCanvasHistory.Entry(it, start.originX, start.originY)
            }
            if (beforeEntry == null || previousPath != beforeEntry.path) {
                deleteInfiniteSnapshot(previousPath)
            }
            if (beforeEntry != null) {
                // 挤出去的 / 重做线断掉的那几张**真删** ✓（不然盘上留一串回不去的图 ✗）
                deleteInfiniteSnapshots(infiniteHistory.commit(beforeEntry))
            }
            refreshInfiniteHistoryFlags()
            infiniteCanvasFile = snapshotPath
            infiniteCanvasSource = workBefore
            // 这一轮的产物**就是画布本身** ✓（`persistGeneratedImages` 刚落库 ✓ ⇒ `current` = 这张画布 ✓）
            // ⇒ 工作图和画布又一致了 ✓，不用等离开这一档再铺 ✓。
            infiniteCanvasDirty = false
            persistInfiniteState()
        }

        val path = saved.firstOrNull()?.filePath
            ?: throw IllegalStateException(rt("error.inpaintNoImages"))
        // 状态行 + 明细：**不静默**地记下这次到底请求了多大、画布长到多大 ✓（日志抽屉可展开 ✓）
        status = rt("infinite.done")
        appendStatusLine(
            rt("infinite.done"),
            buildList {
                add(
                    rf(
                        "infinite.detail.size",
                        mapOf(
                            "fw" to plan.frameInCrop.w,
                            "fh" to plan.frameInCrop.h,
                            "rw" to plan.requestW,
                            "rh" to plan.requestH,
                        ),
                    ),
                )
                add(rf("infinite.detail.canvas", mapOf("w" to canvas.width, "h" to canvas.height)))
                if (grown != start) {
                    add(
                        rf(
                            "infinite.detail.grown",
                            mapOf(
                                "ow" to start.width,
                                "oh" to start.height,
                                "nw" to grown.width,
                                "nh" to grown.height,
                            ),
                        ),
                    )
                }
                add(rt(if (plan.inFreeTier) "infinite.detail.tierFree" else "infinite.detail.tierPaid"))
                if (snapshotPath == null) add(rt("infinite.detail.pasteSkipped"))
            },
        )
        return path
    }

    /** 跑一次无限画布聚焦生成要用到的几块（底图裁剪块、遮罩 PNG、框里已画像素数 ✓）。 */
    private class PreparedInfiniteFrame(
        val basePng: ByteArray,
        val maskPng: ByteArray,
        val drawnInFrame: Int,
    )

    /**
     * **无限画布那一档的主按钮**（界面点「拓展生成」走这里 ✓）。
     *
     * ⚠️ 用**自己的** `infiniteRunning` ✗ 不占全局 `busy` ✓：
     * `busy` 一置上，主按钮再点就变成"排队"（`queueCurrentParams` ✓），
     * 而无限画布这一档排队排的是**文生图**（跟框一点关系都没有 ✗）—— 那是"点了别的功能"。
     */
    fun runInfiniteFrame() {
        if (infiniteRunning) return
        val canvas = infiniteCanvas
        if (canvas == null) {
            toast(rt("infinite.empty"), isError = true)
            return
        }
        val frame = infiniteFrame
        if (frame == null) {
            toast(rt("infinite.runNeedFrame"), isError = true)
            return
        }
        if (effectiveToken().isEmpty()) {
            toast(rt("error.tokenRequired"), isError = true)
            return
        }
        infiniteRunning = true
        cancelRequested = false
        status = rt("infinite.generating")
        viewModelScope.launch {
            try {
                generateInfiniteFrame(canvas, frame)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(rf("status.inpaintFailed", mapOf("error" to cleanError(e))), isError = true)
            } finally {
                runCatching {
                    account = mergeAccountKeepingLast(api.fetchAccount(effectiveToken(), effectiveImageBase()))
                }
                infiniteRunning = false
            }
        }
    }

    /**
     * **把绑在某一格上的绘画层一起挪走**（用户 2026-09-22：「**拖动的是格子，不是图像，要一起拖动**」✓）。
     *
     * 口径：
     *  · 只动 **`kind = PAINT` 且 `panelId == panelId`** 的层 ✓ ——
     *    `GENERATED` 层**本来就会跟着格子走** ✓（显示按 `ComicPageExporter.generatedPlacement`
     *    现算落点 ✓），再挪一次就走两遍 ✗；`BASE`（底板）不属于任何格子 ✗；
     *    气泡 / 文本不是位图 ✗。
     *  · 位移**四舍五入到像素** ✓（帧是像素栅格，和别处一个口径 ✓）；
     *  · 越界的部分**裁掉** ✓（与"格子永远夹在页内"同一个口径 ✓）；
     *  · 写成**新文件**（`layer-<id>-m<时间戳>.png` ✓）再改 `imagePath` ✓ ——
     *    ⚠️ `FileImage` / 缩略图缓存是**按路径**记的 ✗（见 `ui/FileImage.kt` ✓），
     *    写回同一个路径屏幕上不会更新 ✗；旧的那张 scratch 文件顺手删掉 ✓
     *    （不删的话每拖一次留一张页大小的 PNG ✗）。
     *    ⚠️ **只删 scratch 目录里 `layer-` 开头的** ✗：网页画笔 / 图库那些路径不归我们管 ✓。
     *
     * @return 真的挪了几层 ✓（0 = 这一格没有绑着的绘画层 ✓ —— 调用方不必落盘 ✓）
     */
    fun shiftComicPanelLayers(panelId: String, dxPage: Float, dyPage: Float): Int {
        val dx = dxPage.roundToInt()
        val dy = dyPage.roundToInt()
        if (dx == 0 && dy == 0) return 0
        val page = comicBoardPage ?: return 0
        val targets = page.layers.filter { layer ->
            layer.panelId == panelId &&
                layer.kind == ComicLayer.Kind.PAINT &&
                !layer.imagePath.isNullOrBlank()
        }
        if (targets.isEmpty()) return 0
        val scratch = comicScratchDir()
        var moved = 0
        for (layer in targets) {
            val oldPath = layer.imagePath ?: continue
            val decoded = runCatching { images.decodeFull(oldPath) }.getOrNull() ?: continue
            val width = decoded.width
            val height = decoded.height
            val source = runCatching { images.pixelsOf(decoded) }.getOrNull()
            decoded.recycle()
            if (source == null || width <= 0 || height <= 0) continue
            // 平移：`out[y][x] = src[y - dy][x - dx]` ✓（越界留透明 ✓）
            val shifted = IntArray(width * height)
            val x0 = maxOf(0, -dx)
            val x1 = minOf(width, width - dx)
            if (x1 > x0) {
                for (y in 0 until height) {
                    val sy = y - dy
                    if (sy < 0 || sy >= height) continue
                    System.arraycopy(source, sy * width + x0, shifted, y * width + (x0 + dx), x1 - x0)
                }
            }
            val native = images.fromArgb(shifted, width, height)
            val bytes = images.pngBytes(native)
            native.recycle()
            val target = File(scratch, "layer-${layer.id}-m${System.currentTimeMillis()}.png")
            val written = runCatching {
                platform.openOutput(target.absolutePath)?.use { it.write(bytes) } != null
            }.getOrElse { false }
            if (!written) continue
            updateComicBoardPage { current ->
                current.withLayers(
                    current.layers.map { existing ->
                        if (existing.id == layer.id) existing.copy(imagePath = target.absolutePath) else existing
                    },
                )
            }
            val old = File(oldPath)
            if (old.parentFile?.absolutePath == scratch.absolutePath && old.name.startsWith("layer-")) {
                runCatching { old.delete() }
            }
            // 这一层正开着绘画会话 ⇒ 会话里还是旧像素 ✗ ⇒ 按现成那条路重读一遍 ✓
            if (comicPaintLayerId == layer.id) reloadComicPaintLayerFromDisk(layer.id)
            moved++
        }
        if (moved > 0) comicPaintContentChanged()
        // 诊断（`%APPDATA%\NAI Studio\app.log` ✓）：拖完到底挪了几层 ✓ ——
        // 用户 2026-09-22 报「拖动时图片不跟随 / 要切换图层才刷新」时靠这一行定位 ✓。
        logInfo(
            "ComicDrag",
            "[ComicDrag] shift panel=$panelId d=($dx,$dy) bound=${targets.size} moved=$moved",
        )
        return moved
    }

    /**
     * **这一格现在能不能"直接"拖动 / 缩放**（第 ⑭ 批的判据；纯逻辑在
     * [com.kallan.naistudio.models.canBeDirectlyManipulated] ✓，界面不再自己散着写条件 ✗）。
     *
     *  · 还没出过图（`resultPath == null`）→ **可以** ✓（排版期照旧随手拖 ✓）；
     *  · **出过图** → 只有**它已经被选中**才可以 ✓。用户 2026-09-20：
     *    「**格子:生成以后不能随意拖动与改变大小,只有经过套索选中后才能进行操作**」✓。
     *
     * @param selected 这一格**此刻是不是当前选中的那个对象** —— 由界面给 ✓
     *   （选中态住在 `ComicModeUiState.selectedPanelId`，这个类里**不另存一份** ✗：
     *   两份状态早晚会对不上 ✓）。
     *   默认 `false` = "还没选中"，也就是"直接动手"那条口径 ✓。
     * @return 未知 id / 还没有页 → **false** ✓（没有东西可动 ✓）
     */
    fun canDirectlyManipulateComicPanel(panelId: String, selected: Boolean = false): Boolean {
        val panel = comicBoardPage?.panels?.firstOrNull { it.id == panelId } ?: return false
        return panel.canBeDirectlyManipulated(selected)
    }

    /**
     * 「这一格生成过、又没被选中」时用户直接上手拖它 —— 界面据此**如实说一句**该怎么操作 ✓
     * （用户 2026-09-20：「只有经过套索选中后才能进行操作」✓；**绝不静默什么都不做** ✗）。
     */
    fun hintComicObjectSelectFirst() {
        toast(rt("comic.board.selectObject"))
    }

    /**
     * 「选择 / 套索」在底板上按下的**第一步**：这一点（**底板像素** ✓）落在哪个对象上 ✓
     * （纯几何 = `ComicBoardPage.objectAt` ✓，它按"谁盖着谁"的优先级找 ✓）。
     *
     * @return 命中的对象；`null` = 空白 → 调用方照旧走会话的**像素选区** ✓
     */
    fun hitComicObject(pageX: Float, pageY: Float): ComicObjectHit? =
        comicBoardPage?.objectAt(pageX, pageY)

    // ==================================================================
    // 高级漫画 · ④⑤ 气泡 / 文本 / 图层（docs/43 §9.1 §9.2 §9.3）
    // ==================================================================
    //
    // 用户口径：
    //  · 「加**对话气泡**的功能，内置一个图画框，**选定气泡样式后直接拖到相应位置**调大小」✓；
    //  · 「加可以打文本的功能，**可以导入字体**，打字」✓；
    //  · 「一页 = **多图层**（底板 / 生成结果 / 气泡 / 文本各自一层）···**能选中、能拖动、能删**」✓。
    //
    // 分工（和格子那条一模一样，别再另起一套 ✗）：
    //  · 几何 / 编解码 / 图层对齐 = `models/ComicOverlay.kt`（纯函数，有单测 ✓）；
    //  · 图层叠本身 = `models/ComicLayer.kt` 的 [ComicLayerStack]（**所有增删改序都走它** ✓）；
    //  · 写盘时机 = **手势里一帧都不写**，界面在手势 / 编辑结束时调一次 [commitComicBoard] ✓
    //    （每帧写会把 `prefs.json` 磨穿 ✗ —— 见上面那节"什么时候写盘"）。
    //
    // ⚠️ 图层 id **就是**气泡 / 文本的 id（1:1 ✓）—— 于是"这一层画的是谁"根本不用查表，
    //    也就没有"表对不上 → 画出幽灵层"这种洋相 ✓。

    /** 当前页的气泡（顺序 = 创建顺序；**显示顺序**由图层叠说了算，见 [comicBoardLayers]）。 */
    val comicBoardBubbles: List<ComicBubble> get() = comicBoardPage?.bubbles.orEmpty()

    /** 当前页的独立文本。 */
    val comicBoardTexts: List<ComicText> get() = comicBoardPage?.texts.orEmpty()

    /** 当前页的图层叠（**下标 0 = 最底下** ✓，界面按这个顺序从下往上画）。 */
    val comicBoardLayers: List<ComicLayer> get() = comicBoardPage?.layers.orEmpty()

    /**
     * 在底板上放一个气泡（单位 = 底板像素）。
     *
     * @return 新气泡的 id；还没有底板 / 太小（没到最小边长）→ null ✓（界面据此决定要不要切选中）
     */
    fun addComicBubble(style: String, x: Float, y: Float, w: Float, h: Float): String? {
        val page = comicBoardPage ?: return null
        val minSide = ComicOverlayMetrics.minSide(page.baseWidth)
        if (w < minSide || h < minSide) return null

        val bubbleW = w.coerceAtMost(page.baseWidth.toFloat())
        val bubbleH = h.coerceAtMost(page.baseHeight.toFloat())
        val bubble = ComicBubble(
            id = "comic-bubble-" + System.currentTimeMillis().toString() + "-" + page.bubbles.size,
            x = x.coerceIn(0f, (page.baseWidth - bubbleW).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (page.baseHeight - bubbleH).coerceAtLeast(0f)),
            w = bubbleW,
            h = bubbleH,
            style = style.takeIf { ComicBubbleStyles.isKnown(it) } ?: ComicBubbleStyles.ROUND,
            fontSize = ComicOverlayMetrics.defaultFontSize(page.baseWidth),
        )
        // normalizedOverlays：顺手把它的图层补进叠里（**补在最上面** = 新加的盖在别人上面 ✓）
        updateComicBoardPage { it.withBubbles(it.bubbles + bubble).normalizedOverlays() }
        persistComicBoard()
        return bubble.id
    }

    /**
     * 在页面上加一段**独立文本**（不在气泡里 ✓）。
     *
     * 宽高不传（0）就按底板宽度算一个默认大小 —— 界面上"点一下就落一段字"那条路用它 ✓。
     */
    fun addComicText(x: Float, y: Float, w: Float = 0f, h: Float = 0f): String? {
        val page = comicBoardPage ?: return null
        val minSide = ComicOverlayMetrics.minSide(page.baseWidth)
        val textW = (if (w >= minSide) w else ComicOverlayMetrics.defaultTextWidth(page.baseWidth))
            .coerceAtMost(page.baseWidth.toFloat())
        val textH = (if (h >= minSide) h else ComicOverlayMetrics.defaultTextHeight(page.baseHeight))
            .coerceAtMost(page.baseHeight.toFloat())
        val text = ComicText(
            id = "comic-text-" + System.currentTimeMillis().toString() + "-" + page.texts.size,
            x = x.coerceIn(0f, (page.baseWidth - textW).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (page.baseHeight - textH).coerceAtLeast(0f)),
            w = textW,
            h = textH,
            fontSize = ComicOverlayMetrics.defaultFontSize(page.baseWidth),
        )
        updateComicBoardPage { it.withTexts(it.texts + text).normalizedOverlays() }
        persistComicBoard()
        return text.id
    }

    /**
     * 改一个气泡（**不落盘**）。
     *
     * 拖动 / 缩放每帧都会调它，所以这里一个字节都不写盘 ✓（手势结束由界面 commit）。
     * 文字 / 字号 / 颜色 / 对齐 / 字体这些**属性**也走它 —— 界面改完立刻 commit 一次 ✓。
     */
    fun updateComicBubble(id: String, transform: (ComicBubble) -> ComicBubble) {
        updateComicBoardPage { page ->
            if (page.bubbles.none { it.id == id }) {
                page
            } else {
                page.withBubbles(page.bubbles.map { if (it.id == id) transform(it) else it })
            }
        }
    }

    /** 改一段文本（**不落盘**，同 [updateComicBubble]）。 */
    fun updateComicText(id: String, transform: (ComicText) -> ComicText) {
        updateComicBoardPage { page ->
            if (page.texts.none { it.id == id }) {
                page
            } else {
                page.withTexts(page.texts.map { if (it.id == id) transform(it) else it })
            }
        }
    }

    /**
     * 删掉一个气泡 / 文本：**它的图层一起删** ✓。
     *
     * 走 [ComicLayerStack.remove]（不是自己 filter 图层列表 ✗）——
     * 这样"图层怎么加就怎么减"是同一个地方说了算 ✓。
     */
    fun removeComicOverlay(id: String) {
        updateComicBoardPage { page ->
            val stack = ComicLayerStack(page.layers)
            stack.remove(id)
            page.withLayers(stack.all).copy(
                bubbles = page.bubbles.filterNot { it.id == id },
                texts = page.texts.filterNot { it.id == id },
            ).normalizedOverlays()
        }
        persistComicBoard()
    }

    /**
     * 图层排序：`direction > 0` 上移（盖住别人）、否则下移 ✓；到顶 / 到底**什么也不做** ✓。
     *
     * ⚠️ 另有一条不变量：**底板层永远在叠底** —— 别的层不许挪到它下面
     * （挪下去就被底板图盖住、页面上再也看不见了 ✗，而图层列表里还列着它，最费解）。
     * 所以这一下**只有在天平没被弄翻时才生效** ✓（`normalizedOverlays` 也会把底板摆回最下面 ✓）。
     */
    fun moveComicLayer(layerId: String, direction: Int) {
        updateComicBoardPage { page ->
            val stack = ComicLayerStack(page.layers)
            val moved = if (direction > 0) stack.moveUp(layerId) else stack.moveDown(layerId)
            val baseIndex = stack.all.indexOfFirst { it.kind == ComicLayer.Kind.BASE }
            if (!moved || baseIndex > 0) page else page.withLayers(stack.all)
        }
        persistComicBoard()
    }

    /** 图层显隐（隐藏的气泡 / 文本不画出来，但**还在盘上** ✓ —— 不是删除）。 */
    fun setComicLayerVisible(layerId: String, visible: Boolean) {
        updateComicBoardPage { page ->
            val stack = ComicLayerStack(page.layers)
            stack.setVisible(layerId, visible)
            page.withLayers(stack.all)
        }
        persistComicBoard()
    }

    /** 图层透明度（夹在 0~1 ✓，由 [ComicLayerStack.setOpacity] 负责）。 */
    fun setComicLayerOpacity(layerId: String, opacity: Float) {
        updateComicBoardPage { page ->
            val stack = ComicLayerStack(page.layers)
            stack.setOpacity(layerId, opacity)
            page.withLayers(stack.all)
        }
        persistComicBoard()
    }

    /**
     * 同上，但**不落盘** ✓ —— 给透明度**滑杆正在拖**的那几帧用。
     *
     * 为什么必须分开：滑杆每动一下都会回调一次，直接走 [setComicLayerOpacity] 就是
     * "一帧一次 `prefs.json` 全量重写" ✗（和拖动格子那条规矩一模一样 ✓）。
     * 界面在 `onValueChangeFinished` 里调一次 [commitComicBoard] 收口 ✓。
     */
    fun setComicLayerOpacityLive(layerId: String, opacity: Float) {
        updateComicBoardPage { page ->
            val stack = ComicLayerStack(page.layers)
            stack.setOpacity(layerId, opacity)
            page.withLayers(stack.all)
        }
    }

    /**
     * 删掉**任意一层**（第 ⑥ 项图层 UI 的删除按钮走这里 ✓）。
     *
     *  · 气泡 / 文本层：**模型一起删** ✓（走 [removeComicOverlay] —— 那一层的主人就是那个气泡 / 文本 ✓）；
     *  · 生成层：只把这一层从叠里摘掉 ✓（它指向的图**还在图库里**，按 `ComicPanel.resultPath` 那条口径
     *    "旧图不删" ✓ —— 用户随时能在图库里找回那张 ✓）；
     *  · 底板层：**删不掉** ✓（它是叠的底；真删了 `normalizedOverlays` 也会立刻按当前底板再造一层回来 ✓，
     *    与其"点了没反应"不如界面那边就把按钮关掉 ✓）。
     *
     * @return 真的删掉了才 true ✓（界面据此决定要不要清掉选中态 ✓）
     */
    fun removeComicLayer(layerId: String): Boolean {
        val page = comicBoardPage ?: return false
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return false
        if (layer.kind == ComicLayer.Kind.BASE) return false
        // ⚠️ 删掉的正好是**正在画的那一层** → 会话必须跟着结束 ✓（不然会话里那份像素
        //    会在下一次「收工」时把这一层**又写回来** ✗ —— 删了又活，最费解）。
        //    这里**不 commit**：层都没了，写盘就是把它变回来 ✗。
        if (comicPaintLayerId == layerId) {
            comicPaintSessionState = null
            comicPaintLayerId = null
            comicPaintSelection = null
            comicPaintSelectionFloating = false
            releaseComicPaintViewport()
            comicPaintCanUndo = false
            comicPaintCanRedo = false
            // ⚠️ **第 ㊷ 批（2026-09-21）**：用户报「**画布更新要实时，删除图层时，
            //    下面图层的图像会消失，要与画布交互一下才出现**」✓
            //
            // **根因**：会话被清掉了 ✓，但**没有任何人告诉画布"该重画了"** ✗ ——
            //    画布那一帧是靠 `comicPaintFrameTick` / 内容版本号决定要不要重建的 ✓，
            //    这里一个都没动 ⇒ 它照旧显示"上一帧还带着被删那层"的画面 ✓（或者干脆空着 ✓），
            //    直到用户点一下（触发重组 / 别的计数变了）才刷新 ✓ —— 正是他描述的那一下 ✗。
            //
            // **修法**：删完**立刻推一次帧计数** ✓（与 `noteComicPaintFrameChanged()` 同一个口径 ✓）。
            comicPaintVersion++
            noteComicPaintFrameChanged()
        }
        if (layer.kind == ComicLayer.Kind.BUBBLE || layer.kind == ComicLayer.Kind.TEXT) {
            removeComicOverlay(layerId)
            return true
        }
        updateComicBoardPage { current ->
            val stack = ComicLayerStack(current.layers)
            stack.remove(layerId)
            current.withLayers(stack.all)
        }
        persistComicBoard()
        // ⚠️ 第 ㊷ 批：删掉的是**别人**（不是正在画的那一层 ✓）时，会话没被动过 ✓，
        //    但画面里少了一层 ⇒ 同样要**立刻**让画布重画 ✓（见上面那段说明 ✓）。
        comicPaintVersion++
        noteComicPaintFrameChanged()
        return true
    }

    // ==================================================================
    // 高级漫画 · ⑬ **顶部画布编辑器**（第 24 轮；用户 2026-09-20 两条口径）
    // ==================================================================
    //
    // 用户原话：
    //  · 「**顶部放入的是画布编辑器，可以选定图层或新建图层绘画**」✓；
    //  · 「**选定后画出的图不能被选定框拖拽或放大缩小，需要选择或套索才可以**」✓。
    //
    // ## 分工（一块都不重复造 ✗）
    //
    //  · **像素与撤销** = `models/ImageEditSession`（运行条那颗铅笔用的**同一个类** ✓）——
    //    笔画 / 油漆桶 / 吸管 / 模糊 / 选区（抬起→移动·缩放→落回）全是它现成的 ✓；
    //  · **工具枚举** = `models/CanvasTool`（官方那 8 个 ✓）+ `CanvasPalette` ✓；
    //  · **界面** = `screens/ComicModeScreen.kt` 的 `ComicPaintToolbar`（顶部那条 ✓）与
    //    画布上的指针处理 ✓；
    //  · **落盘** = 本文件（`<filesDir>/comic/layer-<layerId>.png` ✓，**绝不写仓库** ✗）。
    //
    // ## 像素内存里的那张图 = **页大小**（`page.baseWidth × baseHeight` ✓）
    //
    //  · BASE 层：就是底板那张图本身 ✓（老数据里底板层没写 imagePath 时退回 `page.basePath` ✓）；
    //  · PAINT 层：页大小、全透明底 ✓；
    //  · **GENERATED 层：现在也能画了** ✓（第 ⑭ 批，用户 2026-09-20「包括生成的图像」✓）——
    //    它本来按"cover 进某一格"的语义存在（见 `ComicPageExporter.plan` ✓），
    //    所以打开会话时先 **materialize**：把那套几何**烘焙进一块页大小的 ARGB** ✓
    //    （看起来和屏幕上**一模一样** ✓），落笔之后它就是普通位图 ✓，
    //    收工时 **kind 转成 `PAINT`** ✓（名字 / `panelId` 留着以便回溯 ✓）。
    //    ⚠️ **如实的副作用** ✗：画过之后这一层就**不再跟着格子走**了 ✓（它变成页大小的位图层 ✓，
    //    拼页 / PSD 导出都按位图层整页画 ✓）—— 旧图 / 旧层不删，用户要对比随时删 ✓。
    //  · 气泡 / 文本**仍然不给画** ✗（它们是矢量层 ✓）—— [beginComicLayerPaint] 返回 false
    //    并**如实 toast 说明原因** ✓（**绝不静默什么都不做** ✗）。
    //
    // ## 什么时候落盘（**拖动过程绝不写盘** ✗）
    //
    //  · **切换图层前** commit 一次 ✓；**离开这一页**（`DisposableEffect.onDispose` ✓）再 commit 一次 ✓；
    //    ⚠️ ⑰ 起这两条就是**全部的自动收口** ✓ —— 用户 2026-09-20：「**画布编辑器不要用完成编辑按钮**」✗，
    //    那颗手动「完成编辑」按钮**整颗删掉了** ✓（别删这两条 ✗：删了就是"刚画的那几笔全丢"✓）。
    //  · 切工具 / 撤销 / 重做**不** commit ✓（它们只改内存 ✓）；
    //  · 导出这一页 / 压平 / 导出 PSD 之前顺手 commit 一次 ✓（不然导出的还是上一次落盘的样子 ✗）。
    //
    // ## 撤销 / 重做只活在这一段会话里（**不落盘** ✗）
    //
    // 历史住在 `ImageEditSession` 的 `EditHistory` 里 ✓ —— 会话一结束（切层 / 离开这一页）
    // 就没了 ✓。**这是如实的口径**：盘上只留"画完之后的那张 PNG"，
    // 下次进来是**新的一段会话**（撤销栈从零开始 ✓），不是接着上次的撤销栈 ✓。
    //
    // ## ⑰ 焦点从哪来（用户 2026-09-20：「不需要点图层确定状态」✓）
    //
    // 进这一页 / 当前聚焦的层没了 → 界面那条 `LaunchedEffect` 调 [autoFocusComicPaintLayer] ✓
    // 自动挑一层（优先当前选中的、否则图层叠最上面的可画层 ✓）；点图层行则只是**
    // 换一个「改哪一层」** ✓（见 `OverlayLayerRow` ✓），**不再是能不能画的前提** ✓。

    /** 当前正在编辑的那一层（`null` = 没在画 ✓）。 */
    var comicPaintLayerId by mutableStateOf<String?>(null)
        private set

    /** 当前绘画会话（像素在这一层；`null` = 没在画 ✓）。 */
    val comicPaintSession: ImageEditSession? get() = comicPaintSessionState

    private var comicPaintSessionState: ImageEditSession? = null

    /** 有没有正在画的一笔（指针还按着 ✓）。 */
    val comicPainting: Boolean get() = comicPaintSessionState?.isDrawing == true

    /**
     * **现在有没有一段活着的会话**（= 有一层被聚焦着、它的像素已经读进内存 ✓）。
     *
     * ⚠️ ⑰ 起这一条**不再是**"画布上的东西让不让路"的判据 ✗ —— 那个判据现在**只有一条**，
     * 就是 [comicPaintSelecting]（**只看当前工具** ✓，和"有没有会话 / 有没有点过图层"无关 ✓）。
     * 用户 2026-09-20（第 ⑰ 批）：「**画布编辑器不要用完成编辑按钮,选了工具就是编辑** ·
     * **不需要点图层确定状态** · **选图层只是确定修改的层** · **软件的交互逻辑按照 ps 来**」✓。
     *
     * 这里留下来的只回答"**现在有没有像素可写**"这一件事 ✓，用在两处**真的需要一段会话**的地方：
     *  · 指针喂笔画之前（`state.comicPaintSession ?: return` 那条 ✓，见 `ComicModeScreen` ✓）；
     *  · **选区选框 / 四角控制点要不要画**（没有选区就没有它 ✓，见 `ComicModeScreen` ✓）。
     */
    val comicPaintOpen: Boolean get() = comicPaintLayerId != null

    /**
     * 这一下是**画画**吗：画笔类工具（[comicPaintTool] ✓）**并且**编辑器开着 ✓。
     * 界面据此决定"要不要把指针喂给会话"✓（选区类工具走另一条，见 [comicPaintSelection] ✓）。
     */
    val comicPaintGestureActive: Boolean
        get() = comicPaintLayerId != null && comicPaintTool.isBrushTool()

    /**
     * **当前工具是「选择 / 套索」吗** —— ⑰ 起这是"画布上的东西能不能动"的**唯一判据** ✓。
     *
     * 用户 2026-09-20（第 ⑰ 批）：「**画布编辑器不要用完成编辑按钮,选了工具就是编辑** ·
     * **不需要点图层确定状态** · **选图层只是确定修改的层** · **软件的交互逻辑按照 ps 来**」✓ ——
     * 于是**工具的类别**（画笔类 / 选择类 ✓，见 `CanvasTool.isBrushTool()` ✓）就决定了画布上
     * 那几条"拖 / 点 = 改内容"的老路（格子拖动与四角缩放、气泡 / 文本的拖动与缩放、尾巴把手、
     * 空白纸上的点与拖 ✓）**让不让路**：
     *  · **画笔类**（画 / 擦 / 桶 / 模糊 / 取色 / 图章）→ 这里是 `false` → 界面那 8 处闸门
     *    `if (!state.comicPaintSelecting) return` **全部早退** ✓ —— **哪怕一层都还没聚焦** ✓
     *    （这正是"选了工具就是编辑"✓：工具一直在手 ✓，不需要先点一下图层 ✗）；
     *  · **选择 / 套索** → `true` → 放行"动东西" ✓（对象仍然**必须先选中**才拖得动 ✓，
     *    第 ⑮ 批那条规则一个字都没动 ✓）。
     *
     * ⚠️ **不再看 [comicPaintOpen]** ✗（第 ⑰ 批改口径的地方，**只此一处** ✓）：旧版是
     * `comicPaintLayerId != null && (SELECT || LASSO)` ✓ —— 前面那半个"有没有会话"的条件正是
     * "**必须先点一下图层才能编辑**"✗ 那条老口径留下的 ✓。留着它就会变成两套判据 ✗
     *（表现就是"选了画笔、还没聚焦到层 → 画布上的格子照样被拖走"✗，那正是用户点名的老毛病 ✓）。
     *
     * ⚠️ 和 [comicPaintOpen] / [comicPaintTool] 一样是**现读**的 getter ✓ ——
     * 界面把它放进 `pointerInput` 的协程里读也没问题 ✓（指针协程不会因为重组而重启，
     * 捕获普通 Boolean 才会变旧 ✗，见 `ComicModeScreen` 那几条说明 ✓）。
     */
    val comicPaintSelecting: Boolean
        get() = comicPaintTool == CanvasTool.SELECT || comicPaintTool == CanvasTool.LASSO

    /**
     * 每画一笔 / 撤销一次 / 落回选区都要 +1 ✓ —— 界面据此**重建显示用的位图** ✓
     * （口径与 `canvasRevision` 逐字一致 ✓：`version` 变了才重建 ✓，**别每帧重建** ✗）。
     */
    var comicPaintVersion by mutableIntStateOf(0)
        private set

    /** 顶部画布编辑器的当前工具（复用 `CanvasTool` 那 8 个 ✓，默认画笔 ✓）。 */
    var comicPaintTool by mutableStateOf(CanvasTool.DRAW)
        private set

    /** 画笔 / 油漆桶的颜色（ARGB ✓）。 */
    var comicPaintColor by mutableIntStateOf(DEFAULT_CANVAS_COLOR)
        private set

    /** 笔刷直径（**底板像素** ✓；和运行条那颗铅笔的笔刷各自独立 ✓）。 */
    var comicPaintBrushPixels by mutableIntStateOf(ImageEditOps.DEFAULT_BRUSH_PIXELS)
        private set

    // ---- 笔尖形状：宽高比 + 角度控制（用户 2026-09-20：「**笔旋转**做出来」✓）----

    /**
     * **笔尖宽高比**（SAI 的 `WxHRatio` 口径 ✓：**1 = 圆 / >1 = 扁** ✓）。
     *
     * 默认 **1（圆）** ✓ ⇒ **默认状态下画面和以前一模一样** ✓
     *（圆的笔尖转不转一个样 ✓，所以"旋转有没有生效"这件事只在用户把这一项拉开之后才看得见 ✓）。
     * 范围 [ImageEditOps.NIB_MIN_RATIO]..[ImageEditOps.NIB_MAX_RATIO] ✓。
     */
    var comicPaintNibRatio by mutableFloatStateOf(1f)
        private set

    /**
     * **角度控制**（SAI 的 `AngleControl` ✓：**0 固定 / 1 自动** 两态 ✓）。
     *
     * ⚠️ 三态里的 `2 笔杆方向` **这一批删掉了** ✓（用户 2026-09-20「取消旋转功能」✓，
     * 网页版 `docs/brush-lab-simple.html` 的下拉框也只有 0 / 1 两条 ✓，见 [NibAngleControl] 的说明 ✓）——
     * 它当初唯一的输入源是笔杆旋转 / 倾角方位角，旋转一取消就成了"有开关、没输入源"的空转 ✗。
     *
     * 默认 **[NibAngleControl.AUTO]（自动 ✓）** —— 与 SAI 的默认值一致 ✓，
     * 而且"自动"在笔尖是圆的时候同样看不出来 ✓ ⇒ 默认画面不变 ✓。
     */
    var comicPaintAngleControl by mutableIntStateOf(NibAngleControl.AUTO)
        private set

    // ---- SAI 笔刷面板的那一整套参数（第 ㉓ 批：面板就是这一份的界面 ✓）----

    /**
     * **当前笔刷**（`BrushSpec` ✓ —— 网页版那一整套 SAI 参数 ✓，**唯一真源** ✓）。
     *
     * ## 为什么必须提到这里来 ✗
     *
     * 上一批 `beginComicPaintStroke` 是**现造**一个 `BrushSpec(wxRatio=…, angleControl=…)` ✓ ——
     * 那两下之后面板上一共有 **37 个字段**要读写（滑杆 / 勾选 / 预设网格 ✓），
     * 现造一个就等于"面板改的数下一笔就没了" ✗。所以提成一个字段 ✓：
     * **面板读它、面板写它、落笔用它** ✓ —— 一处真源，谁也不许在别处再存一份 ✗。
     *
     * ## 和旁边那两个老字段的关系（**不是第二套** ✓）
     *
     * [comicPaintNibRatio] / [comicPaintAngleControl] 是上一批就有的两个入口 ✓，
     * 它们最终要落进同一个 `BrushSpec.wxRatio` / `BrushSpec.angleControl` ✓
     *（换算见 [comicPaintBrush] 这个 getter ✓，存储见 [beginComicPaintStroke] ✓）——
     * 也就是说：**写只写一处（本字段 ✓），读可以读两处（两个老字段给老界面用 ✓）**，
     * 绝不出现"同一件事两个数"✗（那正是 `docs/44` 里点名的坑 ✓）。
     *
     * ⚠️ **不落盘** ✓（和旁边的工具 / 颜色 / 笔刷大小一样 ✓ —— 都是这次会话里的东西 ✓）：
     * 所以它**不进 `prefs.json`** ✓，重启回到网页 `DEFAULTS` ✓（= 本仓库一直以来的手感 ✓）。
     */
    var comicPaintBrush by mutableStateOf(BrushSpec())
        private set

    /**
     * **最小半径**（= SAI 的「最小大小%」✓，0..1 ✓，默认 [PEN_BRUSH_MIN_FACTOR] = **0.25** ✓）。
     *
     * ⚠️ 它**不在 `BrushSpec` 里** ✗ —— 它是**应用侧**的压感参数（管"压力怎么变成半径" ✓，
     * 见 `StrokeSpec.minRadiusRatio` 的说明 ✓）；而 [BrushSpec.minDensity] 是**引擎侧**的
     * 「最小浓度」（管"压力怎么变成浓淡" ✓）。**两条并列、各管一头** ✓（`docs/44` §5 ✓）。
     *
     * 之所以要提到字段上 ✗：面板上「最小大小%」这一根滑杆要能改它 ✓，
     * 而这以前是 `AppState` 里的一个私有常量（`PEN_BRUSH_MIN_FACTOR` ✓）——
     * 留成常量的话这根滑杆就是"点了没反应" ✗（本批硬规矩点名不许 ✓）。
     */
    var comicPaintMinRadiusRatio by mutableFloatStateOf(PEN_BRUSH_MIN_FACTOR)
        private set

    /**
     * **压感曲线**（1 = 线性 ✓，默认 [PEN_PRESSURE_CURVE] = **1** ✓）。
     *
     * 和 [comicPaintMinRadiusRatio] 一样是**应用侧**那一档 ✓（`StrokeSpec.pressureCurve` ✓）——
     * 面板上那根「压感曲线」滑杆读写的就是它 ✓（以前是一个私有常量 ✗，那样滑杆就是"点了没反应" ✗）。
     * 默认值不变 ⇒ **默认手感一个字都没变** ✓。
     */
    var comicPaintPressureCurve by mutableFloatStateOf(PEN_PRESSURE_CURVE)
        private set

    /**
     * **输入侧稳定器强度（EMA ✓）** —— 第 ㉚ 批照 `docs/brush-lab-simple.html` 接上 ✓。
     *
     *  · 网页 `:156` 默认 **35** ✓（`stabilize: 35` ✓）、滑杆 `:670` 范围 **0..95** ✓；
     *  · 公式 `:747` `k = 1 - stabilize/100` ⇒ 35% 时 **k = 0.65** ✓，
     *    `:751-752` `smooth += (p - smooth) * k` ✓ —— 逐步实现见 [StrokeStabilizer] ✓；
     *  · 它是**用户级偏好** ✓（不在 [BrushSpec] 里 ⇒ 网页的 `KEEP_UI_KEYS` 那条语义
     *    "换预设不该把它冲掉" ✓ 在这里自然成立 ✓，见 [applyComicPaintBrush] ✓）。
     *
     * ⚠️ 真值对照那条路（`SimpleBrushParityTest`）**不开**它 ✗ —— 判据①的 928 颗是
     * **没有稳定器**那条路抽出来的 ✓（见 `StrokeSpec.stabilizePercent` 的说明 ✓）。
     */
    var comicPaintStabilize by mutableFloatStateOf(StrokeStabilizer.DEFAULT_STABILIZE_PERCENT)
        private set

    /** 稳定器滑杆（网页 `:670` `min:0 max:95 step:1` ✓）—— 越界一律收敛 ✓。 */
    fun chooseComicPaintStabilize(percent: Float) {
        comicPaintStabilize = StrokeStabilizer.clampPercent(percent)
    }

    /**
     * **整套换一支笔**（预设网格那一格 ✓）—— 口径照网页 `applyFull()` ✓：
     *
     *  · [spec] **整套装进去** ✓（不是"打几个补丁" ✗：换预设 = 换一整套 SAI 参数 ✓）；
     *  · **保留**用户级偏好 ✓：颜色 / 橡皮 / 稳定器 / 混合模式 / 当前工具
     *    —— 它们都不在 [BrushSpec] 里 ✓，所以只要**别去碰它们**就自然保留了 ✓
     *    （这正是"一处真源"省下来的事 ✓，不需要网页那张 `KEEP_UI_KEYS` 表 ✓）。
     *    （第 ㉔ 批：面板上的**分类胶囊**已删 ✗，这一条里不再有"分类" ✓。）
     *
     * ⚠️ 一整套里**不含**笔刷大小 `size` ✗ —— 大小在本仓库是 [comicPaintBrushPixels] ✓，
     * 预设那一档的大小由面板**另外**调 [setComicPaintBrush] 落下去 ✓（见面板的 `choosePreset` ✓）。
     *
     * @return 真的换了才 true ✓（和给进去的那一份逐字段相同 = 没必要动 ✓）
     */
    fun applyComicPaintBrush(spec: BrushSpec): Boolean {
        val next = spec.sanitized()
        if (next == comicPaintBrush) return false
        comicPaintBrush = next
        return true
    }

    /**
     * **下一笔的随机数 seed**（每次落笔 +1 ✓）—— 见 [StrokeSpec.seed] ✓。
     *
     * 为什么不直接用系统时间 / 全局随机 ✗：用户口径是「**随机数要可复现**」✓ ——
     * 拿"第几笔"当 seed，同一串操作重放出来就是同一份抖动 ✓（而两笔之间又不会长得一模一样 ✓）。
     * 这一批界面还设不了这些参数（面板下一批 ✓），所以它现在只影响"以后打开抖动类参数时"的样子 ✓。
     */
    private var comicPaintStrokeSeed = 0L

    // ---- 数位板 / 触控笔（用户 2026-09-20：「compose-stylus 先接这个」✓）----

    /**
     * **笔的只读状态行**（画布编辑器工具栏上那一行 ✓ —— 用户第 4 条：「**让用户能亲眼看到**」✓）。
     *
     * 它是**一个字符串** ✓ —— 不是每帧写盘、也不是每帧重建位图 ✗（用户点名的那两条 ✗）。
     * 由 [notePenSample] 写 ✓，两道闸把它压到"一秒几次"级别 ✓（见那边的说明 ✓）。
     *
     * `null` = **一次笔事件都还没收到过** ✓（这台机器没有数位板信号 / 手机端默认实现 ✓）——
     * 界面据此显示"没有信号"那一句 ✓，**而不是**把压力显示成恒等于 1 ✗（用户点名的坑 ✓）。
     */
    var comicPenStatus by mutableStateOf<String?>(null)
        private set

    /** 上一次写状态行的时刻（毫秒 ✓）—— [PEN_STATUS_MIN_INTERVAL_MS] 的节流用 ✓。 */
    private var comicPenStatusAt = 0L

    /**
     * 收到一次笔采样 → 更新那行只读状态 ✓（由 `Platform.penInputModifier` 的回调喂进来 ✓）。
     *
     * ## 两道闸（用户口径：**别每帧写盘 / 别每帧重建位图** ✗ —— 这只是个字符串 ✓）
     *
     *  · **量化**：压力按 2 位小数、倾斜按整度 ✓ —— 纸面上这对人眼已经是极限，
     *    再多的位数只会让字符串每毫秒都"不一样" ✗（那才是真的每帧重组 ✗）；
     *  · **节流**：[PEN_STATUS_MIN_INTERVAL_MS] —— 数位板一秒能报上百次 ✗，
     *    这行字一秒变几次就够了 ✓。
     *
     * ⚠️ 这里**只碰这一个字符串** ✓：没有 `kv.edit()`（不写盘 ✓）、没有碰 [comicPaintVersion]
     * （不重建位图 ✓）、没有进历史 ✓。
     */
    fun notePenSample(sample: PenSample) {
        val pressure = if (sample.penTool) twoDecimals(sample.pressure) else PEN_NO_VALUE
        val tool = when {
            sample.penTool && sample.eraser -> rf("comic.board.penToolEraser", emptyMap())
            sample.penTool -> rf("comic.board.penToolPen", emptyMap())
            else -> rf("comic.board.penToolMouse", emptyMap())
        }
        val signal = rf(
            if (sample.penTool) "comic.board.penSignalOn" else "comic.board.penSignalOff",
            emptyMap(),
        )
        val next = rf(
            "comic.board.penStatus",
            mapOf(
                "pressure" to pressure,
                "tiltX" to sample.tiltX.roundToInt(),
                "tiltY" to sample.tiltY.roundToInt(),
                // 笔杆旋转（度 ✓ 0..360 ✓）—— 用户 2026-09-20：「**笔旋转**做出来」✓。
                // ⚠️ 电脑上多半一直是 0（库的 RTS 后端没请求"旋转"那个量 ✓，见
                // `DesktopPlatform.toPenSample` 的说明 ✓）—— 这一格就是让用户**亲眼看到**
                // "到底报没报" ✓（别猜 ✓）。没有笔信号时照旧由 `PEN_NO_VALUE` 占位 ✓。
                "rotation" to if (sample.penTool) sample.rotation.roundToInt().toString() else PEN_NO_VALUE,
                "tool" to tool,
                "signal" to signal,
            ),
        )
        if (next == comicPenStatus) return
        val now = System.currentTimeMillis()
        if (comicPenStatus != null && now - comicPenStatusAt < PEN_STATUS_MIN_INTERVAL_MS) return
        comicPenStatusAt = now
        comicPenStatus = next
    }

    /**
     * 压力 → **这一笔的等效笔刷直径**（底板像素 ✓）—— `滑杆值 × (0.25 + 0.75 × pressure)` ✓。
     *
     * ⚠️ 逐点压感那一批（用户 2026-09-20）之后，**落笔已经不走这里了** ✗：
     * `beginComicPaintStroke` 现在给会话的是**基础直径**（滑杆值 ✓），
     * 每个笔尖的压力换算在 [ImageEditOps.brushRadiusPixels] 里**逐点**做 ✓
     *（那边的默认参数 = `0.25` + 曲线 `1.0` ✓，**和这里逐字同一条公式** ✓）。
     *
     * 留着它是**一处口径**的出口 ✓（"给定一个恒定压力，这一笔等效多粗"✓）——
     * 单测 / 以后要显示"当前压感笔宽"的界面都可以读它 ✓。**别再拿它去乘第二遍压力** ✗。
     */
    fun comicPaintBrushPixelsForPressure(pressure: Float): Int =
        ImageEditOps.normalizedBrushPixels(
            (ImageEditOps.brushRadiusPixels(
                brushPixels = comicPaintBrushPixels,
                pressure = pressure,
                minRadiusRatio = PEN_BRUSH_MIN_FACTOR,
                pressureCurve = PEN_PRESSURE_CURVE,
            ) * 2f).roundToInt(),
        )

    var comicPaintCanUndo by mutableStateOf(false)
        private set
    var comicPaintCanRedo by mutableStateOf(false)
        private set

    /** 当前**选区**（矩形 / 套索，归一化坐标）；`null` = 没有框 ✓。 */
    var comicPaintSelection by mutableStateOf<SelectionShape?>(null)
        private set

    /** 选区里的像素**已经抬起来了**（能拖 / 能缩，还没落回 ✓）。 */
    var comicPaintSelectionFloating by mutableStateOf(false)
        private set

    /** 浮动块本体（界面算缩放锚点要用 ✓）。 */
    val comicPaintFloating: FloatingSelection? get() = comicPaintSessionState?.floatingSelection

    /**
     * **视口那一帧**（第 ㉜a 批 ✓ —— 取代㉔/㉖ 的"整页层位图 + 小块叠加"整条链 ✓）。
     *
     * ## 为什么是"视口"而不是"整页层位图"
     *
     * ㉔/㉖ 那条链的病根：层位图是**页大小**（A4 = 2480×3508 = 8.7 MP / 33 MB），
     * 每重建一次就是 `toComposeImageBitmap` 的一次**逐像素拷贝 + 33 MB 纹理上传**
     * （实测 7.55 ms/帧 ✓）⇒ 只能靠"活笔画小块"绕着走 ✓。
     * 现在层真源是一张 `Surface`（`ImageEditSession` ✓），显示这一半只做**视口那么大**的活 ✓：
     *
     * ```
     * ① 可见矩形（页像素）→ readPixels 读成一块 Bitmap（实测 525×743 = 0.076 ms ✓）
     * ② blit 到视口大小的 Surface（实测 LINEAR 0.75 ms ✓）
     * ③ 视口 Surface → Compose 位图（0.785 ms ✓，**只与视口像素数成正比** ✓）
     * ```
     *
     * ⚠️⚠️ **不许**用 `makeImageSnapshot()` 当每帧的源 ✗✗ —— ㉜a 批实测（`SurfaceCanvasProbeTest`）：
     * 快照本身 0.001 ms（写时复制 ✓），但**拿着快照继续往表面上画**会触发**整页拷贝**：
     * "每颗笔尖之前快照一次" = **7.04 ms/颗**（纯画只要 0.0102 ms，**×690** ✗✗）、
     * "快照之后第一次画" = **6.9 ms** ✓ ⇒ 每帧快照 = 每帧拷 33 MB ✗（正是交接单要避开的那个坑 ✓）。
     * 所以这里走**区域回读**（①）✓：代价只跟着**可见区域**走，不跟页大小走 ✓。
     */
    private var viewportSurface: Surface? = null
    private var viewportReadBitmap: Bitmap? = null
    private var viewportReadW = 0
    private var viewportReadH = 0
    private var viewportFrameKey: Long = Long.MIN_VALUE
    private var viewportFrameBitmap: ImageBitmap? = null

    /**
     * ⚠️ **必须**是**快照状态** ✗：界面在 `Canvas` 的 **draw 阶段**读它 ——
     * 读快照状态只会让**那一次绘制**失效（不触发重组 ✓），这正是"每颗笔尖都重画、但不重组"的关键 ✓。
     */
    private var comicPaintFrameTickState by mutableIntStateOf(0)

    /** 走**快路**（表面 Pixmap → 直接缩放 ✓，代价 ∝ 屏幕像素 ✓）建出来的视口帧数（第 ㉝③ 批 ✓）。 */
    var comicPaintViewportFramesDirect: Int = 0
        private set

    /**
     * 走**兜底老路**（先整块 readPixels 再 blit ✗，代价 ∝ 可见页面积 ✗）建出来的视口帧数 ——
     * **生产上必须是 0** ✓（不是 0 就说明快路没走通 ⇒ 缩小时会卡 ✓，见 [comicPaintViewportFrame] ✓）。
     */
    var comicPaintViewportFramesFallback: Int = 0
        private set

    // ---- 诊断（第 ㉝③ 批 ✓）：上一帧**快路**里三个子步骤各花了多少**微秒** ----
    // 为什么要它：`ComicZoomFrameCostProbeTest` 实测"整条快路 21 ms"，而把同样几步
    // 单独拿出来量只有 3.5 ms ✗ ⇒ 不拆开就只能猜 ✓（这就是拆开的那三个数 ✓）。
    /** 表面 → Pixmap 别名 + 取可见区视图（微秒 ✓）。 */
    var viewportMicrosPeek: Long = 0
        private set

    /** `scalePixels`（缩放那一步 ✓，微秒 ✓）。 */
    var viewportMicrosScale: Long = 0
        private set

    /** `Image.makeFromBitmap` + `toComposeImageBitmap`（Compose 那一份 ✓，微秒 ✓）。 */
    var viewportMicrosCompose: Long = 0
        private set

    /** 界面在 **draw 阶段**读它：每颗笔尖 +1 ⇒ **只让视口重绘**、不触发重组 ✓。 */
    val comicPaintFrameTick: Int get() = comicPaintFrameTickState

    /**
     * **正在编辑的那一层的「活缩略图」**（第 ㊷ 批 2026-09-21 ✓）。
     *
     * ## 为什么要有它（用户口径 ✓）
     *
     * > 「**图层需要每画一笔都更新图层的略缩图，略缩图为画布（如画布是 a4，略缩图就是 a4 大小。）**」
     * > 「**略缩图的画面上的图像和画布对应**（如有根线在画布的右上角，那根线也在略缩图的右上角）」
     *
     * 原来的缩略图走 [ComicLayer.imagePath] **读盘** ✓ —— 而那条路只在**收工时**才写盘 ✗
     *（见 `finishComicLayerPaint` ✓）⇒ 画的当中缩略图一直是旧的 ✓（用户报的就是这个 ✓）。
     *
     * ## 口径（两条都要 ✓）
     *
     *  · **跟着每一笔更新** ✓：界面在 **draw 阶段**读 [comicPaintFrameTick] ✓（与视口同一套 ✓），
     *    所以每颗笔尖它都是一个新数 ⇒ 缩略图跟着重画 ✓（**不触发整页重组** ✗）；
     *  · **比例 = 整页**（A4 就是 A4 ✓）⇒ 缩略图里画的是**整页**的等比缩小 ✓，
     *    线在右上角就出现在右上角 ✓（**不是**裁一个角 / 不是 `Crop` ✗）。
     *
     * @param layerId 要哪一层；**不是当前编辑层**时返回 null ✓（调用方回落去读盘 ✓）。
     * @param maxSide 缩略图最长边（px ✓）—— 哪一边长按哪一边缩 ✓，保持整页比例 ✓。
     */
    fun comicPaintLiveThumbnail(layerId: String, maxSide: Int): ImageBitmap? {
        if (layerId != comicPaintLayerId) return null
        val session = comicPaintSessionState ?: return null
        val w = session.width
        val h = session.height
        if (w <= 0 || h <= 0 || maxSide <= 0) return null
        // ⚠️ **第 ㊻ 批：必须缓存** ✗ —— 不缓存的话**每一次重组**都要重缩一整页 ✗
        //    （A4 是 2480×3508 ⇒ 一次几十 ms ✓，而重组每帧都在发生 ✓ ⇒ 界面直接卡死 ✗）。
        //    口径 = `(层 id, 页尺寸, maxSide, 帧计数)` ✓ —— 帧计数每颗笔尖 +1 ✓
        //    ⇒ "每画一笔更新一次"✓，而同一帧内多次读取**只算一次** ✓。
        val cached = comicLiveThumb
        if (cached != null &&
            comicLiveThumbLayerId == layerId &&
            comicLiveThumbW == w &&
            comicLiveThumbH == h &&
            comicLiveThumbMax == maxSide &&
            comicLiveThumbTick == comicPaintFrameTickState
        ) {
            return cached
        }
        // 等比缩到 maxSide 之内 ✓（整页都在 ✓ —— 这就是"a4 的缩略图还是 a4"✓）
        val scale = minOf(maxSide.toFloat() / w, maxSide.toFloat() / h)
        val tw = (w * scale).roundToInt().coerceAtLeast(1)
        val th = (h * scale).roundToInt().coerceAtLeast(1)
        val built = runCatching {
            // 与视口那条路**同一个做法** ✓（`pixels` → Bitmap → Pixmap.scalePixels → Compose 位图 ✓）
            val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
            val bytes = ByteArray(w * h * 4)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asIntBuffer().put(session.pixels, 0, w * h)
            val src = Bitmap()
            if (!src.allocPixels(info)) return null
            src.installPixels(info, bytes, w * 4)
            val dst = Bitmap()
            if (!dst.allocPixels(ImageInfo(tw, th, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))) {
                src.close(); return null
            }
            val srcPixmap = src.peekPixels()
            val dstPixmap = dst.peekPixels()
            if (srcPixmap == null || dstPixmap == null) {
                src.close(); dst.close(); return null
            }
            if (!srcPixmap.scalePixels(dstPixmap, SamplingMode.LINEAR)) {
                src.close(); dst.close(); return null
            }
            val image = Image.makeFromBitmap(dst)
            val out = image.toComposeImageBitmap()
            image.close(); src.close(); dst.close()
            out
        }.getOrNull()
        // 第 ㊻ 批：**记下这次的输入** ✓（下次同样的输入直接给缓存 ✓，见函数开头那段说明 ✓）
        comicLiveThumb = built
        comicLiveThumbLayerId = layerId
        comicLiveThumbW = w
        comicLiveThumbH = h
        comicLiveThumbMax = maxSide
        comicLiveThumbTick = comicPaintFrameTickState
        return built
    }

    // ---- 第 ㊻ 批：活缩略图的缓存（**必须缓存** ✗ —— 见 [comicPaintLiveThumbnail] 的说明 ✓）----
    private var comicLiveThumb: ImageBitmap? = null
    private var comicLiveThumbLayerId: String? = null
    private var comicLiveThumbW = 0
    private var comicLiveThumbH = 0
    private var comicLiveThumbMax = 0
    private var comicLiveThumbTick = Int.MIN_VALUE

    /** 实测：视口那一帧**真的重建了几次**（结构性计数 ✓，单测钉着 ✓）。 */
    var comicPaintViewportFrames: Int = 0
        private set

    /** 实测：从层表面**整页**回读了几次（显示这条路**必须是 0** ✓ —— 显示只读可见区 ✓）。 */
    var comicPaintPageReadbacks: Int = 0
        private set

    /** 视口最后一帧的尺寸（诊断 ✓）。 */
    var comicPaintViewportWidth: Int = 0
        private set
    var comicPaintViewportHeight: Int = 0
        private set

    /**
     * 取**视口那一帧**（可见区域已经缩到视口分辨率 ✓）。
     *
     * @param srcX/srcY/srcW/srcH 可见矩形（**页像素** ✓，调用方已经夹进页 ✓）
     * @param dstW/dstH 视口像素（= 可见矩形在屏幕上的**设备像素**大小 ✓）
     * @param revisionKey 内容版本（`pixelsRevision` / 笔画 tick 任一变了就重建 ✓）
     * @return 建不出来就是 null ✓（调用方退回"这一层画不出来"的老样子 ✓，绝不崩 ✗）
     */
    fun comicPaintViewportFrame(
        srcX: Int,
        srcY: Int,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        revisionKey: Long,
    ): ImageBitmap? {
        val session = comicPaintSessionState ?: return null
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return null
        val key = (revisionKey * 31 + srcX) * 31 + srcY
        val key2 = ((key * 31 + srcW) * 31 + srcH) * 31 + dstW
        val full = key2 * 31 + dstH
        if (viewportFrameBitmap != null && viewportFrameKey == full) return viewportFrameBitmap
        val t0Micros = System.nanoTime()
        val surface = session.layerSurfaceForDisplay() ?: return null

        // ---- ①′ **快路**（第 ㉝③ 批 ✓）：**表面 → Pixmap（别名，零拷贝 ✓）→ 取可见区视图
        //      → 直接缩放进 `dstW×dstH` 的位图** ✓ ----
        //
        // 为什么不能先 readPixels 再缩（上一版那条 ✗）：**那次 readPixels 的大小 = 可见区在
        // "页像素"里的面积** ✗ —— 缩小时可见区在页坐标里是**整页**（A4 = 2480×3508 = 34.8 MB），
        // 而屏幕上只有 ~0.6 MP ⇒ 每帧白拷 34 MB ✓（实测 p50 **20.5 ms/帧** ✗、
        // 放大档只要 1.18 ms ⇒ **17.4×** ✗；一笔 40 段 p95 **33.1 ms** ✗ = 用户说的「卡」✓）。
        //
        // `Pixmap.scalePixels` 的采样量**只跟目标像素走** ✓（线性滤波每个目标像素只读源上
        // 邻近的那几个点 ✓）⇒ 缩小时不再碰那 34 MB ✓；`Pixmap` 是**别名**（`peekPixels` ✓）、
        // `extractSubset` 也是**视图**（不拷贝 ✓）⇒ 整条快路不再有"跟页面积成正比"的拷贝 ✓。
        //
        // 画面口径**一个字没变** ✓：还是 `SamplingMode.LINEAR` 的那一次缩放（与旧路那次 blit 同一个
        // 采样模式 ✓），只是**不再先全分辨率拷一份** ✓。
        val srcPixmap = ensureViewportSourcePixmap()
        if (srcPixmap != null && surface.peekPixels(srcPixmap)) {
            val tPeek = System.nanoTime()
            val subset = ensureViewportSubsetPixmap()
            if (subset != null && srcPixmap.extractSubset(subset, IRect.makeXYWH(srcX, srcY, srcW, srcH))) {
                val dst = ensureViewportReadBitmap(dstW, dstH) ?: return null
                // 尺寸已经是**精确**的 ✓ ⇒ 直接往它里面缩 ✓（视图那一层不需要了 ✓）
                val dstPixmap = dst.peekPixels()
                if (dstPixmap != null && subset.scalePixels(dstPixmap, SamplingMode.LINEAR)) {
                    val tScale = System.nanoTime()
                    val image = runCatching { Image.makeFromBitmap(dst) }.getOrNull()
                    if (image != null) {
                        val frame = runCatching { image.toComposeImageBitmap() }.getOrNull()
                        image.close()
                        val tCompose = System.nanoTime()
                        viewportMicrosPeek = (tPeek - t0Micros) / 1000
                        viewportMicrosScale = (tScale - tPeek) / 1000
                        viewportMicrosCompose = (tCompose - tScale) / 1000
                        if (frame != null) {
                            viewportFrameBitmap = frame
                            viewportFrameKey = full
                            comicPaintViewportFrames++
                            comicPaintViewportFramesDirect++
                            comicPaintViewportWidth = dstW
                            comicPaintViewportHeight = dstH
                            return frame
                        }
                    }
                }
            }
        }

        // ---- ①/②/③ **兜底老路**（表面上拿不到 Pixmap 时才走 ✓，例如非光栅表面 ⚠️）----
        // ⚠️ 这条路**代价 ∝ 可见页面积** ✗ ⇒ 计数器 `comicPaintViewportFramesFallback` 必须一直是 0 ✓。
        val bmp = ensureViewportFallbackBitmap(srcW, srcH) ?: return null
        if (!surface.readPixels(bmp, srcX, srcY)) return null
        val vp = ensureViewportSurface(dstW, dstH) ?: return null
        val image = runCatching { Image.makeFromBitmap(bmp) }.getOrNull() ?: return null
        try {
            vp.canvas.drawImageRect(
                image,
                SkRect.makeLTRB(0f, 0f, srcW.toFloat(), srcH.toFloat()),
                SkRect.makeLTRB(0f, 0f, dstW.toFloat(), dstH.toFloat()),
                SamplingMode.LINEAR,
                viewportBlitPaint,
                true,
            )
        } catch (t: Throwable) {
            return null
        } finally {
            image.close()
        }
        // ---- ③ 视口 → Compose 位图（代价 ∝ **视口像素数** ✓）----
        //
        // ⚠️⚠️ 必须**按 `dstW×dstH` 裁**（第 ㉜b 批修的实机 bug ✗✗）：`vp` 是**复用**的
        //（[ensureViewportSurface]：比这次要的大就直接用旧的 ✓，省一次分配 ✓）——
        // 而上面那次 blit 只覆盖 `(0,0,dstW,dstH)` 那一块 ⇒ **整张快照**里还留着上一帧
        // 剩下的一圈旧内容 ✗。调用方（`ComicPaintViewportLayer`）是拿
        // `IntSize(frame.width, frame.height)` 当源矩形、把它摆进"可见矩形那么大"的 dst 里 ⇒
        // **多出来的那一圈会把画面按比例压扁 / 挪位** ✗：用户 2026-09-21 实机报的
        // 「**放大缩小画布，线条会乱飞，没有跟随画布**」就是它
        //（`ComicCanvasZoomAnchorTest` 实测：2.0 倍之后缩回 1.0 倍，笔迹包围盒左右各差 24 / 55 px ✗；
        //  再缩到 0.5 倍差 22~70 px ✗）。
        // ⇒ 只取"这一次真的画满了的那一块" ✓（`makeImageSnapshot(IRect)` ✓，不拷整张 ✓）。
        val snapshot = runCatching {
            vp.makeImageSnapshot(IRect.makeLTRB(0, 0, dstW, dstH))
        }.getOrNull() ?: return null
        val frame = runCatching { snapshot.toComposeImageBitmap() }.getOrNull()
        snapshot.close()
        viewportFrameBitmap = frame
        viewportFrameKey = full
        if (frame != null) {
            comicPaintViewportFrames++
            comicPaintViewportWidth = dstW
            comicPaintViewportHeight = dstH
        }
        return frame
    }

    private val viewportBlitPaint = Paint().apply { blendMode = BlendMode.SRC }

    /** 快路用的"表面别名"（`peekPixels` 的落点 ✓，不分配像素 ✓，只建一次 ✓）。 */
    private var viewportSourcePixmap: org.jetbrains.skia.Pixmap? = null

    /** 快路用的"可见区视图"（`extractSubset` 的落点 ✓，不分配像素 ✓，只建一次 ✓）。 */
    private var viewportSubsetPixmap: org.jetbrains.skia.Pixmap? = null

    private fun ensureViewportSourcePixmap(): org.jetbrains.skia.Pixmap? {
        viewportSourcePixmap?.let { return it }
        val p = runCatching { org.jetbrains.skia.Pixmap() }.getOrNull() ?: return null
        viewportSourcePixmap = p
        return p
    }

    private fun ensureViewportSubsetPixmap(): org.jetbrains.skia.Pixmap? {
        viewportSubsetPixmap?.let { return it }
        val p = runCatching { org.jetbrains.skia.Pixmap() }.getOrNull() ?: return null
        viewportSubsetPixmap = p
        return p
    }

    /**
     * 快路的目标位图（**尺寸必须精确 = `dstW×dstH`** ✓，第 ㉝③ 批 ✓）。
     *
     * ⚠️⚠️ 这里**不许**用"只增不减"（老写法 ✗，本批实测踩到两次 ✓）：
     *  · `scalePixels` 会缩放到**整张目标**里 ⇒ 要 143×203 也照缩 1170×1655 ✗（**16.2 ms/帧** ✗）；
     *  · `Image.makeFromBitmap` + `toComposeImageBitmap` 拷的也是**整张** ⇒ 又要多花几毫秒 ✗。
     *  ⇒ 尺寸变了就**重分配** ✓（视口尺寸只在缩放 / 改窗口时变，笔画期间恒定 ✓ ⇒ 不抖 ✓）。
     *  ⚠️ **必须**把旧的那张丢掉（`Bitmap.allocPixels` 自己会释放旧像素 ✓）——
     *  但**已经交出去的 Compose 位图是独立一份** ✓（`toComposeImageBitmap` 拷贝 ✓），不受影响 ✓。
     */
    private fun ensureViewportReadBitmap(w: Int, h: Int): Bitmap? {
        val cur = viewportReadBitmap
        if (cur != null && w == viewportReadW && h == viewportReadH) return cur
        val bmp = Bitmap()
        if (!bmp.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))) return null
        viewportReadBitmap = bmp
        viewportReadW = w
        viewportReadH = h
        return bmp
    }

    /** 兜底老路要的那张"可见区大小"的位图（**只在快路走不通时才分配** ✓）。 */
    private var viewportFallbackBitmap: Bitmap? = null
    private var viewportFallbackW = 0
    private var viewportFallbackH = 0

    private fun ensureViewportFallbackBitmap(w: Int, h: Int): Bitmap? {
        comicPaintViewportFramesFallback++
        val cur = viewportFallbackBitmap
        if (cur != null && w <= viewportFallbackW && h <= viewportFallbackH) return cur
        val nw = maxOf(w, viewportFallbackW)
        val nh = maxOf(h, viewportFallbackH)
        val bmp = Bitmap()
        if (!bmp.allocPixels(ImageInfo(nw, nh, ColorType.BGRA_8888, ColorAlphaType.PREMUL))) return null
        viewportFallbackBitmap = bmp
        viewportFallbackW = nw
        viewportFallbackH = nh
        return bmp
    }

    private fun ensureViewportSurface(w: Int, h: Int): Surface? {
        val cur = viewportSurface
        if (cur != null && w <= cur.width && h <= cur.height) return cur
        val s = runCatching { Surface.makeRasterN32Premul(w, h) }.getOrNull() ?: return null
        viewportSurface?.close()
        viewportSurface = s
        return s
    }

    /** 内容变了（笔画 / 撤销 / 油漆桶 / 换层 …）⇒ 视口那一帧下一位重画 ✓。 */
    private fun noteComicPaintFrameChanged() {
        comicPaintFrameTickState++
        viewportFrameKey = Long.MIN_VALUE
    }

    private fun releaseComicPaintViewport() {
        viewportSurface?.close()
        viewportSurface = null
        viewportReadBitmap = null
        viewportReadW = 0
        viewportReadH = 0
        viewportFrameBitmap = null
        viewportFrameKey = Long.MIN_VALUE
    }

    /** 浮动选区那块的显示位图（和主位图同一个版本号口径 ✓）。 */
    private var comicPaintFloatBitmap: ImageBitmap? = null
    private var comicPaintFloatBitmapVersion = -1

    /**
     * **这一层能不能用画笔画**：底板（BASE ✓）、绘画层（PAINT ✓）、
     * 生成层（GENERATED ✓，第 ⑭ 批起**可以** —— 见 [loadComicPaintSession] 的 materialize ✓）
     * 三种位图层都可以；气泡 / 文本（矢量 ✗）不行 ✓。
     */
    fun canComicPaintLayer(layerId: String): Boolean {
        val layer = comicBoardPage?.layers?.firstOrNull { it.id == layerId } ?: return false
        return layer.kind == ComicLayer.Kind.BASE ||
            layer.kind == ComicLayer.Kind.PAINT ||
            layer.kind == ComicLayer.Kind.GENERATED
    }

    /**
     * **自动聚焦一个能画的层** —— 第 ⑰ 批「**选了工具就是编辑**」那一条的落点 ✓。
     *
     * 用户 2026-09-20（第 ⑰ 批）：「**画布编辑器不要用完成编辑按钮,选了工具就是编辑** ·
     * **不需要点图层确定状态** · **选图层只是确定修改的层** · **软件的交互逻辑按照 ps 来**」✓ ——
     * 旧口径是"**点一下图层行才进入编辑**"✗（那一句写在 `OverlayLayerRow` 的 `clickable` 里 ✓），
     * 现在点那一下只是**换一个「改哪一层」** ✓，**不再是能不能画的前提** ✓。
     * 界面那一条 `LaunchedEffect`（`ComicModeScreen` 的 [ComicCanvasHost] ✓）进这一页就调这里 ✓。
     *
     * 挑层的顺序**只此一处** ✓：
     *  1. 已经聚焦着的那一层（[comicPaintLayerId] ✓）**还在、还能画** → **一行都不做** ✓
     *     （界面那条 effect 会在"当前层变了 / 图层表动了"时重跑 ✓ —— 每跑一次都重读一遍像素、
     *      把撤销栈清掉就全错了 ✗；[beginComicLayerPaint] 对同一层虽然是幂等的 ✓，但这条早退更直白 ✓）；
     *  2. [preferredId]（界面传的是**当前选中的那一层** `ui.selectedId` ✓）可画 → 用它 ✓
     *     （不可画 = 气泡 / 文本 ✗ → 跳过 ✓，**不 toast** ✗）；
     *  3. 否则取**图层叠最上面**那个可画的层 ✓（`comicBoardLayers` 下标 0 = 最底下 ✓，所以取 `last` ✓）；
     *  4. 一个可画的都没有（这一页只剩气泡 / 文本 ✗）→ **不开会话** ✓、返回 false ✓ ——
     *     **这里绝不弹 toast** ✗（进这一页就弹一句很烦 ✓）：用户真动手画的那一刻由画布那条手势
     *     如实说一句 ✓，见 [hintComicPaintNeedLayer] ✓。
     *
     * ⚠️ 当前聚焦的那层**被删掉 / 变得不可画**时同样走这条 ✓（界面那条 effect 的 key 里有
     * "当前层 id + 图层 id 序列"✓）→ 自动换一个可画的层 ✓。
     * ⚠️ 走到 2 / 3 时是真开会话 ✓：万一这一层的图读不出来，[beginComicLayerPaint] 会**如实 toast** ✓
     *（那是真失败，不是"进页面就弹一句"✗）。
     *
     * @return 真的**新开**了一段会话才 true ✓。
     */
    /**
     * **把图层焦点让出去**（第 ㊹ 批 2026-09-21 ✓）。
     *
     * 用户口径：「**新建格子后，格子和图层属于同一类型，同一时间只能聚焦一个。**
     *   **只有选择格子时才能拉动和调整大小,并且不能绘画,未选择格子时格子框不可见**」✓
     *
     * 选中格子时调这里 ✓ ⇒ 图层那一侧**干干净净地退出**：
     *  · [comicPaintLayerId] 置空 ✓（画布不再有"正在编辑的层"✗ ⇒ **不能绘画** ✓，正是用户要的 ✓）；
     *  · 会话丢掉 ✗（**不 commit** ✗ —— 用户只是换了个聚焦对象，没说要收工 ✓；
     *    真收工走「完成」那条 ✓）；
     *  · 选区 / 浮动块一并清掉 ✓（它们属于刚才那一层的像素 ✓，留着会画在错的层上 ✗）。
     *
     * ⚠️ 界面那条 `autoFocusComicPaintLayer` 的 effect 见到 `selectedId == null` 时会**重新挑一层** ✓ ——
     *    那正是"同一时间只聚焦一个"的收尾 ✓（格子这一侧接管了焦点 ✓）。
     */
    fun blurComicPaintLayer() {
        if (comicPaintLayerId == null && comicPaintSessionState == null) return
        comicPaintSessionState = null
        comicPaintLayerId = null
        comicPaintSelection = null
        comicPaintSelectionFloating = false
        releaseComicPaintViewport()
        comicPaintCanUndo = false
        comicPaintCanRedo = false
        // 让画布**立刻**重画 ✓（口径与 `removeComicLayer` 那条一样 ✓ ——
        // 不动帧计数的话，画布会停在"上一层还在编辑"的画面 ✓，要点一下才刷新 ✗）。
        comicPaintVersion++
        noteComicPaintFrameChanged()
    }

    fun autoFocusComicPaintLayer(preferredId: String?): Boolean {        val focused = comicPaintLayerId
        if (focused != null && canComicPaintLayer(focused)) return false
        val target = preferredId?.takeIf { canComicPaintLayer(it) }
            ?: comicBoardLayers.lastOrNull { canComicPaintLayer(it.id) }?.id
            ?: return false
        return beginComicLayerPaint(target)
    }

    /**
     * 画笔类工具在手、**这一页却一个能画的层都没有**（只剩气泡 / 文本 ✗）时，用户真在纸上按下 ——
     * **如实说一句** ✓（用户 2026-09-20：「**绝不静默什么都不做**」✗；同时「进这一页就弹 toast」
     * 也很烦 ✗）。
     *
     * 所以这一句**只在用户真的动手的那一刻**说 ✓（画布那条手势里调 ✓）——
     * 进这一页 / 切工具都**不弹** ✗（见 [autoFocusComicPaintLayer] ✓）。
     * 文案用现成的 `comic.board.paintNeedLayer` ✓（不新造 ✓）。
     */
    fun hintComicPaintNeedLayer() {
        toast(rt("comic.board.paintNeedLayer"))
    }

    /**
     * 不能画的那种层的**原因**（挑一句现成文案 ✓，绝不空着 ✗）。
     *
     * ⚠️ 第 ⑭ 批起这里**没有 `GENERATED` 了** ✗：用户 2026-09-20「**包括生成的图像**」✓ ——
     * 生成层被落笔时会先 materialize 成页大小的像素、收工时 kind 转成 `PAINT` ✓，
     * 所以"生成层不能画"这句话**已经不成立**了 ✓（那条文案跟着删掉 ✗，不留一句骗人的 ✓）。
     */
    private fun comicPaintBlockReason(kind: ComicLayer.Kind?): String = when (kind) {
        ComicLayer.Kind.BUBBLE, ComicLayer.Kind.TEXT -> rt("comic.board.paintReasonOverlay")
        else -> rt("comic.board.paintNeedLayer")
    }

    private fun refuseComicPaint(kind: ComicLayer.Kind?): Boolean {
        val why = rf("comic.board.paintBlocked", mapOf("reason" to comicPaintBlockReason(kind)))
        toast(why, isError = true)
        return false
    }

    /**
     * **开始编辑某一层**：把它的像素 materialize / 读进内存 ✓。
     *
     * @return true = 会话开起来了（界面可以开始喂笔画 ✓）；
     *   **false = 不可编**（没有页 / 这一层不存在 / 不是位图层 ✓）并且**已经 toast 说过原因** ✓
     *   （`status` 也写了同一句 ✓ —— 弹窗会消失，状态栏留着还能回头看 ✓）。
     */
    fun beginComicLayerPaint(layerId: String): Boolean {
        val page = comicBoardPage ?: return refuseComicPaint(null)
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return refuseComicPaint(null)
        // ⑭：**生成层也能画** ✓（用户 2026-09-20：「包括生成的图像」✓）——
        // 它那套"cover 进某一格"的几何在 [loadComicPaintSession] 里**烘焙进像素** ✓，
        // 落笔之后这一层就是页大小的普通位图了 ✓（收工时 kind 转 `PAINT` ✓）。
        if (layer.kind != ComicLayer.Kind.BASE &&
            layer.kind != ComicLayer.Kind.PAINT &&
            layer.kind != ComicLayer.Kind.GENERATED
        ) {
            return refuseComicPaint(layer.kind)
        }
        // 已经就在画这一层：幂等返回（别把用户的像素白读一遍 ✗，更别把撤销栈清掉 ✗）
        if (comicPaintLayerId == layerId && comicPaintSessionState != null) return true

        // ⚠️ **切换图层前必须先 commit 上一段** ✓（契约里点名的两条之一 ✓）
        commitComicLayerPaint()

        val session = runCatching { loadComicPaintSession(page, layer) }.getOrNull()
        if (session == null) {
            val why = rf(
                "comic.board.paintBlocked",
                mapOf("reason" to rt("comic.board.paintReasonLoadFailed")),
            )
            toast(why, isError = true)
            return false
        }
        comicPaintSessionState = session
        comicPaintLayerId = layerId
        comicPaintSelection = null
        comicPaintSelectionFloating = false
        // 换了一层 ⇒ 视口那一帧（和它读的那张表面）整块重来 ✓
        releaseComicPaintViewport()
        syncComicPaintHistory()
        comicPaintVersion++
        status = rf("comic.board.paintOn", mapOf("name" to layer.name))
        return true
    }

    /**
     * **收工**：把像素编成 PNG 写进这一层的图（`<filesDir>/comic/layer-<layerId>.png` ✓，
     * **App 私有目录 ✓，绝不写仓库** ✗）→ 更新 `imagePath` → [commitComicBoard] ✓。
     *
     * ⚠️ **一个像素都没动过就不写盘** ✗（`hasChanges` 是唯一判据，和 `closeCanvasEditor` 一个口径 ✓）——
     * 切换图层来回点不会把同一张 PNG 反复写一遍 ✓。
     *
     * ⚠️ **历史（撤销栈）不落盘** ✗：写完之后 [ImageEditSession.markClean] 一下 ✓，
     * 但会话本身**到这儿就结束了** ✓（下一段是新的一段会话 ✓，见本节开头那段说明 ✓）。
     */
    fun commitComicLayerPaint() {
        val session = comicPaintSessionState
        val layerId = comicPaintLayerId
        // 正在画的时候被切走：先把这一笔收口（不收口的话最后一段会丢 ✓）
        session?.endStroke()
        // 「收工」= 这一段会话到此为止（无论后面写不写盘 ✓）
        comicPaintSessionState = null
        comicPaintLayerId = null
        comicPaintSelection = null
        comicPaintSelectionFloating = false
        releaseComicPaintViewport()
        comicPaintCanUndo = false
        comicPaintCanRedo = false
        if (session == null || layerId == null) return
        if (!session.hasChanges) return

        val page = comicBoardPage ?: return
        val existing = page.layers.firstOrNull { it.id == layerId } ?: return
        if (existing.kind != ComicLayer.Kind.BASE &&
            existing.kind != ComicLayer.Kind.PAINT &&
            existing.kind != ComicLayer.Kind.GENERATED
        ) {
            return
        }

        val target = File(comicScratchDir(), "layer-$layerId.png")
        val native = images.fromArgb(session.pixels, session.width, session.height)
        val bytes = images.pngBytes(native)
        native.recycle()
        val written = runCatching {
            platform.openOutput(target.absolutePath)?.use { out -> out.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            status = rt("comic.board.paintSaveFailed")
            toast(status, isError = true)
            return
        }
        session.markClean()
        val wasBase = existing.kind == ComicLayer.Kind.BASE
        // ⑭ **生成层画过之后就不再是"跟着格子走"的生成层了** ✓ —— 它的像素已经被 materialize
        // 成页大小、几何烘焙进去了 ✓，所以 kind 转成 `PAINT` ✓（用户 2026-09-20：「包括生成的图像」✓）。
        // ⚠️ **这是如实的副作用** ✗：这一层从此按位图层整页画（拼页 / PSD 导出都按位图层走 ✓），
        //    它**不再跟着格子缩放 / 移动**了 ✓。
        // ⚠️ 名字与 `panelId` **留着** ✓（回溯"这一层原本是哪一格生成的"要看它 ✓）。
        val wasGenerated = existing.kind == ComicLayer.Kind.GENERATED
        val path = target.absolutePath
        updateComicBoardPage { current ->
            val swapped = current.withLayers(
                current.layers.map { layer ->
                    if (layer.id != layerId) {
                        layer
                    } else if (wasGenerated) {
                        layer.copy(imagePath = path, kind = ComicLayer.Kind.PAINT)
                    } else {
                        layer.copy(imagePath = path)
                    }
                },
            )
            // ⚠️ 底板层还多一步：**画布上显示的那张图来自 `page.basePath`**（见 `ComicCanvasArea` ✓）——
            // 只改图层里的 imagePath 的话，用户"画完 / 收工之后**看不见**自己画的东西" ✗。
            if (wasBase) swapped.copy(basePath = path) else swapped
        }
        // 契约点名的落盘入口：图层表写一次 ✓
        commitComicBoard()
        comicPaintVersion++
        status = rf("comic.board.paintSaved", mapOf("w" to session.width, "h" to session.height))
    }

    /**
     * **把「网页笔刷」那一页画好的图收进来**（第 ㉕ 批 ✓；用户口径「直接搬，不修」✓）。
     *
     * 这条路的三个决定，都是为了**不去动现有那套 Kotlin 笔刷管线** ✓：
     *  · 收到的是一张 **PNG 字节流**（网页那边 `compCv.toDataURL('image/png')` ✓）——
     *    我们不解析它的像素、不重采样、不碰 [ImageEditSession] ✗；
     *  · 它**落盘**成 `<filesDir>/web-brush/web-brush-<时间戳>.png` ✓ —— 因为 [ComicLayer]
     *    的像素来源本来就是 `imagePath` ✓（[loadComicPaintSession] 会按"等比缩到放得下、
     *    居中留边"读成页大小 ✓，和生成层 materialize 同一条路 ✓）⇒ **关掉软件再打开还在** ✓；
     *  · 建出来的是一个**普通 PAINT 层** ✓（`kind = PAINT` ✓）⇒ 之后它能被现有的
     *    移动 / 撤销 / PSD 导出 / 继续用 Kotlin 笔刷改 —— 全是现成能力 ✓，一行新代码都不用 ✓。
     *
     * @return 真的收进来了才 true ✓（没有页 / 写文件失败 = false ✓ 并 toast 说明 ✓，不静默 ✗）。
     */
    fun adoptWebBrushImage(png: ByteArray, reuseLayerId: String? = null): Boolean {
        val page = comicBoardPage
        if (page == null) {
            // 没有页就没有"绘画层"这回事（和 [createComicPaintLayer] 同一个口径 ✓）。
            toast(rt("comic.board.paintNeedLayer"), isError = true)
            return false
        }
        return runCatching {
            val dir = java.io.File(platform.paths.filesDir, "web-brush").apply { mkdirs() }
            val file = java.io.File(dir, "web-brush-" + System.currentTimeMillis() + ".png")
            file.writeBytes(png)
            // ⚠️ 第 ㉛ 批：**抬笔自动写回**是**每一笔**都来一次 ✓ —— 如果每次都 `stack.add`，
            //    用户画十笔就多十个绘画层 ✗✗（图层表当场被刷爆 ✓）。
            //    所以给一个"**复用哪一层**"的口子 ✓：同一张网页画布在一段会话里**始终写回同一层** ✓；
            //    那层要是被用户删了 / 换页了 ⇒ 自然退回"新建一层" ✓（不会写丢 ✓）。
            val reusable = reuseLayerId?.let { id -> page.layers.firstOrNull { it.id == id } }
            if (reusable != null) {
                updateComicBoardPage { current ->
                    current.withLayers(
                        current.layers.map { layer ->
                            if (layer.id == reusable.id) layer.copy(imagePath = file.absolutePath) else layer
                        },
                    )
                }
                persistComicBoard()
                // ⚠️ 那层**正开着绘画会话**时必须把像素也刷新一遍 ✗ —— 不然屏幕上还是旧的 ✓。
                if (comicPaintLayerId == reusable.id) reloadComicPaintLayerFromDisk(reusable.id)
                logInfo(
                    "WebBrush",
                    "[WebBrush] adopted(reuse) bytes=${png.size} file=${file.absolutePath} layer=${reusable.id}",
                )
                return@runCatching true
            }
            val layer = ComicLayer(
                id = "comic-web-" + System.currentTimeMillis() + "-" + page.layers.size,
                name = rt("comic.board.webBrushLayer"),
                kind = ComicLayer.Kind.PAINT,
                imagePath = file.absolutePath,
            )
            updateComicBoardPage { current ->
                val stack = ComicLayerStack(current.layers)
                stack.add(layer)
                // normalizedOverlays：顺手把它的位置摆正（PAINT 在保留名单里 ✓，不会被扫掉 ✓）
                current.withLayers(stack.all).normalizedOverlays()
            }
            persistComicBoard()
            // 建好即选中 + 立刻能继续画 ✓（它会把那张 PNG 读成页大小像素 ✓）
            beginComicLayerPaint(layer.id)
            logInfo(
                "WebBrush",
                "[WebBrush] adopted bytes=${png.size} file=${file.absolutePath} " +
                    "page=${page.baseWidth}x${page.baseHeight} layer=${layer.id}",
            )
            status = rf("comic.board.webBrushAdopted", mapOf("w" to page.baseWidth, "h" to page.baseHeight))
            true
        }.getOrElse { failure ->
            // ⚠️ 失败要**带上异常本身**写日志 ✗（只 toast 一句"失败了"的话，出了问题只能靠猜 ✓）。
            logInfo("WebBrush", "[WebBrush] 采用失败：$failure")
            toast(rt("comic.board.webBrushFailed"), isError = true)
            false
        }
    }

    /**
     * **把某一层从盘上重新读一遍**（第 ㉛ 批 ✓）—— 网页写回新 PNG 之后，
     * 那层要是**正开着绘画会话**，`ImageEditSession` 里还是旧像素 ✗（屏幕上看着"没写回" ✗）。
     *
     * ⚠️⚠️ **绝对不能用 `commitComicLayerPaint()`** ✗✗ —— 那个函数会把**会话里的旧像素写回层文件** ✓，
     * 而层文件**刚刚**才是网页写回来的那张新图 ⇒ 正好把它**覆盖掉** ✗（写回看起来"没生效" ✓）。
     * 所以这里只做一件事：**把当前会话丢掉**（不写盘 ✗），然后照用户点一下那一层的同一条路
     * （`beginComicLayerPaint` ✓）重新读 ✓ —— 像素来源 / 缩放方式 / 历史清空这些语义**完全一致** ✓。
     */
    private fun reloadComicPaintLayerFromDisk(layerId: String) {
        runCatching {
            comicPaintSessionState?.endStroke()
            comicPaintSessionState = null
            comicPaintLayerId = null
            comicPaintSelection = null
            comicPaintSelectionFloating = false
            releaseComicPaintViewport()
            comicPaintCanUndo = false
            comicPaintCanRedo = false
            beginComicLayerPaint(layerId)
        }.onFailure { logInfo("WebBrush", "[WebBrush] 写回后重开绘画会话失败：$it") }
    }

    /**
     * **新建一层空的绘画层**（页大小、全透明 ✓）→ 新建即选中并开始编辑 ✓。
     *
     * @return 新层 id；**还没有底板时返回 null** ✓（并 toast 说明 —— 绘画层是**页**上的一层，
     *   没有页就没有它 ✓）。
     */
    fun createComicPaintLayer(): String? {
        val page = comicBoardPage
        if (page == null) {
            toast(rt("comic.board.paintNeedLayer"), isError = true)
            return null
        }
        val layer = ComicLayer(
            id = "comic-paint-" + System.currentTimeMillis().toString() + "-" + page.layers.size,
            name = COMIC_PAINT_LAYER_NAME,
            kind = ComicLayer.Kind.PAINT,
            imagePath = null,
        )
        updateComicBoardPage { current ->
            val stack = ComicLayerStack(current.layers)
            stack.add(layer)
            // normalizedOverlays：顺手把它的位置摆正（**PAINT 在保留名单里 ✓**，不会被扫掉 ✓）
            current.withLayers(stack.all).normalizedOverlays()
        }
        persistComicBoard()
        // 新建即选中 + 立刻能画 ✓（失败也不会是"点了没反应"✗：[beginComicLayerPaint] 自己会说原因）
        beginComicLayerPaint(layer.id)
        return layer.id
    }

    /**
     * 打开一层：把它的图读成**页大小**的 ARGB（读不出来抛异常，由调用方按失败处理 ✓）。
     *
     * ⚠️ **生成层（`GENERATED`）走 materialize** ✓（第 ⑭ 批）：它本来按"cover 进自己那一格"的
     * 几何存在（不是页大小的像素 ✓）—— 所以先把那套几何**烘焙进像素** ✓（见
     * [materializeGeneratedLayer] ✓），拿到一块**页大小**的 ARGB，之后它就是普通位图层了 ✓。
     * 用户 2026-09-20：「在这个图层焦点时可以随意更改绘画,**包括生成的图像**」✓。
     */
    private fun loadComicPaintSession(page: ComicBoardPage, layer: ComicLayer): ImageEditSession {
        val width = page.baseWidth.coerceAtLeast(1)
        val height = page.baseHeight.coerceAtLeast(1)
        if (layer.kind == ComicLayer.Kind.GENERATED) {
            return ImageEditSession(width, height, materializeGeneratedLayer(page, layer, width, height))
        }
        val fallback = if (layer.kind == ComicLayer.Kind.BASE) page.basePath else null
        val path = layer.imagePath ?: fallback
        if (path == null) {
            // 空白画布 / 新建了还没画的绘画层：**全透明底** ✓
            return ImageEditSession(width, height, IntArray(width * height) { ImageEditOps.TRANSPARENT })
        }
        val decoded = images.decodeFull(path) ?: error("读不出这一层的图：$path")
        val pixels = runCatching { images.pixelsOf(decoded) }.getOrElse {
            decoded.recycle()
            throw it
        }
        val sourceWidth = decoded.width
        val sourceHeight = decoded.height
        if (sourceWidth == width && sourceHeight == height) {
            decoded.recycle()
            return ImageEditSession(width, height, pixels)
        }
        // ⚠️ 尺寸对不上（换了底板尺寸之后老图还在盘上 ✓）：按**画布上显示的那个 Fit** 摆进去 ——
        // 等比缩到放得下、**居中留边** ✓（和 `ContentScale.Fit` 逐字一个口径 ✓）。
        // 不这么做的话，"屏幕上看着底板在中间"而"笔一落就偏了" ✗。
        val fit = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val fitWidth = (sourceWidth * fit).roundToInt().coerceIn(1, width)
        val fitHeight = (sourceHeight * fit).roundToInt().coerceIn(1, height)
        val scaled = images.scale(decoded, fitWidth, fitHeight, smooth = true)
        val fitted = if (scaled === decoded) pixels else images.pixelsOf(scaled)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val out = IntArray(width * height) { ImageEditOps.TRANSPARENT }
        val offsetX = (width - fitWidth) / 2
        val offsetY = (height - fitHeight) / 2
        for (row in 0 until fitHeight) {
            val src = row * fitWidth
            val dst = (offsetY + row) * width + offsetX
            System.arraycopy(fitted, src, out, dst, fitWidth)
        }
        return ImageEditSession(width, height, out)
    }

    /**
     * **把一层生成层烘焙成"页大小的像素"**（第 ⑭ 批：生成层可画的关键一步 ✓）。
     *
     * 生成层本来只有"用哪张图 + 它 cover 进哪一格"这两件事（不是页大小的像素 ✗）——
     * 落笔之前必须先把它**画成页大小的一块 ARGB** ✓，否则"在生成层上画一笔"无从谈起 ✓。
     *
     * 三条口径：
     *  1. **几何只有一处** ✓：落点走 [ComicPageExporter.generatedPlacement] ✓ ——
     *     和拼页导出 / PSD / 预览里那个 `ContentScale.Crop` **同一个来源** ✓，
     *     所以烘焙出来的样子和屏幕上看见的**一模一样** ✓（不会"烘完偏一格"✗）；
     *  2. **透明度不烘进像素** ✓：`blendOver` 传 `opacity = 1f`，图层自己的 `layer.opacity` 留着 ✓ ——
     *     界面画这一层时按 `alpha(layer.opacity)` 叠 ✓，看上去和之前**完全一致** ✓
     *     （烘进去的话会被"再乘一次"✗）；
     *  3. 没图 / 没挂格子 / 尺寸读不出来 → **抛** ✓，由 [beginComicLayerPaint] 接住、
     *     走 `refuseComicPaint(LABEL)` 那条**如实 toast** ✓（绝不静默 ✗）。
     */
    private fun materializeGeneratedLayer(
        page: ComicBoardPage,
        layer: ComicLayer,
        width: Int,
        height: Int,
    ): IntArray {
        val sizeOf: (String) -> Pair<Int, Int>? = { path ->
            runCatching { images.size(path) }.getOrNull()
        }
        val step = ComicPageExporter.generatedPlacement(page, layer, sizeOf)
            ?: error("这一层没有可以烘焙的图：${layer.id}")
        val decoded = images.decodeFull(step.imagePath) ?: error("读不出这一层的图：${step.imagePath}")
        val source = runCatching { images.pixelsOf(decoded) }.getOrElse {
            decoded.recycle()
            throw it
        }
        val scaled = if (decoded.width == step.width && decoded.height == step.height) {
            decoded
        } else {
            images.scale(decoded, step.width, step.height, smooth = true)
        }
        val placed = if (scaled === decoded) source else images.pixelsOf(scaled)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        val out = IntArray(width * height) { ImageEditOps.TRANSPARENT }
        // 越界的部分由 `blendOver` 自己裁 ✓（格子跑到页外的老数据也不会崩 ✓）
        ComicComposer.blendOver(
            dst = out,
            dstW = width,
            dstH = height,
            src = placed,
            srcW = step.width,
            srcH = step.height,
            x = step.x,
            y = step.y,
            opacity = 1f,
        )
        return out
    }

    // ---- 工具 / 颜色 / 笔刷（都**不落盘** ✓，都是这次会话里的东西 ✓）----

    /**
     * 换工具（照 `chooseCanvasTool` 的口径 ✓）：收掉手头这一笔 ✓；**离开选区类工具时
     * 把飘着的那块落回** ✓（不然切了工具它还悬在半空 ✗）。**不 commit** ✓（契约点名 ✓）。
     */
    fun chooseComicPaintTool(tool: CanvasTool) {
        val session = comicPaintSessionState
        val wasDrawing = session?.isDrawing == true
        session?.endStroke()
        // ⚠️ 第 ㉜a 批：切工具会**收掉手头这一笔** ✓（`endStroke` 按块记历史 ✓）——
        // 这一笔的墨迹早就在层表面上了（显示每帧读它 ✓），所以这里只需要让视口重画一次 ✓。
        if (wasDrawing) comicPaintContentChanged()
        val leaving = comicPaintTool != tool &&
            (comicPaintTool == CanvasTool.SELECT || comicPaintTool == CanvasTool.LASSO)
        if (leaving) commitComicPaintSelection()
        comicPaintTool = tool
        // 选区**不在这里清**（和画布编辑器一个口径 ✓：选好一块之后换画笔进去画是正常用法 ✓）
    }

    fun chooseComicPaintColor(color: Int) {
        comicPaintColor = color
    }

    fun setComicPaintBrush(pixels: Int) {
        comicPaintBrushPixels = ImageEditOps.normalizedBrushPixels(pixels)
    }

    /**
     * 笔尖宽高比（1 = 圆 ✓、>1 = 扁 ✓）—— 越界 / NaN 一律收敛到合法区间 ✓。
     *
     * ⚠️ 名字是 `choose*` 不是 `set*`：属性本身已经有 JVM 的 `setComicPaintNibRatio` 了 ✗
     *（同一个签名 = `Platform declaration clash`，编译器直接红 ✓）——
     * 和 [chooseComicPaintTool] / [chooseComicPaintColor] 一个命名口径 ✓。
     */
    fun chooseComicPaintNibRatio(ratio: Float) {
        if (!ratio.isFinite()) return
        comicPaintNibRatio = ratio.coerceIn(ImageEditOps.NIB_MIN_RATIO, ImageEditOps.NIB_MAX_RATIO)
        // ⚠️ **同一件事只写一处** ✓：老字段是给老界面读的 ✓，真源是 `comicPaintBrush` ✓ ——
        // 所以这里顺手把它折进 SAI 的 `WxHRatio`（**0 = 圆** ✓，换算见 `BrushSpec` ✓）。
        applyComicPaintBrush(
            comicPaintBrush.copy(wxRatio = BrushSpec.nibRatioToWxRatio(comicPaintNibRatio)),
        )
    }

    /** 角度控制（**0 固定 / 1 自动** ✓，见 [comicPaintAngleControl] ✓）—— 越界一律收敛 ✓（`choose*` 的理由同上 ✓）。 */
    fun chooseComicPaintAngleControl(control: Int) {
        comicPaintAngleControl = control.coerceIn(
            NibAngleControl.FIXED,
            NibAngleControl.AUTO,
        )
        // 同上：老字段是读出去用的 ✓，真源是 `comicPaintBrush` ✓。
        applyComicPaintBrush(comicPaintBrush.copy(angleControl = comicPaintAngleControl))
    }

    /**
     * **最小大小%**（SAI 的「最小半径」✓，界面给 **0..100 的百分比** ✓，存成 0..1 ✓）。
     *
     * 只吃 `pressureSize` 那一条路上的 `minRadiusRatio` ✓（见 [StrokeSpec.minRadiusRatio] ✓）；
     * 收敛口径与 `ImageEditOps.pressureRadiusScale` 逐字一致 ✓（NaN → 默认 ✓）。
     */
    fun chooseComicPaintMinRadiusRatio(percent: Float) {
        if (!percent.isFinite()) return
        comicPaintMinRadiusRatio = (percent / 100f).coerceIn(0f, 1f)
    }

    /**
     * **压感曲线**（界面给 0.2..3 ✓ —— `< 1` = 轻轻一按就上粗细（"手轻" ✓）、
     * `> 1` = 要按得更重才上粗细（"手重" ✓））。
     *
     * 收敛口径与 `ImageEditOps.pressureRadiusScale` 逐字一致 ✓（**曲线必须 > 0** ✓：
     * 0 或 NaN 会让 `pow` 退化成常数 ⇒ 压感整条失灵 ✗，那边会退回默认的 1 ✓，这里先挡一道 ✓）。
     */
    fun chooseComicPaintPressureCurve(curve: Float) {
        if (!curve.isFinite() || curve <= 0f) return
        comicPaintPressureCurve = curve.coerceIn(0.2f, 3f)
    }

    /**
     * **笔刷面板的一句提示**（工具 / 预设点下去之后那句话 ✓）。
     *
     * ⚠️ 为什么不直接在面板里 `LocalPlatform.current.ui.toast(...)` ✗：`Platform` 上**没有**
     * `toast`（那是 `UiHost` 的事 ✓），而这一页的宿主能力在别处 ✓ —— 绕一圈不如走这里 ✓：
     * 复用 `AppState` 已有的 [`toast`] ✓（**状态栏也写一份** ✓ —— 弹窗会消失、状态栏留着还能回头看 ✓，
     * 这正是本仓库一贯的口径 ✓）。
     */
    fun toastBrushPanel(message: String) {
        if (message.isBlank()) return
        toast(message)
    }

    /**
     * **套用一个预设之后说一句** ✓（面板点那一格时调 ✓）。
     *
     * ⚠️ 由 `AppState` 自己按 `settings.language` 拼 ✓ —— 界面那边只管"哪一格被点了" ✓
     *（文案 / 语言 / 弹窗三条口径都留在这一处 ✓，和 [notePenSample] 一个路子 ✓）。
     */
    fun noteBrushPresetApplied(presetKey: String) {
        toastBrushPanel(
            rf("comic.board.presetApplied", mapOf("name" to rt(presetKey))),
        )
    }

    // ---- 笔画 / 油漆桶 / 吸管 / 撤销重做（坐标一律**底板像素** ✓）----

    // ---- 第 ㉝ 批 ① · **一帧内的输入采样**（coalesced 等价 ✓，见 [StrokeInputCoalescer] ✓）----
    //
    // 两条输入通道（数位板 `compose-stylus` 的回调 ✓ / 鼠标触摸的 `awaitPointerEvent` 循环 ✓）
    // **共用这一个** ✓ —— 用户点名的口径（"两条都要过同一套" ✓）。
    private val comicStrokeInput = StrokeInputCoalescer()

    /**
     * **攒一个 motion 采样点**（第 ㉝ 批 ① ✓）—— 界面在事件回调里只干这一件事 ✓。
     *
     * ⚠️ **这里不画任何东西** ✗：真正喂引擎在**帧边界**（[flushComicPaintStrokePoints] ✓）——
     * 事件回调里干重活会把消息泵堵住，同一帧后面的移动事件就被 OS 合掉了 ✗
     * （实机 `ptPerSec` 只有 21~48 点/秒就是这么来的 ✓，机制写在 [StrokeInputCoalescer] 的说明里 ✓）。
     *
     * ⚠️ 第 ㉝① 回归修复 ✓：**笔优先** —— 这一笔是笔按下开的（[fromPen] ✓）时，
     * 鼠标那条（Windows 把同一支笔**又提升**出来的那串 ✓，压力恒 1 ✗）送来的点**直接丢掉** ✓。
     *
     * @param x/y 已经换算成**纸面（原图）像素**的坐标 ✓
     * @param pressure 这一点自己的笔压 ✓（**逐点保留** ✓，不是只留最后一个 ✗）
     * @param fromPen true = **数位板 / 触控笔**那条通道送来的 ✓（界面按通道给 ✓）
     */
    fun offerComicPaintStrokePoint(x: Float, y: Float, pressure: Float = 1f, fromPen: Boolean = false) {
        comicStrokeInput.offer(x, y, pressure, fromPen)
    }

    /**
     * **帧边界**：把这一帧攒下的点**按到达顺序、一个不少**地喂给引擎 ✓（`withFrameNanos` 之后调 ✓）。
     *
     * 返回这一帧喂了几个 ✓（0 = 这一帧没有新点 ✓）。
     */
    fun flushComicPaintStrokePoints(): Int = comicStrokeInput.drainTo { x, y, pressure ->
        comicPaintStrokeTo(x, y, pressure)
    }

    /**
     * **帧边界（A 方案 ✓）**：喂完这一帧的点之后，**把攒下的覆盖度合成到表面上** ✓。
     *
     * ## 治的是什么（用户实机原话 ✓）
     *
     * > 「**绘画时是先画出一排点，然后才补成完整的线**」
     *
     * **根因**：走 `BrushSpec.strokeCoverage` 那条路时，[comicPaintStrokeTo] **只累积覆盖度、
     * 不碰层表面** ✗ ⇒ 笔画期间屏幕上看不到这一笔的墨 ✓，抬笔那一下才一次性出现整条线 ✓。
     *
     * ⚠️ **次序不能反** ✗：必须**先** `flushComicPaintStrokePoints()`（喂点 ✓）、**再**调这个 ✓ ——
     * 先合的话这一帧的新墨要等下一帧才上表面 ⇒ 延迟翻倍 ✗。
     *
     * ⚠️ 与 `docs/66`（㊲）的"合成按帧攒"**同一条纪律** ✓：都是"每帧一次、与输入率解耦" ✓；
     * 区别只是**攒的东西不同**（㊲ 攒的是"要不要通知显示"✓，这里攒的是"要不要把墨盖上表面"✓）。
     *
     * @return true = 这一帧真的合了一次（有新墨上表面 ✓）
     */
    fun flushComicPaintCoverageTick(): Boolean {
        val session = comicPaintSessionState ?: return false
        if (!session.compositeCoverageFrameTick()) return false
        // 表面变了 ⇒ 显示那一帧作废 ✓（与 `noteComicPaintFrameChanged()` 同一个口径 ✓）。
        // ⚠️ 这里**必须**顺带通知：A 方案下"有墨了"这件事本身就要显示出来 ✓。
        noteComicPaintFrameChanged()
        return true
    }

    /** 这一笔的"攒点"统计（`[PenDab]` 汇总日志用 ✓ —— 就是"每帧几个点 / 攒了几个点" ✓）。 */
    fun comicStrokeInputStats(): StrokeInputCoalesceStats = comicStrokeInput.stats()

    /**
     * 指针按下：开一笔（照 `beginCanvasStroke` 的口径 ✓）。
     *
     * @param pressure 落笔那一刻的**笔压** 0..1 ✓
     *  ⚠️ **整笔一个粗细的日子过去了** ✓（用户 2026-09-20：「**逐点压感和旋转做**」✓）：
     *  现在 `beginStroke` 只吃"基础笔刷直径" ✓，**每个笔尖各按自己那一点的压力现算半径** ✓
     *（`ImageEditSession.strokeTo(x, y, pressure)` ✓）—— 所以这一笔之内会自然变粗变细 ✓。
     *  鼠标 / 触摸 / 收不到笔信号时调用方传默认的 **1f** ✓ → 压感比例 = 1
     *  → **与加这个功能之前逐字节相同** ✓。
     * @param screenX/screenY 这一次落笔在**屏幕（组件局部像素）**上的位置 ✓ —— 只给
     *  `[PenDab] begin` 那条日志用 ✓（用户 2026-09-20：「还是一排点」⇒ 要能一眼看出
     *  "屏幕坐标 → 原图坐标"换算对不对 ✓，见 [logPenDabBegin] ✓；`NaN` = 调用方没给 ✓）。
     * @param zoom 当时的**画布缩放**（`ComicModeUiState.canvasScale` ✓）与 @param dpr
     *  （`LocalDensity.density` ✓）—— 同样只进日志 ✓（见 [logPenDabEnd] ✓）。
     * @param fromPen true = 这一笔是**数位板 / 触控笔**按下开的 ✓（第 ㉝① 回归修复 ✓）——
     *  从此这一笔里**鼠标那条送来的点全部丢掉** ✓（笔优先 ✓，见 [StrokeInputCoalescer] ✓）。
     */
    fun beginComicPaintStroke(
        x: Float,
        y: Float,
        pressure: Float = 1f,
        screenX: Float = Float.NaN,
        screenY: Float = Float.NaN,
        zoom: Float = 1f,
        dpr: Float = 1f,
        fromPen: Boolean = false,
    ) {
        val session = comicPaintSessionState ?: return
        // ⚠️ 第 ㉝ 批 ①：上一笔**攒下还没喂**的点必须先喂掉 ✓ ——
        //    `session.beginStroke` 会把上一笔收口（`if (isDrawing) endStroke()` ✓），
        //    喂晚了那几个点就会落到**新一笔**上 ✗（笔迹会莫名多出一小截 ✓）。
        //    ⚠️ 这两行必须在 `session` 取出来**之前**的位置意义不大（`comicPaintStrokeTo` 自己会取 ✓），
        //    但 `reset` 必须在**这一笔开头**（日志里那几个数才是"这一笔"的 ✓）。
        flushComicPaintStrokePoints()
        // ⚠️ 第 ㉝① 回归修复：这里记下"**这一笔是谁在画**" ✓（`fromPen` ✓）——
        //    `beginStroke(fromPen)` 顺带把这一笔的攒点计数归零 ✓（日志里那几个数才是"这一笔"的 ✓）。
        //    从此"笔在画"的那一笔里，鼠标那条（含 Windows 提升出来的那串，压力恒 1 ✗）**一律丢** ✓。
        comicStrokeInput.beginStroke(fromPen = fromPen)
        // 还飘着一块选区就先落回：不然画的东西会被"飘着的那块"盖住，看着像没画上 ✓
        if (comicPaintSelectionFloating) commitComicPaintSelection()
        val mode = when (comicPaintTool) {
            CanvasTool.ERASE -> StrokeMode.ERASE
            CanvasTool.BLUR -> StrokeMode.BLUR
            else -> StrokeMode.PAINT
        }
        session.beginStroke(
            x = x,
            y = y,
            spec = StrokeSpec(
                mode = mode,
                // ⚠️ 这里给的是**基础笔刷直径**（= 滑杆值 ✓，**不再预先乘压力** ✗）——
                // 逐点压感由会话**每个笔尖现算** ✓（口径见 `ImageEditOps.brushRadiusPixels` ✓）。
                brushPixels = comicPaintBrushPixels,
                color = comicPaintColor,
                blurIntensity = DEFAULT_BLUR_INTENSITY,
                // 两个默认参数 = 参数化之前那条老公式 ✓（口径见 `PEN_PRESSURE_CURVE` ✓）
                // ⚠️ 第 ㉓ 批起「最小大小%」**由面板喂** ✓（[comicPaintMinRadiusRatio] ✓）——
                //    默认仍然是 [PEN_BRUSH_MIN_FACTOR] = 0.25 ✓ ⇒ **默认手感一个字都没变** ✓。
                minRadiusRatio = comicPaintMinRadiusRatio,
                pressureCurve = comicPaintPressureCurve,
                // 笔尖形状 / 朝向（用户 2026-09-20：「**笔旋转**做出来」✓；旋转那一态已删 ✓）
                // ⚠️ 第 ㉓ 批起**整套 SAI 参数**就存在 `comicPaintBrush` 这一处 ✓
                //    （面板直接读写它 ✓ —— 不再每落一笔现造一个 ✗，那样面板改的数下一笔就没了 ✗）。
                //    上一批那两个入口（`comicPaintNibRatio` / `comicPaintAngleControl`）照旧生效 ✓：
                //    界面那两个滑杆走 `choose*` 时**顺带**把它们写进 `comicPaintBrush` ✓
                //    ⇒ "一处真源"没破 ✓（见 [comicPaintBrush] 的说明 ✓）。
                brush = comicPaintBrush,
                // ---- 第 ㉚ 批 · 输入侧稳定器（EMA ✓）----
                // **照网页默认档 35% 开** ✓（`docs/brush-lab-simple.html:156` `stabilize: 35` ✓）。
                // 公式见 `StrokeStabilizer` ✓（`k = 1 - 35/100 = 0.65` ✓）。
                // ⚠️ 真值对照那条路（`SimpleBrushParityTest`）**不开** ✗ —— 判据①的 928 颗
                //    是**没有稳定器**那条路抽出来的 ✓（见 `StrokeSpec.stabilizePercent` 的说明 ✓）。
                stabilizePercent = comicPaintStabilize,
                // 随机数：**每落一笔 +1** ✓（同一笔序号 ⇒ 同一份抖动 ✓，见 `comicPaintStrokeSeed` ✓）
                seed = comicPaintStrokeSeed++,
            ),
            pressure = pressure,
        )
        // ⚠️ 第 ㉜a 批：落笔这一颗**已经画在层表面上了** ✓（显示每帧从表面读可见区 ✓）——
        //    这里只需要让视口重画一次 ✓（不碰 `pixels` 的版本号：那一颗的回读已经 `notePixelsChanged` 过了 ✓）。
        noteComicPaintFrameChanged()
        comicPenStrokeZoom = zoom
        comicPenStrokeDpr = dpr
        logPenDabBegin(x, y, pressure, screenX, screenY)
    }

    // ---- `[PenDab]` 日志（用户 2026-09-20：「还是一排点」⇒ 让用户画一笔就把**数字**带回来 ✓）----
    //
    // 三条硬规矩（用户点名 ✓）：
    //  · **一笔只有两条**（落笔一条 + 收笔一条 ✓）—— 绝不每颗笔尖打一行 ✗（一笔几百行会把
    //    `app.log` 淹掉 ✓）；
    //  · **只走 `logInfo`** ✓（进 `app.log` ✓ + 底部抽屉那个内存环形缓冲 ✓），
    //    绝不把日志画进画布 ✗；
    //  · 键值一律**英文 / 数字** ✓（用户会把这两行原样转回来，机器可读最要紧 ✓）。

    /** 这一笔落笔时的画布缩放（`ComicModeUiState.canvasScale` ✓）—— 收笔那条日志要用 ✓。 */
    private var comicPenStrokeZoom = 1f

    /** 这一笔落笔时的 `LocalDensity.density`（电脑端恒为 1.0 ✓，见 `Platform.uiScale` 的说明 ✓）。 */
    private var comicPenStrokeDpr = 1f

    private fun logPenDabBegin(
        pageX: Float,
        pageY: Float,
        pressure: Float,
        screenX: Float,
        screenY: Float,
    ) {
        val session = comicPaintSessionState ?: return
        val stats = session.strokeDabStats()
        val screen = if (screenX.isFinite() && screenY.isFinite()) {
            "(${penDabFmt(screenX, 1)},${penDabFmt(screenY, 1)})"
        } else {
            "(n/a,n/a)"
        }
        logInfo(
            "PenDab",
            "[PenDab] begin pressure=${penDabFmt(pressure, 2)} " +
                "brushPx=$comicPaintBrushPixels spacing=${penDabFmt(stats.spacingPercent, 0)}% " +
                "nib=${penDabFmt(stats.nibRatio, 2)} angle=$comicPaintAngleControl " +
                "zoom=${penDabFmt(comicPenStrokeZoom, 3)} dpr=${penDabFmt(comicPenStrokeDpr, 2)} " +
                "screen=$screen page=(${penDabFmt(pageX, 1)},${penDabFmt(pageY, 1)}) " +
                "r0=${penDabFmt(stats.radiusMax, 2)} step0=${penDabFmt(stats.stepMax, 3)} " +
                "stamped=${stats.stamped}",
        )
    }

    /**
     * 收笔那条**汇总**日志 ✓（一笔一条 ✓）。
     *
     * 字段（用户点名的格式 ✓，全是**真算出来**的 ✓）：
     * ```
     * [PenDab] dabs=37 dist=284.0px r=min 3.2/max 12.8px step=min 0.35/max 2.40px \
     *   worstStepOverR=0.31 spacing=10% nibRatio=1.00 angleControl=1 brushPx=24 zoom=0.42 dpr=1.0
     * ```
     *  · `worstStepOverR = step / (2 × radius)` 的**最坏值** ✓ —— **这就是"会不会断"的判据**：
     *    正常 `≤ 0.5` ✓（相邻笔尖至少重叠一半 ✓），`> 1` = 两颗笔尖完全不挨着 = **必成一排点** ✗；
     *  · `r=` 是**这一颗笔尖自己**的半径（压感之后的 ✓）—— 它和 `step=` 是配套的 ✓
     *    （"步距按基础半径、笔尖按压感半径"那个老毛病会在这里一眼露馅 ✓）；
     *  · `zoom` / `dpr` 是**落笔那一刻**的值 ✓（见 [logPenDabBegin] ✓）—— 屏幕上的点距 = 这里的
     *    `step`（原图像素）× 纸的 fit 比 × `zoom` ✓，所以要判"屏幕点距 ≈ 4~6px"有没有被放大，
     *    就得有这两个数 ✓；
     *  · `segments=` = 这一笔走了几段（收到的有效输入点数 ✓）⇒ **`dabs / segments ≈ 1` 就是
     *    "每个输入事件只盖一颗" = 屏幕上一排小点 ✗✗**（点距 = 手速 × 采样间隔，所以间距会跟着
     *    手速变 ✓）；`dabs / segments ≫ 1` 才算走位真的在工作 ✓ —— 这一格是"一排点"最直接的判据 ✓；
     *  · `samples=` / `sampleReuses=`（第 ㉔ 批 ✓）= 混色采样**真的扫了几次像素** / 其中**复用**了几次 ✓
     *    （只有 `blending / water / colorStretch > 0` 的笔才可能非 0 ✓）；
     *  · `bitmapBuilds=`（第 ㉔ 批 ✓）= **整页显示位图重建了几次** ✓ ——
     *    用户报的「不跟手 / 卡顿」在数字上就是它太大 ✓（一笔只该 1~2 次 ✓）。
     */
    private fun logPenDabEnd(stats: StrokeDabStats) {
        // 第 ㉚ 批：**输入率实测**（`pt/s` ✓）—— 见 [ImageEditSession.strokeInputStats] ✓。
        // 这一格就是"软件这边一个事件一个点"到底缺不缺采样的**证据** ✓：
        // 网页靠 `getCoalescedEvents()` 一帧能吃到几十个点 ✓，软件这边的 `pt/s` 就是
        // 输入设备真正送到引擎的采样率 ✓（鼠标 ~125Hz、数位板 RTS 通常 133~200Hz ✓）。
        val input = comicPaintSessionState?.strokeInputStats()
        // ---- 第 ㉝ 批 ①：**"每帧几个点 / 攒了几个点"**（用户点名要打进日志的那两个数 ✓）----
        // 口径见 [StrokeInputCoalescer]：`inputPtsPerFrame` = 喂进去的点 ÷ **有点的帧数** ✓、
        // `inputMaxFramePts` = 最挤的那一帧攒了几个 ✓（"一帧一个点"与"一帧一把点"一眼就分得出来 ✓）。
        // ⚠️ `inputPending` 收笔时**必须是 0** ✓（不是 0 = 尾巴没喂 = 丢点 ✗）。
        val coal = comicStrokeInput.stats()
        val coalText = " inputFrames=${coal.frames} inputPtsPerFrame=" +
            "${penDabFmt(coal.pointsPerFrame.toFloat(), 1)} inputMaxFramePts=${coal.peakFramePoints} " +
            "inputLastFramePts=${coal.lastFramePoints} inputPending=${coal.pending} " +
            // ㉝① 回归修复（笔优先 ✓）：这一笔是不是笔在画 ✓ + 丢掉了几个"被提升出来的鼠标点" ✓
            "inputPenDriven=${coal.penDriven} inputDroppedMouse=${coal.droppedMouse}"
        val inputText = if (input == null) {
            "inputPts=n/a ptPerSec=n/a$coalText"
        } else {
            "inputPts=${input.points} ptPerSec=${penDabFmt(input.pointsPerSecond.toFloat(), 0)} " +
                "strokeMs=${penDabFmt(input.elapsedMs.toFloat(), 1)} stabilize=${penDabFmt(comicPaintStabilize, 0)}%" +
                coalText
        }
        logInfo(
            "PenDab",
            "[PenDab] dabs=${stats.dabs} dist=${penDabFmt(stats.distance, 1)}px " +
                "r=min ${penDabFmt(stats.radiusMin, 2)}/max ${penDabFmt(stats.radiusMax, 2)}px " +
                "step=min ${penDabFmt(stats.stepMin, 2)}/max ${penDabFmt(stats.stepMax, 2)}px " +
                "worstStepOverR=${penDabFmt(stats.worstStepOverRadius, 2)} " +
                "spacing=${penDabFmt(stats.spacingPercent, 0)}% " +
                "nibRatio=${penDabFmt(stats.nibRatio, 2)} angleControl=${stats.angleControl} " +
                "brushPx=$comicPaintBrushPixels " +
                "zoom=${penDabFmt(comicPenStrokeZoom, 3)} dpr=${penDabFmt(comicPenStrokeDpr, 2)} " +
                "dabsStamped=${stats.stamped} segments=${stats.segments} " +
                // ㉔ 批：**结构性计数**（用户点名"每 100 颗笔尖采样几次 / 位图重建几次" ✓）——
                // `samples` / `sampleReuses` 来自引擎（采样那一半 ✓），
                // ㉜a 批：`viewportFrames` = 这一段会话到现在**视口那一帧真的重建了几次** ✓
                //（旧口径 `bitmapBuilds` 数的是"整页 33 MB 位图"✗ —— 那条链已经删掉了 ✓；
                //  这两个数**不可比**，别拿新数去跟旧数比 ✓）。
                "samples=${stats.samples} sampleReuses=${stats.sampleReuses} " +
                "viewportFrames=$comicPaintViewportFrames pageReadbacks=$comicPaintPageReadbacks " +
                // ㉚ 批：输入侧（采样点数 / 实测采样率 / 这一笔的挂钟时间 / 当时开的稳定器 ✓）
                inputText,
        )
    }

    /** 日志里的数字格式（**小数点固定 `.`** ✓ —— 免得某些区域设置写出 `3,2` 让日志没法解析 ✗）。 */
    private fun penDabFmt(value: Float, decimals: Int): String =
        if (!value.isFinite()) {
            "n/a"
        } else {
            String.format(java.util.Locale.US, "%.${decimals}f", value)
        }

    /**
     * 画到某点（**逐点压感**在这里进来 ✓）。
     *
     * ⚠️ 第 ㉜a 批：笔**直接画在层表面上** ✓ —— 这一颗落下去，屏幕上**立刻**就是对的
     *（显示每帧从表面读可见区 ✓）。这里只把"视口那一帧作废"的通知发出去 ✓
     *（`noteComicPaintFrameChanged` 只是 +1 一个计数器 ✓，**不建任何位图、不碰 pixels** ✓）。
     *
     * @param pressure 这一点的**笔压** 0..1 ✓（默认 1 = 鼠标 / 触摸那条老路 ✓）。
     * @return 真的盖了至少一个笔尖才 true ✓。
     */
    fun comicPaintStrokeTo(
        x: Float,
        y: Float,
        pressure: Float = 1f,
    ): Boolean {
        val session = comicPaintSessionState ?: return false
        if (!session.isDrawing) return false
        val stamped = session.strokeTo(x, y, pressure)
        if (stamped) noteComicPaintFrameChanged()
        return stamped
    }

    /**
     * 收笔 ✓ —— 顺带打**这一笔唯一的那条汇总日志**（`[PenDab]` ✓，见 [logPenDabEnd] ✓）。
     *
     * ⚠️ 第 ㉜a 批：抬笔**不做任何显示工作** ✓（没有"整页位图重建"、没有"叠加层"）——
     * 墨迹从落笔那一刻起就在层表面上 ✓，`endStroke` 只是把**碰过的那几块**记成历史的一步 ✓。
     *
     * ⚠️ 只有"这一笔真的在画"（`session.isDrawing` ✓）且**真的落过笔尖**（`stats.hasDabs` ✓）
     * 才打日志 ✓ —— 别的调用方（切层 / 撤销 / 收工）也会走到 `endStroke`，那些不该刷日志 ✗。
     */
    fun endComicPaintStroke() {
        val session = comicPaintSessionState ?: return
        // ⚠️ 第 ㉝ 批 ①：抬笔**先把攒下的尾巴喂完** ✓（一个点都不许丢 ✗ ——
        //    快划时最后一帧往往还攒着好几个点 ✓），喂完再收笔 ✓。
        //    放这里（而不是放在界面那几个调用点）是故意的：**任何**收笔路径都走这一条 ✓，
        //    漏一处就是"尾巴丢了"✗（`pending == 0` 是判据之一 ✓，见 `CoalescedInputSamplingTest` ✓）。
        flushComicPaintStrokePoints()
        val wasDrawing = session.isDrawing
        val stats = session.strokeDabStats()
        session.endStroke()
        if (wasDrawing) {
            // ⚠️ 第 ㉜a 批：抬笔**什么显示工作都不做** ✓✗ ——
            // 墨迹从落笔那一刻起就在层表面上（显示每帧读它 ✓），所以既没有"整页位图重建"（㉖ 的 105 ms ✗）、
            // 也没有"叠加层要不要丢"的问题（㉔ 那套整条删掉了 ✓）。
            // 这里只把"下一位重画"通知出去（**不碰 pixels、不重建任何位图** ✓）。
            noteComicPaintFrameChanged()
        }
        if (wasDrawing && stats.hasDabs) logPenDabEnd(stats)
        syncComicPaintHistory()
    }

    /**
     * 层内容**真的变了**（撤销 / 重做 / 油漆桶 / 抬笔合层 / 选区落回 …）⇒ 让显示重画一次 ✓。
     *
     * ⚠️ 第 ㉜a 批起这里**一个位图都不建** ✓：显示是"每帧从层表面读可见区"（[comicPaintViewportFrame] ✓），
     * 所以"内容变了"只需要：① `comicPaintVersion++`（别的界面读它 ✓）、② 视口那一帧作废 ✓。
     */
    private fun comicPaintContentChanged() {
        comicPaintVersion++
        noteComicPaintFrameChanged()
    }

    /**
     * **视口那一帧**要用的内容版本号（界面在 draw 阶段读它 ✓）。
     *
     * 两个都要：`pixelsRevision` 是**真源**那一侧（每动一次层像素 +1 ✓），
     * [comicPaintFrameTick] 是"笔画进行中"那一侧（每颗笔尖 +1 ✓ —— 那时层像素**已经**被回读同步过，
     * 所以其实两者会一起动 ✓，这里是"漏了一处也不会画错"的安全网 ✓）。
     */
    fun comicPaintFrameRevision(): Long =
        (comicPaintVersion.toLong() shl 32) or
            ((comicPaintSessionState?.pixelsRevision ?: 0).toLong() shl 8) or
            (comicPaintFrameTickState.toLong() and 0xFF)

    /** 油漆桶：在点到的位置灌一次（颜色 = 当前色，容忍度 = 官方默认 15 ✓）。 */
    fun fillComicPaintAt(x: Int, y: Int) {
        val session = comicPaintSessionState ?: return
        if (session.fillAt(x, y, comicPaintColor, DEFAULT_FILL_TOLERANCE)) {
            comicPaintContentChanged()
            syncComicPaintHistory()
        }
    }

    /** 吸管：取当前像素的颜色（吸完**自动切回画笔** ✓ —— 和画布编辑器一个手感 ✓）。 */
    fun pickComicPaintColor(x: Int, y: Int) {
        val session = comicPaintSessionState ?: return
        comicPaintColor = session.colorAt(x, y)
        if (comicPaintTool == CanvasTool.PICKER) comicPaintTool = CanvasTool.DRAW
        status = rf("canvas.colorPicked", mapOf("color" to colorHex(comicPaintColor)))
    }

    fun undoComicPaint() {
        val session = comicPaintSessionState ?: return
        if (session.undo()) {
            comicPaintContentChanged()
            syncComicPaintHistory()
        }
    }

    fun redoComicPaint() {
        val session = comicPaintSessionState ?: return
        if (session.redo()) {
            comicPaintContentChanged()
            syncComicPaintHistory()
        }
    }

    // ---- 选区（**只有 SELECT / LASSO 才走这条** ✓ —— ⑦ 的核心）----

    /** 画一个框（还没抬像素 ✓）。拖出来的过程中会反复调它 ✓。 */
    fun updateComicPaintSelection(shape: SelectionShape?) {
        if (comicPaintSelectionFloating) commitComicPaintSelection()
        comicPaintSelection = shape
        // 选区留在画面上、"只在选区内工作"（和画布编辑器同一个口径 ✓）
        comicPaintSessionState?.setSelectionClip(shape)
    }

    fun clearComicPaintSelection() {
        if (comicPaintSelectionFloating) commitComicPaintSelection()
        comicPaintSelection = null
        comicPaintSessionState?.setSelectionClip(null)
    }

    /** 把选区里的像素**抬起来**（第一次移动 / 缩放时调 ✓）。 */
    fun liftComicPaintSelection(): Boolean {
        val session = comicPaintSessionState ?: return false
        if (comicPaintSelectionFloating) return true
        val shape = comicPaintSelection ?: return false
        if (!session.beginSelection(shape)) return false
        comicPaintSelectionFloating = true
        comicPaintContentChanged()
        return true
    }

    /**
     * ⚠️ **第 ㊺ 批（2026-09-21）**：用户报「**套索和选择选择线条和移动线条时卡到死**」✓
     *
     * **根因**：这两个函数原来调的都是 [comicPaintContentChanged] ✗ —— 它做两件**很贵**的事：
     *  ① `comicPaintVersion++` ⇒ [comicPaintFloatingImage] 的缓存**当场作废** ✗
     *     ⇒ **每一帧都重建一整块 patch 位图**（`composeImageOf` ✓，选区一大就是几十 ms ✗）；
     *  ② 视口那一帧作废 ⇒ **每帧把可见区整块回读 + 缩放** ✗。
     *
     * 而"拖动 / 缩放浮动选区"**一个像素都没变** ✓ —— 变的只是它的 `dx/dy/scale` ✓
     *（界面按 `graphicsLayer` 的 `translationX/Y/scaleX` 画 ✓，见 `ComicModeScreen` 那段 ✓）
     * ⇒ 上面两件全是**白烧** ✗，而且是**每帧**白烧 ✓（手一拖就卡死 ✓）。
     *
     * **修法**：拖动 / 缩放只推 [noteComicPaintFloatChanged] 那个**便宜**的计数 ✓
     *（只让"浮动块那一层"重画 ✓）—— **不动** `comicPaintVersion` ✓：
     * patch 位图的缓存留在原地 ✓（内容真的没变 ✓），视口那一帧也不用重读 ✓
     *（浮动块是**画在视口之上**的一层 ✓，不在视口帧里 ✓）。
     */
    fun translateComicPaintSelection(deltaX: Float, deltaY: Float) {
        val session = comicPaintSessionState ?: return
        if (!comicPaintSelectionFloating) return
        session.translateSelection(deltaX, deltaY)
        noteComicPaintFloatChanged()
    }

    fun scaleComicPaintSelection(factorX: Float, factorY: Float, anchorX: Float, anchorY: Float) {
        val session = comicPaintSessionState ?: return
        if (!comicPaintSelectionFloating) return
        session.scaleSelection(factorX, factorY, anchorX, anchorY)
        noteComicPaintFloatChanged()
    }

    /**
     * **浮动选区的变换变了**（拖动 / 缩放 / 旋转 ✓）—— 只推这一个计数 ✓。
     *
     * 界面读它来重画"浮动块那一层" ✓（`graphicsLayer` 的几个值来自 [FloatingSelection] 的
     * 普通字段 ✗ —— **不是快照状态** ✓，所以没有这个计数的话界面根本不会重组 ✓、
     * 手拖就看不到块在动 ✗）。
     *
     * ⚠️ **绝不能**换成 `comicPaintContentChanged()` ✗ —— 那一句就是"移动到卡死"的根因 ✓
     *（见 [translateComicPaintSelection] 的说明 ✓）。
     */
    private fun noteComicPaintFloatChanged() {
        comicPaintFloatTick++
    }

    /**
     * 浮动选区变换的版本号 ✓（每拖动 / 缩放一帧 +1 ✓）—— 界面据此**只重画浮动块那一层** ✓。
     */
    var comicPaintFloatTick by mutableIntStateOf(0)
        private set

    /** **落回**：把变换后的内容盖回画布（整次算一步历史 ✓）。 */
    fun commitComicPaintSelection() {
        val session = comicPaintSessionState ?: return
        val floating = session.floatingSelection
        if (floating == null) {
            comicPaintSelectionFloating = false
            return
        }
        val dx = floating.dx
        val dy = floating.dy
        val shape = comicPaintSelection
        if (session.commitSelection()) {
            val width = session.width.coerceAtLeast(1)
            val height = session.height.coerceAtLeast(1)
            if (shape != null && (dx != 0f || dy != 0f)) {
                val moved = SelectionOps.shiftedShape(shape, dx / width, dy / height)
                comicPaintSelection = moved
                session.setSelectionClip(moved)
            }
            comicPaintContentChanged()
            syncComicPaintHistory()
        }
        comicPaintSelectionFloating = false
    }

    /** 选区的像素尺寸（界面上显示 `120×80` 用 ✓）。 */
    fun comicPaintSelectionSize(): Pair<Int, Int> {
        val shape = comicPaintSelection ?: return 0 to 0
        val session = comicPaintSessionState ?: return 0 to 0
        return SelectionOps.sizeOf(shape, session.width, session.height)
    }

    /**
     * **被抬起来的那块像素**的显示位图（按 [comicPaintVersion] 缓存 ✓，和主位图一个口径 ✓）。
     *
     * 为什么要它 ✗：抬起 = 那块像素**已经从画布上被抠走了**（原处清成透明 ✓）——
     * 不把它画出来的话，"拖选区"的整个过程里那块内容是**看不见的**，
     * 松手（落回）才突然出现 ✗ —— 手感就是"拖了个寂寞"。
     *
     * ⚠️ 第 ㉜a 批：这条**留着** ✓ —— 它不是"层显示链"（那一条整条删了 ✓），
     * 而是**浮动选区那一块的叠加层**（页缓冲上的一小块，最大也就选区那么大 ✓）。
     */
    fun comicPaintFloatingImage(): ImageBitmap? {
        val session = comicPaintSessionState ?: return null
        val floating = session.floatingSelection ?: return null
        val cached = comicPaintFloatBitmap
        if (cached != null && comicPaintFloatBitmapVersion == comicPaintVersion) return cached
        val built = images.composeImageOf(floating.pixels, floating.patchWidth, floating.patchHeight)
        comicPaintFloatBitmap = built
        comicPaintFloatBitmapVersion = comicPaintVersion
        return built
    }

    private fun syncComicPaintHistory() {
        val session = comicPaintSessionState
        comicPaintCanUndo = session?.canUndo == true
        comicPaintCanRedo = session?.canRedo == true
    }

    // ==================================================================
    // 高级漫画 · ⑥ 图层 UI 的落点 / ⑦ 拼页导出（docs/43 §9.3 §10.0000000）
    // ==================================================================
    //
    // 用户口径：
    //  · 「图层功能，**生的图都是一个新图层**，方便修改」→ 每跑出一张就 [addGenerated] 一层 ✓
    //    （落在 [setComicPanelResultPath] 里 —— 那是"这一格的图出来了"唯一的收口处 ✓）；
    //  · 「所有格子出图后**拼成整页** → 进图库 / 导出 PNG」→ [exportComicPage] ✓。
    //
    // 分工（一块都不重复造 ✗）：
    //  · 几何（页尺寸 / 每层落点）= `services/ComicPageExporter`（纯函数、有单测 ✓）；
    //  · 像素合成 = `services/ComicComposer.compose`（位图层 ✓）；
    //  · **气泡 / 文本是矢量** ✗ → 电脑端 `Platform.renderComicOverlayLayer`（`ImageComposeScene` ✓）
    //    栅格化成临时 PNG，再当作一个"位图层"按原顺序叠进去 ✓ —— 这样**叠放顺序与透明度**
    //    仍然只有一个来源（`ComicLayerStack` ✓），不会出现"预览一个样、导出另一个样" ✗；
    //  · 落库 = 现成的 `persistGeneratedImages`（`feature = "comic-page"` ✓）。

    /** 正在导出（按钮据此变灰 ✓，也防连点两次各写一份图 ✗）。 */
    var comicExporting by mutableStateOf(false)
        private set

    /** 正在压平（同上 ✓）。 */
    var comicFlattening by mutableStateOf(false)
        private set

    /**
     * **导出这一页**：合成一张 PNG → 进图库 ✓（用户 2026-09-26 口径）。
     *
     * 全流程（每一步都能在失败时**说出原因** ✗ 不静默）：
     *  1. `ComicPageExporter.plan`：页尺寸 = 底板 与 格子并集 取大 ✓；每一层算落点 ✓；
     *  2. 位图层 → `ComicComposer.BitmapPlacement` ✓；矢量层 → [Platform.renderComicOverlayLayer]
     *     栅格化 → 写进 **App 私有目录**的临时 PNG（**绝不进仓库** ✗）→ 也变成一个 placement ✓；
     *  3. `ComicComposer.compose`：纸色底 + 按图层**从下到上**叠 → PNG 字节 ✓；
     *  4. `persistGeneratedImages(feature = "comic-page")` → 历史 / 当前图 / 临时图库 / （按设置）系统相册 ✓；
     *  5. 临时 PNG 一律删掉 ✓（`finally` 里 ✓，失败也不留垃圾 ✓）。
     */
    fun exportComicPage() {
        if (comicBoardPage == null) {
            toast(rt("comic.board.exportNoPage"), isError = true)
            return
        }
        // 正在画的那一层先收工 ✓ —— 不然导出的还是"上一次落盘的样子"（刚画的那几笔不在 ✗）
        commitComicLayerPaint()
        // ⚠️ 必须在 commit **之后**再读一次：commit 会把这一层的 `imagePath` 换掉 ✓，
        //    上面那份读在手里的页还是老路径（拿它去 plan 等于合成一张没画过的图 ✗）
        val page = comicBoardPage
        if (page == null) {
            toast(rt("comic.board.exportNoPage"), isError = true)
            return
        }
        if (comicExporting) return
        comicExporting = true
        viewModelScope.launch {
            try {
                status = rt("comic.board.exportRunning")
                val plan = ComicPageExporter.plan(page) { path ->
                    runCatching { images.size(path) }.getOrNull()
                }
                if (plan.steps.isEmpty()) {
                    toast(rt("comic.board.exportEmpty"), isError = true)
                    return@launch
                }
                val temps = ArrayList<File>()
                try {
                    val placements = ArrayList<ComicComposer.BitmapPlacement>(plan.steps.size)
                    var skippedOverlay = false
                    // ⚠️ 这里用 `for` 而不是 `forEach`：矢量层要**在界面线程上栅格化**
                    //（`ImageComposeScene` 跑在有 Compose 的那条线程上才稳 ✓），
                    // 而编码 / 写临时文件是纯 CPU —— 那段要 `withContext(Default)` 挪走 ✓。
                    // `forEach` 的 lambda 不是 suspend，塞不进 `withContext` ✗。
                    for (step in plan.steps) {
                        when (step) {
                            is ComicPageExporter.Step.Bitmap -> placements += ComicComposer.BitmapPlacement(
                                imagePath = step.imagePath,
                                x = step.x,
                                y = step.y,
                                width = step.width,
                                height = step.height,
                                opacity = step.opacity,
                            )

                            is ComicPageExporter.Step.Overlay -> {
                                // 平台那边直接给**透明底 PNG 字节** ✓（怎么栅格化是它的事 ✓）
                                val png = platform.renderComicOverlayLayer(
                                    page = page,
                                    layerId = step.layerId,
                                    width = plan.pageWidth,
                                    height = plan.pageHeight,
                                )
                                if (png == null || png.isEmpty()) {
                                    // 这个平台不会栅格化矢量层（手机端默认那条 ✓）→ **如实说**，
                                    // 绝不把"少了气泡"伪装成"导出成功" ✗
                                    skippedOverlay = true
                                    continue
                                }
                                val temp = File(
                                    comicScratchDir(),
                                    "overlay-" + step.layerId.hashCode() + "-" + System.nanoTime() + ".png",
                                )
                                withContext(Dispatchers.Default) { temp.writeBytes(png) }
                                temps += temp
                                placements += ComicComposer.BitmapPlacement(
                                    imagePath = temp.absolutePath,
                                    x = 0,
                                    y = 0,
                                    width = plan.pageWidth,
                                    height = plan.pageHeight,
                                    opacity = step.opacity,
                                )
                            }
                        }
                    }
                    if (placements.isEmpty()) {
                        toast(rt("comic.board.exportEmpty"), isError = true)
                        return@launch
                    }
                    // 重活（解码 / 缩放 / 编码一整页）丢到后台 ✓ —— 别把界面冻住 ✗
                    val bytes = withContext(Dispatchers.Default) {
                        ComicComposer.compose(
                            platform = platform,
                            pageWidth = plan.pageWidth,
                            pageHeight = plan.pageHeight,
                            placements = placements,
                            background = comicBlankPixel,
                        )
                    }
                    if (bytes == null) {
                        toast(rt("comic.board.exportFailed"), isError = true)
                        return@launch
                    }
                    val saved = persistGeneratedImages(
                        images = listOf(bytes),
                        params = params,
                        seed = 0L,
                        feature = "comic-page",
                        model = null,
                        width = plan.pageWidth,
                        height = plan.pageHeight,
                        durationMs = null,
                    )
                    if (saved.isEmpty()) {
                        toast(rt("comic.board.exportFailed"), isError = true)
                        return@launch
                    }
                    val done = rf(
                        "comic.board.exportDone",
                        mapOf("w" to plan.pageWidth, "h" to plan.pageHeight),
                    )
                    status = done
                    toast(done, isError = false)
                    if (skippedOverlay) {
                        toast(rt("comic.board.exportOverlaySkipped"), isError = true)
                    }
                } finally {
                    // 临时 PNG 一律清掉 ✓（**它们是 App 私有目录里的中转件**，不是交付物 ✓）
                    temps.forEach { runCatching { it.delete() } }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val why = rf("comic.board.exportFailedWhy", mapOf("error" to cleanError(e)))
                status = why
                toast(why, isError = true)
            } finally {
                comicExporting = false
            }
        }
    }

    /**
     * **压平**（第 ⑥ 项里那个"可选"的合并 ✓）：把**可见的位图层**（底板 + 生成层）合成一张图，
     * 用一层"底板层"顶掉它们 ✓；**气泡 / 文本层原样保留**（它们还是矢量、还能改字 ✓ ——
     * 真正的"连文字一起烤死"是不可逆的，不给用户在界面上误点 ✗）。
     *
     * 合出来的图存在 **App 私有目录**（`<filesDir>/comic/` ✓，**不进仓库** ✗），
     * 并成为新的底板层 ✓ —— 拼页导出从此只有一层位图要叠，效果和压平前逐像素一致 ✓
     *（同一套 `ComicPageExporter` 几何 ✓）。
     */
    fun flattenComicLayers() {
        if (comicBoardPage == null) {
            toast(rt("comic.board.exportNoPage"), isError = true)
            return
        }
        if (comicFlattening || comicExporting) return
        // 正在画的那一层先收工 ✓（压平读的是盘上的图 ✓ —— 不先写，刚画的那几笔不参与合成 ✗）
        commitComicLayerPaint()
        // 同 `exportComicPage`：commit 之后**重读**一次，拿到的才是换过 imagePath 的那一页 ✓
        val page = comicBoardPage
        if (page == null) {
            toast(rt("comic.board.exportNoPage"), isError = true)
            return
        }
        val stack = ComicLayerStack(page.layers)
        if (!stack.canFlatten()) {
            // canFlatten() 为假 = 空叠 / 全隐藏 → **说清原因**，别静默失效 ✗
            toast(rt("comic.board.layerFlattenNothing"), isError = true)
            return
        }
        val plan = ComicPageExporter.plan(page) { path -> runCatching { images.size(path) }.getOrNull() }
        val bitmapSteps = plan.steps.filterIsInstance<ComicPageExporter.Step.Bitmap>()
        if (bitmapSteps.isEmpty()) {
            toast(rt("comic.board.layerFlattenNoBitmap"), isError = true)
            return
        }
        comicFlattening = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    ComicComposer.compose(
                        platform = platform,
                        pageWidth = plan.pageWidth,
                        pageHeight = plan.pageHeight,
                        placements = ComicPageExporter.bitmapPlacements(bitmapSteps),
                        background = comicBlankPixel,
                    )
                }
                if (bytes == null) {
                    toast(rt("comic.board.layerFlattenFailed"), isError = true)
                    return@launch
                }
                val target = File(
                    comicScratchDir(),
                    "flat-" + System.currentTimeMillis().toString() + ".png",
                )
                target.writeBytes(bytes)
                // 新的叠：一张"底板"（= 刚合出来的那张）+ 原来的气泡 / 文本层（顺序不变 ✓）
                val flatBase = ComicLayer(
                    id = "$page.id-flat-" + System.currentTimeMillis().toString(),
                    name = COMIC_BASE_LAYER_NAME,
                    kind = ComicLayer.Kind.BASE,
                    imagePath = target.absolutePath,
                )
                val overlays = page.layers.filter {
                    it.kind == ComicLayer.Kind.BUBBLE || it.kind == ComicLayer.Kind.TEXT
                }
                updateComicBoardPage { it.withLayers(listOf(flatBase) + overlays) }
                persistComicBoard()
                toast(
                    rf("comic.board.layerFlattened", mapOf("count" to bitmapSteps.size)),
                    isError = false,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                toast(rf("comic.board.exportFailedWhy", mapOf("error" to cleanError(e))), isError = true)
            } finally {
                comicFlattening = false
            }
        }
    }

    /** 导出 / 压平的中转目录：**App 私有目录**（`<filesDir>/comic/` ✓，绝不写进仓库 ✗）。 */
    private fun comicScratchDir(): File =
        File(platform.paths.filesDir, "comic").apply { if (!exists()) mkdirs() }

    // ==================================================================
    // 高级漫画 · ⑧ **导出 PSD**（用户口径 2026-09-20：「加入导出功能，导出 psd 文件」）
    // ==================================================================
    //
    // 和「导出这一页 PNG」[exportComicPage] **共用同一套几何与合成** ✓ —— 一条都不重造 ✗：
    //  · 几何（页尺寸 / 每层落点）= `services/ComicPageExporter`（纯函数、有单测 ✓）；
    //  · 合并图 = 位图 `ComicComposer.compose` + 矢量 `Platform.renderComicOverlayLayer`
    //    （**和 PNG 那条逐字同一段** ✓，只是出口从"进图库"换成"PSD 的合并图" ✓）；
    //  · 逐层像素 = 位图层按几何给的矩形（底板 Fit 整页 / 生成层 Cover 进它自己那一格 ✓）、
    //    矢量层直接用整页大小的栅格化结果（**透明底** ✓，于是它在 PSD 里也只是一层 ✓）；
    //  · 落笔 = `services/PsdWriter`（零依赖、已用 psd-tools + Pillow 逐像素验过 ✓，**一个字都不改** ✗）。
    //
    // ⚠️ 内存：整页 ARGB 数组本身就不小（2480×3508 ≈ 35 MB ✓），PSD 是**逐层**都要一块 ——
    //    层数越多峰值越高（见 `docs/43` 与本次交付说明里"未验证"那一条 ✓）。这一版先如实跑，
    //    真要抠峰值再上"边写边丢"的流式写法 ✗（那是 `PsdWriter` 那一层的事，不动它 ✓）。

    /** 正在导出 PSD（按钮据此变灰 ✓ —— 与 [comicExporting] 一个口径 ✓，也防连点两次 ✗）。 */
    var comicPsdExporting by mutableStateOf(false)
        private set

    /** 上一次 [buildComicPsd] 有几层没能写进去（0 = 一层不落 ✓）—— 界面据此**如实**提示 ✓。 */
    var comicPsdSkippedLayers by mutableIntStateOf(0)
        private set

    /**
     * **合成 + 建一份 PSD 字节**（可测的纯逻辑：**不弹任何对话框、不落盘** ✓ —— 落盘见 [exportComicPsdTo] ✓）。
     *
     * @return 完整 PSD 字节；没有页 / 这一页什么都没有 / 合成失败 / 写出器拒绝 → `null` ✓
     *   （**不抛** ✗，并且把原因写进 [status] ✓）。
     */
    suspend fun buildComicPsd(): ByteArray? {
        // 正在画的那一层先收工 ✓（PSD 逐层读的是盘上的图 ✓ —— 不先写，刚画的那几笔不在 PSD 里 ✗）
        commitComicLayerPaint()
        val page = comicBoardPage
        if (page == null) {
            comicPsdSkippedLayers = 0
            status = rt("comic.board.psdNoPage")
            return null
        }
        // 只读文件头拿尺寸（和 `exportComicPage` 同一口径 ✓：解不开 = 这一层不参与几何 ✓）
        val sizeOf: (String) -> Pair<Int, Int>? = { path -> runCatching { images.size(path) }.getOrNull() }
        val plan = ComicPageExporter.plan(page, sizeOf)
        val pageWidth = plan.pageWidth
        val pageHeight = plan.pageHeight
        if (plan.steps.isEmpty()) {
            comicPsdSkippedLayers = 0
            status = rt("comic.board.exportEmpty")
            return null
        }

        val temps = ArrayList<File>()
        try {
            // ---- ① 合并图：和「导出这一页」同一段（位图 + 矢量栅格化 → compose → PNG ✓）----
            // ⚠️ 矢量层要**在界面线程上栅格化**（`ImageComposeScene` 跑在有 Compose 的那条线程上才稳 ✓），
            // 纯 CPU 那几段（编码 / 解码 / 缩放）才 `withContext(Default)` 挪走 ✓ —— 同 `exportComicPage` ✓。
            val placements = ArrayList<ComicComposer.BitmapPlacement>(plan.steps.size)
            // 栅格化过的矢量层像素：**这一轮顺手留着**当 PSD 层用 ✓（不然同一层要渲两遍 ✗）
            val overlayPixels = HashMap<String, IntArray>()
            var overlaySkipped = 0
            for (step in plan.steps) {
                when (step) {
                    is ComicPageExporter.Step.Bitmap -> placements += ComicComposer.BitmapPlacement(
                        imagePath = step.imagePath,
                        x = step.x,
                        y = step.y,
                        width = step.width,
                        height = step.height,
                        opacity = step.opacity,
                    )

                    is ComicPageExporter.Step.Overlay -> {
                        val png = platform.renderComicOverlayLayer(
                            page = page,
                            layerId = step.layerId,
                            width = pageWidth,
                            height = pageHeight,
                        )
                        if (png == null || png.isEmpty()) {
                            // 这个平台不栅格化矢量层（手机端默认那条 ✓）→ **如实说**，绝不假装画过了 ✗
                            overlaySkipped++
                            continue
                        }
                        withContext(Dispatchers.Default) { argbOfPng(png, pageWidth * pageHeight) }
                            ?.let { overlayPixels[step.layerId] = it }
                        val temp = File(
                            comicScratchDir(),
                            "psd-overlay-" + step.layerId.hashCode() + "-" + System.nanoTime() + ".png",
                        )
                        withContext(Dispatchers.Default) { temp.writeBytes(png) }
                        temps += temp
                        placements += ComicComposer.BitmapPlacement(
                            imagePath = temp.absolutePath,
                            x = 0,
                            y = 0,
                            width = pageWidth,
                            height = pageHeight,
                            opacity = step.opacity,
                        )
                    }
                }
            }
            if (placements.isEmpty()) {
                comicPsdSkippedLayers = 0
                status = rt("comic.board.exportEmpty")
                return null
            }
            val composedPng = withContext(Dispatchers.Default) {
                ComicComposer.compose(
                    platform = platform,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    placements = placements,
                    background = comicBlankPixel,
                )
            }
            if (composedPng == null) {
                comicPsdSkippedLayers = 0
                status = rt("comic.board.exportFailed")
                return null
            }
            val composite = withContext(Dispatchers.Default) {
                argbOfPng(composedPng, pageWidth * pageHeight)
            }
            if (composite == null) {
                comicPsdSkippedLayers = 0
                status = rt("comic.board.exportFailed")
                return null
            }

            // ---- ② 逐层：从下到上（= 叠顺序 ✓），每层一块 ARGB ----
            // 层矩形来自**同一套几何** ✓：把"可见性 / 透明度"临时抹平后再 `plan` 一次 ——
            // 这样**隐藏 / 全透明的层也有它该在的落点** ✓（PSD 里它们是 `visible=false` 的层，
            // 而不是凭空消失 ✗ —— 那正是 PSD 相对于"压平一张 PNG"的意义 ✓）。
            val flattenedGeometry = ComicPageExporter.plan(
                page.copy(layers = page.layers.map { it.copy(visible = true, opacity = 1f) }),
                sizeOf,
            )
            val bitmapStepById = flattenedGeometry.steps
                .filterIsInstance<ComicPageExporter.Step.Bitmap>()
                .associateBy { it.layerId }

            val stack = ComicLayerStack(page.layers).all
            val psdLayers = ArrayList<PsdWriter.Layer>(stack.size)
            var skipped = overlaySkipped
            for (layer in stack) {
                val alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
                // ⚠️ **位图层 = 底板 / 生成层 / 绘画层** ✓（第 ⑬ 批加的 PAINT 也在这里 ✓）——
                // 少了它，绘画层会掉进下面那条"矢量层"的分支：`renderComicOverlayLayer`
                // 拿一个层 id 去渲气泡/文本，渲不出来 → 被当成"跳过一层"✗（画的东西整层丢 ✓）。
                val isBitmap = layer.kind == ComicLayer.Kind.BASE ||
                    layer.kind == ComicLayer.Kind.GENERATED ||
                    layer.kind == ComicLayer.Kind.PAINT
                if (isBitmap) {
                    // 没有图 / 图读不出来 / 矩形算不出来（几何那边就跳过了）→ **这一层跳过** ✓，不画错位置 ✗
                    val step = bitmapStepById[layer.id]
                    if (step == null) {
                        skipped++
                        continue
                    }
                    val moved = withContext(Dispatchers.Default) { psdBitmapLayer(step, pageWidth, pageHeight) }
                    if (moved == null) {
                        skipped++
                        continue
                    }
                    psdLayers += PsdWriter.Layer(
                        name = layer.name,
                        x = moved.x,
                        y = moved.y,
                        width = moved.width,
                        height = moved.height,
                        argb = moved.argb,
                        visible = layer.visible,
                        opacity = alpha,
                    )
                } else {
                    // 气泡 / 文本（矢量）：现成的"整页大小、透明底"栅格化 ✓ → 直接当这一层的像素 ✓
                    val cached = overlayPixels[layer.id]
                    val argb = if (cached != null) {
                        cached
                    } else {
                        val png = platform.renderComicOverlayLayer(
                            page = page,
                            layerId = layer.id,
                            width = pageWidth,
                            height = pageHeight,
                        )
                        if (png == null || png.isEmpty()) {
                            skipped++
                            continue
                        }
                        val decoded = withContext(Dispatchers.Default) { argbOfPng(png, pageWidth * pageHeight) }
                        if (decoded == null) {
                            skipped++
                            continue
                        }
                        decoded
                    }
                    psdLayers += PsdWriter.Layer(
                        name = layer.name,
                        x = 0,
                        y = 0,
                        width = pageWidth,
                        height = pageHeight,
                        argb = argb,
                        visible = layer.visible,
                        opacity = alpha,
                    )
                }
            }
            if (psdLayers.isEmpty()) {
                comicPsdSkippedLayers = skipped
                status = rt("comic.board.psdFailed")
                return null
            }

            // ---- ③ 写字节（RLE ✓；写出器自己会校验尺寸 / 数组长度，非法一律 null 不抛 ✓）----
            val bytes = withContext(Dispatchers.Default) {
                PsdWriter.write(pageWidth, pageHeight, composite, psdLayers, rle = true)
            }
            comicPsdSkippedLayers = skipped
            if (bytes == null) {
                status = rt("comic.board.psdFailed")
                logWarn("ComicPsd", "PsdWriter 拒绝了这一页：${pageWidth}×$pageHeight、${psdLayers.size} 层")
                return null
            }
            status = rf("comic.board.psdDone", mapOf("kb" to ((bytes.size + 1023) / 1024)))
            logInfo(
                "ComicPsd",
                "PSD 写出：${pageWidth}×$pageHeight、${psdLayers.size} 层、" +
                    "${bytes.size} 字节（跳过 $skipped 层）",
            )
            if (skipped > 0) {
                logWarn("ComicPsd", "有 $skipped 层没能写进 PSD（没图 / 图读不出来 / 这一层栅格化失败）")
            }
            return bytes
        } finally {
            // 临时 PNG（矢量层的栅格化中转件）一律清掉 ✓ —— **它们是 App 私有目录里的中转件** ✓
            temps.forEach { runCatching { it.delete() } }
        }
    }

    /**
     * **把当前页导出成 PSD 文件**（`ref` 由宿主给：电脑 = 「另存为」对话框选中的那个路径 ✓）。
     *
     * 三条口径（和 [exportImageFile] / [exportBackupToImage] 那两条一致 ✓）：
     *  · **只在用户选定的 ref 上写** ✓ —— 用户取消（界面拿到 null 就不调这里 ✓）时**什么都不做** ✓，
     *    也**绝不写进仓库目录** ✗；
     *  · 写盘在 `Dispatchers.IO` ✓（一整页 PSD 可能是几十 MB，别卡界面 ✗）；
     *  · 失败**说得出原因**（写不进去 / 合成不出 / 没有页 ✓），成功时报出**字节数（KB）** ✓。
     */
    fun exportComicPsdTo(ref: String) {
        if (comicPsdExporting) return
        comicPsdExporting = true
        viewModelScope.launch {
            try {
                status = rt("comic.board.psdRunning")
                val bytes = buildComicPsd()
                if (bytes == null) {
                    // buildComicPsd 已经把原因写进 status 了（没有页 / 没内容 / 合成失败 ✓）
                    toast(status, isError = true)
                    return@launch
                }
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        platform.openOutput(ref)?.use { out ->
                            out.write(bytes)
                            out.flush()
                        } ?: error("打不开目标文件")
                    }.isSuccess
                }
                if (!written) {
                    toast(rt("comic.board.psdFailed"), isError = true)
                    return@launch
                }
                val done = rf("comic.board.psdDone", mapOf("kb" to ((bytes.size + 1023) / 1024)))
                status = done
                toast(done, isError = false)
                // 有层没写进去 → **在"成功"之后再补一条**（同 `exportComicPage` 的 exportOverlaySkipped ✓）
                if (comicPsdSkippedLayers > 0) {
                    toast(
                        rf("comic.board.psdSkipped", mapOf("count" to comicPsdSkippedLayers)),
                        isError = true,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val why = rf("comic.board.exportFailedWhy", mapOf("error" to cleanError(e)))
                status = why
                toast(why, isError = true)
            } finally {
                comicPsdExporting = false
            }
        }
    }

    /** PNG 字节 → **非预乘** ARGB 像素（用完 `recycle()` ✓）；解不开 / 像素数对不上 → null ✓。 */
    private fun argbOfPng(png: ByteArray, expectedPixels: Int): IntArray? {
        val image = images.decodeBytes(png) ?: return null
        val pixels = runCatching { images.pixelsOf(image) }.getOrNull()
        image.recycle()
        if (pixels == null || pixels.size != expectedPixels) return null
        return pixels
    }

    /** 搬好的位图层：**页内**矩形 + 该矩形的非预乘 ARGB（尺寸 == width×height ✓）。 */
    private class PsdBitmapLayer(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val argb: IntArray,
    )

    /**
     * 把一个 [ComicPageExporter.Step.Bitmap] 变成 PSD 的一层：**按几何给的矩形缩放**，
     * 再**裁进页内**（底板 Fit 整页 / 生成层 Cover 进格子 ✓ —— 都是几何那边算好的 ✓，这里不再另算 ✗）。
     *
     * 为什么要裁 ✗：`cover` 出来的矩形**本来就可能超出画布**（300×300 的格子里放 600×300 的图 →
     * 左右各溢出 150 ✓）—— 合成 PNG 时那些像素也是被裁掉的 ✓，PSD 里照抄同一个矩形，
     * 层坐标就会跑到页外（Photoshop 打开时看着"位置偏了" ✗）。裁进页内 = 与合成图逐像素一致 ✓。
     *
     * @return 搬好的层；解码不了 / 矩形退化到页外 → null ✓（调用方**跳过这一层并如实计数** ✓）
     */
    private fun psdBitmapLayer(
        step: ComicPageExporter.Step.Bitmap,
        pageWidth: Int,
        pageHeight: Int,
    ): PsdBitmapLayer? {
        if (step.width <= 0 || step.height <= 0) return null
        val image = images.decodeFull(step.imagePath) ?: return null
        // ⚠️ `scale` 在"尺寸正好一样"时会把**同一张**还回来（电脑端就是这实现的 ✓）——
        // 那种情况不能回收两次 ✗
        val scaled = runCatching { images.scale(image, step.width, step.height, smooth = true) }.getOrNull()
        if (scaled == null) {
            image.recycle()
            return null
        }
        val pixels = runCatching { images.pixelsOf(scaled) }.getOrNull()
        if (scaled !== image) scaled.recycle()
        image.recycle()
        if (pixels == null || pixels.size < step.width * step.height) return null

        val left = step.x.coerceAtLeast(0)
        val top = step.y.coerceAtLeast(0)
        val right = (step.x + step.width).coerceAtMost(pageWidth)
        val bottom = (step.y + step.height).coerceAtMost(pageHeight)
        if (right <= left || bottom <= top) return null
        val width = right - left
        val height = bottom - top
        val out = IntArray(width * height)
        for (row in 0 until height) {
            val from = (top - step.y + row) * step.width + (left - step.x)
            System.arraycopy(pixels, from, out, row * width, width)
        }
        return PsdBitmapLayer(left, top, width, height, out)
    }

    // ==================================================================
    // 高级漫画 · 第 ③ 批：**队列逐格生成**（跑这一页）
    // ==================================================================
    //
    // 用户口径（`docs/43` §7 / §Q2）：「**队列跑，确定完格子后按序号每次一张跑完一页**」✓，
    // 且「**花钱前必须确认**」✓（§5 风险 1：逐格生成 = 多次付费调用）。
    //
    // 分工：
    //  · 顺序与状态 = `models/GenerationQueue.kt`（纯逻辑、有单测 ✓）；
    //  · 花费 = `models/ComicCost.kt`（价格函数注入 ✓）+ **官方报价接口**（`NaiApi` ✓，不自己拍数字 ✗）；
    //  · 尺寸 = `models/ComicPage.kt` 的 `ComicRunPlan`（确认框与执行器**共用同一份** ✓）；
    //  · 真花钱那一下 = **现成的聚焦重绘那条路**（`FocusedInpaint.plan` + `api.inpaint` +
    //    `persistGeneratedImages` ✓）—— 没有新的付费调用 ✗。
    //
    // ⚠️ 为什么不直接调 [inpaintRegion]：那条路是**生成页的界面状态**驱动的（读全局正面提示词、
    // 读画布上涂的遮罩、改 `busy`/`maskSketch`/`maskVersion`）。一页多格、一格一个提示词，
    // 借用它就会把生成页的界面搅乱，而且队列里也没法"等这一格跑完"✗。所以这里**按格子**
    // 把同一套请求参数再组一次，端点、payload、落库链路一个都不换 ✓。

    /** 逐格队列（null = 这一页还没跑过）。状态机的家：`models/GenerationQueue.kt` ✓。 */
    var comicQueue by mutableStateOf<GenerationQueue?>(null)
        private set

    /**
     * 队列状态的**变化计数**。
     *
     * `GenerationQueue.Item.state` 是普通 `var`（那个类不认识 Compose、所以能单测 ✓），
     * 界面直接读它读不到变化 ✗。所以每改一次状态就 +1，界面拿它当 `remember` 的 key ✓
     * （比把状态机改成 Compose 可变状态干净：纯逻辑那层保持纯 ✓）。
     */
    var comicQueueTick by mutableStateOf(0)
        private set

    /** 整页是否正在跑（按钮在「跑这一页 / 停」之间切 ✓）。 */
    var comicRunning by mutableStateOf(false)
        private set

    /** 跑前报价单（非空 = 确认框开着 ✓）。null 且 [comicQuoteLoading] = 还没要过报价。 */
    var comicRunQuote by mutableStateOf<ComicRunQuote?>(null)
        private set

    /** 正在向官方要报价（这期间确认框显示"读取中"，**不给确认** ✓）。 */
    var comicQuoteLoading by mutableStateOf(false)
        private set

    /** 这次确认后要跑的格子（按生成序号 ✓）。点「确认」才用得上 —— 取消什么都不做 ✓。 */
    var comicRunTargets by mutableStateOf<List<String>>(emptyList())
        private set

    private var comicRunJob: Job? = null

    /** 空白画布的"纸"：不透明白（透明底发出去，模型看到的是 alpha，不可控 ✗）。 */
    private val comicBlankPixel = 0xFFFFFFFF.toInt()

    /**
     * 点「跑这一页」（或某一格「重试」）→ **只报价，不花钱** ✓。
     *
     * 价格走**官方预扣报价接口**（[NaiApi.requestOfficialGenerationPrice] ✓ —— 与文生图那条
     * `refreshGenerationQuote` 同一个来源 ✓）：同一尺寸**只问一次**（多格同尺寸很常见 ✓），
     * 问不到就如实标"未知"，**绝不编一个数字** ✗。
     *
     * ⚠️ 报价按 `action=generate` 的同尺寸 / 同模型请求问（接口就是这么设计的）；真正发出去的是
     * `action=infill` 的聚焦重绘，服务端按实际结算 —— 所以界面上写的是"预估"✓。
     *
     * @param onlyPanelId 只跑这一格（失败的格子点「重试」走这里 ✓）；null = 这一页还没跑完的那些格 ✓
     */
    fun requestComicRun(onlyPanelId: String? = null) {
        val page = comicBoardPage ?: return
        if (comicRunning) return
        val panels = page.orderedPanels()
        if (panels.isEmpty()) {
            toast(rt("comic.runNoPanel"), isError = true)
            return
        }
        val entries = ComicRunPlan.of(panels)
        val targets = if (onlyPanelId != null) {
            listOf(onlyPanelId)
        } else {
            // 已经有这条队列（格子没变）→ 只跑**还没完成**的：已经出过图的格子不重复花钱 ✓
            val queue = comicQueue
            if (queue != null && queue.items.map { it.id } == panels.map { it.id }) {
                queue.items.filter { it.state != GenerationQueue.State.DONE }.map { it.id }
            } else {
                panels.map { it.id }
            }
        }
        val wanted = entries.filter { it.panelId in targets }
        if (wanted.isEmpty()) {
            toast(rt("comic.runAllDone"), isError = false)
            return
        }
        comicRunTargets = wanted.map { it.panelId }
        comicRunQuote = null
        comicQuoteLoading = true
        viewModelScope.launch {
            try {
                comicRunQuote = quote(entries = wanted, token = effectiveToken())
            } finally {
                comicQuoteLoading = false
            }
        }
    }

    /** 取消这次跑（确认框点「取消」）：**什么都不做** ✓ —— 一分钱都不花 ✓。 */
    fun cancelComicRunRequest() {
        comicRunQuote = null
        comicRunTargets = emptyList()
    }

    /** 官方报价 → `ComicCost` 清单（**只读价格，不生成** ✓）。 */
    private suspend fun quote(entries: List<ComicRunEntry>, token: String): ComicRunQuote {
        val unpriced = LinkedHashSet<Pair<Int, Int>>()
        val prices = HashMap<Pair<Int, Int>, Int>()
        entries.map { it.width to it.height }.distinct().forEach { size ->
            val (width, height) = size
            // 没 token 时连生成都发不出去 → 不编价格，全部标"未知"（确认框会如实说 ✓）
            val price = if (token.isEmpty()) {
                null
            } else {
                runCatching {
                    api.requestOfficialGenerationPrice(
                        token = token,
                        params = params.copy(
                            // 问的就是**真要发的那套参数**（同尺寸、同重绘模型 ✓）
                            model = settings.inpaintModel,
                            width = width,
                            height = height,
                        ),
                        imageBaseUrl = effectiveImageBase(),
                    )
                }.getOrNull()
            }
            if (price == null) unpriced += size else prices[size] = price
        }
        return ComicRunQuote(
            estimate = ComicCost.estimate(
                panels = ComicRunPlan.costInput(entries),
                priceOf = { width, height -> prices[width to height] ?: 0 },
            ),
            unpricedSizes = unpriced,
        )
    }

    /**
     * 确认框点「确认」→ **这才开始花钱** ✓（串行逐格）。
     *
     * 一次只跑一格这件事在**实现层**钉死：循环里只用 [GenerationQueue.nextToRun]（有格子在跑
     * 就返回 null ✓），跑完一格才取下一格 ✓。
     *
     * 失败策略用**默认的"停下等人工"** ✓（`stopOnFailure = true`：`markFailed` 返回 false 就
     * 收手 ✓）—— 花钱的事宁可停 ✓。
     */
    fun confirmComicRun() {
        if (comicRunning) return
        val page = comicBoardPage ?: return
        val panels = page.orderedPanels()
        if (panels.isEmpty()) return
        val targets = comicRunTargets.ifEmpty { panels.map { it.id } }.toSet()
        val entries = ComicRunPlan.of(panels)
        // 提示词空着的格子发出去只会白花钱：拦在开跑之前 ✓（报第几格 ✓）
        ComicRunPlan.blankPrompt(entries.filter { it.panelId in targets })?.let { blank ->
            toast(rf("comic.runNeedPrompt", mapOf("index" to blank.index + 1)), isError = true)
            cancelComicRunRequest()
            return
        }
        if (effectiveToken().isEmpty()) {
            toast(rt("error.tokenRequired"), isError = true)
            cancelComicRunRequest()
            return
        }
        cancelComicRunRequest()

        // 队列：格子没变就**沿用**（每格的状态留着：失败的可重试、跳过的能接着跑 ✓）；
        // 格子增删改序了就重开一条（按旧 id 跑 = 跑错格 ✗）。
        val existing = comicQueue
        val queue = if (existing != null && existing.items.map { it.id } == panels.map { it.id }) {
            existing
        } else {
            GenerationQueue(idsInOrder = panels.map { it.id }, stopOnFailure = true).also { comicQueue = it }
        }
        // 这次确认过的格子退回"待跑"（`retry` 从任何状态都能退回 PENDING ✓）
        targets.forEach { queue.retry(it) }

        comicRunning = true
        cancelRequested = false
        comicQueueTick++
        comicRunJob = viewModelScope.launch {
            var anlasBefore: Int? = null
            var done = 0
            var failed = 0
            var stoppedByFailure: Int? = null
            try {
                anlasBefore = snapshotAnlasBefore(effectiveToken())
                while (true) {
                    // ★ "一次一张"的闸门：有格子在跑就返回 null（这里串行，所以永远是上一格结算完才取 ✓）
                    val item = queue.nextToRun() ?: break
                    val current = comicBoardPage ?: break
                    val panel = current.panels.firstOrNull { it.id == item.id } ?: break
                    queue.markRunning(item.id)
                    comicQueueTick++
                    status = rf(
                        "comic.runPanel",
                        mapOf("index" to item.order + 1, "total" to queue.items.size),
                    )
                    val outcome = runCatching { generateComicPanel(current, panel) }
                    // 整个作用域被取消（App 正在关 / ViewModel 清了）不算"这一格失败" ——
                    // 当成失败会白白往下再跑好几格 ✗，所以原样抛出去交给 finally 收尾 ✓
                    outcome.exceptionOrNull()?.let { cause ->
                        if (cause is kotlinx.coroutines.CancellationException) throw cause
                    }
                    val path = outcome.getOrNull()
                    if (path != null) {
                        // 出图：**进图库**（走现成链路 ✓）+ 路径记到这一格（落盘 ✓）；旧图不删 ✓
                        setComicPanelResultPath(panel.id, path)
                        queue.markDone(item.id)
                        done++
                        comicQueueTick++
                        continue
                    }
                    val reason = cleanError(outcome.exceptionOrNull() ?: IllegalStateException("unknown"))
                    failed++
                    comicQueueTick++
                    // false = 配了 stopOnFailure（默认 ✓）→ 收手等人，不默默往下花钱 ✗
                    if (!queue.markFailed(item.id, reason)) {
                        stoppedByFailure = item.order + 1
                        break
                    }
                }
            } catch (e: Exception) {
                status = rf("status.inpaintFailed", mapOf("error" to cleanError(e)))
            } finally {
                comicRunning = false
                comicQueueTick++
                runCatching {
                    account = mergeAccountKeepingLast(api.fetchAccount(effectiveToken(), effectiveImageBase()))
                }
                // ⚠️ 漫画这条是**整页**跑的 ✓：图数 / tag 字数**已经在落库那一步记过了** ✗
                //（`persistGeneratedImages` 每格一次 ✓）⇒ 这里只补**这一轮实扣的点数** ✓
                //（`images = 0` ✓，不然同一批图会被数两遍 ✗）。
                lastAnlasSpent = if (anlasBefore != null && account.anlasBalance != null) {
                    maxOf(0, anlasBefore!! - account.anlasBalance!!)
                } else {
                    null
                }
                // 每日统计：**这一轮实扣的点数** ✓
                recordUsage(images = 0, anlas = lastAnlasSpent ?: 0, tagChars = 0)
                status = rf(
                    "comic.runDone",
                    mapOf("done" to done, "failed" to failed, "spent" to spentText(lastAnlasSpent)),
                )
                stoppedByFailure?.let { toast(rf("comic.runStoppedOnFailure", mapOf("index" to it)), isError = true) }
                if (stoppedByFailure == null && failed > 0) {
                    toast(rf("comic.runDoneFailed", mapOf("failed" to failed)), isError = true)
                }
            }
        }
    }

    /**
     * 按「停」：把**还没跑的都标成跳过** ✓（[GenerationQueue.skipRemaining] ✓）。
     *
     * 正在跑的那一格**让它跑完**（请求已经发出去了，钱已经在花；中途掐断只会多一种半成品状态 ✗），
     * 跑完循环取不到下一格就自然收尾 ✓。
     */
    fun stopComicPageRun() {
        val queue = comicQueue ?: return
        queue.skipRemaining()
        comicQueueTick++
        status = rt("comic.runStopRequested")
    }

    /**
     * 把这一格的出图路径记上并落盘 ✓（**不删旧图**：旧的那张还在图库里 ✓）。
     *
     * ★★★ **「每生成一张 = 自动新增一层」就落在这一行（下面那句 `addGenerated`）** ★★★
     * ——用户点名的口径（`docs/43` §9.3：「图层功能，**生的图都是一个新图层**，方便修改」✓）。
     *
     * 为什么钉在这个函数里：它是"某一格出图了"**唯一的收口** ✓ ——
     * 队列逐格生成跑完一格只走这一条路（`confirmComicRun` 里 `setComicPanelResultPath(panel.id, path)` ✓），
     * 单格重试也走它 ✓。放这儿就不存在"某条生成路忘了加图层"✗ 的可能。
     *
     * 同一格重复生成时**旧层不删** ✓（`ComicLayerStack.addGenerated` 的既定口径 ✓：
     * 用户可能要对比两张 ✓）；不想要的那层在图层列表里隐藏 / 删除都可以 ✓。
     */
    fun setComicPanelResultPath(panelId: String, path: String) {
        updateComicBoardPage { page ->
            val withPanel = page.withPanels(
                page.panels.map { p -> if (p.id == panelId) p.copy(resultPath = path, imagePath = path) else p },
            )
            // ★ 每生成一张 = 新图层（新层盖在最上面 ✓ —— `add` 就是往叠顶加 ✓）
            // ⛔ 第 ㊿l 批「格子与图层合一」（用户 2026-09-22 ✓）：**不再新建"生成层"** ✗ ——
            //    图已经写进**格子自己**了 ✓（"新建图层变为新建格子"的反面就是"生成 = 往这一格写"✓）。
            //    ⚠️ 老数据里那些 GENERATED 层照旧能画 ✓（显示那段没删 ✓），但**新生成不再产生层** ✓。
            withPanel
        }
        persistComicBoard()
    }

    /**
     * **新建底板**（用户 2026-09-22：「**新建底板功能按钮放在生成图片按钮上面**」✓，
     * 高级漫画并进主页的第一步 ✓）。
     *
     * 口径来自同一句话：「**底板就是现在主页的画布**」✓ ⇒ 这个按钮做的事就是
     * **把现在这张工作图当成一张新底板** ✓（`setComicBoardBase` ✓ 现成的函数 ✓），
     * 顺手切到「漫画」那一档 ✓（新建底板就是为了用它 ✓）。
     *
     * ⚠️ 手上**没有图**时**如实说一句** ✗ 不静默新建一张空白 ✓ ——
     * 静默新建会让人以为"点了没反应 / 底板凭空出现" ✓（本仓库最烦的那类坏 ✓）。
     */
    fun newComicBaseFromCurrentImage() {
        val path = workImagePath
        if (path == null) {
            toast(rt("comic.needImageForBase"), isError = true)
            return
        }
        val size = MaskCodec.imageSize(images, path)
        if (size == null || size.first <= 0 || size.second <= 0) {
            toast(rt("comic.importFailed"), isError = true)
            return
        }
        setComicBoardBase(width = size.first, height = size.second, basePath = path)
        chooseCanvasMode(2)
        toast(rt("comic.baseImported"))
    }

    /**
     * 改一格的**正面提示词**（负面提示词全页共用 ✓，不在格子上）。
     *
     * ⚠️ **不落盘**：文本框每敲一个字都会调它（和拖动一个道理 —— 见这一节开头"什么时候写盘" ✗）。
     * 界面在**文本框失去焦点**时调一次 [commitComicBoard] ✓。
     */
    fun setComicPanelPrompt(panelId: String, prompt: String) {
        updateComicBoardPage { page ->
            page.withPanels(
                page.panels.map { if (it.id == panelId) it.copy(prompt = prompt) else it },
            )
        }
    }

    /**
     * **跑一格**：走的是现成的那条付费链路 ✓。
     *
     *  · 几何：[FocusedInpaint.plan]（框 = **格子本身**、上下文 = 0 ✓ —— 用户口径 §Q3「格子就是聚焦区」）；
     *    于是请求尺寸就是格子上那个只读值 `InpaintSize.forRect(格宽, 格高)` ✓；
     *  · 请求：[NaiApi.inpaint]（`action=infill`，和「重做」同一个端点、同一套 payload 构造 ✓）；
     *  · 落库：[AppState.persistGeneratedImages]（历史 + 当前图 + 临时图库，一条不落 ✓）。
     *
     * @return 生成图的路径（已经进图库 ✓）；失败抛异常，由调用方记到那一格上 ✓
     */
    private suspend fun generateComicPanel(page: ComicBoardPage, panel: ComicPanel): String {
        val token = effectiveToken()
        if (token.isEmpty()) throw IllegalStateException(rt("error.tokenRequired"))
        if (panel.prompt.isBlank()) throw IllegalStateException(rt("comic.runNeedPromptSimple"))
        // 框 = 格子（底板像素 → 像素整数，再夹回页内 ✓）
        val frame = EditRect(
            x = panel.x.roundToInt(),
            y = panel.y.roundToInt(),
            w = panel.w.roundToInt(),
            h = panel.h.roundToInt(),
        ).clampTo(page.baseWidth, page.baseHeight)
        if (frame.isEmpty) throw IllegalStateException(rt("comic.runPanelTooSmall"))
        val plan = FocusedInpaint.plan(
            frame = frame,
            // 上下文 = 0：这一格的请求就是"框那一块"本身（口径 §Q3 ✓）
            contextPixels = 0,
            sourceWidth = page.baseWidth,
            sourceHeight = page.baseHeight,
            // 尺寸全自动算（`InpaintSize.forRect` ✓：等比 + 面积 ≤1024×1024 + 保 64 ✓）
            autoSizeByRect = true,
        ) ?: throw IllegalStateException(rt("comic.runPanelTooSmall"))

        val inpaintModel = settings.inpaintModel
        // 空白画布（还没导入图）没有上下文可参考 → 这一格就是"从零画一张"，强度按全重绘 ✓；
        // 有底板（导入的页 / 已有画面）就按用户在重绘那边设定的强度走 ✓
        val strength = if (page.basePath == null) 1.0 else settings.inpaintStrength
        val noise = settings.inpaintNoise
        val taskParams = params.copy(
            model = inpaintModel,
            width = plan.requestW,
            height = plan.requestH,
            // 这一格的**正面**提示词（负面 / 风格预设仍走全局 ✓）
            positivePrompt = panel.prompt,
        )
        val seed = resolveSeed(params)
        val initialExtras = extras.copy(stylePresetPrompts = activeStylePresetPrompts(), stylePresetNegatives = activeStylePresetNegatives())
        val genStart = System.currentTimeMillis()

        // 位图准备放后台：裁剪 + 缩放 + 遮罩吸附 + PNG 编码（和「重做」那条路同一套操作 ✓）
        val prepared = withContext(Dispatchers.Default) {
            val cropPixels = if (page.basePath == null) {
                // 空白画布：给一块**白底**（用户口径 §Q3 的"上下文 = 0 → 生成新画面" ✓）
                IntArray(plan.cropW * plan.cropH) { comicBlankPixel }
            } else {
                val full = MaskCodec.decodeFull(images, page.basePath)
                    ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                val fullPixels = images.pixelsOf(full)
                val fullWidth = full.width
                full.recycle()
                IntArray(plan.cropW * plan.cropH).also { cropped ->
                    for (row in 0 until plan.cropH) {
                        System.arraycopy(
                            fullPixels,
                            (plan.cropY + row) * fullWidth + plan.cropX,
                            cropped,
                            row * plan.cropW,
                            plan.cropW,
                        )
                    }
                }
            }
            val cropImage = images.fromArgb(cropPixels, plan.cropW, plan.cropH)
            val basePng = MaskCodec.basePng(images, cropImage, plan.requestW, plan.requestH)
            cropImage.recycle()
            // 这一格**整框重绘**：本批还没有"格子里再涂一小块"的界面（随遮罩那批接 ✓），
            // 所以全 0 的遮罩交给现成的 `maskIntoRequest(fillWholeFrame = true)`
            // —— 官方"框里留空 = 整框重绘"那条口径 ✓，一行新算法都不加 ✓
            val emptyMask = PixelMask(plan.cropW, plan.cropH, ByteArray(plan.cropW * plan.cropH))
            val focusMask = FocusedInpaint.maskIntoRequest(emptyMask, plan, fillWholeFrame = true)
                ?: throw IllegalStateException(rt("mask.empty"))
            // 吸附 8px 潜空间网格（和「重做」同一口径，免得重绘边界和格子错开 ✓）
            val requestMask = MaskCodec.inpaintMask(focusMask, plan.requestW, plan.requestH)
                ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
            PreparedComicPanel(
                basePng = basePng,
                maskPng = MaskCodec.maskPng(images, requestMask, plan.requestW, plan.requestH),
            )
        }

        // 不带走流式预览：队列在后台跑，预览管道是**生成页**的东西，串起来只会互相打架 ✗
        // （端点和 payload 与流式版完全同一套，只是拿一次性响应 ✓）
        val (returned, usedModel) = api.inpaint(
            token = token,
            params = taskParams,
            seed = seed,
            baseImagePng = prepared.basePng,
            maskPng = prepared.maskPng,
            strength = strength,
            noise = noise,
            imageBaseUrl = effectiveImageBase(),
            extras = initialExtras,
            modelMode = settings.modelMode,
        )
        if (returned.isEmpty()) throw IllegalStateException(rt("error.inpaintNoImages"))
        val saved = persistGeneratedImages(
            images = returned,
            params = taskParams,
            seed = seed,
            feature = "comic",
            model = usedModel,
            // 这一格落库的就是**这一格自己**那张（请求尺寸 = 目标像素 ✓），不是整页合成 ✓
            width = plan.requestW,
            height = plan.requestH,
            durationMs = System.currentTimeMillis() - genStart,
        )
        return saved.firstOrNull()?.filePath ?: throw IllegalStateException(rt("error.inpaintNoImages"))
    }

    /** 跑一格要用到的两块位图（底图裁剪块、发出去的遮罩 PNG ✓）。 */
    private class PreparedComicPanel(
        val basePng: ByteArray,
        val maskPng: ByteArray,
    )

    // ---------------------------------------------------------------- 其它

    fun errorText(message: String) { status = message }

    /**
     * **给界面用的一次性提示**（弹窗 + 状态行都写 ✓，与内部 `toast` 同一口径 ✓）。
     *
     * ⚠️ 为什么要有这个公开壳子：内部 `toast` 是 `private` ✓，而界面（例如无限画布那个
     * "到上限了" ✓）需要**如实说一句** ✗ 不能静默 ✓ —— 这正是"点了没反应"最难查的那类坏 ✓。
     */
    fun showToast(message: String) = toast(message)

    private fun cleanError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}

/** 临时图库里耗时角标的显示格式：`分:秒`（如 `0:12`、`1:05`）。 */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

