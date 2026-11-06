package com.u30pro.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.u30pro.panel.BuildConfig
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.KeyValueRow
import com.u30pro.panel.ui.components.SectionCard

@Composable
fun SettingsScreen(
    state: PanelUiState,
    onPollInterval: (Long) -> Unit,
    onLogout: (clearSaved: Boolean) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "轮询、会话与本地数据",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.sessionBusy) {
            item {
                Banner(
                    text = "设备只允许一个管理会话，原厂网页打开时本 App 会暂停轮询。",
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item {
            SectionCard(title = "状态轮询") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1_000L to "1 秒", 2_000L to "2 秒", 5_000L to "5 秒").forEach { (ms, label) ->
                        FilterChip(
                            selected = state.pollIntervalMs == ms,
                            onClick = { onPollInterval(ms) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "状态读取不占用登录会话；只有写操作会短暂登录一次。App 退到后台时会自动停止轮询。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "当前连接") {
                KeyValueRow("网关", state.profile?.gateway ?: state.gateway)
                KeyValueRow("端口", state.profile?.port?.toString() ?: "—")
                KeyValueRow(
                    "登录算法",
                    when (state.profile?.loginScheme) {
                        com.u30pro.panel.data.profile.LoginScheme.LD_SHA256 -> "LD + 双 SHA-256"
                        com.u30pro.panel.data.profile.LoginScheme.BASE64 -> "Base64（老固件）"
                        null -> "—"
                    },
                )
                KeyValueRow("固件", state.profile?.firmware ?: "—")
                KeyValueRow("已保存密码", if (state.savedPassword) "是" else "否")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onLogout(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("断开连接（保留密码）")
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("断开并清除本地数据")
                    }
                }
            }
        }

        item {
            SectionCard(title = "关于") {
                KeyValueRow("版本", BuildConfig.VERSION_NAME)
                KeyValueRow("范围", "不 root · 只走原厂 goform API")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "本 App 不修改固件、不注入页面、不绕过计费。温度字段只有设备真的返回时才展示；" +
                        "U30 系列的多数固件把温度放在本地 thermal zone，原厂 API 并不提供。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                text = "协议参考：u30mon（U30 Air 实测）、UFI-AXIS（MIT，会话策略）、AirDeck（行为参考）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除本地数据？") },
            text = { Text("会删除保存的管理密码和设备档案。下次连接需要重新输入密码并重新探测。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onLogout(true)
                    },
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}