package com.u30pro.panel.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.settings.SmsMessage
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.StatusPill

@Composable
fun SmsScreen(
    state: PanelUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSend: (String, String) -> Unit,
    onDelete: (List<Long>) -> Unit,
    onMarkRead: (List<Long>) -> Unit,
) {
    var detail by remember { mutableStateOf<SmsMessage?>(null) }
    var composing by remember { mutableStateOf(false) }

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
                        text = "短信",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "读取、发送与管理设备短信",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { composing = true }, enabled = !state.busy) {
                    Icon(Icons.Filled.Edit, contentDescription = "写短信")
                }
                IconButton(onClick = onRefresh, enabled = !state.busy && !state.smsLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }

        if (state.smsLoading) {
            item {
                Text(
                    "正在读取短信…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.smsMessages.isEmpty() && !state.smsLoading) {
            item {
                Text(
                    "没有短信。点右上角铅笔可以发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.smsMessages, key = { it.id }) { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { detail = message },
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = message.number.ifBlank { "未知号码" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (message.unread) StatusPill("未读", MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = message.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = message.body.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "发送短信可能产生运营商费用；删除会立即从设备中移除。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    detail?.let { message ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(message.number.ifBlank { "短信" }) },
            text = {
                Column {
                    Text(
                        message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(message.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (message.unread) onMarkRead(listOf(message.id))
                        detail = null
                    },
                ) {
                    Text(if (message.unread) "标记已读" else "关闭")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDelete(listOf(message.id))
                            detail = null
                        },
                    ) { Text("删除") }
                    TextButton(onClick = { detail = null }) { Text("取消") }
                }
            },
        )
    }

    if (composing) {
        var number by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { composing = false },
            title = { Text("发送短信") },
            text = {
                Column {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("号码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("内容") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        composing = false
                        onSend(number, body)
                    },
                    enabled = number.isNotBlank() && body.isNotBlank() && !state.busy,
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { composing = false }) { Text("取消") }
            },
        )
    }
}