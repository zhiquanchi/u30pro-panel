package com.u30pro.panel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.ui.Connection
import com.u30pro.panel.ui.PanelUiState
import com.u30pro.panel.ui.components.Banner
import com.u30pro.panel.ui.components.KeyValueRow
import com.u30pro.panel.ui.components.Metric
import com.u30pro.panel.ui.components.RateChart
import com.u30pro.panel.ui.components.SectionCard
import com.u30pro.panel.ui.components.SignalBars
import com.u30pro.panel.ui.components.StatusPill
import com.u30pro.panel.ui.components.formatBytes
import com.u30pro.panel.ui.components.formatDuration
import com.u30pro.panel.ui.components.formatRate
import com.u30pro.panel.ui.components.formatSignal
import com.u30pro.panel.ui.components.signalColor
import com.u30pro.panel.ui.components.signalLabel

@Composable
fun OverviewScreen(state: PanelUiState, onRefresh: () -> Unit) {
    val status = state.status ?: StatusSnapshot()
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
                        text = "状态总览",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.profile?.model ?: "U30 Pro",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = connectionLabel(state.connection),
                        color = connectionColor(state.connection),
                    )
                    IconButton(onClick = onRefresh, enabled = !state.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            }
        }

        if (state.sessionBusy) {
            item {
                Banner(
                    text = "原厂后台正在占用管理会话，已让位 30 秒后自动恢复。",
                    tone = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item { SignalCard(status) }
        item { SpeedCard(status, state.rateHistory) }
        item { TrafficCard(status) }
        item { DeviceCard(status) }
    }
}

@Composable
private fun SignalCard(status: StatusSnapshot) {
    val accent = signalColor(status.rsrp)
    SectionCard(title = "信号") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                label = "RSRP",
                value = status.rsrp?.toString() ?: "—",
                unit = if (status.rsrp != null) "dBm" else null,
                accent = accent,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(status.rsrp)
                Spacer(Modifier.width(10.dp))
                StatusPill(text = signalLabel(status.rsrp), color = accent)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))

        KeyValueRow(
            "网络",
            status.networkLabel +
                (status.networkType?.takeIf { it != status.networkLabel }?.let { " · $it" } ?: ""),
        )
        KeyValueRow("运营商", status.provider ?: "—")
        KeyValueRow(
            "SINR",
            status.sinr?.let { String.format(java.util.Locale.US, "%.1f dB", it) } ?: "—",
        )
        KeyValueRow("RSRQ", status.rsrq?.let { "$it dB" } ?: "—")
        KeyValueRow("频段", status.band ?: "—")
        KeyValueRow("PCI / 小区", listOfNotNull(status.pci, status.cellId).joinToString(" / ").ifBlank { "—" })
    }
}

@Composable
private fun SpeedCard(status: StatusSnapshot, history: List<com.u30pro.panel.data.model.RatePoint>) {
    SectionCard(title = "实时速率") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(
                label = "下行",
                value = formatRate(status.downBps).removeSuffix("/s"),
                accent = MaterialTheme.colorScheme.primary,
            )
            Metric(
                label = "上行",
                value = formatRate(status.upBps).removeSuffix("/s"),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        RateChart(history)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(MaterialTheme.colorScheme.primary)
            Text(
                " 下行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            LegendDot(MaterialTheme.colorScheme.secondary)
            Text(
                " 上行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "最近 ${history.size} 秒",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(7.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun TrafficCard(status: StatusSnapshot) {
    val monthTotal = if (status.monthRxBytes == null && status.monthTxBytes == null) {
        null
    } else {
        (status.monthRxBytes ?: 0L) + (status.monthTxBytes ?: 0L)
    }
    val sessionTotal = if (status.sessionRxBytes == null && status.sessionTxBytes == null) {
        null
    } else {
        (status.sessionRxBytes ?: 0L) + (status.sessionTxBytes ?: 0L)
    }

    SectionCard(title = "流量") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Metric(label = "本月", value = formatBytes(monthTotal))
            Metric(label = "本次会话", value = formatBytes(sessionTotal))
            Metric(label = "在线时长", value = formatDuration(status.uptimeSeconds))
        }
        Spacer(Modifier.height(10.dp))
        KeyValueRow("本月下行", formatBytes(status.monthRxBytes))
        KeyValueRow("本月上行", formatBytes(status.monthTxBytes))
    }
}

@Composable
private fun DeviceCard(status: StatusSnapshot) {
    SectionCard(title = "设备") {
        KeyValueRow("型号", status.model ?: "—")
        KeyValueRow("WiFi 客户端", status.wifiClients?.let { "$it 台" } ?: "—")
        KeyValueRow(
            "电量",
            buildString {
                append(status.batteryPercent?.let { "$it%" } ?: "—")
                when (status.charging) {
                    true -> append(" · 充电中")
                    false -> append(" · 未充电")
                    null -> Unit
                }
            },
        )
        status.temperatureC?.let { KeyValueRow("温度", String.format(java.util.Locale.US, "%.1f ℃", it)) }
        KeyValueRow("未读短信", status.smsUnread?.let { "$it 条" } ?: "—")
        KeyValueRow("WAN IP", status.wanIp ?: "—")
        KeyValueRow("IMEI", status.imei ?: "—")
        KeyValueRow("ICCID", status.iccid ?: "—")
        KeyValueRow("本机号码", status.msisdn ?: "—")
    }
}

private fun connectionLabel(connection: Connection): String = when (connection) {
    Connection.Connected -> "已连接"
    Connection.Connecting -> "连接中"
    Connection.Yielded -> "让位中"
    Connection.Failed -> "连接异常"
    Connection.Disconnected -> "未连接"
}

@Composable
private fun connectionColor(connection: Connection): Color = when (connection) {
    Connection.Connected -> MaterialTheme.colorScheme.primary
    Connection.Connecting -> MaterialTheme.colorScheme.secondary
    Connection.Yielded -> MaterialTheme.colorScheme.tertiary
    Connection.Failed -> MaterialTheme.colorScheme.error
    Connection.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
}