package com.kallan.naistudio.services

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test

class FunnelDnsTest {
    private val ip = InetAddress.getByAddress(byteArrayOf(103, 84, -101, -103))
    private fun systemDns(resolve: (String) -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String) = resolve(hostname)
    }
    @Test fun successfulSystemResolutionNeverContactsPublicDns() {
        val dns = FunnelDns(systemDns { listOf(ip) }) { fail("Unexpected public DNS query"); emptyList() }
        assertEquals(listOf(ip), dns.lookup("test.ts.net"))
    }
    @Test fun failedFunnelResolutionUsesHttpsFallback() {
        val dns = FunnelDns(systemDns { throw UnknownHostException() }) { host ->
            assertEquals("test.ts.net", host); listOf(ip)
        }
        assertEquals(listOf(ip), dns.lookup("test.ts.net"))
    }
    @Test fun unrelatedHostFailuresAreNeverSentToPublicFallback() {
        val original = UnknownHostException("private-host")
        var fallbackCalled = false
        val dns = FunnelDns(systemDns { throw original }) { fallbackCalled = true; emptyList() }
        try { dns.lookup("private.example"); fail() }
        catch (error: UnknownHostException) { assertSame(original, error) }
        assertFalse(fallbackCalled)
    }
    @Test fun failedFallbackPreservesOriginalLookupError() {
        val original = UnknownHostException("Funnel DNS unavailable")
        val dns = FunnelDns(systemDns { throw original }) { throw java.io.IOException("Offline") }
        try { dns.lookup("test.ts.net"); fail() }
        catch (error: UnknownHostException) { assertSame(original, error) }
    }
}
