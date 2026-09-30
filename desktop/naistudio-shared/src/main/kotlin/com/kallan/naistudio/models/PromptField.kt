package com.kallan.naistudio.models

/**
 * 三个提示词框的 id。
 *
 * ## 为什么要有一张常量表
 *
 * 提示词框底下那条常驻按钮栏（角色图鉴 / 垃圾桶 / 撤销 / 重做 / 历史 / 翻译 / 优化）
 * 要**按当前选中的那个框**分派（用户 2026-09-16 要求），于是"哪个框"这件事得能：
 * 当 map 的键（每个框各存一份撤销栈）、进设置（锁）、还要在 `when` 里穷尽。
 * 散在七八个文件里各写一遍字符串字面量，迟早有一个人拼错一个字母还编译得过 ——
 * 那种 bug 的表现是"某个按钮偶尔没反应"，最难查。
 */
object PromptField {
    const val STYLE = "style"
    const val POSITIVE = "positive"
    const val NEGATIVE = "negative"

    val ALL = listOf(STYLE, POSITIVE, NEGATIVE)

    /**
     * **漫画剧情框**（用户 2026-09-17："工具框只作用于提示词，不作用于剧情框"）。
     *
     * 它**不属于** [ALL]（那三条是提示词，`params` 里才有），所以在 AppState 里要单独分支：
     * 读 `comicPlot`、写回 `comicPlot`（走它自带的防抖落盘）。
     */
    const val PLOT = "plot"

    fun isPlot(field: String): Boolean = field == PLOT

    /**
     * **角色 / 漫画格子的提示词框**（每个框各配一条工具栏之后才有了身份，用户 2026-09-19）。
     *
     * 编码成 `char:<下标>:p`（正面词）/ `char:<下标>:n`（负面词）。
     * 角色模式与漫画模式**共用同一套下标**（都是 `extras.activeItems`），所以不用再分模式。
     */
    const val CHAR_PREFIX = "char:"

    fun charKey(index: Int, negative: Boolean): String =
        "$CHAR_PREFIX$index${if (negative) ":n" else ":p"}"

    fun isChar(field: String): Boolean = field.startsWith(CHAR_PREFIX)

    /** 解析下标；解析不出来回 -1（调用方 `getOrNull` 会兜住）。 */
    fun charIndex(field: String): Int =
        field.removePrefix(CHAR_PREFIX).substringBefore(':').toIntOrNull() ?: -1

    fun charNegative(field: String): Boolean = field.endsWith(":n")

    /**
     * 兜底成 [POSITIVE]。
     *
     * 界面上的"当前选中框"默认就是正面提示词，而持久化过的值理论上可能因为
     * 版本回退变成不认识的串 —— 那样也不该崩，认成正面框就行。
     */
    fun orDefault(field: String): String = if (field in ALL) field else POSITIVE

    /**
     * **撤销栈 / 按钮状态用的键**：剧情框自成一档，其余不认识的按 [POSITIVE]。
     *
     * ⚠️ 别拿 [orDefault] 当这个用：它把 [PLOT] 折成 [POSITIVE] ✗ ——
     * 于是工具栏的翻译/优化/撤销/重做**看着"作用于剧情"、实际改的是正面提示词**
     * （用户 2026-09-18 报的"这个工具栏只能在提示词栏使用"就是这个）。
     * 角色 / 分镜的框（[isChar]）同理：它们也自成一档，原样返回。
     */
    fun stackKey(field: String): String = when {
        isPlot(field) -> PLOT
        isChar(field) -> field
        else -> orDefault(field)
    }
}

/** 读某个框的文本。不认识的名字按正面提示词处理（见 [PromptField.orDefault]）。 */
fun GenerateParams.promptOf(field: String): String = when (PromptField.orDefault(field)) {
    PromptField.STYLE -> stylePrompt
    PromptField.NEGATIVE -> negativePrompt
    else -> positivePrompt
}

/** 只改某个框的文本，其余字段原样。 */
fun GenerateParams.withPrompt(field: String, text: String): GenerateParams =
    when (PromptField.orDefault(field)) {
        PromptField.STYLE -> copy(stylePrompt = text)
        PromptField.NEGATIVE -> copy(negativePrompt = text)
        else -> copy(positivePrompt = text)
    }

/**
 * 底部常驻栏（垃圾桶 + 「作用于 X」）认的**当前文本框**。
 *
 * ## 为什么要有它
 *
 * 提示词那三个框本来就有身份（[PromptField]），所以底栏的垃圾桶一直只清得了它们 ——
 * 在**角色 / 漫画模式的文本框**里按下去毫无反应（用户 2026-09-16 报的 bug）。
 * 这里把"底栏能作用到"的文本框统一编号：谁拿到焦点谁登记，
 * 垃圾桶照着清、「作用于 X」照着显示。
 *
 * ⚠️ 只覆盖**底栏真正支持**的那几个框。撤销 / 重做 / 翻译 / 优化四个按钮仍然只认
 * 提示词三条（它们各自有独立的撤销栈和 LLM 提示词），别拿这个类型去套它们。
 */
sealed interface TextFieldTarget {
    /**
     * 风格 / 正面 / 负面三条提示词。
     *
     * ⚠️ 用户 2026-09-24：「**生成图片上的对文本框的垃圾桶还是对有些文本框不生效**，
     * 怀疑是**文本框未统一指针**」——**这一条就是那个"不统一"**：
     * 三个框过去注册的是同一个 [Prompt]，垃圾桶分不清用户站在哪一条，只能**一次清三条**；
     * 而正面 / 负面现在是**切换着看**的（同屏只看得到一条），
     * 于是"清掉了我看不见的那两条"看起来就像"垃圾桶不生效 / 清错了"。
     *
     * 现在**三条各带自己的字段**（[field]）——垃圾桶照着那一条清。
     *
     * @param field `PromptField.STYLE / POSITIVE / NEGATIVE`
     */
    data class Prompt(val field: String) : TextFieldTarget

    /** 第 [index] 个角色的正面词；漫画模式下它就是**这一格**的画面提示词。 */
    data class CharacterPrompt(val index: Int, val comic: Boolean) : TextFieldTarget

    /** 第 [index] 个角色的负面词（漫画模式下 = 这一格的负面词）。 */
    data class CharacterNegative(val index: Int, val comic: Boolean) : TextFieldTarget

    /** 漫画剧情框：抽屉里那个三行的、和放大页那个铺满的，读写的是同一个 state。 */
    data object Plot : TextFieldTarget

    /** 漫画「整页风格词」框。 */
    data object ComicStyle : TextFieldTarget
}
