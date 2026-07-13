package com.demo.wealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.ui.theme.HeatLevel1
import com.demo.wealth.ui.theme.HeatLevel2
import com.demo.wealth.ui.theme.HeatLevel3
import com.demo.wealth.ui.theme.HeatLevel4
import com.demo.wealth.ui.theme.HeatLevel5
import com.demo.wealth.ui.theme.HeatTextDark
import com.demo.wealth.ui.theme.HeatTextLight
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.SurfaceVariant
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 热力图 - 红球出现频次可视化
 *
 * 改进点：
 * - 每个格子带数字标签（不仅靠颜色，原仅颜色编码，色盲不友好）
 * - contentDescription 无障碍描述（原无）
 * - 引用语义令牌（原硬编码 0xFFC95B54 / 0xFFE3A39E / 0xFFF4D9D6）
 * - 统一替代原 HeatmapGrid composable
 *
 * @param draws 开奖记录列表
 */
@Composable
fun HeatmapGrid(
    draws: List<LotteryDraw>,
    modifier: Modifier = Modifier
) {
    val counts = remember(draws) {
        (1..33).associateWith { number -> draws.count { number in it.redBalls } }
    }
    val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    // 无障碍描述
    val a11yDescription = remember(counts) {
        val hot = counts.entries.sortedByDescending { it.value }.take(3).map { it.key }
        "红球频次热力图，共${draws.size}期。最热号码：${hot.joinToString("、")}"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingSm),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yDescription }
    ) {
        counts.entries.chunked(11).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingSm),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { (number, count) ->
                    val ratio = count.toFloat() / maxCount
                    val (bgColor, textColor) = when {
                        ratio >= 0.75f -> HeatLevel5 to HeatTextLight
                        ratio >= 0.55f -> HeatLevel4 to HeatTextLight
                        ratio >= 0.35f -> HeatLevel3 to HeatTextLight
                        ratio >= 0.15f -> HeatLevel2 to HeatTextDark
                        count > 0 -> HeatLevel1 to HeatTextDark
                        else -> SurfaceVariant to TextSecondary
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(bgColor, RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = number.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
