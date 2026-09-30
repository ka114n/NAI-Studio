package com.kallan.naistudio.desktop.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.logWarn
import com.kallan.naistudio.screens.ComicOverlayExportContent
import org.jetbrains.skia.EncodedImageFormat

/**
 * **电脑端的矢量层栅格化**（高级漫画第 ⑦ 项「拼页导出」的最后那一厘米）。
 *
 * 背景（`docs/43` §10.0000000）：拼页合成 `ComicComposer.compose` **只吃位图** ✗，
 * 而气泡 / 文本是**矢量**（放大不糊、字随时能改 ✓）—— 导出成 PNG 之前必须先把它们画成像素 ✓。
 * 这个 API（`ImageComposeScene` / `renderComposeScene`）**只有 CMP 桌面端有** ✓，
 * 所以它住在 `naistudio-desktop` 这一侧，共用树只认 [Platform.renderComicOverlayLayer] 那个口子 ✓。
 *
 * ## 四个必须钉住的点
 *
 * 1. **`Density(1f)`**：模型里的坐标与字号都是**底板像素** ✓ —— 密度 1 时 `1.dp == 1px` ✓，
 *    于是 `screens/ComicOverlayExport.kt` 直接拿像素值当 dp 用就落在正确位置 ✓
 *    （界面里那条路是 `(x * scale).dp`，导出时 scale 恰好 = 1 ✓ —— 同一套公式 ✓）。
 * 2. **透明底**：场景没画到的地方 alpha = 0 ✓，[com.kallan.naistudio.services.ComicComposer.blendOver]
 *    于是只把这一层的内容叠上去 ✓（不会拿一块白底把下面那层糊掉 ✗）。
 * 3. **出口是 PNG 字节**（`Image.encodeToData` ✓）而不是像素数组：
 *    ⚠️ 实测过一条弯路 —— `Image.toComposeImageBitmap()` 在渲染出来的这张图上会抛
 *    `RuntimeException: Failed to Image::makeFromBitmap`（渲染产物不是那种能直接搬进 Compose 位图的
 *    图像 ✗，跑 `DesktopComicRasterTest` 时真红过 ✓）。`encodeToData` 是 skia 自己的标准出口 ✓，
 *    而且**正好就是调用方要写进临时文件的那个格式** ✓ —— 少一次"像素 → 位图 → 再压 PNG"的来回 ✓。
 * 4. **失败一律 null 并且记一条日志**（拿不到画布 / 渲染抛异常 / 尺寸非法 ✓）—— 调用方据此
 *    **如实提示**，绝不假装"画过了" ✗（少一层气泡比悄悄少一层强 ✓）。
 */
object DesktopComicRaster {

    /** @return 透明底 PNG 字节；失败 → null ✓（见类头的第 4 条 ✓）。 */
    @OptIn(ExperimentalComposeUiApi::class)
    fun render(
        platform: Platform,
        page: ComicBoardPage,
        layerId: String,
        width: Int,
        height: Int,
    ): ByteArray? {
        if (width <= 0 || height <= 0) return null
        return try {
            // ⚠️ 走**位置参数 + 尾随 lambda**：这个顶层函数只有这一种签名，
            // 写参数名反而会在 API 改名时编不过 ✓
            val image = renderComposeScene(width, height, Density(1f)) {
                ComicOverlayExportContent(page = page, layerId = layerId, platform = platform)
            }
            image.encodeToData(EncodedImageFormat.PNG)?.bytes
        } catch (e: Throwable) {
            // 失败**要说得出原因** ✗（静默 null 的话，用户只看到"导出图里少了一层气泡"，无从查起 ✗）
            logWarn("ComicRaster", "栅格化第 $layerId 层失败：${e.stackTraceToString()}")
            null
        }
    }
}
