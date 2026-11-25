package com.householdbudget.app.data.backup

import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.CashEntryEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.autoentry.Direction
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupPrefs(
    val paydayDom: Int,
    val monthlyBudgetMinor: Long?,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val darkTheme: Boolean,
)

data class BackupSnapshot(
    val prefs: BackupPrefs,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>,
    val merchantRules: List<MerchantRuleEntity>,
    val archivedPeriods: List<ArchivedPeriodEntity>,
    val cashEntries: List<CashEntryEntity>,
)

/** 사용자에게 보여 줄 짧은 이유. 문구는 UI가 리소스로 고른다. */
enum class BackupError { NOT_BACKUP, NEWER_VERSION, DAMAGED, TOO_LARGE }

class BackupFormatException(val error: BackupError, detail: String) : Exception(detail)

/** 백업 JSON 읽기·쓰기. 읽을 때는 DB를 건드리기 전에 전부 검사한다. */
object BackupCodec {
    const val FORMAT = "household-budget-backup"
    const val FORMAT_VERSION = 1
    const val DB_VERSION = 9
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_AMOUNT = 999_999_999_999L
    private const val MAX_TOTAL = 999_999_999_999_999L
    private val EPOCH_DAYS = LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.of(2200, 12, 31).toEpochDay()
    private val KINDS = CategoryKind.entries.map { it.storage }.toSet()
    private val SOURCES = setOf(TransactionSource.MANUAL, TransactionSource.AUTO)
    private val DIRECTIONS = Direction.entries.map { it.name }.toSet()

    fun toJson(s: BackupSnapshot, exportedAt: Instant, appVersion: String): String =
        JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("dbVersion", DB_VERSION)
            .put("exportedAt", exportedAt.toString())
            .put("app", appVersion)
            .put(
                "prefs",
                JSONObject()
                    .put("paydayDom", s.prefs.paydayDom)
                    .put("monthlyBudgetMinor", s.prefs.monthlyBudgetMinor ?: JSONObject.NULL)
                    .put("reminderEnabled", s.prefs.reminderEnabled)
                    .put("reminderHour", s.prefs.reminderHour)
                    .put("darkTheme", s.prefs.darkTheme),
            )
            .put("categories", s.categories.toArray {
                JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                    .put("parentId", it.parentId ?: JSONObject.NULL).put("sortOrder", it.sortOrder)
                    .put("icon", it.icon ?: JSONObject.NULL)
            })
            .put("transactions", s.transactions.toArray {
                JSONObject().put("id", it.id).put("occurredEpochDay", it.occurredEpochDay)
                    .put("amountMinor", it.amountMinor).put("kind", it.kind).put("categoryId", it.categoryId)
                    .put("memo", it.memo).put("source", it.source)
            })
            .put("categoryBudgets", s.categoryBudgets.toArray {
                JSONObject().put("categoryId", it.categoryId).put("monthlyAmountMinor", it.monthlyAmountMinor)
                    .put("enabled", it.enabled)
            })
            .put("merchantRules", s.merchantRules.toArray {
                JSONObject().put("merchantKey", it.merchantKey).put("direction", it.direction)
                    .put("displayName", it.displayName).put("categoryId", it.categoryId ?: JSONObject.NULL)
                    .put("updatedAtMs", it.updatedAtMs)
            })
            .put("archivedPeriods", s.archivedPeriods.toArray {
                JSONObject().put("id", it.id).put("startEpochDay", it.startEpochDay).put("endEpochDay", it.endEpochDay)
                    .put("totalIncomeMinor", it.totalIncomeMinor).put("totalExpenseMinor", it.totalExpenseMinor)
                    .put("totalSavingsMinor", it.totalSavingsMinor).put("archivedAtEpochMs", it.archivedAtEpochMs)
            })
            .put("cashEntries", s.cashEntries.toArray {
                JSONObject().put("id", it.id).put("occurredEpochDay", it.occurredEpochDay)
                    .put("amountMinor", it.amountMinor).put("memo", it.memo).put("createdAtMs", it.createdAtMs)
            })
            .toString()

