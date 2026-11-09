package com.luoxiaohei.pet.domain.physics

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * 物理模拟器（移植自 dsh-pet/shared/physics/throw.ts + drag.ts）
 *
 * 职责：
 * - 拖拽弹簧跟随（过阻尼，不 overshoot）
 * - 松手抛掷（重力 + 碰壁反弹 + 地面摩擦）
 * - 落地挤压触发回调
 *
 * 纯逻辑计算，不直接操作 View，通过回调输出位置和变形
 *
 * @param screenWidth 屏幕宽 px
 * @param screenHeight 屏幕高 px
 * @param petWidth 宠物宽 px
 * @param petHeight 宠物高 px
 */
class PhysicsSimulator(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val petWidth: Int,
    private val petHeight: Int
) {
    /** 位置回调（x, y） */
    var onPosition: ((Float, Float) -> Unit)? = null

    /** 挤压回调 */
    var onSquash: ((Float, Float) -> Unit)? = null

    /** 状态回调 */
    var onState: ((PhysicsState) -> Unit)? = null

    /** 落地速度回调（用于触发挤压动画） */
    var onLanding: ((Float) -> Unit)? = null

    enum class PhysicsState {
        IDLE,       // 静止
        DRAGGING,   // 被拖拽
        FLYING,     // 抛掷飞行中
        SETTLING    // 落地稳定中
    }

    // ---- 状态 ----

    @Volatile
    var state: PhysicsState = PhysicsState.IDLE
        private set

    /** 当前位置 */
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    /** 速度 */
    private var vx = 0f
    private var vy = 0f

    // ---- 拖拽弹簧 ----

    /** 拖拽目标位置 */
    private var targetX = 0f
    private var targetY = 0f

    /** 拖拽轨迹（用于估算松手初速） */
    private val trail = ArrayDeque<TrajectoryPoint>()

    private data class TrajectoryPoint(val x: Float, val y: Float, val t: Long)

    // ---- 挤压 ----

    private var squashTimer = 0f  // 0~1 挤压动画进度
    private var squashActive = false
    private var squashImpactSpeed = 0f

    // ---- 公共 API ----

    /**
     * 设置初始位置
     */
    fun setPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
        targetX = x
        targetY = y
        onPosition?.invoke(x, y)
    }

    /**
     * 开始拖拽
     */
    fun startDrag(touchX: Float, touchY: Float) {
        state = PhysicsState.DRAGGING
        trail.clear()
        trail.addLast(TrajectoryPoint(touchX, touchY, System.currentTimeMillis()))
        onState?.invoke(state)
    }

    /**
     * 拖拽移动
     * @param touchX 触摸点 X（屏幕坐标）
     * @param touchY 触摸点 Y（屏幕坐标）
     */
    fun drag(touchX: Float, touchY: Float) {
        if (state != PhysicsState.DRAGGING) return

        targetX = touchX - petWidth / 2f
        targetY = touchY - petHeight / 2f

        // 记录轨迹
        trail.addLast(TrajectoryPoint(targetX, targetY, System.currentTimeMillis()))

        // 清理过期轨迹
        val now = System.currentTimeMillis()
        while (trail.isNotEmpty() && now - trail.first().t > PhysicsConstants.RELEASE_WINDOW_MS) {
            trail.removeFirst()
        }
    }

    /**
     * 松手：估算初速并切换到抛掷/放下
     */
    fun release() {
        if (state != PhysicsState.DRAGGING) return

        val (initVx, initVy) = estimateReleaseVelocity()

        if (abs(initVx) < PhysicsConstants.DEAD_ZONE_SPEED && abs(initVy) < PhysicsConstants.DEAD_ZONE_SPEED) {
            // 速度太低，原地放下
            state = PhysicsState.SETTLING
            vx = 0f
            vy = 0f
        } else {
            // 抛掷
            state = PhysicsState.FLYING
            vx = initVx * PhysicsConstants.THROW_POWER
            vy = initVy * PhysicsConstants.THROW_POWER

            // 限速
            val speed = kotlin.math.sqrt(vx * vx + vy * vy)
            if (speed > PhysicsConstants.MAX_THROW_SPEED) {
                val scale = PhysicsConstants.MAX_THROW_SPEED / speed
                vx *= scale
                vy *= scale
            }
        }

        trail.clear()
        onState?.invoke(state)
    }

    /**
     * 立即停止（用于隐藏/切换场景）
     */
    fun stop() {
        state = PhysicsState.IDLE
        vx = 0f
        vy = 0f
        squashActive = false
        onState?.invoke(state)
    }

    /**
     * 物理步进（每帧调用）
     * @param dtMs 时间步长 ms
     */
    fun step(dtMs: Long) {
        val dt = dtMs / 1000f  // 转秒

        when (state) {
            PhysicsState.DRAGGING -> stepDrag(dt)
            PhysicsState.FLYING -> stepFly(dt)
            PhysicsState.SETTLING -> stepSettle(dt)
            PhysicsState.IDLE -> {
                // 挤压动画继续
                if (squashActive) stepSquash(dt)
            }
        }
    }

    // ---- 拖拽弹簧 ----

    private fun stepDrag(dt: Float) {
        // 过阻尼弹簧：F = -k*(x-target) - c*v
        val dx = targetX - x
        val dy = targetY - y
        val fx = PhysicsConstants.SPRING_K * dx - PhysicsConstants.SPRING_C * vx
        val fy = PhysicsConstants.SPRING_K * dy - PhysicsConstants.SPRING_C * vy

        // 简化积分（半隐式 Euler）
        vx += fx * dt
        vy += fy * dt
        x += vx * dt
        y += vy * dt

        onPosition?.invoke(x, y)
    }

    // ---- 抛掷飞行 ----

    private fun stepFly(dt: Float) {
        // 重力
        vy += PhysicsConstants.GRAVITY * dt

        // 位置更新
        x += vx * dt
        y += vy * dt

        // 碰壁反弹
        val minX = -petWidth / 2f
        val maxX = screenWidth - petWidth / 2f
        val minY = 0f
        val maxY = screenHeight - petHeight / 2f

        if (x < minX) {
            x = minX
            vx = -vx * PhysicsConstants.RESTITUTION
        } else if (x > maxX) {
            x = maxX
            vx = -vx * PhysicsConstants.RESTITUTION
        }

        // 地面碰撞
        if (y >= maxY) {
            y = maxY

            // 检查是否需要触发挤压
            val impactSpeed = abs(vy)
            if (impactSpeed > PhysicsConstants.SQ_SOFT_SPEED) {
                squashActive = true
                squashTimer = 0f
                squashImpactSpeed = impactSpeed
                onLanding?.invoke(impactSpeed)
            }

            // 反弹或停止
            if (abs(vy) > PhysicsConstants.REST_NORMAL) {
                vy = -vy * PhysicsConstants.RESTITUTION
            } else {
                vy = 0f
            }

            // 地面摩擦
            vx *= (1f - PhysicsConstants.GROUND_FRICTION * dt)
            if (abs(vx) < PhysicsConstants.REST_TANGENT) {
                vx = 0f
            }

            // 完全静止
            if (vx == 0f && vy == 0f) {
                state = PhysicsState.IDLE
                onState?.invoke(state)
            }
        }

        onPosition?.invoke(x, y)
    }

    // ---- 落地稳定 ----

    private fun stepSettle(dt: Float) {
        // 弹簧回弹到目标
        val targetY = screenHeight - petHeight / 2f
        val dy = targetY - y
        val fy = PhysicsConstants.SPRING_K * dy - PhysicsConstants.SPRING_C * vy

        vy += fy * dt
        y += vy * dt

        if (abs(dy) < 1f && abs(vy) < 1f) {
            y = targetY
            vy = 0f
            state = PhysicsState.IDLE
            onState?.invoke(state)
        }

        onPosition?.invoke(x, y)
    }

    // ---- 挤压动画 ----

    private fun stepSquash(dt: Float) {
        squashTimer += dt * 1000f / PhysicsConstants.SQ_DURATION_MS

        if (squashTimer >= 1f) {
            squashActive = false
            onSquash?.invoke(1f, 1f)
            return
        }

        val scaleY = Squash.squashScale(squashTimer)
        // 面积守恒：scaleX ≈ 1/scaleY
        val scaleX = 1f / scaleY
        onSquash?.invoke(scaleX, scaleY)
    }

    // ---- 松手初速估算 ----

    private fun estimateReleaseVelocity(): Pair<Float, Float> {
        if (trail.size < 2) return 0f to 0f

        val now = System.currentTimeMillis()
        val window = trail.filter { now - it.t <= PhysicsConstants.RELEASE_WINDOW_MS }
        if (window.size < 2) return 0f to 0f

        // 检查是否静止太久
        val last = window.last()
        if (now - last.t > PhysicsConstants.RELEASE_STALE_MS) return 0f to 0f

        val first = window.first()
        val lastPoint = window.last()

        val dt = (lastPoint.t - first.t) / 1000f
        if (dt < PhysicsConstants.MIN_SPAN_MS / 1000f) return 0f to 0f

        // 分段速度均值
        var totalVx = 0f
        var totalVy = 0f
        var count = 0
        var maxSpeed = 0f

        for (i in 1 until window.size) {
            val prev = window[i - 1]
            val curr = window[i]
            val segDt = (curr.t - prev.t) / 1000f
            if (segDt < PhysicsConstants.SEG_MIN_DT_MS / 1000f) continue

            val segVx = (curr.x - prev.x) / segDt
            val segVy = (curr.y - prev.y) / segDt
            totalVx += segVx
            totalVy += segVy
            count++

            val speed = kotlin.math.sqrt(segVx * segVx + segVy * segVy)
            if (speed > maxSpeed) maxSpeed = speed
        }

        if (count == 0) return 0f to 0f

        // 均值 × (1-w) + 峰值 × w
        val avgVx = totalVx / count
        val avgVy = totalVy / count

        val peakVx = if (maxSpeed > 0) avgVx / kotlin.math.sqrt(avgVx * avgVx + avgVy * avgVy) * maxSpeed else 0f
        val peakVy = if (maxSpeed > 0) avgVy / kotlin.math.sqrt(avgVx * avgVx + avgVy * avgVy) * maxSpeed else 0f

        val w = PhysicsConstants.PEAK_WEIGHT
        val finalVx = avgVx * (1 - w) + peakVx * w
        val finalVy = avgVy * (1 - w) + peakVy * w

        return finalVx to finalVy
    }
}
