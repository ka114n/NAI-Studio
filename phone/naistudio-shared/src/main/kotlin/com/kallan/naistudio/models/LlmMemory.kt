package com.kallan.naistudio.models

/**
 * **LLM 多轮记忆** —— 语义**照 ComfyUI「API LLM通用链路」节点的源码**（`llm.py`），不靠推断。
 *
 * 源码里的事实（行号对应当前 main 分支 `llm.py`）：
 *  · `conversation_rounds`（1650-1689）：`elements_to_keep = 2 * conversation_rounds`，
 *    历史文件里 `history_temp = [history[0]] + history[-elements_to_keep:]` ——
 *    **只把最近 N 轮（一问一答 = 2 条消息）喂给模型**，而更早的 `history_copy` **原样写回文件**。
 *    ⇒ 所以：**存储全留，只有"发送窗口"受轮数限制**（[window] 管发送，[append] 不裁剪）。
 *  · `is_memory == "disable"`（1651-1653）：把历史文件**重写成只有一条 system** ——
 *    ⇒ 关掉记忆 = **清空**（不是"留着不发"）。见 [LlmMemoryRules.shouldClearOnDisable]。
 *  · `historical_record`（1446-1455 / 1598-1600）：历史是 `temp/<id>.json`，
 *    选一个旧文件名就把 `prompt_path` 指过去 —— ⇒ 记忆**落盘**、可复用（我们按用途分文件）。
 *
 * 与节点的**已知差异**（老实写在这儿）：
 *  · 节点用 `history[0]` 当 system 位、`2×rounds` 是"消息条数"；我们按"轮"存，
 *    等价换算在 [window] 里做（`2 × rounds` 条 = `rounds` 轮）。
 *  · 节点那个 `user_history` 输入（直接用一段 JSON 覆盖历史）我们没做。
 */
data class LlmTurn(
    /** 这一轮发给模型的用户内容。 */
    val user: String,
    /** 模型回的内容。 */
    val assistant: String,
)

/**
 * **"拒答 / 被拦"的识别**（用户 2026-09-18：「自动标记不计入上下文 llm 回传拒绝/失败的对话」）。
 *
 * 纯函数、只看**开头**那一段（前 200 字内），避免把正文里恰好出现的词误判 ——
 * 命中就当作"这条别进上下文"。[LlmMemoryRules.contextWindow] 与界面上的
 * 「不计入上下文」标记都用它。
 */
object LlmRefusal {

    private val PATTERNS = listOf(
        // —— 中文：抱歉/不好意思 + 我(无法|不能…) ——
        Regex("^\\s*(?:抱歉|不好意思|对不起|很抱歉)[，,。！!、]?\\s*(?:我|这|作为|无法|不能|没法)"),
        Regex("^\\s*我\\s*(?:无法|不能|没法|不会|不便|拒绝)\\s*(?:生成|提供|回答|继续|协助|帮助|满足|完成|参与|输出|写)"),
        Regex("^\\s*(?:无法|不能|没法)\\s*(?:生成|提供|回答|继续|协助|帮助|满足|完成|输出)"),
        Regex("^\\s*这个\\s*(?:我)?\\s*(?:没法|无法|不能)"),
        // —— 英文：sorry / I can't … / As an AI … ——
        Regex("(?i)^\\s*(?:i'?m\\s+sorry|sorry)[,.!]?\\s*(?:,|but)?\\s*(?:i|as\\s+an?|this|that)?"),
        Regex("(?i)^\\s*i\\s+(?:can'?t|cannot|can\\s+not|won'?t|will\\s+not|am\\s+unable\\s+to)\\b"),
        Regex("(?i)^\\s*as\\s+an?\\s+(?:ai|assistant)\\b"),
        // —— 平台拦截（Gemini 的"Prohibited Use policy"、内容被过滤等原样回给客户端） ——
        Regex("(?i)prohibited\\s+use\\s+policy|violat\\w*\\s+(?:google'?s|our|the)\\s+\\w*\\s*policy"),
        Regex("(?i)^\\s*(?:the\\s+)?prompt\\s+could\\s+not\\s+be\\s+submitted"),
        Regex("(?i)^\\s*(?:content|request)\\s+(?:was\\s+)?(?:blocked|filtered|rejected)"),
    )

