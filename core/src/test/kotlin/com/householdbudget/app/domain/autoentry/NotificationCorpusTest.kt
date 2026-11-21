package com.householdbudget.app.domain.autoentry

import com.householdbudget.app.domain.autoentry.Direction.IN
import com.householdbudget.app.domain.autoentry.Direction.OUT
import com.householdbudget.app.domain.autoentry.Direction.REFUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * docs/research/2026-11-20-finance-notification-formats.md 의 F01~F60, N01~N12.
 * 함수 이름 끝 글자 = 신뢰도(A 실제 문구 / B 코드에서 복원 / C 합성). 모든 값은 가짜 자리표시자.
 * SMS 형식은 title = "" 로, 문자 본문 전체를 body 로 넣는다.
 */
class NotificationCorpusTest {

    private val KBANK = NotificationParsers.KBANK_PACKAGE
    private val TOSS = "viva.republica.toss"
    private val KAKAOBANK = "com.kakaobank.channel"
    private val KBSTAR = "com.kbstar.kbbank"
    private val SHINHAN = "com.shinhan.sbanking"
    private val WOORI = "com.wooribank.smart.npib"
    private val HANA = "com.hanabank.oqf"
    private val NH = "nh.smart.banking"
    private val SHCARD = "com.shcard.smartpay"
    private val HYUNDAI = "com.hyundaicard.appcard"
    private val KBCARD = "com.kbcard.cxh.appcard"
    private val LOTTE = "com.lcacApp"
    private val WOORICARD = "com.wooricard.smartapp"
    private val HANACARD = "com.hanaskcard.paycla"
    private val KAKAOPAY = "com.kakaopay.app"
    private val NPAY = "com.naverfin.payapp"
    private val PAYBOOK = "kvp.jjy.MispAndroid320"
    private val SAMSUNGCARD = "kr.co.samsungcard.mpocket"
    private val IBK = "com.ibk.android.ionebank"
    private val MG = "com.smg.spbs"
    private val EPOST = "com.epost.psf.sdsi"
    private val NHPAY = "nh.smart.nhallonepay"
    private val GGCARD = "gov.gyeonggi.ggcard"

    /** merchant: 정확한 값, NON_BLANK = 비어 있지 않으면 됨, ANY = 검사 안 함. */
    private fun ok(pkg: String, title: String, body: String, amount: Long, dir: Direction, merchant: String) {
        val p = NotificationParsers.parse(pkg, title, body)
        assertNotNull("금액을 못 찾음", p)
        val gotMerchant = when {
            merchant == ANY -> ANY
            merchant == NON_BLANK && p!!.merchant.isNotBlank() -> NON_BLANK
            else -> p!!.merchant
        }
        assertEquals("$amount $dir [$merchant]", "${p!!.amountMinor} ${p.direction} [$gotMerchant]")
    }

    private fun sms(pkg: String, body: String, amount: Long, dir: Direction, merchant: String) =
        ok(pkg, "", body, amount, dir, merchant)

    private fun noParse(pkg: String, title: String, body: String) =
        assertNull(NotificationParsers.parse(pkg, title, body))

    /** 금액은 읽혀도(현재 코드 동작) 자동 등록되면 안 된다. 검토함(NeedsReview)이나 무시는 괜찮다. */
    private fun notAutoRegistered(pkg: String, title: String, body: String) {
        val p = NotificationParsers.parse(pkg, title, body) ?: return
        val d = AutoClassifier.classify(p, "$title\n$body", pkg, setOf(pkg), null)
        assertFalse("자동 등록됨: $d", d is Decision.RegisterById || d is Decision.RegisterByPath)
    }

    // ---- 1.1 케이뱅크
    @Test fun f01_B() = ok(KBANK, "케이뱅크", "입금 30,000원\n김가짜 | 생활통장(1234", 30000, IN, "김가짜")
    @Test fun f02_B() = sms(KBANK, "[Web발신]\n[케이뱅크]\n홍*동\n입금 30,000원\n잔액 150,000원\n김가짜", 30000, IN, NON_BLANK)
    @Test fun f03_C() = ok(KBANK, "케이뱅크", "승인 17,700원\n출금가능액 18,501원", 17700, OUT, ANY)

