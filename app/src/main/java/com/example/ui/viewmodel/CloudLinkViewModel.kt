package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.CloudUrlParser
import com.example.data.local.SavedLinkEntity
import com.example.data.model.CloudItem
import com.example.data.repository.CloudMailRuRepository
import com.example.data.repository.FolderResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderStackEntry(
    val title: String,
    val weblink: String
)

class CloudLinkViewModel(
    private val repository: CloudMailRuRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Все")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // Currently opened media in viewer
    private val _activeMediaItem = MutableStateFlow<CloudItem?>(null)
    val activeMediaItem: StateFlow<CloudItem?> = _activeMediaItem.asStateFlow()

    init {
        // Pre-warm dispatcher shards
        viewModelScope.launch {
            repository.refreshDispatcher()
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFileTypeFilter(filter: String) {
        _fileTypeFilter.value = filter
    }

    fun setFileSearchQuery(query: String) {
        _fileSearchQuery.value = query
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
    }

    fun openMediaViewer(item: CloudItem) {
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

    private fun getMediaOnlyItems(): List<CloudItem> {
        val all = _currentFolder.value?.items ?: emptyList()
        return all.filter { it.isImage || it.isVideo }
    }

    fun loadFolder(rawUrlOrWeblink: String, resetStack: Boolean = true) {
        val weblink = CloudUrlParser.extractWeblink(rawUrlOrWeblink) ?: rawUrlOrWeblink
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

                // Auto-save to library if this is the root folder
                val firstImage = folderResult.items.firstOrNull { it.isImage }
                val thumbUrl = firstImage?.let { repository.getThumbnailUrl(it.weblink, 512) }
                repository.saveLink(
                    url = CloudUrlParser.toFullPublicUrl(folderResult.weblinkKey),
                    title = folderResult.name,
                    category = "Все",
                    itemCount = folderResult.items.size,
                    previewThumbnailUrl = thumbUrl
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
            _currentFolder.value = null
            _folderBreadcrumbs.value = emptyList()
            false
        }
    }

    fun toggleFavorite(id: Long, currentFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, !currentFav)
        }
    }

    fun deleteSavedLink(id: Long) {
        viewModelScope.launch {
            repository.deleteLink(id)
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
        val allFiles = _currentFolder.value?.items?.filter { !it.isFolder } ?: emptyList()
        _selectedItems.value = allFiles.map { it.weblink }.toSet()
        _isSelectionMode.value = allFiles.isNotEmpty()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun getDownloadUrl(weblink: String): String = repository.getDownloadUrl(weblink)
    fun getThumbnailUrl(weblink: String, size: Int = 512): String = repository.getThumbnailUrl(weblink, size)
    fun getStreamingUrl(weblink: String): String = repository.getStreamingUrl(weblink)
}

class CloudLinkViewModelFactory(
    private val repository: CloudMailRuRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CloudLinkViewModel::class.java)) {
            return CloudLinkViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
