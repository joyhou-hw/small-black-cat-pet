package com.luoxiaohei.pet.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.luoxiaohei.pet.LuoxiaoheiApp
import com.luoxiaohei.pet.MainActivity
import com.luoxiaohei.pet.R
import com.luoxiaohei.pet.domain.animation.AnimationEngine
import com.luoxiaohei.pet.domain.animation.AnimationPicker
import com.luoxiaohei.pet.domain.physics.PhysicsSimulator
import com.luoxiaohei.pet.ui.animation.PetAnimationView
import com.luoxiaohei.pet.ui.bubble.BubbleView
import com.luoxiaohei.pet.ui.floatwindow.FloatWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

/**
 * 前台 Service：宠物保活核心
 *
 * M2 阶段：
 * - 创建悬浮窗显示罗小黑
 * - 整合动画引擎 + 物理模拟器 + 气泡对话
 * - 处理触摸交互（拖拽/点击/抛掷）
 *
 * M3 阶段：
 * - 双击宠物打开聊天页
 * - 整合 AI 对话（通过广播启动 ChatActivity）
 *
 * 布局：宠物 View + 气泡 View（两个独立悬浮窗）
 */
class PetForegroundService : LifecycleService() {

    companion object {
        private const val NOTIF_ID = 1001
        private const val PET_SIZE_DP = 120
        private const val PHYSICS_DT_MS = 16L  // ~60fps
        private const val DOUBLE_CLICK_TIMEOUT = 300L  // 双击间隔 ms

        /** 广播Action：打开聊天页 */
        const val ACTION_OPEN_CHAT = "com.luoxiaohei.pet.OPEN_CHAT"

        fun start(context: Context) {
            val intent = Intent(context, PetForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetForegroundService::class.java))
        }
    }

    // ---- 依赖 ----

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var floatWindowManager: FloatWindowManager
    private lateinit var petView: PetAnimationView
    private lateinit var bubbleView: BubbleView
    private lateinit var animationEngine: AnimationEngine
    private lateinit var physics: PhysicsSimulator

    /** 宠物尺寸 px */
    private val petSizePx: Int by lazy {
        (PET_SIZE_DP * resources.displayMetrics.density).toInt()
    }

    /** 罗小黑台词（M2 占位，M4 接入 AI） */
    private val idleLines = listOf(
        "喵~",
        "小主人在干嘛呀？",
        "罗小黑有点无聊了...",
        "戳戳我嘛！",
        "今天天气怎么样？",
        "小主人要好好学习哦~",
        "罗小黑肚子里装了好多鱼干！",
        "哼，才不是在等你呢",
        "窗外有蝴蝶飞过去了喵",
        "小主人今天开心吗？"
    )

    private val clickLines = listOf(
        "喵！干嘛戳我！",
        "小主人终于理我啦~",
        "嘿嘿，被发现了",
        "罗小黑在这里呀！",
        "嗯？叫我干嘛喵？"
    )

    // ---- 生命周期 ----

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        Timber.i("PetForegroundService 启动")

        initViews()
        initPhysics()
        initAnimationEngine()

        // 延迟显示（等 Service 完全启动）
        handler.postDelayed({ showPet() }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Timber.i("PetForegroundService 停止")
        animationEngine.stop()
        floatWindowManager.hidePet()
        bubbleView.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- 初始化 ----

    private fun initViews() {
        floatWindowManager = FloatWindowManager(this)

        // 宠物 View
        petView = PetAnimationView(this).apply {
            scale = 1.0f
        }

        // 气泡 View
        bubbleView = BubbleView(this).apply {
            charIntervalMs = 80L
            displayDurationMs = 4000L
            fadeOutMs = 600L
            onDismiss = {
                floatWindowManager.hideBubble()
            }
        }
    }

    private fun initPhysics() {
        physics = PhysicsSimulator(
            screenWidth = floatWindowManager.screenWidth,
            screenHeight = floatWindowManager.screenHeight,
            petWidth = petSizePx,
            petHeight = petSizePx
        )

        physics.onPosition = { x, y ->
            floatWindowManager.movePetTo(x.toInt(), y.toInt())
        }

        physics.onSquash = { sx, sy ->
            petView.updateSquash(sx, sy)
        }

        physics.onLanding = { impactSpeed ->
            Timber.d("落地冲击速度: $impactSpeed px/s")
        }

        physics.onState = { state ->
            when (state) {
                PhysicsSimulator.PhysicsState.IDLE -> {
                    animationEngine.isDragging = false
                    if (animationEngine.currentAnim == PetAnimationView.PetAnimation.FALLING ||
                        animationEngine.currentAnim == PetAnimationView.PetAnimation.DRAGGING) {
                        animationEngine.backToIdle()
                    }
                }
                PhysicsSimulator.PhysicsState.DRAGGING -> {
                    animationEngine.isDragging = true
                }
                PhysicsSimulator.PhysicsState.FLYING -> {
                    animationEngine.playTemporary(PetAnimationView.PetAnimation.FALLING, 5000L)
                }
                PhysicsSimulator.PhysicsState.SETTLING -> {
                    animationEngine.isDragging = false
                }
            }
        }
    }

    private fun initAnimationEngine() {
        animationEngine = AnimationEngine(petView, serviceScope)
    }

    // ---- 显示宠物 ----

    private fun showPet() {
        if (!floatWindowManager.hasOverlayPermission()) {
            Timber.w("无悬浮窗权限")
            return
        }

        // 设置宠物初始位置
        val initX = (floatWindowManager.screenWidth - petSizePx) / 2
        val initY = floatWindowManager.screenHeight - petSizePx - 100
        physics.setPosition(initX.toFloat(), initY.toFloat())

        // 显示悬浮窗
        floatWindowManager.showPet(petView, petSizePx, petSizePx, initX, initY)

        // 注册触摸事件
        floatWindowManager.addTouchListener { x, y, action ->
            handleTouch(x, y, action)
        }

        // 启动动画引擎
        animationEngine.start()

        // 启动物理循环
        startPhysicsLoop()

        // 启动主动说话
        startIdleSpeech()

        Timber.i("宠物已显示在屏幕上")
    }

    // ---- 触摸处理 ----

    /** 上次点击时间（用于双击判定） */
    private var lastClickTime = 0L

    private fun handleTouch(touchX: Float, touchY: Float, action: FloatWindowManager.TouchAction) {
        when (action) {
            FloatWindowManager.TouchAction.DOWN -> {
                physics.startDrag(touchX, touchY)
            }
            FloatWindowManager.TouchAction.MOVE -> {
                physics.drag(touchX, touchY)
            }
            FloatWindowManager.TouchAction.UP -> {
                physics.release()
            }
            FloatWindowManager.TouchAction.CLICK -> {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < DOUBLE_CLICK_TIMEOUT) {
                    // 双击 → 打开聊天
                    Timber.i("双击宠物，打开聊天")
                    showBubble("和小黑聊聊天吧~")
                    sendBroadcast(Intent(ACTION_OPEN_CHAT).setPackage(packageName))
                    lastClickTime = 0L
                } else {
                    // 单击 → 回应动画 + 台词
                    lastClickTime = now
                    animationEngine.onClick()
                    showBubble(AnimationPicker.pick(clickLines))
                }
            }
        }
    }

    // ---- 物理循环 ----

    private var lastFrameTime = 0L

    private fun startPhysicsLoop() {
        serviceScope.launch {
            lastFrameTime = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = now - lastFrameTime
                lastFrameTime = now

                physics.step(dt)
                delay(PHYSICS_DT_MS)
            }
        }
    }

    // ---- 主动说话 ----

    private fun startIdleSpeech() {
        serviceScope.launch {
            // 启动后 10 秒第一次说话
            delay(10000)
            if (isActive) showBubble(idleLines.random())

            // 之后随机间隔说话（15~60秒）
            while (isActive) {
                val interval = Random.nextLong(15000, 60000)
                delay(interval)
                if (isActive && !bubbleView.isShowing) {
                    showBubble(idleLines.random())
                }
            }
        }
    }

    // ---- 气泡 ----

    private fun showBubble(text: String) {
        if (!floatWindowManager.isShowing) return

        // 先移除旧气泡
        floatWindowManager.hideBubble()

        // 重新创建气泡（因为 hideBubble 会移除 View）
        bubbleView = BubbleView(this).apply {
            charIntervalMs = 80L
            displayDurationMs = 4000L
            fadeOutMs = 600L
            onDismiss = {
                floatWindowManager.hideBubble()
            }
        }

        floatWindowManager.showBubble(bubbleView, 400, 120)
        bubbleView.show(text)
    }

    // ---- 通知 ----

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LuoxiaoheiApp.CHANNEL_PET)
            .setContentTitle(getString(R.string.pet_name))
            .setContentText(getString(R.string.notification_pet_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