    // ---- 1.2 토스
    @Test fun f04_A() = ok(TOSS, "2원 입금 🎉", "토스뱅크 이벤트로 받은 용돈이에요. (토스뱅크 알림)", 2, IN, NON_BLANK)
    @Test fun f05_A() = ok(TOSS, "680,000원 자동이체", "홍*자님에게 680,000원이 자동이체되었어요.", 680000, OUT, "홍*자")
    @Test fun f06_A() = ok(TOSS, "토스 결제 완료", "21,379원 | 국민카드 \n가짜택시에서 121 포인트 사용했어요.", 21379, OUT, "가짜택시")
    @Test fun f07_B() = ok(TOSS, "30,000원 입금", "김가짜 ...", 30000, IN, "김가짜")
    @Test fun f08_C() = ok(TOSS, "토스뱅크 체크카드", "가짜커피 홍대입구 6,100원 결제", 6100, OUT, "가짜커피 홍대입구")
    @Test fun f09_C() = ok(TOSS, "토스뱅크 체크카드", "가짜쇼핑 12,000원 결제 취소", 12000, REFUND, "가짜쇼핑")
    @Test fun f10_C() = ok(TOSS, "토스", "김가짜님에게 10,000원 송금했어요", 10000, OUT, "김가짜")
    @Test fun f11_C() = ok(TOSS, "토스페이", "가짜편의점 합정역점에서 4,800원 토스페이로 결제했어요", 4800, OUT, "가짜편의점 합정역점")
    @Test fun f12_C() = ok(TOSS, "토스뱅크", "홍*동님이 45,000원을 보냈어요\n잔액 1,045,000원", 45000, IN, "홍*동")

    // ---- 1.3 카카오뱅크
    @Test fun f13_B() = ok(KAKAOBANK, "입금 30,000원", "김가짜 ...", 30000, IN, "김가짜")
    @Test fun f14_B() = sms(KAKAOBANK, "입출금내역 안내 ... 입금 30,000원 ... 김가짜", 30000, IN, NON_BLANK)
    @Test fun f15_B() = sms(KAKAOBANK, "[내통장] 05/17 16:03\n가짜커피\n-4,500원", 4500, OUT, "가짜커피")

    // ---- 1.4 KB국민은행
    @Test fun f16_B() = sms(KBSTAR, "[Web발신]\n[KB]10/07 21:15\n123456**789\n김가짜\n전자금융입금\n10,000\n잔액1,234,567", 10000, IN, "김가짜")
    @Test fun f17_B() = sms(KBSTAR, "KB국민 10/14 13:22 1234**5678 (주)가짜산업 입금 550,000 잔액 1,234,567", 550000, IN, "(주)가짜산업")
    @Test fun f18_B() = ok(KBSTAR, "출금 10,000원", "홍*동님 07/27 09:53 123456-**-***789 가짜도로공사 체크카드출금 10,000 잔액52,980", 10000, OUT, "가짜도로공사")
    @Test fun f19_B() = sms(KBSTAR, "KB스타알림 ... 김가짜 ... 전자금융입금10,000원 ...", 10000, IN, NON_BLANK)

    // ---- 1.5 신한 SOL뱅크
    @Test fun f20_A() = sms(SHINHAN, "[Web발신] 신한11/14 22:42 110-123-456789 출금 10,450 잔액 136,269 가짜구독서비스", 10450, OUT, "가짜구독서비스")
    @Test fun f21_A() = sms(SHINHAN, "[Web발신] 신한12/13 18:26 110-123-456789 입금 500,000 잔액 514,112 KIM GAJJA", 500000, IN, "KIM GAJJA")
    @Test fun f22_A() = sms(SHINHAN, "[Web발신] [신한은행]07/08 10:35:14 [110-***-123456] 출금 2,210,000원 (KIMGAJJA)", 2210000, OUT, "KIMGAJJA")
    @Test fun f23_A() = sms(SHINHAN, "[Web발신] [신한은행] [사고예방] 신한은행 10/15 03:32:55.23 [110-***-123456] 지급 595,315원 (KIMGA", 595315, OUT, "KIMGA")
    @Test fun f24_B() = sms(SHINHAN, "SOL알리미 입금 30,000원 김가짜 ...", 30000, IN, NON_BLANK)

