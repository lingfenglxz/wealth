package com.demo.wealth

import com.demo.wealth.data.LotterySettlement
import com.demo.wealth.ui.groupUniqueWagerSettlementsByIssue
import com.demo.wealth.ui.summarizeSettlements
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

        val grouped = groupUniqueWagerSettlementsByIssue(settlements)

        assertEquals(listOf(2, 1), grouped.map { it.size })
    }

    @Test
    fun summaryUsesTheSameDeduplicatedIssueGroupsAsLotteryPage() {
        val settlements = listOf(
            settlement("2026071", "run-a", "same", invested = 6.0, prize = 10.0),
            settlement("2026071", "run-b", "same", invested = 6.0, prize = 10.0),
            settlement("2026070", "run-c", "other", invested = 4.0, prize = 0.0)
        )

        val summary = summarizeSettlements(settlements)

        assertEquals(2, summary.issueCount)
        assertEquals(10.0, summary.investedAmount, 0.0)
        assertEquals(10.0, summary.prizeAmount, 0.0)
        assertEquals(0.0, summary.roi, 0.0)
    }

    private fun settlement(
        issue: String,
        runId: String,
        detailJson: String,
        invested: Double = 2.0,
        prize: Double = 0.0
    ) = LotterySettlement(
        issue = issue, runId = runId, drawDate = "2026-06-24", settledAt = 1L,
        betCount = 1, investedAmount = invested, simulatedPrizeAmount = prize, roi = -1.0,
        bestRedHits = 0, blueHit = false, tierCountsJson = "{}", detailJson = detailJson
    )
}
