package com.householdbudget.app.domain.autoentry

import com.householdbudget.app.domain.CategoryKind.EXPENSE
import com.householdbudget.app.domain.CategoryKind.INCOME
import com.householdbudget.app.domain.category.CategoryPath

enum class ReviewReason { UNKNOWN_DIRECTION, UNCLASSIFIED, DUP_CROSS_APP, DUP_MANUAL, DUP_REPOST }

/** 호출자가 (정규화한 이름, 방향)으로 찾아 넘긴 규칙. categoryId == null 이면 "기록 안 함". */
data class LearnedRule(val categoryId: Long?)

sealed interface Decision {
    data class RegisterById(val categoryId: Long) : Decision
    data class RegisterByPath(val path: CategoryPath) : Decision
    data object Ignore : Decision
    data class NeedsReview(val reason: ReviewReason) : Decision
}

object AutoClassifier {
    internal val CHARGE_PATH = CategoryPath(EXPENSE, "쇼핑", "간편결제 충전")
    internal val CASHBACK_INTEREST_PATH = CategoryPath(INCOME, "수입", "캐시백/이자")
    internal val REFUND_PATH = CategoryPath(INCOME, "수입", "기타")

    /** 정규화한 이름에 키워드가 들어 있으면 일치, 위에서부터 첫 일치. */
    internal val MERCHANT_RULES: List<Pair<List<String>, CategoryPath>> = listOf(
        listOf("배달의민족", "배민", "요기요", "쿠팡이츠") to CategoryPath(EXPENSE, "식비", "배달"),
        listOf("택시", "카카오t") to CategoryPath(EXPENSE, "교통", "택시"),
        listOf("버스", "지하철", "티머니") to CategoryPath(EXPENSE, "교통", "대중교통"),
        listOf("주유", "주차") to CategoryPath(EXPENSE, "교통", "주유/주차"),
        listOf("카페", "커피", "제과", "베이커리", "스타벅스") to CategoryPath(EXPENSE, "식비", "카페"),
        listOf("마트", "편의점", "gs25", "세븐일레븐", "씨유") to CategoryPath(EXPENSE, "식비", "장보기"),
        listOf("텔레콤", "유플러스", "케이티", "알뜰폰") to CategoryPath(EXPENSE, "생활", "통신"),
        listOf("넷플릭스", "netflix", "유튜브", "youtube") to CategoryPath(EXPENSE, "생활", "구독"),
    )

    // 이자카야, 발신자 이자영 같은 이름을 이자 수입으로 오인하지 않게 한다.
    private val INTEREST = Regex("이자(?![가-힣])")

    // 스마트스토어·스마트로 같은 가맹점이 마트로 잡히지 않게 한다.
    private val MART = Regex("(?<!스)마트")

    private fun matches(key: String, word: String) =
        if (word == "마트") MART.containsMatchIn(key) else key.contains(word)

    fun classify(
        parsed: ParsedNotification,
        fullText: String,
        sourcePackage: String,
        watchedPackages: Set<String>,
        learned: LearnedRule?,
    ): Decision {
        if (learned != null) {
            return learned.categoryId?.let { Decision.RegisterById(it) } ?: Decision.Ignore
        }
        if (parsed.direction == Direction.UNKNOWN) return Decision.NeedsReview(ReviewReason.UNKNOWN_DIRECTION)
        val key = MerchantNormalizer.normalize(parsed.merchant)
        val text = MerchantNormalizer.normalize(fullText)

        if (parsed.direction == Direction.OUT) {
            KnownApps.CHARGE_APPS.firstOrNull { text.contains(it.keyword) }?.let { app ->
                if (sourcePackage in app.packages) {
                    if (text.contains("충전")) return Decision.Ignore
                } else {
                    return if (watchedPackages.any { it in app.packages }) Decision.Ignore
                    else Decision.RegisterByPath(CHARGE_PATH)
                }
            }
            MERCHANT_RULES.firstOrNull { (words, _) -> words.any { matches(key, it) } }
                ?.let { return Decision.RegisterByPath(it.second) }
        }
        if (parsed.direction == Direction.IN && (text.contains("캐시백") || INTEREST.containsMatchIn(fullText))) {
            return Decision.RegisterByPath(CASHBACK_INTEREST_PATH)
        }
        if (parsed.direction == Direction.REFUND) return Decision.RegisterByPath(REFUND_PATH)
        return Decision.NeedsReview(ReviewReason.UNCLASSIFIED)
    }
}
