package com.luoxiaohei.pet.domain.sleep

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * SleepMachine 单元测试（M4 升级版）
 *
 * 验证协程驱动的睡眠三段状态机
 */
class SleepMachineTest {

    private fun createMachine(): SleepMachine {
        return SleepMachine(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()))
    }

    @Test
    fun `初始状态为Awake`() {
        val machine = createMachine()
        assertTrue(machine.state.value is SleepState.Awake)
        assertFalse(machine.isSleeping())
    }

    @Test
    fun `sleep后进入FallingAsleep`() {
        val machine = createMachine()
        machine.sleep()
        assertTrue(machine.state.value is SleepState.FallingAsleep)
        assertTrue(machine.isSleeping())
    }

    @Test
    fun `入睡过程中wake回到Awake`() {
        val machine = createMachine()
        machine.sleep()
        assertTrue(machine.state.value is SleepState.FallingAsleep)
        machine.wake()
        assertTrue(machine.state.value is SleepState.Awake)
    }

    @Test
    fun `清醒时wake无效果`() {
        val machine = createMachine()
        machine.wake()
        assertTrue(machine.state.value is SleepState.Awake)
    }

    @Test
    fun `睡着时onDisturb返回拒绝台词`() {
        val machine = createMachine()
        machine.sleep()
        val text = machine.onDisturb()
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `清醒时onDisturb返回空`() {
        val machine = createMachine()
        val text = machine.onDisturb()
        assertEquals("", text)
    }

    @Test
    fun `sleepStartText非空`() {
        val machine = createMachine()
        assertTrue(machine.sleepStartText().isNotEmpty())
    }

    @Test
    fun `sleepEndText非空`() {
        val machine = createMachine()
        assertTrue(machine.sleepEndText().isNotEmpty())
    }
}
