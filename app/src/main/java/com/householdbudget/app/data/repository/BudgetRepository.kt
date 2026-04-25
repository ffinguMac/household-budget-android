package com.householdbudget.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.dao.ArchivedPeriodDao
import com.householdbudget.app.data.local.dao.CategoryBudgetDao
import com.householdbudget.app.data.local.dao.CategoryDao
import com.householdbudget.app.data.local.dao.RecurringRuleDao
import com.householdbudget.app.data.local.dao.TransactionDao
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_LEAF_NAME = "기본"

/** 카테고리 삭제 결과. */
sealed interface CategoryDeletionResult {
    data object Success : CategoryDeletionResult
    /** 카테고리(또는 후손)에 거래/반복규칙이 [transactionCount]건 있음. force=true로 다시 호출하면 모두 삭제. */
    data class HasReferences(val transactionCount: Int, val recurringCount: Int) :
        CategoryDeletionResult
    /** 정책상 삭제 불가 (예: 마지막 대분류). */
    data class NotAllowed(val reason: CategoryValidationError) : CategoryDeletionResult
}

/** 카테고리 검증 오류. */
sealed interface CategoryValidationError {
    data object DuplicateName : CategoryValidationError
    data object KindMismatch : CategoryValidationError
    data object EmptyName : CategoryValidationError
    data object NotFound : CategoryValidationError

    /** 같은 kind의 마지막 남은 대분류를 삭제하려 함. 0개가 되면 해당 kind로 거래 등록 불가. */
    data object LastParentOfKind : CategoryValidationError
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val archivedPeriodDao: ArchivedPeriodDao,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val periodResolver: PeriodResolver = PeriodResolver(),
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    val paydayDom: Flow<Int> = userPreferencesRepository.paydayDom
    val kbankCardEnabled: Flow<Boolean> = userPreferencesRepository.kbankCardEnabled
    val cashbackCategoryId: Flow<Long?> = userPreferencesRepository.cashbackCategoryId

    suspend fun setPaydayDom(day: Int) {
        userPreferencesRepository.setPaydayDom(day)
        userPreferencesRepository.clearLastSeenPeriodStart()
    }

    suspend fun setKbankCardEnabled(enabled: Boolean) {
        userPreferencesRepository.setKbankCardEnabled(enabled)
    }

    suspend fun setCashbackCategoryId(id: Long?) {
        userPreferencesRepository.setCashbackCategoryId(id)
    }

    fun observeArchivedPeriods(): Flow<List<ArchivedPeriodEntity>> = archivedPeriodDao.observeAll()

    suspend fun getArchivedPeriod(id: Long): ArchivedPeriodEntity? = archivedPeriodDao.getById(id)

