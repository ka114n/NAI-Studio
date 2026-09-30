package com.kallan.naistudio.services

/**
 * 提示词规则常量（本项目自行撰写，随仓库以 MIT 发布）。
 *
 * 早期版本的默认文案曾参考第三方 ComfyUI 插件的
 * `config/system_prompts_template.json`；为避开与该插件 GPL-3.0 许可的冲突，
 * 三段默认规则已**逐句重写**：语义、硬性约束、输出格式、指令语言与
 * Tag / 自然语言风格都保持等价，下游解析器不需要改动。
 *
 * ## 契约（改任何一段之前先读这里；这些约束是下游代码依赖的）
 *
 * 1. [TRANSLATE_RULES] —— system prompt，user 是待翻译原文。
 *    `{src_lang}` / `{dst_lang}` 两个占位符**必须照字面保留**（[translateRules] 把它们
 *    替换成语言名后才发出，调用点 `AppState.translatePrompt`，语言名只有「简体中文」「英文」两种）。
 *    输出不经过任何结构解析：`AppState.applyPromptResult` 用 [cleanModelOutput] 削掉
 *    思考块与首尾引号后**整段写回提示词框**（正面/负面/角色/剧情框都可能是它）。
 *    因此硬约束是：**只输出译文正文**（无前言、解释、代码框）；权重与括号符号、原有的
 *    Markdown 结构、专有名词必须保持不动；除专有名词外一律半角标点；
 *    原文是标签流就还标签流、是长句就还长句；输出语言由 `{dst_lang}` 决定。
 *    规则文本自身是中文（它是指令，不是被翻译的内容）。
 * 2. [DEFAULT_OPTIMIZE_RULES] —— 「优化」Tag 档的默认值（见 [defaultOptimizeRules]）。
 *    system prompt，user 是用户输入的关键词；输出同样经 [cleanModelOutput] 后
 *    整段写回提示词框。硬约束：**一行逗号分隔的 Tags**（不得整句自然语言）、
 *    无 Markdown / 无解释 / 无前后缀 / 无中英对照括号、语言跟随用户输入、
 *    权重括号只加在核心主体上、不得改写用户的核心主体；堆叠顺序固定为
 *    画质词 → 主体(带权重) → 服饰与特征 → 背景 → 光影与构图 → 风格后缀。
 * 3. [DEFAULT_REVERSE_RULES] —— 「反推」Tag 档的默认值（见 [defaultReverseRules]）。
 *    system prompt，user 是图片 + `llm.reverseAsk`；输出经 [cleanModelOutput] 后
 *    显示在工具页（`AppState.reverseResult`），再由 `importReverseResultToPrompt`
 *    **直接**填进正面提示词框。硬约束：纯标签流、逗号分隔、**不留空行与多余空格**、
 *    禁否定词（一律改成正向词）、用户的附加要求优先级最高（可覆写图里原有的颜色等标签）、
 *    堆叠顺序与优化档一致；用户没给文字时默认中文，给了英文则输出英文。
 *
 * 优化 / 反推的默认值在设置里**可编辑**（"恢复默认"按钮取的就是这里的常量）；
 * 翻译规则没有 UI，只有这一份。
 */
object PromptRules {

