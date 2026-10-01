
// ---------------------------------------------------------------------------
// NAI Studio · 电脑版（Windows）
//
// 技术路线：Kotlin + **Compose Multiplatform Desktop**（与手机同一套 Compose 代码）。
// 依据见 docs/25-方案-桌面版exe.md：文档里明确「不用 Electron / WebView / Flutter」，
// 而现有 3 万行里绝大多数本来就是可移植的 Compose + 纯 Kotlin。
//
// 版本对齐（重要）：CMP **1.8.2** 对应 Jetpack Compose 1.8.2 + Material3 **1.3.2**，
// 与手机工程用的 `composeBom = 2025.06.01` 同档 —— API 差异最小。
// Core 编译器插件版本必须与 Kotlin 一致（都是 2.1.21）。
// ---------------------------------------------------------------------------

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

repositories {
    mavenCentral()
    google()
    // KCEF（Chromium 内核，见下面那一段依赖说明）要的 Jogamp 仓库 —— 少了它 `jogl` 系列解析不到。
    maven("https://jogamp.org/deployment/maven")
}

kotlin {
    jvmToolchain(17)
}

/**
 * **唯一**的源码根：`naistudio-shared`（2026-09-16 起）。
 *
 * 手机工程与桌面工程**编的是同一棵树**。门槛只有一条、而且是自动执行的：
 * 树里不许出现 `import android.*` —— 一混进来，这边立刻编不过。
 *
 * 还没搬过去的（`store/state/screens/ui` 里那些碰 Android 的）先留在手机工程里，
 * 按"接口 + 两端实现"逐个搬 —— **每搬一个，能共用的代码就多一个**。
 */
val sharedSrc = file("../naistudio-shared/src/main/kotlin")

sourceSets["main"].kotlin.srcDir(sharedSrc)

/**
 * ⚠️ **共用树的 `resources` 也要带上** ✗（2026-09-24 加 ✓）——
 * `dictionary.tsv`（提示词词典，用户「补充词典库」✓）放在 `naistudio-shared/src/main/resources` ✓。
 * 只带 `kotlin` 不带 `resources` 的话，`getResourceAsStream("/dictionary.tsv")` 会**静默返回 null** ✓
 * —— 词典整个不生效、而且**一个错都不报** ✗（正是最难查的那类 ✓）。手机线那边同样加了这一条 ✓。
 */
