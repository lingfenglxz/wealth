package com.demo.wealth.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
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

@Entity(indices = [Index(value = ["matchId"], unique = true)])
data class FootballMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: String,
    val matchNum: String,
    val leagueName: String,
    val phase: String,
    val kickoffTime: String,
    val homeTeam: String,
    val awayTeam: String,
    val handicap: Int,
    val poolsJson: String,
    @ColumnInfo(defaultValue = "''") val stadium: String,
    @ColumnInfo(defaultValue = "''") val city: String,
    val source: String,
    val updatedAt: String
)

@Entity
data class FootballRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val matchId: String,
    val matchNum: String,
    val leagueName: String,
    val phase: String,
    val kickoffTime: String,
    val homeTeam: String,
    val awayTeam: String,
    val playType: String,
    val playName: String,
    @ColumnInfo(defaultValue = "''") val modelName: String,
    val selection: String,
    val odds: Double,
    val confidence: Double,
    @ColumnInfo(defaultValue = "0.0") val fairProbability: Double,
    @ColumnInfo(defaultValue = "0.0") val modelProbability: Double,
    @ColumnInfo(defaultValue = "0.0") val edge: Double,
    @ColumnInfo(defaultValue = "0.0") val dataQuality: Double,
    @ColumnInfo(defaultValue = "0.0") val homeExpectedGoals: Double,
    @ColumnInfo(defaultValue = "0.0") val awayExpectedGoals: Double,
    val reasonsJson: String
)
