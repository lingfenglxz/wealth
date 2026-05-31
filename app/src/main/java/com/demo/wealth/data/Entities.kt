package com.demo.wealth.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["issue"], unique = true)])
data class LotteryDraw(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issue: String,
    val date: String,
    val redBalls: List<Int>,
    val blueBall: Int
)

@Entity
data class LotteryPrediction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val targetIssue: String,
    val sourceIssue: String,
    val modelVersion: String = "local_legacy",
    val redBalls: List<Int>,
    val blueBalls: List<Int>,
    val score: Double,
    val analysisSummary: String,
    val reasons: String = "",
    val ballDetails: String = "",
    val note: String
)

@Entity(indices = [Index(value = ["targetIssue"], unique = true)])
data class LotteryResearchSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val targetIssue: String,
    val sourceIssue: String,
    val modelVersion: String,
    val reportJson: String
)

@Entity(indices = [Index(value = ["issue"], unique = true)])
data class LotterySettlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issue: String,
    val drawDate: String,
    val settledAt: Long,
    val betCount: Long,
    val investedAmount: Double,
    val simulatedPrizeAmount: Double,
    val roi: Double,
    val bestRedHits: Int,
    val blueHit: Boolean,
    val tierCountsJson: String,
    val detailJson: String
)

@Entity(indices = [Index(value = ["symbol"], unique = true)])
data class StockSymbol(
    @PrimaryKey val symbol: String,
    val name: String,
    val market: String = "CN",
    val lastUpdated: String? = null
)

@Entity(primaryKeys = ["symbol", "date"])
data class StockCandle(
    val symbol: String,
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Entity(primaryKeys = ["symbol", "strategyName"])
data class StrategyConfig(
    val symbol: String,
    val strategyName: String,
    val enabled: Boolean = true,
    val initialCash: Double = 100000.0,
    val feeRate: Double = 0.0003,
    val slippageRate: Double = 0.0002
)

@Entity
data class BacktestSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val strategyName: String,
    val createdAt: Long,
    val totalReturn: Double,
    val maxDrawdown: Double,
    val winRate: Double,
    val tradeCount: Int,
    val equityCurve: List<Double>,
    val latestSignal: String
)