sourceSets["main"].resources.srcDir(file("../naistudio-shared/src/main/resources"))

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // ⚠️ **不要**加 `compose.materialIconsExtended`：实测它一个包就 **37.7 MB**
    //（整套 Material 图标），而手机版用的是 core 图标集 + 自绘路径（见 ui/AppIcons.kt），
    // 共用树里没有任何地方用到 extended。
    //
    // 但 **core 必须补上**：手机上 `androidx.compose.material.icons.Icons` 是 material3
    // 的传递依赖，CMP 的 material3 却**不带**它 —— 桌面这边少了它，共用树里凡是
    // `Icons.Filled.KeyboardArrowDown` 之类的引用全部 "Unresolved reference 'icons'"（实测踩过）。
    // core 只有几十个矢量（约 1MB），和手机端的图标集正好一致。
    // ⚠️ 版本只能写 **1.7.3**：CMP 从 1.8 起**不再发布** material-icons
    //（1.8.2 在 mavenCentral/google 都查无此物，实测报 Could not find …material-icons-core:1.8.2）。
    // 它只依赖 ui-graphics（ImageVector / PathParser），和 1.8.2 运行时二进制兼容。
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    // 与手机一致：运行时第三方依赖只有 OkHttp（JSON 用 org.json 的正式构件）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    // 共用树里的 AppState 是 ViewModel（转屏不丢状态）→ 桌面也要这个多平台版
    // Windows **DPAPI**：把 AES 主密钥按"当前 Windows 用户"保护起来
    //（`jna-platform` 里有现成的 `Crypt32Util.cryptProtectData/cryptUnprotectData` 封装）。
    // 为什么必须要它：没有 DPAPI 时"密钥文件 + 密文"躺在一起，拷到别的机器就能解开；
    // 有了它，密文离开这台机器/这个用户就解不开 —— 和手机上 Android Keystore 是同一档思路。
    // ⚠️ **只加在电脑端**：手机端的运行时依赖仍然只有 OkHttp（见 naistudio-shared/README）。
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    // 桌面端的"系统返回"（共用外壳 `StudioShell` 的 BackHandler 要走 UiHost 钩子，
    // 电脑那份实现在 `androidx.compose.ui.backhandler` 里）。
    // ⚠️ 它虽然是 `compose.desktop.currentOs` 的**运行时**传递依赖（jpackage 输入里有这个 jar），
    // 却**不在编译类路径**上 —— 不显式声明就是 "Unresolved reference 'BackHandler'"（实测）。
    implementation("org.jetbrains.compose.ui:ui-backhandler:1.8.2")
    // ⚠️ 桌面端必须有 Main dispatcher：`viewModelScope` 用的是 `Dispatchers.Main`，
    // 而 JVM 上只有 kotlinx-coroutines-swing 会把 AWT EDT 注册成 Main。少了它，
    // 一构造 AppState 就报 "Module with the Main dispatcher is missing"（实测踩过）。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    // **数位板 / 触控笔压感输入**（用户 2026-09-20：「compose-stylus 先接这个」）。
    //
    // 为什么是它：Windows 上要拿到**真压感**，只能走 RealTimeStylus（RTS）——
    // AWT 的鼠标管线把 pressure / tilt 全丢了。这个库的桌面端就是 JNI 直连
    // RTS（另两端 Cocoa / X11+XInput2），**原生 DLL 已经打进它的 jar**
    //（`native/windows-x86_64/stylus.dll`，见 jar 内的 NativeLoader），
    // 运行时自己解包到临时文件再 `System.load` —— **不需要装 MSVC、不用手工 load** ✓。
    //
    // ⚠️ **只加在电脑端**：共用树（`naistudio-shared`）还要被手机线编译，
    // 那边一行都不动（`Platform.penInputModifier` 默认实现 = `Modifier` ✓）。
    //
    // ⚠️⚠️ **必须带这几条 `exclude`**（实测）：`stylus-compose-jvm:0.1.6` 是按
    // **CMP 1.9.3 + Kotlin 2.2.21** 编的，它的 *runtime* 依赖里点名了
    // `org.jetbrains.compose.{desktop,runtime,foundation,ui}:1.9.3` —— 不排掉的话
    // Gradle 会按"高版本赢"把**本工程的 Compose 整体抬到 1.9.3**
    //（实测 `org.jetbrains.compose.desktop:desktop:1.8.2 -> 1.9.3` ✗），
    // 那就成了"**用 1.8.2 编、拿 1.9.3 跑**" ✗ —— 本工程头上的版本对齐说明
    //（CMP 1.8.2 / Kotlin 2.1.21）就白写了。
    // 这几条只是**不让它替我们升级 Compose**；它要用的 API（`Modifier.Node` /
    // `LayoutAwareModifierNode` / `SkiaLayer.contentHandle` ✓）1.8.2 那份 skiko 0.9.4.2 里都有 ✓（已核 ✓）。
    // `kotlinx-coroutines-swing` 同理：本工程自己写了 1.8.0（`Dispatchers.Main` ✓），不需要它带的 1.10.2 ✓。
    implementation("com.mohamedrejeb.stylus:stylus-compose:0.1.6") {
        exclude(group = "org.jetbrains.compose.desktop")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
        // ⚠️ 这条**必须和下面 `stylus` 那条一起写**：`exclude` 是**按路径**生效的 ✓ ——
        // 只写在 `stylus` 那一侧的话，`stylus-compose → stylus → stylus-jvm → coroutines-core`
        // 这条**绕过去的路**照样成立 ✗（实测：只写一侧时协程还是被抬到 1.10.2 ✗）。
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // ⚠️ **核心模块必须再显式写一遍**（实测的坑 ✓）：`stylus-compose-jvm:0.1.6` 的 Gradle 元数据里
    //   `stylus` 核心**只挂在 `jvmRuntimeElements`（runtime）上** ✗，可它公开 API 的签名里偏偏露着
    //   核心的 `PenEvent`（`Modifier.penInput { event: PenEvent -> }` ✓）——
    //   不写这一行就是一片 `Unresolved reference 'PenEvent'` / `Cannot access class 'PenEvent'` ✗
    //（实测报错见回报 ✓）。这是**它 0.1.6 元数据的缺陷** ✓，不是我们写错 ✓；
    //   而我们自己的 `penInputModifier` 实现本来就要读 `PenEvent.tool/type/x/y` ✓。
    // **原生 DLL 就在这个 jar 里** ✓（`stylus-jvm-0.1.6.jar` 的 `native/windows-x86_64/stylus.dll` ✓，
    //   610 KB ✓）—— 所以这一行同时也是"运行时原生库从哪来"的来源 ✓。
    // 排除 `kotlinx-coroutines-core`：它只在 **runtime** 依赖里点了 **1.10.2** ✗，
    // 而本工程的协程是跟着 `kotlinx-coroutines-swing:1.8.0` 来的 ✓ —— 不让它顺手升级 ✓
    //（这个库整个 `PenInputSource` 只用了 `ConcurrentHashMap` / `ShutdownHook`，不吃新协程 ✓）。
    implementation("com.mohamedrejeb.stylus:stylus:0.1.6") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // ---- 网页笔刷（Chromium 内核，用户 2026-09-20 口径：「直接搬，不修」）----
    //
    // 用户口径是关键：**不修现有那套 Kotlin 笔刷管线**，直接把 `docs/brush-lab.html` 这一页
    // 原样搬进软件里跑。技术结论（实测，见 `tools/webview-spike`）：
    //  · Compose 桌面**不能**拿来装 CEF ✗（实测：`compose-webview-multiplatform` 把
    //    `browser.uiComponent` 加进界面的那条分支走不到 —— KCEF 默认开了
    //    `windowless_rendering_enabled`，库拿 `getWindowlessFrameRate()` 当"是不是 OSR"的判据，
    //    结果走了 else 分支：**只给组件设了个尺寸、根本没加进任何容器** ⇒ 窗口里什么都没有 ✗）；
    //  · 改用 **KCEF + 普通 Swing `JFrame`** 装它 ✓ —— 实测同一台机器上这套**能画出来**
    //    （截图见 `tools/webview-spike/spike-window.png`：整页 SAI 面板 + 画布 + 引擎真落笔
    //    `dabs=1438` ✓）。
    //
    // ⚠️ 只依赖 `kcef` 一个：我们自己装组件（`createBrowser(...).uiComponent` 加进 JFrame ✓），
    //    不再用 `compose-webview-multiplatform`（它那层封装正好是上面那个坑 ✗）。
    //
    // ⚠️ 首次运行会**联网下载 CEF 运行时**（约 150 MB，落到 `installDir`，见
    //    `WebBrushWindow.kt`）—— 交付时那一份要**随镜像一起发**，否则用户第一次点要等下载。
    implementation("dev.datlag:kcef:2024.04.20.3")

    // ---- 测试 ----
    // 平台层（键值存储 / 密钥 / 图片编解码 / 相册 / 路径）是"电脑版正确性"所在，
    // 而这些代码路径**用户不常走**（比如"同名自动改名"、密文迁移），所以要有单测。
    // 用 JUnit 4：与手机工程同一套（`app/src/test` 的 400 条就是这么写的），依赖也早就在本地缓存里。
    testImplementation("junit:junit:4.13.2")
}

