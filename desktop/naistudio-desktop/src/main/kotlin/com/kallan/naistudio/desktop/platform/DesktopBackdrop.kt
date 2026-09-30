package com.kallan.naistudio.desktop.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import java.awt.Window
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import javax.imageio.ImageIO

/**
 * **电脑端的"整体美化"底子**。
 *
 * 背景与系统标题栏分别处理：
 *
 *  1. [styleTitleBar] —— 把**系统标题栏**染成 App 自己的颜色（深色模式还顺带切
 *     `DWMWA_USE_IMMERSIVE_DARK_MODE`，免得深色 App 顶着一条惨白的标题栏）。
 *  2. [customBackgroundBitmap] —— 读**用户自己设的那张背景图**（`filesDir/custom_background.*`，
 *     由设置页选、`services/CustomBackground` 落盘）。调用方（`Main.kt`）把它铺在所有内容
 *     **最底下**，再把页面底色调成半透明 —— 于是侧栏、顶栏、底栏、参数面板这些本来就半透明的面，
 *     背后终于**有东西可糊**，看着就是 Windows 11 那种磨砂玻璃（Mica/Acrylic 的观感）。
 *     **原样铺**；用户另外打开「毛玻璃质感」开关时改成**缩到小尺寸再两遍模糊**（见下）。
 *  3. [wallpaperBitmap] —— **老口径，已废弃**（原样保留，见下面的说明）。
 *  4. [themeBackgroundBitmap] —— glass_* 配色的内置柔光渐变，无自选图时启用，
 *     使用已有 AppBackdrop 毛玻璃面板机制；其他主题仍保留原有纯色底。
 *
 * ## ⚠️ 2026-09-20 补：自选背景图的**毛玻璃**是可选的（用户原话：「自选的背景图可选毛玻璃质感」）
 *
 * 同一个函数多一个 `frosted: Boolean`：
 *
 *  · `false`（默认）= 原图原样等比 Crop 铺满（与加开关之前**完全一致**）；
 *  · `true` = **先缩到长边 [FROSTED_MAX_DIMENSION] 再卷两遍 3×3 均值**，糊成一层磨砂纹理底。
 *
 * 顺序是刻意的：**先缩后糊**。4K 原图（3840×2160 ≈ 830 万像素）直接做卷积是几千万次乘加，
 * 编译期把它丢在组合里就是主线程卡住半秒起步；缩到 512 之后卷积只有 15 万像素，**毫秒级**。
 * 而且模糊半径会跟着放大倍率一起被拉伸 —— 在小图上糊一遍，等于在大图上糊一大圈。
 *
 * ## ⚠️ 2026-09-20 口径变更：不再默认读 Windows 桌面壁纸
 *
 * 原来（2026-09-19）的做法是**启动时去读本机桌面壁纸**，缩到 192 宽、过两遍盒式模糊当底层。
 * 用户现在的口径是：
 *
 *  · **原有主题没设置自定义背景图** → 底层**什么都不铺**，页面底色就是**主题自己的不透明底色**
 *    （浅色 = 主题米白、深色 = 主题近黑）；
 *  · **设置了** → 用用户那张图，**原样等比 Crop 铺满**（不模糊）。
 *
 * 于是 [wallpaperBitmap] 与 [wallpaperFile] **不再被任何地方调用**（保留函数是为了
 * "旧行为可查、随时能翻回来"，也免得留下一堆要清的死引用）。**默认不再碰系统壁纸**。
 *
 * ## ⚠️ 为什么是"自己铺一张图"，而不是真的开系统亚克力
 *
 * Win11 的 `DWMWA_SYSTEMBACKDROP_TYPE`（Mica/Acrylic）要生效，**客户区必须有不透明以外的像素**
 * —— 也就是窗口得是 per-pixel 透明窗口。Java/AWT 那边 per-pixel 透明**要求窗口无边框**
 *（`Window.setBackground(带 alpha 的颜色)` 对 decorated 窗口直接抛异常），而一旦改成
 * `undecorated = true`，Windows 原生标题栏、系统拖动、**边缘拖拽改尺寸**就全没了 ——
 * 这个 App 的整套布局都靠窗口宽度（宽/窄两套界面），不能拿掉缩放。所以走"自绘背景图"这条：
 * 观感等价，风险为零，且完全可回退（读不到图就什么都不做，界面回到纯色）。
 *
 * ## 已知取舍
 *
 *  · 背景图**只在设置变化时读一次**（外加主题切换时重贴标题栏色）。换了图要重新选一次。
 *  · [customBackgroundBitmap] 会把超大图（长边 > [CUSTOM_MAX_DIMENSION]）等比缩下来 ——
 *    一张 8000×6000 的全尺寸位图在显存里是 190MB 级，缩一下既省内存也不影响观感（**不模糊**）。
 *  · 读不到图（文件被删 / 格式认不出，比如 JDK 自带的 ImageIO 不认 webp）时返回 null，
 *    调用方就当没有这层 —— 界面退回主题底色，不会坏。
 */
