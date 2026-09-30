package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushShape
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * **㉝②：选区裁剪（`Canvas.clipRect` / `clipPath`）接上 Skia** 的判据 ✓。
 *
 * ## 现状（改之前 ✗）
 *
 * `ImageEditSession.beginStroke` 那条门槛原来是「没纸纹 + **没选区** + 圆笔尖」✓ ——
 * 有选区时就退回**手写 coverage 光栅** ✗（`ImageEditOps.stampDab` 里逐像素查那张 `ByteArray` 掩码 ✓）。
 * 于是"有选区"这件事本身就把笔尖换了个光栅器 ✓（同一个用户操作，画质却不一样 ✗）。
 *
 * 改法：`SkiaDabRaster.stampOnSurface` 多 `clipRect` / `clipPath` ✓ ——
 * 画之前 `canvas.save() + clipRect/clipPath(INTERSECT, **isAntiAlias = false**)`、
 * 画完 `restore()` ✓（新口径见 `ImageEditSession.selectionClipGeometry` ✓）。
 * ⚠️ **非抗锯齿**是故意的 ✓：老掩码是逐像素 0/1 ✓，Skia 的非抗锯齿裁剪也按**像素中心**取样 ✓
 * ⇒ 两条路裁出来的**像素集合一致** ✓（本测试逐像素核 ✓）。
 *
 * ## 真值：**网页自己**跑（`tools/webview-spike --args=sel` ✓）
 *
 * `docs/brush-lab-simple-sel-truth.json`：CEF 打开 `brush-lab-simple.html`，
 * 画之前 `ctx.save() → beginPath → rect / 多边形 → clip()`、画完 `restore()` ✓ ——
 * 就是 canvas2d 的裁剪原语 ✓，与这边的 `clipRect/clipPath` 同一档 ✓。
 * 选区数字**逐项相同**：矩形 = `rect(200,100,500,400)` ✓；多边形 = 那个 L 形（**含凹角** ✓，
 * 顺带把"even-odd 还是 winding"那条口径也钉住 ✓）。
 *
 * ## 判据
 *
 *  ① **矩形选区解析出来的像素跨度**必须正好是 `[200,700) × [100,500)` ✓
 *    （= 网页那个 `rect(200,100,500,400)` ✓ —— `SelectionGeometry.bounds` 的 `right-left+1` 口径 ✓）；
 *  ② 逐颗笔尖：颗数 / 半径 / 步距 / 位置 / alpha —— 逐项对齐 ✓；
 *  ③ 导出 PNG 逐像素 ✓；
 *  ④ 走的是**表面那条路** ✓；
 *  ⑤ **反例（有牙 ✓）**：同一笔**不设选区**再跑一遍 ⇒
 *    ① 它**必须有墨落在选区外** ✓；② 与本组那张"网页带选区"的图**平均差必须大得多** ✓
 *    （证明选区真的在起作用，而不是"恰好都在里面" ✗）。
 */
class SelectionClipParityTest {

    private val platform: Platform = desktopPlatform()

    private fun docsFile(name: String): File? =
        listOf(File("../docs/$name"), File("docs/$name")).firstOrNull { it.isFile }

    private fun truth(): JSONObject {
        val f = docsFile("brush-lab-simple-sel-truth.json")
        assertTrue(
            "选区 / 方笔尖真值文件找不到（先在 tools/webview-spike 里跑一次 `--args=sel`）✗",
            f != null,
        )
        return JSONObject(f!!.readText())
    }

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
        return (r * 299 + g * 587 + b * 1024 / 1000) / 1000
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

