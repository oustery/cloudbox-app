package com.example.data.preferences

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.cloudBoxSettingsDataStore by preferencesDataStore(name = "cloudbox_settings")

private val themeModeKey = stringPreferencesKey("theme_mode")
private val dynamicColorsKey = booleanPreferencesKey("dynamic_colors")
private val gridByDefaultKey = booleanPreferencesKey("grid_by_default")

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная");

    companion object {
        fun fromPreference(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val gridByDefault: Boolean = true
)

private class AppSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.cloudBoxSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = AppThemeMode.fromPreference(preferences[themeModeKey]),
            dynamicColors = preferences[dynamicColorsKey] ?: true,
            gridByDefault = preferences[gridByDefaultKey] ?: true
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[dynamicColorsKey] = enabled }
    }

    suspend fun setGridByDefault(enabled: Boolean) {
        dataStore.edit { it[gridByDefaultKey] = enabled }
    }
}

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppSettingsRepository(application)

    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColors(enabled) }
    }

    fun setGridByDefault(enabled: Boolean) {
        viewModelScope.launch { repository.setGridByDefault(enabled) }
    }
}
