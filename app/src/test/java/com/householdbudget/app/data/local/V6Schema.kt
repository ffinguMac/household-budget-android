package com.householdbudget.app.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** MIGRATION_3_4 ~ MIGRATION_5_6 이 만든 최종 v6 스키마를 손으로 만든다(exportSchema = false라 JSON이 없다). */
object V6Schema {
    fun create(context: Context, name: String): SupportSQLiteDatabase {
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            DDL.forEach(db::execSQL)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private val DDL =
        listOf(
            """
            CREATE TABLE `categories` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `kind` TEXT NOT NULL,
              `parent_id` INTEGER,
              `sort_order` INTEGER NOT NULL,
              `icon` TEXT,
              FOREIGN KEY(`parent_id`) REFERENCES `categories`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """,
            "CREATE INDEX `index_categories_sort_order` ON `categories` (`sort_order`)",
            "CREATE INDEX `index_categories_parent_id` ON `categories` (`parent_id`)",
            "CREATE INDEX `index_categories_kind` ON `categories` (`kind`)",
            """
            CREATE TABLE `transactions` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `occurred_epoch_day` INTEGER NOT NULL,
              `amount_minor` INTEGER NOT NULL,
              `kind` TEXT NOT NULL,
              `category_id` INTEGER NOT NULL,
              `memo` TEXT NOT NULL,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX `index_transactions_occurred_epoch_day` ON `transactions` (`occurred_epoch_day`)",
            "CREATE INDEX `index_transactions_category_id` ON `transactions` (`category_id`)",
            "CREATE INDEX `index_transactions_kind` ON `transactions` (`kind`)",
            """
            CREATE TABLE `recurring_rules` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `day_of_month` INTEGER NOT NULL,
              `amount_minor` INTEGER NOT NULL,
              `kind` TEXT NOT NULL,
              `category_id` INTEGER NOT NULL,
              `memo` TEXT NOT NULL,
              `enabled` INTEGER NOT NULL,
              `last_applied_year_month` TEXT,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """,
            "CREATE INDEX `index_recurring_rules_category_id` ON `recurring_rules` (`category_id`)",
            """
            CREATE TABLE `archived_periods` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `start_epoch_day` INTEGER NOT NULL,
              `end_epoch_day` INTEGER NOT NULL,
              `total_income_minor` INTEGER NOT NULL,
              `total_expense_minor` INTEGER NOT NULL,
              `archived_at_epoch_ms` INTEGER NOT NULL,
              `total_savings_minor` INTEGER NOT NULL DEFAULT 0
            )
            """,
            "CREATE UNIQUE INDEX `index_archived_periods_start_end` ON `archived_periods` (`start_epoch_day`, `end_epoch_day`)",
            "CREATE INDEX `index_archived_periods_start` ON `archived_periods` (`start_epoch_day`)",
            """
            CREATE TABLE `category_budgets` (
              `category_id` INTEGER PRIMARY KEY NOT NULL,
              `monthly_amount_minor` INTEGER NOT NULL,
              `enabled` INTEGER NOT NULL,
              FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """,
        ).map { it.trimIndent() }
}
