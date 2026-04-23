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
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

const val DEFAULT_LEAF_NAME = "기본"

/** 카테고리 삭제 결과. */
sealed interface CategoryDeletionResult {
    data object Success : CategoryDeletionResult
    /** 카테고리(또는 후손)에 거래/반복규칙이 [transactionCount]건 있음. force=true로 다시 호출하면 모두 삭제. */
    data class HasReferences(val transactionCount: Int, val recurringCount: Int) :
        CategoryDeletionResult
}

/** 카테고리 검증 오류. */
sealed interface CategoryValidationError {
    data object DuplicateName : CategoryValidationError
    data object KindMismatch : CategoryValidationError
    data object EmptyName : CategoryValidationError
    data object NotFound : CategoryValidationError
}

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
        categoryId: Long,
        memo: String,
        enabled: Boolean,
    ): Long {
        require(dayOfMonth in 1..31)
        require(amountMinor > 0)
        val leaf = categoryDao.getById(categoryId) ?: error("Category not found: $categoryId")
        val entity =
            RecurringRuleEntity(
                name = name.trim(),
                dayOfMonth = dayOfMonth,
                amountMinor = amountMinor,
                kind = leaf.kind,
                categoryId = leaf.id,
                memo = memo.trim(),
                enabled = enabled,
                lastAppliedYearMonth = null,
            )
        return recurringRuleDao.insert(entity)
    }

    suspend fun updateRecurringRule(entity: RecurringRuleEntity) {
        require(entity.dayOfMonth in 1..31)
        require(entity.amountMinor > 0)
        val leaf =
            categoryDao.getById(entity.categoryId)
                ?: error("Category not found: ${entity.categoryId}")
        // 카테고리의 kind와 동기화.
        recurringRuleDao.update(entity.copy(kind = leaf.kind))
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
                        kind = rule.kind,
                        categoryId = rule.categoryId,
                        memo = memo,
                    ),
                )
                recurringRuleDao.update(rule.copy(lastAppliedYearMonth = ymStr))
            }
        }
    }

    // ── 카테고리 관리 ──────────────────────────────────────────────────────

    /**
     * 새 대분류와 그 아래 "기본" 소분류를 한 번에 생성. 같은 kind 내에서 이름이 중복되면 에러.
     */
    suspend fun addParentCategory(
        name: String,
        kind: CategoryKind,
    ): Result<Long> = database.withTransaction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withTransaction Result.failure(
            ValidationException(CategoryValidationError.EmptyName),
        )
        if (categoryDao.findTopLevelByName(kind.storage, trimmed) != null) {
            return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.DuplicateName),
            )
        }
        val nextSort = (categoryDao.maxTopLevelSortOrder(kind.storage) ?: -1) + 1
        val parentId =
            categoryDao.insert(
                CategoryEntity(
                    name = trimmed,
                    kind = kind.storage,
                    parentId = null,
                    sortOrder = nextSort,
                ),
            )
        // 기본 leaf 자동 생성.
        categoryDao.insert(
            CategoryEntity(
                name = DEFAULT_LEAF_NAME,
                kind = kind.storage,
                parentId = parentId,
                sortOrder = 0,
            ),
        )
        Result.success(parentId)
    }

    /**
     * 대분류 아래 새 소분류를 추가. 대분류에 거래 0건의 자동 "기본" leaf만 있으면 그것을 제거.
     */
    suspend fun addLeafCategory(
        parentId: Long,
        name: String,
    ): Result<Long> = database.withTransaction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withTransaction Result.failure(
            ValidationException(CategoryValidationError.EmptyName),
        )
        val parent =
            categoryDao.getById(parentId)
                ?: return@withTransaction Result.failure(
                    ValidationException(CategoryValidationError.NotFound),
                )
        if (parent.parentId != null) {
            // 소분류 밑에는 추가 불가 (계층은 2단계만).
            return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.NotFound),
            )
        }
        if (categoryDao.findChildByName(parentId, trimmed) != null) {
            return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.DuplicateName),
            )
        }

        // "기본" leaf만 있고 거래 0건이면 자동 제거 (빈 placeholder 정리).
        val children = categoryDao.getChildren(parentId)
        if (children.size == 1 && children[0].name == DEFAULT_LEAF_NAME) {
            val onlyChild = children[0]
            val tx = categoryDao.countTransactionsForLeaf(onlyChild.id)
            val rr = categoryDao.countRecurringRulesForLeaf(onlyChild.id)
            if (tx == 0 && rr == 0) {
                categoryDao.delete(onlyChild)
            }
        }

        val nextSort = (categoryDao.maxChildSortOrder(parentId) ?: -1) + 1
        val newId =
            categoryDao.insert(
                CategoryEntity(
                    name = trimmed,
                    kind = parent.kind,
                    parentId = parentId,
                    sortOrder = nextSort,
                ),
            )
        Result.success(newId)
    }

    suspend fun renameCategory(id: Long, newName: String): Result<Unit> =
        database.withTransaction {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.EmptyName),
            )
            val cat =
                categoryDao.getById(id)
                    ?: return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.NotFound),
                    )
            // 동일 형제(같은 parent) 내 이름 중복 검사.
            if (cat.parentId == null) {
                val dup = categoryDao.findTopLevelByName(cat.kind, trimmed)
                if (dup != null && dup.id != id) {
                    return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.DuplicateName),
                    )
                }
            } else {
                val dup = categoryDao.findChildByName(cat.parentId, trimmed)
                if (dup != null && dup.id != id) {
                    return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.DuplicateName),
                    )
                }
            }
            categoryDao.update(cat.copy(name = trimmed))
            Result.success(Unit)
        }

    /**
     * 카테고리 삭제. force=false일 때 거래/반복규칙 참조가 있으면 [CategoryDeletionResult.HasReferences] 반환.
     * 대분류는 후손 leaf의 참조까지 모두 검사.
     */
    suspend fun deleteCategory(id: Long, force: Boolean): CategoryDeletionResult =
        database.withTransaction {
            val cat = categoryDao.getById(id) ?: return@withTransaction CategoryDeletionResult.Success
            val leafIds: List<Long> =
                if (cat.parentId == null) {
                    categoryDao.getChildren(cat.id).map { it.id }
                } else {
                    listOf(cat.id)
                }
            var txCount = 0
            var rrCount = 0
            for (lid in leafIds) {
                txCount += categoryDao.countTransactionsForLeaf(lid)
                rrCount += categoryDao.countRecurringRulesForLeaf(lid)
            }
            if ((txCount > 0 || rrCount > 0) && !force) {
                return@withTransaction CategoryDeletionResult.HasReferences(txCount, rrCount)
            }
            // 거래/규칙을 먼저 정리(FK RESTRICT 우회).
            for (lid in leafIds) {
                transactionDao.deleteByCategoryId(lid)
                recurringRuleDao.deleteByCategoryId(lid)
            }
            // 대분류 삭제 시 자식 leaf는 ON DELETE CASCADE로 함께 사라짐.
            categoryDao.delete(cat)
            CategoryDeletionResult.Success
        }

    /** 같은 kind의 다른 대분류로 leaf를 이동. */
    suspend fun moveLeaf(leafId: Long, newParentId: Long): Result<Unit> =
        database.withTransaction {
            val leaf =
                categoryDao.getById(leafId)
                    ?: return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.NotFound),
                    )
            if (leaf.parentId == null) return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.NotFound),
            )
            val newParent =
                categoryDao.getById(newParentId)
                    ?: return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.NotFound),
                    )
            if (newParent.parentId != null) return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.NotFound),
            )
            if (newParent.kind != leaf.kind) return@withTransaction Result.failure(
                ValidationException(CategoryValidationError.KindMismatch),
            )
            if (categoryDao.findChildByName(newParentId, leaf.name) != null) {
                return@withTransaction Result.failure(
                    ValidationException(CategoryValidationError.DuplicateName),
                )
            }
            val nextSort = (categoryDao.maxChildSortOrder(newParentId) ?: -1) + 1
            categoryDao.update(leaf.copy(parentId = newParentId, sortOrder = nextSort))
            Result.success(Unit)
        }

    suspend fun reorderTopLevel(kind: CategoryKind, orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { idx, id ->
                val cat = categoryDao.getById(id) ?: return@forEachIndexed
                if (cat.parentId == null && cat.kind == kind.storage) {
                    categoryDao.update(cat.copy(sortOrder = idx))
                }
            }
        }
    }

    suspend fun reorderChildren(parentId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            orderedIds.forEachIndexed { idx, id ->
                val cat = categoryDao.getById(id) ?: return@forEachIndexed
                if (cat.parentId == parentId) {
                    categoryDao.update(cat.copy(sortOrder = idx))
                }
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

private class ValidationException(val error: CategoryValidationError) : Exception(error.toString())

fun Result<*>.validationError(): CategoryValidationError? =
    (exceptionOrNull() as? ValidationException)?.error

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
