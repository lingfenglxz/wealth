package com.demo.wealth.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 形状令牌 - 5 级圆角系统
 *
 * 设计原则：
 * - 统一替代原代码中 8dp/6dp/999dp 混用
 * - 卡片圆角统一 16dp（比原 8dp 更现代柔和）
 */

// ===== 圆角阶梯 =====
val ShapeSm = RoundedCornerShape(8.dp)    // 小圆角（Pill 内部、小元素）
val ShapeMd = RoundedCornerShape(12.dp)   // 中圆角（按钮、输入框、指标卡）
val ShapeLg = RoundedCornerShape(12.dp)   // 卡片统一 12dp
val ShapeXl = RoundedCornerShape(20.dp)   // 超大圆角（导航激活态背景）
val ShapeFull = RoundedCornerShape(999.dp) // 全圆角（Pill、球体）

// ===== 语义形状 =====
val CardShape: Shape = ShapeLg          // 卡片形状
val ButtonShape: Shape = ShapeMd        // 按钮形状
val InputShape: Shape = ShapeMd         // 输入框形状
val PillShape: Shape = ShapeFull        // 标签形状

// ===== Material3 Shapes 配置 =====
val WealthShapes = Shapes(
    extraSmall = ShapeSm,
    small = ShapeSm,
    medium = ShapeMd,
    large = ShapeLg,
    extraLarge = ShapeXl
)
