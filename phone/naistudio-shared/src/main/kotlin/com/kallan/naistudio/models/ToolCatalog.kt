package com.kallan.naistudio.models

/**
 * 工具目录。
 *
 * 「工具」在界面上分两处出现，**共用这一份目录**：
 *  · 侧边栏里的「工具」那一项是个**下拉分组**（点一下展开工具名，再点名字进对应界面）；
 *  · 工具页顶部那排胶囊（显示当前工具）。
 *
 * ⚠️ 2026-09-13：**「导演台」「角色分区」已从工具界面移除**（用户要求）——
 * 它们本来就是生成页上的面板（导演台在生成页底部有独立按钮，角色分区在生成参数里），
 * 放在工具页只是多一个入口、反而显得工具页杂。功能本身没动。
 *
 * [ToolSurface.GENERATE] 这条路**保留**：以后如果再加"住在生成页"的工具，
 * 点它就先翻回生成页、再打开对应面板（而不是在工具页抄一份 UI，抄一份迟早会不一致）。
 */
enum class ToolSurface {
    /** 住在工具页（工具页会渲染它的卡片）。 */
    TOOLS,

    /** 住在生成页（翻回生成页 + 打开对应面板）。 */
    GENERATE,
}

/** 生成页上要打开的面板标识（[ToolEntry.generateAction] 用）。 */
object GenerateActions {
    const val DIRECTOR = "director"
    const val CHARACTERS = "characters"
}

data class ToolEntry(
    val id: String,
    /** 工具名（i18n key）。 */
    val titleKey: String,
    /** 一句话说明（i18n key，给侧边栏子项和工具页提示用）。 */
    val hintKey: String,
    val surface: ToolSurface,
    /** [ToolSurface.GENERATE] 时：翻到生成页之后要打开的面板。 */
    val generateAction: String? = null,
)

object ToolCatalog {

    const val REVERSE = "reverse"
    const val METADATA = "metadata"
    const val ANIMADEX = "animadex"

    /**
     * 画师超市（法典图鉴 · NovelAI 画师/画风提示词）。
     *
     * 与角色图鉴是**姊妹工具**：都是"图为主、点一下就复制"的图鉴式浏览，
     * 但数据源与检索方式完全不同（见 [TagCodexProtocol] 的注释）。
     */
    const val TAGCODEX = "tagcodex"

    /**
     * 侧边栏「工具」展开后的顺序 = 工具页的顺序 = 这张表。
     * 以后加工具（比如「超分」「标签翻译」）就往这里加一条，两处界面自动跟着变。
     */
    val entries: List<ToolEntry> = listOf(
        ToolEntry(
            id = ANIMADEX,
            titleKey = "anima.title",
            hintKey = "anima.hintShort",
            surface = ToolSurface.TOOLS,
        ),
        ToolEntry(
            id = TAGCODEX,
            titleKey = "tagcodex.title",
            hintKey = "tagcodex.hintShort",
            surface = ToolSurface.TOOLS,
        ),
        ToolEntry(
            id = METADATA,
            titleKey = "metadata.title",
            hintKey = "metadata.hintShort",
            surface = ToolSurface.TOOLS,
        ),
        ToolEntry(
            id = REVERSE,
            titleKey = "reverse.title",
            hintKey = "reverse.hintShort",
            surface = ToolSurface.TOOLS,
        ),
    )

    /** 默认停在哪个工具（工具页/侧边栏初始状态）。 */
    val defaultId: String = METADATA

    fun byId(id: String?): ToolEntry = entries.firstOrNull { it.id == id } ?: entries.first()
}
