package com.householdbudget.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RestoreResult { RESTORED, PREFS_FAILED }

enum class BackupOutcome { EXPORTED, EXPORT_FAILED, INVALID_FILE, RESTORED, UNDONE, PREFS_FAILED, RESTORE_FAILED }

/** 화면이 사라졌다 돌아와도 결과를 볼 수 있게 저장소가 들고 있는 상태. */
sealed interface BackupStatus {
    data object Idle : BackupStatus

    data object Working : BackupStatus

    /** 파일을 읽고 검사까지 마쳤다. 사용자 확인을 기다린다. */
    data class Confirm(val snapshot: BackupSnapshot) : BackupStatus

    /** [error]는 INVALID_FILE일 때만. null이면 파일을 읽지 못한 것. */
    data class Done(val outcome: BackupOutcome, val error: BackupError? = null) : BackupStatus
}

/** "복원 전으로 되돌리기"가 돌아갈 상태. */
data class SafetyCopyInfo(val savedAtMs: Long, val snapshot: BackupSnapshot)

/**
 * 백업 만들기·복원. [lock]은 자동 기록과 같은 뮤텍스라 복원 중에 알림 처리가 끼어들지 않는다.
 * 작업은 앱 범위 [scope]에서 끝까지 돌고, 결과는 [status]로 남는다.
 */
