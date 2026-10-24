package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.CloudUrlParser
import com.example.data.api.LinkProvider
import com.example.data.local.SavedLinkEntity
import com.example.data.model.CloudItem
import com.example.data.model.CloudItemSortOrder
import com.example.data.repository.CloudMailRuRepository
import com.example.data.repository.FolderResult
import com.example.data.repository.ThumbnailSize
import com.example.data.preferences.GridDensity
import com.example.data.preferences.UiPrefs
import com.example.ui.core.feedback.UiMessenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderStackEntry(
    val title: String,
    val weblink: String
)

/**
 * One entry of the unified "Все файлы" pool: a cloud item plus the saved link
 * it came from. [sourceTitle]/[sourceWeblinkKey] are null when the entry is
 * shown inside a regular single-folder listing.
 */
data class AggregatedEntry(
    val item: CloudItem,
    val sourceTitle: String? = null,
    val sourceWeblinkKey: String? = null
)

class CloudLinkViewModel(
    private val repository: CloudMailRuRepository,
    private val uiPrefs: UiPrefs? = null
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Все")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _linkInput = MutableStateFlow("")
    val linkInput: StateFlow<String> = _linkInput.asStateFlow()

    // Filtered saved links
    val savedLinks: StateFlow<List<SavedLinkEntity>> = combine(
        repository.allSavedLinks,
        _selectedCategory,
        _searchQuery
    ) { links, category, query ->
        links.filter { entity ->
            val matchesCategory = when (category) {
                "Все" -> true
                "Избранное" -> entity.isFavorite
                else -> entity.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = if (query.isBlank()) true else {
                entity.title.contains(query, ignoreCase = true) ||
                        entity.weblinkKey.contains(query, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current open folder state
    private val _currentFolder = MutableStateFlow<FolderResult?>(null)
    val currentFolder: StateFlow<FolderResult?> = _currentFolder.asStateFlow()

    private val _isLoadingFolder = MutableStateFlow(false)
    val isLoadingFolder: StateFlow<Boolean> = _isLoadingFolder.asStateFlow()

    private val _folderError = MutableStateFlow<String?>(null)
    val folderError: StateFlow<String?> = _folderError.asStateFlow()
    private var lastRequestedWeblink: String? = null
    private var lastRequestResetStack: Boolean = true

    private val _folderBreadcrumbs = MutableStateFlow<List<FolderStackEntry>>(emptyList())
    val folderBreadcrumbs: StateFlow<List<FolderStackEntry>> = _folderBreadcrumbs.asStateFlow()

    // Explorer view filters and selection
    private val _fileTypeFilter = MutableStateFlow("Все")
    val fileTypeFilter: StateFlow<String> = _fileTypeFilter.asStateFlow()

    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery: StateFlow<String> = _fileSearchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _fileSortOrder = MutableStateFlow(CloudItemSortOrder.DATE_NEWEST)
    val fileSortOrder: StateFlow<CloudItemSortOrder> = _fileSortOrder.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // Currently opened media in viewer
    private val _activeMediaItem = MutableStateFlow<CloudItem?>(null)
    val activeMediaItem: StateFlow<CloudItem?> = _activeMediaItem.asStateFlow()

    /** Last playback position per video weblink, used to resume where the user left off. */
    private val videoResumePositions = mutableMapOf<String, Long>()

    // Unified pool: content of every saved link merged into one list
    private val _aggregatedEntries = MutableStateFlow<List<AggregatedEntry>>(emptyList())
    val aggregatedEntries: StateFlow<List<AggregatedEntry>> = _aggregatedEntries.asStateFlow()

    private val _isLoadingAggregated = MutableStateFlow(false)
    val isLoadingAggregated: StateFlow<Boolean> = _isLoadingAggregated.asStateFlow()

    /** Saved links whose content could not be loaded during the last pool refresh. */
    private val _aggregatedFailedCount = MutableStateFlow(0)
    val aggregatedFailedCount: StateFlow<Int> = _aggregatedFailedCount.asStateFlow()

    /** Total number of saved links known at the moment of the last pool refresh. */
    private val _savedLinksTotal = MutableStateFlow(0)
    val savedLinksTotal: StateFlow<Int> = _savedLinksTotal.asStateFlow()

    /** True when links changed (added/removed) and the pool must be reloaded. */
    @Volatile
    private var poolDirty: Boolean = true

    /** Media playlist override: used when the viewer is opened from the pool. */
    private val _mediaPlaylist = MutableStateFlow<List<CloudItem>?>(null)

    init {
        // Pre-warm dispatcher shards
        viewModelScope.launch {
            repository.refreshDispatcher()
        }
        // Restore the remembered explorer workspace (sort/filter/view mode).
        if (uiPrefs != null) {
            viewModelScope.launch {
                _fileSortOrder.value = uiPrefs.explorerSortOrder.first()
                _fileTypeFilter.value = uiPrefs.explorerFilter.first()
                _isGridView.value = uiPrefs.explorerGridView.first()
                _gridDensity.value = uiPrefs.explorerGridDensity.first()
            }
        }
    }

    private val _gridDensity = MutableStateFlow(GridDensity.STANDARD)
    val gridDensity: StateFlow<GridDensity> = _gridDensity.asStateFlow()

    fun setGridDensity(density: GridDensity) {
        _gridDensity.value = density
        persist { it.setExplorerGridDensity(density) }
    }

    private fun persist(block: suspend (UiPrefs) -> Unit) {
        val prefs = uiPrefs ?: return
        viewModelScope.launch { block(prefs) }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLinkInput(value: String) {
        _linkInput.value = value
        _folderError.value = null
    }

    fun setFileTypeFilter(filter: String) {
        _fileTypeFilter.value = filter
        persist { it.setExplorerFilter(filter) }
    }

    fun setFileSearchQuery(query: String) {
        _fileSearchQuery.value = query
    }

    fun toggleGridView() {
        setGridView(!_isGridView.value)
    }

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
        persist { it.setExplorerGridView(isGrid) }
    }

    fun setFileSortOrder(order: CloudItemSortOrder) {
        _fileSortOrder.value = order
        persist { it.setExplorerSortOrder(order) }
    }

    fun openMediaViewer(item: CloudItem, playlist: List<CloudItem>? = null) {
        _mediaPlaylist.value = playlist
        _activeMediaItem.value = item
    }

    fun closeMediaViewer() {
        _activeMediaItem.value = null
    }

    fun nextMediaItem() {
        val current = _activeMediaItem.value ?: return
        val list = getMediaOnlyItems()
        val index = list.indexOfFirst { it.weblink == current.weblink }
        if (index >= 0 && index < list.size - 1) {
            _activeMediaItem.value = list[index + 1]
        }
    }

    fun previousMediaItem() {
        val current = _activeMediaItem.value ?: return
        val list = getMediaOnlyItems()
        val index = list.indexOfFirst { it.weblink == current.weblink }
        if (index > 0) {
            _activeMediaItem.value = list[index - 1]
        }
    }

    /** Media (image/video) items of the active context: playlist override, else the open folder. */
    fun getActiveMediaItems(): List<CloudItem> = getMediaOnlyItems()

    /** Switches the viewer to another item WITHOUT resetting the active playlist. */
    fun selectMediaInPlaylist(item: CloudItem) {
        _activeMediaItem.value = item
    }

    private fun getMediaOnlyItems(): List<CloudItem> {
        _mediaPlaylist.value?.let { return it }
        val all = _currentFolder.value?.items ?: emptyList()
        return all.filter { it.isImage || it.isVideo }
    }

    fun loadFolder(rawUrlOrWeblink: String, resetStack: Boolean = true) {
        // Keep the canonical key: Mail.ru bare weblink key or normalized Yandex URL.
        val weblink = CloudUrlParser.extractCloudKey(rawUrlOrWeblink) ?: rawUrlOrWeblink
        lastRequestedWeblink = weblink
        lastRequestResetStack = resetStack
        _isLoadingFolder.value = true
        _folderError.value = null
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false

        viewModelScope.launch {
            val result = repository.fetchFolderContent(weblink)
            result.onSuccess { folderResult ->
                _currentFolder.value = folderResult
                _isLoadingFolder.value = false

                if (resetStack) {
                    _linkInput.value = ""
                }

                if (resetStack) {
                    _folderBreadcrumbs.value = listOf(
                        FolderStackEntry(title = folderResult.name, weblink = folderResult.weblinkKey)
                    )
                } else {
                    // Check if already in breadcrumbs
                    val currentCrumbs = _folderBreadcrumbs.value.toMutableList()
                    val existingIndex = currentCrumbs.indexOfFirst { it.weblink == folderResult.weblinkKey }
                    if (existingIndex >= 0) {
                        _folderBreadcrumbs.value = currentCrumbs.subList(0, existingIndex + 1)
                    } else {
                        currentCrumbs.add(FolderStackEntry(title = folderResult.name, weblink = folderResult.weblinkKey))
                        _folderBreadcrumbs.value = currentCrumbs
                    }
                }

                poolDirty = true

                // Auto-save to library if this is the root folder. The stored
                // category and a user-set custom title are preserved.
                val provider = LinkProvider.detectOrDefault(folderResult.weblinkKey)
                val firstImage = folderResult.items.firstOrNull { it.isImage }
                val thumbUrl = firstImage?.let { repository.getThumbnailUrl(it, ThumbnailSize.HIGH_RES) }
                repository.saveLink(
                    url = CloudUrlParser.toFullUrl(provider, folderResult.weblinkKey),
                    title = folderResult.name,
                    itemCount = folderResult.items.size,
                    previewThumbnailUrl = thumbUrl,
                    updateTitle = false
                )
            }.onFailure { exception ->
                _isLoadingFolder.value = false
                _folderError.value = exception.message ?: "Не удалось открыть папку"
            }
        }
    }

    fun retryLastFolderLoad() {
        lastRequestedWeblink?.let { loadFolder(it, resetStack = lastRequestResetStack) }
    }

    /**
     * Opens a folder straight from the unified pool: resets the pool-scoped
     * search/filter so stale conditions don't leak into the folder view.
     */
    fun openFromPool(weblink: String) {
        _fileSearchQuery.value = ""
        setFileTypeFilter("Все")
        loadFolder(weblink, resetStack = false)
    }

    /** Closes the open folder and returns the explorer to the unified pool. */
    fun closeFolder() {
        _currentFolder.value = null
        _folderBreadcrumbs.value = emptyList()
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
        _fileSearchQuery.value = ""
        setFileTypeFilter("Все")
    }

    /**
     * Loads the root content of EVERY saved link in parallel and merges it into
     * one unified pool. Partial failures are tolerated: successful links are
     * shown, failed ones are counted in [aggregatedFailedCount].
     */
    fun loadAllContent() {
        if (_isLoadingAggregated.value) return
        viewModelScope.launch {
            _isLoadingAggregated.value = true
            _aggregatedFailedCount.value = 0
            poolDirty = false
            val links = repository.getSavedLinksOnce()
            _savedLinksTotal.value = links.size
            if (links.isEmpty()) {
                _aggregatedEntries.value = emptyList()
                _isLoadingAggregated.value = false
                return@launch
            }
            val results: List<Pair<SavedLinkEntity, Result<FolderResult>>> = coroutineScope {
                links.map { link ->
                    async {
                        // fetchFolderContent already returns Result (it handles
                        // its own errors); only a non-Exception escape would
                        // wrap again, so normalise explicitly to a flat Result.
                        val outcome: Result<FolderResult> = try {
                            repository.fetchFolderContent(link.weblinkKey)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Result.failure(t)
                        }
                        link to outcome
                    }
                }.awaitAll()
            }
            val entries = mutableListOf<AggregatedEntry>()
            var failed = 0
            results.forEach { (link, result) ->
                val folder: FolderResult? = result.getOrNull()
                if (folder == null) {
                    failed++
                } else {
                    folder.items.forEach { item ->
                        entries.add(
                            AggregatedEntry(
                                item = item,
                                sourceTitle = link.title,
                                sourceWeblinkKey = link.weblinkKey
                            )
                        )
                    }
                }
            }
            _aggregatedEntries.value = entries.distinctBy { it.item.weblink }
            _aggregatedFailedCount.value = failed
            _isLoadingAggregated.value = false
        }
    }

    /** Whether the explorer should kick off an automatic pool load right now. */
    fun shouldAutoLoadPool(): Boolean =
        _currentFolder.value == null && !_isLoadingAggregated.value &&
                (poolDirty || _aggregatedEntries.value.isEmpty())

    /** Items of the currently active context (open folder or the unified pool). */
    private fun activeItems(): List<CloudItem> =
        _currentFolder.value?.items ?: _aggregatedEntries.value.map { it.item }

    /** Maps a set of selected weblinks back to cloud items for batch download. */
    fun getItemsByWeblinks(weblinks: Set<String>): List<CloudItem> =
        activeItems().filter { weblinks.contains(it.weblink) && !it.isFolder }

    fun navigateToBreadcrumb(entry: FolderStackEntry) {
        val currentCrumbs = _folderBreadcrumbs.value
        val index = currentCrumbs.indexOf(entry)
        if (index >= 0) {
            _folderBreadcrumbs.value = currentCrumbs.subList(0, index + 1)
            loadFolder(entry.weblink, resetStack = false)
        }
    }

    fun navigateBack(): Boolean {
        val crumbs = _folderBreadcrumbs.value
        return if (crumbs.size > 1) {
            val target = crumbs[crumbs.size - 2]
            navigateToBreadcrumb(target)
            true
        } else {
            // At the root: drop to the unified pool (keeps explorer visible)
            closeFolder()
            false
        }
    }

    fun toggleFavorite(id: Long, currentFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, !currentFav)
        }
    }

    /** Renames a saved link; confirms via snackbar. */
    fun renameSavedLink(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameLink(id, newTitle)
            UiMessenger.show("Название обновлено")
        }
    }

    /** Moves a saved link to a library category; confirms via snackbar. */
    fun setSavedLinkCategory(id: Long, category: String) {
        viewModelScope.launch {
            repository.setLinkCategory(id, category)
            UiMessenger.show("Категория: $category")
        }
    }

    private val _isRefreshingLinks = MutableStateFlow(false)
    val isRefreshingLinks: StateFlow<Boolean> = _isRefreshingLinks.asStateFlow()

    /**
     * Pull-to-refresh for the library: re-fetches the root folder of every
     * saved link in parallel and updates its file count and cover preview.
     * Titles and user categories are never touched.
     */
    fun refreshSavedLinkCounts() {
        if (_isRefreshingLinks.value) return
        viewModelScope.launch {
            _isRefreshingLinks.value = true
            val links = repository.getSavedLinksOnce()
            var updated = 0
            var failed = 0
            coroutineScope {
                links.map { link ->
                    async {
                        val outcome: Result<FolderResult> = try {
                            repository.fetchFolderContent(link.weblinkKey)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Result.failure(t)
                        }
                        outcome.onSuccess { folder ->
                            val firstImage = folder.items.firstOrNull { it.isImage }
                            val thumbUrl = firstImage?.let {
                                repository.getThumbnailUrl(it, ThumbnailSize.HIGH_RES)
                            }
                            repository.updateLinkStats(
                                id = link.id,
                                itemCount = folder.items.size,
                                previewThumbnailUrl = thumbUrl
                            )
                        }
                        outcome.isSuccess
                    }
                }.awaitAll()
            }.forEach { success -> if (success) updated++ else failed++ }

            _savedLinksTotal.value = links.size
            poolDirty = true
            _isRefreshingLinks.value = false
            UiMessenger.show(
                if (failed == 0) "Библиотека обновлена: $updated ${if (updated == 1) "ссылка" else "ссылок"}"
                else "Обновлено: $updated, не удалось: $failed"
            )
        }
    }

    /**
     * Deletes a saved link but keeps an undo path: the entity is re-inserted
     * (same id, INSERT REPLACE) if the user taps "Отменить" on the snackbar.
     */
    fun deleteSavedLinkWithUndo(link: SavedLinkEntity) {
        viewModelScope.launch {
            repository.deleteLink(link.id)
            poolDirty = true
            UiMessenger.showWithAction(
                text = "Ссылка «${link.title}» удалена",
                actionLabel = "Отменить"
            ) {
                viewModelScope.launch {
                    repository.restoreLink(link)
                    poolDirty = true
                }
            }
        }
    }

    fun toggleItemSelection(weblink: String) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(weblink)) {
            current.remove(weblink)
        } else {
            current.add(weblink)
        }
        _selectedItems.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun selectAllFiles() {
        val allFiles = activeItems().filter { !it.isFolder }
        _selectedItems.value = allFiles.map { it.weblink }.toSet()
        _isSelectionMode.value = allFiles.isNotEmpty()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun getDownloadUrl(weblink: String): String = repository.getDownloadUrl(weblink)
    fun getThumbnailUrl(weblink: String, size: ThumbnailSize = ThumbnailSize.HIGH_RES): String =
        repository.getThumbnailUrl(weblink, size)

    /** Provider-aware thumbnail: uses the embedded Yandex preview when present. */
    fun getThumbnailUrl(item: CloudItem, size: ThumbnailSize = ThumbnailSize.HIGH_RES): String =
        repository.getThumbnailUrl(item, size)

    fun getStreamingUrl(weblink: String): String = repository.getStreamingUrl(weblink)

    /**
     * Resolves a playable URL for the media viewer. Fast for Mail.ru (static
     * shard), a signed-href API call for Yandex — call from a coroutine.
     */
    suspend fun resolveMediaUrl(item: CloudItem): String =
        repository.resolveMediaUrl(item.weblink)

    /** Resolves a downloadable URL for any provider; call from a coroutine. */
    suspend fun resolveDownloadUrl(item: CloudItem): String =
        repository.resolveDirectUrl(item.weblink)

    fun rememberVideoPosition(weblink: String, positionMs: Long) {
        if (positionMs > 0) videoResumePositions[weblink] = positionMs
    }

    fun clearVideoPosition(weblink: String) {
        videoResumePositions.remove(weblink)
    }

    fun getVideoResumePosition(weblink: String): Long = videoResumePositions[weblink] ?: 0L
}

class CloudLinkViewModelFactory(
    private val repository: CloudMailRuRepository,
    private val uiPrefs: UiPrefs? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CloudLinkViewModel::class.java)) {
            return CloudLinkViewModel(repository, uiPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
