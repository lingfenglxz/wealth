package com.demo.wealth

import com.demo.wealth.domain.sports.FootballLotteryParser
import com.demo.wealth.domain.sports.FootballPlayTypes
import com.demo.wealth.domain.sports.FootballScheduleUi
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
                  "stadium": "Mexico City Stadium",
                  "city": "Mexico City",
                  "pools": {
                    "had": {"H": 1.78, "D": 3.30, "A": 4.60},
                    "hhad": {"H": 3.20, "D": 3.25, "A": 1.95}
                  }
                }
              ],
              "recommendations": [
                {
                  "matchId": "wc2026-001",
                  "homeTeam": "Mexico",
                  "awayTeam": "South Africa",
                  "playType": "had",
                  "playName": "胜平负",
                  "modelName": "poisson_v1",
                  "selection": "H",
                  "odds": 1.78,
                  "confidence": 0.55,
                  "fairProbability": 0.51,
                  "modelProbability": 0.58,
                  "edge": 0.0324,
                  "dataQuality": 0.86,
                  "expectedGoals": {"home": 1.82, "away": 0.91},
                  "reasons": ["主胜隐含概率最高"]
                }
              ]
            }
        """.trimIndent()

        val matches = FootballLotteryParser.parseMatches(json)
        val recommendations = FootballLotteryParser.parseRecommendations(json)

        assertEquals(1, matches.size)
        assertEquals("wc2026-001", matches.first().matchId)
        assertEquals("墨西哥", matches.first().homeTeam)
        assertEquals("南非", matches.first().awayTeam)
        assertEquals("小组赛 A组", matches.first().phase)
        assertEquals("墨西哥城球场", matches.first().stadium)
        assertEquals("墨西哥城", matches.first().city)
        assertTrue(matches.first().poolsJson.contains("had"))
        assertEquals(1, recommendations.size)
        assertEquals("墨西哥", recommendations.first().homeTeam)
        assertEquals("南非", recommendations.first().awayTeam)
        assertEquals("H", recommendations.first().selection)
        assertEquals(0.51, recommendations.first().fairProbability, 0.0001)
        assertEquals(0.58, recommendations.first().modelProbability, 0.0001)
        assertEquals(0.0324, recommendations.first().edge, 0.0001)
        assertEquals(0.86, recommendations.first().dataQuality, 0.0001)
        assertEquals("poisson_v1", recommendations.first().modelName)
        assertEquals(1.82, recommendations.first().homeExpectedGoals, 0.0001)
        assertEquals(0.91, recommendations.first().awayExpectedGoals, 0.0001)
    }

    @Test
    fun footballPlayTypesIncludeSportsLotteryBasics() {
        assertEquals(listOf("had", "hhad", "crs", "ttg", "hafu"), FootballPlayTypes.allCodes)
    }

    @Test
    fun matchMovesToHistoryThreeHoursAfterKickoff() {
        val beforeEnd = FootballScheduleUi.isHistoricalKickoff(
            kickoffTime = "2026-06-12T03:00:00+08:00",
            nowIso = "2026-06-12T05:59:00+08:00"
        )
        val afterEnd = FootballScheduleUi.isHistoricalKickoff(
            kickoffTime = "2026-06-12T03:00:00+08:00",
            nowIso = "2026-06-12T06:01:00+08:00"
        )

        assertEquals(false, beforeEnd)
        assertEquals(true, afterEnd)
    }
}
