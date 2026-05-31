package com.demo.wealth.domain.lottery

import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotteryPrediction
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class LotteryModelResult(
    val redScores: Map<Int, Double>,
    val blueScores: Map<Int, Double>,
    val sampleSize: Int,
    val hotReds: List<Int>,
    val coldReds: List<Int>,
    val hotBlues: List<Int>,
    val coldBlues: List<Int>,
    val overdueReds: List<Int>,
    val overdueBlues: List<Int>
)

data class LotteryHitResult(
    val issue: String,
    val redHits: Int,
    val blueHit: Boolean
)

object LotteryRules {
    fun isValid(redBalls: List<Int>, blueBall: Int): Boolean =
        redBalls.size == 6 &&
            redBalls.distinct().size == 6 &&
            redBalls.all { it in 1..33 } &&
            blueBall in 1..16

    fun isValidCompound(redBalls: List<Int>, blueBalls: List<Int>): Boolean =
        redBalls.size in 6..20 &&
            redBalls.distinct().size == redBalls.size &&
            redBalls.all { it in 1..33 } &&
            blueBalls.size in 1..16 &&
            blueBalls.distinct().size == blueBalls.size &&
            blueBalls.all { it in 1..16 }

    fun combinationCount(n: Int, k: Int): Long {
        if (k < 0 || n < k) return 0
        val realK = minOf(k, n - k)
        var result = 1L
        for (i in 1..realK) {
            result = result * (n - realK + i) / i
        }
        return result
    }
}

object LotteryIssues {
    fun next(issue: String): String {
        val value = issue.toLongOrNull() ?: return "下一期"
        return (value + 1).toString().padStart(issue.length, '0')
    }
}

class LotteryFeatureModel {
    fun train(draws: List<LotteryDraw>): LotteryModelResult {
        val sorted = draws.sortedBy { it.date }
        val redFrequency = (1..33).associateWith { number ->
            sorted.count { number in it.redBalls }
        }
        val blueFrequency = (1..16).associateWith { number ->
            sorted.count { number == it.blueBall }
        }
        val redMiss = (1..33).associateWith { number ->
            sorted.asReversed().indexOfFirst { number in it.redBalls }.let { if (it < 0) sorted.size else it }
        }
        val blueMiss = (1..16).associateWith { number ->
            sorted.asReversed().indexOfFirst { number == it.blueBall }.let { if (it < 0) sorted.size else it }
        }

        val redScores = (1..33).associateWith { number ->
            val freqScore = normalize(redFrequency[number] ?: 0, 0, max(1, sorted.size / 3))
            val missScore = normalize(redMiss[number] ?: sorted.size, 0, max(1, sorted.size / 2))
            val centerBias = 1.0 - abs(number - 17) / 17.0 * 0.12
            (0.55 * freqScore + 0.35 * missScore + 0.10 * centerBias).coerceAtLeast(0.01)
        }
        val blueScores = (1..16).associateWith { number ->
            val freqScore = normalize(blueFrequency[number] ?: 0, 0, max(1, sorted.size / 8))
            val missScore = normalize(blueMiss[number] ?: sorted.size, 0, max(1, sorted.size / 2))
            (0.6 * freqScore + 0.4 * missScore).coerceAtLeast(0.01)
        }
        return LotteryModelResult(
            redScores = redScores,
            blueScores = blueScores,
            sampleSize = sorted.size,
            hotReds = redScores.entries.sortedByDescending { it.value }.take(8).map { it.key },
            coldReds = redScores.entries.sortedBy { it.value }.take(8).map { it.key },
            hotBlues = blueScores.entries.sortedByDescending { it.value }.take(5).map { it.key },
            coldBlues = blueScores.entries.sortedBy { it.value }.take(5).map { it.key },
            overdueReds = redMiss.entries.sortedByDescending { it.value }.take(8).map { it.key },
            overdueBlues = blueMiss.entries.sortedByDescending { it.value }.take(5).map { it.key }
        )
    }

    private fun normalize(value: Int, min: Int, max: Int): Double {
        if (max <= min) return 0.5
        return ((value - min).toDouble() / (max - min).toDouble()).coerceIn(0.0, 1.0)
    }
}

