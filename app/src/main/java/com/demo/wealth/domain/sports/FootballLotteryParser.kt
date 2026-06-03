package com.demo.wealth.domain.sports

import com.demo.wealth.data.FootballMatchEntity
import com.demo.wealth.data.FootballRecommendationEntity
import org.json.JSONArray
import org.json.JSONObject

object FootballPlayTypes {
    val allCodes = listOf("had", "hhad", "crs", "ttg", "hafu")

    fun displayName(code: String): String = when (code) {
        "had" -> "胜平负"
        "hhad" -> "让球胜平负"
        "crs" -> "比分"
        "ttg" -> "总进球"
        "hafu" -> "半全场"
        else -> code
    }
}

object FootballLotteryParser {
    fun parseMatches(text: String): List<FootballMatchEntity> {
        val root = JSONObject(text)
        val matches = root.optJSONArray("matches") ?: return emptyList()
        return (0 until matches.length()).mapNotNull { index ->
            val item = matches.optJSONObject(index) ?: return@mapNotNull null
            val matchId = item.optString("matchId")
            val home = item.optString("homeTeam")
            val away = item.optString("awayTeam")
            val kickoff = item.optString("kickoffTime")
            val pools = item.optJSONObject("pools")
            if (matchId.isBlank() || home.isBlank() || away.isBlank() || kickoff.isBlank() || pools == null) {
                return@mapNotNull null
            }
            FootballMatchEntity(
                matchId = matchId,
                matchNum = item.optString("matchNum", matchId),
                leagueName = item.optString("leagueName", "FIFA World Cup 2026"),
                phase = item.optString("phase", "世界杯"),
                kickoffTime = kickoff,
                homeTeam = home,
                awayTeam = away,
                handicap = item.optInt("handicap"),
                poolsJson = pools.toString(),
                source = item.optString("source", root.optString("sourceStatus", "fallback")),
                updatedAt = item.optString("updatedAt")
            )
        }
    }

    fun parseRecommendations(text: String): List<FootballRecommendationEntity> {
        val root = JSONObject(text)
        val items = root.optJSONArray("recommendations") ?: return emptyList()
        val createdAt = System.currentTimeMillis()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val matchId = item.optString("matchId")
            val selection = item.optString("selection")
            if (matchId.isBlank() || selection.isBlank()) return@mapNotNull null
            FootballRecommendationEntity(
                createdAt = createdAt,
                matchId = matchId,
                matchNum = item.optString("matchNum", matchId),
                leagueName = item.optString("leagueName", "FIFA World Cup 2026"),
                phase = item.optString("phase", "世界杯"),
                kickoffTime = item.optString("kickoffTime"),
                homeTeam = item.optString("homeTeam"),
                awayTeam = item.optString("awayTeam"),
                playType = item.optString("playType"),
                playName = item.optString("playName", FootballPlayTypes.displayName(item.optString("playType"))),
                selection = selection,
                odds = item.optDouble("odds", 0.0),
                confidence = item.optDouble("confidence", 0.0),
                reasonsJson = item.optJSONArray("reasons")?.toString() ?: JSONArray().toString()
            )
        }
    }
}
