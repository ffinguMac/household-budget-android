package com.householdbudget.app.ui.autoentry

import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.AutoEntryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingTriageTest {
    private fun e(id: Long) = AutoEntryEntity(
        id = id, logId = id, packageName = "p", occurredEpochDay = 0, postedAtMs = 0, amountMinor = 1000,
        direction = "OUT", merchant = "m$id", merchantKey = "m$id", status = AutoEntryStatus.PENDING,
    )

    @Test fun firstIsCurrent_nextTwoAreUpNext() {
        val s = triageState(listOf(e(1), e(2), e(3)), emptySet(), 0)
        assertEquals(1L, s.current?.id); assertEquals(listOf(2L, 3L), s.upNext.map { it.id })
        assertEquals(1, s.position); assertEquals(3, s.total)
    }

    @Test fun deferredGoesToTheEnd() {
        val s = triageState(listOf(e(1), e(2), e(3)), setOf(1L), 0)
        assertEquals(2L, s.current?.id); assertEquals(listOf(3L, 1L), s.upNext.map { it.id })
    }

    @Test fun cascadeResolvedTwoAtOnce_progressStaysConsistent() {
        val s = triageState(listOf(e(3)), emptySet(), 2)
        assertEquals(3, s.position); assertEquals(3, s.total); assertEquals(emptyList<AutoEntryEntity>(), s.upNext)
    }

    @Test fun allDeferred_stillShowsOne() = assertEquals(1L, triageState(listOf(e(1)), setOf(1L), 0).current?.id)

    @Test fun empty_hasNoCurrent() {
        val s = triageState(emptyList(), emptySet(), 3)
        assertNull(s.current); assertEquals(3, s.position); assertEquals(3, s.total)
    }
}
