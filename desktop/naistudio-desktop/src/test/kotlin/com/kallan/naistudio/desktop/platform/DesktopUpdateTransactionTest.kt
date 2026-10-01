package com.kallan.naistudio.desktop.platform

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DesktopUpdateTransactionTest {
    @Test fun installsAndRetainsPreviousFiles() = exercise(false)
    @Test fun restoresPreviousFilesIfReplacementFails() = exercise(true)

    private fun exercise(fail: Boolean) {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = Files.createTempDirectory("nai-update-transaction").toFile()
        val target = File(root, "target"); val stage = File(root, "work/stage")
        for (base in listOf(target, stage)) {
            File(base, "app").mkdirs(); File(base, "runtime").mkdirs()
            val content = if (base == target) "old" else "new"
            File(base, "app/NAI Studio.cfg").writeText(content)
            File(base, "runtime/sentinel").writeText(content)
            File(base, "NAI Studio.exe").writeText(content)
            File(base, "NAI Studio.ico").writeText(content)
        }
        // A user file alongside the app must not be replaced or removed.
        File(target, "my-image.png").writeText("keep")
        val helper = File(root, "helper.ps1")
        // The failure UI is suppressed in a headless fixture; filesystem transaction code is unchanged.
        helper.writeText(DesktopApplicationUpdater.installerScript.lines().filterNot {
            it.contains("Add-Type -AssemblyName PresentationFramework") || it.contains("[System.Windows.MessageBox]")
        }.joinToString("\n"))
        fun quote(file: File) = "'" + file.absolutePath.replace("'", "''") + "'"
        val harness = File(root, "harness.ps1")
        harness.writeText("""
${'$'}ErrorActionPreference = 'Stop'
${'$'}script:failOnce = ${if (fail) "\$true" else "\$false"}
function Start-Process { param([string]${'$'}FilePath, [string]${'$'}WorkingDirectory) }
function Move-Item {
    param([string]${'$'}LiteralPath, [string]${'$'}Destination)
    if (${'$'}script:failOnce -and ${'$'}LiteralPath -eq (Join-Path ${quote(stage)} 'runtime')) {
        ${'$'}script:failOnce = ${'$'}false
        throw 'Injected replacement failure'
    }
    Microsoft.PowerShell.Management\Move-Item -LiteralPath ${'$'}LiteralPath -Destination ${'$'}Destination -ErrorAction Stop
}
. ${quote(helper)} -TargetDir ${quote(target)} -StageDir ${quote(stage)} -AppPid 2147483647
""".trimIndent())
        try {
            val shell = File(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe")
            val log = File(root, "test.log")
            val process = ProcessBuilder(shell.path, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", harness.path)
                .redirectErrorStream(true).redirectOutput(log).start()
            assertTrue("Updater fixture timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals(log.readText(), 0, process.exitValue())
            val expected = if (fail) "old" else "new"
            for (name in listOf("app/NAI Studio.cfg", "runtime/sentinel", "NAI Studio.exe", "NAI Studio.ico"))
                assertEquals(name, expected, File(target, name).readText())
            assertEquals("keep", File(target, "my-image.png").readText())
            val result = File(root, "work/result.txt").readText()
            assertTrue(result, result.contains(if (fail) "Injected replacement failure" else "Update installed"))
            if (!fail) assertEquals("old", File(root, "work/previous/app/NAI Studio.cfg").readText())
        } finally { root.deleteRecursively() }
    }
}
