package com.demo.wealth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.demo.wealth.ui.components.StatusIndicator
import com.demo.wealth.ui.components.connectionStatus
import com.demo.wealth.ui.screens.DataScreen
import com.demo.wealth.ui.screens.HomeScreen
import com.demo.wealth.ui.screens.LotteryScreen
import com.demo.wealth.ui.screens.SportsScreen
import com.demo.wealth.ui.theme.PrimaryContainer
import com.demo.wealth.ui.theme.PrimaryHover
import com.demo.wealth.ui.theme.SpacingLg
import com.demo.wealth.ui.theme.TextPrimary
import com.demo.wealth.ui.theme.TextSecondary
import com.demo.wealth.ui.theme.TextTertiary

private data class TabSpec(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthApp(viewModel: WealthViewModel = viewModel()) {
    val tabs = remember {
        listOf(
            TabSpec("首页", Icons.Default.Home),
            TabSpec("福彩", Icons.Default.Casino),
            TabSpec("体彩", Icons.Default.SportsSoccer),
            TabSpec("数据", Icons.Default.Storage)
        )
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val draws by viewModel.lotteryDraws.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val settlements by viewModel.lotterySettlements.collectAsState()
    val footballMatches by viewModel.footballMatches.collectAsState()
    val footballRecommendations by viewModel.footballRecommendations.collectAsState()
    val footballRecommendationHistory by viewModel.footballRecommendationHistory.collectAsState()
    val isServerConnected by viewModel.isServerConnected.collectAsState()
    val lotteryServerUrl by viewModel.lotteryServerUrl.collectAsState()
    val compoundRedCount by viewModel.compoundRedCount.collectAsState()
    val compoundBlueCount by viewModel.compoundBlueCount.collectAsState()
    val recentWindow by viewModel.recentWindow.collectAsState()
    val budgetBets by viewModel.budgetBets.collectAsState()
    val modelVersion by viewModel.modelVersion.collectAsState()
    val researchReport by viewModel.researchReport.collectAsState()
    val isLotteryUpdating by viewModel.isLotteryUpdating.collectAsState()
    val isFootballUpdating by viewModel.isFootballUpdating.collectAsState()
    val isLotteryGenerating by viewModel.isLotteryGenerating.collectAsState()
    val isFootballGenerating by viewModel.isFootballGenerating.collectAsState()
    val lotteryUpdateError by viewModel.lotteryUpdateError.collectAsState()
    val footballUpdateError by viewModel.footballUpdateError.collectAsState()
    val lotteryGenerationError by viewModel.lotteryGenerationError.collectAsState()
    val footballGenerationError by viewModel.footballGenerationError.collectAsState()
    val status = connectionStatus(
        isSyncing = isLotteryUpdating || isFootballUpdating || isLotteryGenerating || isFootballGenerating,
        isConnected = isServerConnected
    )

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("搞钱", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("福彩与体彩实验台", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                },
                actions = { StatusIndicator(status, modifier = Modifier.padding(end = SpacingLg)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
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
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (selected) {
                0 -> HomeScreen(predictions, settlements, footballMatches)
                1 -> LotteryScreen(
                    draws, predictions, settlements, researchReport, compoundRedCount, compoundBlueCount,
                    recentWindow, budgetBets, modelVersion, viewModel::setCompoundPlan,
                    viewModel::setRecentWindow, viewModel::setBudgetBets, viewModel::setModelVersion,
                    viewModel::generateLottery, isLotteryGenerating, lotteryGenerationError
                )
                2 -> SportsScreen(
                    footballMatches, footballRecommendations, footballRecommendationHistory,
                    viewModel::generateFootballRecommendations, isFootballGenerating, footballGenerationError
                )
                3 -> DataScreen(
                    lotteryServerUrl, viewModel::setLotteryServerUrl, viewModel::updateLotteryFromServer,
                    viewModel::updateFootballFromServer, viewModel::exportBackup, viewModel::restoreBackup,
                    draws.size, footballMatches.size, isLotteryUpdating, isFootballUpdating,
                    isServerConnected, lotteryUpdateError, footballUpdateError
                )
            }
        }
    }
}
