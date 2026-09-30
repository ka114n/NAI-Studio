package com.kallan.naistudio.state

import com.kallan.naistudio.models.INFINITE_FRAME_MAX_AREA
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kallan.naistudio.BuildConfig
import com.kallan.naistudio.platform.logWarn
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.AnlasCost
import com.kallan.naistudio.models.TagDictionary
import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.BackupCodec
// ---- 画布编辑器（2026-09-16 从电脑线 `naistudio-shared` 的 AppState 搬过来）----
// 这几个 import 就是电脑线 AppState 顶部与画布编辑器相关的那一组，名字一一对应。
import com.kallan.naistudio.models.BrushShape
import com.kallan.naistudio.models.CANVAS_MAX_LONG_SIDE
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.DEFAULT_BLUR_INTENSITY
import com.kallan.naistudio.models.DEFAULT_CANVAS_COLOR
import com.kallan.naistudio.models.DEFAULT_FILL_TOLERANCE
import com.kallan.naistudio.models.FloatingSelection
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.SelectionOps
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.services.AnimaCharacter
import com.kallan.naistudio.services.AnimaDexApi
import com.kallan.naistudio.services.AnimaDexLabels
import com.kallan.naistudio.services.AnimaDexProtocol
import com.kallan.naistudio.services.AnimaFacet
import com.kallan.naistudio.services.AnimaSeries
import com.kallan.naistudio.models.CharCaptionItem
import com.kallan.naistudio.models.COMIC_DEFAULT_STYLE
import com.kallan.naistudio.models.ComicLayout
import com.kallan.naistudio.models.ComicPage
import com.kallan.naistudio.models.ComicStoryboard
import com.kallan.naistudio.models.GenerateExtras
import com.kallan.naistudio.models.FocusedInpaint
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.InfiniteCanvas
import com.kallan.naistudio.models.InfiniteCanvasHistory
import com.kallan.naistudio.models.InfiniteCanvasPixels
import com.kallan.naistudio.models.capInfiniteFrameArea
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.GenerateParamsCodec
import com.kallan.naistudio.models.HistoryGroup
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.ConversationTurn
import com.kallan.naistudio.models.StatusLine
import com.kallan.naistudio.models.StPreset
import com.kallan.naistudio.models.withDisabled
import com.kallan.naistudio.models.withOverrides
import com.kallan.naistudio.models.StPresetRef
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
import com.kallan.naistudio.models.PixelMask
import com.kallan.naistudio.models.PromptField
import com.kallan.naistudio.models.PromptTranslate
import com.kallan.naistudio.models.promptOf
import com.kallan.naistudio.models.withPrompt
import com.kallan.naistudio.models.StylePreset
import com.kallan.naistudio.models.StylePresetLibrary
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
import com.kallan.naistudio.models.TagZhProtocol
import com.kallan.naistudio.services.AccountSummary
import com.kallan.naistudio.services.AuthApi
import com.kallan.naistudio.services.AuthException
import com.kallan.naistudio.services.AuthMe
import com.kallan.naistudio.services.BaiduTranslate
import com.kallan.naistudio.services.DirectorTools
import com.kallan.naistudio.services.ImageMetadata
import com.kallan.naistudio.services.ImageMetadataReader
import com.kallan.naistudio.services.ImageDecoder
import com.kallan.naistudio.services.FocusPaste
import com.kallan.naistudio.services.GatewayQuota
import com.kallan.naistudio.services.InpaintComposite
import com.kallan.naistudio.services.LlmApi
import com.kallan.naistudio.services.LlmException
import com.kallan.naistudio.services.MaskCodec
import com.kallan.naistudio.services.NaiApi
import com.kallan.naistudio.services.NaiHttpException
import com.kallan.naistudio.services.NaiPreview
import com.kallan.naistudio.services.OfficialUpscale
import com.kallan.naistudio.services.PngMetadata
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.services.Storage
import com.kallan.naistudio.models.UsageStats
import com.kallan.naistudio.services.androidPlatform
import com.kallan.naistudio.services.TagCodexApi
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 预扣报价。`amount` 为 null 表示还没拿到报价。 */
data class AnlasQuote(
    val amount: Int? = null,
    val balance: Int? = null,
    val insufficient: Boolean = false,
    /** 与参考实现同名：officialQuote / formulaQuote / pendingQuote。 */
    val source: String = "pendingQuote",
)

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
class AppState(app: Application) : AndroidViewModel(app) {

    private val storage = Storage(androidPlatform(app))
    /** 平台能力（记忆落盘要用 filesDir；手机版原本没留这个引用）。 */
    private val platform = androidPlatform(app)
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

    // ------------------------------------------------------------------
    // **手机外壳实测尺寸**（用户 2026-09-25：「底部抽屉现在铺满画面，到余额胶囊下面」）
    //
    // ⚠️ 为什么摆在 `AppState`（明明是纯界面尺寸）：「余额胶囊」长在 `StudioShell`
    //    的浮层里（`GenerateFloatingBars`），而「底部抽屉」长在 `GenerateScreen` 里 ——
    //    中间隔着 `HorizontalPager`，参数要穿过好几层。把**实测到的那一个数**放这儿最省事
    //    （和 `canvasMode` 同一个理由：纯界面状态、**不落盘**）。
    // ------------------------------------------------------------------

    /**
     * 生图页顶上那排浮动条（圆形侧栏按钮 + **余额胶囊**）的**高度 = 它的下沿**（px）。
     *
     * 底部抽屉靠它算"最多能长到多高"：顶到这条线就停 ⇒ **永远盖不住余额胶囊**。
     */
    var phoneFloatingBottomPx by mutableIntStateOf(0)

    /**
     * 外壳**底部导航栏**（「生图 / 图库 / 工具」那条）的高度（px）。
     *
     * ⚠️ 用户 2026-09-25：「键盘**上空隙**！**和底栏差不多**」的**真正原因** ——
     *    壳层的内容 Box 是 `.padding(inner)`，`inner.bottom` 正好是这条底栏的高度
     *    ⇒ **生成页的坐标系底边被抬高了「一条底栏」**；
     *    可 `imeBottom` 是相对**窗口底**量的 ⇒ 抬升量必须**减掉这一条**，
     *    否则就多抬一条、键盘上方空出一条 ✗。
     *
     * ⚠️ 为什么不能让生成页自己量：生成页**自己也有一个 Scaffold**，但它没有 `bottomBar`、
     *    而且 `contentWindowInsets` 清零 ⇒ 它拿到的 `inner.calculateBottomPadding()` **恒为 0** ✗，
     *    底栏那一条是**壳层**吃掉的 ⇒ 只能由壳层量好放这儿（和 [phoneFloatingBottomPx] 同一套）。
     * ⚠️ 画布编辑器那一档壳层不画底栏 ⇒ 这里是 0 ⇒ 不扣，正好也对 ✓。
     */
    var phoneBottomBarHeightPx by mutableIntStateOf(0)

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
        get() = BuildConfig.HOSTED_EDITION &&
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
    // 「API 地址」那一栏、`nai-...` 虚拟 Key 当 token 填进来就行，**不需要另做协议**。
    // 这里只补两件它做不到、又必须让用户知道的事（细节见 `NaiApi` 类尾那一大段）：
    //  ① 它**没开 img2img / 局部重绘** ⇒ 进这些模式时先提示，别让用户白等一次请求；
    //  ② 它的余额 / V5 日额度在另一条接口上 ⇒ 拉回来给「我的」那张卡显示。
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

    // -----------------------------------------------------------------------
    // **每日使用统计**（用户 2026-09-26：「删除统计，参照电脑的侧边栏统计」）
    //
    // 就是把电脑那套搬过来：纯逻辑 / 序列化 / 排格子都在 `models/UsageStats.kt`
    // （那份是**共用**的纯逻辑，两线同一份文件），界面在 `screens/UsageStatsScreen.kt`。
    // 存在 `settings.usageStats` 那一小段文本里，跟着设置一起进备份。
    // -----------------------------------------------------------------------

    /** 从盘上读回来的那份统计（每次出图就地累加，见 [recordUsage]）。 */
    var usage by mutableStateOf(UsageStats())
        private set

    /**
     * 记一笔统计。
     *
     * ⚠️ 图数与点数**分两笔记**，不合成一笔：图数在落库那里才数得准，
     * 点数要到流程收尾（刷新账号之后）才算得出来 —— 两者时间点不同。
     */
    private fun recordUsage(images: Int, anlas: Int, tagChars: Int) {
        val today = java.time.LocalDate.now().toString()
        val next = usage.plus(today, images = images, anlas = anlas, tagChars = tagChars)
        usage = next
        setSettings { it.copy(usageStats = next.encode()) }
        // 统计变了 ⇒ 桌面小组件那两格数字要跟着变（用户 2026-09-26 要的小组件）
        pushWidgetUpdate()
    }

    // -----------------------------------------------------------------------
    // **桌面小组件**（用户 2026-09-26：1×2 只显示余额、2×3 显示余额 + 统计）
    //
    // 小组件跑在 `com.android.systemui` 进程里，**读不到这里的任何内存态** ⇒
    // 它读的是落盘的 `nai_store` / `app_settings`（即 `settings` 那一份 JSON）。
    // 所以这一层只做两件事：
    //   ① 把**余额**写进快照（[snapshotBalance]）—— 它本来是纯内存的；
    //   ② 数据一变就**主动推一次**小组件刷新（[pushWidgetUpdate]）。
    //
    // ⚠️ 刻意**不依赖** `updatePeriodMillis`（那玩意最短 30 分钟、而且系统想忽略就忽略）：
    //    刷新完全由 App 侧推。代价是"不打开 App，小组件的数字就不会变"——
    //    这是平台限制，快照上带的时间戳就是为了让人看出这一点。
    // -----------------------------------------------------------------------

    /**
     * 把当前余额写进快照（`null` = 还没拿到过，保持"没有快照"）。
     *
     * 用户 2026-09-26：「**1*2 的余额做成这个样式，带进度条**」—— 参考图上那颗绿色小徽章
     * 和右边那条进度条，写的是**档名**和 **V5 剩余百分比**。这两个数本来也只在内存里
     * （`account.tierName` / `account.opusUsage`），小组件读不到 ⇒ 一并落盘。
     *
     * ⚠️ 百分比那一项的口径**和 App 顶栏胶囊逐字一致**（见 `StudioShell.AccountChip`）：
     *    只有 **Opus 档 + 正在用 V5 模型**才算数，其余一律存 `null`（小组件就不画那条）。
     *    两处必须是同一个条件，否则会出现"App 里写着 V5 62%、桌面小组件那条却是 30%"。
     */
    private fun snapshotBalance() {
        val balance = account.anlasBalance ?: return
        val percent = account.opusUsage
            ?.takeIf { account.tierLevel == 3 && params.model.startsWith("nai-diffusion-5-") }
            ?.let { if (it.isNegative) 0 else it.percent.coerceIn(0.0, 100.0).toInt() }
        setSettings {
            it.copy(
                balanceSnapshot = balance,
                balanceSnapshotAt = System.currentTimeMillis(),
                tierSnapshot = account.tierName.orEmpty(),
                v5PercentSnapshot = percent,
            )
        }
    }

    /**
     * 推一次小组件刷新。
     *
     * ⚠️ 用 `runCatching` 包住：小组件那套是 `app` 模块的 Android API，而 `AppState` 在
     * 共用树里（`naistudio-shared`）—— 通过 `platform` 那层拿 `Context`，拿不到就静默跳过。
     * **绝不因为"推小组件失败"把生成流程带崩**。
     */
    private fun pushWidgetUpdate() {
        // 反射调用：`AppState` 编在**共用树**里，而小组件那套是 `app` 模块的 Android API。
        // 共用树的源码是**被 app 模块 srcDir 收进去一起编的**，所以直接 import 理论上也行，
        // 但那会让"共用树不依赖 Android 侧"这条边界在**类型层面**被打破。
        // 这里用反射把依赖压到**运行时**：拿不到（比如共用树被别的宿主编走）就静默跳过，
        // **绝不因为"推小组件失败"把生成流程带崩**。
        runCatching {
            val bridge = Class.forName("com.kallan.naistudio.widget.WidgetBridge")
            val instance = bridge.getDeclaredField("INSTANCE").get(null)
            bridge.getMethod("updateAll", android.content.Context::class.java)
                .invoke(instance, getApplication())
        }
    }

    /**
     * 拉一次网关配额。**只在用网关时发**，失败静默（这块是附加信息，不是主流程）。
     * 调用时机：设置里改完地址/token 之后、以及「我的」那张卡刷新余额时。
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
        }
    }

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
     *
     * 覆盖到的地方 = 所有 `generate*` 入口 + [setToken]（见各自的调用点）。
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
     * **本次会话累计消耗的 Anlas**（内存态，进程重启归零）。
     *
     * 侧边栏底部那个统计块用它（用户 2026-09-26：「做统计」）——
     * 逐笔来自三条流程算出来的实扣（`lastAnlasSpent`），加在这里。
     * ⚠️ 刻意**不落盘**：它统计的就是"这一次开着 App 花了多少"，
     *    落盘反而要处理跨天/重置，没必要（和 [sessionImages] 一个口径）。
     */
    var sessionAnlasSpent by mutableIntStateOf(0)
        private set

