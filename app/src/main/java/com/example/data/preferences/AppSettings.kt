package com.example.data.preferences

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.UiPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.cloudBoxSettingsDataStore by preferencesDataStore(name = "cloudbox_settings")

private val themeModeKey = stringPreferencesKey("theme_mode")
private val dynamicColorsKey = booleanPreferencesKey("dynamic_colors")
private val startScreenKey = stringPreferencesKey("start_screen")
private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
private val dataSaverKey = booleanPreferencesKey("data_saver")
private val autoUpdateCheckKey = booleanPreferencesKey("auto_update_check")
private val fontKey = stringPreferencesKey("app_font")
private val iconVariantKey = stringPreferencesKey("app_icon_variant")

/**
 * Screen the app lands on after a cold start. Stored by [StartScreen.key];
 * anything unknown falls back to HOME so a renamed enum never bricks startup.
 */
enum class StartScreen(val key: String, val title: String) {
    HOME("home", "Главная"),
    EXPLORER("explorer", "Проводник"),
    DOWNLOADS("downloads", "Загрузки");

    companion object {
        fun fromKey(value: String?): StartScreen =
            entries.firstOrNull { it.key == value } ?: HOME
    }
}

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная");

    companion object {
        fun fromPreference(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Typeface family used across the whole UI. Stored by [AppFont.key]; an
 * unknown value (renamed enum, older build) falls back to INTER so startup
 * never bricks.
 */
enum class AppFont(val key: String, val title: String) {
    INTER("inter", "Inter"),
    MANROPE("manrope", "Manrope"),
    RUBIK("rubik", "Rubik"),
    NUNITO("nunito", "Nunito"),
    SYSTEM("system", "Системный");

    companion object {
        fun fromKey(value: String?): AppFont =
            entries.firstOrNull { it.key == value } ?: INTER
    }
}

/**
 * Launcher icon look. The actual icon switching lives in
 * [com.example.data.preferences.AppIconManager] via activity-alias components;
 * this enum only names the choices and their persisted keys.
 */
enum class AppIconVariant(val key: String, val title: String) {
    STANDARD("standard", "Стандартная"),
    GRAPHITE("graphite", "Графит"),
    FROST("frost", "Океан");

    companion object {
        fun fromKey(value: String?): AppIconVariant =
            entries.firstOrNull { it.key == value } ?: STANDARD
    }
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColors: Boolean = true,
    val startScreen: StartScreen = StartScreen.HOME,
    val hapticsEnabled: Boolean = true,
    val dataSaver: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    val font: AppFont = AppFont.INTER,
    val iconVariant: AppIconVariant = AppIconVariant.STANDARD
)

private class AppSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.cloudBoxSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = AppThemeMode.fromPreference(preferences[themeModeKey]),
            dynamicColors = preferences[dynamicColorsKey] ?: true,
            startScreen = StartScreen.fromKey(preferences[startScreenKey]),
            hapticsEnabled = preferences[hapticsEnabledKey] ?: true,
            dataSaver = preferences[dataSaverKey] ?: false,
            autoUpdateCheck = preferences[autoUpdateCheckKey] ?: true,
            font = AppFont.fromKey(preferences[fontKey]),
            iconVariant = AppIconVariant.fromKey(preferences[iconVariantKey])
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[dynamicColorsKey] = enabled }
    }

    suspend fun setStartScreen(screen: StartScreen) {
        dataStore.edit { it[startScreenKey] = screen.key }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[hapticsEnabledKey] = enabled }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        dataStore.edit { it[dataSaverKey] = enabled }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        dataStore.edit { it[autoUpdateCheckKey] = enabled }
    }

    suspend fun setFont(font: AppFont) {
        dataStore.edit { it[fontKey] = font.key }
    }

    suspend fun setIconVariant(variant: AppIconVariant) {
        dataStore.edit { it[iconVariantKey] = variant.key }
    }
}

class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppSettingsRepository(application)
    private val uiPrefs = UiPrefs(application)

    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    /** Explorer view mode — the single source of truth is [UiPrefs]. */
    val explorerGridView: StateFlow<Boolean> = uiPrefs.explorerGridView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    val explorerGridDensity: StateFlow<GridDensity> = uiPrefs.explorerGridDensity.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GridDensity.STANDARD
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColors(enabled) }
    }

    fun setStartScreen(screen: StartScreen) {
        viewModelScope.launch { repository.setStartScreen(screen) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHapticsEnabled(enabled) }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch { repository.setDataSaver(enabled) }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoUpdateCheck(enabled) }
    }

    fun setFont(font: AppFont) {
        viewModelScope.launch { repository.setFont(font) }
    }

    /** Persists the choice and flips the launcher alias components. */
    fun setIconVariant(variant: AppIconVariant) {
        viewModelScope.launch {
            repository.setIconVariant(variant)
            AppIconManager.apply(getApplication(), variant)
        }
    }

    fun setExplorerGridView(isGrid: Boolean) {
        viewModelScope.launch { uiPrefs.setExplorerGridView(isGrid) }
    }

    fun setExplorerGridDensity(density: GridDensity) {
        viewModelScope.launch { uiPrefs.setExplorerGridDensity(density) }
    }
}
