package com.luoxiaohei.pet.domain.physics

/**
 * 物理常量（移植自 dsh-pet/shared/physics/constants.ts）
 * 数值与 dsh-pet 桌面端一致，仅 throwPower 从 1.0 降为 0.8（手机屏幕小）
 */
object PhysicsConstants {
    /** 拖拽弹簧刚度：越大跟手越紧 */
    const val SPRING_K = 200f

    /** 拖拽弹簧阻尼：ζ = c/(2√k) ≈ 1.06，过阻尼，不 overshoot */
    const val SPRING_C = 30f

    /** 拖拽轨迹保留窗口（ms） */
    const val TRAIL_KEEP_MS = 200L

    /** 初速估算窗口（ms） */
    const val RELEASE_WINDOW_MS = 150L

    /** 松手前停顿超过它即温柔放下 */
    const val RELEASE_STALE_MS = 150L

    /** 窗口太短视为不可估算（ms） */
    const val MIN_SPAN_MS = 20L

    /** 分段速度的最小 dt（ms） */
    const val SEG_MIN_DT_MS = 8L

    /** 低于此速度即不抛（原地放下），px/s */
    const val DEAD_ZONE_SPEED = 500f

    /** 甩出速度软上限（px/s） */
    const val MAX_THROW_SPEED = 3600f

    /** 初速大小 = 端点均值×(1-w) + 窗口峰值×w */
    const val PEAK_WEIGHT = 0.5f

    /** 参考加速度（px/s²） */
    const val ACCEL_REF = 8000f

    /** 加速度增益上限 */
    const val ACCEL_GAIN_MAX = 0.6f

    // 抛掷物理
    /** 抛掷重力（px/s²） */
    const val GRAVITY = 1400f

    /** 碰壁反弹恢复系数 */
    const val RESTITUTION = 0.78f

    /** 接触面切向摩擦（/s） */
    const val GROUND_FRICTION = 2.5f

    /** 总力度（手机屏幕小，降低到0.8） */
    const val THROW_POWER = 0.8f

    /** 接触面法向速度低于它就贴住（px/s） */
    const val REST_NORMAL = 40f

    /** 接触面切向速度低于它认为已静止（px/s） */
    const val REST_TANGENT = 15f

    /** 判静止时法向速度的容差（px/s） */
    const val REST_NORMAL_EPS = 1f

    // 挤压
    /** 挤压持续时间（ms） */
    const val SQ_DURATION_MS = 300L

    /** 挤压软速度（px/s）：低于此不压扁（手机屏幕小，从200降到150） */
    const val SQ_SOFT_SPEED = 150f

    /** 挤压硬速度（px/s）：达到此压到最大（手机屏幕小，从1200降到800） */
    const val SQ_HARD_SPEED = 800f

    /** 最大挤压比例（0.4 = 压到原高的40%） */
    const val SQ_MAX_SQUASH = 0.4f

    /** 默认挤压比例 */
    const val SQ_SQUASH = 0.7f
}
