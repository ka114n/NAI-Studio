package com.kallan.naistudio.models

/**
 * **画布编辑器的工具**（NovelAI 官方画布那 8 个，顺序照官方原样）。
 *
 * 顺序就是工具栏里的显示顺序（用户 2026-09-16 要求"照官方顺序原样"）：
 * Draw → Erase → Fill → Select → Lasso → Color Picker → Blur → Clone。
 *
 * [implemented] 标着"做没做"：
 *  · 一期：Draw / Erase / Picker（外加调色板）；
 *  · 二期：Fill / Blur / Clone（外加 HSV 调整、调整画布）；
 *  · 三期：Select / Lasso（选区：框起来能移动 / 缩放 / 旋转）。
 * 八个都齐了 —— 这个标记留着，以后加官方的新工具时还用得上。
 */
enum class CanvasTool(val implemented: Boolean) {
    DRAW(true),
    ERASE(true),
    FILL(true),
    SELECT(true),
    LASSO(true),
    PICKER(true),
    BLUR(true),
    CLONE(true),
}

/**
 * 画布编辑器的快捷调色板（点一下就换色）。
 *
 * 取的是"画草图/补细节"最常用的那一档：黑白 + 三原色 + 常见中间色。
 * 想要别的颜色走旁边的 HSV 轮盘（复用 `ui/ColorPicker.kt`）。
 */
val CanvasPalette: List<Int> = listOf(
    0xFF000000.toInt(), // 黑
    0xFF4A4A4A.toInt(), // 深灰
    0xFF9E9E9E.toInt(), // 浅灰
    0xFFFFFFFF.toInt(), // 白
    0xFFE53935.toInt(), // 红
    0xFFFB8C00.toInt(), // 橙
    0xFFFDD835.toInt(), // 黄
    0xFF43A047.toInt(), // 绿
    0xFF00ACC1.toInt(), // 青
    0xFF1E88E5.toInt(), // 蓝
    0xFF8E24AA.toInt(), // 紫
    0xFFD81B60.toInt(), // 洋红
)

/** 画布默认色（黑 —— 空白画布上最直观）。 */
const val DEFAULT_CANVAS_COLOR: Int = 0xFF000000.toInt()

/** 油漆桶默认容忍度（官方截图里就是 15）。 */
const val DEFAULT_FILL_TOLERANCE: Int = 15

/** 模糊默认强度（官方截图里是 50）。 */
const val DEFAULT_BLUR_INTENSITY: Int = 50

/**
 * 画布长边上限：超过就先等比降下来。
 *
 * 为什么要有：编辑会话把整张图按 ARGB 放在内存里（4 字节/像素），
 * 4000×4000 就是 64 MB，而且每画一笔还要重建一张显示位图。
 * 3072 够覆盖 NovelAI 能出的最大尺寸（3.1 M 像素，长边最多约 3136），实际不会误伤。
 */
const val CANVAS_MAX_LONG_SIDE: Int = 3072
