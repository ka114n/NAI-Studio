package com.kallan.naistudio.models

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * **笔刷引擎**（纯逻辑 ✓）—— 逐函数照 `docs/brush-lab-simple.html` 搬过来的那一份 ✓。
 *
 * ## 一句话口径
 *
 * 网页里那些函数（`nibRadius()` / `dabStep()` / `stamp()` / `walkTo()` / `sampleAvg()` /
 * `texturedDab()` / `rebuildTile()` / `hash2()` / `vnoise()` / `hexToHsl()` / `hslToRgb()` ✓）
 * **一个公式都没有自己发明** ✗ —— 这个文件就是它们 1:1 的 Kotlin 版 ✓
 *（遍历到哪个函数，KDoc 里就标着网页的哪一个 ✓）。
 * **走位**（`walkTo` 那半）留在 `ImageEditSession.strokeTo`（那边早就有 carry 累加器了 ✓）；
 * 这里管的是"**一颗笔尖怎么盖**"（`stamp` 那半 ✓）。
 *
 * ## 和网页渲染管线**必然**不一样的两处（如实说 ✓，不假装逐像素一致 ✓）
 *
 *  1. **不经过 Canvas 2D** ✗：网页是 `ctx.ellipse(...).fill()` + `createPattern` + 三次
 *     `globalCompositeOperation`（`destination-in` 剪形状 / `source-in` 上色 ✓）；
 *     这边是**解析式逐像素**（`ImageEditOps.stampDab` 算覆盖度 ✓）——
 *     公式一致、抗锯齿的**采样细节不可能逐位相同** ✓；
 *  2. **随机数不是 `Math.random()`** ✗：这边一律走 `kotlin.random.Random(seed)` ✓
 *     ⇒ **同一 seed 跑两次逐像素一致** ✓（测试就靠这条做确定性断言 ✓）。
 *
 * ## 性能（如实说 ⚠️）
 *
 * 每颗笔尖最多一次 `MAX_SAMPLE_PX×2` 的**小方块**采样（绝不是整张画布 ✗）+
 * 纸纹只多一次数组查表 ✓。真正贵的是 `count`（一档 N 颗）+ `blending`（每颗读一小块 ✓）——
 * 大笔刷（>200px）+ 高 count + 混色的组合**没在真机上测过** ⚠️（见回报 ✓）。
 */
object BrushEngine {

    /** 纸纹 / 噪点贴图尺寸（2 的幂、够平铺 ✓，照网页 `TILE = 128` ✓）。 */
    const val TILE = 128

    /** 混色采样区域的**半宽硬上限**（像素 ✓）—— **绝不全画布读** ✗（照网页 `MAX_SAMPLE_PX` ✓）。 */
    const val MAX_SAMPLE_PX = 64f

    /** 笔尖半径下限（像素 ✓，照网页那一堆 `Math.max(0.35, …)` ✓）。 */
    const val DAB_MIN_RADIUS = 0.35f

    // ---- 网页里那些"魔法数字"，全部原样搬过来 ✓（改任何一个 = 改手感 ⚠️）----

    /** 扩散和噪点：**半径**抖动幅度 ×强度 ✓（网页 `1 + spread * 0.4 * rnd(-1,1)` ✓）。 */
    const val SPREAD_RADIUS_JITTER = 0.4f

    /** 扩散和噪点：**位置**抖动幅度 ×强度 ✓（网页 `(rand - 0.5) * r * spread * 0.6` ✓）。 */
    const val SPREAD_POSITION_JITTER = 0.6f

    /** 扩散和噪点：**alpha** 抖动幅度 ×强度 ✓（网页 `1 - spread * 0.45 * rand` ✓）。 */
    const val SPREAD_ALPHA_JITTER = 0.45f

    /** 水分量把 alpha 压淡的幅度 ✓（网页 `1 - 0.55 * waterF` ✓）。 */
    const val WATER_ALPHA_FALLOFF = 0.55f

    /** 混色**留 15% 本色**那一条 ✓（网页 `* 0.85` ✓）。 */
    const val MIX_KEEP_SELF = 0.85f

    /** 水分量额外加权混色的系数 ✓（网页 `0.25 * waterF` ✓）。 */
    const val MIX_WATER_WEIGHT = 0.25f

    /** 采样半宽基数 ✓（网页 `r * (0.8 + 1.0 * waterF)` ✓）。 */
    const val SAMPLE_HALF_BASE = 0.8f

    /** 采样半宽随水分量的放大系数 ✓（同上 ✓）。 */
    const val SAMPLE_HALF_WATER = 1.0f

    /** 色延伸把采样点往后拖的距离 ×半径 ✓（网页 `r * (colorStretch/100) * 2.0` ✓）。 */
    const val STRETCH_DRAG = 2.0f

    /** 色延伸把"上一颗笔尖的混色结果"带过来的比例 ✓（网页 `0.5 * (colorStretch/100)` ✓）。 */
    const val STRETCH_CARRY = 0.5f

    /** 散布幅度上限（相对笔尖半径 ✓，网页 `clamp(mag, 0, 1.6)` ✓）。 */
    const val SCATTER_MAX_MAG = 1.6f

    /** 高斯散布的除数 ✓（网页 `Math.abs(gauss()) / 2.2` ✓）。 */
    const val GAUSS_DIVISOR = 2.2f

    /** 贴图生成用的固定 seed ✓ —— **纸纹在任何一笔里都长一个样** ✓（不然每笔换一张纸 ✗）。 */
    const val TILE_SEED = 0x5A17L

    /**
     * **与网页 `Math.random` 同位同序的确定性 PRNG**（mulberry32 ✓，第 ㉜c 批 ✓）。
     *
     * 为什么要专门写一个（`kotlin.random.Random` 明明也能出随机数 ✗）：
     * **纸纹那一层细颗粒是网页用 `Math.random()` 生的** ✓（`brush-lab-simple.html:257` ✓）——
     * 要拿网页当真值逐像素比，两边那张 128×128 贴图就得**逐字节相同** ✗，
     * 而 `kotlin.random.Random` 的序列与 V8 的 `Math.random` 毫无关系 ✓。
     * ⇒ 抽真值时把网页的 `Math.random` **临时换成同一个 mulberry32**（`tools/webview-spike` 的
     * `paper` 模式 ✓），两边于是吃到**同一串 `[0,1)`** ✓、贴图逐字节一致 ✓
     * （`PaperBrushParityTest` 里那条"贴图逐字节相同"就是钉它的 ✓）。
     *
     * 逐位口径（照 V8/JS 语义 ✓，**别改** ✗）：
     *  · `a = a + 0x6D2B79F5 | 0` ⇒ Kotlin 的 `Int` 溢出回绕 ✓；
     *  · `Math.imul(x, y)` = 32 位乘法回绕 ⇒ Kotlin `Int * Int` ✓；
     *  · `t = t + Math.imul(t ^ t >>> 7, 61) ^ t` 的优先级 = `((t + imul(...)) ^ t)` ⚠️；
     *  · 最后 `>>> 0 / 4294967296` ⇒ 当无符号看再除 ✓。
     */
    class Mulberry32(seed: Int) {
        private var a: Int = seed

