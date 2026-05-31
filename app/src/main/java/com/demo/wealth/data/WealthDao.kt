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

    @Query("SELECT * FROM StockSymbol ORDER BY symbol")
    fun observeSymbols(): Flow<List<StockSymbol>>

    @Query("SELECT * FROM StockSymbol ORDER BY symbol")
    suspend fun getSymbols(): List<StockSymbol>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSymbols(symbols: List<StockSymbol>)

    @Query("SELECT * FROM StockCandle WHERE symbol = :symbol ORDER BY date")
    fun observeCandles(symbol: String): Flow<List<StockCandle>>

    @Query("SELECT * FROM StockCandle WHERE symbol = :symbol ORDER BY date")
    suspend fun getCandles(symbol: String): List<StockCandle>

    @Query("SELECT * FROM StockCandle ORDER BY symbol, date")
    suspend fun getAllCandles(): List<StockCandle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(candles: List<StockCandle>)

    @Query("SELECT * FROM StrategyConfig WHERE symbol = :symbol")
    suspend fun getStrategyConfigs(symbol: String): List<StrategyConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStrategyConfig(config: StrategyConfig)

    @Query("SELECT * FROM BacktestSnapshot ORDER BY createdAt DESC")
    fun observeBacktests(): Flow<List<BacktestSnapshot>>

    @Query("SELECT * FROM BacktestSnapshot ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getBacktests(limit: Int = 20): List<BacktestSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktest(snapshot: BacktestSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktests(snapshots: List<BacktestSnapshot>)
}
