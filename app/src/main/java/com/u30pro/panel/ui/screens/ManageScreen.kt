package com.u30pro.panel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.settings.ControlType
import com.u30pro.panel.data.settings.SettingGroup
import com.u30pro.panel.data.settings.SettingRisk
import com.u30pro.panel.data.settings.SettingSpec
import com.u30pro.panel.data.settings.SettingsCatalog
import com.u30pro.panel.data.settings.SettingsSnapshot
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.SectionCard

@Composable
fun ManageScreen(
    state: PanelUiState,
    onRefresh: () -> Unit,
    onWrite: (String, Map<String, String>) -> Unit,
    onOpenApn: () -> Unit,
    onOpenSms: () -> Unit,
    onConfirmWrite: () -> Unit,
    onDismissWrite: () -> Unit,
) {
    val snapshot = state.settings
    var choiceId by remember { mutableStateOf<String?>(null) }
    var textId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "设备管理",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "移动网络、WiFi、系统与流量",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.busy && !state.settingsLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }

        if (state.sessionBusy) {
            item {
                Banner(
                    text = "设备后台正被占用，写入操作会在让位结束后重试。",
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (snapshot == null) {
            item {
                SectionCard(title = "读取设置") {
                    if (state.settingsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "正在读取设备设置…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    } else {
                        Text(
                            "还没有读到设置，点右上角刷新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            SettingGroup.entries.forEach { group ->
                val specs = SettingsCatalog.supported(group, snapshot)
                if (specs.isNotEmpty()) {
                    item {
                        SectionCard(title = group.title) {
                            specs.sortedBy { it.advanced }.forEachIndexed { index, spec ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                if (spec.advanced && (index == 0 || !specs[index - 1].advanced)) {
                                    Banner(
                                        text = "高级操作：可能断网或断开当前手机，请确认后再执行。",
                                        tone = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                SettingRow(
                                    spec = spec,
                                    snapshot = snapshot,
                                    enabled = !state.busy,
                                    onToggle = { checked ->
                                        onWrite(
                                            spec.id,
                                            mapOf("value" to if (checked) "1" else "0"),
                                        )
                                    },
                                    onChoice = { choiceId = spec.id },
                                    onText = { textId = spec.id },
                                    onAction = {
                                        when (spec.actionId) {
                                            "OPEN_APN" -> onOpenApn()
                                            "OPEN_SMS" -> onOpenSms()
                                            else -> onWrite(spec.id, emptyMap())
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            val supportedCount = SettingGroup.entries.sumOf {
                SettingsCatalog.supported(it, snapshot).size
            }
            val hidden = SettingsCatalog.specs.size - supportedCount
            item {
                Text(
                    text = if (hidden > 0) {
                        "当前固件有 $hidden 项不可用（对应字段未返回），已在界面中隐藏；完整字段可到诊断页查看。"
                    } else {
                        "所有已注册设置项在当前固件均可用。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    choiceId?.let { id ->
        val spec = SettingsCatalog.byId(id)
        val options = (spec?.control as? ControlType.Choice)?.options.orEmpty()
        if (spec != null) {
            AlertDialog(
                onDismissRequest = { choiceId = null },
                title = { Text(spec.title) },
                text = {
                    Column {
                        options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        choiceId = null
                                        onWrite(spec.id, mapOf("value" to option.value))
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = spec.display(snapshot ?: SettingsSnapshot()) == option.label,
                                    onClick = {
                                        choiceId = null
                                        onWrite(spec.id, mapOf("value" to option.value))
                                    },
                                )
                                Text(option.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { choiceId = null }) { Text("取消") }
                },
            )
        }
    }

    textId?.let { id ->
        val spec = SettingsCatalog.byId(id)
        val control = spec?.control as? ControlType.Text
        if (spec != null && control != null) {
            val initial = if (control.secret) {
                ""
            } else {
                spec.requiredFields.firstOrNull()?.let { key -> snapshot?.raw(key) }.orEmpty()
            }
            var value by remember(spec.id, snapshot?.fetchedAt) { mutableStateOf(initial) }
            AlertDialog(
                onDismissRequest = { textId = null },
                title = { Text(spec.title) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text(control.hint ?: spec.title) },
                            singleLine = true,
                            visualTransformation = if (control.secret) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (control.numeric) KeyboardType.Number else KeyboardType.Text,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        spec.subtitle?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            textId = null
                            onWrite(spec.id, mapOf("value" to value))
                        },
                        enabled = value.isNotBlank(),
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { textId = null }) { Text("取消") }
                },
            )
        }
    }

    state.pendingWrite?.let { pending ->
        val spec = SettingsCatalog.byId(pending.specId) ?: return@let
        var typed by remember(pending.specId, pending.input) { mutableStateOf("") }
        val strong = spec.risk == SettingRisk.STRONG
        AlertDialog(
            onDismissRequest = onDismissWrite,
            title = { Text(if (strong) "高风险操作：${spec.title}" else "确认修改：${spec.title}") },
            text = {
                Column {
                    Text(
                        if (strong) {
                            "该操作可能导致断网、断开当前手机或需要重新连接设备。确认继续？"
                        } else {
                            "会写入设备配置，是否继续？"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (strong) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text("输入「确认」继续") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmWrite,
                    enabled = !strong || typed.trim() == "确认",
                ) {
                    Text("执行")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissWrite) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingRow(
    spec: SettingSpec,
    snapshot: SettingsSnapshot,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onChoice: () -> Unit,
    onText: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            spec.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            spec.display(snapshot)?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        when (val control = spec.control) {
            ControlType.Toggle -> Switch(
                checked = toggleState(spec, snapshot) ?: false,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
            is ControlType.Choice -> OutlinedButton(onClick = onChoice, enabled = enabled) {
                Text(spec.display(snapshot) ?: "设置")
            }
            is ControlType.Text -> OutlinedButton(onClick = onText, enabled = enabled) {
                Text(if (control.secret) "修改" else "编辑")
            }
            ControlType.Action -> Button(
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(
                    when (spec.actionId) {
                        "OPEN_APN", "OPEN_SMS" -> "打开"
                        "REBOOT" -> "重启"
                        "OTA_CHECK" -> "检查"
                        "RESET_TRAFFIC" -> "清零"
                        "UNLOCK_CELLS" -> "解除"
                        else -> "执行"
                    },
                )
            }
            ControlType.ReadOnly -> Unit
        }
    }
}

private fun toggleState(spec: SettingSpec, snapshot: SettingsSnapshot): Boolean? {
    val key = spec.requiredFields.firstOrNull() ?: return null
    if (key == "ppp_status") {
        val status = snapshot.text(key) ?: return null
        return status.contains("connect", ignoreCase = true) &&
            !status.contains("disconnect", ignoreCase = true)
    }
    return snapshot.bool(key)
}