    /**
     * **画布编辑的"另存"记录**：派生图路径 → 原图路径（用户 2026-09-26）。
     *
     * 用户的要求：「画布编辑器没有保存功能，点击完成后**自动多保存一份**，原图不动，
     * 改变的图多一份，改变的图片**在没有清后台的情况下可以重置修改**，没有修改的图片不多保存」。
     * 所以：完成时若像素真的变了 ⇒ 另存一条新记录 + 在这里记下它的原图；
     * 图库里那张图因此多一个「重置修改」（把派生图和这条记录一起丢掉、回到原图）。
     * ⚠️ 只在内存：用户明说了"没有清后台"才要能重置，不落盘正是他要的语义。
     */
    private val canvasEditOrigins = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** 这张图是不是"画布编辑另存出来的"（图库据此决定要不要显示「重置修改」）。 */
    fun canResetCanvasEdit(path: String?): Boolean =
        path != null && canvasEditOrigins.containsKey(path)

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
     * 状态行文案（已经解析好的文本）。赋值时**自动进 [statusHistory]** ——
     * 底部抽屉的「日志模式」要显示"刚刚都发生了什么"，全项目 90 多处 `status = …`
     * 与其挨个加记录，不如在属性上收口。
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

    /** 状态行历史（最新在最后）。抽屉「日志模式」里"界面日志"那一半。 */
    val statusHistory = androidx.compose.runtime.mutableStateListOf<StatusLine>()

    /** AI 交互记录（最新在最后）—— 抽屉「对话模式」。纯展示，不参与请求。 */
    val conversation = androidx.compose.runtime.mutableStateListOf<ConversationTurn>()

    private var conversationSeq = 0L

    /** 底部抽屉（对话 / 日志）是否展开。宽窄两套布局共享一个状态。 */
    var statusDrawerOpen by mutableStateOf(false)
        private set

    fun openStatusDrawer() { statusDrawerOpen = true }

    fun closeStatusDrawer() { statusDrawerOpen = false }

    fun clearConversation() { conversation.clear() }

    fun clearStatusHistory() { statusHistory.clear() }

    /**
     * **LLM 多轮记忆**：按用途分桶（分镜 / 分页规划 / 优化 / 反推 / 翻译），每桶只留最近 N 轮。
     * 用户 2026-09-16："记忆和保留轮数也使用，这些功能在分镜和狂暴模式很好用"。
     */
    private val llmMemory = mutableMapOf<String, MutableList<LlmTurn>>()

    /** `is_locked` 的结果缓存（源码靠 ComfyUI 节点缓存实现，我们等价地自存一份）。 */
    private val llmLockedResults = mutableMapOf<String, String>()

    private fun llmMemoryFile() = java.io.File(platform.paths.filesDir, "llm_memory.json")

