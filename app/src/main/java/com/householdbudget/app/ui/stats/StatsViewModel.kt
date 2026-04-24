package com.householdbudget.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.MonthlyTotal
import com.householdbudget.app.data.repository.ParentSpend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    val parentSpend: StateFlow<List<ParentSpend>> =
        repository.observeMonthlyExpenseByParent().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _monthlyTotals = MutableStateFlow<List<MonthlyTotal>>(emptyList())
    val monthlyTotals: StateFlow<List<MonthlyTotal>> = _monthlyTotals.asStateFlow()

    init {
        viewModelScope.launch {
            _monthlyTotals.value = repository.recentMonthlyTotals(months = 6)
        }
    }
}

class StatsViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            return StatsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