    /** 那一组该用哪个选区（**数字直接来自真值** ✓ —— 两边同一串 ✓）。 */
    private fun clipShapeOf(truth: JSONObject, kind: String): SelectionShape {
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        return when (kind) {
            "rect" -> {
                val r = truth.getJSONArray("clipRect")
                // ⚠️ `SelectionGeometry.bounds` 的口径是 `right - left + 1` ✓（老掩码就是这么填的 ✓）
                // ⇒ 想要 [200,700) 就要 left=200 / right=699 ✓（**这里断言它** ✓，见判据①）
                SelectionShape.Rect(
                    r.getInt(0).toFloat() / w,
                    r.getInt(1).toFloat() / h,
                    (r.getInt(0) + r.getInt(2) - 1).toFloat() / w,
                    (r.getInt(1) + r.getInt(3) - 1).toFloat() / h,
                )
            }

            "poly" -> {
                val pts = truth.getJSONArray("clipPoly")
                val list = ArrayList<MaskPoint>(pts.length())
                for (i in 0 until pts.length()) {
                    val p = pts.getJSONArray(i)
                    list += MaskPoint(p.getInt(0).toFloat() / w, p.getInt(1).toFloat() / h)
                }
                SelectionShape.Polygon(list)
            }

            else -> SelectionShape.Rect(0f, 0f, 0f, 0f)
        }
    }

