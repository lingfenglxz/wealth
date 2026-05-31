package com.demo.wealth.domain.quant

import com.demo.wealth.data.StockCandle
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

data class IndicatorValue(
    val date: String,
    val name: String,
    val value: Double
)

object Indicators {
    fun ma(candles: List<StockCandle>, period: Int): List<IndicatorValue> =
        candles.mapIndexedNotNull { index, candle ->
            if (index + 1 < period) null else IndicatorValue(
                date = candle.date,
                name = "MA$period",
                value = candles.subList(index + 1 - period, index + 1).map { it.close }.average()
            )
        }

    fun ema(candles: List<StockCandle>, period: Int): List<IndicatorValue> {
        if (candles.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        var previous = candles.first().close
        return candles.map { candle ->
            previous = alpha * candle.close + (1 - alpha) * previous
            IndicatorValue(candle.date, "EMA$period", previous)
        }
    }

    fun rsi(candles: List<StockCandle>, period: Int = 14): List<IndicatorValue> {
        if (candles.size <= period) return emptyList()
        val result = mutableListOf<IndicatorValue>()
        for (i in period until candles.size) {
            val window = candles.subList(i + 1 - period, i + 1)
            val changes = window.zipWithNext { a, b -> b.close - a.close }
            val gains = changes.filter { it > 0 }.sum()
            val losses = -changes.filter { it < 0 }.sum()
            val rs = if (losses == 0.0) 100.0 else gains / losses
            val value = 100.0 - (100.0 / (1.0 + rs))
            result += IndicatorValue(candles[i].date, "RSI$period", value)
        }
        return result
    }

    fun macd(candles: List<StockCandle>): List<MacdValue> {
        val ema12 = ema(candles, 12).associateBy { it.date }
        val ema26 = ema(candles, 26).associateBy { it.date }
        var signal = 0.0
        return candles.mapNotNull { candle ->
            val diff = (ema12[candle.date]?.value ?: return@mapNotNull null) - (ema26[candle.date]?.value ?: return@mapNotNull null)
            signal = 2.0 / 10.0 * diff + 8.0 / 10.0 * signal
            MacdValue(candle.date, diff, signal, diff - signal)
        }
    }

    fun bollinger(candles: List<StockCandle>, period: Int = 20): List<BollingerValue> =
        candles.mapIndexedNotNull { index, candle ->
            if (index + 1 < period) return@mapIndexedNotNull null
            val closes = candles.subList(index + 1 - period, index + 1).map { it.close }
            val mid = closes.average()
            val sd = sqrt(closes.map { (it - mid).pow(2) }.average())
            BollingerValue(candle.date, upper = mid + 2 * sd, middle = mid, lower = mid - 2 * sd)
        }
}

data class MacdValue(val date: String, val diff: Double, val signal: Double, val histogram: Double)
data class BollingerValue(val date: String, val upper: Double, val middle: Double, val lower: Double)

enum class SignalAction { BUY, SELL, HOLD }

data class TradeSignal(
    val date: String,
    val action: SignalAction,
    val reason: String
)

interface QuantStrategy {
    val name: String
    fun signals(candles: List<StockCandle>): List<TradeSignal>
}

class MovingAverageCrossStrategy(
    private val shortPeriod: Int = 5,
    private val longPeriod: Int = 20
) : QuantStrategy {
    override val name: String = "MA${shortPeriod}_${longPeriod}_Cross"

    override fun signals(candles: List<StockCandle>): List<TradeSignal> {
        val short = Indicators.ma(candles, shortPeriod).associateBy { it.date }
        val long = Indicators.ma(candles, longPeriod).associateBy { it.date }
        return candles.mapIndexedNotNull { index, candle ->
            if (index == 0) return@mapIndexedNotNull null
            val prev = candles[index - 1]
            val beforeShort = short[prev.date]?.value ?: return@mapIndexedNotNull null
            val beforeLong = long[prev.date]?.value ?: return@mapIndexedNotNull null
            val nowShort = short[candle.date]?.value ?: return@mapIndexedNotNull null
            val nowLong = long[candle.date]?.value ?: return@mapIndexedNotNull null
            when {
                beforeShort <= beforeLong && nowShort > nowLong -> TradeSignal(candle.date, SignalAction.BUY, "短均线上穿长均线")
                beforeShort >= beforeLong && nowShort < nowLong -> TradeSignal(candle.date, SignalAction.SELL, "短均线下穿长均线")
                else -> TradeSignal(candle.date, SignalAction.HOLD, "均线未触发")
            }
        }
    }
}

class RsiReversionStrategy : QuantStrategy {
    override val name: String = "RSI_Reversion"

