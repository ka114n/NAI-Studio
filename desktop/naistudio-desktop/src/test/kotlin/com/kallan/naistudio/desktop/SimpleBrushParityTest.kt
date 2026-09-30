package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * **㉗ 批：把「和网页一样」变成可量化的判据** ✓
 *
 * 真值不是"我觉得像"✗ —— 是**网页自己跑出来的**：
 *   · `docs/brush-lab-simple-truth.json`（`tools/webview-spike` 用 CEF 打开 `brush-lab-simple.html`，
 *     把随机项全关成中性，再让**网页自己的** `walkTo()/stamp()/nibRadius()/dabStep()` 跑同一串
 *     21 个输入点，逐颗笔尖记录下来 ✓ 928 颗）；
 *   · `docs/brush-lab-simple-truth.png`（同一跑，网页导出的那张图 ✓）。
 *
 * 两条判据：
 *  ① [kotlin_pipeline_matches_web_dab_statistics]：**Kotlin 那条真链路**
 *     （[ImageEditSession] 的 `beginStroke/strokeTo/endStroke` ✓ 就是软件画布在用的那条 ✓）
 *     跑同一串输入点 ⇒ 笔尖颗数 / 半径上下限 / 步距上下限必须和网页**逐项对上** ✓；
 *  ② [skia_aa_stamping_reproduces_the_web_png]：把**网页真值里那 928 颗笔尖**用
 *     **skiko 的 Skia 抗锯齿椭圆**重画一遍 ⇒ 和网页导出的 PNG 做像素级对比 ✓
 *     —— 这一条专门回答"渲染方式该不该换成 Skia"（网页 canvas2d 就是 Skia ✓）。
 *
 * 为什么②要"拿网页的笔尖来画"而不用 Kotlin 自己规划出来的笔尖：这样**把两件事拆开** ✓ ——
 * ② 只问"光栅化那一半像不像"✓，① 只问"规划那一半对不对"✓。混在一起就分不清是谁的锅 ✗。
 */
class SimpleBrushParityTest {

    private val platform: Platform = desktopPlatform()

    private fun truthJson(): JSONObject {
        val f = listOf(File("../docs/brush-lab-simple-truth.json"), File("docs/brush-lab-simple-truth.json"))
            .firstOrNull { it.isFile }
        assertTrue("真值文件找不到（先在 tools/webview-spike 里跑一次抽真值）✗", f != null)
        return JSONObject(f!!.readText())
    }

    private fun truthPng(): File? =
        listOf(File("../docs/brush-lab-simple-truth.png"), File("docs/brush-lab-simple-truth.png"))
            .firstOrNull { it.isFile }

    private fun decode(png: ByteArray): IntArray? {
        val image = platform.images.decodeBytes(png) ?: return null
        val px = platform.images.pixelsOf(image)
        image.recycle()
        return px
    }

