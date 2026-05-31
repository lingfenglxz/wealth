package com.demo.wealth.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LotteryDraw::class,
        LotteryPrediction::class,
        LotteryResearchSnapshot::class,
        LotterySettlement::class,
        StockSymbol::class,
        StockCandle::class,
        StrategyConfig::class,
        BacktestSnapshot::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wealthDao(): WealthDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `LotteryResearchSnapshot` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `targetIssue` TEXT NOT NULL,
                        `sourceIssue` TEXT NOT NULL,
                        `modelVersion` TEXT NOT NULL,
                        `reportJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_LotteryResearchSnapshot_targetIssue` ON `LotteryResearchSnapshot` (`targetIssue`)"
                )
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `LotterySettlement` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `issue` TEXT NOT NULL,
                        `drawDate` TEXT NOT NULL,
                        `settledAt` INTEGER NOT NULL,
                        `betCount` INTEGER NOT NULL,
                        `investedAmount` REAL NOT NULL,
                        `simulatedPrizeAmount` REAL NOT NULL,
                        `roi` REAL NOT NULL,
                        `bestRedHits` INTEGER NOT NULL,
                        `blueHit` INTEGER NOT NULL,
                        `tierCountsJson` TEXT NOT NULL,
                        `detailJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_LotterySettlement_issue` ON `LotterySettlement` (`issue`)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wealth-lab.db"
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
