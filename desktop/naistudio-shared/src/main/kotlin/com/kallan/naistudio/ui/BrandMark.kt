package com.kallan.naistudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **侧栏那枚扁平单色标记**（用户 2026-09-19：「侧边栏的 logo 重绘一个扁平化的单色 logo，
 * 和截图差不多，放大一些」，随后又「**框加粗、星星变大**」）。
 *
 * ## 和 [AppLogo] 的区别
 *
 * [AppLogo] 是**应用图标**那一版：圆角方块底 + 对角渐变 + 近白画框 + 晴蓝星芒（三色 + 底）。
 * 这里要的是**扁平 + 单色**：只要图形本身（画框 + 星芒），**不要底板、不要渐变、不要第二种颜色** ——
 * 颜色由调用方给（侧栏传 [naiBrandTitleColor]，于是深色模式下是纯白、浅色模式下是纯黑，
 * 和截图里那种"白得干净"的观感一致）。
 *
 * ## 坐标
 *
 * 图形沿用 [AppLogo] 的 108 视口坐标（同一份设计稿），但两处按用户要求调过：
 *  · 画框描边 **5 → [FRAME_STROKE]（8）**（"框加粗"）；
 *  · 星芒以 (76,36) 为心放大 **[STAR_SCALE]（1.35 倍）**（"星星变大"）。
 *
 * 外接框由这几个常量算出来（[BOX_LEFT]…），再把整个图形按它等比缩放居中 ——
 * 于是 `size` 给的就是**图形本身的边长**（不像 `AppLogo` 那样还要留底板的白边），
 * 同样写 72dp，图形看起来比原来大一大圈。
 */
private const val FRAME_STROKE = 11f
private const val STAR_SCALE = 2f

/**
 * **画框**（108 视口坐标）。
 *
 * ⚠️ **2026-09-24 全套重定**（用户用调参页 `docs/桌面图标-全参数.html` 拖出来的一组数，
 * 要求「**所有 logo 应用这个数据，包括侧边栏**」）——
 * 从 `28 / 44.5 / 43×38 / 圆角 8` 换成下面这组**更大、更靠左上**的框：
 *  · 框外沿（含 11 粗的描边）x **12.5–83**、y **30.5–94**； · 星芒心 (88.5, 23.5)、放 **2×**。
 *
 * 与上一版的观感差别（都是刻意的，别再"顺手改回去"）：
 *  · 描边 8 → **11**（更厚实），星芒 1.35× → **2×**（明显更大）；
 *  · 框**往上挪**了（y 44.5 → 36）⇒ 星芒下尖 (39.5) 与框上沿 (30.5) 由"贴着"变成
 *    **分开约 9**（星芒完整浮在框的右上角外侧，不再压住框线）；
 *  · 整枚图形在 108 视口里**撑得更满**（总包围盒 x 12.5–104.5 / y 7.5–94）。
 */
private const val FRAME_X = 18f
private const val FRAME_Y = 36f
private const val FRAME_W = 59.5f
private const val FRAME_H = 52.5f
private const val FRAME_CORNER = 8f

/**
 * 星芒中心（108 视口坐标）。
 *
 * ⚠️ 2026-09-24 随上面那组一起改：`(81,31)` → **`(88.5,23.5)`** ——
 * 跟着变大的框一起挪到**右上角外侧**，和框之间留出约 9 的净空（见 [FRAME_X] 的说明）。
 */
private const val STAR_CX = 88.5f
private const val STAR_CY = 23.5f

/** 星芒在设计稿里的中心（放大时以它为心）。 */
private const val STAR_SRC_CX = 76f
private const val STAR_SRC_CY = 36f

/** 星芒半臂（设计稿里 68..84 / 28..44 → 半径 8）。 */
private const val STAR_R = 8f

/**
 * 外接框 = **以画框中心对称外扩**，且**必须把所有图形包进去**（含星芒与描边）。
 *
 * 两个要求一起满足才不会出事：
 *  · 对称（用户 2026-09-20：「侧边栏图标整体往右一些，**方框居中**」）—— 星芒往右上探出去多少，
 *    左下就补等量空白，画框正好落在画布正中央；
 *  · 包住（用户 2026-09-20 紧接着报「**logo 显示不全**」）—— 上一版算 `BOX_TOP` 时用了 `minOf`
 *    跟画框上沿比，结果取到了画框上沿 (40.5) ✗，**星芒顶部 (20.2) 被切掉** ✗。
 *    现在改为"半宽/半高取各方向的最大伸出量"，对称和包含同时成立 ✓。
 */
