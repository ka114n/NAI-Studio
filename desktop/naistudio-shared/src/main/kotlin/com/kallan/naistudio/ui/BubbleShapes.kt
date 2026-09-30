package com.kallan.naistudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.min

/**
 * **对话气泡的内置图形库**（高级漫画模式用）—— 用户 2026-09-26：
 * 「加**对话气泡**的功能，内置一个图画框，**选定气泡样式后直接拖到相应位置调大小**」。
 *
 * 设计要点：
 *  · 气泡是**矢量轮廓**（不是位图贴图 ✗）→ 放大不糊、文字随时改 ✓；
 *  · 轮廓由 `Path` 生成（[bubblePath]），所以能脱离界面单测（比 bounds ✓）——
 *    真机渲染那步交给 `ui/ComicBubbleShape.kt` 那个 `Modifier.bubbleOutline(...)` ✓
 *    （`bubblePath` + `drawPath` ✓，填色与描边同一个路径 ✓）。
 *    ⚠️ **不要**把它包成 `Shape` 再交给 `Modifier.border(...)` ✗ ——
 *    电脑端 `border` + `GenericShape` 会抛 `Failed to Image::makeFromBitmap`
 *    （2026-09-20 用户实机崩溃 ✓，细节与回归钉子见 `ui/ComicBubbleShape.kt` ✓）；
 *  · **尾巴**可选（[BubbleStyle.tail]）：指向说话的人 ✓，尾巴尖由调用方给（`tailTarget`）✓ ——
 *    "拖尾巴"那种交互后面再接，这里先保证**给定目标点能画出正确的三角** ✓。
 *
 * 样式就这几种（用户要"内置"✓，不多不少 —— 以后加样式只要往枚举里塞一个 ✓）：
 *  · [ROUND] 圆角泡（最常用 ✓）
 *  · [RECT] 方框旁白（方形 + 直角 ✓）
 *  · [THOUGHT] 思考泡（云朵边 ✓）
 *  · [SHOUT] 惊叫泡（锯齿边 ✓）
 */
enum class BubbleStyle(
    /** 圆角半径（占短边的比例；方形=0）。 */
    val cornerRatio: Float,
    /** 边线是否走波浪（云朵 / 锯齿）。 */
    val wobble: Wobble,
    /** 有没有尾巴（`RECT` 旁白框没有 ✓）。 */
    val tail: Boolean,
) {
    ROUND(cornerRatio = 0.28f, wobble = Wobble.NONE, tail = true),
    RECT(cornerRatio = 0f, wobble = Wobble.NONE, tail = false),
    THOUGHT(cornerRatio = 0.5f, wobble = Wobble.CLOUD, tail = true),
    SHOUT(cornerRatio = 0.05f, wobble = Wobble.JAGGED, tail = true),
    ;

    enum class Wobble { NONE, CLOUD, JAGGED }
}

/**
 * 生成气泡轮廓（**纯函数** ✓，界面那边包成 `Shape` 就行）。
 *
 * @param style 样式（见 [BubbleStyle]）。
 * @param size  气泡的矩形大小（左上角按 (0,0) 算 ✓ —— 摆放交给调用方的 `offset` ✓）。
 * @param tailTarget 尾巴尖指向哪（相对同一个坐标系 ✓）；`null` 或不带尾巴的样式 → 不画尾 ✓。
 * @param tailWidth 尾巴根部宽度（占短边比例 ✓，别超过 0.4 否则会吃掉泡身 ✗）。
 */
fun bubblePath(
    style: BubbleStyle,
    size: Size,
    tailTarget: Offset? = null,
    tailWidth: Float = 0.22f,
): Path {
    val path = Path()
    val rect = Rect(0f, 0f, size.width, size.height)
    if (rect.width <= 0f || rect.height <= 0f) return path

    val short = min(rect.width, rect.height)
    val radius = (short * style.cornerRatio).coerceIn(0f, short / 2f)

    when (style.wobble) {
        BubbleStyle.Wobble.NONE -> path.addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect,
                androidx.compose.ui.geometry.CornerRadius(radius, radius),
            ),
        )

        BubbleStyle.Wobble.CLOUD -> addWobbly(path, rect, bumps = 9, depth = short * 0.07f, jagged = false)
        BubbleStyle.Wobble.JAGGED -> addWobbly(path, rect, bumps = 13, depth = short * 0.10f, jagged = true)
    }

    // 尾巴：从底边中间往下拉一个三角，指向 tailTarget ✓
    if (style.tail && tailTarget != null) {
        val rootHalf = (short * tailWidth.coerceIn(0.05f, 0.4f)) / 2f
        val cx = rect.width / 2f
        // 目标点若在泡内就别画（会变成一个奇怪的缺口 ✗）
        if (tailTarget.y > rect.bottom) {
            path.moveTo(cx - rootHalf, rect.bottom - 1f)
            path.lineTo(tailTarget.x, tailTarget.y)
            path.lineTo(cx + rootHalf, rect.bottom - 1f)
            path.close()
        }
    }
    return path
}

/** 云朵 / 锯齿：沿着矩形边一路画"外凸的小段"。`jagged=true` 用折线（尖锐），否则用二次曲线（圆钝）。 */
private fun addWobbly(path: Path, rect: Rect, bumps: Int, depth: Float, jagged: Boolean) {
    // 上边 → 右边 → 下边 → 左边，每段切 bumps/4 个起伏 ✓
    val perSide = (bumps / 4).coerceAtLeast(2)
    val corners = listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
        Offset(rect.left, rect.bottom),
    )
    path.moveTo(corners[0].x, corners[0].y)
    for (i in 0 until 4) {
        val a = corners[i]
        val b = corners[(i + 1) % 4]
        // 这一条边的外法线（朝着矩形外面涨出去 ✓）
        val nx = (b.y - a.y)
        val ny = -(b.x - a.x)
        val len = kotlin.math.sqrt(nx * nx + ny * ny).coerceAtLeast(0.0001f)
        val ux = nx / len
        val uy = ny / len
        for (k in 1..perSide) {
            val t = k.toFloat() / perSide
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t
            // 交替"鼓出去 / 缩回来"（锯齿缩得更狠一点，才够尖 ✓）
            val sign = if (k % 2 == 1) 1f else if (jagged) -0.25f else 0.25f
            val out = Offset(px + ux * depth * sign, py + uy * depth * sign)
            if (jagged) {
                path.lineTo(out.x, out.y)
            } else {
                path.quadraticTo(px + ux * depth * 1.6f, py + uy * depth * 1.6f, out.x, out.y)
            }
        }
    }
    path.close()
}
