package com.householdbudget.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
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
class CashReserveRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository
    private val day = LocalDate.of(2026, 11, 25)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BudgetRepository(
            db, db.transactionDao(), db.categoryDao(), db.archivedPeriodDao(), db.categoryBudgetDao(),
            UserPreferencesRepository(context), todayProvider = { day },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun balance() = repo.observeCashBalance().first()

    private suspend fun entries() = repo.observeCashEntries().first()

    @Test fun balance_isZeroWhenEmpty_andSumsSignedAmounts() = runBlocking {
        assertEquals(0L, balance())
        assertEquals(CashResult.SAVED, repo.addCashEntry(day, 100_000, "  월급 남은 돈 "))
        assertEquals(CashResult.SAVED, repo.addCashEntry(day, -30_000, ""))
        assertEquals(70_000L, balance())
        assertEquals("월급 남은 돈", entries().last().memo)
    }

    @Test fun entries_areNewestDayFirstThenNewestId() = runBlocking {
        repo.addCashEntry(day.minusDays(1), 1, "a")
        repo.addCashEntry(day, 2, "b")
        repo.addCashEntry(day.minusDays(1), 3, "c")
        assertEquals(listOf("b", "c", "a"), entries().map { it.memo })
    }

    @Test fun add_overWithdrawal_isRejectedAndNothingWritten() = runBlocking {
        repo.addCashEntry(day, 10_000, "")
        assertEquals(CashResult.OVERDRAWN, repo.addCashEntry(day, -10_001, ""))
        assertEquals(1, entries().size)
        assertEquals(CashResult.SAVED, repo.addCashEntry(day, -10_000, ""))
        assertEquals(0L, balance())
    }

    @Test fun add_invalidAmountOrMemo_isRejected() = runBlocking {
        assertEquals(CashResult.INVALID, repo.addCashEntry(day, 0, ""))
        assertEquals(CashResult.INVALID, repo.addCashEntry(day, 1_000_000_000_000, ""))
        assertEquals(CashResult.INVALID, repo.addCashEntry(day, 1, "가".repeat(31)))
        assertEquals(CashResult.SAVED, repo.addCashEntry(day, 999_999_999_999, " " + "가".repeat(30) + " "))
        assertEquals(1, entries().size)
    }

    @Test fun edit_thatWouldGoNegative_isRejectedAndUnchanged() = runBlocking {
        repo.addCashEntry(day, 50_000, "")
        repo.addCashEntry(day, -20_000, "")
        val deposit = entries().single { it.amountMinor == 50_000L }
        assertEquals(CashResult.OVERDRAWN, repo.updateCashEntry(deposit.id, day, 10_000, ""))
        assertEquals(30_000L, balance())
        assertEquals(CashResult.SAVED, repo.updateCashEntry(deposit.id, day, 20_000, "줄임"))
        assertEquals(0L, balance())
    }

    @Test fun edit_flippingSign_isCheckedAgainstRest() = runBlocking {
        repo.addCashEntry(day, 50_000, "")
        val withdrawal = run { repo.addCashEntry(day, -10_000, ""); entries().first() }
        // 빼기 → 넣기
        assertEquals(CashResult.SAVED, repo.updateCashEntry(withdrawal.id, day, 10_000, ""))
        assertEquals(60_000L, balance())
        // 넣기 → 빼기: 나머지(50,000)보다 크면 막는다
        assertEquals(CashResult.OVERDRAWN, repo.updateCashEntry(withdrawal.id, day, -50_001, ""))
        assertEquals(60_000L, balance())
        assertEquals(CashResult.SAVED, repo.updateCashEntry(withdrawal.id, day, -50_000, ""))
        assertEquals(0L, balance())
    }

    @Test fun delete_depositThatFundsWithdrawal_isRejected() = runBlocking {
        repo.addCashEntry(day, 50_000, "")
        repo.addCashEntry(day, -20_000, "")
        val (withdrawal, deposit) = entries()
        assertEquals(CashResult.OVERDRAWN, repo.deleteCashEntry(deposit.id))
        assertEquals(2, entries().size)
        assertEquals(CashResult.SAVED, repo.deleteCashEntry(withdrawal.id))
        assertEquals(CashResult.SAVED, repo.deleteCashEntry(deposit.id))
        assertEquals(0L, balance())
        assertEquals(CashResult.INVALID, repo.deleteCashEntry(deposit.id))
    }

    @Test fun cash_doesNotTouchTransactions() = runBlocking {
        repo.addCashEntry(day, 50_000, "")
        assertEquals(0, db.transactionDao().countBetween(day.toEpochDay() - 1, day.toEpochDay() + 1))
    }
}