    /** 这条回复是不是"拒答/被拦"。空串返回 false（空回复另有"不进记忆"的既有规则）。 */
    fun looksLikeRefusal(text: String): Boolean {
        val head = text.trim()
        if (head.isEmpty()) return false
        val sample = head.take(200)
        return PATTERNS.any { it.containsMatchIn(sample) }
    }
}

/** 记忆的分桶 key（对应节点里的"每个节点各自一份历史文件"）。 */
object LlmMemoryKind {
    const val STORYBOARD = "storyboard"
    const val PAGE_PLAN = "pageplan"
    const val OPTIMIZE = "optimize"
    const val REVERSE = "reverse"
    const val TRANSLATE = "translate"
}

object LlmMemoryRules {

    /**
     * 取出**要发给模型的**最近 [rounds] 轮 —— 在 [window] 的基础上**剔掉"拒答/被拦"的轮次**
     * （用户 2026-09-18：「自动标记不计入上下文 llm 回传拒绝/失败的对话」）。
     *
     * 为什么必须剔：一条"抱歉，我没法写这个"进了上下文，模型下一轮就会照着继续拒 ✗
     * （用户截图里那三条 分镜 回复就是这种）。**存储照旧**（记忆面板/轮数还是它），
     * 只是"发出去"的窗口里不算它。
     */
    fun contextWindow(turns: List<LlmTurn>, rounds: Int): List<LlmTurn> =
        window(turns, rounds).filterNot { LlmRefusal.looksLikeRefusal(it.assistant) }

    /** 轮数下限（节点 `conversation_rounds` 的 min 是 1）。 */
    const val MIN_ROUNDS = 1

    /** 轮数上限（节点给到 10000；内存里留个更实际的上限，避免手机端无限涨）。 */
    const val MAX_ROUNDS = 10000

    /** 存储上限（节点不限；我们保底一个上限，防止极端情况把内存吃光）。 */
    const val MAX_STORED_TURNS = 2000

    /**
     * 取出**要发给模型的**最近 [rounds] 轮（最新的在最后，保持时间顺序）。
     *
     * 对应源码的 `history[-2*rounds:]`：**只裁"发送窗口"，不动存储**。
     */
    fun window(turns: List<LlmTurn>, rounds: Int): List<LlmTurn> {
        val r = rounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS)
        return turns.takeLast(r)
    }

    /**
     * 追加一轮。
     *
     * ⚠️ 与"发送窗口"不同：**这里不按 `conversation_rounds` 裁剪**（源码把更早的
     * `history_copy` 原样写回文件），只做一个 [MAX_STORED_TURNS] 的兜底上限。
     * 空回复不进记忆（模型失败/空返回不污染后续上下文）。
     */
    fun append(turns: MutableList<LlmTurn>, turn: LlmTurn) {
        if (turn.assistant.isBlank()) return
        turns.add(turn)
        while (turns.size > MAX_STORED_TURNS) turns.removeAt(0)
    }

    /**
     * 关掉记忆时是否要**清空**已存的历史。
     *
     * 源码（1651-1653）：`if is_memory == "disable": json.dump([system])` → **清空**。
     * 用户 2026-09-16："按节点源码做" ⇒ 我们也清空（之前只是"不发"，那是错的）。
     */
    const val CLEAR_ON_DISABLE = true

    /**
     * `is_locked` 的缓存键（源码靠 ComfyUI 节点缓存实现"参数没变就直接返回上轮结果"，
     * 我们等价地自己存一份：**这些输入全都没变就复用上一轮回复，不发请求**）。
     */
    fun lockKey(
        kindKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxLength: Int,
        imageCount: Int,
    ): String = buildString {
        append(kindKey).append('\u0001')
        append(model).append('\u0001')
        append(systemPrompt).append('\u0001')
        append(userPrompt).append('\u0001')
        append(temperature).append('\u0001')
        append(maxLength).append('\u0001')
        append(imageCount)
    }
}
