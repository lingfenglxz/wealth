package com.demo.wealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demo.wealth.data.BacktestSnapshot
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotteryBallDetail
import com.demo.wealth.data.LotteryNumberRanking
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryResearchReport
import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.data.LotterySettlementDetail
import com.demo.wealth.data.StockCandle
import com.demo.wealth.data.StockSymbol
import com.demo.wealth.domain.lottery.LotteryRules
import com.demo.wealth.domain.quant.BreakoutStrategy
import com.demo.wealth.domain.quant.Indicators
import com.demo.wealth.domain.quant.MacdTrendStrategy
import com.demo.wealth.domain.quant.MovingAverageCrossStrategy
import com.demo.wealth.domain.quant.RsiReversionStrategy
import com.demo.wealth.domain.quant.SignalAction
import com.demo.wealth.ui.WealthViewModel
import kotlin.math.max
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WealthTheme {
                WealthApp()
            }
        }
    }
}

@Composable
fun WealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF146A58),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEFE8),
            onPrimaryContainer = Color(0xFF0F3028),
            secondary = Color(0xFFB04A46),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF6DEDC),
            tertiary = Color(0xFF2F6FB2),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDCE8F7),
            background = Color(0xFFF3F5F6),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9EEF0),
            outline = Color(0xFFD7DEE2)
        ),
        content = content
    )
}

data class TabSpec(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthApp(viewModel: WealthViewModel = viewModel()) {
    val tabs = listOf(
        TabSpec("首页", Icons.Default.Home),
        TabSpec("双色球", Icons.Default.Casino),
        TabSpec("量化", Icons.Default.BarChart),
        TabSpec("数据", Icons.Default.Storage)
    )
    var selected by remember { mutableIntStateOf(0) }
    val draws by viewModel.lotteryDraws.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val settlements by viewModel.lotterySettlements.collectAsState()
    val symbols by viewModel.symbols.collectAsState()
    val backtests by viewModel.backtests.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val selectedCandles by viewModel.selectedCandles.collectAsState()
    val lotteryServerUrl by viewModel.lotteryServerUrl.collectAsState()
    val compoundRedCount by viewModel.compoundRedCount.collectAsState()
    val compoundBlueCount by viewModel.compoundBlueCount.collectAsState()
    val recentWindow by viewModel.recentWindow.collectAsState()
    val budgetBets by viewModel.budgetBets.collectAsState()
    val modelVersion by viewModel.modelVersion.collectAsState()
    val researchReport by viewModel.researchReport.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("搞钱", fontWeight = FontWeight.Bold)
                        Text("彩票实验与量化研究", style = MaterialTheme.typography.labelMedium, color = Color(0xFF61706C))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color(0xFF59646A),
                            unselectedTextColor = Color(0xFF59646A)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selected) {
                0 -> HomePage(predictions, settlements, backtests, symbols, message)
                1 -> LotteryPage(
                    draws = draws,
                    predictions = predictions,
                    settlements = settlements,
                    report = researchReport,
                    compoundRedCount = compoundRedCount,
                    compoundBlueCount = compoundBlueCount,
                    recentWindow = recentWindow,
                    budgetBets = budgetBets,
                    modelVersion = modelVersion,
                    onCompoundChange = viewModel::setCompoundPlan,
                    onRecentWindowChange = viewModel::setRecentWindow,
                    onBudgetChange = viewModel::setBudgetBets,
                    onModelVersionChange = viewModel::setModelVersion,
                    onGenerate = viewModel::generateLottery
                )
                2 -> QuantPage(symbols, selectedCandles, backtests, selectedSymbol, viewModel::selectSymbol, viewModel::runBacktest)
                3 -> DataPage(
                    viewModel::importLottery,
                    viewModel::updateLotteryFromOfficial,
                    lotteryServerUrl,
                    viewModel::setLotteryServerUrl,
                    viewModel::updateLotteryFromServer,
                    viewModel::importStock,
                    viewModel::updateStockFromServer,
                    viewModel::updateStockFromHttp,
                    viewModel::exportBackup,
                    viewModel::restoreBackup,
                    message
                )
            }
        }
    }
}

