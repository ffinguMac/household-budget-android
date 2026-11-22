package com.householdbudget.app.ui.autoentry

import com.householdbudget.app.data.local.entity.AutoEntryEntity

/** 한 건씩 정리하는 화면의 상태. [position]은 1부터, 함께 정리된 항목도 [total]에 남는다. */
data class TriageState(
    val current: AutoEntryEntity?,
    val upNext: List<AutoEntryEntity>,
    val position: Int,
    val total: Int,
)

/** 미루지 않은 항목(원래 순서) 뒤에 미룬 항목. 전부 미뤄도 첫 항목을 보여 준다. */
fun triageState(pending: List<AutoEntryEntity>, deferredIds: Set<Long>, doneCount: Int): TriageState {
    val (deferred, fresh) = pending.partition { it.id in deferredIds }
    val ordered = fresh + deferred
    val total = doneCount + pending.size
    return TriageState(ordered.firstOrNull(), ordered.drop(1).take(2), minOf(doneCount + 1, total), total)
}
