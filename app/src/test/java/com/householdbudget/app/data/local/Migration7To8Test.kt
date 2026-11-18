package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.AutoEntryStatus
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.NotificationLogEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration7To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test-v8.db"
    private var room: AppDatabase? = null

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    @Test fun migratesFromV6ToV8_andKeepsExistingTransactionsAsManual() {
        context.deleteDatabase(dbName)
        V6Schema.create(context, dbName).apply {
            execSQL("INSERT INTO categories (id, name, kind, parent_id, sort_order) VALUES (1, '식비', 'EXPENSE', NULL, 0)")
            execSQL("INSERT INTO categories (id, name, kind, parent_id, sort_order) VALUES (2, '식사', 'EXPENSE', 1, 0)")
            execSQL("INSERT INTO transactions (occurred_epoch_day, amount_minor, kind, category_id, memo) VALUES (20000, 9000, 'EXPENSE', 2, '')")
            close()
        }
        val db =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        room = db
        // 스키마 대조는 첫 접근 때 일어난다.
        assertEquals("MANUAL", db.openHelper.readableDatabase.query("SELECT source FROM transactions").use { it.moveToFirst(); it.getString(0) })
    }

    @Test fun notificationLog_ignoresExactDuplicate() = runBlocking {
        val db = memDb()
        val e = NotificationLogEntity(packageName = "pkg.a", notificationKey = "k", postTimeMs = 1, title = "t", body = "b", contentHash = "h")
        assertEquals(true, db.notificationLogDao().insert(e) > 0)
        assertEquals(-1L, db.notificationLogDao().insert(e))
        assertEquals(1, db.notificationLogDao().count())
    }

    @Test fun countCrossAppOut_excludesSamePackageAndIgnored() = runBlocking {
        val dao = memDb().autoEntryDao()
        dao.insert(auto("pkg.a", AutoEntryStatus.REGISTERED))
        dao.insert(auto("pkg.c", AutoEntryStatus.IGNORED))
        assertEquals(1, dao.countCrossAppOut(12000, 20000, "pkg.b", 0, 10_000))
        assertEquals(0, dao.countCrossAppOut(12000, 20000, "pkg.a", 0, 10_000))
    }

    @Test fun countManualMatch_ignoresAutoTransactions() = runBlocking {
        val db = memDb()
        val leaf = db.categoryDao().findLeafByPath("EXPENSE", "식비", "식사")!!.id
        fun tx(source: String) = TransactionEntity(occurredEpochDay = 20000, amountMinor = 4500, kind = "EXPENSE", categoryId = leaf, source = source)
        db.transactionDao().insert(tx(TransactionSource.MANUAL))
        db.transactionDao().insert(tx(TransactionSource.AUTO))
        assertEquals(1, db.transactionDao().countManualMatch(20000, 4500, "EXPENSE"))
    }

    @Test fun merchantRule_isKeyedByNameAndDirection() = runBlocking {
        val db = memDb()
        val dao = db.merchantRuleDao()
        val meal = db.categoryDao().findLeafByPath("EXPENSE", "식비", "식사")!!.id
        dao.upsert(MerchantRuleEntity("김가짜", "OUT", "김가짜", meal, 1))
        dao.upsert(MerchantRuleEntity("김가짜", "IN", "김가짜", null, 1))
        assertEquals(2, dao.count())
        assertEquals(meal, dao.getByKey("김가짜", "OUT")!!.categoryId)
        assertNull(dao.getByKey("김가짜", "REFUND"))
        dao.deleteByKey("김가짜", "OUT")
        assertEquals(true, dao.getByKey("김가짜", "IN") != null)
    }

    @Test fun merchantRuleRow_joinsCategoryNames() = runBlocking {
        val db = memDb()
        val cafe = db.categoryDao().findLeafByPath("EXPENSE", "식비", "카페")!!.id
        db.merchantRuleDao().upsert(MerchantRuleEntity("가짜커피", "OUT", "가짜커피", cafe, 1))
        db.merchantRuleDao().upsert(MerchantRuleEntity("무시", "OUT", "무시", null, 1))
        val rows = db.merchantRuleDao().observeAll().first().associateBy { it.merchantKey }
        assertEquals("카페" to "식비", rows.getValue("가짜커피").let { it.categoryName to it.parentCategoryName })
        assertEquals(null to null, rows.getValue("무시").let { it.categoryName to it.parentCategoryName })
    }

    private fun auto(pkg: String, status: String) =
        AutoEntryEntity(
            logId = 1, packageName = pkg, occurredEpochDay = 20000, postedAtMs = 5000, amountMinor = 12000,
            direction = "OUT", merchant = "가짜커피", merchantKey = "가짜커피", status = status,
        )

    private fun memDb(): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        room = db
        runBlocking { AppDatabase.seedCategoriesIfEmpty(db.categoryDao()) }
        return db
    }
}
