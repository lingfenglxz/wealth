package com.demo.wealth.domain.lottery

import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotteryResearchSnapshot
import com.demo.wealth.data.LotterySettlement
import org.json.JSONArray
import org.json.JSONObject

data class LotteryServerState(
    val predictions: List<LotteryPrediction>,
    val researchSnapshots: List<LotteryResearchSnapshot>,
    val settlements: List<LotterySettlement>
)

object LotteryServerStateParser {
    fun parse(text: String): LotteryServerState {
        val root = JSONObject(text)
        val state = root.optJSONObject("stateBackup") ?: return LotteryServerState(emptyList(), emptyList(), emptyList())
        val createdAt = System.currentTimeMillis()
        return LotteryServerState(
            predictions = state.optJSONArray("predictions").toObjects {
                LotteryPrediction(
                    createdAt = createdAt,
                    targetIssue = optString("targetIssue"),
                    sourceIssue = optString("sourceIssue"),
                    modelVersion = optString("modelVersion", "recent_focus_v3"),
                    redBalls = optJSONArray("redBalls").toInts(),
                    blueBalls = optJSONArray("blueBalls").toInts(),
                    score = optDouble("score"),
                    analysisSummary = optString("analysisSummary"),
                    reasons = optJSONArray("reasons")?.toStrings()?.joinToString("\n") ?: optString("reasons"),
                    ballDetails = optJSONArray("ballDetails")?.toString() ?: optString("ballDetails"),
                    note = optString("note")
                )
            },
            researchSnapshots = state.optJSONArray("researchReports").toObjects {
                val report = optJSONObject("report") ?: JSONObject()
                LotteryResearchSnapshot(
                    createdAt = createdAt,
                    targetIssue = optString("targetIssue"),
                    sourceIssue = optString("sourceIssue"),
                    modelVersion = optString("modelVersion", report.optString("modelVersion", "recent_focus_v3")),
                    reportJson = report.toString()
                )
            },
            settlements = state.optJSONArray("lotterySettlements").toObjects {
                LotterySettlement(
                    issue = optString("issue"),
                    drawDate = optString("drawDate"),
                    settledAt = optLong("settledAt", createdAt),
                    betCount = optLong("betCount"),
                    investedAmount = optDouble("investedAmount"),
                    simulatedPrizeAmount = optDouble("simulatedPrizeAmount"),
                    roi = optDouble("roi"),
                    bestRedHits = optInt("bestRedHits"),
                    blueHit = optBoolean("blueHit"),
                    tierCountsJson = optJSONObject("tierCounts")?.toString() ?: "{}",
                    detailJson = optJSONArray("details")?.toString() ?: "[]"
                )
            }
        )
    }

    private fun <T> JSONArray?.toObjects(block: JSONObject.() -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index)?.block() }
    }

    private fun JSONArray?.toInts(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).map { optInt(it) }
    }

    private fun JSONArray.toStrings(): List<String> = (0 until length()).map { optString(it) }
}
