package com.demo.wealth.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demo.wealth.data.LotteryBallDetail
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryResearchReport
import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.domain.lottery.LotteryRules
import com.demo.wealth.ui.components.Ball
import com.demo.wealth.ui.components.BallSmall
import com.demo.wealth.ui.components.ChoiceButton
import com.demo.wealth.ui.components.CollapsibleCard
import com.demo.wealth.ui.components.CountStepper
import com.demo.wealth.ui.components.ErrorState
import com.demo.wealth.ui.components.GroupLabel
import com.demo.wealth.ui.components.HeatmapGrid
import com.demo.wealth.ui.components.MetaPill
import com.demo.wealth.ui.components.MetricRow
import com.demo.wealth.ui.components.OmissionChart
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.components.SectionHeader
import com.demo.wealth.ui.frequentNumbers
import com.demo.wealth.ui.expectedSsqDrawDate
import com.demo.wealth.ui.money
import com.demo.wealth.ui.nextIssueLabel
import com.demo.wealth.ui.overdueNumbers
import com.demo.wealth.ui.parseBallDetails
import com.demo.wealth.ui.parseSettlementDetails
import com.demo.wealth.ui.parseStringArray
import com.demo.wealth.ui.percent
import com.demo.wealth.ui.signedPercent
import com.demo.wealth.ui.summarizeSettlements
import com.demo.wealth.ui.theme.ErrorContainer
import com.demo.wealth.ui.theme.InfoContainer
import com.demo.wealth.ui.theme.OnErrorContainer
import com.demo.wealth.ui.theme.OnInfoContainer
import com.demo.wealth.ui.theme.OnPrimaryContainer
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.PrimaryHover
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.SpacingMd
import com.demo.wealth.ui.theme.SpacingSm
import com.demo.wealth.ui.theme.TextSecondary
import com.demo.wealth.ui.theme.TouchTargetMin

