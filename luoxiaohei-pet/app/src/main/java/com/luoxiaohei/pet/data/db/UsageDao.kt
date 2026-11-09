package com.luoxiaohei.pet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luoxiaohei.pet.data.db.entity.UsageDailyEntity
import com.luoxiaohei.pet.data.db.entity.UsageAppDetailEntity

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_daily WHERE date = :date")
    suspend fun getDaily(date: String): UsageDailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(entity: UsageDailyEntity)

    @Insert
    suspend fun insertAppDetail(entity: UsageAppDetailEntity)

    @Query("SELECT category, SUM(durationSec) as total FROM usage_app_detail WHERE date = :date GROUP BY category")
    suspend fun getCategorySummary(date: String): List<CategorySummary>

    data class CategorySummary(val category: Int, val total: Int)
}
