package com.demo.wealth.domain.lottery

import com.demo.wealth.data.LotteryDraw
import org.json.JSONObject

object LotteryOfficialParser {
    fun parseCwlJson(text: String): List<LotteryDraw> {
        val root = JSONObject(text)
        val result = root.optJSONArray("result") ?: return emptyList()
        return parseArray(result)
    }

    fun parseServerJson(text: String): List<LotteryDraw> {
        val root = JSONObject(text)
        val draws = root.optJSONArray("draws") ?: return emptyList()
        return parseArray(draws)
    }

    private fun parseArray(result: org.json.JSONArray): List<LotteryDraw> =
        buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val issue = item.optString("issue", item.optString("code"))
                val date = item.optString("date").substringBefore("(")
                val reds = when {
                    item.has("redBalls") -> item.optJSONArray("redBalls")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optInt(it).takeIf { value -> value > 0 } }
                    }.orEmpty()
                    else -> item.optString("red").split(",").mapNotNull { it.trim().toIntOrNull() }
                }.sorted()
                val blue = when {
                    item.has("blueBall") -> item.optInt("blueBall")
                    else -> item.optString("blue").trim().toIntOrNull() ?: 0
                }
                if (issue.isNotBlank() && LotteryRules.isValid(reds, blue)) {
                    add(LotteryDraw(issue = issue, date = date, redBalls = reds, blueBall = blue))
                }
            }
        }
}