@Composable
fun HomePage(
    predictions: List<LotteryPrediction>,
    settlements: List<LotterySettlement>,
    backtests: List<BacktestSnapshot>,
    symbols: List<StockSymbol>,
    message: String
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusCard(message) }
        item { HomeSettlementSummaryCard(settlements) }
        item {
            SectionTitle(predictions.firstOrNull()?.targetIssue?.let { "第 $it 期双色球" } ?: "下期双色球")
            PredictionCard(predictions.firstOrNull())
        }
        item {
            SectionTitle("最近回测")
            BacktestCard(backtests.firstOrNull())
        }
        item {
            SectionTitle("观察池")
            MetricRow("股票数", symbols.size.toString(), "回测数", backtests.size.toString())
        }
    }
}

@Composable
fun HomeSettlementSummaryCard(settlements: List<LotterySettlement>) {
    val totals = remember(settlements) {
        val totalInvested = settlements.sumOf { it.investedAmount }
        val totalPrize = settlements.sumOf { it.simulatedPrizeAmount }
        Triple(totalInvested, totalPrize, if (totalInvested == 0.0) 0.0 else (totalPrize - totalInvested) / totalInvested)
    }
    Panel {
        Text("真实推荐结算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("仅按每期实际复式方案统计。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        MetricRow("已结算期数", settlements.size.toString(), "累计 ROI", percent(totals.third))
        Spacer(Modifier.height(8.dp))
        MetricRow("累计投入", money(totals.first), "奖金", money(totals.second))
    }
}

@Composable
fun LotteryPage(
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
    onGenerate: (Int, Int) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val targetIssue = draws.firstOrNull()?.issue?.let { nextIssueLabel(it) } ?: "请先更新数据"
            ActionHeader("双色球实验模型", "历史期数 ${draws.size} · 推荐目标 $targetIssue", Icons.Default.PlayArrow, "生成推荐") {
                onGenerate(compoundRedCount, compoundBlueCount)
            }
        }
        item {
            LotteryAnalysisCard(draws, predictions.firstOrNull(), compoundRedCount, compoundBlueCount, report)
        }
        item {
            SettlementOverviewCard(settlements)
        }
        if (draws.isNotEmpty()) {
            item {
                LotteryTrendCard(draws)
            }
        }
        item {
            ModelParameterCard(modelVersion, recentWindow, budgetBets, onModelVersionChange, onRecentWindowChange, onBudgetChange)
        }
        item {
            CompoundPlanCard(compoundRedCount, compoundBlueCount, report, onCompoundChange)
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
        items(predictions, key = { it.id }) { prediction ->
            PredictionCard(prediction)
        }
        item {
            SectionTitle("历史开奖")
        }
        if (draws.isEmpty()) {
            item {
                Panel {
                    Text("暂无历史开奖", style = MaterialTheme.typography.bodyLarge)
                    Text("到“数据”页点击双色球自动更新，App会从中国福彩网拉取最近开奖记录。", color = Color(0xFF61706C))
                }
            }
        }
        items(draws.take(20), key = { it.issue }) { draw ->
            DrawCard(draw)
        }
    }
}

@Composable
fun QuantPage(
    symbols: List<StockSymbol>,
    candles: List<StockCandle>,
    backtests: List<BacktestSnapshot>,
    selectedSymbol: String,
    onSelect: (String) -> Unit,
    onRun: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ActionHeader("A股量化研究", selectedSymbol.ifBlank { "未选择股票" }, Icons.Default.BarChart, "运行回测") {
                onRun(selectedSymbol)
            }
        }
        if (symbols.isEmpty()) {
            item {
                Panel {
                    Text("暂无股票数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("到“数据”页导入日线CSV，或填写CSV地址后用HTTP更新。", color = Color(0xFF61706C))
                }
            }
        }
        if (candles.isNotEmpty()) {
            item { StockSnapshotCard(candles) }
            item { StrategySignalCard(candles) }
        }
        items(symbols, key = { it.symbol }) { symbol ->
            CardRow(
                title = "${symbol.symbol}  ${symbol.name}",
                subtitle = "最新数据 ${symbol.lastUpdated ?: "-"}",
                selected = symbol.symbol == selectedSymbol,
                onClick = { onSelect(symbol.symbol) }
            )
        }
        items(
            backtests.filter { selectedSymbol.isBlank() || it.symbol == selectedSymbol },
            key = { "${it.symbol}-${it.strategyName}-${it.createdAt}" }
        ) { snapshot ->
            BacktestCard(snapshot)
        }
    }
}

