package com.householdbudget.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.backup.BackupCodec
import com.householdbudget.app.data.backup.BackupError
import com.householdbudget.app.data.backup.BackupFormatException
import com.householdbudget.app.data.backup.BackupSnapshot
import com.householdbudget.app.data.backup.RestoreResult
import com.householdbudget.app.data.work.ReminderScheduler
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 화면 아래 한 줄 안내. */
private data class BackupMessage(val text: String, val isError: Boolean)

/** 확인 대기: 파일에서 복원(snapshot) 또는 복원 전으로 되돌리기(null). */
private data class PendingRestore(val snapshot: BackupSnapshot?)

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as BudgetApplication).container }
    val backup = container.backupRepository
    val prefs = container.userPreferencesRepository
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<BackupMessage?>(null) }
    var hasSafetyCopy by remember { mutableStateOf(backup.hasSafetyCopy()) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }

    fun ok(res: Int) = BackupMessage(context.getString(res), isError = false)
    fun err(res: Int) = BackupMessage(context.getString(res), isError = true)

    val createLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            working = true
            scope.launch {
                message = runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = backup.exportJson().toByteArray(Charsets.UTF_8)
                        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("no output stream")
                        out.use { it.write(bytes) }
                    }
                }.fold({ ok(R.string.backup_export_done) }, { err(R.string.backup_export_error) })
                working = false
            }
        }

    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            working = true
            message = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.use { BackupCodec.readCapped(it) }
                            ?: error("no input stream")
                        BackupCodec.fromJson(text).getOrThrow()
                    }
                }.onSuccess { pending = PendingRestore(it) }
                    .onFailure { e ->
                        val reason = when ((e as? BackupFormatException)?.error) {
                            BackupError.NOT_BACKUP -> R.string.backup_reason_not_backup
                            BackupError.NEWER_VERSION -> R.string.backup_reason_newer
                            BackupError.DAMAGED -> R.string.backup_reason_damaged
                            BackupError.TOO_LARGE -> R.string.backup_reason_too_large
                            null -> R.string.backup_reason_unreadable
                        }
                        message = BackupMessage(context.getString(R.string.backup_invalid, context.getString(reason)), isError = true)
                    }
                working = false
            }
        }

    fun runRestore(target: PendingRestore) {
        working = true
        message = null
        scope.launch {
            val snapshot = target.snapshot
            message = runCatching { if (snapshot != null) backup.restore(snapshot) else backup.undoRestore() }
                .fold(
                    { result ->
                        // 리마인더 예약은 설정값만으로는 안 바뀌므로 복원된 값에 맞춘다.
                        runCatching {
                            if (prefs.reminderEnabled.first()) {
                                ReminderScheduler.schedule(context, prefs.reminderHour.first())
                            } else {
                                ReminderScheduler.cancel(context)
                            }
                        }
                        when {
                            result == RestoreResult.PREFS_FAILED -> err(R.string.backup_restore_prefs_failed)
                            snapshot == null -> ok(R.string.backup_undo_done)
                            else -> ok(R.string.backup_restore_done)
                        }
                    },
                    { err(R.string.backup_restore_error) },
                )
            hasSafetyCopy = backup.hasSafetyCopy()
            working = false
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = stringResource(R.string.backup_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.backup_where),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { createLauncher.launch(context.getString(R.string.backup_file_name, LocalDate.now().toString())) },
                enabled = !working,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Space.sm),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !working,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
            if (hasSafetyCopy) {
                TextButton(
                    onClick = { pending = PendingRestore(null) },
                    enabled = !working,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.backup_undo)) }
            }
            val shown = if (working) BackupMessage(stringResource(R.string.backup_working), isError = false) else message
            shown?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    pending?.let { target ->
        val s = target.snapshot
        AlertDialog(
            onDismissRequest = { pending = null },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(if (s != null) R.string.backup_confirm_title else R.string.backup_undo_confirm_title)) },
            text = {
                Text(
                    if (s != null) {
                        stringResource(
                            R.string.backup_confirm_body,
                            s.transactions.size, s.categories.size, s.cashEntries.size, s.archivedPeriods.size,
                        )
                    } else {
                        stringResource(R.string.backup_undo_confirm_body)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        runRestore(target)
                    },
                    enabled = !working,
                ) {
                    Text(
                        stringResource(if (s != null) R.string.backup_confirm else R.string.backup_undo_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }
}
