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

    suspend fun setExplorerSortOrder(order: CloudItemSortOrder) {
        dataStore.edit { it[explorerSortOrderKey] = order.name }
    }

    suspend fun setExplorerFilter(filter: String) {
        dataStore.edit { it[explorerFilterKey] = filter }
    }

    suspend fun setExplorerGridView(isGrid: Boolean) {
        dataStore.edit { it[explorerGridViewKey] = isGrid }
    }
}
