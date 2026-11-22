package com.householdbudget.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    private val _offset = MutableStateFlow(0)

    /** 0 = 현재 월급 주기, 음수 = 과거, 양수 = 미래. */
    val offset: StateFlow<Int> = _offset.asStateFlow()

    val period: StateFlow<BudgetPeriod?> =
        _offset
            .flatMapLatest { repository.observePeriodAtOffset(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayTotals: StateFlow<Map<Long, DayTotalRow>> =
        period
            .filterNotNull()
            .flatMapLatest { p ->
                repository.observeDayTotalsIn(p).map { rows -> rows.associateBy { it.dayEpoch } }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun previousPeriod() {
        _offset.update { it - 1 }
    }

    fun nextPeriod() {
        _offset.update { it + 1 }
    }

    fun resetPeriod() {
        _offset.value = 0
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