    /** 中性档（= 抽真值时网页那一套 ✓）：随机项全关、纸纹关、不混色 ✓。 */
    private fun neutralBrush() = BrushSpec(
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

    // ------------------------------------------------------------------
    // 判据 ①：笔尖统计逐项对齐
    // ------------------------------------------------------------------

    @Test
    fun kotlin_pipeline_matches_web_dab_statistics() {
        val truth = truthJson()
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val points = truth.getJSONArray("points")
        val stats = truth.getJSONObject("stats")

        val session = ImageEditSession(w, h, IntArray(w * h))
        session.beginStroke(
            points.getJSONObject(0).getDouble("x").toFloat(),
            points.getJSONObject(0).getDouble("y").toFloat(),
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = 24,
                color = 0xFF1A1A1A.toInt(),
                brush = neutralBrush(),
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
        val s = session.strokeDabStats()

        fun f(d: Double) = String.format(java.util.Locale.US, "%.3f", d)
        println(
            "[㉗-①] 笔尖统计（网页 vs Kotlin）\n" +
                "           dabs      rMin     rMax     stepMin  stepMax\n" +
                "  网页  : ${stats.getInt("dabs")}  ${f(stats.getDouble("rMin"))}  " +
                "${f(stats.getDouble("rMax"))}  ${f(stats.getDouble("stepMin"))}  ${f(stats.getDouble("stepMax"))}\n" +
                "  Kotlin: ${s.dabs}  ${f(s.radiusMin.toDouble())}  ${f(s.radiusMax.toDouble())}  " +
                "${f(s.stepMin.toDouble())}  ${f(s.stepMax.toDouble())}",
        )

        // ⚠️ **已知差距**（㉗ 批实测，别当成"已经一致"✗）：同一串 21 个输入点，
        // 网页落下 928 颗、Kotlin 落 943 颗 ⇒ **多 15 颗（+1.6%）** ✗。
        // 半径 / 步距的上下限都对得上 ✓（说明"单颗笔尖怎么算"是一致的 ✓），
        // 差的量级 ≈ 0.7 颗/段 ⇒ 嫌疑集中在 **`walkTo` 的 `carry` 在"段与段之间"的交接**
        //（网页：`carry = dist - lastStampAt`，超过一个步距再 `% step` ✓ 见 brush-lab-simple.html:569-570 ✓）。
        // 这一条**下一批要收掉**（收掉之后这里的容差要跟着收紧 ✗ 不许留成"永远绿"的宽容差 ✓）。
        assertTrue(
            "笔尖颗数：网页 ${stats.getInt("dabs")} vs Kotlin ${s.dabs}" +
                "（已知差距 +${s.dabs - stats.getInt("dabs")} 颗 = 段间 carry 交接，见本测试注释 ✗）",
            abs(s.dabs - stats.getInt("dabs")) <= 2,
        )
        assertTrue(
            "半径下限：网页 ${stats.getDouble("rMin")} vs Kotlin ${s.radiusMin} ✗",
            abs(s.radiusMin - stats.getDouble("rMin").toFloat()) < 0.05f,
        )
        assertTrue(
            "半径上限：网页 ${stats.getDouble("rMax")} vs Kotlin ${s.radiusMax} ✗",
            abs(s.radiusMax - stats.getDouble("rMax").toFloat()) < 0.05f,
        )
        assertTrue(
            "步距下限：网页 ${stats.getDouble("stepMin")} vs Kotlin ${s.stepMin} ✗",
            abs(s.stepMin - stats.getDouble("stepMin").toFloat()) < 0.05f,
        )
        assertTrue(
            "步距上限：网页 ${stats.getDouble("stepMax")} vs Kotlin ${s.stepMax} ✗",
            abs(s.stepMax - stats.getDouble("stepMax").toFloat()) < 0.05f,
        )
    }

    // ------------------------------------------------------------------
    // 判据 ②：Skia 抗锯齿椭圆 vs 网页 canvas2d
    // ------------------------------------------------------------------

    @Test
    fun production_pipeline_png_matches_the_web_png() {
        val truth = truthJson()
        val pngFile = truthPng()
        assertTrue("真值 PNG 找不到 ✗", pngFile != null)
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val points = truth.getJSONArray("points")

        // ---- 走**生产链路**（㉘ 批：`ImageEditSession` 里打点已经是 Skia 抗锯齿椭圆 ✓）----
        val session = ImageEditSession(w, h, IntArray(w * h))
        session.beginStroke(
            points.getJSONObject(0).getDouble("x").toFloat(),
            points.getJSONObject(0).getDouble("y").toFloat(),
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = 24,
                color = 0xFF1A1A1A.toInt(),
                brush = neutralBrush(),
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

        // 先看**会话像素里到底有没有墨**（分辨"没画上"还是"导出坏了" ✓）
        var sessionInk = 0
        for (p in session.pixels) if (((p ushr 24) and 0xFF) != 0) sessionInk++
        println("[㉘-②] 会话像素里不透明的像素数 = $sessionInk（0 = Skia 打点没写进去 ✗；>0 = 导出那条路的问题）")
        println("[㉘-②] SkiaDabRaster.lastError = ${com.kallan.naistudio.models.SkiaDabRaster.lastError}")

        // 层像素 → 白底 PNG（和网页 `savePng()` 一个口径 ✓）
        val composited = IntArray(w * h)
        for (i in composited.indices) {
            composited[i] = overWhite(session.pixels[i])
        }
        val info = org.jetbrains.skia.ImageInfo(
            w,
            h,
            org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.UNPREMUL,
        )
        val bytes = ByteArray(w * h * 4)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(composited)
        val bitmap = org.jetbrains.skia.Bitmap()
        assertTrue("位图分配失败 ✗", bitmap.allocPixels(info))
        bitmap.installPixels(info, bytes, w * 4)
        val kotlinPng = org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .encodeToData(EncodedImageFormat.PNG)!!.bytes

        val produced = decode(kotlinPng)
        val web = decode(pngFile!!.readBytes())
        assertTrue("两张图都必须解得开 ✗", produced != null && web != null)

        var inkWeb = 0
        var inkKotlin = 0
        var midWeb = 0
        var midKotlin = 0
        var sumDiff = 0L
        var maxDiff = 0
        for (i in produced!!.indices) {
            val a = produced[i] and 0xFF
            val b = web!![i] and 0xFF
            if (a < 200) inkKotlin++
            if (b < 200) inkWeb++
            if (a in 40..215) midKotlin++
            if (b in 40..215) midWeb++
            val d = abs(a - b)
            sumDiff += d
            if (d > maxDiff) maxDiff = d
        }
        val n = produced.size
        val meanDiff = sumDiff.toDouble() / n
        val inkDiffPct = 100.0 * abs(inkKotlin - inkWeb) / maxOf(1, inkWeb)
        val midDiffPct = 100.0 * abs(midKotlin - midWeb) / maxOf(1, midWeb)
        println(
            "[㉘-②] **生产链路**（ImageEditSession，Skia 抗锯齿打点）vs 网页 canvas2d（1000×700）\n" +
                "   墨迹像素（灰 <200）：网页 $inkWeb / Kotlin $inkKotlin  ⇒ 差 ${"%.2f".format(inkDiffPct)}%\n" +
                "   中间灰阶（40..215）：网页 $midWeb / Kotlin $midKotlin  ⇒ 差 ${"%.2f".format(midDiffPct)}%\n" +
                "   逐像素灰度差：平均 ${"%.3f".format(meanDiff)} / 最大 $maxDiff（0..255）",
        )

        assertTrue("必须真的画出墨迹（$inkKotlin 个像素）✗", inkKotlin > 0)
        assertTrue(
            "墨迹像素数差必须 ≤ 3%（实测 ${"%.2f".format(inkDiffPct)}%）✗",
            inkDiffPct <= 3.0,
        )
        assertTrue(
            "抗锯齿中间灰阶像素数差必须 ≤ 15%（实测 ${"%.2f".format(midDiffPct)}%）✗",
            midDiffPct <= 15.0,
        )
        assertTrue(
            "逐像素平均灰度差必须 ≤ 3（实测 ${"%.3f".format(meanDiff)}）✗",
            meanDiff <= 3.0,
        )
    }

    private fun overWhite(src: Int): Int {
        val a = ((src ushr 24) and 0xFF) / 255f
        if (a <= 0f) return 0xFFFFFFFF.toInt()
        fun ch(shift: Int): Int = ((((src ushr shift) and 0xFF) * a + 255f * (1f - a)) + 0.5f)
            .toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
