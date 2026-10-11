package com.kallan.naistudio.services

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Only a failed system lookup of a Funnel domain uses public HTTPS DNS. No pairing key is sent. */
internal class FunnelDns(
    private val system: Dns = Dns.SYSTEM,
    private val fallback: (String) -> List<InetAddress> = ::lookupFunnelDns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = try {
        system.lookup(hostname)
    } catch (error: UnknownHostException) {
        if (!hostname.lowercase().endsWith(".ts.net")) throw error
        val addresses = runCatching { fallback(hostname) }.getOrDefault(emptyList())
        if (addresses.isEmpty()) throw error
        addresses
    }
}

private val dnsTransport by lazy {
    OkHttpClient.Builder().dns(Dns.SYSTEM).callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
}

private fun lookupFunnelDns(hostname: String): List<InetAddress> {
    for (type in listOf("A", "AAAA")) {
        val url = "https://cloudflare-dns.com/dns-query".toHttpUrl().newBuilder()
            .addQueryParameter("name", hostname).addQueryParameter("type", type)
            .addQueryParameter("cachebust", System.currentTimeMillis().toString()).build()
        val request = Request.Builder().url(url).header("Accept", "application/dns-json")
            .header("Cache-Control", "no-cache").build()
        val addresses = dnsTransport.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val input = response.body?.byteStream() ?: error("Missing DNS response")
            val bytes = input.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 64 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val result = JSONObject(String(bytes, Charsets.UTF_8))
            val answers = result.optJSONArray("Answer")
            buildList {
                if (answers != null) for (i in 0 until answers.length()) {
                    val item = answers.getJSONObject(i)
                    val ip = item.optString("data")
                    if (item.optInt("type") == 1 && ip.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) ||
                        item.optInt("type") == 28 && ip.matches(Regex("[0-9a-fA-F:]+"))) add(InetAddress.getByName(ip))
                }
            }
        }
        if (addresses.isNotEmpty()) return addresses
    }
    return emptyList()
}
