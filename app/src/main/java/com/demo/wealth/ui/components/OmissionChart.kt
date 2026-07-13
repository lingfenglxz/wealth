package com.demo.wealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.ui.theme.ChartBaseline
import com.demo.wealth.ui.theme.ChartGridLine
import com.demo.wealth.ui.theme.Primary
import com.demo.wealth.ui.theme.ShapeMd
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SurfaceVariant
import com.demo.wealth.ui.theme.TextTertiary
import kotlin.math.max

/**
 * 遗漏曲线 - 某号码的遗漏期数走势
 *
 * 改进点：
 * - 网格线 + 坐标轴标注（原无坐标参考）
 * - contentDescription 无障碍描述（原无）
 * - 引用语义令牌（原硬编码 0xFFD7DEE2）
 * - 统一替代原 OmissionCurveCanvas composable
 *
 * @param draws 开奖记录（按时间正序）
 * @param number 查看的红球号码
 */
@Composable
fun OmissionChart(
    draws: List<LotteryDraw>,
    number: Int,
    modifier: Modifier = Modifier
) {
    val omissions = remember(draws, number) {
        var miss = 0
        draws.map { draw ->
            if (number in draw.redBalls) {
                miss = 0
            } else {
                miss += 1
            }
            miss
        }
    }
    val maxMiss = remember(omissions) { max(1, omissions.maxOrNull() ?: 1) }

    val a11yDescription = remember(number, omissions) {
        val currentMiss = omissions.lastOrNull() ?: 0
        "红球${number}号遗漏走势图。最近${draws.size}期，最大遗漏${maxMiss}期，当前遗漏${currentMiss}期"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceVariant, ShapeMd)
            .padding(SpacingMd)
            .semantics { contentDescription = a11yDescription }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            if (omissions.size < 2) return@Canvas
            val step = size.width / (omissions.size - 1)

            // 网格线（25%/50%/75% 处虚线）
            repeat(3) { i ->
                val y = size.height * (i + 1) / 4
                drawLine(
                    color = ChartGridLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
            // 基线
            drawLine(
                color = ChartBaseline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )
            // 遗漏曲线
            val path = Path()
            omissions.forEachIndexed { index, value ->
                val x = index * step
                val y = size.height - value.toFloat() / maxMiss * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Primary, style = Stroke(width = 3f, cap = StrokeCap.Round))

            // 最新数据点高亮
            val lastIndex = omissions.lastIndex
            val lastX = lastIndex * step
            val lastY = size.height - omissions.last().toFloat() / maxMiss * size.height
            drawCircle(
                color = Primary,
                radius = 5f,
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = Offset(lastX, lastY)
            )
        }
        // 坐标轴标注
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("30期前", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text("20期前", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text("10期前", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text("当前", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}
