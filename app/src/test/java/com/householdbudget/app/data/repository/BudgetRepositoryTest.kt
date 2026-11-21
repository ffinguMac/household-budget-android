package com.householdbudget.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BudgetRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository
    private lateinit var leaf: CategoryEntity
    private var fakeToday = LocalDate.of(2026, 11, 19)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        leaf = db.categoryDao().findLeafByPath("EXPENSE", "식비", "간식")!!
        repo = BudgetRepository(
            db, db.transactionDao(), db.categoryDao(), db.archivedPeriodDao(), db.categoryBudgetDao(),
            UserPreferencesRepository(context), todayProvider = { fakeToday },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun tx(day: Long, amount: Long, kind: String): Long {
        val categoryId = if (kind == "EXPENSE") leaf.id else db.categoryDao().observeAll().first().first { it.kind == kind && it.parentId != null }.id
        return db.transactionDao().insert(TransactionEntity(occurredEpochDay = day, amountMinor = amount, kind = kind, categoryId = categoryId))
    }

    private suspend fun archive(start: Long, end: Long) {
        db.archivedPeriodDao().insert(ArchivedPeriodEntity(startEpochDay = start, endEpochDay = end, totalIncomeMinor = 0, totalExpenseMinor = 0, archivedAtEpochMs = 0))
    }

    @Test fun archivedPeriods_totalsFollowEditedTransactions() = runBlocking {
        archive(S, E)
        val id = tx(S + 3, 10_000, "EXPENSE"); tx(S + 4, 300_000, "INCOME")
        var row = repo.observeArchivedPeriods().first().single()
        assertEquals(10_000L, row.totalExpenseMinor); assertEquals(300_000L, row.totalIncomeMinor)
        repo.updateTransaction(id, LocalDate.ofEpochDay(S + 3), 25_000, leaf.id, "")
        row = repo.observeArchivedPeriods().first().single()
        assertEquals(25_000L, row.totalExpenseMinor)
    }

    @Test fun archivedPeriods_emptyPeriodStaysWithZeroTotals() = runBlocking {
        archive(S, E)
        val row = repo.observeArchivedPeriods().first().single()
        assertEquals(0L, row.totalIncomeMinor + row.totalExpenseMinor + row.totalSavingsMinor)
    }

    @Test fun archivedPeriods_endDayBelongsToNextPeriod() = runBlocking {
        archive(S, E); tx(E, 7_000, "EXPENSE"); tx(E - 1, 1_000, "EXPENSE")
        assertEquals(1_000L, repo.observeArchivedPeriods().first().single().totalExpenseMinor)
    }

    @Test fun currentPeriod_followsTodayAfterRefresh() = runBlocking {
        assertEquals(LocalDate.of(2026, 10, 23), repo.currentPeriod.first().startInclusive)
        fakeToday = LocalDate.of(2026, 11, 25)
        assertEquals(LocalDate.of(2026, 10, 23), repo.currentPeriod.first().startInclusive)
        repo.refreshToday()
        assertEquals(LocalDate.of(2026, 11, 25), repo.currentPeriod.first().startInclusive)
        assertEquals(LocalDate.of(2026, 11, 25), repo.observeHomeSummary().first().period.startInclusive)
    }

    @Test fun observeSummaryAtOffset_minusOne_isPreviousPeriod() = runBlocking {
        tx(S + 1, 5_000, "EXPENSE")
        val s = repo.observeSummaryAtOffset(-1).first()
        assertEquals(LocalDate.of(2026, 9, 23), s.period.startInclusive)
        assertEquals(5_000L, s.totalExpenseMinor); assertEquals(-5_000L, s.netMinor)
    }

    private companion object {
        val S = LocalDate.of(2026, 9, 23).toEpochDay()
        val E = LocalDate.of(2026, 10, 23).toEpochDay()
    }

    @Test fun deleteRecordsBefore_removesOnlyOlderTransactionsAndArchives() = runBlocking {
        archive(S, E); archive(E, E + 30)
        tx(S + 1, 1_000, "EXPENSE"); tx(E - 1, 2_000, "EXPENSE"); tx(E, 3_000, "EXPENSE"); tx(E + 5, 4_000, "EXPENSE")
        assertEquals(2 to 1, repo.countRecordsBefore(LocalDate.ofEpochDay(E)))
        repo.deleteRecordsBefore(LocalDate.ofEpochDay(E))
        val left = repo.observeArchivedPeriods().first()
        assertEquals(listOf(E), left.map { it.startEpochDay })
        assertEquals(7_000L, left.single().totalExpenseMinor)
        assertEquals(0 to 0, repo.countRecordsBefore(LocalDate.ofEpochDay(E)))
    }
}