    fun observeTransactionsInRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): Flow<List<TransactionWithCategoryRow>> =
        transactionDao.observeBetween(startEpochDay, endExclusiveEpochDay)

    /** Ledger 화면의 필터/검색/정렬을 위한 wrapper. */
    fun observeFilteredTransactions(
        startEx: Long,
        endEx: Long,
        kind: String?,
        parentId: Long?,
        leafId: Long?,
        query: String?,
        minAmount: Long,
        maxAmount: Long,
        orderBy: String,
    ): Flow<List<TransactionWithCategoryRow>> =
        transactionDao.observeFiltered(
            startEx = startEx,
            endEx = endEx,
            kind = kind,
            parentId = parentId,
            leafId = leafId,
            query = query,
            minAmount = minAmount,
            maxAmount = maxAmount,
            orderBy = orderBy,
        )

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
        val startEx = yearMonth.atDay(1).toEpochDay()
        val endEx = yearMonth.plusMonths(1).atDay(1).toEpochDay()
        return transactionDao.observeDayTotalsBetween(startEx, endEx)
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

    suspend fun setCategoryIcon(id: Long, icon: String?): Result<Unit> =
        database.withTransaction {
            val cat =
                categoryDao.getById(id)
                    ?: return@withTransaction Result.failure(
                        ValidationException(CategoryValidationError.NotFound),
                    )
            val normalized = icon?.trim()?.ifEmpty { null }
            categoryDao.update(cat.copy(icon = normalized))
            Result.success(Unit)
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
     * 대분류는 후손 leaf의 참조까지 모두 검사. 같은 kind의 유일한 대분류는 삭제 불가.
     */
    suspend fun deleteCategory(id: Long, force: Boolean): CategoryDeletionResult =
        database.withTransaction {
            val cat = categoryDao.getById(id) ?: return@withTransaction CategoryDeletionResult.Success

            // 마지막 대분류 삭제 차단 — 0개가 되면 해당 kind의 거래 등록 불가.
            if (cat.parentId == null) {
                val siblings = categoryDao.countTopLevelByKind(cat.kind)
                if (siblings <= 1) {
                    return@withTransaction CategoryDeletionResult.NotAllowed(
                        CategoryValidationError.LastParentOfKind,
                    )
                }
            }

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

    /** 같은 kind의 다른 대분류로 leaf를 이동. 관련 반복규칙의 kind도 동기화. */
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

            // 방어적 동기화: 지금은 새 parent.kind == 기존 leaf.kind이지만,
            // 향후 parent kind 변경 플로우가 들어와도 정기규칙이 뒤처지지 않도록 갱신.
            recurringRuleDao.listByCategoryId(leafId).forEach { rr ->
                if (rr.kind != newParent.kind) {
                    recurringRuleDao.update(rr.copy(kind = newParent.kind))
                }
            }
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

    // ── 통계 ──────────────────────────────────────────────────────────────

    /**
     * 이번 회계월의 **대분류별 지출 합계**. (EXPENSE만; 저축·수입 제외)
     */
    fun observeMonthlyExpenseByParent(): Flow<List<ParentSpend>> =
        userPreferencesRepository.paydayDom.flatMapLatest { dom ->
            val today = LocalDate.now(zoneId)
            val period = periodResolver.periodContaining(today, dom)
            combine(
                transactionDao.observeBetween(
                    period.startInclusive.toEpochDay(),
                    period.endExclusive.toEpochDay(),
                ),
                categoryDao.observeAll(),
            ) { txs, cats ->
                val parentById = cats.filter { it.parentId == null }.associateBy { it.id }
                txs.filter { it.kind == CategoryKind.EXPENSE.storage }
                    .groupBy { it.parentCategoryId ?: -1L }
                    .map { (pid, list) ->
                        val parent = parentById[pid]
                        ParentSpend(
                            parentId = pid,
                            parentName = parent?.name ?: "기타",
                            parentIcon = parent?.icon,
                            amountMinor = list.sumOf { it.amountMinor },
                        )
                    }
                    .sortedByDescending { it.amountMinor }
            }
        }

    /**
     * 최근 [months]개월간(이번 달 포함) **지출·저축 월별 합계**.
     * 달력(calendar)월 기준 — 회계월이 아님에 유의.
     */
    suspend fun recentMonthlyTotals(months: Int): List<MonthlyTotal> {
        require(months > 0)
        val today = LocalDate.now(zoneId)
        val result = mutableListOf<MonthlyTotal>()
        for (offset in (months - 1) downTo 0) {
            val ym = YearMonth.from(today).minusMonths(offset.toLong())
            val startEx = ym.atDay(1).toEpochDay()
            val endEx = ym.plusMonths(1).atDay(1).toEpochDay()
            val agg = transactionDao.aggregateBetween(startEx, endEx)
            result.add(
                MonthlyTotal(
                    yearMonth = ym,
                    expenseMinor = agg.expenseMinor,
                    savingsMinor = agg.savingsMinor,
                    incomeMinor = agg.incomeMinor,
                ),
            )
        }
        return result
    }

    // ── 카테고리 예산 ──────────────────────────────────────────────────────

    fun observeBudgets(): Flow<Map<Long, CategoryBudgetEntity>> =
        categoryBudgetDao.observeAll().map { list -> list.associateBy { it.categoryId } }

    suspend fun setBudget(categoryId: Long, monthlyAmountMinor: Long, enabled: Boolean = true) {
        require(monthlyAmountMinor > 0)
        categoryBudgetDao.upsert(
            CategoryBudgetEntity(
                categoryId = categoryId,
                monthlyAmountMinor = monthlyAmountMinor,
                enabled = enabled,
            ),
        )
    }

    suspend fun clearBudget(categoryId: Long) {
        categoryBudgetDao.deleteByCategoryId(categoryId)
    }

    /**
     * 이번 회계월 기준으로 예산이 설정된 카테고리들의 진행률.
     * 설정된 예산이 없거나 enabled=false면 빈 리스트.
     */
    fun observeBudgetProgress(): Flow<List<BudgetProgress>> =
        userPreferencesRepository.paydayDom.flatMapLatest { dom ->
            val today = LocalDate.now(zoneId)
            val period = periodResolver.periodContaining(today, dom)
            combine(
                transactionDao.observeBetween(
                    period.startInclusive.toEpochDay(),
                    period.endExclusive.toEpochDay(),
                ),
                categoryBudgetDao.observeAll(),
                categoryDao.observeAll(),
            ) { transactions, budgets, categories ->
                val categoryById = categories.associateBy { it.id }
                budgets.filter { it.enabled }.mapNotNull { budget ->
                    val leaf = categoryById[budget.categoryId] ?: return@mapNotNull null
                    val parent = leaf.parentId?.let { pid -> categoryById[pid] }
                    val spent = transactions
                        .filter { it.categoryId == leaf.id }
                        .sumOf { it.amountMinor }
                    BudgetProgress(
                        categoryId = leaf.id,
                        categoryName = leaf.name,
                        categoryIcon = leaf.icon,
                        parentName = parent?.name,
                        parentIcon = parent?.icon,
                        kind = leaf.kind,
                        monthlyAmountMinor = budget.monthlyAmountMinor,
                        spentMinor = spent,
                    )
                }.sortedByDescending { it.percent }
            }
        }

    // ── 백업 / 복원 ───────────────────────────────────────────────────────────

    suspend fun exportBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = buildBackupJson()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            ?: error("출력 스트림을 열 수 없습니다.")
    }

    suspend fun importBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("입력 스트림을 열 수 없습니다.")
        restoreFromJson(json)
    }

    private suspend fun buildBackupJson(): String {
        val cats     = categoryDao.getAll()
        val txs      = transactionDao.getAll()
        val rrs      = recurringRuleDao.getAll()
        val budgets  = categoryBudgetDao.getAll()
        val payday   = userPreferencesRepository.paydayDom.first()
        val kbank    = userPreferencesRepository.kbankCardEnabled.first()
        val cbCatId  = userPreferencesRepository.cashbackCategoryId.first()

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("categories", JSONArray().also { arr ->
                cats.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("kind", c.kind)
                        put("parentId", c.parentId ?: JSONObject.NULL)
                        put("sortOrder", c.sortOrder)
                        put("icon", c.icon ?: JSONObject.NULL)
                    })
                }
            })
            put("transactions", JSONArray().also { arr ->
                txs.forEach { t ->
                    arr.put(JSONObject().apply {
                        put("id", t.id)
                        put("occurredEpochDay", t.occurredEpochDay)
                        put("amountMinor", t.amountMinor)
                        put("kind", t.kind)
                        put("categoryId", t.categoryId)
                        put("memo", t.memo)
                    })
                }
            })
            put("recurringRules", JSONArray().also { arr ->
                rrs.forEach { r ->
                    arr.put(JSONObject().apply {
                        put("id", r.id)
                        put("name", r.name)
                        put("dayOfMonth", r.dayOfMonth)
                        put("amountMinor", r.amountMinor)
                        put("kind", r.kind)
                        put("categoryId", r.categoryId)
                        put("memo", r.memo)
                        put("enabled", r.enabled)
                        put("lastAppliedYearMonth", r.lastAppliedYearMonth ?: JSONObject.NULL)
                    })
                }
            })
            put("categoryBudgets", JSONArray().also { arr ->
                budgets.forEach { b ->
                    arr.put(JSONObject().apply {
                        put("categoryId", b.categoryId)
                        put("monthlyAmountMinor", b.monthlyAmountMinor)
                        put("enabled", b.enabled)
                    })
                }
            })
            put("preferences", JSONObject().apply {
                put("paydayDom", payday)
                put("kbankEnabled", kbank)
                put("cashbackCategoryId", cbCatId ?: JSONObject.NULL)
            })
        }.toString(2)
    }

    private suspend fun restoreFromJson(json: String) {
        val root = JSONObject(json)

        val cats = (0 until root.getJSONArray("categories").length()).map { i ->
            root.getJSONArray("categories").getJSONObject(i).run {
                CategoryEntity(
                    id = getLong("id"),
                    name = getString("name"),
                    kind = getString("kind"),
                    parentId = if (isNull("parentId")) null else getLong("parentId"),
                    sortOrder = getInt("sortOrder"),
                    icon = if (isNull("icon")) null else optString("icon").ifEmpty { null },
                )
            }
        }
        val txs = (0 until root.getJSONArray("transactions").length()).map { i ->
            root.getJSONArray("transactions").getJSONObject(i).run {
                TransactionEntity(
                    id = getLong("id"),
                    occurredEpochDay = getLong("occurredEpochDay"),
                    amountMinor = getLong("amountMinor"),
                    kind = getString("kind"),
                    categoryId = getLong("categoryId"),
                    memo = optString("memo", ""),
                )
            }
        }
        val rrs = (0 until root.getJSONArray("recurringRules").length()).map { i ->
            root.getJSONArray("recurringRules").getJSONObject(i).run {
                RecurringRuleEntity(
                    id = getLong("id"),
                    name = getString("name"),
                    dayOfMonth = getInt("dayOfMonth"),
                    amountMinor = getLong("amountMinor"),
                    kind = getString("kind"),
                    categoryId = getLong("categoryId"),
                    memo = optString("memo", ""),
                    enabled = getBoolean("enabled"),
                    lastAppliedYearMonth = if (isNull("lastAppliedYearMonth")) null else optString("lastAppliedYearMonth"),
                )
            }
        }
        val budgets = (0 until root.getJSONArray("categoryBudgets").length()).map { i ->
            root.getJSONArray("categoryBudgets").getJSONObject(i).run {
                CategoryBudgetEntity(
                    categoryId = getLong("categoryId"),
                    monthlyAmountMinor = getLong("monthlyAmountMinor"),
                    enabled = getBoolean("enabled"),
                )
            }
        }

        database.withTransaction {
            // FK 안전한 삭제 순서
            categoryBudgetDao.deleteAll()
            recurringRuleDao.deleteAll()
            transactionDao.deleteAll()
            categoryDao.deleteAllLeaves()
            categoryDao.deleteAllParents()
            // 부모 먼저, 자식 나중에 삽입
            categoryDao.insertAllReplace(cats.filter { it.parentId == null }.sortedBy { it.id })
            categoryDao.insertAllReplace(cats.filter { it.parentId != null }.sortedBy { it.id })
            transactionDao.insertAllReplace(txs)
            recurringRuleDao.insertAllReplace(rrs)
            categoryBudgetDao.insertAllReplace(budgets)
        }

        // 설정 복원 (DataStore는 트랜잭션 밖에서)
        root.optJSONObject("preferences")?.let { p ->
            if (p.has("paydayDom")) userPreferencesRepository.setPaydayDom(p.getInt("paydayDom"))
            if (p.has("kbankEnabled")) userPreferencesRepository.setKbankCardEnabled(p.getBoolean("kbankEnabled"))
            val cbId = if (p.isNull("cashbackCategoryId")) null else p.optLong("cashbackCategoryId").takeIf { it != 0L }
            userPreferencesRepository.setCashbackCategoryId(cbId)
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
        private const val BACKUP_VERSION = 1
    }
}

private class ValidationException(val error: CategoryValidationError) : Exception(error.toString())

fun Result<*>.validationError(): CategoryValidationError? =
    (exceptionOrNull() as? ValidationException)?.error

/** 대분류별 지출 집계 (차트용). */
data class ParentSpend(
    val parentId: Long,
    val parentName: String,
    val parentIcon: String?,
    val amountMinor: Long,
)

/** 한 월의 수입/지출/저축 합계 (달력월 기준). */
data class MonthlyTotal(
    val yearMonth: java.time.YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val savingsMinor: Long,
)

/**
 * 한 카테고리에 대한 이번 회계월 예산 진행 상태.
 */
data class BudgetProgress(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String?,
    val parentName: String?,
    val parentIcon: String?,
    val kind: String,
    val monthlyAmountMinor: Long,
    val spentMinor: Long,
) {
    /** 0..∞ (100을 넘을 수 있음). */
    val percent: Int
        get() =
            if (monthlyAmountMinor <= 0L) 0
            else ((spentMinor * 100) / monthlyAmountMinor).toInt()

    val remainingMinor: Long
        get() = monthlyAmountMinor - spentMinor

    val exceeded: Boolean
        get() = spentMinor > monthlyAmountMinor
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
