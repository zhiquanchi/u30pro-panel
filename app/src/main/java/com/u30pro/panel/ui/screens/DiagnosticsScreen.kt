package com.u30pro.panel.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.profile.DeviceProfile
import com.u30pro.panel.data.profile.ProbeReport
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.KeyValueRow
import com.u30pro.panel.ui.components.MonoText
import com.u30pro.panel.ui.components.SectionCard
import com.u30pro.panel.ui.components.StatusPill
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val prettyJson = Json { prettyPrint = true }

@Composable
fun DiagnosticsScreen(
    state: PanelUiState,
    onRerunProbe: () -> Unit,
    onExport: () -> Unit,
) {
    val probe = state.probe
    val profile = state.profile
    val clipboard = LocalClipboardManager.current
    var showRaw by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "诊断 / 自检",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "固件能力探测结果与原始字段",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRerunProbe, enabled = !state.busy) {
                        Text(if (state.busy) "探测中…" else "重新探测")
                    }
                    OutlinedButton(onClick = onExport, enabled = !state.busy) {
                        Text("导出 JSON")
                    }
                }
                OutlinedButton(
                    onClick = {
                        profile?.let {
                            clipboard.setText(
                                AnnotatedString(
                                    prettyJson.encodeToString(DeviceProfile.serializer(), it),
                                ),
                            )
                        }
                    },
                    enabled = profile != null,
                ) {
                    Text("复制档案")
                }
            }
        }

        item {
            SectionCard(title = "adb 取回（电脑不用连随身 WiFi）") {
                MonoText(
                    "adb pull /sdcard/Android/data/com.u30pro.panel/files/diagnostics/latest.json",
                )
                state.lastExportPath?.let { path ->
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("最近导出", path)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "先让状态刷新几秒再导出，status.raw 才完整。自动连接复用保存档案时 groups 为空，点「重新探测」后才有逐字段命中率。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        profile?.let { item { ProfileCard(it) } }

        if (probe != null && probe.groups.isNotEmpty()) {
            item {
                Text(
                    text = "字段探测",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(probe.groups.entries.toList()) { (name, report) ->
                GroupCard(name = name, requested = report.requested, available = report.available, missing = report.missing)
            }
        } else if (probe != null) {
            item {
                Banner("当前使用保存的档案，未重新逐字段探测。需要完整报告请点「重新探测」。")
            }
        }

        probe?.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
            item {
                SectionCard(title = "说明") {
                    notes.forEach { note ->
                        Text(
                            text = "· $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "原始字段",
                trailing = {
                    OutlinedButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "收起" else "展开")
                    }
                },
            ) {
                val raw = state.status?.raw.orEmpty()
                if (raw.isEmpty()) {
                    Text(
                        "还没有读到状态数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!showRaw) {
                    Text(
                        "共 ${raw.size} 个字段，展开可查看原始键值，便于对比固件差异。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    raw.toSortedMap().forEach { (key, value) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MonoText(
                                text = key,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            MonoText(text = value.take(60))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "U30 Pro 的原厂字段没有公开验证。若这里的字段和原厂网页显示不一致，" +
                    "把「复制档案」的内容和 docs/probe-u30pro.json 一起保留，方便后续适配。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ProfileCard(profile: DeviceProfile) {
    SectionCard(title = "设备档案") {
        KeyValueRow("网关", "${profile.gateway}:${profile.port}")
        KeyValueRow(
            "登录算法",
            when (profile.loginScheme) {
                com.u30pro.panel.data.profile.LoginScheme.LD_SHA256 -> "LD + 双 SHA-256"
                com.u30pro.panel.data.profile.LoginScheme.BASE64 -> "Base64（老固件）"
            },
        )
        KeyValueRow("型号", profile.model ?: "—")
        KeyValueRow("固件", profile.firmware ?: "—")
        KeyValueRow("可用字段", "${profile.availableFields.size} 个")
        KeyValueRow("温度字段", profile.temperatureField ?: "不可用")
        KeyValueRow("拉黑/解黑", if (profile.aclSupported) "支持" else "不支持")
        KeyValueRow(
            "重命名",
            if (profile.renameSupported || profile.aclSupported) "支持（EDIT_HOSTNAME）" else "未探测",
        )
        KeyValueRow(
            "探测时间",
            if (profile.probedAt > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(profile.probedAt))
            } else {
                "—"
            },
        )
        if (profile.stationKeys.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill("客户端字段")
                MonoText(profile.stationKeys.joinToString(", "))
            }
        }
    }
}

@Composable
private fun GroupCard(name: String, requested: Int, available: Int, missing: List<String>) {
    val progress = if (requested == 0) 0f else available.toFloat() / requested
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$available / $requested",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (available == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MonoText("缺失: " + missing.joinToString(", ").take(400))
            }
        }
    }
}