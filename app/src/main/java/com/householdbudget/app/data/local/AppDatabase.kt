package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.AutoEntryDao
import com.householdbudget.app.data.local.dao.CategoryBudgetDao
import com.householdbudget.app.data.local.dao.MerchantRuleDao
import com.householdbudget.app.data.local.dao.NotificationLogDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.NotificationLogEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.domain.category.FixedCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Database(
    entities =
        [
            CategoryEntity::class,
            TransactionEntity::class,
            ArchivedPeriodEntity::class,
            CategoryBudgetEntity::class,
            NotificationLogEntity::class,
            AutoEntryEntity::class,
            MerchantRuleEntity::class,
        ],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun archivedPeriodDao(): ArchivedPeriodDao

    abstract fun categoryBudgetDao(): CategoryBudgetDao

    abstract fun notificationLogDao(): NotificationLogDao

    abstract fun autoEntryDao(): AutoEntryDao

    abstract fun merchantRuleDao(): MerchantRuleDao

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

        /**
         * v3 → v4: 카테고리에 parent/kind 추가, 거래에 kind 추가, archived_periods에 savings 컬럼 추가.
         *
         * 데이터 변환 전략:
         * 1. 기존 categories의 모든 행을 대분류로 보존, kind는 is_income 매핑.
         * 2. 각 대분류 아래 "기본" 소분류 1개 자동 생성. 기존 거래/반복규칙은 그 "기본" leaf로 재연결.
         * 3. transactions/recurring_rules의 is_income 컬럼은 kind 컬럼으로 대체.
         */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // ── categories 테이블 재생성 (parent_id, kind 추가, is_income 제거) ──
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `categories_new` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL,
                          `kind` TEXT NOT NULL,
                          `parent_id` INTEGER,
                          `sort_order` INTEGER NOT NULL,
                          FOREIGN KEY(`parent_id`) REFERENCES `categories_new`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    // 기존 카테고리들을 대분류(parent_id=NULL)로 복사. is_income → kind 변환.
                    db.execSQL(
                        """
                        INSERT INTO `categories_new` (id, name, kind, parent_id, sort_order)
                        SELECT id, name,
                               CASE WHEN is_income = 1 THEN 'INCOME' ELSE 'EXPENSE' END,
                               NULL, sort_order
                        FROM `categories`
                        """.trimIndent(),
                    )

                    // 각 기존 대분류 아래 "기본" 소분류 자동 생성하기 위해 임시 매핑 테이블 사용.
                    db.execSQL(
                        """
                        CREATE TEMP TABLE IF NOT EXISTS `_cat_remap` (
                          old_id INTEGER PRIMARY KEY NOT NULL,
                          new_leaf_id INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    // 커서를 연 채로 INSERT하면 새 자식 행까지 반복되어 버그가 난다.
                    // 먼저 parent 후보를 모두 수집한 후 INSERT.
                    val parentCandidates = mutableListOf<Pair<Long, String>>()
                    db.query("SELECT id, kind FROM `categories_new` ORDER BY id ASC").use { c ->
                        while (c.moveToNext()) {
                            parentCandidates.add(c.getLong(0) to c.getString(1))
                        }
                    }
                    for ((parentId, kind) in parentCandidates) {
                        db.execSQL(
                            "INSERT INTO `categories_new` (name, kind, parent_id, sort_order) " +
                                "VALUES ('기본', ?, ?, 0)",
                            arrayOf<Any>(kind, parentId),
                        )
                        db.query("SELECT last_insert_rowid()").use { rc ->
                            rc.moveToFirst()
                            val newLeafId = rc.getLong(0)
                            db.execSQL(
                                "INSERT INTO `_cat_remap` (old_id, new_leaf_id) VALUES (?, ?)",
                                arrayOf<Any>(parentId, newLeafId),
                            )
                        }
                    }

                    // ── transactions 재생성 (is_income → kind, category_id를 leaf로 remap) ──
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transactions_new` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `occurred_epoch_day` INTEGER NOT NULL,
                          `amount_minor` INTEGER NOT NULL,
                          `kind` TEXT NOT NULL,
                          `category_id` INTEGER NOT NULL,
                          `memo` TEXT NOT NULL,
                          FOREIGN KEY(`category_id`) REFERENCES `categories_new`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `transactions_new` (id, occurred_epoch_day, amount_minor, kind, category_id, memo)
                        SELECT t.id,
                               t.occurred_epoch_day,
                               t.amount_minor,
                               CASE WHEN t.is_income = 1 THEN 'INCOME' ELSE 'EXPENSE' END,
                               r.new_leaf_id,
                               t.memo
                        FROM `transactions` t
                        JOIN `_cat_remap` r ON r.old_id = t.category_id
                        """.trimIndent(),
                    )

                    // ── recurring_rules 재생성 ──
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recurring_rules_new` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL,
                          `day_of_month` INTEGER NOT NULL,
                          `amount_minor` INTEGER NOT NULL,
                          `kind` TEXT NOT NULL,
                          `category_id` INTEGER NOT NULL,
                          `memo` TEXT NOT NULL,
                          `enabled` INTEGER NOT NULL,
                          `last_applied_year_month` TEXT,
                          FOREIGN KEY(`category_id`) REFERENCES `categories_new`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `recurring_rules_new`
                          (id, name, day_of_month, amount_minor, kind, category_id, memo, enabled, last_applied_year_month)
                        SELECT rr.id, rr.name, rr.day_of_month, rr.amount_minor,
                               CASE WHEN rr.is_income = 1 THEN 'INCOME' ELSE 'EXPENSE' END,
                               r.new_leaf_id,
                               rr.memo, rr.enabled, rr.last_applied_year_month
                        FROM `recurring_rules` rr
                        JOIN `_cat_remap` r ON r.old_id = rr.category_id
                        """.trimIndent(),
                    )

                    // ── 기존 테이블 drop, new를 정식 이름으로 rename ──
                    db.execSQL("DROP TABLE `transactions`")
                    db.execSQL("DROP TABLE `recurring_rules`")
                    db.execSQL("DROP TABLE `categories`")
                    db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
                    db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                    db.execSQL("ALTER TABLE `recurring_rules_new` RENAME TO `recurring_rules`")

                    // ── 인덱스 재생성 ──
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_categories_sort_order` ON `categories` (`sort_order`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_categories_kind` ON `categories` (`kind`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_occurred_epoch_day` " +
                            "ON `transactions` (`occurred_epoch_day`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_category_id` " +
                            "ON `transactions` (`category_id`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transactions_kind` ON `transactions` (`kind`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_recurring_rules_category_id` " +
                            "ON `recurring_rules` (`category_id`)",
                    )

                    db.execSQL("DROP TABLE IF EXISTS `_cat_remap`")

                    // ── archived_periods에 total_savings_minor 추가 ──
                    db.execSQL(
                        "ALTER TABLE `archived_periods` ADD COLUMN `total_savings_minor` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `category_budgets` (
                          `category_id` INTEGER PRIMARY KEY NOT NULL,
                          `monthly_amount_minor` INTEGER NOT NULL,
                          `enabled` INTEGER NOT NULL,
                          FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `categories` ADD COLUMN `icon` TEXT")
                    // 기본 시드 카테고리에 이모지 아이콘 적용
                    val icons =
                        mapOf(
                            "월급" to "💰",
                            "기타 수입" to "💵",
                            "식비" to "🍽️",
                            "교통" to "🚌",
                            "통신" to "📱",
                            "쇼핑" to "🛒",
                            "문화/여가" to "🎭",
                            "의료" to "🏥",
                            "기타" to "📦",
                            "저축" to "🏦",
                        )
                    for ((name, icon) in icons) {
                        db.execSQL(
                            "UPDATE `categories` SET `icon` = ? WHERE `name` = ? AND `parent_id` IS NULL",
                            arrayOf(icon, name),
                        )
                    }
                }
            }

        /** v7 → v8: 거래 출처 컬럼, 알림 로그·자동 기록·가맹점 규칙 테이블. */
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `notification_log` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `package_name` TEXT NOT NULL, " +
                            "`notification_key` TEXT NOT NULL, `post_time_ms` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `content_hash` TEXT NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_log_package_name_notification_key_post_time_ms_content_hash` " +
                            "ON `notification_log` (`package_name`, `notification_key`, `post_time_ms`, `content_hash`)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_log_post_time_ms` ON `notification_log` (`post_time_ms`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `auto_entries` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `log_id` INTEGER NOT NULL, " +
                            "`package_name` TEXT NOT NULL, `occurred_epoch_day` INTEGER NOT NULL, " +
                            "`posted_at_ms` INTEGER NOT NULL, `amount_minor` INTEGER NOT NULL, " +
                            "`direction` TEXT NOT NULL, `merchant` TEXT NOT NULL, `merchant_key` TEXT NOT NULL, " +
                            "`status` TEXT NOT NULL, `reason` TEXT, `transaction_id` INTEGER)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_entries_status` ON `auto_entries` (`status`)")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_auto_entries_occurred_epoch_day_amount_minor` " +
                            "ON `auto_entries` (`occurred_epoch_day`, `amount_minor`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `merchant_rules` (" +
                            "`merchant_key` TEXT NOT NULL, `direction` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
                            "`category_id` INTEGER, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`merchant_key`, `direction`))",
                    )
                }
            }

        internal val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        fun getInstance(context: Context): AppDatabase {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "household_budget.db",
                            )
                            .addMigrations(*ALL_MIGRATIONS)
                            .build()
                            .also { db ->
                                instance = db
                                runBlocking(Dispatchers.IO) {
                                    seedCategoriesIfEmpty(db.categoryDao())
                                }
                            }
                }
        }

        /**
         * 신규 설치 시드: 비어 있을 때만 [FixedCategories.TREE]를 넣는다.
         * 고정 대분류 아래의 고정 아닌 세부는 사용자가 만든 것이다. 시드·보충 로직은 지우거나 바꾸지 않는다.
         */
        internal suspend fun seedCategoriesIfEmpty(categoryDao: CategoryDao) {
            if (categoryDao.count() > 0) return

            FixedCategories.TREE.forEachIndexed { parentSort, seed ->
                val parentId =
                    categoryDao.insert(
                        CategoryEntity(
                            name = seed.name,
                            kind = seed.kind.storage,
                            parentId = null,
                            sortOrder = parentSort,
                            icon = seed.icon,
                        ),
                    )
                seed.children.forEachIndexed { idx, childName ->
                    categoryDao.insert(
                        CategoryEntity(
                            name = childName,
                            kind = seed.kind.storage,
                            parentId = parentId,
                            sortOrder = idx,
                        ),
                    )
                }
            }
        }
    }
}
