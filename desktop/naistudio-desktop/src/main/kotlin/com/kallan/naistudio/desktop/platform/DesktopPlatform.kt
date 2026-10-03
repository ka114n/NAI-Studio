package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.models.INFINITE_MAX_SIDE_DESKTOP
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.platform.AppPaths
import com.kallan.naistudio.platform.DeviceInfo
import com.kallan.naistudio.platform.GallerySink
import com.kallan.naistudio.platform.ImageIo
import com.kallan.naistudio.platform.NativeImage
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.platform.PenPhase
import com.kallan.naistudio.platform.PenSample
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.SecretStore
import com.kallan.naistudio.platform.installAppLogger
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.mohamedrejeb.stylus.PenEvent
import com.mohamedrejeb.stylus.PenEventType
import com.mohamedrejeb.stylus.PenTool
import com.mohamedrejeb.stylus.compose.penInput
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * **电脑端（Windows）的平台层实现。**
 *
 * 与手机端 `services/AndroidPlatform.kt` 一一对应 —— 共用树只认
 * `com.kallan.naistudio.platform.*` 那几个接口，两边的差异全部收在各自这一个文件里。
 *
 * | 能力 | 手机 | 电脑（这里） |
 * |---|---|---|
 * | 键值 | SharedPreferences | `%APPDATA%\NAI Studio\prefs.json` |
 * | 密钥 | Android Keystore | 本机密钥文件 + AES-GCM（见 [FileSecretStore] 的风险说明） |
 * | 目录 | `filesDir` | `%APPDATA%\NAI Studio` |
 * | 相册 | MediaStore | 没有相册 → false |
 * | 设备 | `Build.MANUFACTURER/MODEL` | `os.name` / 机器名 |
 * | 另存 | SAF | 普通目录拷贝 |
 */
