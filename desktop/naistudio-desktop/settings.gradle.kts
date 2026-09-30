// 电脑版（Windows exe）工程 —— **过渡方案**：不重构手机工程，直接把它的源码编进来。
//
// 为什么另起一个工程而不是在 naistudio 里加模块：
//  · 手机工程**每天在改**、每轮都在出包，先动它的构建结构风险太大；
//  · 过渡期只要 `naistudio-desktop` 能编过、能出 exe，就算跑通；
//  · 等平台层稳定了再谈"正式拆 core/ui/app/desktop"（见 docs/25 方案 §3）。
//
// ⚠️ 路径必须全 ASCII（AGP 那条老坑，同一个工作区里保持一致）。
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "naistudio-desktop"
