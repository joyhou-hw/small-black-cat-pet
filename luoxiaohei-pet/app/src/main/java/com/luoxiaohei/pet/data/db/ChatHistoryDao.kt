package com.luoxiaohei.pet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.luoxiaohei.pet.data.db.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_history WHERE sessionId = :sid ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(sid: String, limit: Int): Flow<List<ChatHistoryEntity>>

    @Insert
    suspend fun insert(entity: ChatHistoryEntity)

    @Query("DELETE FROM chat_history WHERE sessionId = :sid")
    suspend fun deleteSession(sid: String)
}
