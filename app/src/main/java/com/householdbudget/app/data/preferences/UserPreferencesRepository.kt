package com.householdbudget.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val paydayDom: Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[PAYDAY_DOM] ?: DEFAULT_PAYDAY_DOM
        }

    suspend fun setPaydayDom(day: Int) {
        require(day in 1..31)
        dataStore.edit { it[PAYDAY_DOM] = day }
    }

    /** 월급일 변경 시 회계월 아카이브 커서를 초기화한다. */
    suspend fun clearLastSeenPeriodStart() {
        dataStore.edit { it.remove(LAST_SEEN_PERIOD_START) }
    }

    suspend fun getPaydayDomSnapshot(): Int =
        dataStore.data.map { it[PAYDAY_DOM] ?: DEFAULT_PAYDAY_DOM }.first()

    suspend fun getLastSeenPeriodStartSnapshot(): Long? =
        dataStore.data.map { it[LAST_SEEN_PERIOD_START] }.first()

    suspend fun setLastSeenPeriodStart(epochDay: Long) {
        dataStore.edit { it[LAST_SEEN_PERIOD_START] = epochDay }
    }

    /** 월 총 예산 (minor 단위). null = 미설정 (예산 바 숨김). */
    val monthlyBudgetMinor: Flow<Long?> =
        dataStore.data.map { prefs -> prefs[MONTHLY_BUDGET_MINOR] }

    suspend fun setMonthlyBudgetMinor(value: Long?) {
        dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(MONTHLY_BUDGET_MINOR)
            } else {
                prefs[MONTHLY_BUDGET_MINOR] = value
            }
        }
    }

    val reminderEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[REMINDER_ENABLED] ?: false }

    val reminderHour: Flow<Int> =
        dataStore.data.map { prefs -> prefs[REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }

    suspend fun setReminderEnabled(value: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = value }
    }

    suspend fun setReminderHour(value: Int) {
        require(value in 0..23)
        dataStore.edit { it[REMINDER_HOUR] = value }
    }

    val appLockEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[APP_LOCK_ENABLED] ?: false }

    suspend fun setAppLockEnabled(value: Boolean) {
        dataStore.edit { it[APP_LOCK_ENABLED] = value }
    }

    companion object {
        private val PAYDAY_DOM = intPreferencesKey("payday_dom")
        private val LAST_SEEN_PERIOD_START = longPreferencesKey("last_seen_period_start_epoch_day")
        private val MONTHLY_BUDGET_MINOR = longPreferencesKey("monthly_budget_minor")
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private const val DEFAULT_PAYDAY_DOM = 25
        private const val DEFAULT_REMINDER_HOUR = 21
    }
}
