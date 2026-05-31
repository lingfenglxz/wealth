package com.demo.wealth.data

import android.content.Context
import com.demo.wealth.domain.lottery.LotteryCsvParser
import com.demo.wealth.domain.lottery.LotteryOfficialParser
import com.demo.wealth.domain.market.StockCsvParser
import com.demo.wealth.domain.quant.Backtester
import com.demo.wealth.domain.quant.BreakoutStrategy
import com.demo.wealth.domain.quant.MacdTrendStrategy
import com.demo.wealth.domain.quant.MovingAverageCrossStrategy
import com.demo.wealth.domain.quant.QuantStrategy
import com.demo.wealth.domain.quant.RsiReversionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class WealthRepository(context: Context) {
    private val dao = AppDatabase.get(context).wealthDao()
    private val backtester = Backtester()

    val lotteryDraws: Flow<List<LotteryDraw>> = dao.observeLotteryDraws()
    val lotteryPredictions: Flow<List<LotteryPrediction>> = dao.observePredictions()
    val lotteryPredictionHistory: Flow<List<LotteryPrediction>> = dao.observeAllPredictions()
    val lotterySettlements: Flow<List<LotterySettlement>> = dao.observeLotterySettlements()
    val lotteryResearchReport: Flow<LotteryResearchReport?> =
        dao.observeLatestResearchSnapshot().map { it?.let(::parseStoredResearchReport) }
    val stockSymbols: Flow<List<StockSymbol>> = dao.observeSymbols()
    val backtests: Flow<List<BacktestSnapshot>> = dao.observeBacktests()

    fun observeCandles(symbol: String): Flow<List<StockCandle>> = dao.observeCandles(symbol)

    suspend fun refreshLotterySettlements() {
        settleResolvedPredictions()
    }

    suspend fun importLotteryCsv(text: String): Int {
        val draws = LotteryCsvParser.parse(text)
        dao.upsertLotteryDraws(draws)
        settleResolvedPredictions()
        return draws.size
    }

    suspend fun updateLotteryFromOfficial(): Int {
        val text = fetchFirst(CWL_SSQ_URLS)
        val draws = LotteryOfficialParser.parseCwlJson(text)
        if (draws.isEmpty()) throw IOException("官网返回数据为空")
        dao.upsertLotteryDraws(draws)
        settleResolvedPredictions()
        return draws.size
    }

    suspend fun updateLotteryFromServer(baseUrl: String): Int {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText("$normalized/api/lottery/ssq/draws?limit=3000&refresh=true")
        val draws = LotteryOfficialParser.parseServerJson(text)
        if (draws.isEmpty()) throw IOException("服务端返回数据为空")
        dao.upsertLotteryDraws(draws)
        settleResolvedPredictions()
        return draws.size
    }

    suspend fun generateLotteryPredictionsFromServer(
        baseUrl: String,
        singleCount: Int = 3,
        compoundCount: Int = 1,
        compoundRedCount: Int = 9,
        compoundBlueCount: Int = 3,
        modelVersion: String = "balanced_v2",
        recentWindow: Int = 120,
        budgetBets: Int = 252
    ): LotteryRecommendationResponse {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText(
            "$normalized/api/lottery/ssq/recommendations" +
                "?singleCount=$singleCount&compoundCount=$compoundCount&redCount=$compoundRedCount&blueCount=$compoundBlueCount" +
                "&modelVersion=$modelVersion&recentWindow=$recentWindow&budgetBets=$budgetBets&limit=3000"
        )
        val draws = LotteryOfficialParser.parseServerJson(text)
        if (draws.size < MIN_LOTTERY_SAMPLE_SIZE) {
            throw IOException("服务端历史开奖数据不足，至少需要 $MIN_LOTTERY_SAMPLE_SIZE 期")
        }
        val predictions = parseServerPredictions(text)
        val report = parseResearchReport(text)
        if (predictions.isEmpty()) throw IOException("服务端未返回推荐结果")
        dao.upsertLotteryDraws(draws)
        dao.deletePredictionsForIssue(predictions.first().targetIssue)
        dao.insertPredictions(predictions)
        report?.let {
            dao.deleteResearchSnapshotForIssue(predictions.first().targetIssue)
            dao.insertResearchSnapshot(
                LotteryResearchSnapshot(
                    createdAt = predictions.first().createdAt,
                    targetIssue = predictions.first().targetIssue,
                    sourceIssue = predictions.first().sourceIssue,
                    modelVersion = it.modelVersion,
                    reportJson = it.toJsonObject().toString()
                )
            )
        }
        settleResolvedPredictions()
        return LotteryRecommendationResponse(predictions, report)
    }

    suspend fun importStockCsv(symbol: String, name: String, text: String): Int {
        val candles = StockCsvParser.parse(symbol, text)
        if (candles.isNotEmpty()) {
            dao.upsertSymbols(listOf(StockSymbol(symbol = symbol, name = name.ifBlank { symbol }, lastUpdated = candles.last().date)))
            dao.upsertCandles(candles)
        }
        return candles.size
    }

    suspend fun updateStockFromHttp(symbol: String, name: String, endpoint: String): Int {
        val url = endpoint.replace("{symbol}", symbol)
        val text = fetchText(url)
        return importStockCsv(symbol, name, text)
    }

    suspend fun updateStockFromServer(baseUrl: String, symbol: String, name: String): Int {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText("$normalizedBaseUrl/api/market/stocks/$symbol/daily?startDate=20180101&adjust=qfq")
        val candles = parseServerCandles(symbol, text)
        if (candles.isEmpty()) throw IOException("服务端未返回股票日线数据")
        dao.upsertSymbols(listOf(StockSymbol(symbol = symbol, name = name.ifBlank { symbol }, lastUpdated = candles.last().date)))
        dao.upsertCandles(candles)
        return candles.size
    }

    suspend fun runBacktests(symbol: String): List<BacktestSnapshot> {
        val candles = dao.getCandles(symbol)
        val strategies = defaultStrategies()
        return strategies.map { strategy ->
            val result = backtester.run(candles, strategy)
            BacktestSnapshot(
                symbol = symbol,
                strategyName = result.strategyName,
                createdAt = System.currentTimeMillis(),
                totalReturn = result.totalReturn,
                maxDrawdown = result.maxDrawdown,
                winRate = result.winRate,
                tradeCount = result.tradeCount,
                equityCurve = result.equityCurve,
                latestSignal = result.latestSignal.action.name
            ).also { dao.insertBacktest(it) }
        }
    }

    suspend fun runAllStockSignals(): Int {
        val symbols = dao.getSymbols()
        symbols.forEach { runBacktests(it.symbol) }
        return symbols.size
    }

    suspend fun exportBackup(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("lotteryDraws", JSONArray().also { array ->
            dao.getLotteryDraws().forEach {
                array.put(JSONObject()
                    .put("issue", it.issue)
                    .put("date", it.date)
                    .put("redBalls", JSONArray(it.redBalls))
                    .put("blueBall", it.blueBall))
            }
        })
        root.put("predictions", JSONArray().also { array ->
            dao.getPredictions(1000).forEach {
                array.put(JSONObject()
                    .put("createdAt", it.createdAt)
                    .put("targetIssue", it.targetIssue)
                    .put("sourceIssue", it.sourceIssue)
                    .put("modelVersion", it.modelVersion)
                    .put("redBalls", JSONArray(it.redBalls))
                    .put("blueBalls", JSONArray(it.blueBalls))
                    .put("score", it.score)
                    .put("analysisSummary", it.analysisSummary)
                    .put("reasons", it.reasons)
                    .put("ballDetails", it.ballDetails)
                    .put("note", it.note))
            }
        })
        root.put("researchReports", JSONArray().also { array ->
            dao.getResearchSnapshots(1000).forEach {
                array.put(JSONObject()
                    .put("createdAt", it.createdAt)
                    .put("targetIssue", it.targetIssue)
                    .put("sourceIssue", it.sourceIssue)
                    .put("modelVersion", it.modelVersion)
                    .put("report", JSONObject(it.reportJson)))
            }
        })
        root.put("lotterySettlements", JSONArray().also { array ->
            dao.getLotterySettlements(1000).forEach {
                array.put(JSONObject()
                    .put("issue", it.issue)
                    .put("drawDate", it.drawDate)
                    .put("settledAt", it.settledAt)
                    .put("betCount", it.betCount)
                    .put("investedAmount", it.investedAmount)
                    .put("simulatedPrizeAmount", it.simulatedPrizeAmount)
                    .put("roi", it.roi)
                    .put("bestRedHits", it.bestRedHits)
                    .put("blueHit", it.blueHit)
                    .put("tierCounts", JSONObject(it.tierCountsJson))
                    .put("details", JSONArray(it.detailJson)))
            }
        })
        root.put("symbols", JSONArray().also { array ->
            dao.getSymbols().forEach {
                array.put(JSONObject()
                    .put("symbol", it.symbol)
                    .put("name", it.name)
                    .put("market", it.market)
                    .put("lastUpdated", it.lastUpdated))
            }
        })
        root.put("candles", JSONArray().also { array ->
            dao.getAllCandles().forEach {
                array.put(JSONObject()
                    .put("symbol", it.symbol)
                    .put("date", it.date)
                    .put("open", it.open)
                    .put("high", it.high)
                    .put("low", it.low)
                    .put("close", it.close)
                    .put("volume", it.volume))
            }
        })
        root.put("backtests", JSONArray().also { array ->
            dao.getBacktests(1000).forEach {
                array.put(JSONObject()
                    .put("symbol", it.symbol)
                    .put("strategyName", it.strategyName)
                    .put("createdAt", it.createdAt)
                    .put("totalReturn", it.totalReturn)
                    .put("maxDrawdown", it.maxDrawdown)
                    .put("winRate", it.winRate)
                    .put("tradeCount", it.tradeCount)
                    .put("equityCurve", JSONArray(it.equityCurve))
                    .put("latestSignal", it.latestSignal))
            }
        })
        return root.toString(2)
    }

    suspend fun restoreBackup(text: String) {
        val root = JSONObject(text)
        dao.upsertLotteryDraws(root.optJSONArray("lotteryDraws").toObjects {
            LotteryDraw(
                issue = getString("issue"),
                date = getString("date"),
                redBalls = getJSONArray("redBalls").toInts(),
                blueBall = getInt("blueBall")
            )
        })
        dao.insertPredictions(root.optJSONArray("predictions").toObjects {
            LotteryPrediction(
                createdAt = getLong("createdAt"),
                targetIssue = optString("targetIssue", ""),
                sourceIssue = optString("sourceIssue", ""),
                modelVersion = optString("modelVersion", "balanced_v2"),
                redBalls = getJSONArray("redBalls").toInts(),
                blueBalls = optJSONArray("blueBalls")?.toInts() ?: listOf(getInt("blueBall")),
                score = getDouble("score"),
                analysisSummary = optString("analysisSummary", ""),
                reasons = optString("reasons", ""),
                ballDetails = optString("ballDetails", ""),
                note = optString("note")
            )
        })
        dao.insertResearchSnapshots(root.optJSONArray("researchReports").toObjects {
            LotteryResearchSnapshot(
                createdAt = getLong("createdAt"),
                targetIssue = getString("targetIssue"),
                sourceIssue = optString("sourceIssue", ""),
                modelVersion = optString("modelVersion", "balanced_v2"),
                reportJson = optJSONObject("report")?.toString() ?: "{}"
            )
        })
        dao.upsertLotterySettlements(root.optJSONArray("lotterySettlements").toObjects {
            LotterySettlement(
                issue = getString("issue"),
                drawDate = getString("drawDate"),
                settledAt = getLong("settledAt"),
                betCount = getLong("betCount"),
                investedAmount = getDouble("investedAmount"),
                simulatedPrizeAmount = getDouble("simulatedPrizeAmount"),
                roi = getDouble("roi"),
                bestRedHits = getInt("bestRedHits"),
                blueHit = getBoolean("blueHit"),
                tierCountsJson = optJSONObject("tierCounts")?.toString() ?: "{}",
                detailJson = optJSONArray("details")?.toString() ?: "[]"
            )
        })
        dao.upsertSymbols(root.optJSONArray("symbols").toObjects {
            StockSymbol(
                symbol = getString("symbol"),
                name = getString("name"),
                market = optString("market", "CN"),
                lastUpdated = optString("lastUpdated").ifBlank { null }
            )
        })
        dao.upsertCandles(root.optJSONArray("candles").toObjects {
            StockCandle(
                symbol = getString("symbol"),
                date = getString("date"),
                open = getDouble("open"),
                high = getDouble("high"),
                low = getDouble("low"),
                close = getDouble("close"),
                volume = optDouble("volume")
            )
        })
        dao.insertBacktests(root.optJSONArray("backtests").toObjects {
            BacktestSnapshot(
                symbol = getString("symbol"),
                strategyName = getString("strategyName"),
                createdAt = getLong("createdAt"),
                totalReturn = getDouble("totalReturn"),
                maxDrawdown = getDouble("maxDrawdown"),
                winRate = getDouble("winRate"),
                tradeCount = getInt("tradeCount"),
                equityCurve = getJSONArray("equityCurve").toDoubles(),
                latestSignal = getString("latestSignal")
            )
        })
        settleResolvedPredictions()
    }

    private fun defaultStrategies(): List<QuantStrategy> = listOf(
        MovingAverageCrossStrategy(),
        RsiReversionStrategy(),
        MacdTrendStrategy(),
        BreakoutStrategy()
    )

    private fun <T> JSONArray?.toObjects(block: JSONObject.() -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { getJSONObject(it).block() }
    }

    private fun JSONArray.toInts(): List<Int> = (0 until length()).map { getInt(it) }

    private fun JSONArray.toDoubles(): List<Double> = (0 until length()).map { getDouble(it) }

    private fun JSONArray.toStrings(): List<String> = (0 until length()).map { getString(it) }

    private fun parseServerCandles(symbol: String, text: String): List<StockCandle> {
        val root = JSONObject(text)
        val candles = root.optJSONArray("candles") ?: return emptyList()
        return (0 until candles.length()).mapNotNull { index ->
            val item = candles.optJSONObject(index) ?: return@mapNotNull null
            val date = item.optString("date")
            if (date.isBlank()) return@mapNotNull null
            StockCandle(
                symbol = symbol,
                date = date,
                open = item.optDouble("open"),
                high = item.optDouble("high"),
                low = item.optDouble("low"),
                close = item.optDouble("close"),
                volume = item.optDouble("volume")
            )
        }.sortedBy { it.date }
    }

    private fun parseServerPredictions(text: String): List<LotteryPrediction> {
        val root = JSONObject(text)
        val predictions = root.optJSONArray("predictions") ?: return emptyList()
        val createdAt = System.currentTimeMillis()
        return (0 until predictions.length()).mapNotNull { index ->
            val item = predictions.optJSONObject(index) ?: return@mapNotNull null
            val reds = item.optJSONArray("redBalls")?.toInts().orEmpty()
            val blues = item.optJSONArray("blueBalls")?.toInts().orEmpty()
            if (reds.isEmpty() || blues.isEmpty()) return@mapNotNull null
            LotteryPrediction(
                createdAt = createdAt,
                targetIssue = item.optString("targetIssue"),
                sourceIssue = item.optString("sourceIssue"),
                modelVersion = item.optString("modelVersion", "balanced_v2"),
                redBalls = reds,
                blueBalls = blues,
                score = item.optDouble("score"),
                analysisSummary = item.optString("analysisSummary"),
                reasons = item.optJSONArray("reasons")?.toStrings()?.joinToString("\n").orEmpty(),
                ballDetails = item.optJSONArray("ballDetails")?.toString().orEmpty(),
                note = item.optString("note")
            )
        }
    }

    private fun parseResearchReport(text: String): LotteryResearchReport? =
        parseResearchReport(JSONObject(text))

    private fun parseStoredResearchReport(snapshot: LotteryResearchSnapshot): LotteryResearchReport? =
        runCatching { parseResearchReport(JSONObject(snapshot.reportJson)) }.getOrNull()

    private fun parseResearchReport(root: JSONObject): LotteryResearchReport? {
        val backtest = root.optJSONObject("backtest")?.let {
            LotteryBacktestReport(
                issueCount = it.optInt("issueCount"),
                averageBestRedHits = it.optDouble("averageBestRedHits"),
                blueHitRate = it.optDouble("blueHitRate"),
                atLeastThreeRedRate = it.optDouble("atLeastThreeRedRate")
            )
        }
        val comparison = root.optJSONArray("modelComparison").toObjects {
            LotteryModelComparison(
                version = getString("version"),
                averageBestRedHits = getDouble("averageBestRedHits"),
                blueHitRate = getDouble("blueHitRate"),
                atLeastThreeRedRate = getDouble("atLeastThreeRedRate")
            )
        }
        val budgetPlan = root.optJSONObject("budgetPlan")?.let {
            LotteryBudgetPlan(
                budgetBets = it.optInt("budgetBets"),
                redCount = it.optInt("redCount"),
                blueCount = it.optInt("blueCount"),
                betCount = it.optLong("betCount"),
                reason = it.optString("reason")
            )
        }
        val rankings = root.optJSONObject("numberRankings")
        return LotteryResearchReport(
            modelVersion = root.optString("modelVersion", "balanced_v2"),
            recentWindow = root.optInt("recentWindow", 120),
            backtest = backtest,
            modelComparison = comparison,
            budgetPlan = budgetPlan,
            narrative = root.optJSONArray("narrative")?.toStrings().orEmpty(),
            redRankings = rankings?.optJSONArray("reds").toObjects { toNumberRanking() },
            blueRankings = rankings?.optJSONArray("blues").toObjects { toNumberRanking() }
        )
    }

    private fun LotteryResearchReport.toJsonObject(): JSONObject =
        JSONObject()
            .put("modelVersion", modelVersion)
            .put("recentWindow", recentWindow)
            .put("backtest", backtest?.let {
                JSONObject()
                    .put("issueCount", it.issueCount)
                    .put("averageBestRedHits", it.averageBestRedHits)
                    .put("blueHitRate", it.blueHitRate)
                    .put("atLeastThreeRedRate", it.atLeastThreeRedRate)
            })
            .put("modelComparison", JSONArray().also { array ->
                modelComparison.forEach {
                    array.put(JSONObject()
                        .put("version", it.version)
                        .put("averageBestRedHits", it.averageBestRedHits)
                        .put("blueHitRate", it.blueHitRate)
                        .put("atLeastThreeRedRate", it.atLeastThreeRedRate))
                }
            })
            .put("budgetPlan", budgetPlan?.let {
                JSONObject()
                    .put("budgetBets", it.budgetBets)
                    .put("redCount", it.redCount)
                    .put("blueCount", it.blueCount)
                    .put("betCount", it.betCount)
                    .put("reason", it.reason)
            })
            .put("narrative", JSONArray(narrative))
            .put("numberRankings", JSONObject()
                .put("reds", JSONArray().also { array -> redRankings.forEach { array.put(it.toJsonObject()) } })
                .put("blues", JSONArray().also { array -> blueRankings.forEach { array.put(it.toJsonObject()) } }))

    private fun LotteryNumberRanking.toJsonObject(): JSONObject =
        JSONObject()
            .put("color", color)
            .put("number", number)
            .put("rank", rank)
            .put("totalScore", totalScore)
            .put("fullFrequencyScore", fullFrequencyScore)
            .put("recentScore", recentScore)
            .put("omissionScore", omissionScore)
            .put("centerBiasScore", centerBiasScore)
            .put("fullCount", fullCount)
            .put("recentWeighted", recentWeighted)
            .put("missCount", missCount)
            .put("recent30Count", recent30Count)
            .put("recent60Count", recent60Count)
            .put("recent120Count", recent120Count)
            .put("latestAppearances", JSONArray(latestAppearances))
            .put("summary", summary)

    private fun JSONObject.toNumberRanking(): LotteryNumberRanking =
        LotteryNumberRanking(
            color = getString("color"),
            number = getInt("number"),
            rank = getInt("rank"),
            totalScore = getDouble("totalScore"),
            fullFrequencyScore = getDouble("fullFrequencyScore"),
            recentScore = getDouble("recentScore"),
            omissionScore = getDouble("omissionScore"),
            centerBiasScore = getDouble("centerBiasScore"),
            fullCount = getInt("fullCount"),
            recentWeighted = getDouble("recentWeighted"),
            missCount = getInt("missCount"),
            recent30Count = getInt("recent30Count"),
            recent60Count = getInt("recent60Count"),
            recent120Count = getInt("recent120Count"),
            latestAppearances = optJSONArray("latestAppearances")?.toStrings().orEmpty(),
            summary = getString("summary")
        )

    private suspend fun settleResolvedPredictions() {
        val drawsByIssue = dao.getLotteryDraws().associateBy { it.issue }
        val predictionsByIssue = dao.getAllPredictions().groupBy { it.targetIssue }
        predictionsByIssue.forEach { (issue, predictions) ->
            val draw = drawsByIssue[issue] ?: return@forEach
            val settlement = LotterySettlementCalculator.buildTrackedCompoundSettlement(draw, predictions)
            if (settlement == null) {
                dao.deleteSettlementForIssue(issue)
            } else {
                dao.upsertLotterySettlements(listOf(settlement))
            }
        }
    }

    private fun buildSettlement(draw: LotteryDraw, predictions: List<LotteryPrediction>): LotterySettlement {
        val details = predictions.map { settlePrediction(it, draw) }
        val totalBetCount = details.sumOf { it.betCount }
        val totalPrize = details.sumOf { it.prizeAmount }
        val invested = totalBetCount * LOTTERY_BET_PRICE
        val tiers = linkedMapOf("first" to 0L, "second" to 0L, "third" to 0L, "fourth" to 0L, "fifth" to 0L, "sixth" to 0L)
        details.forEach { detail ->
            detail.tierCounts.forEach { (tier, count) -> tiers[tier] = (tiers[tier] ?: 0L) + count }
        }
        return LotterySettlement(
            issue = draw.issue,
            drawDate = draw.date,
            settledAt = System.currentTimeMillis(),
            betCount = totalBetCount,
            investedAmount = invested,
            simulatedPrizeAmount = totalPrize,
            roi = if (invested == 0.0) 0.0 else (totalPrize - invested) / invested,
            bestRedHits = details.maxOfOrNull { it.bestRedHits } ?: 0,
            blueHit = details.any { it.blueHit },
            tierCountsJson = JSONObject(tiers as Map<*, *>).toString(),
            detailJson = JSONArray().also { array -> details.forEach { array.put(it.toJsonObject()) } }.toString()
        )
    }

    private fun settlePrediction(prediction: LotteryPrediction, draw: LotteryDraw): LotterySettlementDetail {
        val redOverlap = prediction.redBalls.count { it in draw.redBalls }
        val nonHitReds = prediction.redBalls.size - redOverlap
        val hasWinningBlue = draw.blueBall in prediction.blueBalls
        val losingBlueCount = prediction.blueBalls.size - if (hasWinningBlue) 1 else 0
        val tierCounts = linkedMapOf("first" to 0L, "second" to 0L, "third" to 0L, "fourth" to 0L, "fifth" to 0L, "sixth" to 0L)
        for (redHits in 0..6) {
            val redCombinationCount = combination(redOverlap, redHits) * combination(nonHitReds, 6 - redHits)
            if (redCombinationCount == 0L) continue
            if (hasWinningBlue) addTierCount(tierCounts, redHits, blueHit = true, redCombinationCount)
            if (losingBlueCount > 0) addTierCount(tierCounts, redHits, blueHit = false, redCombinationCount * losingBlueCount)
        }
        val betCount = combination(prediction.redBalls.size, 6) * prediction.blueBalls.size
        val prize = (tierCounts["third"] ?: 0L) * 3000.0 +
            (tierCounts["fourth"] ?: 0L) * 200.0 +
            (tierCounts["fifth"] ?: 0L) * 10.0 +
            (tierCounts["sixth"] ?: 0L) * 5.0
        return LotterySettlementDetail(
            label = if (prediction.redBalls.size > 6 || prediction.blueBalls.size > 1) "复式" else "单式",
            redBalls = prediction.redBalls,
            blueBalls = prediction.blueBalls,
            betCount = betCount,
            bestRedHits = redOverlap.coerceAtMost(6),
            blueHit = hasWinningBlue,
            prizeAmount = prize,
            tierCounts = tierCounts
        )
    }

    private fun addTierCount(target: MutableMap<String, Long>, redHits: Int, blueHit: Boolean, count: Long) {
        val tier = when {
            redHits == 6 && blueHit -> "first"
            redHits == 6 -> "second"
            redHits == 5 && blueHit -> "third"
            redHits == 5 || redHits == 4 && blueHit -> "fourth"
            redHits == 4 || redHits == 3 && blueHit -> "fifth"
            blueHit && redHits in 0..2 -> "sixth"
            else -> null
        } ?: return
        target[tier] = (target[tier] ?: 0L) + count
    }

    private fun combination(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0
        if (k == 0 || k == n) return 1
        val useK = minOf(k, n - k)
        var result = 1L
        for (i in 1..useK) {
            result = result * (n - useK + i) / i
        }
        return result
    }

    private fun LotterySettlementDetail.toJsonObject(): JSONObject =
        JSONObject()
            .put("label", label)
            .put("redBalls", JSONArray(redBalls))
            .put("blueBalls", JSONArray(blueBalls))
            .put("betCount", betCount)
            .put("bestRedHits", bestRedHits)
            .put("blueHit", blueHit)
            .put("prizeAmount", prizeAmount)
            .put("tierCounts", JSONObject(tierCounts as Map<*, *>))

    private suspend fun fetchFirst(urls: List<String>): String {
        var lastError: Throwable? = null
        for (url in urls) {
            runCatching { return fetchText(url) }
                .onFailure { lastError = it }
        }
        throw IOException(lastError.toFriendlyNetworkMessage(), lastError)
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
            setRequestProperty("Referer", "https://www.cwl.gov.cn/")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("官网返回 HTTP $code")
            if (text.isBlank()) throw IOException("官网返回空内容")
            text
        } catch (error: SocketTimeoutException) {
            throw IOException("连接官网超时", error)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MIN_LOTTERY_SAMPLE_SIZE = 30
        private const val LOTTERY_BET_PRICE = 2.0

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

        private val CWL_SSQ_URLS = listOf(
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount=200&issueStart=&issueEnd=&dayStart=&dayEnd=&pageNo=1&pageSize=200&week=&systemType=PC",
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount=100&issueStart=&issueEnd=&dayStart=&dayEnd=&pageNo=1&pageSize=100&week=&systemType=PC",
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&pageNo=1&pageSize=100&systemType=PC",
            "http://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq&issueCount=100&issueStart=&issueEnd=&dayStart=&dayEnd=&pageNo=1&pageSize=100&week=&systemType=PC"
        )
    }

}

private fun Throwable?.toFriendlyNetworkMessage(): String {
    val message = this?.message.orEmpty()
    return when {
        this == null -> "网络请求失败"
        this is SocketTimeoutException -> "连接官网超时"
        "HTTP" in message -> message
        "Unable to resolve host" in message -> "无法解析官网域名，请检查网络"
        "Failed to connect" in message -> "无法连接中国福彩网"
        "timeout" in message.lowercase() -> "连接官网超时"
        message.startsWith("http://") || message.startsWith("https://") -> "官网接口请求失败"
        message.isBlank() -> "网络请求失败"
        else -> message.take(80)
    }
}
