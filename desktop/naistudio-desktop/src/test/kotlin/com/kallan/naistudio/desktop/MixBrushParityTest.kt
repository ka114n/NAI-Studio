package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * **㉜b：混色类笔刷（`blending` / `water` / `colorStretch` > 0）接上 Skia 抗锯齿打点**的判据 ✓。
 *
 * ## 为什么原来"一颗一颗的点" ✗
 *
 * `ImageEditSession.stampDabAt` 的门槛里原来有一条 `!brush.wantsMix` ✗ —— 混色笔被它挡在
 * "直接画在层表面上"（Skia 抗锯齿椭圆 ✓）之外，自动退回**手写 coverage 光栅** ✗ ⇒
 * 同一支笔"开一个参数就换了一个光栅器"，观感就是一个个点 ✓（用户原话：
 * 「**选用水彩笔一类的画笔还是能看出是一个个的点，为什么不能做连贯呢**」✗）。
 *
 * ## 真值还是**网页自己**跑出来的（㉗ 那套管道 ✓）
 *
 * `docs/brush-lab-simple-mix-truth.json`：CEF 打开 `brush-lab-simple.html`，
 * 把随机项/纸纹全关成中性，只开混色参数，用**网页自己的** `walkTo()/stamp()/sampleAvg()/
 * dabStep()/nibRadius()` 跑同一串 21 个输入点（每组 928 颗笔尖 ✓），逐颗记下：
 *   · 采样色（`sampleAvg()` 的原样输出 ✓）、
 *   · 拖色之后那个色（算式与 `stamp()` 里那两行同源 ✓）、
 *   · **画上去的颜色**（`stamp()` 自己写进 `st.mix` 的那一份 ✓）。
 *
 * 底图刻意是**三块纯色矩形**（网页 `fillRect` ✓ / Kotlin 直接写像素 ✓）——两边逐字节同源 ✓，
 * 这样"采样色的差"只反映**采样这条链**，不掺"底图本身的差" ✓。
 *
 * ## 三条判据（都写进断言 ✓，阈值不是"永远绿"的宽容差 ✗）
 *
 *  ① **逐颗笔尖**：颗数 / 半径 / 步距 / 位置 / **采样色** / 拖色后的色 / 画上去的色 —— 逐项对齐 ✓；
 *  ② **导出 PNG 逐像素**：白底合成后与网页导出的那张图比（墨迹 / 中间灰阶 / 逐像素平均差 ✓）；
 *  ③ **走的是表面那条路**：`strokeOnLayerSurface == true` ✓ ——
 *    不然"混色到底走没走 Skia"就只是文档里的一句话 ✗。
 */
class MixBrushParityTest {

    private val platform: Platform = desktopPlatform()

    private fun docsFile(name: String): File? =
        listOf(File("../docs/$name"), File("docs/$name")).firstOrNull { it.isFile }

    private fun truth(): JSONObject {
        val f = docsFile("brush-lab-simple-mix-truth.json")
        assertTrue("混色真值文件找不到（先在 tools/webview-spike 里跑一次 `--args=mix`）✗", f != null)
        return JSONObject(f!!.readText())
    }

    /** 中性档 + 指定的混色参数（其余全关 ✓ —— 与抽真值时网页那一套逐项一致 ✓）。 */
    private fun brush(blending: Float, water: Float, colorStretch: Float) = BrushSpec(
        paperOn = false,
        paperStrength = 0f,
        spreadNoise = false,
        spreadNoiseStrength = 0f,
        blending = blending,
        water = water,
        colorStretch = colorStretch,
        sizeJitter = 0f,
        wxJitter = 0f,
        countJitter = 0f,
        scattering = 0f,
        count = 1,
        hueJitter = 0f,
        saturationJitter = 0f,
        brightnessJitter = 0f,
        colJitter = 0f,
        opacity = 100f,
        spacing = 10f,
        wxRatio = 0f,
        angleControl = 1,
        angle = 0f,
        angleJitter = 0f,
        integerPosition = 0,
        allDirScattering = 0,
        gaussianDistribution = 0,
        applyToEachShape = 0,
        scaling = 100f,
        shapeSize = 100f,
        absoluteSize = 0,
        density = 100f,
        minDensity = 0f,
        pressureAlpha = false,
    )

    private fun decode(png: ByteArray): IntArray? {
        val image = platform.images.decodeBytes(png) ?: return null
        val px = platform.images.pixelsOf(image)
        image.recycle()
        return px
    }

