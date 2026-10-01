package com.kallan.naistudio.platform

import java.io.File

/**
 * **平台层**：手机与电脑共用的代码只认这里的接口，不认 `Context` / `Uri` / `Bitmap`。
 *
 * ## 为什么这么设计
 *
 * 工程的规矩是「`naistudio-shared` 里不许出现 `import android.*`」，而共用代码又确实需要
 * 存设置、读密钥、写图片 —— 于是把这几件事抽成接口，两端各写一个实现：
 *
 * | 能力 | 手机 | 电脑 |
 * |---|---|---|
 * | [KeyValueStore] | SharedPreferences | `%APPDATA%` 下的文件 |
 * | [SecretStore] | Android Keystore（AES-GCM） | DPAPI（绑当前用户） |
 * | [AppPaths] | `filesDir` | `%APPDATA%\NAI Studio` |
 * | [GallerySink] | MediaStore 写相册 | 没有这回事（空实现） |
 * | [DeviceInfo] | `Build.MANUFACTURER/MODEL` | `os.name` |
 *
 * ## 一个刻意的设计：[KeyValueStore.edit] 沿用 SharedPreferences 的形状做
 *
 * `prefs.edit().putString(k, v).apply()` 这种写法在 `Storage.kt` 里有几十处。
 * 如果接口只给 `putString(k,v)`，那几十处**每一处都要改**（还要小心漏掉 `.apply()`）；
 * 沿用 SharedPreferences 的编辑器形状，`Storage` 里就只用把 `prefs` 的来源换掉，
 * 其余一行不动 —— 这种"零 diff 搬迁"能显著降低把手机版改坏的风险。
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String? = null): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int

    fun edit(): Editor

    /** 对齐 `SharedPreferences.Editor`：链式写入 + [apply] 落盘。 */
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun remove(key: String): Editor
        fun apply()
    }
}

/**
 * 密钥存储：**明文绝不落盘**。
 *
 * 手机上原来叫 `TokenVault`（Android Keystore 的 AES-GCM）；电脑上没有 Keystore，
 * 实现换成 Windows DPAPI。共用代码只调下面那几个扩展函数，不关心底下是什么。
 */
interface SecretStore {
    /** 读；缺失或解不开返回空串（解不开时顺手丢弃那条密文）。 */
    fun getSecret(key: String): String

    /** 写；值为空串等于删除。 */
    fun putSecret(key: String, value: String)

    fun removeSecret(key: String)
}

/** App 私有目录。 */
interface AppPaths {
    /** App 文档目录（历史索引、缩略图缓存等）。 */
    val filesDir: File

    /** 生成图的默认落盘根目录（手机上是 `filesDir/images`）。 */
    fun defaultImagesDir(): File
}

/** 把一张图塞进"系统相册"。手机 = MediaStore；电脑没有相册 → 返回 false。 */
interface GallerySink {
    fun putImage(file: File): Boolean
}

/** 设备信息（备份元数据里要记"从哪台设备导出的"）。 */
interface DeviceInfo {
    val manufacturer: String
    val model: String
}

/**
 * **一次笔采样**：数位板 / 触控笔报上来的一个点（[Platform.penInputModifier] 的出口 ✓）。
 *
 * 坐标是**那个 `Modifier` 所在组件的局部像素** ✓ —— 和 `pointerInput` 里拿到的
 * `PointerInputChange.position` **是同一套** ✓（桌面实现就是这么换的 ✓，见
 * `DesktopPlatform.penInputModifier` 的说明 ✓），所以调用方**可以直接喂给现成的坐标换算** ✓
 * （高级漫画那边就是原样喂给 `comicScreenToPagePixel` 的 ✓）。
 *
 * ⚠️ **压力 / 倾斜在"没有笔"时是"中性值"**（[penTool] = false → [pressure] = 1 ✓）：
 * 这条口径是给画笔用的 —— "没有压感"必须等于**老行为**（整宽一笔 ✓），
 * 绝不能是 0（0 会被 `radius = base*(0.25+0.75*p)` 当成"笔尖没碰到" ✗）。
 * 状态行要显示"无压感"时看的是 [penTool] / [toolName] ✓，不是看这个数 ✗。
 */