/**
 * KCEF 要的 JVM 开关（`gradle run` 时生效；**打出来的镜像那一份要另加**，见
 * `docs/45-交付-桌面镜像加新依赖.md` 与 `app\NAI Studio.cfg` 的 `java-options`）。
 *
 * 为什么是这两条：JCEF 在 Windows 上要往 `sun.awt` 里放东西（它自己那套 heavyweight 画布），
 * 不给 `--add-opens` 就在初始化时炸（KCEF 的 COMPOSE.md / README.desktop.md 都点名了 ✓）。
 */
afterEvaluate {
    tasks.withType<JavaExec> {
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
    }
}

compose.desktop {
    application {
        mainClass = "com.kallan.naistudio.desktop.MainKt"

        // 打包成"自带运行时的绿色版"：`gradle createDistributable` 出一个文件夹 + exe，
        // 双击即用、**不需要用户装 Java**。要正规安装器（exe/msi）得再装 WiX，见 docs/25 §5。
        nativeDistributions {
            // ⚠️ **不设 targetFormats**：默认只出 app-image（`createDistributable` 任务）——
            // 一个文件夹 + `NAI Studio.exe`，双击即用、不用装 Java，也**不需要 WiX**。
            // 要正规安装器（exe/msi）得先装 WiX，再 `targetFormats(TargetFormat.Exe)`，
            // 见 docs/25 方案 §5。
            packageName = "NAI Studio"
            packageVersion = "1.1.142"
            description = "NAI Studio 电脑版"
            vendor = "NAI Studio"
            // 裁剪运行时用到的模块：Compose 桌面端（skiko）需要这几个，
            // 少一个就会在 jlink 出来的运行时里报模块缺失。
            modules("java.instrument", "jdk.unsupported", "java.naming", "jdk.crypto.ec")
            windows {
                menu = true
                shortcut = true
                // ⚠️ 这里**不写** icon：CMP 1.8.2 这个 block 里没有 `icon` 属性
                //（写了是 "Unresolved reference: icon"，实测）。图标在**实际交付走的那条路**
                // 上传给 jpackage：`tools\make_desktop_exe.ps1` 的 `--icon <icons\app.ico>`。
                // 图标本身是 tools\make_app_icon.ps1 按 brand/nai-icon.svg 的坐标**生成**的。
            }
        }
    }
}

