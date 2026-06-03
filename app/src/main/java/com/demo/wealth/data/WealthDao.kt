package com.demo.wealth.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WealthDao {
    @Query("SELECT * FROM LotteryDraw ORDER BY date DESC, issue DESC")
    fun observeLotteryDraws(): Flow<List<LotteryDraw>>

    @Query("SELECT * FROM LotteryDraw ORDER BY date DESC, issue DESC")
    suspend fun getLotteryDraws(): List<LotteryDraw>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLotteryDraws(draws: List<LotteryDraw>)

    @Query("SELECT * FROM LotteryPrediction WHERE createdAt = (SELECT MAX(createdAt) FROM LotteryPrediction) ORDER BY score DESC")
    fun observePredictions(): Flow<List<LotteryPrediction>>

    @Query("SELECT * FROM LotteryPrediction ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getPredictions(limit: Int = 20): List<LotteryPrediction>

    @Query("SELECT * FROM LotteryPrediction ORDER BY createdAt DESC")
    fun observeAllPredictions(): Flow<List<LotteryPrediction>>

    @Query("SELECT * FROM LotteryPrediction ORDER BY createdAt DESC")
    suspend fun getAllPredictions(): List<LotteryPrediction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<LotteryPrediction>)

    @Query("DELETE FROM LotteryPrediction WHERE targetIssue = :targetIssue")
    suspend fun deletePredictionsForIssue(targetIssue: String)

    @Query("SELECT * FROM LotteryResearchSnapshot ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestResearchSnapshot(): Flow<LotteryResearchSnapshot?>

    @Query("SELECT * FROM LotteryResearchSnapshot ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getResearchSnapshots(limit: Int = 100): List<LotteryResearchSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResearchSnapshot(snapshot: LotteryResearchSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResearchSnapshots(snapshots: List<LotteryResearchSnapshot>)

    @Query("DELETE FROM LotteryResearchSnapshot WHERE targetIssue = :targetIssue")
    suspend fun deleteResearchSnapshotForIssue(targetIssue: String)

    @Query("SELECT * FROM LotterySettlement ORDER BY issue DESC")
    fun observeLotterySettlements(): Flow<List<LotterySettlement>>

    @Query("SELECT * FROM LotterySettlement ORDER BY issue DESC LIMIT :limit")
    suspend fun getLotterySettlements(limit: Int = 1000): List<LotterySettlement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLotterySettlements(settlements: List<LotterySettlement>)

    @Query("DELETE FROM LotterySettlement WHERE issue = :issue")
    suspend fun deleteSettlementForIssue(issue: String)

    @Query("SELECT * FROM FootballMatchEntity ORDER BY kickoffTime")
    fun observeFootballMatches(): Flow<List<FootballMatchEntity>>

    @Query("SELECT * FROM FootballMatchEntity ORDER BY kickoffTime")
    suspend fun getFootballMatches(): List<FootballMatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFootballMatches(matches: List<FootballMatchEntity>)

    @Query("SELECT * FROM FootballRecommendationEntity ORDER BY createdAt DESC, confidence DESC")
    fun observeFootballRecommendations(): Flow<List<FootballRecommendationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFootballRecommendations(recommendations: List<FootballRecommendationEntity>)

    @Query("DELETE FROM FootballRecommendationEntity")
    suspend fun clearFootballRecommendations()
}
