package com.householdbudget.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.data.backup.BackupRepository
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.AutoEntryRepository
import com.householdbudget.app.data.repository.BudgetRepository
import java.io.File
import com.householdbudget.app.data.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val userPreferencesRepository = UserPreferencesRepository(appContext)

    private val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // 자동 기록과 백업 복원이 함께 쓴다.
    private val writeLock = Mutex()

    // 인스턴스는 하나만 둔다(Mutex가 인스턴스별이다).
    val autoEntryRepository = AutoEntryRepository(
        database,
        // 사용자가 고르지 않는다: 설치된 정해 둔 금융 앱 전부.
        watchedPackages = { WatchedApps.defaults(appContext) },
        isCandidate = { WatchedApps.isAllowed(it, debuggable) },
        mutex = writeLock,
    )

    val backupRepository = BackupRepository(
        context = appContext,
        database = database,
        prefs = userPreferencesRepository,
        lock = writeLock,
        backupDir = File(appContext.filesDir, "backups"),
        appVersion = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
            .getOrNull().orEmpty(),
        // 화면이 사라져도 백업·복원이 끝까지 가도록 앱 범위에서 돈다.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        onPrefsRestored = { p ->
            if (p.reminderEnabled) ReminderScheduler.schedule(appContext, p.reminderHour) else ReminderScheduler.cancel(appContext)
        },
    )

    val budgetRepository: BudgetRepository =
        BudgetRepository(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            archivedPeriodDao = database.archivedPeriodDao(),
            categoryBudgetDao = database.categoryBudgetDao(),
            userPreferencesRepository = userPreferencesRepository,
            onAutoRecategorized = autoEntryRepository::learnFromCorrection,
        )
}