@Composable
fun StockSnapshotCard(candles: List<StockCandle>) {
    val latest = candles.last()
    val previous = candles.getOrNull(candles.lastIndex - 1)
    val dayChange = previous?.let { (latest.close - it.close) / it.close } ?: 0.0
    val ma5 = Indicators.ma(candles, 5).lastOrNull()?.value
    val ma20 = Indicators.ma(candles, 20).lastOrNull()?.value
    val rsi14 = Indicators.rsi(candles).lastOrNull()?.value
    val macd = Indicators.macd(candles).lastOrNull()

    Panel {
        Text("市场快照", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%.2f".format(latest.close), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            MetaPill(
                "${if (dayChange >= 0) "+" else ""}${percent(dayChange)}",
                if (dayChange >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                if (dayChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }
        Text("最新交易日 ${latest.date}", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        MetricRow("MA5", ma5?.let { "%.2f".format(it) } ?: "-", "MA20", ma20?.let { "%.2f".format(it) } ?: "-")
        Spacer(Modifier.height(8.dp))
        MetricRow("RSI14", rsi14?.let { "%.2f".format(it) } ?: "-", "MACD柱", macd?.histogram?.let { "%.3f".format(it) } ?: "-")
    }
}

@Composable
fun StrategySignalCard(candles: List<StockCandle>) {
    val strategies = listOf(
        MovingAverageCrossStrategy(),
        RsiReversionStrategy(),
        MacdTrendStrategy(),
        BreakoutStrategy()
    )
    Panel {
        Text("当前策略信号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        strategies.forEachIndexed { index, strategy ->
            val signal = strategy.signals(candles).lastOrNull()
            val action = signal?.action ?: SignalAction.HOLD
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strategy.name, fontWeight = FontWeight.SemiBold)
                    Text(signal?.reason ?: "暂无信号", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                }
                MetaPill(
                    when (action) {
                        SignalAction.BUY -> "买入"
                        SignalAction.SELL -> "卖出"
                        SignalAction.HOLD -> "观望"
                    },
                    when (action) {
                        SignalAction.BUY -> MaterialTheme.colorScheme.primaryContainer
                        SignalAction.SELL -> MaterialTheme.colorScheme.secondaryContainer
                        SignalAction.HOLD -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    when (action) {
                        SignalAction.BUY -> MaterialTheme.colorScheme.primary
                        SignalAction.SELL -> MaterialTheme.colorScheme.secondary
                        SignalAction.HOLD -> Color(0xFF59646A)
                    }
                )
            }
            if (index < strategies.lastIndex) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun LotteryAnalysisCard(
    draws: List<LotteryDraw>,
    prediction: LotteryPrediction?,
    compoundRedCount: Int,
    compoundBlueCount: Int,
    report: LotteryResearchReport?
) {
    val latest = remember(draws) {
        draws.maxWithOrNull(compareBy<LotteryDraw> { it.issue.toLongOrNull() ?: 0L }.thenBy { it.date })
    }
    val fallbackHotReds = remember(draws) { frequentNumbers(draws, 1..33, true).take(6) }
    val fallbackOverdueReds = remember(draws) { overdueNumbers(draws, 1..33, true).take(6) }
    val fallbackHotBlues = remember(draws) { frequentNumbers(draws, 1..16, false).take(3) }
    val hotReds = remember(report, fallbackHotReds) {
        report?.redRankings?.sortedBy { it.rank }?.take(6)?.map { it.number } ?: fallbackHotReds
    }
    val overdueReds = remember(report, fallbackOverdueReds) {
        report?.redRankings?.sortedByDescending { it.omissionScore }?.take(6)?.map { it.number } ?: fallbackOverdueReds
    }
    val hotBlues = remember(report, fallbackHotBlues) {
        report?.blueRankings?.sortedBy { it.rank }?.take(3)?.map { it.number } ?: fallbackHotBlues
    }
    Panel {
        Text("数据分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (draws.size < 30) {
            Text("历史样本不足，至少需要 30 期后才会按模型生成推荐。", color = Color(0xFF61706C))
            Text("当前 ${draws.size} 期，请先在“数据”页使用服务端更新。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            return@Panel
        }
        val latestDraw = latest ?: return@Panel
        val targetIssue = prediction?.targetIssue?.ifBlank { null } ?: nextIssueLabel(latestDraw.issue)
        Text("目标期号：第 $targetIssue 期；最新开奖：第 ${latestDraw.issue} 期 ${latestDraw.date}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        MetricRow("样本期数", draws.size.toString(), "当前复式注数", (LotteryRules.combinationCount(compoundRedCount, 6) * compoundBlueCount).toString())
        Spacer(Modifier.height(8.dp))
        Text("计算过程：服务端综合全量频率、最近窗口加权、遗漏期数、三区分布、奇偶比、和值范围和连号约束，再按评分稳定采样。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Text("红球热号 ${hotReds.joinToString(" ")}；红球遗漏 ${overdueReds.joinToString(" ")}；蓝球热号 ${hotBlues.joinToString(" ")}", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        report?.narrative?.takeIf { it.isNotEmpty() }?.forEach {
            Text(it, color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ModelParameterCard(
    modelVersion: String,
    recentWindow: Int,
    budgetBets: Int,
    onModelVersionChange: (String) -> Unit,
    onRecentWindowChange: (Int) -> Unit,
    onBudgetChange: (Int) -> Unit
) {
    Panel {
        Text("模型参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("模型版本", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("balanced_v2" to "均衡", "recent_focus_v3" to "近期", "baseline_v1" to "基线").forEach { (value, label) ->
                ChoiceButton(label, selected = modelVersion == value) { onModelVersionChange(value) }
            }
        }
        Spacer(Modifier.height(10.dp))
        CountStepper("最近窗口", recentWindow, 30, 500, step = 10, onChange = onRecentWindowChange)
        Spacer(Modifier.height(8.dp))
        Text("预算上限只用于计算服务端建议，不会自动改动下面的当前复式方案。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        CountStepper("预算上限（注）", budgetBets, 1, 5000, onChange = onBudgetChange)
    }
}

@Composable
fun CompoundPlanCard(redCount: Int, blueCount: Int, report: LotteryResearchReport?, onChange: (Int, Int) -> Unit) {
    val bets = LotteryRules.combinationCount(redCount, 6) * blueCount
    Panel {
        Text("复式方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("当前实际生成方案：${redCount}+${blueCount}，约 $bets 注。这里控制你点“生成推荐”后真正生成哪种复式。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        CountStepper("红球", redCount, 6, 20) { onChange(it, blueCount) }
        Spacer(Modifier.height(8.dp))
        CountStepper("蓝球", blueCount, 1, 16) { onChange(redCount, it) }
        report?.budgetPlan?.let { plan ->
            Spacer(Modifier.height(10.dp))
            Text("基于预算上限的建议：${plan.redCount}+${plan.blueCount}，${plan.betCount} 注", fontWeight = FontWeight.SemiBold)
            Text(plan.reason, color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onChange(plan.redCount, plan.blueCount) }) {
                Text("设为当前复式方案")
            }
        }
    }
}

@Composable
fun CountStepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Button(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("-") }
        Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
fun DataPage(
    onLotteryImport: (android.net.Uri?) -> Unit,
    onLotteryAutoUpdate: () -> Unit,
    lotteryServerUrl: String,
    onLotteryServerUrlChange: (String) -> Unit,
    onLotteryServerUpdate: () -> Unit,
    onStockImport: (String, String, android.net.Uri?) -> Unit,
    onStockServerUpdate: (String, String) -> Unit,
    onStockHttpUpdate: (String, String, String) -> Unit,
    onBackupExport: (android.net.Uri?) -> Unit,
    onBackupRestore: (android.net.Uri?) -> Unit,
    message: String
) {
    var stockSymbol by remember { mutableStateOf("") }
    var stockName by remember { mutableStateOf("") }
    var stockEndpoint by remember { mutableStateOf("") }
    val lotteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        onLotteryImport(it)
    }
    val stockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        onStockImport(stockSymbol, stockName, it)
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        onBackupExport(it)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        onBackupRestore(it)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusCard(message) }
        item {
            Panel {
                Text("双色球数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("优先使用你部署的轻服务端拉取尽可能多的历史开奖；官网直连作为备用。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = lotteryServerUrl,
                    onValueChange = onLotteryServerUrlChange,
                    label = { Text("服务端地址，如 http://192.168.1.8:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { lotteryLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream")) }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导入CSV")
                    }
                    FilledTonalButton(onClick = onLotteryServerUpdate) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("服务端更新")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLotteryAutoUpdate) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("官网直连")
                    }
                }
            }
        }
        item {
            Panel {
                Text("A股日线数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("常用路径优先走服务端更新；CSV 和自定义 HTTP 仅作为备用导入方式。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = stockSymbol,
                    onValueChange = { stockSymbol = it },
                    label = { Text("股票代码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = stockName,
                    onValueChange = { stockName = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = stockEndpoint,
                    onValueChange = { stockEndpoint = it },
                    label = { Text("CSV地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { onStockServerUpdate(stockSymbol, stockName) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("服务端更新")
                    }
                    Button(onClick = { stockLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream")) }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导入CSV")
                    }
                    FilledTonalButton(onClick = { onStockHttpUpdate(stockSymbol, stockName, stockEndpoint) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("自定义HTTP")
                    }
                }
            }
        }
        item {
            Panel {
                Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { backupExportLauncher.launch("wealthlab-backup.json") }) {
                        Icon(Icons.Default.Storage, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出")
                    }
                    Button(onClick = { backupRestoreLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("恢复")
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1D262B)
    )
}

@Composable
fun StatusCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF17302A))
        }
    }
}

@Composable
fun ActionHeader(title: String, subtitle: String, icon: ImageVector, button: String, onClick: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                MetaPill("研究台", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF172126))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF61706C))
            }
            Button(onClick = onClick) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(button)
            }
        }
    }
}

