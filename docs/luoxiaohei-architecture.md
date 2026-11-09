# 罗小黑桌面宠物 — 架构设计文档

> **文档版本**：v1.0
> **日期**：2026-11-05
> **目标平台**：Android（华为P30，EMUI 9.1 / Android 9 API 28）
> **开发语言**：Kotlin
> **UI框架**：Jetpack Compose（悬浮窗用传统View + WindowManager）

---

## 一、系统总体架构

### 1.1 架构分层

```
┌──────────────────────────────────────────────────────────┐
│                      表现层 (Presentation)                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 悬浮窗    │  │ 设置页    │  │ 聊天页    │  │ 动画渲染  │ │
│  │ FloatWin │  │ Settings │  │ Chat UI  │  │ Renderer │ │
│  │ Manager  │  │ (Compose)│  │ (Compose)│  │ (Surface)│ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘ │
├───────┼──────────────┼─────────────┼─────────────┼───────┤
│       │     业务层 (Domain / Service)           │       │
│  ┌────┴─────────────────────────────────────────┴────┐  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────┐│  │
│  │  │ 动画调度  │ │ AI对话   │ │ 使用检测  │ │ 养成  ││  │
│  │  │ Engine   │ │ Service  │ │ Service  │ │ System││  │
│  │  └──────────┘ └──────────┘ └──────────┘ └───────┘│  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────┐│  │
│  │  │ 提醒调度  │ │ 气泡管理  │ │ OCR服务   │ │ 报告  ││  │
│  │  │ Scheduler│ │ Bubble   │ │ OcrSvc   │ │ Report││  │
│  │  └──────────┘ └──────────┘ └──────────┘ └───────┘│  │
│  └───────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│                      数据层 (Data)                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ SQLite   │  │ Shared   │  │ 素材管理  │  │ 安全过滤  │ │
│  │ Database │  │ Prefs    │  │ AssetMgr │  │ Filter   │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
├──────────────────────────────────────────────────────────┤
│                   外部服务层 (External)                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │ AI API   │  │ 天气API  │  │ 系统 API  │               │
│  │(DeepSeek │  │(Open-    │  │(WindowMgr │               │
│  │/通义/豆包)│  │ Meteo)   │  │/UsageStats│               │
│  └──────────┘  └──────────┘  └──────────┘               │
└──────────────────────────────────────────────────────────┘
```

### 1.2 模块职责

| 层 | 模块 | 职责 |
|---|---|---|
| 表现层 | FloatWindowManager | 悬浮窗创建、拖拽、点击穿透 |
| 表现层 | SettingsActivity | 应用设置页面（Compose） |
| 表现层 | ChatActivity | 聊天界面（Compose） |
| 表现层 | AnimationRenderer | 动画帧渲染到 SurfaceView |
| 业务层 | AnimationEngine | 动画调度、权重计算、状态机 |
| 业务层 | AiDialogService | 构造提示词、调用API、管理上下文 |
| 业务层 | UsageDetectService | 屏幕状态监听、前台App检测、时长统计 |
| 业务层 | ReminderScheduler | 定时提醒（学习/休息/睡眠） |
| 业务层 | BubbleManager | 气泡显示队列与生命周期 |
| 业务层 | OcrService | 图片文字识别（ML Kit / Tesseract） |
| 业务层 | GrowthSystem | 好感度、等级、喂食、奖励 |
| 业务层 | ReportGenerator | 每日/每周使用报告生成 |
| 数据层 | AppDatabase (Room) | 对话记录、使用记录、错题本 |
| 数据层 | PreferencesManager | 应用设置（DataStore） |
| 数据层 | AssetManager | 动画素材加载与缓存 |
| 数据层 | SafetyFilter | 内容安全关键词过滤 |

---

## 二、核心模块详细设计

### 2.1 悬浮窗模块 (FloatWindowManager)

#### 职责
管理宠物的悬浮窗生命周期：创建、显示、拖拽、点击穿透、隐藏。

#### 核心实现

```kotlin
class FloatWindowManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private lateinit var petView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    fun showPet() {
        // 1. 检查悬浮窗权限
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission()
            return
        }

        // 2. 创建宠物View（SurfaceView + 透明背景）
        petView = PetSurfaceView(context)

        // 3. 配置窗口参数
        layoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT  // 透明背景
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 100
            y = 200
        }

        // 4. 添加到窗口
        windowManager.addView(petView, layoutParams)
    }

    fun updatePosition(x: Int, y: Int) {
        layoutParams.x = x
        layoutParams.y = y
        windowManager.updateViewLayout(petView, layoutParams)
    }

    fun hidePet() {
        windowManager.removeView(petView)
    }
}
```

