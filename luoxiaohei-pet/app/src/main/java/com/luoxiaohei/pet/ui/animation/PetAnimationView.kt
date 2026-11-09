package com.luoxiaohei.pet.ui.animation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.content.ContextCompat
import com.luoxiaohei.pet.R
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 宠物动画渲染 View（M2 阶段使用 VectorDrawable + 属性动画）
 *
 * M2 策略：
 * - 用 VectorDrawable 占位（后续替换为 WebP 逐帧）
 * - 支持呼吸缩放、眨眼、挤压、镜像翻转
 * - 透明背景，Canvas 合成
 *
 * 后续 M3 替换为 SurfaceView + WebP 帧渲染管线
 *
 * @param context Context
 */
class PetAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- 渲染状态 ----

    /** 基础猫脸 Drawable */
    private val faceDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_luoxiaohei_face)

    /** 当前动画状态 */
    var currentAnimation: PetAnimation = PetAnimation.IDLE
        private set

    /** 朝向：left/right */
    var facing: Facing = Facing.RIGHT
        private set

    /** 缩放比例（用户设置 0.5~2.0） */
    var scale: Float = 1.0f
        set(value) { field = value.coerceIn(0.3f, 2.0f); invalidate() }

    // ---- 动画参数 ----

    /** 呼吸缩放幅度 */
    private var breathScale = 1.0f
    private var breathPhase = 0f

    /** 眨眼状态 */
    private var blinkProgress = 0f  // 0=open, 1=closed

    /** 挤压参数（落地时） */
    private var squashX = 1.0f
    private var squashY = 1.0f

    /** 拖拽倾斜 */
    private var tiltAngle = 0f

    /** 移动偏移（走路时轻微上下浮动） */
    private var bounceOffset = 0f

    /** 插值器 */
    private val interpolator: Interpolator = AccelerateDecelerateInterpolator()

    // ---- 帧回调 ----

    private val frameListeners = CopyOnWriteArrayList<(PetAnimation) -> Unit>()

    // ---- 画笔 ----

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 0
    }

    // ---- 动画状态机 ----

    enum class PetAnimation {
        IDLE,       // 待机（呼吸+眨眼）
        HAPPY,      // 开心（点击回应）
        SHY,        // 害羞
        ANGRY,      // 傲娇生气
        DRAGGING,   // 被拖拽
        FALLING,    // 下落
        WALKING,    // 移动中
        SLEEPING    // 睡觉
    }

    enum class Facing { LEFT, RIGHT }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        background = null  // 透明背景
    }

    // ---- 公共 API ----

    /**
     * 播放指定动画
     */
    fun play(anim: PetAnimation, facing: Facing = this.facing) {
        this.currentAnimation = anim
        this.facing = facing
        Timber.d("播放动画: $anim, 朝向: $facing")
        invalidate()
    }

    /**
     * 更新呼吸状态（每帧调用）
     * @param phase 呼吸相位 [0, 2π)
     */
    fun updateBreath(phase: Float) {
        breathPhase = phase
        breathScale = 1.0f + 0.03f * kotlin.math.sin(phase.toDouble()).toFloat()  // ±3% 缩放
        invalidate()
    }

    /**
     * 更新眨眼
     * @param progress 0=完全睁开, 1=完全闭眼
     */
    fun updateBlink(progress: Float) {
        blinkProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * 更新挤压（落地时）
     * @param sx X 方向缩放
     * @param sy Y 方向缩放
     */
    fun updateSquash(sx: Float, sy: Float) {
        squashX = sx
        squashY = sy
        invalidate()
    }

    /**
     * 更新拖拽倾斜
     * @param angle 倾斜角度（度）
     */
    fun updateTilt(angle: Float) {
        tiltAngle = angle
        invalidate()
    }

    /**
     * 更新移动浮动
     * @param offset Y方向偏移 px
     */
    fun updateBounce(offset: Float) {
        bounceOffset = offset
        invalidate()
    }

    /**
     * 重置所有变形
     */
    fun resetTransform() {
        squashX = 1.0f
        squashY = 1.0f
        tiltAngle = 0f
        bounceOffset = 0f
        blinkProgress = 0f
        invalidate()
    }

    /**
     * 注册动画帧回调
     */
    fun addFrameListener(listener: (PetAnimation) -> Unit) {
        frameListeners.add(listener)
    }

    // ---- 渲染 ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val drawable = faceDrawable ?: return

        // 计算绘制区域（居中）
        val drawSize = (minOf(width, height) * scale * 0.85f).toInt()
        val left = (width - drawSize) / 2
        val top = (height - drawSize) / 2 + bounceOffset.toInt()
        val right = left + drawSize
        val bottom = top + drawSize

        canvas.save()

        // 应用变换：以中心点为锚
        val cx = width / 2f
        val cy = height / 2f
        canvas.translate(cx, cy + bounceOffset)

        // 镜像翻转
        if (facing == Facing.LEFT) {
            canvas.scale(-1f, 1f)
        }

        // 倾斜
        if (tiltAngle != 0f) {
            canvas.rotate(tiltAngle)
        }

        // 挤压+呼吸
        val sx = squashX * breathScale
        val sy = squashY * breathScale
        canvas.scale(sx, sy)

        canvas.translate(-cx, -cy)

        // 设置 Drawable 边界并绘制
        drawable.setBounds(left, top, right, bottom)
        drawable.draw(canvas)

        // 眨眼遮罩（简单实现：在眼睛区域画黑色横条）
        if (blinkProgress > 0.1f) {
            drawBlinkOverlay(canvas, left, top, drawSize)
        }

        // 睡觉时画 Zzz（简单文字）
        if (currentAnimation == PetAnimation.SLEEPING) {
            drawSleepZzz(canvas, right, top)
        }

        canvas.restore()

        // 通知帧回调
        frameListeners.forEach { it.invoke(currentAnimation) }
    }

    /**
     * 眨眼遮罩
     */
    private fun drawBlinkOverlay(canvas: Canvas, left: Int, top: Int, size: Int) {
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            style = Paint.Style.FILL
        }
        val eyeW = size * 0.12f
        val eyeH = size * 0.15f * blinkProgress
        val eyeY = top + size * 0.45f

        // 左眼
        canvas.drawRoundRect(
            left + size * 0.30f - eyeW / 2, eyeY - eyeH / 2,
            left + size * 0.30f + eyeW / 2, eyeY + eyeH / 2,
            eyeW / 3, eyeW / 3, eyePaint
        )
        // 右眼
        canvas.drawRoundRect(
            left + size * 0.58f - eyeW / 2, eyeY - eyeH / 2,
            left + size * 0.58f + eyeW / 2, eyeY + eyeH / 2,
            eyeW / 3, eyeW / 3, eyePaint
        )
    }

    /**
     * 睡觉 Zzz
     */
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 24f
        alpha = 180
    }

    private var zzzPhase = 0f

    fun updateZzzPhase(phase: Float) {
        zzzPhase = phase
        invalidate()
    }

    private fun drawSleepZzz(canvas: Canvas, x: Int, y: Int) {
        val offsets = floatArrayOf(0f, 0.33f, 0.66f)
        val sizes = floatArrayOf(16f, 20f, 26f)
        val alphas = intArrayOf(100, 160, 220)
        for (i in offsets.indices) {
            val p = (zzzPhase + offsets[i]) % 1f
            val alpha = (alphas[i] * (1f - p)).toInt().coerceIn(0, 255)
            zzzPaint.alpha = alpha
            zzzPaint.textSize = sizes[i]
            canvas.drawText(
                "Z",
                x + 10f + p * 20f,
                y - p * 30f,
                zzzPaint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (160 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(defaultSize, defaultSize)
    }
}