fun desktopPlatform(): Platform = object : Platform {
    override val kv: KeyValueStore = FileKeyValueStore(File(appDir(), "prefs.json"))
    override val secrets: SecretStore = FileSecretStore(File(appDir(), "secrets.bin"))
    override val paths: AppPaths = DesktopAppPaths
    override val gallery: GallerySink = WindowsGallerySink
    override val device: DeviceInfo = DesktopDeviceInfo
    override val images: ImageIo = DesktopImageIo

    /**
     * 系统 UI 缩放（125% / 150% 那种）—— 见 `Platform.uiScale` 的说明。
     *
     * 取 AWT 默认屏幕配置的缩放。拿不到就 1f（宁可少要一点像素，也别把缓存撑爆）。
     */
    override val uiScale: Float
        get() = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .defaultTransform
                .scaleX
                .toFloat()
        }.getOrDefault(1f)

    init {
        // **日志落到文件**（用户 2026-09-16 排查"聚焦重绘没生效"时加的）：
        // jpackage 出来的 exe 没有控制台，`logInfo` 默认的 println 全进了黑洞，
        // 于是排查只能靠猜。现在写到 `%APPDATA%\NAI Studio\app.log`（超过 1 MB 轮转一次）。
        installAppLogger { level, tag, message ->
            runCatching {
                val file = File(appDir(), "app.log")
                if (file.exists() && file.length() > 1_000_000) {
                    File(appDir(), "app.log.1").delete()
                    file.renameTo(File(appDir(), "app.log.1"))
                }
                file.appendText("$level/$tag: $message\n", Charsets.UTF_8)
            }
        }
    }

    /** 版本号：先用常量（与 `build.gradle.kts` 的 `packageVersion` 保持一致）。
     *  桌面要不要独立编号、要不要跟手机同号，见 docs/25 方案 Q10 —— 定了再改成统一来源。 */
    override fun installApplicationUpdate(file: File) = DesktopApplicationUpdater.install(file)

    override val appVersion: String get() = DESKTOP_VERSION

    /** 电脑版暂时只做 local 那一套（自填 token 直连），见 docs/25 方案 Q4。 */
    override val hostedEdition: Boolean get() = false

    /**
     * **无限画布的最大边长**（电脑 **4096** ✓ / 手机 3072 ✓，用户 2026-09-22 拍板 ✓）。
     *
     * ⛔ 这一行**必须显式写** ✗✗ —— 不是"接口里有默认值就能省" ✓：
     * `Platform` 里那个默认实现被 Kotlin 编成了"**抽象方法 + DefaultImpls**" ✓，
     * 实现类必须**自己留一个桥接方法** ✓；而这个匿名对象所在的文件要是没被增量编译重编 ✓，
     * 运行时就是 `AbstractMethodError` ✗ —— **2026-09-22 用户报"进无限画布就崩溃"就是这一条** ✓
     *（`app.log` 干净得很、进程直接没了，只有把 exe 拉起来看 stderr 才看得到 ✓，
     * 堆栈就是 `AppState.resetInfiniteCanvas` → `Platform.getInfiniteCanvasMaxSide` ✓）。
     * 手机端那份（`AndroidPlatform`）当时写了显式覆写，所以手机没崩 ✓ —— 两边**都要写** ✓。
     */
    override val infiniteCanvasMaxSide: Int = INFINITE_MAX_SIDE_DESKTOP

    /** "关于"那一栏的构建信息：电脑版没有 versionCode 的等价物，给固定值，别显示成空。 */
    override val versionCode: Int get() = 1
    override val buildStamp: String get() = DESKTOP_BUILD_STAMP
    override val edition: String get() = "电脑版"

    /** 电脑版开发期恒为 true：诊断日志开着一律可见（发布包里再按需收）。 */
    override val debugBuild: Boolean get() = true

    /**
     * 让出一帧。
     *
     * 电脑上没有 Compose 的组合帧时钟（`MonotonicFrameClock`）挂在这个协程上下文里，
     * 所以退化成"睡一帧"（~16ms）：目的本来就是**让出调度器**、让排队的 UI 活儿先跑，
     * 不是精确同步到 vsync。够用，且不会引入平台依赖。
     */
    override suspend fun yieldFrame() {
        kotlinx.coroutines.delay(16)
    }

    /** 具名 store → 具名文件：`%APPDATA%\NAI Studio\<name>.json`（与手机的多个 prefs 文件对应）。 */
    override fun prefs(name: String): KeyValueStore =
        FileKeyValueStore(File(appDir(), "$name.json"))

    /**
     * 电脑上原样返回：Skia 的文字排版**没有"字体留白"这个概念**
     * （手机那份 `PlatformTextStyle(includeFontPadding = …)` 到此为止）。
     */
    override fun compactTextStyle(style: TextStyle): TextStyle = style

    /**
     * **用户导入的字体 → Compose 的 `FontFamily`**（高级漫画的气泡 / 文本用）。
     *
     * · 电脑端能读字体文件的 API 是 `androidx.compose.ui.text.platform.Font(File)`
     *   （CMP / skiko 那份；手机上是 `androidx.compose.ui.text.font.Font(File)`，包名不同）——
     *   所以这个平台差异收在**这一个文件**里，共用树只调 [Platform.comicFontFamily] ✓；
     * · `.ttf` / `.otf` / `.ttc` 它都吃（skiko 的 `Typeface.makeFromData` 走 HarfBuzz）；
     * · **读不出来 / 文件没了 → null**（回落系统默认字体 ✓，不能崩 ✗）；
     * · 结果**按路径缓存**：`FontFamily` 每次重建都要过一遍字体解析，
     *   而界面上每敲一个字都会重组一次 ✗（缓存里存 null 也一样 —— 坏文件不反复重试 ✓）。
     */
    override fun comicFontFamily(path: String): FontFamily? = synchronized(fontFamilies) {
        if (fontFamilies.containsKey(path)) return@synchronized fontFamilies[path]
        val loaded = runCatching {
            val file = File(path)
            if (!file.isFile) null else FontFamily(androidx.compose.ui.text.platform.Font(file))
        }.getOrNull()
        fontFamilies[path] = loaded
        loaded
    }

    /** 字体家族缓存（key = 落盘绝对路径）。见 [comicFontFamily] 的说明。 */
    private val fontFamilies = HashMap<String, FontFamily?>()

    /**
     * **气泡 / 文本那一层 → 透明底 PNG 字节**（高级漫画 ⑦ 拼页导出 ✓）。
     *
     * 实现全在 [DesktopComicRaster]（`androidx.compose.ui.ImageComposeScene` ✓ ——
     * 这个 API 只有 CMP 桌面端有，所以它住在这一侧 ✓）；这里只负责把"电脑端就是能栅格化"
     * 这件事**从平台层说出来** ✓（共用树那边只认 [Platform.renderComicOverlayLayer] ✓）。
     */
    override fun renderComicOverlayLayer(
        page: com.kallan.naistudio.models.ComicBoardPage,
        layerId: String,
        width: Int,
        height: Int,
    ): ByteArray? = DesktopComicRaster.render(this, page, layerId, width, height)

    /**
     * **数位板 / 触控笔压感**（用户 2026-09-20：「compose-stylus 先接这个」✓）。
     *
     * 实现就是库里的 `Modifier.penInput`（`com.mohamedrejeb.stylus:stylus-compose` ✓，
     * **依赖只在这个模块里** ✓）→ 翻译成共用树的 [PenSample] ✓。
     *
     * ## 桌面这条路是**原生 JNI**（不是 Compose 指针管线）
     *
     * Windows = RealTimeStylus（RTS）、macOS = Cocoa、Linux = X11 + XInput2 ✓；
     * **原生 DLL 已经打进 `stylus-jvm-0.1.6.jar`**（`native/windows-x86_64/stylus.dll` 610 KB ✓），
     * 运行时有 `NativeLoader` 自己解包到临时文件再 `System.load` ✓ ——
     * **不用装 MSVC、不用手工 `System.load`** ✓（详见本文件下面那段说明 ✓）。
     *
     * ## 两处刻意的换算
     *
     *  · **坐标**：库里已经减掉了 `positionInWindow()` ✓（见 `ComposePenInputManager.translateToComponent` ✓），
     *    所以出来的就是**这个 Modifier 所在组件的局部像素** ✓ —— 和 `pointerInput` 的 `position`
     *    是同一套 ✓，调用方（高级漫画的画布）可以直接喂 `comicScreenToPagePixel` ✓；
     *  · **压力**：只有**真笔**（笔尖 / 笔尾橡皮 ✓）才吃它报上来的值 ✓；鼠标 / 触摸报的是 0，
     *    这里**换成 1** ✓ —— "没有压感"必须等于**老行为**（整宽一笔 ✓），
     *    拿 0 去乘笔刷半径会得到"细得看不见的一笔" ✗（见 [PenSample.pressure] 的 ⚠️ ✓）。
     *    状态行要看"有没有笔"看的是 `penTool` / `toolName` ✓，不是看这个数 ✗。
     *
     * ⚠️ **不吃鼠标事件** ✓：库那个节点只旁观（`onPointerEvent` 里从不 `consume()` ✓），
     * 鼠标 / 触摸那条路一个字都没改 ✓。
     * ⚠️ 这一版**没给窗口包库里的 `ProvidePenInputWindow`** ✗ —— 电脑版只有一个 `Window` ✓，
     * 库里那条回退"取当前聚焦的可见窗口"就是它 ✓（真要开多窗口时再包 ✓）。
     */
    override fun penInputModifier(key: Any, onSample: (PenSample) -> Unit): Modifier {
        // 一次性探针：把"**原生库到底加载上没有**"写进 `app.log` ✓（见 [probePenNative] ✓）。
        probePenNative()
        return Modifier.penInput(key = key) { event -> onSample(event.toPenSample()) }
    }

    /** 引用就是文件路径。 */
    override fun openInput(ref: String): InputStream? =
        runCatching { File(ref).inputStream() }.getOrNull()

    override fun openOutput(ref: String): OutputStream? = runCatching {
        File(ref).apply { parentFile?.mkdirs() }.outputStream()
    }.getOrNull()

    /** 电脑上就是普通路径，没有"授权"这回事。 */
    override fun takePersistablePermission(ref: String): Boolean = true

    override fun exportToUserDir(source: File, treeUri: String): Boolean = runCatching {
        val dir = File(treeUri)
        if (!dir.isDirectory) return@runCatching false
        source.copyTo(File(dir, source.name), overwrite = false)
        true
    }.getOrDefault(false)
}