@Composable
fun PredictionCard(prediction: LotteryPrediction?) {
    var expanded by remember(prediction?.id) { mutableStateOf(false) }
    val reasonLines = remember(prediction?.reasons) {
        prediction?.reasons?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()
    }
    val details = remember(prediction?.ballDetails) {
        prediction?.ballDetails?.takeIf { it.isNotBlank() }?.let(::parseBallDetails).orEmpty()
    }
    Panel {
        if (prediction == null) {
            Text("暂无推荐", style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MetaPill(
                    if (prediction.redBalls.size > 6 || prediction.blueBalls.size > 1) "复式推荐" else "单式推荐",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary
                )
                MetaPill("模型 ${prediction.modelVersion}", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text("目标期号：第 ${prediction.targetIssue.ifBlank { "下一" }} 期 · 最新开奖：第 ${prediction.sourceIssue.ifBlank { "-" }} 期", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                prediction.redBalls.forEach { Ball(it.toString().padStart(2, '0'), Color(0xFFC94444)) }
                prediction.blueBalls.forEach { Ball(it.toString().padStart(2, '0'), Color(0xFF2868B8)) }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("评分 ${"%.2f".format(prediction.score)}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起说明" else "展开说明")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (prediction.analysisSummary.isNotBlank()) {
                    Text(prediction.analysisSummary, color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                }
                Text(prediction.note, color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                reasonLines.forEach {
                    Text("理由：$it", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("逐球解释", fontWeight = FontWeight.SemiBold)
                    details.forEach { detail ->
                        BallDetailRow(detail)
                    }
                }
            }
        }
    }
}

@Composable
fun BallDetailRow(detail: LotteryBallDetail) {
    Spacer(Modifier.height(6.dp))
    val colorName = if (detail.color == "red") "红球" else "蓝球"
    Text(
        "$colorName ${detail.number.toString().padStart(2, '0')} · 总分 ${"%.2f".format(detail.totalScore)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "全量 ${"%.2f".format(detail.fullFrequencyScore)} · 近期 ${"%.2f".format(detail.recentScore)} · 遗漏 ${"%.2f".format(detail.omissionScore)} · 形态 ${"%.2f".format(detail.centerBiasScore)}",
        color = Color(0xFF61706C),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "出现 ${detail.fullCount} 次，近期加权 ${"%.2f".format(detail.recentWeighted)}，当前遗漏 ${detail.missCount} 期",
        color = Color(0xFF61706C),
        style = MaterialTheme.typography.bodySmall
    )
    detail.reasons.forEach {
        Text("入选原因：$it", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LotteryBacktestCard(report: com.demo.wealth.data.LotteryBacktestReport) {
    Panel {
        Text("模型滚动回测", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("使用历史开奖做近 ${report.issueCount} 期模拟，不等于真实推荐次数。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        MetricRow("回测期数", report.issueCount.toString(), "平均最佳红球", "%.2f".format(report.averageBestRedHits))
        Spacer(Modifier.height(8.dp))
        MetricRow("蓝球命中率", percent(report.blueHitRate), "至少3红", percent(report.atLeastThreeRedRate))
    }
}

@Composable
fun ModelComparisonCard(comparisons: List<com.demo.wealth.data.LotteryModelComparison>) {
    Panel {
        Text("模型对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        comparisons.forEach { item ->
            Text(
                "${item.version} · 平均最佳红球 ${"%.2f".format(item.averageBestRedHits)} · 蓝球 ${percent(item.blueHitRate)} · 至少3红 ${percent(item.atLeastThreeRedRate)}",
                color = Color(0xFF61706C),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun NumberRankingCard(report: LotteryResearchReport, predictions: List<LotteryPrediction>) {
    var color by remember(report) { mutableStateOf("red") }
    var sortMode by remember(report) { mutableStateOf("total") }
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
        Text("号码榜单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("红球榜", selected = color == "red") { color = "red" }
            ChoiceButton("蓝球榜", selected = color == "blue") { color = "blue" }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("total" to "综合", "recent" to "近期", "omission" to "遗漏").forEach { (value, label) ->
                ChoiceButton(label, selected = sortMode == value) { sortMode = value }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            sorted.forEach { item ->
                ChoiceButton("${item.number.toString().padStart(2, '0')} #${item.rank}", selected = item.number == selected?.number) {
                    selectedNumber = item.number
                }
            }
        }
        selected?.let { item ->
            Spacer(Modifier.height(12.dp))
            Text("${if (color == "red") "红球" else "蓝球"} ${item.number.toString().padStart(2, '0')} · 综合排名 #${item.rank}", fontWeight = FontWeight.SemiBold)
            Text(
                if (item.number in currentNumbers) "已进入本期推荐" else "本期未入选；最终组合还会受到形态和覆盖约束影响",
                color = if (item.number in currentNumbers) Color(0xFF14745D) else Color(0xFF61706C),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "综合 ${"%.2f".format(item.totalScore)} · 全量 ${"%.2f".format(item.fullFrequencyScore)} · 近期 ${"%.2f".format(item.recentScore)} · 遗漏 ${"%.2f".format(item.omissionScore)}",
                color = Color(0xFF61706C),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "近30期 ${item.recent30Count} 次 · 近60期 ${item.recent60Count} 次 · 近120期 ${item.recent120Count} 次 · 当前遗漏 ${item.missCount} 期",
                color = Color(0xFF61706C),
                style = MaterialTheme.typography.bodySmall
            )
            Text("最近出现：${item.latestAppearances.joinToString("、").ifBlank { "暂无" }}", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            Text(item.summary, color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettlementOverviewCard(settlements: List<LotterySettlement>) {
    val totals = remember(settlements) {
        val totalInvested = settlements.sumOf { it.investedAmount }
        val totalPrize = settlements.sumOf { it.simulatedPrizeAmount }
        Triple(totalInvested, totalPrize, if (totalInvested == 0.0) 0.0 else (totalPrize - totalInvested) / totalInvested)
    }
    val totalInvested = totals.first
    val totalPrize = totals.second
    val totalRoi = totals.third
    var showIssues by remember(settlements) { mutableStateOf(false) }

    Panel {
        Text("真实推荐结算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("仅按每期实际复式方案结算投入；奖金按三至六等奖及福运奖固定金额估算，一二等奖浮动奖金暂不计入。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        MetricRow("已结算期数", settlements.size.toString(), "累计 ROI", percent(totalRoi))
        Spacer(Modifier.height(8.dp))
        MetricRow("累计投入", money(totalInvested), "奖金", money(totalPrize))
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { showIssues = !showIssues }) {
            Text(if (showIssues) "收起每期明细" else "展开每期明细")
        }
        if (showIssues) {
            Spacer(Modifier.height(10.dp))
            if (settlements.isEmpty()) {
                Text("还没有可结算的真实推荐。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            } else {
                settlements.forEachIndexed { index, settlement ->
                    SettlementIssueRow(settlement)
                    if (index < settlements.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementIssueRow(settlement: LotterySettlement) {
    var expanded by remember(settlement.issue) { mutableStateOf(false) }
    val details = remember(settlement.detailJson) { parseSettlementDetails(settlement.detailJson) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("第 ${settlement.issue} 期", fontWeight = FontWeight.SemiBold)
                Text(
                    "${money(settlement.investedAmount)} 投入 · ${money(settlement.simulatedPrizeAmount)} 奖金 · ROI ${percent(settlement.roi)}",
                    color = Color(0xFF61706C),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开")
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "最佳红球 ${settlement.bestRedHits} 个 · 蓝球${if (settlement.blueHit) "命中" else "未中"} · 共 ${settlement.betCount} 注",
                color = Color(0xFF61706C),
                style = MaterialTheme.typography.bodySmall
            )
            details.forEach { detail ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "${detail.label} ${detail.redBalls.joinToString(" ")} + ${detail.blueBalls.joinToString(" ")}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${detail.betCount} 注 · 最佳红球 ${detail.bestRedHits} · 蓝球${if (detail.blueHit) "命中" else "未中"} · 奖金 ${money(detail.prizeAmount)}",
                    color = Color(0xFF61706C),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun LotteryTrendCard(draws: List<LotteryDraw>) {
    val recent = remember(draws) { draws.take(30).reversed() }
    val selectedNumber = remember(draws) { mutableIntStateOf(frequentNumbers(draws, 1..33, true).firstOrNull() ?: 1) }
    var expanded by remember(draws) { mutableStateOf(false) }
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("走势可视化", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("最近 30 期红球冷热分布与遗漏走势。", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开")
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            HeatmapGrid(recent)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                (1..33).forEach { number ->
                    ChoiceButton(number.toString().padStart(2, '0'), selected = selectedNumber.intValue == number) {
                        selectedNumber.intValue = number
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OmissionCurveCanvas(recent, selectedNumber.intValue)
            Text("当前查看：红球 ${selectedNumber.intValue.toString().padStart(2, '0')}", color = Color(0xFF61706C), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HeatmapGrid(draws: List<LotteryDraw>) {
    val counts = remember(draws) { (1..33).associateWith { number -> draws.count { number in it.redBalls } } }
    val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.entries.chunked(11).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (number, count) ->
                    val ratio = count.toFloat() / maxCount
                    val color = when {
                        ratio >= 0.75f -> Color(0xFFC95B54)
                        ratio >= 0.45f -> Color(0xFFE3A39E)
                        count > 0 -> Color(0xFFF4D9D6)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(color, RoundedCornerShape(6.dp))
                    ) {
                        Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun OmissionCurveCanvas(draws: List<LotteryDraw>, number: Int) {
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
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        if (omissions.size < 2) return@Canvas
        val maxMiss = max(1, omissions.maxOrNull() ?: 1)
        val step = size.width / (omissions.size - 1)
        val path = Path()
        omissions.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - value.toFloat() / maxMiss * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color(0xFFD7DEE2), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable
fun DrawCard(draw: LotteryDraw) {
    Panel {
        Text("开奖期号 ${draw.issue}  ${draw.date}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            draw.redBalls.forEach { Ball(it.toString().padStart(2, '0'), Color(0xFFC94444)) }
            Ball(draw.blueBall.toString().padStart(2, '0'), Color(0xFF2868B8))
        }
    }
}

@Composable
fun Ball(text: String, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .background(color, CircleShape)
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BacktestCard(snapshot: BacktestSnapshot?) {
    Panel {
        if (snapshot == null) {
            Text("暂无回测", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("${snapshot.symbol} · ${snapshot.strategyName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            EquityCurve(snapshot.equityCurve)
            Spacer(Modifier.height(8.dp))
            MetricRow(
                "收益", percent(snapshot.totalReturn),
                "回撤", percent(snapshot.maxDrawdown)
            )
            MetricRow(
                "胜率", percent(snapshot.winRate),
                "信号", snapshot.latestSignal
            )
        }
    }
}

@Composable
fun EquityCurve(values: List<Double>) {
    val line = Color(0xFF14745D)
    Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val range = max(1.0, maxValue - min)
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Color(0xFFE2E8E5), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable
fun MetricRow(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Metric(leftLabel, leftValue, Modifier.weight(1f))
        Metric(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF61706C))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF172126))
    }
}

@Composable
fun CardRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(8.dp)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF61706C))
        }
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun MetaPill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text)
        }
    }
}

fun percent(value: Double): String = "${"%.2f".format(value * 100)}%"

fun money(value: Double): String = "¥${"%.2f".format(value)}"

fun nextIssueLabel(issue: String): String =
    issue.toLongOrNull()?.let { (it + 1).toString().padStart(issue.length, '0') } ?: "下一期"

fun frequentNumbers(draws: List<LotteryDraw>, range: IntRange, red: Boolean): List<Int> =
    range.map { number ->
        val count = draws.count { draw -> if (red) number in draw.redBalls else number == draw.blueBall }
        number to count
    }.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).map { it.first }

fun overdueNumbers(draws: List<LotteryDraw>, range: IntRange, red: Boolean): List<Int> {
    val ordered = draws.sortedWith(compareByDescending<LotteryDraw> { it.issue.toLongOrNull() ?: 0L }.thenByDescending { it.date })
    return range.map { number ->
        val miss = ordered.indexOfFirst { draw -> if (red) number in draw.redBalls else number == draw.blueBall }.let { if (it < 0) ordered.size else it }
        number to miss
    }.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).map { it.first }
}

fun parseBallDetails(text: String): List<LotteryBallDetail> {
    if (text.isBlank()) return emptyList()
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        LotteryBallDetail(
            color = item.optString("color"),
            number = item.optInt("number"),
            totalScore = item.optDouble("totalScore"),
            fullFrequencyScore = item.optDouble("fullFrequencyScore"),
            recentScore = item.optDouble("recentScore"),
            omissionScore = item.optDouble("omissionScore"),
            centerBiasScore = item.optDouble("centerBiasScore"),
            fullCount = item.optInt("fullCount"),
            recentWeighted = item.optDouble("recentWeighted"),
            missCount = item.optInt("missCount"),
            reasons = item.optJSONArray("reasons")?.let { reasons ->
                (0 until reasons.length()).map { reasons.optString(it) }
            }.orEmpty()
        )
    }
}

fun parseSettlementDetails(text: String): List<LotterySettlementDetail> {
    if (text.isBlank()) return emptyList()
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val tiers = item.optJSONObject("tierCounts")
        LotterySettlementDetail(
            label = item.optString("label"),
            redBalls = item.optJSONArray("redBalls")?.let { reds -> (0 until reds.length()).map { reds.optInt(it) } }.orEmpty(),
            blueBalls = item.optJSONArray("blueBalls")?.let { blues -> (0 until blues.length()).map { blues.optInt(it) } }.orEmpty(),
            betCount = item.optLong("betCount"),
            bestRedHits = item.optInt("bestRedHits"),
            blueHit = item.optBoolean("blueHit"),
            prizeAmount = item.optDouble("prizeAmount"),
            tierCounts = if (tiers == null) emptyMap() else tiers.keys().asSequence().associateWith { tiers.optLong(it) }
        )
    }
}
