package com.sigynvs.phonejanitor.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sigynvs.phonejanitor.scan.ScanFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists the tunable scan thresholds. Backed by Preferences DataStore. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val DOWNLOADS_DAYS = intPreferencesKey("downloads_older_than_days")
        val SCREENSHOTS_DAYS = intPreferencesKey("screenshots_older_than_days")
        val LARGE_MB = intPreferencesKey("large_file_min_mb")
        val DRY_RUN = booleanPreferencesKey("dry_run")
        val GMAIL_QUERY = stringPreferencesKey("gmail_search_query")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_millis")
    }

    val filters: Flow<ScanFilters> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            ScanFilters(
                downloadsOlderThanDays = p[Keys.DOWNLOADS_DAYS] ?: ScanFilters.DEFAULT_DOWNLOADS_DAYS,
                screenshotsOlderThanDays = p[Keys.SCREENSHOTS_DAYS] ?: ScanFilters.DEFAULT_SCREENSHOTS_DAYS,
                largeFileMinMb = p[Keys.LARGE_MB] ?: ScanFilters.DEFAULT_LARGE_MB,
                dryRun = p[Keys.DRY_RUN] ?: false,
            )
        }

    val gmailQuery: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.GMAIL_QUERY]?.takeIf { q -> q.isNotBlank() } ?: DEFAULT_GMAIL_QUERY }

    val lastUpdateCheck: Flow<Long> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.LAST_UPDATE_CHECK] ?: 0L }

    suspend fun setLastUpdateCheck(millis: Long) = write { it[Keys.LAST_UPDATE_CHECK] = millis }

    suspend fun setDownloadsDays(value: Int) = write { it[Keys.DOWNLOADS_DAYS] = value.coerceIn(7, 3650) }
    suspend fun setScreenshotsDays(value: Int) = write { it[Keys.SCREENSHOTS_DAYS] = value.coerceIn(7, 3650) }
    suspend fun setLargeMb(value: Int) = write { it[Keys.LARGE_MB] = value.coerceIn(10, 4096) }
    suspend fun setDryRun(value: Boolean) = write { it[Keys.DRY_RUN] = value }
    suspend fun setGmailQuery(value: String) = write { it[Keys.GMAIL_QUERY] = value.trim() }

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    companion object {
        const val DEFAULT_GMAIL_QUERY = "category:promotions OR category:social older_than:180d"
    }
}
