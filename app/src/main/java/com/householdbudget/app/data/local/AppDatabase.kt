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
    version = 4,
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

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // FK 비활성 상태(Room 마이그레이션 기본값)에서 카테고리 초기화
                    db.execSQL("DELETE FROM categories")
                    val expenseCategories = listOf(
                        Triple("식비", 0, 10),
                        Triple("교통비", 0, 11),
                        Triple("통신비", 0, 12),
                        Triple("월세", 0, 13),
                        Triple("생활비", 0, 14),
                        Triple("경조비", 0, 15),
                        Triple("문화비", 0, 16),
                        Triple("보험", 0, 17),
                        Triple("투자", 0, 18),
                        Triple("청약저축", 0, 19),
                        Triple("연금저축", 0, 20),
                        Triple("기타", 0, 99),
                    )
                    val incomeCategories = listOf(
                        Triple("월급", 1, 0),
                        Triple("기타 수입", 1, 1),
                    )
                    for ((name, isIncome, sortOrder) in incomeCategories + expenseCategories) {
                        db.execSQL(
                            "INSERT INTO categories (name, is_income, sort_order) VALUES ('$name', $isIncome, $sortOrder)"
                        )
                    }
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
                    CategoryEntity(name = "교통비", isIncome = false, sortOrder = 11),
                    CategoryEntity(name = "통신비", isIncome = false, sortOrder = 12),
                    CategoryEntity(name = "월세", isIncome = false, sortOrder = 13),
                    CategoryEntity(name = "생활비", isIncome = false, sortOrder = 14),
                    CategoryEntity(name = "경조비", isIncome = false, sortOrder = 15),
                    CategoryEntity(name = "문화비", isIncome = false, sortOrder = 16),
                    CategoryEntity(name = "보험", isIncome = false, sortOrder = 17),
                    CategoryEntity(name = "투자", isIncome = false, sortOrder = 18),
                    CategoryEntity(name = "청약저축", isIncome = false, sortOrder = 19),
                    CategoryEntity(name = "연금저축", isIncome = false, sortOrder = 20),
                    CategoryEntity(name = "기타", isIncome = false, sortOrder = 99),
                )
            categoryDao.insertAll(defaults)
        }
    }
}