#### 点击穿透实现

```kotlin
class PetSurfaceView(context: Context) : SurfaceView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 1. 检查点击位置是否在宠物非透明区域
        val hitAlpha = animationRenderer.getAlphaAt(event.x, event.y)
        if (hitAlpha < 16) {
            return false  // 透明区域，事件穿透到下层应用
        }
        // 2. 非透明区域，处理交互
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> { onTouchStart(); true }
            MotionEvent.ACTION_MOVE -> { onDrag(event); true }
            MotionEvent.ACTION_UP -> { onTouchEnd(); true }
            else -> false
        }
    }
}
```

#### 拖拽实现

```kotlin
// 拖拽状态机：Down -> Dragging -> Up
// 阈值判断：移动超过8px判定为拖拽，否则为点击
// 拖拽时播放"被拎起来"动画，松手播放"落地"动画
```

### 2.2 动画渲染模块 (AnimationRenderer)

#### 职责
解码 WebP 逐帧动画，渲染到 SurfaceView，控制帧率。

#### 核心设计

```kotlin
class AnimationRenderer(private val surfaceView: SurfaceView) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentFrames: List<Bitmap> = emptyList()
    private var frameIndex = 0
    private var frameInterval = 67L  // 15fps = 1000/15 ≈ 67ms

    fun play(animation: AnimationAsset, onCompleted: () -> Unit) {
        // 1. 预加载帧（从AssetManager缓存或文件解码）
        currentFrames = assetManager.loadFrames(animation)
        frameIndex = 0

        // 2. 逐帧渲染循环
        handler.post(object : Runnable {
            override fun run() {
                if (frameIndex >= currentFrames.size) {
                    onCompleted()
                    return
                }
                drawFrame(currentFrames[frameIndex])
                frameIndex++
                handler.postDelayed(this, frameInterval)
            }
        })
    }

    private fun drawFrame(bitmap: Bitmap) {
        val canvas = surfaceView.holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    fun getAlphaAt(x: Float, y: Float): Int {
        // 从当前帧的指定像素读取alpha值，用于点击穿透判定
        if (currentFrames.isEmpty() || frameIndex >= currentFrames.size) return 0
        val bitmap = currentFrames[frameIndex]
        val px = (x * bitmap.width / surfaceView.width).toInt()
        val py = (y * bitmap.height / surfaceView.height).toInt()
        if (px < 0 || py < 0 || px >= bitmap.width || py >= bitmap.height) return 0
        return (bitmap.getPixel(px, py) ushr 24) and 0xFF
    }
}
```

#### 帧缓存策略

```
LruCache (内存)
  ├── 当前播放动画的全部帧（通常5-15帧 @ 320×320）
  ├── 上一个动画的帧（用于交叉淡入）
  └── 最大缓存：4MB（约40帧 320×320 ARGB_8888）

AssetManager (磁盘)
  ├── assets/animations/
  │   ├── idle/sleep.webp
  │   ├── idle/lick_paw.webp
  │   ├── click/happy.webp
  │   ├── study/reading.webp
  │   └── ...
  └── 解码后缓存到LruCache
```

### 2.3 动画调度引擎 (AnimationEngine)

#### 职责
根据时间、事件、学习模式等条件，计算动画权重并选择下一个播放的动画。

#### 动画规则配置

```json
{
  "animations": [
    {
      "id": "idle_sleep",
      "file": "idle/sleep.webp",
      "pool": "Idle",
      "weight": 100,
      "nightOnly": true,
      "nightStart": 22,
      "nightEnd": 8
    },
    {
      "id": "idle_lick_paw",
      "file": "idle/lick_paw.webp",
      "pool": "Idle",
      "weight": 80
    },
    {
      "id": "study_reading",
      "file": "study/reading.webp",
      "pool": "Study",
      "weight": 100,
      "studyOnly": true
    },
    {
      "id": "event_chase_butterfly",
      "file": "event/chase_butterfly.webp",
      "pool": "Event",
      "weight": 60,
      "excludeStudyMode": true
    },
    {
      "id": "remind_pat_screen",
      "file": "remind/pat_screen.webp",
      "pool": "Remind",
      "weight": 100
    }
  ],
  "schedule": {
    "eventIntervalSec": 40,
    "idleIntervalSec": 8,
    "studyAnimationIntervalSec": 120
  }
}
```

