package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.DesktopImageIo
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.state.AppState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **聚焦重绘的"接线"验证（不需要界面、不联网、不花点数）**。
 *
 * 背景：用户报「在遮罩层画了框，按重做还是整图重绘」。那条路走不走，取决于
 * `AppState.maskFocusPlan()` 能不能从"框 + 工作图"算出方案 —— 这里用**真的 `AppState`**
 * （配 `desktopPlatform()`，测试任务已把 APPDATA / user.home 指到隔离目录）把这件事钉死：
 *
 *  · **没有框** → `maskFocusPlan()` 必须是 null（那就是整图那条路）
 *  · **画了框** → 必须算得出方案，且请求尺寸 ≠ 整图尺寸、框在请求图里也落在画布内
 *
 * 谁哪天把"框"和"工作图"接错了（读错路径、读错尺寸、算错映射），这几条会直接红。
 */
class FocusedInpaintWiringTest {

    private val width = 832
    private val height = 1216

    /** 造一张能解码的测试图（渐变，省得全白看着像失败）。 */
    private fun makeSourceImage(): File {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (0xFF shl 24) or ((x * 255 / width) shl 16) or ((y * 255 / height) shl 8) or 0x80
        }
        val image = DesktopImageIo.fromArgb(pixels, width, height)
        val bytes = DesktopImageIo.pngBytes(image)
        image.recycle()
        val file = File(System.getProperty("user.home"), "focused-wiring-source.png")
        file.writeBytes(bytes)
        return file
    }

    /**
     * 造状态：把测试图设成**图生图底图**（`useImageAsI2iBase` 是公开入口，
     * 走完之后 `workImagePath` 就是它 —— 和"从图库选一张来重绘"是同一条路）。
     */
    private fun stateWithImage(): Pair<AppState, File> {
        val file = makeSourceImage()
        val state = AppState(desktopPlatform())
        state.useImageAsI2iBase(file.absolutePath, width, height)
        return state to file
    }

    @Test
    fun `no frame means no focused plan`() {
        val (state, file) = stateWithImage()
        assertEquals("工作图应当是刚设进来的那张", file.absolutePath, state.workImagePath)
        assertNull("没画框就不该有聚焦方案（走整图那条路）", state.maskFocusPlan())
        assertEquals(0 to 0, state.maskFocusSize())
    }

    @Test
    fun `a drawn frame yields a focused plan`() {
        val (state, _) = stateWithImage()
        // 框住中间一块（归一化坐标，和界面上拖框存的是同一种东西）
        state.updateMaskFocus(SelectionShape.Rect(0.25f, 0.20f, 0.75f, 0.60f))

        val size = state.maskFocusSize()
        // 0.25..0.75 在 832 上是 208..624：`SelectionGeometry.bounds` 含右/下边界（+1 惯例）
        assertEquals("框的像素宽", 417, size.first)
        assertEquals("框的像素高", 487, size.second)

        val plan = state.maskFocusPlan()
        assertNotNull("画了框就必须算得出聚焦方案", plan)
        plan!!
        assertTrue("聚焦请求不该等于整图尺寸", plan.requestW != width || plan.requestH != height)
        assertEquals("请求尺寸要 64 对齐", 0, plan.requestW % 64)
        assertEquals(0, plan.requestH % 64)
        assertTrue(
            "请求面积不能超服务端上限（实际 ${plan.requestW}x${plan.requestH}）",
            plan.requestW * plan.requestH <= 3_145_728,
        )
        assertTrue("框在请求图里的坐标必须是正的", plan.frameInRequest.x >= 0 && plan.frameInRequest.y >= 0)
        assertTrue(
            "框必须整块落在请求图里（否则贴回会贴到画布外）",
            plan.frameInRequest.x + plan.frameInRequest.w <= plan.requestW,
        )
        assertTrue(
            plan.frameInRequest.y + plan.frameInRequest.h <= plan.requestH,
        )

        println(
            "[wiring] 框 ${size.first}x${size.second} → 裁剪 ${plan.cropW}x${plan.cropH}" +
                " → 请求 ${plan.requestW}x${plan.requestH}（放大 ${"%.2f".format(plan.scale)}×）",
        )
    }

    @Test
    fun `clearing the frame goes back to the full image path`() {
        val (state, _) = stateWithImage()
        state.updateMaskFocus(SelectionShape.Rect(0.3f, 0.3f, 0.6f, 0.6f))
        assertNotNull(state.maskFocusPlan())
        state.clearMaskFocus()
        assertNull("清框之后必须回到整图那条路", state.maskFocusPlan())
    }

    @Test
    fun `a tiny frame still maps into the request`() {
        val (state, _) = stateWithImage()
        // 很小的框（放大倍率会顶到上限）：也不能算不出方案
        state.updateMaskFocus(SelectionShape.Rect(0.40f, 0.40f, 0.42f, 0.42f))
        val plan = state.maskFocusPlan()
        assertNotNull("小框同样要能重绘", plan)
        assertTrue(plan!!.frameRequestPixels > 0)
        val frame = SelectionGeometry.bounds(
            SelectionShape.Rect(0.40f, 0.40f, 0.42f, 0.42f), width, height,
        )!!
        assertTrue("框不该为空", frame.area > 0)
    }

    /**
     * **回归：只画了框、框里一点没涂，也必须走「重绘」那条路**。
     *
     * 这是用户 2026-09-16 报的"画了框还是全图重绘"的**真正根因**：
     * `generateOrInpaint()` 原先只看 `maskActive`，而它要求"涂过遮罩"，
     * 于是"只有聚焦框"（官方口径里的整框重绘）被分派去了 `generate()` ——
     * 发出去的是**文生图**，日志里连一条 `Inpaint start:` 都没有。
     */
    @Test
    fun `an empty focus box still dispatches to inpaint`() {
        val (state, _) = stateWithImage()
        // 真实顺序：先按 ⛶ 进遮罩模式（工具条才有聚焦按钮），再拖框，一步都不少
        state.toggleMaskMode()
        state.chooseMaskFocusTool(true)
        state.updateMaskFocus(SelectionShape.Rect(0.30f, 0.30f, 0.60f, 0.55f))
        assertTrue("先决条件：确实在遮罩模式里", state.maskMode)
        assertEquals("框里没涂，但分派必须是重绘", AppState.RunAction.INPAINT, state.runAction)

        // 清掉框、也没涂遮罩 → 落回"这张图是图生图底图"那条路（不是重绘）
        state.clearMaskFocus()
        assertEquals(AppState.RunAction.IMG2IMG, state.runAction)
    }

    /** 涂了遮罩（没有框）同样必须走重绘 —— 老路径不能被我改坏。 */
    @Test
    fun `a painted mask without a frame still dispatches to inpaint`() {
        val (state, _) = stateWithImage()
        // 没 token 时 `inpaintRegion()` 会立刻返回，不会发请求；这里只要判定结果
        state.toggleMaskMode()
        state.startMaskStroke()
        state.paintMask(com.kallan.naistudio.models.MaskPoint(0.5f, 0.5f))
        state.endMaskStroke()
        assertTrue("遮罩模式 + 涂过 → 走重绘", state.runAction == AppState.RunAction.INPAINT)
    }

    /**
     * **回归：退出遮罩模式 = 连涂的遮罩和聚焦框一起删掉**（用户 2026-09-18 深夜口径：
     * 「退出遮罩模式时删除遮罩和聚焦框」）。
     *
     * 早先 `toggleMaskMode()` 关掉时是"只退出、遮罩保留"（退出后图上还留着涂过的区域），
     * 现在必须清干净 —— 谁哪天把它改回"保留"，这条会直接红。
     */
    @Test
    fun `leaving mask mode drops the paint and the frame`() {
        val (state, _) = stateWithImage()
        state.toggleMaskMode()
        state.chooseMaskFocusTool(true)
        state.updateMaskFocus(SelectionShape.Rect(0.30f, 0.30f, 0.60f, 0.55f))
        state.startMaskStroke()
        state.paintMask(com.kallan.naistudio.models.MaskPoint(0.5f, 0.5f))
        state.endMaskStroke()
        assertTrue("先决条件：确实涂上了", state.maskPaintedPercent > 0)
        assertNotNull("先决条件：确实有框", state.maskFocusRect)

        state.toggleMaskMode() // ← 退出遮罩模式

        assertEquals("退出后遮罩必须清空", 0, state.maskPaintedPercent)
        assertTrue("退出后图上不该再画遮罩", !state.maskVisible)
        assertNull("退出后聚焦框必须删掉", state.maskFocusRect)
        assertNull("没有框了，就该回到整图那条路", state.maskFocusPlan())
        assertTrue("先决条件：模式确实退出了", !state.maskMode)
    }

    /**
     * **把各种框尺寸下的"请求像素"打出来**（用户问"框内显示的请求像素好像错了"）：
     * 让人一眼看清 请求尺寸 = **框 + 上下文圈**放大之后的整块，而不是框本身。
     */
    @Test
    fun `print the request size table for several frame sizes`() {
        val sourceW = 1024
        val sourceH = 1536
        val context = 96
        println("[table] 源图 ${sourceW}x$sourceH，最小上下文 ${context}px（倍率用户自己调）")
        println("[table]  框(w×h)      1×请求        2×请求        4×请求(限免费档)  4×请求(不限)")
        for ((fw, fh) in listOf(163 to 98, 300 to 200, 417 to 487, 600 to 800, 900 to 1200)) {
            val frame = com.kallan.naistudio.models.EditRect(100, 100, fw, fh)
            fun size(scale: Float, free: Boolean): String {
                val p = com.kallan.naistudio.models.FocusedInpaint
                    .plan(frame, context, sourceW, sourceH, scale, free) ?: return "-"
                return "${p.requestW}x${p.requestH}"
            }
            println(
                "[table]  ${fw}x$fh".padEnd(16) +
                    size(1f, true).padEnd(14) +
                    size(2f, true).padEnd(14) +
                    size(4f, true).padEnd(18) +
                    size(4f, false),
            )
        }
    }
}
