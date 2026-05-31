package com.demo.wealth

import com.demo.wealth.data.StockCandle
import com.demo.wealth.domain.quant.Backtester
import com.demo.wealth.domain.quant.Indicators
import com.demo.wealth.domain.quant.MovingAverageCrossStrategy
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantBacktestTest {
    @Test
    fun indicatorsAndBacktestProduceFiniteResults() {
        val candles = (1..80).map {
            val close = 10.0 + it * 0.08 + if (it % 9 == 0) -0.4 else 0.0
            StockCandle(
                symbol = "000001",
                date = "2024-03-${it.toString().padStart(2, '0')}",
                open = close - 0.05,
                high = close + 0.2,
                low = close - 0.2,
                close = close,
                volume = 100000.0 + it
            )
        }

        assertTrue(Indicators.ma(candles, 20).isNotEmpty())
        assertTrue(Indicators.rsi(candles, 14).isNotEmpty())
        assertTrue(Indicators.macd(candles).isNotEmpty())

        val result = Backtester().run(candles, MovingAverageCrossStrategy())
        assertTrue(result.equityCurve.isNotEmpty())
        assertTrue(result.totalReturn.isFinite())
        assertTrue(result.maxDrawdown in 0.0..1.0)
    }
}