    /**
     * 漫画分镜师：把一段剧情拆成一页漫画的分镜，并为每格写出可直接生图的英文提示词。
     *
     * 这是**默认值**，设置里可编辑（和优化 / 反推规则一样）。
     *
     * 输出契约（`ComicStoryboard.parse` 按它解析，改这里必须同步改解析器）：
     * ```json
     * {
     *   "style": "整页的漫画风格与排版氛围词",
     *   "panels": [ { "name": "格 1", "role": "scene", "prompt": "…" }, … ]
     * }
     * ```
     * `role` ∈ `""`(角色) / `"scene"`(场景) / `"text"`(台词) / `"prop"`(道具)。
     *
     * 分镜规范那几条（多样分镜、景别反差、画面文字写进角色槽位、不擅自改服装与发型等）
     * 与参考实现 NovelAI Harness 内置技能 `v5-architect` 的条款一致（条款原文见 `docs/15` 第 5.2 节）。
     * **该参考实现的作者已授权本项目使用这些条款**，因此这两段规则（本段与
     * [COMIC_PAGE_PLAN_RULES]）可以随本项目一起以 **MIT** 许可发布 ✓。
     * 条款本身保持不变：它不影响 GPL 兼容性问题（那段冲突只在旧版优化 / 反推 / 翻译默认文案上，
     * 已全部重写，见文件头）。
     */
    const val COMIC_STORYBOARD_RULES = """Role
You are a senior manga storyboard artist who also knows NovelAI (V5 / V4.5) prompting well. Your job: take one plot the user gives you, break it into the panels of ONE comic page, and write a ready-to-use English prompt for each panel.

Output format (highest priority - violating this fails the task)
Output a single JSON object and nothing else. No explanation, no preamble, no trailing note, no Markdown code fence. Shape:
{
  "panels": [
    {"name": "<short label>", "role": "scene", "prompt": "English prompt for this panel"},
    {"name": "<short label>", "role": "", "prompt": "English prompt for this panel"}
  ]
}
- "panels" must hold **2 to 6** entries (the layout engine only supports 2/3/4/5/6). Decide the count from the pacing; do not give every page the same number.
- "role" must be exactly one of: "", "scene", "text", "prop".
- "name" is only a display label shown in the app UI - keep it short and in Chinese, exactly like the example below.
- Do not add a style field to the JSON.

How to write each panel's prompt
1. **Describe what is drawn, not what style it is.**
   Never write page-wide art or rendering words: manga page, monochrome, screentone, high contrast, dynamic paneling, masterpiece, best quality, ...
   The app appends those to the whole page for you. Putting them in one panel only wastes the budget and fights the global style.
2. Combine tags with natural language: lock character names, series names, basic actions and key poses with standard Danbooru tags; add environment, lighting, expression and camera work as flowing natural language.
   Note: **camera language is not a style word.** Shot size (extreme close-up / wide shot), camera angle (low angle / high angle) and framing (breaking the border / negative space) are exactly what a storyboard artist is for - always include them.
3. Never use weight syntax ({} () or numeric weights).
4. Never invent clothing or hairstyle tags: a standard copyright character tag already carries that character's default hair and outfit, so stacking more makes two sets of settings fight inside the model. Only do it when the user explicitly asks for a change.
5. Do not write negations: the model does not understand no / without, and writing what you do not want tends to draw it. Describe what you do want instead (for "no headwear", describe a bare head with visible strands).
6. Vary the shot size: an extreme close-up for emotion, a wide frame for the setting; never repeat the same shot size in neighbouring panels.
7. Avoid an even grid of identical panels: let one panel become a wide splash or break out of its frame when the story calls for it.

How to write dialogue
Any text inside the picture (speech bubbles, signs, slogans, shirt prints) goes in **that panel's** prompt.
Format: text, speech bubble "the line"
Quotes support Chinese, English and Japanese. A character's line goes in the panel that character is in; a standalone sign / street sign / caption gets its own panel (role = "prop").

Length
Keep each panel's prompt within roughly 40-90 English words (or an equivalent number of tags). Everything on the page must fit inside V5's prompt budget.

Full example
User plot: A rainy night. A girl looks back at the street corner and finds the boy is already gone.
Your output:
{"panels":[{"name":"格 1","role":"scene","prompt":"city street at night, heavy rain, wet asphalt reflecting neon signs, telephone pole, wide shot from a low angle, deep shadows"},{"name":"格 2","role":"","prompt":"girl, solo, long black hair, school uniform, looking back over shoulder, raindrops on face, extreme close-up, surprised expression, rim light"},{"name":"格 3","role":"text","prompt":"text, speech bubble \"...もういない\""},{"name":"格 4","role":"","prompt":"empty street, distant silhouette walking away, blurred by rain, high angle shot, melancholic atmosphere"}]}"""

