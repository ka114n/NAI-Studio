package com.kallan.naistudio.platform

/**
 * **极简日志出口**（共用树里唯一允许"打日志"的地方）。
 *
 * 共用代码不能 `import android.util.Log`（那会把桌面端编挂），可服务层又确实需要
 * 打点（网络耗时、图片大小、失败原因）。所以留一个**可替换的后端**：
 *
 * · 手机：`MainActivity` 里装 `android.util.Log` 的后端 —— 保住 `adb logcat -s <TAG>` 的可用性；
 * · 电脑：默认后端（`println`），jpackage 出来的 exe 没控制台，但开发期跑 `gradle run` 看得到。
 *
 * 为什么用全局变量而不是把 logger 一路传下去：日志是**横切关注点**，
 * 为了它给每个构造函数加参数不值当（也污染共用 API）。这是这一种情况下的合理妥协。
 */
private var backend: (level: Char, tag: String, message: String) -> Unit =
    { level, tag, message -> println("$level/$tag: $message") }

/** 装后端。两端入口各调一次即可；不装就用默认的 `println`。 */
fun installAppLogger(action: (level: Char, tag: String, message: String) -> Unit) {
    backend = action
}

/**
 * **最近日志的内存环形缓冲**（底部抽屉的「日志模式」用）。
 *
 * 为什么不直接读 `app.log`：
 *  · 只有电脑端装了文件后端（手机没有），而界面要的是**两端一致**的东西；
 *  · 读文件还得处理轮转与编码，而这里要的只是"刚刚发生了什么"。
 *
 * 用 `mutableStateListOf` 是为了让 Compose 直接观察到新行（共享树本来就依赖 compose runtime）。
 */
val recentLogs: androidx.compose.runtime.snapshots.SnapshotStateList<LogLine> =
    androidx.compose.runtime.mutableStateListOf()

/** 抽屉里最多留多少行日志（超了丢最旧的）。 */
const val MAX_LOG_LINES = 400

/** 一行内存日志。 */
data class LogLine(val atMillis: Long, val level: Char, val tag: String, val message: String)

private fun appendLog(level: Char, tag: String, message: String) {
    recentLogs.add(LogLine(System.currentTimeMillis(), level, tag, message))
    while (recentLogs.size > MAX_LOG_LINES) recentLogs.removeAt(0)
}

/** 清空内存日志（抽屉里的「清空」按钮）。 */
fun clearRecentLogs() {
    recentLogs.clear()
}

fun logInfo(tag: String, message: String) {
    backend('i', tag, message)
    appendLog('i', tag, message)
}

fun logWarn(tag: String, message: String) {
    backend('w', tag, message)
    appendLog('w', tag, message)
}

fun logError(tag: String, message: String) {
    backend('e', tag, message)
    appendLog('e', tag, message)
}

