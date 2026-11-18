package com.householdbudget.app.domain.autoentry

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantNormalizerTest {

    @Test fun normalize() {
        assertEquals("가짜커피코리아", MerchantNormalizer.normalize("(주) 가짜커피 코리아"))
        assertEquals("fakecafe강남점", MerchantNormalizer.normalize("Fake Cafe (강남점)"))
        assertEquals("가짜상사", MerchantNormalizer.normalize("주식회사 가짜상사"))
        assertEquals("", MerchantNormalizer.normalize("  "))
    }
}