        /** 下一个 `[0, 1)` ✓（与网页 `mulberry32()` 返回的那个 double 逐位相同 ✓）。 */
        fun nextFloat(): Float = (nextDouble()).toFloat()

        /** 下一个 `[0, 1)` ✓（**double 精度** —— 贴图细颗粒要的就是它 ✓）。 */
        fun nextDouble(): Double {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (1 or a)
            t = (t + (t xor (t ushr 7)) * 61) xor t
            val u = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
            return u / 4294967296.0
        }
    }

    /** 网页那一层细颗粒 ✓（`(Math.random() * 256) | 0` 逐位 ✓ = Kotlin 截断 ✓）。 */
    fun mulberryGrain(seed: Int = TILE_SEED.toInt()): ByteArray {
        val rnd = Mulberry32(seed)
        val grain = ByteArray(TILE * TILE)
        for (i in grain.indices) grain[i] = (rnd.nextDouble() * 256.0).toInt().toByte()
        return grain
    }

    /**
     * **相邻笔尖的采样点相距多少像素以内就直接复用上一次的结果**（第 ㉔ 批 ✓）。
     *
     * 为什么安全：这一段距离远小于采样窗口（最小 `2 × 1px = 2px` ✓，典型 `2r × 0.8` 有几十像素 ✓），
     * 窗口里那份"平均色"在这点位移下**看不出差别** ✓；而"笔尖走得很密"时（小笔刷 / 间距调小 ✓），
     * 采样次数能从"每颗一次"压到"几颗一次" ✓。
     *
     * ⚠️ **如实的边界** ✗：默认档（φ45 px 笔刷 + `spacing = 10%`）两颗笔尖差 **4.5 px** > 2 px
     * ⇒ **够不着**，那一档采不到省 ✓ —— 大笔刷真正的省法是"`blending / water / colorStretch`
     * 全 0 时**一次都不采**"（默认笔刷就是它 ✓）。实测数字见第 ㉔ 批回报 ✓。
     *
     * ⚠️ 它**不改公式** ✓：只是"要不要真的去扫那一块像素"的取舍 ✓，
     * 而且完全由坐标决定（没有随机）⇒ **同一个 seed 仍然逐位可复现** ✓。
     */
    const val SAMPLE_REUSE_PX = 2f

    // -----------------------------------------------------------------------
    // ① 小工具函数（网页 `clamp` / `rnd` / `gauss` / `hash2` / `vnoise` ✓）
    // -----------------------------------------------------------------------

    /** `rnd(a, b)`：`[a, b)` 上的均匀随机 ✓（网页 `a + Math.random() * (b - a)` ✓）。 */
    fun randomBetween(random: Random, a: Float, b: Float): Float = a + random.nextFloat() * (b - a)

    /**
     * 标准正态分布的一个样本 ✓（网页 `gauss()` 的 Box–Muller ✓）。
     *
     * ⚠️ 两个 `nextFloat()` 都可能是 0 —— 网页用 `while (!u)` 挡掉 ✓，这边照做 ✓
     *（`log(0) = -∞` 会让散布坐标变 NaN ✗，一 NaN 就是"这颗笔尖凭空消失" ✗）。
     */
    fun gaussian(random: Random): Float {
        var u = 0f
        while (u == 0f) u = random.nextFloat()
        var v = 0f
        while (v == 0f) v = random.nextFloat()
        return sqrt(-2f * ln(u)) * cos(2f * PI.toFloat() * v)
    }

