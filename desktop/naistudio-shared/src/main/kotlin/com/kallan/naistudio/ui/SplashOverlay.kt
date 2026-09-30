package com.kallan.naistudio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **冷启动开屏。**
 *
 * ## 它不只是"看两秒"
 *
 * 用户报的是"**刚开应用拉底栏卡**"。那一下卡的真正原因是**抽屉子树第一次组合**
 * （TabRow + 三个提示词框 + NovelAI 权重高亮转换器 + 文字排版）—— 一次性几百毫秒的重活，
 * 全挤在用户第一次下拉的那几帧里。
 *
 * 所以开屏在这里的职责是**把那段空窗期用起来**：`MainActivity` 一边盖着开屏，
 * 一边让 `GenerateScreen` 把抽屉**瞬时开合一次**（见那里的 `panelPrewarm`）。
 * 等预热做完才收开屏 —— 用户真正那一拉就只剩纯动画了，不再有首帧重活。
 *
 * ## 时长口径（用户 2026-09-16）
 *
 * **等预热完成，但有上下限**：最少 [SplashOverlayMinMillis]（入场动画得跑完，别闪一下），
 * 最多 `SPLASH_MAX_MS`（预热万一没做完也必须放人进去 —— 比如托管版停在登录门禁上时，
 * 抽屉根本不会被组合，没有上限就会永远卡在开屏）。
 * 固定 2 秒是**不行的**：那既白等，又保证不了后续流畅。
 *
 * ## 两个细节
 *
 * - **底色用 `naiBackgroundColor()`**，和 `windowBackground`（`values/colors.xml`）同一个色，
 *   这样系统那一下启动图切到 Compose 开屏时不会闪一个色块。
 * - **开屏期间吃掉所有触摸**：底下就是真正的界面（只是被盖着），若不拦，
 *   用户在开屏上随手一点可能正好点到「生成图片」—— 那是要花 Anlas 的。
 */
@Composable
fun SplashOverlay(alpha: Float, appName: String, modifier: Modifier = Modifier) {
    // 入场动画：只在第一次组合时跑一遍（图标轻微放大 + 淡入，名字晚一点淡入）
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val iconProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "splashIcon",
    )
    val textProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = 180),
        label = "splashText",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(alpha)
            // 吃掉全部触摸（含拖动）：底下是真界面，别让开屏上的误触落到「生成图片」上
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 图标：和侧边栏顶部共用 `AppLogo` 那一份实现（背景 + 前景两层矢量叠出来）
            AppLogo(
                size = 108.dp,
                modifier = Modifier.graphicsLayer {
                    val s = 0.88f + 0.12f * iconProgress
                    scaleX = s
                    scaleY = s
                    this.alpha = iconProgress
                },
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                modifier = Modifier.graphicsLayer { this.alpha = textProgress },
            )
        }

        // 底部一条细进度：告诉用户"在准备"，而不是卡住了
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .width(120.dp)
                .height(3.dp)
                .graphicsLayer { this.alpha = textProgress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        )
    }
}

/** 开屏**最少**显示多久 —— 入场动画（420ms）得跑完，不然就是闪一下。 */
const val SplashOverlayMinMillis = 600L

/** 开屏**最多**显示多久 —— 预热万一没做完也必须放人进去（例如托管版停在登录门禁）。 */
const val SplashOverlayMaxMillis = 2000L

/** 开屏淡出时长。 */
const val SplashOverlayFadeMillis = 220
