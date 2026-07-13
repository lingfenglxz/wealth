package com.demo.wealth.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.demo.wealth.data.LotteryNumberRanking
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryResearchReport
import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.domain.lottery.LotteryRules
import com.demo.wealth.ui.components.Ball
import com.demo.wealth.ui.components.BallSmall
import com.demo.wealth.ui.components.ChoiceButton
import com.demo.wealth.ui.components.CollapsibleCard
import com.demo.wealth.ui.components.CountStepper
import com.demo.wealth.ui.components.GroupLabel
import com.demo.wealth.ui.components.HeatmapGrid
import com.demo.wealth.ui.components.MetaPill
import com.demo.wealth.ui.components.MetricRow
import com.demo.wealth.ui.components.OmissionChart
import com.demo.wealth.ui.components.Panel
import com.demo.wealth.ui.components.SectionHeader
import com.demo.wealth.ui.frequentNumbers
import com.demo.wealth.ui.groupSettlementsByIssue
import com.demo.wealth.ui.money
import com.demo.wealth.ui.nextIssueLabel
import com.demo.wealth.ui.overdueNumbers
import com.demo.wealth.ui.parseBallDetails
import com.demo.wealth.ui.parseSettlementDetails
import com.demo.wealth.ui.parseStringArray
import com.demo.wealth.ui.percent
import com.demo.wealth.ui.signedPercent
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
import com.demo.wealth.ui.theme.Success
import com.demo.wealth.ui.theme.TextSecondary

/**
 * 福彩页 - 双色球实验模型
 *
 * 布局优化：
 * - 移除 WelfareLotteryPage 的单按钮 ChoiceButton 行
 * - 11 个卡片重组为 3 分组：「推荐与生成」「数据分析」「历史记录」
 * - 使用 GroupLabel 分组标题
 */
@Composable
fun LotteryScreen(
    draws: List<LotteryDraw>,
    predictions: List<LotteryPrediction>,
    settlements: List<LotterySettlement>,
    report: LotteryResearchReport?,
    compoundRedCount: Int,
    compoundBlueCount: Int,
    recentWindow: Int,
    budgetBets: Int,
    modelVersion: String,
    onCompoundChange: (Int, Int) -> Unit,
    onRecentWindowChange: (Int) -> Unit,
    onBudgetChange: (Int) -> Unit,
    onModelVersionChange: (String) -> Unit,
    onGenerate: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(SpacingLg),
        verticalArrangement = Arrangement.spacedBy(SpacingMd)
    ) {
        // ===== 分组 1: 推荐与生成 =====
        item { GroupLabel("推荐与生成") }

        item {
            val targetIssue = draws.firstOrNull()?.issue?.let { nextIssueLabel(it) } ?: "请先更新数据"
            ActionHeader(
                "双色球实验模型",
                "历史期数 ${draws.size} · 推荐目标 $targetIssue"
            ) {
                onGenerate(compoundRedCount, compoundBlueCount)
            }
        }

        // 推荐结果
        items(predictions, key = { it.id }) { prediction ->
            PredictionCard(prediction)
        }

        // 号码组合
        item {
            CompoundPlanCard(compoundRedCount, compoundBlueCount, report, onCompoundChange)
        }

        // 模型参数
        item {
            ModelParameterCard(modelVersion, recentWindow, budgetBets, onModelVersionChange, onRecentWindowChange, onBudgetChange)
        }

        // ===== 分组 2: 数据分析 =====
        item { GroupLabel("数据分析") }

        item {
            LotteryAnalysisCard(draws, predictions.firstOrNull(), compoundRedCount, compoundBlueCount, report)
        }

        if (draws.isNotEmpty()) {
            item { LotteryTrendCard(draws) }
        }

        report?.backtest?.let { backtest ->
            item { LotteryBacktestCard(backtest) }
        }

        if (!report?.modelComparison.isNullOrEmpty()) {
            item { ModelComparisonCard(report!!.modelComparison) }
        }

        if (report != null && (report.redRankings.isNotEmpty() || report.blueRankings.isNotEmpty())) {
            item { NumberRankingCard(report, predictions) }
        }

        // ===== 分组 3: 历史记录 =====
        item { GroupLabel("历史记录") }

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
        items(draws.take(20), key = { it.issue }) { draw ->
            DrawCard(draw)
        }
    }
}

// ===== ActionHeader =====
@Composable
private fun ActionHeader(title: String, subtitle: String, onClick: () -> Unit) {
    Panel(raised = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                MetaPill("研究台", PrimaryContainer, PrimaryHover)
                Spacer(Modifier.height(SpacingSm))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Button(onClick = onClick) {
                androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(SpacingSm))
                Text("生成推荐")
            }
        }
    }
}

