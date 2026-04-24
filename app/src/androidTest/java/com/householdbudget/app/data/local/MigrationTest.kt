package com.householdbudget.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration_test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /**
     * v3 스키마를 직접 구성 후 기본 카테고리와 거래/반복규칙을 시드하고
     * MIGRATION_3_4를 적용해 데이터가 제대로 변환되는지 검증한다.
     */
    @Test
    fun migrate_3_to_4_preserves_data_and_creates_hierarchy() {
        // ── v3 스키마 직접 CREATE ──
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `name` TEXT NOT NULL,
                  `is_income` INTEGER NOT NULL,
                  `sort_order` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_categories_sort_order` ON `categories` (`sort_order`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactions` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `occurred_epoch_day` INTEGER NOT NULL,
                  `amount_minor` INTEGER NOT NULL,
                  `is_income` INTEGER NOT NULL,
                  `category_id` INTEGER NOT NULL,
                  `memo` TEXT NOT NULL,
                  FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
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

            // 카테고리 시드: 월급(수입), 식비(지출), 기타(지출)
            db.execSQL("INSERT INTO categories (id, name, is_income, sort_order) VALUES (1, '월급', 1, 0)")
            db.execSQL("INSERT INTO categories (id, name, is_income, sort_order) VALUES (2, '식비', 0, 10)")
            db.execSQL("INSERT INTO categories (id, name, is_income, sort_order) VALUES (3, '기타', 0, 99)")

            // 거래 시드
            db.execSQL(
                "INSERT INTO transactions (occurred_epoch_day, amount_minor, is_income, category_id, memo) " +
                    "VALUES (19800, 2500000, 1, 1, '월급')",
            )
            db.execSQL(
                "INSERT INTO transactions (occurred_epoch_day, amount_minor, is_income, category_id, memo) " +
                    "VALUES (19801, 15000, 0, 2, '점심')",
            )
            db.execSQL(
                "INSERT INTO transactions (occurred_epoch_day, amount_minor, is_income, category_id, memo) " +
                    "VALUES (19801, 3000, 0, 3, '')",
            )

            // 반복 규칙 시드
            db.execSQL(
                "INSERT INTO recurring_rules (name, day_of_month, amount_minor, is_income, category_id, memo, enabled, last_applied_year_month) " +
                    "VALUES ('월세', 1, 500000, 0, 3, '', 1, NULL)",
            )
        }

        // ── MIGRATION_3_4 적용 ──
        val migrated =
            helper.runMigrationsAndValidate(
                dbName,
                4,
                /* validateDroppedTables = */ true,
                *AppDatabase.MIGRATIONS,
            )

        // ── categories 검증: 3개 대분류 + 3개 "기본" 소분류 = 6행 ──
        migrated.query(
            "SELECT COUNT(*) FROM categories WHERE parent_id IS NULL",
        ).use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
        migrated.query(
            "SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL AND name = '기본'",
        ).use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }

        // kind 변환 검증
        migrated.query(
            "SELECT kind FROM categories WHERE id = 1",
        ).use { c ->
            c.moveToFirst()
            assertEquals("INCOME", c.getString(0))
        }
        migrated.query(
            "SELECT kind FROM categories WHERE id = 2",
        ).use { c ->
            c.moveToFirst()
            assertEquals("EXPENSE", c.getString(0))
        }

        // ── transactions: 거래 3건 모두 leaf를 가리키는지 ──
        migrated.query(
            "SELECT COUNT(*) FROM transactions t " +
                "JOIN categories c ON c.id = t.category_id " +
                "WHERE c.parent_id IS NULL",
        ).use { c ->
            c.moveToFirst()
            assertEquals("모든 거래는 leaf(parent_id IS NOT NULL)를 가리켜야 함", 0, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }

        // kind 복사 확인
        migrated.query(
            "SELECT kind FROM transactions WHERE amount_minor = 2500000",
        ).use { c ->
            c.moveToFirst()
            assertEquals("INCOME", c.getString(0))
        }
        migrated.query(
            "SELECT kind FROM transactions WHERE amount_minor = 15000",
        ).use { c ->
            c.moveToFirst()
            assertEquals("EXPENSE", c.getString(0))
        }

        // ── recurring_rules 검증 ──
        migrated.query(
            "SELECT kind, category_id FROM recurring_rules WHERE name = '월세'",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("EXPENSE", c.getString(0))
            // "기타" 대분류 밑 "기본" leaf를 가리켜야 함
            val leafId = c.getLong(1)
            migrated.query(
                "SELECT parent_id, name FROM categories WHERE id = $leafId",
            ).use { cc ->
                cc.moveToFirst()
                assertEquals(3L, cc.getLong(0))
                assertEquals("기본", cc.getString(1))
            }
        }

        // ── archived_periods total_savings_minor 컬럼 기본값 ──
        migrated.execSQL(
            "INSERT INTO archived_periods " +
                "(start_epoch_day, end_epoch_day, total_income_minor, total_expense_minor, archived_at_epoch_ms) " +
                "VALUES (19700, 19730, 1000, 500, 0)",
        )
        migrated.query(
            "SELECT total_savings_minor FROM archived_periods LIMIT 1",
        ).use { c ->
            c.moveToFirst()
            assertEquals(0L, c.getLong(0))
        }

        migrated.close()
    }

    /** v4 스키마를 Room 자체로 열면 스키마 검증이 통과해야 한다. */
    @Test
    fun open_v4_schema_with_room_validates() {
        helper.createDatabase(dbName, 4).close()
        val db =
            Room.databaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java,
                    dbName,
                )
                .addMigrations(*AppDatabase.MIGRATIONS)
                .build()
        assertNotNull(db.categoryDao())
        db.close()
    }
}
