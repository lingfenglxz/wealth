package com.demo.wealth.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩令牌 - 现代金融科技风格
 *
 * 设计原则：
 * - 语义化命名（按用途而非色相）
 * - 3 级文字层级（primary/secondary/tertiary）
 * - 2 级边框（subtle/default）
 * - 统一替代原代码中 4 种灰、4 种黑、5 种边框灰
 */

// ===== 主色 =====
val Primary = Color(0xFF10B981)
val PrimaryHover = Color(0xFF059669)
val PrimaryContainer = Color(0xFFECFDF5)
val OnPrimary = Color(0xFFFFFFFF)
val OnPrimaryContainer = Color(0xFF065F46)

// ===== 文字层级（3 级统一） =====
val TextPrimary = Color(0xFF0F172A)   // slate-900，统一所有标题
val TextSecondary = Color(0xFF475569) // slate-600，统一所有次要文字
val TextTertiary = Color(0xFF94A3B8)  // slate-400，仅辅助提示

// ===== 背景/表面 =====
val Background = Color(0xFFF8FAFC)    // slate-50
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF1F5F9) // slate-100

// ===== 边框（2 级统一） =====
val BorderSubtle = Color(0xFFE2E8F0)  // slate-200
val BorderDefault = Color(0xFFCBD5E1) // slate-300

// ===== 语义功能色 =====
val Success = Color(0xFF10B981)
val Error = Color(0xFFEF4444)
val Warning = Color(0xFFF59E0B)
val Info = Color(0xFF3B82F6)

// 语义容器色（用于浅色背景标签）
val SuccessContainer = Color(0xFFECFDF5)
val ErrorContainer = Color(0xFFFEE2E2)
val WarningContainer = Color(0xFFFEF3C7)
val InfoContainer = Color(0xFFDBEAFE)

// 语义容器上的文字色
val OnSuccessContainer = Color(0xFF065F46)
val OnErrorContainer = Color(0xFF991B1B)
val OnWarningContainer = Color(0xFFB45309)
val OnInfoContainer = Color(0xFF1D4ED8)

// ===== 彩球专用（令牌化） =====
val LotteryRed = Color(0xFFDC2626)
val LotteryRedLight = Color(0xFFF87171) // 用于径向渐变高光
val LotteryBlue = Color(0xFF2563EB)
val LotteryBlueLight = Color(0xFF60A5FA) // 用于径向渐变高光

// ===== 热力图梯度（5 级） =====
val HeatLevel1 = Color(0xFFFEE2E2)
val HeatLevel2 = Color(0xFFFCA5A5)
val HeatLevel3 = Color(0xFFF87171)
val HeatLevel4 = Color(0xFFEF4444)
val HeatLevel5 = Color(0xFFB91C1C)

// 热力图文字色（保证对比度）
val HeatTextDark = Color(0xFF991B1B)   // 浅色背景用
val HeatTextLight = Color(0xFFFFFFFF)  // 深色背景用

// ===== 图表辅助色 =====
val ChartGridLine = Color(0xFFE2E8F0)
val ChartBaseline = Color(0xFFCBD5E1)
