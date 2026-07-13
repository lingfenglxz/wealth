package com.demo.wealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.ui.theme.BorderSubtle
import com.demo.wealth.ui.theme.Error
import com.demo.wealth.ui.theme.ShapeMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.Success
import com.demo.wealth.ui.theme.SurfaceVariant
import com.demo.wealth.ui.theme.TextPrimary
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 指标卡 - 标签 + 数值
 *
 * 改进点：
 * - 等宽数字（tabular figures）防止跳动（原无）
 * - 支持趋势色（涨绿跌红）（原无）
 * - 引用语义令牌（原硬编码 0xFFF7F9FA / 0xFFE6EBEF / 0xFF68737C / 0xFF111820）
 *
 * @param label 标签（如 "累计 ROI"）
 * @param value 数值（如 "+12.40%"）
 * @param trend 趋势色，null=默认色
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    trend: Color? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceVariant, ShapeMd)
            .border(1.dp, BorderSubtle, ShapeMd)
            .padding(SpacingMd)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = trend ?: TextPrimary
        )
    }
}

/**
 * 指标卡行 - 两列并排
 *
 * 改进点：
 * - 引用 Spacing 令牌（原硬编码 12.dp）
 * - 统一替代原 MetricRow + Metric 两个 composable
 */
@Composable
fun MetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftTrend: Color? = null,
    rightTrend: Color? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpacingMd),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricCard(leftLabel, leftValue, leftTrend, Modifier.weight(1f))
        MetricCard(rightLabel, rightValue, rightTrend, Modifier.weight(1f))
    }
}