data class PenSample(
    /** 组件局部像素 ✓（左边距已经在实现里减掉了 ✓）。 */
    val x: Float,
    val y: Float,
    /** 压到笔尖上的力度 **0..1** ✓；[penTool] = false 时是 1（= 没有压感，按老行为画 ✓）。 */
    val pressure: Float = 1f,
    /** 倾斜（**度** ✓；桌面那三个原生后端报的就是度 ✓）。 */
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    /**
     * **笔杆旋转**（**度** ✓，0..360 ✓；硬件 / 后端不支持就是 0 ✓）。
     *
     * ⚠️ 用户 2026-09-20 已经**取消了旋转功能** ✓（网页版 `docs/brush-lab-simple.html` 把
     * `AngleControl` 收成 0 固定 / 1 自动 两态 ✓，Kotlin 这边照做 ✓）——
     * 所以这一格**不再喂给任何笔刷逻辑** ✓（`NibAngleControl.BARREL` 与
     * `ImageEditOps.barrelAngleDegrees()` 一并删掉了 ✓，见 `NibAngleControl` 的说明 ✓）。
     *
     * 它**留着**是因为还有**一个**用处：`AppState.notePenSample` 那行只读状态里显示它 ✓ ——
     * 用户点名要"**亲眼看到**这台机器到底报没报旋转" ✓（那是观测，不是画法 ✓）。
     *
     * 口径：
     *  · **单位是度**（0..360 ✓）—— 库里 `PenEvent.rotation` 报的是**弧度**，
     *    桌面实现在 `DesktopPlatform.toPenSample` 里换算 ✓（那一条写在那儿 ✓）；
     *  · **没有笔 / 硬件不报旋转**时是 0 ✓（和 [pressure] 的"中性值 = 1"不同：
     *    角度本来就以 0 为"没转"✓）；
     *  · ⚠️ `compose-stylus` 的 **Windows（RTS）后端目前根本没请求"旋转"这个量** ✓ ⇒
     *    电脑上这一格多半恒为 0 ✓（原来那条"用倾斜方位角兜底"的路已经随旋转一起删了 ✓）。
     */
    val rotation: Float = 0f,
    /** 笔尾橡皮（`PenTool.Eraser` ✓）—— 用户口径：**别让笔尾还得手动切** ✓。 */
    val eraser: Boolean = false,
    /** 硬件工具名（`Pen` / `Eraser` / `Mouse` / `Touch` / `None` ✓）—— 只给状态行看 ✓。 */
    val toolName: String = "",
    /**
     * 这一路**是不是笔**（笔尖 / 笔尾橡皮 ✓）：鼠标 / 触摸 / 压根没有数位板 = `false` ✓。
     *
     * 两个用处：① 画笔只在 `true` 时才吃压力 ✓（否则按 1 画 = 老行为 ✓）；
     * ② 状态行靠它区分"真的收到数位板信号"和"只是鼠标在动" ✓（用户点名要能看出来 ✓）。
     */
    val penTool: Boolean = false,
    /** 这一次是**哪一种**事件（[PenPhase] ✓）。 */
    val phase: PenPhase = PenPhase.Move,
)

/**
 * [PenSample.phase]：一次笔事件的四种形态。
 *
 * 取值与 `compose-stylus` 的 `PenEventType` **一一对应** ✓（Hover / Move / Press / Release ✓，
 * 那两个枚举的 ordinal 是 JNI 的 ABI ✗，所以这里自己再声明一份、不直接透出去 ✓）。
 */
enum class PenPhase { Hover, Press, Move, Release }

/**
 * `Modifier.penInput` 的默认节点 key。
 *
 * 为什么要有它：库里同一个 key 的两个节点**会互相顶掉**（后注册的把先注册的换掉 ✗）——
 * 所以同一个界面里同时活着的多个画布要**各给一个** ✓（[Platform.penInputModifier] 的
 * `key` 参数 ✓，调用方一个 `remember { Any() }` 就够 ✓）。
 */