/**
 * 单测要**落到真实文件系统**（图库目录、缩略图缓存、密钥文件、相册），
 * 所以给它一套**隔离档案**：`APPDATA` 与 `user.home` 都指到 `build/test-home` 下。
 *
 * 为什么两条都要改：`DesktopAppPaths` 读 `APPDATA`，而 `WindowsGallerySink`
 * 按 `user.home/Pictures` 找"图片"库 —— 只改一条的话，测试就会往用户**真实的**
 * "图片"目录里丢文件。
 */
tasks.withType<Test>().configureEach {
    val isolatedHome = layout.buildDirectory.dir("test-home").get().asFile
    environment("APPDATA", isolatedHome.absolutePath)
    systemProperty("user.home", isolatedHome.absolutePath)
    testLogging {
        events("failed")
        // ⚠️ 批测的**实测数字**都走 `println`（判据 / A4 计时 / 探针 ✓）——
        // 默认不打印（全量跑 380+ 条会淹掉输出 ✓），要看数字时加 `-DdshTestOut=1` ✓。
        if (System.getProperty("dshTestOut") != null) events("standardOut")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/**
 * 把图标 PNG 打进 jar 的 `icon/` 目录。
 *
 * 为什么不放 `src/main/resources`：图标是**生成物**（`tools\make_app_icon.ps1` 按
 * `brand/nai-icon.svg` 的坐标画），放 `icons/` 下更好找；jar 任务直接从那儿取一份进包，
 * 运行时就当资源读（`Main.kt` 用它设窗口图标，见那里的说明）。
 */
tasks.named<Jar>("jar") {
    from(layout.projectDirectory.dir("icons")) {
        include("app-256.png")
        into("icon")
    }
}

/**
 * 把"跑起来需要的所有 jar"（自己的 + 全部依赖）收进一个目录。
 *
 * 为什么要它：CMP 那个 `createDistributable` 在 Windows 上会**先下载 WiX** 才能往下走
 * （实测 8 分多钟后从 GitHub 下载超时失败），而 app-image 根本不需要 WiX。
 * 所以改成：让 Gradle 备好输入目录 + 复用 CMP 已经 jlink 出来的运行时，
 * 再**手工调一次 jpackage**（见 tools/make_desktop_exe.ps1）。少一个网络依赖，快且可控。
 */
tasks.register<Sync>("desktopInput") {
    into(layout.buildDirectory.dir("jpackage-input"))
    from(tasks.named("jar"))
    from(configurations.runtimeClasspath)
}