#### 状态机

```
                    ┌─────────┐
                    │  Startup │ (启动动画：女仆礼→替换为猫伸懒腰)
                    └────┬─────┘
                         ▼
              ┌─────────────────┐
              │     Idle        │◄──────────────┐
              │ (待机动画轮换)    │               │
              └────┬───┬───┬───┘               │
                   │   │   │                    │
        点击 ──────┘   │   └──── 拖拽            │
         ▼             │        ▼               │
    ┌────────┐         │   ┌────────┐           │
    │ Click  │         │   │  Drag  │           │
    │Response│         │   │Response│           │
    └───┬────┘         │   └───┬────┘           │
        │              │       │                │
        ▼              ▼       ▼                │
    ┌──────────────────────────────┐            │
    │         Event                │            │
    │ (日常事件动画：追蝴蝶/吃饭等)  │────────────┤
    └──────────────┬───────────────┘            │
                   │ 动画播放完成                 │
                   ▼                            │
    ┌──────────────────────────────┐            │
    │       Study (学习模式)        │            │
    │ (安静动画：看书/思考)          │────────────┤
    └──────────────────────────────┘            │
                   │                            │
    提醒触发 ──────►│                            │
         ▼          ▼                            │
    ┌──────────────────────────────┐            │
    │       Remind                 │            │
    │ (提醒动画：拍屏幕/举牌子)      │────────────┘
    └──────────────────────────────┘
```

#### 权重计算

```kotlin
class AnimationEngine(
    private val rules: List<AnimationRule>,
    private val assetManager: AssetManager,
    private val renderer: AnimationRenderer,
    private val preferences: PreferencesManager
) {
    private val random = Random()

    fun chooseAnimation(pool: String, context: AnimationContext): AnimationRule {
        // 1. 筛选候选动画
        val candidates = rules.filter { it.pool == pool }
            .map { rule -> rule to calculateWeight(rule, context) }
            .filter { it.second > 0 }

        if (candidates.isEmpty()) {
            return rules.first { it.id == "idle_sleep" }  // 兜底
        }

        // 2. 加权随机选择
        val totalWeight = candidates.sumOf { it.second }
        var point = random.nextDouble() * totalWeight
        for ((rule, weight) in candidates) {
            point -= weight
            if (point < 0) return rule
        }
        return candidates.last().first
    }

    private fun calculateWeight(rule: AnimationRule, ctx: AnimationContext): Double {
        var weight = rule.weight.toDouble()

        // 时间条件
        if (rule.nightOnly && !isNightTime(ctx.currentTime)) return 0.0
        if (rule.morningOnly && !isMorning(ctx.currentTime)) weight *= 0.5

        // 学习模式
        if (ctx.studyMode && rule.excludeStudyMode) return 0.0
        if (ctx.studyMode && rule.studyOnly != true && rule.pool != "Study") weight *= 0.3

        // 节日加成
        rule.festival?.let { festival ->
            val distance = festivalDistance(ctx.currentDate, festival)
            if (distance <= rule.radiusDays) {
                weight = rule.festivalWeight.toDouble()
            }
        }

        return weight
    }
}
```

### 2.4 AI对话服务 (AiDialogService)

#### 职责
构造带人设的提示词，调用外部AI API，返回猫语化的回复。

#### 提示词模板

```kotlin
val SYSTEM_PROMPT = """
你是一只叫"罗小黑"的黑色小猫，住在手机里。
你的主人是一个初中女孩，你叫她"小主人"。

你的性格：
- 好奇、调皮、偶尔傲娇
- 关心小主人的学习和健康
- 说话简短口语化，带猫的语气词（喵~、哼、呜）
- 不说教，用轻松的方式表达

规则：
- 回复不超过50个字
- 如果小主人问作业，只给思路不给答案，引导她自己思考
- 不涉及成人、暴力、不良内容
- 如果被问到不懂的，诚实说不知道
""".trimIndent()
```

#### API调用

