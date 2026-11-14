package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        CachedCourseEntity::class,
        CachedEnrolmentEntity::class,
        CachedAttendanceEntity::class,
        CachedResultEntity::class,
        CachedAnnouncementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JySmsDatabase : RoomDatabase() {
    abstract fun jySmsDao(): JySmsDao

    companion object {
        @Volatile
        private var INSTANCE: JySmsDatabase? = null

        fun getInstance(context: Context): JySmsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JySmsDatabase::class.java,
                    "jy_sms_offline_cache.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jy_sms_prefs")

data class UserPreferences(
    val darkModeOverride: Boolean? = null,
    val notifyMessages: Boolean = true,
    val notifyPayments: Boolean = true,
    val notifyAcademics: Boolean = true,
    val notifyAnnouncements: Boolean = true,
    val paymentBackendUrl: String = ""
)

class AppPreferencesRepository(private val context: Context) {
    private val keyDarkMode = stringPreferencesKey("dark_mode_override")
    private val keyNotifyMessages = booleanPreferencesKey("notify_messages")
    private val keyNotifyPayments = booleanPreferencesKey("notify_payments")
    private val keyNotifyAcademics = booleanPreferencesKey("notify_academics")
    private val keyNotifyAnnouncements = booleanPreferencesKey("notify_announcements")
    private val keyPaymentBackendUrl = stringPreferencesKey("payment_backend_url")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val darkStr = prefs[keyDarkMode]
        UserPreferences(
            darkModeOverride = when (darkStr) {
                "DARK" -> true
                "LIGHT" -> false
                else -> null
            },
            notifyMessages = prefs[keyNotifyMessages] ?: true,
            notifyPayments = prefs[keyNotifyPayments] ?: true,
            notifyAcademics = prefs[keyNotifyAcademics] ?: true,
            notifyAnnouncements = prefs[keyNotifyAnnouncements] ?: true,
            paymentBackendUrl = prefs[keyPaymentBackendUrl] ?: ""
        )
    }

    suspend fun setDarkMode(mode: Boolean?) {
        context.dataStore.edit { prefs ->
            when (mode) {
                true -> prefs[keyDarkMode] = "DARK"
                false -> prefs[keyDarkMode] = "LIGHT"
                null -> prefs.remove(keyDarkMode)
            }
        }
    }

    suspend fun updateNotificationPrefs(
        messages: Boolean,
        payments: Boolean,
        academics: Boolean,
        announcements: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[keyNotifyMessages] = messages
            prefs[keyNotifyPayments] = payments
            prefs[keyNotifyAcademics] = academics
            prefs[keyNotifyAnnouncements] = announcements
        }
    }

    suspend fun setPaymentBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[keyPaymentBackendUrl] = url.trim()
        }
    }
}
