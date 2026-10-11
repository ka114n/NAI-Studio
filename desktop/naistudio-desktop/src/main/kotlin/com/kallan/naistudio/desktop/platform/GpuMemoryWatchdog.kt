package com.kallan.naistudio.desktop.platform

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal interface GpuMemoryUsage : AutoCloseable {
    fun usedBytes(): Long?
    override fun close() {}
}

/** Reads driver-wide usage without spawning nvidia-smi or loading a CUDA model. */
internal class NvidiaGpuMemory : GpuMemoryUsage {
    private var library: NativeLibrary? = null
    private var initialized = false
    private var closed = false

    @Synchronized override fun usedBytes(): Long? = runCatching {
        if (closed) return null
        val dll = library ?: run {
            val candidates = listOf(
                File(System.getenv("SystemRoot") ?: "C:/Windows", "System32/nvml.dll"),
                File(System.getenv("ProgramFiles") ?: "C:/Program Files", "NVIDIA Corporation/NVSMI/nvml.dll"),
            )
            NativeLibrary.getInstance(candidates.first { it.isFile }.absolutePath).also { library = it }
        }
        if (!initialized) {
            check(dll.getFunction("nvmlInit_v2").invokeInt(emptyArray()) == 0)
            initialized = true
        }
        val count = IntByReference()
        check(dll.getFunction("nvmlDeviceGetCount_v2").invokeInt(arrayOf(count)) == 0)
        check(count.value in 1..64)
        var maximum = 0L
        Memory(24).use { memory ->
            for (index in 0 until count.value) {
                val handle = PointerByReference()
                check(dll.getFunction("nvmlDeviceGetHandleByIndex_v2").invokeInt(arrayOf(index, handle)) == 0)
                check(dll.getFunction("nvmlDeviceGetMemoryInfo").invokeInt(arrayOf(handle.value, memory)) == 0)
                // nvmlMemory_t: uint64 total, free, used. Includes other applications.
                maximum = maxOf(maximum, memory.getLong(16))
            }
        }
        maximum.takeIf { it >= 0 }
    }.getOrNull()

    @Synchronized override fun close() {
        closed = true
        if (initialized) runCatching { library?.getFunction("nvmlShutdown")?.invokeInt(emptyArray()) }
        initialized = false
        library?.close(); library = null
    }
}

/** A qualified pressure event remains pending until an idle task boundary can release it. */
internal class GpuPressurePolicy(
    private val limitBytes: Long = 6L * 1024 * 1024 * 1024,
    private val holdNanos: Long = TimeUnit.SECONDS.toNanos(3),
) {
    private var highSince: Long? = null
    private var pending = false
    fun observe(usedBytes: Long?, now: Long): Boolean {
        if (usedBytes != null && usedBytes > limitBytes) {
            val start = highSince ?: now.also { highSince = it }
            if (now - start >= holdNanos) pending = true
        } else highSince = null
        return pending
    }
    fun reset() { highSince = null; pending = false }
}

internal class GpuMemoryWatchdog(
    private val memory: GpuMemoryUsage,
    private val hasResidents: () -> Boolean,
    private val releaseIdle: () -> Boolean,
    private val clock: () -> Long = System::nanoTime,
    autoStart: Boolean = true,
) : AutoCloseable {
    private val policy = GpuPressurePolicy()
    private var unavailableSince: Long? = null
    private var closed = false
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "nai-gpu-memory-monitor").apply { isDaemon = true }
    }
    init {
        if (autoStart) timer.scheduleWithFixedDelay({ runCatching { checkNow() } }, 500, 500, TimeUnit.MILLISECONDS)
    }
    @Synchronized internal fun checkNow() {
        if (closed) return
        if (!hasResidents()) { policy.reset(); unavailableSince = null; return }
        val now = clock()
        val used = runCatching { memory.usedBytes() }.getOrNull()
        if (used == null) { if (unavailableSince == null) unavailableSince = now }
        else unavailableSince = null
        val unavailable = unavailableSince?.let { now - it >= TimeUnit.SECONDS.toNanos(3) } == true
        if ((policy.observe(used, now) || unavailable) && releaseIdle()) {
            policy.reset(); unavailableSince = null
        }
    }
    @Synchronized override fun close() {
        closed = true; timer.shutdownNow(); memory.close()
    }
}
