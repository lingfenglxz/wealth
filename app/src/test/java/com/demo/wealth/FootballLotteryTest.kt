package com.demo.wealth

import com.demo.wealth.domain.sports.FootballLotteryParser
import com.demo.wealth.domain.sports.FootballPlayTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballLotteryTest {
    @Test
    fun serverFootballJsonParsesMatchesAndRecommendations() {
        val json = """
            {
              "sourceStatus": "fallback",
              "matches": [
                {
                  "matchId": "wc2026-001",
                  "matchNum": "001",
                  "leagueName": "FIFA World Cup 2026",
                  "phase": "小组赛 A组",
                  "kickoffTime": "2026-06-11T19:00:00-06:00",
                  "homeTeam": "Mexico",
                  "awayTeam": "South Africa",
                  "handicap": 0,
                  "pools": {
                    "had": {"H": 1.78, "D": 3.30, "A": 4.60},
                    "hhad": {"H": 3.20, "D": 3.25, "A": 1.95}
                  }
                }
              ],
              "recommendations": [
                {
                  "matchId": "wc2026-001",
                  "playType": "had",
                  "playName": "胜平负",
                  "selection": "H",
                  "confidence": 0.55,
                  "reasons": ["主胜隐含概率最高"]
                }
              ]
            }
        """.trimIndent()

        val matches = FootballLotteryParser.parseMatches(json)
        val recommendations = FootballLotteryParser.parseRecommendations(json)

        assertEquals(1, matches.size)
        assertEquals("wc2026-001", matches.first().matchId)
        assertTrue(matches.first().poolsJson.contains("had"))
        assertEquals(1, recommendations.size)
        assertEquals("H", recommendations.first().selection)
    }

    @Test
    fun footballPlayTypesIncludeSportsLotteryBasics() {
        assertEquals(listOf("had", "hhad", "crs", "ttg", "hafu"), FootballPlayTypes.allCodes)
    }
}
