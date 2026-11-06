package com.zcodeandriod.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设计令牌：唯一取色处。主题映射只在本目录 Theme.kt 做，
 * 其余代码一律引用 MaterialTheme.colorScheme，不直接引用这里的 Color。
 */
object ZcodeSpace {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val radius: Dp = 14.dp
}

// 品牌主蓝：延续 v0.2 启动图标与吉祥物配图（branding/mascot/SOURCES.md）的视觉资产
private object Brand {
    val Primary = Color(0xFF1E50D0)
    val PrimaryDark = Color(0xFF9DB8FF)
}

internal fun lightColorScheme() = androidx.compose.material3.lightColorScheme(
    primary = Brand.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF0A1E52),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF152033),
    surface = Color.White,
    onSurface = Color(0xFF152033),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5C6A80),
    outline = Color(0xFFDFE4ED),
    error = Color(0xFFB3261E),
)

internal fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
    primary = Brand.PrimaryDark,
    onPrimary = Color(0xFF0A1E52),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE5FF),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
)
