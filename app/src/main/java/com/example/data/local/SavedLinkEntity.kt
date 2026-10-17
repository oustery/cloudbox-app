package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_links")
data class SavedLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val weblinkKey: String,
    val title: String,
    val category: String = "Все",
    val itemCount: Int = 0,
    val previewThumbnailUrl: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
