package com.kallan.naistudio.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kallan.naistudio.platform.logWarn
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.RootPaneContainer
import org.jetbrains.skia.Image as SkiaImage

/**
 * 抓下来的一帧：位图 + 它在**窗口客户区**里的物理像素位置/尺寸。
 *
 * [offsetX] / [offsetY] 非零只在一种情况下出现：**窗口有一部分在屏幕外**
 *（最典型的就是最大化 —— Windows 会把窗口四周各撑出 8px 去）。
 * 那时只抓屏幕里那部分，画遮罩时按这个偏移摆，屏幕外的部分本来也看不见。
 */
data class WindowFrame(
    val bitmap: ImageBitmap,
    /** 截图在窗口客户区里的物理像素偏移。 */
    val offsetX: Int,
    val offsetY: Int,
    /** 窗口客户区的物理像素尺寸（换算逻辑坐标用）。 */
    val windowWidth: Int,
    val windowHeight: Int,
)

/**
 * 抓**当前窗口客户区**那一帧，给主题扩散动画当"旧画面"。
 *
 * ## ⚠️ 2026-09-19：这里以前会**静默失败**，于是"深浅色动画失效"
 *
 * 抓屏用的是窗口客户区在屏幕上的矩形。窗口一旦**最大化**，Windows 会把窗口四周各撑出 8px
 *（实测 `GetWindowRect` = `(-8,-8)-(2568,1400)`，比 2560×1440 的屏幕还宽），
 * 于是 `Robot.createScreenCapture` 拿到一个**跑到屏幕外**的矩形 → 抛异常 → 这里 `getOrNull()`
 * 吞掉 → 调用方退回"直接切主题"：**动画就没了，而且没有任何提示**（用户报的正是这个）。
 *
 * 现在改成：
 *  1. 把矩形**裁到屏幕内**再抓（屏幕外的部分本来就看不见）；
 *  2. 连同"截图在窗口里的偏移 + 窗口物理尺寸"一起交给遮罩层，由它按逻辑坐标摆正；
 *  3. 真失败时**写一条日志**（`%APPDATA%\NAI Studio\app.log`），不再无声无息。
 *
 * 其余口径不变（见下面各段注释）：抓的是客户区而不是整窗、走 PNG 转换省得手写像素格式。
 */
fun captureWindowFrame(window: Window): WindowFrame? {
    return runCatching {
        // ⚠️ `contentPane` 在 `RootPaneContainer` 上（JFrame / JDialog），不在 `java.awt.Window` 上 ——
        // 所以先转一下；万一不是那种窗口（或还没挂好），退回"按 insets 抠出客户区"。
        val pane = (window as? RootPaneContainer)?.contentPane
        val paneRect = if (pane != null && pane.isShowing && pane.width > 0 && pane.height > 0) {
            val onScreen = pane.locationOnScreen ?: return@runCatching null
            Rectangle(onScreen.x, onScreen.y, pane.width, pane.height)
        } else {
            val insets = window.insets ?: return@runCatching null
            val width = window.width - insets.left - insets.right
            val height = window.height - insets.top - insets.bottom
            if (window.width <= 0 || window.height <= 0 || width <= 0 || height <= 0) {
                return@runCatching null
            }
            Rectangle(
                window.x + insets.left,
                window.y + insets.top,
                width,
                height,
            )
        }

        // 1) 裁到屏幕内（最大化时窗口比屏幕大，必须裁，否则 Robot 直接报错）
        val screen = window.graphicsConfiguration?.bounds
            ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .bounds
        val capture = paneRect.intersection(screen)
        if (capture.width <= 0 || capture.height <= 0) {
            logWarn("ThemeWipe", "抓屏跳过：窗口完全在屏幕外 $paneRect")
            return@runCatching null
        }

        val frame = Robot().createScreenCapture(capture)

        // 走一次 PNG，省得手写像素格式（ARGB ↔ BGRA）的搬运：
        // 一次主题切换只抓一帧，这点开销比"格式搬错导致整屏颜色发蓝"划算得多。
        val bytes = ByteArrayOutputStream(frame.width * frame.height / 2 + 1024)
        ImageIO.write(frame, "png", bytes)
        WindowFrame(
            bitmap = SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap(),
            offsetX = capture.x - paneRect.x,
            offsetY = capture.y - paneRect.y,
            windowWidth = paneRect.width,
            windowHeight = paneRect.height,
        )
    }.onFailure {
        // ⚠️ 别再静默：以前悄悄退回"直接切主题"，用户只会觉得"动画坏了"
        logWarn("ThemeWipe", "抓屏失败，退回直接切主题：${it.message}")
    }.getOrNull()
}
