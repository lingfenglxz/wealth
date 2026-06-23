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
        FootballMatchEntity::class,
        FootballRecommendationEntity::class
    ],
    version = 12,
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
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `StockSymbol`")
                db.execSQL("DROP TABLE IF EXISTS `StockCandle`")
                db.execSQL("DROP TABLE IF EXISTS `StrategyConfig`")
                db.execSQL("DROP TABLE IF EXISTS `BacktestSnapshot`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `FootballMatchEntity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `matchId` TEXT NOT NULL,
                        `matchNum` TEXT NOT NULL,
                        `leagueName` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `kickoffTime` TEXT NOT NULL,
                        `homeTeam` TEXT NOT NULL,
                        `awayTeam` TEXT NOT NULL,
                        `handicap` INTEGER NOT NULL,
                        `poolsJson` TEXT NOT NULL,
                        `stadium` TEXT NOT NULL DEFAULT '',
                        `city` TEXT NOT NULL DEFAULT '',
                        `source` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_FootballMatchEntity_matchId` ON `FootballMatchEntity` (`matchId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `FootballRecommendationEntity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `matchId` TEXT NOT NULL,
                        `matchNum` TEXT NOT NULL,
                        `leagueName` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `kickoffTime` TEXT NOT NULL,
                        `homeTeam` TEXT NOT NULL,
                        `awayTeam` TEXT NOT NULL,
                        `playType` TEXT NOT NULL,
                        `playName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL DEFAULT '',
                        `selection` TEXT NOT NULL,
                        `odds` REAL NOT NULL,
                        `confidence` REAL NOT NULL,
                        `fairProbability` REAL NOT NULL DEFAULT 0.0,
                        `modelProbability` REAL NOT NULL DEFAULT 0.0,
                        `edge` REAL NOT NULL DEFAULT 0.0,
                        `dataQuality` REAL NOT NULL DEFAULT 0.0,
                        `homeExpectedGoals` REAL NOT NULL DEFAULT 0.0,
                        `awayExpectedGoals` REAL NOT NULL DEFAULT 0.0,
                        `reasonsJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `fairProbability` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `modelProbability` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `edge` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `dataQuality` REAL NOT NULL DEFAULT 0.0")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `FootballMatchEntity` ADD COLUMN `stadium` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `FootballMatchEntity` ADD COLUMN `city` TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `homeExpectedGoals` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `FootballRecommendationEntity` ADD COLUMN `awayExpectedGoals` REAL NOT NULL DEFAULT 0.0")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `LotteryPrediction` ADD COLUMN `runId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `LotteryResearchSnapshot` ADD COLUMN `runId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `LotterySettlement` ADD COLUMN `runId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS `index_LotteryResearchSnapshot_targetIssue`")
                db.execSQL("DROP INDEX IF EXISTS `index_LotterySettlement_issue`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LotteryResearchSnapshot_targetIssue_runId` ON `LotteryResearchSnapshot` (`targetIssue`, `runId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LotterySettlement_issue_runId` ON `LotterySettlement` (`issue`, `runId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wealth-lab.db"
                ).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
