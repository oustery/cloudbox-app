package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {

    @Query("SELECT * FROM saved_links ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllSavedLinks(): Flow<List<SavedLinkEntity>>

    @Query("SELECT * FROM saved_links WHERE weblinkKey = :weblinkKey LIMIT 1")
    suspend fun getByWeblink(weblinkKey: String): SavedLinkEntity?

    @Query("SELECT * FROM saved_links WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavedLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SavedLinkEntity): Long

    @Update
    suspend fun updateLink(link: SavedLinkEntity)

    @Delete
    suspend fun deleteLink(link: SavedLinkEntity)

    @Query("DELETE FROM saved_links WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE saved_links SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
}