object DefaultPenInputKey

/**
 * 平台能力总集：`Storage` / `AppState` 只依赖这一个对象。
 *
 * 收成一个而不是散着传，是为了**以后加能力不用改构造签名**。
 */
interface Platform {
    val kv: KeyValueStore
    val secrets: SecretStore
    val paths: AppPaths
    val gallery: GallerySink
    val device: DeviceInfo
    /** 图片编解码（手机 BitmapFactory / 电脑 ImageIO）。 */
    val images: ImageIo

    /**
     * **系统 UI 缩放**（Windows 的 125% / 150% 那种）。
     *
     * 为什么要它：电脑上那套 exe **不是 DPI 感知的**，Windows 会把整个窗口**位图拉伸**到物理像素
     * —— 文字是矢量的所以看着还行，**位图（缩略图）就被拉糊了** ✗（用户 2026-09-20：
     * 「图库的缩略图狗牙多、模糊」）。而 Compose 报的 `LocalDensity` 还是 1.0，
     * 于是"按 dp 算出来的请求尺寸"偏小 1.5 倍。
     *
     * 约定：**请求位图尺寸时要乘上它**（[com.kallan.naistudio.ui.FileImage] / 图库那几处都这么用），
     * 这样解码出来的像素和屏幕上真正占的物理像素 1:1，不会被拉伸。
     * 默认 1f —— 手机端本来就有真的 density，用不上这个。
     */
    val uiScale: Float get() = 1f

    /** App 版本号（手机 = `BuildConfig.VERSION_NAME`）。写进备份元数据用。 */
    fun installApplicationUpdate(file: java.io.File) { error("Application updating is unavailable in this build") }

    val appVersion: String

    /**
     * **无限画布的最大边长**（px ✓）—— 用户 2026-09-22 拍板：**桌面 4096 / 手机 3072** ✓
     *（理由见 `docs/69` §二 ✓：内存峰值 = 上限面积 × 2 张全尺寸表面 ⇒ 4096² ≈ 134 MB、
     * 3072² ≈ 74 MB ✓）。默认给桌面那一档；手机端实现里覆盖成 3072 ✓（手机那版随后做 ✓）。
     */
    val infiniteCanvasMaxSide: Int
        get() = com.kallan.naistudio.models.INFINITE_MAX_SIDE_DESKTOP

    /** 是不是"托管版"（手机上有 hosted / local 两个 flavor）。 */
    val hostedEdition: Boolean

    /**
     * 构建号（手机 = `BuildConfig.VERSION_CODE`）。
     *
     * 只给"关于"那一栏显示用；电脑版没有这个概念的等价物，给个固定值即可。
     */
    val versionCode: Int

    /** 构建时间戳（手机 = `BuildConfig.BUILD_STAMP`；电脑 = 构建日期）。 */
    val buildStamp: String

    /** 版本渠道（手机 = `BuildConfig.EDITION`，如 `local` / `hosted`；电脑 = `电脑版`）。 */
    val edition: String

    /**
     * 是不是 **debug 构建**（手机 = `BuildConfig.DEBUG`；电脑开发期恒为 true）。
     *
     * 只用来门控"诊断型日志"（例如生成页那条一次手势只收口一次的验收打点）——
     * 正式包里不该有这些噪音，但排查时又要能一键打开。
     */
    val debugBuild: Boolean

    /**
     * 让出**一帧**（错峰加载用）。
     *
     * 手机借 `AndroidUiDispatcher`（它同时是 Dispatcher 和 MonotonicFrameClock）；
     * 电脑用 Swing/AWT 的帧时钟。共用代码里只调这个，不碰平台 API。
     */
    suspend fun yieldFrame()

    /**
     * 另开一个**具名**的键值存储。
     *
     * 手机上 App 有几个独立的小 prefs 文件（`nai_store` 放设置/参数/历史索引，
     * `animadex_tool` / `tagcodex_tool` 放工具页的展示偏好）—— 与共用代码的语义无关，
     * 只是"另一格抽屉"。给名字而不是固定一个全局 store，是为了**保持文件布局不变**
     * （换布局 = 用户那些偏好被清一次）。
     */
    fun prefs(name: String): KeyValueStore

