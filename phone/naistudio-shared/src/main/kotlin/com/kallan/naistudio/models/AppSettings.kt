package com.kallan.naistudio.models

import com.kallan.naistudio.services.PromptRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * 应用设置。
 *
 * **行为对齐参考实现** `mobile/lib/models/nai_models.dart` 的 `AppSettings`：
 * 字段名、默认值、fromJson 的容错方式（缺键取默认、未知键忽略、越界值夹取）都以其为准。
 *
 * 说明：参考实现里还有 agent / tavern / comic / 在线画廊 / 备份 等大批字段，属于本项目
 * **当前范围之外**的功能，这里先不声明（我们有自己的存储命名空间，不存在数据互通问题）。
 * 后续实现对应功能时再按参考实现的名字和默认值补上。
 */

/**
 * 一次 LLM 调用要用的那套配置。见 [AppSettings.llmConfig]。
 *
 * 和 [com.kallan.naistudio.services.LlmApi.chat] 的三个入参一一对应，
 * 单独抽出来是为了让"取哪一套"这件事变成**可单测的纯函数**。
 */
data class LlmConfig(
    val url: String,
    val key: String,
    val model: String,
    /**
     * 这一套是不是**分功能 AI**（三项里至少填了一项），而不是主 API。
     *
     * 用途只有一个：**「思考程度」只对主 API 生效**（用户 2026-09-16 要求）。
     * `reasoning_effort` 是 OpenAI o 系列带起来的字段，大量中转 / 小模型不认识、
     * 有的直接报 400；分功能 AI 常常是"换一家跑视觉模型"这种场景，
     * 把主 API 的思考档位硬塞过去只会出事。温度 / Top-P 是通用采样参数，两边都发。
     */
    val overridden: Boolean = false,
) {
    /** 地址和密钥都填了才算配好（模型名有默认值，不算门槛）。 */
    val configured: Boolean get() = url.isNotEmpty() && key.isNotEmpty()
}