class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val prefs: UserPreferencesRepository,
    private val lock: Mutex,
    backupDir: File,
    private val appVersion: String,
    private val scope: CoroutineScope,
    /** 복원된 설정에 맞춰 리마인더 예약을 다시 한다. */
    private val onPrefsRestored: suspend (BackupPrefs) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    /** 마지막 복원 직전 상태. 되돌리기를 하면 그 직전 상태로 바뀐다(맞바꾸기). */
    val safetyFile = File(backupDir, "before-restore.json")

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _safetyCopy = MutableStateFlow<SafetyCopyInfo?>(null)
    val safetyCopy: StateFlow<SafetyCopyInfo?> = _safetyCopy.asStateFlow()

    fun hasSafetyCopy(): Boolean = safetyFile.exists()

    /** 오래된 사본은 지우고, 남아 있으면 내용을 읽어 [safetyCopy]에 올린다. */
    suspend fun refreshSafetyCopy() = withContext(Dispatchers.IO) {
        if (safetyFile.exists() && clock().toEpochMilli() - safetyFile.lastModified() > SAFETY_COPY_TTL_MS) {
            safetyFile.delete()
        }
        _safetyCopy.value = if (safetyFile.exists()) {
            BackupCodec.fromJson(safetyFile.readText()).getOrNull()?.let { SafetyCopyInfo(safetyFile.lastModified(), it) }
        } else {
            null
        }
    }

    /** 확인 창 닫기·결과 문구 지우기. 일하는 중이면 그대로 둔다. */
    fun dismissResult() = _status.update { if (it is BackupStatus.Working) it else BackupStatus.Idle }

    fun exportTo(uri: Uri) = launchWork {
        val ok = runCatching {
            val bytes = exportJson().toByteArray(Charsets.UTF_8)
            openForWrite(uri).use { it.write(bytes) }
        }.isSuccess
        if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        BackupStatus.Done(if (ok) BackupOutcome.EXPORTED else BackupOutcome.EXPORT_FAILED)
    }

    fun load(uri: Uri) = launchWork {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { BackupCodec.readCapped(it) } ?: error("no input")
            BackupCodec.fromJson(text).getOrThrow()
        }.fold(
            { BackupStatus.Confirm(it) },
            { BackupStatus.Done(BackupOutcome.INVALID_FILE, (it as? BackupFormatException)?.error) },
        )
    }

    fun confirmRestore(snapshot: BackupSnapshot) = launchWork {
        runCatching { restore(snapshot) }.fold(
            { BackupStatus.Done(if (it == RestoreResult.PREFS_FAILED) BackupOutcome.PREFS_FAILED else BackupOutcome.RESTORED) },
            { BackupStatus.Done(BackupOutcome.RESTORE_FAILED) },
        )
    }

    fun confirmUndo() = launchWork {
        runCatching { undoRestore() }.fold(
            {
                when (it) {
                    null -> BackupStatus.Done(BackupOutcome.RESTORE_FAILED)
                    RestoreResult.PREFS_FAILED -> BackupStatus.Done(BackupOutcome.PREFS_FAILED)
                    RestoreResult.RESTORED -> BackupStatus.Done(BackupOutcome.UNDONE)
                }
            },
            { BackupStatus.Done(BackupOutcome.RESTORE_FAILED) },
        )
    }

    /** 이미 일하는 중이면 무시한다(두 번 누름 방지). */
    private fun launchWork(block: suspend () -> BackupStatus) {
        synchronized(this) {
            if (_status.value is BackupStatus.Working) return
            _status.value = BackupStatus.Working
        }
        scope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) { block() }
            refreshSafetyCopy()
            _status.value = result
        }
    }

    // 새로 만든 문서라 "w"로 열어도 남는 내용이 없다. "wt"를 못 여는 제공자를 위한 대비.
    private fun openForWrite(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        return runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("no output")
    }

    /** 읽기 검사를 통과하는 내용만 내보낸다(못 읽는 백업이나 사본을 만들지 않게). */
    suspend fun exportJson(): String =
        BackupCodec.toJson(snapshot(), clock(), appVersion).also { BackupCodec.fromJson(it).getOrThrow() }

    /** 지금 기록을 [snapshot]으로 바꾼다. 바꾸기 전 상태는 [safetyFile]에 남긴다. */
    suspend fun restore(snapshot: BackupSnapshot): RestoreResult = guarded {
        saveSafetyCopy()
        replace(snapshot)
    }

    /** 사본 상태와 지금 상태를 맞바꾼다. 사본이 없으면 null. */
    suspend fun undoRestore(): RestoreResult? = guarded {
        if (!safetyFile.exists()) return@guarded null
        val snapshot = BackupCodec.fromJson(safetyFile.readText()).getOrThrow()
        saveSafetyCopy()
        replace(snapshot)
    }

    // 취소돼도 DB와 설정이 반쯤만 바뀌지 않게 끝까지 간다.
    private suspend fun <T> guarded(block: suspend () -> T): T =
        withContext(Dispatchers.IO + NonCancellable) { lock.withLock { block() } }

    internal suspend fun saveSafetyCopy() {
        safetyFile.parentFile?.mkdirs()
        val tmp = File(safetyFile.parentFile, safetyFile.name + ".tmp")
        FileOutputStream(tmp).use {
            it.write(exportJson().toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (!tmp.renameTo(safetyFile)) error("safety copy rename failed")
    }

    private suspend fun currentPrefs() = BackupPrefs(
        paydayDom = prefs.paydayDom.first(),
        monthlyBudgetMinor = prefs.monthlyBudgetMinor.first(),
        reminderEnabled = prefs.reminderEnabled.first(),
        reminderHour = prefs.reminderHour.first(),
        darkTheme = prefs.darkTheme.first(),
    )

    private suspend fun snapshot(): BackupSnapshot {
        val p = currentPrefs()
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
            // 자식 테이블부터. 알림 로그·자동 기록은 남긴다: 같은 알림이 다시 와도 중복으로 알아보고, 확인 대기도 잃지 않는다.
            listOf("transactions", "category_budgets", "merchant_rules", "cash_entries", "archived_periods")
                .forEach { db.execSQL("DELETE FROM `$it`") }
            db.execSQL("DELETE FROM categories WHERE parent_id IS NOT NULL")
            db.execSQL("DELETE FROM categories")
            // id를 그대로 넣으면 AUTOINCREMENT가 그 위에서 이어진다. 부모 먼저.
            s.categories.sortedBy { it.parentId != null }.forEach { database.categoryDao().insert(it) }
            s.transactions.forEach { database.transactionDao().insert(it) }
            s.categoryBudgets.forEach { database.categoryBudgetDao().upsert(it) }
            s.merchantRules.forEach { database.merchantRuleDao().upsert(it) }
            s.archivedPeriods.forEach { database.archivedPeriodDao().insert(it) }
            s.cashEntries.forEach { database.cashEntryDao().insert(it) }
            // 자동 기록 → 거래 연결은 같은 자동 거래(같은 id·금액)가 복원됐을 때만 남긴다.
            db.execSQL(
                "UPDATE auto_entries SET transaction_id = NULL WHERE transaction_id IS NOT NULL AND NOT EXISTS (" +
                    "SELECT 1 FROM transactions t WHERE t.id = auto_entries.transaction_id " +
                    "AND t.source = 'AUTO' AND t.amount_minor = auto_entries.amount_minor)",
            )
        }
        val result = runCatching {
            with(s.prefs) {
                prefs.restoreBackupPrefs(
                    paydayDom, monthlyBudgetMinor, reminderEnabled, reminderHour, darkTheme,
                    // 마지막 지난 기록 뒤부터 다시 보관하게 한다. 그 사이에 끝난 기간이 빠지지 않는다.
                    lastSeenPeriodStart = s.archivedPeriods.maxOfOrNull { it.endEpochDay },
                )
            }
        }.fold({ RestoreResult.RESTORED }, { RestoreResult.PREFS_FAILED })
        runCatching { onPrefsRestored(currentPrefs()) }
        return result
    }

    companion object {
        const val SAFETY_COPY_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