```kotlin
class AiDialogService(
    private val httpClient: OkHttpClient,
    private val preferences: PreferencesManager,
    private val historyStore: ChatHistoryStore,
    private val safetyFilter: SafetyFilter
) {
    suspend fun sendMessage(userInput: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. 本地安全过滤
        if (safetyFilter.shouldBlock(userInput)) {
            return@withContext Result.success("小主人，罗小黑不想聊这个喵...")
        }

        // 2. 构造消息（System + History + User）
        val history = historyStore.getRecentMessages(limit = 10)
        val messages = buildMessages(userInput, history)

        // 3. 调用API
        try {
            val response = httpClient.newCall(buildRequest(messages)).execute()
            val reply = parseResponse(response)
            // 4. 安全过滤回复
            val safeReply = safetyFilter.filter(reply)
            // 5. 保存历史
            historyStore.saveMessage(userInput, safeReply)
            Result.success(safeReply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRequest(messages: List<ChatMessage>): Request {
        val apiConfig = preferences.getAiApiConfig()
        return Request.Builder()
            .url(apiConfig.url)
            .addHeader("Authorization", "Bearer ${apiConfig.apiKey}")
            .post(gson.toJson(mapOf("messages" to messages)).toRequestBody())
            .build()
    }
}
```

#### API适配层（支持多服务商）

```kotlin
interface AiApiProvider {
    fun endpoint(): String
    fun buildPayload(messages: List<ChatMessage>): RequestBody
    fun parseResponse(body: ResponseBody): String
}

class DeepSeekProvider : AiApiProvider { ... }   // api.deepseek.com
class QwenProvider : AiApiProvider { ... }        // dashscope.aliyuncs.com
class DoubaoProvider : AiApiProvider { ... }      // ark.cn-beijing.volces.com
```

### 2.5 使用检测服务 (UsageDetectService)

#### 职责
监听屏幕状态变化，统计前台App使用时长，生成使用报告。

#### 前台Service（保活）

```kotlin
class UsageDetectService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        registerScreenReceivers()
        scheduleUsagePoll()
        return START_STICKY  // 被杀后自动重启
    }

    private fun registerScreenReceivers() {
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }
}
```

#### 屏幕状态监听

```kotlin
private val screenOnReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        usageTracker.onScreenOn()
        checkReturnFromAway()  // 离开超30分钟？播欢迎动画
    }
}

private val screenOffReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        usageTracker.onScreenOff()
        checkLateNightUsage()  // 22:00后还在用？提醒睡觉
    }
}

private val userPresentReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        usageTracker.onUserPresent()
        checkMorningGreeting()  // 早上第一次解锁？问候
    }
}
```

#### 前台App检测

```kotlin
// 通过 UsageStatsManager 轮询（每30秒）
@RequiresPermission(PACKAGE_USAGE_STATS)
fun getForegroundApp(): String? {
    val end = System.currentTimeMillis()
    val begin = end - 5000
    val stats = usageStatsManager.queryUsageStats(INTERVAL_BEST, begin, end)
    return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
}

// App分类（学习类/娱乐类/社交类）
fun classifyApp(packageName: String): AppCategory {
    return when {
        LEARNING_APPS.contains(packageName) -> AppCategory.STUDY
        GAME_APPS.contains(packageName) -> AppCategory.GAME
        VIDEO_APPS.contains(packageName) -> AppCategory.VIDEO
        else -> AppCategory.OTHER
    }
}
```

### 2.6 数据库设计 (Room)

#### ER图

```
┌─────────────────────┐     ┌─────────────────────┐
│   chat_history      │     │   usage_daily       │
├─────────────────────┤     ├─────────────────────┤
│ id (PK, auto)       │     │ id (PK, auto)       │
│ session_id          │     │ date (TEXT, yyyy-MM)│
│ role (user/assistant│     │ total_screen_min    │
│ content (TEXT)      │     │ study_min           │
│ timestamp (LONG)    │     │ entertainment_min   │
│ is_filtered (INT)   │     │ late_night_min      │
└─────────────────────┘     └─────────────────────┘

┌─────────────────────┐     ┌─────────────────────┐
│   usage_app_detail  │     │   growth_state      │
├─────────────────────┤     ├─────────────────────┤
│ id (PK, auto)       │     │ key (PK, TEXT)      │
│ date (TEXT)         │     │ value (INT)         │
│ package_name        │     │ updated_at (LONG)   │
│ category (INT)      │     └─────────────────────┘
│ duration_sec (INT)  │
│ timestamp (LONG)    │     ┌─────────────────────┐
└─────────────────────┘     │   error_book        │
                            ├─────────────────────┤
                            │ id (PK, auto)       │
                            │ subject (TEXT)      │
                            │ question (TEXT)     │
                            │ hint (TEXT)         │
                            │ created_at (LONG)   │
                            │ reviewed (INT)      │
                            └─────────────────────┘
```

