package com.luoxiaohei.pet.ui.floatwindow

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 悬浮窗管理器（移植自 dsh-pet 的 floating-layer 逻辑，适配 Android WindowManager）
 *
 * 职责：
 * - 创建/销毁 TYPE_APPLICATION_OVERLAY 悬浮窗
 * - 管理宠物 View 和气泡 View 的位置
 * - 处理触摸事件分发：拖动 vs 点击 vs 透明穿透
 * - 屏幕尺寸适配与边界约束
 *
 * P30 适配：Android 9 (API 28) 使用 TYPE_APPLICATION_OVERLAY
 */
class FloatWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** 屏幕宽高（px） */
    val screenWidth: Int
    val screenHeight: Int

    init {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    /** 宠物主 View */
    private var petView: View? = null
    private var petParams: LayoutParams? = null

    /** 气泡 View */
    private var bubbleView: View? = null
    private var bubbleParams: LayoutParams? = null

    /** 当前宠物位置 */
    var petX: Int = 0
        private set
    var petY: Int = 0
        private set

    /** 宠物尺寸 */
    var petWidth: Int = 0
        private set
    var petHeight: Int = 0
        private set

    /** 触摸事件监听器列表 */
    private val touchListeners = CopyOnWriteArrayList<(Float, Float, TouchAction) -> Unit>()

    enum class TouchAction { DOWN, MOVE, UP, CLICK }

    /** 是否已显示 */
    var isShowing = false
        private set

    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context)
        else true

    /**
     * 显示宠物悬浮窗
     * @param view 宠物渲染 View
     * @param width 宠物宽度 px
     * @param height 宠物高度 px
     * @param x 初始 X（默认屏幕底部中央）
     * @param y 初始 Y
     */
    fun showPet(view: View, width: Int, height: Int, x: Int = -1, y: Int = -1) {
        if (!hasOverlayPermission()) {
            Timber.w("无悬浮窗权限，无法显示宠物")
            return
        }
        if (petView != null) {
            Timber.w("宠物悬浮窗已存在")
            return
        }

        petWidth = width
        petHeight = height
        petX = if (x >= 0) x else (screenWidth - width) / 2
        petY = if (y >= 0) y else screenHeight - height - 100

        val params = LayoutParams().apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED
            gravity = Gravity.TOP or Gravity.START
            this.width = width
            this.height = height
            this.x = petX
            this.y = petY
        }
        petParams = params
        petView = view

        // 绑定触摸处理
        view.setOnTouchListener { _, event -> handlePetTouch(event); true }

        try {
            windowManager.addView(view, params)
            isShowing = true
            Timber.i("宠物悬浮窗已显示: pos=($petX,$petY) size=${width}x${height}")
        } catch (e: Exception) {
            Timber.e(e, "显示宠物悬浮窗失败")
        }
    }

    /**
     * 显示气泡悬浮窗（在宠物头顶上方）
     */
    fun showBubble(view: View, width: Int, height: Int) {
        if (!hasOverlayPermission()) return

        // 先移除旧气泡
        hideBubble()

        val params = LayoutParams().apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    LayoutParams.FLAG_NOT_TOUCHABLE
            gravity = Gravity.TOP or Gravity.START
            this.width = width
            this.height = height
            // 气泡在宠物头顶上方
            x = petX + (petWidth - width) / 2
            y = petY - height - 10
        }
        bubbleParams = params
        bubbleView = view

        try {
            windowManager.addView(view, params)
            Timber.d("气泡已显示")
        } catch (e: Exception) {
            Timber.e(e, "显示气泡失败")
        }
    }

    /**
     * 更新气泡位置（跟随宠物移动）
     */
    fun updateBubblePosition() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        params.x = petX + (petWidth - params.width) / 2
        params.y = petY - params.height - 10
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Timber.w(e, "更新气泡位置失败")
        }
    }

    /**
     * 移动宠物到指定位置（带边界约束）
     */
    fun movePetTo(x: Int, y: Int) {
        val params = petParams ?: return
        val view = petView ?: return

        // 边界约束：允许半个身体超出屏幕边缘
        petX = x.coerceIn(-petWidth / 2, screenWidth - petWidth / 2)
        petY = y.coerceIn(0, screenHeight - petHeight / 2)

        params.x = petX
        params.y = petY
        try {
            windowManager.updateViewLayout(view, params)
            updateBubblePosition()
        } catch (e: Exception) {
            Timber.w(e, "移动宠物失败")
        }
    }

    /**
     * 隐藏宠物
     */
    fun hidePet() {
        petView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Timber.w(e, "移除宠物悬浮窗失败")
            }
        }
        petView = null
        petParams = null
        isShowing = false
        hideBubble()
    }

    /**
     * 隐藏气泡
     */
    fun hideBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Timber.w(e, "移除气泡失败")
            }
        }
        bubbleView = null
        bubbleParams = null
    }

    /**
     * 注册触摸事件回调
     */
    fun addTouchListener(listener: (Float, Float, TouchAction) -> Unit) {
        touchListeners.add(listener)
    }

    fun removeTouchListener(listener: (Float, Float, TouchAction) -> Unit) {
        touchListeners.remove(listener)
    }

    // ---- 触摸事件处理 ----

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var hasMoved = false

    /** 点击判定阈值 */
    private companion object {
        const val CLICK_THRESHOLD = 20f  // dp→px 在下方换算
        const val CLICK_TIME_MS = 200L
    }

    private val clickThresholdPx: Float by lazy {
        val density = context.resources.displayMetrics.density
        CLICK_THRESHOLD * density
    }

    private fun handlePetTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                downTime = System.currentTimeMillis()
                hasMoved = false
                notifyTouch(downX, downY, TouchAction.DOWN)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                val totalDx = event.rawX - downX
                val totalDy = event.rawY - downY

                if (Math.abs(totalDx) > clickThresholdPx || Math.abs(totalDy) > clickThresholdPx) {
                    hasMoved = true
                }

                if (hasMoved) {
                    // 拖动宠物
                    val newX = (petX + dx).toInt()
                    val newY = (petY + dy).toInt()
                    movePetTo(newX, newY)
                }

                lastX = event.rawX
                lastY = event.rawY
                notifyTouch(event.rawX, event.rawY, TouchAction.MOVE)
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTime
                if (!hasMoved && elapsed < CLICK_TIME_MS * 3) {
                    // 点击事件
                    notifyTouch(event.rawX, event.rawY, TouchAction.CLICK)
                }
                notifyTouch(event.rawX, event.rawY, TouchAction.UP)
            }
        }
    }

    private fun notifyTouch(x: Float, y: Float, action: TouchAction) {
        touchListeners.forEach { it.invoke(x, y, action) }
    }
}
