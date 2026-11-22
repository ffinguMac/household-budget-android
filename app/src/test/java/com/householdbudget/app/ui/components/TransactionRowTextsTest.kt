package com.householdbudget.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionRowTextsTest {
    @Test fun merchantIsTitle_categoryIsSubtitle() =
        assertEquals("김가짜 칼국수" to "식비 · 식사", transactionRowTexts("식사", "식비", "김가짜 칼국수", null))

    @Test fun blankMemo_fallsBackToLeafName() =
        assertEquals("식사" to "식비", transactionRowTexts("식사", "식비", "  ", null))

    @Test fun dateLabelLeadsSubtitle() =
        assertEquals("가짜커피" to "11.22 (일) · 식비 · 카페", transactionRowTexts("카페", "식비", "가짜커피", "11.22 (일)"))

    @Test fun noParentNoMemo_hasEmptySubtitle() =
        assertEquals("월급" to "", transactionRowTexts("월급", null, "", null))
}
