package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.RecurringRuleDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Database(
    entities =
        [
            CategoryEntity::class,
            TransactionEntity::class,
            RecurringRuleEntity::class,
            ArchivedPeriodEntity::class,
        ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun recurringRuleDao(): RecurringRuleDao

    abstract fun archivedPeriodDao(): ArchivedPeriodDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recurring_rules` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL,
                          `day_of_month` INTEGER NOT NULL,
                          `amount_minor` INTEGER NOT NULL,
                          `is_income` INTEGER NOT NULL,
                          `category_id` INTEGER NOT NULL,
                          `memo` TEXT NOT NULL,
                          `enabled` INTEGER NOT NULL,
                          `last_applied_year_month` TEXT,
                          FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_recurring_rules_category_id` " +
                            "ON `recurring_rules` (`category_id`)",
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `archived_periods` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `start_epoch_day` INTEGER NOT NULL,
                          `end_epoch_day` INTEGER NOT NULL,
                          `total_income_minor` INTEGER NOT NULL,
                          `total_expense_minor` INTEGER NOT NULL,
                          `archived_at_epoch_ms` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_archived_periods_start_end` " +
                            "ON `archived_periods` (`start_epoch_day`, `end_epoch_day`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_archived_periods_start` " +
                            "ON `archived_periods` (`start_epoch_day`)",
                    )
                }
            }

        fun getInstance(context: Context): AppDatabase {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "household_budget.db",
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build()
                            .also { db ->
                                instance = db
                                runBlocking(Dispatchers.IO) {
                                    seedCategoriesIfEmpty(db.categoryDao())
                                }
                            }
                }
        }

        private suspend fun seedCategoriesIfEmpty(categoryDao: CategoryDao) {
            if (categoryDao.count() > 0) return
            val defaults =
                listOf(
                    CategoryEntity(name = "월급", isIncome = true, sortOrder = 0),
                    CategoryEntity(name = "기타 수입", isIncome = true, sortOrder = 1),
                    CategoryEntity(name = "식비", isIncome = false, sortOrder = 10),
                    CategoryEntity(name = "교통", isIncome = false, sortOrder = 11),
                    CategoryEntity(name = "통신", isIncome = false, sortOrder = 12),
                    CategoryEntity(name = "쇼핑", isIncome = false, sortOrder = 13),
                    CategoryEntity(name = "문화/여가", isIncome = false, sortOrder = 14),
                    CategoryEntity(name = "의료", isIncome = false, sortOrder = 15),
                    CategoryEntity(name = "기타", isIncome = false, sortOrder = 99),
                )
            categoryDao.insertAll(defaults)
        }
    }
}
