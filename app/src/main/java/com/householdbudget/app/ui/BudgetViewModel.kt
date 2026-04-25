package com.householdbudget.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetProgress
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.PeriodResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    val kbankCardEnabled: StateFlow<Boolean> =
        repository.kbankCardEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val cashbackCategoryId: StateFlow<Long?> =
        repository.cashbackCategoryId.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val budgetProgress: StateFlow<List<BudgetProgress>> =
        repository.observeBudgetProgress().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setBudget(categoryId: Long, monthlyAmountMinor: Long) {
        viewModelScope.launch { repository.setBudget(categoryId, monthlyAmountMinor) }
    }

    fun clearBudget(categoryId: Long) {
        viewModelScope.launch { repository.clearBudget(categoryId) }
    }

    fun setPaydayDom(day: Int) {
        viewModelScope.launch { repository.setPaydayDom(day) }
    }

    fun setKbankCardEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setKbankCardEnabled(enabled) }
    }

    fun setCashbackCategoryId(id: Long?) {
        viewModelScope.launch { repository.setCashbackCategoryId(id) }
    }

    // ── 백업 / 복원 ───────────────────────────────────────────────────────────

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.Working
            runCatching { repository.exportBackup(context, uri) }
                .onSuccess { _backupStatus.value = BackupStatus.ExportDone }
                .onFailure { _backupStatus.value = BackupStatus.Error(it.message ?: "오류") }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.Working
            runCatching { repository.importBackup(context, uri) }
                .onSuccess { _backupStatus.value = BackupStatus.ImportDone }
                .onFailure { _backupStatus.value = BackupStatus.Error(it.message ?: "오류") }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    companion object {
        private const val DEFAULT_PAYDAY = 25
    }
}

sealed class BackupStatus {
    data object Idle : BackupStatus()
    data object Working : BackupStatus()
    data object ExportDone : BackupStatus()
    data object ImportDone : BackupStatus()
    data class Error(val message: String) : BackupStatus()
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
