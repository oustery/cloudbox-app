package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.CloudItem
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Sort options of the download manager list. */
enum class DownloadSortOrder(val title: String) {
    DATE_NEWEST("Сначала новые"),
    DATE_OLDEST("Сначала старые"),
    NAME_AZ("Имя: А–Я"),
    NAME_ZA("Имя: Я–А"),
    SIZE_LARGEST("Сначала большие"),
    SIZE_SMALLEST("Сначала маленькие")
}

class DownloadViewModel(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _tabFilter = MutableStateFlow("Все") // "Все", "В процессе", "Завершено", "С ошибками"
    val tabFilter: StateFlow<String> = _tabFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(DownloadSortOrder.DATE_NEWEST)
    val sortOrder: StateFlow<DownloadSortOrder> = _sortOrder.asStateFlow()

    /** Total number of downloads regardless of filters (for "Показано X из Y"). */
    val totalCount: StateFlow<Int> = repository.allDownloads
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val downloads: StateFlow<List<DownloadTaskEntity>> = combine(
        repository.allDownloads,
        _tabFilter,
        _searchQuery,
        _sortOrder
    ) { list, filter, query, order ->
        val byTab = when (filter) {
            "В процессе" -> list.filter { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
            "Завершено" -> list.filter { it.status == DownloadStatus.COMPLETED.name }
            "С ошибками" -> list.filter { it.status == DownloadStatus.FAILED.name }
            else -> list
        }
        val byQuery = if (query.isBlank()) byTab else byTab.filter {
            it.fileName.contains(query, ignoreCase = true)
        }
        byQuery.sortedWith(
            when (order) {
                DownloadSortOrder.DATE_NEWEST -> compareByDescending { it.createdAt }
                DownloadSortOrder.DATE_OLDEST -> compareBy { it.createdAt }
                DownloadSortOrder.NAME_AZ -> compareBy { it.fileName.lowercase() }
                DownloadSortOrder.NAME_ZA -> compareByDescending { it.fileName.lowercase() }
                DownloadSortOrder.SIZE_LARGEST -> compareBy<DownloadTaskEntity> { it.fileSize <= 0L }
                    .thenByDescending { it.fileSize }
                DownloadSortOrder.SIZE_SMALLEST -> compareBy<DownloadTaskEntity> { it.fileSize <= 0L }
                    .thenBy { it.fileSize }
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeCount: StateFlow<Int> = repository.activeDownloads
        .combine(MutableStateFlow(Unit)) { list, _ -> list.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun setFilter(filter: String) {
        _tabFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: DownloadSortOrder) {
        _sortOrder.value = order
    }

    fun downloadItem(item: CloudItem) {
        repository.enqueueDownload(item)
    }

    fun downloadBatch(items: List<CloudItem>) {
        repository.enqueueBatch(items)
    }

    fun pauseDownload(id: String) {
        repository.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        repository.resumeDownload(id)
    }

    fun cancelDownload(id: String) {
        repository.cancelDownload(id)
    }

    fun clearCompleted() {
        repository.clearCompleted()
    }
}

class DownloadViewModelFactory(
    private val repository: DownloadRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadViewModel::class.java)) {
            return DownloadViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
