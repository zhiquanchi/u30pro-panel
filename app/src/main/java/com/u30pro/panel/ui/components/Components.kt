package com.u30pro.panel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.u30pro.panel.data.model.RatePoint
import com.u30pro.panel.data.parse.StatusParser
import java.util.Locale
import kotlin.math.max

// ---------------------------------------------------------------------------
// formatting
// ---------------------------------------------------------------------------

fun formatBytes(value: Long?): String {
    if (value == null || value < 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = 0
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024
        index++
    }
    return if (index == 0) {
        "${value} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", amount, units[index])
    }
}

fun formatRate(bytesPerSecond: Long?): String =
    if (bytesPerSecond == null) "—" else formatBytes(bytesPerSecond) + "/s"

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}天 ${hours}小时"
        hours > 0 -> "${hours}小时 ${minutes}分"
        else -> "${minutes}分"
    }
}

fun formatSignal(value: Int?): String = value?.let { "$it dBm" } ?: "—"

fun formatDecimal(value: Double?, suffix: String = ""): String =
    value?.let { String.format(Locale.US, "%.1f%s", it, suffix) } ?: "—"

fun signalLabel(rsrp: Int?): String = when (StatusParser.quality(rsrp)) {
    StatusParser.SignalQuality.EXCELLENT -> "极佳"
    StatusParser.SignalQuality.GOOD -> "良好"
    StatusParser.SignalQuality.FAIR -> "一般"
    StatusParser.SignalQuality.POOR -> "较弱"
    StatusParser.SignalQuality.UNKNOWN -> "—"
}

@Composable
fun signalColor(rsrp: Int?): Color = when (StatusParser.quality(rsrp)) {
    StatusParser.SignalQuality.EXCELLENT -> MaterialTheme.colorScheme.primary
    StatusParser.SignalQuality.GOOD -> MaterialTheme.colorScheme.secondary
    StatusParser.SignalQuality.FAIR -> MaterialTheme.colorScheme.tertiary
    StatusParser.SignalQuality.POOR -> MaterialTheme.colorScheme.error
    StatusParser.SignalQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------------------------------------------------------------------------
// reusable pieces
// ---------------------------------------------------------------------------

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun Metric(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Banner(text: String, tone: Color = MaterialTheme.colorScheme.tertiary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(tone, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tone,
        )
    }
}

@Composable
fun SignalBars(rsrp: Int?, modifier: Modifier = Modifier) {
    val active = when (StatusParser.quality(rsrp)) {
        StatusParser.SignalQuality.EXCELLENT -> 5
        StatusParser.SignalQuality.GOOD -> 4
        StatusParser.SignalQuality.FAIR -> 3
        StatusParser.SignalQuality.POOR -> 2
        StatusParser.SignalQuality.UNKNOWN -> 0
    }
    val color = signalColor(rsrp)
    Row(
        modifier = modifier.height(26.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(5) { index ->
            Box(
                Modifier
                    .width(6.dp)
                    .height((8 + index * 4).dp)
                    .background(
                        color = if (index < active) color else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
fun RateChart(history: List<RatePoint>, modifier: Modifier = Modifier) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        if (history.isEmpty()) return@Canvas

        val maxValue = max(
            history.maxOfOrNull { max(it.downBps, it.upBps) } ?: 0L,
            1024L,
        ).toFloat()

        val stepX = if (history.size <= 1) 0f else size.width / (history.size - 1)

        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        fun drawSeries(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value / maxValue) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = color, style = Stroke(width = 3f))
        }

        drawSeries(history.map { it.downBps.toFloat() }, downColor)
        drawSeries(history.map { it.upBps.toFloat() }, upColor)
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}