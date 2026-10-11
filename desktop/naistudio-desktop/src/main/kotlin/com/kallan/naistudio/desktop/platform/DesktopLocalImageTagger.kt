package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.LocalImageTagger
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Isolated, optional NVIDIA GPU runtime. Nothing is installed system-wide or bundled with the EXE. */
internal class DesktopLocalImageTagger(
    private val root: File = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "NAI Studio/local-tagger",
    ),
    gpuMemory: GpuMemoryUsage = NvidiaGpuMemory(),
) : LocalImageTagger {
    private val lock = Mutex()
    @Volatile private var closed = false
    @Volatile private var worker: Process? = null
    private var output: LinkedBlockingQueue<String>? = null
    @Volatile private var vfxWorker: Process? = null
    private var vfxOutput: LinkedBlockingQueue<String>? = null
    private val watchdog = GpuMemoryWatchdog(gpuMemory,
        hasResidents = { worker?.isAlive == true || vfxWorker?.isAlive == true },
        releaseIdle = {
            if (lock.tryLock()) {
                try { releaseWorkers(); true } finally { lock.unlock() }
            } else false
        },
    )
    internal fun residentWorkerIds(): Set<Long> = listOfNotNull(worker, vfxWorker)
        .filter { it.isAlive }.map { it.pid() }.toSet()

    init { Runtime.getRuntime().addShutdownHook(Thread({ close() }, "local-tagger-shutdown")) }

    private val python get() = File(root, "env-cuda126/Scripts/python.exe")
    private val uv get() = File(root, "uv.exe")

    override suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                require(File(imagePath).isFile) { "Image file no longer exists" }
                try {
                    // Downloads may take a while; cancellation remains available throughout.
                    withTimeout(60 * 60 * 1000L) {
                        if (worker?.isAlive != true) {
                            prepare(onProgress)
                            val script = File(root, "worker-cuda126.py")
                            javaClass.getResourceAsStream("/local-tagger/worker.py")!!.use {
                                script.writeBytes(it.readBytes())
                            }
                            val process = start(listOf(python.absolutePath, "-u", script.absolutePath,
                                File(root, "model").absolutePath))
                            worker = process
                            check(!closed) { "GPU runtime is closed" }
                            output = readLines(process)
                            // stderr must be drained to prevent a full pipe from stalling inference.
                            Thread({ process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                                while (reader.readLine() != null) { /* no images or diagnostics persisted */ }
                            } }, "local-tagger-diagnostics").apply { isDaemon = true; start() }
                            val ready = awaitEvent(process, output!!, "ready", onProgress)
                            check(ready.optString("device").startsWith("cuda")) {
                                "Local model must run on GPU; CPU fallback is disabled"
                            }
                            onProgress("gpu")
                        }
                        val process = worker!!
                        process.outputStream.write((JSONObject().put("image", imagePath).toString() + "\n")
                            .toByteArray(Charsets.UTF_8))
                        process.outputStream.flush()
                        val event = awaitEvent(process, output!!, "result", onProgress)
                        event.getString("text").also {
                            require(it.isNotBlank()) { "No tags met the model confidence thresholds" }
                        }
                    }
                } catch (error: Throwable) {
                    releaseWorkers()
                    throw error
                }
            }
        }

    private suspend fun prepareBase(onProgress: (String) -> Unit) {
        require(System.getProperty("os.name").startsWith("Windows", true) &&
            System.getProperty("os.arch") in listOf("amd64", "x86_64")) {
            "RTX VFX currently requires 64-bit Windows"
        }
        root.mkdirs()
        if (!uv.isFile) downloadUv(onProgress)
        if (!python.isFile) {
            runCommand(listOf(uv.absolutePath, "venv", "--managed-python", "--python", "3.11.13",
                File(root, "env-cuda126").absolutePath))
        }
        val baseMarker = File(root, "cuda-runtime-cuda126.txt")
        if (baseMarker.takeIf { it.exists() }?.readText() != BASE_ENVIRONMENT) {
            onProgress("cuda")
            runCommand(listOf(uv.absolutePath, "pip", "install", "--link-mode", "copy", "--python", python.absolutePath,
                "--index-url", "https://pypi.org/simple", TORCH_CUDA, TORCHVISION_CUDA, "numpy==2.2.6", "Pillow==11.3.0"))
            runCommand(listOf(python.absolutePath, "-c", "import torch, torchvision, PIL; assert torch.cuda.is_available() and torch.version.cuda is not None, 'CUDA PyTorch and an NVIDIA GPU are required'"))
            baseMarker.writeText(BASE_ENVIRONMENT)
        }
    }

    private suspend fun prepare(onProgress: (String) -> Unit) {
        prepareBase(onProgress)
        val marker = File(root, "environment-cuda126.txt")
        if (marker.takeIf { it.exists() }?.readText() == ENVIRONMENT) return
        onProgress("dependencies")
        runCommand(listOf(uv.absolutePath, "pip", "install", "--link-mode", "copy", "--python", python.absolutePath,
            "--index-url", "https://pypi.org/simple", "transformers==4.57.1", "timm==1.0.20", "huggingface-hub==0.36.0",
            "safetensors==0.6.2"))
        runCommand(listOf(python.absolutePath, "-c", "import torch, torchvision, transformers, timm, PIL; assert torch.version.cuda is not None, 'CUDA PyTorch required'"))
        marker.writeText(ENVIRONMENT)
    }

    private suspend fun prepareVfx(onProgress: (String) -> Unit) {
        prepareBase(onProgress)
        val marker = File(root, "nvidia-vfx-0.2.0.0.txt")
        if (marker.takeIf { it.exists() }?.readText() == VFX_ENVIRONMENT) return
        onProgress("vfx")
        runCommand(listOf(uv.absolutePath, "pip", "install", "--link-mode", "copy", "--python", python.absolutePath,
            "--index-url", "https://pypi.nvidia.com", "nvidia-vfx==0.2.0.0"))
        runCommand(listOf(python.absolutePath, "-c", "import torch, nvvfx; assert torch.cuda.is_available()"))
        marker.writeText(VFX_ENVIRONMENT)
    }

    override suspend fun upscale(imagePath: String): ByteArray = withContext(Dispatchers.IO) {
        lock.withLock {
            require(File(imagePath).isFile) { "Image file no longer exists" }
            try {
                withTimeout(20 * 60 * 1000L) {
                    if (vfxWorker?.isAlive != true) {
                        prepareVfx {}
                        val script = File(root, "rtx-vfx-upscale.py")
                        javaClass.getResourceAsStream("/local-tagger/rtx-vfx-upscale.py")!!.use { script.writeBytes(it.readBytes()) }
                        val process = start(listOf(python.absolutePath, "-u", script.absolutePath, "--worker"))
                        vfxWorker = process
                        check(!closed) { "GPU runtime is closed" }
                        vfxOutput = readLines(process)
                        drainDiagnostics(process)
                        val ready = awaitEvent(process, vfxOutput!!, "ready") {}
                        check(ready.optString("device") == "cuda") { "RTX worker must run on GPU" }
                    }
                    val outputFile = File.createTempFile("nai-rtx-upscale-", ".png", root)
                    try {
                        val process = vfxWorker!!
                        val request = JSONObject().put("image", File(imagePath).absolutePath)
                            .put("output", outputFile.absolutePath).toString() + "\n"
                        process.outputStream.write(request.toByteArray(Charsets.UTF_8))
                        process.outputStream.flush()
                        awaitEvent(process, vfxOutput!!, "result") {}
                        check(outputFile.isFile && outputFile.length() > 0) { "RTX did not produce an image" }
                        outputFile.readBytes()
                    } finally { outputFile.delete() }
                }
            } catch (error: Throwable) {
                vfxWorker?.let { terminate(it) }; vfxWorker = null; vfxOutput = null
                throw error
            }
        }
    }

    private fun drainDiagnostics(process: Process) {
        Thread({ process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (reader.readLine() != null) { /* never block on SDK diagnostics */ }
        } }, "rtx-vfx-diagnostics").apply { isDaemon = true; start() }
    }

    private suspend fun downloadUv(onProgress: (String) -> Unit) {
        val archive = File(root, "uv.zip.part")
        try {
            val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(UV_URL).build()).execute().use { response ->
                check(response.isSuccessful) { "Runtime download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty runtime download")
                val length = body.contentLength()
                var downloaded = 0L
                var lastPercent = -1
                body.byteStream().use { input -> archive.outputStream().use { stream ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        stream.write(bytes, 0, n)
                        downloaded += n
                        val percent = if (length > 0) (downloaded * 100 / length).toInt() else 0
                        if (percent != lastPercent) { onProgress("runtime:$percent"); lastPercent = percent }
                    }
                } }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == UV_SHA256) {
                "Runtime download checksum mismatch; retry the download"
            }
            val temporary = File(root, "uv.exe.part")
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // Extract only the verified executable; never use archive-controlled paths.
                    if (entry.name.substringAfterLast('/') == "uv.exe") {
                        temporary.outputStream().use { zip.copyTo(it) }
                        break
                    }
                }
            }
            check(temporary.isFile) { "Runtime archive does not contain uv.exe" }
            java.nio.file.Files.move(temporary.toPath(), uv.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { archive.delete() }
    }

    private fun start(arguments: List<String>): Process {
        check(!closed) { "GPU runtime is closed" }
        val builder = ProcessBuilder(arguments).directory(root)
        val environment = builder.environment()
        environment["PYTHONUTF8"] = "1"
        environment["PYTHONIOENCODING"] = "utf-8"
        environment.remove("PYTHONHOME")
        environment.remove("PYTHONPATH")
        environment["UV_PYTHON_INSTALL_DIR"] = File(root, "python").absolutePath
        environment["UV_CACHE_DIR"] = File(root, "cache").absolutePath
        environment["HF_HOME"] = File(root, "hf-cache").absolutePath
        environment["HF_HUB_DISABLE_TELEMETRY"] = "1"
        environment["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"
        environment["HF_HUB_DISABLE_XET"] = "1"
        environment["DO_NOT_TRACK"] = "1"
        environment["UV_NO_PROGRESS"] = "1"
        return builder.start().also { process ->
            if (closed) { terminate(process); error("GPU runtime is closed") }
        }
    }

    private fun readLines(process: Process): LinkedBlockingQueue<String> {
        val queue = LinkedBlockingQueue<String>()
        Thread({
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) { queue.offer(reader.readLine() ?: break) }
                }
            } finally { queue.offer("{\"event\":\"exit\"}") }
        }, "local-tagger-output").apply { isDaemon = true; start() }
        return queue
    }

    private suspend fun awaitEvent(
        process: Process, lines: LinkedBlockingQueue<String>, wanted: String, onProgress: (String) -> Unit,
    ): JSONObject {
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = lines.poll()
            if (line != null) {
                val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
                when (event.getString("event")) {
                    "progress" -> onProgress(event.getString("stage"))
                    "error" -> error(event.optString("message", "Local tagging failed"))
                    "exit" -> error("Local tagging worker exited unexpectedly")
                    wanted -> return event
                }
            } else {
                check(process.isAlive) { "GPU worker exited unexpectedly" }
                delay(50)
            }
        }
    }

    private suspend fun runCommand(arguments: List<String>) {
        val process = start(arguments)
        val tail = java.util.ArrayDeque<String>()
        val reader = Thread({
            process.errorStream.bufferedReader(Charsets.UTF_8).use { input ->
                while (true) {
                    val line = input.readLine() ?: break
                    synchronized(tail) { tail.addLast(line); if (tail.size > 12) tail.removeFirst() }
                }
            }
        }, "local-tagger-setup").apply { isDaemon = true; start() }
        readLines(process)
        try {
            while (process.isAlive) { currentCoroutineContext().ensureActive(); delay(100) }
            reader.join(1000)
            check(process.exitValue() == 0) {
                "Environment setup failed: " + synchronized(tail) { tail.joinToString("\n") }.takeLast(1800)
            }
        } finally { terminate(process) }
    }

    override fun close() {
        closed = true
        watchdog.close()
        releaseWorkers()
    }

    private fun releaseWorkers() {
        vfxWorker?.let { terminate(it) }
        vfxWorker = null
        vfxOutput = null
        worker?.let { terminate(it) }
        worker = null
        output = null
    }

    private fun terminate(process: Process) {
        runCatching { process.descendants().use { children -> children.forEach { it.destroyForcibly() } } }
        process.destroyForcibly()
    }

    companion object {
        private const val ENVIRONMENT = "cuda126-py31113-torch280-transformers4571-v2"
        private const val BASE_ENVIRONMENT = "cuda126-base-v1"
        private const val VFX_ENVIRONMENT = "nvidia-vfx-0.2.0.0"
        // Official CUDA 12.6 wheels; main CDN serves identical files and avoids R2 403s.
        private const val TORCH_CUDA = "https://download.pytorch.org/whl/cu126/torch-2.8.0%2Bcu126-cp311-cp311-win_amd64.whl#sha256=cfb2c640a8955fbd8686c056802f53a512b610c098960d6dad5800cbc16c02b6"
        private const val TORCHVISION_CUDA = "https://download.pytorch.org/whl/cu126/torchvision-0.23.0%2Bcu126-cp311-cp311-win_amd64.whl#sha256=921ec706184a4b79190483724d439fb2f769b4cde83e3031a7b7ac097676a63b"
        private const val UV_URL = "https://github.com/astral-sh/uv/releases/download/0.8.22/uv-x86_64-pc-windows-msvc.zip"
        private const val UV_SHA256 = "5049375aa2a5162f132b2c1cb992e25d42d47d934cab8c174dbe6f60973dcc12"
    }
}
