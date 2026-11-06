package com.u30pro.panel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0A0F14)
private val Surface = Color(0xFF111820)
private val SurfaceHigh = Color(0xFF1A2430)
private val Mint = Color(0xFF34D399)
private val Cyan = Color(0xFF22D3EE)
private val Amber = Color(0xFFFBBF24)
private val Coral = Color(0xFFFF6B6B)
private val Text = Color(0xFFE6EDF3)
private val Muted = Color(0xFF9AA7B4)

private val U30Colors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF04120C),
    primaryContainer = Color(0xFF123B2C),
    onPrimaryContainer = Color(0xFFB9F5D9),
    secondary = Cyan,
    onSecondary = Color(0xFF03181D),
    tertiary = Amber,
    onTertiary = Color(0xFF1B1300),
    background = Ink,
    onBackground = Text,
    surface = Surface,
    onSurface = Text,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    outline = Color(0xFF2B3A49),
    outlineVariant = Color(0xFF1E2A36),
    error = Coral,
    onError = Color(0xFF1B0505),
    errorContainer = Color(0xFF3A1111),
    onErrorContainer = Color(0xFFFFD9D9),
)

@Composable
fun U30Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = U30Colors,
        typography = Typography(),
        content = content,
    )
}