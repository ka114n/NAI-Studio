package com.kallan.naistudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.R

/**
 * **App 图标**：侧边栏顶部与开屏共用，直接渲染**启动器那个图标本身**
 * （`R.mipmap.ic_launcher`，自适应图标）。
 *
 * ## 为什么不是"照 108 画布重绘"
 *
 * 早先按 `brand/nai-icon.svg` 的 108×108 视口逐坐标重绘，结果**图案看着偏小**
 * （用户 2026-09-16 反馈）—— 因为那个 108 画布是**自适应图标的全尺寸资产**：
 * 系统只取**中间 72×72** 当可见区、再套上启动器的遮罩，
 * 外圈那 18 是给视差/遮罩留的出血位。照着整个 108 画满，等于把出血位也画进去，
 * 图案自然缩水一圈。
 *
 * 所以这里改成**让系统自己画**：取 `AdaptiveIconDrawable` 画进一张位图
 * （`draw()` 内部会连遮罩一起裁好），拿到的就是**手机桌面上那个样子** ✓
 * 好处还有一个：以后改启动器图标，侧边栏跟着变，不用两处同步。
 *
 * ## 为什么不用 `painterResource(R.mipmap.ic_launcher)`
 *
 * 那是 `<adaptive-icon>` XML：`painterResource` 只对 `<vector>` 走向量路径，
 * 其余交给 `BitmapFactory`，而 BitmapFactory **解不了 XML drawable**（返回 null/抛错）。
 * 自己用 `android.graphics.Canvas` 画一遍是唯一不引第三方库的做法（androidx 自带的
 * `DrawablePainter` 在 Accompanist 里，这个工程不引第三方 UI 库）。
 *
 * @param size 边长。位图按 **像素尺寸**缓存（`remember(px)`），尺寸不变就不重画。
 */
@Composable
fun AppLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    // 缓存键是"像素尺寸"：同尺寸换主题/重组都不重画
    val bitmap: ImageBitmap? = remember(px) { renderLauncherIcon(context, px) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
        )
    }
}

/**
 * 把启动器图标画进一张 [px]×[px] 的位图。
 *
 * `AdaptiveIconDrawable.draw()` 自己会套遮罩，所以拿到的就是桌面上那个形状
 * （圆的/方的取决于系统与厂商）；画不出来时返回 null，调用方画空白而不是崩。
 */
private fun renderLauncherIcon(context: android.content.Context, px: Int): ImageBitmap? =
    runCatching {
        val drawable = context.getDrawable(R.mipmap.ic_launcher) ?: return null
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
