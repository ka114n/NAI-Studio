package com.kallan.naistudio.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.state.AppState
import kotlinx.coroutines.launch

/**
 * 登录/注册门禁页（仅托管模式且未登录时由外壳渲染，见 StudioShell）。
 *
 * 登录/注册成功后 `AppState.authState` 变为 LoggedIn，外壳据此自动切回主界面——
 * 本页不需要自己做导航。服务器地址在此页出现时一定已配置（门禁条件保证）。
 */
@Composable
fun LoginScreen(state: AppState) {
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }
    val scope = rememberCoroutineScope()

    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val busy = state.authBusy

    val submit: () -> Unit = submit@{
        if (busy) return@submit
        error = ""
        if (email.isBlank()) { error = t("account.emailEmpty"); return@submit }
        if (password.isBlank()) { error = t("account.passwordEmpty"); return@submit }
        scope.launch {
            val err = if (registerMode) {
                state.register(email, password, invite.ifBlank { null })
            } else {
                state.login(email, password)
            }
            // 成功时 authState 变 LoggedIn，外壳自动切走；失败留在本页显示错误
            if (err != null) error = err
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("NAI Studio", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (registerMode) t("account.register") else t("account.welcome"),
                style = MaterialTheme.typography.bodyMedium,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = "" },
                label = { Text(t("account.email")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = "" },
                label = { Text(t("account.password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (registerMode) {
                OutlinedTextField(
                    value = invite,
                    onValueChange = { invite = it; error = "" },
                    label = { Text(t("account.inviteCode")) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error.isNotEmpty()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.negative,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = submit,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp).widthIn(max = 18.dp),
                    )
                    Text(t("account.processing"))
                } else {
                    Text(if (registerMode) t("account.register") else t("account.login"))
                }
            }

            TextButton(onClick = { registerMode = !registerMode; error = "" }, enabled = !busy) {
                Text(if (registerMode) t("account.switchToLogin") else t("account.switchToRegister"))
            }

            // 「我已有 NovelAI API，跳过」：切回 BYO 直连模式（关账号模式），去设置里填自己的 token。
            // 没有 API 的用户没有这个出口，只能登录使用。
            TextButton(onClick = { state.useOwnNovelAiToken() }, enabled = !busy) {
                Text(t("account.skip"), color = com.kallan.naistudio.ui.LocalRef.current.muted)
            }
            Text(
                t("account.skipHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
        }
    }
}
