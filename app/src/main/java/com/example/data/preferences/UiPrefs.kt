package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CloudItemSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPrefsDataStore by preferencesDataStore(name = "cloudbox_ui_prefs")

private val explorerSortOrderKey = stringPreferencesKey("explorer_sort_order")
private val explorerFilterKey = stringPreferencesKey("explorer_filter")
private val explorerGridViewKey = booleanPreferencesKey("explorer_grid_view")
private val explorerGridDensityKey = stringPreferencesKey("explorer_grid_density")
private val homeSortOrderKey = stringPreferencesKey("home_sort_order")

/**
 * Sort order of the saved-links library on the home screen. Persisted like the
 * explorer's view state: the library should come back the way it was left.
 */
enum class LibrarySortOrder(val title: String) {
    NEWEST("Сначала новые"),
    OLDEST("Сначала старые"),
    NAME("По имени"),
    MOST_FILES("Больше файлов");
}

/**
 * Density of the explorer grid: the minimum tile size fed to
 * GridCells.Adaptive. Compact fits more columns, Comfortable gives larger
 * thumbnails.
 */
enum class GridDensity(val title: String, val minSizeDp: Int) {
    COMPACT("Компактно", 104),
    STANDARD("Обычно", 140),
    COMFORTABLE("Крупно", 184);
}

/**
 * Remembers the explorer's view state (sort order, file filter, grid/list)
 * across sessions. The explorer is a workspace: users expect it to come back
 * exactly the way they left it.
 */
class UiPrefs(context: Context) {

    private val dataStore = context.applicationContext.uiPrefsDataStore

    val explorerSortOrder: Flow<CloudItemSortOrder> = dataStore.data.map { preferences ->
        preferences[explorerSortOrderKey]?.let { name ->
            CloudItemSortOrder.entries.firstOrNull { it.name == name }
        } ?: CloudItemSortOrder.DATE_NEWEST
    }

    val explorerFilter: Flow<String> = dataStore.data.map { preferences ->
        preferences[explorerFilterKey] ?: "Все"
    }

    val explorerGridView: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[explorerGridViewKey] ?: true
    }

    val explorerGridDensity: Flow<GridDensity> = dataStore.data.map { preferences ->
        preferences[explorerGridDensityKey]?.let { name ->
            GridDensity.entries.firstOrNull { it.name == name }
        } ?: GridDensity.STANDARD
    }

    val homeSortOrder: Flow<LibrarySortOrder> = dataStore.data.map { preferences ->
        preferences[homeSortOrderKey]?.let { name ->
            LibrarySortOrder.entries.firstOrNull { it.name == name }
        } ?: LibrarySortOrder.NEWEST
    }

    suspend fun setExplorerSortOrder(order: CloudItemSortOrder) {
        dataStore.edit { it[explorerSortOrderKey] = order.name }
    }

    suspend fun setExplorerFilter(filter: String) {
        dataStore.edit { it[explorerFilterKey] = filter }
    }

    suspend fun setExplorerGridView(isGrid: Boolean) {
        dataStore.edit { it[explorerGridViewKey] = isGrid }
    }

    suspend fun setExplorerGridDensity(density: GridDensity) {
        dataStore.edit { it[explorerGridDensityKey] = density.name }
    }

    suspend fun setHomeSortOrder(order: LibrarySortOrder) {
        dataStore.edit { it[homeSortOrderKey] = order.name }
    }
}
