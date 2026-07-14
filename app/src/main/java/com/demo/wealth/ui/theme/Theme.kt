package com.demo.wealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * WealthLab 主题 - 现代金融科技风格（仅浅色模式）
 *
 * 设计原则：
 * - 统一色彩、字体、形状、间距、动画令牌
 * - 替代原 WealthTheme 中仅 lightColorScheme + 默认字体/形状的配置
 * - 所有组件应引用 MaterialTheme.colorScheme/typography/shapes
 *   或直接引用 Spacing/Elevation 令牌
 */

// ===== 色彩方案 =====
private val WealthColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Info,
    onSecondary = OnPrimary,
    secondaryContainer = InfoContainer,
    tertiary = Warning,
    onTertiary = OnPrimary,
    tertiaryContainer = WarningContainer,
    error = Error,
    onError = OnPrimary,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderDefault
)

/**
 * WealthLab 主题入口
 * @param content 页面内容
 */
@Composable
fun WealthTheme(content: @Composable () -> Unit) {
    // 仅浅色模式，忽略系统暗色主题
    @Suppress("UNUSED_VARIABLE")
    val isDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = WealthColorScheme,
        typography = WealthTypography,
        shapes = WealthShapes,
        content = content
    )
}