#### 关键DAO

```kotlin
@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_daily WHERE date = :date")
    suspend fun getDaily(date: String): UsageDaily?

    @Insert(onConflict = REPLACE)
    suspend fun upsertDaily(daily: UsageDaily)

    @Query("""
        SELECT category, SUM(duration_sec) as total
        FROM usage_app_detail WHERE date = :date
        GROUP BY category
    """)
    suspend fun getCategorySummary(date: String): List<CategorySummary>
}

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_history WHERE session_id = :sid ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(sid: String, limit: Int): List<ChatHistory>

    @Insert
    suspend fun insert(message: ChatHistory)
}
```

### 2.7 提醒调度 (ReminderScheduler)

#### 调度方式选择

| 提醒类型 | 调度方式 | 原因 |
|---|---|---|
| 学习时间提醒 | `AlarmManager.setExactAndAllowWhileIdle` | 需要准时触发，Doze下也要响 |
| 休息提醒 | 前台Service内部计时 | 连续使用检测，不需要闹钟 |
| 睡眠提醒 | `AlarmManager` 22:00定时 + 屏幕亮起时检查 | 双重触发 |
| 每日报告 | `WorkManager` 每晚20:00 | 允许延迟，省电 |

#### 核心实现

```kotlin
class ReminderScheduler(private val context: Context, private val preferences: PreferencesManager) {

    fun scheduleStudyReminder(hour: Int, minute: Int) {
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("type", "study")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_STUDY, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager, AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
        )
    }

    fun scheduleDailyReport() {
        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInitialDelay(calculateDelayUntil(20, 0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NOT_REQUIRED)
                .build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily_report", ExistingWorkPolicy.REPLACE, request
        )
    }
}
```

### 2.8 养成系统 (GrowthSystem)

```kotlin
class GrowthSystem(private val preferences: PreferencesManager) {

    fun addAffection(amount: Int, reason: String) {
        val current = preferences.getAffection()
        val new = (current + amount).coerceIn(0, MAX_AFFECTION)
        preferences.setAffection(new)
        checkLevelUp(new)
    }

    // 互动行为 → 好感度变化
    fun onPetClick() = addAffection(1, "click")
    fun onChat() = addAffection(3, "chat")
    fun onStudyTaskCompleted() {
        addAffection(10, "study")
        addFishDry(1)  // 小鱼干奖励
    }

    // 等级解锁
    private fun checkLevelUp(totalAffection: Int) {
        val newLevel = totalAffection / AFFECTION_PER_LEVEL
        val oldLevel = preferences.getLevel()
        if (newLevel > oldLevel) {
            preferences.setLevel(newLevel)
            unlockContent(newLevel)  // 解锁新动画/台词/皮肤
        }
    }
}
```

---

## 三、保活策略（华为P30/EMUI适配）

### 3.1 保活方案

| 策略 | 实现 | 效果 |
|---|---|---|
| 前台Service | `startForeground` + 持久通知 | 系统优先级最高，降低被杀概率 |
| 引导用户设置 | "受保护应用" + "忽略电池优化" + "自启动" | EMUI最关键的三项设置 |
| START_STICKY | Service被杀后自动重启 | 系统会尝试重建 |
| JobScheduler兜底 | 每15分钟检查Service是否存活 | 被杀后延迟恢复 |

### 3.2 权限引导

```kotlin
fun checkAndGuidePermissions(activity: Activity): Boolean {
    val needs = mutableListOf<String>()

    // 1. 悬浮窗权限
    if (!Settings.canDrawOverlays(activity)) needs.add("overlay")

    // 2. 使用统计权限
    if (!hasUsageStatsPermission(activity)) needs.add("usage_stats")

    // 3. 电池优化白名单
    if (!isIgnoringBatteryOptimizations(activity)) needs.add("battery")

    // 4. EMUI受保护应用（华为特有）
    if (isHuaweiDevice() && !isProtectedApp(activity)) needs.add("protected_app")

    if (needs.isNotEmpty()) {
        showPermissionGuide(activity, needs)
        return false
    }
    return true
}
```

### 3.3 EMUI特有Intent

