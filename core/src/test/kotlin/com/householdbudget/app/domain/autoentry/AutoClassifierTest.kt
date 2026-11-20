package com.householdbudget.app.domain.autoentry

import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.CategoryKind.EXPENSE
import com.householdbudget.app.domain.CategoryKind.INCOME
import com.householdbudget.app.domain.autoentry.Direction.IN
import com.householdbudget.app.domain.autoentry.Direction.OUT
import com.householdbudget.app.domain.autoentry.Direction.REFUND
import com.householdbudget.app.domain.autoentry.Direction.UNKNOWN
import com.householdbudget.app.domain.autoentry.ReviewReason.UNCLASSIFIED
import com.householdbudget.app.domain.autoentry.ReviewReason.UNKNOWN_DIRECTION
import com.householdbudget.app.domain.category.CategoryPath
import com.householdbudget.app.domain.category.FixedCategories
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private val KBANK = "com.kbankwith.smartbank"
private val NPAY = "com.naverfin.payapp"
private fun c(
    amount: Long, dir: Direction, merchant: String, text: String = merchant, pkg: String = KBANK,
    watched: Set<String> = setOf(KBANK), learned: LearnedRule? = null,
) = AutoClassifier.classify(ParsedNotification(amount, dir, merchant), text, pkg, watched, learned)
private fun path(k: CategoryKind, p: String, ch: String) = Decision.RegisterByPath(CategoryPath(k, p, ch))

class AutoClassifierTest {
    @Test fun builtinRules() {
        assertEquals(path(EXPENSE, "식비", "카페"), c(4500, OUT, "가짜커피 강남점"))
        assertEquals(path(EXPENSE, "식비", "장보기"), c(4800, OUT, "가짜마트"))
        assertEquals(path(EXPENSE, "교통", "택시"), c(9800, OUT, "카카오T 택시"))
        assertEquals(path(EXPENSE, "교통", "대중교통"), c(1500, OUT, "가짜버스운송"))
        assertEquals(path(EXPENSE, "식비", "배달"), c(23000, OUT, "배달의민족"))
        assertEquals(path(EXPENSE, "생활", "구독"), c(17000, OUT, "NETFLIX"))
    }

    @Test fun smartStore_isNotGrocery() =
        assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(12000, OUT, "스마트스토어가짜"))
    @Test fun unknownMerchant_needsReview() = assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(30000, OUT, "김가짜"))
    @Test fun unknownDirection_needsReview() = assertEquals(Decision.NeedsReview(UNKNOWN_DIRECTION), c(7000, UNKNOWN, "가짜커피"))
    @Test fun learnedRule_beatsBuiltin() =
        assertEquals(Decision.RegisterById(77), c(4500, OUT, "가짜커피", learned = LearnedRule(77)))
    @Test fun learnedIgnore() = assertEquals(Decision.Ignore, c(4500, OUT, "가짜커피", learned = LearnedRule(null)))
    @Test fun learnedRule_appliesToUnknownDirection() =
        assertEquals(Decision.RegisterById(77), c(7000, UNKNOWN, "김가짜", learned = LearnedRule(77)))
    @Test fun charge_whenPayAppWatched_isIgnored() =
        assertEquals(Decision.Ignore, c(10000, OUT, "네이버페이", "출금 10,000원 네이버페이 충전", watched = setOf(KBANK, NPAY)))
    @Test fun charge_whenPayAppNotWatched_isRecordedAsCharge() =
        assertEquals(path(EXPENSE, "쇼핑", "간편결제 충전"), c(10000, OUT, "네이버페이", "출금 10,000원 네이버페이 충전"))
    @Test fun payAppOwnPurchase_usesBuiltinRule() =
        assertEquals(path(EXPENSE, "식비", "카페"), c(4500, OUT, "가짜커피", "네이버페이 결제 4,500원 가짜커피", pkg = NPAY, watched = setOf(KBANK, NPAY)))
    @Test fun payAppOwnTopUp_isIgnored() =
        assertEquals(Decision.Ignore, c(10000, OUT, "", "네이버페이 충전 10,000원 완료", pkg = NPAY, watched = setOf(KBANK, NPAY)))
    @Test fun cashbackAndInterest_goToIncome() {
        assertEquals(path(INCOME, "수입", "캐시백/이자"), c(120, IN, "캐시백", "입금 120원 캐시백"))
        assertEquals(path(INCOME, "수입", "캐시백/이자"), c(35, IN, "가짜은행", "입금 35원 예금이자"))
    }
    @Test fun parsedCashbackDeposit_goesToCashbackIncome() {
        val text = "입금 45원 | 가짜카드 캐시백"
        val parsed = NotificationParsers.parse("com.fake.bank", "가짜은행", text)!!
        assertEquals(path(INCOME, "수입", "캐시백/이자"), AutoClassifier.classify(parsed, "가짜은행\n" + text, "com.fake.bank", setOf(KBANK), null))
    }
    @Test fun plainDeposit_needsReview() = assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(50000, IN, "김가짜", "입금 50,000원 김가짜"))
    @Test fun depositFromSenderNamedLikeInterest_needsReview() =
        assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(50000, IN, "이자영", "입금 50,000원 이자영"))
    @Test fun refund_goesToIncomeOther() = assertEquals(path(INCOME, "수입", "기타"), c(8900, REFUND, "테스트상점"))

    @Test fun everyBuiltinPath_existsInFixedTree() {
        val paths = AutoClassifier.MERCHANT_RULES.map { it.second } +
            listOf(AutoClassifier.CHARGE_PATH, AutoClassifier.CASHBACK_INTEREST_PATH, AutoClassifier.REFUND_PATH, AutoClassifier.INVEST_PATH)
        val tree = FixedCategories.TREE.flatMap { p -> p.children.map { CategoryPath(p.kind, p.name, it) } }.toSet()
        paths.forEach { assertTrue("missing in TREE: $it", it in tree) }
    }

    @Test fun bankTransferToSecurities_isInvestSaving() = assertEquals(
        Decision.RegisterByPath(AutoClassifier.INVEST_PATH),
        AutoClassifier.classify(ParsedNotification(500_000, Direction.OUT, "가짜증권"), "출금 500,000원 | 가짜증권", "com.kbankwith.smartbank", emptySet(), null))

    @Test fun transferToKakaoPaySecurities_isInvest_notCharge() = assertEquals(
        Decision.RegisterByPath(AutoClassifier.INVEST_PATH),
        AutoClassifier.classify(ParsedNotification(500_000, Direction.OUT, "카카오페이증권"), "출금 500,000원 | 카카오페이증권", "com.kbankwith.smartbank", setOf("com.kakaopay.app"), null))

    @Test fun securitiesTradeExecution_isIgnored() = assertEquals(
        Decision.Ignore,
        AutoClassifier.classify(ParsedNotification(700_000, Direction.OUT, "가짜전자"), "토스증권 가짜전자 10주 매수 체결 700,000원", "viva.republica.toss", emptySet(), null))
}
