package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.DesktopImageIo
import com.kallan.naistudio.desktop.platform.FileKeyValueStore
import com.kallan.naistudio.desktop.platform.FileSecretStore
import com.kallan.naistudio.models.GenerateExtras
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.FocusedInpaint
import com.kallan.naistudio.models.MaskExpand
import com.kallan.naistudio.models.PixelMask
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.platform.getToken
import com.kallan.naistudio.services.MaskCodec
import com.kallan.naistudio.services.NaiApi
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **真实重绘实验：把一张图挖一片透明通道，再发一次局部重绘**（用户 2026-09-16 授权花一次点数）。
 *
 * 想回答的问题：**底图上"透明"的那一块，发重绘时会不会被重绘出来**（也就是
 * "透明通道是否等价于遮罩"）。
 *
 * ## 为什么做成"要显式打开的测试"而不是日常用例
 *
 * 它会**真的发一次付费请求**（infill），所以默认**永远不跑**：
 * 只有同时给了 `NAI_LIVE_INPAINT=1` 和 `NAI_LIVE_PROFILE=<真实 %APPDATA%\NAI Studio 路径>`
 * 才会执行（Gradle 的 test 任务会把 APPDATA 指到临时目录，所以这里必须显式传真实 profile）。
 *
 * ## 走的是 App 自己的那条链路
 *
 * 位图准备、遮罩膨胀 + 吸附 8px 潜空间网格、请求组装全部复用线上代码
 * （`MaskCodec` / `MaskExpand` / `NaiApi.inpaint`）—— 这样结论才对得上 App 里的行为。
 *
 * 产物写在 `docs/shots/inpaint-alpha-live/`：01 带透明洞的底图、02 发出去的遮罩、03 服务端返回。
 */
class LiveInpaintAlphaTest {

    private fun profileDir(): File? =
        System.getenv("NAI_LIVE_PROFILE")?.takeIf { it.isNotBlank() }?.let(::File)

    /**
     * **给某个隔离 profile 塞一个假 token**（不联网、不花点数）。
     *
     * 用途：把"假 token"当**免费的请求探针** —— 点「重做」时能过 token 闸门、
     * 走到 `Inpaint start: …` 那行日志，然后请求在 401 处失败。
     * 于是"到底走了聚焦那条路还是整图那条路"就能从 `app.log` 里读出来，不用真出图。
     *
     * 用法：`NAI_SEED_PROFILE=<隔离 profile 目录>`（目录里要先有 prefs.json + images）。
     */
    @Test
    fun seed_fake_token_for_ui_probe() {
        val dir = System.getenv("NAI_SEED_PROFILE")?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue("opt-in: 需要 NAI_SEED_PROFILE", dir != null)
        val store = FileSecretStore(File(dir, "secrets.bin"))
        store.putSecret("nai_token", "fake-token-for-ui-probe")
        println("[seed] 假 token 已写入 $dir")
    }

