package com.householdbudget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val repository: BudgetRepository,
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

    fun setPaydayDom(day: Int) {
        viewModelScope.launch {
            repository.setPaydayDom(day)
        }
    }

    companion object {
        private const val DEFAULT_PAYDAY = 25
    }
}

class BudgetViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
