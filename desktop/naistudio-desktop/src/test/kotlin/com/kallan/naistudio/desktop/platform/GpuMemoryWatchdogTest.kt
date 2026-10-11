package com.kallan.naistudio.desktop.platform

import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class GpuMemoryWatchdogTest {
    private val limit = 6L * 1024 * 1024 * 1024
    private fun seconds(value: Long) = TimeUnit.SECONDS.toNanos(value)

    @Test fun requiresStrictlyMoreThanSixGiBForThreeContinuousSeconds() {
        val policy = GpuPressurePolicy()
        assertFalse(policy.observe(limit, 0))
        assertFalse(policy.observe(limit, seconds(10)))
        assertFalse(policy.observe(limit + 1, seconds(11)))
        assertFalse(policy.observe(limit + 1, seconds(14) - 1))
        assertTrue(policy.observe(limit + 1, seconds(14)))
    }

    @Test fun shortSpikesAndMissingSamplesBreakTheContinuousWindow() {
        val policy = GpuPressurePolicy()
        assertFalse(policy.observe(limit + 1, 0))
        assertFalse(policy.observe(limit, seconds(2)))
        assertFalse(policy.observe(limit + 1, seconds(3)))
        assertFalse(policy.observe(null, seconds(5)))
        assertFalse(policy.observe(limit + 1, seconds(6)))
        assertFalse(policy.observe(limit + 1, seconds(8)))
        assertTrue(policy.observe(limit + 1, seconds(9)))
    }

    @Test fun qualifiedPressureWaitsForTaskCompletionAndReleasesOnce() {
        var now = 0L
        var idle = false
        var resident = true
        var used: Long? = limit + 1
        var releases = 0
        var reads = 0
        val memory = object : GpuMemoryUsage {
            override fun usedBytes(): Long? { reads++; return used }
        }
        GpuMemoryWatchdog(memory, { resident }, {
            if (idle) { releases++; resident = false; true } else false
        }, { now }, autoStart = false).use { monitor ->
            monitor.checkNow()
            now = seconds(3); monitor.checkNow()
            assertEquals(0, releases)
            used = 0; now = seconds(4); monitor.checkNow()
            assertEquals(0, releases)
            idle = true; monitor.checkNow()
            assertEquals(1, releases)
            val before = reads
            now = seconds(20); monitor.checkNow()
            assertEquals(before, reads)
            assertEquals(1, releases)
        }
    }

    @Test fun unavailableMonitoringDoesNotLeaveCachesResidentForever() {
        var now = 0L
        var releases = 0
        val memory = object : GpuMemoryUsage { override fun usedBytes(): Long? = null }
        GpuMemoryWatchdog(memory, { true }, { releases++; true }, { now }, autoStart = false).use { monitor ->
            monitor.checkNow()
            now = seconds(3) - 1; monitor.checkNow(); assertEquals(0, releases)
            now = seconds(3); monitor.checkNow(); assertEquals(1, releases)
        }
    }
}
