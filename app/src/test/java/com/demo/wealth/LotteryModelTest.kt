package com.demo.wealth

import com.demo.wealth.data.LotteryDraw
import com.demo.wealth.domain.lottery.LotteryFeatureModel
import com.demo.wealth.domain.lottery.LotteryGenerator
import com.demo.wealth.domain.lottery.LotteryOfficialParser
import com.demo.wealth.domain.lottery.LotteryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LotteryModelTest {
    @Test
    fun generatedNumbersAreValid() {
        val draws = (1..60).map {
            LotteryDraw(
                issue = "2024$it",
                date = "2024-01-${(it % 28 + 1).toString().padStart(2, '0')}",
                redBalls = listOf(1, 6, 12, 18, 24, 30).map { number -> ((number + it) % 33).let { shifted -> if (shifted == 0) 33 else shifted } }.sorted(),
                blueBall = it % 16 + 1
            )
        }
        val result = LotteryFeatureModel().train(draws)
        val predictions = LotteryGenerator(Random(7)).generate(result, 8)
        assertEquals(8, predictions.size)
        assertTrue(predictions.all { it.blueBalls.size == 1 && LotteryRules.isValid(it.redBalls, it.blueBalls.first()) })
    }

    @Test
    fun compoundPredictionsUseValidRedAndBluePools() {
        val draws = (1..80).map {
            LotteryDraw(
                issue = "2025$it",
                date = "2025-02-${(it % 28 + 1).toString().padStart(2, '0')}",
                redBalls = listOf(2, 7, 13, 19, 25, 31).map { number -> ((number + it) % 33).let { shifted -> if (shifted == 0) 33 else shifted } }.sorted(),
                blueBall = it % 16 + 1
            )
        }
        val result = LotteryFeatureModel().train(draws)
        val predictions = LotteryGenerator(Random(9)).generateCompound(result, count = 3, redCount = 9, blueCount = 3)
        assertEquals(3, predictions.size)
        assertTrue(predictions.all { LotteryRules.isValidCompound(it.redBalls, it.blueBalls) })
        assertTrue(predictions.all { LotteryRules.combinationCount(it.redBalls.size, 6) * it.blueBalls.size == 252L })
    }

    @Test
    fun blueCompoundSixPlusThreeIsValid() {
        val draws = (1..80).map {
            LotteryDraw(
                issue = "2026$it",
                date = "2026-03-${(it % 28 + 1).toString().padStart(2, '0')}",
                redBalls = listOf(3, 8, 14, 20, 26, 32).map { number -> ((number + it) % 33).let { shifted -> if (shifted == 0) 33 else shifted } }.sorted(),
                blueBall = it % 16 + 1
            )
        }
        val result = LotteryFeatureModel().train(draws)
        val predictions = LotteryGenerator(Random(11)).generateCompound(result, count = 2, redCount = 6, blueCount = 3, targetIssue = "2026081", sourceIssue = "2026080")
        assertEquals(2, predictions.size)
        assertTrue(predictions.all { it.redBalls.size == 6 && it.blueBalls.size == 3 })
        assertTrue(predictions.all { LotteryRules.combinationCount(it.redBalls.size, 6) * it.blueBalls.size == 3L })
    }

    @Test
    fun sameSeedKeepsPredictionsStable() {
        val draws = (1..80).map {
            LotteryDraw(
                issue = "2027$it",
                date = "2027-04-${(it % 28 + 1).toString().padStart(2, '0')}",
                redBalls = listOf(4, 9, 15, 21, 27, 33).map { number -> ((number + it) % 33).let { shifted -> if (shifted == 0) 33 else shifted } }.sorted(),
                blueBall = it % 16 + 1
            )
        }
        val result = LotteryFeatureModel().train(draws)
        val first = LotteryGenerator(Random(19)).generateWithCompound(result, compoundRedCount = 7, compoundBlueCount = 2, targetIssue = "2027081", sourceIssue = "2027080")
        val second = LotteryGenerator(Random(19)).generateWithCompound(result, compoundRedCount = 7, compoundBlueCount = 2, targetIssue = "2027081", sourceIssue = "2027080")
        assertEquals(first.map { it.redBalls to it.blueBalls }, second.map { it.redBalls to it.blueBalls })
    }

    @Test
    fun officialJsonCanBeParsed() {
        val json = """
            {
              "state": 0,
              "result": [
                {
                  "name": "双色球",
                  "code": "2026053",
                  "date": "2026-05-12(二)",
                  "red": "01,02,03,08,13,14",
                  "blue": "02"
                }
              ]
            }
        """.trimIndent()

        val draws = LotteryOfficialParser.parseCwlJson(json)
        assertEquals(1, draws.size)
        assertEquals("2026053", draws.first().issue)
        assertEquals("2026-05-12", draws.first().date)
        assertEquals(listOf(1, 2, 3, 8, 13, 14), draws.first().redBalls)
        assertEquals(2, draws.first().blueBall)
    }

    @Test
    fun serverJsonCanBeParsed() {
        val json = """
            {
              "source": "cwl",
              "count": 1,
              "draws": [
                {
                  "issue": "2026053",
                  "date": "2026-05-12",
                  "redBalls": [1,2,3,8,13,14],
                  "blueBall": 2
                }
              ]
            }
        """.trimIndent()

        val draws = LotteryOfficialParser.parseServerJson(json)
        assertEquals(1, draws.size)
        assertEquals("2026053", draws.first().issue)
        assertEquals(listOf(1, 2, 3, 8, 13, 14), draws.first().redBalls)
        assertEquals(2, draws.first().blueBall)
    }
}
