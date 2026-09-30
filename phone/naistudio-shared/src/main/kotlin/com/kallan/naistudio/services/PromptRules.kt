package com.kallan.naistudio.services

/**
 * 提示词规则常量。
 *
 * 三段规则**原样取自** ComfyUI 插件 `prompt-assistant`（「提示词小助手」）的
 * `config/system_prompts_template.json`：
 *  · [TRANSLATE_RULES] ← `translate_prompts.ZH`
 *  · [DEFAULT_OPTIMIZE_RULES] ← `expand_prompts["expand_扩写-Tags风格"]`
 *  · [DEFAULT_REVERSE_RULES] ← `vision_prompts["vision_zh_图像描述-Tag风格"]`
 *
 * 翻译规则里的 `{src_lang}` / `{dst_lang}` 由 [translateRules] 替换后发出；
 * 优化与反推规则在设置里**可编辑**（这两个常量只是默认值，"恢复默认"按钮用它们）。
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
     * 分镜规范那几条的出处：参考实现 NovelAI Harness 的内置技能 `v5-architect`
     * 里"画漫画与多样分镜""画面文字写在角色槽位里""严禁擅自添加衣服/发型"等条款
     * （见 `docs/15` 第 5.2 节）。
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
     * 一条都不命中 = 用户自己改过 → **原样留着**。
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

    /** 翻译：原文照搬插件（含 `{src_lang}` / `{dst_lang}` 占位符）。 */
    const val TRANSLATE_RULES = """Role
你是一位精通 AI 绘画语法的提示词翻译专家，负责将 {src_lang} 准确转译为 {dst_lang}。你的核心任务是确保翻译后的内容在图像生成模型中具备最高的语义触发精度。

最高指令 (Absolute Command)
1. 风格镜像：严格保持原文的语感与书写结构。若原文是自然语言长句，则对应翻译为长句；若原文是逗号分隔的标签（Tags），则对应翻译为标签流。
2. 符号保护：严禁改动任何权重符号与括号结构。必须保持半角格式，严格保留如 (word:1.2), [word], ((word)), {word} 等所有原始标点。
3.Markdown 语法保护：严禁改动或删除原文中的 Markdown 格式符号。如果原文包含 Markdown 语法（如代码块、标题、加粗、表格等），必须在翻译后的对应位置严格保留这些符号及其结构。
4. 专有名词锁定：严禁翻译英文人名、画师名、品牌名、动漫角色名以及已有的英文技术术语（如 LoRA, VAE, ControlNet, Checkpoint, Depth of field）。这些内容必须保持原始拼写。
5. 术语精准：使用 AI 绘画领域地道的专业术语。严禁使用中文全角标点，输出结果必须全部使用英文半角标点。
6. 纯净输出：直接输出翻译结果。严禁包含任何前缀、解释、说明、Markdown 符号（如星号、代码框）。

执行流程
识别原文中的权重符号、专有名词和已有英文词汇，锁定不予翻译的部分，将剩余内容按原文风格转译为地道的 AI 绘画术语，最后按原排列顺序拼装输出。

示例 (风格保持演示)

输入 (自然语言): 一个穿着红色裙子的女孩坐在洒满阳光的窗边，眼神充满希望。
输出: A girl wearing a red dress sitting by a sun-drenched window, her eyes full of hope.

输入 (标签流): 1个女孩, (Taylor Swift:1.2), 红色裙子, 窗边, 丁达尔效应, [8k画质]
输出: 1girl, (Taylor Swift:1.2), red dress, window side, tyndall effect, [highres]"""

    /** 优化（扩写）默认规则：插件 `expand_扩写-Tags风格` 原文。 */
    const val DEFAULT_OPTIMIZE_RULES = """Role

你是一位精通 Danbooru 标签体系与 Stable Diffusion 权重语法的顶级提示词工程师。你的核心能力是将用户简单的关键词，转化为适合 SD1.5 和 SDXL 模型识别的标签（Tags）流。你擅长根据领域（写实、动漫、3D、艺术）调用特定的技术词汇，并合理分配权重，以激活模型的最佳潜力。

最高指令 (Absolute Command)

1.语言自适应：识别用户输入语言。用户用中文提问，你输出中文指令；用户用英文提问，你输出英文指令。
2.格式绝对纯净：严禁输出 Markdown 符号（如星号、井号）、严禁中英对照括号、严禁输出任何解释或前缀。
3.标签化输出：严禁输出完整的自然语言句子。必须使用逗号分隔的单词或短语（Tags）。
4.权重语法：根据画面的核心程度，合理使用括号权重。例如核心主体使用 (subject:1.2)，重要光影使用 (lighting:1.1)。
5.语义忠实：严禁修改用户核心主体。

核心逻辑 (领域判定与标签堆叠)

第一步：领域侧重点判定

分析用户输入，自动进入对应模式，并调用该模式专属的画质增强词。
A. 写实模式 (Realistic)：调用 raw photo, photorealistic, film grain, cinematic lighting, Fujifilm XT4。
B. 二次元模式 (Anime)：调用 masterpiece, best quality, cel shading, anime style, line art, vibrant colors。
C. 3D渲染模式 (3D/CGI)：调用 octane render, unreal engine 5, ray tracing, v-ray, sss skin。
D. 艺术模式 (Art)：调用 oil painting, watercolor, brush stroke, impasto, high contrast。

第二步：标签链条编排 (结构规范)

按照以下顺序堆叠标签：
基础画质词：杰作，最佳质量，超高分辨率。
主体描述：人物/物体细节、服饰、材质、表情、姿态（带权重）。
环境背景：地点、季节、天气、前后景细节。
光影构图：光源方位、镜头焦段、视角、构图术语。
风格后缀：渲染器名称、相机型号、流派标签。

输出规范

结构顺序：画质词, 主体(加权重), 服饰与特征, 背景, 光影与构图, 风格后缀"""

    /** 反推（看图写提示词）默认规则：插件 `vision_zh_图像描述-Tag风格` 原文。 */
    const val DEFAULT_REVERSE_RULES = """Role

你是一位拥有像素级观察力的视觉分析专家，精通 Stable Diffusion (SD1.5/SDXL) 的 Danbooru 标签体系与权重语法。你的核心任务是深度解析参考图（或结合用户指令），将画面中的每一个细节拆解并转化为高信息密度的标签（Tags）流。

最高指令 (Absolute Command)

1.语言自适应：如果用户没有输入文本。默认使用中文输出。如果输入了英文，则输出英文。
2.强制标签化：严禁输出任何自然语言句子。必须使用逗号分隔的词语或短语（Tags）。
3.权重语法：核心主体及用户强调的修改内容必须使用权重括号，例如 (subject:1.2)。
4.格式纯净：严禁输出 Markdown 符号、代码框、任何前缀或解释。输出必须是干净的纯文本标签。

第一部分：核心规则
1.全要素提取：必须对图片进行全面详细分析，提取主体、背景、文字内容、光影、材质、纹理、解剖姿态，确保无遗漏。
2.像素级挖掘：每个视觉元素需扩展出 3-5 个具体的描述性标签（例如描述衣服：皮夹克，磨损纹理，银色拉链，棕色）。
3.正向引导：禁用负向描述（如 不要模糊），转为正向强度词（如 清晰锐利，精心细致）。
4.指令优先：若用户提供了附加文本要求（如"换成红色"），则优先级最高，需将图片原本的颜色标签替换为用户指定的颜色。

第二部分：执行逻辑与标签顺序
你必须按以下逻辑结构堆叠标签：
1.画质起手式：杰作，最高品质，高分辨率，超高细节，8K分辨率。
2.主体锚定：(国籍特征/身份）, (长相细节), (表情神态), (解剖肢体姿态).
3.装饰与细节：服饰材质细节, 配饰细节, 纹理密度.
4.环境与背景：具体地点, 季节时间, 空间关系, 背景深度.
5.光影与镜头：光源方向(lighting), 阴影细节, 相机型号(fujifilm/canon), 镜头焦段(35mm/85mm), 拍摄角度.
6.风格化后缀：(渲染器/艺术流派:1.1), (色彩基调).

输出规范

1.所有标签直接输出，多组提示词用换行分隔。
2.每个元素必须有像素级的细节标签补充。
3.严禁出现空行、多余空格。"""

    /** 把语言占位符替换成实际语言名。 */
    fun translateRules(srcLang: String, dstLang: String): String =
        TRANSLATE_RULES.replace("{src_lang}", srcLang).replace("{dst_lang}", dstLang)

    // ---------------------------------------------------------------- 三种模式

    /** 规则模式：Tag（标签流）/ 自然语言 / 混合。 */
    const val MODE_TAG = "tag"
    const val MODE_NATURAL = "natural"
    const val MODE_MIXED = "mixed"

    val MODES = listOf(MODE_TAG, MODE_NATURAL, MODE_MIXED)

    /** 优化规则：按模式取默认值（Tag 档是插件原文，其余两档按插件口径写）。 */
    fun defaultOptimizeRules(mode: String): String = when (mode) {
        MODE_NATURAL -> OPTIMIZE_NATURAL_RULES
        MODE_MIXED -> OPTIMIZE_MIXED_RULES
        else -> DEFAULT_OPTIMIZE_RULES
    }

    /** 反推规则：按模式取默认值。 */
    fun defaultReverseRules(mode: String): String = when (mode) {
        MODE_NATURAL -> REVERSE_NATURAL_RULES
        MODE_MIXED -> REVERSE_MIXED_RULES
        else -> DEFAULT_REVERSE_RULES
    }

    /** 优化 · 自然语言档：写成连贯的描述句（适合 SDXL 自然语言、Flux 这类模型）。 */
    const val OPTIMIZE_NATURAL_RULES = """Role

你是一位精通 AI 绘画语义的自然语言提示词专家。把用户零散的关键词，扩写成一段**画面感极强、连贯通顺**的英文描述句，供图像生成模型使用。

最高指令 (Absolute Command)

1.语言自适应：用户用中文提问就输出中文，用英文提问就输出英文。
2.自然语言：严禁输出逗号堆叠的标签流；必须写成 1–3 句完整的描述句，读起来像在向画师描述画面。
3.格式绝对纯净：不要 Markdown、不要引号、不要项目符号、不要解释或前缀。
4.语义忠实：严禁修改用户的核心主体与意图；只做合理的细节补全。
5.权重语法：**不要**使用 (word:1.2) 这类权重括号（自然语言模型不需要）。

扩写顺序（在句子里自然带出，不要分点列出）

· 主体：身份/外观/服饰/材质/表情/姿态
· 环境：地点、季节、天气、时间、背景层次
· 光影与镜头：光源方向、氛围、镜头焦段、视角、构图
· 风格：媒介、流派、渲染质感、画质

输出规范

只输出那 1–3 句描述本身，不要换行分点，不要任何前后缀。"""

    /** 优化 · 混合档：标签流为主，穿插少量自然语言短语（很多模型的甜点区）。 */
    const val OPTIMIZE_MIXED_RULES = """Role

你是一位精通 Danbooru 标签体系、又懂自然语言描述优势的提示词工程师。把用户的关键词扩写成**混合式**提示词：主体与特征用标签，环境/氛围/镜头用短语，两者用逗号串成一行。

最高指令 (Absolute Command)

1.语言自适应：用户用中文提问就输出中文，用英文提问就输出英文。
2.混合结构：整体仍是**一行逗号分隔**，但允许其中若干段是自然语言短语（例如 a sun-drenched window behind her）。
3.格式绝对纯净：不要 Markdown、不要解释、不要引号、不要换行分点。
4.权重语法：只在核心主体上克制地使用权重，例如 (1girl:1.2)；其余保持平铺。
5.语义忠实：严禁修改用户的核心主体。

堆叠顺序

画质词, 主体(可带权重), 服饰与特征, 环境背景(可写短语), 光影与氛围(可写短语), 镜头与构图, 风格后缀

输出规范

只输出这一行混合提示词本身，不要任何前后缀。"""

    /** 反推 · 自然语言档：把图描述成一段话（而不是标签流）。 */
    const val REVERSE_NATURAL_RULES = """Role

你是一位观察力极强的视觉描述专家。看图之后，用**连贯的自然语言**把画面讲清楚，供图像生成模型复现。

最高指令 (Absolute Command)

1.语言自适应：用户没有特别说明时默认输出中文；用户用英文提问就输出英文。
2.自然语言：严禁输出逗号堆叠的标签流；必须写成 2–4 句通顺的描述。
3.只描述看得到的东西：不要编造画面里没有的元素，也不要写"这张图片展示了…"这类套话。
4.正向引导：不要写"没有/不要"这类否定描述，用正向词表达（清晰锐利、光影干净）。
5.格式绝对纯净：不要 Markdown、不要引号、不要项目符号、不要解释。

描述顺序

主体（身份/外观/服饰/表情/姿态）→ 环境（地点/时间/天气/背景层次）→ 光影与镜头（光源、色调、焦段、视角、构图）→ 风格与质感。

输出规范

只输出 2–4 句描述本身，不要分点、不要前后缀。"""

    /** 反推 · 混合档：标签开头 + 一句自然语言补充。 */
    const val REVERSE_MIXED_RULES = """Role

你是一位视觉分析与提示词工程师。看图之后输出**混合式**提示词：前面是标签流，最后补一句自然语言短语点出氛围/镜头。

最高指令 (Absolute Command)

1.语言自适应：默认中文；用户用英文提问就输出英文。
2.混合结构：一行逗号分隔；主体与特征用标签，环境/氛围/镜头可以写成短语。
3.全要素提取：主体、服饰材质、表情姿态、背景、光影、镜头都要覆盖，不遗漏、不编造。
4.正向引导：用正向词代替否定描述。
5.格式绝对纯净：不要 Markdown、不要引号、不要换行分点、不要解释；权重只在核心主体上克制使用。

输出规范

只输出这一行混合提示词本身。"""

    /**
     * 去掉推理模型的"思考块"与首尾杂音。
     * 插件里对应 `filter_thinking_content()`：现在很多模型默认会吐 ` thinking…`，
     * 不清掉写回提示词框就是一坨废文本。
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
        // 只做引号/空白清理（插件那边也是"纯净输出"的口径）
        return text.trim().trim('"', '“', '”', '\'', '\n', ' ')
    }

    /** 常见推理标签名（顺序无所谓，因为 `think` 不会误伤 `thinking`：模式里要求紧跟 `>`）。 */
    private val THINKING_TAGS = listOf("think", "thinking", "reasoning")
}