    /** 底图（三块纯色矩形 ✓，与网页 `fillRect` 同色 ✓）。 */
    private fun basePixels(w: Int, h: Int, base: org.json.JSONArray): IntArray {
        val px = IntArray(w * h)
        for (i in 0 until base.length()) {
            val r = base.getJSONObject(i)
            val argb = 0xFF000000.toInt() or r.getString("color").removePrefix("#").toInt(16)
            val x0 = r.getInt("x")
            val y0 = r.getInt("y")
            val x1 = (x0 + r.getInt("w")).coerceAtMost(w)
            val y1 = (y0 + r.getInt("h")).coerceAtMost(h)
            for (y in y0 until y1) {
                java.util.Arrays.fill(px, y * w + x0, y * w + x1, argb)
            }
        }
        return px
    }

    private fun overWhite(src: Int): Int {
        val a = ((src ushr 24) and 0xFF) / 255f
        if (a <= 0f) return 0xFFFFFFFF.toInt()
        fun ch(shift: Int): Int = ((((src ushr shift) and 0xFF) * a + 255f * (1f - a)) + 0.5f)
            .toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun pngOf(pixels: IntArray, w: Int, h: Int): ByteArray {
        val info = org.jetbrains.skia.ImageInfo(
            w, h,
            org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.UNPREMUL,
        )
        val bytes = ByteArray(w * h * 4)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
        val bitmap = org.jetbrains.skia.Bitmap()
        assertTrue("位图分配失败 ✗", bitmap.allocPixels(info))
        bitmap.installPixels(info, bytes, w * 4)
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
    }

    private class Diff {
        var maxSample = 0.0
        var meanSample = 0.0
        var worstSample = 0.0
        var maxCarried = 0.0
        var maxDrawn = 0.0
        var maxPos = 0.0
        var maxRadius = 0.0
        var maxStep = 0.0
        var mirrorDiff = 0
        var mirrorMax = 0
        var dabsWeb = 0
        var dabsKotlin = 0
        var sampledWeb = 0
        var sampledKotlin = 0
        var reusedKotlin = 0
        var inkWeb = 0
        var inkKotlin = 0
        var midWeb = 0
        var midKotlin = 0
        var meanPix = 0.0
        var maxPix = 0
        var pixOver2 = 0
        var pixOver8 = 0
    }

    @Test
    fun mix_brushes_match_the_web_group_by_group() {
        val truth = truth()
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val base = truth.getJSONArray("base")
        val groups = truth.getJSONArray("groups")
        val failures = ArrayList<String>()

        println(
            "[㉜b] 混色真值：$w×$h、${groups.length()} 组、每组 21 个输入点（网页自己跑的 ✓）\n" +
                "[㉜b] 判据：逐颗笔尖的 采样色 / 拖色色 / 画上去的色 / 半径 / 步距 / 位置 + 导出 PNG 逐像素",
        )

        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            val id = g.getString("id")
            val params = g.getJSONObject("params")
            val spec = brush(
                blending = params.getDouble("blending").toFloat(),
                water = params.getDouble("water").toFloat(),
                colorStretch = params.getDouble("colorStretch").toFloat(),
            )
            val points = g.getJSONArray("points")
            val webDabs = g.getJSONArray("dabs")
            val stats = g.getJSONObject("stats")

            val session = ImageEditSession(w, h, basePixels(w, h, base))
            session.dabProbeEnabled = true
            session.beginStroke(
                points.getJSONObject(0).getDouble("x").toFloat(),
                points.getJSONObject(0).getDouble("y").toFloat(),
                StrokeSpec(
                    mode = StrokeMode.PAINT,
                    brushPixels = truth.getInt("size"),
                    color = 0xFF1A1A1A.toInt(),
                    brush = spec,
                    minRadiusRatio = 0.15f,
                    pressureCurve = 1f,
                ),
                pressure = points.getJSONObject(0).getDouble("p").toFloat(),
            )
            for (i in 1 until points.length()) {
                val p = points.getJSONObject(i)
                session.strokeTo(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("p").toFloat())
            }
            session.endStroke()
            val log = session.dabProbeLog
            val d = Diff()
            d.dabsWeb = webDabs.length()
            d.dabsKotlin = log.size
            var sampleSum = 0.0
            var sampleN = 0
            var worstIndex = -1
            var worstDetail = ""

            // ---- 采样源口径（㉜b 的关键一条 ✓）：`pixels` 必须是**层表面的逐字节镜像** ----
            // 采样走的是 `pixels`（= 网页 `getImageData` 同口径的非预乘 ARGB ✓）；
            // 它要是不等于表面，那"采到的"就不是"画出来的" ✗（这一条钉死它 ✓）。
            // ⚠️ 这里**自己用 skiko 读**、不借 `SkiaDabRaster` ✗：那是个"只增不减"的共享缓冲，
            // 借它读一张整页会把 `peakReadBytes` 顶到整页大小 ⇒ 后面 `SkiaStampCostTest` 的
            // "大笔刷缓冲要跟着长大"当场红 ✗（这一条真踩过 ✓）。
            run {
                val s = session.layerSurfaceForDisplay()
                if (s == null) {
                    failures += "[$id] 层表面拿不到（采样源口径没法核）✗"
                } else {
                    val info = org.jetbrains.skia.ImageInfo(
                        w, h,
                        org.jetbrains.skia.ColorType.BGRA_8888,
                        org.jetbrains.skia.ColorAlphaType.PREMUL,
                    )
                    val bmp = org.jetbrains.skia.Bitmap()
                    assertTrue("[$id] 位图分配失败 ✗", bmp.allocPixels(info))
                    assertTrue("[$id] 表面回读失败 ✗", s.readPixels(bmp, 0, 0))
                    val raw = bmp.readPixels(info, w * 4, 0, 0)
                    assertTrue("[$id] 位图→字节失败 ✗", raw != null && raw.size >= w * h * 4)
                    val ints = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .asIntBuffer()
                    var diff = 0
                    var maxDiff = 0
                    for (i in 0 until w * h) {
                        val premul = ints.get(i)
                        val a = (premul ushr 24) and 0xFF
                        val un = if (a == 255 || a == 0) {
                            if (a == 0) 0 else premul
                        } else {
                            val f = 255f / a
                            fun ch(shift: Int): Int = ((((premul ushr shift) and 0xFF) * f) + 0.5f)
                                .toInt().coerceIn(0, 255)
                            (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
                        }
                        if (un != session.pixels[i]) {
                            diff++
                            val b = session.pixels[i]
                            maxDiff = maxOf(
                                maxDiff,
                                abs(((un ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)),
                                abs(((un ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)),
                                abs((un and 0xFF) - (b and 0xFF)),
                                abs(((un ushr 24) and 0xFF) - ((b ushr 24) and 0xFF)),
                            )
                        }
                    }
                    d.mirrorDiff = diff
                    d.mirrorMax = maxDiff
                    if (diff != 0) {
                        failures += "[$id] `pixels` 与层表面不一致：$diff 个像素（最大通道差 $maxDiff）✗"
                    }
                }
            }

            val n = minOf(webDabs.length(), log.size)
            for (i in 0 until n) {
                val wd = webDabs.getJSONObject(i)
                val kd = log[i]
                d.maxPos = maxOf(
                    d.maxPos,
                    abs(kd.x - wd.getDouble("x").toFloat()).toDouble(),
                    abs(kd.y - wd.getDouble("y").toFloat()).toDouble(),
                )
                d.maxRadius = maxOf(d.maxRadius, abs(kd.radius - wd.getDouble("r").toFloat()).toDouble())
                d.maxStep = maxOf(d.maxStep, abs(kd.stepPixels - wd.getDouble("step").toFloat()).toDouble())
                if (wd.has("sR")) {
                    d.sampledWeb++
                    val dr = abs(kd.plan.sampleR - wd.getDouble("sR").toFloat()).toDouble()
                    val dg = abs(kd.plan.sampleG - wd.getDouble("sG").toFloat()).toDouble()
                    val db = abs(kd.plan.sampleB - wd.getDouble("sB").toFloat()).toDouble()
                    val m = maxOf(dr, dg, db)
                    d.maxSample = maxOf(d.maxSample, m)
                    sampleSum += m
                    sampleN++
                    if (m > d.worstSample) {
                        d.worstSample = m
                        worstIndex = i
                        worstDetail = "dab#$i x=${"%.2f".format(kd.x)},${"%.2f".format(kd.y)}" +
                            " Kotlin=(${"%.2f".format(kd.plan.sampleR)},${"%.2f".format(kd.plan.sampleG)}," +
                            "${"%.2f".format(kd.plan.sampleB)})" +
                            " 网页=(${"%.2f".format(wd.getDouble("sR"))},${"%.2f".format(wd.getDouble("sG"))}," +
                            "${"%.2f".format(wd.getDouble("sB"))})" +
                            " 采样点=(${"%.1f".format(wd.getDouble("sx"))},${"%.1f".format(wd.getDouble("sy"))})" +
                            " half=${"%.3f".format(wd.getDouble("half"))}" +
                            " 窗口内已画过（前几颗）=${if (worstIndex > 0) "是" else "否"}"
                    }
                    val cr = abs(kd.plan.mixR - wd.getDouble("cR").toFloat()).toDouble()
                    val cg = abs(kd.plan.mixG - wd.getDouble("cG").toFloat()).toDouble()
                    val cb = abs(kd.plan.mixB - wd.getDouble("cB").toFloat()).toDouble()
                    d.maxCarried = maxOf(d.maxCarried, cr, cg, cb)
                }
                if (kd.plan.sampled) d.sampledKotlin++
                // 画上去的色（网页 = `stamp()` 自己写的 `st.mix` ✓；Kotlin = 计划里那个形状的 ARGB ✓）
                val shape = kd.plan.shapes.firstOrNull()
                if (shape != null && wd.has("mR")) {
                    val ar = ((shape.colorArgb ushr 16) and 0xFF).toDouble()
                    val ag = ((shape.colorArgb ushr 8) and 0xFF).toDouble()
                    val ab = (shape.colorArgb and 0xFF).toDouble()
                    d.maxDrawn = maxOf(
                        d.maxDrawn,
                        abs(ar - wd.getDouble("mR")),
                        abs(ag - wd.getDouble("mG")),
                        abs(ab - wd.getDouble("mB")),
                    )
                }
            }
            d.meanSample = if (sampleN > 0) sampleSum / sampleN else 0.0
            d.reusedKotlin = session.strokeDabStats().sampleReuses

            // ---- ② 导出 PNG 逐像素（白底合成 ✓，与网页 `savePNG()` 一个口径 ✓）----
            val composited = IntArray(w * h) { overWhite(session.pixels[it]) }
            val kotlinPng = pngOf(composited, w, h)
            // 顺手落一份到 build/ 便于**肉眼对**（判据还是上面那些像素数字 ✓，这份只当旁证 ✓）
            runCatching {
                val out = File("build/mix-kotlin-$id.png")
                out.parentFile?.mkdirs()
                out.writeBytes(kotlinPng)
            }
            val kotlinPixels = decode(kotlinPng)
            val webPng = docsFile("brush-lab-simple-mix-$id.png")
            assertTrue("[$id] 网页导出的那张 PNG 找不到 ✗", webPng != null)
            val web = decode(webPng!!.readBytes())
            assertTrue("[$id] 两张图都必须解得开 ✗", kotlinPixels != null && web != null)
            var sum = 0L
            for (i in kotlinPixels!!.indices) {
                val a = kotlinPixels[i] and 0xFF
                val b = web!![i] and 0xFF
                if (a < 200) d.inkKotlin++
                if (b < 200) d.inkWeb++
                if (a in 40..215) d.midKotlin++
                if (b in 40..215) d.midWeb++
                val dd = abs(a - b)
                sum += dd
                if (dd > d.maxPix) d.maxPix = dd
                if (dd > 2) d.pixOver2++
                if (dd > 8) d.pixOver8++
            }
            d.meanPix = sum.toDouble() / kotlinPixels.size

            val inkPct = 100.0 * abs(d.inkKotlin - d.inkWeb) / maxOf(1, d.inkWeb)
            val midPct = 100.0 * abs(d.midKotlin - d.midWeb) / maxOf(1, d.midWeb)
            println(
                "[㉜b] 组 $id（blending=${params.getDouble("blending")} water=${params.getDouble("water")} " +
                    "colorStretch=${params.getDouble("colorStretch")} ⇒ mixT=" +
                    "%.4f".format(java.util.Locale.US, params.getDouble("mixT")) + "）\n" +
                    "    笔尖：网页 ${d.dabsWeb} / Kotlin ${d.dabsKotlin}（采样过 ${d.sampledWeb} / ${d.sampledKotlin}，" +
                    "复用 ${d.reusedKotlin}）  半径差 ≤ ${"%.4f".format(d.maxRadius)}" +
                    "  步距差 ≤ ${"%.4f".format(d.maxStep)}  位置差 ≤ ${"%.2f".format(d.maxPos)} px\n" +
                    "    采样色：**逐点差最大 ${"%.3f".format(d.maxSample)}、平均 ${"%.3f".format(d.meanSample)}**" +
                    "（0..255 / 通道 ✓）  拖色后 ≤ ${"%.3f".format(d.maxCarried)}" +
                    "  画上去的色 ≤ ${"%.3f".format(d.maxDrawn)}\n" +
                    "    PNG（$w×$h）：墨迹 网页 ${d.inkWeb} / Kotlin ${d.inkKotlin} = 差 " +
                    "${"%.2f".format(inkPct)}%、中间灰阶 网页 ${d.midWeb} / Kotlin ${d.midKotlin} = 差 " +
                    "${"%.2f".format(midPct)}%、逐像素平均差 ${"%.3f".format(d.meanPix)}（最大 ${d.maxPix}，" +
                    ">2 的 ${d.pixOver2} 个 / >8 的 ${d.pixOver8} 个 = " +
                    "${"%.4f".format(100.0 * d.pixOver2 / (w * h))}%）\n" +
                    "    采样源口径：`pixels` vs 层表面逐字节 ${if (d.mirrorDiff == 0) "一致 ✓" else "差 ${d.mirrorDiff} 个 ✗"}（最大通道差 ${d.mirrorMax}）\n" +
                    "    采样差最大的那一颗：$worstDetail\n" +
                    "    走的是表面那条路：${session.strokeOnLayerSurface}（混色必须 true ✓）",
            )

            // ---- ③ 混色必须真的走 Skia 表面 ----
            if (!session.strokeOnLayerSurface) {
                failures += "[$id] 混色笔**没走**层表面（= 退回手写 coverage 光栅 ✗）"
            }
            if (!session.dabProbeLog.all { it.plan.sampled } && d.sampledWeb > 0) {
                failures += "[$id] 有笔尖没采样（网页每颗都采了 ✗）"
            }
            // ---- ① 逐颗对齐 ----
            // 阈值怎么定的（**不是"宽容差"** ✗）：每条都取"实测到的最坏那一档"再留一点余量，
            // 拿掉本批任意一条修复都会当场红 ✓：
            //   · 颗数 / 半径 / 步距 / 位置：**必须逐项相等**（实测 928/928、0.0000、0.0000、0.00 ✓）；
            //   · 采样色：实测最大 2.556（`all` 组）、平均最大 1.151 —— 而"不混色反馈"的
            //     `stretch60` 组只有 0.185 / 0.011 ✓ ⇒ 这点差就是**逐颗 ±1 的舍入**
            //     （我们 `roundToInt` / 网页 `|0` 截断，加上预乘↔非预乘那一次舍入）
            //     被"笔边画边采"放大出来的 ✓。带 `SAMPLE_REUSE_PX` 老复用那版这里是 **86 / 3.11** ✗。
            //   · PNG：与普通笔判据②**同一档阈值**（墨迹 ≤3% / 中间灰阶 ≤15% / 平均 ≤3 ✓），
            //     实测 0.00% / ≤0.10% / ≤0.047 ✓。
            if (d.dabsKotlin != d.dabsWeb) failures += "[$id] 笔尖颗数：网页 ${d.dabsWeb} vs Kotlin ${d.dabsKotlin}"
            if (d.maxRadius > 0.02) failures += "[$id] 半径差 ${d.maxRadius}"
            if (d.maxStep > 0.02) failures += "[$id] 步距差 ${d.maxStep}"
            if (d.maxPos > 0.5) failures += "[$id] 位置差 ${d.maxPos}"
            if (d.reusedKotlin != 0) {
                failures += "[$id] 还在复用旧采样（${d.reusedKotlin} 次 ✗ —— 画布每颗都在变，复用就是采旧的 ✗）"
            }
            if (d.maxSample > 3.0) failures += "[$id] 采样色逐点差 ${d.maxSample} > 3"
            if (d.meanSample > 1.5) failures += "[$id] 采样色平均差 ${d.meanSample} > 1.5"
            if (d.maxCarried > 3.0) failures += "[$id] 拖色后色差 ${d.maxCarried} > 3"
            if (d.maxDrawn > 2.5) failures += "[$id] 画上去的色差 ${d.maxDrawn} > 2.5"
            if (inkPct > 3.0) failures += "[$id] 墨迹像素差 ${"%.2f".format(inkPct)}% > 3%"
            if (midPct > 15.0) failures += "[$id] 中间灰阶差 ${"%.2f".format(midPct)}% > 15%"
            if (d.meanPix > 3.0) failures += "[$id] 逐像素平均差 ${"%.3f".format(d.meanPix)} > 3"
            val over2Pct = 100.0 * d.pixOver2 / (w * h)
            if (over2Pct > 1.0) failures += "[$id] 差 >2 的像素占 ${"%.3f".format(over2Pct)}% > 1%"
            // 网页那一侧的自证（真值文件本身对不对 ✓）
            if (stats.getInt("dabs") != d.dabsWeb) failures += "[$id] 真值文件自己的 dabs 对不上 ✗"
        }

        if (failures.isNotEmpty()) {
            throw AssertionError(
                "混色笔与网页不一致：\n" + failures.joinToString("\n"),
            )
        }
    }
}
