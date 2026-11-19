package com.householdbudget.app.ui.autoentry

import com.householdbudget.app.R
import com.householdbudget.app.domain.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingLabelsTest {
    @Test fun defaultKind() {
        assertEquals(CategoryKind.INCOME, defaultKindFor("IN")); assertEquals(CategoryKind.INCOME, defaultKindFor("REFUND"))
        assertEquals(CategoryKind.EXPENSE, defaultKindFor("OUT")); assertEquals(CategoryKind.EXPENSE, defaultKindFor("UNKNOWN"))
    }

    @Test fun reasonLabels() {
        assertEquals(R.string.auto_reason_duplicate, reasonLabelRes("DUP_MANUAL"))
        assertEquals(R.string.auto_reason_duplicate, reasonLabelRes("DUP_CROSS_APP"))
        assertEquals(R.string.auto_reason_direction, reasonLabelRes("UNKNOWN_DIRECTION"))
        assertEquals(R.string.auto_reason_unclassified, reasonLabelRes("UNCLASSIFIED"))
        assertEquals(R.string.auto_reason_unclassified, reasonLabelRes(null))
    }
}
