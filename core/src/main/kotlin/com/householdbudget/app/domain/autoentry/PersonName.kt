package com.householdbudget.app.domain.autoentry

/** 은행 입출금·송금 알림의 상대가 사람 이름인지. 사람에게 오간 돈은 매번 달라서 자동 분류도 학습도 하지 않는다. */
object PersonName {
    // 흔한 성씨로 시작하는 2~3글자(가린 글자 * 포함), 또는 복성으로 시작하는 4글자. 끝의 "님"은 뗀다.
    // shortcut: 영문 이름(KIM GAJJA)·드문 성씨·4글자 단성 이름은 사람으로 못 본다, 실제 알림에서 놓치는 게 보이면 넓힌다.
    private const val SURNAMES =
        "김이박최정강조윤장임한오서신권황안송류유전홍고문양손배백허남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모"
    private val NAME = Regex("""^(?:[$SURNAMES][가-힣*]{1,2}|(?:남궁|황보|제갈|선우|독고|서문|사공)[가-힣*]{2})$""")

    private val TRANSFER_WORDS = Regex("""입금|출금|이체|송금|보냈|보냄|받았|받음|RCV|Dep\.|W/D""")
    private val CARD_WORDS = Regex("""승인|결제|일시불|할부|사용|체크카드|신용카드|누적""")

    // 사람 이름 모양이어도 기관·수입 종류·분류 키워드면 사람이 아니다. 이자영 같은 이름은 살린다.
    private val NOT_PERSON = Regex(
        """급여|월급|상여|수당|이자(?![가-힣])|캐시백|환급|환불|관리비|보험|통신|페이|카드|은행|뱅크|증권|금고|주식|\(주\)|㈜|\(유\)|""" +
            """결산|예금|적금|대출|연금|세금|국세|수수료|요금|충전|송금|이체|입금|출금|자동|한국|공사|""" +
            """신한|우리|하나|농협|기업|국민|KB|NH|IBK|토스|카카오|네이버|케이뱅크|우체국""",
    )
    private val KEYWORDS = AutoClassifier.MERCHANT_RULES.flatMap { it.first } + KnownApps.CHARGE_APPS.map { it.keyword }

    fun looksLikePerson(merchant: String, direction: Direction, rawText: String): Boolean {
        if (direction != Direction.IN && direction != Direction.OUT) return false
        if (!TRANSFER_WORDS.containsMatchIn(rawText) || CARD_WORDS.containsMatchIn(rawText)) return false
        val name = merchant.trim().removeSuffix("님")
        if (!NAME.matches(name) || NOT_PERSON.containsMatchIn(name)) return false
        val key = MerchantNormalizer.normalize(name)
        return KEYWORDS.none { key.contains(it) }
    }
}
