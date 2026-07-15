package com.demo.wealth

import com.demo.wealth.data.FootballRecommendationEntity
import com.demo.wealth.data.buildSsqRecommendationPath
import com.demo.wealth.ui.components.ConnectionStatus
import com.demo.wealth.ui.components.connectionStatus
import com.demo.wealth.ui.latestRecommendationRun
import com.demo.wealth.ui.parseHadOdds
import com.demo.wealth.ui.nextVisibleCount
import com.demo.wealth.ui.expectedSsqDrawDate
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiBehaviorTest {
    @Test
    fun lotteryScreenShowsOnlyTenDrawsWithoutRankingOrLoadMore() {
        val source = File("src/main/java/com/demo/wealth/ui/screens/LotteryScreen.kt").readText()

        assertTrue(source.contains("draws.take(10)"))
        assertFalse(source.contains("NumberRankingCard"))
        assertFalse(source.contains("号码榜单"))
        assertFalse(source.contains("加载更多"))
    }

    @Test
    fun lotteryScreenUsesDrawTimeCopy() {
        val source = File("src/main/java/com/demo/wealth/ui/screens/LotteryScreen.kt").readText()

        assertFalse(source.contains("预计开奖"))
        assertTrue(source.contains("开奖时间："))
        assertTrue(source.contains("开奖时间待更新"))
    }

    @Test
    fun sportsScreenLabelsPublicMarketOdds() {
        val source = File("src/main/java/com/demo/wealth/ui/screens/SportsScreen.kt").readText()

        assertTrue(source.contains("公开市场快照（2026-07-14）"))
        assertTrue(source.contains("非中国竞彩网官方赔率"))
    }

    @Test
    fun visibleCountAdvancesByPageWithoutPassingTotal() {
        assertEquals(40, nextVisibleCount(current = 20, total = 55, pageSize = 20))
        assertEquals(55, nextVisibleCount(current = 40, total = 55, pageSize = 20))
    }

    @Test
    fun connectionStatusUsesConfirmedServerState() {
        assertEquals(ConnectionStatus.SYNCING, connectionStatus(isSyncing = true, isConnected = false))
        assertEquals(ConnectionStatus.CONNECTED, connectionStatus(isSyncing = false, isConnected = true))
        assertEquals(ConnectionStatus.OFFLINE, connectionStatus(isSyncing = false, isConnected = false))
    }

    @Test
    fun parsesHadOddsInHomeDrawAwayOrder() {
        assertEquals(
            listOf("主胜" to 1.78, "平" to 3.30, "客胜" to 4.60),
            parseHadOdds("""{"had":{"H":1.78,"D":3.30,"A":4.60}}""")
        )
        assertEquals(emptyList<Pair<String, Double>>(), parseHadOdds("{}"))
    }

    @Test
    fun historicalSummaryUsesOnlyTheLatestGenerationRun() {
        val history = listOf(
            recommendation(createdAt = 100L, playType = "had"),
            recommendation(createdAt = 200L, playType = "had"),
            recommendation(createdAt = 200L, playType = "hhad")
        )

        val latest = latestRecommendationRun(history)

        assertEquals(listOf("had", "hhad"), latest.map { it.playType })
    }

    @Test
    fun expectedDrawDateAdvancesToNextTuesdayThursdayOrSunday() {
        assertEquals("2026-07-14", expectedSsqDrawDate("2026-07-12"))
        assertEquals("2026-07-16", expectedSsqDrawDate("2026-07-14"))
        assertEquals("2026-07-19", expectedSsqDrawDate("2026-07-16"))
        assertEquals("2027-01-03", expectedSsqDrawDate("2026-12-31"))
        assertNull(expectedSsqDrawDate("not-a-date"))
    }

    @Test
    fun ssqRecommendationRequestOnlySendsCombinationInputs() {
        assertEquals(
            "/api/lottery/ssq/recommendations?compoundCount=2&redCount=7&blueCount=4",
            buildSsqRecommendationPath(compoundCount = 2, compoundRedCount = 7, compoundBlueCount = 4)
        )
    }

    private fun recommendation(createdAt: Long, playType: String) = FootballRecommendationEntity(
        createdAt = createdAt,
        matchId = "match-1",
        matchNum = "001",
        leagueName = "世界杯",
        phase = "小组赛",
        kickoffTime = "2026-06-12T03:00:00+08:00",
        homeTeam = "墨西哥",
        awayTeam = "南非",
        playType = playType,
        playName = playType,
        modelName = "poisson_v1",
        selection = "H",
        odds = 1.8,
        confidence = 0.6,
        fairProbability = 0.5,
        modelProbability = 0.6,
        edge = 0.1,
        dataQuality = 0.9,
        homeExpectedGoals = 1.5,
        awayExpectedGoals = 0.8,
        reasonsJson = "[]"
    )
}
