package com.luoxiaohei.pet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.luoxiaohei.pet.data.db.entity.ErrorBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorBookDao {
    @Query("SELECT * FROM error_book ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ErrorBookEntity>>

    @Insert
    suspend fun insert(entity: ErrorBookEntity)

    @Query("UPDATE error_book SET reviewed = 1 WHERE id = :id")
    suspend fun markReviewed(id: Long)
}
