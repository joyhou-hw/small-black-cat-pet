package com.luoxiaohei.pet.domain.sleep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 睡眠三段接力状态机（移植自 dsh-pet/client/pet/card/sleep-machine.ts）
 *
 * 入睡(start) → 循环(loop, 无限) → 醒来(end)
 * 睡着时完全安静：不自动动画、不被打扰
 */

sealed class SleepState {
    object Awake : SleepState()
    object FallingAsleep : SleepState()
    object Sleeping : SleepState()
    object WakingUp : SleepState()
}

class SleepMachine(
    private val refuseTexts: List<String> = listOf("喵...别烦我，在睡觉...", "呼...呼...（装没听见）"),
    private val startTexts: List<String> = listOf("困了...先睡一会儿喵..."),
    private val endTexts: List<String> = listOf("唔...醒了喵。小主人什么时候来的？")
) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Awake)
    val state: StateFlow<SleepState> = _state

    private val random = kotlin.random.Random

    fun sleep() {
        if (_state.value !is SleepState.Awake) return
        _state.value = SleepState.FallingAsleep
        // M4 阶段在此播放入睡动画，完成后调用 onFallAsleepComplete()
    }

    fun onFallAsleepComplete() {
        if (_state.value is SleepState.FallingAsleep) {
            _state.value = SleepState.Sleeping
        }
    }

    fun wake() {
        when (_state.value) {
            is SleepState.Sleeping -> {
                _state.value = SleepState.WakingUp
                // M4 阶段在此播放醒来动画，完成后调用 onWakeUpComplete()
            }
            is SleepState.FallingAsleep -> {
                _state.value = SleepState.Awake
            }
            else -> {}
        }
    }

    fun onWakeUpComplete() {
        if (_state.value is SleepState.WakingUp) {
            _state.value = SleepState.Awake
        }
    }

    fun isSleeping(): Boolean = _state.value is SleepState.Sleeping

    /** 睡着时被打扰，返回拒绝台词 */
    fun onDisturb(): String {
        return if (isSleeping() || _state.value is SleepState.FallingAsleep) {
            refuseTexts[random.nextInt(refuseTexts.size)]
        } else ""
    }

    fun sleepStartText(): String = startTexts[random.nextInt(startTexts.size)]
    fun sleepEndText(): String = endTexts[random.nextInt(endTexts.size)]
}