private val BOX_CX = FRAME_X + FRAME_W / 2f
private val BOX_CY = FRAME_Y + FRAME_H / 2f
private val BOX_HALF_W = maxOf(
    FRAME_W / 2f + FRAME_STROKE / 2f,
    (STAR_CX + STAR_R * STAR_SCALE) - BOX_CX,
    BOX_CX - (STAR_CX - STAR_R * STAR_SCALE),
)
private val BOX_HALF_H = maxOf(
    FRAME_H / 2f + FRAME_STROKE / 2f,
    (STAR_CY + STAR_R * STAR_SCALE) - BOX_CY,
    BOX_CY - (STAR_CY - STAR_R * STAR_SCALE),
)
private val BOX_LEFT = BOX_CX - BOX_HALF_W
private val BOX_RIGHT = BOX_CX + BOX_HALF_W
private val BOX_TOP = BOX_CY - BOX_HALF_H
private val BOX_BOTTOM = BOX_CY + BOX_HALF_H

@Composable
fun FlatBrandMark(
    /** **图形宽度**；高度按外接框的比例算出来（所以不会在上下留多余的空白）。 */
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    /**
     * **右上角那颗星星的颜色**（用户 2026-09-24：「logo 的右上角**星星常态蓝色**」✓）。
     *
     * ⚠️ 与 [color] **分开传** ✗（不是"整枚 logo 一个色"✓）：
     * 画框跟着主题走（深底白框 / 浅底黑框 ✓，那是原来的口径 ✓），
     * **只有星星固定是那抹蓝** ✓ —— 默认值 = [color]，所以**不传就是原来的纯单色** ✓
     *（收纳态那枚小 logo 就走默认 ✓，不额外加戏 ✓）。
     */
    starColor: Color = color,
) {
    val boxW = BOX_RIGHT - BOX_LEFT
    val boxH = BOX_BOTTOM - BOX_TOP
    // ⚠️ 用户 2026-09-20：「侧边栏图标和标题之间空隙太大了，靠近一些」——
    // 以前画布是**正方形**（`.size(size)`），而外接框是横的 → 上下各留一条与图形等量的空白 ✗，
    // 图标底下就凭空多出 ~20dp，视觉上离标题很远。改成**画布跟着外接框的比例走**（宽 = size、
    // 高 = size × 外接框高宽比），图形正好铺满，多余空白没有了。
    Canvas(modifier.size(width = size, height = size * (boxH / boxW))) {
        val s = this.size.width / boxW
        fun px(x: Float) = (x - BOX_LEFT) * s
        fun py(y: Float) = (y - BOX_TOP) * s
        // 星芒：**以设计稿里它自己的心 (76,36) 放大**，再整体挪到 (STAR_CX, STAR_CY)，
        // 最后走上面那套映射。（先缩放后平移 —— 直接按新心缩放会把星芒的形状也带偏。）
        fun starX(x: Float) = px(STAR_CX + (x - STAR_SRC_CX) * STAR_SCALE)
        fun starY(y: Float) = py(STAR_CY + (y - STAR_SRC_CY) * STAR_SCALE)

        // ---- 画框：圆角矩形描边（不填充）----
        drawRoundRect(
            color = color,
            topLeft = Offset(px(FRAME_X), py(FRAME_Y)),
            size = Size(FRAME_W * s, FRAME_H * s),
            cornerRadius = CornerRadius(FRAME_CORNER * s, FRAME_CORNER * s),
            style = Stroke(width = FRAME_STROKE * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ---- 星芒：四段三次贝塞尔（和 AppLogo 同一串坐标，只是放大了）----
        val star = Path().apply {
            moveTo(starX(76f), starY(28f))
            cubicTo(starX(77.2f), starY(33.2f), starX(78.8f), starY(34.8f), starX(84f), starY(36f))
            cubicTo(starX(78.8f), starY(37.2f), starX(77.2f), starY(38.8f), starX(76f), starY(44f))
            cubicTo(starX(74.8f), starY(38.8f), starX(73.2f), starY(37.2f), starX(68f), starY(36f))
            cubicTo(starX(73.2f), starY(34.8f), starX(74.8f), starY(33.2f), starX(76f), starY(28f))
            close()
        }
        drawPath(star, color = starColor)
    }
}