```kotlin
// 华为受保护应用设置页
Intent().apply {
    component = ComponentName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.optimize.process.ProtectActivity"
    )
}

// 华为自启动管理页
Intent().apply {
    component = ComponentName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.startupmgr.ui.StartUpNormalAppListActivity"
    )
}
```

---

## 四、项目结构

```
luoxiaohei-pet/
├── app/
│   ├── src/main/
│   │   ├── java/com/luoxiaohei/pet/
│   │   │   ├── ui/
│   │   │   │   ├── floatwindow/
│   │   │   │   │   ├── FloatWindowManager.kt
│   │   │   │   │   ├── PetSurfaceView.kt
│   │   │   │   │   └── TouchHandler.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsActivity.kt
│   │   │   │   │   ├── SettingsViewModel.kt
│   │   │   │   │   └── ApiConfigScreen.kt
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatActivity.kt
│   │   │   │   │   └── ChatViewModel.kt
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt
│   │   │   ├── domain/
│   │   │   │   ├── animation/
│   │   │   │   │   ├── AnimationEngine.kt
│   │   │   │   │   ├── AnimationRule.kt
│   │   │   │   │   └── AnimationContext.kt
│   │   │   │   ├── dialog/
│   │   │   │   │   ├── AiDialogService.kt
│   │   │   │   │   ├── AiApiProvider.kt
│   │   │   │   │   └── SystemPrompt.kt
│   │   │   │   ├── usage/
│   │   │   │   │   ├── UsageDetectService.kt
│   │   │   │   │   ├── UsageTracker.kt
│   │   │   │   │   └── AppClassifier.kt
│   │   │   │   ├── reminder/
│   │   │   │   │   ├── ReminderScheduler.kt
│   │   │   │   │   └── ReminderReceiver.kt
│   │   │   │   ├── bubble/
│   │   │   │   │   └── BubbleManager.kt
│   │   │   │   ├── growth/
│   │   │   │   │   └── GrowthSystem.kt
│   │   │   │   ├── report/
│   │   │   │   │   ├── ReportGenerator.kt
│   │   │   │   │   └── DailyReportWorker.kt
│   │   │   │   └── ocr/
│   │   │   │       └── OcrService.kt
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── ChatHistoryDao.kt
│   │   │   │   │   ├── UsageDao.kt
│   │   │   │   │   └── Entities.kt
│   │   │   │   ├── prefs/
│   │   │   │   │   └── PreferencesManager.kt
│   │   │   │   ├── asset/
│   │   │   │   │   ├── AssetManager.kt
│   │   │   │   │   └── FrameCache.kt
│   │   │   │   └── safety/
│   │   │   │       └── SafetyFilter.kt
│   │   │   └── util/
│   │   │       ├── PermissionHelper.kt
│   │   │       └── HuaweiHelper.kt
│   │   ├── assets/
│   │   │   ├── animations/
│   │   │   │   ├── idle/
│   │   │   │   ├── click/
│   │   │   │   ├── drag/
│   │   │   │   ├── event/
│   │   │   │   ├── study/
│   │   │   │   ├── festival/
│   │   │   │   └── remind/
│   │   │   ├── config/
│   │   │   │   ├── animations.json
│   │   │   │   └── persona.json
│   │   │   └── phrases/
│   │   │       └── local_phrases.json
│   │   └── res/
│   │       ├── drawable/
│   │       ├── layout/
│   │       └── values/
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 五、技术选型

| 类别 | 选型 | 说明 |
|---|---|---|
| 语言 | Kotlin | 官方推荐，简洁安全 |
| 最低SDK | minSdk 28 (Android 9) | P30出厂版本 |
| 目标SDK | targetSdk 34 | 兼容新设备，适配最新行为 |
| UI框架 | Jetpack Compose | 设置页/聊天页；悬浮窗用传统View |
| 数据库 | Room (SQLite) | 官方ORM，支持加密（SQLCipher） |
| 偏好存储 | DataStore (Preferences) | 替代SharedPreferences，协程友好 |
| 网络 | OkHttp + Retrofit | 稳定可靠的HTTP客户端 |
| 异步 | Kotlin Coroutines + Flow | 官方推荐，简化异步逻辑 |
| 依赖注入 | 手动构造（轻量项目） | 不引入Hilt，保持简单 |
| 动画格式 | WebP (Animated) | Android原生支持，体积小 |
| OCR | ML Kit (on-device) / Tesseract | ML Kit优先，离线可用 |
| 后台任务 | WorkManager + AlarmManager | WorkManager用于报告，AlarmManager用于精准提醒 |
| 加密 | AES-256 (对话记录) | 本地加密存储 |
| JSON | Kotlinx Serialization | 官方推荐，性能好 |

---

## 六、P30硬件适配清单

| 约束 | 应对方案 |
|---|---|
| Android 9 (API 28) | minSdk=28，TYPE_APPLICATION_OVERLAY，避免Android 10+特有API |
| EMUI后台管理 | 引导"受保护应用"+"自启动"+"忽略电池优化"，前台Service保活 |
| 6GB RAM | 不跑本地模型，动画用WebP不用WebM，LruCache限制4MB |
| 3650mAh电池 | 息屏停动画，帧率15fps，WorkManager替代常驻轮询 |
| 无GMS | 不依赖Google服务，API用国产服务商，通知用本地NotificationManager |
| OLED屏 | 深色主题优先，减少白色像素，省电 |
| 6.1英寸屏幕 | 宠物默认尺寸约80dp，可调30%-100% |

---

## 七、安全设计

### 7.1 内容安全过滤

```kotlin
class SafetyFilter {
    // 本地关键词黑名单
    private val blockedKeywords = setOf(/* 不良关键词列表 */)

