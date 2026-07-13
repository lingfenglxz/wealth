package com.demo.wealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demo.wealth.ui.WealthViewModel
import com.demo.wealth.ui.screens.DataScreen
import com.demo.wealth.ui.screens.HomeScreen
import com.demo.wealth.ui.screens.LotteryScreen
import com.demo.wealth.ui.screens.SportsScreen
import com.demo.wealth.ui.theme.WealthTheme
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.PrimaryHover
import com.demo.wealth.ui.theme.TextPrimary
import com.demo.wealth.ui.theme.TextSecondary
import com.demo.wealth.ui.theme.TextTertiary

/**
 * WealthLab 主 Activity
 *
 * 职责：仅作为入口 + 导航壳（Scaffold + NavigationBar + 页面路由）
 *
 * 重构后从 1393 行降至 ~120 行：
 * - 设计系统 -> ui/theme/
 * - 组件库 -> ui/components/
 * - 4 个页面 -> ui/screens/
 * - 工具函数 -> ui/UiUtils.kt
 */
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

data class TabSpec(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthApp(viewModel: WealthViewModel = viewModel()) {
    val tabs = listOf(
        TabSpec("首页", Icons.Default.Home),
        TabSpec("福彩", Icons.Default.Casino),
        TabSpec("体彩", Icons.Default.SportsSoccer),
        TabSpec("数据", Icons.Default.Storage)
    )
    var selected by rememberSaveable { mutableIntStateOf(0) }

    // 收集 ViewModel 状态
    val draws by viewModel.lotteryDraws.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val settlements by viewModel.lotterySettlements.collectAsState()
    val footballMatches by viewModel.footballMatches.collectAsState()
    val footballRecommendations by viewModel.footballRecommendations.collectAsState()
    val footballRecommendationHistory by viewModel.footballRecommendationHistory.collectAsState()
    val message by viewModel.message.collectAsState()
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
                    androidx.compose.foundation.layout.Column {
                        Text("搞钱", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "福彩与体彩实验台",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = PrimaryContainer,
                            selectedIconColor = PrimaryHover,
                            selectedTextColor = PrimaryHover,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selected) {
                0 -> HomeScreen(predictions, settlements, footballMatches, message)
                1 -> LotteryScreen(
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
                2 -> SportsScreen(
                    matches = footballMatches,
                    recommendations = footballRecommendations,
                    recommendationHistory = footballRecommendationHistory,
                    onGenerate = viewModel::generateFootballRecommendations
                )
                3 -> DataScreen(
                    lotteryServerUrl = lotteryServerUrl,
                    onLotteryServerUrlChange = viewModel::setLotteryServerUrl,
                    onLotteryServerUpdate = viewModel::updateLotteryFromServer,
                    onFootballServerUpdate = viewModel::updateFootballFromServer,
                    onBackupExport = viewModel::exportBackup,
                    onBackupRestore = viewModel::restoreBackup,
                    lotteryDrawsCount = draws.size,
                    footballMatchesCount = footballMatches.size,
                    isLotteryUpdating = false,
                    isFootballUpdating = false
                )
            }
        }
    }
}
