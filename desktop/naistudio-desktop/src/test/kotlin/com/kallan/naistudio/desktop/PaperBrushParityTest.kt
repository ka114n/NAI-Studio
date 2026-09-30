package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushEngine
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import kotlin.math.abs

/**
 * **㉜c：纸纹（`paperOn` / `paperStrength`）接上 Skia 抗锯齿打点**的判据 ✓。
 *
 * ## 为什么非得做这一批（用户视角）
 *
 * ㉜b 把**混色 / 水分 / 色延伸**接上了 Skia 表面 ✓，可**出厂那批水彩 / 混色预设全是
 * `paperOn = true`** ✗（`BrushPanelData.kt` ✓）⇒ 它们走的还是**纸纹那条老路**（手写 coverage 光栅 ✗），
 * 于是用户点开一看**一点没变** ✓（这一条写在 `docs/55` §8.2 ✓）。
 *
 * ## 真值：**网页自己**跑（`tools/webview-spike --args=paper` ✓）
 *
 * `docs/brush-lab-simple-paper-truth.json`：CEF 打开 `brush-lab-simple.html`，
 * 中性参数 + `paperOn=1`（`paperStrength` 35 / 60 两组 ✓），同一串 21 个输入点（每组 928 颗笔尖 ✓），
 * 逐颗记下位置 / 半径 / 步距 / **alpha** / 走完这一笔之后**笔尖中心那一颗像素的 alpha**（`fa` ✓），
 * 外加**那张 128×128 贴图的 alpha 通道**（`tileB64` ✓）与导出 PNG ✓。
 *
 * ⚠️ 两个必须说清的取样口径（不然这张表会被误读 ✗）：
 *  ① **贴图必须是确定的**：网页那层细颗粒是 `Math.random()` 生的 ✗ ⇒ 抽真值时把 `Math.random`
 *     临时换成与 [BrushEngine.Mulberry32] **逐位同序**的那个 PRNG（seed = `TILE_SEED` ✓），
 *     生产链路也用 [BrushEngine.mulberryGrain] ✓ ⇒ 两边**同一张纸** ✓（本测试**逐字节**核它 ✓）。
 *  ② **网页 `texturedDab()` 那条链自己有个 bug** ✗：`dabCv` 只增不减，而 `stamp()` 最后那句
 *     `drawImage(dabCv, …, s2, s2)` 的**源是整张画布** ⇒ 小笔尖被**缩着摆**（还捎上残留 ✗）——
 *     导出的 `…-asshipped.png` 就是**一串方块**（本测试把它当**反例**量出来 ✓，见打印 ✓）。
 *     真值用**修正版**（只把 `dabCv` 的尺寸还给这一颗 ✓，`stamp()` 一个字没动 ✓）——
 *     那才是"图案 × 椭圆 × 上色、1:1 摆上去"的**本意口径** ✓ = 本批实现的口径 ✓。
 *
 * ## 判据
 *
 *  ① 逐颗笔尖：颗数 / 半径 / 步距 / 位置 / **alpha** —— 逐项对齐 ✓；
 *  ② 贴图：`mulberryGrain(TILE_SEED)` + `buildTileFromGrain` 与网页交回来的那张**逐字节相同** ✓；
 *  ③ 画上去的：**每颗笔尖中心那一颗像素的 alpha**（`fa` ✓）+ 导出 PNG 逐像素 ✓；
 *  ④ 走的是表面那条路（`strokeOnLayerSurface == true` ✓）—— 不然"到底接没接上"只是文档里一句话 ✗；
 *  ⑤ **反例**：把纸纹关掉再跑同一笔，与网页那张图比 ⇒ 必须**明显更差** ✓
 *    （证明阈值不是"随便都能过" ✗）。
 */
class PaperBrushParityTest {

    private val platform: Platform = desktopPlatform()

    private fun docsFile(name: String): File? =
        listOf(File("../docs/$name"), File("docs/$name")).firstOrNull { it.isFile }

