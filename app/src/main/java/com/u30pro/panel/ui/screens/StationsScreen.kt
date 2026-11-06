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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.model.Station
import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.SectionCard
import com.u30pro.panel.ui.components.StatusPill

@Composable
fun StationsScreen(
    state: PanelUiState,
    onRefresh: () -> Unit,
    onBlock: (Station) -> Unit,
    onUnblock: (Station) -> Unit,
    onRename: (Station) -> Unit,
) {
    val snapshot = state.stations
    val canRename = state.profile?.let { it.renameSupported || it.aclSupported } == true

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "已连设备",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = snapshot?.let { "WiFi ${it.wifiCount} · 有线 ${it.lanCount}" } ?: "尚未加载",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }

        if (state.sessionBusy) {
            item {
                Banner(
                    text = "原厂后台占用了管理会话，设备列表会在让位结束后刷新。",
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (snapshot != null && !snapshot.aclSupported) {
            item {
                Banner(
                    text = "当前固件未返回 ACL 列表，只能查看设备，无法拉黑/解黑。",
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (snapshot == null) {
            item {
                SectionCard(title = "等待数据") {
                    Text(
                        text = "正在读取 station_list…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (snapshot.stations.isEmpty()) {
            item {
                SectionCard(title = "没有客户端") {
                    Text(
                        text = "设备返回的在线列表为空。如果原厂后台能看到设备，可能是该固件使用了不同的字段，去「诊断」页重新探测。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(snapshot.stations, key = { it.mac }) { station ->
                StationCard(
                    station = station,
                    canManage = snapshot.aclSupported,
                    canRename = canRename,
                    onBlock = onBlock,
                    onUnblock = onUnblock,
                    onRename = onRename,
                )
            }
        }

        item {
            Text(
                text = "重命名使用 EDIT_HOSTNAME（U30 Pro 原厂 UI 已包含该命令），执行时会短暂登录并写入。" +
                    "拉黑通过 ACL 黑名单写入，会让该设备断开并在下次连接时被拒绝。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StationCard(
    station: Station,
    canManage: Boolean,
    canRename: Boolean,
    onBlock: (Station) -> Unit,
    onUnblock: (Station) -> Unit,
    onRename: (Station) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = if (station.kind == Station.Kind.WIFI) "WiFi" else "有线",
                            color = if (station.kind == Station.Kind.WIFI) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (station.blocked) {
                            StatusPill("已拉黑", MaterialTheme.colorScheme.error)
                        }
                        if (station.hostname.isNullOrBlank()) {
                            StatusPill("未命名", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = { onRename(station) }, enabled = canRename && !station.blocked) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = if (canRename && !station.blocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = listOfNotNull(station.mac, station.ip).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (station.blocked) {
                    OutlinedButton(onClick = { onUnblock(station) }, enabled = canManage) {
                        Text("解黑")
                    }
                } else {
                    Button(
                        onClick = { onBlock(station) },
                        enabled = canManage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text("拉黑")
                    }
                }
            }
        }
    }
}