/**
 * 福彩页 - 双色球
 *
 * 布局优化：
 * - 移除 WelfareLotteryPage 的单按钮 ChoiceButton 行
 * - 11 个卡片重组为 3 分组：「推荐与生成」「数据分析」「历史记录」
 * - 使用 GroupLabel 分组标题
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LotteryScreen(
    draws: List<LotteryDraw>,
    predictions: List<LotteryPrediction>,
    settlements: List<LotterySettlement>,
    report: LotteryResearchReport?,
    compoundRedCount: Int,
    compoundBlueCount: Int,
    onCompoundChange: (Int, Int) -> Unit,
    onGenerate: (Int, Int) -> Unit,
    isGenerating: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    totalDrawCount: Int = draws.size
) {
    val visibleDraws = remember(draws) { draws.take(10) }
    val drawDatesByIssue = remember(draws) { draws.associate { it.issue to it.date } }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // ===== 分组 1: 推荐与生成 =====
        stickyHeader { StickyGroupLabel("推荐与生成") }

        item {
            val targetIssue = draws.firstOrNull()?.issue?.let { nextIssueLabel(it) } ?: "请先更新数据"
            ActionHeader(
                "双色球",
                "历史期数 $totalDrawCount · 推荐目标 $targetIssue",
                isGenerating
            ) {
                onGenerate(compoundRedCount, compoundBlueCount)
            }
        }

        errorMessage?.let { message ->
            item { ErrorState(message, onRetry = { onGenerate(compoundRedCount, compoundBlueCount) }) }
        }

        // 推荐结果
        items(predictions, key = { it.id }) { prediction ->
            PredictionCard(prediction, drawDatesByIssue[prediction.sourceIssue])
        }

        // 号码组合
        item {
            CompoundPlanCard(compoundRedCount, compoundBlueCount, onCompoundChange)
        }

        // ===== 分组 2: 数据分析 =====
        stickyHeader { StickyGroupLabel("数据分析") }

        item {
            LotteryAnalysisCard(draws, predictions.firstOrNull(), compoundRedCount, compoundBlueCount, report, totalDrawCount)
        }

        if (draws.isNotEmpty()) {
            item { LotteryTrendCard(draws) }
        }

        report?.backtest?.let { backtest ->
            item { LotteryBacktestCard(backtest) }
        }

        // ===== 分组 3: 历史记录 =====
        stickyHeader { StickyGroupLabel("历史记录") }

        item {
            SettlementOverviewCard(settlements)
        }

        item { SectionHeader("历史开奖") }
        if (draws.isEmpty()) {
            item {
                Panel {
                    Text("暂无历史开奖", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "到“数据”页点击双色球自动更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = SpacingSm)
                    )
                }
            }
        }
        items(visibleDraws, key = { it.issue }) { draw ->
            DrawCard(draw)
        }
    }
}

@Composable
private fun StickyGroupLabel(text: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        GroupLabel(text)
    }
}

// ===== ActionHeader =====
@Composable
private fun ActionHeader(title: String, subtitle: String, loading: Boolean, onClick: () -> Unit) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = onClick, enabled = !loading) {
                if (loading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp
                    )
                } else {
                    androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.width(SpacingSm))
                Text(if (loading) "生成中" else "生成推荐")
            }
        }
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

// ===== PredictionCard =====
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PredictionCard(prediction: LotteryPrediction, sourceDate: String?) {
    var expanded by rememberSaveable(prediction.id) { mutableStateOf(false) }
    val reasonLines = remember(prediction.reasons) {
        prediction.reasons.lineSequence().filter { it.isNotBlank() }.toList()
    }
    val details = remember(prediction.ballDetails) {
        prediction.ballDetails.takeIf { it.isNotBlank() }?.let(::parseBallDetails).orEmpty()
    }

    Panel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "第 ${prediction.targetIssue.ifBlank { "下一" }} 期推荐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            MetaPill(
                if (prediction.redBalls.size > 6 || prediction.blueBalls.size > 1) "复式" else "单式",
                PrimaryContainer, PrimaryHover
            )
        }
        Spacer(Modifier.height(SpacingSm))
        Text(
            sourceDate?.let(::expectedSsqDrawDate)?.let { "开奖时间：$it" } ?: "开奖时间待更新",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(SpacingSm))
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
        Spacer(Modifier.height(SpacingSm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(SpacingSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("模型综合分 ${"%.2f".format(prediction.score)}", fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingSm),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .defaultMinSize(minHeight = TouchTargetMin)
                    .padding(SpacingSm)
            ) {
                Text(
                    if (expanded) "收起说明" else "展开说明",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
        }

        CollapsibleContent(expanded) {
            if (prediction.analysisSummary.isNotBlank()) {
                Text(prediction.analysisSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text(prediction.note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            reasonLines.forEach {
                Text("理由：$it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(SpacingSm))
                Text("逐球解释", fontWeight = FontWeight.SemiBold)
                details.forEach { BallDetailRow(it) }
            }
        }
    }
}

@Composable
private fun CollapsibleContent(expanded: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = expanded,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
        Column(Modifier.padding(top = SpacingSm)) { content() }
    }
}

@Composable
private fun BallDetailRow(detail: LotteryBallDetail) {
    Spacer(Modifier.height(SpacingSm))
    val colorName = if (detail.color == "red") "红球" else "蓝球"
    Text(
        "$colorName ${detail.number.toString().padStart(2, '0')} · 总分 ${"%.2f".format(detail.totalScore)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "全量 ${"%.2f".format(detail.fullFrequencyScore)} · 近期 ${"%.2f".format(detail.recentScore)} · 遗漏 ${"%.2f".format(detail.omissionScore)} · 形态 ${"%.2f".format(detail.centerBiasScore)}",
        style = MaterialTheme.typography.bodySmall, color = TextSecondary
    )
    Text(
        "出现 ${detail.fullCount} 次，近期加权 ${"%.2f".format(detail.recentWeighted)}，当前遗漏 ${detail.missCount} 期",
        style = MaterialTheme.typography.bodySmall, color = TextSecondary
    )
    detail.reasons.forEach {
        Text("入选原因：$it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

// ===== CompoundPlanCard =====
@Composable
private fun CompoundPlanCard(redCount: Int, blueCount: Int, onChange: (Int, Int) -> Unit) {
    val bets = LotteryRules.combinationCount(redCount, 6) * blueCount
    Panel {
        Text("号码组合", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Text(
            "C($redCount, 6) × $blueCount = $bets 注",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        Spacer(Modifier.height(SpacingMd))
        CountStepper("红球", redCount, 6, 20) { onChange(it, blueCount) }
        Spacer(Modifier.height(SpacingSm))
        CountStepper("蓝球", blueCount, 1, 16) { onChange(redCount, it) }
    }
}

// ===== LotteryAnalysisCard =====
@Composable
private fun LotteryAnalysisCard(
    draws: List<LotteryDraw>, prediction: LotteryPrediction?,
    compoundRedCount: Int, compoundBlueCount: Int, report: LotteryResearchReport?,
    totalDrawCount: Int = draws.size
) {
    val latest = remember(draws) {
        draws.maxWithOrNull(compareBy<LotteryDraw> { it.issue.toLongOrNull() ?: 0L }.thenBy { it.date })
    }
    val hotReds = remember(report, draws) {
        report?.redRankings?.sortedBy { it.rank }?.take(6)?.map { it.number } ?: frequentNumbers(draws, 1..33, true).take(6)
    }
    val overdueReds = remember(report, draws) {
        report?.redRankings?.sortedByDescending { it.omissionScore }?.take(6)?.map { it.number } ?: overdueNumbers(draws, 1..33, true).take(6)
    }
    val hotBlues = remember(report, draws) {
        report?.blueRankings?.sortedBy { it.rank }?.take(3)?.map { it.number } ?: frequentNumbers(draws, 1..16, false).take(3)
    }

    Panel {
        Text("数据分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        if (totalDrawCount < 30) {
            Text("历史样本不足，至少需要 30 期后才会按模型生成推荐。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("当前 $totalDrawCount 期，请先在“数据”页使用服务端更新。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            return@Panel
        }
        val latestDraw = latest ?: return@Panel
        val targetIssue = prediction?.targetIssue?.ifBlank { null } ?: nextIssueLabel(latestDraw.issue)
        Text(
            expectedSsqDrawDate(latestDraw.date)?.let { "目标期号：第 $targetIssue 期 · 开奖时间：$it" }
                ?: "目标期号：第 $targetIssue 期 · 开奖时间待更新",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(SpacingSm))
        MetricRow("样本期数", totalDrawCount.toString(), "组合注数", (LotteryRules.combinationCount(compoundRedCount, 6) * compoundBlueCount).toString())
        Spacer(Modifier.height(SpacingSm))
        Text("红球热号", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            hotReds.forEach { BallSmall(it.toString().padStart(2, '0'), isRed = true) }
        }
        Spacer(Modifier.height(SpacingSm))
        Text("红球遗漏", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            overdueReds.forEach { BallSmall(it.toString().padStart(2, '0'), isRed = true) }
        }
        Spacer(Modifier.height(SpacingSm))
        Text("蓝球热号", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            hotBlues.forEach { BallSmall(it.toString().padStart(2, '0'), isRed = false) }
        }
        report?.narrative?.takeIf { it.isNotEmpty() }?.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// ===== LotteryTrendCard =====
@Composable
private fun LotteryTrendCard(draws: List<LotteryDraw>) {
    val recent = remember(draws) { draws.take(30).reversed() }
    val selectedNumber = remember(draws) { mutableIntStateOf(frequentNumbers(draws, 1..33, true).firstOrNull() ?: 1) }

    CollapsibleCard(title = "走势可视化", subtitle = "最近 30 期") {
        HeatmapGrid(recent)
        Spacer(Modifier.height(SpacingMd))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            items((1..33).toList(), key = { it }) { number ->
                ChoiceButton(number.toString().padStart(2, '0'), selected = selectedNumber.intValue == number) {
                    selectedNumber.intValue = number
                }
            }
        }
        Spacer(Modifier.height(SpacingMd))
        OmissionChart(recent, selectedNumber.intValue)
        Text(
            "当前查看：红球 ${selectedNumber.intValue.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
    }
}

// ===== LotteryBacktestCard =====
@Composable
private fun LotteryBacktestCard(report: com.demo.wealth.data.LotteryBacktestReport) {
    Panel {
        Text("模型历史验证", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Text("使用历史开奖做近 ${report.issueCount} 期模拟。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(SpacingSm))
        MetricRow("验证期数", report.issueCount.toString(), "平均最佳红球", "%.2f".format(report.averageBestRedHits))
        Spacer(Modifier.height(SpacingSm))
        MetricRow("蓝球命中率", percent(report.blueHitRate), "至少3红", percent(report.atLeastThreeRedRate))
        Spacer(Modifier.height(SpacingSm))
        MetricRow("模拟中奖率", percent(report.prizeHitRate), "平均注数", "%.1f".format(report.averageBetCount))
    }
}

// ===== SettlementOverviewCard =====
@Composable
private fun SettlementOverviewCard(settlements: List<LotterySettlement>) {
    val summary = remember(settlements) { summarizeSettlements(settlements) }

    CollapsibleCard(title = "真实推荐结算", subtitle = "ROI ${percent(summary.roi)}") {
        MetricRow("已结算期数", summary.issueCount.toString(), "累计 ROI", percent(summary.roi))
        Spacer(Modifier.height(SpacingSm))
        MetricRow("累计投入", money(summary.investedAmount), "奖金", money(summary.prizeAmount))
        Spacer(Modifier.height(SpacingMd))
        if (settlements.isEmpty()) {
            Text("还没有可结算的真实推荐。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        } else {
            summary.issueGroups.forEachIndexed { index, issueSettlements ->
                SettlementIssueRow(issueSettlements)
                if (index < summary.issueGroups.lastIndex) {
                    Spacer(Modifier.height(SpacingSm))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(SpacingSm))
                }
            }
        }
    }
}

@Composable
private fun SettlementIssueRow(settlements: List<LotterySettlement>) {
    val issue = settlements.firstOrNull()?.issue.orEmpty()
    val totalInvested = settlements.sumOf { it.investedAmount }
    val totalPrize = settlements.sumOf { it.simulatedPrizeAmount }
    val totalRoi = if (totalInvested == 0.0) 0.0 else (totalPrize - totalInvested) / totalInvested
    var expanded by rememberSaveable(issue) { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TouchTargetMin)
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("第 $issue 期", fontWeight = FontWeight.SemiBold)
                Text(
                    "${settlements.size} 次推荐 · ${money(totalInvested)} 投入 · ${money(totalPrize)} 奖金 · ROI ${percent(totalRoi)}",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起第 $issue 期结算" else "展开第 $issue 期结算",
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = TextSecondary
            )
        }
        CollapsibleContent(expanded) {
            settlements.forEachIndexed { index, settlement ->
                Text(
                    if (settlements.size > 1) "第 ${index + 1} 次推荐" else "本次推荐",
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${money(settlement.investedAmount)} 投入 · ${money(settlement.simulatedPrizeAmount)} 奖金 · 最佳红球 ${settlement.bestRedHits} 个 · 蓝球${if (settlement.blueHit) "命中" else "未中"} · 共 ${settlement.betCount} 注",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
                parseSettlementDetails(settlement.detailJson).forEach { detail ->
                    Text(
                        "${detail.label} ${detail.redBalls.joinToString(" ")} + ${detail.blueBalls.joinToString(" ")}",
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${detail.betCount} 注 · 最佳红球 ${detail.bestRedHits} · 蓝球${if (detail.blueHit) "命中" else "未中"} · 奖金 ${money(detail.prizeAmount)}",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                }
            }
        }
    }
}

// ===== DrawCard =====
@Composable
private fun DrawCard(draw: LotteryDraw) {
    Panel {
        Text("开奖期号 ${draw.issue}  ${draw.date}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(SpacingSm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            draw.redBalls.forEach { BallSmall(it.toString().padStart(2, '0'), isRed = true) }
            BallSmall(draw.blueBall.toString().padStart(2, '0'), isRed = false)
        }
    }
}
