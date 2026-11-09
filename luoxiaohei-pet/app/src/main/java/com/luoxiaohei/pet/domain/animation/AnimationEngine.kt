package com.luoxiaohei.pet.domain.animation

import com.luoxiaohei.pet.domain.physics.PhysicsConstants
import com.luoxiaohei.pet.ui.animation.PetAnimationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 动画调度引擎
 *
 * 职责：
 * - 驱动呼吸/眨眼等持续动画
 * - 按权重随机调度 idle/action 动画切换
 * - 世代号机制：新动画请求到来时丢弃旧动画的异步效果
 * - 协程驱动，在 Service 生命周期内运行
 *
 * 移植自 dsh-pet 的 scheduler + ticker 逻辑
 */
class AnimationEngine(
    private val view: PetAnimationView,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    /** 世代号：每次切换动画递增，旧协程检测到世代号不匹配时自动退出 */
    @Volatile
    private var generation: Int = 0

    /** 当前调度任务 */
    private var schedulerJob: Job? = null

    /** 呼吸任务 */
    private var breathJob: Job? = null

    /** 眨眼任务 */
    private var blinkJob: Job? = null

    /** 当前播放的动画 */
    @Volatile
    var currentAnim: PetAnimationView.PetAnimation = PetAnimationView.PetAnimation.IDLE
        private set

    /** 是否正在拖拽（拖拽时暂停调度） */
    @Volatile
    var isDragging = false
        set(value) {
            field = value
            if (value) {
                view.play(PetAnimationView.PetAnimation.DRAGGING)
            }
        }

    /** 是否正在睡觉 */
    @Volatile
    var isSleeping = false

    // ---- 动画参数 ----

    /** idle 切换间隔范围（ms） */
    var idleIntervalRange = 3000L..8000L

    /** 眨眼间隔范围（ms） */
    var blinkIntervalRange = 3000L..6000L

    /** 点击回应持续时间（ms） */
    var clickDurationMs = 1500L

    /** 权重配置 */
    var weights = AnimationWeights(idle = 70, turn = 10, move = 10)

    /** 点击回应动画池 */
    var clickAnims = listOf(
        PetAnimationView.PetAnimation.HAPPY,
        PetAnimationView.PetAnimation.SHY,
        PetAnimationView.PetAnimation.ANGRY
    )

    // ---- 公共 API ----

    /**
     * 启动动画引擎
     */
    fun start() {
        if (schedulerJob?.isActive == true) return

        startBreath()
        startBlink()
        startScheduler()

        Timber.i("动画引擎启动")
    }

    /**
     * 停止动画引擎
     */
    fun stop() {
        schedulerJob?.cancel()
        breathJob?.cancel()
        blinkJob?.cancel()
        scope.cancel()
        Timber.i("动画引擎停止")
    }

    /**
     * 触发点击回应
     */
    fun onClick() {
        if (isDragging || isSleeping) return
        playTemporary(
            AnimationPicker.pick(clickAnims, currentAnim),
            durationMs = clickDurationMs
        )
    }

    /**
     * 播放临时动画，播完后回到 idle
     */
    fun playTemporary(anim: PetAnimationView.PetAnimation, durationMs: Long = 2000L) {
        val gen = ++generation
        currentAnim = anim
        view.play(anim)

        scope.launch {
            delay(durationMs)
            if (gen == generation && !isDragging && !isSleeping) {
                backToIdle(gen)
            }
        }
    }

    /**
     * 回到 idle
     */
    fun backToIdle() {
        backToIdle(++generation)
    }

    private fun backToIdle(gen: Int) {
        if (gen != generation) return
        currentAnim = PetAnimationView.PetAnimation.IDLE
        view.play(PetAnimationView.PetAnimation.IDLE)
        view.resetTransform()
    }

    // ---- 内部调度 ----

    /**
     * 呼吸动画：正弦波 ±3% 缩放
     */
    private fun startBreath() {
        breathJob = scope.launch {
            var t = 0f
            while (isActive) {
                t += 0.05f  // 每帧步进
                view.updateBreath(t * (2f * PI.toFloat()))
                delay(33L)  // ~30fps
            }
        }
    }

    /**
     * 眨眼动画：随机间隔，快速闭眼→睁眼
     */
    private fun startBlink() {
        blinkJob = scope.launch {
            while (isActive) {
                delay(Random.nextLong(blinkIntervalRange.first, blinkIntervalRange.last + 1))

                if (isDragging || isSleeping) continue

                // 闭眼 80ms
                var progress = 0f
                while (progress < 1f && isActive) {
                    progress += 0.2f
                    view.updateBlink(progress)
                    delay(16L)
                }
                delay(40L)  // 闭眼停留
                // 睁眼 80ms
                while (progress > 0f && isActive) {
                    progress -= 0.2f
                    view.updateBlink(progress.coerceAtLeast(0f))
                    delay(16L)
                }
                view.updateBlink(0f)
            }
        }
    }

    /**
     * 动画调度器：idle/action 轮换
     */
    private fun startScheduler() {
        schedulerJob = scope.launch {
            while (isActive) {
                // 等待下一次切换
                val interval = if (isSleeping) 5000L
                    else Random.nextLong(idleIntervalRange.first, idleIntervalRange.last + 1)
                delay(interval)

                if (isDragging || isSleeping) continue

                // 掷骰决定下一个动画
                val roll = Random.nextFloat()
                val kind = AnimationPicker.rollKind(roll, weights)

                val gen = ++generation
                when (kind) {
                    RollKind.IDLE -> {
                        currentAnim = PetAnimationView.PetAnimation.IDLE
                        view.play(PetAnimationView.PetAnimation.IDLE)
                        view.resetTransform()
                    }
                    RollKind.TURN -> {
                        // 翻转朝向
                        val newFacing = if (view.facing == PetAnimationView.Facing.RIGHT)
                            PetAnimationView.Facing.LEFT else PetAnimationView.Facing.RIGHT
                        currentAnim = PetAnimationView.PetAnimation.IDLE
                        view.play(PetAnimationView.PetAnimation.IDLE, newFacing)
                        Timber.d("转身 → $newFacing")
                    }
                    RollKind.MOVE -> {
                        // 走路动画（M3 接入物理移动）
                        currentAnim = PetAnimationView.PetAnimation.WALKING
                        view.play(PetAnimationView.PetAnimation.WALKING)
                        scope.launch {
                            delay(2000L)
                            if (gen == generation && !isDragging) backToIdle(gen)
                        }
                    }
                    RollKind.ACTION -> {
                        // 随机选一个点击回应动画作为 action
                        val action = AnimationPicker.pick(
                            clickAnims, PetAnimationView.PetAnimation.IDLE
                        )
                        playTemporary(action, durationMs = 2000L)
                    }
                }
            }
        }
    }
}