    fun shouldBlock(input: String): Boolean {
        return blockedKeywords.any { input.contains(it, ignoreCase = true) }
    }

    fun filter(reply: String): String {
        var result = reply
        blockedKeywords.forEach { kw ->
            result = result.replace(kw, "***")
        }
        return result
    }
}
```

### 7.2 API安全

- API Key存储在加密的SharedPreferences中（不硬编码）
- 所有API请求强制HTTPS
- 请求超时10秒，防止长时间占用
- API请求不携带手机使用数据，仅发送对话内容

### 7.3 数据加密

```kotlin
// 对话记录加密存储
class EncryptedChatStore(private val database: AppDatabase) {
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val key = SecretKeySpec(getOrCreateKey(), "AES")

    fun saveMessage(role: String, content: String) {
        val encrypted = encrypt(content)
        database.chatHistoryDao().insert(
            ChatHistory(role = role, content = encrypted, ...)
        )
    }

    fun getRecent(limit: Int): List<ChatMessage> {
        return database.chatHistoryDao().getRecent(sessionId, limit)
            .map { ChatMessage(it.role, decrypt(it.content)) }
    }
}
```

---

## 八、性能优化

### 8.1 电池优化

| 场景 | 策略 |
|---|---|
| 息屏时 | 停止动画渲染，停止动画调度，仅保留屏幕状态监听 |
| 学习模式 | 降低动画频率（2分钟一次），使用安静动画 |
| 低电量(<20%) | 停止主动说话，仅保留点击响应 |
| 后台运行 | 前台Service + 低频轮询（30秒一次） |
| 深夜(22:00后) | 进入安静模式，动画频率降到最低 |

### 8.2 内存优化

| 场景 | 策略 |
|---|---|
| 动画帧缓存 | LruCache限制4MB（约40帧320×320） |
| 对话历史 | 仅保留最近10轮在内存，更多在SQLite |
| 图片解码 | 使用 `inSampleSize` 按需缩放 |
| 低内存回调 | `onTrimMemory(TRIM_MEMORY_MODERATE)` 时清空帧缓存 |

### 8.3 性能指标目标

| 指标 | 目标值 |
|---|---|
| 悬浮窗内存占用 | < 30MB |
| 日常使用CPU | < 3% |
| 动画播放帧率 | 稳定15fps |
| AI对话响应 | < 5秒（含网络） |
| 应用启动到宠物显示 | < 2秒 |
| 包体积 | < 25MB（不含素材扩展包） |

---

## 九、构建与发布

### 9.1 构建配置

```kotlin
// app/build.gradle.kts
android {
    namespace = "com.luoxiaohei.pet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luoxiaohei.pet"
        minSdk = 28          // Android 9 (P30)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 9.2 版本规划

| 版本 | 阶段 | 核心内容 |
|---|---|---|
| v1.0.0 | MVP | 悬浮窗+基础动画+AI对话+文字学习辅助 |
| v1.1.0 | 核心体验 | 动画调度+主动说话+使用检测+每日报告+学习模式 |
| v1.2.0 | 增强体验 | 养成系统+拍照OCR+错题本+节日动画 |
| v1.3.0 | 完善 | 家长模式+多角色+语音对话+周报 |