    // ---- 1.6 우리WON뱅킹
    @Test fun f25_A() = sms(WOORI, "우리 01/17 19:33  *123456  출금 7,580원  가짜마트  잔액 604,141원", 7580, OUT, "가짜마트")
    @Test fun f26_A() = sms(WOORI, "우리 06/20 11:20 *123456 입금 472원 예금결산 잔액 1,199,234원", 472, IN, "예금결산")
    @Test fun f27a_A() = sms(WOORI, "[Web발신] WOORI 07/29 [11:24] *123456 PAY 25,000WON 가짜식당 BAL 7,960WON", 25000, OUT, "가짜식당")
    @Test fun f27b_A() = sms(WOORI, "[Web발신] WOORI 07/28 [22:42] *123456 RCV 30,000WON KIM GAJJA BAL 32,960WON", 30000, IN, "KIM GAJJA")
    @Test fun f28_B() = sms(WOORI, "[Web발신]\n우리 07/29 11:24\n*123456\n입금 30,000원\n김가짜\n잔액 32,960원", 30000, IN, "김가짜")
    @Test fun f29a_B() = ok(WOORI, "[입금] 김가짜", "30,000원", 30000, IN, "김가짜")
    @Test fun f29b_B() = ok(WOORI, "[출금] 가짜마트", "7,580원", 7580, OUT, "가짜마트")

    // ---- 1.7 하나은행
    @Test fun f30_A() = ok(HANA, "하나은행", "출금 10,000원 가짜쇼핑 잔액 352,232원 03/05 11:21 123-******-12345", 10000, OUT, "가짜쇼핑")
    @Test fun f31a_A() = sms(HANA, "[Web발신] 하나,09/11,02:37 123******12345 출금40,000원 가짜송금　　　　　 잔액201,610원", 40000, OUT, "가짜송금")
    @Test fun f31b_A() = sms(HANA, "[Web발신] 하나,09/17,15:42 123******12345 출금10,000 가짜송금　　　　　 잔액1,968,123", 10000, OUT, "가짜송금")
    @Test fun f31c_A() = sms(HANA, "[Web발신] 하나,08/31,09:54 123******12345 입금420,000원 김가짜 잔액437,892원", 420000, IN, "김가짜")
    @Test fun f32a_A() = sms(HANA, "[Web발신] 하나,07/26,20:01 123******12345 출금300,000원 KIMGAJJA", 300000, OUT, "KIMGAJJA")
    @Test fun f32b_A() = sms(HANA, "[Web발신] 하나,09/10 11:17 123******12345 출금13,958원 0908_FAKESTORE*i 잔액669,942원", 13958, OUT, "0908_FAKESTORE*i")
    @Test fun f33a_A() = sms(HANA, "HANABANK,04/23,13:10 123******12345 W/D:1,000 가짜커피코 Bal.:2,495,623", 1000, OUT, "가짜커피코")
    @Test fun f33b_A() = sms(HANA, "[Web발신] HANABANK,04/18,13:15 123******12345 W/D:200,000 Bal.:474,341", 200000, OUT, ANY)
    @Test fun f33c_A() = sms(HANA, "[Web발신] HANABANK,12/05,17:03 123******12345 Dep.:297,000 김가짜 Bal.:1,345,083", 297000, IN, "김가짜")
    @Test fun f34_B() = sms(HANA, "[Web발신]\n하나,07/29,11:24\n123***45678\n입금30,000원\n김가짜\n잔액150,000원", 30000, IN, "김가짜")

    // ---- 1.8 NH농협
    @Test fun f35a_A() = sms(NH, "[Web발신] 농협 출금701,000원 08/22 22:35 123-****-1234-12 가짜오픈뱅 잔액4,308,001원", 701000, OUT, "가짜오픈뱅")
    @Test fun f35b_A() = sms(NH, "[Web발신]  농협 입금73,000원  08/14 23:34  123-****-1234-12  KIMGAJJA  잔액123,189원", 73000, IN, "KIMGAJJA")
    @Test fun f36_A() = sms(NH, "[Web발신] 농협07/22 20:18  123-****-1234-12  자동출금16,421원 (가짜인터넷)  잔액51,089원", 16421, OUT, "가짜인터넷")
    @Test fun f37_B() = ok(NH, "입출금 알림: 농협 입금30,000원 ...", "김가짜 ...", 30000, IN, NON_BLANK)

