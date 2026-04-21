package com.householdbudget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.repository.BudgetRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CashbackChannel { ONLINE, OFFLINE }

data class EditTransactionUiState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
    val amountText: String = "",
    val isIncome: Boolean = false,
    val categoryId: Long? = null,
    val memo: String = "",
    val isSaving: Boolean = false,
    /** 수정 모드에서 서버(로컬) 로드 완료 여부 */
    val loadFinished: Boolean = false,
)

class EditTransactionViewModel(
    private val repository: BudgetRepository,
    private val transactionId: Long?,
) : ViewModel() {

    private val zoneId = ZoneId.of("Asia/Seoul")

    private val _uiState = MutableStateFlow(EditTransactionUiState())
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    init {
        if (transactionId != null) {
            viewModelScope.launch {
                val row = repository.getTransaction(transactionId)
                if (row != null) {
                    _uiState.update {
                        EditTransactionUiState(
                            date = LocalDate.ofEpochDay(row.occurredEpochDay),
                            amountText = row.amountMinor.toString(),
                            isIncome = row.isIncome,
                            categoryId = row.categoryId,
                            memo = row.memo,
                            loadFinished = true,
                        )
                    }
                } else {
                    _uiState.update { it.copy(loadFinished = true) }
                }
            }
        } else {
            _uiState.update { it.copy(loadFinished = true) }
        }
    }

    fun setDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun setAmountText(text: String) {
        if (text.isEmpty() || text.all { it.isDigit() }) {
            _uiState.update { it.copy(amountText = text) }
        }
    }

    fun setIncome(value: Boolean, categories: List<CategoryEntity>) {
        _uiState.update { s ->
            if (s.isIncome == value) return@update s
            val nextCategory =
                categories.firstOrNull { it.isIncome == value }?.id ?: s.categoryId
            s.copy(isIncome = value, categoryId = nextCategory)
        }
    }

    fun setCategoryId(id: Long) {
        _uiState.update { it.copy(categoryId = id) }
    }

    fun setMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun save(cashbackChannel: CashbackChannel?, onSuccess: () -> Unit, onInvalid: () -> Unit) {
        val s = _uiState.value
        val amount = s.amountText.toLongOrNull() ?: 0L
        val categoryId = s.categoryId
        if (amount <= 0L || categoryId == null) {
            onInvalid()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (transactionId == null) {
                    repository.insertTransaction(
                        occurredDate = s.date,
                        amountMinor = amount,
                        isIncome = s.isIncome,
                        categoryId = categoryId,
                        memo = s.memo,
                    )
                    if (!s.isIncome && cashbackChannel != null) {
                        val rate = if (cashbackChannel == CashbackChannel.ONLINE) 11L else 6L
                        val cashback = amount * rate / 1000L
                        if (cashback > 0L) {
                            val incomeCatId = repository.observeCategories().first()
                                .firstOrNull { it.isIncome }?.id
                            if (incomeCatId != null) {
                                val label = if (cashbackChannel == CashbackChannel.ONLINE) "온라인 1.1%" else "오프라인 0.6%"
                                repository.insertTransaction(
                                    occurredDate = s.date,
                                    amountMinor = cashback,
                                    isIncome = true,
                                    categoryId = incomeCatId,
                                    memo = "케이뱅크 캐시백 ($label)",
                                )
                            }
                        }
                    }
                } else {
                    repository.updateTransaction(
                        id = transactionId,
                        occurredDate = s.date,
                        amountMinor = amount,
                        isIncome = s.isIncome,
                        categoryId = categoryId,
                        memo = s.memo,
                    )
                }
                onSuccess()
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun delete(onSuccess: () -> Unit) {
        val id = transactionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                repository.deleteTransaction(id)
                onSuccess()
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

class EditTransactionViewModelFactory(
    private val repository: BudgetRepository,
    private val transactionId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(EditTransactionViewModel::class.java)) {
            return EditTransactionViewModel(repository, transactionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
