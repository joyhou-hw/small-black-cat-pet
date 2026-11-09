package com.luoxiaohei.pet.domain.sleep

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 睡眠状态机测试
 */
class SleepMachineTest {

    @Test
    fun `初始状态为Awake`() {
        val machine = SleepMachine()
        assertTrue(machine.state.value is SleepState.Awake)
    }

    @Test
    fun `sleep从Awake进入FallingAsleep`() {
        val machine = SleepMachine()
        machine.sleep()
        assertTrue(machine.state.value is SleepState.FallingAsleep)
    }

    @Test
    fun `sleep在非Awake状态无效`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.sleep() // 重复调用
        assertTrue(machine.state.value is SleepState.FallingAsleep)
    }

    @Test
    fun `onFallAsleepComplete进入Sleeping`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.onFallAsleepComplete()
        assertTrue(machine.isSleeping())
    }

    @Test
    fun `wake从Sleeping进入WakingUp`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.onFallAsleepComplete()
        machine.wake()
        assertTrue(machine.state.value is SleepState.WakingUp)
    }

    @Test
    fun `onWakeUpComplete回到Awake`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.onFallAsleepComplete()
        machine.wake()
        machine.onWakeUpComplete()
        assertTrue(machine.state.value is SleepState.Awake)
    }

    @Test
    fun `睡眠中被打扰返回拒绝台词`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.onFallAsleepComplete()
        val reply = machine.onDisturb()
        assertTrue("拒绝台词应非空", reply.isNotEmpty())
    }

    @Test
    fun `Awake状态被打扰返回空字符串`() {
        val machine = SleepMachine()
        val reply = machine.onDisturb()
        assertEquals("", reply)
    }
}
