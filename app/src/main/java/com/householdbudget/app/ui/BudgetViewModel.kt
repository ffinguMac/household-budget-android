package com.householdbudget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val repository: BudgetRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val homeSummary: StateFlow<HomeSummary> =
        repository.observeHomeSummary().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                HomeSummary(
                    period =
                        PeriodResolver()
                            .periodContaining(
                                LocalDate.now(ZoneId.of("Asia/Seoul")),
                                DEFAULT_PAYDAY,
                            ),
                    transactions = emptyList(),
                ),
        )

    val transactions: StateFlow<List<TransactionWithCategoryRow>> =
        repository.observeTransactionsInCurrentPeriod().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val paydayDom: StateFlow<Int> =
        repository.paydayDom.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DEFAULT_PAYDAY,
        )

    val categories: StateFlow<List<CategoryEntity>> =
        repository.observeCategories().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** kind별 대분류(parent) 목록. */
    val parentsByKind: StateFlow<Map<CategoryKind, List<CategoryEntity>>> =
        repository.observeCategories()
            .map { all ->
                all.filter { it.parentId == null }
                    .groupBy { CategoryKind.fromStorage(it.kind) }
                    .mapValues { (_, v) -> v.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    /** parent id별 소분류(leaf) 목록. */
    val childrenByParent: StateFlow<Map<Long, List<CategoryEntity>>> =
        repository.observeCategories()
            .map { all ->
                all.filter { it.parentId != null }
                    .groupBy { it.parentId!! }
                    .mapValues { (_, v) -> v.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    // ── 예산 (라운드 2) ────────────────────────────────────────────────────

    /** 월 총 예산 (minor). null = 미설정 (예산 바 숨김). */
    val monthlyBudgetMinor: StateFlow<Long?> =
        preferences.monthlyBudgetMinor.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** categoryId -> 카테고리별 월 예산 (설정된 것만). */
    val categoryBudgets: StateFlow<Map<Long, Long>> =
        repository.observeCategoryBudgets()
            .map { budgets ->
                budgets
                    .filter { it.enabled }
                    .associate { it.categoryId to it.monthlyAmountMinor }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    /** 현재 회계월의 남은 일수 (오늘 포함, 최소 0). 피드/통계 공용. */
    val daysRemainingInPeriod: StateFlow<Int> =
        repository.observeHomeSummary()
            .map { summary ->
                val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
                (summary.period.endExclusive.toEpochDay() - today.toEpochDay())
                    .coerceAtLeast(0L)
                    .toInt()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    // ── 피드 화면: 월(기간) 이동 ───────────────────────────────────────────

    /** 피드에서 선택한 기간 오프셋. 0 = 현재 기간, -1 = 직전 기간. */
    private val _ledgerPeriodOffset = MutableStateFlow(0)
    val ledgerPeriodOffset: StateFlow<Int> = _ledgerPeriodOffset.asStateFlow()

    /** 선택된 기간의 거래 요약. DB에서 첫 값이 오기 전에는 null (스켈레톤 표시용). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ledgerSummary: StateFlow<HomeSummary?> =
        _ledgerPeriodOffset
            .flatMapLatest { offset -> observeSummaryAtOffset(offset) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** [homeSummary]가 DB에서 첫 값을 방출했는지 여부 (콜드 스타트 "0원" 깜빡임 방지용). */
    val homeSummaryLoaded: StateFlow<Boolean> =
        repository.observeHomeSummary()
            .map { true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun previousPeriod() {
        _ledgerPeriodOffset.value -= 1
    }

    /** 현재 기간(오프셋 0)보다 미래로는 이동하지 않는다. */
    fun nextPeriod() {
        if (_ledgerPeriodOffset.value < 0) _ledgerPeriodOffset.value += 1
    }

    fun resetPeriod() {
        _ledgerPeriodOffset.value = 0
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSummaryAtOffset(offset: Int): Flow<HomeSummary> =
        if (offset == 0) {
            repository.observeHomeSummary()
        } else {
            repository.paydayDom.flatMapLatest { dom ->
                val resolver = PeriodResolver()
                var period =
                    resolver.periodContaining(LocalDate.now(ZoneId.of("Asia/Seoul")), dom)
                repeat(-offset) { period = resolver.previousPeriod(period, dom) }
                repository
                    .observeTransactionsInRange(
                        period.startInclusive.toEpochDay(),
                        period.endExclusive.toEpochDay(),
                    )
                    .map { rows -> HomeSummary(period = period, transactions = rows) }
            }
        }

    fun setPaydayDom(day: Int) {
        viewModelScope.launch { repository.setPaydayDom(day) }
    }

    companion object {
        private const val DEFAULT_PAYDAY = 25
    }
}

class BudgetViewModelFactory(
    private val repository: BudgetRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(repository, preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
