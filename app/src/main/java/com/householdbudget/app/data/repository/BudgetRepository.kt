package com.householdbudget.app.data.repository

import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryBudgetDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CashEntryEntity
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
import com.householdbudget.app.domain.category.CustomCategories
import com.householdbudget.app.domain.category.FixedCategories
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val todayProvider: () -> LocalDate = { LocalDate.now(zoneId) },
) {
    val paydayDom: Flow<Int> = userPreferencesRepository.paydayDom

    private val _today = MutableStateFlow(todayProvider())

    /** 앱 전체의 "오늘". 화면 복귀 때 [refreshToday]로 갱신한다. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refreshToday() {
        _today.value = todayProvider()
    }

    /** (월급일, 오늘)로 정한 현재 회계월. */
    val currentPeriod: Flow<BudgetPeriod> =
        combine(paydayDom, today) { dom, day -> periodResolver.periodContaining(day, dom) }
            .distinctUntilChanged()

    /** 0 = 현재 기간, -1 = 직전 기간, +1 = 다음 기간. */
    fun observePeriodAtOffset(offset: Int): Flow<BudgetPeriod> =
        combine(paydayDom, today) { dom, day -> periodResolver.periodAtOffset(day, dom, offset) }
            .distinctUntilChanged()

    fun observeSummaryAtOffset(offset: Int): Flow<HomeSummary> =
        observePeriodAtOffset(offset)
            .flatMapLatest { period ->
                transactionDao
                    .observeBetween(period.startInclusive.toEpochDay(), period.endExclusive.toEpochDay())
                    .map { rows -> HomeSummary(period = period, transactions = rows) }
            }

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
        refreshToday()
        val current = periodResolver.periodContaining(today.value, dom)
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
            if (endEx <= lastSeen) return@repeat
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
        currentPeriod.flatMapLatest { period ->
            transactionDao.observeBetween(
                period.startInclusive.toEpochDay(),
                period.endExclusive.toEpochDay(),
            )
        }

    fun observeHomeSummary(): Flow<HomeSummary> = observeSummaryAtOffset(0)

    // DAO 경계는 양쪽 포함이라 마지막 포함일(endExclusive − 1)을 넘긴다.
    fun observeDayTotalsIn(period: BudgetPeriod): Flow<List<DayTotalRow>> =
        transactionDao.observeDayTotalsBetween(
            period.startInclusive.toEpochDay(),
            period.endExclusive.toEpochDay() - 1,
        )

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

    /** 지난 기록 한 달(기간)의 거래 수. 삭제 확인 문구용. */
    suspend fun countTransactionsIn(period: ArchivedPeriodEntity): Int =
        transactionDao.countBetween(period.startEpochDay, period.endEpochDay)

    /** 지난 기록 한 달(기간)의 거래·자동 기록 항목과 그 지난 기록을 지운다. 되돌릴 수 없다. 학습한 규칙과 설정은 남긴다. */
    suspend fun deleteArchivedPeriod(period: ArchivedPeriodEntity) {
        database.withTransaction {
            transactionDao.deleteBetween(period.startEpochDay, period.endEpochDay)
            database.autoEntryDao().deleteBetween(period.startEpochDay, period.endEpochDay)
            archivedPeriodDao.deleteById(period.id)
        }
    }

    // ── 비상금: 예산·통계·거래와 따로 둔다. 잔액이 음수가 되는 쓰기는 트랜잭션 안에서 막는다. ──

    fun observeCashBalance(): Flow<Long> = database.cashEntryDao().observeBalance()

    fun observeCashEntries(): Flow<List<CashEntryEntity>> = database.cashEntryDao().observeAll()

    suspend fun addCashEntry(day: LocalDate, signedAmount: Long, memo: String): CashResult =
        database.withTransaction {
            val dao = database.cashEntryDao()
            val cleanMemo = validCashMemo(signedAmount, memo) ?: return@withTransaction CashResult.INVALID
            if (dao.balance() + signedAmount < 0) return@withTransaction CashResult.OVERDRAWN
            dao.insert(
                CashEntryEntity(
                    occurredEpochDay = day.toEpochDay(), amountMinor = signedAmount, memo = cleanMemo,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
            CashResult.SAVED
        }

    suspend fun updateCashEntry(id: Long, day: LocalDate, signedAmount: Long, memo: String): CashResult =
        database.withTransaction {
            val dao = database.cashEntryDao()
            val old = dao.getById(id) ?: return@withTransaction CashResult.INVALID
            val cleanMemo = validCashMemo(signedAmount, memo) ?: return@withTransaction CashResult.INVALID
            if (dao.balance() - old.amountMinor + signedAmount < 0) return@withTransaction CashResult.OVERDRAWN
            dao.update(old.copy(occurredEpochDay = day.toEpochDay(), amountMinor = signedAmount, memo = cleanMemo))
            CashResult.SAVED
        }

    suspend fun deleteCashEntry(id: Long): CashResult =
        database.withTransaction {
            val dao = database.cashEntryDao()
            val old = dao.getById(id) ?: return@withTransaction CashResult.INVALID
            if (dao.balance() - old.amountMinor < 0) return@withTransaction CashResult.OVERDRAWN
            dao.delete(old)
            CashResult.SAVED
        }

    /** 금액 1..999,999,999,999(부호 무관), 메모 앞뒤 공백 뺀 30자 이하. 맞으면 정리한 메모, 아니면 null. */
    private fun validCashMemo(signedAmount: Long, memo: String): String? {
        if (signedAmount == 0L || signedAmount !in -MAX_CASH_MINOR..MAX_CASH_MINOR) return null
        return memo.trim().takeIf { it.length <= MAX_CASH_MEMO }
    }

    private fun kindOf(c: CategoryEntity): CategoryKind = CategoryKind.fromStorage(c.kind)

    /** 직접 추가 세부 판정은 이 함수 한 곳에서만 한다. */
    private fun isCustomChild(c: CategoryEntity, parent: CategoryEntity?): Boolean =
        c.parentId != null && parent != null && parent.id == c.parentId &&
            !FixedCategories.isFixedChild(kindOf(parent), parent.name, c.name)

    /** 직접 추가 세부면 (항목, 대분류), 아니면 null. */
    private suspend fun customWithParent(id: Long): Pair<CategoryEntity, CategoryEntity>? {
        val c = categoryDao.getById(id) ?: return null
        val parent = c.parentId?.let { categoryDao.getById(it) }
        return if (isCustomChild(c, parent)) c to parent!! else null
    }

    suspend fun addCustomCategory(parentId: Long, rawName: String): CustomCategoryResult =
        database.withTransaction {
            val parent = categoryDao.getById(parentId)
            if (parent == null || parent.parentId != null || !FixedCategories.allowsCustomChildren(kindOf(parent), parent.name)) {
                error("Cannot add custom category under $parentId")
            }
            val siblings = categoryDao.childrenOf(parentId)
            val taken = siblings.map { it.name } + FixedCategories.fixedChildrenOf(kindOf(parent), parent.name)
            CustomCategories.validateName(rawName, taken)?.let { return@withTransaction CustomCategoryResult.Invalid(it) }
            val entity = CategoryEntity(
                name = rawName.trim(),
                kind = parent.kind,
                parentId = parentId,
                sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                icon = null,
            )
            CustomCategoryResult.Saved(entity.copy(id = categoryDao.insert(entity)))
        }

    suspend fun renameCustomCategory(id: Long, rawName: String): CustomCategoryResult =
        database.withTransaction {
            val (c, parent) = customWithParent(id) ?: error("Not a custom category: $id")
            val taken = categoryDao.childrenOf(parent.id).filter { it.id != id }.map { it.name } +
                FixedCategories.fixedChildrenOf(kindOf(parent), parent.name)
            CustomCategories.validateName(rawName, taken)?.let { return@withTransaction CustomCategoryResult.Invalid(it) }
            val name = rawName.trim()
            categoryDao.rename(id, name)
            CustomCategoryResult.Saved(c.copy(name = name))
        }

    /** 직접 추가 항목이 아니거나 옮길 고정 세부가 없으면 null. */
    suspend fun customCategoryDeletePreview(id: Long): CustomCategoryDeletePreview? {
        val (_, parent) = customWithParent(id) ?: return null
        val kind = kindOf(parent)
        val targets = categoryDao.childrenOf(parent.id).filter { FixedCategories.isFixedChild(kind, parent.name, it.name) }
        if (targets.isEmpty()) return null
        val defaultName = CustomCategories.deleteTargetName(FixedCategories.fixedChildrenOf(kind, parent.name))
        val default = targets.firstOrNull { it.name == defaultName } ?: targets.first()
        return CustomCategoryDeletePreview(targets, default.id, transactionDao.countByCategory(id))
    }

    /** 거래·학습 규칙을 targetId로 옮기고 지운다. 반환값은 옮겨 간 고정 세부. 지우지 않았으면 null. */
    suspend fun deleteCustomCategory(id: Long, targetId: Long): CategoryEntity? =
        database.withTransaction {
            val (_, parent) = customWithParent(id) ?: return@withTransaction null
            val target = categoryDao.getById(targetId)
            if (target == null || target.parentId != parent.id ||
                !FixedCategories.isFixedChild(kindOf(parent), parent.name, target.name)
            ) {
                return@withTransaction null
            }
            transactionDao.moveCategory(id, targetId)
            database.merchantRuleDao().repointCategory(id, targetId)
            categoryDao.deleteById(id)
            target
        }

    /** 선택 칩에서 숨길 직접 추가 세부 id. */
    fun observeHiddenCustomCategoryIds(): Flow<Set<Long>> =
        combine(categoryDao.observeAll(), transactionDao.observeLastUsedByCategory(), today) { cats, lastUsed, day ->
            val byId = cats.associateBy { it.id }
            val last = lastUsed.associate { it.categoryId to it.lastEpochDay }
            cats.filter { c ->
                isCustomChild(c, c.parentId?.let(byId::get)) && CustomCategories.isHidden(last[c.id], day.toEpochDay())
            }.mapTo(HashSet()) { it.id }
        }

    companion object {
        private const val ARCHIVE_MAX_STEPS = 48
    }
}

data class HomeSummary(
    val period: BudgetPeriod,
    val transactions: List<TransactionWithCategoryRow>,
) {
    val totalIncomeMinor: Long =
        transactions.filter { it.kind == CategoryKind.INCOME.storage }.sumOf { it.amountMinor }

    val totalExpenseMinor: Long =
        transactions.filter { it.kind == CategoryKind.EXPENSE.storage }.sumOf { it.amountMinor }

    val totalSavingsMinor: Long =
        transactions.filter { it.kind == CategoryKind.SAVINGS.storage }.sumOf { it.amountMinor }

    /** 순잉여 = 수입 − 지출 − 저축. */
    val netMinor: Long = totalIncomeMinor - totalExpenseMinor - totalSavingsMinor
}

const val MAX_CASH_MINOR = 999_999_999_999L
const val MAX_CASH_MEMO = 30

enum class CashResult { SAVED, OVERDRAWN, INVALID }

sealed interface CustomCategoryResult {
    data class Saved(val category: CategoryEntity) : CustomCategoryResult
    data class Invalid(val error: CustomCategories.NameError) : CustomCategoryResult
}

/** 삭제 확인 대화상자용. targets = 같은 대분류의 고정 세부(정렬 순), defaultTargetId = 기본 선택. */
data class CustomCategoryDeletePreview(val targets: List<CategoryEntity>, val defaultTargetId: Long, val transactionCount: Int)