    /**
     * 收紧一段文字style的**行高留白**。
     *
     * 手机上对应 `PlatformTextStyle(includeFontPadding = false)`：默认字体在每行上下各留
     * 一块空白，像 `!` 这种单个字符塞进 18dp 圆圈里会被压低，必须关掉。
     * 电脑（Skia）没有"字体留白"这个概念，原样返回。
     *
     * 为什么走平台层而不是在共用 UI 里判断：`PlatformTextStyle` 的**构造签名两端不同**
     * （桌面那份根本没有 `includeFontPadding` 参数），在共用代码里写就编不过。
     */
    fun compactTextStyle(
        style: androidx.compose.ui.text.TextStyle,
    ): androidx.compose.ui.text.TextStyle

    /**
     * 把用户导入的字体文件（`<filesDir>/fonts/` 里的那一份）组装成 Compose 的 `FontFamily`
     * —— 高级漫画的"气泡 / 文本"用它渲染（用户口径：**可以导入字体**，打字 ✓）。
     *
     * ## 为什么走平台层（和 [compactTextStyle] 同一个理由）
     *
     * "从文件加载字体"这个 API **两端不同名**：
     *  · 电脑：`androidx.compose.ui.text.platform.Font(File)`（CMP / skiko 那份）；
     *  · 手机：`androidx.compose.ui.text.font.Font(File)`（androidx 那份）。
     * 共用树里写哪一个，另一端都会编不过 ✗ —— 所以只在这里声明，两端各自实现 ✓。
     *
     * **默认实现返回 `null` = 回落系统默认字体** ✓：
     *  · 字体库空着、用户把文件删了、文件坏了 —— 一律不能崩 ✗；
     *  · 手机端这一版不接自定义字体也完全没问题（拿到 null 就用默认字体 ✓，
     *    那条线一行都不用改 ✓）。
     */
    fun comicFontFamily(path: String): androidx.compose.ui.text.font.FontFamily? = null

    /**
     * **把一页里的一层矢量内容（气泡 / 文本）栅格化成像素** —— 高级漫画第 ⑦ 项「拼页导出」用
     *（`docs/43` §10.0000000：气泡与文本是矢量 ✗，位图合成 [com.kallan.naistudio.services.ComicComposer]
     * 只吃位图，中间这一厘米得有人补 ✓）。
     *
     * 电脑端用 `androidx.compose.ui.ImageComposeScene`（CMP / skiko 才有，**手机端没有这个 API** ✗）
     * 把 `screens/ComicOverlayExport.kt` 那个"只画不接手势"的层渲染到一张透明底画布上 ✓。
     *
     * ## 为什么走平台层（和 [comicFontFamily] 同一个理由）
     *
     * `ImageComposeScene` 只在 CMP 的桌面端存在，共用树里写它就等于让手机端编不过 ✗。
     * 只在这里声明、两端各自实现 ✓。
     *
     * **默认实现返回 `null` = 这个平台不栅格化矢量层** ✓：
     *  · 手机端这一版没接（拿到 null → 导出**只合位图层**，并**如实提示**"气泡 / 文本没进去"✗，
     *    绝不静默少东西 ✓）；
     *  · 电脑端才是这次交付的目标 ✓（`DesktopComicRaster` ✓）。
     *
     * ⚠️ 出口是 **PNG 字节**而不是像素数组：这一层马上就会被当成"一个位图层"喂给拼页合成
     *（`ComicComposer.compose` 吃的是**图片路径** ✓）——直接给 PNG 就少一次
     * "像素 → 位图 → 再压 PNG" 的来回 ✗（一整页 2480×3508 的像素数组本身也有 35 MB ✓ 不划算）。
     * 透明底、尺寸 = width×height 由实现保证 ✓。
     *
     * @param width/height 目标画布尺寸（= 出口页尺寸 ✓，和位图那几层**同一个画布** ✓）
     * @return **透明底 PNG 字节**；这个平台不支持 / 渲染失败 → null ✓
     */
    fun renderComicOverlayLayer(
        page: com.kallan.naistudio.models.ComicBoardPage,
        layerId: String,
        width: Int,
        height: Int,
    ): ByteArray? = null