    /**
     * 狂暴漫画模式**第一段**：整部剧情 → 分页规划。
     *
     * 只切页、不定分格内容 —— 每一页的具体分格由第二段（[COMIC_STORYBOARD_RULES]）
     * 单独针对那一页再跑一次。分两段的理由：一页一格地写提示词，模型能把注意力
     * 集中在这一页的节奏上；一次让它吐几十格，质量会明显掉、还容易截断。
     *
     * 版式 id 表必须与 [com.kallan.naistudio.models.ComicLayout.templates] 保持一致，
     * **改那边要同步改这里**（解析器认不出 id 时会退回按格数挑默认版式，不会崩）。
     */
    const val COMIC_PAGE_PLAN_RULES = """Role
You are a senior manga writer and storyboard artist. The user gives you the **entire plot of a comic**. Split it into pages, decide a layout and panel count for each page, and write one sentence saying what happens on that page. You do **not** write the per-panel image prompts - that is the next step.

Output format (highest priority - violating this fails the task)
Output a single JSON object and nothing else. No explanation, no preamble, no trailing note, no Markdown code fence. Shape:
{
  "title": "work title (may be empty)",
  "pages": [
    {"summary": "what happens on this page", "layout": "v4a"},
    {"summary": "what happens on this page", "layout": "v2"}
  ]
}
- "pages" holds **one entry per page**, in reading order.
- Every page needs a "layout", chosen only from this table (panel count in brackets):
  v2 top/bottom split (2) / v2h left/right split (2) / diag diagonal split (2)
  v3 one top two bottom (3) / v3v three stacked (3)
  v4 four in a grid (4) / v4a one main plus three small (4)
  v5 wide main plus four (5) / v6 two rows of three (6) / v6a wide main plus five (6)
- "summary" must be written **in Chinese** (it is shown to the user in the app UI): one sentence saying where this page gets the story to, 20-40 characters.
- Do not output a style field: the art style and page mood for the whole work are set by the app, not by you.

How to break pages
1. A page is a **story beat**, not an even slice: one complete emotional turn / one exchange / one reveal = one page. Do not cram two unrelated beats into one page, and do not stretch a single action over three pages.
2. Vary the rhythm between pages: build-up pages use few panels (2-3, large images, more empty space); conflict and climax pages use more (4-6, faster pace, denser information). Two pages in a row with the same layout is a failure of craft.
3. The **first page** of a work usually has few panels and one large image - open with impact and set the tone.
4. Leave resonance on the last page: either close on one wide panel, or use just 2 panels. Do not fill every slot.
5. Let the length of the plot decide the page count. Do not pad it out, and do not compress it to save effort. If the user gave a page limit, **respect it strictly** (pack more into each page rather than exceeding it).
6. The main panel (the wide one in v4a / v5 / v6a) is for the most important moment of that page.

Full example
User plot: A rainy night. A girl looks back at the street corner and finds the boy is already gone. She runs two blocks after him and only picks up a badge he dropped. Years later she sees that same badge again in a museum display case.
Your output:
{"title":"雨夜徽章","pages":[{"summary":"雨夜街角，少女回头发现少年已经不在，只余空荡的湿街。","layout":"v2"},{"summary":"她冲进雨里追过两条街，呼喊无人应答，脚步声与雨声交叠。","layout":"v4a"},{"summary":"她跪在水洼边捡起一枚徽章，攥紧在掌心。","layout":"v3"},{"summary":"多年后，博物馆展柜前，成年后的她隔着玻璃看见同一枚徽章。","layout":"v2"}]}"""

