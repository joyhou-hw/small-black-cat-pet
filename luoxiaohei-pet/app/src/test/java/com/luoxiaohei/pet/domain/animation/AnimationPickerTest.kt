package com.luoxiaohei.pet.domain.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 动画选择测试（对应 dsh-pet 的 pickers + next-animation 测试）
 */
class AnimationPickerTest {

    private val pool = AnimationPool(
        idle = listOf("sleep", "lick_paw", "daze"),
        turn = listOf("turn_left", "turn_right"),
        clicks = listOf("happy", "shy", "angry"),
        drag = listOf("grabbed"),
        categories = listOf(
            Category("play", 40, actions = listOf("chase_butterfly", "yarn_ball")),
            Category("rest", 60, actions = listOf("yawn", "stretch", "shake"))
        )
    )
    private val weights = AnimationWeights(idle = 30, turn = 10, move = 10)

    @Test
    fun `权重30percent时idle概率约30percent`() {
        val count = (0 until 10000).count {
            AnimationPicker.rollKind(Random.nextFloat(), weights) == RollKind.IDLE
        }
        assertTrue("idle概率应约30%，实际$count/10000", count in 2700..3300)
    }

    @Test
    fun `权重10percent时turn概率约10percent`() {
        val count = (0 until 10000).count {
            AnimationPicker.rollKind(Random.nextFloat(), weights) == RollKind.TURN
        }
        assertTrue("turn概率应约10%，实际$count/10000", count in 700..1300)
    }

    @Test
    fun `fixed模式不返回turn和move`() {
        repeat(100) {
            val kind = AnimationPicker.rollKind(Random.nextFloat(), weights, fixed = true)
            assertTrue("fixed模式不应返回TURN或MOVE，实际$kind",
                kind == RollKind.IDLE || kind == RollKind.ACTION)
        }
    }

    @Test
    fun `pick不连续重复`() {
        val first = AnimationPicker.pick(pool.idle, null)
        val second = AnimationPicker.pick(pool.idle, first)
        assertNotEquals("连续抽取不应重复", first, second)
    }

    @Test
    fun `单元素池排除后退回原池`() {
        val singlePool = listOf("only")
        val result = AnimationPicker.pick(singlePool, "only")
        assertEquals("单元素池排除后应退回原值", "only", result)
    }

    @Test
    fun `pickWeightedCategory返回有效分类`() {
        val cat = AnimationPicker.pickWeightedCategory(pool.categories, "left")
        assertTrue("应返回有效分类", cat != null)
        assertTrue("分类应在play或rest中", cat!!.id in listOf("play", "rest"))
    }

    @Test
    fun `pickCategoryAction返回有效动作`() {
        val (id, name) = AnimationPicker.pickCategoryAction(
            pool.categories, pool.idle, "left", "sleep"
        )
        assertTrue("分类id应有效", id in listOf("play", "rest", "FALLBACK"))
        assertTrue("动作名应非空", name.isNotEmpty())
    }
}