    /**
     * **笔（数位板 / 触控笔）输入**：桌面端把 compose-stylus 的事件接到这里；
     * 其它平台返回 `Modifier`（= 没有笔压，退回鼠标路径 ✓）。用户 2026-09-20：
     * 「**compose-stylus 先接这个**」✓（他要拿它做绘画软件 ✓，所以这一批的重点是
     * **能跑起来 + 能看到压力** ✓，不是做完美笔刷 ✗）。
     *
     * ## 为什么走平台层（和 [comicFontFamily] / [renderComicOverlayLayer] 同一个理由）
     *
     * 桌面端要拿**真压感**只有一条路：原生（Windows = RealTimeStylus / RTS ✓，AWT 的鼠标管线
     * 把 pressure / tilt 全丢了 ✗）。库 `com.mohamedrejeb.stylus` 是 **JNI** 的，
     * 而它**只加在 `naistudio-desktop` 的依赖里** ✓（`naistudio-shared` 还要被手机线编译 ✗ ——
     * 那棵树绝不能多一个桌面原生库 ✗）。于是共用树只认这一个方法 ✓，
     * **默认实现 = 原样返回 `Modifier`** ✓：手机端一行都不用改 ✓，拿到的是"没有笔压" ✓。
     *
     * ## 调用方拿到什么
     *
     * [onSample] **每一次笔事件回调一次** ✓（Hover / Press / Move / Release 全在里面 ✓，
     * 看 [PenSample.phase] ✓）。两条既定口径：
     *  · 坐标 = **这个 `Modifier` 挂在哪个组件上，就是这个组件的局部像素** ✓
     *    （和 `pointerInput` 的 `position` 同一套 ✓ —— 挂在哪一层，换算就得用哪一层的那套 ✓）；
     *  · 压力 0..1 ✓（"没有笔"时是 1 = 老行为 ✓，见 [PenSample.pressure] 的 ⚠️ ✓）。
     *
     * ## ⚠️ 它**不吃鼠标事件**（这条要核 ✓）
     *
     * 桌面实现用的是库里那个 `Modifier.penInput`：它的节点虽然实现了 `PointerInputModifierNode`，
     * 但 `onPointerEvent` 里**只读不 `consume()`** ✓（只用来判"光标在这一块上"，见库源码
     * `PenInputNode.onPointerEvent` ✓）—— 所以鼠标 / 触摸那条老路一个字都不用改 ✓。
     * **实测层面（真的没吃掉）要用户在真机上确认** ✓，我这边没有数位板 ✗。
     *
     * @param key 节点 key（同一个界面里同时活着的画布**要各给一个** ✓ —— 库里同 key 的节点
     *   会互相顶掉 ✗；一个 `remember { Any() }` 就够 ✓）。不传用 [DefaultPenInputKey] ✓。
     * @param onSample 每次笔事件回调一次 ✓（**别在这里写盘 / 重建位图** ✗，它一秒能来上百次 ✓）。
     */
    fun penInputModifier(
        key: Any = DefaultPenInputKey,
        onSample: (PenSample) -> Unit,
    ): androidx.compose.ui.Modifier = androidx.compose.ui.Modifier

    /**
     * 打开一个"引用"做读/写。
     *
     * 这里的 `ref` 是**字符串**而不是各平台的类型：手机上是 `content://…` 的 URI 字符串，
     * 电脑上就是文件路径 —— 共用代码只当它是一个不透明句柄传下去。
     *
     * @return null = 打不开（调用方按失败处理）
     */
    fun openInput(ref: String): java.io.InputStream?
    fun openOutput(ref: String): java.io.OutputStream?

