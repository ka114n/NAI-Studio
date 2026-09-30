package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.WebBrushWindow
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * **网页笔刷的自检探针**（第 ㉕ 批起 ✓；第 ㉛ 批扩到"装层 + 抬笔自动写回" ✓）。
 *
 * 为什么要有它：这条链（CEF 初始化 → 加载那一页 → 装当前层 → 网页里画一笔 →
 * 自动写回 PNG → console 送回来 → base64 解 → 回传）**没有窗口就验不了** ✗，
 * 而"每次让人手点一遍"不现实 ✓。这个 main 把整条链自动跑一遍，判据全在 stdout 上 ✓。
 *
 * ## ㉛ 批新增的两条（都是**在网页里用合成事件真画一笔** ✓，不是"看着像" ✓）
 *
 *  1. **装层**：喂一张"认得出"的 PNG 进去 ⇒ 网页报 `LAYER:ok <W>x<H> inkPx=N` ✓
 *     （N 是网页**自己**数出来的不透明像素数 ✓）；
 *  2. **自动写回**：用 `cv.dispatchEvent(new PointerEvent(...))` **在那张画布上合成一笔** ✓
 *     （走的是网页**自己的** `pointerdown/move/up` 监听器 ✓，**没有改网页一个字节** ✓），
 *     然后等自动写回把 PNG 送回来 ⇒ 落盘 + 打印字节数 ✓。
 *
 * 跑法（用的就是**交付那份 classpath** ✓ —— `build/jpackage-input` 里的 jar 就是镜像 `app\` 里的）：
 * ```
 * java --add-opens java.desktop/sun.awt=ALL-UNNAMED `
 *      --add-opens java.desktop/java.awt.peer=ALL-UNNAMED `
 *      -cp "build/jpackage-input/(星号)" com.kallan.naistudio.desktop.WebBrushProbeKt
 * ```
 * 工作目录要是 `naistudio-desktop`（`docs/brush-lab-simple.html` 走 `../docs/` 那条候选 ✓）。
 */
fun main() {
    val out = File("build/web-brush-probe.png")
    val layer = File("build/web-brush-probe-layer.png")
    println("PROBE_OPEN=1")

    // ① 造一张"认得出"的当前层（左半红、右半透明 + 一条对角黑线）——
    //    既验"装进去了没有"，也能验"写回来之后它还应在" ✓。
    writeProbeLayer(layer, 640, 480)

    WebBrushWindow.open(pageWidth = 640, pageHeight = 480) { bytes ->
        runCatching { out.writeBytes(bytes) }
        println("PROBE_ADOPT=${bytes.size}")
        println("PROBE_PNG=${out.absolutePath}")
        val size = runCatching { ImageIO.read(out)?.let { it.width to it.height } }.getOrNull()
        println("PROBE_PNG_SIZE=$size")
        true
    }
    // 把当前层交给网页（走得就是 ㉛ 那条"开画前把内容装进网页画布"的路 ✓）
    WebBrushWindow.setLayer(layer.readBytes())

    // 给内核初始化 / 页面加载 / 装层留够时间（内核已经装好时约 10 秒 ✓）。
    Thread.sleep(30_000)
    println("PROBE_STEP=layer")
    println("PROBE_LAYER_REPORT=${WebBrushWindow.lastLayerReport}")

    // ② 在网页画布上**合成一笔**（走网页自己的 pointer 监听器 ✓，不改网页源码 ✓）
    println("PROBE_STEP=paint")
    WebBrushWindow.paintForSelfTest()
    // 自动写回是 350ms 轮询 + 导出 ⇒ 给足 8 秒 ✓
    Thread.sleep(8_000)
    println("PROBE_AUTO_BYTES=${WebBrushWindow.lastAutoAdoptBytes}")

    // ③ 兜底写回（= 关窗 / 退出模式时那一次 ✓）
    println("PROBE_STEP=flush")
    WebBrushWindow.flush()
    Thread.sleep(6_000)
    println("PROBE_STEP=adopt")
    WebBrushWindow.adoptForSelfTest()
    Thread.sleep(12_000)
    println("PROBE_DONE=1")
    exitProcess(0)
}

/** 造一张探针用的"当前层"（尺寸故意和页不一样，验装层时有没有按页尺寸放 ✓）。 */
private fun writeProbeLayer(file: File, w: Int, h: Int) {
    val image = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val argb = when {
                x == y -> 0xFF000000.toInt()               // 对角黑线
                x < w / 2 -> 0xFFFF0000.toInt()            // 左半不透明红
                else -> 0x00000000                          // 右半透明
            }
            image.setRGB(x, y, argb)
        }
    }
    file.parentFile?.mkdirs()
    ImageIO.write(image, "png", file)
    println("PROBE_LAYER_FILE=${file.absolutePath} ${w}x$h")
}
