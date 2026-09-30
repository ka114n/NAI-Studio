# NAI Studio

基于 Kotlin / Compose 的图像生成与编辑应用，支持 Windows 和 Android。包含提示词编辑、图生图、遮罩、无限画布和漫画分镜工作流。本项目独立维护，并非 NovelAI 官方产品，需要自行配置 API 凭据。

## 下载

在 [Releases](https://github.com/ka114n/NAI-Studio/releases) 下载。

- Windows：解压 ZIP，打开 `NAI Studio.exe`，保留同目录的 `app` 和 `runtime`，无需安装 Java。
- Android：安装 APK，要求 Android 8.0 或更高。如果旧测试版签名冲突，请先导出备份，再卸载测试版后安装。

Windows 网页笔刷首次使用会下载 Chromium 运行组件。翻译词典与索引首次启动在后台准备，需要网络；之后使用本机缓存。

## 源码结构

- `desktop/naistudio-desktop`：Windows 平台应用。
- `desktop/naistudio-shared`：桌面界面、状态、模型和服务。
- `phone/naistudio`：Android 应用。
- `phone/naistudio-shared`：手机界面、模型和服务。

两端保留独立源码副本，跨端修改需同步检查。

## 构建

使用 JDK 17，首次构建需要联网下载 Gradle 依赖。

Windows，在 `desktop/naistudio-desktop` 执行：

```powershell
..\gradlew.bat test desktopInput --no-daemon
```

`desktopInput` 收集应用和依赖 JAR 到 `build/jpackage-input`。可使用 JDK 的 `jpackage --type app-image` 制作便携版，主类为 `com.kallan.naistudio.desktop.MainKt`。发布时将 `docs/brush-lab*.html` 放入应用的 `app` 目录。

Android，安装 SDK，在 `phone/naistudio/local.properties` 配置 `sdk.dir`，然后在 `phone/naistudio` 执行：

```powershell
.\gradlew.bat :app:testLocalDebugUnitTest :app:assembleLocalRelease --no-daemon
```

Release APK 默认未签名，使用自己的密钥签名。构建会递增版本迭代文件，本次发布版本为 1.1.140。

## 数据与默认规则

新安装的风格预设列表、正负面提示词及历史示例上下文为空。保留翻译、优化、反推和漫画分镜的功能规则，以及模型画质词和负面预设。标签数据和可选外部词典可能包含成人词汇，词典与 AI 功能规则是不同内容。

用户配置的服务会接收相关提示词或图像。仓库不含个人凭据、设置、生成历史及签名私钥，请勿提交这些文件。

## 许可

项目自有代码采用 [MIT](LICENSE)。第三方依赖、数据和素材的条款不由 MIT 覆盖，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