    // ---- 2.1 신한카드
    @Test fun f38_A() = sms(SHCARD, "[Web발신] [신한체크승인] 홍*동(1234) 10/24 18:12 (금액)81,450원 (유)가짜식자재할인마", 81450, OUT, "(유)가짜식자재할인마")
    @Test fun f39_A() = sms(SHCARD, "[Web발신]\n신한체크승인 홍*동(1*3*) 01/31 12:19 10,000원 주식회사가짜 잔액5,163원", 10000, OUT, "주식회사가짜")
    // 해외 승인(원 없음): 잘못된 금액을 만들지 말고 기록만 남기도록 null.
    @Test fun f40a_A() = noParse(SHCARD, "", "[Web발신] 신한체크해외승인 홍*동(1234) 10/16 16:00 14.99 달러 (US)FAKESTORE *Ya")
    @Test fun f40b_A() = noParse(SHCARD, "", "[Web발신] 신한체크해외승인 홍*동(1234) 10/12 16:59 RUB 299.00 (RU)FAKE TV")

    // ---- 2.2 현대카드
    @Test fun f41_A() = ok(HYUNDAI, "현대카드", "홍*동 님, 가짜현대카드 승인 \n9,200원 일시불, 3/1 18:30\n가짜생활용품 \n누적287,514원", 9200, OUT, "가짜생활용품")
    @Test fun f42_A() = sms(HYUNDAI, "[Web발신]\n현대ZERO승인 홍*동\n68,000원 일시불\n08/10 21:03 가짜숙박\n1.2% 할인", 68000, OUT, "가짜숙박")

    // ---- 2.3 KB국민카드
    @Test fun f43_A() = ok(KBCARD, "17,000원 결제", "KB국민카드 ⎸ 가짜구독서비스(일시불)", 17000, OUT, "가짜구독서비스")
    @Test fun f44a_A() = ok(KBCARD, "KB Pay", "KB Pay\nKB국민카드1234승인\n홍*동님\n600원 일시불\n02/06 20:49\n가짜 아이스크림\n누적1,194,249원", 600, OUT, "가짜 아이스크림")
    @Test fun f44b_A() = ok(KBCARD, "KB Pay", "KB국민카드1234승인 홍*동님 600원 일시불 02/06 20:49 가짜 아이스크림 누적1,194,249원", 600, OUT, "가짜 아이스크림")
    @Test fun f45_A() = sms(KBCARD, "[Web발신]\nKB국민카드 1*3* 승인\n홍*동\n75,200원 일시불\n08/19 16:29\n가짜철도공사\n누적 ***원", 75200, OUT, "가짜철도공사")
    @Test fun f46a_A() = sms(KBCARD, "[Web발신]\nKB국민체크(1*3*)\n홍*동\n08/14 19:00\n7,290원\n가짜편의점서초 사용", 7290, OUT, "가짜편의점서초")
    @Test fun f46b_A() = sms(KBCARD, "KB국민체크(1*3*) 홍*동님 02/26 18:09 1,500원 가짜편의점 남산 사용", 1500, OUT, "가짜편의점 남산")

    // ---- 2.4 롯데카드
    @Test fun f47_A() = sms(LOTTE, "롯데OO(1*2*) 75,000원 일시불 08/16 10:30 가짜백화점 본점 누적 754,000원", 75000, OUT, "가짜백화점 본점")

    // ---- 2.5 우리카드
    @Test fun f48_B() = ok(WOORICARD, "안내", "우리카드 이용안내 우리카드(1234)체크승인\n홍*동님\n12,000원\n05/1716:03\n가짜커피", 12000, OUT, "가짜커피")

    // ---- 2.6 하나카드
    @Test fun f49_A() = sms(HANACARD, "[Web발신]\n하나(1*2*) 승인 홍*동님 25,000원 일시불 08/13 20:50 가짜보신탕 누적 4,055,876원", 25000, OUT, "가짜보신탕")
    @Test fun f50_A() = sms(HANACARD, "가짜페이(가짜이 / 체크카드(일시불) / 05.09 13:14 ║ (결제) 33,900원", 33900, OUT, "가짜페이(가짜이")

