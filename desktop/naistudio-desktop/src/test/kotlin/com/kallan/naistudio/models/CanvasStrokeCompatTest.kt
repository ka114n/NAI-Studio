package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **老路回归钉**：`CanvasEditor`（生成页那颗铅笔的画布编辑器 ✓）走的是**同一个**
 * [ImageEditSession] ✓ —— 所以"笔画引擎改成沿路径盖笔尖"这件事**必须不弄坏它** ✓
 * （用户口径：「现有工具（画笔 / 橡皮 / 油漆桶 / 模糊 / 取色 / 选区）**行为不许坏**」✓）。
 *
 * 这一条是回报里"**请核一遍**"那一条的凭据 ✓，钉三件事：
 *
 *  1. **`StrokeSpec` 加参数没有改变老调用点的行为** ✓：`AppState.beginCanvasStroke` 一个字段
 *     都不传（走默认 ✓）⇒ 半径 / 形状 / 走位全都退化成老口径 ✓（半径那条由
 *     [PenDabEngineTest] 用老公式逐字钉死 ✓，这里钉**形状**：方的还是方的、软的还是软的 ✓）；
 *  2. **模糊 / 图章照旧"整段一次"** ✓（它们不吃逐点压感 ✓，见 `ImageEditSession.strokeSegmentRadius` ✓）——
 *     这才是"没坏"的判据 ✓（不是"没崩"就算 ✓）；
 *  3. **橡皮 / 撤销照旧** ✓（dab 逐笔尖记的脏矩形没有啃到旁边 ✓）。
 */
class CanvasStrokeCompatTest {

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /** 一整列（第 [x] 列）上有多少不透明像素。 */
    private fun columnThickness(pixels: IntArray, width: Int, height: Int, x: Int): Int {
        var count = 0
        for (y in 0 until height) {
            if (alphaOf(pixels[y * width + x]) > 0) count++
        }
        return count
    }

    @Test
    fun the_old_canvas_editor_stroke_path_is_unchanged() {
        val width = 160
        val height = 160
        val color = 0xFF224466.toInt()

        // ---- ① 方笔（老画布编辑器的一个现存形状 ✓）：恒压一笔 = 连续的一条方带 ✓ ----
        val square = ImageEditSession(width, height, IntArray(width * height))
        square.beginStroke(
            20f, 80f,
            // ⚠️ 这就是 `AppState.beginCanvasStroke` 那种**老调用点**的写法 ✓：
            // 新参数（spacing / minRadiusRatio / pressureCurve / angleControl / nibRatio / rotation）
            // **一个都不传** ✓ —— 全部走默认 ✓。
            StrokeSpec(mode = StrokeMode.PAINT, brushPixels = 40, shape = BrushShape.SQUARE, color = color),
        )
        for (x in 22..140 step 2) {
            square.strokeTo(x.toFloat(), 80f)          // 压力走默认 1f = 鼠标那条路 ✓
        }
        square.endStroke()
        assertTrue("方笔那一条老路必须还在画", square.hasChanges)
        // 半径 20 的方块 → 40 px 厚；走位 `Spacing` 默认 10%（= 半径的 20%，4 px 一步）⇒ 全程 40 px 上下 ✓
        for (x in 24..136) {
            val thickness = columnThickness(square.pixels, width, height, x)
            assertTrue("方笔笔迹在 x=$x 断了（$thickness px）", thickness >= 36)
            assertTrue("方笔不该比笔尖还粗（x=$x 处 $thickness px）", thickness <= 40)
        }
        assertTrue("撤销要能回到原样", square.undo())
        assertTrue("撤销之后应当一个不透明像素都不剩", square.pixels.all { alphaOf(it) == 0 })

        // ---- ② 软圆：羽化边还在（不是被 dab 叠成硬边 ✗）----
        val soft = ImageEditSession(width, height, IntArray(width * height))
        soft.beginStroke(
            20f, 80f,
            StrokeSpec(mode = StrokeMode.PAINT, brushPixels = 40, shape = BrushShape.SOFT_ROUND, color = color),
        )
        for (x in 22..140 step 2) soft.strokeTo(x.toFloat(), 80f)
        soft.endStroke()
        val softColumn = (0 until height).map { soft.pixels[it * width + 80] }
        assertTrue(
            "软圆笔尖必须还有**半透明**的边（全是 0 / 255 就说明退化成硬边了 ✗）",
            softColumn.any { alphaOf(it) in 1..254 },
        )
        val opaqueCore = softColumn.count { alphaOf(it) == 255 }
        assertTrue("软圆中间该是实的（实际 $opaqueCore px）", opaqueCore >= 18)

        // ---- ③ 模糊 / 图章：照旧"整段一次"（恒压分支 ✓），而且撤销干净 ✓ ----
        // 先铺一块不透明底，模糊才有东西可糊 ✓
        val blur = ImageEditSession(width, height, IntArray(width * height) { color })
        assertTrue(
            "模糊那一段必须真的落下来（恒压分支照旧 ✓）",
            blur.beginStroke(20f, 80f, StrokeSpec(mode = StrokeMode.BLUR, brushPixels = 40, blurIntensity = 100)) &&
                blur.strokeTo(140f, 80f),
        )
        blur.endStroke()
        assertTrue("模糊之后也该算改过", blur.hasChanges)
        assertTrue("模糊那一笔要能撤销", blur.undo())
        assertTrue("撤销之后像素逐位回到原样", blur.pixels.all { it == color })

        // 图章：源偏移非 0 = **真的把那一块搬过来** ✓（老路照旧"整段一次"✓）
        val base = 0xFF808080.toInt()
        val mark = 0xFF00AA55.toInt()
        val clonePixels = IntArray(width * height) { base }
        for (y in 70..90) {
            for (x in 30..50) clonePixels[y * width + x] = mark
        }
        val clone = ImageEditSession(width, height, clonePixels)
        clone.beginStroke(
            100f, 80f,
            StrokeSpec(mode = StrokeMode.CLONE, brushPixels = 20, cloneOffsetX = -70, cloneOffsetY = 0),
        )
        assertTrue("图章那一段要真的落下来", clone.strokeTo(120f, 80f))
        clone.endStroke()
        assertTrue("图章也算改过", clone.hasChanges)
        assertEquals(
            "图章把源点那块（带标记的那一块 ✓）搬过来了",
            mark,
            clone.pixels[80 * width + 110],
        )
        assertTrue("图章那一笔要能撤销", clone.undo())
        assertEquals("撤销之后那段回到原样", base, clone.pixels[80 * width + 110])

        // ---- ④ 橡皮：alpha 衰减（老口径：只动 alpha ✓；**擦透那一档归一成全透明 0** ✓）----
        val erase = ImageEditSession(width, height, IntArray(width * height) { color })
        erase.beginStroke(20f, 80f, StrokeSpec(mode = StrokeMode.ERASE, brushPixels = 40))
        erase.strokeTo(140f, 80f)
        erase.endStroke()
        assertEquals("擦掉的地方 alpha 应当归零", 0, alphaOf(erase.pixels[80 * width + 80]))
        assertEquals(
            "擦透的像素必须是**全透明 0**（不是 0x00RRGGBB ✗）—— 留着 RGB 会让 " +
                "`pixels.all { it == ImageEditOps.TRANSPARENT }` 那种判断失效 ✓",
            0,
            erase.pixels[80 * width + 80],
        )
        assertTrue("橡皮那一笔也要能撤销", erase.undo())
        assertEquals("撤销之后那块颜色回来了", color, erase.pixels[80 * width + 80])
    }
}
