package com.householdbudget.app.domain.autoentry

import java.util.Locale

object MerchantNormalizer {
    private val COMPANY_MARKS = Regex("""\(주\)|㈜|주식회사""")
    private val BRACKETS = Regex("""[()\[\]{}]""")
    private val SPACES = Regex("""\s+""")

    fun normalize(name: String): String = name
        .replace(COMPANY_MARKS, "")
        .replace(BRACKETS, "")
        .replace(SPACES, "")
        .lowercase(Locale.ROOT)
}