    override fun signals(candles: List<StockCandle>): List<TradeSignal> =
        Indicators.rsi(candles).map {
            when {
                it.value < 30 -> TradeSignal(it.date, SignalAction.BUY, "RSI超卖")
                it.value > 70 -> TradeSignal(it.date, SignalAction.SELL, "RSI超买")
                else -> TradeSignal(it.date, SignalAction.HOLD, "RSI中性")
            }
        }
}

class MacdTrendStrategy : QuantStrategy {
    override val name: String = "MACD_Trend"

    override fun signals(candles: List<StockCandle>): List<TradeSignal> =
        Indicators.macd(candles).map {
            when {
                it.histogram > 0 -> TradeSignal(it.date, SignalAction.BUY, "MACD柱为正")
                it.histogram < 0 -> TradeSignal(it.date, SignalAction.SELL, "MACD柱为负")
                else -> TradeSignal(it.date, SignalAction.HOLD, "MACD中性")
            }
        }
}

class BreakoutStrategy(private val period: Int = 20) : QuantStrategy {
    override val name: String = "Breakout$period"

    override fun signals(candles: List<StockCandle>): List<TradeSignal> =
        candles.mapIndexedNotNull { index, candle ->
            if (index < period) return@mapIndexedNotNull null
            val previous = candles.subList(index - period, index)
            val high = previous.maxOf { it.high }
            val low = previous.minOf { it.low }
            when {
                candle.close > high -> TradeSignal(candle.date, SignalAction.BUY, "突破$period 日高点")
                candle.close < low -> TradeSignal(candle.date, SignalAction.SELL, "跌破$period 日低点")
                else -> TradeSignal(candle.date, SignalAction.HOLD, "区间内")
            }
        }
}

data class Trade(
    val date: String,
    val action: SignalAction,
    val price: Double,
    val shares: Int,
    val equity: Double,
    val reason: String
)

data class BacktestResult(
    val strategyName: String,
    val totalReturn: Double,
    val maxDrawdown: Double,
    val winRate: Double,
    val tradeCount: Int,
    val equityCurve: List<Double>,
    val latestSignal: TradeSignal,
    val trades: List<Trade>
)

class Backtester {
    fun run(
        candles: List<StockCandle>,
        strategy: QuantStrategy,
        initialCash: Double = 100000.0,
        feeRate: Double = 0.0003,
        slippageRate: Double = 0.0002
    ): BacktestResult {
        if (candles.isEmpty()) {
            return BacktestResult(strategy.name, 0.0, 0.0, 0.0, 0, emptyList(), TradeSignal("", SignalAction.HOLD, "无数据"), emptyList())
        }
        val signals = strategy.signals(candles).associateBy { it.date }
        var cash = initialCash
        var shares = 0
        var entryPrice = 0.0
        var wins = 0
        var closedTrades = 0
        var peak = initialCash
        var maxDrawdown = 0.0
        val equityCurve = mutableListOf<Double>()
        val trades = mutableListOf<Trade>()

        for (candle in candles) {
            val signal = signals[candle.date] ?: TradeSignal(candle.date, SignalAction.HOLD, "无信号")
            when (signal.action) {
                SignalAction.BUY -> if (shares == 0) {
                    val price = candle.close * (1 + slippageRate)
                    val targetShares = max(0, (cash / price).toInt() / 100 * 100)
                    val cost = targetShares * price * (1 + feeRate)
                    if (targetShares > 0 && cost <= cash) {
                        cash -= cost
                        shares = targetShares
                        entryPrice = price
                        trades += Trade(candle.date, SignalAction.BUY, price, shares, cash + shares * candle.close, signal.reason)
                    }
                }
                SignalAction.SELL -> if (shares > 0) {
                    val price = candle.close * (1 - slippageRate)
                    cash += shares * price * (1 - feeRate)
                    if (price > entryPrice) wins++
                    closedTrades++
                    trades += Trade(candle.date, SignalAction.SELL, price, shares, cash, signal.reason)
                    shares = 0
                    entryPrice = 0.0
                }
                SignalAction.HOLD -> Unit
            }
            val equity = cash + shares * candle.close
            peak = max(peak, equity)
            maxDrawdown = max(maxDrawdown, if (peak == 0.0) 0.0 else (peak - equity) / peak)
            equityCurve += equity
        }
        val finalEquity = equityCurve.lastOrNull() ?: initialCash
        return BacktestResult(
            strategyName = strategy.name,
            totalReturn = (finalEquity - initialCash) / initialCash,
            maxDrawdown = maxDrawdown,
            winRate = if (closedTrades == 0) 0.0 else wins.toDouble() / closedTrades,
            tradeCount = trades.size,
            equityCurve = equityCurve,
            latestSignal = signals[candles.last().date] ?: TradeSignal(candles.last().date, SignalAction.HOLD, "无信号"),
            trades = trades
        )
    }
}