// ===== PredictionCard =====
@Composable
private fun PredictionCard(prediction: LotteryPrediction) {
    var expanded by rememberSaveable(prediction.id) { mutableStateOf(false) }
    val reasonLines = remember(prediction.reasons) {
        prediction.reasons?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()
    }
    val details = remember(prediction.ballDetails) {
        prediction.ballDetails?.takeIf { it.isNotBlank() }?.let(::parseBallDetails).orEmpty()
    }

    Panel {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            MetaPill(
                if (prediction.redBalls.size > 6 || prediction.blueBalls.size > 1) "复式推荐" else "单式推荐",
                PrimaryContainer, PrimaryHover
            )
            MetaPill("模型 ${prediction.modelVersion}", InfoContainer, OnInfoContainer)
        }
        Spacer(Modifier.height(SpacingSm))
        Text(
            "目标期号：第 ${prediction.targetIssue.ifBlank { "下一" }} 期 · 最新开奖：第 ${prediction.sourceIssue.ifBlank { "-" }} 期",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(SpacingSm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            prediction.redBalls.forEach { Ball(it.toString().padStart(2, '0'), isRed = true) }
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
            Text("评分 ${"%.2f".format(prediction.score)}", fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingSm),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
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
private fun CompoundPlanCard(redCount: Int, blueCount: Int, report: LotteryResearchReport?, onChange: (Int, Int) -> Unit) {
    val bets = LotteryRules.combinationCount(redCount, 6) * blueCount
    Panel {
        Text("号码组合", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Text(
            "当前实际生成：一组 ${redCount}+${blueCount}，约 $bets 注。",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
        Spacer(Modifier.height(SpacingMd))
        CountStepper("红球", redCount, 6, 20) { onChange(it, blueCount) }
        Spacer(Modifier.height(SpacingSm))
        CountStepper("蓝球", blueCount, 1, 16) { onChange(redCount, it) }
        report?.budgetPlan?.let { plan ->
            Spacer(Modifier.height(SpacingMd))
            Text("基于预算上限的建议：${plan.redCount}+${plan.blueCount}，${plan.betCount} 注", fontWeight = FontWeight.SemiBold)
            Text(plan.reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(SpacingSm))
            Button(onClick = { onChange(plan.redCount, plan.blueCount) }) {
                Text("设为当前组合")
            }
        }
    }
}

// ===== ModelParameterCard =====
@Composable
private fun ModelParameterCard(
    modelVersion: String, recentWindow: Int, budgetBets: Int,
    onModelVersionChange: (String) -> Unit,
    onRecentWindowChange: (Int) -> Unit,
    onBudgetChange: (Int) -> Unit
) {
    Panel {
        Text("模型参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Text("模型版本", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("auto" to "自动", "recent_focus_v3" to "近期", "hit_rate_v4" to "命中", "balanced_v2" to "均衡", "baseline_v1" to "基线").forEach { (value, label) ->
                ChoiceButton(label, selected = modelVersion == value) { onModelVersionChange(value) }
            }
        }
        Spacer(Modifier.height(SpacingMd))
        CountStepper("最近窗口", recentWindow, 30, 500, step = 10, onChange = onRecentWindowChange)
        Spacer(Modifier.height(SpacingSm))
        CountStepper("预算上限（注）", budgetBets, 1, 5000, onChange = onBudgetChange)
    }
}

// ===== LotteryAnalysisCard =====
@Composable
private fun LotteryAnalysisCard(
    draws: List<LotteryDraw>, prediction: LotteryPrediction?,
    compoundRedCount: Int, compoundBlueCount: Int, report: LotteryResearchReport?
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
        if (draws.size < 30) {
            Text("历史样本不足，至少需要 30 期后才会按模型生成推荐。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("当前 ${draws.size} 期，请先在“数据”页使用服务端更新。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            return@Panel
        }
        val latestDraw = latest ?: return@Panel
        val targetIssue = prediction?.targetIssue?.ifBlank { null } ?: nextIssueLabel(latestDraw.issue)
        Text("目标期号：第 $targetIssue 期；最新开奖：第 ${latestDraw.issue} 期 ${latestDraw.date}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(SpacingSm))
        MetricRow("样本期数", draws.size.toString(), "组合注数", (LotteryRules.combinationCount(compoundRedCount, 6) * compoundBlueCount).toString())
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
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            (1..33).forEach { number ->
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

// ===== ModelComparisonCard =====
@Composable
private fun ModelComparisonCard(comparisons: List<com.demo.wealth.data.LotteryModelComparison>) {
    Panel {
        Text("模型对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        comparisons.forEach { item ->
            Text(
                "${item.version} · 模拟中奖 ${percent(item.prizeHitRate)} · 平均最佳红球 ${"%.2f".format(item.averageBestRedHits)} · 蓝球 ${percent(item.blueHitRate)} · 至少3红 ${percent(item.atLeastThreeRedRate)}",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }
    }
}

// ===== NumberRankingCard =====
@Composable
private fun NumberRankingCard(report: LotteryResearchReport, predictions: List<LotteryPrediction>) {
    var color by rememberSaveable(report) { mutableStateOf("red") }
    var sortMode by rememberSaveable(report) { mutableStateOf("total") }
    val source = if (color == "red") report.redRankings else report.blueRankings
    val sorted = remember(source, sortMode) {
        when (sortMode) {
            "recent" -> source.sortedWith(compareByDescending<LotteryNumberRanking> { it.recentScore }.thenBy { it.number })
            "omission" -> source.sortedWith(compareByDescending<LotteryNumberRanking> { it.omissionScore }.thenBy { it.number })
            else -> source.sortedBy { it.rank }
        }
    }
    var selectedNumber by remember(color, report) { mutableIntStateOf(sorted.firstOrNull()?.number ?: 1) }
    val selected = sorted.firstOrNull { it.number == selectedNumber } ?: sorted.firstOrNull()
    val currentNumbers = remember(color, predictions) {
        if (color == "red") predictions.flatMap { it.redBalls }.toSet() else predictions.flatMap { it.blueBalls }.toSet()
    }

    Panel {
        Text("号码榜单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm)) {
            ChoiceButton("红球榜", selected = color == "red") { color = "red" }
            ChoiceButton("蓝球榜", selected = color == "blue") { color = "blue" }
        }
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("total" to "综合", "recent" to "近期", "omission" to "遗漏").forEach { (value, label) ->
                ChoiceButton(label, selected = sortMode == value) { sortMode = value }
            }
        }
        Spacer(Modifier.height(SpacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingSm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            sorted.forEach { item ->
                ChoiceButton(
                    text = "${item.number.toString().padStart(2, '0')} #${item.rank}",
                    selected = item.number == selected?.number,
                    onClick = { selectedNumber = item.number }
                )
            }
        }
        selected?.let { item ->
            Spacer(Modifier.height(SpacingMd))
            Text("${if (color == "red") "红球" else "蓝球"} ${item.number.toString().padStart(2, '0')} · 综合排名 #${item.rank}", fontWeight = FontWeight.SemiBold)
            Text(
                if (item.number in currentNumbers) "已进入本期推荐" else "本期未入选",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.number in currentNumbers) Success else TextSecondary
            )
            Spacer(Modifier.height(SpacingSm))
            Text("综合 ${"%.2f".format(item.totalScore)} · 全量 ${"%.2f".format(item.fullFrequencyScore)} · 近期 ${"%.2f".format(item.recentScore)} · 遗漏 ${"%.2f".format(item.omissionScore)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("近30期 ${item.recent30Count} 次 · 近60期 ${item.recent60Count} 次 · 当前遗漏 ${item.missCount} 期", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// ===== SettlementOverviewCard =====
@Composable
private fun SettlementOverviewCard(settlements: List<LotterySettlement>) {
    val settlementIssues = remember(settlements) { groupSettlementsByIssue(settlements) }
    val uniqueSettlements = remember(settlementIssues) { settlementIssues.flatten() }
    val totals = remember(uniqueSettlements) {
        val totalInvested = uniqueSettlements.sumOf { it.investedAmount }
        val totalPrize = uniqueSettlements.sumOf { it.simulatedPrizeAmount }
        Triple(totalInvested, totalPrize, if (totalInvested == 0.0) 0.0 else (totalPrize - totalInvested) / totalInvested)
    }

    CollapsibleCard(title = "真实推荐结算", subtitle = "ROI ${percent(totals.third)}") {
        MetricRow("已结算期数", settlementIssues.size.toString(), "累计 ROI", percent(totals.third))
        Spacer(Modifier.height(SpacingSm))
        MetricRow("累计投入", money(totals.first), "奖金", money(totals.second))
        Spacer(Modifier.height(SpacingMd))
        if (settlements.isEmpty()) {
            Text("还没有可结算的真实推荐。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        } else {
            settlementIssues.forEachIndexed { index, issueSettlements ->
                SettlementIssueRow(issueSettlements)
                if (index < settlementIssues.lastIndex) {
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
        Text("第 $issue 期", fontWeight = FontWeight.SemiBold)
        Text(
            "${settlements.size} 次推荐 · ${money(totalInvested)} 投入 · ${money(totalPrize)} 奖金 · ROI ${percent(totalRoi)}",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary
        )
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            draw.redBalls.forEach { BallSmall(it.toString().padStart(2, '0'), isRed = true) }
            BallSmall(draw.blueBall.toString().padStart(2, '0'), isRed = false)
        }
    }
}
