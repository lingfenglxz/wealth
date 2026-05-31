package com.demo.wealth

import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.data.LotteryPrediction
import com.demo.wealth.data.LotterySettlementCalculator
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LotterySettlementCalculatorTest {
    @Test
    fun settlementTracksOnlyOneCompoundTicketPerIssue() {
        val draw = LotteryDraw(
            issue = "2026055",
            date = "2026-05-17",
            redBalls = listOf(1, 2, 3, 4, 5, 6),
            blueBall = 3
        )
        val predictions = listOf(
            prediction(listOf(1, 7, 8, 9, 10, 11), listOf(1)),
            prediction(listOf(2, 7, 8, 9, 10, 11), listOf(2)),
            prediction(listOf(3, 7, 8, 9, 10, 11), listOf(3)),
            prediction(listOf(1, 2, 3, 4, 5, 6), listOf(1, 2, 3))
        )

        val settlement = LotterySettlementCalculator.buildTrackedCompoundSettlement(draw, predictions, settledAt = 1L)

        assertNotNull(settlement)
        assertEquals(3L, settlement!!.betCount)
        assertEquals(6.0, settlement.investedAmount, 0.0)
        assertEquals(1, JSONArray(settlement.detailJson).length())
    }

    @Test
    fun threeRedHitsWithoutBlueCountsFortunePrizeForEachBlueBet() {
        val draw = LotteryDraw(
            issue = "2026060",
            date = "2026-05-28",
            redBalls = listOf(1, 2, 3, 9, 10, 11),
            blueBall = 16
        )
        val predictions = listOf(
            prediction(listOf(1, 2, 3, 20, 21, 22), listOf(1, 2, 3))
        )

        val settlement = LotterySettlementCalculator.buildTrackedCompoundSettlement(draw, predictions, settledAt = 1L)

        assertNotNull(settlement)
        assertEquals(3L, settlement!!.betCount)
        assertEquals(15.0, settlement.simulatedPrizeAmount, 0.0)
    }

    private fun prediction(reds: List<Int>, blues: List<Int>) = LotteryPrediction(
        createdAt = 1L,
        targetIssue = "2026055",
        sourceIssue = "2026054",
        redBalls = reds,
        blueBalls = blues,
        score = 1.0,
        analysisSummary = "",
        note = ""
    )
}
