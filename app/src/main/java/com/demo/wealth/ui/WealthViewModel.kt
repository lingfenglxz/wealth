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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
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
    val footballRecommendationHistory: StateFlow<List<FootballRecommendationEntity>> = repository.footballRecommendationHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val messageChannel = Channel<String>(capacity = Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    val isServerConnected = MutableStateFlow(false)
    val isLotteryUpdating = MutableStateFlow(false)
    val isFootballUpdating = MutableStateFlow(false)
    val isLotteryGenerating = MutableStateFlow(false)
    val isFootballGenerating = MutableStateFlow(false)
    val lotteryUpdateError = MutableStateFlow<String?>(null)
    val footballUpdateError = MutableStateFlow<String?>(null)
    val lotteryGenerationError = MutableStateFlow<String?>(null)
    val footballGenerationError = MutableStateFlow<String?>(null)
    private val prefs = application.getSharedPreferences("wealthlab", Context.MODE_PRIVATE)
    val lotteryServerUrl = MutableStateFlow(prefs.getString("lottery_server_url", "") ?: "")
    val compoundRedCount = MutableStateFlow(prefs.getInt("compound_red_count", 6))
    val compoundBlueCount = MutableStateFlow(prefs.getInt("compound_blue_count", 3))
    val researchReport: StateFlow<LotteryResearchReport?> =
        repository.lotteryResearchReport.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.refreshLotterySettlements()
        }
    }

    fun updateLotteryFromServer() {
        if (!isLotteryUpdating.compareAndSet(expect = false, update = true)) return
        lotteryUpdateError.value = null
        viewModelScope.launch {
            try {
                val drawCount = repository.updateLotteryFromServer(lotteryServerUrl.value)
                isServerConnected.value = true
                notify("服务端更新 $drawCount 期")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lotteryUpdateError.value = friendlyError(error)
                isServerConnected.value = false
                notify("服务端更新失败：${lotteryUpdateError.value}")
            } finally {
                isLotteryUpdating.value = false
            }
        }
    }

    fun updateFootballFromServer() {
        if (!isFootballUpdating.compareAndSet(expect = false, update = true)) return
        footballUpdateError.value = null
        viewModelScope.launch {
            try {
                val count = repository.updateFootballMatchesFromServer(lotteryServerUrl.value)
                isServerConnected.value = true
                notify("服务端已更新 $count 场足球赛事")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                footballUpdateError.value = friendlyError(error)
                isServerConnected.value = false
                notify("足球赛事更新失败：${footballUpdateError.value}")
            } finally {
                isFootballUpdating.value = false
            }
        }
    }

    fun generateFootballRecommendations() {
        if (!isFootballGenerating.compareAndSet(expect = false, update = true)) return
        footballGenerationError.value = null
        viewModelScope.launch {
            try {
                val count = repository.generateFootballRecommendationsFromServer(lotteryServerUrl.value)
                isServerConnected.value = true
                notify("服务端已生成 $count 条足球彩票推荐")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                footballGenerationError.value = friendlyError(error)
                isServerConnected.value = false
                notify("足球推荐失败：${footballGenerationError.value}")
            } finally {
                isFootballGenerating.value = false
            }
        }
    }

    fun generateLottery(redCount: Int = compoundRedCount.value, blueCount: Int = compoundBlueCount.value) {
        if (!isLotteryGenerating.compareAndSet(expect = false, update = true)) return
        lotteryGenerationError.value = null
        setCompoundPlan(redCount, blueCount)
        viewModelScope.launch {
            try {
                val response = repository.generateLotteryPredictionsFromServer(
                    baseUrl = lotteryServerUrl.value,
                    compoundRedCount = redCount,
                    compoundBlueCount = blueCount
                )
                val predictions = response.predictions
                val target = predictions.firstOrNull()?.targetIssue ?: "下一"
                isServerConnected.value = true
                notify("服务端已生成第 ${target} 期 ${redCount}+${blueCount} 推荐")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lotteryGenerationError.value = friendlyError(error)
                isServerConnected.value = false
                notify("生成失败：${lotteryGenerationError.value}")
            } finally {
                isLotteryGenerating.value = false
            }
        }
    }

    fun setLotteryServerUrl(value: String) {
        if (lotteryServerUrl.value == value) return
        lotteryServerUrl.value = value
        isServerConnected.value = false
        lotteryUpdateError.value = null
        footballUpdateError.value = null
        lotteryGenerationError.value = null
        footballGenerationError.value = null
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

    fun exportBackup(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                val text = repository.exportBackup()
                val output = requireNotNull(resolver.openOutputStream(uri)) { "无法写入所选文件" }
                output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }.onSuccess {
                notify("备份已导出")
            }.onFailure {
                notify("导出失败：${it.message ?: "无法写入文件"}")
            }
        }
    }

    fun restoreBackup(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                repository.restoreBackup(readText(uri))
                notify("备份已恢复")
            }.onFailure {
                notify("恢复失败：${it.message ?: "文件格式错误"}")
            }
        }
    }

    private fun notify(message: String) {
        messageChannel.trySend(message)
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