class LotteryGenerator(
    private val random: Random = Random.Default
) {
    fun generateWithCompound(
        model: LotteryModelResult,
        singleCount: Int = 5,
        compoundCount: Int = 2,
        compoundRedCount: Int = 9,
        compoundBlueCount: Int = 3,
        targetIssue: String,
        sourceIssue: String,
        createdAt: Long = System.currentTimeMillis()
    ): List<LotteryPrediction> =
        generate(model, singleCount, targetIssue, sourceIssue, createdAt) +
            generateCompound(model, compoundCount, compoundRedCount, compoundBlueCount, targetIssue, sourceIssue, createdAt)

    fun generate(
        model: LotteryModelResult,
        count: Int = 5,
        targetIssue: String = "",
        sourceIssue: String = "",
        createdAt: Long = System.currentTimeMillis()
    ): List<LotteryPrediction> {
        if (model.sampleSize == 0) return emptyList()
        return buildList {
            var guard = 0
            while (size < count && guard < count * 80) {
                guard++
                val reds = weightedSample(model.redScores, 6).sorted()
                val blue = weightedPick(model.blueScores)
                if (LotteryRules.isValid(reds, blue) && passesShapeConstraints(reds)) {
                    add(
                        LotteryPrediction(
                            createdAt = createdAt,
                            targetIssue = targetIssue,
                            sourceIssue = sourceIssue,
                            redBalls = reds,
                            blueBalls = listOf(blue),
                            score = reds.sumOf { model.redScores[it] ?: 0.0 } + (model.blueScores[blue] ?: 0.0),
                            analysisSummary = model.summaryText(),
                            note = "单式；样本${model.sampleSize}期；热号${model.hotReds.joinToString(" ")}"
                        )
                    )
                }
            }
            addScoreRankedSingles(model, count - size, targetIssue, sourceIssue, createdAt)
        }.distinctBy { it.redBalls to it.blueBalls }.take(count)
    }

    fun generateCompound(
        model: LotteryModelResult,
        count: Int = 2,
        redCount: Int = 9,
        blueCount: Int = 3,
        targetIssue: String = "",
        sourceIssue: String = "",
        createdAt: Long = System.currentTimeMillis()
    ): List<LotteryPrediction> {
        val safeRedCount = redCount.coerceIn(6, 20)
        val safeBlueCount = blueCount.coerceIn(1, 16)
        if (model.sampleSize == 0) return emptyList()
        return buildList {
            var guard = 0
            while (size < count && guard < count * 80) {
                guard++
                val reds = weightedSample(model.redScores, safeRedCount).sorted()
                val blues = weightedSample(model.blueScores, safeBlueCount).sorted()
                if (LotteryRules.isValidCompound(reds, blues) && passesCompoundShapeConstraints(reds)) {
                    val betCount = LotteryRules.combinationCount(reds.size, 6) * blues.size
                    add(
                        LotteryPrediction(
                            createdAt = createdAt,
                            targetIssue = targetIssue,
                            sourceIssue = sourceIssue,
                            redBalls = reds,
                            blueBalls = blues,
                            score = reds.sumOf { model.redScores[it] ?: 0.0 } + blues.sumOf { model.blueScores[it] ?: 0.0 },
                            analysisSummary = model.summaryText(),
                            note = "复式${reds.size}+${blues.size}；约${betCount}注；样本${model.sampleSize}期"
                        )
                    )
                }
            }
            addScoreRankedCompound(model, count - size, safeRedCount, safeBlueCount, targetIssue, sourceIssue, createdAt)
        }.distinctBy { it.redBalls to it.blueBalls }.take(count)
    }

    private fun MutableList<LotteryPrediction>.addScoreRankedSingles(
        model: LotteryModelResult,
        count: Int,
        targetIssue: String,
        sourceIssue: String,
        createdAt: Long
    ) {
        if (count <= 0) return
        val targetSize = size + count
        val redPool = model.redScores.entries.sortedByDescending { it.value }.map { it.key }
        val bluePool = model.blueScores.entries.sortedByDescending { it.value }.map { it.key }
        var offset = 0
        while (size < targetSize && offset < 12) {
            val reds = redPool.drop(offset).take(6).sorted()
            val blue = bluePool[offset % bluePool.size]
            if (LotteryRules.isValid(reds, blue)) {
                add(
                    LotteryPrediction(
                        createdAt = createdAt,
                        targetIssue = targetIssue,
                        sourceIssue = sourceIssue,
                        redBalls = reds,
                        blueBalls = listOf(blue),
                        score = reds.sumOf { model.redScores[it] ?: 0.0 } + (model.blueScores[blue] ?: 0.0),
                        analysisSummary = model.summaryText(),
                        note = "单式；评分排序补足；样本${model.sampleSize}期"
                    )
                )
            }
            offset++
        }
    }

    private fun MutableList<LotteryPrediction>.addScoreRankedCompound(
        model: LotteryModelResult,
        count: Int,
        redCount: Int,
        blueCount: Int,
        targetIssue: String,
        sourceIssue: String,
        createdAt: Long
    ) {
        if (count <= 0) return
        val targetSize = size + count
        val redPool = model.redScores.entries.sortedByDescending { it.value }.map { it.key }
        val bluePool = model.blueScores.entries.sortedByDescending { it.value }.map { it.key }
        var offset = 0
        while (size < targetSize && offset < 12) {
            val reds = redPool.drop(offset).take(redCount).sorted()
            val blues = bluePool.drop(offset % 4).take(blueCount).sorted()
            if (LotteryRules.isValidCompound(reds, blues)) {
                val bets = LotteryRules.combinationCount(reds.size, 6) * blues.size
                add(
                    LotteryPrediction(
                        createdAt = createdAt,
                        targetIssue = targetIssue,
                        sourceIssue = sourceIssue,
                        redBalls = reds,
                        blueBalls = blues,
                        score = reds.sumOf { model.redScores[it] ?: 0.0 } + blues.sumOf { model.blueScores[it] ?: 0.0 },
                        analysisSummary = model.summaryText(),
                        note = "复式${reds.size}+${blues.size}；约${bets}注；评分排序补足"
                    )
                )
            }
            offset++
        }
    }

    private fun weightedSample(scores: Map<Int, Double>, count: Int): List<Int> {
        val picked = mutableSetOf<Int>()
        while (picked.size < count) picked += weightedPick(scores)
        return picked.toList()
    }

    private fun weightedPick(scores: Map<Int, Double>): Int {
        val total = scores.values.sum().coerceAtLeast(0.01)
        var cursor = random.nextDouble(total)
        for ((number, score) in scores) {
            cursor -= score
            if (cursor <= 0.0) return number
        }
        return scores.keys.last()
    }

    private fun passesShapeConstraints(reds: List<Int>): Boolean {
        val oddCount = reds.count { it % 2 == 1 }
        val sum = reds.sum()
        val zones = listOf(1..11, 12..22, 23..33).map { zone -> reds.count { it in zone } }
        val consecutivePairs = reds.zipWithNext().count { (a, b) -> b - a == 1 }
        return oddCount in 2..4 &&
            sum in 70..145 &&
            zones.all { it <= 4 } &&
            consecutivePairs <= 2
    }

    private fun passesCompoundShapeConstraints(reds: List<Int>): Boolean {
        val oddCount = reds.count { it % 2 == 1 }
        val zones = listOf(1..11, 12..22, 23..33).map { zone -> reds.count { it in zone } }
        val minOdd = max(2, reds.size / 3)
        val maxOdd = minOf(reds.size - 1, reds.size * 2 / 3 + 1)
        return oddCount in minOdd..maxOdd && zones.all { it >= 1 }
    }

    private fun LotteryModelResult.summaryText(): String =
        "样本${sampleSize}期；红球热号${hotReds.take(6).joinToString(" ")}；红球遗漏${overdueReds.take(6).joinToString(" ")}；蓝球热号${hotBlues.take(3).joinToString(" ")}"
}

class LotteryBacktester {
    fun compare(prediction: LotteryPrediction, actual: LotteryDraw): LotteryHitResult =
        LotteryHitResult(
            issue = actual.issue,
            redHits = prediction.redBalls.count { it in actual.redBalls },
            blueHit = actual.blueBall in prediction.blueBalls
        )
}
