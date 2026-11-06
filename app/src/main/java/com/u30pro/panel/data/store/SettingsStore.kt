package com.u30pro.panel.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.u30pro.panel.data.profile.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "u30_settings")

class SettingsStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val PROFILE = stringPreferencesKey("device_profile")
        val POLL_MS = longPreferencesKey("poll_interval_ms")
        val GATEWAY = stringPreferencesKey("last_gateway")
    }

    val profile: Flow<DeviceProfile?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PROFILE]?.let {
            runCatching { json.decodeFromString<DeviceProfile>(it) }.getOrNull()
        }
    }

    val pollIntervalMs: Flow<Long> = context.settingsDataStore.data.map { it[Keys.POLL_MS] ?: 1_000L }

    val lastGateway: Flow<String?> = context.settingsDataStore.data.map { it[Keys.GATEWAY] }

    suspend fun loadProfile(): DeviceProfile? = profile.first()

    suspend fun saveProfile(profile: DeviceProfile) {
        context.settingsDataStore.edit {
            it[Keys.PROFILE] = json.encodeToString(DeviceProfile.serializer(), profile)
            it[Keys.GATEWAY] = profile.gateway
        }
    }

    suspend fun savePollInterval(ms: Long) {
        context.settingsDataStore.edit { it[Keys.POLL_MS] = ms }
    }

    suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }
}