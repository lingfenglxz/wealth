package com.demo.wealth.ui.theme

/**
 * 阴影/海拔令牌 - 3 级层级系统
 *
 * 设计原则：
 * - 统一替代原代码中全 0 阴影的扁平卡片
 * - 建立视觉层级：平面 -> 卡片 -> 悬浮 -> 对话框
 *
 * 注意：Compose 中通过 tonalElevation/shadowElevation 或 Modifier.shadow 实现
 */

// ===== 海拔层级（dp） =====
const val Elevation0 = 0f   // 平面（背景层，无阴影）
const val Elevation1 = 1f   // 卡片基础阴影
const val Elevation2 = 4f   // 悬浮卡片（如 ActionHeader）
const val Elevation3 = 8f   // 对话框/底部 Sheet

// ===== 阴影配置（用于 Modifier.shadow） =====
// elevation1: 0 1px 2px rgba(15,23,42,0.04) + 0 1px 3px rgba(15,23,42,0.06)
// elevation2: 0 4px 12px rgba(15,23,42,0.08)
// elevation3: 0 8px 24px rgba(15,23,42,0.12)
// 在 Compose 中用 shadowElevation = N.dp 实现，系统会自动渲染阴影
