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

    val kbankCardEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KBANK_CARD_ENABLED] ?: false }

    suspend fun setKbankCardEnabled(enabled: Boolean) {
        dataStore.edit { it[KBANK_CARD_ENABLED] = enabled }
    }

    companion object {
        private val PAYDAY_DOM = intPreferencesKey("payday_dom")
        private val LAST_SEEN_PERIOD_START = longPreferencesKey("last_seen_period_start_epoch_day")
        private val KBANK_CARD_ENABLED = booleanPreferencesKey("kbank_card_enabled")
        private const val DEFAULT_PAYDAY_DOM = 25
    }
}
