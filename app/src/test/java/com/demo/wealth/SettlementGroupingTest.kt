package com.demo.wealth

import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.ui.groupSettlementsByIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementGroupingTest {
    @Test
    fun groupsMultipleRunsIntoOneIssue() {
        val settlements = listOf(
            settlement("2026071", "run-a", "same"),
            settlement("2026071", "run-b", "different"),
            settlement("2026071", "run-c", "same"),
            settlement("2026070", "run-d", "other")
        )

        val grouped = groupSettlementsByIssue(settlements)

        assertEquals(listOf(2, 1), grouped.map { it.size })
    }

    private fun settlement(issue: String, runId: String, detailJson: String) = LotterySettlement(
        issue = issue, runId = runId, drawDate = "2026-06-24", settledAt = 1L,
        betCount = 1, investedAmount = 2.0, simulatedPrizeAmount = 0.0, roi = -1.0,
        bestRedHits = 0, blueHit = false, tierCountsJson = "{}", detailJson = detailJson
    )
}
