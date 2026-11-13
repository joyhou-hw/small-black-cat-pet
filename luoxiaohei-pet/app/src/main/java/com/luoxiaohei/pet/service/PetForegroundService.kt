package com.luoxiaohei.pet.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.luoxiaohei.pet.LuoxiaoheiApp
import com.luoxiaohei.pet.MainActivity
import com.luoxiaohei.pet.R
import com.luoxiaohei.pet.domain.animation.AnimationEngine
import com.luoxiaohei.pet.domain.animation.AnimationPicker
import com.luoxiaohei.pet.domain.bubble.BubbleManager
import com.luoxiaohei.pet.domain.bubble.PhraseManager
import com.luoxiaohei.pet.domain.physics.PhysicsSimulator
import com.luoxiaohei.pet.domain.sleep.SleepMachine
import com.luoxiaohei.pet.domain.sleep.SleepState
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
 * M2: 悬浮窗 + 动画 + 物理 + 气泡
 * M3: 双击触发聊天
 * M4: BubbleManager队列 + PhraseManager台词库 + SleepMachine睡眠 + 碎碎念
 */
class PetForegroundService : LifecycleService() {

    companion object {
        private const val NOTIF_ID = 1001
        private const val PET_SIZE_DP = 120
        private const val PHYSICS_DT_MS = 16L  // ~60fps
        private const val DOUBLE_CLICK_TIMEOUT = 300L

        const val ACTION_OPEN_CHAT = "com.luoxiaohei.pet.OPEN_CHAT"
        const val ACTION_SLEEP = "com.luoxiaohei.pet.SLEEP"
        const val ACTION_WAKE = "com.luoxiaohei.pet.WAKE"

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

    // M4 新增
    private lateinit var bubbleManager: BubbleManager
    private lateinit var phraseManager: PhraseManager
    private lateinit var sleepMachine: SleepMachine

    /** 宠物尺寸 px */
    private val petSizePx: Int by lazy {
        (PET_SIZE_DP * resources.displayMetrics.density).toInt()
    }

    // ---- 生命周期 ----

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        Timber.i("PetForegroundService 启动 (M4)")

        initViews()
        initPhysics()
        initAnimationEngine()
        initBubbleAndPhrases()
        initSleepMachine()

        handler.postDelayed({ showPet() }, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 处理Action命令
        when (intent?.action) {
            ACTION_SLEEP -> triggerSleep()
            ACTION_WAKE -> triggerWake()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Timber.i("PetForegroundService 停止")
        animationEngine.stop()
        bubbleManager.clear()
        floatWindowManager.hidePet()
        bubbleView.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- 初始化 ----

    private fun initViews() {
        floatWindowManager = FloatWindowManager(this)

        petView = PetAnimationView(this).apply { scale = 1.0f }

        bubbleView = BubbleView(this).apply {
            charIntervalMs = 80L
            displayDurationMs = 4000L
            fadeOutMs = 600L
            onDismiss = { floatWindowManager.hideBubble() }
        }
    }

    private fun initPhysics() {
        physics = PhysicsSimulator(
            screenWidth = floatWindowManager.screenWidth,
            screenHeight = floatWindowManager.screenHeight,
            petWidth = petSizePx,
            petHeight = petSizePx
        )

        physics.onPosition = { x, y -> floatWindowManager.movePetTo(x.toInt(), y.toInt()) }
        physics.onSquash = { sx, sy -> petView.updateSquash(sx, sy) }
        physics.onLanding = { impactSpeed -> Timber.d("落地冲击速度: $impactSpeed px/s") }
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

    private fun initBubbleAndPhrases() {
        phraseManager = PhraseManager(this)

        bubbleManager = BubbleManager(serviceScope, minIntervalMs = 2000L)
        bubbleManager.onShow = { text -> showBubble(text) }
        bubbleManager.onHide = { floatWindowManager.hideBubble() }
    }

    private fun initSleepMachine() {
        sleepMachine = SleepMachine(serviceScope)

        // 监听睡眠状态变化
        serviceScope.launch {
            sleepMachine.state.collect { state ->
                when (state) {
                    is SleepState.FallingAsleep -> {
                        animationEngine.isSleeping = true
                        petView.play(PetAnimationView.PetAnimation.SLEEPING)
                        bubbleManager.enqueue(phraseManager.sleepStart(), BubbleManager.Priority.NORMAL)
                    }
                    is SleepState.Sleeping -> {
                        // 深度睡眠，动画引擎自动降低频率
                    }
                    is SleepState.WakingUp -> {
                        animationEngine.isSleeping = false
                        petView.play(PetAnimationView.PetAnimation.IDLE)
                    }
                    is SleepState.Awake -> {
                        animationEngine.isSleeping = false
                        animationEngine.backToIdle()
                        bubbleManager.enqueue(phraseManager.sleepEnd(), BubbleManager.Priority.NORMAL)
                    }
                }
            }
        }
    }

    // ---- 显示宠物 ----

    private fun showPet() {
        if (!floatWindowManager.hasOverlayPermission()) {
            Timber.w("无悬浮窗权限")
            return
        }

        val initX = (floatWindowManager.screenWidth - petSizePx) / 2
        val initY = floatWindowManager.screenHeight - petSizePx - 100
        physics.setPosition(initX.toFloat(), initY.toFloat())

        floatWindowManager.showPet(petView, petSizePx, petSizePx, initX, initY)
        floatWindowManager.addTouchListener { x, y, action -> handleTouch(x, y, action) }

        animationEngine.start()
        startPhysicsLoop()
        startWhisper()

        Timber.i("宠物已显示在屏幕上 (M4)")
    }

    // ---- 触摸处理 ----

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
                // 睡着时点击 → 拒绝打扰
                if (sleepMachine.isSleeping()) {
                    bubbleManager.enqueue(sleepMachine.onDisturb(), BubbleManager.Priority.HIGH)
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastClickTime < DOUBLE_CLICK_TIMEOUT) {
                    // 双击 → 打开聊天
                    Timber.i("双击宠物，打开聊天")
                    bubbleManager.enqueue("和小黑聊聊天吧~", BubbleManager.Priority.URGENT)
                    sendBroadcast(Intent(ACTION_OPEN_CHAT).setPackage(packageName))
                    lastClickTime = 0L
                } else {
                    // 单击 → 回应
                    lastClickTime = now
                    animationEngine.onClick()
                    bubbleManager.enqueue(phraseManager.click(), BubbleManager.Priority.HIGH)
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

    // ---- 碎碎念（主动说话）----

    private fun startWhisper() {
        serviceScope.launch {
            // 启动后 10 秒第一次说话
            delay(10000)
            if (isActive && !sleepMachine.isSleeping()) {
                bubbleManager.enqueue(phraseManager.idle(), BubbleManager.Priority.LOW)
            }

            // 之后随机间隔说话（15~60秒）
            while (isActive) {
                val interval = Random.nextLong(15000, 60000)
                delay(interval)
                if (isActive && !sleepMachine.isSleeping() && !bubbleManager.isShowing) {
                    bubbleManager.enqueue(phraseManager.idle(), BubbleManager.Priority.LOW)
                }
            }
        }
    }

    // ---- 睡眠控制 ----

    private fun triggerSleep() {
        if (!sleepMachine.isSleeping()) {
            sleepMachine.sleep()
        }
    }

    private fun triggerWake() {
        if (sleepMachine.isSleeping()) {
            sleepMachine.wake()
        }
    }

    // ---- 气泡显示 ----

    private fun showBubble(text: String) {
        if (!floatWindowManager.isShowing) return

        floatWindowManager.hideBubble()

        bubbleView = BubbleView(this).apply {
            charIntervalMs = 80L
            displayDurationMs = 4000L
            fadeOutMs = 600L
            onDismiss = { floatWindowManager.hideBubble() }
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
