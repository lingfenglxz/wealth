package com.demo.wealth.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val isServerConnected by viewModel.isServerConnected.collectAsStateWithLifecycle()
    val isLotteryUpdating by viewModel.isLotteryUpdating.collectAsStateWithLifecycle()
    val isFootballUpdating by viewModel.isFootballUpdating.collectAsStateWithLifecycle()
    val isLotteryGenerating by viewModel.isLotteryGenerating.collectAsStateWithLifecycle()
    val isFootballGenerating by viewModel.isFootballGenerating.collectAsStateWithLifecycle()
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
                    Text("搞钱", fontWeight = FontWeight.Bold, color = TextPrimary)
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
                0 -> HomeRoute(viewModel)
                1 -> LotteryRoute(viewModel)
                2 -> SportsRoute(viewModel)
                3 -> DataRoute(viewModel)
            }
        }
    }
}

@Composable
private fun HomeRoute(viewModel: WealthViewModel) {
    val predictions by viewModel.predictions.collectAsStateWithLifecycle()
    val settlements by viewModel.lotterySettlements.collectAsStateWithLifecycle()
    val draws by viewModel.lotteryDraws.collectAsStateWithLifecycle()
    HomeScreen(predictions, settlements, draws)
}

@Composable
private fun LotteryRoute(viewModel: WealthViewModel) {
    val draws by viewModel.lotteryDraws.collectAsStateWithLifecycle()
    val totalDrawCount by viewModel.lotteryDrawTotalCount.collectAsStateWithLifecycle()
    val predictions by viewModel.predictions.collectAsStateWithLifecycle()
    val settlements by viewModel.lotterySettlements.collectAsStateWithLifecycle()
    val report by viewModel.researchReport.collectAsStateWithLifecycle()
    val redCount by viewModel.compoundRedCount.collectAsStateWithLifecycle()
    val blueCount by viewModel.compoundBlueCount.collectAsStateWithLifecycle()
    val generating by viewModel.isLotteryGenerating.collectAsStateWithLifecycle()
    val error by viewModel.lotteryGenerationError.collectAsStateWithLifecycle()
    LotteryScreen(
        draws, predictions, settlements, report, redCount, blueCount,
        viewModel::setCompoundPlan, viewModel::generateLottery, generating, error,
        totalDrawCount = totalDrawCount
    )
}

@Composable
private fun SportsRoute(viewModel: WealthViewModel) {
    SportsScreen()
}

@Composable
private fun DataRoute(viewModel: WealthViewModel) {
    val serverUrl by viewModel.lotteryServerUrl.collectAsStateWithLifecycle()
    val drawCount by viewModel.lotteryDrawTotalCount.collectAsStateWithLifecycle()
    val matches by viewModel.footballMatches.collectAsStateWithLifecycle()
    val lotteryUpdating by viewModel.isLotteryUpdating.collectAsStateWithLifecycle()
    val footballUpdating by viewModel.isFootballUpdating.collectAsStateWithLifecycle()
    val connected by viewModel.isServerConnected.collectAsStateWithLifecycle()
    val lotteryError by viewModel.lotteryUpdateError.collectAsStateWithLifecycle()
    val footballError by viewModel.footballUpdateError.collectAsStateWithLifecycle()
    DataScreen(
        serverUrl, viewModel::setLotteryServerUrl, viewModel::updateLotteryFromServer,
        viewModel::updateFootballFromServer, viewModel::exportBackup, viewModel::restoreBackup,
        drawCount, matches.size, lotteryUpdating, footballUpdating, connected, lotteryError, footballError
    )
}
