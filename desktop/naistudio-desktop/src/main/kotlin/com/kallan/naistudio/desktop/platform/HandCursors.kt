package com.kallan.naistudio.desktop.platform

import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * 画布平移用的两只手：**张手**（可以拖）与**握手**（拖动中）。
 *
 * 位图来源：用户指定的 SAI（`C:\SAI Ver.2 2021`）。SAI 的光标**不在磁盘上**，
 * 是编在 `sai2.exe` 的 PE 资源里的（`RT_GROUP_CURSOR` 共 69 个），
 * 用 `EnumResourceNames` + `LoadCursor` + `DrawIconEx` 导出成 PNG 后放进
 * `src/main/resources/cursor/`：
 *
 * | 文件 | SAI 资源 | 大小 | 热点 |
 * | --- | --- | --- | --- |
 * | `cursor/grab.png` | #11 id318（张手） | 32×32 | 11,9 |
 * | `cursor/grabbing.png` | #12 id319（握手） | 32×32 | 11,9 |
 *
 * ⚠️ 热点必须**按表里给的值**设置：`createCustomCursor` 的 hotSpot 决定"手心的哪一点算指针位置"，
 * 给 0,0 的话手会整个歪到右下角，拖起来非常别扭。
 *
 * 建一次就缓存（`Toolkit.createCustomCursor` 每次都要过一趟 Toolkit，别每帧调用）。
 * 资源缺失或建不出来时退回系统手型（张手用 `HAND_CURSOR`、握手用 `MOVE_CURSOR`）——
 * 一只指针不值得把功能卡死。
 */
internal object HandCursors {

    private const val HOTSPOT_X = 11
    private const val HOTSPOT_Y = 9

    private val cache = HashMap<Boolean, Cursor>()
    private val fallback = HashMap<Boolean, Cursor>()

    fun of(grabbing: Boolean): Cursor = cache.getOrPut(grabbing) { build(grabbing) }

    private fun build(grabbing: Boolean): Cursor {
        val name = if (grabbing) "grabbing.png" else "grab.png"
        val custom = runCatching {
            val stream = object {}.javaClass.getResourceAsStream("/cursor/$name")
                ?: return@runCatching null
            val image: BufferedImage = stream.use { ImageIO.read(it) } ?: return@runCatching null
            Toolkit.getDefaultToolkit().createCustomCursor(
                image,
                Point(HOTSPOT_X, HOTSPOT_Y),
                if (grabbing) "nai-canvas-grabbing" else "nai-canvas-grab",
            )
        }.getOrNull()
        if (custom != null) return custom
        // 退路：系统手型（张手没有对应的标准光标，用 HAND；握手用 MOVE）
        return fallback.getOrPut(grabbing) {
            Cursor.getPredefinedCursor(
                if (grabbing) Cursor.MOVE_CURSOR else Cursor.HAND_CURSOR,
            )
        }
    }
}