    /**
     * 整数哈希 → `[0, 1)` ✓（网页 `hash2()` **逐位**搬过来 ✓，含 `>>> 0` 那个"当无符号看"✓）。
     *
     * 为什么要自己写一个：价值噪声的格点值必须是**坐标的纯函数** ✓（同一坐标永远同一个值 ✓）——
     * 用 `Random` 就不行了 ✗（顺序一变整张贴图就变了 ✗）。
     */
    fun hash2(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n shr 13)) * 1274126177
        return ((n xor (n shr 16)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967295f
    }

    /**
     * **无缝** value noise ✓（网页 `vnoise()` ✓）：坐标归一化到 `0..1`、`freq` 个格点**环绕** ✓
     * ⇒ 平铺时接缝处对得上 ✓（纸纹要平铺，接缝一眼就能看出来 ✗）。
     */
    fun vnoise(u: Float, v: Float, freq: Int): Float {
        val f = if (freq < 1) 1 else freq
        val x = u * f
        val y = v * f
        val xi = floor(x.toDouble()).toInt()
        val yi = floor(y.toDouble()).toInt()
        val xf = x - xi
        val yf = y - yi
        val sx = xf * xf * (3f - 2f * xf)
        val sy = yf * yf * (3f - 2f * yf)
        fun wrap(i: Int): Int = ((i % f) + f) % f
        val a = hash2(wrap(xi), wrap(yi))
        val b = hash2(wrap(xi + 1), wrap(yi))
        val c = hash2(wrap(xi), wrap(yi + 1))
        val d = hash2(wrap(xi + 1), wrap(yi + 1))
        return (a * (1f - sx) + b * sx) * (1f - sy) + (c * (1f - sx) + d * sx) * sy
    }

    // -----------------------------------------------------------------------
    // ② 纸纹 / 噪点贴图（网页 `rebuildTile()` ✓）
    // -----------------------------------------------------------------------

    /**
     * 生成 [TILE]×[TILE] 的 alpha 贴图 ✓（灰度存成 `ByteArray`，`0..255` *不是* `0..1` ✓ ——
     * 后面 [textureAt] 再除回去 ✓，省一半内存也够准 ✓）。
     *
     * 两个乘数**照网页** ✓：
     *  · **画用纸** `ps = (paperStrength/100) × 0.85`：3 个八度的平滑噪声（纤维 ✓ `4 / 9 / 21` 个格点 ✓）
     *    再掺 15% 细颗粒 ✓；
     *  · **扩散和噪点** `ss = (spreadNoiseStrength/100) × 0.9`：把细颗粒**平方**后乘上去 ✓
     *    （平方 ⇒ 大部分地方接近 1、只有少数点被压深 ✓ = "噪点" ✓）。
     *
     * ⚠️ [random] 决定那一层细颗粒 ✓ —— 传固定 seed 就是**同一张纸** ✓（见 [TILE_SEED] ✓）；
     * 两个乘数都是 0 时返回**全 255**（= 乘 1 = 不影响 ✓），调用方靠 [BrushSpec.needsTexture] 先挡一道 ✓。
     */
    fun buildTile(brush: BrushSpec, random: Random): ByteArray {
        val grain = ByteArray(TILE * TILE)
        random.nextBytes(grain)
        return buildTileFromGrain(brush, grain)
    }

    /**
     * 同上，但**细颗粒由调用方给** ✓（第 ㉜c 批 ✓）。
     *
     * 干嘛要这个口子：纸纹要跟网页**逐字节**对（见 [Mulberry32] ✓）——
     * 生产链路喂 [mulberryGrain]（= 网页那张纸 ✓），
     * 单测则可以直接喂**网页交回来的那一层细颗粒** ✓（`PaperBrushParityTest` ✓）。
     */
    fun buildTileFromGrain(brush: BrushSpec, grain: ByteArray): ByteArray {
        val ps = if (brush.paperOn) (brush.paperStrength / 100f) * 0.85f else 0f
        val ss = if (brush.spreadNoise) (brush.spreadNoiseStrength / 100f) * 0.9f else 0f
        val out = ByteArray(TILE * TILE)
        for (y in 0 until TILE) {
            for (x in 0 until TILE) {
                val u = x.toFloat() / TILE
                val v = y.toFloat() / TILE
                var tex = 1f
                if (ps > 0f) {
                    val pn = vnoise(u, v, 4) * 0.55f + vnoise(u, v, 9) * 0.3f + vnoise(u, v, 21) * 0.15f
                    val fine = (grain[y * TILE + x].toInt() and 0xFF) / 255f
                    val paper = (pn * 0.85f + fine * 0.15f).coerceIn(0f, 1f)
                    tex *= 1f - ps * (1f - paper)
                }
                if (ss > 0f) {
                    // 网页是 `grain[((y*7+13)*TILE + (x*11+5)) % (TILE*TILE)]` —— 同一个粒子里"跳着取" ✓
                    //（常数都非负 ⇒ Kotlin 的 `%` 与 JS 的 `%` 同结果 ✓）
                    val index = ((y * 7 + 13) * TILE + (x * 11 + 5)) % (TILE * TILE)
                    val sn = (grain[index].toInt() and 0xFF) / 255f
                    tex *= 1f - ss * sn * sn
                }
                out[y * TILE + x] = (tex.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /**
     * **贴图按画布坐标对齐**（像素 ✓）—— 网页 `texturedDab()` 里那两行 `tx / ty` 求的就是这件事 ✓：
     * 小画布上第 `(i, j)` 个像素对应画布坐标 `(ox + i, oy + j)`，而图案是 `translate(tx, ty)` 之后铺的
     * ⇒ 它取到的格子正好是 `(画布 x mod TILE, 画布 y mod TILE)` ✓。
     *
     * 也就是说：**纸纹钉在画布上、不跟着笔尖跑** ✓ —— 这才是"画在纸上"的样子 ✓
     *（跟着笔尖跑 = 每一笔都带着同一块花纹走，一眼假 ✗）。
     */
    fun textureAt(tile: ByteArray, canvasX: Int, canvasY: Int): Float {
        if (tile.size < TILE * TILE) return 1f
        val tx = ((canvasX % TILE) + TILE) % TILE
        val ty = ((canvasY % TILE) + TILE) % TILE
        return (tile[ty * TILE + tx].toInt() and 0xFF) / 255f
    }

    // -----------------------------------------------------------------------
    // ③ 颜色：网页 `hexToHsl()` / `hslToRgb()` ✓（**HSL**，不是 `ImageEditOps` 那套 HSV ✗）
    // -----------------------------------------------------------------------

    /** ARGB → `[h(0..360), s(0..1), l(0..1)]` ✓（网页 `hexToHsl()` ✓，只是这边直接吃打包整数 ✓）。 */
    fun hslOf(argb: Int): FloatArray {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = kotlin.math.min(r, kotlin.math.min(g, b))
        val d = mx - mn
        var h = 0f
        if (d > 0f) {
            h = when (mx) {
                r -> (((g - b) / d) % 6f)
                g -> ((b - r) / d) + 2f
                else -> ((r - g) / d) + 4f
            } * 60f
            if (h < 0f) h += 360f
        }
        val l = (mx + mn) / 2f
        // ⚠️ 网页那句 `d / (1 - Math.abs(2 * l - 1))` 在 l = 0 / 1 时是 0/0（NaN ✗）——
        //    这边给出 0（无彩）✓，比把 NaN 带进颜色里强 ✓（纯黑 / 纯白的饱和度本来就无所谓 ✓）。
        val denom = 1f - abs(2f * l - 1f)
        val s = if (d <= 0f || denom <= 1e-6f) 0f else (d / denom).coerceIn(0f, 1f)
        return floatArrayOf(h, s, l)
    }

    /** `[h, s, l]` → `[r, g, b]`（0..255 ✓）—— 网页 `hslToRgb()` ✓。 */
    fun rgbOfHsl(hue: Float, saturation: Float, lightness: Float): IntArray {
        val s = if (saturation.isFinite()) saturation.coerceIn(0f, 1f) else 0f
        val l = if (lightness.isFinite()) lightness.coerceIn(0f, 1f) else 0f
        val h = (((if (hue.isFinite()) hue else 0f) % 360f) + 360f) % 360f / 360f
        if (s <= 0f) {
            val value = (l * 255f).roundToInt().coerceIn(0, 255)
            return intArrayOf(value, value, value)
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun channel(t0: Float): Int {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            val value = when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
            return (value * 255f).roundToInt().coerceIn(0, 255)
        }
        return intArrayOf(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f))
    }

    // -----------------------------------------------------------------------
    // ④ 一颗笔尖的半径 / 朝向 / 计数 / 散布（网页 `nibRadius` / `stamp` 的前半 ✓）
    // -----------------------------------------------------------------------

    /** 扩散和噪点的**强度系数**（0 = 关 ✓；照网页 `spread = strength / 100` ✓）。 */
    fun spreadAmount(brush: BrushSpec): Float =
        if (brush.spreadNoise && brush.spreadNoiseStrength > 0f) brush.spreadNoiseStrength / 100f else 0f

    /**
     * **这一颗笔尖的半径**（像素 ✓）—— 网页 `nibRadius()` 里 `× jit` 那一步 + `spread` 那一步 ✓。
     *
     * ⚠️ `Scaling` / `AbsoluteSize` / **压感**这三步**不在**这里 ✗ ——
     * 它们在 `ImageEditSession.nibRadiusAt`（那是"基准半径从哪来"，本仓库早就有的一处口径 ✓）。
     * 传进来的 [baseRadius] 已经是"压感之后、Scaling / AbsoluteSize 之后"的那个数 ✓，
     * 于是这里只剩两件事：
     *  · `SizeJitter`：`× (1 ± 百分比)` ✓；
     *  · 扩散和噪点：再 `× (1 ± 0.4 × 强度)` ✓（网页是在 `nibRadius()` **之后**乘的 ✓）。
     */
    fun jitterNibRadius(baseRadius: Float, brush: BrushSpec, random: Random): Float {
        val base = if (baseRadius.isFinite()) baseRadius.coerceAtLeast(0f) else 0f
        val jit = 1f + (brush.sizeJitter / 100f) * randomBetween(random, -1f, 1f)
        var r = base * jit
        val spread = spreadAmount(brush)
        if (spread > 0f) r *= 1f + spread * SPREAD_RADIUS_JITTER * randomBetween(random, -1f, 1f)
        return r.coerceAtLeast(DAB_MIN_RADIUS)
    }

    /**
     * **笔尖朝向**（弧度 ✓）—— 网页 `stamp()` 里 `ang` 的前两项 ✓：
     * `angleControl = 1`（自动）跟**笔画方向** ✓、`= 0`（固定）用 [BrushSpec.angle] ✓。
     *
     * ⚠️ **不含角度抖动** ✗ —— 抖动那颗 `rnd(-π, π)` 是给 `stamp()` 用的 ✓；
     * 护栏（[ImageEditOps.dabStepPixels]）要的是**不带抖动的朝向** ✓（照网页 `dabStep()` ✓）。
     */
    fun nibAngleRadians(brush: BrushSpec, directionRadians: Float): Float {
        val base = if (brush.angleControl == NibAngleControl.AUTO) {
            if (directionRadians.isFinite()) directionRadians else 0f
        } else {
            0f
        }
        val offset = if (brush.angle.isFinite()) brush.angle else 0f
        return base + offset * (PI.toFloat() / 180f)
    }

    /** **一档落几颗**（网页 `Math.max(1, Math.round(Count * (1 + CountJitter/100 * rnd(-1,1))))` ✓）。 */
    fun dabCount(brush: BrushSpec, random: Random): Int {
        val base = brush.count.coerceIn(1, 20)
        val jitter = (brush.countJitter / 100f) * randomBetween(random, -1f, 1f)
        return max(1, (base * (1f + jitter)).roundToInt())
    }

    /**
     * **散布偏移**（像素 ✓）—— 网页 `stamp()` 里算 `ox / oy` 那一段 ✓（**纯函数** ✓，单测直接钉 ✓）。
     *
     *  · 方向：`allDirScattering = 1` 全方向 ✓；`= 0` **只往与笔画直交的方向**（两侧各一半 ✓）✓；
     *  · 幅度：均匀 → `rand`；`gaussianDistribution = 1` → `|gauss()| / 2.2` ✓（**越远越稀** ✓）；
     *  · 再夹到 `1.6 × scatterAmp` ✓。
     *
     * @return `[ox, oy]`（`scattering = 0` 时是 `[0, 0]` ✓）
     */
    fun scatterOffset(brush: BrushSpec, directionRadians: Float, radius: Float, random: Random): FloatArray {
        val amp = (if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f) * (brush.scattering / 100f)
        if (amp <= 0f) return floatArrayOf(0f, 0f)
        val dir = if (directionRadians.isFinite()) directionRadians else 0f
        val angle: Float
        var mag: Float
        if (brush.allDirScattering == 1) {
            angle = random.nextFloat() * 2f * PI.toFloat()
            mag = random.nextFloat()
        } else {
            angle = dir + PI.toFloat() / 2f + (if (random.nextBoolean()) 0f else PI.toFloat())
            mag = random.nextFloat()
        }
        if (brush.gaussianDistribution == 1) mag = abs(gaussian(random)) / GAUSS_DIVISOR
        mag = mag.coerceIn(0f, SCATTER_MAX_MAG) * amp
        return floatArrayOf(cos(angle) * mag, sin(angle) * mag)
    }

    // -----------------------------------------------------------------------
    // ⑤ alpha（网页 `stamp()` 里那段"工具层：浓度 / 水分量" ✓）
    // -----------------------------------------------------------------------

    /**
     * **这一颗笔尖的 alpha**（0..1 ✓）—— 网页 `stamp()` 第 419~425 行 ✓。
     *
     * `opacity/100 × density/100` →（`pressureAlpha` 时）乘压感下限 ✓ → 水分量压淡 ✓ →
     * 扩散噪点抖动 ✓ → `keepOpacity && !erasing` 时**直接 1** ✓。
     */
    fun dabAlpha(
        brush: BrushSpec,
        pressure: Float,
        pressureCurve: Float,
        random: Random,
        erasing: Boolean,
    ): Float {
        var alpha = (brush.opacity / 100f) * (brush.density / 100f)
        if (brush.pressureAlpha) {
            val p = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
            val curve = if (pressureCurve.isFinite() && pressureCurve > 0f) pressureCurve else 1f
            val floor = (brush.minDensity / 100f).coerceIn(0f, 1f)
            alpha *= floor + (1f - floor) * p.pow(curve)
        }
        alpha *= 1f - WATER_ALPHA_FALLOFF * (brush.water / 100f).coerceIn(0f, 1f)
        val spread = spreadAmount(brush)
        if (spread > 0f) alpha *= 1f - spread * SPREAD_ALPHA_JITTER * random.nextFloat()
        if (brush.keepOpacity && !erasing) alpha = 1f
        return alpha.coerceIn(0f, 1f)
    }

    /** **混色比例**（网页 `mixT = clamp(blending/100 + 0.25 × waterF, 0, 1) × 0.85` ✓）。 */
    fun mixRatio(brush: BrushSpec): Float {
        val waterF = (brush.water / 100f).coerceIn(0f, 1f)
        return ((brush.blending / 100f) + MIX_WATER_WEIGHT * waterF).coerceIn(0f, 1f) * MIX_KEEP_SELF
    }

    /**
     * **keepOpacity 抬笔时一次性合成的 alpha** ✓（= 这一笔的"浓度" ✓）。
     *
     * `opacity/100 × density/100 × (1 − 0.55 × 水分/100)` ✓ ——
     * 与单颗笔尖的 alpha 同一条公式（只是**不含**压感→浓度与扩散抖动那两条 ✓：
     * 整笔一个数才叫"保持不透明度" ✓）。
     */
    fun strokeCompositeAlpha(brush: BrushSpec): Float =
        ((brush.opacity / 100f) * (brush.density / 100f) *
            (1f - WATER_ALPHA_FALLOFF * (brush.water / 100f).coerceIn(0f, 1f))).coerceIn(0f, 1f)

    // -----------------------------------------------------------------------
    // ⑥ 采样：只取 dab 附近一小块的平均色（网页 `sampleAvg()` ✓，**绝不全画布读** ✗）
    // -----------------------------------------------------------------------

    /**
     * 取 ([cx],[cy]) 附近一个 `2×half` 的**小方块**的**alpha 加权平均色** ✓。
     *
     * 口径照网页 ✓：
     *  · 窗口先夹进画布（网页给 `x0 / y0` 都留了 2 像素余量 ✓，这边照做 ✓）；
     *  · **高 = 宽**（网页那句 `const h = Math.min(w, …)` 就是这么写的 ✓，虽然看着像笔误，
     *    但它决定"采样窗口是方的" ⇒ **照抄** ✓，别自作聪明改成两个方向各自夹 ✗ —— 那样就不是同一套公式了 ✓）；
     *  · 平均是**按 alpha 加权**的 ✓（透明的像素不该把颜色往黑里拉 ✗）；
     *  · 整块全透明（`a ≤ 0.001`）→ 返回 **白色 + alpha 0** ✓（网页就是这么给的 ✓，
     *    调用方见到 `alpha < 0.02` 会改用白色 ✓ = "在空白处落笔不会突然变黑" ✓）。
     *
     * @return `[r, g, b, a]`；窗口根本放不下（画布太小 / 越界）→ `null` ✓
     */
    fun sampleAvg(pixels: IntArray, width: Int, height: Int, cx: Float, cy: Float, half: Float): FloatArray? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        if (!cx.isFinite() || !cy.isFinite() || !half.isFinite()) return null
        var w = (half * 2f).roundToInt()
        if (w < 2) w = 2
        var x0 = (cx - half).roundToInt().coerceIn(0, max(0, width - 2))
        var y0 = (cy - half).roundToInt().coerceIn(0, max(0, height - 2))
        w = kotlin.math.min(w, width - x0)
        val h = kotlin.math.min(w, height - y0)
        if (w < 2 || h < 2) return null

        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        var n = 0
        for (y in y0 until (y0 + h)) {
            val row = y * width
            for (x in x0 until (x0 + w)) {
                val c = pixels[row + x]
                val al = ((c ushr 24) and 0xFF) / 255f
                r += ((c ushr 16) and 0xFF) * al
                g += ((c ushr 8) and 0xFF) * al
                b += (c and 0xFF) * al
                a += al
                n++
            }
        }
        if (a <= 0.001f) return floatArrayOf(255f, 255f, 255f, 0f)
        return floatArrayOf(r / a, g / a, b / a, a / n)
    }

    /**
     * [sampleAvg] 的**带活笔画叠加**版本（第 ㉔ 批 ✓）—— 采样窗口里先做一次"层 ⊖ 活笔画"的合成 ✓。
     *
     * ## 为什么要它（口径：**"采到的色"必须和用户眼下看到的一样** ✓）
     *
     * 活笔画缓冲（`LiveStrokeLayer` ✓）只装**这一笔的墨迹** ✓，层像素（[sampleAvg] 的 `base`）
     * 里**没有**这一笔刚画上去的东西 ✓ —— 直接采 `base` 的话，"混色 / 涂抹"类笔刷
     * **采不到自己刚抹过去的那一笔** ✗（以前它是直接写在层像素上的，采得到 ✗）。
     *
     * 所以：窗口里每个像素先 `overPixel(base, ink, 1)` ✓ —— 结果与"每颗笔尖直接写层像素"
     * 那时候采到的**是同一份** ✓（用户看到什么就混什么 ✓）。
     *
     * ⚠️ 只在 `blending / water / colorStretch > 0` 时才被调用 ✓（见 [BrushSpec.wantsMix] ✓）；
     * ⚠️ 窗口口径（**方窗口** / 夹进画布 / alpha 加权 / 全透明给白色 ✓）与 [sampleAvg] **逐条一致** ✓ ——
     * 只是每个像素多一步合成 ✓（窗口最大 [MAX_SAMPLE_PX]×2 = 128 px 见方 ✓，绝不扫全画布 ✓）。
     *
     * @param overlay 活笔画缓冲（ARGB ✓，`null` / 尺寸为 0 = 没有活笔画 ⇒ 等价于 [sampleAvg] ✓）
     * @param overlayX/overlayY 活笔画缓冲左上角在**页面坐标**里的位置 ✓（缓冲下标 ↔ 页面坐标的平移 ✓）
     */
    fun sampleAvgOverlay(
        base: IntArray,
        width: Int,
        height: Int,
        overlay: IntArray?,
        overlayWidth: Int,
        overlayHeight: Int,
        overlayX: Int,
        overlayY: Int,
        cx: Float,
        cy: Float,
        half: Float,
    ): FloatArray? {
        if (overlay == null || overlayWidth <= 0 || overlayHeight <= 0 ||
            overlay.size < overlayWidth * overlayHeight
        ) {
            return sampleAvg(base, width, height, cx, cy, half)
        }
        if (width <= 0 || height <= 0 || base.size < width * height) return null
        if (!cx.isFinite() || !cy.isFinite() || !half.isFinite()) return null
        var w = (half * 2f).roundToInt()
        if (w < 2) w = 2
        var x0 = (cx - half).roundToInt().coerceIn(0, max(0, width - 2))
        var y0 = (cy - half).roundToInt().coerceIn(0, max(0, height - 2))
        w = kotlin.math.min(w, width - x0)
        val h = kotlin.math.min(w, height - y0)
        if (w < 2 || h < 2) return null

        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        var n = 0
        for (y in y0 until (y0 + h)) {
            val row = y * width
            val localRow = (y - overlayY) * overlayWidth
            val inRow = y >= overlayY && y < overlayY + overlayHeight
            for (x in x0 until (x0 + w)) {
                var c = base[row + x]
                if (inRow) {
                    val localCol = x - overlayX
                    if (localCol >= 0 && localCol < overlayWidth) {
                        val ink = overlay[localRow + localCol]
                        if (((ink ushr 24) and 0xFF) != 0) {
                            // 层 ⊖ 活笔画（**显示出来的那一份** ✓）—— 与 `ImageEditOps.overPixel` 同一个合成 ✓
                            c = ImageEditOps.overPixel(c, ink, 1f)
                        }
                    }
                }
                val al = ((c ushr 24) and 0xFF) / 255f
                r += ((c ushr 16) and 0xFF) * al
                g += ((c ushr 8) and 0xFF) * al
                b += (c and 0xFF) * al
                a += al
                n++
            }
        }
        if (a <= 0.001f) return floatArrayOf(255f, 255f, 255f, 0f)
        return floatArrayOf(r / a, g / a, b / a, a / n)
    }

    // -----------------------------------------------------------------------
    // ⑦ 一颗笔尖的**计划**（网页 `stamp()` 的完整前半 ✓）
    //
    // 为什么分成"先计划、后落笔"两步（网页是一边算一边画 ✓）：
    //  · 历史栈要在**动第一个像素之前**拿到"这一颗笔尖会碰到的整块矩形" ✓
    //    （count > 1 + 散布时，几颗笔尖散开 ⇒ 单颗的 dabBounds 盖不住 ✗，
    //     撤销就会把没记进历史的像素留下来 = 画面被啃掉一块 ✗）；
    //  · 顺便让"散布 / 抖动 / 计数"这一层**纯函数可测** ✓（不用去数像素 ✓）。
    // -----------------------------------------------------------------------

    /** 走位 / 混色要跨笔尖带的状态（网页 `makeStrokeState()` ✓ 里那几个字段 ✓）。 */
    class StrokeState(val random: Random) {
        var mixR: Float = 255f
        var mixG: Float = 255f
        var mixB: Float = 255f
        var hasMix: Boolean = false
        var dabCount: Int = 0

        // ---- 第 ㉔ 批：**采样的结构性计数**（报告 / 单测用 ✓，不参与绘制 ✓）----
        //
        // 用户口径：「顺手报一组"每 100 颗笔尖"的**结构性计数**（采样次数 / 位图重建次数 ✓）——
        // 这个能在测试里数 ✓」。这两个数就是"采样"那一半 ✓（位图重建那一半在 `AppState` ✓）。

        /** 真的调过几次 [sampleAvg] / [sampleAvgOverlay] ✓。 */
        var sampleCalls: Int = 0

        /** 因为"离上一颗笔尖的采样点 ≤ [SAMPLE_REUSE_PX]"而**复用**了几次 ✓。 */
        var sampleReuses: Int = 0

        /** 上一次**真的采到**的那个原始色（**还没被色延伸拖过** ✓ —— 拖色每次都要重算 ✓）。 */
        var lastSampleX: Float = Float.NaN
        var lastSampleY: Float = Float.NaN
        var lastSampleR: Float = 255f
        var lastSampleG: Float = 255f
        var lastSampleB: Float = 255f

        /** 上一次采到的**平均 alpha**（`sampleAvg` 的那个 `a / n` ✓）—— 复用那一档要用它 ✓。 */
        var lastSampleA: Float = 0f

        /**
         * 上一次**真的采样**时，画布那一侧的版本号（第 ㉜b 批 ✓）。
         *
         * 为什么要有它 ✗：㉔ 批那条"相邻两颗笔尖的采样点几乎重合就复用上一次的结果"的省法
         * **与网页口径冲突** ✗ —— 因为笔一落下去画布就变了（第 ㉜a 批起笔**直接画在层表面上** ✓），
         * 而网页**每一颗都重新采**（`stamp()` 里那句 `sampleAvg()` 无条件跑 ✓）。
         * 实测（`MixBrushParityTest`）：复用那版在 `blending=60` 组里逐点采样色**最大差 86**、
         * 平均差 3.11 ✗（最坏那一颗正好是"窗口里已经画过前两颗"的位置 ✓）。
         * ⇒ 复用**只在"画布确实没变过"时**才成立 ✓：调用方把当前画布版本传进来，
         * 与上一次采样时的版本**相等**才复用 ✓（不传 = `-1` ⇒ 一律重采 = 网页口径 ✓）。
         */
        var sampleEpoch: Int = 0
        var hasSample: Boolean = false
    }

    /** 一颗**算好了的**笔尖（位置 / 半径 / 纵横比 / 朝向 / 颜色 ✓，一步都不用再随机 ✓）。 */
    class DabShape(
        val x: Float,
        val y: Float,
        val radius: Float,
        val ratio: Float,
        val angleRadians: Float,
        val colorArgb: Int,
    )

    /** 一档笔尖的计划（这一档的 alpha + 要盖的那几颗 ✓）。 */
    class DabPlan(
        val alpha: Float,
        val shapes: List<DabShape>,
        /**
         * SAI 的「高品质缩小」（[BrushSpec.highQualitySampling] ✓）—— 第 ㉔ 批起它**有作用点**了 ✓：
         * 映射成"笔尖边缘 **2×2 超采样**"开关 ✓（见 [ImageEditOps.stampDab] ✓）。
         * 默认 false ⇒ 老调用点（老单测 / 预览）**一个字节都不变** ✓。
         */
        val highQualitySampling: Boolean = false,
        // ---- 混色诊断（第 ㉜b 批 ✓ —— 判据要"逐颗笔尖的采样色"，不把这些数带出来就只能靠嘴说 ✗）----
        // 全部默认值 = "这一档没采样" ✓ ⇒ 老调用点（不混色的笔 ✓）一个字节都不变 ✓。
        /** 这一档真的去采样了吗（`blending / water / colorStretch` 全 0 时为 false ✓）。 */
        val sampled: Boolean = false,
        /** 采到的**原始平均色**（α 加权 ✓，**还没过色延伸 / 混色** ✓，与网页 `sampleAvg` 同一个数 ✓）。 */
        val sampleR: Float = 0f,
        val sampleG: Float = 0f,
        val sampleB: Float = 0f,
        val sampleA: Float = 0f,
        /** 色延伸拖过之后、**混进笔画色之前**的那个色（= 网页 `stamp()` 里的 `sr/sg/sb` ✓）。 */
        val mixR: Float = 255f,
        val mixG: Float = 255f,
        val mixB: Float = 255f,
        /** 混色比例（= 网页 `mixT` ✓）。 */
        val mixT: Float = 0f,
    )

    /**
     * 把**一档**笔尖算出来（网页 `stamp()` 从 `sizeJit` 到形状入队那一整段 ✓）。
     *
     * @param baseRadius **压感 / Scaling / AbsoluteSize 之后的基准半径**（见 [jitterNibRadius] ✓）
     * @param sampleSource 混色的采样源 ✓ —— 第 ㉔ 批起它是"**层**"（还没合进活笔画的那一份 ✓），
     *   活笔画由下面 `sampleOverlay` 那几个参数带进来 ✓（合成只在采样窗口里做 ✓）
     * @param sampleOverlay 活笔画缓冲（`null` / 尺寸 0 = 没有活笔画 ✓ ⇒ 等价于老口径 ✓）
     */
    fun planDab(
        brush: BrushSpec,
        state: StrokeState,
        sampleSource: IntArray?,
        sampleWidth: Int,
        sampleHeight: Int,
        x: Float,
        y: Float,
        baseRadius: Float,
        pressure: Float,
        directionRadians: Float,
        colorArgb: Int,
        erasing: Boolean,
        pressureCurve: Float,
        sampleOverlay: IntArray? = null,
        sampleOverlayWidth: Int = 0,
        sampleOverlayHeight: Int = 0,
        sampleOverlayX: Int = 0,
        sampleOverlayY: Int = 0,
        /**
         * 画布那一侧现在的版本号（第 ㉜b 批 ✓）—— 只有它**等于**上一次采样时的版本
         * （[StrokeState.sampleEpoch] ✓）才允许"复用上一次的采样结果" ✓。
         * 默认 `-1` = 调用方不跟踪版本 ⇒ **一律重采**（= 网页口径 ✓）。
         */
        sampleEpoch: Int = -1,
    ): DabPlan {
        val random = state.random
        val dir = if (directionRadians.isFinite()) directionRadians else 0f

        // ① 半径（SizeJitter + 扩散和噪点 ✓）
        val r = jitterNibRadius(baseRadius, brush, random)

        // ② 朝向（基准 + 角度抖动 ±180° × 百分比 ✓）
        val angle = nibAngleRadians(brush, dir) +
            (brush.angleJitter / 100f) * randomBetween(random, -PI.toFloat(), PI.toFloat())

        // ③ 纵横比（±纵横比抖动 ✓，夹 0.05..20 ✓）
        val ratio = (brush.nibRatio * (1f + (brush.wxJitter / 100f) * randomBetween(random, -1f, 1f)))
            .coerceIn(0.05f, 20f)

        // ④ alpha
        val alpha = dabAlpha(brush, pressure, pressureCurve, random, erasing)

        // ⑤ 混色采样（**每档一次** ✓，照网页 ✓）
        //
        // ⚠️ 第 ㉔ 批的两条省法（都不改公式 ✓，只改"要不要真的去扫那一块" ✓）：
        //  ① `wantMix` 为假（`blending / water / colorStretch` 三条全 0 ✓）⇒ **一次都不采** ✓ ——
        //     这是绝大多数笔的正常情况 ✓（默认笔刷就是它 ✓）；
        //  ② 相邻笔尖的采样点**几乎重合**（≤ [SAMPLE_REUSE_PX]）**且画布没变过** ⇒ 复用上一次的结果 ✓ ——
        //     同一处连着盖几十颗笔尖时，那一块像素**只扫一次** ✓（结构性计数见 [StrokeState.sampleReuses] ✓）。
        //     ⚠️ **"画布没变过"这一条是第 ㉜b 批补上的** ✗：㉔ 那版只看"采样点近不近"，
        //     而笔一落下去画布就变了（㉜a 起笔**直接画在层表面上** ✓）⇒ 复用会拿**旧的**采样色 ✗，
        //     与网页"每颗都重采"对不上（实测逐点差最大 86、平均 3.11 ✗，见 [sampleEpoch] ✓）。
        val wantMix = !erasing && brush.wantsMix
        var mixR = 255f
        var mixG = 255f
        var mixB = 255f
        var mixT = 0f
        // ---- 诊断用（第 ㉜b 批 ✓）：这一档**真的采到的原始色** + 拖色之后那个色 ----
        var sampled = false
        var sampleR = 0f
        var sampleG = 0f
        var sampleB = 0f
        var sampleA = 0f
        if (wantMix) {
            val waterF = (brush.water / 100f).coerceIn(0f, 1f)
            mixT = mixRatio(brush)
            val half = (r * (SAMPLE_HALF_BASE + SAMPLE_HALF_WATER * waterF)).coerceIn(1f, MAX_SAMPLE_PX)
            val drag = r * (brush.colorStretch / 100f) * STRETCH_DRAG
            val sampleX = x - cos(dir) * drag
            val sampleY = y - sin(dir) * drag
            var sr = 255f
            var sg = 255f
            var sb = 255f
            var sa = 0f
            var haveSample = false
            val reuse = state.hasSample && state.sampleEpoch == sampleEpoch &&
                kotlin.math.hypot(sampleX - state.lastSampleX, sampleY - state.lastSampleY) <= SAMPLE_REUSE_PX
            if (reuse) {
                sr = state.lastSampleR
                sg = state.lastSampleG
                sb = state.lastSampleB
                sa = state.lastSampleA
                haveSample = true
                state.sampleReuses++
            } else {
                val source = sampleSource
                val sample = when {
                    source == null -> null
                    sampleOverlay != null && sampleOverlayWidth > 0 -> sampleAvgOverlay(
                        base = source,
                        width = sampleWidth,
                        height = sampleHeight,
                        overlay = sampleOverlay,
                        overlayWidth = sampleOverlayWidth,
                        overlayHeight = sampleOverlayHeight,
                        overlayX = sampleOverlayX,
                        overlayY = sampleOverlayY,
                        cx = sampleX,
                        cy = sampleY,
                        half = half,
                    )

                    else -> sampleAvg(source, sampleWidth, sampleHeight, sampleX, sampleY, half)
                }
                if (sample != null) {
                    sr = sample[0]
                    sg = sample[1]
                    sb = sample[2]
                    sa = sample[3]
                    if (sample[3] < 0.02f) {
                        sr = 255f
                        sg = 255f
                        sb = 255f
                    }
                    // 缓存**原始色**（还没被色延伸拖过 ✓）—— 拖色每次都要按新的 `state.mix` 重算 ✓
                    state.lastSampleX = sampleX
                    state.lastSampleY = sampleY
                    state.lastSampleR = sr
                    state.lastSampleG = sg
                    state.lastSampleB = sb
                    state.lastSampleA = sa
                    state.hasSample = true
                    state.sampleEpoch = sampleEpoch
                    state.sampleCalls++
                    haveSample = true
                }
            }
            if (haveSample) {
                // 诊断：**拖色之前**的那一份（= 网页 `sampleAvg` 的原样输出 ✓）
                sampled = true
                sampleR = sr
                sampleG = sg
                sampleB = sb
                sampleA = sa
                if (brush.colorStretch > 0f && state.hasMix) {
                    // 把**上一颗笔尖**的混色结果带过来 = 拖色 ✓
                    val k = STRETCH_CARRY * (brush.colorStretch / 100f)
                    sr = sr * (1f - k) + state.mixR * k
                    sg = sg * (1f - k) + state.mixG * k
                    sb = sb * (1f - k) + state.mixB * k
                }
                mixR = sr
                mixG = sg
                mixB = sb
            }
        }

        // ⑥ 计数 + 散布 + 颜色抖动（网页 `stamp()` 的 for 循环 ✓）
        val n = dabCount(brush, random)
        val scatterAmp = r * (brush.scattering / 100f)
        val spread = spreadAmount(brush)
        val hsl = hslOf(colorArgb)
        val eachShape = brush.applyToEachShape == 1
        val shapes = ArrayList<DabShape>(n)
        for (i in 0 until n) {
            val offset = if (scatterAmp > 0f) scatterOffset(brush, dir, r, random) else floatArrayOf(0f, 0f)
            var ox = offset[0]
            var oy = offset[1]
            if (spread > 0f) {
                ox += (random.nextFloat() - 0.5f) * r * spread * SPREAD_POSITION_JITTER
                oy += (random.nextFloat() - 0.5f) * r * spread * SPREAD_POSITION_JITTER
            }
            var px = x + ox
            var py = y + oy
            if (brush.integerPosition == 1) {
                px = px.roundToInt().toFloat()
                py = py.roundToInt().toFloat()
            }

            var h = hsl[0]
            var s = hsl[1]
            var l = hsl[2]
            if (eachShape || i == 0) {
                if (brush.hueJitter != 0f) h += (brush.hueJitter / 100f) * randomBetween(random, -180f, 180f)
                if (brush.saturationJitter != 0f) {
                    s += (brush.saturationJitter / 100f) * randomBetween(random, -1f, 1f)
                }
                if (brush.brightnessJitter != 0f) {
                    l += (brush.brightnessJitter / 100f) * randomBetween(random, -0.5f, 0.5f)
                }
            }
            val rgb = rgbOfHsl(h, s, l)
            var cr = rgb[0].toFloat()
            var cg = rgb[1].toFloat()
            var cb = rgb[2].toFloat()
            if (brush.colJitter != 0f) {
                // 前景 ↔ **背景（当白色 ✓）**
                val t = (brush.colJitter / 100f) * random.nextFloat()
                cr = 255f * t + cr * (1f - t)
                cg = 255f * t + cg * (1f - t)
                cb = 255f * t + cb * (1f - t)
            }
            if (wantMix && mixT > 0f) {
                cr = cr * (1f - mixT) + mixR * mixT
                cg = cg * (1f - mixT) + mixG * mixT
                cb = cb * (1f - mixT) + mixB * mixT
            }
            shapes += DabShape(px, py, r, ratio, angle, argbOf(cr, cg, cb, alpha))
            if (wantMix) {
                state.mixR = cr
                state.mixG = cg
                state.mixB = cb
                state.hasMix = true
            }
        }
        state.dabCount += n
        return DabPlan(
            alpha = alpha,
            shapes = shapes,
            highQualitySampling = brush.highQualitySampling == 1,
            sampled = sampled,
            sampleR = sampleR,
            sampleG = sampleG,
            sampleB = sampleB,
            sampleA = sampleA,
            mixR = mixR,
            mixG = mixG,
            mixB = mixB,
            mixT = mixT,
        )
    }

    /** 一档笔尖会碰到的**整块矩形**（[planDab] 那几颗的 `dabBounds` 并集 ✓）。 */
    fun planBounds(plan: DabPlan, width: Int, height: Int): EditRect? {
        var union: EditRect? = null
        for (shape in plan.shapes) {
            val rect = ImageEditOps.dabBounds(
                centerX = shape.x,
                centerY = shape.y,
                radius = shape.radius,
                nibRatio = shape.ratio,
                angleRadians = shape.angleRadians,
                width = width,
                height = height,
            ) ?: continue
            union = union?.union(rect) ?: rect
        }
        return union
    }

    /**
     * 把算好的一档笔尖**盖上去** ✓（= 网页 `stamp()` 里那两个 `fill` 分支 ✓）。
     *
     * @param texture 纸纹 / 噪点贴图（[buildTile] ✓；null = 不用 ✓）——
     *   ⚠️ **橡皮不吃纸纹** ✓（网页 `useTex && !erasing` ✓）：擦除是一条 alpha 衰减，
     *   乘上纸纹只会擦得断断续续 ✗。
     * @param originX/originY 目标缓冲左上角在**页面坐标**里的位置 ✓（第 ㉔ 批加 ✓，
     *   默认 0 = 目标缓冲就是整页 ✓，老调用点一个字节都不变 ✓）——
     *   活笔画缓冲只覆盖"这一笔的包围盒" ✓，笔尖坐标要平移成缓冲局部坐标才能落上去 ✓。
     *   ⚠️ 缓冲起点永远是纸纹边长（128 ✓）的整数倍 ⇒ 贴图查表的 `% 128` 与页面坐标**同余** ✓，
     *   "纸纹钉在画布上"这条口径**没变** ✓。
     * @param colorOverride 把每一颗笔尖的颜色换成它（`null` = 用计划里那一份 ✓，默认 ✓）——
     *   只有活笔画缓冲那条路会用 ✓：**橡皮**在缓冲里存的是"**累计擦除覆盖度**"
     *   （白 + alpha ✓，见 `LiveStrokeLayer` ✓），所以颜色必须是**不透明白** ✓，
     *   而不是"擦除色"（那一条本来也不看颜色 ✓）。
     */
    fun stampPlan(
        target: IntArray,
        width: Int,
        height: Int,
        plan: DabPlan,
        shape: BrushShape,
        erasing: Boolean,
        clip: ByteArray?,
        texture: ByteArray?,
        originX: Int = 0,
        originY: Int = 0,
        colorOverride: Int? = null,
    ): EditRect? {
        var union: EditRect? = null
        for (dab in plan.shapes) {
            val rect = ImageEditOps.stampDab(
                pixels = target,
                width = width,
                height = height,
                centerX = dab.x - originX,
                centerY = dab.y - originY,
                radius = dab.radius,
                shape = shape,
                color = colorOverride ?: dab.colorArgb,
                erasing = erasing,
                nibRatio = dab.ratio,
                angleRadians = dab.angleRadians,
                clip = clip,
                texture = texture,
                highQualitySampling = plan.highQualitySampling,
            ) ?: continue
            union = union?.union(rect) ?: rect
        }
        return union
    }

    /**
     * **keepOpacity 抬笔时的合成** ✓：把独立笔画缓冲按 [alpha] 一次性盖回主缓冲 ✓。
     *
     * @return 真的碰到的像素数（0 = 这一笔什么都没画上 ✓）
     */
    fun compositeLayer(
        target: IntArray,
        layer: IntArray,
        width: Int,
        height: Int,
        rect: EditRect,
        alpha: Float,
    ): Int {
        val a = if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else 0f
        if (a <= 0f) return 0
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return 0
        var touched = 0
        for (y in safe.y until (safe.y + safe.h)) {
            val row = y * width
            for (x in safe.x until (safe.x + safe.w)) {
                val index = row + x
                if (index < 0 || index >= target.size) continue
                val src = layer[index]
                if (((src ushr 24) and 0xFF) == 0) continue
                // `overPixel(dst, src, coverage)` 里 coverage 是**再乘一次** src 的 alpha 的 ✓
                // ⇒ 这里直接把"整笔浓度"当 coverage 用 ✓（结果 = 层 alpha × 浓度 ✓）
                target[index] = ImageEditOps.overPixel(target[index], src, a)
                touched++
            }
        }
        return touched
    }

    /** 打包 ARGB（**clamp 过** ✓ —— 颜色抖动 / 混色都可能算出 0..255 之外的值 ✓）。 */
    private fun argbOf(r: Float, g: Float, b: Float, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val red = r.roundToInt().coerceIn(0, 255)
        val green = g.roundToInt().coerceIn(0, 255)
        val blue = b.roundToInt().coerceIn(0, 255)
        return (a shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /** `p.pow(curve)`（`dabAlpha` 用 ✓）—— 单独列出来只是为了别在 main 里再 import 一次 ✓。 */
    private fun Float.pow(exponent: Float): Float = Math.pow(this.toDouble(), exponent.toDouble()).toFloat()
}
