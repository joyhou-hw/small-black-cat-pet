package com.luoxiaohei.pet.domain.animation

import kotlin.random.Random

/**
 * 动画权重掷骰与池抽取（移植自 dsh-pet/shared/pickers.ts）
 *
 * 纯函数，无 Android 依赖，可独立单测。
 * 与 dsh-pet 桌面端行为严格一致。
 */

/** 掷骰结果类别 */
enum class RollKind { IDLE, TURN, MOVE, ACTION }

/** 权重配置 */
data class AnimationWeights(val idle: Int, val turn: Int, val move: Int)

/** 动画分类 */
data class Category(
    val id: String,
    val weight: Int,
    val noMirror: Boolean = false,
    val actions: List<String>
)

/** 动画池配置 */
data class AnimationPool(
    val idle: List<String>,
    val turn: List<String>,
    val clicks: List<String>,
    val drag: List<String>,
    val categories: List<Category>
)

object AnimationPicker {

    /**
     * 从池里等概率抽一个，exclude 避免连续重复
     */
    fun <T> pick(pool: List<T>, exclude: T? = null): T {
        val entries = if (exclude != null) pool.filter { it != exclude } else pool
        val src = if (entries.isNotEmpty()) entries else pool
        return src[Random.nextInt(src.size)]
    }

    /**
     * 按权重掷骰：roll ∈ [0,1) → 下一个动画类别
     *
     * (idle + turn + move) / 100 是三档占比，剩余归 action
     * fixed 为真时 turn/move 权重按 0 算
     */
    fun rollKind(roll: Float, w: AnimationWeights, fixed: Boolean = false): RollKind {
        val turn = if (fixed) 0 else w.turn
        val move = if (fixed) 0 else w.move
        val topEnd = (w.idle + turn + move) / 100f
        return when {
            roll < w.idle / 100f -> RollKind.IDLE
            roll < (w.idle + turn) / 100f -> RollKind.TURN
            roll < topEnd -> RollKind.MOVE
            else -> RollKind.ACTION
        }
    }

    /**
     * 按权重在分类池中选一个分类
     * facing=right 时排除 noMirror 分类并归一化剩余权重
     */
    fun pickWeightedCategory(categories: List<Category>, facing: String): Category? {
        val cats = categories.filter { it.actions.isNotEmpty() }
        if (cats.isEmpty()) return null
        val filtered = cats.filter { !(it.noMirror && facing == "right") }
        val eligible = if (filtered.isNotEmpty()) filtered else cats
        val totalW = eligible.sumOf { it.weight }.coerceAtLeast(1)
        var t = Random.nextFloat() * totalW
        for (c in eligible) {
            t -= c.weight
            if (t <= 0) return c
        }
        return eligible.last()
    }

    /**
     * 从分类池选一个动作；无可用分类时回退 idle 池
     */
    fun pickCategoryAction(
        categories: List<Category>,
        idlePool: List<String>,
        facing: String,
        current: String
    ): Pair<String, String> {
        val cat = pickWeightedCategory(categories, facing)
        if (cat == null) return "FALLBACK" to pick(idlePool, current)
        return cat.id to pick(cat.actions, current)
    }
}
