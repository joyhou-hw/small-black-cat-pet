package com.luoxiaohei.pet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luoxiaohei.pet.data.db.entity.ChatHistoryEntity
import com.luoxiaohei.pet.data.db.entity.UsageDailyEntity
import com.luoxiaohei.pet.data.db.entity.UsageAppDetailEntity
import com.luoxiaohei.pet.data.db.entity.ErrorBookEntity
import com.luoxiaohei.pet.data.db.entity.GrowthStateEntity

/**
 * Room 数据库
 * M1 阶段：仅定义结构，M3 起接入实际使用
 */
@Database(
    entities = [
        ChatHistoryEntity::class,
        UsageDailyEntity::class,
        UsageAppDetailEntity::class,
        ErrorBookEntity::class,
        GrowthStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun usageDao(): UsageDao
    abstract fun errorBookDao(): ErrorBookDao
}
