package com.demo.wealth.data

data class LotteryResearchReport(
    val modelVersion: String,
    val recentWindow: Int,
    val backtest: LotteryBacktestReport?,
    val narrative: List<String>,
    val redRankings: List<LotteryNumberRanking>,
    val blueRankings: List<LotteryNumberRanking>
)

data class LotteryBacktestReport(
    val issueCount: Int,
    val averageBestRedHits: Double,
    val blueHitRate: Double,
    val atLeastThreeRedRate: Double,
    val prizeHitRate: Double = 0.0,
    val averageBetCount: Double = 0.0,
    val averageDistinctBlueCount: Double = 0.0
)

data class LotteryRecommendationResponse(
    val predictions: List<LotteryPrediction>,
    val report: LotteryResearchReport?
)

data class LotterySettlementDetail(
    val label: String,
    val redBalls: List<Int>,
    val blueBalls: List<Int>,
    val betCount: Long,
    val bestRedHits: Int,
    val blueHit: Boolean,
    val prizeAmount: Double,
    val tierCounts: Map<String, Long>
)

data class LotteryBallDetail(
    val color: String,
    val number: Int,
    val totalScore: Double,
    val fullFrequencyScore: Double,
    val recentScore: Double,
    val omissionScore: Double,
    val centerBiasScore: Double,
    val fullCount: Int,
    val recentWeighted: Double,
    val missCount: Int,
    val reasons: List<String>
)

data class LotteryNumberRanking(
    val color: String,
    val number: Int,
    val rank: Int,
    val totalScore: Double,
    val fullFrequencyScore: Double,
    val recentScore: Double,
    val omissionScore: Double,
    val centerBiasScore: Double,
    val fullCount: Int,
    val recentWeighted: Double,
    val missCount: Int,
    val recent30Count: Int,
    val recent60Count: Int,
    val recent120Count: Int,
    val latestAppearances: List<String>,
    val summary: String
)
