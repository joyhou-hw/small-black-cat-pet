package com.luoxiaohei.pet.data.config

import kotlinx.serialization.Serializable

/**
 * 全局应用配置（对应 config.json）
 */
@Serializable
data class AppConfig(
    val pet: PetConfig = PetConfig(),
    val physics: PhysicsConfig = PhysicsConfig(),
    val schedule: ScheduleConfig = ScheduleConfig(),
    val chatMemoryRounds: Int = 10,
    val studyMode: StudyModeConfig = StudyModeConfig(),
    val safety: SafetyConfig = SafetyConfig()
)

@Serializable
data class PetConfig(
    val id: String = "luoxiaohei",
    val name: String = "罗小黑",
    val size: Int = 320,
    val positionCorner: String = "bottom-right",
    val positionMarginX: Int = 16,
    val positionMarginY: Int = 100
)

@Serializable
data class PhysicsConfig(
    val gravity: Float = 1400f,
    val restitution: Float = 0.78f,
    val groundFriction: Float = 2.5f,
    val ceilingBounce: Boolean = true,
    val throwPower: Float = 0.8f,
    val hapticEnabled: Boolean = true,
    val tiltGravity: Boolean = true
)

@Serializable
data class ScheduleConfig(
    val eventIntervalSec: Int = 40,
    val idleIntervalSec: Int = 8,
    val studyAnimationIntervalSec: Int = 120,
    val whisperMinIntervalSec: Int = 120,
    val whisperMaxIntervalSec: Int = 1800
)

@Serializable
data class StudyModeConfig(
    val enabled: Boolean = false,
    val startTime: String = "19:00",
    val endTime: String = "21:00",
    val quietAnimationsOnly: Boolean = true,
    val reduceEventFrequency: Boolean = true
)

@Serializable
data class SafetyConfig(
    val educationMode: Boolean = true,
    val maxReplyLength: Int = 50,
    val maxWhisperLength: Int = 20,
    val noDirectAnswers: Boolean = true
)