    private fun stroke(
        w: Int,
        h: Int,
        spec: BrushSpec,
        square: Boolean,
        points: org.json.JSONArray,
        sizePx: Int,
        color: Int,
        clip: SelectionShape?,
    ): ImageEditSession {
        val session = ImageEditSession(w, h, IntArray(w * h))
        session.dabProbeEnabled = true
        session.setSelectionClip(clip)
        session.beginStroke(
            points.getJSONObject(0).getDouble("x").toFloat(),
            points.getJSONObject(0).getDouble("y").toFloat(),
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = sizePx,
                color = color,
                shape = if (square) BrushShape.SQUARE else BrushShape.ROUND,
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
    fun selection_clip_matches_the_web_dab_by_dab() {
        val truth = truth()
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val groups = truth.getJSONArray("groups")
        val failures = ArrayList<String>()

        // ---- 判据①：矩形选区解析出来的像素跨度 ✓ ----
        val rectShape = clipShapeOf(truth, "rect")
        val rectPixels = SelectionGeometry.bounds(rectShape, w, h)
        val r = truth.getJSONArray("clipRect")
        val want = EditRect(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3))
        println(
            "[㉝②-选区] 矩形选区解析：网页 rect(${want.x},${want.y},${want.w},${want.h})" +
                " ⇒ Kotlin 跨度 $rectPixels（必须逐项相同 ✓）",
        )
        if (rectPixels != want) failures += "判据①：矩形跨度 $rectPixels != 网页 $want"

        println(
            "[㉝②-选区] 真值：$w×$h（网页自己跑的 ✓，`ctx.clip()` 那条原语 ✓）；" +
                "判据：跨度 + 逐颗 + PNG + 走表面 + 反例（不设选区必须更差 ✓）",
        )

        var tested = 0
        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            val params = g.getJSONObject("params")
            val kind = params.optString("clip", "none")
            if (kind == "none") continue
            val id = g.getString("id")
            val square = params.optBoolean("square", false)
            val wxh = params.getDouble("wxh").toFloat()
            val sizePx = params.getInt("size")
            val points = g.getJSONArray("points")
            val spec = neutral(wxh)
            val clip = clipShapeOf(truth, kind)
            tested++

            val session = stroke(w, h, spec, square, points, sizePx, 0xFF1A1A1A.toInt(), clip)

            // ---- ② 逐颗笔尖 ----
            val dabs = g.getJSONArray("dabs")
            val mine = session.dabProbeLog
            var maxR = 0.0
            var maxStep = 0.0
            var maxPos = 0.0
            var maxAlpha = 0.0
            for (i in 0 until minOf(dabs.length(), mine.size)) {
                val d = dabs.getJSONObject(i)
                val m = mine[i]
                maxR = maxOf(maxR, abs(d.getDouble("r") - m.radius))
                maxStep = maxOf(maxStep, abs(d.getDouble("step") - m.stepPixels))
                maxPos = maxOf(maxPos, abs(d.getDouble("x") - m.x), abs(d.getDouble("y") - m.y))
                maxAlpha = maxOf(maxAlpha, abs(d.getDouble("alpha") - m.plan.alpha))
            }

            // ---- ③ 导出 PNG 逐像素 ----
            val truthPng = docsFile("brush-lab-simple-sel-$id.png")
            assertTrue("真值 PNG 找不到：brush-lab-simple-sel-$id.png ✗", truthPng != null)
            val web = decode(truthPng!!.readBytes())
            assertTrue("[$id] 真值 PNG 解不开 ✗", web != null)
            val webArr = web!!
            val minePx = IntArray(w * h) { overWhite(session.pixels[it]) }
            val mineDecoded = decode(pngOf(minePx, w, h))
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
                val gw = gray(webArr[i])
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

            // ---- ⑤ 反例：**不设选区**再跑一遍 ✓ ----
            val noClip = stroke(w, h, spec, square, points, sizePx, 0xFF1A1A1A.toInt(), null)
            var outsideInk = 0
            var noClipSum = 0.0
            for (yy in 0 until h) {
                for (xx in 0 until w) {
                    val i = yy * w + xx
                    noClipSum += abs(gray(webArr[i]) - gray(overWhite(noClip.pixels[i])))
                    val alpha = (noClip.pixels[i] ushr 24) and 0xFF
                    if (alpha == 0) continue
                    val inside = SelectionGeometry.contains(
                        clip,
                        (xx + 0.5f) / w,
                        (yy + 0.5f) / h,
                    )
                    if (!inside) outsideInk++
                }
            }
            val noClipMean = noClipSum / (w * h)

            println(
                "[㉝②-选区] $id（clip=$kind、方笔=$square、WxHRatio=$wxh）：\n" +
                    "    颗数：网页 ${dabs.length()} / Kotlin ${mine.size}；" +
                    "半径差 ${"%.4f".format(maxR)}、步距差 ${"%.4f".format(maxStep)}、" +
                    "位置差 ${"%.3f".format(maxPos)} px、alpha 差 ${"%.6f".format(maxAlpha)}\n" +
                    "    PNG：墨迹 网页 $inkWeb / Kotlin $inkMine ⇒ 差 ${"%.2f".format(inkDiff)}%；" +
                    "中间灰阶 网页 $midWeb / Kotlin $midMine ⇒ 差 ${"%.2f".format(midDiff)}%\n" +
                    "    逐像素灰度差：平均 ${"%.3f".format(mean)} / 最大 $maxPix、" +
                    ">2 占 ${"%.4f".format(over2 * 100.0 / (w * h))}%、" +
                    ">8 占 ${"%.4f".format(over8 * 100.0 / (w * h))}%\n" +
                    "    走表面=${session.strokeOnLayerSurface} ✓；" +
                    "**反例（不设选区）**：选区外的墨 $outsideInk 个像素 ✓、" +
                    "与网页这张图的平均差 ${"%.3f".format(noClipMean)}" +
                    "（= 带选区那档的 ${"%.1f".format(noClipMean / maxOf(mean, 1e-6))} 倍 ✓）",
            )

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
            if (outsideInk == 0) failures += "[$id] 反例无效：不设选区居然也没有墨落在选区外 ✗"
            if (noClipMean < maxOf(mean, 0.02) * 3.0) {
                failures += "[$id] 反例没牙：不设选区只差 ${noClipMean}（带选区那档 $mean）✗"
            }
        }

        assertTrue("真值里一组选区都没有 ✗（先跑 `--args=sel` ✓）", tested > 0)
        if (failures.isNotEmpty()) {
            throw AssertionError("选区裁剪与网页对不上 ✗：\n" + failures.joinToString("\n"))
        }
    }
}
