package com.kallan.naistudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kallan.naistudio.screens.LoginScreen
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.LocalThemeSwitch
import com.kallan.naistudio.ui.LocalSchemeSwitch
import com.kallan.naistudio.ui.NaiStudioTheme
import com.kallan.naistudio.ui.SplashOverlay
import com.kallan.naistudio.ui.SplashOverlayFadeMillis
import com.kallan.naistudio.ui.SplashOverlayMaxMillis
import com.kallan.naistudio.ui.SplashOverlayMinMillis
import com.kallan.naistudio.ui.StudioShell
import com.kallan.naistudio.ui.ThemeWipeOverlay
import com.kallan.naistudio.ui.captureWindowFrame
import com.kallan.naistudio.ui.paletteOverridesOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                com.kallan.naistudio.services.AndroidFullBackup.recoverInterruptedRestore(applicationContext)
            }
        } catch (_: Exception) {
            android.app.AlertDialog.Builder(this)
                .setTitle("备份恢复未完成")
                .setMessage("原数据仍保留在应用内。请不要卸载或清除数据，先联系维护者排查。")
                .setCancelable(false)
                .setPositiveButton("关闭") { _, _ -> finish() }
                .show()
            return
        }
        enableEdgeToEdge()
        setContent {
            val state: AppState = viewModel()

            // 用户 2026-09-17："后台删除应用后重新打开，剧情没了" —— 剧情是 600ms 防抖落盘的，
            // 被划掉时那 600ms 可能还没到。进后台（ON_STOP）就立刻写掉，与提示词同一个可靠性。
            val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(owner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) state.flushComicPlot()
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }

            // 冷启动开屏：**只认进程级首次**（`splashDone` 在 ViewModel 里，转屏不会重来）。
            var splashVisible by remember { mutableStateOf(!state.splashDone) }
            val splashAlpha by animateFloatAsState(
                targetValue = if (splashVisible) 1f else 0f,
                animationSpec = tween(durationMillis = SplashOverlayFadeMillis),
                label = "splashAlpha",
            )

            // 启动时读设置 / 参数 / 历史 / 账号（与参考实现的 AppState.load 对应）。
            // 读完之后**趁开屏还盖着**请 GenerateScreen 把抽屉的首次组合做掉 —— 那才是
            // "冷启动后第一次拉底栏卡"的真正来源（见 GenerateScreen 里那段预热注释）。
            LaunchedEffect(Unit) {
                val startedAt = System.nanoTime()
                state.load()
                state.requestPanelPrewarm()

                // 等预热真的做完（托管版停在登录门禁上时抽屉不会被组合 → 靠下面的上限兜底）
                snapshotFlow { state.panelPrewarmed }.first { it }

                // 最短展示：入场动画要跑完，否则开屏就是"闪一下"
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                if (elapsedMs < SplashOverlayMinMillis) {
                    delay(SplashOverlayMinMillis - elapsedMs)
                }
                state.markSplashDone()
                splashVisible = false
            }

            // 上限兜底：预热万一没做完，也必须放人进去（否则永远卡在开屏）
            LaunchedEffect(Unit) {
                delay(SplashOverlayMaxMillis)
                state.markSplashDone()
                splashVisible = false
            }

            val settings = state.settings
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            // 自定义调色：**深/浅各一套**，当前模式下用哪一套
            val overrides = paletteOverridesOf(settings, dark)
            // 视觉层统一走 ui/Theme.kt（Reference 皮肤 + 自定义调色板 + 圆角刻度 + 排版）。
            //
            // 深/浅色切换带**圆形扩散动画**：开关那边只报一个圆心，抓屏 + 铺遮罩 + 挖洞
            // 全在这里给出去（`LocalThemeSwitch`）。遮罩画在**最外层**，所以连侧边栏一起盖住。
            //
            // ⚠️「跟随系统」**不播**动画：那种情况下主题是跟着系统变的，没有"从哪个按钮
            // 扩散"可言。这个入口只由侧边栏那颗手点按钮触发，所以天然满足。
            val window = this.window
            val switchTheme: (Offset, Boolean) -> Unit = remember(window, state) {
                { origin, targetDark ->
                    val started = captureWindowFrame(window) { bitmap ->
                        state.startThemeWipe(
                            snapshot = bitmap.asImageBitmap(),
                            originX = origin.x,
                            originY = origin.y,
                            dark = targetDark,
                        )
                    }
                    // 抓屏失败（尺寸异常 / OOM / 系统拒绝）→ 直接切，不播动画。
                    // 宁可没有动画，也不要退回"纯色圆铺开"那种观感。
                    if (!started) state.setTheme(if (targetDark) "dark" else "light")
                }
            }

            val switchScheme: (Offset, String) -> Unit = remember(window, state) {
                { origin, scheme ->
                    val started = captureWindowFrame(window) { bitmap ->
                        state.startSchemeWipe(
                            snapshot = bitmap.asImageBitmap(),
                            originX = origin.x,
                            originY = origin.y,
                            scheme = scheme,
                        )
                    }
                    if (!started) state.setScheme(scheme)
                }
            }

            val wipe = state.themeWipe
            CompositionLocalProvider(LocalThemeSwitch provides switchTheme, LocalSchemeSwitch provides switchScheme) {
                Box(Modifier.fillMaxSize()) {
                    NaiStudioTheme(dark = dark, palette = settings.palette, overrides = overrides) {
                        // 门禁：托管模式未登录 → 登录页；启动未就绪 → 转圈；否则主界面。
                        // 启动时 load() 会在 booted 置位前就把已保存会话恢复成 LoggedIn，避免闪一下登录页。
                        when {
                            !state.booted -> Surface(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.showLoginGate -> Surface(Modifier.fillMaxSize()) { LoginScreen(state) }
                            else -> StudioShell(state)
                        }
                    }
                    if (wipe != null) {
                        // 播完只撤遮罩：主题在 startThemeWipe 里就已经切好了。
                        // 截图位图（约 18MB）不显式 recycle —— 万一某一帧还在用它，
                        // recycle 会直接崩；丢掉引用交给 GC 回收更安全。
                        ThemeWipeOverlay(wipe = wipe, onFinished = { state.finishThemeWipe() })
                    }
                    // 开屏**画在最外层**：它要盖住底下的界面（以及预热那一瞬开合的抽屉）。
                    // 淡出结束后（alpha 归 0）就不再组合，省得白留一层铺满屏幕的图层。
                    if (splashVisible || splashAlpha > 0.01f) {
                        SplashOverlay(alpha = splashAlpha)
                    }
                }
            }
        }
    }
}
