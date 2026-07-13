package com.demo.wealth.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.demo.wealth.ui.theme.SpacingXs
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.TextTertiary
import com.demo.wealth.ui.theme.TextPrimary

/**
 * 分组标题 - 区块标题
 *
 * 改进点：
 * - 引用语义令牌（原硬编码 0xFF111820）
 * - 区分主标题与副标题
 * - 统一替代原 SectionTitle composable
 *
 * @param text 标题文字
 * @param subtitle 副标题（可选）
 */
@Composable
fun SectionHeader(
    text: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = SpacingXs)
            )
        }
    }
}

/**
 * 分组小标题 - 用于卡片内分组（如"红球热号"）
 * 大写、小字号、tertiary 色
 */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextTertiary,
        modifier = modifier.padding(vertical = SpacingSm)
    )
}
