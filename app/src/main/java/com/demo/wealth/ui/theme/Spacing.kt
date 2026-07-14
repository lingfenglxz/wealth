package com.demo.wealth.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距令牌 - 8dp 栅格系统
 *
 * 设计原则：
 * - 所有间距为 4dp 的整数倍
 * - 统一替代原代码中 4/6/8/10/12/14/16 + 11/5 等非网格值
 * - 命名按尺寸层级，语义清晰
 */

// ===== 基础间距阶梯 =====
val SpacingXs = 4.dp     // 最小间距（图标与文字间隙）
val SpacingSm = 8.dp     // 小间距（组件内元素）
val SpacingMd = 12.dp    // 中间距（列表项间隙）
val SpacingLg = 16.dp    // 大间距（页面/卡片内边距）
val SpacingXl = 24.dp    // 超大间距（区块间）
val Spacing2Xl = 32.dp   // 双倍超大（大区块间）
val Spacing3Xl = 48.dp   // 最大间距（页面顶部/空状态）

// ===== 语义间距 =====
val PagePadding = 16.dp       // 页面水平内边距
val CardPadding = 16.dp       // 卡片内边距
val SectionGap = 16.dp        // 区块间距
val ItemGap = 12.dp           // 列表项间距
val ComponentGap = 8.dp       // 组件内元素间距

// ===== 触控目标最小尺寸 =====
val TouchTargetMin = 48.dp
val TouchTargetComfortable = 48.dp // 舒适触控目标
val ButtonHeightLarge = 52.dp
val ButtonHeightMedium = 40.dp
val ButtonHeightSmall = 36.dp