    /** 크기 제한을 넘으면 끝까지 읽지 않고 멈춘다. */
    fun readCapped(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw BackupFormatException(BackupError.TOO_LARGE, "over $MAX_BYTES bytes")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun fromJson(text: String): Result<BackupSnapshot> = runCatching {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException(BackupError.NOT_BACKUP, "malformed JSON: ${e.message}")
        }
        if (root.opt("format") != FORMAT) throw BackupFormatException(BackupError.NOT_BACKUP, "format")
        try {
            parse(root)
        } catch (e: JSONException) {
            throw BackupFormatException(BackupError.DAMAGED, e.message ?: "bad field")
        }
    }

    private fun parse(root: JSONObject): BackupSnapshot {
        val formatVersion = root.int("formatVersion")
        val dbVersion = root.int("dbVersion")
        if (formatVersion > FORMAT_VERSION || dbVersion > DB_VERSION) {
            throw BackupFormatException(BackupError.NEWER_VERSION, "formatVersion=$formatVersion dbVersion=$dbVersion")
        }
        check(formatVersion >= 1, "formatVersion")

        val p = root.obj("prefs")
        val prefs = BackupPrefs(
            paydayDom = p.int("paydayDom").also { check(it in 1..31, "paydayDom") },
            monthlyBudgetMinor = p.longOrNull("monthlyBudgetMinor")?.also { check(it in 0..MAX_AMOUNT, "monthlyBudgetMinor") },
            reminderEnabled = p.bool("reminderEnabled"),
            reminderHour = p.int("reminderHour").also { check(it in 0..23, "reminderHour") },
            darkTheme = p.bool("darkTheme"),
        )

        val categories = root.list("categories") {
            CategoryEntity(
                id = it.long("id"),
                name = it.str("name"),
                kind = it.str("kind"),
                parentId = it.longOrNull("parentId"),
                sortOrder = it.int("sortOrder"),
                icon = it.strOrNull("icon"),
            )
        }
        check(categories.isNotEmpty(), "no categories")
        val byId = categories.associateBy { it.id }
        check(byId.size == categories.size, "duplicate category id")
        categories.forEach { c ->
            check(c.id > 0 && c.name.isNotBlank() && c.kind in KINDS, "category ${c.id}")
            // 두 단계 트리만 있다: 부모는 대분류여야 한다(순환도 막는다).
            c.parentId?.let { check(byId[it]?.parentId == null && it in byId, "category parent ${c.id}") }
        }

        val transactions = root.list("transactions") {
            TransactionEntity(
                id = it.long("id"),
                occurredEpochDay = it.long("occurredEpochDay"),
                amountMinor = it.long("amountMinor"),
                kind = it.str("kind"),
                categoryId = it.long("categoryId"),
                memo = it.str("memo"),
                source = it.str("source"),
            )
        }
        checkUnique(transactions.map { it.id }, "transaction")
        transactions.forEach { t ->
            check(
                t.id > 0 && t.occurredEpochDay in EPOCH_DAYS && t.amountMinor in 0..MAX_AMOUNT &&
                    t.kind in KINDS && t.categoryId in byId && t.source in SOURCES,
                "transaction ${t.id}",
            )
        }

        val budgets = root.list("categoryBudgets") {
            CategoryBudgetEntity(
                categoryId = it.long("categoryId"),
                monthlyAmountMinor = it.long("monthlyAmountMinor"),
                enabled = it.bool("enabled"),
            )
        }
        checkUnique(budgets.map { it.categoryId }, "budget")
        budgets.forEach { check(it.categoryId in byId && it.monthlyAmountMinor in 0..MAX_AMOUNT, "budget ${it.categoryId}") }

        val rules = root.list("merchantRules") {
            MerchantRuleEntity(
                merchantKey = it.str("merchantKey"),
                direction = it.str("direction"),
                displayName = it.str("displayName"),
                categoryId = it.longOrNull("categoryId"),
                updatedAtMs = it.long("updatedAtMs"),
            )
        }
        checkUnique(rules.map { it.merchantKey to it.direction }, "rule")
        rules.forEach { check(it.direction in DIRECTIONS, "rule direction") }

        val archived = root.list("archivedPeriods") {
            ArchivedPeriodEntity(
                id = it.long("id"),
                startEpochDay = it.long("startEpochDay"),
                endEpochDay = it.long("endEpochDay"),
                totalIncomeMinor = it.long("totalIncomeMinor"),
                totalExpenseMinor = it.long("totalExpenseMinor"),
                totalSavingsMinor = it.long("totalSavingsMinor"),
                archivedAtEpochMs = it.long("archivedAtEpochMs"),
            )
        }
        checkUnique(archived.map { it.id }, "archived")
        checkUnique(archived.map { it.startEpochDay to it.endEpochDay }, "archived range")
        archived.forEach { a ->
            check(
                a.id > 0 && a.startEpochDay in EPOCH_DAYS && a.endEpochDay in EPOCH_DAYS && a.startEpochDay < a.endEpochDay &&
                    listOf(a.totalIncomeMinor, a.totalExpenseMinor, a.totalSavingsMinor).all { it in 0..MAX_TOTAL },
                "archived ${a.id}",
            )
        }

        val cash = root.list("cashEntries") {
            CashEntryEntity(
                id = it.long("id"),
                occurredEpochDay = it.long("occurredEpochDay"),
                amountMinor = it.long("amountMinor"),
                memo = it.str("memo"),
                createdAtMs = it.long("createdAtMs"),
            )
        }
        checkUnique(cash.map { it.id }, "cash")
        cash.forEach { c ->
            check(c.id > 0 && c.occurredEpochDay in EPOCH_DAYS && c.amountMinor in -MAX_AMOUNT..MAX_AMOUNT, "cash ${c.id}")
        }

        return BackupSnapshot(
            prefs = prefs,
            categories = categories,
            transactions = transactions,
            categoryBudgets = budgets,
            // 규칙은 외래 키가 없다: 없는 카테고리를 가리키면 버린다.
            merchantRules = rules.filter { it.categoryId == null || it.categoryId in byId },
            archivedPeriods = archived,
            cashEntries = cash,
        )
    }

