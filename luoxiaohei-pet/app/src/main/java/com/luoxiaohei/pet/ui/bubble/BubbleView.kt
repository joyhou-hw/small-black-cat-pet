package com.luoxiaohei.pet.ui.bubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * 对话气泡 View
 *
 * 功能：
 * - 奶油色圆角背景 + 尾巴指向宠物
 * - 逐字显示文本（打字机效果）
 * - 显示完毕后自动淡出消失
 *
 * 悬浮窗中用 FLAG_NOT_TOUCHABLE 显示，不拦截触摸事件
 *
 * @param context Context
 */
class BubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ---- 文本状态 ----

    /** 完整文本 */
    private var fullText: String = ""

    /** 当前显示的文本（逐字增长） */
    private var displayedText: String = ""

    /** 打字速度（ms/字） */
    var charIntervalMs: Long = 80L

    /** 完整显示后停留时间（ms） */
    var displayDurationMs: Long = 4000L

    /** 淡出动画时长（ms） */
    var fadeOutMs: Long = 800L

    // ---- 画笔 ----

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF8E8")  // 奶油色
        style = Paint.Style.FILL
        setShadowLayer(8f, 2f, 4f, Color.parseColor("#33000000"))
    }

    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF8E8")
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 42f
        isFakeBoldText = false
    }

    /** 内边距 */
    private val paddingH = 32f
    private val paddingV = 20f

    /** 圆角半径 */
    private val cornerRadius = 28f

    /** 尾巴宽度/高度 */
    private val tailWidth = 24f
    private val tailHeight = 18f

    /** 当前透明度（淡出用） */
    private var alphaValue = 255

    /** 当前打字任务 */
    private var typewriterJob: Job? = null

    /** 是否正在显示 */
    var isShowing = false
        private set

    // ---- 回调 ----

    /** 气泡消失回调 */
    var onDismiss: (() -> Unit)? = null

    init {
        background = null
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ---- 公共 API ----

    /**
     * 显示对话气泡
     * @param text 要显示的文本
     */
    fun show(text: String) {
        if (text.isBlank()) return

        cancelTyping()
        fullText = text
        displayedText = ""
        alphaValue = 255
        isShowing = true
        measureAndLayout()
        invalidate()

        startTypewriter()
    }

    /**
     * 立即显示完整文本（跳过打字机效果）
     */
    fun showImmediate(text: String) {
        cancelTyping()
        fullText = text
        displayedText = text
        alphaValue = 255
        isShowing = true
        measureAndLayout()
        invalidate()

        // 直接开始倒计时
        scope.launch {
            delay(displayDurationMs)
            fadeOut()
        }
    }

    /**
     * 隐藏气泡
     */
    fun dismiss() {
        cancelTyping()
        isShowing = false
        displayedText = ""
        alphaValue = 255
        invalidate()
        onDismiss?.invoke()
    }

    // ---- 打字机效果 ----

    private fun startTypewriter() {
        typewriterJob = scope.launch {
            // 逐字显示
            for (i in fullText.indices) {
                if (!isActive) return@launch
                displayedText = fullText.substring(0, i + 1)
                measureAndLayout()
                invalidate()
                delay(charIntervalMs)
            }

            // 停留
            delay(displayDurationMs)

            // 淡出
            fadeOut()
        }
    }

    private fun fadeOut() {
        val steps = (fadeOutMs / 16L).toInt().coerceAtLeast(1)
        val stepAlpha = 255 / steps
        scope.launch {
            while (alphaValue > 0 && isActive) {
                alphaValue -= stepAlpha
                if (alphaValue < 0) alphaValue = 0
                invalidate()
                delay(16L)
            }
            isShowing = false
            displayedText = ""
            invalidate()
            onDismiss?.invoke()
            Timber.d("气泡淡出完成")
        }
    }

    private fun cancelTyping() {
        typewriterJob?.cancel()
        typewriterJob = null
    }

    // ---- 测量与布局 ----

    private var bubbleRect = RectF()

    private fun measureAndLayout() {
        if (displayedText.isEmpty()) {
            bubbleRect = RectF()
            return
        }

        // 测量文本宽度（考虑自动换行）
        val maxWidth = min(600f, resources.displayMetrics.widthPixels * 0.6f)
        val textWidth = textPaint.measureText(displayedText)
        val lineWidth = min(textWidth, maxWidth)

        // 简单单行测量（后续可扩展多行）
        val w = lineWidth + paddingH * 2
        val h = textPaint.textSize + paddingV * 2

        bubbleRect = RectF(0f, 0f, w, h)

        // 更新 View 大小
        val totalW = (w).toInt()
        val totalH = (h + tailHeight).toInt()  // 加尾巴高度
        layoutParams?.let {
            if (it.width != totalW || it.height != totalH) {
                it.width = totalW
                it.height = totalH
                requestLayout()
            }
        }
    }

    // ---- 渲染 ----

    override fun onDraw(canvas: Canvas) {
        if (!isShowing || displayedText.isEmpty()) return

        // 设置整体透明度
        bgPaint.alpha = alphaValue
        tailPaint.alpha = alphaValue
        textPaint.alpha = alphaValue

        // 画圆角矩形背景
        canvas.drawRoundRect(bubbleRect, cornerRadius, cornerRadius, bgPaint)

        // 画尾巴（指向下方的三角形）
        val cx = bubbleRect.width() / 2f
        val tailPath = Path().apply {
            moveTo(cx - tailWidth / 2, bubbleRect.bottom)
            lineTo(cx + tailWidth / 2, bubbleRect.bottom)
            lineTo(cx, bubbleRect.bottom + tailHeight)
            close()
        }
        canvas.drawPath(tailPath, tailPaint)

        // 画文本
        val baseline = paddingV + textPaint.textSize - textPaint.descent()
        canvas.drawText(displayedText, paddingH, baseline, textPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (bubbleRect.width() > 0) bubbleRect.width().toInt() else 0
        val h = if (bubbleRect.height() > 0) (bubbleRect.height() + tailHeight).toInt() else 0
        setMeasuredDimension(w, h)
    }

    /**
     * 释放资源
     */
    fun release() {
        cancelTyping()
        scope.cancel()
    }
}
