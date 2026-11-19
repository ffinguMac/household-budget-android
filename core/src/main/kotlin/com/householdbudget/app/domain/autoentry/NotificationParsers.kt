package com.householdbudget.app.domain.autoentry

enum class Direction { OUT, IN, REFUND, UNKNOWN }

data class ParsedNotification(val amountMinor: Long, val direction: Direction, val merchant: String)

object NotificationParsers {
    const val KBANK_PACKAGE = "com.kbankwith.smartbank"

    private val AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")
    private val KBANK_DEPOSIT_TITLE = Regex("""^입금\s+([\d,]+)원$""")
    private val AMOUNT_GUARDS = listOf("잔액", "누적", "한도", "잔여")
    private val REFUND_WORDS = listOf("취소", "환불")
    // 캐시백은 카드 승인 문구에도 붙어서 방향 키워드로 쓰지 않는다. 실제 캐시백 입금은 "입금"으로 잡힌다.
    private val IN_WORDS = listOf("입금", "받았", "받음")

    // 이자는 이자카야 때문에 방향 키워드로 쓰지 않는다.
    private val OUT_WORDS = listOf("승인", "결제", "출금", "이체", "송금", "보냈", "보냄", "충전", "납부")
    private val NAME_NOISE = listOf(
        AMOUNT,
        Regex("""\d{4}[./-]\d{1,2}[./-]\d{1,2}"""),
        Regex("""\d{1,2}월\s*\d{1,2}일"""),
        Regex("""\d{1,2}[/.]\d{1,2}(?!\d)"""),
        Regex("""\d{1,2}:\d{2}(?::\d{2})?"""),
        Regex("""[^\s|]*\*+[^\s|]*"""),
        Regex("""\(\d{3,4}\)"""),
        Regex("""[^\s|]*(?:통장|계좌)"""),
    )
    private val NAME_KEYWORDS = listOf(
        "입출금", "체크카드", "신용카드", "일시불", "승인", "결제", "출금", "이체", "송금",
        "입금", "취소", "환불", "충전", "납부", "잔액", "누적", "완료",
    )
    private val PIECE_SEPARATOR = Regex("""[|\n]""")
    private val SPACES = Regex("""\s+""")
    private const val PIECE_TRIM = "[]()·,.:-"

    const val TITLE_CAP = 200
    const val BODY_CAP = 2000

    /** body = bigText가 비어 있지 않으면 bigText, 아니면 text. 금액을 못 찾으면 null. */
    fun parse(packageName: String, rawTitle: String, rawBody: String): ParsedNotification? {
        val title = rawTitle.take(TITLE_CAP)
        val body = rawBody.take(BODY_CAP)
        if (packageName == KBANK_PACKAGE) parseKbankDeposit(title, body)?.let { return it }
        return parseGeneric(title, body)
    }

    private fun parseKbankDeposit(title: String, body: String): ParsedNotification? {
        val digits = KBANK_DEPOSIT_TITLE.find(title)?.groupValues?.get(1) ?: return null
        if ('|' !in body) return null
        val amount = digits.replace(",", "").toLongOrNull() ?: return null
        return ParsedNotification(amount, Direction.IN, body.split('|').first().trim())
    }

    private fun parseGeneric(title: String, body: String): ParsedNotification? {
        val full = title + "\n" + body
        if ("(광고)" in full || "수신거부" in full) return null
        val amount = findAmount(full) ?: return null
        val titleIsJustAppName = !AMOUNT.containsMatchIn(title) && directionOf(title) == Direction.UNKNOWN
        val merchant = extractName(if (titleIsJustAppName) body else full)
        return ParsedNotification(amount, directionOf(full), merchant)
    }

    private fun findAmount(text: String): Long? = AMOUNT.findAll(text).firstNotNullOfOrNull { match ->
        val before = text.substring(maxOf(0, match.range.first - 8), match.range.first).substringAfterLast('\n')
        val value = match.groupValues[1].replace(",", "").toLongOrNull()
        value?.takeIf { it != 0L && AMOUNT_GUARDS.none { guard -> guard in before } }
    }

    private fun directionOf(text: String): Direction {
        val t = text.replace("입출금", "") // 통장 이름 입출금통장이 출금으로 읽히지 않게
        return when {
            REFUND_WORDS.any { it in t } -> Direction.REFUND
            IN_WORDS.any { it in t } -> Direction.IN
            OUT_WORDS.any { it in t } -> Direction.OUT
            else -> Direction.UNKNOWN
        }
    }

    private fun extractName(text: String): String {
        var t = NAME_NOISE.fold(text) { acc, regex -> regex.replace(acc, "") }
        t = NAME_KEYWORDS.fold(t) { acc, word -> acc.replace(word, "") }
        return t.split(PIECE_SEPARATOR)
            .map { piece -> piece.replace(SPACES, " ").trim { it.isWhitespace() || it in PIECE_TRIM } }
            .fold("") { longest, piece -> if (piece.length > longest.length) piece else longest }
    }
}
