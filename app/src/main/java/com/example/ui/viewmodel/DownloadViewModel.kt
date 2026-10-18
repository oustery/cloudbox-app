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
import kotlinx.coroutines.flow.stateIn

class DownloadViewModel(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _tabFilter = MutableStateFlow("Все") // "Все", "В процессе", "Завершено", "С ошибками"
    val tabFilter: StateFlow<String> = _tabFilter.asStateFlow()

    val downloads: StateFlow<List<DownloadTaskEntity>> = combine(
        repository.allDownloads,
        _tabFilter
    ) { list, filter ->
        when (filter) {
            "В процессе" -> list.filter { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
            "Завершено" -> list.filter { it.status == DownloadStatus.COMPLETED.name }
            "С ошибками" -> list.filter { it.status == DownloadStatus.FAILED.name }
            else -> list
        }
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