    /**
     * 把用户选的那个"目录/文件"的授权**长期握在手里**。
     *
     * 手机（SAF）特有：系统给的授权默认只在本次进程有效，不重新握一次的话，
     * 重启后设置里显示着目录、但图不再另存过去。电脑上就是一个普通路径，直接返回 true。
     */
    fun takePersistablePermission(ref: String): Boolean

    /**
     * 把一张图另存到用户选定的目录。
     *
     * · 手机：`treeUri` 是 SAF 的 tree URI（`content://…`），走 `DocumentsContract`；
     * · 电脑：`treeUri` 就是一个普通目录路径，直接拷过去。
     *
     * @return true = 真的写出去了；false = 没配 / 写不动（调用方按"可选步骤"处理，不当失败）
     */
    fun exportToUserDir(source: File, treeUri: String): Boolean
}

/**
 * 图片编解码。
 *
 * ## 为什么需要这一层
 *
 * 共用的逻辑层（遮罩编解码、绘画合成、缩略图）到处要"解码一张图 / 缩放 / 压成 PNG"，
 * 而这些在手机上是 `android.graphics.Bitmap`、电脑上是 `java.awt.image.BufferedImage`。
 * 于是把它们抽成 [NativeImage] + 这一组操作 —— 逻辑层只认接口，两边各自实现。
 *
 * 只放**逻辑层真的用到**的那几个操作，不搞通用图像库。
 */
interface ImageIo {
    /** 只读文件头拿像素尺寸（不解码整张图）。解不开返回 null。 */
    fun size(path: String): Pair<Int, Int>?

    /** 从字节全尺寸解码（局部重绘的合成要原尺寸）。解不开返回 null。 */
    fun decodeBytes(bytes: ByteArray): NativeImage?

    /** 全尺寸解码（按路径）。OOM / 解不开返回 null。 */
    fun decodeFull(path: String): NativeImage?

    /** 缩放。[smooth] = true 走滤波（底图缩放），false 走最近邻（遮罩要"非黑即白"）。 */
    fun scale(image: NativeImage, width: Int, height: Int, smooth: Boolean): NativeImage

    /** 从 ARGB 像素数组建图（画遮罩用）。 */
    fun fromArgb(pixels: IntArray, width: Int, height: Int): NativeImage

    /** 读出 ARGB 像素（局部重绘的合成要按像素算）。 */
    fun pixelsOf(image: NativeImage): IntArray

    /** 有没有 alpha 通道（决定缩略图存 PNG 还是 JPEG）。 */
    fun hasAlpha(image: NativeImage): Boolean

    /**
     * 从字节解一张**降采样**的图（长边 ≤ [maxDimension]）。
     *
     * 用在两处：服务端推来的流式预览帧、列表缩略图。手机上靠 `inSampleSize`，
     * 电脑上先整张解再缩放 —— 语义一致（结果长边 ≤ maxDimension）。
     */
    fun decodeSampled(bytes: ByteArray, maxDimension: Int): NativeImage?

    fun pngBytes(image: NativeImage): ByteArray

    fun jpegBytes(image: NativeImage, quality: Int): ByteArray

    /**
     * 任意图片字节 → PNG 字节；长边超过 [maxDimension] 先降采样（相机大图整张解码会 OOM）。
     * 解不开返回 null。
     */
    fun toPng(bytes: ByteArray, maxDimension: Int = 4096): ByteArray?

    /** 给 Compose 用（流式预览帧 / 缩略图 / 查看器）。 */
    fun toComposeImage(image: NativeImage): androidx.compose.ui.graphics.ImageBitmap?

    /**
     * **直接从 ARGB 像素**建一张给 Compose 用的位图。
     *
     * 为什么不能拿 [toComposeImage] 顶：那条路是"编码成 PNG 再解回来"（两端实现都是），
     * 一张 832×1216 要几十毫秒 —— 而画布编辑器**每画一笔**都要重建一张显示位图，
     * 走那条路会明显拖手。这个是直接搬像素。
     *
     * 尺寸不合法或建不出来返回 null。
     */
    fun composeImageOf(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): androidx.compose.ui.graphics.ImageBitmap?
}