data class AppSettings(
    val apiBaseUrl: String = "https://api.novelai.net",
    val imageBaseUrl: String = "https://image.novelai.net",
    val allowCustomEndpoint: Boolean = false,

    /**
     * **接口来源 = 第三方/自有网关**（用户 2026-09-26：
     * https://github.com/fangchen2003/service-tools 那个 NAI Gate）。
     *
     * ## 为什么它必须是一个**独立存的开关**，而不是"从地址推出来"
     *
     * 一开始我图省事，用"地址 != 官方默认地址"当判据 —— **那是错的**：
     * 用户点「第三方」时地址是空的（等他去填自己的站点），而"空"按那条判据算**官方** ⇒
     * 开关刚点亮就立刻熄了，界面根本没反应（真机上就是这么表现的：
     * 点「第三方」两个字、点那颗小圆圈，**都没反应**）。
     *
     * 所以这里**显式记一个模式**：点了「第三方」就置 true，点了「官方」就置 false。
     * 地址填没填、填的是什么，都只影响"连不连得上"，不再影响"现在是哪种模式"。
     * [imageBaseUrl] 仍然是真正发请求用的地址（空 ⇒ 回落官方，见 `NaiApi.normalizeBase`）。
     */
    val useThirdPartyApi: Boolean = false,

    /**
     * **第三方网关的站点地址**（用户 2026-09-26：「第三方和官方的 api **分开存储**啊，可切换，
     * 第三方**也不使用官方 api** 啊」）。
     *
     * 和官方的 [imageBaseUrl] **各存各的**：来回切的时候两边都留着，
     * 不会"切过去把官方地址冲没了、切回来又得重填"。
     *
     * ⚠️ **它为空时绝不允许回落到官方地址**：`NaiApi.normalizeBase` 的兜底是官方默认值，
     * 那条兜底是给官方模式用的；网关模式下地址为空 ⇒ [thirdPartyConfigured] 为 false ⇒
     * 直接拦住并提示"请先填站点地址"，**不会**拿虚拟 Key 去打 NovelAI 官方。
     */
    val thirdPartyBaseUrl: String = "",

    /**
     * **每日使用统计**（用户 2026-09-26：「删除统计，参照电脑的侧边栏统计」——
     * 即把电脑那套统计整个搬到手机上来）。
     *
     * 序列化成一小段文本（见 `UsageStats.encode`）：
     * `"2026-09-23:12,345,678;…"`，存在设置里，**跟着设置一起进备份**。
     */
    val usageStats: String = "",

    /**
     * **余额快照**（用户 2026-09-26：桌面小组件要显示余额）。
     *
     * ## 为什么必须落盘
     *
     * 余额本来是**纯内存态**（`AppState.account` 是 `mutableStateOf`）。而桌面小组件跑在
     * **`com.android.systemui` 进程**里，**不是本 App 进程** ⇒ 它读不到那个内存值。
     * 想让它显示余额，只能把最后一次已知的余额**写进 prefs**（小组件按同一个
     * `nai_store` / `app_settings` 键去读）。
     *
     * ## 它是"快照"不是"实时"
     *
     * 小组件**不联网、不刷新**（平台也不给这个机会）⇒ 它显示的永远是
     * "**App 最后一次刷新到的余额**"。所以同时存 [balanceSnapshotAt]（毫秒时间戳），
     * 大那个尺寸的小组件会把时间一并显示出来 —— 让人一眼看出这是几点的数，而不是实时数。
     *
     * ⚠️ `null` = 从来没拿到过余额（没配 token / 一次都没刷新成功）⇒
     *    小组件显示"—"，**不编一个 0 出来**（0 会被读成"余额用光了"）。
     */
    val balanceSnapshot: Int? = null,

    /** [balanceSnapshot] 是什么时候拿到的（毫秒时间戳，0 = 没有快照）。 */
    val balanceSnapshotAt: Long = 0L,

    /**
     * **会员档名快照**（用户 2026-09-26：「1*2 的余额做成这个样式，带进度条」——
     * 参考图里那颗绿框小徽章写的就是档名 `Paper`）。
     *
     * 同 [balanceSnapshot]：小组件跑在启动器进程、读不到 `AppState.account`，
     * 所以档名也得落盘。空串 = 没快照（小组件干脆不画那颗徽章）。
     */
    val tierSnapshot: String = "",

    /**
     * **V5 免费额度剩余百分比快照**（0..100；`null` = 不适用，小组件不画进度条）。
     *
     * ⚠️ 口径**和 App 里胶囊上那个 `V5 N%` 逐字一致**（`AccountChip`）：
     *    只有 **Opus 档（tierLevel == 3）+ V5 模型**才有这个数，其余档位一律不写。
     *    这样小组件和 App 顶栏不会出现"字 62%、条 30%"这种打架的画面。
     */
    val v5PercentSnapshot: Int? = null,

    // ---- 自建账号体系（见 docs/05-方案-账号体系与接口契约.md）----
    /** 账号服务器基址（auth / sync / 生图代理共用；空 = 未配置）。 */
    val accountServerUrl: String = "",
    /**
     * 账号模式（托管）：
     *  · true  = 登录门禁 + 生图走服务器代理 + 服务端额度（accessToken 鉴权）
     *  · false = 老的“自填 NovelAI token 直连”回退（默认，未配置服务器时保持可用）
     */
    val hostedMode: Boolean = false,

    val language: String = "zh-CN",
    /** "system" / "light" / "dark"。 */
    val theme: String = "system",
    /** 配色（与深/浅色正交）："classic" = 经典画廊（默认），"bluewhite" = 蓝白。 */
    val palette: String = "classic",

    // -----------------------------------------------------------------------
    // 自定义调色（设置页「调色板」，用户 2026-09-16 要求）
    //   · 四档可调：主色 / 胶囊浅色（primaryContainer）/ 页面底色 / 文字色；
    //   · **深色、浅色各存一套**（用户口径：在哪个模式下就调哪一套）；
    //   · 空串 = 这一档没自定义，用主题默认 —— 四项都空时**一个色都不动**，
    //     默认观感与加这个功能之前完全一致；
    //   · 值用 `"#RRGGBB"` 存（人可读、备份里一眼能看懂、org.json 不用管可空数字）。
    // 其余色槽（卡片/面板/描边/次要文字…）由 `ui/Palette.kt` 按底色自动派生，
    // 不做成可调 —— 22 个槽全放开，实际只会调出看不清的配色。
    // -----------------------------------------------------------------------
    val colorPrimaryLight: String = "",
    val colorPrimaryDark: String = "",
    val colorContainerLight: String = "",
    val colorContainerDark: String = "",
    // 1.1.121 起：Reference 调色板的第 3 个基色改成「卡片色」（panel）；上面 container 两项保留只为兼容旧备份，不再生效。
    val colorPanelLight: String = "",
    val colorPanelDark: String = "",
    val colorBackgroundLight: String = "",
    val colorBackgroundDark: String = "",
    val colorTextLight: String = "",
    val colorTextDark: String = "",

    // `navigationMode` 已删除：底栏取消后导航只剩左侧边栏一种形态，留着只会是死配置
    /** "anime" / "furry" / "background"。 */
    val modelMode: String = "anime",

    /** false 时保存前剥掉 PNG 的 tEXt/iTXt/zTXt/eXIf 块。 */
    val keepImageMetadata: Boolean = true,
    /** 保存后是否再往系统相册写一份。 */
    val saveToGallery: Boolean = false,
    /** 流式预览开关。当前里程碑未实现流式，保留字段以对齐设置项。 */
    val streamPreviewEnabled: Boolean = true,

    /** 自定义原图保存根目录。空 = App 文档目录下的 images。 */
    val imageOutputDir: String = "",

    /**
     * **自定义保存目录（SAF 授权过的文件夹）**：系统文件夹选择器选出来的 tree URI，
     * 例如 `content://com.android.externalstorage.documents/tree/primary%3ANAI`。
     * 存图时会在 App 私有目录照常落一份（图库/编辑要用本地路径），**同时另存一份到这个文件夹**。
     * 空串 = 不另存。对应界面上那个"点击选文件夹"的按钮。
     */
    val imageOutputTreeUri: String = "",

    val activeHistoryGroupId: String = "",
    val generationGroupId: String = "",

    val lockStylePrompt: Boolean = false,
    val lockNegativePrompt: Boolean = false,
    val savedStylePrompt: String = "",
    val savedNegativePrompt: String = "",

    // ---- 提示词锁（用户 2026-09-16 要求）：提示词框右上角那个锁图标 ----
    /**
     * 锁上之后**这条提示词任何人都改不动**（用户要求）：手打不进、AI 翻译 / 优化
     * 不改写它、垃圾桶清不掉、撤销 / 重做和"从图片元数据导入"也绕不过去 ——
     * 落点在 [com.kallan.naistudio.state.AppState] 的 `params` 写入口，只有一处。
     *
     * ⚠️ 与上面的 [lockStylePrompt] / [lockNegativePrompt] **不是一回事**：
     * 那一对开关管的是"**跨重启保留**、别被『重置参数』清掉"（开关在设置页里），
     * 锁上之后照样可以随手编辑。这里这三个是"**改不了**"，锁是钉死的。
     * 两组名字必须能一眼分开，所以特意用了 `promptLock*` 这个前缀。
     */
    val promptLockStyle: Boolean = false,
    val promptLockPositive: Boolean = false,
    val promptLockNegative: Boolean = false,

    /** 文件名模板，支持 {date} {time} {seq} {seed} {model} {type} {name} {ts}。 */
    val imageNameTemplate: String = "{date}_{seq}_{model}",

    val batchIntervalSeconds: Int = 0,

    /**
     * 每工具的"恢复上次参数"开关。默认全 true（即当前行为）；
     * 关掉某个工具，它下次启动就回落到硬编码默认值。
     */
    val persistGenerateParams: Boolean = true,
    val persistInpaintParams: Boolean = true,
    val persistUpscaleParams: Boolean = true,
    val persistDirectorParams: Boolean = true,

    /**
     * **生图后自动走官方 2× 放大**。
     * ⚠️ 官方放大是**付费接口**：按输入像素扣 1–4 Anlas（与生成本身分开计费），所以默认关。
     * 打开后每张生成结果都会再放大一张：**放大前与放大后两张都保存**，
     * 而图生图 / 遮罩重绘仍然用**放大前**的那张（坐标口径不变）。
     */
    val officialUpscaleAfterGenerate: Boolean = false,

    // 以下为后续里程碑（重绘/超分/Director）的参数，先按参考实现的默认值声明
    val inpaintModel: String = "nai-diffusion-5-full-inpainting",
    val inpaintStrength: Double = 1.0,
    val inpaintNoise: Double = 0.0,
    /** 独立于主生成的正向提示词 —— 重绘**不**自动继承主提示词。 */
    val inpaintPositivePrompt: String = "",
    // ---- 局部重做的"生成后控制"（对齐 ComfyUI「NAI 局部重绘采样器」节点）----
    /** 把**发出去的**遮罩方形膨胀多少像素（0..128）：接缝落在选区外，细笔迹也不容易被吃掉。 */
    val inpaintMaskExpand: Int = 7,
    /** 回贴软边宽度（0..64 px）：只影响本地回贴，不进请求。 */
    val inpaintMaskFeather: Int = 20,
    /** 回贴是否做软边（关 = 硬边回贴）。 */
    val inpaintEdgeProtection: Boolean = true,
    /**
     * 本地回贴：把未遮罩区域用原图贴回去。
     * 请求里 `add_original_image` **恒为 false**（官方客户端要原始 infill，自己回贴）。
     */
    val inpaintAddOriginalImage: Boolean = true,
    // ---- 聚焦重绘（Focused Inpainting，2026-09-17 从电脑线移植）----
    /** 框外留多少像素做参考（官方 Minimum Context Area；0 = 不留）。 */
    val inpaintFocusContext: Int = 96,
    /** 放大倍率（**用户自己调**，1.0 = 不放大）；框比免费档小时自动放大到接近额度。 */
    val inpaintFocusScale: Float = 1.0f,
    /** true = 整块请求夹在免费档内（每边 ≤1024、面积 ≤1MP，Opus 大图 0 点数的前提）。 */
    val inpaintFocusFreeTierOnly: Boolean = true,
    // ---- 无限画布（用户 2026-09-22 点的单 ✓；`docs/69` 方案 / `docs/71`、`docs/72` 落地 ✓）----
    // 为什么落盘：画布是内存里一张位图 ✓，每次拓展都是**花钱**出来的 ✓ —— 关一次 App 就全没了
    // 的话，"拓展了半天的画"就白拓展了 ✗（最伤人的那种 ✗）。
    /** 上次停在哪个画布模式：0 = 普通 / 1 = 无限画布 / 2 = 漫画 ✓。老 prefs.json 读成 0 ✓。 */
    val canvasMode: Int = 0,
    /** 无限画布框外留给模型参考的上下文像素，独立于普通局部重绘。 */
    val infiniteContext: Int = 96,
    /** 上次那张画布的**快照路径**（`files/infinite/canvas-<时间戳>.png` ✓；空 = 没有 ✓）。 */
    val infiniteCanvasSnapshot: String = "",
    /** 这张画布**是从哪张工作图开的** ✓（和现在的 `workImagePath` 一致 ⇒ 接着用快照 ✓）。 */
    val infiniteCanvasSource: String = "",
    /** 上次那个**聚焦框**（**文档坐标** ✓）；宽 / 高 ≤0 ⇒ 没有框 ✓。 */
    val infiniteFrameX: Int = 0,
    val infiniteFrameY: Int = 0,
    val infiniteFrameW: Int = 0,
    val infiniteFrameH: Int = 0,
    // ---- 「AI 对话层」的开关（2026-09-16，参考 ComfyUI「API LLM通用链路」节点）----
    // 总开关 `is_enable` 已整项去除（用户 2026-09-17）：AI 功能恒可用，不再有"整体关闭"态。
    /** LLM 采样温度（节点 `temperature`：默认 0.7、0.0–1.0、step 0.1）。 */
    val llmTemperature: Double = 0.7,
    /** 是否**记忆多轮**（节点 `is_memory`，默认 disable）：**关掉 = 清空历史**（源码 llm.py:1651-1653）。 */
    val llmMemoryEnabled: Boolean = false,
    /**
     * 发给模型的历史轮数（节点 `conversation_rounds`：默认 100）；旧历史仍留在文件里。
     * 上限收到 **200**（用户 2026-09-17；节点源码本身允许到 10000）。
     */
    val llmHistoryRounds: Int = 100,
    /** `is_locked`（节点同名）：参数没变就直接返回上轮结果、不发请求。 */
    val llmIsLocked: Boolean = false,
    /**
     * 输出上限（节点 `max_length`：默认 1920、256–128000、step 128）。
     *
     * ⚠️ **2026-09-18 起不再发进请求**（用户：「把输出上限去除」）：分镜那种长 JSON
     * 老被 1920 切在字符串中间。字段保留只为不破坏旧档案的读写 —— 请求里现在
     * **没有** `max_tokens`（见 `LlmApi.buildPayload`）。想自己限长度就用
     * 「额外请求参数」（`llmExtraParameters`）填 `max_tokens`。
     */
    val llmMaxLength: Int = 1920,
    /** 额外请求参数（节点 `extra_parameters`）：一段 JSON，原样合并进请求体。 */
    val llmExtraParameters: String = "",
    /** 图床 key（节点 `imgbb_api_key`）：留空 = base64 直发，填了 = 上传取 URL。 */
    val llmImgbbKey: String = "",
    /** 节点 `system_prompt_input`：附加 system（挂面具），拼在 system_prompt 后面。 */
    val llmSystemPromptInput: String = "",
    /** 节点 `file_content`：拼到 system 末尾的"已知信息"。 */
    val llmFileContent: String = "",
    /** 节点 `is_tools_in_sys_prompt`：把工具说明写进 system prompt。 */
    val llmToolsInSysPrompt: Boolean = false,
    /** 节点 `tools`：OpenAI 格式工具清单 JSON。 */
    val llmToolsJson: String = "",
    /** 节点 `user_history`：非空时直接覆盖历史。 */
    val llmUserHistoryJson: String = "",
    /** 「导入上下文」的来源名（界面显示"当前上下文来自哪"；空 = 没导入，走记忆窗口）。 */
    val llmContextSource: String = "",
    // ---- SillyTavern 预设（2026-09-17；手机线先行）----
    /** 导入过的 ST 预设（**只存引用**；正文落盘在 `filesDir/st_presets/`）。 */
    val stPresetRefs: List<StPresetRef> = emptyList(),
    /** 全局启用的 ST 预设 id；空 = 不用（各功能可用下面的字段单独覆盖）。 */
    val stPresetId: String = "",
    /** 各功能单独覆盖的 ST 预设 id；空 = 跟随 [stPresetId]。 */
    val stPresetTranslate: String = "",
    val stPresetOptimize: String = "",
    val stPresetStoryboard: String = "",
    val stPresetPlan: String = "",
    val stPresetReverse: String = "",
    /** 节点 `stream`：流式输出。**默认开**（用户 2026-09-17）。 */
    val llmStream: Boolean = true,
    /**
     * 「流式默认开」的一次性迁移标记（用户 2026-09-17）。
     *
     * 老档案里落盘的 `llmStream=false` 只是"当时的默认值"，分不出是不是用户主动关的；
     * 载入时若盘上没有这个标记，就无视落盘值强行置 `true`，同时把标记置上 ——
     * 之后用户自己关掉就是自己的选择，重启不会再被翻回来。
     */
    val llmStreamDefaulted: Boolean = false,
    /** 节点 `main_brain`：false = 本用途作为别的用途的工具。 */
    val llmMainBrain: Boolean = true,
    // ---- 图生图（对齐 ComfyUI「NAI 图生图采样器」节点：强度 0.70 / 附加噪声 0.00 / 尺寸策略保持输入尺寸）----
    /** 图生图强度（`strength`）。 */
    val i2iStrength: Double = 0.7,
    /** 附加噪声（`noise`），节点默认 0。 */
    val i2iNoise: Double = 0.0,
    /**
     * 尺寸策略：`source` = 保持输入尺寸（64 对齐）；`core` = 使用核心参数里的宽高。
     */
    val i2iSizeMode: String = "source",
    /**
     * 图生图**底图来源**：生成一次之后用哪张继续。
     *  · `latest`   = 用刚生成的那张（预览区显示结果，适合连续迭代）
     *  · `original` = 一直用最开始导入 / 指定的那张
     */
    val i2iSourceMode: String = "latest",
    val upscaleScale: Int = 2,
    val directorTool: String = "bg-removal",
    val augmentDefry: Double = 0.0,
    val augmentColorizePrompt: String = "",
    val augmentEmotion: String = "happy",
    val augmentEmotionLevel: Double = 0.0,
    /** 导演工具「清理杂物」是否保留文字气泡（决定发 `declutter` 还是 `declutter-keep-bubbles`）。 */
    val augmentKeepTextBubbles: Boolean = false,

    val historyRetentionDays: Int = 365,

    // ---- LLM API（「我的 → LLM API」）：用来翻译 / 优化提示词，OpenAI 兼容接口 ----
    /** 接口基址，例如 `https://api.openai.com/v1`；也可以直接填完整的 `/chat/completions` 地址。 */
    val llmApiUrl: String = "",
    val llmApiKey: String = "",
    /** 模型名；不同服务商不一样（如 gpt-4o-mini / deepseek-chat / qwen-plus）。 */
    val llmModel: String = "gpt-4o-mini",
    /**
     * **用户自建的 LLM 预设**（2026-09-17 用户要求，形态照"提示词预设"）：
     * 每条 = 名字 + 服务地址 + 密钥 + 模型名；分功能 AI 里**选一条**即可，不用逐个填三项。
     *
     * ⚠️ 里面带密钥：`toBackupJson()` 会**逐条剔除 apiKey**（[SECRET_KEYS] 是顶层键，管不到嵌套）。
     */
    val llmPresets: List<LlmPresetEntry> = emptyList(),
    /** 各功能选用的预设 id；空 = 用手填的那三项（再空则回落主配置）。 */
    val llmTranslatePreset: String = "",
    val llmOptimizePreset: String = "",
    val llmStoryboardPreset: String = "",
    val llmPlanPreset: String = "",
    val llmReversePreset: String = "",

    // ---- AI 对话配置：现在**只剩「思考程度」**（用户 2026-09-16 要求）----
    /**
     * 「思考程度」（reasoning effort）：OpenAI 兼容接口的 `reasoning_effort` 字段，
     * `"off"` = 不发该字段（很多模型/中转不认识它，默认关最稳）。
     * 其余值 low / medium / high 原样发 —— 支持的模型（o 系列 / DeepSeek R1 等）会按档位多想一会，
     * 不支持的一般会忽略，个别严格的会报 400，关掉即可。
     *
     * ⚠️ **只对主 API 生效**，分功能 AI 一律不发（见 `LlmConfig.overridden`）。
     *
     * `llmTemperature` / `llmTopP` / `llmMaxOutput` 三个键**已删除**：对应的滑条按用户
     * 要求全部删掉，采样参数改成固定值（`LlmApi.AiParams`）—— 删控件不等于改行为，
     * 所以温度仍是原来的默认 0.3，不是"不发"。
     */
    val llmReasoningEffort: String = "off",

    // ---- 分功能 AI（用户 2026-09-16 要求）：每个用到 AI 的功能都能单独指定一套 ----
    /**
     * 上面那三项（[llmApiUrl] / [llmApiKey] / [llmModel]）就是**正面提示词**那一套，
     * 同时也是所有功能的**兜底**。下面每组的三个字段**留空即回落**。
     *
     * ## 为什么是"三项各自独立回落"而不是整组切换
     *
     * 最常见的用法是「只换个模型」：图片反推要视觉模型、翻译想用便宜的小模型，
     * 但地址和密钥还是同一家。要是整组回落，用户就得把地址和密钥**再抄一遍**，
     * 抄错一个字符就是一次莫名的 401。所以这里逐字段判空、逐字段回落。
     *
     * 于是"只填了模型名"= 同地址同密钥、换模型；"三样都填"= 完全独立的一套。
     *
     * ⚠️ 这三个 key 必须登记进 [SECRET_KEYS]，否则会跟着备份文件跑出去。
     */
    val llmTranslateApiUrl: String = "",
    val llmTranslateApiKey: String = "",
    /** 翻译用的模型名；留空用 [llmModel]。 */
    val llmTranslateModel: String = "",

    /** AI 优化提示词（正面提示词框那个 ✨）。 */
    val llmOptimizeApiUrl: String = "",
    val llmOptimizeApiKey: String = "",
    val llmOptimizeModel: String = "",

    /**
     * **「分镜」**：漫画「按剧情分镜」与狂暴模式的第二段（逐页分镜）**共用这一套**。
     *
     * 两处本来就是同一段规则、同一件事，拆开只会让人配两遍（用户明确要求合并）。
     */
    val llmStoryboardApiUrl: String = "",
    val llmStoryboardApiKey: String = "",
    val llmStoryboardModel: String = "",

    /** **「狂暴模式」**：第一段的整部剧情 → 分页规划。 */
    val llmPlanApiUrl: String = "",
    val llmPlanApiKey: String = "",
    val llmPlanModel: String = "",

    /** **「图片反推」**：要视觉模型，通常和文本模型不是同一个，所以单独一组。 */
    val llmReverseApiUrl: String = "",
    val llmReverseApiKey: String = "",
    val llmReverseModel: String = "",

    /** 翻译用哪个后端：`"llm"` = 上面的 LLM 接口；`"baidu"` = 百度翻译 API。 */
    val translateService: String = "llm",
    // ---- 百度翻译（translateService = "baidu" 时用）----
    val baiduTranslateAppId: String = "",
    val baiduTranslateSecret: String = "",

    // ---- 提示词规则（设置里可改；默认值取自 ComfyUI「提示词小助手」插件 / 按其口径写）----
    /**
     * 优化（扩写）规则：按模式分别保存（`tag` / `natural` / `mixed`）。
     * 生成页那个 ✨ 图标用当前模式对应的那一段当 system prompt。
     */
    val optimizeRuleMode: String = PromptRules.MODE_TAG,
    val optimizeRulesTag: String = PromptRules.DEFAULT_OPTIMIZE_RULES,
    val optimizeRulesNatural: String = PromptRules.OPTIMIZE_NATURAL_RULES,
    val optimizeRulesMixed: String = PromptRules.OPTIMIZE_MIXED_RULES,
    /** 反推规则：同样按模式分别保存；图库长按「反推提示词」用当前模式那一段。 */
    val reverseRuleMode: String = PromptRules.MODE_TAG,
    val reverseRulesTag: String = PromptRules.DEFAULT_REVERSE_RULES,
    val reverseRulesNatural: String = PromptRules.REVERSE_NATURAL_RULES,
    val reverseRulesMixed: String = PromptRules.REVERSE_MIXED_RULES,

    // ---- 漫画模式（「按剧情分镜」）----
    /** 漫画分镜师规则：把一段剧情拆成每格的提示词。设置里可改，改坏了有"恢复默认"。 */
    val comicStoryboardRules: String = PromptRules.COMIC_STORYBOARD_RULES,
    /**
     * 狂暴漫画模式**第一段**的规则：整部剧情 → 分页规划（页数 + 每页版式 + 每页概要）。
     *
     * 与 [comicStoryboardRules] 是两段独立的提示词：先分页，再逐页分镜。
     */
    val comicPagePlanRules: String = PromptRules.COMIC_PAGE_PLAN_RULES,
    /**
     * 上面两段规则**升级到了第几版默认文案**（见 [PromptRules.COMIC_RULES_VERSION]）。
     *
     * 两者是存在盘上的，所以升级时要靠这个字段判断"要不要把新默认文案推下去"。
     * 老设置没有这个键 → `0` → 会走一次迁移。
     */
    val comicRulesVersion: Int = 0,
    /**
     * 生成完分镜后**是否直接触发生图**。
     *
     * 默认关：分镜生成不花钱、可以反复调，让人先看一眼再决定花不花 Anlas。
     * 打开后就是"写一段剧情 → 一页漫画"的一条龙。
     *
     * 狂暴模式下同一个开关也管着"规划完是否立刻把每一页排进生成队列"。
     */
    val comicAutoGenerate: Boolean = false,
    /**
     * 狂暴漫画模式的**页数上限**：AI 规划出的页数会被截到这个数以内。
     *
     * 这不是"目标页数"—— 剧情短的时候 AI 可以给更少。上限存在的意义是防止
     * 模型把一格拆成一页、一口气排出去几十张图。
     */
    val comicMaxPages: Int = 8,

    /**
     * 图库显示模式：
     *  · `grid` —— 松散网格，**每行数目可调**（[galleryColumns]）
     *  · `list` —— 列表（缩略图 + 提示词/seed/文件名）
     */
    val galleryLayout: String = "grid",
    /** 松散网格每行数目，2..6。 */
    val galleryColumns: Int = 3,
    /** 图库排序：`time`（按生成时间） / `random`。 */
    val gallerySort: String = "time",
    /** 按时间排序时是否降序（新的在前）。随机排序没有方向。 */
    val gallerySortDescending: Boolean = true,
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("apiBaseUrl", apiBaseUrl)
        put("imageBaseUrl", imageBaseUrl)
        put("allowCustomEndpoint", allowCustomEndpoint)
        put("useThirdPartyApi", useThirdPartyApi)
        put("thirdPartyBaseUrl", thirdPartyBaseUrl)
        put("usageStats", usageStats)
        // 余额快照：`null` 要写成 JSON 的 null（**不能省略** —— 省略了下一次读会误以为
        // "还是老值"；也不写 0，0 会被小组件当成"余额用光了"）
        put("balanceSnapshot", balanceSnapshot ?: JSONObject.NULL)
        put("balanceSnapshotAt", balanceSnapshotAt)
        put("tierSnapshot", tierSnapshot)
        // 同上：`null` 要写成 JSON null（0 是"额度用光了"，和"没有这一档"不是一回事）
        put("v5PercentSnapshot", v5PercentSnapshot ?: JSONObject.NULL)
        put("accountServerUrl", accountServerUrl)
        put("hostedMode", hostedMode)
        put("language", language)
        put("theme", theme)
        put("palette", palette)
        // 自定义调色（空串也不省略：写出来更能说明"这一档没自定义"）
        put("colorPrimaryLight", colorPrimaryLight)
        put("colorPrimaryDark", colorPrimaryDark)
        put("colorContainerLight", colorContainerLight)
        put("colorContainerDark", colorContainerDark)
        put("colorPanelLight", colorPanelLight)
        put("colorPanelDark", colorPanelDark)
        put("colorBackgroundLight", colorBackgroundLight)
        put("colorBackgroundDark", colorBackgroundDark)
        put("colorTextLight", colorTextLight)
        put("colorTextDark", colorTextDark)
        put("modelMode", modelMode)
        put("keepImageMetadata", keepImageMetadata)
        put("saveToGallery", saveToGallery)
        put("streamPreviewEnabled", streamPreviewEnabled)
        put("imageOutputDir", imageOutputDir)
        put("imageOutputTreeUri", imageOutputTreeUri)
        put("activeHistoryGroupId", activeHistoryGroupId)
        put("generationGroupId", generationGroupId)
        put("lockStylePrompt", lockStylePrompt)
        put("lockNegativePrompt", lockNegativePrompt)
        put("savedStylePrompt", savedStylePrompt)
        put("savedNegativePrompt", savedNegativePrompt)
        put("promptLockStyle", promptLockStyle)
        put("promptLockPositive", promptLockPositive)
        put("promptLockNegative", promptLockNegative)
        put("imageNameTemplate", imageNameTemplate)
        put("batchIntervalSeconds", batchIntervalSeconds)
        put("persistGenerateParams", persistGenerateParams)
        put("persistInpaintParams", persistInpaintParams)
        put("persistUpscaleParams", persistUpscaleParams)
        put("persistDirectorParams", persistDirectorParams)
        put("officialUpscaleAfterGenerate", officialUpscaleAfterGenerate)
        put("inpaintModel", inpaintModel)
        put("inpaintStrength", inpaintStrength)
        put("inpaintNoise", inpaintNoise)
        put("inpaintPositivePrompt", inpaintPositivePrompt)
        put("inpaintMaskExpand", inpaintMaskExpand)
        put("inpaintMaskFeather", inpaintMaskFeather)
        put("inpaintEdgeProtection", inpaintEdgeProtection)
        put("inpaintAddOriginalImage", inpaintAddOriginalImage)
        put("inpaintFocusContext", inpaintFocusContext)
        put("inpaintFocusScale", inpaintFocusScale.toDouble())
        put("inpaintFocusFreeTierOnly", inpaintFocusFreeTierOnly)
        put("canvasMode", canvasMode)
        put("infiniteContext", infiniteContext)
        put("infiniteCanvasSnapshot", infiniteCanvasSnapshot)
        put("infiniteCanvasSource", infiniteCanvasSource)
        put("infiniteFrameX", infiniteFrameX)
        put("infiniteFrameY", infiniteFrameY)
        put("infiniteFrameW", infiniteFrameW)
        put("infiniteFrameH", infiniteFrameH)
        put("llmTemperature", llmTemperature)
        put("llmMemoryEnabled", llmMemoryEnabled)
        put("llmHistoryRounds", llmHistoryRounds)
        put("llmIsLocked", llmIsLocked)
        put("llmMaxLength", llmMaxLength)
        put("llmExtraParameters", llmExtraParameters)
        put("llmImgbbKey", llmImgbbKey)
        put("llmSystemPromptInput", llmSystemPromptInput)
        put("llmFileContent", llmFileContent)
        put("llmToolsInSysPrompt", llmToolsInSysPrompt)
        put("llmToolsJson", llmToolsJson)
        put("llmUserHistoryJson", llmUserHistoryJson)
        put("llmContextSource", llmContextSource)
        put(
            "stPresetRefs",
            org.json.JSONArray().apply { stPresetRefs.forEach { put(it.toJson()) } },
        )
        put("stPresetId", stPresetId)
        put("stPresetTranslate", stPresetTranslate)
        put("stPresetOptimize", stPresetOptimize)
        put("stPresetStoryboard", stPresetStoryboard)
        put("stPresetPlan", stPresetPlan)
        put("stPresetReverse", stPresetReverse)
        put("llmStream", llmStream)
        put("llmStreamDefaulted", llmStreamDefaulted)
        put("llmMainBrain", llmMainBrain)
        put("i2iStrength", i2iStrength)
        put("i2iNoise", i2iNoise)
        put("i2iSizeMode", i2iSizeMode)
        put("i2iSourceMode", i2iSourceMode)
        put("upscaleScale", upscaleScale)
        put("directorTool", directorTool)
        put("augmentDefry", augmentDefry)
        put("augmentColorizePrompt", augmentColorizePrompt)
        put("augmentEmotion", augmentEmotion)
        put("augmentEmotionLevel", augmentEmotionLevel)
        put("augmentKeepTextBubbles", augmentKeepTextBubbles)
        put("historyRetentionDays", historyRetentionDays)
        put("llmApiUrl", llmApiUrl)
        put("llmApiKey", llmApiKey)
        put("llmModel", llmModel)
        put(
            "llmPresets",
            org.json.JSONArray().apply { llmPresets.forEach { put(it.toJson()) } },
        )
        put("llmTranslatePreset", llmTranslatePreset)
        put("llmOptimizePreset", llmOptimizePreset)
        put("llmStoryboardPreset", llmStoryboardPreset)
        put("llmPlanPreset", llmPlanPreset)
        put("llmReversePreset", llmReversePreset)
    put("llmReasoningEffort", llmReasoningEffort)
        put("llmTranslateApiUrl", llmTranslateApiUrl)
        put("llmTranslateApiKey", llmTranslateApiKey)
        put("llmTranslateModel", llmTranslateModel)
        put("llmOptimizeApiUrl", llmOptimizeApiUrl)
        put("llmOptimizeApiKey", llmOptimizeApiKey)
        put("llmOptimizeModel", llmOptimizeModel)
        put("llmStoryboardApiUrl", llmStoryboardApiUrl)
        put("llmStoryboardApiKey", llmStoryboardApiKey)
        put("llmStoryboardModel", llmStoryboardModel)
        put("llmPlanApiUrl", llmPlanApiUrl)
        put("llmPlanApiKey", llmPlanApiKey)
        put("llmPlanModel", llmPlanModel)
        put("llmReverseApiUrl", llmReverseApiUrl)
        put("llmReverseApiKey", llmReverseApiKey)
        put("llmReverseModel", llmReverseModel)
        put("translateService", translateService)
        put("baiduTranslateAppId", baiduTranslateAppId)
        put("baiduTranslateSecret", baiduTranslateSecret)
        put("optimizeRuleMode", optimizeRuleMode)
        put("optimizeRulesTag", optimizeRulesTag)
        put("optimizeRulesNatural", optimizeRulesNatural)
        put("optimizeRulesMixed", optimizeRulesMixed)
        put("reverseRuleMode", reverseRuleMode)
        put("reverseRulesTag", reverseRulesTag)
        put("reverseRulesNatural", reverseRulesNatural)
        put("reverseRulesMixed", reverseRulesMixed)
        put("comicStoryboardRules", comicStoryboardRules)
        put("comicPagePlanRules", comicPagePlanRules)
        put("comicRulesVersion", comicRulesVersion)
        put("comicAutoGenerate", comicAutoGenerate)
        put("comicMaxPages", comicMaxPages)
        put("galleryLayout", galleryLayout)
        put("galleryColumns", galleryColumns)
        put("gallerySort", gallerySort)
        put("gallerySortDescending", gallerySortDescending)
    }

    /**
     * **备份专用**的 JSON：与 [toJson] 逐键一致，只是**剔掉隐私字段**。
     *
     * ## 为什么必须分成两个
     *
     * API Key 这类东西一旦进了备份文件，就跟着文件到处跑 —— 备份会被塞进 PNG、
     * 传到网盘、发给别人排查问题。而这两种损失的代价差得远：
     * **密钥丢了只是重填一次，泄了是要去改密码、还要回头查有没有被用掉的。**
     *
     * 所以备份**只带走配置，不带走凭证**。
     *
     * ⚠️ 新增密钥类字段时**必须往 [SECRET_KEYS] 里登记**，否则它会悄悄进备份。
     * 单测里有一条断言专门防这个（遍历 `toJson()` 的键名，长得像密钥却没登记的直接失败）。
     */
    fun toBackupJson(): JSONObject = toJson().apply {
        SECRET_KEYS.forEach { remove(it) }
        // 预设里的 apiKey 是**嵌套**的（SECRET_KEYS 只管顶层键）—— 这里逐条抹掉
        optJSONArray("llmPresets")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.remove("apiKey")
            }
        }
    }

    // ---------------------------------------------------------------- 分功能 AI

    /**
     * 组装某个功能该用的 LLM 配置：**三项各自独立回落**到「正面提示词」那一套。
     *
     * `url` / `key` / `model` 分别判空 —— 传空串就意味着"这一项跟随主配置"。
     * 所以调用方可以只关心自己填了哪几项，回落逻辑只有这一处。
     */
    /**
     * 某个用途实际生效的 **SillyTavern 预设 id**：先用功能自己的覆盖，空则用全局，空则"不用"。
     */
    fun stPresetIdFor(kindKey: String): String {
        val key = kindKey.substringAfterLast('.')
        val own = when (key) {
            LlmMemoryKind.TRANSLATE -> stPresetTranslate
            LlmMemoryKind.OPTIMIZE -> stPresetOptimize
            LlmMemoryKind.STORYBOARD -> stPresetStoryboard
            LlmMemoryKind.PAGE_PLAN -> stPresetPlan
            LlmMemoryKind.REVERSE -> stPresetReverse
            else -> ""
        }.trim()
        return own.ifEmpty { stPresetId.trim() }
    }

    /** 按 id 找预设（空 id / 找不到 = null，调用方回落手填三项）。 */
    fun llmPresetById(id: String): LlmPresetEntry? =
        id.takeIf { it.isNotBlank() }?.let { key -> llmPresets.firstOrNull { it.id == key } }

    fun llmConfig(url: String = "", key: String = "", model: String = ""): LlmConfig = LlmConfig(
        url = url.trim().ifEmpty { llmApiUrl.trim() },
        key = key.trim().ifEmpty { llmApiKey.trim() },
        model = model.trim().ifEmpty { llmModel },
        overridden = url.isNotBlank() || key.isNotBlank() || model.isNotBlank(),
    )

    /** 翻译提示词用哪一套。 */
    fun llmConfigForTranslate(): LlmConfig =
        // **预设优先**（用户 2026-09-17）：选了预设就用它那一套；没选才走手填三项（再空则回落主配置）
        llmPresetById(llmTranslatePreset)?.let { LlmConfig(url = it.baseUrl, key = it.apiKey, model = it.model, overridden = true) }
            ?: llmConfig(llmTranslateApiUrl, llmTranslateApiKey, llmTranslateModel)

    /** AI 优化提示词用哪一套。 */
    fun llmConfigForOptimize(): LlmConfig =
        // **预设优先**（用户 2026-09-17）：选了预设就用它那一套；没选才走手填三项（再空则回落主配置）
        llmPresetById(llmOptimizePreset)?.let { LlmConfig(url = it.baseUrl, key = it.apiKey, model = it.model, overridden = true) }
            ?: llmConfig(llmOptimizeApiUrl, llmOptimizeApiKey, llmOptimizeModel)

    /** 分镜用哪一套（漫画「按剧情分镜」+ 狂暴模式逐页分镜共用）。 */
    fun llmConfigForStoryboard(): LlmConfig =
        // **预设优先**（用户 2026-09-17）：选了预设就用它那一套；没选才走手填三项（再空则回落主配置）
        llmPresetById(llmStoryboardPreset)?.let { LlmConfig(url = it.baseUrl, key = it.apiKey, model = it.model, overridden = true) }
            ?: llmConfig(llmStoryboardApiUrl, llmStoryboardApiKey, llmStoryboardModel)

    /** 狂暴模式第一段（整部剧情 → 分页规划）用哪一套。 */
    fun llmConfigForPlan(): LlmConfig =
        // **预设优先**（用户 2026-09-17）：选了预设就用它那一套；没选才走手填三项（再空则回落主配置）
        llmPresetById(llmPlanPreset)?.let { LlmConfig(url = it.baseUrl, key = it.apiKey, model = it.model, overridden = true) }
            ?: llmConfig(llmPlanApiUrl, llmPlanApiKey, llmPlanModel)

    /** 图片反推提示词用哪一套。 */
    fun llmConfigForReverse(): LlmConfig =
        // **预设优先**（用户 2026-09-17）：选了预设就用它那一套；没选才走手填三项（再空则回落主配置）
        llmPresetById(llmReversePreset)?.let { LlmConfig(url = it.baseUrl, key = it.apiKey, model = it.model, overridden = true) }
            ?: llmConfig(llmReverseApiUrl, llmReverseApiKey, llmReverseModel)

    companion object {
        /**
         * 保留轮数（[llmHistoryRounds]）的**界面与读档上限**。
         * 用户 2026-09-17：从节点源码的 10000 收到 200；滑条与 `fromJson` 夹取共用这一个值。
         */
        const val LLM_HISTORY_ROUNDS_MAX = 200

        /**
         * **不进备份**的字段。
         *
         * 判定口径是**代价不对称**，不是"敏感不敏感"：
         * 泄露要付出改密码/查账的代价、而重填只是几秒钟的，一律不进备份。
         *
         * 所以 [llmApiUrl]（接口地址）**不算** —— 它是配置不是凭证，
         * 备份里带着反而方便（换机后连地址都不用重填）。
         *
         * ⚠️ 加新的密钥类字段时往这里加一条。`BackupPrivacyTest` 会遍历
         * [toJson] 的键名，凡是长得像密钥却没登记在这里的，直接让构建失败。
         */
        val SECRET_KEYS = listOf(
            "llmApiKey",
            // 分功能 AI 的密钥：**一个都不能漏**，否则会跟着备份文件跑出去。
            // 地址（*ApiUrl）和模型名（*Model）不算凭证，照 llmApiUrl 的口径留在备份里。
            "llmTranslateApiKey",
            "llmOptimizeApiKey",
            "llmStoryboardApiKey",
            "llmPlanApiKey",
            "llmReverseApiKey",
            "baiduTranslateAppId",
            "baiduTranslateSecret",
        )

        /** 判断一个键名"长得像不像密钥"（给上面那条断言用）。 */
        val SECRET_KEY_PATTERN = Regex("(?i)(apikey|api_key|secret|token|password|appid|app_id)")

        fun fromJson(json: JSONObject?): AppSettings {
            if (json == null) return AppSettings(llmStreamDefaulted = true)
            // 流式默认开迁移：盘上没有标记的老档案，无视落盘的 llmStream 直接置 true（见字段注释）。
            val streamMigrated = json.optBoolean("llmStreamDefaulted", false)
            val defaults = AppSettings()
            return AppSettings(
                apiBaseUrl = json.optString("apiBaseUrl", defaults.apiBaseUrl),
                imageBaseUrl = json.optString("imageBaseUrl", defaults.imageBaseUrl),
                allowCustomEndpoint = json.optBoolean("allowCustomEndpoint", defaults.allowCustomEndpoint),
                // 老设置没有这个键 ⇒ 回落到"看地址像不像第三方"：升级前**手动填过第三方
                // 地址**的人自动就是第三方模式，不用再点一次（见 `useThirdPartyApi` 的说明）。
                useThirdPartyApi = json.optBoolean(
                    "useThirdPartyApi",
                    json.optString("imageBaseUrl", defaults.imageBaseUrl)
                        .let { it.isNotBlank() && it.trimEnd('/') != defaults.imageBaseUrl },
                ),
                // 老设置里没有这一项：把当初填在 `imageBaseUrl` 里的第三方地址**搬过来**，
                // 这样升级用户不用重填一次（官方那一栏的修正在 `Storage` 读完之后做，
                // 见那里的 `migrateLegacyImageBase`）。
                thirdPartyBaseUrl = json.optString("thirdPartyBaseUrl", ""),
                usageStats = json.optString("usageStats", defaults.usageStats),
                // 老设置没有这两个键 ⇒ 快照是 null（小组件显示"—"，不编数）
                balanceSnapshot = if (json.has("balanceSnapshot") && !json.isNull("balanceSnapshot")) {
                    json.optInt("balanceSnapshot")
                } else {
                    defaults.balanceSnapshot
                },
                balanceSnapshotAt = json.optLong("balanceSnapshotAt", defaults.balanceSnapshotAt),
                // 老设置没有这两个键 ⇒ 档名空串（徽章不画）、百分比 null（进度条不画）
                tierSnapshot = json.optString("tierSnapshot", defaults.tierSnapshot),
                v5PercentSnapshot = if (json.has("v5PercentSnapshot") && !json.isNull("v5PercentSnapshot")) {
                    json.optInt("v5PercentSnapshot")
                } else {
                    defaults.v5PercentSnapshot
                },
                accountServerUrl = json.optString("accountServerUrl", defaults.accountServerUrl),
                hostedMode = json.optBoolean("hostedMode", defaults.hostedMode),
                language = json.optString("language", defaults.language),
                theme = json.optString("theme", defaults.theme),
                palette = json.optString("palette", defaults.palette),
        // 老设置里没有这些键 → optString 拿到默认空串 = 全部跟随主题默认（升级无感）
        colorPrimaryLight = json.optString("colorPrimaryLight", defaults.colorPrimaryLight),
        colorPrimaryDark = json.optString("colorPrimaryDark", defaults.colorPrimaryDark),
        colorContainerLight = json.optString("colorContainerLight", defaults.colorContainerLight),
        colorContainerDark = json.optString("colorContainerDark", defaults.colorContainerDark),
        colorPanelLight = json.optString("colorPanelLight", defaults.colorPanelLight),
        colorPanelDark = json.optString("colorPanelDark", defaults.colorPanelDark),
        colorBackgroundLight = json.optString("colorBackgroundLight", defaults.colorBackgroundLight),
        colorBackgroundDark = json.optString("colorBackgroundDark", defaults.colorBackgroundDark),
        colorTextLight = json.optString("colorTextLight", defaults.colorTextLight),
        colorTextDark = json.optString("colorTextDark", defaults.colorTextDark),
                modelMode = json.optString("modelMode", defaults.modelMode),
                keepImageMetadata = json.optBoolean("keepImageMetadata", defaults.keepImageMetadata),
                saveToGallery = json.optBoolean("saveToGallery", defaults.saveToGallery),
                streamPreviewEnabled = json.optBoolean("streamPreviewEnabled", defaults.streamPreviewEnabled),
                imageOutputDir = json.optString("imageOutputDir", defaults.imageOutputDir),
            imageOutputTreeUri = json.optString("imageOutputTreeUri", defaults.imageOutputTreeUri),
                activeHistoryGroupId = json.optString("activeHistoryGroupId", defaults.activeHistoryGroupId),
                generationGroupId = json.optString("generationGroupId", defaults.generationGroupId),
                lockStylePrompt = json.optBoolean("lockStylePrompt", defaults.lockStylePrompt),
                lockNegativePrompt = json.optBoolean("lockNegativePrompt", defaults.lockNegativePrompt),
                savedStylePrompt = json.optString("savedStylePrompt", defaults.savedStylePrompt),
                savedNegativePrompt = json.optString("savedNegativePrompt", defaults.savedNegativePrompt),
                promptLockStyle = json.optBoolean("promptLockStyle", defaults.promptLockStyle),
                promptLockPositive = json.optBoolean("promptLockPositive", defaults.promptLockPositive),
                promptLockNegative = json.optBoolean("promptLockNegative", defaults.promptLockNegative),
                imageNameTemplate = json.optString("imageNameTemplate", defaults.imageNameTemplate),
                batchIntervalSeconds = json.optInt("batchIntervalSeconds", defaults.batchIntervalSeconds)
                    .coerceIn(0, 3600),
                persistGenerateParams = json.optBoolean("persistGenerateParams", defaults.persistGenerateParams),
                persistInpaintParams = json.optBoolean("persistInpaintParams", defaults.persistInpaintParams),
                persistUpscaleParams = json.optBoolean("persistUpscaleParams", defaults.persistUpscaleParams),
                persistDirectorParams = json.optBoolean("persistDirectorParams", defaults.persistDirectorParams),
                officialUpscaleAfterGenerate = json.optBoolean(
                    "officialUpscaleAfterGenerate",
                    defaults.officialUpscaleAfterGenerate,
                ),
                inpaintModel = json.optString("inpaintModel", defaults.inpaintModel),
                inpaintStrength = finiteClamp(
                    json.optDouble("inpaintStrength", defaults.inpaintStrength), 0.0, 1.0, 1.0,
                ),
                inpaintNoise = finiteClamp(
                    json.optDouble("inpaintNoise", defaults.inpaintNoise), 0.0, 0.99, 0.0,
                ),
                inpaintPositivePrompt = json.optString("inpaintPositivePrompt", defaults.inpaintPositivePrompt),
                inpaintMaskExpand = json.optInt("inpaintMaskExpand", defaults.inpaintMaskExpand)
                    .coerceIn(0, 128),
                inpaintMaskFeather = json.optInt("inpaintMaskFeather", defaults.inpaintMaskFeather)
                    .coerceIn(0, 64),
                inpaintEdgeProtection =
                    json.optBoolean("inpaintEdgeProtection", defaults.inpaintEdgeProtection),
                inpaintAddOriginalImage =
                    json.optBoolean("inpaintAddOriginalImage", defaults.inpaintAddOriginalImage),
                inpaintFocusContext =
                    json.optInt("inpaintFocusContext", defaults.inpaintFocusContext).coerceIn(0, 256),
                inpaintFocusScale = finiteClamp(
                    json.optDouble("inpaintFocusScale", defaults.inpaintFocusScale.toDouble()),
                    0.1,
                    8.0,
                    defaults.inpaintFocusScale.toDouble(),
                ).toFloat(),
                inpaintFocusFreeTierOnly =
                    json.optBoolean("inpaintFocusFreeTierOnly", defaults.inpaintFocusFreeTierOnly),
                // 无限画布：老 prefs.json 里没有这几项 ⇒ 全部读成"没开过这一档" ✓
                canvasMode = json.optInt("canvasMode", defaults.canvasMode).coerceIn(0, 2),
                infiniteContext = json.optInt("infiniteContext", defaults.infiniteContext).coerceIn(0, 512),
                infiniteCanvasSnapshot =
                    json.optString("infiniteCanvasSnapshot", defaults.infiniteCanvasSnapshot),
                infiniteCanvasSource = json.optString("infiniteCanvasSource", defaults.infiniteCanvasSource),
                infiniteFrameX = json.optInt("infiniteFrameX", defaults.infiniteFrameX),
                infiniteFrameY = json.optInt("infiniteFrameY", defaults.infiniteFrameY),
                infiniteFrameW = json.optInt("infiniteFrameW", defaults.infiniteFrameW)
                    .coerceAtLeast(0),
                infiniteFrameH = json.optInt("infiniteFrameH", defaults.infiniteFrameH)
                    .coerceAtLeast(0),
                llmTemperature = finiteClamp(
                    json.optDouble("llmTemperature", defaults.llmTemperature), 0.0, 1.0, 0.7,
                ),
                llmMemoryEnabled = json.optBoolean("llmMemoryEnabled", defaults.llmMemoryEnabled),
                llmHistoryRounds = json.optInt("llmHistoryRounds", defaults.llmHistoryRounds)
                    .coerceIn(1, LLM_HISTORY_ROUNDS_MAX),
                llmIsLocked = json.optBoolean("llmIsLocked", defaults.llmIsLocked),
                llmMaxLength = json.optInt("llmMaxLength", defaults.llmMaxLength).coerceIn(256, 128000),
                llmExtraParameters = json.optString("llmExtraParameters", defaults.llmExtraParameters),
                llmImgbbKey = json.optString("llmImgbbKey", defaults.llmImgbbKey),
                llmSystemPromptInput = json.optString("llmSystemPromptInput", defaults.llmSystemPromptInput),
                llmFileContent = json.optString("llmFileContent", defaults.llmFileContent),
                llmToolsInSysPrompt = json.optBoolean("llmToolsInSysPrompt", defaults.llmToolsInSysPrompt),
                llmToolsJson = json.optString("llmToolsJson", defaults.llmToolsJson),
                llmUserHistoryJson = json.optString("llmUserHistoryJson", defaults.llmUserHistoryJson),
                llmContextSource = json.optString("llmContextSource", defaults.llmContextSource),
                stPresetRefs = json.optJSONArray("stPresetRefs")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { StPresetRef.fromJson(it) }
                    }.filter { it.id.isNotBlank() }
                } ?: defaults.stPresetRefs,
                stPresetId = json.optString("stPresetId", defaults.stPresetId),
                stPresetTranslate = json.optString("stPresetTranslate", defaults.stPresetTranslate),
                stPresetOptimize = json.optString("stPresetOptimize", defaults.stPresetOptimize),
                stPresetStoryboard = json.optString("stPresetStoryboard", defaults.stPresetStoryboard),
                stPresetPlan = json.optString("stPresetPlan", defaults.stPresetPlan),
                stPresetReverse = json.optString("stPresetReverse", defaults.stPresetReverse),
                llmStream = if (streamMigrated) json.optBoolean("llmStream", defaults.llmStream) else true,
                llmStreamDefaulted = true,
                llmMainBrain = json.optBoolean("llmMainBrain", defaults.llmMainBrain),
                i2iStrength = finiteClamp(
                    json.optDouble("i2iStrength", defaults.i2iStrength), 0.0, 1.0, 0.7,
                ),
                i2iNoise = finiteClamp(
                    json.optDouble("i2iNoise", defaults.i2iNoise), 0.0, 0.99, 0.0,
                ),
                i2iSizeMode = when (json.optString("i2iSizeMode", defaults.i2iSizeMode)) {
                    "core" -> "core"
                    else -> "source"
                },
                i2iSourceMode = when (json.optString("i2iSourceMode", defaults.i2iSourceMode)) {
                    "original" -> "original"
                    else -> "latest"
                },
                upscaleScale = if (json.optInt("upscaleScale", defaults.upscaleScale) == 4) 4 else 2,
                directorTool = json.optString("directorTool", defaults.directorTool),
                augmentDefry = finiteClamp(json.optDouble("augmentDefry", defaults.augmentDefry), 0.0, 5.0, 0.0),
                augmentColorizePrompt = json.optString("augmentColorizePrompt", defaults.augmentColorizePrompt),
                augmentEmotion = json.optString("augmentEmotion", defaults.augmentEmotion),
            augmentKeepTextBubbles = json.optBoolean(
                "augmentKeepTextBubbles",
                defaults.augmentKeepTextBubbles,
            ),
                augmentEmotionLevel = finiteClamp(
                    json.optDouble("augmentEmotionLevel", defaults.augmentEmotionLevel), 0.0, 5.0, 0.0,
                ),
                historyRetentionDays = json.optInt("historyRetentionDays", defaults.historyRetentionDays),
            llmApiUrl = json.optString("llmApiUrl", defaults.llmApiUrl),
            llmApiKey = json.optString("llmApiKey", defaults.llmApiKey),
            llmModel = json.optString("llmModel", defaults.llmModel),
            llmPresets = json.optJSONArray("llmPresets")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { LlmPresetEntry.fromJson(it) }
                }.filter { it.id.isNotBlank() }
            } ?: defaults.llmPresets,
            llmTranslatePreset = json.optString("llmTranslatePreset", defaults.llmTranslatePreset),
            llmOptimizePreset = json.optString("llmOptimizePreset", defaults.llmOptimizePreset),
            llmStoryboardPreset = json.optString("llmStoryboardPreset", defaults.llmStoryboardPreset),
            llmPlanPreset = json.optString("llmPlanPreset", defaults.llmPlanPreset),
            llmReversePreset = json.optString("llmReversePreset", defaults.llmReversePreset),
            llmReasoningEffort = json.optString("llmReasoningEffort", defaults.llmReasoningEffort),
            // 分功能 AI：留空 = 跟随正面提示词那一套，所以缺键取空串即可
            llmTranslateApiUrl = json.optString("llmTranslateApiUrl", defaults.llmTranslateApiUrl),
            llmTranslateApiKey = json.optString("llmTranslateApiKey", defaults.llmTranslateApiKey),
            llmTranslateModel = json.optString("llmTranslateModel", defaults.llmTranslateModel),
            llmOptimizeApiUrl = json.optString("llmOptimizeApiUrl", defaults.llmOptimizeApiUrl),
            llmOptimizeApiKey = json.optString("llmOptimizeApiKey", defaults.llmOptimizeApiKey),
            llmOptimizeModel = json.optString("llmOptimizeModel", defaults.llmOptimizeModel),
            llmStoryboardApiUrl = json.optString("llmStoryboardApiUrl", defaults.llmStoryboardApiUrl),
            llmStoryboardApiKey = json.optString("llmStoryboardApiKey", defaults.llmStoryboardApiKey),
            llmStoryboardModel = json.optString("llmStoryboardModel", defaults.llmStoryboardModel),
            llmPlanApiUrl = json.optString("llmPlanApiUrl", defaults.llmPlanApiUrl),
            llmPlanApiKey = json.optString("llmPlanApiKey", defaults.llmPlanApiKey),
            llmPlanModel = json.optString("llmPlanModel", defaults.llmPlanModel),
            llmReverseApiUrl = json.optString("llmReverseApiUrl", defaults.llmReverseApiUrl),
            llmReverseApiKey = json.optString("llmReverseApiKey", defaults.llmReverseApiKey),
            llmReverseModel = json.optString("llmReverseModel", defaults.llmReverseModel),
            translateService = when (json.optString("translateService", defaults.translateService)) {
                "baidu" -> "baidu"
                else -> "llm"
            },
            baiduTranslateAppId = json.optString("baiduTranslateAppId", defaults.baiduTranslateAppId),
            baiduTranslateSecret = json.optString("baiduTranslateSecret", defaults.baiduTranslateSecret),
            optimizeRuleMode = PromptRules.MODES.firstOrNull {
                it == json.optString("optimizeRuleMode", defaults.optimizeRuleMode)
            } ?: defaults.optimizeRuleMode,
            optimizeRulesTag = json.optString("optimizeRulesTag", defaults.optimizeRulesTag),
            optimizeRulesNatural = json.optString("optimizeRulesNatural", defaults.optimizeRulesNatural),
            optimizeRulesMixed = json.optString("optimizeRulesMixed", defaults.optimizeRulesMixed),
            reverseRuleMode = PromptRules.MODES.firstOrNull {
                it == json.optString("reverseRuleMode", defaults.reverseRuleMode)
            } ?: defaults.reverseRuleMode,
            reverseRulesTag = json.optString("reverseRulesTag", defaults.reverseRulesTag),
            reverseRulesNatural = json.optString("reverseRulesNatural", defaults.reverseRulesNatural),
            reverseRulesMixed = json.optString("reverseRulesMixed", defaults.reverseRulesMixed),
            comicStoryboardRules = json.optString("comicStoryboardRules", defaults.comicStoryboardRules),
            comicPagePlanRules = json.optString("comicPagePlanRules", defaults.comicPagePlanRules),
            comicRulesVersion = json.optInt("comicRulesVersion", 0),
            comicAutoGenerate = json.optBoolean("comicAutoGenerate", defaults.comicAutoGenerate),
            comicMaxPages = json.optInt("comicMaxPages", defaults.comicMaxPages)
                .coerceIn(ComicStoryboard.MIN_PAGES, ComicStoryboard.MAX_PAGES),
                galleryLayout = json.optString("galleryLayout", defaults.galleryLayout)
                    .takeIf { it in setOf("grid", "list") } ?: "grid",
                galleryColumns = json.optInt("galleryColumns", defaults.galleryColumns).coerceIn(2, 6),
                gallerySort = json.optString("gallerySort", defaults.gallerySort)
                    .takeIf { it in setOf("time", "random") } ?: "time",
                gallerySortDescending = json.optBoolean(
                    "gallerySortDescending", defaults.gallerySortDescending,
                ),
            )
        }
    }
}

