package com.demo.wealth.data

import android.content.Context
import com.demo.wealth.domain.lottery.LotteryOfficialParser
import com.demo.wealth.domain.lottery.LotteryServerStateParser
import com.demo.wealth.domain.sports.FootballLotteryParser
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

    val lotteryDraws: Flow<List<LotteryDraw>> = dao.observeLotteryDraws()
    val lotteryPredictions: Flow<List<LotteryPrediction>> = dao.observePredictions()
    val lotteryPredictionHistory: Flow<List<LotteryPrediction>> = dao.observeAllPredictions()
    val lotterySettlements: Flow<List<LotterySettlement>> = dao.observeLotterySettlements()
    val lotteryResearchReport: Flow<LotteryResearchReport?> =
        dao.observeLatestResearchSnapshot().map { it?.let(::parseStoredResearchReport) }
    val footballMatches: Flow<List<FootballMatchEntity>> = dao.observeFootballMatches()
    val footballRecommendations: Flow<List<FootballRecommendationEntity>> = dao.observeFootballRecommendations()
    val footballRecommendationHistory: Flow<List<FootballRecommendationEntity>> = dao.observeFootballRecommendationHistory()

    suspend fun refreshLotterySettlements() {
        settleResolvedPredictions()
    }

    suspend fun updateLotteryFromServer(baseUrl: String): Int {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText("$normalized/api/lottery/ssq/draws?limit=3000&refresh=true")
        val draws = LotteryOfficialParser.parseServerJson(text)
        if (draws.isEmpty()) throw IOException("服务端返回数据为空")
        dao.upsertLotteryDraws(draws)
        restoreMissingLotteryStateFromServer(text)
        settleResolvedPredictions()
        return draws.size
    }

    suspend fun generateLotteryPredictionsFromServer(
        baseUrl: String,
        singleCount: Int = 0,
        compoundCount: Int = 1,
        compoundRedCount: Int = 6,
        compoundBlueCount: Int = 3,
        modelVersion: String = "auto",
        recentWindow: Int = 240,
        budgetBets: Int = 252
    ): LotteryRecommendationResponse {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText(
            normalized + buildSsqRecommendationPath(
                singleCount = singleCount,
                compoundCount = compoundCount,
                compoundRedCount = compoundRedCount,
                compoundBlueCount = compoundBlueCount,
                modelVersion = modelVersion,
                recentWindow = recentWindow,
                budgetBets = budgetBets
            )
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

    private suspend fun restoreMissingLotteryStateFromServer(text: String) {
        val state = LotteryServerStateParser.parse(text)
        if (state.predictions.isNotEmpty() && dao.getAllPredictions().isEmpty()) {
            dao.insertPredictions(state.predictions)
        }
        if (state.researchSnapshots.isNotEmpty() && dao.getResearchSnapshots(1).isEmpty()) {
            dao.insertResearchSnapshots(state.researchSnapshots)
        }
        if (state.settlements.isNotEmpty() && dao.getLotterySettlements(1).isEmpty()) {
            dao.upsertLotterySettlements(state.settlements)
        }
    }

    suspend fun updateFootballMatchesFromServer(baseUrl: String, refresh: Boolean = true): Int {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText("$normalizedBaseUrl/api/lottery/sports/football/matches?refresh=$refresh")
        val matches = FootballLotteryParser.parseMatches(text)
        if (matches.isEmpty()) throw IOException("服务端未返回足球赛事")
        dao.upsertFootballMatches(matches)
        return matches.size
    }

    suspend fun importFootballJson(text: String): Int {
        val matches = FootballLotteryParser.parseMatches(text)
        if (matches.isNotEmpty()) dao.upsertFootballMatches(matches)
        return matches.size
    }

    suspend fun generateFootballRecommendationsFromServer(baseUrl: String, playType: String = "all"): Int {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) throw IOException("请先填写服务端地址")
        val text = fetchText("$normalizedBaseUrl/api/lottery/sports/football/recommendations?playType=$playType")
        val matches = FootballLotteryParser.parseMatches(text)
        val recommendations = FootballLotteryParser.parseRecommendations(text)
        if (matches.isNotEmpty()) dao.upsertFootballMatches(matches)
        if (recommendations.isEmpty()) throw IOException("服务端未返回足球推荐")
        dao.insertFootballRecommendations(recommendations)
        return recommendations.size
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
        root.put("footballMatches", JSONArray().also { array ->
            dao.getFootballMatches().forEach {
                array.put(JSONObject()
                    .put("matchId", it.matchId)
                    .put("matchNum", it.matchNum)
                    .put("leagueName", it.leagueName)
                    .put("phase", it.phase)
                    .put("kickoffTime", it.kickoffTime)
                    .put("homeTeam", it.homeTeam)
                    .put("awayTeam", it.awayTeam)
                    .put("handicap", it.handicap)
                    .put("pools", JSONObject(it.poolsJson))
                    .put("stadium", it.stadium)
                    .put("city", it.city)
                    .put("source", it.source)
                    .put("updatedAt", it.updatedAt))
            }
        })
        root.put("footballRecommendations", JSONArray().also { array ->
            dao.getFootballRecommendations().forEach {
                array.put(JSONObject()
                    .put("createdAt", it.createdAt)
                    .put("matchId", it.matchId)
                    .put("matchNum", it.matchNum)
                    .put("leagueName", it.leagueName)
                    .put("phase", it.phase)
                    .put("kickoffTime", it.kickoffTime)
                    .put("homeTeam", it.homeTeam)
                    .put("awayTeam", it.awayTeam)
                    .put("playType", it.playType)
                    .put("playName", it.playName)
                    .put("modelName", it.modelName)
                    .put("selection", it.selection)
                    .put("odds", it.odds)
                    .put("confidence", it.confidence)
                    .put("fairProbability", it.fairProbability)
                    .put("modelProbability", it.modelProbability)
                    .put("edge", it.edge)
                    .put("dataQuality", it.dataQuality)
                    .put("expectedGoals", JSONObject().put("home", it.homeExpectedGoals).put("away", it.awayExpectedGoals))
                    .put("reasons", JSONArray(it.reasonsJson)))
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
                modelVersion = optString("modelVersion", "recent_focus_v3"),
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
                modelVersion = optString("modelVersion", "recent_focus_v3"),
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
        root.optJSONArray("footballMatches")?.let { matches ->
            importFootballJson(JSONObject().put("matches", matches).toString())
        }
        root.optJSONArray("footballRecommendations")?.let { recommendations ->
            dao.clearFootballRecommendations()
            dao.insertFootballRecommendations(
                FootballLotteryParser.parseRecommendations(JSONObject().put("recommendations", recommendations).toString())
            )
        }
        settleResolvedPredictions()
    }

    private fun <T> JSONArray?.toObjects(block: JSONObject.() -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { getJSONObject(it).block() }
    }

    private fun JSONArray.toInts(): List<Int> = (0 until length()).map { getInt(it) }

    private fun JSONArray.toStrings(): List<String> = (0 until length()).map { getString(it) }

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
                modelVersion = item.optString("modelVersion", "recent_focus_v3"),
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
                atLeastThreeRedRate = it.optDouble("atLeastThreeRedRate"),
                prizeHitRate = it.optDouble("prizeHitRate"),
                averageBetCount = it.optDouble("averageBetCount"),
                averageDistinctBlueCount = it.optDouble("averageDistinctBlueCount")
            )
        }
        val comparison = root.optJSONArray("modelComparison").toObjects {
            LotteryModelComparison(
                version = getString("version"),
                averageBestRedHits = getDouble("averageBestRedHits"),
                blueHitRate = getDouble("blueHitRate"),
                atLeastThreeRedRate = getDouble("atLeastThreeRedRate"),
                prizeHitRate = optDouble("prizeHitRate"),
                averageBetCount = optDouble("averageBetCount"),
                averageDistinctBlueCount = optDouble("averageDistinctBlueCount")
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
            modelVersion = root.optString("modelVersion", "recent_focus_v3"),
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
                    .put("prizeHitRate", it.prizeHitRate)
                    .put("averageBetCount", it.averageBetCount)
                    .put("averageDistinctBlueCount", it.averageDistinctBlueCount)
            })
            .put("modelComparison", JSONArray().also { array ->
                modelComparison.forEach {
                    array.put(JSONObject()
                        .put("version", it.version)
                        .put("averageBestRedHits", it.averageBestRedHits)
                        .put("blueHitRate", it.blueHitRate)
                        .put("atLeastThreeRedRate", it.atLeastThreeRedRate)
                        .put("prizeHitRate", it.prizeHitRate)
                        .put("averageBetCount", it.averageBetCount)
                        .put("averageDistinctBlueCount", it.averageDistinctBlueCount))
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
    }

}

internal fun buildSsqRecommendationPath(
    singleCount: Int = 0,
    compoundCount: Int = 1,
    compoundRedCount: Int = 6,
    compoundBlueCount: Int = 3,
    modelVersion: String = "auto",
    recentWindow: Int = 240,
    budgetBets: Int = 252,
    limit: Int = 3000
): String =
    "/api/lottery/ssq/recommendations" +
        "?singleCount=$singleCount&compoundCount=$compoundCount&redCount=$compoundRedCount&blueCount=$compoundBlueCount" +
        "&modelVersion=$modelVersion&recentWindow=$recentWindow&budgetBets=$budgetBets&limit=$limit"
