package com.borhji.mushaf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mushaf_settings")

/** إعدادات المشغل وحالة الجلسة الأخيرة (آخر سورة/موضع/سرعة/وضع تشغيل) */
data class PlayerPrefs(
    val lastSurah: Int? = null,
    val lastPositionMs: Long = 0L,
    val lastDurationMs: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val speed: Float = 1.0f,
    val repeatOne: Boolean = false,
    val copyIntoApp: Boolean = true,
    val showMissingOnly: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LAST_SURAH = intPreferencesKey("last_surah")
        val LAST_POSITION = longPreferencesKey("last_position_ms")
        val LAST_DURATION = longPreferencesKey("last_duration_ms")
        val LAST_UPDATED = longPreferencesKey("last_updated_at")
        val SPEED = floatPreferencesKey("playback_speed")
        val REPEAT_ONE = booleanPreferencesKey("repeat_one")
        val COPY_INTO_APP = booleanPreferencesKey("copy_into_app")
        val SHOW_MISSING_ONLY = booleanPreferencesKey("show_missing_only")
    }

    val prefs: Flow<PlayerPrefs> = context.settingsDataStore.data.map { p ->
        PlayerPrefs(
            lastSurah = p[Keys.LAST_SURAH],
            lastPositionMs = p[Keys.LAST_POSITION] ?: 0L,
            lastDurationMs = p[Keys.LAST_DURATION] ?: 0L,
            lastUpdatedAt = p[Keys.LAST_UPDATED] ?: 0L,
            speed = p[Keys.SPEED] ?: 1.0f,
            repeatOne = p[Keys.REPEAT_ONE] ?: false,
            copyIntoApp = p[Keys.COPY_INTO_APP] ?: true,
            showMissingOnly = p[Keys.SHOW_MISSING_ONLY] ?: false,
        )
    }

    /** قراءة لقطة متزامنة من الإعدادات (تُستخدم عند بدء خدمة التشغيل) */
    suspend fun snapshot(): PlayerPrefs = prefs.first()

    suspend fun setLast(surah: Int, positionMs: Long, durationMs: Long) {
        context.settingsDataStore.edit { p ->
            p[Keys.LAST_SURAH] = surah
            p[Keys.LAST_POSITION] = positionMs.coerceAtLeast(0L)
            p[Keys.LAST_DURATION] = durationMs.coerceAtLeast(0L)
            p[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun clearLast() {
        context.settingsDataStore.edit { p ->
            p.remove(Keys.LAST_SURAH)
            p[Keys.LAST_POSITION] = 0L
            p[Keys.LAST_DURATION] = 0L
            p[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    /** تصفير حالة الجلسة فقط إذا كانت السورة المعنية هي آخر سورة محفوظة */
    suspend fun clearLastIfSurah(surah: Int) {
        context.settingsDataStore.edit { p ->
            if (p[Keys.LAST_SURAH] == surah) {
                p.remove(Keys.LAST_SURAH)
                p[Keys.LAST_POSITION] = 0L
                p[Keys.LAST_DURATION] = 0L
                p[Keys.LAST_UPDATED] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setSpeed(speed: Float) {
        context.settingsDataStore.edit { it[Keys.SPEED] = speed }
    }

    suspend fun setRepeatOne(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REPEAT_ONE] = enabled }
    }

    suspend fun setCopyIntoApp(copy: Boolean) {
        context.settingsDataStore.edit { it[Keys.COPY_INTO_APP] = copy }
    }

    suspend fun setShowMissingOnly(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_MISSING_ONLY] = show }
    }
}
