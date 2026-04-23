package com.householdbudget.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringEditorUiState(
    val name: String = "",
    val dayOfMonth: Int = 1,
    val amountText: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val parentId: Long? = null,
    val categoryId: Long? = null,
    val memo: String = "",
    val enabled: Boolean = true,
    val loadFinished: Boolean = false,
    val isSaving: Boolean = false,
)

class RecurringEditorViewModel(
    private val repository: BudgetRepository,
    private val ruleId: Long?,
) : ViewModel() {

    private val _ui = MutableStateFlow(RecurringEditorUiState())
    val uiState: StateFlow<RecurringEditorUiState> = _ui.asStateFlow()

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val row = repository.getRecurringRule(ruleId)
                if (row != null) {
                    val cat = repository.observeCategories().first().firstOrNull { it.id == row.categoryId }
                    _ui.value =
                        RecurringEditorUiState(
                            name = row.name,
                            dayOfMonth = row.dayOfMonth,
                            amountText = row.amountMinor.toString(),
                            kind = CategoryKind.fromStorage(row.kind),
                            parentId = cat?.parentId,
                            categoryId = row.categoryId,
                            memo = row.memo,
                            enabled = row.enabled,
                            loadFinished = true,
                        )
                } else {
                    _ui.update { it.copy(loadFinished = true) }
                }
            }
        } else {
            _ui.update { it.copy(loadFinished = true) }
        }
    }

    fun setName(v: String) {
        _ui.update { it.copy(name = v) }
    }

    fun setDayOfMonth(day: Int) {
        require(day in 1..31)
        _ui.update { it.copy(dayOfMonth = day) }
    }

    fun setAmountText(v: String) {
        if (v.isEmpty() || v.all { it.isDigit() }) {
            _ui.update { it.copy(amountText = v) }
        }
    }

    fun setKind(value: CategoryKind) {
        _ui.update { s ->
            if (s.kind == value) s
            else s.copy(kind = value, parentId = null, categoryId = null)
        }
    }

    fun setParent(parentId: Long, firstLeaf: CategoryEntity?) {
        _ui.update { it.copy(parentId = parentId, categoryId = firstLeaf?.id) }
    }

    fun setCategoryId(id: Long) {
        _ui.update { it.copy(categoryId = id) }
    }

    fun setMemo(v: String) {
        _ui.update { it.copy(memo = v) }
    }

    fun setEnabled(v: Boolean) {
        _ui.update { it.copy(enabled = v) }
    }

    fun save(onDone: () -> Unit, onInvalid: () -> Unit) {
        val s = _ui.value
        val amount = s.amountText.toLongOrNull() ?: 0L
        val cat = s.categoryId
        if (s.name.isBlank() || amount <= 0L || cat == null) {
            onInvalid()
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true) }
            try {
                if (ruleId == null) {
                    repository.insertRecurringRule(
                        name = s.name,
                        dayOfMonth = s.dayOfMonth,
                        amountMinor = amount,
                        categoryId = cat,
                        memo = s.memo,
                        enabled = s.enabled,
                    )
                } else {
                    val existing =
                        repository.getRecurringRule(ruleId) ?: run {
                            _ui.update { it.copy(isSaving = false) }
                            return@launch
                        }
                    repository.updateRecurringRule(
                        existing.copy(
                            name = s.name.trim(),
                            dayOfMonth = s.dayOfMonth,
                            amountMinor = amount,
                            categoryId = cat,
                            memo = s.memo.trim(),
                            enabled = s.enabled,
                        ),
                    )
                }
                onDone()
            } finally {
                _ui.update { it.copy(isSaving = false) }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = ruleId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true) }
            try {
                repository.deleteRecurringRule(id)
                onDone()
            } finally {
                _ui.update { it.copy(isSaving = false) }
            }
        }
    }
}

class RecurringEditorViewModelFactory(
    private val repository: BudgetRepository,
    private val ruleId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        if (modelClass.isAssignableFrom(RecurringEditorViewModel::class.java)) {
            return RecurringEditorViewModel(repository, ruleId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
