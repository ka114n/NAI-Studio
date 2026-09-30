package com.kallan.naistudio.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBubble
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicText
import com.kallan.naistudio.models.ComicTextAlign
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.FontLibrary
import com.kallan.naistudio.ui.bubbleOutline
import com.kallan.naistudio.ui.bubbleStyleOf
import com.kallan.naistudio.ui.bubbleTailFraction
import java.io.File

/**
 * **拼页导出用的"只画不接手势"版气泡 / 文本层**（高级漫画第 ⑦ 项）。
 *
 * 为什么不直接用界面那一版（`ComicOverlayLayers.kt` 的 `BubbleItem` / `TextItem`）：
 * 那两个是 `BoxScope` 里的**交互节点**（点选 / 双击 / 拖动 / 四角把手 / 选中高亮 / "双击打字"占位字 ✗）——
 * 把它们画进导出图里，等于把**界面控件**烤进成图 ✗。所以这里只保留"用户看见的那点内容" ✓：
 * 轮廓 + 泡里的字（空的不画占位提示 ✗）、文本层就是那段字 ✓。
 *
 * ## 单位：**1dp = 1px**（调用方负责 ✓）
 *
 * 模型里的坐标 / 字号全是**底板像素** ✓。电脑端的栅格化用
 * `Density(1f)` 开一张"页大小"的画布（见 `DesktopComicRaster` ✓），
 * 于是这里直接 `offset { 像素值.dp }` 就落在正确的位置上 ✓ —— 和界面里
 * `(x * scale).dp`（scale = dp / 底板像素）是同一个公式，只是导出时 scale = 1 ✓。
 *
 * ⚠️ 只有**这一层自己**不透明地画出来（每层的透明度由 `ComicComposer` 合成时按
 * `ComicLayer.opacity` 处理 ✓）—— 一处管透明度，别两处各乘一次（会变成 opacity² ✗）。
 */
@Composable
fun ComicOverlayExportContent(
    page: ComicBoardPage,
    layerId: String,
    platform: Platform,
) {
    val layer = page.layers.firstOrNull { it.id == layerId } ?: return
    Box(Modifier.fillMaxSize()) {
        when (layer.kind) {
            ComicLayer.Kind.BUBBLE -> page.bubbles.firstOrNull { it.id == layerId }
                ?.let { ExportBubble(it, platform) }

            ComicLayer.Kind.TEXT -> page.texts.firstOrNull { it.id == layerId }
                ?.let { ExportText(it, platform) }

            // 位图层不在这里画（底板 / 生成层由 ComicComposer 负责 ✓）
            else -> Unit
        }
    }
}

/** 泡身的墨色（和界面那份 `ComicInk` 同一个色值 ✓ —— 漫画是内容，不跟主题走 ✓）。 */
private val ExportInk = Color(0xFF1A1A1A)

@Composable
private fun ExportBubble(bubble: ComicBubble, platform: Platform) {
    val style = bubbleStyleOf(bubble.style)
    val tail = bubble.tail?.let { (tailX, tailY) ->
        bubbleTailFraction(tailX, tailY, bubble.x, bubble.y, bubble.w, bubble.h)
    }
    Box(
        Modifier
            .offset(x = bubble.x.dp, y = bubble.y.dp)
            .size(
                bubble.w.coerceAtLeast(1f).dp,
                bubble.h.coerceAtLeast(1f).dp,
            )
            // ⚠️⚠️ **底色 + 描边走 `bubbleOutline`（`bubblePath` + `drawPath` ✓）**，
            // 绝不用 `.background(...) + .border(…, bubbleShape(...))` ✗✗ ——
            // 电脑端 `Modifier.border` + **GenericShape** 内部拿一张 1×1 位图当画笔、
            // 抛 `RuntimeException: Failed to Image::makeFromBitmap` ✗
            //（2026-09-20 用户实机就是这条 ✗；回归钉子 `ComicOverlayDrawTest` ✓）。
            // 走同一条路径还带来一个好处：**界面那颗气泡和导出的这一层逐字同源** ✓，
            // 不会再"界面看着对、导出少一条边" ✗。
            .bubbleOutline(
                style = style,
                tailFraction = tail,
                fill = Color.White,
                // 1.5dp：导出画布是 `Density(1f)` → 1.5px，和界面那条轮廓一样粗 ✓
                stroke = ExportInk,
                strokeWidth = 1.5.dp,
            ),
    ) {
        // 空气泡**不画占位提示**（"双击打字"是界面上的东西 ✗）
        if (bubble.text.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bubble.text,
                    style = exportTextStyle(
                        platform = platform,
                        fontFile = bubble.fontFile,
                        fontSize = bubble.fontSize,
                        colorArgb = bubble.colorArgb,
                        align = bubble.align,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExportText(text: ComicText, platform: Platform) {
    if (text.text.isEmpty()) return
    Box(
        Modifier
            .offset(x = text.x.dp, y = text.y.dp)
            .size(
                text.w.coerceAtLeast(1f).dp,
                text.h.coerceAtLeast(1f).dp,
            ),
    ) {
        Box(Modifier.fillMaxSize().padding(2.dp)) {
            Text(
                text = text.text,
                style = exportTextStyle(
                    platform = platform,
                    fontFile = text.fontFile,
                    fontSize = text.fontSize,
                    colorArgb = text.colorArgb,
                    align = text.align,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 导出版的文字样式：**字号不乘页面缩放**（导出画布本来就是 1:1 底板像素 ✓），
 * 字体走 [Platform.comicFontFamily]（拿不到 = 系统默认字体 ✓，绝不崩 ✗）。
 *
 * ⚠️ 和 `ComicOverlayLayers.kt` 里那个 `overlayTextStyle` 是**同一个口径的两份实现**：
 * 那一份是界面用（字号要乘窗口缩放 ✓），这一份是导出用（1:1 ✓）。两份合流要等
 * 界面那条线收工（它那个文件当时还在飞 ✗，这一轮不去动它比较稳）。
 */
@Composable
private fun exportTextStyle(
    platform: Platform,
    fontFile: String?,
    fontSize: Float,
    colorArgb: Int,
    align: ComicTextAlign,
): TextStyle {
    val density = LocalDensity.current
    val family = remember(platform, fontFile) { exportFontFamily(platform, fontFile) }
    return TextStyle(
        color = Color(colorArgb),
        fontSize = with(density) { fontSize.dp.toSp() },
        fontFamily = family,
        textAlign = when (align) {
            ComicTextAlign.START -> TextAlign.Start
            ComicTextAlign.CENTER -> TextAlign.Center
            ComicTextAlign.END -> TextAlign.End
        },
    )
}

/** 字体落盘名 → `FontFamily`；任何一步拿不到就 null（回落系统默认 ✓）。 */
private fun exportFontFamily(platform: Platform, fontFile: String?): FontFamily? {
    val name = fontFile?.takeIf { it.isNotBlank() } ?: return null
    val path = runCatching { File(FontLibrary.dir(platform), name).absolutePath }.getOrNull() ?: return null
    return runCatching { platform.comicFontFamily(path) }.getOrNull()
}
