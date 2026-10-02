package com.kallan.naistudio.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import com.kallan.naistudio.ui.RefButton as Button
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import com.kallan.naistudio.ui.RefTextButton as TextButton
import com.kallan.naistudio.ui.SectionCard

/** Explicit, password-protected export of saved data including credentials. No secrets are displayed. */
@Composable
fun FullBackupControls(state: AppState) {
    val context = LocalContext.current
    val english = state.settings.language.startsWith("en")
    fun label(zh: String, en: String) = if (english) en else zh
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }
    fun run(uri: Uri, restore: Boolean) {
        choosing = false
        val secret = password.toCharArray()
        password = ""; confirmation = ""
        state.runFullBackup(uri, secret, restore)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        choosing = false
        if (uri != null) run(uri, false)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        choosing = false
        if (uri != null) run(uri, true)
    }
    SectionCard(label("完整迁移备份（含密钥和图片）", "Full migration backup (keys and images)")) {
        Text(label(
            "包含全部已保存设置、API Key / 登录凭据、历史记录、原图、无限画布快照、预设正文和对话记忆。使用独立密码加密，普通 JSON 备份不能代替。",
            "Includes all saved settings, API keys/login credentials, history, original images, infinite-canvas snapshots, preset files and conversation memory. Password-encrypted; the regular JSON backup is not a substitute."
        ), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.fullBackupMode = "export"; password = ""; confirmation = ""; state.fullBackupMessage = ""; state.fullBackupVerified = false }, enabled = !state.busy && !state.fullBackupOperationRunning && !state.infiniteRunning, modifier = Modifier.weight(1f)) {
                Text(label("导出完整备份", "Export all"))
            }
            OutlinedButton(onClick = { state.fullBackupMode = "restore"; password = ""; confirmation = ""; state.fullBackupMessage = ""; state.fullBackupVerified = false }, enabled = !state.busy && !state.fullBackupOperationRunning && !state.infiniteRunning, modifier = Modifier.weight(1f)) {
                Text(label("恢复完整备份", "Restore all"))
            }
        }
    }
    if (state.fullBackupMode.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!state.fullBackupOperationRunning && !state.fullBackupNeedsClose && !choosing) { state.fullBackupMode = ""; password = ""; confirmation = "" } },
        title = { Text(if (state.fullBackupMode == "export") label("导出所有已保存数据", "Export all saved data") else label("恢复完整备份", "Restore full backup")) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label(
                "文件包含 API Key 与机密信息，请勿分享。密码至少 8 位，遗忘后无法恢复。请保存到手机“下载”等应用目录外的位置，卸载前确认备份校验成功。未保存的画布编辑和撤销记录不包含。",
                "Contains API keys and confidential data. Do not share. Use at least 8 characters; a lost password cannot be recovered. Save outside the app (e.g. Downloads) and verify before uninstalling. Unsaved edits and undo stacks are excluded."
            ), style = MaterialTheme.typography.bodySmall)
            if (state.fullBackupMode == "restore") Text(label("恢复将替换当前已保存数据，完成后必须关闭重开。", "Restoring replaces saved data. Close and reopen afterwards."))
            if (!state.fullBackupVerified && !state.fullBackupOperationRunning && !state.fullBackupNeedsClose) {
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(label("备份密码", "Backup password")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), singleLine = true)
                if (state.fullBackupMode == "export") OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, label = { Text(label("再次输入密码", "Confirm password")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), singleLine = true)
            }
            if (state.fullBackupOperationRunning) CircularProgressIndicator()
            if (state.fullBackupMessage.isNotBlank()) Text(state.fullBackupMessage, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = {
            TextButton(enabled = !state.fullBackupOperationRunning && !choosing && (state.fullBackupNeedsClose || state.fullBackupVerified || (password.length >= 8 && (state.fullBackupMode == "restore" || password == confirmation))), onClick = {
                if (state.fullBackupNeedsClose) {
                    findBackupActivity(context)?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else if (state.fullBackupVerified) { state.fullBackupMode = ""; password = ""; confirmation = "" }
                else {
                    choosing = true
                    if (state.fullBackupMode == "export") exportLauncher.launch("NAI-Studio-full-${java.time.LocalDate.now()}.naibackup")
                    else importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                }
            }) { Text(if (state.fullBackupNeedsClose) label("关闭应用", "Close app") else if (state.fullBackupVerified) label("完成", "Done") else label("选择文件", "Choose file")) }
        },
        dismissButton = { if (!state.fullBackupNeedsClose && !state.fullBackupVerified) TextButton(enabled = !state.fullBackupOperationRunning && !choosing, onClick = { state.fullBackupMode = ""; password = ""; confirmation = "" }) { Text(label("取消", "Cancel")) } }
    )
}

private fun findBackupActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findBackupActivity(context.baseContext)
    else -> null
}
