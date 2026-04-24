package com.householdbudget.app.ui.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.CategoryDeletionResult
import com.householdbudget.app.data.repository.CategoryValidationError
import com.householdbudget.app.data.repository.validationError
import com.householdbudget.app.domain.CategoryKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParentGroup(
    val parent: CategoryEntity,
    val children: List<CategoryEntity>,
)

data class CategoryManagementUi(
    val selectedKind: CategoryKind = CategoryKind.EXPENSE,
    val expanded: Set<Long> = emptySet(),
    val error: CategoryValidationError? = null,
    val pendingDeletion: PendingDeletion? = null,
)

/** 삭제 시 거래/반복 규칙이 있어 force 확인이 필요한 상태. */
data class PendingDeletion(
    val categoryId: Long,
    val categoryName: String,
    val isParent: Boolean,
    val transactionCount: Int,
    val recurringCount: Int,
)

class CategoryManagementViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CategoryManagementUi())
    val uiState: StateFlow<CategoryManagementUi> = _ui.asStateFlow()

    val budgets: StateFlow<Map<Long, CategoryBudgetEntity>> =
        repository.observeBudgets().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    fun setBudget(categoryId: Long, monthlyAmountMinor: Long) {
        viewModelScope.launch { repository.setBudget(categoryId, monthlyAmountMinor) }
    }

    fun clearBudget(categoryId: Long) {
        viewModelScope.launch { repository.clearBudget(categoryId) }
    }

    /** 현재 선택된 kind의 대분류 + 각 대분류의 자식들. */
    val groups: StateFlow<List<ParentGroup>> =
        repository.observeCategories()
            .map { all ->
                val parents =
                    all.filter { it.parentId == null }
                        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val byParent =
                    all.filter { it.parentId != null }
                        .groupBy { it.parentId!! }
                        .mapValues { (_, v) -> v.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
                parents.map { p -> ParentGroup(p, byParent[p.id].orEmpty()) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun selectKind(kind: CategoryKind) {
        _ui.value = _ui.value.copy(selectedKind = kind, error = null)
    }

    fun toggleExpanded(parentId: Long) {
        val cur = _ui.value.expanded
        _ui.value =
            _ui.value.copy(
                expanded = if (parentId in cur) cur - parentId else cur + parentId,
                error = null,
            )
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun dismissPendingDeletion() {
        _ui.value = _ui.value.copy(pendingDeletion = null)
    }

    fun addParent(name: String) {
        viewModelScope.launch {
            val result = repository.addParentCategory(name, _ui.value.selectedKind)
            result.validationError()?.let { err ->
                _ui.value = _ui.value.copy(error = err)
            }
            result.getOrNull()?.let { newId ->
                _ui.value = _ui.value.copy(expanded = _ui.value.expanded + newId)
            }
        }
    }

    fun addChild(parentId: Long, name: String) {
        viewModelScope.launch {
            val result = repository.addLeafCategory(parentId, name)
            result.validationError()?.let { err ->
                _ui.value = _ui.value.copy(error = err)
            }
            if (result.isSuccess) {
                _ui.value = _ui.value.copy(expanded = _ui.value.expanded + parentId)
            }
        }
    }

    fun rename(id: Long, newName: String) {
        viewModelScope.launch {
            val result = repository.renameCategory(id, newName)
            result.validationError()?.let { err ->
                _ui.value = _ui.value.copy(error = err)
            }
        }
    }

    /**
     * 1차 시도. 참조가 있으면 확인 다이얼로그 상태를 세팅.
     */
    fun requestDelete(category: CategoryEntity) {
        viewModelScope.launch {
            val result = repository.deleteCategory(category.id, force = false)
            when (result) {
                is CategoryDeletionResult.Success -> Unit
                is CategoryDeletionResult.HasReferences -> {
                    _ui.value =
                        _ui.value.copy(
                            pendingDeletion =
                                PendingDeletion(
                                    categoryId = category.id,
                                    categoryName = category.name,
                                    isParent = category.parentId == null,
                                    transactionCount = result.transactionCount,
                                    recurringCount = result.recurringCount,
                                ),
                        )
                }
                is CategoryDeletionResult.NotAllowed -> {
                    _ui.value = _ui.value.copy(error = result.reason)
                }
            }
        }
    }

    /** 참조가 있어도 강제 삭제. */
    fun confirmForceDelete() {
        val pending = _ui.value.pendingDeletion ?: return
        viewModelScope.launch {
            repository.deleteCategory(pending.categoryId, force = true)
            _ui.value = _ui.value.copy(pendingDeletion = null)
        }
    }

    fun reorderParents(kind: CategoryKind, orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderTopLevel(kind, orderedIds) }
    }

    fun reorderChildren(parentId: Long, orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderChildren(parentId, orderedIds) }
    }
}

class CategoryManagementViewModelFactory(
    private val repository: BudgetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(CategoryManagementViewModel::class.java)) {
            return CategoryManagementViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