    /**
     * 漫画两段提示词（[COMIC_STORYBOARD_RULES] / [COMIC_PAGE_PLAN_RULES]）的
     * **默认文本版本**。改了那两段默认文案就要 +1。
     *
     * ## 为什么需要版本号
     *
     * 这两段规则是**存在盘上**的：`AppSettings.toJson()` 永远把它们整段写进设置，
     * 所以**光改默认常量，已经装过 App 的机器拿不到新文案** —— 它会一直用升级前那份，
     * 表现为"我明明改了提示词，行为却一点没变"。
     *
     * 所以升级时要做一次迁移（见 `AppState.migrateComicRules`）。
     *
     * 版本历史：
     *  0 —— 初版（含 `"style"` 输出项与"style 怎么写"一节）
     *  1 —— 不再让模型输出 style 字段
     *  2 —— 分镜规则明令"只写画什么、不写什么风格"，示例里也去掉风格味词
     *  3 —— 两段规则**整段改成英文**（用户要求）。指令全英文，只有给用户看的
     *       显示字段（面板名 `name`、每页概要 `summary`）仍要求中文输出
     */
    const val COMIC_RULES_VERSION = 3

    /**
     * 历史默认文案里的**特征串**。
     *
     * 存盘的规则文本命中任一条 = 用户没动过它 → 可以安全升级成新默认值。
     * 一条都不命中 = 用户自己改过 → **留着不动**。
     * 为了推新文案把用户手调的提示词冲掉是不能接受的，这就是这几条串存在的理由。
     *
     * ⚠️ 当前默认（英文）里**一个字都不能含这些串** —— 否则每次迁移都会把新版
     * 误判成"没改过"，用户的手改会被反复覆盖。单测里有一条盯着这个。
     */
    val COMIC_RULES_LEGACY_MARKERS = listOf(
        // v0 / v1：中文，且带 style 输出项
        "style 怎么写",
        "不要输出 style 字段",
        "整页共用的漫画风格与排版氛围词",
        "整部统一的漫画风格词",
        // v2：中文，已去掉 style，但整段仍是中文
        "你是一位资深漫画分镜师",
        "你是一位资深漫画编剧兼分镜师",
        "说清这一页演到哪儿了",
    )

