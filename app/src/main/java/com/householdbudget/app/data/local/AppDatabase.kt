package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryBudgetDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.RecurringRuleDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.domain.CategoryKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Database(
    entities =
        [
            CategoryEntity::class,
            TransactionEntity::class,
            RecurringRuleEntity::class,
            ArchivedPeriodEntity::class,
            CategoryBudgetEntity::class,
        ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun recurringRuleDao(): RecurringRuleDao

    abstract fun archivedPeriodDao(): ArchivedPeriodDao

    abstract fun categoryBudgetDao(): CategoryBudgetDao

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

        fun getInstance(context: Context): AppDatabase {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "household_budget.db",
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
         * 기본 카테고리 트리 버전. 트리를 확장할 때마다 올린다.
         * [topUpDefaultCategories] 실행 여부를 UserPreferences 의 categorySeedVersion 과 비교해 결정한다.
         */
        const val CATEGORY_SEED_VERSION = 2

        /** 대분류 → 소분류 기본 트리. 신규 설치 시드와 기존 설치 부족분 채우기(top-up)가 공유한다. */
        private data class DefaultSeed(
            val kind: CategoryKind,
            val parent: String,
            val icon: String,
            val children: List<String>,
        )

        private val DEFAULT_CATEGORY_SEEDS =
            listOf(
                // ── 수입 ─────────────────────────────────────────────────────
                DefaultSeed(CategoryKind.INCOME, "월급", "💰", listOf("기본", "상여/보너스")),
                DefaultSeed(CategoryKind.INCOME, "부수입", "💵", listOf("이자/배당", "캐시백/포인트", "중고 판매", "용돈")),
                DefaultSeed(CategoryKind.INCOME, "기타 수입", "🧧", listOf("기본", "환급/지원금")),
                // ── 지출 ─────────────────────────────────────────────────────
                DefaultSeed(CategoryKind.EXPENSE, "식비", "🍽️", listOf("식사", "카페", "간식", "배달", "술/모임")),
                DefaultSeed(CategoryKind.EXPENSE, "생활/마트", "🧺", listOf("장보기", "생필품", "가전/가구")),
                DefaultSeed(CategoryKind.EXPENSE, "주거/공과금", "🏠", listOf("월세/관리비", "전기/가스", "수도", "인터넷/TV")),
                DefaultSeed(CategoryKind.EXPENSE, "통신", "📱", listOf("휴대폰", "구독 서비스")),
                DefaultSeed(CategoryKind.EXPENSE, "교통", "🚌", listOf("대중교통", "택시", "기차/시외버스")),
                DefaultSeed(CategoryKind.EXPENSE, "차량", "🚗", listOf("주유", "주차/통행료", "정비/세차", "보험/자동차세")),
                DefaultSeed(CategoryKind.EXPENSE, "쇼핑", "🛒", listOf("의류/패션", "뷰티/미용", "온라인 쇼핑")),
                DefaultSeed(CategoryKind.EXPENSE, "문화/여가", "🎭", listOf("영화/공연", "취미", "운동/헬스", "게임")),
                DefaultSeed(CategoryKind.EXPENSE, "여행", "✈️", listOf("숙소", "항공/교통", "현지 경비")),
                DefaultSeed(CategoryKind.EXPENSE, "의료", "🏥", listOf("병원", "약국", "영양제/건강식품")),
                DefaultSeed(CategoryKind.EXPENSE, "교육", "📚", listOf("도서", "강의/학원", "시험/자격증")),
                DefaultSeed(CategoryKind.EXPENSE, "경조사/선물", "🎁", listOf("경조사비", "선물")),
                DefaultSeed(CategoryKind.EXPENSE, "금융", "💳", listOf("보험료", "수수료/이자", "세금")),
                DefaultSeed(CategoryKind.EXPENSE, "반려동물", "🐾", listOf("사료/간식", "병원/미용")),
                DefaultSeed(CategoryKind.EXPENSE, "기타", "📦", listOf("기본")),
                // ── 저축 ─────────────────────────────────────────────────────
                DefaultSeed(CategoryKind.SAVINGS, "적금/예금", "🏦", listOf("적금", "예금", "파킹통장")),
                DefaultSeed(CategoryKind.SAVINGS, "투자", "📈", listOf("주식", "펀드/ETF", "코인")),
                DefaultSeed(CategoryKind.SAVINGS, "연금", "🪙", listOf("연금저축", "IRP")),
                DefaultSeed(CategoryKind.SAVINGS, "청약", "🏗️", listOf("기본")),
                DefaultSeed(CategoryKind.SAVINGS, "비상금", "🚨", listOf("기본")),
            )

        private suspend fun seedCategoriesIfEmpty(categoryDao: CategoryDao) {
            if (categoryDao.count() > 0) return

            var parentSort = 0
            for (seed in DEFAULT_CATEGORY_SEEDS) {
                val parentId =
                    categoryDao.insert(
                        CategoryEntity(
                            name = seed.parent,
                            kind = seed.kind.storage,
                            parentId = null,
                            sortOrder = parentSort++,
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

        /**
         * 기존 설치에 기본 트리의 **부족분만** 추가한다 (앱 업데이트로 기본 카테고리가 확장된 경우).
         * 이름 기준으로 비교하므로 사용자가 만들거나 이름을 바꾼 카테고리는 건드리지 않고,
         * 사용자가 지운 기본 카테고리는 시드 버전이 올라간 딱 한 번만 다시 생길 수 있다.
         */
        suspend fun topUpDefaultCategories(categoryDao: CategoryDao) {
            for (seed in DEFAULT_CATEGORY_SEEDS) {
                val existing = categoryDao.findTopLevelByName(seed.kind.storage, seed.parent)
                val parentId =
                    if (existing == null) {
                        val sort = (categoryDao.maxTopLevelSortOrder(seed.kind.storage) ?: -1) + 1
                        categoryDao.insert(
                            CategoryEntity(
                                name = seed.parent,
                                kind = seed.kind.storage,
                                parentId = null,
                                sortOrder = sort,
                                icon = seed.icon,
                            ),
                        )
                    } else {
                        if (existing.icon == null) {
                            categoryDao.update(existing.copy(icon = seed.icon))
                        }
                        existing.id
                    }
                var childSort = (categoryDao.maxChildSortOrder(parentId) ?: -1) + 1
                for (childName in seed.children) {
                    if (categoryDao.findChildByName(parentId, childName) == null) {
                        categoryDao.insert(
                            CategoryEntity(
                                name = childName,
                                kind = seed.kind.storage,
                                parentId = parentId,
                                sortOrder = childSort++,
                            ),
                        )
                    }
                }
            }
        }
    }
}