/**
 * **一次性探针**：原生库（`stylus.dll`）到底加载上没有 → 写进 `%APPDATA%\NAI Studio\app.log` ✓。
 *
 * 为什么要它（用户最担心的一条：「**运行时会不会缺 DLL**」⚠️）：
 *  · 库自己的加载器（`NativeLoader`）是**自己解包 jar 里的 DLL 到临时目录**再 `System.load` ✓，
 *    失败时它只 `System.err.println("[stylus] ...")` ✗ —— 而 jpackage 出来的 exe **没有控制台** ✓，
 *    那句话就掉进黑洞了 ✗（`app.log` 里一个字都没有 ✓，用户只会看到"笔压没反应"）；
 *  · 所以这里**借库的入口做一次探测** ✓：`PenInputSource` 的伴生对象 `init` 里就会
 *    `NativeLoader.loadLibrary("stylus")` ✓ —— 碰一下它，成功 / 失败都能落到**我们自己的日志**里 ✓。
 *  · **失败不影响任何东西** ✓：笔压本来就是"锦上添花" ✓，加载不出来就照旧走鼠标那条路 ✓
 *    （用户口径：能跑起来优先 ✓，绝不因为没数位板就崩 ✗）。
 *
 * 只探一次 ✓（`penNativeProbed`）—— 每次组合都探就变成反复解包 DLL ✗。
 */
private var penNativeProbed = false

private fun probePenNative() {
    if (penNativeProbed) return
    penNativeProbed = true
    runCatching { com.mohamedrejeb.stylus.PenInputSource.Default }
        .onSuccess {
            logInfo("PenInput", "compose-stylus 原生库已加载（数位板笔压可用）")
        }
        .onFailure { error ->
            logWarn(
                "PenInput",
                "compose-stylus 原生库加载失败，笔压不可用（照旧走鼠标路径，不影响其它功能）：$error",
            )
        }
}

