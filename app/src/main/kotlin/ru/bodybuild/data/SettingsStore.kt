package ru.bodybuild.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val advancedKey = booleanPreferencesKey("advanced_mode")
    val advancedMode: Flow<Boolean> = context.settingsDataStore.data.map { it[advancedKey] ?: false }
    suspend fun setAdvancedMode(enabled: Boolean) { context.settingsDataStore.edit { it[advancedKey] = enabled } }
}
