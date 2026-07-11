package com.example.customkeyboard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    /** Pinned items always first (most recently pinned first), then unpinned by recency. */
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, timestamp DESC")
    fun observeAll(): Flow<List<ClipboardItem>>

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipboardItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardItem): Long

    @Update
    suspend fun update(item: ClipboardItem)

    @Delete
    suspend fun delete(item: ClipboardItem)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clipboard_items")
    suspend fun clearAll()

    /** Keeps unpinned history bounded (battery/storage friendly) by trimming oldest entries. */
    @Query(
        """DELETE FROM clipboard_items WHERE id IN (
            SELECT id FROM clipboard_items WHERE isPinned = 0
            ORDER BY timestamp DESC LIMIT -1 OFFSET :maxUnpinnedEntries
        )"""
    )
    suspend fun trimHistory(maxUnpinnedEntries: Int)
}