/**
 * 库里的一次 [PenEvent] → 共用树的 [PenSample]（见 [Platform.penInputModifier] 的说明 ✓）。
 *
 * 三件事值得单独说：
 *  · **压力只在真笔上生效** ✓（`PenTool.Pen` / `PenTool.Eraser`）—— 鼠标 / 触摸报的是 0.0，
 *    这里换成 1f ✓（"没有压感" = 老行为 = 整宽一笔 ✓，0 会画成"细得看不见" ✗）；
 *  · **`penTool` 是"有没有数位板信号"的判据** ✓（状态行要能看出"没有信号"而不是
 *    "压力恒等于 1" ✗ —— 用户点名的那一条 ✓）；
 *  · **倾斜 / 旋转的单位照库的口径换算** ✓：库的 `PenEvent.tiltX/tiltY` 在桌面（RTS / X11）
 *    是**度** ✓（iOS 才是弧度 ✗，这边用不上 ✓）；而 `PenEvent.rotation` 库文档写明是
 *    **弧度**（「Barrel rotation in radians, or 0 if unsupported」✓）⇒ 这里换成**度** ✓
 *    —— 共用树那份 [PenSample.rotation] 是 0..360 的度 ✓（见那边的说明 ✓）。
 *
 * ⚠️ **Windows 上 `rotation` 多半恒为 0**（用户 2026-09-20 那一条的实话 ✓）：
 * 库的 RTS 后端只 `SetDesiredPacketDescription(6, …)` 要了 **X / Y / 压力 / 切向压力 / X 倾角 /
 * Y 倾角** ✓（上游 `RealTimeStylusEventHandler::initialize` ✓）—— **没有"旋转"那一路** ✗。
 * ⚠️ 而且**旋转那一套已经取消了** ✓（`NibAngleControl.BARREL` / `barrelAngleDegrees` 都删了 ✓）——
 * 这一格现在只喂 `comic.board.penStatus` 那行只读状态 ✓（让用户**亲眼看到**报没报 ✓）。
 * **真机上到底报不报，得用户在数位板上看那一行状态** ✓（我这边没有数位板 ✗）——
 * 看 `comic.board.penStatus` 里那个「旋转 N°」✓。
 */
private fun PenEvent.toPenSample(): PenSample {
    val isPen = tool == PenTool.Pen || tool == PenTool.Eraser
    return PenSample(
        x = x.toFloat(),
        y = y.toFloat(),
        pressure = if (isPen) pressure.toFloat().coerceIn(0f, 1f) else 1f,
        tiltX = tiltX.toFloat(),
        tiltY = tiltY.toFloat(),
        // 弧度 → 度（0..360 ✓）；不支持旋转的后端报 0 → 这里仍然是 0 ✓
        rotation = penRotationDegrees(rotation),
        eraser = tool == PenTool.Eraser,
        toolName = tool.name,
        penTool = isPen,
        phase = when (type) {
            PenEventType.Hover -> PenPhase.Hover
            PenEventType.Press -> PenPhase.Press
            PenEventType.Move -> PenPhase.Move
            PenEventType.Release -> PenPhase.Release
        },
    )
}

/**
 * 库的**弧度** → 共用树的**度**（0..360 ✓）。
 *
 * ⚠️ **这一条是"没在真机上验过"的假设** ✗（我这边没有数位板 ✓）：依据是库自己的
 * `PenEvent` KDoc（「Barrel rotation in radians, or 0 if unsupported」✓）。
 * 万一某块板子报的其实是度，这里换算完就会偏（`90° → 90°×180/π mod 360` ✗）——
 * 那时**只改这一个函数**即可 ✓（结果只影响状态行上显示的那个"旋转 N°" ✓，
 * 旋转那一套已经取消了、不再参与任何画法 ✓）。
 */
private fun penRotationDegrees(radians: Double): Float {
    if (!radians.isFinite()) return 0f
    return ImageEditOps.normalizeDegrees(Math.toDegrees(radians).toFloat())
}


/** 电脑版版本号。见 [Platform.appVersion] 的说明。 */
const val DESKTOP_VERSION = "1.1.148"

/** "关于"里显示的构建标识（电脑版没有 BuildConfig，给个能看出是电脑版的串）。 */
const val DESKTOP_BUILD_STAMP = "desktop"

/** App 数据根目录：`%APPDATA%\NAI Studio`。 */
fun appDir(): File = File(
    System.getenv("APPDATA") ?: System.getProperty("user.home"),
    "NAI Studio",
).apply { mkdirs() }