/** 历史分组。JSON 结构与参考实现一致：{id, name, createdAt}。 */
data class HistoryGroup(
    val id: String,
    val name: String,
    val createdAt: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryGroup = HistoryGroup(
            id = json.optString("id"),
            name = json.optString("name", ""),
            createdAt = json.optString("createdAt", ""),
        )
    }
}

/**
 * 历史记录条目。JSON 字段名与参考实现一致。
 *
 * `params` 存生成当时的 [GenerateParams] 快照，用于"复用参数"。
 */
data class HistoryItem(
    val id: String,
    val filePath: String,
    /** YYYY-MM-DD。 */
    val date: String,
    /** ISO8601。 */
    val createdAt: String,
    val seed: Long,
    val model: String,
    val width: Int,
    val height: Int,
    val prompt: String,
    /** t2i / i2i / inpaint / upscale / director-<tool>。 */
    val feature: String = "t2i",
    /**
     * 仅官方放大版有值：**放大前那张图的路径**。
     * 图库据此把「放大前 + 放大后」并成同一个格子，也据此判断"编辑要用放大前那张"。
     */
    val upscaleOfPath: String? = null,
    val groupId: String? = null,
    val params: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        put("date", date)
        put("createdAt", createdAt)
        put("seed", seed)
        put("model", model)
        put("width", width)
        put("height", height)
        put("prompt", prompt)
        put("feature", feature)
        put("upscaleOfPath", upscaleOfPath ?: JSONObject.NULL)
        put("groupId", groupId ?: JSONObject.NULL)
        put("params", params)
    }

    companion object {
        fun fromJson(json: JSONObject): HistoryItem = HistoryItem(
            id = json.optString("id"),
            filePath = json.optString("filePath"),
            date = json.optString("date"),
            createdAt = json.optString("createdAt"),
            seed = json.optLong("seed", 0L),
            model = json.optString("model", ""),
            width = json.optInt("width", 0),
            height = json.optInt("height", 0),
            prompt = json.optString("prompt", ""),
            feature = json.optString("feature", "t2i"),
            upscaleOfPath = if (json.isNull("upscaleOfPath")) null else json.optString("upscaleOfPath"),
            groupId = if (json.isNull("groupId")) null else json.optString("groupId"),
            params = json.optJSONObject("params") ?: JSONObject(),
        )

        fun listFromJson(text: String): List<HistoryItem> = try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { fromJson(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
