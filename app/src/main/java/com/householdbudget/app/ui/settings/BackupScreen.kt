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
import com.householdbudget.app.data.backup.BackupError
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import java.time.LocalDate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.data.backup.BackupOutcome
import com.householdbudget.app.data.backup.BackupStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val safetyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm").withLocale(Locale.KOREA)

/**
 * 얇은 화면: 작업은 [com.householdbudget.app.data.backup.BackupRepository]가 앱 범위에서 끝까지 하고,
 * 결과는 status로 남아 화면이 다시 열려도 보인다.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backup = remember(context) { (context.applicationContext as BudgetApplication).container.backupRepository }
    val status by backup.status.collectAsStateWithLifecycle()
    val safetyCopy by backup.safetyCopy.collectAsStateWithLifecycle()
    val working = status is BackupStatus.Working
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by remember { mutableStateOf(false) }

    LaunchedEffect(backup) { backup.refreshSafetyCopy() }

    val createLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) backup.exportTo(uri)
        }
    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) backup.load(uri)
        }

    // 문서 화면이 없는 기기에서 죽지 않게 한다.
    fun pick(launch: () -> Unit) {
        backup.dismissResult()
        pickerFailed = runCatching(launch).isFailure
    }

    val message: Pair<String, Boolean>? = when (val st = status) {
        BackupStatus.Working -> stringResource(R.string.backup_working) to false
        is BackupStatus.Done -> when (st.outcome) {
            BackupOutcome.EXPORTED -> stringResource(R.string.backup_export_done) to false
            BackupOutcome.EXPORT_FAILED -> stringResource(R.string.backup_export_error) to true
            BackupOutcome.INVALID_FILE -> stringResource(
                R.string.backup_invalid,
                stringResource(
                    when (st.error) {
                        BackupError.NOT_BACKUP -> R.string.backup_reason_not_backup
                        BackupError.NEWER_VERSION -> R.string.backup_reason_newer
                        BackupError.DAMAGED -> R.string.backup_reason_damaged
                        BackupError.TOO_LARGE -> R.string.backup_reason_too_large
                        null -> R.string.backup_reason_unreadable
                    },
                ),
            ) to true
            BackupOutcome.RESTORED -> stringResource(R.string.backup_restore_done) to false
            BackupOutcome.UNDONE -> stringResource(R.string.backup_undo_done) to false
            BackupOutcome.PREFS_FAILED -> stringResource(R.string.backup_restore_prefs_failed) to true
            BackupOutcome.RESTORE_FAILED -> stringResource(R.string.backup_restore_error) to true
        }
        else -> if (pickerFailed) stringResource(R.string.backup_picker_error) to true else null
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
                onClick = { pick { createLauncher.launch(context.getString(R.string.backup_file_name, LocalDate.now().toString())) } },
                enabled = !working,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Space.sm),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(
                // 제공자마다 .json 형식 표시가 달라 다 보여 준다. 내용 검사가 막는다.
                onClick = { pick { openLauncher.launch(arrayOf("*/*")) } },
                enabled = !working,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
            if (safetyCopy != null) {
                TextButton(
                    onClick = { confirmUndo = true },
                    enabled = !working,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.backup_undo)) }
                Text(
                    text = stringResource(R.string.backup_undo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { (text, isError) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    (status as? BackupStatus.Confirm)?.snapshot?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.backup_confirm_title),
            body = stringResource(
                R.string.backup_confirm_body,
                s.transactions.size, s.categories.size, s.cashEntries.size, s.archivedPeriods.size,
            ),
            confirm = stringResource(R.string.backup_confirm),
            onConfirm = { backup.confirmRestore(s) },
            onDismiss = backup::dismissResult,
        )
    }

    val copy = safetyCopy
    if (confirmUndo && copy != null) {
        val s = copy.snapshot
        ConfirmDialog(
            title = stringResource(R.string.backup_undo_confirm_title),
            body = stringResource(
                R.string.backup_undo_confirm_body,
                Instant.ofEpochMilli(copy.savedAtMs).atZone(ZoneId.systemDefault()).format(safetyTimeFormatter),
                s.transactions.size, s.categories.size, s.cashEntries.size, s.archivedPeriods.size,
            ),
            confirm = stringResource(R.string.backup_undo_confirm),
            onConfirm = {
                confirmUndo = false
                backup.confirmUndo()
            },
            onDismiss = { confirmUndo = false },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}
