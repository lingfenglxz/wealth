package com.demo.wealth

import com.demo.wealth.domain.lottery.LotteryServerStateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryServerStateTest {
    @Test
    fun parsesServerStateBackupForPredictionsReportsAndSettlements() {
        val json = """
            {
              "stateBackup": {
                "predictions": [
                  {
                    "targetIssue": "2026063",
                    "sourceIssue": "2026062",
                    "modelVersion": "balanced_v2",
                    "redBalls": [1,2,3,4,5,6],
                    "blueBalls": [7],
                    "score": 1.23,
                    "analysisSummary": "样本",
                    "reasons": ["reason"],
                    "ballDetails": [],
                    "note": "单式"
                  }
                ],
                "researchReports": [
                  {
                    "targetIssue": "2026063",
                    "sourceIssue": "2026062",
                    "modelVersion": "balanced_v2",
                    "report": {"modelVersion": "balanced_v2", "recentWindow": 120}
                  }
                ],
                "lotterySettlements": [
                  {
                    "issue": "2026062",
                    "drawDate": "2026-06-02",
                    "settledAt": 20260604,
                    "betCount": 1,
                    "investedAmount": 2.0,
                    "simulatedPrizeAmount": 5.0,
                    "roi": 1.5,
                    "bestRedHits": 1,
                    "blueHit": true,
                    "tierCounts": {"sixth": 1},
                    "details": []
                  }
                ]
              }
            }
        """.trimIndent()

        val state = LotteryServerStateParser.parse(json)

        assertEquals("2026063", state.predictions.first().targetIssue)
        assertEquals("balanced_v2", state.researchSnapshots.first().modelVersion)
        assertEquals("2026062", state.settlements.first().issue)
        assertTrue(state.settlements.first().blueHit)
    }
}
