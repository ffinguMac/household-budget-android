package com.householdbudget.app.domain.autoentry

import com.householdbudget.app.domain.autoentry.Direction.IN
import com.householdbudget.app.domain.autoentry.Direction.OUT
import com.householdbudget.app.domain.autoentry.Direction.REFUND
import com.householdbudget.app.domain.autoentry.ReviewReason.PERSON_TRANSFER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonNameTest {
    private val TOSS = "viva.republica.toss"
    private val KBANK = NotificationParsers.KBANK_PACKAGE

    private fun person(pkg: String, title: String, body: String): Boolean {
        val p = NotificationParsers.parse(pkg, title, body)!!
        return PersonName.looksLikePerson(p.merchant, p.direction, "$title\n$body")
    }

    private fun decide(pkg: String, title: String, body: String, learned: LearnedRule? = null): Decision {
        val p = NotificationParsers.parse(pkg, title, body)!!
        return AutoClassifier.classify(p, "$title\n$body", pkg, setOf(pkg), learned)
    }

    @Test fun bankTransfers_withPersonName() {
        assertTrue(person(KBANK, "케이뱅크", "입금 30,000원\n김가짜 | 생활통장(1234"))
        assertTrue(PersonName.looksLikePerson("김가짜님", OUT, "출금 30,000원"))
        assertTrue(person(TOSS, "토스", "김가짜님에게 50,000원 송금했어요"))
        assertTrue(person(TOSS, "680,000원 자동이체", "홍*자님에게 680,000원이 자동이체되었어요."))
        assertTrue(person(TOSS, "토스뱅크", "홍*동님이 45,000원을 보냈어요\n잔액 1,045,000원"))
        assertTrue(person("com.kbstar.kbbank", "", "[Web발신]\n[KB]10/07 21:15\n123456**789\n이가짜\n전자금융입금\n10,000\n잔액1,234,567"))
        assertTrue(person("com.hanabank.oqf", "", "[Web발신] 하나,08/31,09:54 123******12345 입금420,000원 이가짜 잔액437,892원"))
        assertTrue(person("com.wooribank.smart.npib", "[입금] 이가짜", "30,000원"))
        assertTrue(person("com.wooribank.smart.npib", "", "우리 01/17 19:33  *123456  출금 7,580원  엄마  잔액 604,141원"))
        assertTrue(PersonName.looksLikePerson("김**", IN, "입금 30,000원"))
        assertTrue(PersonName.looksLikePerson("이자영", IN, "입금 50,000원 이자영"))
        assertTrue(PersonName.looksLikePerson("남궁가짜", OUT, "출금 10,000원 남궁가짜"))
    }

    @Test fun cardPayments_atNameLikeShops_areNotPeople() {
        assertFalse(person("com.shcard.smartpay", "", "[Web발신] [신한체크승인] 홍*동(1234) 10/24 18:12 (금액)8,000원 가짜집"))
        assertFalse(PersonName.looksLikePerson("김가짜", OUT, "가짜카드 승인 7,000원 김가짜"))
        assertFalse(PersonName.looksLikePerson("김밥집", OUT, "KB국민체크 홍*동님 7,000원 김밥집 사용"))
        assertFalse(PersonName.looksLikePerson("이가짜", OUT, "토스뱅크 체크카드 이가짜 6,100원 결제"))
        assertFalse(PersonName.looksLikePerson("한가짜", OUT, "롯데 75,000원 일시불 한가짜 누적 754,000원"))
    }

    @Test fun nonPersonTokens_areNotPeople() {
        listOf(
            "가짜집", "(주)가짜", "㈜가짜", "급여", "월급", "가짜페이", "KB국민", "이자", "캐시백", "관리비", "보험료", "신한은행",
            "가짜송금", "가짜쇼핑", "예금결산", "한국전력", "카카오", "토스", "KIM GAJJA", "김가짜산업", "", "김",
            "이마트", "정카페", "김택시",
        ).forEach { assertFalse(it, PersonName.looksLikePerson(it, IN, "입금 50,000원 $it")) }
        assertFalse(PersonName.looksLikePerson("김가짜", REFUND, "입금 취소 50,000원 김가짜"))
        assertFalse(PersonName.looksLikePerson("김가짜", IN, "김가짜 50,000원"))  // 입출금 문구가 없다
    }

    @Test fun personTransfer_needsReview_evenWithLearnedRule() {
        assertEquals(Decision.NeedsReview(PERSON_TRANSFER), decide(KBANK, "입금 50,000원", "김가짜 | 가짜통장(1234)", LearnedRule(77)))
        assertEquals(Decision.NeedsReview(PERSON_TRANSFER), decide(TOSS, "토스", "김가짜님에게 10,000원 송금했어요", LearnedRule(null)))
    }

    @Test fun cardApprovalAtShop_stillUsesRule() =
        assertEquals(Decision.RegisterById(77), decide("com.fake.card", "가짜카드", "승인 8,000원\n김가짜", LearnedRule(77)))
}
