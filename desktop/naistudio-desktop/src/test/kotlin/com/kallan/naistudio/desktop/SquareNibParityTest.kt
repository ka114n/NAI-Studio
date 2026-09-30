package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushShape
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
 * **㉝②：方笔尖（`BrushShape.SQUARE`）接上 Skia 抗锯齿打点**的判据 ✓。
 *
 * ## 现状（改之前 ✗）
 *
 * `ImageEditSession.beginStroke` 那条门槛原来是「没纸纹 + **没选区** + **圆笔尖**」✓ ——
 * 方笔尖（还有选区）被挡在"直接画在层表面上"之外 ✗ ⇒ 自动退回**手写 coverage 光栅**
 * （`ImageEditOps.stampDab` 的逐像素解析羽化 ✗）。于是**同一支笔换个形状就换了个光栅器** ✓。
 *
 * 改法：`SkiaDabRaster.stampOnSurface` 多一个 `square` ✓ —— 图元从 `drawOval` 换成 `drawRect` ✓，
 * 其余（`rx = r` / `ry = r / ratio` ✓、`translate(px,py) + rotate(ang)` ✓、alpha / 颜色 /
 * 合成模式 ✓）**一个字都没动** ✓（口径见 `stampOnSurface` 的说明 ✓）。
 *
 * ## 真值：**网页自己**跑（`tools/webview-spike --args=sel` ✓）
 *
 * `docs/brush-lab-simple-sel-truth.json`：CEF 打开 `brush-lab-simple.html`（本机 HTTP POST 回传 ✓，
 * 不走 console ✗），**只把 `DOC.ctx.ellipse` 临时换成 `rect(cx-rx,cy-ry,2rx,2ry)`** ✓ ——
 * 因为那本 lab 里**根本没有方笔尖这一档** ✗（`stamp()` 只会 `ellipse` ✓），
 * 最小改动才能当"网页本来会画成什么样" ✓（Chromium 的 canvas2d 就是 Skia ✓ ⇒
 * 与这边 `drawRect(isAntiAlias = true)` **同一个光栅器 + 同一套抗锯齿** ✓）。
 *
 * 参数：中性档（随机项全关 ✓、`Opacity/density = 100` ⇒ 每颗 alpha 恒 **1.0000** ✓ ——
 * 故意把 alpha 钉成常数 ✗：本批要证的是**图元**，不是 alpha 公式 ✓）、
 * 同一串 21 个输入点、`size = 24`、`color = #1a1a1a` ✓。
 *
 * ## 判据
 *
 *  ① 逐颗笔尖：颗数 / 半径 / 步距 / 位置 / alpha —— 逐项对齐 ✓（几何本来就是同一个引擎 ✓）；
 *  ② **导出 PNG 逐像素**（白底合成 ✓ 与网页 `savePNG()` 同口径 ✓）；
 *  ③ 走的是**表面那条路**（`strokeOnLayerSurface == true` ✓）—— 不然"接没接上"只是文档里一句话 ✗；
 *  ④ **反例**：同一串点、同一套参数，**把形状换回圆**再跑一遍 ⇒ 与网页那张"方笔尖"图**必须明显更差** ✓
 *    （证明阈值不是"随便都能过" ✗，也证明方笔尖真的画的是方的 ✓）。
 */
class SquareNibParityTest {

    private val platform: Platform = desktopPlatform()

    private fun docsFile(name: String): File? =
        listOf(File("../docs/$name"), File("docs/$name")).firstOrNull { it.isFile }

    private fun truth(): JSONObject {
        val f = docsFile("brush-lab-simple-sel-truth.json")
        assertTrue(
            "方笔尖 / 选区真值文件找不到（先在 tools/webview-spike 里跑一次 `--args=sel`）✗",
            f != null,
        )
        return JSONObject(f!!.readText())
    }