/**
 * 文件型键值存储：整份 JSON 读进内存，[KeyValueStore.Editor.apply] 时整份写回。
 *
 * ## 两个刻意的选择
 *
 * · **形状与 SharedPreferences 一致**（`edit().putX().apply()`）—— 与手机端同一个接口，
 *   共用代码里几十处调用点一行不用改。
 * · **写回是同步的**（手机上 `apply()` 是异步落盘）。设置这个东西只有几 KB，
 *   写到本地 SSD 是微秒级；更重要的是**同步写完才不会有"刚改完就断电丢设置"**。
 *   如果以后 profiles 变大到可观，再改成"标脏 + 后台 flush"。
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {

    /** 内存镜像；构造时读一次。 */
    private val data: JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText(StandardCharsets.UTF_8)) else JSONObject()
    }.getOrElse { JSONObject() }

    override fun getString(key: String, defaultValue: String?): String? =
        if (data.has(key) && !data.isNull(key)) data.getString(key) else defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (data.has(key) && !data.isNull(key)) data.getBoolean(key) else defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        if (data.has(key) && !data.isNull(key)) data.getInt(key) else defaultValue

    override fun edit(): KeyValueStore.Editor = object : KeyValueStore.Editor {
        private val pending = JSONObject()
        private val removals = mutableSetOf<String>()

        override fun putString(key: String, value: String): KeyValueStore.Editor = apply {
            pending.put(key, value)
            removals.remove(key)
        }

        override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor = apply {
            pending.put(key, value)
            removals.remove(key)
        }

        override fun putInt(key: String, value: Int): KeyValueStore.Editor = apply {
            pending.put(key, value)
            removals.remove(key)
        }

        override fun remove(key: String): KeyValueStore.Editor = apply {
            removals.add(key)
            pending.remove(key)
        }

        override fun apply() {
            removals.forEach { data.remove(it) }
            pending.keys().forEach { data.put(it, pending.get(it)) }
            file.parentFile?.mkdirs()
            // 先写临时文件再改名：中途崩了也不会留一份半截的 JSON（读的时候会整个丢掉）
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(data.toString(), StandardCharsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(data.toString(), StandardCharsets.UTF_8)
                tmp.delete()
            }
        }
    }
}

/**
 * 密钥存储：**DPAPI 保护的主密钥 + AES-GCM 密文**（与手机同档）。
 *
 * ## 为什么是这个结构（而不是直接拿 DPAPI 加密每个 token）
 *
 * DPAPI 每次加密都会带一段随机熵、开销不小；而 token 会**频繁读写**（启动读、刷新写）。
 * 所以用标准的**混合**做法：
 *
 *  · `secret.key`：一颗 32 字节的 AES 主密钥，**由 DPAPI（当前 Windows 用户）保护后落盘**
 *    —— 文件前言是 `DPAPI1`，后面是 `CryptProtectData` 的结果；
 *  · `secrets.bin`：真正的密文，格式与手机端完全一致（`base64(IV):base64(密文)`）。
 *
 * 于是"拷到别的机器/别的用户"就解不开了 —— 和手机上的 Android Keystore 是同一档思路
 *（差别只在手机的主密钥在硬件里，电脑的在 DPAPI 的用户密钥下）。
 *
 * ## 老数据能平滑升上来
 *
 * 早先是**裸密钥文件**（32 字节、无前缀）。读到时自动用 DPAPI 包一层再写回，
 * 密文格式没变 —— **用户不用重新登录**。DPAPI 不可用（非 Windows / 缺 JNA）时退回裸密钥，
 * 只记一条警告，不让 App 起不来。
 *
 * ## 换了机器/用户会怎样
 *
 * 把 `%APPDATA%\NAI Studio` 拷到别的机器：主密钥解不开 → **换一颗新密钥并清掉已失效的旧密文**
 *（只记一条警告，然后就是"未配置"状态，用户重新填一次 token 即可）。
 * 这里刻意不做"静默失败"：早期实现是解不开就返回 null，结果 `putSecret` 什么都不干，
 * 用户重填 token 却存不下去、重启又变未配置，且毫无提示。
 *
 * ## 密文格式（与手机一致）
 *
 * `base64(IV):base64(密文)`，AES/GCM/NoPadding、128 位 tag。
 */
class FileSecretStore(private val file: File) : SecretStore {

    /** DPAPI 保护后的主密钥文件前缀（用它区分"裸密钥"这个老格式）。 */
    private val wrappedPrefix = "DPAPI1\n".toByteArray(StandardCharsets.US_ASCII)

    /** 主密钥；DPAPI 解不开时是 null（表现为"读不到任何密钥"，而不是崩）。 */
    private val key: SecretKeySpec? by lazy { loadOrCreateKey() }

