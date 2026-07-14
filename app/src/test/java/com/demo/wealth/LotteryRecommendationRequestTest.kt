package com.demo.wealth

import com.demo.wealth.data.buildSsqRecommendationPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryRecommendationRequestTest {
    @Test
    fun buildsOneGroupRecommendationPathFromCurrentShape() {
        val path = buildSsqRecommendationPath(compoundRedCount = 6, compoundBlueCount = 1)

        assertTrue(path.contains("compoundCount=1&redCount=6&blueCount=1"))
        assertFalse(path.contains("budgetBets"))
        assertFalse(path.contains("modelVersion"))
        assertFalse(path.contains("recentWindow"))
        assertEquals(3L, com.demo.wealth.domain.lottery.LotteryRules.combinationCount(6, 6) * 3)
    }
}
