package com.householdbudget.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** 이번 월급 주기 + 직전 주기의 거래 스냅샷. 화면 집계는 이 값 하나로 파생한다. */
data class StatsPeriodData(
    val period: BudgetPeriod,
    val previousPeriod: BudgetPeriod,
    val current: List<TransactionWithCategoryRow>,
    val previous: List<TransactionWithCategoryRow>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    repository: BudgetRepository,
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Seoul")
    private val resolver = PeriodResolver()

    /** DB에서 첫 값이 오기 전에는 null (스켈레톤 표시용). */
    val periodData: StateFlow<StatsPeriodData?> =
        repository.paydayDom
            .flatMapLatest { dom ->
                val today = LocalDate.now(zone)
                val period = resolver.periodContaining(today, dom)
                val previous = resolver.previousPeriod(period, dom)
                combine(
                    repository.observeTransactionsInRange(
                        period.startInclusive.toEpochDay(),
                        period.endExclusive.toEpochDay(),
                    ),
                    repository.observeTransactionsInRange(
                        previous.startInclusive.toEpochDay(),
                        previous.endExclusive.toEpochDay(),
                    ),
                ) { cur, prev ->
                    StatsPeriodData(
                        period = period,
                        previousPeriod = previous,
                        current = cur,
                        previous = prev,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val recurringRules: StateFlow<List<RecurringRuleEntity>> =
        repository.observeRecurringRules().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}

class StatsViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            return StatsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