    // ---- 3. 페이 (합성)
    @Test fun f51a_C() = sms(KAKAOPAY, "카카오페이 30,000원 충전", 30000, OUT, "카카오페이")
    @Test fun f51b_C() = sms(TOSS, "카카오페이머니 20,000원 충전했어요", 20000, OUT, "카카오페이머니")
    @Test fun f52_C() = ok(NPAY, "네이버페이", "네이버페이 10,000원 충전", 10000, OUT, "네이버페이")

    // ---- 4. 목록 밖 앱
    @Test fun f53a_A() = sms(SAMSUNGCARD, "[Web발신] 삼성1234승인 홍* 732,710원 12개월 10/28 12:17 가짜세입금 누적2,550,680원", 732710, OUT, "가짜세입금")
    @Test fun f53b_A() = sms(SAMSUNGCARD, "삼성1234승인 홍* 100원 일시불 01/09 20:07 가짜편의점아산 누적80,090원", 100, OUT, "가짜편의점아산")
    @Test fun f54_A() = sms(SAMSUNGCARD, "[Web발신]\n삼성1234승인\n202,000원 일시불\n10/19 12:28 ㈜가짜전자", 202000, OUT, "㈜가짜전자")
    @Test fun f55a_A() = sms(IBK, "[Web발신] 2021/08/17 19:00 입금 1,000원. 잔액 1,014,145원 KIMGAJJA 123***12345678 기업", 1000, IN, "KIMGAJJA")
    @Test fun f55b_A() = sms(IBK, "[Web발신] 2021/08/19 04:55 출금 11,000원 잔액 1,003,145원 KIMGAJJA_아 123***12345678 기업", 11000, OUT, "KIMGAJJA_아")
    @Test fun f56_A() = sms(IBK, "[Web발신]\n08/13 12:33\n출금 3,500원\n잔액 120,000원\n가짜편의점시흥\n123*456789\n기업", 3500, OUT, "가짜편의점시흥")
    @Test fun f57_B() = sms(MG, "[Web발신]\n<새마을금고>1234***5678\n김가짜\n입금30,000\n잔액150,000원\n07/29 11:24", 30000, IN, "김가짜")
    @Test fun f58_B() = sms(EPOST, "우체국,11:24\n1234***5678\n입금30,000원\n잔액150,000원\n김가짜", 30000, IN, "김가짜")
    @Test fun f59_A() = sms(NHPAY, "[Web발신]\nNH카드1*3*승인\n홍*동\n56,000원 체크\n08/15 15:27\n가짜식당", 56000, OUT, "가짜식당")
    @Test fun f60a_A() = ok(GGCARD, "경기지역화폐", "결제 완료 2,650원...", 2650, OUT, ANY)
    @Test fun f60b_C() = sms(GGCARD, "[경기지역화폐] 15,000원 결제\n(가짜커피 수원점)\n잔액: 35,000원", 15000, OUT, "가짜커피 수원점")

    // ---- 5. 거래가 아닌 푸시
    @Test fun n01() = notAutoRegistered(TOSS, "환율 예측 결과가 나왔어요🥁", "예측대로 오르면 2원을 받을 수 있어요 (토스뱅크 알림)")
    @Test fun n02() = notAutoRegistered(TOSS, "가짜주식 📉", "최근 1년 중 최저가를 기록했어요 (549,937원)")
    @Test fun n03() = noParse(TOSS, "자동이체 공지사항 도착", "출금 이체에 동의해야 자동이체를 계속 쓸 수 있어요.")
    @Test fun n04() = notAutoRegistered(TOSS, "홍*동님, 신용퀴즈가 다시 열렸어요", "오늘도 퀴즈 맞히고 최대 10원 받아가세요.")
    @Test fun n05() = noParse(TOSS, "굴비적금 안내", "이번 달에도 30만원까지 저금할 수 있어요. …")
    @Test fun n06() = noParse(PAYBOOK, "페이북 머니 적립안내", "(광고)BC카드 결제하셨네요! 100% 당첨 머니 박스를 받았어요. …")
    @Test fun n07() = noParse(KAKAOBANK, "카드 청구서가 도착했어요.", "3월14일(금)에 출금 예정인 1개 카드사의 청구서를 지금 바로 확인해 보세요.")
    @Test fun n08() = noParse(KBCARD, "KBPay", "홍*동님, 최근 일주일 이내에 2건의 카드 결제 취소가 있어요. …")
    @Test fun n09() = noParse(KBANK, "(광고)케이뱅크 처음이라면", "네이버페이 2천원권 드려요 …")
    @Test fun n10() = noParse(HANA, "하나은행", "하나은행 기준환율 안내 2025.03.05 13:57:40 [USD 1456.70], [CAD 1009.46]")
    @Test fun n11() = noParse(HYUNDAI, "(광고)⟪현대카드⟫큰 지출 나눠내는 분할결제", "")
    @Test fun n12() = noParse(TOSS, "총 상금 2,000만원", "(광고) 홍*동님, 실전투자대회에 …")

