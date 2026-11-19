package com.householdbudget.app.domain.autoentry

import com.householdbudget.app.domain.autoentry.Direction.IN
import com.householdbudget.app.domain.autoentry.Direction.OUT
import com.householdbudget.app.domain.autoentry.Direction.REFUND
import com.householdbudget.app.domain.autoentry.Direction.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParsersTest {

    private fun p(title: String, body: String, pkg: String = "com.fake.bank") = NotificationParsers.parse(pkg, title, body)

    @Test fun cardApproval_multiLine() =
        assertEquals(ParsedNotification(12000, OUT, "별다방 강남점"), p("가짜카드", "승인 12,000원\n별다방 강남점\n11/18 12:34 잔액 345,000원"))

    @Test fun balanceBeforeAmount_isSkipped() =
        assertEquals(ParsedNotification(3300, OUT, "가나다마트"), p("가짜은행", "잔액 345,000원 | 출금 3,300원 | 가나다마트"))

    @Test fun onlyBalance_returnsNull() = assertNull(p("가짜은행", "잔액 10,000원 안내"))

    @Test fun genericDeposit() =
        assertEquals(ParsedNotification(50000, IN, "김가짜"), p("입금 50,000원", "김가짜 | 생활비통장(1234)"))

    @Test fun refund_winsOverPaymentKeyword() =
        assertEquals(ParsedNotification(8900, REFUND, "테스트상점"), p("가짜카드", "결제 취소 8,900원\n테스트상점"))

    @Test fun amountWithoutDirection_isUnknown() =
        assertEquals(ParsedNotification(7000, UNKNOWN, "테스트상점"), p("가짜은행", "7,000원 테스트상점"))

    @Test fun accountNameWithChulgeum_doesNotMeanOut() =
        assertEquals(ParsedNotification(5000, UNKNOWN, "김가짜"), p("가짜은행", "5,000원 김가짜 입출금통장(1234)"))

    @Test fun izakaya_isNotIncoming() =
        assertEquals(ParsedNotification(35000, OUT, "가짜이자카야"), p("가짜카드", "승인 35,000원\n가짜이자카야"))

    @Test fun maskedCardToken_isRemovedFromMerchant() =
        assertEquals(ParsedNotification(4500, OUT, "테스트커피"), p("가짜카드", "가짜카드(1*3*) 승인 4,500원 일시불\n테스트커피"))

    @Test fun chargeKeepsPayName() =
        assertEquals(ParsedNotification(10000, OUT, "네이버페이"), p("출금 10,000원", "네이버페이 충전 | 가짜통장(1234)"))

    @Test fun pipeWithoutSpaces_keepsMerchant() =
        assertEquals(ParsedNotification(3300, OUT, "가나다마트"), p("가짜은행", "출금 3,300원|가나다마트|가짜통장(1234)"))

    @Test fun noAmount_returnsNull() = assertNull(p("로그인 알림", "새 기기에서 로그인했어요"))

    @Test fun ad_returnsNull() = assertNull(p("(광고) 가짜카드", "지금 결제하면 5,000원 할인\n수신거부: 설정 > 알림"))

    @Test fun emptyBody_givesEmptyMerchant() = assertEquals(ParsedNotification(3000, OUT, ""), p("출금 3,000원", ""))

    @Test fun kbankDeposit_oneWon() =
        assertEquals(ParsedNotification(1, IN, "김가짜"), p("입금 1원", "김가짜 | 가짜통장(1234)", NotificationParsers.KBANK_PACKAGE))

    @Test fun kbankDeposit_withoutPipe_fallsBackToGeneric() =
        assertEquals(ParsedNotification(2000, IN, "김가짜"), p("입금 2,000원", "김가짜", NotificationParsers.KBANK_PACKAGE))

    @Test fun cardApprovalWithCashback_isOut() =
        assertEquals(OUT, p("가짜카드", "체크카드 승인 4,500원 가짜커피 캐시백 45원")?.direction)

    @Test fun cashbackDeposit_isIn() =
        assertEquals(ParsedNotification(45, IN, "가짜카드 캐시백"), p("가짜은행", "입금 45원 | 가짜카드 캐시백"))

    @Test fun hugeBodyWithoutAmount_returnsNull() = assertNull(p("가짜은행", "가".repeat(100_000)))
}
