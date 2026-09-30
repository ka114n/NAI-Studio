package com.kallan.naistudio.models

/**
 * **一次"AI 交互"的记录**（纯展示用 —— 用户 2026-09-16 的定位："纯展示层，不做真对话"）。
 *
 * 覆盖两类：
 *  · **LLM 调用**（翻译 / 优化提示词 / 反推提示词 / 按剧情分镜 / 分页规划）—— 有 system/user prompt 和回复文本；
 *  · **出图调用**（文生图 / 图生图 / 局部重绘 / 放大）—— 有提示词、模型、尺寸，回复是"出了 n 张图"。
 *
 * 结构参考 ComfyUI 那个「API LLM通用链路」节点（`system_prompt_input` / `user_prompt_input` /
 * `images` / `extra_parameters` / `tools` → `assistant_response`），但**只落到展示**：
 * 不维护多轮 history、不做工具调用（那两样的位置先留成字段，见 [toolsOffered] / `llmMemoryEnabled`）。
 *
 * 只存在内存里（进程结束即清空）—— 落盘记忆对应节点里的 `historical_record`，本轮不做。
 */
data class ConversationTurn(
    val id: Long,
    val atMillis: Long,
    /** i18n key，例如 `conversation.kind.optimize`。 */
    val kindKey: String,
    /** `llm` / `novelai`。 */
    val provider: String,
    val model: String,
    /** 发给它的"系统提示词"（出图类为空）。 */
    val systemPrompt: String = "",
    /** 发给它的"用户内容"（出图类是提示词）。 */
    val userPrompt: String = "",
    /** 随请求一起发的图片张数（反推是 1，出图的图生图/重绘是 1，文生图是 0）。 */
    val imageCount: Int = 0,
    /** 本轮提供给模型的工具数 —— tools 还没做，恒 0，字段先留着。 */
    val toolsOffered: Int = 0,
    /** 它回了什么（文本；出图类是"出了 n 张图"）。 */
    val response: String = "",
    val ok: Boolean = true,
    val error: String = "",
    val durationMs: Long = 0L,
    /** 尺寸 / 步数 / 实扣点数之类的补充。 */
    val extra: String = "",
) {
    companion object {
        /** 展示层最多保留多少条。 */
        const val MAX_TURNS = 200
    }
}

/**
 * 底部那行字幕（状态行）的历史 —— 抽屉「日志模式」里和网络日志并排显示。
 *
 * [detail] 是**可展开的明细**（用户 2026-09-22：「日志里的"已翻译并填入…"变成可下拉，
 * 里面写词典命中了多少 tag、调用了几次翻译」）—— 空表示这条没什么可展开的，
 * 抽屉里就画成普通一行（不带箭头、点了也不动）。
 */
data class StatusLine(
    val atMillis: Long,
    val text: String,
    val detail: List<String> = emptyList(),
) {
    companion object {
        const val MAX_LINES = 200
    }
}