/**
 * 一张已解码的位图（手机 = `android.graphics.Bitmap`，电脑 = `BufferedImage`）。
 *
 * ⚠️ 用完要 [recycle]（手机上是真的释放 native 内存；电脑上是个空操作）。
 */
interface NativeImage {
    val width: Int
    val height: Int
    fun recycle()
}

/**
 * 给**界面层**用的图片能力。
 *
 * `ui/` 里的文件（查看器、缩略图）只拿得到路径，拿不到 `AppState`；
 * 由两端入口 provide 一次即可（手机 `MainActivity`、电脑 `Main.kt`）。
 */
val LocalImageIo = androidx.compose.runtime.staticCompositionLocalOf<ImageIo> {
    error("LocalImageIo 没提供：入口要 CompositionLocalProvider(LocalImageIo provides platform.images)")
}

/**
 * 给**界面层**用的整套平台能力（[LocalImageIo] 的超集）。
 *
 * 为什么还要多一个：`ui/` 里的文件除了图片解码，还要拿 `paths.filesDir`（缩略图磁盘缓存
 * 就写在那儿）、`kv`、`device` 之类。逐个 provide 会变成一串 CompositionLocal，
 * 直接给整个 [Platform] 最省事；两端入口 provide 一次即可。
 *
 * 只做解码的调用方继续用 [LocalImageIo] 也行（它是同一份 `platform.images`，不冲突）。
 */
val LocalPlatform = androidx.compose.runtime.staticCompositionLocalOf<Platform> {
    error("LocalPlatform 没提供：入口要 CompositionLocalProvider(LocalPlatform provides platform)")
}

// ---------------------------------------------------------------------------
// token 的键名与语义（**键名是存储契约的一部分，不能改** —— 改了用户就得重新登录）
// ---------------------------------------------------------------------------

/** 流式预览帧 / 缩略图的最长边：预览只为看构图，不需要全分辨率。 */
const val PREVIEW_MAX_DIMENSION = 1080

private const val KEY_TOKEN = "nai_token"
private const val KEY_ACCESS = "account_access_token"
private const val KEY_REFRESH = "account_refresh_token"

/**
 * **第三方网关那把 Key**（用户 2026-09-26：「第三方和官方的 api **分开存储**啊，可切换」）。
 *
 * 和官方的 `nai_token` **各存各的**：来回切的时候两把都留着，不用重填。
 * ⚠️ 新键名，老版本没有 ⇒ 读出来就是空串（不存在迁移问题）；
 *    也**不要**把老 token 搬过来当网关 Key —— 官方 token 和 `nai-...` 虚拟 Key
 *    是两种东西，搬过去只会让人以为连上了。
 */
private const val KEY_GATEWAY_TOKEN = "gateway_token"

/** 对应参考实现的 `Storage.setToken`。 */
fun SecretStore.setToken(token: String) = putSecret(KEY_TOKEN, token)

fun SecretStore.getToken(): String = getSecret(KEY_TOKEN)

fun SecretStore.clearToken() = removeSecret(KEY_TOKEN)

// ---- 第三方网关那把 Key（和上面那把完全独立）----

fun SecretStore.setGatewayToken(token: String) = putSecret(KEY_GATEWAY_TOKEN, token)

fun SecretStore.getGatewayToken(): String = getSecret(KEY_GATEWAY_TOKEN)

fun SecretStore.clearGatewayToken() = removeSecret(KEY_GATEWAY_TOKEN)

fun SecretStore.setAuthTokens(accessToken: String, refreshToken: String) {
    putSecret(KEY_ACCESS, accessToken)
    putSecret(KEY_REFRESH, refreshToken)
}

fun SecretStore.getAccessToken(): String = getSecret(KEY_ACCESS)

fun SecretStore.getRefreshToken(): String = getSecret(KEY_REFRESH)

fun SecretStore.clearAuthTokens() {
    removeSecret(KEY_ACCESS)
    removeSecret(KEY_REFRESH)
}