    /**
     * **记忆落盘**（用户 2026-09-18：「现在重启还会重置记忆吗，重置的话改成不重置，
     * 因为失败和拒绝不计入记忆了」）。
     *
     * 以前是**只活在当前进程**（用户 2026-09-17 要求"退出就清"）✗；现在改成写
     * `files/llm_memory.json`、启动时读回来 —— 关掉 App 再打开，分镜/狂暴那几轮记忆还在。
     * 之所以现在敢落盘：**拒答/失败已经不计入上下文**（`LlmMemoryRules.contextWindow`），
     * 不会像以前那样把一条"我没法写"带进下一轮。
     */
    private fun persistLlmMemory() {
        val snapshot = llmMemory.mapValues { (_, turns) -> turns.toList() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val json = LlmContext.toMemoryJson(snapshot)
                    if (snapshot.isEmpty() || json == "{}") {
                        llmMemoryFile().delete()
                    } else {
                        llmMemoryFile().writeText(json)
                    }
                }
            }
        }
    }

    /** 启动时把记忆读回来（读不动就当空的，绝不影响启动）。 */
    private suspend fun loadLlmMemory() {
        val text = withContext(Dispatchers.IO) {
            runCatching { llmMemoryFile().takeIf { it.isFile }?.readText() }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: return
        val loaded = LlmContext.fromMemoryJson(text)
        if (loaded.isEmpty()) return
        llmMemory.clear()
        loaded.forEach { (kind, turns) -> llmMemory[kind] = turns.toMutableList() }
    }

    fun llmMemoryRounds(kindKey: String): Int = llmMemory[kindKey]?.size ?: 0

    fun llmMemoryTotalRounds(): Int = llmMemory.values.sumOf { it.size }

    /** 清空全部记忆（抽屉「清空」）。 */
    fun clearLlmMemory() {
        llmMemory.clear()
        llmLockedResults.clear()
        persistLlmMemory() // 落盘的那份也一起清掉
    }

    /** 清掉某几类用途的记忆（新一轮分镜 / 狂暴模式开始时用）。 */
    private fun clearLlmMemory(vararg kinds: String) {
        kinds.forEach { llmMemory.remove(it) }
        llmLockedResults.clear()
        persistLlmMemory()
    }

    /** 记一条 AI 交互（纯展示）。LLM 调用与出图调用都走它。 */
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
    var generationPreview by mutableStateOf<Bitmap?>(null)
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

    /** 一次性弹窗提示（导入/导出这类"一次性结果"用；外壳弹完就清掉）。 */
    var toastEvent by mutableStateOf<ToastEvent?>(null)
        private set

    fun consumeToast() {
        toastEvent = null
    }

    /**
     * 界面层想弹一句提示时调用（外壳监听 [toastEvent] 去弹 Toast）。
     *
     * 原来是 `private fun toast(...)`，界面层够不着 —— 2026-09-16 加这个公开入口，
     * 给"运行条上那颗占位铅笔"用：点了提示一句"功能还没做"。
     */
    fun showToast(message: String) = toast(message)

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
    private suspend fun yieldFrame() {
        runCatching {
            withContext(AndroidUiDispatcher.CurrentThread) { withFrameNanos { } }
        }
    }

    fun load() {
        if (bootedOnce) return
        // ⚠️ 以前这里删老版本的 `llm_memory.json`（"记忆不落盘"）—— 用户 2026-09-18 要求
        // 记忆**不要**在重启时重置，所以改成启动时把它读回来（见 loadLlmMemory）。
        bootedOnce = true
        warmPromptTranslationIndex()

        loadJob = viewModelScope.launch {
            try {
                // ---- 错峰加载（用户 2026-09-16：「刚开应用拉底栏顿」）----
                // 这一段原本是"同步读盘 → 立刻写大 state"连着来，三个大 state 挤在同一帧里落地，
                // 等于一帧之内把生成页整棵子树重算好几遍；用户第一次下拉只要撞上这几帧就顿。
                // 两条改法：
                //  ① **同步读盘一律挪到 IO**（storage 那几个 get* 都是非挂起函数，直接在
                //     `viewModelScope`（Main）里调就是主线程读盘 + 解析）；
                //  ② **每落地一段就让出一帧**（`yieldFrame()`），让 Compose 把这一帧画完再唤醒下一批。
                settings = withContext(Dispatchers.IO) { migrateComicRules(storage.getSettings()) }
                // 无限画布那一档：把上次的状态接着摆回来 ✓（模式 + 框 + 快照记账 ✓ ——
                // **像素不在这里读** ✗，几十 MB 的解码等真进这一档时 `ensureInfiniteCanvas` 再做 ✓）
                restoreInfiniteCanvasState()
                // 安装包自带的那份上下文：默认加载（读盘/解析都在 IO 里，见函数注释）
                loadBundledLlmContext()
                // **提示词词典**（用户 2026-09-24：「补充词典库」✓）——
                // 打包在共用树的 `src/main/resources/dictionary.tsv` ✓（39,577 条 ✓，
                // 见 `models/TagDictionary.kt` ✓，两个 `build.gradle.kts` 里各配了一条 `resources.srcDir` ✓）。
                // ⚠️ 后台读 + 解析 ✓（十几 MB 文本扫一遍 ✓，主线程做会卡首帧 ✗）；
                // 读不到就保持**空表** ✓（补全整个不出现 ✓，**不是崩溃** ✗）。
                runCatching {
                    val raw = withContext(Dispatchers.IO) {
                        javaClass.getResourceAsStream("/dictionary.tsv")
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                    if (raw != null) tagDictionary = TagDictionary.parse(raw)
                }
                // 记忆读回来（用户 2026-09-18：重启不要重置记忆）
                loadLlmMemory()
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

                // Restore the storyboard switch only after the comic data and canvas mode
                // have both loaded; this also migrates the old independent comic tab.
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
                current = history.firstOrNull()

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

                // 每日使用统计（电脑那套的口径，见 `recordUsage`）：从设置里那一小段文本解出来
                usage = UsageStats.decode(settings.usageStats)

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
                    // ⚠️ 读**当前这一档**那一把：网关模式下看 `gateway_token`，
                    //    否则看官方的 `nai_token`（两把在各存各的，用户 2026-09-26 要求）。
                    val token = withContext(Dispatchers.IO) {
                        if (settings.useThirdPartyApi) storage.getGatewayToken()
                        else storage.getToken()
                    }
                    hasToken = token.isNotEmpty()
                    if (hasToken) {
                        // 先放一个"有 token"的占位，真实账号请求走启动路径之外——
                        // 参考实现的注释：等它会让启动卡住（没有代理时甚至无限等）
                        account = AccountSummary(hasToken = true)
                    }
                    if (settings.useThirdPartyApi) refreshGatewayQuota()
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
            // 启动拉到余额 ⇒ 写快照 + 推小组件（见"桌面小组件"那一段的说明）
            snapshotBalance()
            pushWidgetUpdate()
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
        stackOf(promptRedo, key).addLast(if (PromptField.isPlot(key)) comicPlot else params.promptOf(key))
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
        stackOf(promptUndo, key).addLast(if (PromptField.isPlot(key)) comicPlot else params.promptOf(key))
        val next = redo.removeLast()
        lastPromptEditAt[key] = 0L
        syncPromptUndoState(key)
        applyPromptText(key, next)
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
        params = params.withPrompt(field, text)
        val snapshot = params
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(snapshot) } }
    }

    /** 每改一次 [field] 这个框就记一笔（带合并窗口）。 */
    private fun recordPromptEdit(field: String, previous: String) {
        val key = PromptField.stackKey(field)
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
     * 手改过的原样留着。
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
        (from..to).map { CharCaptionItem(name = "格 $it") }

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
        val name = if (extras.comicMode) "格 ${list.size + 1}" else "角色 ${list.size + 1}"
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
    fun startThemeWipe(snapshot: ImageBitmap, originX: Float, originY: Float, dark: Boolean) {
        // 正在播就忽略，免得连点两下叠出两个圆
        if (themeWipe != null) return
        setTheme(if (dark) "dark" else "light")
        themeWipe = ThemeWipe(snapshot = snapshot, originX = originX, originY = originY)
    }

    /** 直接换配色方案（存在 `palette` 字段里），不播动画。 */
    fun setScheme(value: String) {
        if (settings.palette == value) return
        settings = settings.copy(palette = value)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setSettings(settings) } }
    }

    /** 换配色方案 + 圆形扩散，顺序同 [startThemeWipe]（先换、同帧铺旧截图）。 */
    fun startSchemeWipe(snapshot: ImageBitmap, originX: Float, originY: Float, scheme: String) {
        if (themeWipe != null) return
        setScheme(scheme)
        themeWipe = ThemeWipe(snapshot = snapshot, originX = originX, originY = originY)
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
    /**
     * 剧情防抖还没落盘时立即写掉。
     *
     * **公开**是有意的（用户 2026-09-17 报"后台划掉应用后剧情丢了"）：剧情输入有 600ms 防抖，
     * 而提示词是即时写的 —— 所以 App 进后台 / 剧情框失焦时必须把这段补上。
     */
    fun flushComicPlot() {
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
                name = panel.name.ifBlank { "格" },
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
                                name = panel.name.ifBlank { "格" },
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
                        replaceComicPages(pages, index) // 让页表/界面跟上（也落盘，中途断电不丢）
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
            "请只输出**这一页**的分镜 JSON，panels 数组必须正好有 ${page.panelCount} 个元素，" +
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
        if (usingGateway) {
            storage.clearGatewayToken()
        } else {
            storage.clearToken()
        }
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
                // 余额变了 ⇒ 写快照 + 推小组件（见"桌面小组件"那一段的说明）
                snapshotBalance()
                pushWidgetUpdate()
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
                // 本次会话累计（见 `sessionAnlasSpent`）+ **每日统计**里那一笔实扣点数
                // （图数 / tag 字数在落库那一步已记，见 `recordUsage` 的说明）
                lastAnlasSpent?.let {
                    sessionAnlasSpent += it
                    recordUsage(images = 0, anlas = it, tagChars = 0)
                }

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
        val dims = MaskCodec.imageSize(sourcePath)
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
            val bitmap = MaskCodec.decodeFull(sourcePath)
                ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
            val bytes = java.io.ByteArrayOutputStream().use { buffer ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer)
                buffer.toByteArray()
            }
            bitmap.recycle()
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
        val dims = MaskCodec.imageSize(sourcePath)
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
                    val bitmap = MaskCodec.decodeFull(sourcePath)
                        ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                    val scaled = if (bitmap.width != reqW || bitmap.height != reqH) {
                        android.graphics.Bitmap.createScaledBitmap(bitmap, reqW, reqH, true)
                            .also { if (it !== bitmap) bitmap.recycle() }
                    } else {
                        bitmap
                    }
                    val bytes = java.io.ByteArrayOutputStream().use { buffer ->
                        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer)
                        buffer.toByteArray()
                    }
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
    fun setOutputFolder(uri: Uri?) {
        if (uri == null) {
            setSettings { it.copy(imageOutputTreeUri = "") }
            status = rt("storage.folderCleared")
            return
        }
        val resolver = getApplication<Application>().contentResolver
        val ok = runCatching {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        setSettings { it.copy(imageOutputTreeUri = uri.toString()) }
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

    /** 内置工具 `another_llm` 能调用的"用途"清单（对应节点里可被别的 LLM 调用的那些节点）。 */
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
        temperature = settings.llmTemperature,
    )

    /**
     * **统一的 LLM 调用入口**：发请求 + 记一条对话记录（成功失败都记），
     * 并且（记忆开着时）带上最近 N 轮历史、把这一轮追加进记忆。
     */
    // ------------------------------------------- 导入上下文（节点 `user_history` / `historical_record`）

    /**
     * Optional bundled conversation context. The public source keeps this asset empty
     * so first launch does not import private or example conversation history.
     */
    private suspend fun loadBundledLlmContext() {
        val asset = "llm_context.json"
        val raw = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().assets.open(asset)
                    .use { String(it.readBytes(), Charsets.UTF_8) }
            }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: return
        val turns = LlmContext.parse(raw)
        if (turns.isEmpty()) return
        // ⚠️ **只在"完全没有上下文"时才导入自带那份**（用户 2026-09-18：
        // 「关闭每次都清空上下文，上下文保留，只有没有上下文时才导入预设上下文」）。
        // 之前是"只要盘的上下文跟自带那份不一样就覆盖" ✗ —— 那等于每次开 App 都
        // 把用户手上那份上下文冲回这 12 轮。现在有上下文就原样保留，只补空档。
        if (settings.llmUserHistoryJson.isNotBlank()) return
        val json = LlmContext.toUserHistoryJson(turns)
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

    /** 导出到文件（手机：写到系统给的 Uri）。 */
    fun exportLlmMemoryUri(uri: Uri, kind: String? = null) {
        viewModelScope.launch {
            val json = llmMemoryExportJson(kind)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                }.getOrDefault(false)
            }
            status = rt(if (ok) "llm.contextExported" else "llm.contextBad")
        }
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
        java.io.File(getApplication<Application>().filesDir, "st_presets").apply { mkdirs() }

    /** 已解析的预设缓存（id → 解析结果；导入时解析一次，之后直接用）。 */
    private val stPresetCache = mutableMapOf<String, StPreset.Preset?>()

    /** 从系统文件选择器导入一份预设（`uri` 那条路）。 */
    fun importStPresetUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { String(it.readBytes(), Charsets.UTF_8) }
                }.getOrNull()
            }
            if (text == null) {
                toast(rt("st.importFailed"), isError = true)
                return@launch
            }
            // ⚠️ `uri.lastPathSegment` 在 SAF 下是 `document:1000045162`（**不是**文件名，用户
            // 2026-09-18 明确不要看到它）→ 查 `OpenableColumns.DISPLAY_NAME` 拿真文件名。
            val display = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull()
            }
            val name = display?.takeIf { it.isNotBlank() }
                ?: runCatching { uri.lastPathSegment ?: "" }.getOrDefault("")
            importStPreset(text, name)
        }
    }

    /**
     * **导出当前选中的预设**（用户 2026-09-18：「在导入预设旁边加一个导出预设」）。
     *
     * 原样把盘上那份 `.json` 写到用户选的位置 —— 不重新序列化，导出的文件跟导入的一模一样
     * （注释、键顺序、没启用的条目都保留），方便搬去电脑线或备份。
     */
    fun exportStPresetUri(uri: Uri) {
        val ref = settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }
        if (ref == null) {
            toast(rt("st.exportNone"), isError = true)
            return
        }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { java.io.File(stPresetDir(), ref.fileName).readText() }.getOrNull()
            }
            if (text == null) {
                toast(rt("st.exportFailed"), isError = true)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                }.getOrDefault(false)
            }
            toast(rt(if (ok) "st.exported" else "st.exportFailed"), isError = !ok)
        }
    }

    /**
     * 导入一份 ST 预设：解析 → **原样落盘** → 记一条引用。
     *
     * ⚠️ **不做自动瘦身**（用户 2026-09-18：「这个预设功能和之前不一样，用下午 2 点那个预设系统」）：
     * 导入进来就是文件本来的样子（134 条也在），`查看`里逐条开关 / 改正文 / `恢复默认` ——
     * 就是 1.1.29（下午两点前最后一版）那套，**没有**自动删条目、也没有"删条目"按钮。
     * `.orig` 留的是**刚导入的原文**，所以「恢复默认」= 回到导入那一刻（删掉的条目能找回来）。
     *
     * 解析失败（不是 ST 预设 / 没有 prompts）会明确报错，不会存进去半个空壳。
     */
    fun importStPreset(json: String, fileName: String) {
        // 名字 = **文件名**（用户 2026-09-18：不要显示 SAF 的 `document:123456`，要显示文件名）。
        // `importStPresetUri` 已经查过 DISPLAY_NAME；这里再兜一层，老调用/老名字都清干净。
        val name = fileName.substringAfterLast('/').substringBeforeLast('.')
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

    /**
     * **删掉某一条**（「查看」里每行的「删除」；用户 2026-09-18：「加上删除功能」）。
     *
     * 只写工作文件，**不动 `.orig`** —— 所以「恢复默认」= 回到刚导入那一刻，删错了能找回来。
     * 顺手把这个 id 从两份开关清单里去掉（留着只会指向已删除的条目），并重算统计。
     */
    fun deleteStPresetEntry(presetId: String, entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { pruneEntryInternal(presetId, entryId) }
            if (!ok) {
                toast(rt("st.entryDeleteFailed"), isError = true)
                return@launch
            }
            toast(rt("st.entryDeleted"))
        }
    }

    /** **一键删掉所有关着的**（「查看」里的「删除关着的」= 手动瘦身；用户 2026-09-18 要求）。 */
    fun pruneOffStPresetEntries(presetId: String) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { pruneOffInternal(presetId) }
            if (removed <= 0) {
                toast(rt("st.offNothingToDelete"))
                return@launch
            }
            toast(rf("st.offDeleted", mapOf("n" to removed.toString())))
        }
    }

    /** 真干活：删一条。返回是否成功（读/写任一失败都返回 false）。 */
    private fun pruneEntryInternal(presetId: String, entryId: String): Boolean {
        val ref = settings.stPresetRefs.firstOrNull { it.id == presetId } ?: return false
        val file = java.io.File(stPresetDir(), ref.fileName)
        val raw = runCatching { file.readText() }.getOrNull() ?: return false
        val parsed = StPreset.parse(raw, ref.name) ?: return false
        val keep = parsed.allEntries.map { it.identifier }.filter { it != entryId }.toSet()
        if (keep.isEmpty()) return false // 只剩一条还要删：不干（别把文件删空）
        val slim = StPreset.pruneToIds(raw, keep)
        if (slim == raw) return false
        val wrote = runCatching { file.writeText(slim) }.isSuccess
        if (wrote) afterEntryChange(presetId, removed = listOf(entryId))
        return wrote
    }

    /** 真干活：删掉所有"关着的"（= 用户实际不会发出去的那些）。返回删掉几条。 */
    private fun pruneOffInternal(presetId: String): Int {
        val ref = settings.stPresetRefs.firstOrNull { it.id == presetId } ?: return 0
        val file = java.io.File(stPresetDir(), ref.fileName)
        val raw = runCatching { file.readText() }.getOrNull() ?: return 0
        val parsed = StPreset.parse(raw, ref.name) ?: return 0
        val effective = parsed
            .withOverrides(ref.enabledEntries, ref.disabledEntries)
            .entries.map { it.identifier }.toSet()
        val removed = parsed.allEntries.map { it.identifier }.filter { it !in effective }
        if (removed.isEmpty() || effective.isEmpty()) return 0
        // ⚠️ 这条路的 keep 是"用户实际会发出去"的集合 → 里面若有不在顺序表里的 extras，
        // 要接回顺序表（`appendMissingToOrder = true`），否则它们会被当成关着丢掉。
        val slim = StPreset.pruneToIds(raw, effective, appendMissingToOrder = true)
        if (slim == raw) return 0
        val wrote = runCatching { file.writeText(slim) }.isSuccess
        if (!wrote) return 0
        afterEntryChange(presetId, removed = removed)
        return removed.size
    }

    /** 删完条目之后的收尾：缓存作废、两份清单去掉已删的 id、统计重算。 */
    private fun afterEntryChange(presetId: String, removed: List<String>) {
        stPresetCache.remove(presetId)
        val gone = removed.toSet()
        setSettings { current ->
            current.copy(
                stPresetRefs = current.stPresetRefs.map { ref ->
                    if (ref.id != presetId) {
                        ref
                    } else {
                        ref.copy(
                            enabledEntries = ref.enabledEntries.filterNot { it in gone },
                            disabledEntries = ref.disabledEntries.filterNot { it in gone },
                        )
                    }
                },
            )
        }
        refreshStPresetStats(presetId)
    }

    /** 逐条启用/停用（存的是"停用清单"，所以默认状态=预设原样）。 */
    fun setStPresetEntryEnabled(presetId: String, entryId: String, enabled: Boolean) {
        if (entryId.isBlank()) return
        stPresetCache.remove(presetId) // 过滤结果变了，缓存作废
        setSettings { current ->
            current.copy(
                stPresetRefs = current.stPresetRefs.map { ref ->
                    if (ref.id != presetId) {
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
        refreshStPresetStats(presetId)
    }

    /** 改某一条的正文（写回落盘文件；原始副本不动，随时可"恢复默认"）。 */
    fun updateStPresetEntryContent(presetId: String, entryId: String, content: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ref = settings.stPresetRefs.firstOrNull { it.id == presetId }
                        ?: return@runCatching false
                    val file = java.io.File(stPresetDir(), ref.fileName)
                    val root = org.json.JSONObject(file.readText())
                    val prompts = root.optJSONArray("prompts") ?: return@runCatching false
                    var changed = false
                    for (index in 0 until prompts.length()) {
                        val item = prompts.optJSONObject(index) ?: continue
                        if (item.optString("identifier") == entryId) {
                            item.put("content", content)
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
            stPresetCache.remove(presetId)
            setSettings { current ->
                current.copy(
                    stPresetRefs = current.stPresetRefs.map {
                        if (it.id == presetId) it.copy(hasEdits = true) else it
                    },
                )
            }
            refreshStPresetStats(presetId)
            status = rt("st.saved")
        }
    }

    /** 恢复默认：用导入时留的原始副本覆盖回去（连同停用清单与编辑标记一起清）。 */
    fun resetStPreset(presetId: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ref = settings.stPresetRefs.firstOrNull { it.id == presetId }
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
            stPresetCache.remove(presetId)
            setSettings { current ->
                current.copy(
                    stPresetRefs = current.stPresetRefs.map {
                        if (it.id == presetId) {
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
            refreshStPresetStats(presetId)
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
        val memoryKey = kindKey.substringAfterLast('.')
        val rounds = settings.llmHistoryRounds
        // 源码 llm.py:1651-1653：关掉记忆 = **清空历史**
        if (!settings.llmMemoryEnabled && LlmMemoryRules.CLEAR_ON_DISABLE) {
            if (llmMemory.containsKey(memoryKey)) {
                llmMemory.remove(memoryKey)
                persistLlmMemory() // 关了记忆=清空：落盘那份也跟着清
            }
        }
        // 源码 llm.py:1611-1617：is_locked 开启时参数没变就直接返回上轮结果
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
        val memoryNote = if (settings.llmMemoryEnabled) " · 记忆 ${history.size}/$rounds 轮" else " · 记忆关"

        // 本次请求会带上多少轮"导入的上下文"（用户 2026-09-17：第一次对话也要带上）
        val contextTurns = settings.llmUserHistoryJson.takeIf { it.isNotBlank() }
            ?.let { LlmContext.parse(it).size } ?: 0
        val contextNote = if (contextTurns > 0) " · 上下文 $contextTurns 轮" else ""
        // 图片走哪条路（源码 539-576）：有 ImgBB key 先上传取 URL，否则 base64
        var dataUrlForRequest = imageDataUrl
        var imageUrlForRequest: String? = null
        if (imageDataUrl != null && settings.llmImgbbKey.isNotBlank()) {
            imageUrlForRequest = llm.uploadToImgbb(
                settings.llmImgbbKey,
                imageDataUrl.substringAfter("base64,", imageDataUrl),
            )
            dataUrlForRequest = null
        }
        // **全部输入口按节点传过去**（对话格式见 LlmApi.messages 的逐条对照）
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
            tools = llmToolsFor(memoryKey),
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
            if (settings.llmMemoryEnabled) {
                LlmMemoryRules.append(
                    turns = llmMemory.getOrPut(memoryKey) { mutableListOf() },
                    turn = LlmTurn(user = userPrompt, assistant = out),
                )
                persistLlmMemory() // 落盘：重启之后这一轮还在（用户 2026-09-18 要求）
            }
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

    /** 节点的 `tools`：用户填的 JSON + 内置 `another_llm`（只在不是主脑时提供，源码 1607-1610）。 */
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
        // ⚠️ **内置 `another_llm` 工具已停用**（用户 2026-09-18 报 400）：
        // 它以前是"只要 `llmMainBrain` 是关的（默认就是关的）就**每次请求都带上 `tools`**"，
        // 而 DeepSeek **思考模式下带 `tools` 的请求必须把历史轮次的 `reasoning_content` 完整回传**
        // （官方《思考模式》文档：带 tools 不回传就 400；不带 tools 则无需回传）。
        // 我们历史里的 assistant 只有 `content`（自带上下午那 12 轮是 gemini 存的，根本没有 reasoning）
        // → 于是分镜这类调用直接 400 ✗。
        // 控制它的「主脑」开关也已按用户要求从界面删掉，所以它现在是"没人能关的默认开启" ✗。
        // 要用回来得先把 `reasoning_content` 也存进记忆/上下文并回传，再给个显式开关。
        return list
    }

    /** 执行一次工具调用（源码 `dispatch_tool` 的等价物）。 */
    private suspend fun dispatchLlmTool(call: LlmApi.ToolCall, callerKey: String): String? {
        if (call.name != "another_llm") return null
        val args = runCatching { org.json.JSONObject(call.arguments) }.getOrNull() ?: return null
        val target = args.optString("id").trim()
        val question = args.optString("question").ifBlank { args.optString("prompt") }
        if (target.isEmpty() || target == callerKey || question.isEmpty()) return "找不到对应的智能助手"
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
     * system prompt 用手写常量 [PromptRules.TRANSLATE_RULES]（**原样**取自「提示词小助手」插件）。
     */
    fun translatePrompt(field: String = PromptField.POSITIVE) {
        // 剧情框也能翻（用户 2026-09-17）：它不是提示词，读写的都是 `comicPlot`
        val plot = PromptField.isPlot(field)
        val key = if (plot) PromptField.PLOT else PromptField.orDefault(field)
        val text = (if (plot) comicPlot else params.promptOf(key)).trim()
        if (text.isEmpty()) {
            toast(rt("llm.emptyPrompt"), isError = true)
            return
        }
        if (blockedByPromptLock(key)) return
        // 再点一次 = 撤销翻译，回到原文
        val revert = translateRevert[key]
        if (revert != null && text == revert.second) {
            if (plot) {
                comicPlot = revert.first
            } else {
                params = params.withPrompt(key, revert.first)
                viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
            }
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
     * system prompt 用设置里可编辑的「优化规则」（默认值原样取自「提示词小助手」插件）。
     */
    fun optimizePrompt(field: String = PromptField.POSITIVE) {
        val plot = PromptField.isPlot(field)
        val key = if (plot) PromptField.PLOT else PromptField.orDefault(field)
        val text = (if (plot) comicPlot else params.promptOf(key)).trim()
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
        if (PromptField.isPlot(key)) {
            // 剧情：写回 comicPlot（自带防抖落盘 + setter 自己记一笔撤销）
            comicPlot = cleaned
        } else {
            val before = params.promptOf(key)
            params = params.withPrompt(key, cleaned)
            // AI 改写也算一次改动，撤销按钮要能回到改写前
            if (before != cleaned) recordPromptEdit(key, before)
            withContext(Dispatchers.IO) { storage.setParams(params) }
        }
        if (rememberOriginal != null) {
            translateRevert[key] = rememberOriginal to cleaned
        }
        toast(rt(doneKey), detail = detail)
    }

    /**
     * 把一段文本直接写进某个提示词框（历史提示词的「用这一条」用它）。
     *
     * 走这里而不是 `setParam`，是因为历史抽屉现在要按**当前选中的框**落笔。
     */
    fun setPromptField(field: String, text: String) {
        val key = PromptField.orDefault(field)
        if (blockedByPromptLock(key)) return
        val before = params.promptOf(key)
        if (before == text) return
        params = params.withPrompt(key, text)
        recordPromptEdit(key, before)
        viewModelScope.launch { withContext(Dispatchers.IO) { storage.setParams(params) } }
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
     * **只清提示词里的某一条**（风格 / 正面 / 负面）。
     *
     * ⚠️ 用户 2026-09-24：「生成图片上的**对文本框的垃圾桶还是对有些文本框不生效**，
     * 怀疑是**文本框未统一指针**」—— 那条报的**根因就在这里**：
     * 过去三个提示词框注册的是**同一个** `TextFieldTarget.Prompt`，垃圾桶分不清用户站在哪一条，
     * 只能**一次清三条**；而正面 / 负面现在是**切换着看**的（同屏只看得见一条），
     * 于是"清掉了我看不见的那两条"表现就是"垃圾桶不生效 / 清错地方"。
     *
     * 锁着的那一条**跳过并如实说一句**（不静默）—— 与 [clearPrompts] 同一条纪律。
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
        // 清空也要进撤销栈（和 clearPrompts 同一条口径）
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
     */
    fun onTextFieldFocus(target: TextFieldTarget, focused: Boolean) {
        if (focused) {
            textFieldTarget = target
        } else if (textFieldTarget == target) {
            textFieldTarget = null
        }
        // 剧情框失焦就立刻落盘（别等那 600ms —— 用户可能马上就去划掉应用）
        if (target == TextFieldTarget.Plot && !focused) flushComicPlot()
    }

    /**
     * **底栏垃圾桶**：清掉当前聚焦的那个文本框。
     *
     * 三条提示词走 [clearPrompts]（一次清三条、跳过锁住的）；角色 / 漫画那几个框各清各的。
     * 先看目标再动手，所以不会出现"在角色框里按了，结果提示词被清空"。
     */
    fun clearFocusedTextField() {
        when (val target = textFieldTarget) {
            // 还没登记（没点过任何输入框）⇒ 保持老行为：三条一起清
            null -> clearPrompts()

            // ⚠️ 用户 2026-09-24：提示词三个框**各清各的** —— 原来一律 `clearPrompts()`
            //    一次清三条，而正面 / 负面现在是**切换着看**的 ⇒ "清掉看不见的那两条"
            //    看起来就是垃圾桶坏了（"对有些文本框不生效"那条报的就是这个）。
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
    fun selectReverseImage(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { copyImageToPrivate(uri, "reverse") }
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
    private val animaPrefs = getApplication<android.app.Application>()
        .getSharedPreferences("animadex_tool", android.content.Context.MODE_PRIVATE)

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
     * ⚠️ 为什么不照抄角色图鉴那套"服务端分页 + facets"：法典图鉴**没有检索接口**，
     * 它就是把整本 JSON（画师词典 2.9 MB / 2688 条）一次拉下来在浏览器里筛。
     * 我们也一样 —— 换来的是搜索零延迟、可以反复筛不用来回请求。
     *
     * ⚠️ 数据是**在线读、本地缓存**，不打包进 APK：那份汇编的授权写明"未经许可不得再分发"
     *（代码 MIT，但词条与数据汇编另有归属，见 [TagCodexProtocol.LICENSE_NOTE]）。
     */
    /**
     * ⚠️ **第 ㊿ 批**：给它一个**本地缓存目录** ✓ —— 用户口径「**不能下载在app或软件里
     * 不用每次下载吗**」⇒ 下过一次就存着 ✓、之后不再联网 ✓（词表 1.8 MB，只第一次下 ✓）。
     *
     * ⚠️ 为什么不打包进 APK ✗：授权写明"数据汇编未经许可不得再分发" ✓ ——
     * 缓存在**用户自己的机器上**不违反 ✓，体验与打包**没有区别** ✓。
     */
    private val tagCodex = TagCodexApi(
        cacheDir = java.io.File(platform.paths.filesDir, "tagcodex-cache"),
    )

    var tagCodexBusy by mutableStateOf(false)
        private set
    var tagCodexError by mutableStateOf<String?>(null)
        private set

    /** 可选的法典列表（已剔除 NSFW 与外链本）。 */
    var tagCodexList by mutableStateOf<List<TagCodexSummary>>(emptyList())
        private set

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
    private val tagCodexPrefs = getApplication<android.app.Application>()
        .getSharedPreferences("tagcodex_tool", android.content.Context.MODE_PRIVATE)

    init {
        tagCodexColumns = tagCodexPrefs.getInt("columns", 2).coerceIn(2, 4)
        // 上次开的是哪一本也记着：常用的那本不用每次重挑
        tagCodexId = tagCodexPrefs.getString("codex", null)
            ?.takeIf { it.isNotBlank() }
            ?: TagCodexProtocol.DEFAULT_CODEX
    }

    private fun rememberTagCodexPrefs() {
        tagCodexPrefs.edit()
            .putInt("columns", tagCodexColumns)
            .putString("codex", tagCodexId)
            .apply()
    }

    /** 齿轮里改列数：存下来，下次进工具还是这样。 */
    fun tagCodexSetColumns(columns: Int) {
        tagCodexColumns = columns.coerceIn(2, 4)
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
     * 同级**先 core 再书分片** ✓。
     */
    private var tagCodexZhShards by mutableStateOf<List<TagZhProtocol.Shard>>(emptyList())

    /** 词表备好了吗 ✓ —— 界面据此决定要不要画中文小字 ✓。 */
    val tagCodexZhReady: Boolean get() = tagCodexZhShards.isNotEmpty()

    /** **查一个 tag 的中文** ✓（没有就 `null` ✓ —— 那一格只显示英文 ✓，不显空占位 ✗）。 */
    fun tagCodexZh(text: String): String? =
        TagZhProtocol.lookup(tagCodexZhShards, TagZhProtocol.key(text))

    /**
     * 把 `text` 按 tag 切成段、逐段配好中文 ✓（界面照着画 ✓）。
     *
     * ⚠️ **拼回 `lead + text + sep` 就是原文** ✓ —— 这就是"**复制不带翻译**"的地基 ✓。
     */
    fun tagCodexZhPieces(text: String): List<Pair<TagZhProtocol.Piece, String?>> =
        TagZhProtocol.split(text).map { it to tagCodexZh(it.text) }

    /** 拉当前这本的两片词表 ✓（**失败静默** ✗ —— 没有对照就是"不显示中文" ✓）。 */
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

    private fun tagCodexLoad(codexId: String) {
        viewModelScope.launch {
            tagCodexBusy = true
            tagCodexError = null
            try {
                // 列表只在第一次拉；之后切本不用再要
                if (tagCodexList.isEmpty()) {
                    val array = withContext(Dispatchers.IO) { tagCodex.codexes() }
                    tagCodexList = TagCodexProtocol.parseCodexes(array)
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
                //（同一条缓存 + 三级降级 ✓，下过一次就不再联网 ✓；
                // 失败静默 ✓ —— 没有对照表就只是"不显示中文" ✓，不影响查词条 ✓）。
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
    fun tagCodexSelect(id: String) {
        if (id == tagCodexId && tagCodexDoc != null) return
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
    fun selectMetadataImage(uri: Uri) {
        viewModelScope.launch {
            metadataBusy = true
            status = rt("metadata.parsing")
            try {
                val path = withContext(Dispatchers.IO) { copyImageToPrivate(uri, "metadata") }
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
        val bitmap = MaskCodec.decodeFull(path)
            ?: throw LlmException(rt("error.inpaintSourceMissing"))
        val scaled = if (maxOf(bitmap.width, bitmap.height) > 1024) {
            val ratio = 1024f / maxOf(bitmap.width, bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
        val bytes = java.io.ByteArrayOutputStream().use { buffer ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, buffer)
            buffer.toByteArray()
        }
        scaled.recycle()
        return "data:image/jpeg;base64," +
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    // -------------------------------------------------------------- 备份 / 恢复

    /**
     * 把备份**内嵌进一张自选图片**再导出（和 ComfyUI / NovelAI 把参数写进 PNG 一样）：
     * 图片照常能看，备份 JSON 跟在 PNG 的 `tEXt` 块里（关键字 `naistudio-backup`）。
     */
    fun exportBackupToImage(imageUri: Uri, targetUri: Uri) {
        viewModelScope.launch {
            status = rt("status.backupExporting")
            val json = BackupCodec.encode(
                bundle = currentBundle(),
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
                pretty = false,
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val sourceBytes = context.contentResolver.openInputStream(imageUri)
                        ?.use { it.readBytes() }
                        ?: error("读不到选中的图片")
                    // PNG 直接用原字节（不重编码，画质无损）；其它格式先解成位图再编码成 PNG
                    val png = if (PngMetadata.isPng(sourceBytes)) {
                        sourceBytes
                    } else {
                        // 相机大图直接整张解码会 OOM：先按需降采样（长边 ≤ 4096），再转 PNG
                        val bounds = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
                        var sample = 1
                        val longest = maxOf(bounds.outWidth, bounds.outHeight)
                        while (longest / sample > 4096) sample *= 2
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }
                        val bitmap = android.graphics.BitmapFactory
                            .decodeByteArray(sourceBytes, 0, sourceBytes.size, options)
                            ?: error("这张图片解不开")
                        val buffer = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer)
                        bitmap.recycle()
                        buffer.toByteArray()
                    }
                    val embedded = PngMetadata.embedText(png, json) ?: error("这张图片不是有效的 PNG")
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
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
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            status = rt("status.backupExporting")
            val text = BackupCodec.encode(
                bundle = currentBundle(),
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
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
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            status = rt("status.backupImporting")
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
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
            restoredSettings?.let { restored ->
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
    private fun reacquireOutputFolder(uriString: String): Boolean = runCatching {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            Uri.parse(uriString),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }.isSuccess

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
                        ImageDecoder.decodeSampled(preview.image)
                    }
                    if (bitmap != null) generationPreview = bitmap
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
     * （放大 / 导演工具 / `resetMaskState`）不受影响，重绘请求也只在模式内发出，不会误清。
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
        // 从漫画 / 无限画布点遮罩，先回普通画布；没有工作图时仍提示用户导入或生成。
        if (canvasMode != 0) chooseCanvasMode(0)
        // 工作图：图生图导入的图优先，否则最近生成的那张 —— 导入的图也能涂遮罩
        val sourcePath = workImagePath
        if (sourcePath == null) {
            status = rt("mask.needImage")
            return
        }
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
        val dims = MaskCodec.imageSize(path)
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
    //
    // ⚠️ 这一整段是从电脑线搬过来的（电脑线 `naistudio-shared/.../state/AppState.kt` 里
    //    「画布编辑器」那一节，行号 4877-5508 一带），语义、注释、行为**照抄**。
    //
    // 只改了两处**平台差异**（电脑线的 `platform.ImageIo` 抽象手机线还没有，手机端 AppState
    // 本来就直接用 Android 的 `Bitmap`）：
    //  1. 像素 ↔ 位图：电脑线 `images.composeImageOf / decodeFull / scale / pixelsOf / fromArgb /
    //     pngBytes` → 这里用 `Bitmap.createBitmap / MaskCodec.decodeFull / Bitmap.createScaledBitmap /
    //     getPixels / compress`（见本节末尾那三个 `canvasXxx` 私有小工具）；
    //  2. 落盘：电脑线 `platform.openOutput(path)`（键值对里那条"能打开输出流"的平台能力）
    //     手机线没有 → 直接 `File.outputStream()`（手机端本来就是文件系统私有目录）。
    // 其余（字段名、函数名、状态机、读盘/落盘策略、遮罩的联动判据）一字未改。

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
     * 「**按住空格 + 左键拖动 = 平移画布**」（用户 2026-09-16 要求）。
     *
     * 住在 `AppState` 而不是编辑器内部：按键是**窗口层**收的（走 `EditorShortcuts` 信箱），
     * 而用这个标记的是画布的指针处理 —— 两边隔着一层，放这儿是两边都能够到的最短路径。
     * （另外中键拖动一直都能平移，这个是给没中键/嫌中键别扭的人。）
     *
     * ⚠️ 手机线：键位那一层没搬（见 `screens/CanvasEditor.kt` 末尾的说明），所以这个状态
     * 目前**恒为 false**。字段和函数照搬过来，以后接上外接键盘 / DeX 就能直接用。
     */
    var canvasSpacePan by mutableStateOf(false)
        private set

    /** 空格按下/抬起（编辑器关掉时也会被复位成 false）。 */
    fun holdCanvasSpace(held: Boolean) {
        if (canvasSpacePan == held) return
        canvasSpacePan = held
    }

    /**
     * 编辑器里**有没有文本输入框正拿着焦点**（笔刷大小、调整画布的宽高、色值输入）。
     *
     * 为什么要这个：键位是从**窗口层**收的（`EditorShortcuts`），一旦改成"抢在焦点节点之前"
     * 收键（`onPreviewKeyEvent`，为了不漏掉空格的抬起），字母键就会把输入框里的打字抢走。
     * 所以输入框拿到焦点时，编辑器**一个键都不吃**（见 `onCanvasShortcut` 开头）。
     *
     * ⚠️ 手机线：界面上的数值框仍然照电脑线那样把它报上来（行为一致，只是暂时没人读）。
     */
    var canvasTextFocused by mutableStateOf(false)
        private set

    fun reportCanvasTextFocus(focused: Boolean) {
        if (canvasTextFocused == focused) return
        canvasTextFocused = focused
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
        if (canvasMode != 0) chooseCanvasMode(0)
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
        canvasTextFocused = false
        if (commit && session != null) commitCanvasSession(session)
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
        cancelCanvasBitmapCache()
        canvasRevision++
        syncCanvasHistory()
        status = rt("canvas.resetApplied")
    }

    /**
     * 编辑器显示用的位图（按 [canvasRevision] 缓存）。
     *
     * 每画一笔都要重建这么一张，所以**不能走 PNG 编解码**（编码成 PNG 再解回来，
     * 一张 832×1216 要几十毫秒，涂起来会拖手）；
     * 电脑线走的是 `images.composeImageOf`（直接从 ARGB 建图），手机线上就是
     * `Bitmap.createBitmap(pixels, …).asImageBitmap()`（同样是"直接从 ARGB 建图"那条路）。
     */
    fun canvasImage(): ImageBitmap? {
        val session = canvasSession ?: return null
        val cached = canvasBitmap
        if (cached != null && canvasBitmapRevision == canvasRevision) return cached
        val built = canvasArgbBitmap(session.pixels, session.width, session.height).asImageBitmap()
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
        val built = canvasArgbBitmap(floating.pixels, floating.patchWidth, floating.patchHeight)
            .asImageBitmap()
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
        val decoded = MaskCodec.decodeFull(source)
        if (decoded == null) {
            status = rt("canvas.loadFailed")
            return null
        }
        var image: Bitmap = decoded
        val longest = maxOf(decoded.width, decoded.height)
        if (longest > CANVAS_MAX_LONG_SIDE) {
            val ratio = CANVAS_MAX_LONG_SIDE.toFloat() / longest.toFloat()
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            image = scaled
            status = rf("canvas.downscaled", mapOf("side" to CANVAS_MAX_LONG_SIDE))
        }
        val pixels = canvasPixelsOf(image)
        // ⚠️ 手机端多写这两行：Android 的 `Bitmap` 回收之后再去读 `width/height` 虽然目前还能拿到，
        // 但那是实现细节；电脑线的 `NativeImage` 没这层顾虑。取值放在 recycle 之前，语义完全一样。
        val imageWidth = image.width
        val imageHeight = image.height
        image.recycle()
        canvasSourcePath = source
        canvasOriginalPixels = pixels.copyOf()
        return ImageEditSession(imageWidth, imageHeight, pixels)
    }

    /**
     * 点「完成」之后把编辑结果**另存一份**并切成底图。
     *
     * ## 用户 2026-09-26 定的规矩
     *
     * 「画布编辑器**没有保存功能**，点击完成后**自动多保存一份**，**原图不动**，
     * **改变的图多一份**，改变的图片**在没有清后台的情况下可以重置修改**，
     * **没有修改的图片不多保存**」。逐条落到这里：
     *  · **改没改**：拿 `canvasOriginalPixels`（打开编辑器那一刻的副本）和当前像素**逐像素比**
     *    —— 比 `session.canUndo` 准：改了一下又手动还原成逐像素一样，也只有这里看得出来；
     *  · **没改 ⇒ 一个字节都不写**（这条以前是"无论如何都落一个派生文件"，所以"点开看一眼
     *    再点完成"也会多出一份；顺带还省掉了大图被降采样那一下）；
     *  · **改了 ⇒ 另存成图库里的一条新记录**（[Storage.saveImage]，`feature = "canvas"`），
     *    **原图那条一动不动**；文件名 `<原图名>-edit`、再编辑就是 `-edit2`…（见 [canvasEditStem]）；
     *  · **重置修改**：把"新图 → 原图"记进 [canvasEditOrigins]（**只在内存**，
     *    用户明说了"没有清后台"才要能重置，不落盘正是这个语义）——
     *    图库里那张图的详情因此多一个「重置修改」（见 [resetCanvasEdit]）。
     *
     * ## 为什么不再往 `filesDir/canvas` 写那一份
     *
     * 以前派生图落在私有目录、文件名按源图算（原地覆盖），是为了"涂过遮罩再进编辑器，
     * 遮罩别被当成换图清掉"。现在派生图**直接就是图库里那张**（和 `useImageAsI2iBase`
     * 同一个口径），遮罩的归属在下面照旧按 `maskSourcePath` 接上 ⇒ 这个顾虑没了，
     * 少写一份冗余文件。
     */
    private fun commitCanvasSession(session: ImageEditSession) {
        val source = canvasSourcePath
        // ---- ① 改没改（见上面那条注释）----
        val original = canvasOriginalPixels
        val modified = original == null || !original.contentEquals(session.pixels)
        if (!modified) {
            // 没改：底图原样指回源图（尺寸没变 ⇒ 遮罩照旧有效，什么都不用动）
            if (source != null) {
                i2iBasePath = source
                i2iDisplayPath = source
                i2iDisplayWidth = session.width
                i2iDisplayHeight = session.height
            }
            status = rt("canvas.committed")
            return
        }
        // ---- ② 尺寸没变的编辑：遮罩还能用（同一套像素坐标），等下指到新图上；
        //         尺寸变了（或本来就是空白画布）：遮罩的网格对不上，只能清掉。
        //         ⚠️ 判据要拿**遮罩自己的草图尺寸**比，不能拿 `canvasWidth`：那个是会话尺寸、
        //            恒等于自己，等于没判。这一条必须在**关编辑器之前**算好（外面马上清字段）。
        val keepMask = source != null &&
            maskSourcePath == source &&
            maskSketch?.width == session.width &&
            maskSketch?.height == session.height
        val stem = canvasEditStem(source)
        val sourceItem = source?.let { path -> history.firstOrNull { it.filePath == path } } ?: current
        val native = canvasArgbBitmap(session.pixels, session.width, session.height)
        val bytes = canvasPngBytes(native)
        native.recycle()
        // PNG 编码 + 落盘都是重活（832×1216 要几十毫秒），丢到主线程外算：
        // 关编辑器这一下不能被它顿住（原来是在主线程同步写的）。
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.saveImage(
                        bytes = bytes,
                        // 参数 / 种子 / 模型沿用**原图那条记录** —— 图库里点开看到的是同一套
                        params = sourceItem?.let { GenerateParamsCodec.fromJson(it.params) } ?: params,
                        seed = sourceItem?.seed ?: resolveSeed(params),
                        settings = settings,
                        groups = groups,
                        feature = "canvas",
                        model = sourceItem?.model,
                        width = session.width,
                        height = session.height,
                        groupId = null,
                        stemOverride = stem,
                    )
                }
            }.onSuccess { item ->
                history = listOf(item) + history
                sessionImages.add(0, item)
                current = item
                // 新图不是放大版了 —— 清掉"放大前那张"的记录（和生成那条流程一个口径）
                upscaleSource = null
                i2iOriginalPath = i2iOriginalPath ?: source
                i2iBasePath = item.filePath
                i2iDisplayPath = item.filePath
                i2iDisplayWidth = session.width
                i2iDisplayHeight = session.height
                if (keepMask) maskSourcePath = item.filePath else resetMaskState()
                // "重置修改"的凭据：只在内存（用户：没有清后台才要能重置）
                if (source != null) canvasEditOrigins[item.filePath] = source
                status = rt("canvas.savedCopy")
            }.onFailure {
                status = rt("canvas.saveFailed")
            }
        }
    }

    /**
     * **重置修改**（用户 2026-09-26）：把某张"画布另存"出来的图连同那条记录一起丢掉，
     * 底图 / 遮罩 / 当前图退回**原图**。
     *
     * 由图库那张图的详情里那颗「重置修改」调用（只有 [canResetCanvasEdit] 为 true 时才显示）。
     */
    fun resetCanvasEdit(editedPath: String) {
        val original = canvasEditOrigins[editedPath]
        if (original == null) {
            toast(rt("gallery.resetEditMissing"), isError = true)
            return
        }
        viewModelScope.launch {
            val item = history.firstOrNull { it.filePath == editedPath }
            if (item != null) {
                runCatching { withContext(Dispatchers.IO) { storage.deleteHistory(item.id) } }
                history = history.filterNot { it.id == item.id }
                sessionImages.removeAll { it.id == item.id }
                sessionImageDurationMs.remove(item.id)
                if (current?.id == item.id) {
                    current = history.firstOrNull { it.filePath == original }
                        ?: history.firstOrNull()
                }
            }
            // 底图 / 显示 / 遮罩归属都退回原图（尺寸没变过，坐标口径照旧）
            if (i2iDisplayPath == editedPath) i2iDisplayPath = original
            if (i2iBasePath == editedPath) i2iBasePath = original
            if (maskSourcePath == editedPath) maskSourcePath = original
            canvasEditOrigins.remove(editedPath)
            status = rt("gallery.resetEditDone")
        }
    }

    /**
     * 画布另存的文件名前缀：`<原图名>-edit`，同一张再改就是 `-edit2`、`-edit3`…
     *
     * 先把上一次的 `-edit`/`-edit2` 尾巴掐掉再编号，免得越接越长（`A-edit-edit-edit`）。
     */
    private fun canvasEditStem(source: String?): String {
        val raw = source
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "canvas"
        val base = Regex("-edit\\d*$")
            .find(raw)
            ?.let { raw.substring(0, it.range.first) }
            ?.takeIf { it.isNotBlank() }
            ?: raw
        val used = history.count { it.filePath.substringAfterLast('/').startsWith("$base-edit") }
        return if (used <= 0) "$base-edit" else "$base-edit${used + 1}"
    }

    // ---- 画布编辑器用到的三个位图小工具（电脑线那套 `platform.ImageIo` 的手机版等价物）----

    /** ARGB 像素 → Bitmap（电脑线的 `ImageIo.fromArgb` / `composeImageOf` 在手机端的对应实现）。 */
    private fun canvasArgbBitmap(pixels: IntArray, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

    /** Bitmap → ARGB 像素（电脑线的 `ImageIo.pixelsOf`）。 */
    private fun canvasPixelsOf(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    /** Bitmap → PNG 字节（电脑线的 `ImageIo.pngBytes`）。 */
    private fun canvasPngBytes(bitmap: Bitmap): ByteArray =
        java.io.ByteArrayOutputStream().use { buffer ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
            buffer.toByteArray()
        }

    private fun colorHex(color: Int): String =
        "#%06X".format(color and 0xFFFFFF)

    // ------------------------------------------------------------------
    // **出图积分预测**（用户 2026-09-24：「给**双端**加上积分预测功能，显示在**生成按钮**上，
    //   显示 **[数字] 闪电图标**」✓）—— 公式在 `models/AnlasCost.kt` ✓（照参考仓库
    //   `Aaalice233/Aaalice_NAI_Launcher` 的 `anlas_calculator.dart` 抄的 ✓）。
    //
    // ⚠️ 手机线**没有 `RunAction` 那个枚举** ✗（分派是 `generateOrInpaint` 里那个 `when` ✓）——
    //    所以这里**按同一套条件重写一遍判定** ✓（条件逐条对着那个 `when` 抄 ✓，
    //    改了那边记得改这里 ✓）。
    // ⚠️ 这一块**只读** ✓：界面拿它算按钮上那个数字 ✓。
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // **提示词词典**（用户 2026-09-24：「补充词典库」✓）—— 见 `models/TagDictionary.kt` ✓。
    //
    // ⚠️ **只读** ✗（启动时后台加载一次 ✓，界面拿它出候选 ✓，没有任何写入口 ✓）；
    // ⚠️ 加载失败 = **空表** ✓（补全不出现 ✓）——**不是**崩溃 ✓。
    // ------------------------------------------------------------------

    /** 词典（39,577 条 ✓；加载完成前是空的 ✓）。 */
    var tagDictionary by mutableStateOf<List<TagDictionary.Entry>>(emptyList())
        private set

    /** **按已经打出来的那一截给候选** ✓（界面每次重组调一次 ✓，一趟线性扫描 ✓）。 */
    fun tagSuggestions(query: String, limit: Int = 8): List<TagDictionary.Entry> =
        if (tagDictionary.isEmpty()) emptyList() else TagDictionary.suggest(tagDictionary, query, limit)

    /** 现在是不是 Opus 档 ✓（免费额度那条要它 ✓）。 */
    private val opusNow: Boolean
        get() = account.tierLevel == AnlasCost.OPUS_TIER

    /** V5 的免费池透支了没 ✓（透支就不再抵那一张 ✓）。 */
    private val opusQuotaExhaustedNow: Boolean
        get() = account.opusUsage?.isNegative == true

    /**
     * **这次按主按钮大概要几点** ✓（`null` = 算不出来 / 现在不该显示 ✓）。
     *
     * 档位判定与 [generateOrInpaint] 那个 `when` **逐条对应** ✓：
     *  ① 无限画布（`canvasMode == 1` ✓）→ 按当前聚焦框那套尺寸 ✓（框还没拖 ⇒ `null` ✓）；
     *  ② 局部重绘（`maskActive || (maskMode && maskFocusRect != null)` ✓）→ 聚焦框 / 遮罩范围 ✓；
     *  ③ 图生图（`img2imgActive` ✓）→ 当前底图尺寸 ✓；
     *  ④ 否则文生图 → `params` 的宽高 ✓。
     */
    val runAnlasEstimate: Int?
        get() {
            if (!hasToken) return null
            if (canvasMode == 1) {
                val plan = infiniteFramePlan()?.second ?: return null
                return AnlasCost.estimate(
                    width = plan.requestW,
                    height = plan.requestH,
                    steps = params.steps,
                    model = settings.inpaintModel,
                    smea = params.smea,
                    smeaDyn = params.smeaDyn,
                    strength = settings.inpaintStrength,
                    isOpus = opusNow,
                    opusQuotaExhausted = opusQuotaExhaustedNow,
                ).takeIf { it != AnlasCost.INVALID }
            }
            val inpaint = maskActive || (maskMode && maskFocusRect != null)
            val w: Int
            val h: Int
            val model: String
            val strength: Double
            when {
                inpaint -> {
                    val plan = maskFocusPlan()
                    val (fw, fh) = maskFocusSize()
                    val computed = if (fw > 0 && fh > 0) {
                        com.kallan.naistudio.services.InpaintSize.forRect(
                            fw.toFloat(),
                            fh.toFloat(),
                        )
                    } else {
                        null
                    }
                    w = plan?.requestW ?: computed?.first ?: workImageWidth
                    h = plan?.requestH ?: computed?.second ?: workImageHeight
                    model = settings.inpaintModel
                    strength = settings.inpaintStrength
                }
                img2imgActive -> {
                    w = i2iDisplayWidth
                    h = i2iDisplayHeight
                    model = params.model
                    strength = settings.i2iStrength
                }
                else -> {
                    w = params.width
                    h = params.height
                    model = params.model
                    strength = 1.0
                }
            }
            if (w <= 0 || h <= 0) return null
            return AnlasCost.estimate(
                width = w,
                height = h,
                steps = params.steps,
                model = model,
                smea = params.smea,
                smeaDyn = params.smeaDyn,
                strength = strength,
                isOpus = opusNow,
                opusQuotaExhausted = opusQuotaExhaustedNow,
            ).takeIf { it != AnlasCost.INVALID }
        }

    /**
     * 运行条上那个按钮的入口：
     *  · 生成中 → 排队
     *  · 遮罩模式且有遮罩 → 「重做」（局部重绘）
     *  · 图生图模式（导入了图片）→ 「使用当前图片图生图」
     *  · 否则 → 文生图
     */
    fun generateOrInpaint() {
        // ⚠️ **无限画布那一档排在最前** ✓：这一档里"遮罩开没开 / 有没有导入图"很可能还留着
        // 上一次的旧值 ✓（用户是从普通模式切过来的 ✓），按它们分派会发出一次文生图 / 图生图 ✗ ——
        // 跟"按框拓展"半点关系都没有 ✗（点了"拓展生成"却重画一张整图，正是最难查的那类 bug ✓）。
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
        when {
            // 有聚焦框时，**框里没涂也算重绘**（官方：留空 = 整框重绘）—— 否则会掉到图生图
            maskActive || (maskMode && maskFocusRect != null) -> {
                // ⚠️ 第三方网关**没开局部重绘**（见 `gatewayAllows` 的说明）：先拦、给一句人话，
                //    别让用户等到网关回一个 4xx 才知道。
                if (gatewayAddressReady() && gatewayAllows("infill")) inpaintRegion()
            }
            img2imgActive -> {
                if (gatewayAddressReady() && gatewayAllows("img2img")) runImg2Img()
            }
            else -> if (gatewayAddressReady()) generate()
        }
    }

    // ----------------------------------------------------------- 图生图

    /** 导入一张图当图生图的底图（复制进 App 私有目录，之后按路径使用）。 */
    fun importImage(uri: Uri) {
        viewModelScope.launch {
            status = rt("status.importingImage")
            val saved = withContext(Dispatchers.IO) { copyImportedImage(uri) }
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
        val dims = MaskCodec.imageSize(path)
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
        maskVersion++
    }

    /** 把相册/文件里的图复制到私有目录（原样复制，后续都用 BitmapFactory 按内容解码）。 */
    private fun copyImportedImage(uri: Uri): String? = copyImageToPrivate(uri, "imported")

    /**
     * 把相册/文件里的图复制到私有目录的某个子目录（原样复制，不重编码）。
     *
     * @param subdir 子目录名，如 `imported`（图生图底图）、`reverse`（工具页反推用图）
     * @return 复制成功后的绝对路径；读不到或解不开则返回 null
     */
    private fun copyImageToPrivate(uri: Uri, subdir: String): String? = try {
        val context = getApplication<Application>()
        val dir = java.io.File(context.filesDir, subdir).apply { mkdirs() }
        val target = java.io.File(dir, "${System.currentTimeMillis()}.png")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied || MaskCodec.imageSize(target.absolutePath) == null) {
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
        val sourceDims = MaskCodec.imageSize(sourcePath)
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
                    val bitmap = MaskCodec.decodeFull(sourcePath)
                        ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                    val bytes = MaskCodec.basePng(bitmap, requestWidth, requestHeight)
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
                // 本次会话累计（见 `sessionAnlasSpent`）+ **每日统计**里那一笔实扣点数
                lastAnlasSpent?.let {
                    sessionAnlasSpent += it
                    recordUsage(images = 0, anlas = it, tagChars = 0)
                }
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
     * 请求口径照参考实现：纯 JSON + base64、`action="infill"`、尺寸按源图 64 对齐；
     * **优先走流式**（和文生图一样有"模糊→清晰"的渐进预览），服务端不支持时自动回落非流式。
     *
     * 注意这**是一次真实的付费生成**（Opus 正常尺寸走订阅额度，但别假设），完成后显示实扣。
     */
    // ------------------------------------------------- 聚焦重绘（Focused Inpainting）

    /** 聚焦工具是否选中（工具条上那颗「聚焦」）。 */
    var maskFocusTool by mutableStateOf(false)
        private set

    /** 用户拖出来的框（**归一化**，相对工作图）；null = 没框。 */
    var maskFocusRect by mutableStateOf<MaskFocusFrame?>(null)
        private set

    /** 框是否生效（工具选中 或 已经画了框）。 */
    val maskFocusActive: Boolean get() = maskFocusTool || maskFocusRect != null

    fun chooseMaskFocusTool(on: Boolean) {
        maskFocusTool = on
        if (!on) maskFocusRect = null
        status = if (on) rt("mask.focusOn") else rt("mask.focusOff")
    }

    fun updateMaskFocus(frame: MaskFocusFrame?) {
        maskFocusRect = frame?.takeIf { it.isValid }
    }

    fun clearMaskFocus() {
        maskFocusRect = null
    }

    /** 焦点框在**原图**里的像素尺寸（界面提示用）。 */
    fun maskFocusSize(): Pair<Int, Int> {
        val rect = maskFocusRect ?: return 0 to 0
        val px = rect.toPixels(workImageWidth, workImageHeight)
        return px.w to px.h
    }

    /** 算这次聚焦重绘的裁剪/请求方案（没框 / 尺寸未知 → null）。 */
    fun maskFocusPlan(): FocusedInpaint.Plan? {
        val rect = maskFocusRect ?: return null
        if (workImageWidth <= 0 || workImageHeight <= 0) return null
        // 归一化的框 → **原图像素**矩形 ✓（共享算法只认像素 ✓，见 `MaskFocusFrame` 的注释 ✓）
        val frame = rect.toPixels(workImageWidth, workImageHeight)
        if (frame.isEmpty) return null
        return FocusedInpaint.plan(
            frame = frame,
            contextPixels = settings.inpaintFocusContext,
            sourceWidth = workImageWidth,
            sourceHeight = workImageHeight,
            scale = settings.inpaintFocusScale,
            limitToFreeTier = settings.inpaintFocusFreeTierOnly,
            // 用户 2026-09-26：「聚焦修改的长宽**自动计算**」—— 请求尺寸由 `InpaintSize.forRect`
            // 按「等比 + 总面积 ≤1024×1024 + 64 倍数」自动算（宽高可超 1024，总像素不行 ✓）。
            // ⚠️ 这一条**电脑线早就改了** ✓，手机线这次跟着拉齐 ✓：旧口径按"每边 ≤1024"判免费档
            //    会把 `1792×576`（= 1.03MP）判成"要扣点数" ✗，正是用户报过的那个显示错误 ✓。
            autoSizeByRect = true,
        )
    }

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
        // 聚焦重绘时**框里可以是空的**（= 整框重绘），所以把聚焦框也算作"有内容"
        val focusPlan = maskFocusPlan()
        if ((mask == null || mask.isEmpty()) && focusPlan == null) {
            status = rt("mask.empty")
            return
        }
        // 提示词沿用正面提示词框：为空时与文生图同样拦下，避免发出没有提示词的请求
        if (params.positivePrompt.trim().isEmpty()) {
            status = rt("error.positiveRequired")
            return
        }

        val sourceDims = MaskCodec.imageSize(sourcePath)
        if (sourceDims == null) {
            status = rt("error.inpaintSourceMissing")
            return
        }
        // 尺寸取**源图**（64 对齐 + 面积上限），不套用生成页当前的宽高：重做的是这张图本身
        // 聚焦重绘：请求尺寸用**裁剪放大后**的尺寸（见 FocusedInpaint.plan）；否则按源图 64 对齐
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
                    val base = MaskCodec.decodeFull(sourcePath)
                        ?: throw IllegalStateException(rt("error.inpaintSourceMissing"))
                    // mask_expand：把遮罩膨胀几像素，接缝落在选区外面（也补偿后面吸附带来的收缩）
                    val expanded = mask?.let { MaskExpand.dilate(it, expandPixels) }
                    if (focusPlan != null) {
                        // 聚焦重绘：裁出「框 + 最小上下文」→ 放大到请求尺寸；
                        // 遮罩**只保留框内**（框里一点没涂 = 整框重绘，官方口径）
                        val cropped = android.graphics.Bitmap.createBitmap(
                            base,
                            focusPlan.cropX,
                            focusPlan.cropY,
                            focusPlan.cropW,
                            focusPlan.cropH,
                        )
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            cropped, requestWidth, requestHeight, true,
                        )
                        val basePng = MaskCodec.basePng(scaled, requestWidth, requestHeight)
                        cropped.recycle()
                        scaled.recycle()
                        base.recycle()
                        val focusMask = FocusedInpaint.maskIntoRequest(
                            // ⚠️ 框里一点没涂时 `expanded` 是 **null** ✓（`mask?.let { … }` ✓）——
                            // 共享的 `maskIntoRequest` 要的是**非空**蒙版 ✗ ⇒ 给一张空蒙版 ✓：
                            // 空蒙版 + `fillWholeFrame = true` 正是官方那句"框里留空 = 整框重绘" ✓
                            fullMask = expanded
                                ?: PixelMask(base.width, base.height, ByteArray(base.width * base.height)),
                            plan = focusPlan,
                            fillWholeFrame = true,
                        ) ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
                        // 尺寸已经是请求尺寸 → inpaintMask 只做"潜空间网格吸附"那一步
                        val requestMask = MaskCodec.inpaintMask(focusMask, requestWidth, requestHeight)
                            ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
                        PreparedInpaint(
                            basePng = basePng,
                            maskPng = MaskCodec.maskPng(requestMask, requestWidth, requestHeight),
                            requestMask = requestMask,
                        )
                    } else {
                        val basePng = MaskCodec.basePng(base, requestWidth, requestHeight)
                        base.recycle()
                        // 吸附 8px 潜空间网格（节点口径）：不吸附的话服务端那边的重绘边界会和画的线错开，
                        // 表现就是"涂区外圈留一圈半重绘的带"
                        val requestMask = MaskCodec.inpaintMask(
                            expanded ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall")),
                            requestWidth,
                            requestHeight,
                        ) ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
                        PreparedInpaint(
                            basePng = basePng,
                            maskPng = MaskCodec.maskPng(requestMask, requestWidth, requestHeight),
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
                // ⚠️ 聚焦重绘**必须**回贴（用户 2026-09-17 反馈"输出图片也是截出来的图，没有外围的图了"）：
                // 产物只是"裁剪+放大"过的一块，不回贴就等于把整张图换成了那一块。
                // 所以这里与 `add_original_image` 开关无关 —— 聚焦模式下强制回贴。
                val needsPaste = pasteBack || focusPlan != null
                var focusPasteFailed = false
                val finalImages = if (needsPaste) {
                    withContext(Dispatchers.Default) {
                        // 聚焦重绘：产物是"裁剪放大"过的一块 → **只把框内贴回源图**
                        //（框外含上下文那圈一个像素都不动）
                        val sourceBytes = if (focusPlan != null) {
                            runCatching { java.io.File(sourcePath).readBytes() }.getOrNull()
                        } else {
                            null
                        }
                        images.map { bytes ->
                            runCatching {
                                if (focusPlan != null) {
                                    val src = sourceBytes
                                        ?: throw IllegalStateException("聚焦回贴：读不到源图")
                                    FocusPaste.pasteFrame(
                                        sourcePng = src,
                                        generatedPng = bytes,
                                        plan = focusPlan,
                                        feather = feather,
                                    ) ?: throw IllegalStateException("聚焦回贴失败")
                                } else {
                                    InpaintComposite.composePng(
                                        generatedPng = bytes,
                                        originalPng = prepared.basePng,
                                        mask = prepared.requestMask,
                                        feather = feather,
                                        edgeProtection = edgeProtection,
                                    ) ?: bytes
                                }
                            }.getOrElse { error ->
                                if (focusPlan != null) {
                                    focusPasteFailed = true
                                    logWarn("Inpaint", "focus paste-back failed: ${error.message}")
                                }
                                bytes
                            }
                        }
                    }
                } else {
                    images
                }

                persistGeneratedImages(
                    images = finalImages,
                    params = taskParams,
                    seed = seed,
                    feature = "inpaint",
                    model = usedModel,
                    // 聚焦重绘的产物是**源图尺寸**（只把框内贴回），所以这里报源图尺寸
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
                // 本次会话累计（见 `sessionAnlasSpent`）+ **每日统计**里那一笔实扣点数
                lastAnlasSpent?.let {
                    sessionAnlasSpent += it
                    recordUsage(images = 0, anlas = it, tagChars = 0)
                }
                val note = if (usedModel != inpaintModel) {
                    rf("status.inpaintFallback", mapOf("model" to usedModel))
                } else {
                    ""
                }
                val focusNote = focusPlan?.let {
                    " · 聚焦 ${it.cropW}×${it.cropH}→${it.requestW}×${it.requestH}" +
                        if (it.inFreeTier) "（免费档）" else "" +
                        if (focusPasteFailed) " · ⚠️ 回贴失败（只拿到裁剪块）" else ""
                }.orEmpty()
                status = rf(
                    "status.inpaintDone",
                    mapOf("note" to (note + focusNote), "spent" to spentText(lastAnlasSpent)),
                )
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
        // 历史插到最前面，与参考实现一致（索引 0 = 最新）
        history = saved + history
        current = saved.first()
        // 新图不是放大版了 —— 清掉"放大前那张"的记录（否则会拿旧图当工作图）
        upscaleSource = null
        // 本次会话的临时图库：只存在内存里，进程被杀（后台关掉软件）就没了
        sessionImages.addAll(0, saved)
        if (durationMs != null) {
            // **这一批每张都记**：同一次请求产出的图是一起回来的，耗时相同；
            // 早前只给 firstOrNull() 记，批量时只有第一张有角标（用户反馈"有的图没有时间"）。
            saved.forEach { sessionImageDurationMs[it.id] = durationMs }
        }
        // **每日统计**（用户 2026-09-26：「参照电脑的侧边栏统计」）——
        // 图数 / tag 字数记在**落库这一刻**（这里才知道真存了几张），
        // 点数要到各条流程**收尾**（刷新账号之后）才补一笔，见 `recordUsage` 的说明。
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
    // 第 ㊿k/㊿l 批（2026-09-22）：**无限画布**（手机线 ✓，方案 `docs/69`、落地 `docs/71`、`docs/72` ✓）
    //
    // 口径（用户原话）：「生图后可自选方向拓展空白画布，通过聚焦生成无限拓展；聚焦生成可在画布上
    // 随意画框，**请求尺寸限制在最大免费档上限内**，**自动放大倍率（不得靠缩小倍率牺牲画质）**」。
    //
    // 手机线与电脑线的差别**只有平台那一层** ✓：几何、方案、撤销栈都是共享的纯逻辑
    //（`InfiniteCanvas` / `InfiniteCanvasHistory` / `FocusedInpaint` / `InpaintSize` ✓）；
    // 位图这边用 **Android Bitmap**（手机线还没有 `platform.ImageIo` 那层抽象 ✓，见 `inpaintRegion` 同理 ✓）。
    // ==================================================================

    /** 画布模式：0 = 普通 / 1 = 无限画布 / 2 = 漫画 ✓（跟着 `prefs.json` 走 ✓，见 `docs/72` ✓）。 */
    var canvasMode by mutableStateOf(0)
        private set

    /** 切档 ✓；**离开无限画布就把位图收掉** ✓（着一张 3072² 的 ARGB 就是 37 MB ✓，不放手不行 ✓）。 */
    fun chooseCanvasMode(mode: Int) {
        if (canvasMode == mode) return
        if (mode == 2) {
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
        canvasMode = mode
        // Comic mode uses storyboard panels, never the focus redraw canvas.
        setComicMode(mode == 2)
        if (mode != 1) closeInfiniteCanvas()
        persistInfiniteState()
    }

    /** 画布几何 ✓（`null` = 还没开这一档 ✓）。 */
    var infiniteCanvas by mutableStateOf<InfiniteCanvas?>(null)
        private set

    /** 这一档**正在忙**（跑一次聚焦生成 / 撤销重做 ✓）—— 界面据此显示进度与禁用那两颗按钮 ✓。 */
    var infiniteRunning by mutableStateOf(false)
        private set

    /** 画布像素（`width * height` 的 ARGB ✓）。 */
    private var infinitePixels: IntArray = IntArray(0)

    /** 画布内容的**版本号**（开画布 / 拓展 / 贴回 / 撤销都 +1 ✓）—— 显示那张位图据此作废 ✓。 */
    private var infiniteRevision by mutableIntStateOf(0)
    private var infiniteImageCache: ImageBitmap? = null
    private var infiniteImageBuilt = Int.MIN_VALUE

    /**
     * 画布那张**显示用位图** ✓（按 [infiniteRevision] 缓存 ✓）。
     *
     * ⚠️ **必须缓存** ✗：不缓存的话每次重组都要把整张 ARGB 转一遍位图（3072² 就是 37 MB ✓），
     * 界面当场卡死 ✗ —— 这正是用户在电脑线上报过的"卡飞了"那一类坑（`docs/68` §四 ✓）。
     */
    fun infiniteCanvasImage(): ImageBitmap? {
        val canvas = infiniteCanvas ?: return null
        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0 || infinitePixels.size < w * h) return null
        infiniteImageCache?.let { if (infiniteImageBuilt == infiniteRevision) return it }
        val built = runCatching {
            Bitmap.createBitmap(infinitePixels.copyOf(w * h), w, h, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }.getOrNull()
        infiniteImageCache = built
        infiniteImageBuilt = infiniteRevision
        return built
    }

    /** 只读快照（界面画它 / 单测读它 ✓）—— 别拿去改 ✗。 */
    fun infiniteCanvasPixels(): IntArray = infinitePixels

    /**
     * **聚焦框**（**文档坐标** ✓）—— 用户口径：「给一个可以调大小的框，用户想要拓展就将框移到画像外
     * 想要拓展的部分」✓。⚠️ 用文档坐标而不是画布像素 ✗✗：框**允许整个在画布外** ✓（那正是"往那边
     * 拓展"的表达 ✓），换成画布像素就会被边界夹住 ✗。
     */
    var infiniteFrame by mutableStateOf<EditRect?>(null)
        private set

    fun updateInfiniteFrame(rect: EditRect?) {
        infiniteFrame = rect
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

    /** 撤销 / 重做栈 ✓（共享纯逻辑 + 电脑线单测 ✓）。⚠️ **不跨进程** ✗。 */
    private val infiniteHistory = InfiniteCanvasHistory()

    /** 那两颗按钮的可用状态 ✓（栈里有货才亮 ✓ —— 亮着点了没反应最烦 ✗）。 */
    var infiniteCanUndo by mutableStateOf(false)
        private set
    var infiniteCanRedo by mutableStateOf(false)
        private set

    /** 画布**存盘那一份快照**的路径 ✓ 与"这张画布是从哪张工作图开的" ✓（判据见 `docs/72` §三 ✓）。 */
    private var infiniteCanvasFile: String? = null
    private var infiniteCanvasSource: String? = null
    /** 冷启动第一次进入无限画布时，盘上的快照比最近图库条目更可靠。 */
    private var infiniteRestorePending = false

    /** **用一张刚出来的图开画布** ✓（超过平台上限就如实夹取 ✓ —— 夹取只裁不多，绝不"先缩再放大" ✗）。 */
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

    /** 关掉这一档 ✓（⚠️ 盘上那份快照**留着** ✓，切回来还能接着用 ✓，见 `docs/72` ✓）。 */
    fun closeInfiniteCanvas() {
        infiniteCanvas = null
        infinitePixels = IntArray(0)
        infiniteImageCache = null
        infiniteImageBuilt = Int.MIN_VALUE
        infiniteRevision++
    }

    /**
     * **进这一档时把画布铺好** ✓（幂等 ✓）。顺序（`docs/72` §三 的判据表 ✓）：
     *  · 快照还在、且**中间没生成过新图**（或工作图还没读出来 ✓ 启动竞态护栏 ✓）⇒ 接着用 ✓；
     *  · 否则以**现在的工作图**为准重开 ✓（老快照删掉 ✗ 别占盘 ✓）；
     *  · 还没有图 ⇒ 给一张 1024² 的**空白**（透明 ✓）—— 接着就能框着生成 ✓。
     */
    fun ensureInfiniteCanvas() {
        if (infiniteCanvas != null) return
        val snapshot = infiniteCanvasFile
        val restoreSavedCanvas = infiniteRestorePending
        infiniteRestorePending = false
        if (snapshot != null && (restoreSavedCanvas || workImagePath == null || infiniteCanvasSource == workImagePath)) {
            if (loadInfiniteCanvasFile(snapshot)) return
        }
        clearInfiniteSnapshot()
        val path = workImagePath
        if (path != null) {
            val decoded = MaskCodec.decodeFull(path)
            if (decoded != null) {
                val w = decoded.width
                val h = decoded.height
                val pixels = IntArray(w * h)
                decoded.getPixels(pixels, 0, w, 0, 0, w, h)
                decoded.recycle()
                if (w > 0 && h > 0) {
                    resetInfiniteCanvas(pixels, w, h)
                    infiniteCanvasSource = path
                    return
                }
            }
        }
        val side = 1024
        resetInfiniteCanvas(IntArray(side * side), side, side)
        infiniteCanvasSource = null
    }

    /**
     * **这一次会请求多大** ✓（界面那行提示与真正发出去的请求**同一个函数** ✓ ——
     * 界面写一个数、请求发另一个数是最伤人的不一致 ✗）。
     *
     * 内部先把画布"在心里"扩到装得下这个框 ✓（框通常整个在画布外 ✓，直接喂给 `FocusedInpaint.plan`
     * 会被夹成空矩形 ✗），再交给 `InpaintSize.forRect` **自动算尺寸**：
     * 等比 + **总面积 ≤1024×1024 = 1MP** + 两边保 64 ✓（小框自动放大 ✓ 大框才缩 ✓）。
     */
    fun infiniteFramePlan(contextPixels: Int = settings.infiniteContext): Pair<InfiniteCanvas, FocusedInpaint.Plan>? {
        val canvas = infiniteCanvas ?: return null
        val frame = infiniteFrame ?: return null
        val grown = canvas.expandedToInclude(
            frame.x.toFloat(),
            frame.y.toFloat(),
            frame.w.toFloat(),
            frame.h.toFloat(),
        ) ?: return null
        // ⚠️ 换算必须用 **grown** 的 `pixelX/pixelY` ✗：往左 / 上长过之后原点变了 ✓
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
        val context = contextPixels.coerceIn(0, 512)
        val local = FocusedInpaint.plan(
            frame = EditRect(context, context, framePx.w, framePx.h),
            contextPixels = context,
            sourceWidth = framePx.w + context * 2,
            sourceHeight = framePx.h + context * 2,
            autoSizeByRect = true,
        ) ?: return null
        return grown to local.copy(cropX = framePx.x - context, cropY = framePx.y - context)
    }

    /** 界面上那一行"将请求 …"（没框 / 算不出 ⇒ null ✓）。 */
    fun infinitePlanText(contextPixels: Int = settings.infiniteContext): String? {
        val plan = infiniteFramePlan(contextPixels)?.second ?: return null
        return rf("infinite.planInfo", mapOf("w" to plan.requestW, "h" to plan.requestH))
    }

    /** 框拖完了 ⇒ 落一次盘 ✓（拖动过程中每帧写盘是拿磁盘换流畅 ✗，与漫画同一个口径 ✓）。 */
    fun commitInfiniteCanvas() {
        if (canvasMode != 1) return
        persistInfiniteState()
    }

    /** 启动时把上次那一档接着摆回来 ✓（**只摆记账、不读像素** ✗ —— 几十 MB 的解码等真进这一档再做 ✓）。 */
    private fun restoreInfiniteCanvasState() {
        canvasMode = settings.canvasMode
        infiniteCanvasFile = settings.infiniteCanvasSnapshot.takeIf { it.isNotBlank() }
        infiniteCanvasSource = settings.infiniteCanvasSource.takeIf { it.isNotBlank() }
        infiniteRestorePending = canvasMode == 1 && infiniteCanvasFile != null
        val w = settings.infiniteFrameW
        val h = settings.infiniteFrameH
        infiniteFrame = if (w > 0 && h > 0) {
            capInfiniteFrameArea(EditRect(settings.infiniteFrameX, settings.infiniteFrameY, w, h))
        } else {
            null
        }
        infiniteHistory.clear()
        refreshInfiniteHistoryFlags()
    }

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

    /** 快照 PNG 旁边那份"原点"小文件 ✓（往左 / 上长过的画布原点不是 0 ✓，不一起记就整体错位 ✗）。 */
    private fun infiniteOriginFile(pngPath: String): File =
        File(pngPath.removeSuffix(".png") + ".origin")

    private fun readInfiniteOrigin(pngPath: String): Pair<Int, Int>? = runCatching {
        val parts = File(infiniteOriginFile(pngPath).absolutePath).readText().trim().split(',')
        if (parts.size != 2) null else parts[0].trim().toInt() to parts[1].trim().toInt()
    }.getOrNull()

    /** 只**解码**一张快照 ✓（纯 CPU + 读盘 ✓，不写状态 ✗ ⇒ 可以在 `Dispatchers.Default` 上调 ✓）。 */
    private fun decodeInfiniteSnapshot(path: String): DecodedInfiniteCanvas? {
        val decoded = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val w = decoded.width
        val h = decoded.height
        if (w <= 0 || h <= 0 || w > platform.infiniteCanvasMaxSide ||
            h > platform.infiniteCanvasMaxSide
        ) {
            decoded.recycle()
            return null
        }
        val pixels = IntArray(w * h)
        decoded.getPixels(pixels, 0, w, 0, 0, w, h)
        decoded.recycle()
        val origin = readInfiniteOrigin(path)
        return DecodedInfiniteCanvas(
            pixels = pixels,
            width = w,
            height = h,
            originX = origin?.first ?: 0,
            originY = origin?.second ?: 0,
        )
    }

    /**
     * 把解码好的画布摆上去 ✓（主线程 ✓）。
     * ⚠️ 直接摆原点 ✗ **不能**走 `resetInfiniteCanvas`（那个把原点当 0 ✓，拿它还原往左 / 上长过的画布
     * 会把文档坐标整体挪位 ✗ —— 框和画就对不上了 ✗）。
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

    private class DecodedInfiniteCanvas(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
    )

    private fun loadInfiniteCanvasFile(path: String): Boolean {
        val decoded = decodeInfiniteSnapshot(path) ?: return false
        applyDecodedCanvas(decoded)
        return true
    }

    /** **把整张画布编成 PNG 存盘** ✓（`filesDir/infinite/canvas-<时间戳>.png` ✓，返回路径 ✓）。
     *  ⚠️ 纯 CPU ⇒ 调用方一定在 `Dispatchers.Default` 上调 ✓（3072² 编码要一秒级 ✓）。 */
    private fun encodeInfiniteCanvas(canvas: InfiniteCanvas, pixels: IntArray): String? {
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(pixels.copyOf(width * height), width, height, Bitmap.Config.ARGB_8888)
            val dir = File(platform.paths.filesDir, "infinite").apply { if (!exists()) mkdirs() }
            val stamp = System.currentTimeMillis()
            val target = File(dir, "canvas-$stamp.png")
            java.io.FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            // ⚠️ **原点必须一起存** ✗（理由见 `infiniteCanvasFile` ✓）
            File(dir, "canvas-$stamp.origin").writeText("${canvas.originX},${canvas.originY}")
            target.absolutePath
        }.getOrNull()
    }

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

    private fun deleteInfiniteSnapshots(entries: List<InfiniteCanvasHistory.Entry>) {
        entries.forEach { deleteInfiniteSnapshot(it.path) }
    }

    private fun clearInfiniteSnapshot() {
        deleteInfiniteSnapshots(infiniteHistory.clear())
        deleteInfiniteSnapshot(infiniteCanvasFile)
        refreshInfiniteHistoryFlags()
        infiniteCanvasFile = null
        infiniteCanvasSource = null
    }

    private fun refreshInfiniteHistoryFlags() {
        infiniteCanUndo = infiniteHistory.canUndo
        infiniteCanRedo = infiniteHistory.canRedo
    }

    /** **撤销上一步拓展** ✓（一次拓展是**花钱 + 破坏性**的 ✓ ⇒ 撤销是这一档能不能放心用的分界线 ✓）。 */
    fun undoInfiniteCanvas() = swapInfiniteHistory(undo = true)

    fun redoInfiniteCanvas() = swapInfiniteHistory(undo = false)

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
                // 读不出来 ⇒ 把栈**原样倒回去** ✓ 再如实报一句 ✗（不然栈和画布就对不上了 ✗）
                if (undo) infiniteHistory.redo(swap.restore) else infiniteHistory.undo(swap.restore)
                refreshInfiniteHistoryFlags()
                toast(rt(if (undo) "infinite.undoFailed" else "infinite.redoFailed"), isError = true)
            } else {
                applyDecodedCanvas(decoded)
                infiniteCanvasFile = swap.restore.path
                deleteInfiniteSnapshots(swap.evicted)
                persistInfiniteState()
                status = rt(if (undo) "infinite.undoDone" else "infinite.redoDone")
            }
            infiniteRunning = false
        }
    }

    /**
     * **跑一次聚焦生成** ✓（这一档的主按钮走这里 ✓，完整链路见 `docs/71` §一 ✓）：
     * 扩画布（老像素整块搬 ✓ 不重采样 ⇒ 画质不降 ✓）→ 算方案 → 裁底图 + 整框蒙版 →
     * `api.inpaint` → 落库 → **只把框那一块贴回画布** ✓（框外留透明 ⇒ 就是那块"凸出一块" ✓）。
     *
     * ⚠️ 用**自己的** `infiniteRunning` ✗ 不占全局 `busy` ✓：占了的话主按钮再点会走"排队"，
     * 而那条路排的是**文生图** ✗（跟框半点关系没有 ✗）。
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

    private suspend fun generateInfiniteFrame(start: InfiniteCanvas, frameDoc: EditRect): String {
        // ---- ⓪ "上一步"的快照（撤销要回到的就是它 ✓，必须在**扩画布之前**备好 ✓）----
        // 盘上那张 == 当前画布 ✓（画布只在"贴回 / 撤销 / 重开"时变 ✓）⇒ 直接沿用省一次编码 ✓；
        // 还没有快照（第一次拓展 / 刚从工作图重开 ✓）才现编一张 ✓。
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

        // ---- ② 方案（和界面那一行**同一处算** ✓）----
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

        // ---- ③ 裁剪 + 蒙版（位图活儿放后台 ✓，与「重做」同一套操作 ✓）----
        val prepared = withContext(Dispatchers.Default) {
            val crop = IntArray(plan.cropW * plan.cropH)
            // 框的范围**从 `plan.frameInCrop` 反推** ✓（不再自己换算一次 ✓，见 `InfiniteCanvas` 的"唯一一处"✓）
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
                    // ⚠️ 透明像素铺**白**再送 ✗：服务端对"全透明底图"没有定义（实测出灰噪 ✓）。
                    //    白底 = 这张画布上"还没画"那块的替身 ✓（贴回来的只有框那一块 ✓，白不会留在画布上 ✓）
                    if ((argb ushr 24) != 0) {
                        crop[dstRow + col] = argb
                        if (gx >= frameLeft && gx < frameRight && gy >= frameTop && gy < frameBottom) {
                            drawnInFrame++
                        }
                    } else {
                        crop[dstRow + col] = 0xFFFFFFFF.toInt()
                    }
                }
            }
            val cropBitmap = Bitmap.createBitmap(crop, plan.cropW, plan.cropH, Bitmap.Config.ARGB_8888)
            val basePng = MaskCodec.basePng(cropBitmap, plan.requestW, plan.requestH)
            cropBitmap.recycle()
            // 本批还没有"框里再涂一小块"的界面 ⇒ 交给现成的 `maskIntoRequest(fillWholeFrame = true)`
            //（官方"框里留空 = 整框重绘" ✓，一行新算法都不加 ✓）
            val emptyMask = PixelMask(plan.cropW, plan.cropH, ByteArray(plan.cropW * plan.cropH))
            val focusMask = FocusedInpaint.maskIntoRequest(emptyMask, plan, fillWholeFrame = true)
                ?: throw IllegalStateException(rt("mask.empty"))
            val requestMask = MaskCodec.inpaintMask(focusMask, plan.requestW, plan.requestH)
                ?: throw IllegalStateException(rt("error.inpaintMaskTooSmall"))
            PreparedInfiniteFrame(
                basePng = basePng,
                maskPng = MaskCodec.maskPng(requestMask, plan.requestW, plan.requestH),
                drawnInFrame = drawnInFrame,
            )
        }

        // 强度：框里**一点画都没有**（往空白处拓展 ✓）⇒ 全重绘 1.0 ✓（留着 0.7 会把白底透出来，
        // 看着就是"灰蒙蒙一片没画" ✗）；框压到了已有画面 ⇒ 按用户设的强度 ✓，保住笔触 ✓。
        val strength = if (prepared.drawnInFrame == 0) 1.0 else settings.inpaintStrength
        val noise = settings.inpaintNoise
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

        // ---- ⑤ 先贴回画布，再把整张合成结果存入图库 ----
        // 返回图只是裁剪请求的结果；把它先落库会让保存、普通预览和重启恢复都指向那一小块。
        val snapshotPath = withContext(Dispatchers.Default) {
            val decoded = runCatching {
                BitmapFactory.decodeByteArray(returned.first(), 0, returned.first().size)
            }.getOrNull() ?: return@withContext null
            val scaled = if (decoded.width == plan.cropW && decoded.height == plan.cropH) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, plan.cropW, plan.cropH, true)
            }
            val pixels = IntArray(plan.cropW * plan.cropH)
            scaled.getPixels(pixels, 0, plan.cropW, 0, 0, plan.cropW, plan.cropH)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            val touched = FocusedInpaint.pasteBack(
                canvas = infinitePixels,
                sourceWidth = canvas.width,
                sourceHeight = canvas.height,
                generated = pixels,
                plan = plan,
            ) != null
            if (!touched) return@withContext null
            // ⚠️ 快照必须是**贴回之后**的像素（`pasteBack` 就地改的 ⇒ 这时拍正好 ✓）
            encodeInfiniteCanvas(canvas, infinitePixels)
        }
        infiniteRevision++ // Compose 立即看到贴回后的像素，不必切换模式触发重组。
        if (snapshotPath == null) throw IllegalStateException(rt("infinite.pasteFailed"))
        val beforeEntry = beforePath?.let {
            InfiniteCanvasHistory.Entry(it, start.originX, start.originY)
        }
        // 老快照若被撤销栈收下，不能删。
        if (beforeEntry == null || previousPath != beforeEntry.path) {
            deleteInfiniteSnapshot(previousPath)
        }
        if (beforeEntry != null) {
            deleteInfiniteSnapshots(infiniteHistory.commit(beforeEntry))
        }
        refreshInfiniteHistoryFlags()
        infiniteCanvasFile = snapshotPath
        val saved = try {
            val compositePng = withContext(Dispatchers.IO) { File(snapshotPath).readBytes() }
            persistGeneratedImages(
                images = listOf(compositePng),
                params = taskParams,
                seed = seed,
                feature = "infinite",
                model = usedModel,
                width = canvas.width,
                height = canvas.height,
                durationMs = System.currentTimeMillis() - genStart,
            )
        } catch (error: Exception) {
            // 图库写入失败时也保住已贴回的画布；正常路径只写一次设置，避免两次异步写盘乱序。
            infiniteCanvasSource = workImagePath
            persistInfiniteState()
            throw error
        }

        val path = saved.firstOrNull()?.filePath
            ?: throw IllegalStateException(rt("error.inpaintNoImages"))
        infiniteCanvasSource = path
        persistInfiniteState()
        // 状态行 + 明细：**不静默**地记下这次请求了多大、画布长到多大 ✓（日志抽屉可展开 ✓）
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
            },
        )
        return path
    }

    /** 跑一次聚焦生成要用到的几块（底图裁剪块、遮罩 PNG、框里已画像素数 ✓）。 */
    private class PreparedInfiniteFrame(
        val basePng: ByteArray,
        val maskPng: ByteArray,
        val drawnInFrame: Int,
    )

    // ---------------------------------------------------------------- 其它

    fun errorText(message: String) { status = message }

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

