package com.kallan.naistudio.models

import com.kallan.naistudio.services.ImageMetadata
import com.kallan.naistudio.services.ImageMetadataReader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * NovelAI 的 **alpha 通道隐写**解码（`stealth_pngcomp`）。
 *
 * ## 这个夹具不是我自己编的
 *
 * 下面这张 64×64 的 PNG 是**用 Python 按官方 `nai_meta.py` 的语义写出来的**
 *（同款列优先 + 高位在前 + 15 字节魔数 + 4 字节大端位长 + gzip）。
 * 也就是说：**写入端是独立实现，读出端是 Kotlin**。
 *
 * 这一点很重要 —— 如果测试用我自己写的编码器来喂自己的解码器，
 * 两边共享同一个误解时照样全绿（我第一版就按 16 字节魔数 + 行优先写，两处都错），
 * 而真实图片一张都解不开。
 *
 * 真实样本的实测记录（832×1216 正规下载图）：
 * ```text
 * chunk 类型集合: ['IDAT', 'IEND', 'IHDR']
 * 从 alpha 最低位解出: stealth_pngcomp + 21784 位(2723 字节) gzip
 * → Description 1652 字 / Comment 9780 字 / Software "NovelAI" / Source / Generation time
 * ```
 */
class StealthPngTest {

    /**
     * Python 生成的夹具。内容：
     * Description / Software=NovelAI / Source / Generation time / Comment(JSON)
     */
    private val fixtureBase64 = buildString {
        append("iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAACLUlEQVR42t2bsY7DMAxDM3e+uf//leItnQ6J9agOZ3oIiiRt")
        append("ASsSRdLK9fp5V3Poc/w91829uvluNb/91+N6WAQNxNOnmv+ICYAW97XIjvgMUJPqAk9eKQHoUv0uzQumeKUGgNS9mgDdBWPb")
        append("EigD3OiCCgRlywDUwyK7AHX4EFcCaupai9Sn/GH7LkDxQYn13/EAwYU8PfXtW+AKA5wnT0jT1gGophMI3o9B/qcSUCNoBPBh")
        append("e9QnGaABIZLJIbZsg1TyOgSpTg3AMccF0JtQ5BUviOEBGpCgTvLGtMFqaC1deC1ks1K0AKW8XZZUEhMUDERBYCSkaWsmSGTv")
        append("Nx5ihBok/mD325gMoEqQyuUIKUz3BTrF52ZLNAh2zo+gbojjAXRz5Cgx5FjhHfBVWgAo/ye4EOMIET1Qg5KJVYN0Q4RgQUwJ")
        append("EAeYmqSV5gcQwOvQv9KCcUGFN8WHOCpM6G2ZfCAKBB1H2DFIo0qATHkUuK40P2C61++qwwgiRPf6iJcY6wd015zdIqViwGT+")
        append("5whDZCKJ46ZDnCGpDh8Eu0NMF6C7POQ8EgM05PuVFoQLTns6FFmpPIBsfTkSOc4ScxnhMSDoTIKS8lBaBrgz/vWFWIobkyvY")
        append("7hyLfGsi1DE9JyixjtCUzxOvMNYSm3gCSgwAcYDpHNERJeCIJDJYGacGXQs8kgc4i5kowrg2SPq6qxniXphwxuWPoMLOcONk")
        append("MCoaBJ3WGP/OkGuH0bLYNgC/99atothFy9UAAAAASUVORK5CYII=")
    }

    private val fixture: ByteArray
        get() = Base64.getMimeDecoder().decode(fixtureBase64)

    /** 一张最小的合法 PNG（1×1 RGB），没有隐写。 */
    private val plainPng: ByteArray = Base64.getMimeDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    @Test
    fun hidden_metadata_is_removed_and_plain_png_is_unchanged() {
        assertNotNull(StealthPng.decode(fixture))
        assertNull(StealthPng.decode(StealthPng.stripHiddenMetadata(fixture)))
        org.junit.Assert.assertArrayEquals(plainPng, StealthPng.stripHiddenMetadata(plainPng))
    }

    // ------------------------------------------------------------ 常量（踩过的坑）

