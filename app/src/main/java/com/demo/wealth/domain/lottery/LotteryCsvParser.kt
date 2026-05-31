package com.demo.wealth.domain.lottery

import com.demo.wealth.data.LotteryDraw

object LotteryCsvParser {
    fun parse(text: String): List<LotteryDraw> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .dropWhile { it.contains("issue", ignoreCase = true) || it.contains("期号") }
            .mapNotNull(::parseLine)
            .toList()

    private fun parseLine(line: String): LotteryDraw? {
        val parts = line.split(",", "\t", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 9) return null
        val issue = parts[0]
        val date = parts[1]
        val reds = parts.drop(2).take(6).mapNotNull { it.toIntOrNull() }
        val blue = parts.getOrNull(8)?.toIntOrNull() ?: return null
        return if (LotteryRules.isValid(reds, blue)) {
            LotteryDraw(issue = issue, date = date, redBalls = reds.sorted(), blueBall = blue)
        } else null
    }
}
