package com.householdbudget.app.domain.category

/** 사용자가 고정 대분류 아래에 직접 추가하는 세부 카테고리의 규칙. */
object CustomCategories {
    const val MAX_NAME_LENGTH = 10
    const val HIDE_AFTER_DAYS = 90L

    enum class NameError { EMPTY, TOO_LONG, DUPLICATE }

    /** takenNames = 형제 이름(자기 자신 제외) + 고정 세부 이름. */
    fun validateName(raw: String, takenNames: Collection<String>): NameError? {
        val name = raw.trim()
        if (name.isEmpty()) return NameError.EMPTY
        if (name.codePointCount(0, name.length) > MAX_NAME_LENGTH) return NameError.TOO_LONG
        val key = compareKey(name)
        if (takenNames.any { compareKey(it) == key }) return NameError.DUPLICATE
        return null
    }

    private fun compareKey(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

    fun deleteTargetName(fixedChildren: List<String>): String? =
        fixedChildren.firstOrNull { it == "기타" } ?: fixedChildren.firstOrNull { it == "기본" } ?: fixedChildren.firstOrNull()

    fun isHidden(lastUsedEpochDay: Long?, todayEpochDay: Long): Boolean =
        lastUsedEpochDay != null && todayEpochDay - lastUsedEpochDay > HIDE_AFTER_DAYS
}
