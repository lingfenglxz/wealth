package com.demo.wealth

import com.demo.wealth.data.buildSsqRecommendationPath
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryRecommendationRequestTest {
    @Test
    fun buildsOneGroupRecommendationPathFromCurrentShape() {
        val path = buildSsqRecommendationPath(compoundRedCount = 6, compoundBlueCount = 1)

        assertTrue(path.contains("singleCount=0&compoundCount=1&redCount=6&blueCount=1"))
        assertTrue(path.contains("modelVersion=auto&recentWindow=240"))
    }
}
