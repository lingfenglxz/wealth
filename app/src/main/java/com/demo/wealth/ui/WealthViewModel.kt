package com.demo.wealth.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demo.wealth.data.FootballMatchEntity
import com.demo.wealth.data.FootballRecommendationEntity
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryResearchReport
import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.data.WealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WealthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WealthRepository(application)
    private val resolver = application.contentResolver

    val lotteryDraws: StateFlow<List<LotteryDraw>> = repository.lotteryDraws.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val predictions: StateFlow<List<LotteryPrediction>> = repository.lotteryPredictions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val predictionHistory: StateFlow<List<LotteryPrediction>> = repository.lotteryPredictionHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lotterySettlements: StateFlow<List<LotterySettlement>> = repository.lotterySettlements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val footballMatches: StateFlow<List<FootballMatchEntity>> = repository.footballMatches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val footballRecommendations: StateFlow<List<FootballRecommendationEntity>> = repository.footballRecommendations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow("准备就绪")
    private val prefs = application.getSharedPreferences("wealthlab", Context.MODE_PRIVATE)
    val lotteryServerUrl = MutableStateFlow(prefs.getString("lottery_server_url", "") ?: "")
    val compoundRedCount = MutableStateFlow(prefs.getInt("compound_red_count", 9))
    val compoundBlueCount = MutableStateFlow(prefs.getInt("compound_blue_count", 3))
    val recentWindow = MutableStateFlow(prefs.getInt("lottery_recent_window", 120))
    val budgetBets = MutableStateFlow(prefs.getInt("lottery_budget_bets", 252))
    val modelVersion = MutableStateFlow(prefs.getString("lottery_model_version", "balanced_v2") ?: "balanced_v2")
    val researchReport: StateFlow<LotteryResearchReport?> =
        repository.lotteryResearchReport.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.refreshLotterySettlements()
        }
    }

    fun updateLotteryFromServer() {
        viewModelScope.launch {
            runCatching {
                val drawCount = repository.updateLotteryFromServer(lotteryServerUrl.value)
                message.value = "服务端更新 $drawCount 期"
            }.onFailure {
                message.value = "服务端更新失败：${friendlyError(it)}"
            }
        }
    }

    fun updateFootballFromServer() {
        viewModelScope.launch {
            runCatching {
                val count = repository.updateFootballMatchesFromServer(lotteryServerUrl.value)
                message.value = "服务端已更新 $count 场足球赛事"
            }.onFailure {
                message.value = "足球赛事更新失败：${friendlyError(it)}"
            }
        }
    }

    fun generateFootballRecommendations() {
        viewModelScope.launch {
            runCatching {
                val count = repository.generateFootballRecommendationsFromServer(lotteryServerUrl.value)
                message.value = "服务端已生成 $count 条足球彩票实验推荐"
            }.onFailure {
                message.value = "足球推荐失败：${friendlyError(it)}"
            }
        }
    }

    fun generateLottery(redCount: Int = compoundRedCount.value, blueCount: Int = compoundBlueCount.value) {
        setCompoundPlan(redCount, blueCount)
        viewModelScope.launch {
            runCatching {
                val response = repository.generateLotteryPredictionsFromServer(
                    baseUrl = lotteryServerUrl.value,
                    compoundRedCount = redCount,
                    compoundBlueCount = blueCount,
                    modelVersion = modelVersion.value,
                    recentWindow = recentWindow.value,
                    budgetBets = budgetBets.value
                )
                val predictions = response.predictions
                val target = predictions.firstOrNull()?.targetIssue ?: "下一"
                message.value = "服务端已生成第 ${target} 期 ${predictions.size} 组推荐，复式方案 ${redCount}+${blueCount}"
            }.onFailure {
                message.value = "生成失败：${friendlyError(it)}"
            }
        }
    }

    fun setLotteryServerUrl(value: String) {
        lotteryServerUrl.value = value
        prefs.edit().putString("lottery_server_url", value).apply()
    }

    fun setCompoundPlan(redCount: Int, blueCount: Int) {
        val red = redCount.coerceIn(6, 20)
        val blue = blueCount.coerceIn(1, 16)
        compoundRedCount.value = red
        compoundBlueCount.value = blue
        prefs.edit()
            .putInt("compound_red_count", red)
            .putInt("compound_blue_count", blue)
            .apply()
    }

    fun setRecentWindow(value: Int) {
        val normalized = value.coerceIn(30, 500)
        recentWindow.value = normalized
        prefs.edit().putInt("lottery_recent_window", normalized).apply()
    }

    fun setBudgetBets(value: Int) {
        val normalized = value.coerceIn(1, 5000)
        budgetBets.value = normalized
        prefs.edit().putInt("lottery_budget_bets", normalized).apply()
    }

    fun setModelVersion(value: String) {
        modelVersion.value = value
        prefs.edit().putString("lottery_model_version", value).apply()
    }

    fun exportBackup(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val text = repository.exportBackup()
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            message.value = "备份已导出"
        }
    }

    fun restoreBackup(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                repository.restoreBackup(readText(uri))
                message.value = "备份已恢复"
            }.onFailure {
                message.value = "恢复失败：${it.message ?: "文件格式错误"}"
            }
        }
    }

    private fun readText(uri: Uri): String =
        resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.startsWith("http://") || message.startsWith("https://") -> "官网接口请求失败，请稍后重试"
            message.length > 80 -> message.take(80)
            message.isBlank() -> "网络不可用"
            else -> message
        }
    }
}
