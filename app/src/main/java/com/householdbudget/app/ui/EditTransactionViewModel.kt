package com.householdbudget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CashbackChannel { ONLINE, OFFLINE }

data class EditTransactionUiState(
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
    val amountText: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val parentId: Long? = null,
    val categoryId: Long? = null,
    val memo: String = "",
    val isSaving: Boolean = false,
    /** 수정 모드에서 서버(로컬) 로드 완료 여부 */
    val loadFinished: Boolean = false,
) {
    /** 현재 입력된 금액 (숫자만). 비어 있으면 0. */
    val amountMinor: Long
        get() = amountText.toLongOrNull() ?: 0L

    /** 저장 가능 여부 — 금액 > 0 이고 소분류가 선택되어 있어야 한다. */
    val canSave: Boolean
        get() = amountMinor > 0L && categoryId != null
}

class EditTransactionViewModel(
    private val repository: BudgetRepository,
    private val transactionId: Long?,
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Seoul")

    private val _uiState = MutableStateFlow(EditTransactionUiState())
    val uiState: StateFlow<EditTransactionUiState> = _uiState.asStateFlow()

    /** 수정 모드에서 로드 직후 상태 스냅샷 (변경 여부 판단용). */
    private var loadedSnapshot: EditTransactionUiState? = null

    /**
     * 최근 60일 거래 기준, kind별 최근 사용 소분류 id (최신순, 중복 제거, 최대 6개).
     * 카테고리 빠른 선택 칩에 쓴다.
     */
    val recentCategoryIdsByKind: StateFlow<Map<CategoryKind, List<Long>>> =
        run {
            val today = LocalDate.now(zone)
            repository
                .observeTransactionsInRange(
                    startEpochDay = today.minusDays(RECENT_LOOKBACK_DAYS).toEpochDay(),
                    endExclusiveEpochDay = today.plusDays(1).toEpochDay(),
                )
                .map { rows ->
                    rows
                        .sortedWith(
                            compareByDescending<TransactionWithCategoryRow> { it.occurredEpochDay }
                                .thenByDescending { it.id },
                        )
                        .groupBy { CategoryKind.fromStorage(it.kind) }
                        .mapValues { (_, v) ->
                            v.map { it.categoryId }.distinct().take(RECENT_MAX_CHIPS)
                        }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyMap(),
                )
        }

    init {
        if (transactionId != null) {
            viewModelScope.launch {
                val row = repository.getTransaction(transactionId)
                if (row != null) {
                    val loaded =
                        EditTransactionUiState(
                            date = LocalDate.ofEpochDay(row.occurredEpochDay),
                            amountText = row.amountMinor.toString(),
                            kind = CategoryKind.fromStorage(row.kind),
                            parentId = row.parentCategoryId,
                            categoryId = row.categoryId,
                            memo = row.memo,
                            loadFinished = true,
                        )
                    loadedSnapshot = loaded
                    _uiState.update { loaded }
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

    /**
     * 금액 입력. 콤마 등 숫자 이외 문자는 걸러내고, 앞자리 0 은 제거하며,
     * 자릿수는 [MAX_AMOUNT_DIGITS]로 제한한다.
     */
    fun setAmountText(text: String) {
        val digits =
            text.filter { it.isDigit() }
                .trimStart('0')
                .take(MAX_AMOUNT_DIGITS)
        _uiState.update { it.copy(amountText = digits) }
    }

    /** 내장 키패드에서 숫자("1"~"9", "0", "00") 입력. */
    fun appendAmountDigits(digits: String) {
        setAmountText(_uiState.value.amountText + digits)
    }

    /** 내장 키패드 ⌫ — 마지막 한 자리 삭제. */
    fun deleteLastAmountDigit() {
        _uiState.update { it.copy(amountText = it.amountText.dropLast(1)) }
    }

    /** 내장 키패드 ⌫ 길게 누름 — 전체 삭제. */
    fun clearAmount() {
        _uiState.update { it.copy(amountText = "") }
    }

    fun setKind(value: CategoryKind) {
        _uiState.update { s ->
            if (s.kind == value) s
            else s.copy(kind = value, parentId = null, categoryId = null)
        }
    }

    fun setParent(parentId: Long, firstLeaf: CategoryEntity?) {
        _uiState.update { it.copy(parentId = parentId, categoryId = firstLeaf?.id) }
    }

    fun setCategoryId(id: Long) {
        _uiState.update { it.copy(categoryId = id) }
    }

    fun setMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    /**
     * 닫기 전에 확인 다이얼로그가 필요한지 판단.
     * 신규: 금액이나 메모를 입력했으면 true.
     * 수정: 로드된 값에서 하나라도 바뀌었으면 true.
     */
    fun hasUnsavedChanges(): Boolean {
        val s = _uiState.value
        val base = loadedSnapshot
        return if (transactionId == null || base == null) {
            s.amountText.isNotEmpty() || s.memo.isNotBlank()
        } else {
            s.date != base.date ||
                s.amountText != base.amountText ||
                s.kind != base.kind ||
                s.categoryId != base.categoryId ||
                s.memo != base.memo
        }
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
                        categoryId = categoryId,
                        memo = s.memo,
                    )
                    if (s.kind == CategoryKind.EXPENSE && cashbackChannel != null) {
                        val rate = if (cashbackChannel == CashbackChannel.ONLINE) 11L else 6L
                        val cashback = amount * rate / 1000L
                        if (cashback > 0L) {
                            val incomeLeafId =
                                repository.observeCategories().first()
                                    .firstOrNull {
                                        it.kind == CategoryKind.INCOME.storage && it.parentId != null
                                    }?.id
                            if (incomeLeafId != null) {
                                val label =
                                    if (cashbackChannel == CashbackChannel.ONLINE) "온라인 1.1%"
                                    else "오프라인 0.6%"
                                repository.insertTransaction(
                                    occurredDate = s.date,
                                    amountMinor = cashback,
                                    categoryId = incomeLeafId,
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

    companion object {
        /** Long 오버플로 방지 겸 현실적인 최대 금액 자릿수. */
        private const val MAX_AMOUNT_DIGITS = 12
        private const val RECENT_LOOKBACK_DAYS = 60L
        private const val RECENT_MAX_CHIPS = 6
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
