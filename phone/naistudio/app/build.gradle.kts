import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 项目专用签名身份（`keystore.properties` + `keystore/` 跟着工程走）。
// 目的：**任何机器/会话**构建出来的包都用同一把密钥，能互相覆盖安装 ——
// 之前不同机器用各自 IDE 的 debug 密钥签，装机就会 INSTALL_FAILED_UPDATE_INCOMPATIBLE。
// 找不到配置时自动回落到 Gradle 默认 debug 签名（不影响别人 clone 后能构建）。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasProjectKeystore = keystoreProps.getProperty("storeFile") != null &&
    rootProject.file(keystoreProps.getProperty("storeFile")).exists()

// ---------------------------------------------------------------------------
// 版本号：**每次构建自动生成**，唯一且单调递增 —— 不用手改，也就不会忘。
//   · versionName = 0.2.xx，xx 是**迭代次数**（两位数，从 03 起，每次构建 +1）：
//     次数存在工程根的 `version-iteration.txt` 里，读一次就把文件写成下一次的值，
//     所以"这一包是 0.2.03、下一包是 0.2.04"是自动的，人只需要报号。
//   · versionCode = 2026-01-01 起的秒数：单调递增、同秒内不会重复，
//     而且远小于 Google Play 的上限 2100000000（按这个速率能用几十年）。
//   · 构建时间戳/版本形态另外放进 BuildConfig（BUILD_STAMP），「关于」里显示，
//     需要回溯"这一包是哪一次构建"时看它，不必塞进 versionName。
// ---------------------------------------------------------------------------
val versionIterationFile = rootProject.file("version-iteration.txt")
/** 本次构建用的迭代号（文件里的值；文件不存在就从 1 起）。 */
val versionIteration: Int = versionIterationFile
    .takeIf { it.exists() }
    ?.readText()?.trim()?.toIntOrNull()
    ?: 1
// 版本号体系（用户 2026-09-16 定）：**1.1.x**，x 从 1 开始、每出一个包 +1。
// 之前是 `0.2.xx`（两位补零）。迭代号仍存在同一个文件里，机制不变
// （读一次 → 这一包用旧值 → 写回 +1），只是不再补零、前缀换成 1.1。
val semverBase = "1.1.$versionIteration"
val epochSeconds2026 = 1_767_225_600L // 2026-01-01T00:00:00Z
val buildStamp = SimpleDateFormat("yyMMddHHmmss", Locale.US).format(Date())
val autoVersionCode = ((System.currentTimeMillis() / 1000L) - epochSeconds2026).toInt()

// 构建过一次就把迭代号 +1 写回文件（放在 assemble 的 doFirst：**这一包用旧值，下一包自动 +1**）。
// 构建失败也算消耗掉一个号 —— 号只要求单调，不要求连号。
var versionBumped = false
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    doFirst {
        if (!versionBumped) {
            versionBumped = true
            versionIterationFile.writeText((versionIteration + 1).toString())
            logger.lifecycle(
                "版本：本次 $semverBase；下一次 0.2.%02d".format(versionIteration + 1),
            )
        }
    }
}

android {
    namespace = "com.kallan.naistudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kallan.naistudio"
        minSdk = 26
        targetSdk = 36
        // 两个都自动生成，见文件开头注释
        versionCode = autoVersionCode
        versionName = semverBase
        buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")
        buildConfigField("String", "SEMVER_BASE", "\"$semverBase\"")
    }

    // -----------------------------------------------------------------------
    // 两个版本（同一套代码，出两个包）：
    //   hosted —— 完整版：带自建账号体系（登录门禁 / 托管代理 / 云同步入口）
    //   local  —— **纯净版**：无账号、纯本地直连 NovelAI（自填 API token）
    // 构建命令：assembleHostedDebug / assembleLocalDebug
    // 装哪个都一样（applicationId 相同，同一个 App 的两种版本，覆盖安装即可切换，数据保留）
    // -----------------------------------------------------------------------
    flavorDimensions += "edition"
    productFlavors {
        create("hosted") {
            dimension = "edition"
            buildConfigField("boolean", "HOSTED_EDITION", "true")
            buildConfigField("String", "EDITION", "\"hosted\"")
        }
        create("local") {
            dimension = "edition"
            buildConfigField("boolean", "HOSTED_EDITION", "false")
            buildConfigField("String", "EDITION", "\"local\"")
        }
    }

    signingConfigs {
        if (hasProjectKeystore) {
            create("project") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 用项目签名身份，保证换机器构建也能覆盖安装
            if (hasProjectKeystore) signingConfig = signingConfigs.getByName("project")
        }
        release {
            // 上架用 release 密钥另行配置（不要用上面那把共享调试密钥）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 备份文件里要写 App 版本号（versionName），所以打开 BuildConfig
        buildConfig = true
    }

    // -----------------------------------------------------------------------
    // **共用源码树**（2026-09-16 起）：手机与电脑共用同一份代码。
    //
    //   naistudio-shared/src/main/kotlin/…   ← 只放"零 Android 依赖"的代码
    //   app/src/main/java/…                  ← 手机专属（Activity / 平台实现 / 待搬的页面）
    //
    // ⚠️ 进 `naistudio-shared` 的唯一门槛：**不许 import android.\***。
    //   `naistudio-desktop`（Compose Multiplatform）直接编那棵树，一旦混进 Android API 它立刻编不过 ——
    //   所以这条门槛是**自动强制执行**的，不靠自觉。
    // -----------------------------------------------------------------------
    sourceSets {
        getByName("main") {
            java.srcDir(rootProject.file("../naistudio-shared/src/main/kotlin"))
            // ⚠️ **共用树的 `resources` 也要带上** ✗（2026-09-24 加 ✓）——
            //    `dictionary.tsv`（提示词词典，用户「补充词典库」✓）就放在那儿 ✓。
            //    只带 `kotlin` 不带 `resources` 的话，`getResourceAsStream("/dictionary.tsv")`
            //    会**静默返回 null** ✓ —— 词典整个不生效、而且**一个错都不报** ✗（最难查的那类 ✓）。
            resources.srcDir(rootProject.file("../naistudio-shared/src/main/resources"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
