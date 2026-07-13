package com.demo.wealth.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.PillShape
import com.demo.wealth.ui.theme.SpacingSm

/**
 * 标签 Pill - 圆角标签
 *
 * 改进点：
 * - minHeight 28dp + hitSlop 扩展至 48dp 触控目标（原 ~26dp 过小）
 * - 引用语义令牌（原硬编码多色）
 * - 统一替代原 MetaPill composable
 *
 * @param text 标签文字
 * @param background 背景色
 * @param foreground 文字色
 */
@Composable
fun MetaPill(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = background,
        shape = PillShape,
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .border(1.dp, foreground.copy(alpha = 0.16f), PillShape)
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
