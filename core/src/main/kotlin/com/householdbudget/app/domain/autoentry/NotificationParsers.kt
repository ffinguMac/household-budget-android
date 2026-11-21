package com.householdbudget.app.domain.autoentry

enum class Direction { OUT, IN, REFUND, UNKNOWN }

data class ParsedNotification(val amountMinor: Long, val direction: Direction, val merchant: String)

object NotificationParsers {
    const val KBANK_PACKAGE = "com.kbankwith.smartbank"

    private val AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*(?:원|WON)""")
    // 금액 후보. 거래 키워드가 바로 앞에 붙어 있으면 "원" 없이도 금액으로 본다(은행 문자 "출금 10,450", "입금30,000").
    private val AMOUNT_CANDIDATE =
        Regex("""(?:(입금|출금|지급|PAY|RCV|W/D:|Dep\.:)\s*)?([+-]?)(\d{1,3}(?:,\d{3})+|\d+)(\s*(?:원|WON))?""")
    private val IN_AMOUNT_KEYWORDS = setOf("입금", "RCV", "Dep.:")
    private val KBANK_DEPOSIT_TITLE = Regex("""^입금\s+([\d,]+)원$""")
    private val AMOUNT_GUARDS = listOf("잔액", "누적", "한도", "잔여", "BAL", "Bal")
    private val REFUND_WORDS = listOf("취소", "환불")
    // 캐시백은 카드 승인 문구에도 붙어서 방향 키워드로 쓰지 않는다. 실제 캐시백 입금은 "입금"으로 잡힌다.
    // "가짜세입금" 같은 가맹점 이름 속 입금은 무시하고, 은행 거래 구분어(전자금융입금 등)만 입금으로 본다.
    private val IN_PATTERN = Regex("""(?<![가-힣])(?:전자금융|무통장|창구|타행|[A-Z]+)?입금|받았|받음|님이 [^\n]*보냈""")

    // 이자는 이자카야 때문에 방향 키워드로 쓰지 않는다. 일시불·할부는 승인 글자가 없는 카드 문구(롯데)용.
    private val OUT_WORDS =
        listOf("승인", "결제", "출금", "이체", "송금", "보냈", "보냄", "충전", "납부", "지급", "사용", "일시불", "할부")

    // 가맹점이 아닌 조각. 지우지 않고 조각 경계로 바꿔서 한 줄짜리 문자도 토큰별로 나뉘게 한다.
    private val NAME_NOISE = listOf(
        Regex("""\[[^\]\n|]{0,15}]|<[^>\n|]{0,15}>"""), // [Web발신] [KB] <새마을금고> [110-***-123456]
        AMOUNT,
        Regex("""[\d*\-]*(?:\d\*|\*\d|\*\*)[\d*\-]*"""), // 가린 계좌·카드 번호
        Regex("""[가-힣]+\*+[가-힣]*"""), // 가린 이름 홍*동
        Regex("""\d+(?:-\d+){2,}"""), // 계좌 번호
        Regex("""\d{4}[./-]\d{1,2}[./-]\d{1,2}"""),
        Regex("""\d{1,2}월\s*\d{1,2}일"""),
        Regex("""\d+(?:\.\d+)?%"""),
        Regex("""\d{1,2}[/.]\d{1,2}(?!\d)"""),
        Regex("""\d{1,2}:\d{2}(?::\d{2})?"""),
        Regex("""\d{1,3}(?:,\d{3})+"""), // 원 없는 금액·잔액
        Regex("""\d+개월"""),
        Regex("""\(\d{3,4}\)"""),
        Regex("""[^\s|]*(?:통장|계좌)"""),
        Regex("""[^\s|]*승인(?:취소)?"""), // 신한체크승인, KB국민카드1234승인
        Regex("""[^\s|]*님(?=[\s,|]|$)"""),
        // 거래 키워드는 낱말 머리에 올 때만 지운다(가맹점 "가짜송금"은 남긴다).
        Regex(
            """(?<![\p{L}\d])(?:전자금융|CMS|무통장|창구|FBS|체크카드|스마트폰|공동CMS|지로|자동|타행)?""" +
                """(?:입출금|체크카드|신용카드|일시불|결제|출금|입금|이체|송금|지급|사용|취소|환불|충전|납부|잔액|누적|완료|""" +
                """PAY|RCV|BAL|W/D|Dep\.|Bal\.)+""",
        ),
    )
    // "가짜택시에서 … 사용했어요", "김가짜님에게 … 송금했어요" 같은 문장은 문장 속 이름을 그대로 쓴다.
    private val SENTENCE_NAMES = listOf(
        Regex("""(?m)^[^\S\n]*([^\n|]+?)에서 [^\n|]*했어요"""),
        Regex("""([^\s|]+?)님(?:에게|이|께) """),
    )
    // 카드 상품명·은행 이름 조각은 다른 조각이 없을 때만 가맹점으로 쓴다.
    private val INSTITUTION = Regex("""카드|체크|은행|뱅크|금고|우체국|WOORI|HANABANK""")
    private val PIECE_SEPARATOR = Regex("""[|\n⎸║]| / """)
    private val SPACES = Regex("""[\s　]+""")
    private const val PIECE_TRIM = "[]<>·,.:-"

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
        // 해외 승인(달러 등, 원 없음)은 금액 후보가 없어 여기서 null → 기록만 남는다.
        val (amount, amountDirection) = findAmount(full) ?: return null
        val titleIsJustAppName = !AMOUNT.containsMatchIn(title) && directionOf(title) == Direction.UNKNOWN
        val merchant = extractName(if (titleIsJustAppName) body else full)
        val keywordDirection = directionOf(full)
        val direction =
            if (keywordDirection == Direction.REFUND || amountDirection == Direction.UNKNOWN) keywordDirection
            else amountDirection
        return ParsedNotification(amount, direction, merchant)
    }

    /** 금액과, 금액에 붙은 키워드·부호가 알려 주는 방향(없으면 UNKNOWN). */
    private fun findAmount(text: String): Pair<Long, Direction>? =
        AMOUNT_CANDIDATE.findAll(text).firstNotNullOfOrNull { match ->
            val (keyword, sign, digits, unit) = match.destructured
            val next = text.getOrNull(match.range.last + 1)
            if (unit.isEmpty() && (keyword.isEmpty() || (next != null && !next.isWhitespace()))) return@firstNotNullOfOrNull null
            val before = text.substring(maxOf(0, match.range.first - 8), match.range.first).substringAfterLast('\n')
            if (AMOUNT_GUARDS.any { it in before }) return@firstNotNullOfOrNull null
            val value = digits.replace(",", "").toLongOrNull()?.takeIf { it != 0L } ?: return@firstNotNullOfOrNull null
            value to when {
                keyword in IN_AMOUNT_KEYWORDS -> Direction.IN
                keyword.isNotEmpty() -> Direction.OUT
                sign == "-" -> Direction.OUT
                sign == "+" -> Direction.IN
                else -> Direction.UNKNOWN
            }
        }

    private fun directionOf(text: String): Direction {
        val t = text.replace("입출금", "") // 통장 이름 입출금통장이 출금으로 읽히지 않게
        return when {
            REFUND_WORDS.any { it in t } -> Direction.REFUND
            IN_PATTERN.containsMatchIn(t) -> Direction.IN
            OUT_WORDS.any { it in t } -> Direction.OUT
            else -> Direction.UNKNOWN
        }
    }

    private fun extractName(text: String): String {
        SENTENCE_NAMES.firstNotNullOfOrNull { it.find(text) }?.let { match ->
            cleanPiece(match.groupValues[1]).takeIf { name -> name.any { it.isLetter() } }?.let { return it }
        }
        val pieces = NAME_NOISE.fold(text) { acc, regex -> regex.replace(acc, "|") }
            .split(PIECE_SEPARATOR)
            .map(::cleanPiece)
            .filter { piece -> piece.any { it.isLetter() } }
        return pieces.filterNot { INSTITUTION.containsMatchIn(it) }.ifEmpty { pieces }
            .fold("") { longest, piece -> if (piece.length > longest.length) piece else longest }
    }

    private fun cleanPiece(raw: String): String {
        val piece = raw.replace(SPACES, " ").trim { it.isWhitespace() || it in PIECE_TRIM }
        val opens = piece.count { it == '(' }
        return when {
            opens != piece.count { it == ')' } -> piece.trim { it.isWhitespace() || it in "()" }
            opens == 1 && piece.startsWith('(') && piece.endsWith(')') -> piece.substring(1, piece.length - 1).trim()
            else -> piece
        }
    }
}