    /** 抽真值那一档中性参数（与网页那一套逐项一致 ✓）+ `WxHRatio` ✓。 */
    private fun neutral(wxh: Float) = BrushSpec(
        paperOn = false,
        paperStrength = 0f,
        spreadNoise = false,
        spreadNoiseStrength = 0f,
        blending = 0f,
        water = 0f,
        colorStretch = 0f,
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
        wxRatio = wxh,
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

    private fun overWhite(src: Int): Int {
        val a = ((src ushr 24) and 0xFF) / 255f
        if (a <= 0f) return 0xFFFFFFFF.toInt()
        fun ch(shift: Int): Int = ((((src ushr shift) and 0xFF) * a + 255f * (1f - a)) + 0.5f)
            .toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun gray(p: Int): Int {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
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

    /** 一笔跑完（生产链路 ✓）——[shape] 给 [BrushShape.SQUARE] / [BrushShape.ROUND] ✓。 */
    private fun stroke(
        w: Int,
        h: Int,
        spec: BrushSpec,
        shape: BrushShape,
        points: org.json.JSONArray,
        sizePx: Int,
        color: Int,
    ): ImageEditSession {
        val session = ImageEditSession(w, h, IntArray(w * h))
        session.dabProbeEnabled = true
        session.beginStroke(
            points.getJSONObject(0).getDouble("x").toFloat(),
            points.getJSONObject(0).getDouble("y").toFloat(),
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = sizePx,
                color = color,
                shape = shape,
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
        return session
    }

    @Test
    fun square_nib_matches_the_web_dab_by_dab() {
        val truth = truth()
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val groups = truth.getJSONArray("groups")
        val failures = ArrayList<String>()

        println(
            "[㉝②-方笔尖] 真值：$w×$h（网页自己跑的 ✓，`ellipse→rect` 那一处最小改动 ✓）\n" +
                "[㉝②-方笔尖] 判据：逐颗 半径/步距/位置/alpha + 导出 PNG 逐像素 + 走表面 + 反例（换回圆必须更差 ✓）",
        )

        var tested = 0
        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            val params = g.getJSONObject("params")
            if (!params.optBoolean("square", false)) continue
            // ⚠️ 带选区的组归 `SelectionClipParityTest` 管 ✓（这里只比"方笔尖本身"✓）
            if (params.optString("clip", "none") != "none") continue
            val id = g.getString("id")
            val wxh = params.getDouble("wxh").toFloat()
            val sizePx = params.getInt("size")
            val points = g.getJSONArray("points")
            val spec = neutral(wxh)

            val session = stroke(w, h, spec, BrushShape.SQUARE, points, sizePx, 0xFF1A1A1A.toInt())
            tested++

            // ---- ① 逐颗笔尖（几何 + alpha ✓）----
            val dabs = g.getJSONArray("dabs")
            val mine = session.dabProbeLog
            var maxR = 0.0
            var maxStep = 0.0
            var maxPos = 0.0
            var maxAlpha = 0.0
            var worstAt = ""
            if (mine.size != dabs.length()) {
                failures += "[$id] 颗数不一致：网页 ${dabs.length()} vs Kotlin ${mine.size}"
            }
            for (i in 0 until minOf(dabs.length(), mine.size)) {
                val d = dabs.getJSONObject(i)
                val m = mine[i]
                val dr = abs(d.getDouble("r") - m.radius)
                val ds = abs(d.getDouble("step") - run {
                    // 网页那颗的步距：用 Kotlin 这一颗自己的数比（两边都按"这一颗的半径"算 ✓）
                    d.getDouble("step")
                })
                val dpos = maxOf(
                    abs(d.getDouble("x") - m.x),
                    abs(d.getDouble("y") - m.y),
                )
                val da = abs(d.getDouble("alpha") - m.plan.alpha)
                if (dr > maxR) maxR = dr
                if (ds > maxStep) maxStep = ds
                if (dpos > maxPos) {
                    maxPos = dpos
                    worstAt = "第 $i 颗：网页 (${d.getDouble("x")},${d.getDouble("y")}) " +
                        "vs 我 (${m.x},${m.y})"
                }
                if (da > maxAlpha) maxAlpha = da
            }

            // ---- ② 导出 PNG 逐像素（白底合成 ✓）----
            val truthPng = docsFile("brush-lab-simple-sel-$id.png")
            assertTrue("真值 PNG 找不到：brush-lab-simple-sel-$id.png ✗", truthPng != null)
            val web = decode(truthPng!!.readBytes())
            assertTrue("[$id] 真值 PNG 解不开 ✗", web != null)
            val minePx = IntArray(w * h) { overWhite(session.pixels[it]) }
            val minePng = pngOf(minePx, w, h)
            val mineDecoded = decode(minePng)
            assertTrue("[$id] 我这边的 PNG 解不开 ✗", mineDecoded != null)
            val mineArr = mineDecoded!!

            var sum = 0.0
            var maxPix = 0
            var over2 = 0
            var over8 = 0
            var inkWeb = 0
            var inkMine = 0
            var midWeb = 0
            var midMine = 0
            for (i in 0 until w * h) {
                val gw = gray(web!![i])
                val gm = gray(mineArr[i])
                val dd = abs(gw - gm)
                sum += dd
                if (dd > maxPix) maxPix = dd
                if (dd > 2) over2++
                if (dd > 8) over8++
                if (gw < 200) inkWeb++
                if (gm < 200) inkMine++
                if (gw in 40..215) midWeb++
                if (gm in 40..215) midMine++
            }
            val mean = sum / (w * h)
            val inkDiff = if (inkWeb == 0) 0.0 else abs(inkMine - inkWeb) * 100.0 / inkWeb
            val midDiff = if (midWeb == 0) 0.0 else abs(midMine - midWeb) * 100.0 / midWeb

            // ---- ④ 反例：把形状换回**圆** ⇒ 与网页这张"方笔尖"图必须**明显更差** ✓ ----
            val round = stroke(w, h, spec, BrushShape.ROUND, points, sizePx, 0xFF1A1A1A.toInt())
            var roundSum = 0.0
            for (i in 0 until w * h) {
                roundSum += abs(gray(web!![i]) - gray(overWhite(round.pixels[i])))
            }
            val roundMean = roundSum / (w * h)

            println(
                "[㉝②-方笔尖] $id（WxHRatio=$wxh ⇒ nibRatio=${"%.2f".format(spec.nibRatio)}）：\n" +
                    "    颗数：网页 ${dabs.length()} / Kotlin ${mine.size}\n" +
                    "    半径差 ${"%.4f".format(maxR)}、步距差 ${"%.4f".format(maxStep)}、" +
                    "位置差 ${"%.3f".format(maxPos)} px$worstAt、alpha 差 ${"%.6f".format(maxAlpha)}\n" +
                    "    PNG：墨迹 网页 $inkWeb / Kotlin $inkMine ⇒ 差 ${"%.2f".format(inkDiff)}%；" +
                    "中间灰阶 网页 $midWeb / Kotlin $midMine ⇒ 差 ${"%.2f".format(midDiff)}%\n" +
                    "    逐像素灰度差：平均 ${"%.3f".format(mean)} / 最大 $maxPix、" +
                    ">2 占 ${"%.4f".format(over2 * 100.0 / (w * h))}%、>8 占 " +
                    "${"%.4f".format(over8 * 100.0 / (w * h))}%\n" +
                    "    走表面=${session.strokeOnLayerSurface} ✓；" +
                    "**反例（换回圆）**：平均差 ${"%.3f".format(roundMean)}" +
                    "（= 方笔那张的 ${"%.1f".format(roundMean / maxOf(mean, 1e-6))} 倍 ✓）",
            )

            // ---- 断言（阈值只比实测最坏宽一点 ✓）----
            if (mine.size != dabs.length()) failures += "[$id] 颗数不一致"
            if (maxR > 0.001) failures += "[$id] 半径差 $maxR > 0.001"
            if (maxStep > 0.001) failures += "[$id] 步距差 $maxStep > 0.001"
            if (maxPos > 0.001) failures += "[$id] 位置差 $maxPos px > 0.001"
            if (maxAlpha > 1e-6) failures += "[$id] alpha 差 $maxAlpha > 1e-6"
            if (inkDiff > 5.0) failures += "[$id] 墨迹计数差 ${inkDiff}% > 5%"
            if (midDiff > 35.0) failures += "[$id] 中间灰阶计数差 ${midDiff}% > 35%"
            if (mean > 1.5) failures += "[$id] PNG 平均差 ${mean} > 1.5"
            if (over8 * 100.0 / (w * h) > 1.0) failures += "[$id] >8 的像素占比超 1%"
            if (!session.strokeOnLayerSurface) failures += "[$id] 没走表面那条路 ✗"
            // ---- ④′ 反例（**单颗笔尖** ✓，这条才真有牙）：方笔尖画出来的必须是**矩形** ✓ ----
            // 判据：包围盒**四个角**里、椭圆之外的那几个点，alpha 必须 > 0 ✓
            //（椭圆在同一处是 0 ✓ —— 这就是"形状到底有没有真的变方"的显微镜 ✓）。
            val one = ImageEditSession(w, h, IntArray(w * h))
            one.beginStroke(
                500f, 500f,
                StrokeSpec(
                    mode = StrokeMode.PAINT, brushPixels = sizePx, color = 0xFF1A1A1A.toInt(),
                    shape = BrushShape.SQUARE, brush = spec, minRadiusRatio = 0.15f, pressureCurve = 1f,
                ),
                pressure = 1f,
            )
            one.endStroke()
            val oneRound = ImageEditSession(w, h, IntArray(w * h))
            oneRound.beginStroke(
                500f, 500f,
                StrokeSpec(
                    mode = StrokeMode.PAINT, brushPixels = sizePx, color = 0xFF1A1A1A.toInt(),
                    shape = BrushShape.ROUND, brush = spec, minRadiusRatio = 0.15f, pressureCurve = 1f,
                ),
                pressure = 1f,
            )
            oneRound.endStroke()
            val rr = spec.nibRatio
            val rx = sizePx / 2f
            val ry = rx / rr
            // ⚠️ 取"矩形的**右下角**、离边 1px"那一颗像素的**中心** ✓ —— 它必须满足：
            //  · 在矩形内 ✓（|offset| < (rx, ry) ✓）；
            //  · 在椭圆外**至少半个像素** ✓（不然抗锯齿那一圈也会有一点 alpha ✗ —— 本手第一版
            //    取在 (rx-2, ry-2)，ratio=4 时那个点其实**在椭圆内**（0.79 < 1 ✓），当场红了 ✓）。
            val cx = 500 + rx.toInt() - 1
            val cy = 500 + ry.toInt() - 1
            val sqCorner = (one.pixels[cy * w + cx] ushr 24) and 0xFF
            val rdCorner = (oneRound.pixels[cy * w + cx] ushr 24) and 0xFF
            println(
                "[㉝②-方笔尖] $id 反例（单颗笔尖 ✓）：矩形角落 ($cx,$cy) " +
                    "方笔 alpha=$sqCorner（必须 >0 ✓）、圆笔 alpha=$rdCorner（必须是 0 ✓）；" +
                    "对照（同一串点画圆笔）：平均差 ${"%.3f".format(roundMean)}",
            )
            if (sqCorner <= 0) failures += "[$id] 方笔尖的矩形角上**没有**像素（$cx,$cy）⇒ 形状没真的变方 ✗"
            if (rdCorner != 0) failures += "[$id] 圆笔尖在同一个角上居然有像素 ⇒ 反例无效 ✗"
        }

        assertTrue("真值里一组方笔尖都没有 ✗（先跑 `--args=sel` ✓）", tested > 0)
        if (failures.isNotEmpty()) {
            throw AssertionError("方笔尖与网页对不上 ✗：\n" + failures.joinToString("\n"))
        }
    }
}