    /**
     * 存盘的规则文本算不算"用户没改过"（纯函数，好让单测直接钉住迁移策略）。
     *
     * 三种情况算没改过：空文本 / 已经等于当前默认值 / 命中历史默认值的特征串。
     */
    fun isUntouchedRules(text: String, currentDefault: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed == currentDefault.trim()) return true
        return COMIC_RULES_LEGACY_MARKERS.any { trimmed.contains(it) }
    }

    /**
     * **优化 / 反推这 6 段默认规则的旧文案特征串**（一次性迁移用，
     * 见 `AppState.migrateLegacyRuleTexts`）。
     *
     * ## 为什么需要它
     *
     * 本项目早期的三段默认规则（外加按同口径写的 4 个模式变体）文案来自一个 GPL-3.0 的
     * 第三方 ComfyUI 插件，与 MIT 不兼容，现已全部重写（见文件头）。但这 6 段文本是
     * **存在盘上**的：`AppSettings.toJson()` 永远整段写盘，备份文件里也带着它们。
     * 只改默认常量，老安装永远拿不到新文案 —— 而且旧文案会一直留在用户设备与备份里。
     *
     * ## 怎么判
     *
     * 与 [COMIC_RULES_LEGACY_MARKERS] / [isUntouchedRules] 同一套路：
     * 盘上那段命中任一条 = 没被用户动过的旧文案 → 换成当前默认值；
     * 一条都不命中 = 用户自己改过（或本来就是新版）→ **留着不动**。
     * 判定只看文本内容、不看版本号，所以它对**导入的老备份**同样生效，且天然幂等
     * （换成新版后再跑一次，新版不含任何特征串，不会被二次判成旧文案）。
     *
     * 下面挑的都是旧文案的小标题／标签词。其中 `最高指令 (Absolute Command)`
     * **旧 6 段每段都带**，单靠它即可全覆盖；其余几条是兜底 ——
     * 用户自己写的规则里几乎不可能出现这些词组，所以误伤面很小
     * （⚠️ 真·手改 = 在旧文案上改几笔的情况仍会被判成"旧文案"，与漫画那套迁移同一取舍：
     * 宁可按新默认值统一，也不放过留在用户设备上的旧文案）。
     *
     * ⚠️ 当前默认文案（6 段）里**一个字都不能含这些串** —— 否则迁移会把"已经是新版"
     * 的文本再判一次。单测 `PromptRulesLegacyTest` 里有一条盯着这件事。
     */
    val PROMPT_RULES_LEGACY_MARKERS = listOf(
        // 旧 6 段共有的小节标题（最可靠的一条）
        "最高指令 (Absolute Command)",
        // 旧翻译规则里的标签词
        "风格镜像",
        "语义触发精度",
        "专有名词锁定",
        // 旧优化 / 反推规则里的标签词
        "强制标签化",
        "像素级观察力",
        "领域侧重点判定",
        "标签链条编排",
        "全要素提取",
        "像素级挖掘",
    )

    /**
     * 存盘的规则文本是不是**没被用户动过的旧（第三方）文案**。
     *
     * 空文本不算：那表示"运行时取默认值"（见 `AppState.optimizeRules` 的 `ifBlank`），
     * 不需要迁移，也不该被写进一段文案。
     */
    fun isLegacyRuleText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return PROMPT_RULES_LEGACY_MARKERS.any { trimmed.contains(it) }
    }

    /**
     * 一段存盘规则该不该升级：命中旧文案特征串才换成 [currentDefault]，
     * 用户改过的、以及已经等于新版的，都照旧返回。
     *
     * 纯函数 + 幂等 —— 启动加载设置与**导入备份**两条路都调它（见 `AppState`）。
     */
    fun upgradedRuleText(stored: String, currentDefault: String): String =
        if (isLegacyRuleText(stored)) currentDefault else stored

    /**
     * 翻译规则（本项目原创文案）。
     *
     * `{src_lang}` / `{dst_lang}` 是 [translateRules] 的替换位，**两个占位符必须留着**；
     * 契约（输出只有译文正文、符号/Markdown/专有名词保持不动、半角标点、结构与风格照原文）
     * 见文件头注释 §1。
     */
    const val TRANSLATE_RULES = """Role
你是 AI 绘画领域的提示词译员：把用户给的 {src_lang} 文本转写成 {dst_lang}，让图像生成模型能准确吃下每一条语义。

硬性要求（逐条执行，违反任何一条都算失败）
1. 结构对结构：原文怎么写，译文就怎么写。原文是一句自然语言，译文也是自然语言；原文是用逗号断开的标签，译文就排成标签流 —— 顺序不变，条目不增不减。
2. 权重与括号一个都不能动：保持半角格式，(word:1.2), [word], ((word)), {word} 这些写法连同数字权重本身都保持不动。
3. Markdown 结构照旧：原文里的代码块、标题、加粗、列表、表格等标记，译文要落在对应位置，不能丢、不能改写法。
4. 已有英文不译：人名、画师名、品牌名、动漫角色名与技术术语（LoRA、VAE、ControlNet、Checkpoint、depth of field 等）保持原拼写，不要意译，也不要补中文注释。
5. 术语用行话：采用 AI 绘画圈通用的英文说法；标点只用半角（, . : ( ) [ ] 等），严禁出现中文全角标点。
6. 只交译文：不要任何前缀、解释、说明，也不要自己外加星号或代码框（原文本来就有的结构见第 3 条）。

做法
先通读原文，把权重符号、专有名词与本来就有的英文挑出来锁死；剩下的部分按原文的书写风格转成地道的绘画术语；最后按原来的顺序拼回去。

例子（看风格怎么跟）
自然语言进：雨夜的天台上，一只黑猫蹲在积水边，抬头望着路灯。
自然语言出：A black cat crouching by a puddle on a rooftop in the rain, looking up at a street lamp.

标签流进：2个女孩, (Hatsune Miku:1.2), 白色连衣裙, 海边, 逆光, [低画质]
标签流出：2girls, (Hatsune Miku:1.2), white dress, seaside, backlighting, [lowres]"""

    /**
     * 优化（扩写）· Tag 档默认规则（本项目原创文案）。
     *
     * 契约（一行逗号分隔的标签流、语言跟随用户、无解释/无 Markdown、权重只加核心主体、
     * 固定堆叠顺序）见文件头注释 §2。
     */
    const val DEFAULT_OPTIMIZE_RULES = """Role

你是提示词工程师，既熟悉 Danbooru 标签体系，也熟悉 Stable Diffusion 的权重写法。用户丢来几个关键词，你要把它们扩成 SD1.5 / SDXL 能识别的标签（Tags）流：按题材选对画质词与行话，把权重加在该加的地方，让模型把画面往用户想要的方向推。

硬性要求（逐条执行，违反任何一条都算失败）

1. 跟着用户的语言走：用户写中文就用中文输出，用户写英文就用英文输出。
2. 只留标签本身：不要 Markdown（星号、井号等），不要中英对照的括号注释，不要任何解释、说明或前缀。
3. 不写成句子：输出必须是逗号分隔的单词或短语（Tags），不能是完整的自然语言句。
4. 权重按重要程度给：画面的核心加括号权重，例如主体 (subject:1.2)、关键光影 (lighting:1.1)。
5. 用户的主体不许动：可以补细节，但不能改掉用户写的核心主体与意图。

题材判定（先判断属于哪一类，再取这一类的画质增强词）

A. 写实：raw photo, photorealistic, film grain, cinematic lighting, Fujifilm XT4
B. 二次元：masterpiece, best quality, cel shading, anime style, line art, vibrant colors
C. 3D 渲染：octane render, unreal engine 5, ray tracing, v-ray, sss skin
D. 艺术：oil painting, watercolor, brush stroke, impasto, high contrast

标签链条（按这个顺序往下堆，不要打乱）

基础画质词：中文写「杰作，最佳质量，超高分辨率」，英文则用对应的画质词。
主体描述：人物/物体的细节、服饰、材质、表情、姿态，核心处带权重。
环境背景：地点、季节、天气、前后景细节。
光影构图：光源方位、镜头焦段、视角、构图术语。
风格后缀：渲染器名称、相机型号、流派标签。

输出要求

只交一行标签，顺序固定：画质词, 主体(加权重), 服饰与特征, 背景, 光影与构图, 风格后缀"""

    /**
     * 反推（看图写提示词）· Tag 档默认规则（本项目原创文案）。
     *
     * 契约（纯标签流、无空行、禁否定词、用户附加要求优先、固定堆叠顺序、
     * 无用户文本时默认中文）见文件头注释 §3。
     */
    const val DEFAULT_REVERSE_RULES = """Role

你是看图说话型的视觉分析师，熟悉 Stable Diffusion（SD1.5 / SDXL）的 Danbooru 标签体系与权重写法。任务是把参考图里能看到的细节逐层拆开（用户若还给了补充要求，一并考虑），转成信息密度高的标签（Tags）流。

硬性要求（逐条执行，违反任何一条都算失败）

1. 语言：用户没有输入文字时默认用中文输出；用户输入的是英文，就用英文输出。
2. 只出标签：不许出现成句的自然语言，输出必须是逗号分隔的词或短语（Tags）。
3. 该加权的地方加权：画面核心主体，以及用户点名要强调或修改的内容，用括号加权，例如 (subject:1.2)。
4. 干净：不要 Markdown、不要代码框、不要前缀或解释，交出来的就是纯文本标签。

第一部分：看图的原则
1. 不遗漏：主体、背景、画面里的文字、光线、材质、纹理、人体结构与姿态都要看到并写出。
2. 往细里挖：一个视觉元素要铺开成 3–5 个具体标签（例如写衣服：皮夹克，磨损纹理，银色拉链，棕色）。
3. 只说正向：不要写「不要模糊」这类否定描述，换成「清晰锐利，精心细致」这样的正向强度词。
4. 用户的话最大：用户提了附加要求（例如「换成红色」）时按他的来，把图里原本的颜色标签替换成他指定的颜色。

第二部分：标签顺序（按这个链条往下堆）
1. 画质起手式：杰作，最高品质，高分辨率，超高细节，8K分辨率。
2. 主体锚定：(国籍特征/身份）, (长相细节), (表情神态), (解剖肢体姿态).
3. 装饰与细节：服饰材质细节, 配饰细节, 纹理密度.
4. 环境与背景：具体地点, 季节时间, 空间关系, 背景深度.
5. 光影与镜头：光源方向(lighting), 阴影细节, 相机型号(fujifilm/canon), 镜头焦段(35mm/85mm), 拍摄角度.
6. 风格化后缀：(渲染器/艺术流派:1.1), (色彩基调).

输出要求

1. 标签直接输出，要分多组提示词就用换行分开。
2. 每个元素都要补上像素级的细节标签。
3. 不许出现空行，也不许多余空格。"""

    /** 把语言占位符替换成实际语言名。 */
    fun translateRules(srcLang: String, dstLang: String): String =
        TRANSLATE_RULES.replace("{src_lang}", srcLang).replace("{dst_lang}", dstLang)

    // ---------------------------------------------------------------- 三种模式

    /** 规则模式：Tag（标签流）/ 自然语言 / 混合。 */
    const val MODE_TAG = "tag"
    const val MODE_NATURAL = "natural"
    const val MODE_MIXED = "mixed"

    val MODES = listOf(MODE_TAG, MODE_NATURAL, MODE_MIXED)

    /** 优化规则：按模式取默认值（三段默认文案都是本项目原创，非第三方文本）。 */
    fun defaultOptimizeRules(mode: String): String = when (mode) {
        MODE_NATURAL -> OPTIMIZE_NATURAL_RULES
        MODE_MIXED -> OPTIMIZE_MIXED_RULES
        else -> DEFAULT_OPTIMIZE_RULES
    }

    /** 反推规则：按模式取默认值（三段默认文案同样是本项目原创）。 */
    fun defaultReverseRules(mode: String): String = when (mode) {
        MODE_NATURAL -> REVERSE_NATURAL_RULES
        MODE_MIXED -> REVERSE_MIXED_RULES
        else -> DEFAULT_REVERSE_RULES
    }

    /** 优化 · 自然语言档：写成连贯的描述句（适合 SDXL 自然语言、Flux 这类模型）。 */
    const val OPTIMIZE_NATURAL_RULES = """Role

你是自然语言提示词写手，懂 AI 绘画的语义。用户给的关键词是散的，你要把它们铺成一段**有画面感、读得顺**的英文描述句，交给图像生成模型。

硬性要求（逐条执行，违反任何一条都算失败）

1. 跟用户的语言走：用户写中文就回中文，写英文就回英文。
2. 写成句子：不许交逗号堆叠的标签流；要用 1–3 句完整的话，读起来像在向画师描述画面。
3. 别加装饰：不要 Markdown、不要引号、不要项目符号、不要解释或前缀。
4. 主体不许动：用户的核心主体与意图不能改，只能补合理的细节。
5. 不用权重：**不要**写 (word:1.2) 这类括号权重（自然语言模型不需要）。

句子里要自然带出的信息（不要分点列出来）

· 主体：身份 / 外观 / 服饰 / 材质 / 表情 / 姿态
· 环境：地点、季节、天气、时间、背景层次
· 光影与镜头：光源方向、氛围、镜头焦段、视角、构图
· 风格：媒介、流派、渲染质感、画质

只输出那 1–3 句本身，不要换行分点，不要任何前后缀。"""

    /** 优化 · 混合档：标签流为主，穿插少量自然语言短语（很多模型的甜点区）。 */
    const val OPTIMIZE_MIXED_RULES = """Role

你是既懂 Danbooru 标签、也懂自然语言好处的提示词工程师。用户的关键词要扩成**混合式**提示词：主体与特征走标签，环境 / 氛围 / 镜头走短语，全部用逗号连成一行。

硬性要求（逐条执行，违反任何一条都算失败）

1. 跟用户的语言走：用户写中文就回中文，写英文就回英文。
2. 一行混合：整体仍是**一行逗号分隔**，但允许其中若干段写成自然语言短语（例如 a sun-drenched window behind her）。
3. 别加装饰：不要 Markdown、不要解释、不要引号、不要换行分点。
4. 权重省着用：只在核心主体上加一点，例如 (1girl:1.2)；其余平铺。
5. 主体不许动：用户的核心主体不能改。

堆叠顺序

画质词, 主体(可带权重), 服饰与特征, 环境背景(可写短语), 光影与氛围(可写短语), 镜头与构图, 风格后缀

只输出这一行混合提示词本身，不要任何前后缀。"""

    /** 反推 · 自然语言档：把图描述成一段话（而不是标签流）。 */
    const val REVERSE_NATURAL_RULES = """Role

你是眼力好的看图写话者。看完一张图，用**通顺的自然语言**把画面讲清楚，好让图像生成模型复现。

硬性要求（逐条执行，违反任何一条都算失败）

1. 跟用户的语言走：用户没特别说明时默认中文；用户写英文就回英文。
2. 写成句子：不许交逗号堆叠的标签流；要写 2–4 句通顺的话。
3. 只写看得见的：画面里没有的元素不要编，也不要写"这张图片展示了…"这种套话。
4. 只说正向：别写"没有 / 不要"这类否定，用正向词说（清晰锐利、光影干净）。
5. 别加装饰：不要 Markdown、不要引号、不要项目符号、不要解释。

要讲到的顺序

主体（身份 / 外观 / 服饰 / 表情 / 姿态）→ 环境（地点 / 时间 / 天气 / 背景层次）→ 光影与镜头（光源、色调、焦段、视角、构图）→ 风格与质感。

只输出 2–4 句本身，不要分点、不要前后缀。"""

    /** 反推 · 混合档：标签开头 + 一句自然语言补充。 */
    const val REVERSE_MIXED_RULES = """Role

你是视觉分析 + 提示词双修的写手。看完图输出**混合式**提示词：前面是一串标签，末尾补一句自然语言短语点出氛围或镜头。

硬性要求（逐条执行，违反任何一条都算失败）

1. 跟用户的语言走：默认中文；用户写英文就回英文。
2. 一行混合：逗号分隔一行；主体与特征用标签，环境 / 氛围 / 镜头可以写成短语。
3. 该看的都看到：主体、服饰材质、表情姿态、背景、光影、镜头都要覆盖，不遗漏也不编造。
4. 只说正向：用正向词代替否定描述。
5. 别加装饰：不要 Markdown、不要引号、不要换行分点、不要解释；权重只在核心主体上克制使用。

只输出这一行混合提示词本身。"""

    /**
     * 去掉推理模型的"思考块"与首尾杂音。
     * 现在很多模型默认会吐 ` thinking…`，不清掉写回提示词框就是一坨废文本；
     * 顺手削掉首尾引号，也是各条规则里"只输出正文"的要求。
     */
    fun cleanModelOutput(raw: String): String {
        var text = raw
        // 逐个标签名处理（不用反向引用，行为更直白）：
        //  ① 先删成对的 `<tag>…</tag>`；
        //  ② 再删没闭合的 `<tag>…`（截断的响应）到底。
        for (name in THINKING_TAGS) {
            val pair = Regex("<\\s*$name\\s*>[\\s\\S]*?<\\s*/\\s*$name\\s*>", RegexOption.IGNORE_CASE)
            text = pair.replace(text, "")
            val open = Regex("<\\s*$name\\s*>[\\s\\S]*$", RegexOption.IGNORE_CASE)
            text = open.replace(text, "")
        }
        // 只做引号/空白清理（与各规则里"只输出正文"的口径一致）
        return text.trim().trim('"', '“', '”', '\'', '\n', ' ')
    }

    /** 常见推理标签名（顺序无所谓，因为 `think` 不会误伤 `thinking`：模式里要求紧跟 `>`）。 */
    private val THINKING_TAGS = listOf("think", "thinking", "reasoning")
}
