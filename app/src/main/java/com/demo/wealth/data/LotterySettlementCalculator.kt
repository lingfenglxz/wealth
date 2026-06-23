package com.demo.wealth.data

import org.json.JSONArray
import org.json.JSONObject

object LotterySettlementCalculator {
    private const val LOTTERY_BET_PRICE = 2.0
    private const val MIN_FORTUNE_ISSUE = 2026014L

    fun buildTrackedCompoundSettlement(
        draw: LotteryDraw,
        predictions: List<LotteryPrediction>,
        runId: String = predictions.firstOrNull()?.runId.orEmpty(),
        settledAt: Long = System.currentTimeMillis()
    ): LotterySettlement? {
        val trackedPrediction = predictions
            .filter { it.redBalls.size > 6 || it.blueBalls.size > 1 }
            .maxByOrNull { it.score }
            ?: return null
        val detail = settlePrediction(trackedPrediction, draw)
        val invested = detail.betCount * LOTTERY_BET_PRICE
        return LotterySettlement(
            issue = draw.issue,
            runId = runId,
            drawDate = draw.date,
            settledAt = settledAt,
            betCount = detail.betCount,
            investedAmount = invested,
            simulatedPrizeAmount = detail.prizeAmount,
            roi = if (invested == 0.0) 0.0 else (detail.prizeAmount - invested) / invested,
            bestRedHits = detail.bestRedHits,
            blueHit = detail.blueHit,
            tierCountsJson = JSONObject(detail.tierCounts as Map<*, *>).toString(),
            detailJson = JSONArray().put(detail.toJsonObject()).toString()
        )
    }

    private fun settlePrediction(prediction: LotteryPrediction, draw: LotteryDraw): LotterySettlementDetail {
        val redOverlap = prediction.redBalls.count { it in draw.redBalls }
        val nonHitReds = prediction.redBalls.size - redOverlap
        val hasWinningBlue = draw.blueBall in prediction.blueBalls
        val fortuneEnabled = (draw.issue.toLongOrNull() ?: MIN_FORTUNE_ISSUE) >= MIN_FORTUNE_ISSUE
        val losingBlueCount = prediction.blueBalls.size - if (hasWinningBlue) 1 else 0
        val tierCounts = linkedMapOf(
            "first" to 0L,
            "second" to 0L,
            "third" to 0L,
            "fourth" to 0L,
            "fifth" to 0L,
            "sixth" to 0L,
            "fortune" to 0L
        )
        for (redHits in 0..6) {
            val redCombinationCount = combination(redOverlap, redHits) * combination(nonHitReds, 6 - redHits)
            if (redCombinationCount == 0L) continue
            if (hasWinningBlue) addTierCount(tierCounts, redHits, blueHit = true, redCombinationCount, fortuneEnabled)
            if (losingBlueCount > 0) addTierCount(tierCounts, redHits, blueHit = false, redCombinationCount * losingBlueCount, fortuneEnabled)
        }
        val betCount = combination(prediction.redBalls.size, 6) * prediction.blueBalls.size
        val prize = (tierCounts["third"] ?: 0L) * 3000.0 +
            (tierCounts["fourth"] ?: 0L) * 200.0 +
            (tierCounts["fifth"] ?: 0L) * 10.0 +
            (tierCounts["sixth"] ?: 0L) * 5.0 +
            (tierCounts["fortune"] ?: 0L) * 5.0
        return LotterySettlementDetail(
            label = "复式",
            redBalls = prediction.redBalls,
            blueBalls = prediction.blueBalls,
            betCount = betCount,
            bestRedHits = redOverlap.coerceAtMost(6),
            blueHit = hasWinningBlue,
            prizeAmount = prize,
            tierCounts = tierCounts
        )
    }

    private fun addTierCount(target: MutableMap<String, Long>, redHits: Int, blueHit: Boolean, count: Long, fortuneEnabled: Boolean) {
        val tier = when {
            redHits == 6 && blueHit -> "first"
            redHits == 6 -> "second"
            redHits == 5 && blueHit -> "third"
            redHits == 5 || redHits == 4 && blueHit -> "fourth"
            redHits == 4 || redHits == 3 && blueHit -> "fifth"
            blueHit && redHits in 0..2 -> "sixth"
            fortuneEnabled && redHits == 3 -> "fortune"
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
}