object DesktopBackdrop {

    /** 内置毛玻璃主题的柔光底图，不读取系统壁纸；自选背景仍由入口优先使用。 */
    fun themeBackgroundBitmap(palette: String, dark: Boolean): ImageBitmap? {
        val hues = when (palette) {
            "glass_cyan" -> intArrayOf(0x48BFD4, 0x739BDC, 0x78CDBF)
            "glass_violet" -> intArrayOf(0xAA83DD, 0x719FD5, 0xD894B9)
            "glass_emerald" -> intArrayOf(0x60B69A, 0x7FB6B7, 0xB9C68A)
            "glass_rose" -> intArrayOf(0xDD95AE, 0xAC91CA, 0xE0B497)
            else -> return null
        }
        // 小尺寸的连续径向渐变放大后保持柔和，供已有 AppBackdrop 对齐重画。
        val width = 512
        val height = 320
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(
                java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY,
            )
            graphics.color = java.awt.Color(if (dark) 0x111923 else 0xF3F5F8)
            graphics.fillRect(0, 0, width, height)
            val centers = arrayOf(0.10f to 0.08f, 0.90f to 0.35f, 0.48f to 1.02f)
            hues.forEachIndexed { index, hue ->
                val color = java.awt.Color(hue)
                graphics.paint = java.awt.RadialGradientPaint(
                    java.awt.geom.Point2D.Float(width * centers[index].first, height * centers[index].second),
                    width * 0.86f,
                    floatArrayOf(0f, 0.55f, 1f),
                    arrayOf(
                        java.awt.Color(color.red, color.green, color.blue, if (dark) 104 else 118),
                        java.awt.Color(color.red, color.green, color.blue, if (dark) 42 else 48),
                        java.awt.Color(color.red, color.green, color.blue, 0),
                    ),
                )
                graphics.fillRect(0, 0, width, height)
            }
        } finally {
            graphics.dispose()
        }
        return image.toComposeImageBitmap()
    }

    // ---- DWM 属性号（Windows SDK 的 dwmapi.h；数字是稳定的，老版本不认的会返回错误码，忽略即可）----

    /** 深色标题栏（Win10 1809 起是 19，2004 起正式是 20 —— 两个都试）。 */
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    /** 标题栏边框色（Win11）。 */
    private const val DWMWA_BORDER_COLOR = 34

    /** 标题栏底色（Win11）。 */
    private const val DWMWA_CAPTION_COLOR = 35

    /** 标题栏文字色（Win11）。 */
    private const val DWMWA_TEXT_COLOR = 36

    /** `dwmacore.h` 的 `S_OK`。 */
    private const val S_OK = 0

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int

        companion object {
            val INSTANCE: DwmApi? by lazy {
                runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
            }
        }
    }

    /**
     * 把系统标题栏染成 App 自己的颜色。
     *
     * @param background 页面底色（**必须是不透明色** —— COLORREF 没有 alpha 通道）
     * @param foreground 标题文字色（用 `onBackground`）
     * @return 至少一项设置成功
     */
    fun styleTitleBar(window: Window, dark: Boolean, background: Int, foreground: Int): Boolean {
        val api = DwmApi.INSTANCE ?: return false
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return false

        fun set(attribute: Int, value: Int): Boolean {
            val ref = IntByReference(value)
            return runCatching { api.DwmSetWindowAttribute(hwnd, attribute, ref, 4) == S_OK }
                .getOrDefault(false)
        }

        val darkOk = set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) or
            set(DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, if (dark) 1 else 0)
        // Win10 不认这三个（返回 E_INVALIDARG），那就是"只切了深色标题栏"，观感照样是提升
        val captionOk = set(DWMWA_CAPTION_COLOR, toColorRef(background))
        set(DWMWA_TEXT_COLOR, toColorRef(foreground))
        set(DWMWA_BORDER_COLOR, toColorRef(background))
        return darkOk || captionOk
    }

    /** Compose 的 ARGB → Win32 的 `COLORREF`（`0x00BBGGRR`）。 */
    private fun toColorRef(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    /**
     * **用户设的自定义背景图** → Compose 位图（调用方拉到全屏 Crop 铺开）。读不到就返回 null。
     *
     * @param maxDimension 不糊时的**最大长边**：超过就等比缩下来（只为了省内存，不是艺术效果）。
     * @param frosted 用户那个「毛玻璃质感」开关（见 [AppSettings.customBackgroundFrosted]）：
     *   · `false` = **原图原样**（老行为，含上面那条"只缩不糊"）；
     *   · `true`  = **先缩到长边 [FROSTED_MAX_DIMENSION] 再两遍 3×3 均值模糊**，
     *     铺出来是磨砂纹理底 —— 照片细节不跟文字抢注意力，半透明底色上的字更清楚。
     *
     *   ⚠️ 糊这条路**只走小图**（512 而非 3840）：4K 原图直接卷积是几千万次乘加，
     *   放在组合里就是主线程卡住；缩完只有 15 万像素，毫秒级。
     *   ⚠️ 调用方**必须在 IO 线程调它**（`Main.kt` 用的是 `LaunchedEffect` + `Dispatchers.IO`）——
     *   解码本身也是 CPU 活，糊不糊都不该在组合期同步做。
     *
     * 只认 JDK ImageIO 能解的格式（png / jpg / bmp / gif…）。**webp 解不了**（JDK 17 没有 webp 插件），
     * 那时返回 null —— 调用方退回主题底色，界面不会坏，`app.log` 里会留一行 WARN。
     */
    fun customBackgroundBitmap(
        file: File,
        maxDimension: Int = CUSTOM_MAX_DIMENSION,
        frosted: Boolean = false,
    ): ImageBitmap? = runCatching {
        if (!file.isFile) return null
        val source = ImageIO.read(file) ?: return null
        if (source.width <= 0 || source.height <= 0) return null

        val bitmap = if (frosted) frostedTexture(source) else scaleTo(source, maxDimension)
        logInfo(
            "Backdrop",
            "自定义背景图 ${bitmap.width}x${bitmap.height}${if (frosted) "（毛玻璃）" else ""} 来自 ${file.name}",
        )
        bitmap.toComposeImageBitmap()
    }.onFailure { logWarn("Backdrop", "自定义背景图读取失败：${it.message}") }.getOrNull()

    /**
     * 毛玻璃纹理：**先缩到长边 [FROSTED_MAX_DIMENSION]，再两遍 3×3 均值**。
     *
     * 为什么是 512（不是老壁纸那套的 192）：
     *  · 192 铺到 1920 宽的窗口上是 **10 倍**放大，每个小像素变成 10px 的色块，
     *    看着像水彩晕开、不像磨砂（那是"彻底糊掉"，不是"毛玻璃"）；
     *  · 512 是 **3.75 倍**（4K 屏 7.5 倍），再叠上两遍模糊的等效半径，
     *    放大后是**看得出色带层次、但没有任何照片细节**的磨砂纹理 —— 也就是 Win11 Acrylic 那种观感；
     *  · 顺带把成本钉死：512×~288 = 15 万像素，两遍卷积仍是**毫秒级**，换多大的原图都一样。
     *
     * 两遍 3×3 均值 ≈ 一次 5×5 的近似高斯（照 [wallpaperBitmap] 的老做法）。
     * `EDGE_NO_OP` 免得边缘被啃黑一圈（均值核按 0 补边会让四边发暗）。
     */
    private fun frostedTexture(source: BufferedImage): BufferedImage {
        var small = scaleTo(source, FROSTED_MAX_DIMENSION)
        val blur = ConvolveOp(
            Kernel(3, 3, FloatArray(9) { 1f / 9f }),
            ConvolveOp.EDGE_NO_OP,
            null,
        )
        repeat(FROSTED_BLUR_PASSES) { small = blur.filter(small, null) }
        return small
    }

    /** 等比缩到**长边 = [longestTarget]**（bilinear）；本来就够小就原样返回，只缩不放。 */
    private fun scaleTo(source: BufferedImage, longestTarget: Int): BufferedImage {
        val longest = maxOf(source.width, source.height)
        if (longest <= longestTarget) return source
        val ratio = longestTarget.toDouble() / longest
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { out ->
            out.createGraphics().apply {
                setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                drawImage(source, 0, 0, width, height, null)
                dispose()
            }
        }
    }

    /**
     * 本机壁纸 → 一张很小的"磨砂"位图（调用方拉到全屏铺开）。读不到就返回 null。
     *
     * 步骤：注册表拿壁纸路径（拿不到就退回 Windows 自己转码出来的 `TranscodedWallpaper`）
     * → 等比缩到 [TARGET_WIDTH] 宽 → 两遍 3×3 均值模糊 → 交给 Compose。
     *
     * ⚠️ **2026-09-20 起没有调用方**（用户口径改成"默认不读桌面壁纸"、要糊就糊**自选的那张**，
     * 见 [customBackgroundBitmap] 的 `frosted` 与本文件顶部说明）。
     * 函数**刻意保留**：旧观感想翻回来时改一行调用即可，也免得 `wallpaperFile` / 模糊那套变成死代码。
     */
    fun wallpaperBitmap(targetWidth: Int = TARGET_WIDTH): ImageBitmap? = runCatching {
        val file = wallpaperFile() ?: return null
        val source = ImageIO.read(file) ?: return null
        if (source.width <= 0 || source.height <= 0) return null

        val width = targetWidth.coerceAtLeast(MIN_TARGET_WIDTH)
        val height = (source.height.toLong() * width / source.width).toInt().coerceAtLeast(1)
        var small = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        small.createGraphics().apply {
            setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        // 两遍 3×3 均值 ≈ 一次 5×5 的近似高斯；`EDGE_NO_OP` 免得边缘被啃黑一圈
        val blur = ConvolveOp(
            Kernel(3, 3, FloatArray(9) { 1f / 9f }),
            ConvolveOp.EDGE_NO_OP,
            null,
        )
        repeat(2) { small = blur.filter(small, null) }

        logInfo("Backdrop", "壁纸磨砂层 ${width}x$height 来自 ${file.name}")
        small.toComposeImageBitmap()
    }.onFailure { logWarn("Backdrop", "壁纸磨砂层生成失败：${it.message}") }.getOrNull()

    /** 壁纸文件：先问注册表，再退回系统转码出来的那份。 */
    private fun wallpaperFile(): File? {
        val fromRegistry = runCatching {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Control Panel\\Desktop",
                "WallPaper",
            )
        }.getOrNull()
        val registryFile = fromRegistry?.takeIf { it.isNotBlank() }?.let(::File)
        if (registryFile != null && registryFile.isFile) return registryFile

        // 幻灯片 / 主题壁纸时注册表里那个路径可能为空或已失效；这一份永远在。
        val appData = System.getenv("APPDATA") ?: return null
        val transcoded = File(File(appData, "Microsoft\\Windows\\Themes"), "TranscodedWallpaper")
        return transcoded.takeIf { it.isFile }
    }

    private const val TARGET_WIDTH = 192
    private const val MIN_TARGET_WIDTH = 32

    /**
     * 自定义背景图的**最大长边**：超了就等比缩下来。
     *
     * 为什么要有：用户完全可能选一张 8000×6000 的原图，全尺寸解码出来是 190MB 级
     * （还要再进显存），够把 App 拖垮；3840 已经覆盖 4K 屏的物理像素，缩到这儿看不出差别。
     */
    private const val CUSTOM_MAX_DIMENSION = 3840

    /**
     * **毛玻璃**那条路的缩放长边：先缩到这儿再模糊。
     *
     * 512 的依据见 [frostedTexture]：192（老壁纸那套）糊成水彩块、3840（原图）又慢得没道理，
     * 512 在"看得出纹理层次"和"毫秒级卷积"之间。**改大 = 更清楚但更慢；改小 = 更糊**。
     */
    private const val FROSTED_MAX_DIMENSION = 512

    /** 均值模糊遍数：两遍 3×3 ≈ 一次 5×5 的近似高斯（与 [wallpaperBitmap] 同口径）。 */
    private const val FROSTED_BLUR_PASSES = 2
}
