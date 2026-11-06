package com.u30pro.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.SectionCard

@Composable
fun ConnectScreen(
    state: PanelUiState,
    onConnect: (gateway: String, password: String, remember: Boolean) -> Unit,
) {
    var gateway by rememberSaveable { mutableStateOf(state.gateway.ifBlank { "192.168.0.1" }) }
    var password by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = "U30 Pro",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "不 root 的本地管理面板",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        SectionCard(title = "连接设备") {
            OutlinedTextField(
                value = gateway,
                onValueChange = { gateway = it },
                label = { Text("网关") },
                placeholder = { Text("192.168.0.1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("管理密码") },
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("记住密码", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "会话失效时自动重新登录，密码用 Keystore 加密保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = remember, onCheckedChange = { remember = it })
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Banner(text = state.error, tone = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onConnect(gateway, password, remember) },
                enabled = !state.busy && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("正在探测设备…")
                } else {
                    Text("连接并探测")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "开始之前") {
            Text(
                text = "1. 手机先在系统设置里连接 U30 Pro 的 WiFi。\n" +
                    "2. 管理密码就是原厂后台的登录密码。\n" +
                    "3. 首次连接会探测固件支持哪些接口字段，通常几秒钟。\n" +
                    "4. U30 Pro 的状态读取不需要登录会话；只有拉黑/解黑/重命名会短暂登录，日常监控不会和原厂网页互踢。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "只读取设备状态，不修改固件。拉黑/解黑与重命名会写入设备配置，操作前会二次确认。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}