    private fun loadOrCreateKey(): SecretKeySpec? {
        val keyFile = File(file.parentFile, "secret.key")
        val stored = runCatching { if (keyFile.exists()) keyFile.readBytes() else null }.getOrNull()

        // ---- 已有文件 ----
        if (stored != null && stored.isNotEmpty()) {
            // 老格式：32 字节裸密钥 → 包一层 DPAPI 写回（密文不动，用户无感）
            if (stored.size == KEY_BYTES && !stored.startsWithBytes(wrappedPrefix)) {
                val protected = dpapiProtect(stored)
                if (protected != null) {
                    runCatching { keyFile.writeBytes(wrappedPrefix + protected) }
                }
                return SecretKeySpec(stored, "AES")
            }
            // 新格式：DPAPI 解包
            if (stored.startsWithBytes(wrappedPrefix)) {
                val body = stored.copyOfRange(wrappedPrefix.size, stored.size)
                val plain = dpapiUnprotect(body)
                if (plain == null || plain.size != KEY_BYTES) {
                    // 换了机器/换了用户 → 旧密文**确实**解不开了。
                    // ⚠️ 这里不能"返回 null 就算完"：那样 `putSecret` 会静默什么都不做，
                    // 用户重新填了 token 却存不下去（重启又变未配置，且毫无提示）。
                    // 正确做法 = 换一颗新密钥、把已失效的旧密文清掉，让存储**重新可用**。
                    logWarn("SecretStore", "主密钥解不开（换机器/换用户？）→ 换新密钥并清空旧密文")
                    runCatching { if (file.exists()) file.delete() }
                    val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
                    val reprotected = dpapiProtect(fresh)
                    runCatching {
                        keyFile.writeBytes(
                            if (reprotected != null) wrappedPrefix + reprotected else fresh,
                        )
                    }
                    return SecretKeySpec(fresh, "AES")
                }
                return SecretKeySpec(plain, "AES")
            }
        }

        // ---- 首次运行：生成一颗，并按 DPAPI 落盘（DPAPI 不可用就退回裸存）----
        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val protected = dpapiProtect(fresh)
        runCatching {
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(if (protected != null) wrappedPrefix + protected else fresh)
        }
        return SecretKeySpec(fresh, "AES")
    }

    private fun readAll(): JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText(StandardCharsets.UTF_8)) else JSONObject()
    }.getOrElse { JSONObject() }

    private fun writeAll(obj: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(obj.toString(), StandardCharsets.UTF_8)
    }

    override fun putSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeSecret(key)
            return
        }
        val secretKey = this.key ?: return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
        val payload = b64(cipher.iv) + ":" + b64(encrypted)
        writeAll(readAll().put(key, payload))
    }

    override fun getSecret(key: String): String {
        val payload = readAll().optString(key, "")
        if (payload.isEmpty()) return ""
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) {
            removeSecret(key)
            return ""
        }
        val secretKey = this.key ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, unb64(parts[0]))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(unb64(parts[1])), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // 密钥换了/坏了 → 丢弃旧密文（与手机端同样的处理）
            removeSecret(key)
            ""
        }
    }

    override fun removeSecret(key: String) {
        val obj = readAll()
        if (!obj.has(key)) return
        obj.remove(key)
        writeAll(obj)
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray =
        java.util.Base64.getDecoder().decode(text)
}

/** 主密钥长度（AES-256）。 */
private const val KEY_BYTES = 32

/** 前缀比较（判"这个文件是裸密钥还是 DPAPI 包过的"）。 */
private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

/**
 * **Windows DPAPI**（按当前用户）包一段数据。
 *
 * `CryptProtectData` 的默认口径就是"当前用户 + 当前机器"：**换个用户或换台机器都解不开**，
 * 正好对上我们要挡的那一档（拷走 AppData 目录）。
 *
 * 失败返回 null（非 Windows、缺 JNA、被策略拦），调用方退回"裸密钥文件"并记一条警告 ——
 * 安全降级总比 App 起不来强。
 */
private fun dpapiProtect(data: ByteArray): ByteArray? =
    runCatching { com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data) }
        .onFailure { logWarn("SecretStore", "DPAPI 不可用，密钥将裸存（安全性降级）: $it") }
        .getOrNull()

/** 解开 [dpapiProtect] 包过的数据；解不开（换了机器/用户）返回 null。 */
private fun dpapiUnprotect(data: ByteArray): ByteArray? =
    runCatching { com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data) }
        .onFailure { logInfo("SecretStore", "DPAPI 解包失败（换机器/换用户？），旧密文按不可读处理") }
        .getOrNull()

object DesktopAppPaths : AppPaths {
    override val filesDir: File get() = appDir()

    /** 与手机一致：生成图落在 `images` 子目录下。 */
    override fun defaultImagesDir(): File = File(appDir(), "images").apply { mkdirs() }
}