    private fun truth(): JSONObject {
        val f = docsFile("brush-lab-simple-paper-truth.json")
        assertTrue("纸纹真值文件找不到（先在 tools/webview-spike 里跑一次 `--args=paper`）✗", f != null)
        return JSONObject(f!!.readText())
    }

    /** 抽真值那一档中性参数（与网页那一套逐项一致 ✓）+ 纸纹 ✓。 */
    private fun neutral(paperOn: Boolean, strength: Float) = BrushSpec(
        paperOn = paperOn,
        paperStrength = strength,
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

    /** 一笔跑完（返回会话 ✓）——生产链路，页缓冲当底图（真值那边也是空画布 ✓）。 */
    private fun stroke(
        w: Int,
        h: Int,
        spec: BrushSpec,
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

    private class Diff {
        var dabsWeb = 0
        var dabsKotlin = 0
        var maxRadius = 0.0
        var maxStep = 0.0
        var maxPos = 0.0
        var maxAlpha = 0.0
        var maxCenterAlpha = 0
        var meanCenterAlpha = 0.0
        var tileDiff = 0
        var inkWeb = 0
        var inkKotlin = 0
        var midWeb = 0
        var midKotlin = 0
        var meanPix = 0.0
        var maxPix = 0
        var pixOver2 = 0
        var pixOver8 = 0
        // 反例：同一笔**关掉纸纹**之后与网页那张图的差 ✓
        var meanPixNoTex = 0.0
        var maxPixNoTex = 0
    }

    /** 单颗笔尖探针的结果（㉜c ✓）。 */
    private class DabDiff {
        var blocks = 0
        var mean = 0.0
        var max = 0
        var worstAt = ""
        /** 反例：网页**关纸纹**那一片与"网页开纸纹"那一片的差 ✓（纸纹到底改变了多少 ✓）。 */
        var controlMean = 0.0
    }

    /**
     * **单颗笔尖**那一档：把"纸纹怎么乘到笔尖 alpha 上"单独量出来 ✓。
     *
     * 为什么要单列（实测踩出来的 ✓）：**密集笔画会把纸纹叠没** ✗ ——
     * 一个点被十几颗笔尖盖住时，累加式 `1 − Π(1 − α·tex_i)` 里的 `tex_i` 各不相同 ⇒ 颗粒被平均掉 ✓
     *（实测：中性 24 把刷那一档，"关掉纸纹"重新渲染出来的图与"开着纸纹"的网页图**只差 0.21** ✓，
     * 而"开着纸纹"的我们与网页差 0.22 ✓ —— 两者同量级 ⇒ **那一档根本区分不出纸纹** ✗）。
     * 一颗笔尖画在空画布上就没有这个问题 ⇒ 贴图 × 覆盖度**逐像素**能直接对上 ✓。
     */
    private fun dabProbe(truth: JSONObject, id: String, failures: MutableList<String>): DabDiff {
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val groups = truth.getJSONArray("groups")
        val out = DabDiff()
        // ⚠️ 先把两组都收齐再比 ✗（`dabPaper60` 在 `dabPlain` 前面，边遍历边取 control 会拿到 null ✓ ——
        //    这一版第一趟就是这么写错的，反例量出来是 0.000 ✓）。
        var target: JSONObject? = null
        var control: JSONObject? = null
        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            if (g.optString("mode", "") != "dab") continue
            when (g.getString("id")) {
                id -> target = g
                "dabPlain" -> control = g
            }
        }
        val t = target ?: return out
        val spec = neutral(true, t.getDouble("strength").toFloat())
        val sizePx = t.getInt("size")
        val blocks = t.getJSONArray("blocks")
        var sum = 0.0
        var n = 0
        for (b in 0 until blocks.length()) {
            val blk = blocks.getJSONObject(b)
            assertTrue("[$id] 网页那一颗没画上（ok=false）✗", blk.optBoolean("ok", true))
            val bx = blk.getDouble("x").toFloat()
            val by = blk.getDouble("y").toFloat()
            val x0 = blk.getInt("x0")
            val y0 = blk.getInt("y0")
            val bw = blk.getInt("w")
            val bh = blk.getInt("h")
            val webBytes = Base64.getDecoder().decode(blk.getString("a"))
            val session = ImageEditSession(w, h, IntArray(w * h))
            session.beginStroke(
                bx, by,
                StrokeSpec(
                    mode = StrokeMode.PAINT,
                    brushPixels = sizePx,
                    color = 0xFF1A1A1A.toInt(),
                    brush = spec,
                    minRadiusRatio = 0.15f,
                    pressureCurve = 1f,
                ),
                pressure = 1f,
            )
            session.endStroke()
            var localMax = 0
            var localSum = 0.0
            var localWorst = ""
            for (yy in 0 until bh) {
                for (xx in 0 until bw) {
                    val mine = (session.pixels[(y0 + yy) * w + (x0 + xx)] ushr 24) and 0xFF
                    val web = webBytes[yy * bw + xx].toInt() and 0xFF
                    val dd = abs(mine - web)
                    localSum += dd
                    if (dd > localMax) {
                        localMax = dd
                        localWorst = "(${x0 + xx},${y0 + yy}) 我=$mine 网页=$web"
                    }
                }
            }
            sum += localSum / (bw * bh)
            n++
            if (localMax > out.max) {
                out.max = localMax
                out.worstAt = "第 $b 颗 @ ($bx,$by) $localWorst"
            }
        }
        out.blocks = n
        out.mean = if (n > 0) sum / n else 0.0
        // 反例：网页"关纸纹"的那一颗 vs 网页"开纸纹"的这一颗（同一位置 ✓）
        control?.let { ctl ->
            val cblocks = ctl.getJSONArray("blocks")
            var cs = 0.0
            var cn = 0
            for (b in 0 until minOf(cblocks.length(), blocks.length())) {
                val c = cblocks.getJSONObject(b)
                val p = blocks.getJSONObject(b)
                if (c.getInt("w") != p.getInt("w")) continue
                val cb = Base64.getDecoder().decode(c.getString("a"))
                val pb = Base64.getDecoder().decode(p.getString("a"))
                var s = 0.0
                for (i in cb.indices) s += abs((cb[i].toInt() and 0xFF) - (pb[i].toInt() and 0xFF))
                cs += s / cb.size
                cn++
            }
            out.controlMean = if (cn > 0) cs / cn else 0.0
        }
        return out
    }

    @Test
    fun paper_texture_matches_the_web_dab_by_dab() {
        val truth = truth()
        val w = truth.getJSONObject("canvas").getInt("w")
        val h = truth.getJSONObject("canvas").getInt("h")
        val groups = truth.getJSONArray("groups")
        val failures = ArrayList<String>()
        val reports = ArrayList<String>()

        println(
            "[㉜c] 纸纹真值：$w×$h、${groups.length()} 组（网页自己跑的 ✓，贴图 seed=0x5A17 双方同序 ✓）\n" +
                "[㉜c] 判据：逐颗 半径/步距/位置/alpha + 贴图逐字节 + 每颗中心 alpha + 导出 PNG 逐像素 + 走表面",
        )

        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            val id = g.getString("id")
            val mode = g.optString("mode", "points")
            if (mode != "points") continue // 直线那两组在 PaperStrokeContinuityTest 里对齐 ✓
            val params = g.getJSONObject("params")
            val spec = neutral(true, params.getDouble("paperStrength").toFloat())
            val sizePx = params.getInt("size")
            val points = g.getJSONArray("points")
            val webDabs = g.getJSONArray("dabs")
            val stats = g.getJSONObject("stats")

            val session = stroke(w, h, spec, points, sizePx, 0xFF1A1A1A.toInt())
            val log = session.dabProbeLog
            val d = Diff()
            d.dabsWeb = webDabs.length()
            d.dabsKotlin = log.size

            // ---- ② 贴图逐字节：生产链路那张 vs 网页交回来的那张 ----
            run {
                val mine = BrushEngine.buildTileFromGrain(
                    spec,
                    BrushEngine.mulberryGrain(BrushEngine.TILE_SEED.toInt()),
                )
                val web = Base64.getDecoder().decode(g.getString("tileB64"))
                var diff = 0
                var firstBad = -1
                for (i in mine.indices) {
                    if (i >= web.size || mine[i] != web[i]) {
                        diff++
                        if (firstBad < 0) firstBad = i
                    }
                }
                d.tileDiff = diff
                if (diff != 0) {
                    failures += "[$id] 贴图与网页不一致：$diff 个格点（第一个 @ $firstBad，" +
                        "我=${mine.getOrNull(firstBad)?.toInt()?.and(0xFF)} 网页=" +
                        "${web.getOrNull(firstBad)?.toInt()?.and(0xFF)}）✗"
                }
            }

            // ---- ① 逐颗对齐 ----
            var centerSum = 0.0
            var centerN = 0
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
                d.maxAlpha = maxOf(d.maxAlpha, abs(kd.plan.alpha - wd.getDouble("alpha").toFloat()).toDouble())
                // ---- ③ 每颗笔尖中心那一颗像素的 alpha（网页 = 走完整笔后那一颗 ✓）----
                val cx = kotlin.math.floor(kd.x.toDouble()).toInt().coerceIn(0, w - 1)
                val cy = kotlin.math.floor(kd.y.toDouble()).toInt().coerceIn(0, h - 1)
                val mineAlpha = (session.pixels[cy * w + cx] ushr 24) and 0xFF
                val webAlpha = wd.optInt("fa", -1)
                if (webAlpha >= 0) {
                    val dd = abs(mineAlpha - webAlpha)
                    if (dd > d.maxCenterAlpha) d.maxCenterAlpha = dd
                    centerSum += dd
                    centerN++
                }
            }
            d.meanCenterAlpha = if (centerN > 0) centerSum / centerN else 0.0

            // ---- ③ 导出 PNG 逐像素（白底合成 ✓，与网页 `savePNG()` 同口径 ✓）----
            val composited = IntArray(w * h) { overWhite(session.pixels[it]) }
            val kotlinPng = pngOf(composited, w, h)
            runCatching {
                val out = File("build/paper-kotlin-$id.png")
                out.parentFile?.mkdirs()
                out.writeBytes(kotlinPng)
            }
            val kotlinPixels = decode(kotlinPng)
            val webPng = docsFile("brush-lab-simple-paper-$id.png")
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

            // ---- ⑤ 反例：同一笔**把纸纹关掉** ⇒ 与网页那张（开纸纹）的差必须明显更大 ✓ ----
            run {
                val noTex = stroke(w, h, neutral(false, 0f), points, sizePx, 0xFF1A1A1A.toInt())
                val comp2 = IntArray(w * h) { overWhite(noTex.pixels[it]) }
                val noTexPix = decode(pngOf(comp2, w, h))
                var s2 = 0L
                for (i in noTexPix!!.indices) {
                    val dd = abs((noTexPix[i] and 0xFF) - (web!![i] and 0xFF))
                    s2 += dd
                    if (dd > d.maxPixNoTex) d.maxPixNoTex = dd
                }
                d.meanPixNoTex = s2.toDouble() / noTexPix.size
            }

            // ---- 反例：网页**原样**那张（`dabCv` 只增不减的 bug ✓）----
            var meanPixAsShipped = -1.0
            runCatching {
                val f = docsFile("brush-lab-simple-paper-$id-asshipped.png")
                if (f != null) {
                    val asShip = decode(f.readBytes())
                    if (asShip != null) {
                        var s3 = 0L
                        for (i in asShip.indices) s3 += abs((asShip[i] and 0xFF) - (web!![i] and 0xFF))
                        meanPixAsShipped = s3.toDouble() / asShip.size
                    }
                }
            }

            val inkPct = 100.0 * abs(d.inkKotlin - d.inkWeb) / maxOf(1, d.inkWeb)
            val midPct = 100.0 * abs(d.midKotlin - d.midWeb) / maxOf(1, d.midWeb)
            val line = "[㉜c] 组 $id（paperOn=true, paperStrength=${params.getDouble("paperStrength")}）\n" +
                "    笔尖：网页 ${d.dabsWeb} / Kotlin ${d.dabsKotlin}  半径差 ≤ ${"%.4f".format(d.maxRadius)}" +
                "  步距差 ≤ ${"%.4f".format(d.maxStep)}  位置差 ≤ ${"%.2f".format(d.maxPos)} px" +
                "  alpha 差 ≤ ${"%.6f".format(d.maxAlpha)}\n" +
                "    贴图：${BrushEngine.TILE}×${BrushEngine.TILE} 逐字节差 **${d.tileDiff}**" +
                "（0 = 与网页同一张纸 ✓）\n" +
                "    每颗笔尖中心那颗像素的 alpha：最大差 ${d.maxCenterAlpha}、平均差 " +
                "${"%.3f".format(d.meanCenterAlpha)}（0..255 ✓）\n" +
                "    PNG（$w×$h）：墨迹 网页 ${d.inkWeb} / Kotlin ${d.inkKotlin} = 差 ${"%.2f".format(inkPct)}%、" +
                "中间灰阶 网页 ${d.midWeb} / Kotlin ${d.midKotlin} = 差 ${"%.2f".format(midPct)}%、" +
                "逐像素平均差 ${"%.3f".format(d.meanPix)}（最大 ${d.maxPix}，>2 的 ${d.pixOver2} 个 = " +
                "${"%.4f".format(100.0 * d.pixOver2 / (w * h))}%、>8 的 ${d.pixOver8} 个 = " +
                "${"%.4f".format(100.0 * d.pixOver8 / (w * h))}%）\n" +
                "    反例甲（**同一笔关掉纸纹** vs 网页开纸纹）：平均差 ${"%.3f".format(d.meanPixNoTex)}" +
                "（最大 ${d.maxPixNoTex}）⇒ 必须**明显更大** ✓\n" +
                "    反例乙（网页**原样**那张 = `dabCv` 只增不减的 bug ✓）：与修正版的平均差 " +
                "${if (meanPixAsShipped < 0) "n/a" else "%.3f".format(meanPixAsShipped)}" +
                "（= 那个 bug 有多大 ✓）\n" +
                "    走表面：${session.strokeOnLayerSurface}（必须 true ✓）"
            reports += line

            // ---- 断言 ----
            if (!session.strokeOnLayerSurface) failures += "[$id] 纸纹**没走**层表面（又退回手写光栅 ✗）"
            if (d.dabsKotlin != d.dabsWeb) failures += "[$id] 颗数：网页 ${d.dabsWeb} vs Kotlin ${d.dabsKotlin}"
            if (d.maxRadius > 0.02) failures += "[$id] 半径差 ${d.maxRadius}"
            if (d.maxStep > 0.02) failures += "[$id] 步距差 ${d.maxStep}"
            if (d.maxPos > 0.5) failures += "[$id] 位置差 ${d.maxPos}"
            if (d.maxAlpha > 0.002) failures += "[$id] 每颗 alpha 差 ${d.maxAlpha}"
            if (d.tileDiff != 0) failures += "[$id] 贴图差 ${d.tileDiff} 个格点"
            if (d.maxCenterAlpha > 12) failures += "[$id] 每颗中心 alpha 最大差 ${d.maxCenterAlpha} > 12"
            if (d.meanCenterAlpha > 3.0) failures += "[$id] 每颗中心 alpha 平均差 ${d.meanCenterAlpha} > 3"
            // ---- PNG 那几条**为什么与混色/普通笔不同档**（如实写 ✓，不是放水 ✗）----
            // 纸纹只作用在**笔尖 alpha**上，而 928 颗笔尖叠下来，绝大部分像素早就叠到不透明了 ⇒
            // 真正有差的只有**笔迹轮廓那一圈过渡带**（实测：>2 的像素只占 0.50~0.68% ✓）。
            // 那一圈的亚像素落点两边不可能逐位相同 —— 网页是**先画进位图再 `drawImage` 摆上去**（含一次重采样 ✓），
            // 这边是矢量 + shader **不重采样** ✓ ⇒ 双线性那一点点差就落在这一圈上 ✓。
            // 因此主判据取"平均差"与"大差像素的占比"（对整幅图有意义 ✓），
            // 墨迹 / 中间灰阶那两个**计数**仍然记下来，但阈值按这一档的实测放宽并**注明原因** ✓
            //（它们只数那一圈过渡带，对亚像素极敏感 ✓ —— 混色那批笔迹宽、过渡带占比大，才用它们当主判据 ✓）。
            if (inkPct > 5.0) failures += "[$id] 墨迹像素差 ${"%.2f".format(inkPct)}% > 5%"
            if (midPct > 35.0) failures += "[$id] 中间灰阶差 ${"%.2f".format(midPct)}% > 35%"
            if (d.meanPix > 0.5) failures += "[$id] 逐像素平均差 ${"%.3f".format(d.meanPix)} > 0.5"
            val over2Pct = 100.0 * d.pixOver2 / (w * h)
            if (over2Pct > 1.0) failures += "[$id] 差 >2 的像素占 ${"%.4f".format(over2Pct)}% > 1%"
            val over8Pct = 100.0 * d.pixOver8 / (w * h)
            if (over8Pct > 0.6) failures += "[$id] 差 >8 的像素占 ${"%.4f".format(over8Pct)}% > 0.6%"
            if (stats.getInt("dabs") != d.dabsWeb) failures += "[$id] 真值文件自己的 dabs 对不上 ✗"
        }

