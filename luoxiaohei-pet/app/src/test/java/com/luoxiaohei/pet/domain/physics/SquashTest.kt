package com.luoxiaohei.pet.domain.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落地挤压测试（对应 dsh-pet 的 squash.test.ts）
 */
class SquashTest {

    @Test
    fun `软速度以下不挤压`() {
        assertEquals(1.0f, Squash.landingSquash(100f), 0.01f)
        assertEquals(1.0f, Squash.landingSquash(150f), 0.01f) // SQ_SOFT_SPEED
    }

    @Test
    fun `硬速度以上最大挤压`() {
        assertEquals(PhysicsConstants.SQ_MAX_SQUASH, Squash.landingSquash(800f), 0.01f)
        assertEquals(PhysicsConstants.SQ_MAX_SQUASH, Squash.landingSquash(2000f), 0.01f)
    }

    @Test
    fun `中间速度线性插值`() {
        // 速度475（150和800的中点），结果应在 0.4~0.8 之间
        val squash = Squash.landingSquash(475f)
        assertTrue("中间速度结果应在0.4~0.8之间，实际$squash", squash in 0.4f..0.8f)
    }

    @Test
    fun `挤压曲线起点为1`() {
        assertEquals(1.0f, Squash.squashScale(0f), 0.01f)
    }

    @Test
    fun `挤压曲线终点回1`() {
        assertEquals(1.0f, Squash.squashScale(1.0f), 0.01f)
    }

    @Test
    fun `挤压曲线中段压低`() {
        // u=0.45 是下压最低点
        val low = Squash.squashScale(0.45f)
        assertTrue("下压段应<1.0，实际$low", low < 1.0f)
    }

    @Test
    fun `回弹段有过冲`() {
        // 在0.5~1.0之间找一个最大值，应>1.0
        var peak = 0f
        var u = 0.46f
        while (u <= 1.0f) {
            peak = maxOf(peak, Squash.squashScale(u))
            u += 0.01f
        }
        assertTrue("回弹段应有过冲(>1.0)，实际峰值$peak", peak > 1.0f)
    }
}
