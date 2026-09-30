package com.kallan.naistudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * **取色器：色环 + 饱和/明度方块**（用户 2026-09-16 选定的形态）。
 *
 * ## 为什么自己画
 *
 * 这个工程**不引第三方 UI 库**（运行时依赖只有 OkHttp），所以没有 `ColorPicker` 可用。
 * 好在要的东西都不复杂：色环 = 一圈 `sweepGradient` + 一个描边圆挖空，
 * 方块 = 底色 + 横向白→透明 + 纵向透明→黑两层渐变叠出来。都是 Canvas 几行的事。
 *
 * ## 交互
 *
 * · 色环：按下/拖动 → 取**相对圆心的角度**定色相（`H`）；
 * · 方块：按下/拖动 → 取**归一化坐标**定饱和度（`S`）与明度（`V`），x 越大越饱和、y 越大越暗；
 * · 两处都**按下即响应**（不等抬手），手指滑出边界也继续跟 —— 取色时这是必要的。
 *
 * ## 与调用方的关系
 *
 * 这里只管"当前是什么颜色"，**不落盘**：调用方（设置页）拿 [onColorChange] 实时更新预览，
 * 点保存时才写进 `AppSettings`。
 */
@Composable
fun NaiColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    ringSize: Dp = 200.dp,
) {
    // 把外部颜色拆成 HSV。用 state 记住 HSV 而不是每帧反推：
    // 反过来算的话，颜色落在灰阶（S=0）或纯黑（V=0）时色相会丢，环上的标记会乱跳。
    val initial = remember(color) { color.toHsv() }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }

    // 外部颜色被别处改了（比如点了预设色板）→ 重新对齐 HSV
    LaunchedEffect(color) {
        val hsv = color.toHsv()
        if (abs(hsv[0] - hue) > 0.5f || abs(hsv[1] - sat) > 0.004f || abs(hsv[2] - value) > 0.004f) {
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    fun emit() = onColorChange(hsvToColor(hue, sat, value))

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // ---------------- 色环 ----------------
        Box(
            Modifier
                .size(ringSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // ⚠️ 同方块：`size` 是 IntSize，先转 Float
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(center.x, center.y)
                        fun handle(pos: Offset) {
                            val dx = pos.x - center.x
                            val dy = pos.y - center.y
                            if (hypot(dx, dy) < radius * 0.35f) return // 环内空白区不认（避免误触）
                            // atan2 的 0 在 3 点钟方向、逆时针为正；取色环从 12 点顺时针走
                            val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            hue = ((deg + 90f) % 360f + 360f) % 360f
                            emit()
                        }
                        handle(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            handle(change.position)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = minOf(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringWidth = radius * 0.34f
                val stroke = radius - ringWidth / 2f
                // 一圈色相：首尾都放 0°，让接缝不露白
                val wheel = buildList {
                    for (i in 0..12) add(hsvToColor(i * 30f % 360f, 1f, 1f))
                }
                drawCircle(
                    brush = Brush.sweepGradient(wheel, center),
                    radius = stroke,
                    center = center,
                    style = Stroke(width = ringWidth),
                )
                // 当前色相的小标记：贴在环中线处
                val rad = Math.toRadians((hue - 90f).toDouble())
                val marker = Offset(
                    x = center.x + stroke * cos(rad).toFloat(),
                    y = center.y + stroke * sin(rad).toFloat(),
                )
                drawCircle(color = Color.White, radius = ringWidth * 0.30f, center = marker)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = ringWidth * 0.30f,
                    center = marker,
                    style = Stroke(width = 2f),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 饱和 / 明度方块 ----------------
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun handle(pos: Offset) {
                            // ⚠️ `size` 在 pointerInput 里是 IntSize，除法要先转 Float
                            sat = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                            value = 1f - (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                            emit()
                        }
                        handle(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            handle(change.position)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val pure = hsvToColor(hue, 1f, 1f)
                // ① 纯色底 ② 白→透明（横向，饱和） ③ 透明→黑（纵向，明度）
                drawRect(color = pure, size = size)
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                    size = size,
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                    size = size,
                )
                // 当前点
                val marker = Offset(sat * size.width, (1f - value) * size.height)
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = marker)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 7.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

/**
 * 预设色板（取色器下面的快捷行）。
 *
 * 挑的是这个 App 里**已经在用**的几档：画廊的天空蓝 / 青绿 / 靛蓝 / 琥珀，
 * 加上黑白灰方便调文字与底色 —— 不塞一堆用不上的颜色。
 */
val NaiPickerSwatches: List<Color> = listOf(
    Color(0xFF4FA8C8), // 天空蓝（经典主色）
    Color(0xFF2E8B93), // 青绿
    Color(0xFF3D5A8A), // 靛蓝
    Color(0xFF9FDCEB), // 淡青（胶囊浅色那档）
    Color(0xFFE0A458), // 琥珀（高亮）
    Color(0xFFD96A6A), // 砖红
    Color(0xFF6E7B85), // 中性灰
    Color(0xFF101619), // 近黑（文字/底色）
    Color(0xFFF6F9FB), // 近白（底色）
    Color(0xFFFFFFFF), // 纯白
)

/** 一排预设色：点一下就换（直接回调，不再中转）。 */
@Composable
fun NaiSwatchRow(
    colors: List<Color>,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
    selected: Color? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { c ->
            val isSelected = selected != null && c.toHexString() == selected.toHexString()
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}

/** 十六进制输入 + 即时校验（用户想手输色值时用；不做成唯一入口）。 */
@Composable
fun NaiHexField(
    value: Color,
    onHex: (Color) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toHexString()) }
    var bad by remember { mutableStateOf(false) }
    // 外部颜色变了（拖动取色）→ 跟着刷新文本，除非用户正在手输（那时文本是权威）
    LaunchedEffect(value) {
        val hex = value.toHexString()
        if (!text.equals(hex, ignoreCase = true) && !bad) text = hex
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val parsed = parseHexColor(raw)
            bad = parsed == null && raw.isNotBlank()
            if (parsed != null) onHex(parsed)
        },
        label = { Text("HEX") },
        singleLine = true,
        isError = bad,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.width(140.dp),
    )
}

/** 当前色预览：大色块 + 十六进制。 */
@Composable
fun NaiColorPreview(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            color.toHexString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// HSV ↔ Color（纯计算，放这儿免得调用方各自实现一遍）
// ---------------------------------------------------------------------------

/** Color → `[h(0..360), s(0..1), v(0..1)]`。 */
fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == red -> 60f * (((green - blue) / d) % 6f)
        max == green -> 60f * (((blue - red) / d) + 2f)
        else -> 60f * (((red - green) / d) + 4f)
    }
    val s = if (max == 0f) 0f else d / max
    return floatArrayOf((h + 360f) % 360f, s, max)
}

/** `[h, s, v]` → Color（输入自动夹到合法区间）。 */
fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
