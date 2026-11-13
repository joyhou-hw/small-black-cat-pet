package com.luoxiaohei.pet.domain.sleep

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

/**
 * 睡眠三段接力状态机（移植自 dsh-pet/client/pet/card/sleep-machine.ts）
 *
 * 入睡(start) → 循环(loop, 无限) → 醒来(end)
 * 睡着时完全安静：不自动动画、不被打扰
 *
 * M4: 增加协程驱动入睡/醒来动画过渡
 */

sealed class SleepState {
    object Awake : SleepState()
    object FallingAsleep : SleepState()      // 入睡动画播放中
    object Sleeping : SleepState()            // 睡着循环
    object WakingUp : SleepState()            // 醒来动画播放中
}

class SleepMachine(
    private val scope: CoroutineScope,
    private val refuseTexts: List<String> = listOf(
        "喵...别烦我，在睡觉...",
        "呼...呼...（装没听见）",
        "困死了...再让我睡五分钟喵..."
    ),
    private val startTexts: List<String> = listOf(
        "困了...先睡一会儿喵...",
        "罗小黑撑不住了...zzZ",
        "小主人也该休息了喵..."
    ),
    private val endTexts: List<String> = listOf(
        "唔...醒了喵。小主人什么时候来的？",
        "伸个懒腰~睡得真舒服喵",
        "啊...罗小黑做了个好梦呢~"
    )
) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Awake)
    val state: StateFlow<SleepState> = _state

    /** 入睡动画持续时间 */
    private val fallAsleepDurationMs = 1500L

    /** 醒来动画持续时间 */
    private val wakeUpDurationMs = 1000L

    /** 入睡过渡任务 */
    private var transitionJob: Job? = null

    /**
     * 触发入睡
     *
     * @param onSleepStart 入睡动画开始回调
     * @param onSleepComplete 入睡完成（进入Sleeping状态）回调
     */
    fun sleep(
        onSleepStart: (() -> Unit)? = null,
        onSleepComplete: (() -> Unit)? = null
    ) {
        if (_state.value !is SleepState.Awake) return

        _state.value = SleepState.FallingAsleep
        onSleepStart?.invoke()
        Timber.i("开始入睡")

        transitionJob = scope.launch {
            delay(fallAsleepDurationMs)
            if (isActive) {
                _state.value = SleepState.Sleeping
                onSleepComplete?.invoke()
                Timber.i("已入睡，进入睡眠循环")
            }
        }
    }

    /**
     * 触发醒来
     *
     * @param onWakeStart 醒来动画开始回调
     * @param onWakeComplete 醒来完成回调
     */
    fun wake(
        onWakeStart: (() -> Unit)? = null,
        onWakeComplete: (() -> Unit)? = null
    ) {
        when (_state.value) {
            is SleepState.Sleeping -> {
                _state.value = SleepState.WakingUp
                onWakeStart?.invoke()
                Timber.i("开始醒来")

                transitionJob = scope.launch {
                    delay(wakeUpDurationMs)
                    if (isActive) {
                        _state.value = SleepState.Awake
                        onWakeComplete?.invoke()
                        Timber.i("已醒来")
                    }
                }
            }
            is SleepState.FallingAsleep -> {
                // 入睡过程中被唤醒，直接回到清醒
                transitionJob?.cancel()
                _state.value = SleepState.Awake
                Timber.i("入睡过程被打断，回到清醒")
            }
            else -> {}
        }
    }

    /** 是否正在睡眠中（含入睡中） */
    fun isSleeping(): Boolean =
        _state.value is SleepState.Sleeping || _state.value is SleepState.FallingAsleep

    /** 是否完全睡着（Sleeping状态） */
    fun isDeepSleeping(): Boolean = _state.value is SleepState.Sleeping

    /** 睡着时被打扰，返回拒绝台词；清醒时返回空 */
    fun onDisturb(): String {
        return if (isSleeping()) {
            refuseTexts[Random.nextInt(refuseTexts.size)]
        } else ""
    }

    fun sleepStartText(): String = startTexts[Random.nextInt(startTexts.size)]
    fun sleepEndText(): String = endTexts[Random.nextInt(endTexts.size)]
}
