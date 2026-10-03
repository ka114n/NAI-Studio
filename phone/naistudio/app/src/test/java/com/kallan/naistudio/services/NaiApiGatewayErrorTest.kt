package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Test

/** Server policy rejections must retain the server's actual reason. */
class NaiApiGatewayErrorTest {
    private val api = NaiApi()
    private fun error(code: Int, body: String): String {
        val method = NaiApi::class.java.getDeclaredMethod("errorText", Int::class.javaPrimitiveType, ByteArray::class.java)
        method.isAccessible = true
        return method.invoke(api, code, body.toByteArray(Charsets.UTF_8)) as String
    }

    @Test fun gatewayPolicyReasonsArePreserved() {
        for (code in listOf(401, 402, 403)) {
            assertEquals("请求失败（$code）：Key policy rejected", error(code,
                """{"error":{"message":"Key policy rejected","status":$code}}"""))
        }
    }

    @Test fun officialMessageAndEmptyBodyFallbackRemainSupported() {
        assertEquals("请求失败（402）：Insufficient Anlas", error(402,
            """{"message":"Insufficient Anlas"}"""))
        assertEquals("账户没有有效订阅或 Anlas 不足（402）。", error(402, "{}"))
        assertEquals("Token 无效或已失效（401），请重新获取并填写。", error(401, "{}"))
    }
}
