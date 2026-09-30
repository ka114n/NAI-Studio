package com.kallan.naistudio.desktop.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.RefLauncher
import com.kallan.naistudio.platform.UiHost
import com.kallan.naistudio.screens.InfiniteCanvasArea
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **无限画布上"拖一下到底能不能动框"**（用户 2026-09-22 连报两次「拖不动框」✓ 之后补的离屏手势测试 ✓）。
 *
 * 为什么必须是**真送指针事件**的测试 ✗ 不能只测算术 ✓：
 * 前两次我都是"读代码推" ✓ —— 一次推"Skia 关早了"，一次推"命中测试进不去父盒子" ✓，
 * 用户两次回来说"还是不行"✗。这类 bug 的现场在**事件派发**那一层 ✓（父子的命中顺序、
 * 手势节点挂在哪、`pressed` 有没有被消费 ✓），光看源码推不出来 ✓。
 *
 * 这个用例用 `ImageComposeScene` **不开窗口**地把这一块真组合起来 ✓，
 * 用 `sendPointerEvent` 真送 按下 / 移动 / 抬起 ✓，然后断言 `state.infiniteFrame` 真的变了 ✓：
 *  · 拖空白 ⇒ **新建一个框** ✓；
 *  · 再拖**框里面** ⇒ 那个框**被挪走**（而不是又新建一个 ✓）—— 这一条正是用户报的那个症状 ✓。
 *
 * ⚠️ 用**隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home` ✓，绝不碰用户 prefs.json ✓）。
 */
class InfiniteCanvasGestureTest {

    private val platform: Platform = desktopPlatform()

    /** 一个**不弹真对话框**的宿主（手势测试里没人点"选图"，只要接口在就行 ✓）。 */
    private class QuietUiHost : UiHost {
        @Composable
        private fun noop(): RefLauncher = remember { object : RefLauncher { override fun launch(input: String?) = Unit } }

        @Composable
        override fun rememberImagePicker(onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFilePicker(mimeTypes: List<String>, onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFileCreator(mimeType: String, onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFolderPicker(onPicked: (String?) -> Unit): RefLauncher = noop()

        override fun toast(message: String, long: Boolean) = Unit
        override fun openUrl(url: String) = Unit
        override fun fullscreenDialogProperties(): DialogProperties = DialogProperties()
        @Composable
        override fun backHandler(enabled: Boolean, onBack: () -> Unit) = Unit
        override fun exitApp() = Unit
        override fun horizontalResizeCursor(): Modifier = Modifier
        override fun panCursor(grabbing: Boolean): Modifier = Modifier
    }

    /** 一块 512×512 的透明画布（`resetInfiniteCanvas` 是公开的 ✓，不用走界面 ✓）。 */
    private fun freshCanvas(): AppState = AppState(platform).also {
        it.resetInfiniteCanvas(IntArray(512 * 512), 512, 512)
    }

    private val primaryDown = PointerButtons(isPrimaryPressed = true)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun sceneOf(state: AppState) = ImageComposeScene(
        width = 800,
        height = 600,
        density = Density(1f),
    ) {
        CompositionLocalProvider(
            LocalPlatform provides platform,
            LocalUiHost provides QuietUiHost(),
        ) {
            MaterialTheme {
                InfiniteCanvasArea(state = state, t = { key -> key }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    /** 真送一次"按下 → 拖 → 抬起" ✓（每步都 `render()` 一帧，让手势协程真的跑起来 ✓）。 */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun ImageComposeScene.drag(from: Offset, to: Offset) {
        render()
        sendPointerEvent(PointerEventType.Press, from, buttons = primaryDown, button = PointerButton.Primary)
        render()
        // 分几步走：真实拖动是**多帧**的 ✓（一步跳到位有些手势判据会当成"没动" ✗）
        for (step in 1..5) {
            val t = step / 5f
            sendPointerEvent(
                PointerEventType.Move,
                Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t),
                buttons = primaryDown,
            )
            render()
        }
        sendPointerEvent(PointerEventType.Release, to, buttons = PointerButtons(), button = PointerButton.Primary)
        render()
    }

    @Test
    fun `在画布空白处拖一下 —— 新建一个框，尺寸就是拖出来的那一块`() {
        val state = freshCanvas()
        sceneOf(state).drag(Offset(200f, 200f), Offset(420f, 380f))
        val frame = state.infiniteFrame
        assertNotNull("拖完应该有一个框 ✗（这一条正是用户报的「拖不动框」✓）", frame)
        assertTrue("框的宽高都得 > 0（宽=${frame!!.w} 高=${frame.h}）", frame.w > 0 && frame.h > 0)
        // ⛔ 尺寸断言是**这条 bug 的护栏** ✗（2026-09-22）：
        //    当时 `anchor` 每帧都被推进 ⇒ 框只长出"最后一帧的位移"（实测 38×31 ✓），
        //    又被 `INFINITE_FRAME_MIN` 的护栏收掉 ⇒ 用户看到的是"什么都建不出来" ✓。
        //    画布 512²、视口 800×600 ⇒ fit = 1.171875、内容层左边 100px ✓；
        //    屏幕拖 (220, 180) ⇒ 文档应约 (188, 154) ✓。
        assertEquals("框的宽应该等于拖出来的水平距离 ✗", 188, frame.w)
        assertEquals("框的高应该等于拖出来的垂直距离 ✗", 154, frame.h)
    }

    @Test
    fun `再拖框里面 —— 框被挪走，而不是又新建一个`() {
        val state = freshCanvas()
        val scene = sceneOf(state)
        scene.drag(Offset(150f, 150f), Offset(300f, 300f))
        val first = state.infiniteFrame
        assertNotNull("第一下应该建出框来 ✗", first)

        // 第二下：按在**框里面**往右下拖（框的位置是**文档坐标** ✓，这里只断言"变了" ✓）
        scene.drag(Offset(220f, 220f), Offset(300f, 310f))
        val moved = state.infiniteFrame
        assertNotNull("第二下之后框不该消失 ✗", moved)
        assertTrue(
            "按在框里拖 —— 框的位置应该**变**（原=${first!!.x},${first.y} 现=${moved!!.x},${moved.y}）",
            moved.x != first.x || moved.y != first.y,
        )
        assertEquals("按在框里拖**不该**把框的尺寸改成新画的那个 ✗", first.w, moved.w)
    }
}
