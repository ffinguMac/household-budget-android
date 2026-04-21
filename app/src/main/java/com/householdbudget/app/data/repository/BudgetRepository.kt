package com.householdbudget.app.data.repository

import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.RecurringRuleDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val archivedPeriodDao: ArchivedPeriodDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val periodResolver: PeriodResolver = PeriodResolver(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    val paydayDom: Flow<Int> = userPreferencesRepository.paydayDom
    val kbankCardEnabled: Flow<Boolean> = userPreferencesRepository.kbankCardEnabled

    suspend fun setPaydayDom(day: Int) {
        userPreferencesRepository.setPaydayDom(day)
        userPreferencesRepository.clearLastSeenPeriodStart()
    }

    suspend fun setKbankCardEnabled(enabled: Boolean) {
        userPreferencesRepository.setKbankCardEnabled(enabled)
    }

    fun observeArchivedPeriods(): Flow<List<ArchivedPeriodEntity>> = archivedPeriodDao.observeAll()

    suspend fun getArchivedPeriod(id: Long): ArchivedPeriodEntity? = archivedPeriodDao.getById(id)

    fun observeTransactionsInRange(startEpochDay: Long, endExclusiveEpochDay: Long): Flow<List<TransactionWithCategoryRow>> =
        transactionDao.observeBetween(startEpochDay, endExclusiveEpochDay)

    /**
     * 월급일 기준 **이전 회계월**들을 스냅샷으로 저장한다.
     * 현재 보고 있는 회계월 시작일이 바뀌었을 때만, 아직 없는 과거 구간을 최대 48개까지 채운다.
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
        repeat(ARCHIVE_MAX_STEPS) {
            val startEx = candidate.startInclusive.toEpochDay()
            val endEx = candidate.endExclusive.toEpochDay()
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
        isIncome: Boolean,
        categoryId: Long,
        memo: String,
    ): Long {
        require(amountMinor > 0)
        val entity =
            TransactionEntity(
                occurredEpochDay = occurredDate.toEpochDay(),
                amountMinor = amountMinor,
                isIncome = isIncome,
                categoryId = categoryId,
                memo = memo.trim(),
            )
        return transactionDao.insert(entity)
    }

    suspend fun updateTransaction(
        id: Long,
        occurredDate: LocalDate,
        amountMinor: Long,
        isIncome: Boolean,
        categoryId: Long,
        memo: String,
    ) {
        require(amountMinor > 0)
        val existing =
            transactionDao.getById(id) ?: error("Transaction not found: $id")
        val updated =
            existing.copy(
                occurredEpochDay = occurredDate.toEpochDay(),
                amountMinor = amountMinor,
                isIncome = isIncome,
                categoryId = categoryId,
                memo = memo.trim(),
            )
        transactionDao.update(updated)
    }

    suspend fun deleteTransaction(id: Long) {
        val existing = transactionDao.getById(id) ?: return
        transactionDao.delete(existing)
    }

    fun observeRecurringRules(): Flow<List<RecurringRuleEntity>> = recurringRuleDao.observeAll()

    suspend fun getRecurringRule(id: Long): RecurringRuleEntity? = recurringRuleDao.getById(id)

    suspend fun insertRecurringRule(
        name: String,
        dayOfMonth: Int,
        amountMinor: Long,
        isIncome: Boolean,
        categoryId: Long,
        memo: String,
        enabled: Boolean,
    ): Long {
        require(dayOfMonth in 1..31)
        require(amountMinor > 0)
        val entity =
            RecurringRuleEntity(
                name = name.trim(),
                dayOfMonth = dayOfMonth,
                amountMinor = amountMinor,
                isIncome = isIncome,
                categoryId = categoryId,
                memo = memo.trim(),
                enabled = enabled,
                lastAppliedYearMonth = null,
            )
        return recurringRuleDao.insert(entity)
    }

    suspend fun updateRecurringRule(entity: RecurringRuleEntity) {
        require(entity.dayOfMonth in 1..31)
        require(entity.amountMinor > 0)
        recurringRuleDao.update(entity)
    }

    suspend fun deleteRecurringRule(id: Long) {
        val e = recurringRuleDao.getById(id) ?: return
        recurringRuleDao.delete(e)
    }

    /**
     * 달력 기준 매월 N일에 해당하는 날이 오면, 아직 이번 달에 반영되지 않은 규칙에 대해 거래를 한 건씩 추가한다.
     */
    suspend fun applyDueRecurringRules() {
        val today = LocalDate.now(zoneId)
        val ym = YearMonth.from(today)
        val ymStr = YM_FORMAT.format(ym)
        val rules = recurringRuleDao.listEnabled()
        for (rule in rules) {
            if (!rule.enabled) continue
            val target = clampDayInMonth(ym, rule.dayOfMonth)
            if (today.isBefore(target)) continue
            if (rule.lastAppliedYearMonth == ymStr) continue
            database.withTransaction {
                val memo = buildAutoMemo(rule)
                transactionDao.insert(
                    TransactionEntity(
                        occurredEpochDay = target.toEpochDay(),
                        amountMinor = rule.amountMinor,
                        isIncome = rule.isIncome,
                        categoryId = rule.categoryId,
                        memo = memo,
                    ),
                )
                recurringRuleDao.update(rule.copy(lastAppliedYearMonth = ymStr))
            }
        }
    }

    private fun buildAutoMemo(rule: RecurringRuleEntity): String {
        val base = "[자동] ${rule.name}"
        return if (rule.memo.isBlank()) base else "$base · ${rule.memo.trim()}"
    }

    private fun clampDayInMonth(ym: YearMonth, dayOfMonth: Int): LocalDate {
        val last = ym.lengthOfMonth()
        val d = minOf(dayOfMonth, last)
        return ym.atDay(d)
    }

    companion object {
        private val YM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        private const val ARCHIVE_MAX_STEPS = 48
    }
}

data class HomeSummary(
    val period: BudgetPeriod,
    val transactions: List<TransactionWithCategoryRow>,
) {
    val totalIncomeMinor: Long
        get() = transactions.filter { it.isIncome }.sumOf { it.amountMinor }

    val totalExpenseMinor: Long
        get() = transactions.filter { !it.isIncome }.sumOf { it.amountMinor }

    val netMinor: Long
        get() = totalIncomeMinor - totalExpenseMinor
}
