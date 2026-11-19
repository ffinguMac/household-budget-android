package com.householdbudget.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.AutoEntryRepository
import com.householdbudget.app.data.repository.BudgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val userPreferencesRepository = UserPreferencesRepository(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // DataStore를 알림마다 다시 읽지 않는다. 읽기 실패는 "저장한 적 없음"으로 본다.
    private val storedWatched = userPreferencesRepository.watchedPackagesOrNull
        .catch { emit(null) }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    // 인스턴스는 하나만 둔다(Mutex가 인스턴스별이다).
    val autoEntryRepository = AutoEntryRepository(
        database,
        watchedPackages = { WatchedApps.effective(storedWatched.first(), appContext) },
        isCandidate = { WatchedApps.isCandidate(it, storedWatched.first(), debuggable) },
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
