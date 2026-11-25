package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.entity.CashEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration8To9Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test-v9.db"
    private var room: AppDatabase? = null

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    @Test fun migratesToV9_keepsExistingData_andAddsEmptyCashTable() = runBlocking {
        context.deleteDatabase(dbName)
        V6Schema.create(context, dbName).apply {
            execSQL("INSERT INTO categories (id, name, kind, parent_id, sort_order) VALUES (1, '식비', 'EXPENSE', NULL, 0)")
            execSQL("INSERT INTO categories (id, name, kind, parent_id, sort_order) VALUES (2, '식사', 'EXPENSE', 1, 0)")
            execSQL("INSERT INTO transactions (occurred_epoch_day, amount_minor, kind, category_id, memo) VALUES (20000, 9000, 'EXPENSE', 2, '점심')")
            close()
        }
        // v6 → v8 → v9 를 모두 거친다. Room 이 첫 접근 때 v9 스키마(cash_entries 포함)와 대조한다.
        val db =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        room = db
        val tx = db.transactionDao().getById(1)!!
        assertEquals(9000L to "점심", tx.amountMinor to tx.memo)
        assertEquals(0L, db.cashEntryDao().balance())

        db.cashEntryDao().insert(CashEntryEntity(occurredEpochDay = 20000, amountMinor = 5000, createdAtMs = 1))
        assertEquals("", db.cashEntryDao().observeAll().first().single().memo)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cash_entries (occurred_epoch_day, amount_minor, created_at_ms) VALUES (20001, -1000, 2)",
        )
        assertEquals(4000L, db.cashEntryDao().balance())
    }
}
