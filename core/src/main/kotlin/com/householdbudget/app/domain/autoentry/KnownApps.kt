package com.householdbudget.app.domain.autoentry

data class ChargeApp(val keyword: String, val packages: Set<String>)

object KnownApps {
    val FINANCE_PACKAGES: List<String> = listOf(
        NotificationParsers.KBANK_PACKAGE, "viva.republica.toss", "com.kakaobank.channel", "com.kakaopay.app",
        "com.naverfin.payapp", "kvp.jjy.MispAndroid320", "com.nhnent.payapp", "com.kbstar.kbbank",
        "com.shinhan.sbanking", "com.shcard.smartpay", "com.hyundaicard.appcard", "com.kbcard.cxh.appcard",
        "com.lcacApp", "com.wooribank.smart.npib", "com.wooricard.smartapp", "com.kebhana.hanapush",
        "com.hanaskcard.paycla", "nh.smart.banking",
    )

    val CHARGE_APPS: List<ChargeApp> = listOf(
        ChargeApp("네이버페이", setOf("com.naverfin.payapp", "com.nhn.android.search")),
        ChargeApp("페이북", setOf("kvp.jjy.MispAndroid320")),
        ChargeApp("카카오페이", setOf("com.kakaopay.app", "com.kakao.talk")),
        ChargeApp("토스페이", setOf("viva.republica.toss")),
        ChargeApp("페이코", setOf("com.nhnent.payapp")),
    )
}
