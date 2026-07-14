package com.demo.wealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.data.FootballMatchEntity
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.domain.sports.FootballDisplayNames
import com.demo.wealth.ui.components.Ball
import com.demo.wealth.ui.components.MetaPill
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.components.SectionHeader
import com.demo.wealth.ui.money
import com.demo.wealth.ui.expectedSsqDrawDate
import com.demo.wealth.ui.summarizeSettlements
import com.demo.wealth.ui.theme.InfoContainer
import com.demo.wealth.ui.theme.OnInfoContainer
import com.demo.wealth.ui.theme.OnPrimary
import com.demo.wealth.ui.theme.Primary
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.PrimaryHover
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.SpacingXl
import com.demo.wealth.ui.theme.TextSecondary
import androidx.compose.ui.draw.clip

/**
 * 首页 - 仪表盘
 *
 * 布局优化：
 * - 移除 StatusCard（原占用黄金位置），改为 TopAppBar 中的 StatusIndicator
 * - ROI 提升为 Hero 卡片（渐变背景 + 大数字 + 趋势），作为视觉焦点
 * - 新顺序：ROI Hero -> 推荐 -> 足球
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    predictions: List<LotteryPrediction>,
    settlements: List<LotterySettlement>,
    footballMatches: List<FootballMatchEntity>,
    draws: List<LotteryDraw>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // ROI Hero 卡片（替代原 HomeSettlementSummaryCard）
        item { RoiHeroCard(settlements) }

        // 推荐卡
        item {
            SectionHeader("双色球")
            val prediction = predictions.firstOrNull()
            val sourceDate = draws.firstOrNull { it.issue == prediction?.sourceIssue }?.date
            HomePredictionCard(prediction, sourceDate)
        }

        // 足球赛事摘要
        item {
            SectionHeader("体彩世界杯")
            FootballSummaryCard(footballMatches)
        }
    }
}

/**
 * ROI Hero 卡片 - 渐变背景 + 大数字 + 趋势 + 统计行
 * 替代原平淡的 HomeSettlementSummaryCard
 */
@Composable
private fun RoiHeroCard(settlements: List<LotterySettlement>) {
    val summary = remember(settlements) { summarizeSettlements(settlements) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Primary)
            .padding(SpacingXl)
    ) {
        Column {
            Text(
                "真实推荐累计 ROI",
                style = MaterialTheme.typography.bodyMedium,
                color = OnPrimary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(SpacingSm))
            Text(
                signedRoi(summary.roi),
                style = MaterialTheme.typography.displayLarge,
                color = OnPrimary
            )
            if (summary.roi >= 0) {
                Spacer(Modifier.height(SpacingSm))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OnPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = SpacingMd, vertical = 4.dp)
                ) {
                    Text(
                        "▲ 较上期收益为正",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnPrimary
                    )
                }
            }
            Spacer(Modifier.height(SpacingLg))
            // 统计行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoiStat("已结算", "${summary.issueCount} 期")
                RoiStat("投入", money(summary.investedAmount))
                RoiStat("奖金", money(summary.prizeAmount))
            }
        }
    }
}

@Composable
private fun RoiStat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = OnPrimary.copy(alpha = 0.8f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnPrimary
        )
    }
}

private fun signedRoi(value: Double): String =
    "${if (value >= 0) "+" else ""}${"%.2f".format(value * 100)}%"

/**
 * 首页推荐卡 - 简化版（不含展开说明，点击进入福彩页查看详情）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePredictionCard(prediction: LotteryPrediction?, sourceDate: String?) {
    Panel {
        if (prediction == null) {
            Text("暂无推荐", style = MaterialTheme.typography.bodyLarge)
            Text(
                "到“福彩”页点击“生成推荐”获取本期号码。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = SpacingSm)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "第 ${prediction.targetIssue.ifBlank { "下一" }} 期推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                MetaPill(
                    if (prediction.redBalls.size > 6 || prediction.blueBalls.size > 1) "复式" else "单式",
                    PrimaryContainer,
                    PrimaryHover
                )
            }
            Spacer(Modifier.height(SpacingSm))
            Text(
                sourceDate?.let(::expectedSsqDrawDate)?.let { "预计开奖：$it" } ?: "预计开奖日期待更新",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(SpacingMd))
            Text("红球", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(SpacingSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SpacingSm),
                verticalArrangement = Arrangement.spacedBy(SpacingSm),
                modifier = Modifier.fillMaxWidth()
            ) { prediction.redBalls.forEach { Ball(it.toString().padStart(2, '0'), isRed = true) } }
            Spacer(Modifier.height(SpacingMd))
            Text("蓝球", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(SpacingSm))
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
                prediction.blueBalls.forEach { Ball(it.toString().padStart(2, '0'), isRed = false) }
            }
            Spacer(Modifier.height(SpacingMd))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型综合分 ${"%.2f".format(prediction.score)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                MetaPill("模型 ${prediction.modelVersion}", InfoContainer, OnInfoContainer)
            }
        }
    }
}

/**
 * 足球赛事摘要卡 - 更紧凑
 */
@Composable
private fun FootballSummaryCard(matches: List<FootballMatchEntity>) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "足球彩票",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (matches.isNotEmpty()) {
                MetaPill("${matches.size} 场", InfoContainer, OnInfoContainer)
            }
        }
        Spacer(Modifier.height(SpacingSm))
        if (matches.isEmpty()) {
            Text(
                "暂无世界杯赛事，请到“数据”页更新。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        } else {
            val next = matches.first()
            Text(
                "${FootballDisplayNames.team(next.homeTeam)} vs ${FootballDisplayNames.team(next.awayTeam)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${FootballDisplayNames.phase(next.phase)} · ${next.kickoffTime}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
