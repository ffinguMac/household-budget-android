package com.householdbudget.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.profilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles")

data class Profile(val id: Long, val name: String)

class ProfileManager(context: Context) {
    private val ds = context.applicationContext.profilesDataStore

    val profiles: Flow<List<Profile>> = ds.data.map { prefs ->
        parseProfiles(prefs[PROFILES_JSON] ?: "[]")
    }

    val currentProfileId: Flow<Long> = ds.data.map { prefs ->
        prefs[CURRENT_ID] ?: DEFAULT_PROFILE_ID
    }

    suspend fun getProfilesSnapshot(): List<Profile> = profiles.first()

    suspend fun getCurrentProfileIdSnapshot(): Long = currentProfileId.first()

    /** 최초 실행 시 기본 프로필이 없으면 자동 생성. */
    suspend fun ensureDefaultProfile() {
        val existing = getProfilesSnapshot()
        if (existing.isEmpty()) {
            ds.edit { prefs ->
                prefs[PROFILES_JSON] = serializeProfiles(listOf(Profile(DEFAULT_PROFILE_ID, "기본")))
                prefs[CURRENT_ID] = DEFAULT_PROFILE_ID
            }
        }
    }

    suspend fun addProfile(name: String): Profile {
        val current = getProfilesSnapshot()
        val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
        val newProfile = Profile(newId, name.trim())
        ds.edit { prefs ->
            prefs[PROFILES_JSON] = serializeProfiles(current + newProfile)
        }
        return newProfile
    }

    suspend fun renameProfile(id: Long, name: String) {
        val current = getProfilesSnapshot()
        ds.edit { prefs ->
            prefs[PROFILES_JSON] = serializeProfiles(
                current.map { if (it.id == id) it.copy(name = name.trim()) else it }
            )
        }
    }

    suspend fun deleteProfile(id: Long) {
        require(id != DEFAULT_PROFILE_ID) { "기본 프로필은 삭제할 수 없습니다." }
        val current = getProfilesSnapshot()
        ds.edit { prefs ->
            prefs[PROFILES_JSON] = serializeProfiles(current.filter { it.id != id })
            if ((prefs[CURRENT_ID] ?: DEFAULT_PROFILE_ID) == id) {
                prefs[CURRENT_ID] = DEFAULT_PROFILE_ID
            }
        }
    }

    suspend fun setCurrentProfile(id: Long) {
        ds.edit { prefs -> prefs[CURRENT_ID] = id }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = 1L

        private val PROFILES_JSON = stringPreferencesKey("profiles_json")
        private val CURRENT_ID = longPreferencesKey("current_profile_id")

        fun dbNameForProfile(profileId: Long): String =
            if (profileId == DEFAULT_PROFILE_ID) "household_budget.db"
            else "household_budget_$profileId.db"

        private fun parseProfiles(json: String): List<Profile> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Profile(id = obj.getLong("id"), name = obj.getString("name"))
            }
        }.getOrDefault(emptyList())

        private fun serializeProfiles(profiles: List<Profile>): String =
            JSONArray().also { arr ->
                profiles.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                    })
                }
            }.toString()
    }
}