    // ---- 리뷰에서 나온 오탐·회귀 (코퍼스 밖, 가짜 문구)
    @Test fun review_couponsAndNoticesAreNotAutoRegistered() = listOf(
        "스타벅스 3,000원 쿠폰 사용 가능",
        "카카오T 택시 5,000원 할인쿠폰을 사용해 보세요",
        "스타벅스에서 사용 가능한 5,000원 쿠폰이 도착했어요",
        "배달의민족 3,000원 할인쿠폰 지급",
        "마트 할인쿠폰 3,000원 지급 완료",
        "캐시백 3,000원 지급 스타벅스 이벤트",
        "11/25 결제 예정 금액 523,400원\nSK텔레콤 외 12건",
        "이번 달 결제예정금액 523,400원 주유 혜택",
        "스타벅스 5,000원 승인거절",
        "택시 요금 10% 할인! 30,000원 이상 결제 시",
    ).forEach { notAutoRegistered(KBCARD, "알림", it) }

    @Test fun review_insurancePayoutIsNotOut() =
        assertFalse(NotificationParsers.parse(SHINHAN, "알림", "보험금 1,000,000원 지급 완료")?.direction == OUT)

    @Test fun review_unitlessAmountNeedsThousandsComma() {
        sms(SHINHAN, "출금\n1234\n30,000원\n가짜상점", 30000, OUT, ANY)
        sms(SHINHAN, "신한 출금 1234 가짜상점 10,450원", 10450, OUT, ANY)
        sms(SHINHAN, "출금 110 30,000원 가짜", 30000, OUT, ANY)
        sms(SHINHAN, "출금 2026 년 30,000원", 30000, OUT, ANY)
    }

    @Test fun review_amountKeywordWordBoundary() {
        sms(KAKAOBANK, "입출금 30,000원 입금 김가짜", 30000, IN, ANY)
        assertEquals(Direction.UNKNOWN, NotificationParsers.parse(KAKAOBANK, "", "입출금 30,000원 김가짜")?.direction)
        sms(SHCARD, "서울시세입금 6,100원 결제", 6100, OUT, ANY)
        val text = "체크카드 승인 지방세입금 45,000원 캐시백 45원"
        val p = NotificationParsers.parse(SHCARD, "알림", text)!!
        assertEquals(OUT, p.direction)
        val d = AutoClassifier.classify(p, "알림\n$text", SHCARD, setOf(SHCARD), null)
        assertFalse(d == Decision.RegisterByPath(AutoClassifier.CASHBACK_INTEREST_PATH))
    }

    @Test fun review_bankShortNameIsNotMerchant() {
        sms(WOORI, "우리 01/17 19:33  *123456  출금 7,580원  엄마  잔액 604,141원", 7580, OUT, "엄마")
        sms(SHINHAN, "[Web발신] 신한11/14 22:42 110-123-456789 출금 10,450 잔액 136,269 CU", 10450, OUT, "CU")
    }

    @Test fun review_accountSentenceIsNotMerchant() =
        ok(TOSS, "토스", "토스뱅크 통장에서 15,000원 출금했어요\n가짜택시", 15000, OUT, "가짜택시")

    private companion object {
        const val NON_BLANK = "<non-blank>"
        const val ANY = "<any>"
    }
}
