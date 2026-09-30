package com.kallan.naistudio.ui

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * 把 [BubbleStyle] **包成 Compose 的 [Shape]**（`ui/BubbleShapes.kt` 那批留下的最后一厘米）。
 *
 * 轮廓本身早写好并有单测（`BubbleShapesTest` 6 条 ✓）：`bubblePath(style, size, tailTarget, tailWidth)`
 * 是**纯函数** ✓。这里只做两件事：
 *  1. 把 `Shape` 的 lambda 参数（draw 阶段的 `size`，单位是**像素**）喂给它；
 *  2. 把尾巴尖从"**比例**坐标"换算成像素 —— 为什么不用绝对像素：
 *     `Shape` 的 lambda 里拿不到节点自己的 dp 尺寸，而气泡是**跟着页面等比缩放**的，
 *     存绝对值的话窗口一缩放尾巴就指歪了 ✗。存"尾巴尖相对泡身的比例"，
 *     不管缩放到多少像素都对得上 ✓。
 *
 * ⚠️⚠️ **只拿它 `Modifier.background(fill, bubbleShape(...))` 填色** ✓，
 * **绝不要拿它去 `Modifier.border(宽, 色, bubbleShape(...))`** ✗✗ ——
 * 在 Compose Multiplatform 电脑端（skiko）上，`border` + **GenericShape** 会走
 * `SkiaBackedCanvas.drawImageRect` 那条路（内部拿一张 1×1 位图当画笔 ✗），抛
 * `java.lang.RuntimeException: Failed to Image::makeFromBitmap Bitmap(_ptr=0x…)` ✗ ——
 * **2026-09-20 用户实机就是这个框**（进高级漫画页、放下第一颗气泡时炸的 ✓）。
 * 回归钉子：`naistudio-desktop/src/test/.../ComicOverlayDrawTest.kt`（三条 `border` 用例当场红 ✓）。
 * 标准形状（`RoundedCornerShape` / `CircleShape`）**不受影响** ✓ —— 它们不走位图那条路 ✓。
 *
 * 要"填色 + 描边"就用下面的 [bubbleOutline]（自己走 `Path` + `drawPath` ✓，没有位图这一环 ✓）。
 */
fun bubbleShape(
    style: BubbleStyle,
    /** 尾巴尖的**比例**坐标（相对泡身左上角，1f = 右边 / 下边）；`null` = 不画尾 ✓ */
    tailFraction: Offset? = null,
    tailWidth: Float = 0.22f,
): Shape = GenericShape { size, _ ->
    val target = tailFraction?.let { Offset(size.width * it.x, size.height * it.y) }
    bubblePath(style, size, target, tailWidth)
}

/** 样式 id（模型里存的是字符串，见 `models/ComicOverlay.kt`）→ 枚举；**认不出来一律回落圆泡** ✓（不能崩 ✗）。 */
fun bubbleStyleOf(id: String?): BubbleStyle =
    BubbleStyle.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: BubbleStyle.ROUND

/**
 * 气泡的**底色 + 描边**（同一条 [bubblePath] 画两遍：先填色、再描边 ✓）。
 *
 * 为什么要有这个 helper（而不是 `.background(...)` + `.border(...)`）✗：
 * `.border(宽, 色, bubbleShape(...))` 在电脑端会抛
 * `Failed to Image::makeFromBitmap`（见 [bubbleShape] 的 ⚠️ 那段 ✓）。
 * 自己 `drawPath` 就**没有位图这一环** ✓，而且**填色与描边是同一个路径** ——
 * 界面上那颗气泡（`screens/ComicOverlayLayers.kt` 的 `BubbleItem` ✓）与
 * 拼页导出那一层（`screens/ComicOverlayExport.kt` ✓）**逐字同源** ✓，
 * 不会再出现"界面看着对、导出少一条边"的偏差 ✗。
 *
 * 画在**内容之下**（`drawBehind` ✓）—— 泡里的字不会被底色盖住 ✓。
 *
 * @param tailFraction 尾巴尖的**比例**坐标（相对泡身左上角）；`null` = 不画尾 ✓
 * @param strokeWidth `0.dp` 或透明色 = 只填色不描边 ✓
 */
fun Modifier.bubbleOutline(
    style: BubbleStyle,
    tailFraction: Offset? = null,
    tailWidth: Float = 0.22f,
    fill: Color,
    stroke: Color,
    strokeWidth: Dp,
): Modifier = drawBehind {
    val target = tailFraction?.let { Offset(size.width * it.x, size.height * it.y) }
    val path = bubblePath(style, size, target, tailWidth)
    drawPath(path, fill)
    if (strokeWidth.value > 0f && stroke.alpha > 0f) {
        drawPath(path, stroke, style = Stroke(width = strokeWidth.toPx()))
    }
}

/** 尾巴尖的**比例**坐标：绝对坐标（底板像素）减泡身左上角，再除以泡身宽高。 */
fun bubbleTailFraction(
    tailX: Float,
    tailY: Float,
    bubbleX: Float,
    bubbleY: Float,
    bubbleW: Float,
    bubbleH: Float,
): Offset {
    val fx = if (bubbleW > 0f) (tailX - bubbleX) / bubbleW else 0f
    val fy = if (bubbleH > 0f) (tailY - bubbleY) / bubbleH else 0f
    return Offset(fx, fy)
}