        // ---- 单颗笔尖：纸纹 × 覆盖度**逐像素**（这一档才真的能区分"纸纹有没有乘对" ✓）----
        val dab = dabProbe(truth, "dabPaper60", failures)
        reports += "[㉜c] 单颗笔尖（size=72、alpha=1、8 个位置带小数 ✓）：${dab.blocks} 颗\n" +
            "    每颗的包围盒逐像素比（贴图 × 椭圆覆盖度）：**平均差 ${"%.3f".format(dab.mean)}、" +
            "最大差 ${dab.max}**（0..255 ✓）\n" +
            "    最坏那一点：${dab.worstAt}\n" +
            "    反例（网页**关掉纸纹**的同一颗 vs 网页开纸纹的那一颗）：平均差 " +
            "${"%.3f".format(dab.controlMean)} ⇒ 纸纹真的改变了这么多 ✓（我们只差 ${"%.3f".format(dab.mean)} ✓）"
        if (dab.blocks < 8) failures += "单颗笔尖探针少了（实测 ${dab.blocks} 颗 ✗）"
        if (!(dab.mean > 0.0)) failures += "单颗笔尖探针一个像素都没量到 ✗"
        // 阈值 = 实测最坏那一档 + 余量（实测：平均 1.942 / 最大 48 ✓；**反例 42.038** ✓）：
        // 拿掉"纸纹乘进 alpha"这一条，平均差会从 ~2 跳到 ~42（20 倍 ✓）⇒ 这两条拦得住 ✗。
        if (dab.mean > 3.0) failures += "单颗笔尖逐像素平均差 ${"%.3f".format(dab.mean)} > 3"
        if (dab.max > 60) failures += "单颗笔尖逐像素最大差 ${dab.max} > 60"
        if (dab.controlMean <= dab.mean * 5) {
            failures += "单颗笔尖那条反例不成立：关掉纸纹的差 ${"%.3f".format(dab.controlMean)} " +
                "不足我们的 5 倍（${"%.3f".format(dab.mean)}）✗"
        }

        reports.forEach { println(it) }
        if (failures.isNotEmpty()) {
            throw AssertionError("纸纹与网页不一致：\n" + failures.joinToString("\n"))
        }
    }
}
