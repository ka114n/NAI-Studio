package com.kallan.naistudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.services.ApplicationRelease
import com.kallan.naistudio.services.ApplicationUpdates
import kotlinx.coroutines.*
import java.io.File

/** One startup check; explicit download/install. Network errors never interrupt startup. */
@Composable
fun ApplicationUpdateHost(version: String, kind: String, directory: File, install: (File) -> Unit, language: String, manual: Boolean = false) {
    val english = language.startsWith("en")
    fun label(zh: String, en: String) = if (english) en else zh
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ApplicationRelease?>(null) }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var message by remember { mutableStateOf("") }
    var downloaded by remember { mutableStateOf<File?>(null) }
    fun checkUpdate(explicit: Boolean) { scope.launch {
        if (busy) return@launch
        busy = true
        if (explicit) { visible = true; message = label("正在检查更新…", "Checking for updates…") }
        try {
            release = withContext(Dispatchers.IO) { ApplicationUpdates.check(kind, version) }
            downloaded = null
            if (release != null) { visible = true; message = "" }
            else if (explicit) message = label("当前已是最新版本", "You are up to date")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (explicit) message = label("检查失败：", "Check failed: ") + (e.message ?: "")
        } finally { busy = false }
    } }
    LaunchedEffect(Unit) { if (!manual) { delay(8000); checkUpdate(false) } }
    if (manual) OutlinedButton(onClick = { checkUpdate(true) }, enabled = !busy) { Text(label("检查更新", "Check for updates")) }
    if (visible) AlertDialog(
        onDismissRequest = { if (!busy) visible = false },
        title = { Text(release?.let { label("发现新版本 ", "Update available: ") + it.version } ?: label("应用更新", "Application update")) },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label("当前版本：", "Current version: ") + version)
            release?.let {
                Text(it.notes)
                Text(if (kind == "Windows") label("安装会退出并重启程序；设置和图片保留。", "Installation restarts the app and keeps your data.") else label("安装由系统确认，覆盖安装保留数据。请勿卸载旧版。", "Confirm installation in Android. Update without uninstalling to retain data."))
            }
            if (progress >= 0) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Text("${(progress * 100).toInt()}%") }
            if (message.isNotBlank()) Text(message)
        } },
        confirmButton = { if (release != null) TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                val item = release!!
                val file = downloaded ?: withContext(Dispatchers.IO) {
                    ApplicationUpdates.download(item, directory) { value -> scope.launch { progress = value } }
                }.also { downloaded = it }
                withContext(Dispatchers.IO) { ApplicationUpdates.verify(file, item) }
                if (kind == "Windows") withContext(Dispatchers.IO) { install(file) } else install(file)
                message = label("已交给安装器；如果刚授权安装权限，请再点一次安装。", "Installer opened. If you just granted installation permission, tap Install again.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = label("更新失败：", "Update failed: ") + (e.message ?: "")
            } finally { busy = false }
        } }) { Text(if (downloaded == null) label("下载并更新", "Download and update") else label("安装更新", "Install update")) } else TextButton(enabled = !busy, onClick = { visible = false }) { Text(label("确定", "OK")) } },
        dismissButton = { if (release != null) TextButton(enabled = !busy, onClick = { visible = false }) { Text(label("稍后", "Later")) } }
    )
}