    @Test
    fun cut_a_transparent_hole_and_inpaint() {
        assumeTrue(
            "opt-in only: 需要 NAI_LIVE_INPAINT=1 与 NAI_LIVE_PROFILE（会花一次点数）",
            System.getenv("NAI_LIVE_INPAINT") == "1" && profileDir() != null,
        )
        val dir = profileDir()!!
        val kv = FileKeyValueStore(File(dir, "prefs.json"))
        val secrets = FileSecretStore(File(dir, "secrets.bin"))
        val token = secrets.getToken()
        assertTrue("profile 里没有 token（$dir）", token.isNotBlank())

        val settings = runCatching { JSONObject(kv.getString("app_settings", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val inpaintModel = settings.optString("inpaintModel", "nai-diffusion-5-full")
        val strength = settings.optDouble("inpaintStrength", 1.0)
        val noise = settings.optDouble("inpaintNoise", 0.0)
        val expand = settings.optInt("inpaintMaskExpand", 8)
        val feather = settings.optInt("inpaintMaskFeather", 20)
        val edgeProtection = settings.optBoolean("inpaintEdgeProtection", true)
        val imageBaseUrl = settings.optString("imageBaseUrl", "https://image.novelai.net")
        val modelMode = settings.optString("modelMode", "anime")

        // 拿历史里最近一张**还在盘上**的图（这样实验用的就是用户自己的图）
        val history = runCatching {
            JSONArray(kv.getString("history_index_v2", "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        var source: JSONObject? = null
        for (i in history.length() - 1 downTo 0) {
            val item = history.optJSONObject(i) ?: continue
            val path = item.optString("filePath", "")
            if (path.isNotBlank() && File(path).isFile) {
                source = item
                break
            }
        }
        assertTrue("历史里找不到可用的图", source != null)
        val sourcePath = source!!.optString("filePath")
        val sourceParams = source.optJSONObject("params") ?: JSONObject()
        val prompt = sourceParams.optString("positivePrompt", "").ifBlank {
            source.optString("prompt", "1girl").ifBlank { "1girl" }
        }
        val negative = sourceParams.optString("negativePrompt", "")

        // ---- 请求尺寸（和 App 一样按源图算）----
        val dims = DesktopImageIo.size(sourcePath)!!
        val (width, height) = MaskCodec.requestSize(dims.first, dims.second)
        println("[live] 源图 ${dims.first}x${dims.second} → 请求尺寸 ${width}x$height")

        val decoded = DesktopImageIo.decodeFull(sourcePath)!!
        val scaled = DesktopImageIo.scale(decoded, width, height, smooth = true)
        if (scaled !== decoded) decoded.recycle()
        val pixels = DesktopImageIo.pixelsOf(scaled)
        scaled.recycle()

        // ---- 挖一个透明洞：中间 22% 见方，alpha 直接归 0 ----
        val holeWidth = (width * 0.22f).toInt()
        val holeHeight = (height * 0.22f).toInt()
        val holeX = (width - holeWidth) / 2
        val holeY = (height - holeHeight) / 2
        var holePixels = 0
        for (y in holeY until (holeY + holeHeight)) {
            val row = y * width
            for (x in holeX until (holeX + holeWidth)) {
                pixels[row + x] = 0
                holePixels++
            }
        }
        val baseWithHole = DesktopImageIo.fromArgb(pixels, width, height)
        val basePng = DesktopImageIo.pngBytes(baseWithHole)
        baseWithHole.recycle()

        // ---- 遮罩：和洞一模一样的位置（这才是 App 会发出去的东西）----
        val maskBytes = ByteArray(width * height)
        for (y in holeY until (holeY + holeHeight)) {
            val row = y * width
            for (x in holeX until (holeX + holeWidth)) maskBytes[row + x] = 255.toByte()
        }
        val expanded = MaskExpand.dilate(PixelMask(width, height, maskBytes), expand)
        val requestMask = MaskCodec.inpaintMask(expanded, width, height)!!
        val maskPng = MaskCodec.maskPng(DesktopImageIo, requestMask, width, height)

        val outDir = File(File(System.getProperty("java.io.tmpdir"), "naistudio/docs/shots/inpaint-alpha-live").absolutePath).apply { mkdirs() }
        File(outDir, "01-base-with-transparent-hole.png").writeBytes(basePng)
        File(outDir, "02-mask.png").writeBytes(maskPng)
        println("[live] 透明洞 ${holeWidth}x$holeHeight @($holeX,$holeY) = $holePixels 像素")

        // ---- 发请求（App 原样的 infill）----
        val params = GenerateParams(
            model = inpaintModel,
            positivePrompt = prompt,
            negativePrompt = negative,
            width = width,
            height = height,
        )
        val (images, usedModel) = runBlocking {
            NaiApi().inpaint(
                token = token,
                params = params,
                seed = 20260916L,
                baseImagePng = basePng,
                maskPng = maskPng,
                strength = strength,
                noise = noise,
                imageBaseUrl = imageBaseUrl,
                extras = GenerateExtras(),
                modelMode = modelMode,
            )
        }
        assertTrue("服务端没返回图", images.isNotEmpty())
        File(outDir, "03-result.png").writeBytes(images.first())
        println(
            "[live] 完成：模型=$usedModel 返回 ${images.size} 张，已写到 ${outDir.absolutePath}" +
                "（feather=$feather edgeProtection=$edgeProtection strength=$strength noise=$noise）",
        )
    }

    /**
     * **第二次实验（用户单独授权）：只给透明、不给遮罩** —— 走 img2img，看透明区会不会被补上。
     *
     * 和上面那条的区别只有一个：请求是 `action = "img2img"`（**没有 `parameters.mask`**）。
     * 它回答的是"透明通道本身算不算遮罩"这个问题：
     *  · 若结果里那块**变成不透明的画面** → 服务端会重绘透明区（等价于隐式遮罩）；
     *  · 若结果里那块**还是 alpha=0 / 变黑** → 透明不会被自动当成"要重绘这里"。
     *
     * 为了能读出信号，这里除了存图，还会**逐像素统计**洞内外的 alpha/亮度：
     * 洞外变化大不大（说明 strength 生效）、洞内有没有被补上。
     */
    @Test
    fun transparency_only_no_mask_img2img() {
        assumeTrue(
            "opt-in only: 需要 NAI_LIVE_TRANSPARENT_ONLY=1 与 NAI_LIVE_PROFILE（会花一次点数）",
            System.getenv("NAI_LIVE_TRANSPARENT_ONLY") == "1" && profileDir() != null,
        )
        val dir = profileDir()!!
        val kv = FileKeyValueStore(File(dir, "prefs.json"))
        val secrets = FileSecretStore(File(dir, "secrets.bin"))
        val token = secrets.getToken()
        assertTrue("profile 里没有 token（$dir）", token.isNotBlank())

        val settings = runCatching { JSONObject(kv.getString("app_settings", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val strength = settings.optDouble("i2iStrength", 0.7)
        val noise = settings.optDouble("i2iNoise", 0.0)
        val imageBaseUrl = settings.optString("imageBaseUrl", "https://image.novelai.net")
        val modelMode = settings.optString("modelMode", "anime")

        val history = runCatching { JSONArray(kv.getString("history_index_v2", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        var source: JSONObject? = null
        for (i in history.length() - 1 downTo 0) {
            val item = history.optJSONObject(i) ?: continue
            val path = item.optString("filePath", "")
            if (path.isNotBlank() && File(path).isFile) {
                source = item
                break
            }
        }
        assertTrue("历史里找不到可用的图", source != null)
        val sourcePath = source!!.optString("filePath")
        val sourceParams = source.optJSONObject("params") ?: JSONObject()
        val prompt = sourceParams.optString("positivePrompt", "").ifBlank {
            source.optString("prompt", "1girl").ifBlank { "1girl" }
        }
        val negative = sourceParams.optString("negativePrompt", "")

        val dims = DesktopImageIo.size(sourcePath)!!
        val (width, height) = MaskCodec.requestSize(dims.first, dims.second)
        val decoded = DesktopImageIo.decodeFull(sourcePath)!!
        val scaled = DesktopImageIo.scale(decoded, width, height, smooth = true)
        if (scaled !== decoded) decoded.recycle()
        val pixels = DesktopImageIo.pixelsOf(scaled)
        scaled.recycle()

        // 和上一次实验**同一块洞**，两次结果可以直接对照
        val holeWidth = (width * 0.22f).toInt()
        val holeHeight = (height * 0.22f).toInt()
        val holeX = (width - holeWidth) / 2
        val holeY = (height - holeHeight) / 2
        for (y in holeY until (holeY + holeHeight)) {
            val row = y * width
            for (x in holeX until (holeX + holeWidth)) pixels[row + x] = 0
        }
        val baseWithHole = DesktopImageIo.fromArgb(pixels, width, height)
        val basePng = DesktopImageIo.pngBytes(baseWithHole)
        baseWithHole.recycle()

        val outDir = File(File(System.getProperty("java.io.tmpdir"), "naistudio/docs/shots/inpaint-alpha-live").absolutePath).apply { mkdirs() }
        File(outDir, "04-transparent-only-base.png").writeBytes(basePng)
        println("[live2] 只给透明、不给遮罩：img2img strength=$strength noise=$noise 尺寸 ${width}x$height")

        val params = GenerateParams(
            model = settings.optString("model", "nai-diffusion-5-full"),
            positivePrompt = prompt,
            negativePrompt = negative,
            width = width,
            height = height,
        )
        val images = runBlocking {
            NaiApi().img2img(
                token = token,
                params = params,
                seed = 20260916L,
                baseImagePng = basePng,
                strength = strength,
                noise = noise,
                imageBaseUrl = imageBaseUrl,
                extras = GenerateExtras(),
                modelMode = modelMode,
            )
        }
        assertTrue("服务端没返回图", images.isNotEmpty())
        File(outDir, "05-transparent-only-result.png").writeBytes(images.first())

        // ---- 逐像素统计：洞内到底补上了没有 ----
        val resultImage = DesktopImageIo.decodeBytes(images.first())!!
        val resultPixels = DesktopImageIo.pixelsOf(resultImage)
        resultImage.recycle()
        var holeTransparent = 0
        var holeOpaque = 0
        var outsideChanged = 0
        var outsideTotal = 0
        for (y in 0 until height) {
            val row = y * width
            val inHoleRow = y in holeY until (holeY + holeHeight)
            for (x in 0 until width) {
                val inHole = inHoleRow && x in holeX until (holeX + holeWidth)
                if (inHole) {
                    val alpha = (resultPixels[row + x] ushr 24) and 0xFF
                    if (alpha == 0) holeTransparent++ else holeOpaque++
                } else {
                    outsideTotal++
                    if (resultPixels[row + x] != pixels[row + x]) outsideChanged++
                }
            }
        }
        println(
            "[live2] 洞内：不透明 $holeOpaque 像素 / 透明 $holeTransparent 像素；" +
                "洞外改动 ${"%.1f".format(outsideChanged * 100.0 / outsideTotal.coerceAtLeast(1))}%",
        )
        println("[live2] 产物：${outDir.absolutePath}")
    }

    /**
     * **真实聚焦重绘（用户 2026-09-16 授权，花一次点数）**：验证"只重绘框内"。
     *
     * 走的是 App 里那条路（`FocusedInpaint.plan` → 裁剪 → 放大 → 蒙版搬进请求 → `NaiApi.inpaint`
     * → 只把框贴回），产物四张：原图 / 发出去的蒙版 / 服务端返回的裁剪图 / 贴回后的整图。
     *
     * **判分方式**：贴回后的整图与"把原图当底图"逐像素比 ——
     *  · 框外改动必须是 **0 像素**（这就是"只重绘框内"的定义）
     *  · 框内应当大面积变化
     */
    @Test
    fun focused_inpaint_live() {
        assumeTrue(
            "opt-in only: 需要 NAI_LIVE_FOCUS=1 与 NAI_LIVE_PROFILE（会花一次点数）",
            System.getenv("NAI_LIVE_FOCUS") == "1" && profileDir() != null,
        )
        val dir = profileDir()!!
        val kv = FileKeyValueStore(File(dir, "prefs.json"))
        val secrets = FileSecretStore(File(dir, "secrets.bin"))
        val token = secrets.getToken()
        assertTrue("profile 里没有 token（$dir）", token.isNotBlank())

        val settings = runCatching { JSONObject(kv.getString("app_settings", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val inpaintModel = settings.optString("inpaintModel", "nai-diffusion-5-full-inpainting")
        val strength = settings.optDouble("inpaintStrength", 1.0)
        val noise = settings.optDouble("inpaintNoise", 0.0)
        val expand = settings.optInt("inpaintMaskExpand", 8)
        val context = settings.optInt("inpaintFocusContext", 96)
        val imageBaseUrl = settings.optString("imageBaseUrl", "https://image.novelai.net")
        val modelMode = settings.optString("modelMode", "anime")

        val history = runCatching { JSONArray(kv.getString("history_index_v2", "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        var source: JSONObject? = null
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val path = item.optString("filePath", "")
            if (path.isNotBlank() && File(path).isFile) {
                source = item
                break
            }
        }
        assertTrue("历史里找不到可用的图", source != null)
        val sourcePath = source!!.optString("filePath")
        val sourceParams = source.optJSONObject("params") ?: JSONObject()
        val prompt = sourceParams.optString("positivePrompt", "").ifBlank {
            source.optString("prompt", "1girl").ifBlank { "1girl" }
        }
        val negative = sourceParams.optString("negativePrompt", "")

        val dims = DesktopImageIo.size(sourcePath)!!
        val (sourceW, sourceH) = dims
        // 框在脸部一带（归一化坐标，和界面上拖出来的框是同一种东西）
        val frameNorm = SelectionShape.Rect(0.28f, 0.12f, 0.72f, 0.42f)
        val frame = SelectionGeometry.bounds(frameNorm, sourceW, sourceH)!!
        val plan = FocusedInpaint.plan(frame, context, sourceW, sourceH)
        assertNotNull("算不出聚焦方案", plan)
        plan!!
        println(
            "[live3] 框 ${frame.w}x${frame.h} 上下文 ${context}px → 裁剪 ${plan.cropW}x${plan.cropH}@${plan.cropX},${plan.cropY}" +
                " → 请求 ${plan.requestW}x${plan.requestH}（放大 ${"%.2f".format(plan.scale)}×）",
        )

        // ---- 原图（全分辨率）----
        val sourceImage = DesktopImageIo.decodeFull(sourcePath)!!
        val sourcePixels = DesktopImageIo.pixelsOf(sourceImage)
        sourceImage.recycle()

        // ---- 裁剪 + 放大 → 底图 ----
        val cropped = IntArray(plan.cropW * plan.cropH)
        for (row in 0 until plan.cropH) {
            System.arraycopy(
                sourcePixels,
                (plan.cropY + row) * sourceW + plan.cropX,
                cropped,
                row * plan.cropW,
                plan.cropW,
            )
        }
        val cropImage = DesktopImageIo.fromArgb(cropped, plan.cropW, plan.cropH)
        val basePng = MaskCodec.basePng(DesktopImageIo, cropImage, plan.requestW, plan.requestH)
        cropImage.recycle()

        // ---- 蒙版：框里没涂 → 整框（`fillWholeFrame = true`）----
        val blankMask = PixelMask(sourceW, sourceH, ByteArray(sourceW * sourceH))
        val focusMask = FocusedInpaint.maskIntoRequest(blankMask, plan, fillWholeFrame = true)!!
        val requestMask = MaskCodec.inpaintMask(
            MaskExpand.dilate(focusMask, expand), plan.requestW, plan.requestH,
        )!!
        val maskPng = MaskCodec.maskPng(DesktopImageIo, requestMask, plan.requestW, plan.requestH)

        val outDir = File(File(System.getProperty("java.io.tmpdir"), "naistudio/docs/shots/inpaint-alpha-live").absolutePath).apply { mkdirs() }
        File(outDir, "10-focus-source.png").writeBytes(DesktopImageIo.pngBytes(sourceImageOf(sourcePixels, sourceW, sourceH)))
        File(outDir, "11-focus-mask.png").writeBytes(maskPng)
        File(outDir, "12-focus-request-base.png").writeBytes(basePng)

        // ---- 发请求（App 原样的 infill，只是底图/蒙版换成了裁剪图）----
        val params = GenerateParams(
            model = inpaintModel,
            positivePrompt = prompt,
            negativePrompt = negative,
            width = plan.requestW,
            height = plan.requestH,
        )
        val (images, usedModel) = runBlocking {
            NaiApi().inpaint(
                token = token,
                params = params,
                seed = 20260916L,
                baseImagePng = basePng,
                maskPng = maskPng,
                strength = strength,
                noise = noise,
                imageBaseUrl = imageBaseUrl,
                extras = GenerateExtras(),
                modelMode = modelMode,
            )
        }
        assertTrue("服务端没返回图", images.isNotEmpty())
        File(outDir, "13-focus-raw-result.png").writeBytes(images.first())

        // ---- 贴回：只写框内 ----
        val generated = DesktopImageIo.decodeBytes(images.first())!!
        val scaledResult = DesktopImageIo.scale(generated, plan.cropW, plan.cropH, smooth = true)
        if (scaledResult !== generated) generated.recycle()
        val generatedPixels = DesktopImageIo.pixelsOf(scaledResult)
        scaledResult.recycle()

        val working = sourcePixels.copyOf()
        val touched = FocusedInpaint.pasteBack(working, sourceW, sourceH, generatedPixels, plan)
        assertNotNull("贴回没改到任何像素 —— 映射算错了", touched)
        File(outDir, "14-focus-pasted.png").writeBytes(DesktopImageIo.pngBytes(sourceImageOf(working, sourceW, sourceH)))

        // ---- 判分：框内 vs 框外 ----
        var outsideChanged = 0
        var insideChanged = 0
        for (y in 0 until sourceH) {
            val row = y * sourceW
            for (x in 0 until sourceW) {
                val inFrame = x >= frame.x && x < frame.x + frame.w && y >= frame.y && y < frame.y + frame.h
                if (working[row + x] != sourcePixels[row + x]) {
                    if (inFrame) insideChanged++ else outsideChanged++
                }
            }
        }
        println(
            "[live3] 完成：模型=$usedModel 请求 ${plan.requestW}x${plan.requestH}" +
                " 耗时逻辑同上；框内改动 $insideChanged / ${frame.area} 像素，" +
                "**框外改动 $outsideChanged 像素**（必须是 0）",
        )
        println("[live3] 产物：${outDir.absolutePath}")
        assertEquals("框外一个像素都不该变（这就是'只重绘框内'）", 0, outsideChanged)
    }

    private fun sourceImageOf(pixels: IntArray, width: Int, height: Int): com.kallan.naistudio.platform.NativeImage {
        val image = DesktopImageIo.fromArgb(pixels, width, height)
        return image
    }
}