/**
 * 电脑上的"保存到相册"。
 *
 * 手机上是 `MediaStore`（写系统相册）；电脑上没有"相册"这个东西，但用户要的语义是
 * **"这张图我在系统里找得到"** —— 所以把它另存一份到 Windows 的"图片"库下面、
 * 以 App 命名的子目录：`%USERPROFILE%\Pictures\NAI Studio\`。
 * 资源管理器左侧点「图片」就能看见，照片应用也会自动收录。
 *
 * 三个口径：
 *  · **另存一份**，不是搬走 —— 图库/缩略图/遮罩编辑全都按 App 私有目录里的路径工作
 *   （和手机上是一样的做法，见 `Storage.exportToOutputFolder` 的注释）；
 *  · **同名自动改名**（`名字 (2).png`），和手机相册的行为对齐，不覆盖用户已有的图；
 *  · **失败就返回 false**：调用方本来就把它当"可选步骤"（写不出相册不该影响已保存的原图）。
 */
object WindowsGallerySink : GallerySink {
    /** 相册子目录名（和 App 名一致，方便用户在"图片"里一眼找到）。 */
    private const val SUB_DIR = "NAI Studio"

    override fun putImage(file: File): Boolean = putImageInto(picturesDir(), file)

    /**
     * 真正干活的那一步（**抽出来是为了能测**：`picturesDir()` 指向用户真实目录，
     * 单测不能往那儿写）。目标目录由调用方给，其余规则一致。
     */
    internal fun putImageInto(dir: File?, file: File): Boolean = runCatching {
        if (dir == null || !file.isFile) return false
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = uniqueTarget(dir, file.name)
        file.copyTo(target, overwrite = false)
        true
    }.getOrDefault(false)

    /** Windows 的"图片"库：物理目录名固定是 `Pictures`（显示名才会本地化）。 */
    private fun picturesDir(): File? {
        val home = System.getProperty("user.home") ?: return null
        val pictures = File(home, "Pictures")
        // 有些精简/改过的系统没有这个目录 —— 那就建一个（用户目录一定可写）
        return File(pictures, SUB_DIR)
    }

    /** 同名自动改名（`名字 (2).png`），和手机相册行为对齐 —— 不覆盖用户已有的图。 */
    internal fun uniqueTarget(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var candidate = File(dir, fileName)
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}

/**
 * 图片编解码：`javax.imageio`（纯 JVM，**不需要引 Skia 解码**）。
 *
 * 口径与手机端对齐：
 *  · [size] 只读文件头（`ImageReader` 拿尺寸，不解整张）；
 *  · [scale] 的 `smooth` 映射到 Graphics2D 的插值提示（双线性 ↔ 最近邻）；
 *  · [pngBytes] / [jpegBytes] 走 ImageIO；**JPEG 不支持 alpha**，所以先铺一层不透明底再写。
 */
object DesktopImageIo : ImageIo {

    override fun size(path: String): Pair<Int, Int>? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        javax.imageio.ImageIO.createImageInputStream(file).use { stream ->
            val readers = javax.imageio.ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    override fun decodeFull(path: String): NativeImage? = runCatching {
        javax.imageio.ImageIO.read(File(path))?.let { DesktopNativeImage(it) }
    }.getOrNull()

    override fun decodeBytes(bytes: ByteArray): NativeImage? = runCatching {
        javax.imageio.ImageIO.read(bytes.inputStream())?.let { DesktopNativeImage(it) }
    }.getOrNull()

    override fun scale(image: NativeImage, width: Int, height: Int, smooth: Boolean): NativeImage {
        val source = (image as DesktopNativeImage).image
        if (source.width == width && source.height == height) return image
        val out = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            if (smooth) {
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
            } else {
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            },
        )
        g.drawImage(source, 0, 0, width, height, null)
        g.dispose()
        return DesktopNativeImage(out)
    }

    override fun fromArgb(pixels: IntArray, width: Int, height: Int): NativeImage {
        val out = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, width, height, pixels, 0, width)
        return DesktopNativeImage(out)
    }

    override fun pngBytes(image: NativeImage): ByteArray =
        write((image as DesktopNativeImage).image, "png")

    override fun jpegBytes(image: NativeImage, quality: Int): ByteArray {
        val source = (image as DesktopNativeImage).image
        // JPEG 没有 alpha 通道：先铺白底再写，否则透明区会变成黑块
        val opaque = java.awt.image.BufferedImage(source.width, source.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = opaque.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, source.width, source.height)
        g.drawImage(source, 0, 0, null)
        g.dispose()
        return write(opaque, "jpg", quality)
    }

    override fun toPng(bytes: ByteArray, maxDimension: Int): ByteArray? = runCatching {
        val source = javax.imageio.ImageIO.read(bytes.inputStream()) ?: return null
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return@runCatching write(source, "png")
        val scaled = scale(DesktopNativeImage(source), (source.width * maxDimension / longest).coerceAtLeast(1),
            (source.height * maxDimension / longest).coerceAtLeast(1), smooth = true)
        write((scaled as DesktopNativeImage).image, "png")
    }.getOrNull()

