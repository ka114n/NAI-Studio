package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.*
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class DesktopTaggerSharing(
    private val tagger: LocalImageTagger, private val secrets: SecretStore, private val images: ImageIo,
) : TaggerSharing {
    private var server: TaggerHttpServer? = null
    private var info: TaggerShareInfo? = null
    private var ownsFunnel = false
    private val cli get() = File(System.getenv("ProgramFiles") ?: "C:/Program Files", "Tailscale/tailscale.exe")
    init { Runtime.getRuntime().addShutdownHook(Thread({ close() }, "tagger-share-shutdown")) }
    private fun command(vararg args: String): String {
        check(cli.isFile) { "请先在电脑安装并登录 Tailscale" }
        val process = ProcessBuilder(listOf(cli.absolutePath) + args).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread({ process.inputStream.bufferedReader().use { input ->
            while (true) { val line = input.readLine() ?: break; synchronized(output) { if (output.length <= 1024 * 1024) output.append(line).append('\n') } }
        } }).apply { isDaemon = true; start() }
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly(); reader.join(1000)
            val detail = synchronized(output) { output.toString() }
            error("Tailscale 配置尚未完成，请登录账号并允许 Funnel 后重试。\n$detail")
        }
        reader.join(1000)
        val result = synchronized(output) { output.toString() }
        check(result.length <= 1024 * 1024) { "Tailscale 返回数据过大" }
        check(process.exitValue() == 0) { "Tailscale 配置失败：$result" }
        return result
    }
    override suspend fun start(): TaggerShareInfo = withContext(Dispatchers.IO) {
        info?.let { return@withContext it }
        val status = JSONObject(command("status", "--json", "--peers=false"))
        check(status.optString("BackendState") == "Running") { "请先连接电脑 Tailscale" }
        val dns = status.getJSONObject("Self").optString("DNSName").trimEnd('.')
        check(dns.endsWith(".ts.net")) { "请先启用 Tailscale MagicDNS" }
        val serve = JSONObject(command("serve", "status", "--json"))
        val handlers = serve.optJSONObject("Web")?.optJSONObject("$dns:8443")?.optJSONObject("Handlers")
        val handler = handlers?.optJSONObject("/")
        check((handlers == null || handlers.keySet().all { it == "/" }) &&
            (handler == null || handler.optString("Proxy") == "http://127.0.0.1:18965")) { "Tailscale 8443 端口已被其他服务使用" }
        val code = secrets.getSecret("reverse_share_key").ifBlank {
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }).also { secrets.putSecret("reverse_share_key", it) }
        }
        val created = TaggerHttpServer(code, tagger, images::size)
        try {
            created.start(30000, true)
            server = created
            command("funnel", "--bg", "--https=8443", "http://127.0.0.1:18965")
            ownsFunnel = true
            TaggerShareInfo("https://$dns:8443", code).also { info = it }
        } catch (error: Exception) { created.stop(); server = null; throw error }
    }
    override suspend fun stop() = withContext(Dispatchers.IO) {
        close()
        if (ownsFunnel) { command("funnel", "--https=8443", "off"); ownsFunnel = false }
    }
    override fun close() {
        server?.stop(); server = null; info = null
        // Keep the fixed forwarding/DNS registration across app restarts. With no local
        // listener the endpoint cannot process images; explicit stop() disables Funnel.
    }
}
