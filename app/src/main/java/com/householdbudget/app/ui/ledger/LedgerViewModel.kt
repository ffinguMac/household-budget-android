package com.householdbudget.app.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class LedgerSortBy(val storage: String) {
    DATE_DESC("DATE_DESC"),
    DATE_ASC("DATE_ASC"),
    AMOUNT_DESC("AMOUNT_DESC"),
    AMOUNT_ASC("AMOUNT_ASC"),
}

data class LedgerFilterUi(
    val query: String = "",
    val kind: CategoryKind? = null,
    val parentId: Long? = null,
    val leafId: Long? = null,
    val minAmount: Long = 0L,
    val maxAmount: Long = Long.MAX_VALUE,
    val sortBy: LedgerSortBy = LedgerSortBy.DATE_DESC,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || kind != null || parentId != null || leafId != null ||
            minAmount > 0L || maxAmount != Long.MAX_VALUE || sortBy != LedgerSortBy.DATE_DESC
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LedgerViewModel(
    private val repository: BudgetRepository,
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    private val periodResolver: PeriodResolver = PeriodResolver(),
) : ViewModel() {

    private val _filter = MutableStateFlow(LedgerFilterUi())
    val filter: StateFlow<LedgerFilterUi> = _filter.asStateFlow()

    val results: StateFlow<List<TransactionWithCategoryRow>> =
        _filter
            .debounce(250)
            .flatMapLatest { f ->
                // 현재 회계월 범위를 쓰되 payday는 간단히 스냅샷으로 해결.
                val today = LocalDate.now(zoneId)
                val period = periodResolver.periodContaining(today, DEFAULT_PAYDAY)
                repository.observeFilteredTransactions(
                    startEx = period.startInclusive.toEpochDay(),
                    endEx = period.endExclusive.toEpochDay(),
                    kind = f.kind?.storage,
                    parentId = f.parentId,
                    leafId = f.leafId,
                    query = f.query.trim().ifBlank { null },
                    minAmount = f.minAmount,
                    maxAmount = f.maxAmount,
                    orderBy = f.sortBy.storage,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun setQuery(v: String) = _filter.update { it.copy(query = v) }
    fun setKind(k: CategoryKind?) = _filter.update { it.copy(kind = k, parentId = null, leafId = null) }
    fun setParent(id: Long?) = _filter.update { it.copy(parentId = id, leafId = null) }
    fun setLeaf(id: Long?) = _filter.update { it.copy(leafId = id) }
    fun setMinAmount(v: Long) = _filter.update { it.copy(minAmount = v) }
    fun setMaxAmount(v: Long) = _filter.update { it.copy(maxAmount = v) }
    fun setSort(s: LedgerSortBy) = _filter.update { it.copy(sortBy = s) }
    fun clear() = _filter.update { LedgerFilterUi() }

    companion object {
        private const val DEFAULT_PAYDAY = 25
    }
}

class LedgerViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
