package com.kallan.naistudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **App 图标（桌面版实现）** —— 侧边栏顶部与开屏共用。
 *
 * ## 为什么这里有一份"平台各自实现"
 *
 * 共用层（`naistudio-shared`）的 `SplashOverlay` 与侧边栏只负责**调用** `AppLogo`，
 * 具体怎么画由各平台在自己的源集里给 —— 和 `platform/AndroidPlatform.kt`、
 * `DesktopPlatform.kt` 是同一个套路（同名、同包、同签名，模块各自解析）。
 *
 * 手机那份（`naistudio/app/.../ui/AppLogo.kt`）画的是**启动器图标本身**
 * （把 `R.mipmap.ic_launcher` 这个自适应图标画进位图）：用户 2026-09-16 明确反馈过
 * "照 108 画布重绘显得偏小"，让系统自己画才是桌面上那个样子。那条路径依赖
 * `android.graphics` + `AdaptiveIconDrawable`，桌面端没有，也就不去动它 ——
 * **手机端零回归**比"两边共用一份"重要。
 *
 * ## 桌面这份怎么来的
 *
 * 设计源是 `naistudio/brand/nai-icon.svg`（108×108 视口，与
 * `res/drawable/ic_launcher_background.xml` / `ic_launcher_foreground.xml` 同一套图形）：
 * 深青→深靛对角渐变底 + 近白画框 + 晴蓝星芒。Android 不能在运行时解 SVG，
 * 桌面这边也只需要一个固定图形，所以**按 SVG 坐标等比重绘**：内部坐标系恒为
 * 108×108，每处坐标乘同一个 `s`，任意尺寸都是精确等比。
 *
 * 坐标逐条对着 SVG 抄，没有自由发挥：
 * · 底：`rect 0,0 108 108 rx=24 fill=url(#bg)`，渐变三档 #0B2A33 → #0E3B4A → #0A1620（对角）；
 * · 画框：`M34,42 h32 a6,6 ... v26 ... h-32 ... v-26 Z`，即圆角矩形 x28..72 / y42..80 / r6，
 *   `stroke=#F2F7FA` 宽 5，圆头圆角（SVG 描边居中，Compose 的 [Stroke] 同样居中）；
 * · 星芒：`M76,28 C…` 四段三次贝塞尔，`fill=#5BC8E8`。
 *
 * SVG 里那个 `<clipPath id="squircle">` 不需要：它裁的是前景，而画框与星芒
 * （x28..84 / y28..80）本来就落在圆角矩形内部，裁不裁一个样。
 *
 * @param size 边长；图形按 108 视口等比缩放到这个尺寸。
 */
@Composable
fun AppLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        // 内部坐标系恒为 SVG 的 108×108 → 任何尺寸都是精确等比
        val s = this.size.minDimension / 108f
        fun at(x: Float, y: Float) = Offset(x * s, y * s)
        fun u(v: Float) = v * s

        // ---- 底：圆角方块 + 对角渐变 ----
        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF0B2A33),
                    0.55f to Color(0xFF0E3B4A),
                    1f to Color(0xFF0A1620),
                ),
                start = at(0f, 0f),
                end = at(108f, 108f),
            ),
            topLeft = at(0f, 0f),
            size = Size(108f * s, 108f * s),
            cornerRadius = CornerRadius(24f * s, 24f * s),
        )

        // ---- 画框：圆角矩形描边（不填充）----
        // ⚠️ 2026-09-24 全套重定：跟 `BrandMark.kt`（侧边栏那枚）用**同一组数**
        //（用户：「**所有 logo 应用这个数据，包括侧边栏**」）。
        // 旧值 28/42 44×38 r6 stroke5 → 新值 18/36 59.5×52.5 r8 **stroke 11**。
        drawRoundRect(
            color = Color(0xFFF2F7FA),
            topLeft = at(18f, 36f),
            size = Size(59.5f * s, 52.5f * s),
            cornerRadius = CornerRadius(8f * s, 8f * s),
            style = Stroke(width = 11f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ---- 星芒：四段三次贝塞尔 ----
        // ⚠️ 心 (88.5, 23.5)、放 **2×**（原来 1× 在 (76,36)）。
        // **先按设计稿自己的心 (76,36) 缩放、再平移到目标心** —— 顺序不能反：
        // 直接按新心缩放会把"偏移量"也一起放大，星芒就飘到框外面去了。
        // 注意 Path.cubicTo 收的是 Float（不是 Offset）
        val starCx = 88.5f
        val starCy = 23.5f
        val starScale = 2f
        fun sx(x: Float) = u(starCx + (x - 76f) * starScale)
        fun sy(y: Float) = u(starCy + (y - 36f) * starScale)
        val star = Path().apply {
            moveTo(sx(76f), sy(28f))
            cubicTo(sx(77.2f), sy(33.2f), sx(78.8f), sy(34.8f), sx(84f), sy(36f))
            cubicTo(sx(78.8f), sy(37.2f), sx(77.2f), sy(38.8f), sx(76f), sy(44f))
            cubicTo(sx(74.8f), sy(38.8f), sx(73.2f), sy(37.2f), sx(68f), sy(36f))
            cubicTo(sx(73.2f), sy(34.8f), sx(74.8f), sy(33.2f), sx(76f), sy(28f))
            close()
        }
        drawPath(star, Color(0xFF5BC8E8))
    }
}