    @Test
    fun `魔数是 15 个字符不是 16`() {
        // ⚠️ 我第一版按 16 字节去比，结果永远匹配不上、白查了一轮。
        // `stealth_pngcomp` 数一下就是 15。
        assertEquals(15, StealthPng.MAGIC.length)
        assertEquals(15, StealthPng.MAGIC_BYTES.size)
    }

    // ------------------------------------------------------------ 解码

    @Test
    fun `能把夹具里的隐写元数据解出来`() {
        val map = StealthPng.decode(fixture)
        assertNotNull("没解出隐写元数据 —— 多半是列优先/位序写错了", map)
        map!!

        assertEquals("NovelAI", map["Software"])
        assertEquals("1girl, solo, test prompt", map["Description"])
        assertEquals("Stable Diffusion XL C9B7", map["Source"])
        assertEquals("2026-01-01T00:00:00", map["Generation time"])
        assertNotNull(map["Comment"])
    }

    @Test
    fun `Comment 里是嵌套 JSON`() {
        val comment = StealthPng.decode(fixture)?.get("Comment")
        assertNotNull(comment)
        val json = JSONObject(comment!!)
        assertEquals("1girl, solo", json.optString("prompt"))
        assertEquals(28, json.optInt("steps"))
        assertEquals(12345L, json.optLong("seed"))
    }

    @Test
    fun `载荷是 gzip`() {
        val payload = StealthPng.extractPayload(fixture)
        assertNotNull(payload)
        // gzip 固定头: 1f 8b
        assertEquals(0x1f, payload!![0].toInt() and 0xFF)
        assertEquals(0x8b, payload[1].toInt() and 0xFF)
        val text = StealthPng.gunzip(payload)
        assertNotNull(text)
        assertTrue(text!!.contains("stealth") == false) // 载荷本身就是 JSON，不该再带魔数
        assertTrue(text.contains("\"Software\""))
    }

    // ------------------------------------------------------------ 负例

    @Test
    fun `普通 PNG 没有隐写时返回 null`() {
        assertNull(StealthPng.decode(plainPng))
    }

    @Test
    fun `不是 PNG 时返回 null`() {
        assertNull(StealthPng.decode("这不是一张图".toByteArray()))
        assertNull(StealthPng.decode(ByteArray(0)))
    }

    @Test
    fun `被截断的图不会崩 只是解不出来`() {
        val cut = fixture.copyOf(fixture.size / 2)
        assertNull(StealthPng.decode(cut))
    }

    @Test
    fun `字节被改坏时不会崩`() {
        val broken = fixture.copyOf()
        // 往 IDAT 中间塞点垃圾
        for (i in 200 until 260) {
            if (i < broken.size) broken[i] = (broken[i].toInt() xor 0x5A).toByte()
        }
        StealthPng.decode(broken) // 不抛异常即可
    }

    // ------------------------------------------------------------ 端到端

    @Test
    fun `夹具里确实没有文本块 所以只能靠隐写`() {
        // 这条是"为什么需要隐写"的证据：同一张图，常规路径读不出来
        val chunks = com.kallan.naistudio.services.PngMetadata.readTextChunks(fixture)
        assertTrue("夹具不该有文本块，否则这条测试就失去意义了", chunks.isEmpty())
    }

    @Test
    fun `元数据解析器能直接读出这张隐写图的提示词`() {
        // 端到端：字节 → ImageMetadataReader → 提示词
        val meta = ImageMetadataReader.read(fixture)
        assertNotNull("隐写这条路没接上", meta)
        meta!!
        assertEquals(ImageMetadata.Source.NAI, meta.source)
        // 注意取到的是 **Comment 里那个结构化 prompt**，不是 `Description` 那串 ——
        // 解析器优先信结构化字段，这是对的（Description 在 V3 之后就只是人类可读的副本）。
        assertEquals("1girl, solo", meta.positivePrompt)
        // 结构化的参数也解出来了
        val params = meta.params
        assertNotNull(params)
        assertEquals(28, params!!.steps)
        assertEquals(12345L, params.seed)
    }

    @Test
    fun `普通 PNG 走完整解析器仍然返回 null`() {
        // 隐写这条路**不能**把普通图片误判成有元数据
        assertNull(ImageMetadataReader.read(plainPng))
    }
}
