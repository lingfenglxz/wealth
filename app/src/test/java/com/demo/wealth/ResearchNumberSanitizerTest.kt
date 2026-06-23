package com.demo.wealth

import com.demo.wealth.data.optFiniteDouble
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchNumberSanitizerTest {
    @Test
    fun missingOrNanResearchMetricFallsBackToZero() {
        assertEquals(0.0, JSONObject().optFiniteDouble("averageBetCount"), 0.0)
        assertEquals(0.0, JSONObject("{\"averageBetCount\":\"NaN\"}").optFiniteDouble("averageBetCount"), 0.0)
    }
}