    override fun toComposeImage(image: NativeImage): ImageBitmap? = runCatching {
        // 走 skia：把 PNG 字节交给 skiko 解成 Compose 的 ImageBitmap（Compose Desktop 自带 skiko）
        org.jetbrains.skia.Image.makeFromEncoded(pngBytes(image)).toComposeImageBitmap()
    }.getOrNull()

    /**
     * 直接从 ARGB 像素建位图（画布编辑器每画一笔都要用）。
     *
     * 桌面上的坑：`toComposeImage` 是 **PNG 编码 + skia 解码**两趟，一张 832×1216 要几十毫秒，
     * 涂起来会明显拖手。这里改成直接喂 skia 的 `makeRaster`。
     *
     * 像素格式：skia 要 **BGRA_8888**，而我们的 `IntArray` 是 ARGB 打包整数。
     * 小端机器上 `0xAARRGGBB` 那个 int 落进内存正好是 `B,G,R,A`，所以按下标拆字节写进去即可
     * （写成 RGBA 的话颜色会红蓝对调 —— 这是这类代码最容易错的一处）。
     * alpha 用 `UNPREMUL`：我们的像素是非预乘的，直接画会偏亮/偏暗。
     */
    override fun composeImageOf(pixels: IntArray, width: Int, height: Int): ImageBitmap? = runCatching {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val count = width * height
        val bytes = ByteArray(count * 4)
        var index = 0
        var offset = 0
        while (index < count) {
            val color = pixels[index]
            bytes[offset] = (color and 0xFF).toByte()            // B
            bytes[offset + 1] = ((color ushr 8) and 0xFF).toByte()  // G
            bytes[offset + 2] = ((color ushr 16) and 0xFF).toByte() // R
            bytes[offset + 3] = ((color ushr 24) and 0xFF).toByte() // A
            index++
            offset += 4
        }
        val info = org.jetbrains.skia.ImageInfo(
            width,
            height,
            org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.UNPREMUL,
        )
        org.jetbrains.skia.Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
    }.getOrNull()

    override fun pixelsOf(image: NativeImage): IntArray {
        val src = (image as DesktopNativeImage).image
        return src.getRGB(0, 0, src.width, src.height, null, 0, src.width)
    }

    override fun hasAlpha(image: NativeImage): Boolean =
        (image as DesktopNativeImage).image.colorModel.hasAlpha()

    override fun decodeSampled(bytes: ByteArray, maxDimension: Int): NativeImage? = runCatching {
        val source = javax.imageio.ImageIO.read(bytes.inputStream()) ?: return null
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) {
            DesktopNativeImage(source)
        } else {
            val targetW = (source.width * maxDimension / longest).coerceAtLeast(1)
            val targetH = (source.height * maxDimension / longest).coerceAtLeast(1)
            // ⚠️ 用户 2026-09-20：「图库的缩略图狗牙多、模糊」——
            // **一步**双线性把 2000px 缩到 320px，采样点会漏掉大量像素，边缘出现锯齿 ✗。
            // 这里先**反复折半**缩到接近目标（每步 2:1 的双线性不会漏像素），最后再一步到位收尾 ——
            // 也就是图像处理里标准的"逐步降采样"，缩略图会干净很多。
            var current: NativeImage = DesktopNativeImage(source)
            while (current.width / 2 >= targetW && current.height / 2 >= targetH) {
                current = scale(current, current.width / 2, current.height / 2, smooth = true)
            }
            if (current.width == targetW && current.height == targetH) {
                current
            } else {
                scale(current, targetW, targetH, smooth = true)
            }
        }
    }.getOrNull()
    private fun write(image: java.awt.image.BufferedImage, format: String, quality: Int = 100): ByteArray {        val buffer = java.io.ByteArrayOutputStream()
        if (format == "jpg") {
            val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next()
            val params = writer.defaultWriteParam.apply {
                compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                compressionQuality = (quality / 100f).coerceIn(0f, 1f)
            }
            javax.imageio.ImageIO.createImageOutputStream(buffer).use { out ->
                writer.output = out
                writer.write(null, javax.imageio.IIOImage(image, null, null), params)
            }
            writer.dispose()
        } else {
            javax.imageio.ImageIO.write(image, format, buffer)
        }
        return buffer.toByteArray()
    }
}

/** 电脑侧的原生位图。 */
class DesktopNativeImage(val image: java.awt.image.BufferedImage) : NativeImage {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    /** 电脑上是 GC 管的，没有 native 内存要手动放 —— 与手机同名同义，空实现。 */
    override fun recycle() = Unit
}

object DesktopDeviceInfo : DeviceInfo {
    override val manufacturer: String get() = System.getProperty("os.name") ?: "Windows"
    override val model: String
        get() = (System.getenv("COMPUTERNAME") ?: "PC") + " " + (System.getProperty("os.arch") ?: "")
}
