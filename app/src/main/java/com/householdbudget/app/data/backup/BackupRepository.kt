package com.householdbudget.app.data.backup

import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RestoreResult { RESTORED, PREFS_FAILED }

/**
 * 백업 만들기·복원. [lock]은 자동 기록과 같은 뮤텍스라 복원 중에 알림 처리가 끼어들지 않는다.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val prefs: UserPreferencesRepository,
    private val lock: Mutex,
    backupDir: File,
    private val appVersion: String,
    private val clock: () -> Instant = Instant::now,
) {
    /** 마지막 복원 직전 상태. "복원 전으로 되돌리기"가 쓴다. */
    val safetyFile = File(backupDir, "before-restore.json")

    fun hasSafetyCopy(): Boolean = safetyFile.exists()

    suspend fun exportJson(): String = BackupCodec.toJson(snapshot(), clock(), appVersion)

    /** 지금 기록을 [snapshot]으로 바꾼다. 바꾸기 전 상태는 [safetyFile]에 남긴다. */
    suspend fun restore(snapshot: BackupSnapshot): RestoreResult = guarded {
        saveSafetyCopy()
        replace(snapshot)
    }

    /** 복원 직전 상태로 되돌리고 그 사본을 지운다. 사본이 없으면 null. */
    suspend fun undoRestore(): RestoreResult? = guarded {
        if (!safetyFile.exists()) return@guarded null
        val snapshot = BackupCodec.fromJson(safetyFile.readText()).getOrThrow()
        replace(snapshot).also { safetyFile.delete() }
    }

    // 취소돼도 DB와 설정이 반쯤만 바뀌지 않게 끝까지 간다.
    private suspend fun <T> guarded(block: suspend () -> T): T =
        withContext(Dispatchers.IO + NonCancellable) { lock.withLock { block() } }

    internal suspend fun saveSafetyCopy() {
        safetyFile.parentFile?.mkdirs()
        val tmp = File(safetyFile.parentFile, safetyFile.name + ".tmp")
        tmp.writeText(exportJson())
        if (!tmp.renameTo(safetyFile)) error("safety copy rename failed")
    }

    private suspend fun snapshot(): BackupSnapshot {
        val p = BackupPrefs(
            paydayDom = prefs.paydayDom.first(),
            monthlyBudgetMinor = prefs.monthlyBudgetMinor.first(),
            reminderEnabled = prefs.reminderEnabled.first(),
            reminderHour = prefs.reminderHour.first(),
            darkTheme = prefs.darkTheme.first(),
        )
        return database.withTransaction {
            BackupSnapshot(
                prefs = p,
                categories = database.categoryDao().all(),
                transactions = database.transactionDao().all(),
                categoryBudgets = database.categoryBudgetDao().all(),
                merchantRules = database.merchantRuleDao().all(),
                archivedPeriods = database.archivedPeriodDao().all(),
                cashEntries = database.cashEntryDao().all(),
            )
        }
    }

    private suspend fun replace(s: BackupSnapshot): RestoreResult {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            // 자식 테이블부터. 알림 원문·자동 기록은 백업에 없고 옛 거래를 가리키므로 같이 비운다.
            listOf(
                "transactions", "category_budgets", "merchant_rules", "auto_entries", "notification_log",
                "cash_entries", "archived_periods",
            ).forEach { db.execSQL("DELETE FROM `$it`") }
            db.execSQL("DELETE FROM categories WHERE parent_id IS NOT NULL")
            db.execSQL("DELETE FROM categories")
            // id를 그대로 넣으면 AUTOINCREMENT가 그 위에서 이어진다. 부모 먼저.
            s.categories.sortedBy { it.parentId != null }.forEach { database.categoryDao().insert(it) }
            s.transactions.forEach { database.transactionDao().insert(it) }
            s.categoryBudgets.forEach { database.categoryBudgetDao().upsert(it) }
            s.merchantRules.forEach { database.merchantRuleDao().upsert(it) }
            s.archivedPeriods.forEach { database.archivedPeriodDao().insert(it) }
            s.cashEntries.forEach { database.cashEntryDao().insert(it) }
        }
        return runCatching {
            prefs.setPaydayDom(s.prefs.paydayDom)
            prefs.clearLastSeenPeriodStart()
            prefs.setMonthlyBudgetMinor(s.prefs.monthlyBudgetMinor)
            prefs.setReminderEnabled(s.prefs.reminderEnabled)
            prefs.setReminderHour(s.prefs.reminderHour)
            prefs.setDarkTheme(s.prefs.darkTheme)
        }.fold({ RestoreResult.RESTORED }, { RestoreResult.PREFS_FAILED })
    }
}
