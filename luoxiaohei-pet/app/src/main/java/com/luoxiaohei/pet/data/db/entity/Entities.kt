package com.luoxiaohei.pet.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isFiltered: Int = 0
)

@Entity(tableName = "usage_daily", primaryKeys = ["date"])
data class UsageDailyEntity(
    val date: String,
    val totalScreenMin: Int = 0,
    val studyMin: Int = 0,
    val entertainmentMin: Int = 0,
    val lateNightMin: Int = 0,
    val pickupCount: Int = 0,
    val createdAt: Long
)

@Entity(tableName = "usage_app_detail")
data class UsageAppDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val packageName: String,
    val category: Int,
    val durationSec: Int,
    val timestamp: Long
)

@Entity(tableName = "error_book")
data class ErrorBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val question: String,
    val hint: String? = null,
    val imagePath: String? = null,
    val createdAt: Long,
    val reviewed: Int = 0
)

@Entity(tableName = "growth_state", primaryKeys = ["key"])
data class GrowthStateEntity(
    val key: String,
    val value: Int,
    val updatedAt: Long
)