    private fun check(ok: Boolean, what: String) {
        if (!ok) throw BackupFormatException(BackupError.DAMAGED, "invalid $what")
    }

    private fun checkUnique(keys: List<Any>, what: String) = check(keys.toSet().size == keys.size, "duplicate $what")

    private fun <T> List<T>.toArray(row: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(row(it)) } }

    private fun <T> JSONObject.list(key: String, row: (JSONObject) -> T): List<T> {
        val a = get(key) as? JSONArray ?: throw JSONException("$key is not an array")
        return (0 until a.length()).map { row(a.get(it) as? JSONObject ?: throw JSONException("$key[$it]")) }
    }

    private fun JSONObject.obj(key: String) = get(key) as? JSONObject ?: throw JSONException("$key is not an object")

    // org.json은 "12"나 1.5도 숫자로 바꿔 주므로 타입을 직접 본다.
    private fun JSONObject.long(key: String): Long = when (val v = get(key)) {
        is Int -> v.toLong()
        is Long -> v
        else -> throw JSONException("$key is not an integer")
    }

    private fun JSONObject.int(key: String): Int = long(key).let {
        if (it !in Int.MIN_VALUE..Int.MAX_VALUE) throw JSONException("$key out of range")
        it.toInt()
    }

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) && has(key)) null else long(key)

    private fun JSONObject.str(key: String): String = get(key) as? String ?: throw JSONException("$key is not a string")

    private fun JSONObject.strOrNull(key: String): String? = if (isNull(key) && has(key)) null else str(key)

    private fun JSONObject.bool(key: String): Boolean = get(key) as? Boolean ?: throw JSONException("$key is not a boolean")
}
