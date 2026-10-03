package com.kallan.naistudio.services

import android.util.Base64
import android.util.Log
import com.kallan.naistudio.models.CharCaptionItem
import com.kallan.naistudio.models.ComicLayout
import com.kallan.naistudio.models.GenerateExtras
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.NaiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * NovelAI API 层。
 *
 * **行为对齐参考实现**：`novelai-image-desktop/mobile/lib/services/nai_api.dart`。
 * 请求体字段、默认值、端点选择、错误分类、重试策略都以其为准；代码是 Kotlin 自写。
 *
 * 端点依据参考实现（注意账号走的是 `/user/data`，不是 `/user/subscription`）：
 *  · 生图：`{imageBase}/ai/generate-image`
 *  · 账号：`{imageBase}/user/data` —— 官方 image 网页客户端用的就是这个路由
 */

const val DEFAULT_API_BASE = "https://api.novelai.net"
const val DEFAULT_IMAGE_BASE = "https://image.novelai.net"

class NaiHttpException(val statusCode: Int, message: String) : Exception(message)

class NaiNetworkException(cause: Throwable) : Exception("NovelAI 网络连接失败", cause)

data class OpusGenerationUsage(
    val percent: Double,
    val isNegative: Boolean,
    val timeUntilNextPercent: Double,
)

data class AccountSummary(
    val hasToken: Boolean = false,
    val tierName: String? = null,
    val tierLevel: Int? = null,
    val anlasBalance: Int? = null,
    val hasActiveSubscription: Boolean? = null,
    val opusUsage: OpusGenerationUsage? = null,
    /** true 表示这次读到的账号信息是失败后的兜底值，不是真实数据。 */
    val stale: Boolean = false,
)

/**
 * **自托管网关的配额**（NAI Gate 独有的一块，见 [NaiApi.fetchGatewayQuota]）。
 *
 * 官方的 `AccountSummary` 里没有"V5 每日还剩几张"这个维度 —— 那是网关自己加的本地额度，
 * 所以单独一个类型装着，界面按"网关模式才显示"处理。
 */
data class GatewayQuota(
    /** V5 免费图今天还剩几张。 */
    val v5LeftToday: Int,
    /** V5 免费图每日上限（0 = 不限）。 */
    val v5DailyLimit: Int,
    /** 站长把 V5 设成不限量。 */
    val v5Unlimited: Boolean,
    /** 这把 Key 是否被授权用 Anlas（付费规格）。 */
    val anlasEnabled: Boolean,
    /** 本月 Anlas 还剩多少。 */
    val anlasLeft: Int,
    /** 本月 Anlas 上限。 */
    val anlasMonthlyLimit: Int,
    /** `all` = 含 V5；否则只到 V4.5。 */
    val imageModelScope: String,
) {
    /** V5 免费额度用了百分之多少（0..100）；不限量或没配额度 ⇒ null（界面不画条）。 */
    val v5UsedPercent: Int?
        get() = if (v5Unlimited || v5DailyLimit <= 0) {
            null
        } else {
            ((v5DailyLimit - v5LeftToday).coerceIn(0, v5DailyLimit) * 100) / v5DailyLimit
        }
}

class NaiApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val random = SecureRandom()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        // **整体调用超时**：readTimeout 只在"两次读之间"计时，服务端只要一直慢慢吐数据
        // （或流式连接被挂住），就永远不会超时 —— 真机上图生图就这么卡死过一次。
        // 这一条保证任何请求最终都会结束，不会无限转圈。
        .callTimeout(240, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 报价接口单独用短超时（参考实现是 12 秒），慢报价不该拖住生成页。 */
    private val quoteHttp: OkHttpClient = http.newBuilder()
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** 进行中的请求。取消要能命中"当前真正在飞的那个"，所以每个请求都登记自己的 Call。 */
    private val activeCalls: MutableSet<okhttp3.Call> =
        java.util.Collections.synchronizedSet(mutableSetOf<okhttp3.Call>())

    /** 对应参考实现的 `cancelActiveGeneration`。OkHttp 的 `Call.cancel()` 是线程安全的。 */
    fun cancelActiveGeneration() {
        synchronized(activeCalls) {
            activeCalls.toList().forEach { it.cancel() }
            activeCalls.clear()
        }
    }

    /** 参考实现：`1 + _rng.nextInt(2147483646)`。 */
    fun randomSeed(): Long = (1 + random.nextInt(2_147_483_646)).toLong()

    // =======================================================================
    // 请求体构造
    // =======================================================================

    /**
     * 构造文生图请求体。
     *
     * 逐条对应参考实现的 `buildPayload`。几个**容易改错**的点：
     *  · V5 的 `noise_schedule` 固定为 `karras`（模拟官方前端；V5 不开放该控制）
     *  · `uc` 与 `negative_prompt` 发同一份文本，`ucPreset` 与 `uc_preset` 也各发一次
     *  · `tag_hint_qt`：standard=1 / light=3 / none=0
     *  · `skip_cfg_above_sigma` 默认显式发 null，仅 variety 开启时才置 58
     *  · 选 k_euler_ancestral 且调度非 native 时，必须带
     *    `deliberate_euler_ancestral_bug=false` 与 `prefer_brownian=true`
     *  · 非 V4+ 模型走 `sm`/`sm_dyn`，V4+ 走 `v4_prompt`/`v4_negative_prompt`
     *
     * @param seed 已解析好的种子（随机或固定），调用方负责决定
     * @param modelMode 数据集模式："anime" / "furry" / "background"
     * @param structuredCharacters false 时角色提示词降级成 `a | b` 管道形式
     */
    fun buildPayload(
        params: GenerateParams,
        seed: Long,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
        structuredCharacters: Boolean = true,
        /** `generate`（文生图）或 `infill`（局部重绘）。 */
        action: String = "generate",
        /** 局部重绘强度：只进 `inpaintImg2ImgStrength`；`strength` 字段按参考实现固定 0.7。 */
        inpaintStrength: Double = 1.0,
        /** 局部重绘的噪声（节点里叫 `noise`，默认 0）。 */
        inpaintNoise: Double = 0.0,
        /** 图生图强度（`strength`，节点默认 0.70）。 */
        img2imgStrength: Double = 0.7,
        /** 图生图附加噪声（`noise`，节点默认 0）。 */
        img2imgNoise: Double = 0.0,
    ): JSONObject {
        // 重绘用的模型是 `-inpainting` 系，normalized() 必须放行，否则会被打回默认文生图模型
        val p = params.normalized(allowInpaintModel = action == "infill")
        val resolvedSeed = seed.coerceIn(1L, 0xFFFFFFFFL)

        // 风格预设只在**这一刻**并入：勾选/取消预设不会回写风格提示词输入框，
        // 用户输入的风格提示词与预设内容各自独立保存。
        val presetPrompt = extras.stylePresetPrompts
            .filter { it.isNotBlank() }
            .fold("") { acc, next -> NaiText.merge(acc, next) }
        val basePrompt = NaiText.merge(
            NaiText.merge(presetPrompt, p.stylePrompt),
            p.positivePrompt,
        )

        // NovelAI V4+ 在 Furry 模式下共用 Anime 权重。官方前端靠前置这个标签来启用
        // furry 数据集；而 Furry V3 本身就是专用权重，**不能**再给它加。
        val hasFurryDataset = FURRY_DATASET.containsMatchIn(basePrompt)
        val modePrompt = if (modelMode == "furry" && p.isV4Plus && !hasFurryDataset) {
            NaiText.merge("fur dataset", basePrompt)
        } else {
            basePrompt
        }

        val qualityPreset = p.qualityPreset
        // 漫画模式：在**请求这一刻**追加风格词（不改用户的提示词输入框），
        // 与 stylePresetPrompts 的处理方式一致。
        // 风格词是**字段**不是常量 —— "按剧情分镜"会让 LLM 输出它，用户也能手改。
        // 漫画模式与普通模式共用风格、正面和负面提示词，不再隐式追加旧整页风格词。
        val qualityPrompt = NaiText.merge(
            modePrompt,
            NaiText.qualityTags(p.model, qualityPreset, modePrompt),
        )

        val transparentBackground = p.isV5 && p.transparentBackground
        val effectivePrompt = if (transparentBackground) {
            NaiText.merge(qualityPrompt, "transparent background")
        } else {
            qualityPrompt
        }
        // ⚠️ 预设的**负面**那一半也是在这一刻并入（用户 2026-09-24：「**负面进负面**」）——
        //    和上面 `presetPrompt`（正面 → 风格词）**同一个口径**：
        //    勾选 / 取消预设**不回写**负面输入框，用户手写的负面词与预设内容各归各的。
        //    ⚠️ 顺序：**用户手写的在前、预设拼在后面**。
        val presetNegative = extras.stylePresetNegatives
            .filter { it.isNotBlank() }
            .fold("") { acc, next -> NaiText.merge(acc, next) }
        val effectiveNegative = NaiText.merge(
            NaiText.merge(p.negativePrompt, presetNegative),
            NaiText.ucPresetText(p.model, p.ucPreset),
        )

        // 未勾选的整个不参与请求（与 ComfyUI 节点的 apply() 一致），
        // 空提示词的也没有意义，一并排除。
        // ⚠️ 走**当前模式那一份列表**：漫画模式下是分格列表，与角色分区互不干扰。
        val activeCharacters = extras.activeItems
            .filter { it.enabled && it.prompt.trim().isNotEmpty() }
            .take(p.maxCharacterPrompts)

        // 漫画模式：坐标**由版式派生**（分格自己的 x/y 根本不参与）。
        // 角色模式：anchors 为空，下面按各自手拖的 x/y 走。
        val comicAnchors = if (extras.comicMode) {
            ComicLayout.anchorsFor(extras.comicLayout, extras.comicOrder, activeCharacters.size)
        } else {
            emptyList()
        }

        /** 取第 index 个条目实际发送的坐标。 */
        fun centerOf(index: Int, item: com.kallan.naistudio.models.CharCaptionItem): Pair<Double, Double> =
            if (extras.comicMode) {
                // 漫画模式：**手拖过就用手拖的**（用户在版式预览里点开位置编辑挪过的格），
                // 没动过的仍按版式派生。这样"一键套版式"和"手动微调"能共存。
                if (item.useCoords) {
                    item.x to item.y
                } else {
                    comicAnchors.getOrNull(index) ?: (0.5 to 0.5)
                }
            } else {
                item.x to item.y
            }

        /** 漫画模式下始终发坐标（版式本身就给了坐标）；角色模式沿用各自开关。 */
        fun useCoordsOf(item: com.kallan.naistudio.models.CharCaptionItem): Boolean =
            if (extras.comicMode) true else item.useCoords

        fun centersJson(index: Int, item: com.kallan.naistudio.models.CharCaptionItem): JSONArray {
            val (x, y) = centerOf(index, item)
            val use = useCoordsOf(item)
            return JSONArray().put(
                JSONObject().apply {
                    put("x", if (use) x.coerceIn(0.0, 1.0) else 0.5)
                    put("y", if (use) y.coerceIn(0.0, 1.0) else 0.5)
                },
            )
        }

        val charCaptions = activeCharacters.mapIndexed { index, character ->
            JSONObject().apply {
                put("char_caption", character.prompt.trim())
                // V4/V4.5 即使把位置交给 AI，也**必须**给一个 center：空数组会 500。
                // (0.5, 0.5) 就是"由 AI 决定"的哨兵值，此时 use_coords 保持 false。
                put("centers", centersJson(index, character))
            }
        }

        val negativeCharCaptions = if (activeCharacters.any { it.negativePrompt.trim().isNotEmpty() }) {
            activeCharacters.mapIndexed { index, character ->
                JSONObject().apply {
                    put("char_caption", character.negativePrompt.trim())
                    put("centers", centersJson(index, character))
                }
            }
        } else {
            emptyList()
        }

        val hasCoords = structuredCharacters &&
            (extras.comicMode && activeCharacters.isNotEmpty() || activeCharacters.any { it.useCoords })

        val inputPrompt = if (structuredCharacters || charCaptions.isEmpty()) {
            effectivePrompt
        } else {
            (listOf(effectivePrompt) + charCaptions.map { it.getString("char_caption") })
                .filter { it.isNotBlank() }
                .joinToString(" | ")
        }

        // NovelAI 的 V5 前端把调度固定成 Karras 并隐藏了选择器。这里保持移动端
        // 请求与那条规则字节级一致，而不是从所选采样器去推导另一个调度。
        val effectiveNoiseSchedule = if (p.isV5) {
            "karras"
        } else {
            if (p.noiseSchedule.isEmpty()) "native" else p.noiseSchedule
        }

        val parameters = JSONObject().apply {
            put("params_version", 4)
            put("width", p.width)
            put("height", p.height)
            put("scale", p.cfgScale.coerceIn(0.0, 10.0))
            put("sampler", p.sampler)
            put("steps", p.steps)
            put("n_samples", 1)
            put("seed", resolvedSeed)
            put("noise_schedule", effectiveNoiseSchedule)
            put("uc", effectiveNegative)
            put("negative_prompt", effectiveNegative)
            put("ucPreset", p.ucPreset)
            put("uc_preset", p.ucPreset)
            put("cfg_rescale", p.cfgRescale)
            put("legacy", false)
            put("legacy_v3_extend", false)
            put("dynamic_thresholding", if (p.isV5) false else p.cfgRescale > 0)
            put("skip_cfg_above_sigma", JSONObject.NULL)
            put("qualityPresetId", qualityPreset)
            put("qualityToggle", qualityPreset != "none")
            put("quality_toggle", qualityPreset != "none")
            put(
                "tag_hint_qt",
                when (qualityPreset) {
                    "standard" -> 1
                    "light" -> 3
                    else -> 0
                },
            )
        }

        if (p.isV5) {
            parameters.put("tag_hint_transparent_background", transparentBackground)
            parameters.put("straight_alpha", transparentBackground)
        }
        if (p.variety && p.supportsVariety) {
            parameters.put("skip_cfg_above_sigma", 58)
        }
        if (p.sampler == "k_euler_ancestral" && effectiveNoiseSchedule != "native") {
            parameters.put("deliberate_euler_ancestral_bug", false)
            parameters.put("prefer_brownian", true)
        }

        if (p.isV4Plus) {
            parameters.put("use_coords", hasCoords)
            parameters.put(
                "v4_prompt",
                JSONObject().apply {
                    put(
                        "caption",
                        JSONObject().apply {
                            put("base_caption", inputPrompt)
                            put("char_captions", if (structuredCharacters) JSONArray(charCaptions) else JSONArray())
                        },
                    )
                    put("use_coords", hasCoords)
                    put("use_order", true)
                },
            )
            parameters.put(
                "v4_negative_prompt",
                JSONObject().apply {
                    put(
                        "caption",
                        JSONObject().apply {
                            put("base_caption", effectiveNegative)
                            put(
                                "char_captions",
                                if (structuredCharacters) JSONArray(negativeCharCaptions) else JSONArray(),
                            )
                        },
                    )
                    put("use_coords", hasCoords && negativeCharCaptions.isNotEmpty())
                    put("use_order", false)
                    put("legacy_uc", !p.isV4Plus)
                },
            )
        } else {
            parameters.put("sm", p.smea)
            parameters.put("sm_dyn", p.smea && p.smeaDyn)
        }

        // ---- 局部重绘（infill）----
        // 字段口径照参考实现 `mobile/lib/services/nai_api.dart` 的 inpaint()，两个反直觉但要照抄的点：
        //  · `strength` 固定 0.7：官方 infill 报文保留的是通用 img2img 默认值，
        //    用户调的那个强度只进 `inpaintImg2ImgStrength`
        //  · `noise` 固定 0：参考实现里 noise 形参已被丢弃，请求里恒为 0
        // `image` / `mask`（base64 字符串）由 [inpaint] 填，因为要等位图准备好。
        if (action == "infill") {
            val strength = inpaintStrength.coerceIn(0.0, 1.0)
            parameters.put("strength", 0.7)
            parameters.put("noise", inpaintNoise.coerceIn(0.0, 1.0))
            parameters.put("extra_noise_seed", maxOf(0L, resolvedSeed - 1))
            parameters.put("request_type", "NativeInfillingRequest")
            parameters.put("add_original_image", false)
            parameters.put("inpaintImg2ImgStrength", strength)
            if (kotlin.math.abs(strength - 1.0) > 1e-8) {
                parameters.put(
                    "img2img",
                    JSONObject().apply {
                        put("strength", strength)
                        put("color_correct", true)
                    },
                )
            }
        }

        // ---- 图生图（img2img）----
        // 口径照参考实现：`image` 是 base64（由 [img2img] 填），`request_type = Img2ImgRequest`，
        // `strength`（节点默认 0.70）与 `noise`（默认 0）都在 parameters 里。
        // 尺寸策略「保持输入尺寸」时，调用方已经把宽高 64 对齐成输入图的尺寸传进来了。
        if (action == "img2img") {
            parameters.put("strength", img2imgStrength.coerceIn(0.0, 1.0))
            parameters.put("noise", img2imgNoise.coerceIn(0.0, 1.0))
            parameters.put("request_type", "Img2ImgRequest")
        }

        return JSONObject().apply {
            put("input", inputPrompt)
            put("model", p.model)
            put("action", action)
            put("parameters", parameters)
        }
    }

    // =======================================================================
    // 官方放大（Official Upscale，固定 2×）
    // =======================================================================

    /**
     * NovelAI **官方放大**：`POST {imageBase}/ai/upscale`，multipart 带 `image`（PNG）
     * 与 `request`（JSON，见 [OfficialUpscale.requestJson]）。口径照插件
     * `novelai-genytools` 的 `NAIOfficialUpscale` 节点：输出**固定 2×**、按输入像素扣 1–4 Anlas。
     *
     * @return 放大后的图片字节（服务端可能回 ZIP，也可能回裸图，两条路都走）
     */
    suspend fun officialUpscale(
        token: String,
        imagePng: ByteArray,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}${OfficialUpscale.PATH}"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "image.png",
                imagePng.toRequestBody("image/png".toMediaType()),
            )
            .addFormDataPart(
                "request",
                "request.json",
                OfficialUpscale.requestJson().toString().toRequestBody(jsonMedia),
            )
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/zip, image/png, application/octet-stream")
            .header("x-correlation-id", correlationId())
            .header("x-initiated-at", java.time.Instant.now().toString())
            .post(body)
            .build()

        Log.i(TAG, "upscale: POST $url image=${imagePng.size / 1024}KB")
        val started = System.currentTimeMillis()
        val call = http.newCall(request)
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                val bytes = response.body?.bytes()
                    ?: throw NaiHttpException(0, "NovelAI 返回了空响应。")
                if (!response.isSuccessful) {
                    throw NaiHttpException(response.code, errorText(response.code, bytes))
                }
                val image = extractImages(bytes).firstOrNull()
                    ?: throw NaiHttpException(0, "官方放大没有返回图片。")
                Log.i(TAG, "upscale: ok ${image.size / 1024}KB in ${System.currentTimeMillis() - started}ms")
                image
            }
        } catch (e: IOException) {
            if (!activeCalls.contains(call)) throw NaiHttpException(0, "已取消生成。")
            Log.w(TAG, "upscale failed after ${System.currentTimeMillis() - started}ms: ${e.message}")
            throw NaiNetworkException(e)
        } finally {
            activeCalls.remove(call)
        }
    }

    // =======================================================================
    // 图生图（img2img）
    // =======================================================================

    /**
     * 图生图：把 [baseImagePng] 当底图，按 [strength] 重新生成。
     *
     * 行为对齐参考实现 `nai_api.dart` 的 `generateI2I()`：**纯 JSON + base64**
     * （`parameters.image` 直接是 base64，无 `data:` 前缀），顶层 `action = "img2img"`。
     *
     * ⚠️ **图生图刻意不走流式**：参考实现有一条 `_supportsSafeStreamTransport` ——
     * "只有 `action == "generate"` 且不带附件时才走流式"。带内联附件的请求体是 MB 级的
     * （底图 base64），发给流式端点会把连接挂住（真机上卡死过一次，界面永远停在"正在生成"）。
     * 所以这里只发普通 JSON 请求。
     *
     * @return 图片字节列表
     */
    suspend fun img2img(
        token: String,
        params: GenerateParams,
        seed: Long,
        baseImagePng: ByteArray,
        strength: Double,
        noise: Double,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image"
        val payload = buildPayload(
            params = params,
            seed = seed,
            extras = extras,
            modelMode = modelMode,
            action = "img2img",
            img2imgStrength = strength,
            img2imgNoise = noise,
        )
        payload.getJSONObject("parameters")
            .put("image", Base64.encodeToString(baseImagePng, Base64.NO_WRAP))
        val body = payload.toString()
        Log.i(TAG, "img2img: POST $url model=${params.model} ${params.width}x${params.height} body=${body.length / 1024}KB")
        val started = System.currentTimeMillis()
        try {
            val bytes = postWithRetry(token, url, body)
            Log.i(TAG, "img2img: got ${bytes.size / 1024}KB in ${System.currentTimeMillis() - started}ms")
            extractImages(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "img2img failed after ${System.currentTimeMillis() - started}ms: ${e.message}")
            throw e
        }
    }

    // =======================================================================
    // 生图
    // =======================================================================

    /**
     * 文生图。返回 1 张（`n_samples` 固定为 1，与参考实现一致）PNG 字节。
     *
     * 429 会重试，最多 4 次尝试，优先遵循 `retry-after`，否则按 2s/4s/8s 退避（上限 30s）。
     */
    suspend fun generate(
        token: String,
        params: GenerateParams,
        seed: Long,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val payload = buildPayload(params, seed, extras, modelMode)
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image"
        val bytes = postWithRetry(token, url, payload.toString())
        extractImages(bytes)
    }

    // =======================================================================
    // 局部重绘（infill）
    // =======================================================================

    /**
     * 局部重绘：把 [baseImagePng] 里被 [maskPng] 涂白的区域重新生成。
     *
     * 行为对齐参考实现 `mobile/lib/services/nai_api.dart` 的 `inpaint()`：
     *  · **纯 JSON + base64**（不是 multipart）：`parameters.image` / `parameters.mask` 直接是
     *    base64 字符串，**不带 `data:image/png;base64,` 前缀**
     *  · 顶层 `action = "infill"`
     *  · 模型按候选链回退（curated → full），只在服务端明确说"不支持"时才换下一个
     *
     * 参考实现还会把结果按官方做法回贴到原图（latent 膨胀 + 模糊）；这里**先用服务端返回图**，
     * 少一类误差来源，回贴列为后续可选项。
     *
     * @return (图片字节列表, 实际使用的模型)
     */
    suspend fun inpaint(
        token: String,
        params: GenerateParams,
        seed: Long,
        baseImagePng: ByteArray,
        maskPng: ByteArray,
        strength: Double,
        noise: Double = 0.0,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
    ): Pair<List<ByteArray>, String> = withContext(Dispatchers.IO) {
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image"
        val encodedImage = Base64.encodeToString(baseImagePng, Base64.NO_WRAP)
        val encodedMask = Base64.encodeToString(maskPng, Base64.NO_WRAP)

        var lastError: NaiHttpException? = null
        for (candidate in inpaintModelCandidates(params.model)) {
            val payload = buildPayload(
                params = params.copy(model = candidate),
                seed = seed,
                extras = extras,
                modelMode = modelMode,
                action = "infill",
                inpaintStrength = strength,
                inpaintNoise = noise,
            )
            payload.getJSONObject("parameters").apply {
                put("image", encodedImage)
                put("mask", encodedMask)
            }

            try {
                val bytes = postWithRetry(token, url, payload.toString())
                return@withContext extractImages(bytes) to candidate
            } catch (e: NaiHttpException) {
                lastError = e
                // 只有"服务端明确说不支持这个重绘模型"才换候选；别的错误（402 余额不足、
                // 401 token 失效…）必须原样抛出去，不然会把真实原因吃掉
                if (!unsupportedInpaintModel(e)) throw e
            }
        }
        throw lastError ?: NaiHttpException(0, "重绘请求未能完成。")
    }

    /**
     * 重绘模型候选链。
     *
     * V5 Curated 等 curated 档**没有对应的重绘权重**，官网的做法是回退到同代 full 版；
     * 副作用是角色上限会从 32 掉到 6，UI 必须把回退提示出来（不能静默）。
     */
    fun inpaintModelCandidates(requested: String): List<String> = when (requested) {
        "nai-diffusion-5-curated-inpainting" ->
            listOf(requested, "nai-diffusion-5-full-inpainting")

        "nai-diffusion-4-5-curated-inpainting" ->
            listOf(requested, "nai-diffusion-4-5-full-inpainting")

        "nai-diffusion-4-curated-inpainting" ->
            listOf(requested, "nai-diffusion-4-full-inpainting")

        else -> listOf(requested)
    }

    /**
     * 局部重绘的**流式**版：和 [generateStreaming] 一样有"模糊 → 清晰"的渐进预览。
     *
     * 传输沿用本项目已验证的流式形状（multipart，只带一个 `request` 部件，
     * `Accept` 协商返回格式），底图与遮罩按 base64 **内联进 JSON**
     * （流式端点没有 multipart 附件通道；插件那边 `_stream_payload` 也是这个做法）。
     *
     * @return null 表示**服务端不支持流式**，调用方应回退到 [inpaint]；这与"失败"是两回事。
     */
    suspend fun inpaintStreaming(
        token: String,
        params: GenerateParams,
        seed: Long,
        baseImagePng: ByteArray,
        maskPng: ByteArray,
        strength: Double,
        noise: Double = 0.0,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
        onPreview: (NaiPreview) -> Unit,
    ): Pair<List<ByteArray>, String>? = withContext(Dispatchers.IO) {
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image-stream"
        val encodedImage = Base64.encodeToString(baseImagePng, Base64.NO_WRAP)
        val encodedMask = Base64.encodeToString(maskPng, Base64.NO_WRAP)
        val totalSteps = params.normalized(allowInpaintModel = true).steps

        var lastError: NaiHttpException? = null
        for (candidate in inpaintModelCandidates(params.model)) {
            val payload = buildPayload(
                params = params.copy(model = candidate),
                seed = seed,
                extras = extras,
                modelMode = modelMode,
                action = "infill",
                inpaintStrength = strength,
                inpaintNoise = noise,
            )
            payload.getJSONObject("parameters").apply {
                put("image", encodedImage)
                put("mask", encodedMask)
            }

            when (val outcome = executeStreamingRequest(token, url, payload.toString(), totalSteps, onPreview)) {
                is StreamOutcome.Images -> return@withContext outcome.images to candidate
                // 服务端不支持流式：整体回退非流式（不再试下一个候选模型）
                StreamOutcome.Unsupported -> return@withContext null
                is StreamOutcome.HttpError -> {
                    lastError = outcome.error
                    // 只有"服务端说不支持这个重绘模型"才换候选，其它错误原样抛
                    if (!unsupportedInpaintModel(outcome.error)) throw outcome.error
                }
            }
        }
        throw lastError ?: NaiHttpException(0, "重绘请求未能完成。")
    }

    private fun unsupportedInpaintModel(error: NaiHttpException): Boolean {
        if (error.statusCode != 400 && error.statusCode != 422) return false
        val text = (error.message ?: "").lowercase()
        return UNSUPPORTED_INPAINT_HINTS.any { text.contains(it) }
    }

    private fun postWithRetry(token: String, url: String, json: String, maxAttempts: Int = 4): ByteArray {
        var lastMessage = ""
        for (attempt in 0 until maxAttempts) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/zip, application/octet-stream")
                .post(json.toRequestBody(jsonMedia))
                .build()

            val call = http.newCall(request)
            activeCalls.add(call)
            try {
                call.execute().use { response ->
                    val body = response.body?.bytes() ?: ByteArray(0)
                    if (response.isSuccessful) return body

                    lastMessage = errorText(response.code, body)
                    val retryable = response.code == 429
                    if (!retryable || attempt == maxAttempts - 1) {
                        throw NaiHttpException(response.code, lastMessage)
                    }
                    val retryAfter = response.header("retry-after")?.toIntOrNull()
                    val waitMs = if (retryAfter != null && retryAfter > 0) {
                        retryAfter * 1000L
                    } else {
                        2000L shl attempt
                    }
                    Thread.sleep(waitMs.coerceAtMost(30_000L))
                }
            } catch (e: NaiHttpException) {
                throw e
            } catch (e: IOException) {
                if (!activeCalls.contains(call)) throw NaiHttpException(0, "已取消生成。")
                throw NaiNetworkException(e)
            } finally {
                activeCalls.remove(call)
            }
        }
        throw NaiHttpException(0, lastMessage.ifEmpty { "生成请求未能完成。" })
    }

    // =======================================================================
    // 流式生成（渐进预览）
    // =======================================================================

    /**
     * 流式生成。**返回 null 表示服务端不支持流式**，调用方应回退到 [generate]；
     * 这与"失败"是两回事（404/405/415/501，或报文明确说 not allowed，都算不支持）。
     *
     * 请求形状与参考实现的 `_postGenerateStream` 一致：
     *  · **multipart，只带一个 `request` 部件**（内容是 JSON）
     *  · `Accept: application/x-msgpack, text/event-stream, application/zip`
     *    —— 返回格式由 Accept 协商，不预设；解码器会按首块字节嗅探
     *  · **不设** `parameters.stream`
     *
     * 安全前提（参考实现的 `_supportsSafeStreamTransport`）：只有 `action == "generate"`
     * 且不带 image/mask/参考图的 payload 才走流式。图生图类请求会让附件内联，吃内存。
     */
    suspend fun generateStreaming(
        token: String,
        params: GenerateParams,
        seed: Long,
        extras: GenerateExtras = GenerateExtras(),
        modelMode: String = "anime",
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
        onPreview: (NaiPreview) -> Unit,
    ): List<ByteArray>? = withContext(Dispatchers.IO) {
        val payload = buildPayload(params, seed, extras, modelMode)
        val totalSteps = params.normalized().steps
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image-stream"

        when (val outcome = executeStreamingRequest(token, url, payload.toString(), totalSteps, onPreview)) {
            is StreamOutcome.Images -> return@withContext outcome.images
            // 404/405/415/501 或报文明确说不支持 → 回退非流式（与"失败"两回事）
            StreamOutcome.Unsupported -> return@withContext null
            is StreamOutcome.HttpError -> throw outcome.error
        }
    }

    private fun streamingUnavailable(text: String): Boolean {
        val lower = text.lowercase()
        return STREAM_UNAVAILABLE_HINTS.any { lower.contains(it) }
    }

    /** 一次流式请求的结果：成功出图 / 服务端不支持流式（调用方应回退非流式）/ 其它 HTTP 错误。 */
    private sealed class StreamOutcome {
        class Images(val images: List<ByteArray>) : StreamOutcome()
        object Unsupported : StreamOutcome()
        class HttpError(val error: NaiHttpException) : StreamOutcome()
    }

    /**
     * 发一次流式生成请求并消费响应。文生图与局部重绘的流式路径**传输部分逐行相同**，抽出来复用：
     * multipart（只带一个 `request` 部件）+ Accept 协商 + 逐块消费 + 取消/网络异常归一。
     *
     * payload 的组装仍留在各自调用方用 [buildPayload] 完成（局部重绘还要注入 image/mask 的 base64，
     * 并按候选模型重试），所以 README 硬约束"请求体只在 buildPayload 里拼"依旧成立——这里只抽传输层。
     */
    private suspend fun executeStreamingRequest(
        token: String,
        url: String,
        payloadBody: String,
        totalSteps: Int,
        onPreview: (NaiPreview) -> Unit,
    ): StreamOutcome {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("request", "blob", payloadBody.toRequestBody(jsonMedia))
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/x-msgpack, text/event-stream, application/zip")
            .header("x-correlation-id", correlationId())
            .header("x-initiated-at", java.time.Instant.now().toString())
            .post(body)
            .build()

        val call = http.newCall(request)
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    if (response.code in STREAM_UNSUPPORTED_CODES || streamingUnavailable(text)) {
                        return StreamOutcome.Unsupported
                    }
                    return StreamOutcome.HttpError(
                        NaiHttpException(response.code, errorText(response.code, text.toByteArray())),
                    )
                }
                val contentType = response.header("Content-Type").orEmpty()
                val source = response.body?.source()
                    ?: throw NaiHttpException(0, "NovelAI 返回了空响应。")
                val result = consumeNaiGenerationStream(source, totalSteps, contentType, onPreview)
                val images = result.archive?.let { extractImages(it) } ?: result.images
                return StreamOutcome.Images(images)
            }
        } catch (e: NaiStreamException) {
            // 已经出过预览才断流：不静默改用非流式重发，避免重复扣费（与参考实现一致）
            throw NaiHttpException(0, e.message ?: "流式解码失败")
        } catch (e: IOException) {
            if (!activeCalls.contains(call)) throw NaiHttpException(0, "已取消生成。")
            throw NaiNetworkException(e)
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun correlationId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(6)

    // =======================================================================
    // 官方报价
    // =======================================================================

    /**
     * 向官方的报价接口要一次预扣价格。
     *
     * 参考实现：把提示词为空的参数换成 `quote` 占位后再构造 payload，POST 到
     * `{imageBase}/ai/generate-image/request-price`，非 2xx 直接返回 null。
     *
     * ⚠️ **响应结构未经核对**（规格调研时 `billing/anlas.dart` 未读到），所以这里只做
     * 尽力解析：认得出数字就返回，认不出返回 null，让 UI 显示"读取中/未知"，
     * **绝不编一个数字显示给用户**。
     */
    suspend fun requestOfficialGenerationPrice(
        token: String,
        params: GenerateParams,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val quoteParams = if (params.positivePrompt.trim().isEmpty()) {
                params.copy(positivePrompt = "quote")
            } else {
                params
            }
            val payload = buildPayload(quoteParams, randomSeed())
            val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/ai/generate-image/request-price"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            quoteHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                extractAnlasPrice(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAnlasPrice(text: String): Int? {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        for (container in listOf(null, "data", "price", "result", "quote")) {
            val scope = if (container == null) root else root.optJSONObject(container) ?: continue
            for (key in ANLAS_PRICE_KEYS) {
                if (scope.has(key) && !scope.isNull(key)) {
                    val value = scope.optDouble(key, Double.NaN)
                    if (value.isFinite()) return value.toInt()
                }
            }
        }
        return null
    }

    // =======================================================================
    // 导演工具（Director Tools，6 件套）
    // =======================================================================

    /**
     * 调一次导演工具。口径照插件 `nai_client.director()`：**纯 JSON**（不是 multipart），
     * 图片以 base64 内联，另外带 `width`/`height` 与 `use_new_shared_trial`。
     *
     * @return ZIP 里的 (条目名 → 图片字节)；移除背景会有 3 条（masked / generated / blend）
     */
    suspend fun directorTool(
        token: String,
        reqType: String,
        imagePng: ByteArray,
        width: Int,
        height: Int,
        prompt: String? = null,
        defry: Int? = null,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): List<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}${DirectorTools.PATH}"
        val payload = DirectorTools.requestJson(
            reqType = reqType,
            width = width,
            height = height,
            base64Image = Base64.encodeToString(imagePng, Base64.NO_WRAP),
            prompt = prompt,
            defry = defry,
        )
        Log.i(TAG, "director: POST $url req_type=$reqType ${width}x$height image=${imagePng.size / 1024}KB")
        val started = System.currentTimeMillis()
        try {
            val bytes = postWithRetry(token, url, payload.toString())
            val images = extractImagesNamed(bytes)
            Log.i(
                TAG,
                "director: ok ${images.size} image(s) in ${System.currentTimeMillis() - started}ms",
            )
            images
        } catch (e: Exception) {
            Log.w(TAG, "director failed after ${System.currentTimeMillis() - started}ms: ${e.message}")
            throw e
        }
    }

    /** 响应可能是 ZIP（多图）也可能是裸 PNG。 */
    private fun extractImages(bytes: ByteArray): List<ByteArray> =
        extractImagesNamed(bytes).map { it.second }

    /**
     * 同上，但**连 ZIP 条目名一起返回**。
     * 导演工具的「移除背景」一次回 3 张（`masked` / `generated` / `blend`），
     * 要按名字挑"混合结果"（插件与官方客户端都把 blend 当预览），所以名字不能丢。
     */
    private fun extractImagesNamed(bytes: ByteArray): List<Pair<String, ByteArray>> {
        // ⚠️ **第三方网关兼容（2026-09-26）**：非 2xx 的响应体拿不到（OkHttp 那边只有
        // `response.body` 一条路，见 `errorText`），但我们**保证请求头带 `Accept: */*`**、
        // 且网关自己也不回裸 HTML —— 所以能进到这里的一定是图片或 ZIP。
        // 真要是收到一段文本（比如网关把 HTML 错误页怼了回来），这里会解不出图、
        // 由下面的 `extractImages` 抛"没有可用图片" —— 不至于静默出个空图。
        if (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val images = mutableListOf<Pair<String, ByteArray>>()
            try {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val data = zip.readBytes()
                            if (data.isNotEmpty()) images.add((entry.name ?: "") to data)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (e: IOException) {
                throw NaiHttpException(0, "解压 NovelAI 返回的压缩包失败：${e.message ?: "数据损坏"}")
            }
            if (images.isNotEmpty()) return images
        }
        if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
            return listOf("image.png" to bytes)
        }
        throw NaiHttpException(0, "NovelAI 返回的数据里没有可用图片。")
    }

    private fun errorText(code: Int, body: ByteArray): String {
        val text = String(body, Charsets.UTF_8)
        // ⚠️ **第三方网关兼容（2026-09-26）**：NAI Gate 的错误体是
        // `{"error":{"message":"…","status":429}}`（见它的 `gate_error_handler`），
        // 跟官方的 `{"message":…}` **不是同一个形状** ⇒ 只读顶层 `message` 会读成一片空白，
        // 用户看到的就是"请求失败（429）："后面什么都没有。两层都认。
        val message = try {
            val root = JSONObject(text)
            root.optString("message").takeIf { it.isNotEmpty() }
                ?: root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
        return when (code) {
            401 -> message?.let { "请求失败（401）：$it" } ?: "Token 无效或已失效（401），请重新获取并填写。"
            402 -> message?.let { "请求失败（402）：$it" } ?: "账户没有有效订阅或 Anlas 不足（402）。"
            // 网关那边 429 是**全站冷却**（不是"你手速太快"），说清楚免得用户连点重试
            429 -> message?.let { "请求过于频繁（429）：$it" } ?: "请求过于频繁（429）。"
            else -> if (message != null) {
                "请求失败（$code）：$message"
            } else {
                "请求失败（HTTP $code）：${text.take(300)}"
            }
        }
    }

    private fun normalizeBase(value: String, fallback: String): String {
        val candidate = value.trim().ifEmpty { fallback }
        return candidate.trimEnd('/')
    }

    // =======================================================================
    // 账号
    // =======================================================================

    /**
     * 校验 Token。成功返回账号摘要；401 抛异常。
     *
     * 说明（参考实现里的原话）：官方网页版 image 客户端用的就是 `/user/data`；
     * `api.novelai.net` 上的同类路由对部分账号会返回 400，提示第三方工具"改用 image 地址"。
     */
    suspend fun verifyToken(
        token: String,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): AccountSummary = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext AccountSummary(hasToken = false)

        val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/user/data"
        val text = getWithRetry(token, url)
        parseAccount(text)
    }

    /** 读取账号信息；失败时返回 [AccountSummary.stale] = true 的兜底值，不抛异常。 */
    suspend fun fetchAccount(
        token: String,
        imageBaseUrl: String = DEFAULT_IMAGE_BASE,
    ): AccountSummary = withContext(Dispatchers.IO) {
        try {
            if (token.isBlank()) return@withContext AccountSummary(hasToken = false)
            val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/user/data"
            parseAccount(getWithRetry(token, url))
        } catch (e: Exception) {
            AccountSummary(hasToken = true, tierName = "Verified", stale = true)
        }
    }

    private fun getWithRetry(token: String, url: String, attempts: Int = 3): String {
        var lastError: Exception? = null
        for (attempt in 0 until attempts) {
            if (attempt > 0) Thread.sleep(1500L * attempt)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/json")
                .header("User-Agent", "NAIStudio-Android/$APP_VERSION")
                .get()
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 401) {
                        throw NaiHttpException(401, "Token 无效或已失效（401）。")
                    }
                    if (response.code >= 400) {
                        throw NaiHttpException(response.code, "校验 Token 失败（HTTP ${response.code}）。")
                    }
                    return text
                }
            } catch (e: NaiHttpException) {
                if (e.statusCode == 401) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw NaiNetworkException(lastError ?: IOException("未知网络错误"))
    }

    private fun parseAccount(body: String): AccountSummary {
        val root = JSONObject(body)

        fun mapOf(key: String): JSONObject? = root.optJSONObject(key)
        val directData = mapOf("data")
        val information = mapOf("information")
        val nestedInformation = directData?.optJSONObject("information")

        val subscription = mapOf("subscription")
            ?: information?.optJSONObject("subscription")
            ?: directData?.optJSONObject("subscription")
            ?: nestedInformation?.optJSONObject("subscription")
            ?: JSONObject()

        val tier = if (subscription.has("tier")) subscription.optInt("tier") else null

        var anlas: Int? = null
        val steps = subscription.opt("trainingStepsLeft")
        when (steps) {
            is JSONObject -> anlas = steps.optInt("fixedTrainingStepsLeft", 0) +
                steps.optInt("purchasedTrainingSteps", 0)
            is Number -> anlas = steps.toInt()
        }

        var opusUsage: OpusGenerationUsage? = null
        subscription.optJSONObject("usage")?.let { usage ->
            if (usage.has("percent") && usage.has("timeUntilNextPercent")) {
                opusUsage = OpusGenerationUsage(
                    percent = usage.optDouble("percent", 0.0),
                    isNegative = usage.optBoolean("isNegative", false),
                    timeUntilNextPercent = maxOf(0.0, usage.optDouble("timeUntilNextPercent", 0.0)),
                )
            }
        }

        return AccountSummary(
            hasToken = true,
            tierName = tierName(tier),
            tierLevel = tier,
            anlasBalance = anlas,
            hasActiveSubscription = if (subscription.has("active")) subscription.optBoolean("active") else null,
            opusUsage = opusUsage,
        )
    }

    private fun tierName(tier: Int?): String = when (tier) {
        3 -> "Opus"
        2 -> "Scroll"
        1 -> "Tablet"
        0 -> "Paper"
        else -> "Verified"
    }

    // =======================================================================
    // 第三方网关兼容（用户 2026-09-26：https://github.com/fangchen2003/service-tools）
    // =======================================================================
    //
    // 对方是 **NAI Gate**：自托管的 NovelAI 转发网关，自己在服务端配上游 Token、
    // 给用户发 `nai-...` 虚拟 Key。对客户端而言它**就是**一个"能改服务器地址的 NovelAI"，
    // 所以"支持第三方 API"这件事**不需要另做一套协议** —— 用户把站点根地址填进
    // 「API 地址」那一栏、把虚拟 Key 当 token 填进来就行。
    //
    // 需要额外做的只有下面这四件事，其余照旧走官方那套 `{base}/ai/...` 路径：
    //  ① 图生图 / 局部重绘按服务器权限开放，客户端不预先拦截；
    //  ② 它的 429 是**全站冷却**（图片生成整体暂停 60 秒起）⇒ 不要自动重试，
    //     要告诉用户"这是全站限流，等一会儿"，不然三连重试只会白烧三次；
    //  ③ 余额不在官方那个 `usage` 里 ⇒ 数值其实**不用改**：它的
    //     `/user/data` 就是官方同名接口的形状（`trainingStepsLeft.fixedTrainingStepsLeft`），
    //     现成的 `parseAccount` 直接读得出来；只有"V5 每日还剩多少"是它独有的，
    //     藏在 `/user/subscription` 的 `naiGate` 字段里，[fetchGatewayQuota] 去取；
    //  ④ 它的错误体是 `{"error":{"message":..,"status":..}}` ⇒ [errorText] 认这一种。

    /** 这条路是不是"自托管网关"（而不是 NovelAI 官方）。判据只有"地址不是官方"这一条。 */
    fun isThirdPartyBase(imageBaseUrl: String): Boolean {
        val normalized = normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)
        return normalized != normalizeBase(DEFAULT_IMAGE_BASE, DEFAULT_IMAGE_BASE)
    }

    /**
     * **网关判定的唯一口径** —— `AppState.usingGateway` 和界面都用它。
     *
     * ⚠️ 注意"**空 = 官方**"这一条：设置里地址从来没填过时它就是空串，那时
     * `normalizeBase` 会回落到官方默认地址（见 [normalizeBase]）—— 两边口径必须一致。
     */
    fun isGatewayBase(imageBaseUrl: String?): Boolean =
        !imageBaseUrl.isNullOrBlank() && isThirdPartyBase(imageBaseUrl)

    /**
     * **网关配额**（NAI Gate 独有）：V5 今天还剩几张、Anlas 月度还剩多少。
     *
     * 走 `GET {base}/user/subscription`，只读它独有的 `naiGate` 那一块；
     * 拿不到 / 不是网关（没有 `naiGate` 字段）⇒ 返回 null，界面那边按"不显示"处理。
     *
     * ⚠️ 单独开一个请求是**故意的**：官方账号那边一次 `fetchAccount` 就够，
     *    网关这边多一次很小的 GET，且只在用户真的用了第三方地址时才发。
     */
    suspend fun fetchGatewayQuota(
        token: String,
        imageBaseUrl: String,
    ): GatewayQuota? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${normalizeBase(imageBaseUrl, DEFAULT_IMAGE_BASE)}/user/subscription"
            val root = JSONObject(getWithRetry(token, url, attempts = 1))
            val gate = root.optJSONObject("naiGate") ?: return@runCatching null
            GatewayQuota(
                v5LeftToday = gate.optInt("v5LeftToday", 0),
                v5DailyLimit = gate.optInt("v5DailyLimit", 0),
                v5Unlimited = gate.optBoolean("v5Unlimited", false),
                anlasEnabled = gate.optBoolean("anlasEnabled", false),
                anlasLeft = gate.optInt("anlasLeft", 0),
                anlasMonthlyLimit = gate.optInt("anlasMonthlyLimit", 0),
                imageModelScope = gate.optString("imageModelScope", ""),
            )
        }.getOrNull()
    }

    /** 429 从网关过来时给一句说得通的提示（它那边是全站冷却，不是"你手速太快"）。 */
    fun gatewayRateLimitMessage(): String =
        "网关正在全站限流（上游 429 冷却中），请等 1 分钟再试。"

    private companion object {
        const val APP_VERSION = "0.1.0"
        const val TAG = "NaiApi"

        /** 参考实现里用来判断"提示词是否已经带了 furry 数据集标签"的正则。 */
        val FURRY_DATASET = Regex("(?:^|,\\s*)fur dataset(?:\\s*,|$)", RegexOption.IGNORE_CASE)

        /**
         * 漫画模式风格词现在是 `extras.comicStyle` 字段（"按剧情分镜"由 LLM 输出、
         * 用户也能手改），兜底默认值见 [com.kallan.naistudio.models.COMIC_DEFAULT_STYLE]。
         */


        /** 报价响应里可能出现的价格字段名（尽力解析，见 requestOfficialGenerationPrice）。 */
        val ANLAS_PRICE_KEYS = listOf("anlas", "price", "cost", "amount", "requestPrice", "totalAnlas")

        /** 这些状态码意味着"这个端点/格式不支持"，应回退到非流式，而不是报错。 */
        val STREAM_UNSUPPORTED_CODES = setOf(404, 405, 415, 501)

        /** 服务端明确拒绝流式的报文特征（小写匹配）。 */
        val STREAM_UNAVAILABLE_HINTS = listOf(
            "streaming is not allowed",
            "streaming not allowed",
            "stream is not allowed",
            "stream not allowed",
        )

        /**
         * 服务端明确"不支持这个重绘模型"的报文特征（小写匹配），照参考实现的正则口径。
         * 只有命中了才允许换下一个候选模型——否则会把 402/401 这种真实错误糊掉。
         */
        val UNSUPPORTED_INPAINT_HINTS = listOf(
            "doesn't support",
            "does not support",
            "not support",
            "unsupported",
            "invalid model",
            "action infill",
        )
    }
}
