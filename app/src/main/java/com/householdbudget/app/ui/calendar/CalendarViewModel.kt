package com.householdbudget.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.repository.BudgetRepository
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Seoul")

    private val _visibleMonth = MutableStateFlow(YearMonth.now(zone))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    val dayTotals: StateFlow<Map<Long, DayTotalRow>> =
        _visibleMonth
            .flatMapLatest { ym ->
                repository.observeDayTotalsInMonth(ym).map { rows ->
                    rows.associateBy { it.dayEpoch }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun previousMonth() {
        _visibleMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        _visibleMonth.update { it.plusMonths(1) }
    }
}

class CalendarViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            return CalendarViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
