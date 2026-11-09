package com.luoxiaohei.pet.domain.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 落地挤压曲线（移植自 dsh-pet/shared/physics/squash.ts）
 *
 * 纯函数，无 Android 依赖，可独立单测。
 */
object Squash {

    /**
     * 落地冲击速度 → 下压幅度
     *
     * @param impactSpeed 落地时的竖直速度（px/s，取绝对值）
     * @return scaleY ∈ [SQ_MAX_SQUASH, 1.0]，值越小压得越扁
     */
    fun landingSquash(impactSpeed: Float): Float {
        val t = (abs(impactSpeed) - PhysicsConstants.SQ_SOFT_SPEED) /
            (PhysicsConstants.SQ_HARD_SPEED - PhysicsConstants.SQ_SOFT_SPEED)
        val clamped = t.coerceIn(0f, 1f)
        return (1f - clamped * (1f - PhysicsConstants.SQ_MAX_SQUASH)).coerceIn(PhysicsConstants.SQ_MAX_SQUASH, 1f)
    }

    /**
     * Q弹挤压曲线：u∈[0,1] 进度 → scaleY
     *
     * 下压段（0~0.45）ease-in 压缩
     * 回弹段（0.45~1）easeOutBack 带约4%过冲
     *
     * @param u 动画进度 [0, 1]
     * @param squash 基础挤压比例，默认 SQ_SQUASH
     * @return scaleY 值，可能略>1.0（过冲段）
     */
    fun squashScale(u: Float, squash: Float = PhysicsConstants.SQ_SQUASH): Float {
        if (u < 0.45f) {
            val p = u / 0.45f
            return 1f - (1f - squash) * p * p
        }
        val p = (u - 0.45f) / 0.55f
        // easeOutBack 过冲系数
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val f = 1f + c3 * (p - 1f).pow(3) + c1 * (p - 1f).pow(2)
        return min(1.12f, squash + (1f - squash) * max(f, 0f))
    }
}

// Float.pow 扩展（Kotlin 1.9 没有内置 Float.pow(Int)）
private fun Float.pow(n: Int): Float {
    var result = 1f
    repeat(n) { result *= this }
    return result
}
