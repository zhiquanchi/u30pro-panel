package com.u30pro.panel.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.settings.ApnProfile
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.KeyValueRow
import com.u30pro.panel.ui.components.SectionCard
import com.u30pro.panel.ui.components.StatusPill

@Composable
fun ApnScreen(
    state: PanelUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (ApnProfile) -> Unit,
    onDelete: (Int) -> Unit,
    onSetDefault: (ApnProfile) -> Unit,
) {
    val apns = state.settings?.apns.orEmpty()
    var editTarget by remember { mutableStateOf<ApnProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApnProfile?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APN 配置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "默认 APN 决定蜂窝数据走哪个接入点",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }

        item {
            SectionCard(title = "当前 APN") {
                val snapshot = state.settings
                KeyValueRow("配置", snapshot?.text("profile_name") ?: "—")
                KeyValueRow("APN", snapshot?.text("apn_wan_apn") ?: "—")
                KeyValueRow("PDP", snapshot?.text("apn_pdp_type") ?: "—")
                KeyValueRow("模式", snapshot?.text("apn_mode") ?: "—")
            }
        }

        item {
            Button(
                onClick = { creating = true },
                enabled = !state.busy && apns.size < 20,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新增 APN")
            }
        }

        if (apns.isEmpty()) {
            item {
                Text(
                    "没有可编辑的 APN 配置。设备当前使用上面显示的自动配置；新增会写入一个新的 APN 条目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(apns, key = { it.index }) { apn ->
                ApnCard(
                    apn = apn,
                    busy = state.busy,
                    onEdit = { editTarget = apn },
                    onDelete = { deleteTarget = apn },
                    onSetDefault = { onSetDefault(apn) },
                )
            }
        }

        item {
            Text(
                text = "APN 写入会短暂登录设备；如果填错可能导致蜂窝数据无法连接，请保留默认配置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (creating || editTarget != null) {
        val original = editTarget
        ApnEditDialog(
            title = if (original == null) "新增 APN" else "编辑 APN",
            initial = original ?: ApnProfile(
                index = (apns.maxOfOrNull { it.index } ?: -1) + 1,
                name = "",
                apn = "",
                mode = "manual",
                dial = "*99#",
                auth = "none",
                username = "",
                password = "",
                pdpType = "IPv4v6",
                raw = "",
            ),
            onDismiss = {
                creating = false
                editTarget = null
            },
            onSave = { profile ->
                creating = false
                editTarget = null
                onSave(profile)
            },
        )
    }

    deleteTarget?.let { apn ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 APN？") },
            text = { Text("${apn.name.ifBlank { apn.apn }} 将被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDelete(apn.index)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ApnCard(
    apn: ApnProfile,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = apn.name.ifBlank { "未命名 APN" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (apn.isDefault) {
                    StatusPill("默认", MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${apn.apn.ifBlank { "—" }} · ${apn.pdpType.ifBlank { "—" }} · ${apn.auth.ifBlank { "none" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSetDefault, enabled = !busy && !apn.isDefault) {
                    Text("设为默认")
                }
                OutlinedButton(onClick = onEdit, enabled = !busy) { Text("编辑") }
                OutlinedButton(onClick = onDelete, enabled = !busy) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ApnEditDialog(
    title: String,
    initial: ApnProfile,
    onDismiss: () -> Unit,
    onSave: (ApnProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var apn by remember { mutableStateOf(initial.apn) }
    var dial by remember { mutableStateOf(initial.dial.ifBlank { "*99#" }) }
    var auth by remember { mutableStateOf(initial.auth.ifBlank { "none" }) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var pdp by remember { mutableStateOf(initial.pdpType.ifBlank { "IPv4v6" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ApnField("名称", name) { name = it }
                ApnField("APN", apn) { apn = it }
                ApnField("拨号号码", dial) { dial = it }
                ApnField("认证方式", auth) { auth = it }
                ApnField("用户名", username) { username = it }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                ApnField("PDP 类型", pdp) { pdp = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            apn = apn.trim(),
                            dial = dial.trim(),
                            auth = auth.trim(),
                            username = username.trim(),
                            password = password,
                            pdpType = pdp.trim(),
                        ),
                    )
                },
                enabled = name.isNotBlank() && apn.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ApnField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}