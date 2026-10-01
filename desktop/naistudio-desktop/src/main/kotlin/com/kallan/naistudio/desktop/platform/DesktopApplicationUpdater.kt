package com.kallan.naistudio.desktop.platform

import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.system.exitProcess

object DesktopApplicationUpdater {
    /** Only shipped program paths may enter staging; reject traversal and oversized archives. */
    fun extract(archive: File, stage: File) {
        check(stage.mkdirs() || stage.isDirectory)
        var bytes = 0L
        val seen = mutableSetOf<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                require(!name.contains('\\') && !name.startsWith('/') && !name.contains(':')) { "Unsafe update archive path" }
                val file = File(stage, name).canonicalFile
                require(seen.add(file.path.lowercase())) { "Duplicate update archive path" }
                require(file.toPath().startsWith(stage.canonicalFile.toPath()) && file != stage.canonicalFile) { "Update archive escapes staging directory" }
                require(name.substringBefore('/') in setOf("app", "runtime", "NAI Studio.exe", "NAI Studio.ico")) { "Unexpected update archive entry" }
                if (entry.isDirectory) { check(file.mkdirs() || file.isDirectory); continue }
                check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
                zip.getInputStream(entry).use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= 1073741824L) { "Update archive exceeds extraction limit" }
                        output.write(buffer, 0, n)
                    }
                } }
            }
        }
        require(File(stage, "NAI Studio.exe").isFile && File(stage, "app/NAI Studio.cfg").isFile &&
            File(stage, "app/naistudio-desktop.jar").isFile && File(stage, "runtime/bin/java.dll").isFile) { "Incomplete Windows update package" }
    }

    fun install(archive: File) {
        val launcher = System.getProperty("jpackage.app-path")?.let { File(it).canonicalFile }
            ?: error("Automatic installation is available in the packaged Windows application only")
        require(launcher.name == "NAI Studio.exe" && launcher.isFile)
        val target = launcher.parentFile
        require(File(target, "app/NAI Studio.cfg").isFile && File(target, "runtime").isDirectory)
        File.createTempFile("nai-update-access-", ".tmp", target).also { check(it.delete()) }
        val work = File(archive.parentFile, "install-${UUID.randomUUID()}")
        val stage = File(work, "stage")
        extract(archive, stage)
        val script = File(work, "install.ps1")
        script.writeText(installerScript, Charsets.UTF_8)
        val shell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
        val helper = ProcessBuilder(shell.absolutePath, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.absolutePath,
            "-TargetDir", target.absolutePath, "-StageDir", stage.absolutePath, "-AppPid", ProcessHandle.current().pid().toString())
            .redirectErrorStream(true).redirectOutput(File(work, "helper.log")).start()
        val ready = File(work, "ready.txt")
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!ready.isFile && helper.isAlive && System.nanoTime() < deadline) Thread.sleep(100)
        check(ready.isFile && helper.isAlive) { "Update helper could not start; see ${work.absolutePath}" }
        exitProcess(0)
    }

    // The helper waits for exit. Moves retain the previous application for rollback; no recursive deletion.
    val installerScript: String = """
param([string]${'$'}TargetDir, [string]${'$'}StageDir, [int]${'$'}AppPid)
${'$'}ErrorActionPreference = 'Stop'
${'$'}target = [IO.Path]::GetFullPath(${'$'}TargetDir)
${'$'}stage = [IO.Path]::GetFullPath(${'$'}StageDir)
${'$'}work = Split-Path ${'$'}stage -Parent
${'$'}backup = Join-Path ${'$'}work 'previous'
${'$'}names = @('app','runtime','NAI Studio.exe','NAI Studio.ico')
${'$'}moved = @()
${'$'}installed = @()
try {
    'Ready' | Set-Content -LiteralPath (Join-Path ${'$'}work 'ready.txt')
    ${'$'}process = Get-Process -Id ${'$'}AppPid -ErrorAction SilentlyContinue
    if (${'$'}process -and !${'$'}process.WaitForExit(120000)) { throw 'Application did not exit; installation cancelled' }
    if (!(Test-Path -LiteralPath (Join-Path ${'$'}target 'app\NAI Studio.cfg'))) { throw 'Invalid installation directory' }
    New-Item -ItemType Directory -Path ${'$'}backup -Force | Out-Null
    foreach (${'$'}name in ${'$'}names) {
        ${'$'}old = Join-Path ${'$'}target ${'$'}name
        ${'$'}new = Join-Path ${'$'}stage ${'$'}name
        if (!(Test-Path -LiteralPath ${'$'}new)) { continue }
        if ((Split-Path ([IO.Path]::GetFullPath(${'$'}old)) -Parent) -ne ${'$'}target) { throw 'Unsafe installation target' }
        if (Test-Path -LiteralPath ${'$'}old) {
            if ((Get-Item -LiteralPath ${'$'}old -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked installation path is unsupported' }
            Move-Item -LiteralPath ${'$'}old -Destination (Join-Path ${'$'}backup ${'$'}name)
            ${'$'}moved += ${'$'}name
        }
        Move-Item -LiteralPath ${'$'}new -Destination ${'$'}old
        ${'$'}installed += ${'$'}name
    }
    Start-Process -FilePath (Join-Path ${'$'}target 'NAI Studio.exe') -WorkingDirectory ${'$'}target
    'Update installed; previous files retained here for recovery.' | Set-Content -LiteralPath (Join-Path ${'$'}work 'result.txt')
} catch {
    ${'$'}failure = ${'$'}_.Exception.Message
    try {
        foreach (${'$'}name in ${'$'}installed) { Move-Item -LiteralPath (Join-Path ${'$'}target ${'$'}name) -Destination (Join-Path ${'$'}stage ${'$'}name) }
        foreach (${'$'}name in ${'$'}moved) { Move-Item -LiteralPath (Join-Path ${'$'}backup ${'$'}name) -Destination (Join-Path ${'$'}target ${'$'}name) }
        Start-Process -FilePath (Join-Path ${'$'}target 'NAI Studio.exe') -WorkingDirectory ${'$'}target
    } catch { ${'$'}failure += '; recovery: ' + ${'$'}_.Exception.Message }
    ${'$'}failure | Set-Content -LiteralPath (Join-Path ${'$'}work 'result.txt')
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show('NAI Studio update failed: ' + ${'$'}failure + "`nLog: " + ${'$'}work) | Out-Null
}
""".trimIndent()
}
