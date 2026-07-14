package com.demo.wealth.ui

import com.demo.wealth.data.LotteryBallDetail
import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotterySettlementDetail
import com.demo.wealth.data.FootballRecommendationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * UI 工具函数 - 格式化与数据解析
 *
 * 从 MainActivity.kt 提取，供所有 Screen 共用
 */

// ===== 格式化 =====

fun percent(value: Double): String = "${"%.2f".format(value * 100)}%"

fun signedPercent(value: Double): String = "${if (value >= 0) "+" else ""}${"%.2f".format(value * 100)}%"

fun money(value: Double): String = "¥${"%.2f".format(value)}"

fun nextIssueLabel(issue: String): String =
    issue.toLongOrNull()?.let { (it + 1).toString().padStart(issue.length, '0') } ?: "下一期"

fun expectedSsqDrawDate(sourceDate: String): String? {
    val parsed = runCatching { LocalDate.parse(sourceDate) }.getOrNull() ?: return null
    return generateSequence(parsed.plusDays(1)) { it.plusDays(1) }
        .first { it.dayOfWeek in setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY) }
        .toString()
}

fun nextVisibleCount(current: Int, total: Int, pageSize: Int): Int =
    (current + pageSize).coerceAtMost(total)

fun latestRecommendationRun(
    recommendations: List<FootballRecommendationEntity>
): List<FootballRecommendationEntity> {
    val latestCreatedAt = recommendations.maxOfOrNull { it.createdAt } ?: return emptyList()
    return recommendations.filter { it.createdAt == latestCreatedAt }
}

fun parseHadOdds(poolsJson: String): List<Pair<String, Double>> {
    val had = runCatching { JSONObject(poolsJson).optJSONObject("had") }.getOrNull() ?: return emptyList()
    return listOf("主胜" to "H", "平" to "D", "客胜" to "A").mapNotNull { (label, key) ->
        had.optDouble(key).takeIf { !it.isNaN() && it > 0.0 }?.let { label to it }
    }.takeIf { it.size == 3 }.orEmpty()
}

// ===== 号码统计 =====

fun frequentNumbers(draws: List<LotteryDraw>, range: IntRange, red: Boolean): List<Int> =
    range.map { number ->
        val count = draws.count { draw -> if (red) number in draw.redBalls else number == draw.blueBall }
        number to count
    }.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).map { it.first }

fun overdueNumbers(draws: List<LotteryDraw>, range: IntRange, red: Boolean): List<Int> {
    val ordered = draws.sortedWith(compareByDescending<LotteryDraw> { it.issue.toLongOrNull() ?: 0L }.thenByDescending { it.date })
    return range.map { number ->
        val miss = ordered.indexOfFirst { draw -> if (red) number in draw.redBalls else number == draw.blueBall }.let { if (it < 0) ordered.size else it }
        number to miss
    }.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).map { it.first }
}

fun groupSettlementsByIssue(settlements: List<com.demo.wealth.data.LotterySettlement>): List<List<com.demo.wealth.data.LotterySettlement>> =
    settlements.distinctBy { it.issue to it.detailJson }.groupBy { it.issue }.values.toList()

// ===== JSON 解析 =====

fun parseBallDetails(text: String): List<LotteryBallDetail> {
    if (text.isBlank()) return emptyList()
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        LotteryBallDetail(
            color = item.optString("color"),
            number = item.optInt("number"),
            totalScore = item.optDouble("totalScore"),
            fullFrequencyScore = item.optDouble("fullFrequencyScore"),
            recentScore = item.optDouble("recentScore"),
            omissionScore = item.optDouble("omissionScore"),
            centerBiasScore = item.optDouble("centerBiasScore"),
            fullCount = item.optInt("fullCount"),
            recentWeighted = item.optDouble("recentWeighted"),
            missCount = item.optInt("missCount"),
            reasons = item.optJSONArray("reasons")?.let { reasons ->
                (0 until reasons.length()).map { reasons.optString(it) }
            }.orEmpty()
        )
    }
}

fun parseStringArray(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
}

fun parseSettlementDetails(text: String): List<LotterySettlementDetail> {
    if (text.isBlank()) return emptyList()
    val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val tiers = item.optJSONObject("tierCounts")
        LotterySettlementDetail(
            label = item.optString("label"),
            redBalls = item.optJSONArray("redBalls")?.let { reds -> (0 until reds.length()).map { reds.optInt(it) } }.orEmpty(),
            blueBalls = item.optJSONArray("blueBalls")?.let { blues -> (0 until blues.length()).map { blues.optInt(it) } }.orEmpty(),
            betCount = item.optLong("betCount"),
            bestRedHits = item.optInt("bestRedHits"),
            blueHit = item.optBoolean("blueHit"),
            prizeAmount = item.optDouble("prizeAmount"),
            tierCounts = if (tiers == null) emptyMap() else tiers.keys().asSequence().associateWith { tiers.optLong(it) }
        )
    }
}
