package com.householdbudget.app.data.repository

import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryBudgetDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val archivedPeriodDao: ArchivedPeriodDao,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val periodResolver: PeriodResolver = PeriodResolver(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    private val onAutoRecategorized: suspend (transactionId: Long, categoryId: Long) -> Unit = { _, _ -> },
) {
    val paydayDom: Flow<Int> = userPreferencesRepository.paydayDom

    /** 월 총 예산 (minor). null = 미설정. */
    val monthlyBudgetMinor: Flow<Long?> = userPreferencesRepository.monthlyBudgetMinor

    suspend fun setMonthlyBudgetMinor(value: Long?) {
        userPreferencesRepository.setMonthlyBudgetMinor(value)
    }

    suspend fun setPaydayDom(day: Int) {
        userPreferencesRepository.setPaydayDom(day)
        userPreferencesRepository.clearLastSeenPeriodStart()
    }

    fun observeCategoryBudgets(): Flow<List<CategoryBudgetEntity>> = categoryBudgetDao.observeAll()

    suspend fun setCategoryBudget(categoryId: Long, amountMinor: Long?) {
        if (amountMinor == null) {
            categoryBudgetDao.deleteByCategoryId(categoryId)
        } else {
            require(amountMinor > 0)
            categoryBudgetDao.upsert(
                CategoryBudgetEntity(
                    categoryId = categoryId,
                    monthlyAmountMinor = amountMinor,
                ),
            )
        }
    }

    fun observeArchivedPeriods(): Flow<List<ArchivedPeriodEntity>> = archivedPeriodDao.observeAll()

    suspend fun getArchivedPeriod(id: Long): ArchivedPeriodEntity? = archivedPeriodDao.getById(id)

    fun observeTransactionsInRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): Flow<List<TransactionWithCategoryRow>> =
        transactionDao.observeBetween(startEpochDay, endExclusiveEpochDay)

    /**
     * 월급일 기준 **이전 회계월**들을 스냅샷으로 저장한다.
     */
    suspend fun tryArchiveCompletedPeriods() {
        val dom = userPreferencesRepository.getPaydayDomSnapshot()
        val today = LocalDate.now(zoneId)
        val current = periodResolver.periodContaining(today, dom)
        val currentStartEx = current.startInclusive.toEpochDay()

        val lastSeen = userPreferencesRepository.getLastSeenPeriodStartSnapshot()
        if (lastSeen == null) {
            userPreferencesRepository.setLastSeenPeriodStart(currentStartEx)
            return
        }
        if (lastSeen == currentStartEx) {
            return
        }

        var candidate = periodResolver.previousPeriod(current, dom)
        // lastSeen 이후에 끝난 기간만 보관. 그 이전은 이미 처리했거나 앱 설치 전.
        repeat(ARCHIVE_MAX_STEPS) {
            val startEx = candidate.startInclusive.toEpochDay()
            val endEx = candidate.endExclusive.toEpochDay()
            if (startEx < lastSeen) return@repeat
            if (archivedPeriodDao.countByBounds(startEx, endEx) > 0) {
                candidate = periodResolver.previousPeriod(candidate, dom)
                return@repeat
            }
            val agg = transactionDao.aggregateBetween(startEx, endEx)
            archivedPeriodDao.insert(
                ArchivedPeriodEntity(
                    startEpochDay = startEx,
                    endEpochDay = endEx,
                    totalIncomeMinor = agg.incomeMinor,
                    totalExpenseMinor = agg.expenseMinor,
                    totalSavingsMinor = agg.savingsMinor,
                    archivedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            candidate = periodResolver.previousPeriod(candidate, dom)
        }

        userPreferencesRepository.setLastSeenPeriodStart(currentStartEx)
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeTransactionsInCurrentPeriod(): Flow<List<TransactionWithCategoryRow>> =
        userPreferencesRepository.paydayDom.flatMapLatest { dom ->
            val today = LocalDate.now(zoneId)
            val period = periodResolver.periodContaining(today, dom)
            transactionDao.observeBetween(
                period.startInclusive.toEpochDay(),
                period.endExclusive.toEpochDay(),
            )
        }

    fun observeHomeSummary(): Flow<HomeSummary> =
        userPreferencesRepository.paydayDom.flatMapLatest { dom ->
            val today = LocalDate.now(zoneId)
            val period = periodResolver.periodContaining(today, dom)
            transactionDao
                .observeBetween(
                    period.startInclusive.toEpochDay(),
                    period.endExclusive.toEpochDay(),
                )
                .map { rows -> HomeSummary(period = period, transactions = rows) }
        }

    fun observeDayTotalsInMonth(yearMonth: YearMonth): Flow<List<DayTotalRow>> {
        val minEx = yearMonth.atDay(1).toEpochDay()
        val maxEx = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeDayTotalsBetween(minEx, maxEx)
    }

    fun observeTransactionsOnDay(epochDay: Long): Flow<List<TransactionWithCategoryRow>> =
        transactionDao.observeBetween(epochDay, epochDay + 1)

    suspend fun getTransaction(id: Long): TransactionWithCategoryRow? =
        transactionDao.getWithCategoryById(id)

    suspend fun insertTransaction(
        occurredDate: LocalDate,
        amountMinor: Long,
        categoryId: Long,
        memo: String,
    ): Long {
        require(amountMinor > 0)
        val leaf =
            categoryDao.getById(categoryId)
                ?: error("Category not found: $categoryId")
        val entity =
            TransactionEntity(
                occurredEpochDay = occurredDate.toEpochDay(),
                amountMinor = amountMinor,
                kind = leaf.kind,
                categoryId = leaf.id,
                memo = memo.trim(),
            )
        return transactionDao.insert(entity)
    }

    suspend fun updateTransaction(
        id: Long,
        occurredDate: LocalDate,
        amountMinor: Long,
        categoryId: Long,
        memo: String,
    ) {
        require(amountMinor > 0)
        val existing = transactionDao.getById(id) ?: error("Transaction not found: $id")
        val leaf = categoryDao.getById(categoryId) ?: error("Category not found: $categoryId")
        val updated =
            existing.copy(
                occurredEpochDay = occurredDate.toEpochDay(),
                amountMinor = amountMinor,
                kind = leaf.kind,
                categoryId = leaf.id,
                memo = memo.trim(),
            )
        transactionDao.update(updated)
        if (existing.source == TransactionSource.AUTO && existing.categoryId != leaf.id) {
            onAutoRecategorized(id, leaf.id)
        }
    }

    suspend fun deleteTransaction(id: Long) {
        val existing = transactionDao.getById(id) ?: return
        transactionDao.delete(existing)
    }

    companion object {
        private const val ARCHIVE_MAX_STEPS = 48
    }
}

data class HomeSummary(
    val period: BudgetPeriod,
    val transactions: List<TransactionWithCategoryRow>,
) {
    val totalIncomeMinor: Long
        get() = transactions.filter { it.kind == CategoryKind.INCOME.storage }.sumOf { it.amountMinor }

    val totalExpenseMinor: Long
        get() = transactions.filter { it.kind == CategoryKind.EXPENSE.storage }.sumOf { it.amountMinor }

    val totalSavingsMinor: Long
        get() = transactions.filter { it.kind == CategoryKind.SAVINGS.storage }.sumOf { it.amountMinor }

    /** 순잉여 = 수입 − 지출 − 저축. */
    val netMinor: Long
        get() = totalIncomeMinor - totalExpenseMinor - totalSavingsMinor
}
