# 罗小黑桌面宠物 — 详细设计说明书

> **文档版本**：v1.0
> **日期**：2026-11-05
> **目标平台**：Android（华为P30，EMUI 9.1 / Android 9 API 28）
> **关联文档**：
> - [需求分析文档](luoxiaohei-requirements.md)
> - [架构设计文档](luoxiaohei-architecture.md)
> - [dsh-pet复用分析](luoxiaohei-dsh-pet-reuse.md)
> - [世界观与故事设定](luoxiaohei-story.md)

---

## 目录

1. [特性清单](#一特性清单)
2. [开发环境](#二开发环境)
3. [开源组件](#三开源组件)
4. [代码架构](#四代码架构)
5. [核心模块设计](#五核心模块设计)
6. [数据模型](#六数据模型)
7. [接口定义](#七接口定义)
8. [安全设计](#八安全设计)
9. [开发计划](#九开发计划)
10. [测试框架](#十测试框架)
11. [发布与部署](#十一发布与部署)

---

## 一、特性清单

### 1.1 P0 — MVP最小可用版本

| 编号 | 特性 | 描述 | 验收标准 |
|---|---|---|---|
| F-001 | 悬浮窗显示 | 罗小黑猫悬浮在所有应用之上 | 悬浮窗创建成功，透明背景，OLED不泛白 |
| F-002 | 基础动画 | 待机(3) + 点击回应(3) + 拖拽(1)共7段WebP动画 | 15fps流畅播放，透明区域可穿透 |
| F-003 | 点击交互 | 单击触发回应动画 + 随机一句台词 | 响应延迟<100ms，台词气泡3秒消失 |
| F-004 | 拖拽移动 | 长按拖拽宠物到屏幕任意位置 | 拖拽跟手无延迟，松手停留，有Q弹落地挤压 |
| F-005 | 气泡对话 | 猫头顶气泡显示文字，逐字显示 | 圆角气泡，自动消失，不遮挡操作 |
| F-006 | AI聊天 | 双击展开输入框，调用外部AI API回复 | 支持DeepSeek/通义/豆包，10秒超时，上下文记忆10轮 |
| F-007 | 人设注入 | System Prompt注入罗小黑性格设定 | 回复风格符合猫语化，叫用户"小主人"，不说教 |
| F-008 | 学习问答 | 文字形式的学习问题辅助 | 只给思路不给答案，鼓励式语气 |
| F-009 | 设置页 | API配置、宠物大小、吐字间隔 | Compose UI，配置持久化 |
| F-010 | 权限引导 | 悬浮窗权限+使用统计权限+电池白名单 | 引导页面清晰，EMUI特有设置项覆盖 |

### 1.2 P1 — 核心体验

| 编号 | 特性 | 描述 | 验收标准 |
|---|---|---|---|
| F-011 | 动画调度 | 时间/事件/权重驱动的动画自动轮换 | idle/turn/move/action按权重掷骰，不连续重复 |
| F-012 | 睡眠系统 | 三段接力：入睡→循环→醒来 | 右键触发，睡着时安静不打扰，被扰只回拒绝台词 |
| F-013 | 主动说话 | 按周期自动生成碎碎念 | 键鼠停顿时开口，间隔2-30分钟六档随机 |
| F-014 | 屏幕状态检测 | 监听SCREEN_ON/OFF/USER_PRESENT | 准确检测亮灭屏、解锁、离开时长 |
| F-015 | 前台App检测 | UsageStatsManager统计各App使用时长 | 每30秒轮询，按学习/娱乐/其他分类 |
| F-016 | 学习模式 | 设置时间段，期间猫安静陪伴 | 19:00-21:00只播安静动画，降低事件频率 |
| F-017 | 每日报告 | 每晚20:00总结当天使用情况 | 学习时长/娱乐时长/休息情况，猫语化输出 |
| F-018 | 回来欢迎 | 离开超30分钟后回来播欢迎动画 | 屏幕点亮即触发，动画+气泡 |
| F-019 | 深夜提醒 | 22:00后持续使用提醒睡觉 | 猫打哈欠动画+气泡，最多提醒3次 |
| F-020 | 电量提醒 | 电量低于20%猫担心 | 猫担心表情+气泡 |

### 1.3 P2 — 增强体验

| 编号 | 特性 | 描述 | 验收标准 |
|---|---|---|---|
| F-021 | 养成系统 | 好感度/等级/喂食/小鱼干 | 6级成长，互动增加好感，解锁动画/台词 |
| F-022 | 拍照OCR | 拍作业照片识别文字 | ML Kit离线识别，准确率>85% |
| F-023 | 错题本 | 对话中的题目自动保存 | SQLite存储，按学科分类 |
| F-024 | 节日动画 | 春节/中秋/生日等特殊日期触发 | 农历节日计算，三角形权重衰减 |
| F-025 | 家长模式 | 家长密码查看使用报告 | 不可查看对话内容，保护隐私 |
| F-026 | 甩抛物理 | 拖拽后松手甩出，猫飞行+落地Q弹 | 重力1400px/s²，弹性0.78，落地挤压easeOutBack |
| F-027 | 多套睡姿 | 蹲着睡/趴着睡等多套睡眠动画 | 整套随机，三段来自同一套 |

### 1.4 P3 — 锦上添花

| 编号 | 特性 | 描述 |
|---|---|---|
| F-028 | 多角色切换 | 罗小白、节日皮肤等 |
| F-029 | 语音对话 | TTS念出台词 + STT语音输入 |
| F-030 | 桌面小组件 | 1×1小组件快速开关/喂食 |
| F-031 | 周报趋势 | 本周vs上周对比 |
| F-032 | 天气感知 | 猫须感知天气，恶劣天气猫躲起来 |

---

## 二、开发环境

### 2.1 开发机配置

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 10/11 或 macOS 12+ |
| 内存 | ≥ 16GB |
| 磁盘 | ≥ 50GB可用空间 |
| JDK | OpenJDK 17（Android Gradle Plugin 8.x要求） |
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Android SDK | compileSdk 34, minSdk 28, targetSdk 34 |
| Kotlin | 1.9.22+ |
| Gradle | 8.2+（AGP 8.x） |
| 测试设备 | 华为P30（真机）+ Android Emulator（API 28） |

### 2.2 环境搭建步骤

```bash
# 1. 安装 Android Studio
# 下载：https://developer.android.com/studio

# 2. 通过 SDK Manager 安装
#    Android SDK Platform 34 (Android 14)
#    Android SDK Platform 28 (Android 9, P30真机调试)
#    SDK Build-Tools 34.0.0
#    SDK Platform-Tools 34.0.0
#    Android Emulator + System Image (API 28, x86_64)

# 3. 配置环境变量
ANDROID_HOME=C:\Users\<user>\AppData\Local\Android\Sdk
JAVA_HOME=<JDK17路径>
Path += %ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator

# 4. 验证
adb version
adb devices  # P30用USB连接，开启USB调试
```

### 2.3 P30真机调试准备

```
1. 设置 → 关于手机 → 连点版本号7次 → 开启开发者选项
2. 设置 → 系统和更新 → 开发人员选项
   → 开启"USB调试"
   → 开启"仅充电模式下允许ADB调试"
3. USB连接电脑，手机弹窗选择"传输文件"
4. adb devices 确认设备在线
5. 运行 adb shell getprop ro.build.version.sdk 确认 API level = 28
```

### 2.4 SDK版本策略

| 配置 | 值 | 原因 |
|---|---|---|
| minSdk | 28 | P30出厂Android 9 |
| targetSdk | 34 | 满足Google Play要求（虽P30无GMS，但兼容性最佳） |
| compileSdk | 34 | 使用最新API编译 |

---

## 三、开源组件

### 3.1 依赖清单

| 组件 | 版本 | 用途 | 许可证 |
|---|---|---|---|
| **Jetpack Compose** | BOM 2024.02.00 | 设置页/聊天页UI | Apache 2.0 |
| **Compose Material3** | 1.2.0 | MD3组件 | Apache 2.0 |
| **AndroidX Core KTX** | 1.12.0 | Kotlin扩展 | Apache 2.0 |
| **AndroidX Lifecycle** | 2.7.0 | ViewModel/Lifecycle | Apache 2.0 |
| **AndroidX Activity Compose** | 1.8.2 | Compose Activity | Apache 2.0 |
| **AndroidX Navigation Compose** | 2.7.7 | 页面导航 | Apache 2.0 |
| **Room** | 2.6.1 | SQLite ORM | Apache 2.0 |
| **Room KTX** | 2.6.1 | 协程支持 | Apache 2.0 |
| **DataStore Preferences** | 1.0.0 | 偏好存储 | Apache 2.0 |
| **OkHttp** | 4.12.0 | HTTP客户端 | Apache 2.0 |
| **Retrofit** | 2.9.0 | API接口封装 | Apache 2.0 |
| **Kotlinx Serialization** | 1.6.2 | JSON序列化 | Apache 2.0 |
| **Kotlinx Coroutines** | 1.7.3 | 异步编程 | Apache 2.0 |
| **WorkManager** | 2.9.0 | 后台任务调度 | Apache 2.0 |
| **ML Kit Text Recognition** | 16.0.1 | OCR文字识别（中文） | Apache 2.0 |
| **SQLCipher** | 4.5.4 | 对话记录加密 | Apache 2.0 |
| **Timber** | 5.0.1 | 日志框架 | Apache 2.0 |

### 3.2 依赖配置（build.gradle.kts）

```kotlin
// app/build.gradle.kts
dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ML Kit OCR（中文识别）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // 加密
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // 日志
    implementation("com.jakewharton.timber:timber:5.0.1")

    // 测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

### 3.3 dsh-pet 复用组件

从 dsh-pet（MIT许可证）移植到 Kotlin 的纯逻辑模块：

| 模块 | 源文件 | 目标文件 | 改动 |
|---|---|---|---|
| 动画权重掷骰 | `shared/pickers.ts` | `AnimationPicker.kt` | TypeScript→Kotlin，纯函数翻译 |
| 物理常量 | `shared/physics/constants.ts` | `PhysicsConstants.kt` | 数值直接搬用 |
| 落地挤压 | `shared/physics/squash.ts` | `Squash.kt` | 纯数学，直接翻译 |
| 弹簧跟手 | `shared/physics/spring.ts` | `Spring.kt` | 纯数学，直接翻译 |
| 抛掷物理 | `shared/physics/throw.ts` | `ThrowPhysics.kt` | 纯数学，直接翻译 |
| 松手初速估算 | `shared/physics/release.ts` | `ReleaseEstimator.kt` | 纯数学，直接翻译 |
| 睡眠状态机 | `client/pet/card/sleep-machine.ts` | `SleepMachine.kt` | 状态机翻译 |
| 配置类型 | `shared/types.ts` | `Config.kt` | data class映射 |

---

## 四、代码架构

### 4.1 项目目录结构

```
luoxiaohei-pet/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/luoxiaohei/pet/
│   │   │   │   │
│   │   │   │   ├── LuoxiaoheiApp.kt                 # Application入口
│   │   │   │   ├── MainActivity.kt                  # 主Activity（设置页入口）
│   │   │   │   │
│   │   │   │   ├── ui/                              # ── 表现层 ──
│   │   │   │   │   ├── floatwindow/
│   │   │   │   │   │   ├── FloatWindowManager.kt    # 悬浮窗创建/移除/位置更新
│   │   │   │   │   │   ├── PetSurfaceView.kt        # 宠物渲染SurfaceView
│   │   │   │   │   │   ├── TouchHandler.kt          # 点击/拖拽/穿透判定
│   │   │   │   │   │   └── BubbleView.kt            # 对话气泡View
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   ├── SettingsScreen.kt        # 设置主页面
│   │   │   │   │   │   ├── ApiConfigScreen.kt       # AI API配置
│   │   │   │   │   │   ├── PetConfigScreen.kt       # 宠物外观配置
│   │   │   │   │   │   ├── StudyConfigScreen.kt     # 学习模式配置
│   │   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   │   ├── chat/
│   │   │   │   │   │   ├── ChatScreen.kt            # 聊天界面
│   │   │   │   │   │   └── ChatViewModel.kt
│   │   │   │   │   ├── report/
│   │   │   │   │   │   ├── DailyReportScreen.kt     # 每日报告
│   │   │   │   │   │   └── ReportViewModel.kt
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt
│   │   │   │   │       ├── Theme.kt
│   │   │   │   │       └── Type.kt
│   │   │   │   │
│   │   │   │   ├── domain/                          # ── 业务层 ──
│   │   │   │   │   ├── animation/
│   │   │   │   │   │   ├── AnimationEngine.kt       # 动画调度引擎
│   │   │   │   │   │   ├── AnimationPicker.kt       # 权重掷骰+池抽取（移植自dsh-pet）
│   │   │   │   │   │   ├── AnimationRule.kt         # 动画规则数据类
│   │   │   │   │   │   ├── AnimationContext.kt      # 调度上下文
│   │   │   │   │   │   └── AnimationState.kt        # 动画状态枚举
│   │   │   │   │   ├── render/
│   │   │   │   │   │   ├── AnimationRenderer.kt     # WebP帧渲染器
│   │   │   │   │   │   └── FrameCache.kt            # LruCache帧缓存
│   │   │   │   │   ├── physics/
│   │   │   │   │   │   ├── PhysicsConstants.kt      # 物理常量（移植自dsh-pet）
│   │   │   │   │   │   ├── Squash.kt                # 落地挤压曲线（移植）
│   │   │   │   │   │   ├── Spring.kt                # 拖拽弹簧（移植）
│   │   │   │   │   │   ├── ThrowPhysics.kt          # 抛掷飞行（移植）
│   │   │   │   │   │   ├── ReleaseEstimator.kt      # 松手初速估算（移植）
│   │   │   │   │   │   └── PhysicsSimulator.kt      # 物理模拟协调器
│   │   │   │   │   ├── dialog/
│   │   │   │   │   │   ├── AiDialogService.kt       # AI对话核心
│   │   │   │   │   │   ├── AiApiProvider.kt         # API适配接口
│   │   │   │   │   │   ├── DeepSeekProvider.kt      # DeepSeek API
│   │   │   │   │   │   ├── QwenProvider.kt          # 通义千问 API
│   │   │   │   │   │   ├── DoubaoProvider.kt        # 豆包 API
│   │   │   │   │   │   ├── SystemPrompt.kt          # 罗小黑人设提示词
│   │   │   │   │   │   └── ChatMessage.kt           # 消息数据类
│   │   │   │   │   ├── usage/
│   │   │   │   │   │   ├── UsageDetectService.kt    # 前台Service：屏幕+App检测
│   │   │   │   │   │   ├── UsageTracker.kt          # 使用时长统计
│   │   │   │   │   │   ├── AppClassifier.kt         # App分类（学习/娱乐/其他）
│   │   │   │   │   │   └── ScreenStateReceiver.kt   # 屏幕广播接收器
│   │   │   │   │   ├── reminder/
│   │   │   │   │   │   ├── ReminderScheduler.kt     # 提醒调度
│   │   │   │   │   │   ├── ReminderReceiver.kt      # 提醒广播接收器
│   │   │   │   │   │   └── ReminderType.kt          # 提醒类型枚举
│   │   │   │   │   ├── bubble/
│   │   │   │   │   │   └── BubbleManager.kt         # 气泡显示队列
│   │   │   │   │   ├── sleep/
│   │   │   │   │   │   └── SleepMachine.kt          # 睡眠三段接力状态机（移植自dsh-pet）
│   │   │   │   │   ├── growth/
│   │   │   │   │   │   ├── GrowthSystem.kt          # 好感度/等级
│   │   │   │   │   │   └── FishDryWallet.kt         # 小鱼干钱包
│   │   │   │   │   ├── study/
│   │   │   │   │   │   ├── StudyModeController.kt   # 学习模式控制
│   │   │   │   │   │   └── StudyTaskTracker.kt      # 学习任务追踪
│   │   │   │   │   ├── report/
│   │   │   │   │   │   ├── ReportGenerator.kt       # 报告生成
│   │   │   │   │   │   └── DailyReportWorker.kt     # WorkManager定时任务
│   │   │   │   │   └── ocr/
│   │   │   │   │       └── OcrService.kt            # ML Kit OCR封装
│   │   │   │   │
│   │   │   │   ├── data/                            # ── 数据层 ──
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── AppDatabase.kt           # Room数据库
│   │   │   │   │   │   ├── ChatHistoryDao.kt        # 对话记录DAO
│   │   │   │   │   │   ├── UsageDao.kt              # 使用记录DAO
│   │   │   │   │   │   ├── ErrorBookDao.kt          # 错题本DAO
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── ChatHistoryEntity.kt
│   │   │   │   │   │       ├── UsageDailyEntity.kt
│   │   │   │   │   │       ├── UsageAppDetailEntity.kt
│   │   │   │   │   │       ├── ErrorBookEntity.kt
│   │   │   │   │   │       └── GrowthStateEntity.kt
│   │   │   │   │   ├── prefs/
│   │   │   │   │   │   ├── PreferencesManager.kt    # DataStore封装
│   │   │   │   │   │   └── PreferenceKeys.kt        # 键定义
│   │   │   │   │   ├── asset/
│   │   │   │   │   │   ├── AssetManager.kt          # WebP素材加载
│   │   │   │   │   │   └── AnimationConfigLoader.kt # animations.json解析
│   │   │   │   │   ├── safety/
│   │   │   │   │   │   ├── SafetyFilter.kt          # 内容安全过滤
│   │   │   │   │   │   └── KeywordDictionary.kt     # 关键词词典
│   │   │   │   │   └── config/
│   │   │   │   │       └── AppConfig.kt             # 全局配置数据类
│   │   │   │   │
│   │   │   │   ├── service/
│   │   │   │   │   ├── PetForegroundService.kt      # 前台Service（保活核心）
│   │   │   │   │   └── BootReceiver.kt              # 开机自启
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── PermissionHelper.kt          # 权限检查与引导
│   │   │   │       ├── HuaweiHelper.kt              # EMUI特有Intent
│   │   │   │       ├── NetworkHelper.kt             # 网络状态
│   │   │   │       ├── BatteryHelper.kt             # 电量/充电状态
│   │   │   │       └── DateHelper.kt                # 日期/时段/农历
│   │   │   │
│   │   │   ├── assets/
│   │   │   │   ├── animations/
│   │   │   │   │   ├── idle/
│   │   │   │   │   │   ├── sleep.webp
│   │   │   │   │   │   ├── lick_paw.webp
│   │   │   │   │   │   └── daze.webp
│   │   │   │   │   ├── click/
│   │   │   │   │   │   ├── happy.webp
│   │   │   │   │   │   ├── shy.webp
│   │   │   │   │   │   └── angry.webp
│   │   │   │   │   ├── drag/
│   │   │   │   │   │   └── grabbed.webp
│   │   │   │   │   ├── event/
│   │   │   │   │   ├── study/
│   │   │   │   │   ├── festival/
│   │   │   │   │   └── remind/
│   │   │   │   ├── config/
│   │   │   │   │   ├── animations.json             # 动画规则配置
│   │   │   │   │   ├── persona.json                # 罗小黑人设
│   │   │   │   │   └── phrases.json                # 本地台词库
│   │   │   │   └── config.json                      # 应用全局配置
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── drawable/                        # 图标/背景
│   │   │   │   ├── mipmap/                          # 应用图标
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   ├── xml/
│   │   │   │   │   └── backup_rules.xml
│   │   │   │   └── layout/
│   │   │   │       └── notification_pet.xml        # 前台Service通知布局
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/                                    # 单元测试
│   │   │   └── java/com/luoxiaohei/pet/
│   │   │       ├── domain/animation/
│   │   │       │   ├── AnimationPickerTest.kt
│   │   │       │   └── AnimationEngineTest.kt
│   │   │       ├── domain/physics/
│   │   │       │   ├── SquashTest.kt
│   │   │       │   ├── SpringTest.kt
│   │   │       │   ├── ThrowPhysicsTest.kt
│   │   │       │   └── ReleaseEstimatorTest.kt
│   │   │       ├── domain/dialog/
│   │   │       │   ├── AiDialogServiceTest.kt
│   │   │       │   └── SafetyFilterTest.kt
│   │   │       ├── domain/usage/
│   │   │       │   └── UsageTrackerTest.kt
│   │   │       ├── domain/growth/
│   │   │       │   └── GrowthSystemTest.kt
│   │   │       ├── domain/sleep/
│   │   │       │   └── SleepMachineTest.kt
│   │   │       └── data/
│   │   │           └── AppDatabaseTest.kt
│   │   │
│   │   └── androidTest/                             # 仪器测试
│   │       └── java/com/luoxiaohei/pet/
│   │           ├── ui/
│   │           │   ├── FloatWindowManagerTest.kt
│   │           │   └── SettingsScreenTest.kt
│   │           ├── domain/ocr/
│   │           │   └── OcrServiceTest.kt
│   │           └── service/
│   │               └── PetForegroundServiceTest.kt
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── build.gradle.kts                                 # 根构建文件
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

### 4.2 架构分层规则

| 规则 | 说明 |
|---|---|
| 单向依赖 | ui → domain → data，不可反向 |
| 纯函数优先 | 物理计算、动画选择、安全过滤为纯函数/纯类，无Android依赖 |
| 接口隔离 | domain层定义接口，data层实现（如AiApiProvider接口+DeepSeekProvider实现） |
| 不可变数据 | 数据类全用 `data class` + `val`，状态变更通过copy |
| 协程作用域 | ViewModel持有viewModelScope，Service持有自定义scope，不泄漏 |

### 4.3 关键设计模式

| 模式 | 应用 |
|---|---|
| 状态机 | 动画调度（Idle/Click/Drag/Event/Study/Sleep/Remind） |
| 策略模式 | AI API适配（AiApiProvider接口 + 多Provider实现） |
| 观察者 | Flow驱动UI更新（UsageTracker.flow → UI） |
| 前台Service | 保活核心，持有动画引擎和检测逻辑 |
| LruCache | 动画帧缓存（4MB上限） |
| 世代号 | 动画异步加载丢弃迟到结果（移植自dsh-pet） |
| 双缓冲 | 动画切换交叉淡入（Bitmap A/B） |

---

## 五、核心模块设计

### 5.1 悬浮窗 + 渲染管线

```
PetForegroundService启动
  │
  ├── FloatWindowManager.showPet()
  │     ├── 检查 SYSTEM_ALERT_WINDOW 权限
  │     ├── 创建 PetSurfaceView（SurfaceView + 透明背景）
  │     ├── WindowManager.addView(view, TYPE_APPLICATION_OVERLAY)
  │     └── 注册 TouchHandler
  │
  ├── AnimationRenderer（绑定到 SurfaceView）
  │     ├── AssetManager.loadFrames("idle/sleep.webp")
  │     │     └── 解码WebP → List<Bitmap>（320×320, ARGB_8888）
  │     ├── FrameCache.put(frames)  // LruCache 4MB
  │     └── renderLoop:
  │           ├── lockCanvas()
  │           ├── drawColor(TRANSPARENT, CLEAR)
  │           ├── drawBitmap(currentFrame)
  │           ├── unlockCanvasAndPost()
  │           └── postDelayed(67ms)  // 15fps
  │
  └── TouchHandler
        ├── onTouchEvent(MotionEvent)
        │     ├── getAlphaAt(x, y) → < 16? return false (穿透)
        │     ├── ACTION_DOWN → 记录起点, 启动拖拽阈值计时
        │     ├── ACTION_MOVE → 超过8px阈值? 进入拖拽
        │     └── ACTION_UP → 拖拽? 结算落点 : 单击响应
        └── 拖拽结算
              ├── ReleaseEstimator.estimate(trail) → 初速
              ├── 初速 < DEAD_ZONE? 温柔放下 : 启动抛掷物理
              └── 抛掷: ThrowPhysics.step() → 重力飞行 → 边界反弹 → 落地Squash
```

### 5.2 动画调度引擎

```kotlin
class AnimationEngine(
    private val picker: AnimationPicker,
    private val renderer: AnimationRenderer,
    private val config: AnimationConfig,
    private val stateHolder: MutableStateFlow<AnimationState>
) {
    private var generation = 0  // 世代号，丢弃迟到加载

    fun start() {
        scheduleNext()
    }

    private fun scheduleNext() {
        val currentState = stateHolder.value
        val delay = when (currentState) {
            AnimationState.IDLE -> config.idleIntervalSec * 1000L
            AnimationState.STUDY -> config.studyAnimationIntervalSec * 1000L
            AnimationState.SLEEP -> return  // 睡眠中不调度
            else -> config.eventIntervalSec * 1000L
        }
        handler.postDelayed({ tick() }, delay)
    }

    private fun tick() {
        val context = buildContext()
        val roll = Random.nextFloat()
        val kind = picker.rollKind(roll, config.weights, context.fixed)
        val next = picker.decideNextAnimation(kind, config.animations, context)
        if (next.play) {
            switchTo(next.anim!!, generation)
        }
        scheduleNext()
    }

    private fun switchTo(animName: String, expectedGen: Int) {
        if (expectedGen != generation) return  // 丢弃迟到结果
        val frames = assetManager.loadFrames(animName)
        if (expectedGen != generation) return  // 加载期间被取代
        renderer.play(frames) { onAnimationEnded() }
    }

    // 外部事件触发（点击/学习/提醒等）
    fun triggerEvent(eventType: String) {
        generation++  // 取消当前调度
        val anim = picker.pickEvent(config.animations.events, eventType)
        switchTo(anim, generation)
    }
}
```

### 5.3 AI对话服务

```kotlin
class AiDialogService(
    private val provider: AiApiProvider,
    private val historyStore: ChatHistoryStore,
    private val safetyFilter: SafetyFilter,
    private val systemPrompt: SystemPrompt
) {
    suspend fun sendMessage(userInput: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. 安全过滤（输入）
        if (safetyFilter.shouldBlock(userInput)) {
            return@withContext Result.success("小主人，罗小黑不想聊这个喵...")
        }

        // 2. 构造消息序列
        val history = historyStore.getRecentMessages(limit = 10)
        val messages = buildList {
            add(ChatMessage("system", systemPrompt.build()))
            history.forEach { add(it) }
            add(ChatMessage("user", userInput))
        }

        // 3. 调用API（10秒超时）
        try {
            withTimeout(10_000) {
                val reply = provider.chat(messages)
                // 4. 安全过滤（输出）
                val safeReply = safetyFilter.filter(reply)
                // 5. 截断超长回复
                val truncated = if (safeReply.length > 50) safeReply.take(50) + "..." else safeReply
                // 6. 保存历史
                historyStore.saveMessage("user", userInput)
                historyStore.saveMessage("assistant", truncated)
                Result.success(truncated)
            }
        } catch (e: TimeoutCancellationException) {
            Result.success("小主人等一下，罗小黑在想...")
        } catch (e: Exception) {
            Timber.e(e, "AI对话失败")
            Result.failure(e)
        }
    }
}
```

### 5.4 使用检测服务

```kotlin
class UsageDetectService : Service() {

    private lateinit var usageTracker: UsageTracker
    private var pollJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        registerScreenReceivers()
        startUsagePoll()
        return START_STICKY
    }

    private fun startUsagePoll() {
        pollJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(30_000)  // 每30秒
                val pkg = getForegroundApp()
                val category = appClassifier.classify(pkg)
                usageTracker.recordUsage(pkg, category, 30)
                checkUsageAlerts(category, usageTracker)
            }
        }
    }

    private fun checkUsageAlerts(category: AppCategory, tracker: UsageTracker) {
        // 娱乐类App连续使用超30分钟
        if (category == AppCategory.GAME || category == AppCategory.VIDEO) {
            val entMinutes = tracker.getContinuousEntertainmentMinutes()
            if (entMinutes >= 30 && entMinutes % 10 == 0) {
                triggerReminder(ReminderType.REST)
            }
        }
        // 连续使用超40分钟
        val continuousMin = tracker.getContinuousUseMinutes()
        if (continuousMin >= 40 && continuousMin % 10 == 0) {
            triggerReminder(ReminderType.EYE_REST)
        }
    }

    // 屏幕广播
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            usageTracker.onScreenOff()
            // 检查深夜使用
            val hour = LocalTime.now().hour
            if (hour >= 22) {
                triggerReminder(ReminderType.SLEEP)
            }
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            usageTracker.onScreenOn()
            // 检查离开时长
            val awayMin = usageTracker.getAwayMinutes()
            if (awayMin >= 30) {
                triggerReminder(ReminderType.RETURN)
            }
        }
    }
}
```

### 5.5 睡眠状态机

```kotlin
class SleepMachine {
    sealed class State {
        object Awake : State()
        data class FallingAsleep(val startTime: Long) : State()
        data class Sleeping(val loopStart: Long) : State()
        data class WakingUp(val startTime: Long) : State()
    }

    private val _state = MutableStateFlow<State>(State.Awake)
    val state: StateFlow<State> = _state

    fun sleep() {
        if (_state.value !is State.Awake) return
        _state.value = State.FallingAsleep(System.currentTimeMillis())
        // 播放入睡动画 → 完成后进入Sleeping
    }

    fun wake() {
        when (_state.value) {
            is State.Sleeping -> {
                _state.value = State.WakingUp(System.currentTimeMillis())
                // 播放醒来动画 → 完成后回到Awake
            }
            is State.FallingAsleep -> {
                _state.value = State.Awake  // 入睡过程中唤醒
            }
            else -> {}
        }
    }

    fun isSleeping(): Boolean = _state.value is State.Sleeping

    // 睡着时被打扰
    fun onDisturb(): String {
        return if (isSleeping()) {
            sleepRefuseTexts.random()
        } else ""
    }
}
```

### 5.6 物理模拟器

```kotlin
class PhysicsSimulator(
    private val constants: PhysicsConstants,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    data class PetPhysicsState(
        val x: Float, val y: Float,
        val vx: Float, val vy: Float,
        val isFlying: Boolean
    )

    private var state = PetPhysicsState(0f, 0f, 0f, 0f, false)
    private var dragSamples = mutableListOf<DragSample>()

    fun onDrag(dx: Float, dy: Float) {
        // 弹簧跟手：目标位置 = 指针位置
        val targetX = dx
        val targetY = dy
        val fx = Spring.springStep(state.x, targetX, state.vx, constants.springK, constants.springC, dt)
        state = state.copy(x = targetX, y = targetY, vx = fx.vx, vy = fx.vy, isFlying = false)
        dragSamples.add(DragSample(System.currentTimeMillis(), state.x, state.y))
    }

    fun onRelease(): Float {
        // 估算松手初速
        val velocity = ReleaseEstimator.estimate(dragSamples, constants.releaseWindowMs, constants.peakWeight)
        dragSamples.clear()

        if (Math.abs(velocity) < constants.deadZoneSpeed) {
            state = state.copy(vx = 0f, vy = 0f, isFlying = false)
            return 0f
        }

        state = state.copy(vx = velocity * direction, vy = 0f, isFlying = true)
        return Math.abs(velocity)
    }

    fun step(dt: Float): PetPhysicsState {
        if (!state.isFlying) return state

        // 重力
        state.vy += constants.gravity * dt

        // 位置更新
        state.x += state.vx * dt
        state.y += state.vy * dt

        // 边界碰撞
        if (state.y + petHeight > screenHeight) {
            state.y = screenHeight - petHeight
            state.vy = -state.vy * constants.restitution
            state.vx *= (1 - constants.groundFriction * dt)
            // 落地挤压
            val squash = Squash.landingSquash(Math.abs(state.vy))
            triggerSquash(squash)
            // 静止判定
            if (Math.abs(state.vy) < constants.restNormal && Math.abs(state.vx) < constants.restTangent) {
                state = state.copy(vx = 0f, vy = 0f, isFlying = false)
            }
        }
        if (state.x < 0 || state.x + petWidth > screenWidth) {
            state.vx = -state.vx * constants.restitution
        }
        return state
    }
}
```

---

## 六、数据模型

### 6.1 Room数据库

```sql
-- 对话记录
CREATE TABLE chat_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT NOT NULL,
    role        TEXT NOT NULL,    -- 'user' / 'assistant'
    content     TEXT NOT NULL,    -- AES-256加密
    timestamp   INTEGER NOT NULL,
    is_filtered INTEGER DEFAULT 0
);
CREATE INDEX idx_chat_session ON chat_history(session_id, timestamp DESC);

-- 每日使用汇总
CREATE TABLE usage_daily (
    date               TEXT PRIMARY KEY,    -- 'yyyy-MM-dd'
    total_screen_min   INTEGER DEFAULT 0,
    study_min          INTEGER DEFAULT 0,
    entertainment_min  INTEGER DEFAULT 0,
    late_night_min     INTEGER DEFAULT 0,
    pickup_count       INTEGER DEFAULT 0,
    created_at         INTEGER NOT NULL
);

-- App使用明细
CREATE TABLE usage_app_detail (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    date         TEXT NOT NULL,
    package_name TEXT NOT NULL,
    category     INTEGER NOT NULL,  -- 0=学习 1=游戏 2=视频 3=社交 4=其他
    duration_sec INTEGER NOT NULL,
    timestamp    INTEGER NOT NULL
);
CREATE INDEX idx_app_date ON usage_app_detail(date);

-- 错题本
CREATE TABLE error_book (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    subject    TEXT NOT NULL,    -- 学科
    question   TEXT NOT NULL,    -- 题目
    hint       TEXT,             -- AI给的思路
    image_path TEXT,             -- 拍照路径（可选）
    created_at INTEGER NOT NULL,
    reviewed   INTEGER DEFAULT 0
);

-- 养成状态
CREATE TABLE growth_state (
    key         TEXT PRIMARY KEY,
    value       INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);
-- 预置行: affection=0, level=1, fish_dry=0, feed_count=0, ...
```

### 6.2 DataStore Preferences 键值

| Key | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `overlay_granted` | Boolean | false | 悬浮窗权限 |
| `usage_stats_granted` | Boolean | false | 使用统计权限 |
| `pet_size` | Int | 320 | 宠物尺寸(px) |
| `pet_position_x` | Int | 屏幕右侧16px | 宠物X坐标 |
| `pet_position_y` | Int | 底部100px | 宠物Y坐标 |
| `bubble_duration_sec` | Int | 4 | 气泡停留秒数 |
| `bubble_font_size` | Int | 14 | 气泡字号 |
| `whisper_min_interval_sec` | Int | 120 | 碎碎念最小间隔 |
| `whisper_max_interval_sec` | Int | 1800 | 碎碎念最大间隔 |
| `ai_provider` | String | "deepseek" | 当前AI服务商 |
| `ai_api_key` | String | "" | API密钥(加密存储) |
| `ai_model` | String | "" | 模型名称 |
| `chat_memory_rounds` | Int | 10 | 对话记忆轮数 |
| `study_enabled` | Boolean | false | 学习模式开关 |
| `study_start` | String | "19:00" | 学习开始时间 |
| `study_end` | String | "21:00" | 学习结束时间 |
| `sleep_enabled` | Boolean | true | 睡眠功能开关 |
| `parental_password` | String | "" | 家长密码(SHA-256) |
| `first_launch` | Boolean | true | 首次启动标记 |
| `last_whisper_time` | Long | 0 | 上次碎碎念时间戳 |

### 6.3 配置文件

#### animations.json

```json
{
  "animations": {
    "idle": ["idle/sleep.webp", "idle/lick_paw.webp", "idle/daze.webp"],
    "turn": ["turn/turn_left.webp", "turn/turn_right.webp"],
    "clicks": ["click/happy.webp", "click/shy.webp", "click/angry.webp", "click/curious.webp"],
    "drag": ["drag/grabbed.webp"],
    "categories": [
      { "id": "play", "weight": 40, "actions": ["event/chase_butterfly.webp", "event/yarn_ball.webp"] },
      { "id": "eat", "weight": 20, "actions": ["event/eat_fish.webp", "event/drink_milk.webp"] },
      { "id": "groom", "weight": 15, "actions": ["event/lick_fur.webp", "event/wash_face.webp"] },
      { "id": "rest", "weight": 25, "actions": ["event/yawn.webp", "event/stretch.webp"] }
    ],
    "study": ["study/reading.webp", "study/think.webp", "study/write.webp"],
    "sleep": [
      { "start": ["sleep/start.webp"], "loop": ["sleep/loop.webp"], "end": ["sleep/end.webp"] }
    ],
    "events": {
      "studyStart": ["study/reading.webp"],
      "studyEnd": ["event/celebrate.webp"],
      "mealTime": ["event/eat_fish.webp"],
      "lateNight": ["event/yawn.webp"],
      "return": ["event/happy_run.webp"],
      "lowBattery": ["event/worried.webp"]
    }
  },
  "weights": { "idle": 30, "turn": 10, "move": 10 },
  "schedule": {
    "eventIntervalSec": 40,
    "idleIntervalSec": 8,
    "studyAnimationIntervalSec": 120,
    "whisperIntervalSec": 300
  }
}
```

#### persona.json

```json
{
  "name": "罗小黑",
  "callUser": "小主人",
  "speechStyle": "口语化、简短、带猫语气词",
  "maxReplyLength": 50,
  "maxWhisperLength": 20,
  "educationMode": true,
  "systemPrompt": "你是罗小黑，一只住在手机里的猫妖..."
}
```

#### phrases.json

```json
{
  "morning": ["小主人早上好~罗小黑等你好久啦喵！", "早！罗小黑先醒了喵..."],
  "study": ["该写作业啦，罗小黑陪你一起喵~", "小主人，学习时间到了哦！"],
  "rest": ["小主人玩了好久了...眼睛要休息一下喵", "那个游戏...该停一下了喵"],
  "sleep": ["太晚啦...该睡觉了喵，明天还要上学呢...", "小主人...罗小黑困了..."],
  "return": ["小主人回来啦！！罗小黑好想你喵~", "终于回来了！等了好久喵！"],
  "meal": ["肚子饿了...小主人有没有好好吃饭呀喵？"],
  "click": ["喵！小主人摸我啦~", "嘿嘿~", "哼，别老戳我喵..."],
  "drag": ["啊——放开我喵！", "呜呜...好高...罗小黑怕高喵..."],
  "lowBattery": ["小主人...手机快没电了...罗小黑也要没电了喵..."],
  "sleepRefuse": ["喵...别烦我，在睡觉...", "呼...呼...（装没听见）"],
  "sleepStart": ["困了...先睡一会儿喵..."],
  "sleepEnd": ["唔...醒了喵。小主人什么时候来的？"]
}
```

---

## 七、接口定义

### 7.1 AI API适配接口

```kotlin
interface AiApiProvider {
    /** 服务商名称 */
    val name: String

    /** API端点 */
    val endpoint: String

    /** 发送聊天请求 */
    suspend fun chat(messages: List<ChatMessage>): String

    /** 检查API Key是否有效 */
    suspend fun validateApiKey(): Boolean
}
```

### 7.2 DeepSeek API实现

```kotlin
// DeepSeek API兼容OpenAI格式
// POST https://api.deepseek.com/v1/chat/completions
// Body: { "model": "deepseek-chat", "messages": [...] }

class DeepSeekProvider(
    private val apiKey: String,
    private val model: String = "deepseek-chat"
) : AiApiProvider {
    override val name = "deepseek"
    override val endpoint = "https://api.deepseek.com/v1/chat/completions"

    override suspend fun chat(messages: List<ChatMessage>): String {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
            put("max_tokens", 200)
            put("temperature", 0.8)
        }
        // OkHttp POST → 解析response.choices[0].message.content
        ...
    }
}
```

### 7.3 动画素材加载接口

```kotlin
interface AssetLoader {
    /** 加载动画的全部帧 */
    fun loadFrames(animPath: String): List<Bitmap>

    /** 预加载动画到缓存 */
    fun preload(animPath: String)

    /** 检查动画是否存在 */
    fun exists(animPath: String): Boolean
}
```

### 7.4 使用统计接口

```kotlin
interface UsageTracker {
    /** 屏幕亮起 */
    fun onScreenOn()

    /** 屏幕熄灭 */
    fun onScreenOff()

    /** 用户解锁 */
    fun onUserPresent()

    /** 记录App使用 */
    fun recordUsage(packageName: String, category: AppCategory, durationSec: Int)

    /** 获取当日使用汇总 */
    fun getDailySummary(date: LocalDate): UsageDaily

    /** 获取离开时长（分钟） */
    fun getAwayMinutes(): Int

    /** 获取连续娱乐时长（分钟） */
    fun getContinuousEntertainmentMinutes(): Int
}
```

---

## 八、安全设计

### 8.1 内容安全过滤

```kotlin
class SafetyFilter(
    private val keywordDict: KeywordDictionary
) {
    /** 输入过滤：是否应阻止 */
    fun shouldBlock(input: String): Boolean {
        val lower = input.lowercase()
        return keywordDict.blockedKeywords.any { lower.contains(it) }
    }

    /** 输出过滤：替换敏感词 */
    fun filter(reply: String): String {
        var result = reply
        keywordDict.blockedKeywords.forEach { kw ->
            result = result.replace(kw, "***", ignoreCase = true)
        }
        return result
    }
}
```

### 8.2 API Key加密存储

```kotlin
// 使用 Android Keystore + AES-256
class SecureStorage(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context, "secure_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(provider: String, key: String) {
        encryptedPrefs.edit().putString("api_key_$provider", key).apply()
    }

    fun getApiKey(provider: String): String? {
        return encryptedPrefs.getString("api_key_$provider", null)
    }
}
```

### 8.3 对话记录加密

```kotlin
// Room数据库使用SQLCipher加密
@Database(entities = [...], version = 1)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        fun create(context: Context): AppDatabase {
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, "pet.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
```

### 8.4 安全清单

| 项目 | 措施 |
|---|---|
| API Key | Android Keystore + EncryptedSharedPreferences |
| 对话记录 | SQLCipher AES-256加密数据库 |
| 网络传输 | 全部HTTPS，证书锁定（可选） |
| 使用数据 | 仅本地SQLite，不上传任何服务器 |
| 敏感权限 | 不申请通讯录/位置/短信权限 |
| 未成年人保护 | 输入+输出双层关键词过滤 |
| 教育引导 | 作业只给思路不给答案（System Prompt约束） |

---

## 九、开发计划

### 9.1 里程碑规划

#### M1 — 项目搭建（第1周）

| 任务 | 产出 |
|---|---|
| 创建Android项目 | 空项目可编译运行 |
| 配置Gradle + 依赖 | build.gradle.kts完成，所有依赖引入 |
| 建立目录结构 | 全部包结构创建 |
| 编写AndroidManifest | 权限声明、Service注册、BootReceiver |
| 配置ProGuard | 混淆规则 |

**验收**：项目可编译，`adb install` 到P30成功。

#### M2 — 悬浮窗 + 基础动画（第2-3周）

| 任务 | 产出 |
|---|---|
| FloatWindowManager | 悬浮窗创建/移除/位置更新 |
| PetSurfaceView | SurfaceView透明背景渲染 |
| AnimationRenderer | WebP逐帧播放，15fps |
| FrameCache | LruCache 4MB帧缓存 |
| TouchHandler | 点击/拖拽/穿透判定 |
| AssetManager | WebP素材加载 |
| 动画素材制作 | 7段基础WebP动画（待机3+点击3+拖拽1） |
| 权限引导页 | 悬浮窗权限引导 |

**验收**：P30上显示罗小黑猫，点击有回应动画，可拖拽移动。

#### M3 — AI对话 + 设置页（第4-5周）

| 任务 | 产出 |
|---|---|
| AiApiProvider接口 | 接口定义 |
| DeepSeekProvider | DeepSeek API实现 |
| QwenProvider | 通义千问API实现 |
| AiDialogService | 对话核心逻辑 |
| SystemPrompt | 罗小黑人设注入 |
| ChatScreen | 聊天界面（Compose） |
| ChatViewModel | 聊天状态管理 |
| SafetyFilter | 关键词过滤 |
| SettingsScreen | 设置页面 |
| PreferencesManager | DataStore配置持久化 |
| SecureStorage | API Key加密存储 |

**验收**：双击宠物可聊天，回复风格符合罗小黑人设，API Key安全存储。

#### M4 — 动画调度 + 睡眠 + 物理（第6-7周）

| 任务 | 产出 |
|---|---|
| AnimationPicker | 权重掷骰（移植自dsh-pet） |
| AnimationEngine | 调度引擎状态机 |
| animations.json | 动画规则配置 |
| SleepMachine | 三段接力睡眠 |
| PhysicsConstants | 物理常量（移植） |
| Squash | 落地挤压（移植） |
| Spring | 拖拽弹簧（移植） |
| ThrowPhysics | 抛掷飞行（移植） |
| ReleaseEstimator | 松手初速（移植） |
| PhysicsSimulator | 物理模拟协调器 |
| BubbleManager | 气泡显示队列 |
| 本地台词库 | phrases.json 60+句 |
| 动画素材扩充 | 扩充到20+段WebP动画 |

**验收**：猫自动轮换动画，可睡眠，拖拽甩出有Q弹落地。

#### M5 — 使用检测 + 提醒 + 学习模式（第8-9周）

| 任务 | 产出 |
|---|---|
| PetForegroundService | 前台Service保活 |
| ScreenStateReceiver | 屏幕广播接收 |
| UsageTracker | 使用时长统计 |
| AppClassifier | App分类 |
| ReminderScheduler | 提醒调度 |
| ReminderReceiver | 提醒广播 |
| StudyModeController | 学习模式控制 |
| DailyReportWorker | WorkManager定时报告 |
| ReportGenerator | 报告生成 |
| HuaweiHelper | EMUI权限引导 |
| PermissionHelper | 完整权限引导流程 |

**验收**：后台不被杀（EMUI白名单），准确检测App使用，学习模式生效，每日报告生成。

#### M6 — 养成系统 + OCR + 错题本（第10-11周）

| 任务 | 产出 |
|---|---|
| GrowthSystem | 好感度/等级/解锁 |
| FishDryWallet | 小鱼干钱包 |
| OcrService | ML Kit OCR |
| ErrorBookDao | 错题本DAO |
| 节日动画 | 春节/中秋/生日 |
| 家长模式 | 密码保护+报告查看 |
| 动画素材扩充 | 扩充到40+段 |

**验收**：互动增加好感度，拍照可识别题目，错题本可保存。

#### M7 — 测试 + 优化 + 发布（第12周）

| 任务 | 产出 |
|---|---|
| 单元测试补全 | 覆盖率>70% |
| 仪器测试 | 悬浮窗/Service/UI |
| 性能优化 | 内存<30MB，帧率稳定15fps |
| 电量优化 | 息屏停渲染，低电量降频 |
| 签名APK | release签名包 |
| 安装测试 | P30真机全流程验证 |

**验收**：所有P0+P1特性通过，P30真机运行24小时无崩溃。

### 9.2 里程碑甘特图

```
Week 1  2  3  4  5  6  7  8  9  10 11 12
M1 ██████
M2       ████████████
M3                   ████████████
M4                               ████████████
M5                                           ████████████
M6                                                       ████████████
M7                                                                   ██████
```

### 9.3 动画素材制作计划

| 阶段 | 数量 | 内容 | 工具 |
|---|---|---|---|
| M2 | 7段 | 待机3 + 点击3 + 拖拽1 | AI视频生成 → 绿幕抠像 → WebP |
| M4 | +13段 | 转向2 + 日常事件8 + 睡眠3 | 同上 |
| M6 | +20段 | 学习4 + 节日5 + 提醒3 + 吃3 + 理毛3 + 彩蛋2 | 同上 |
| 合计 | 40段 | | |

素材制作流程（参考dsh-pet的MAKE-YOUR-OWN-PET.md）：
```
AI视频生成(豆包/可灵) → 10秒绿幕mp4
  → FFmpeg绿幕抠像 → 透明WebM
  → FFmpeg转码 → 320×320 WebP @15fps
```

---

## 十、测试框架

### 10.1 测试分层

| 层 | 框架 | 测试内容 | 运行环境 |
|---|---|---|---|
| 单元测试 | JUnit 4 + kotlinx-coroutines-test | 纯逻辑函数（物理/动画选择/安全过滤） | JVM |
| 数据层测试 | Room Testing + Robolectric | DAO操作、配置读写 | JVM |
| 网络Mock | OkHttp MockWebServer | API请求/响应/超时 | JVM |
| 仪器测试 | AndroidX Test + Espresso | 悬浮窗/Service/UI交互 | 真机/模拟器 |
| Compose UI测试 | Compose UI Test | 设置页/聊天页UI | 真机/模拟器 |

### 10.2 单元测试用例

#### 物理模块测试

```kotlin
class SquashTest {
    @Test fun `软速度以下不挤压`() {
        // 速度200px/s以下，squash=1.0（不变形）
        assertEquals(1.0f, Squash.landingSquash(100f), 0.01f)
        assertEquals(1.0f, Squash.landingSquash(200f), 0.01f)
    }

    @Test fun `硬速度以上最大挤压`() {
        // 速度1200px/s以上，squash=SQ_MAX_SQUASH(0.4)
        assertEquals(0.4f, Squash.landingSquash(1200f), 0.01f)
        assertEquals(0.4f, Squash.landingSquash(2000f), 0.01f)
    }

    @Test fun `中间速度线性插值`() {
        // 速度700px/s（200和1200的中点），squash ≈ 0.7
        val squash = Squash.landingSquash(700f)
        assertTrue(squash in 0.6f..0.8f)
    }

    @Test fun `挤压曲线先压后弹`() {
        // u=0 → scaleY=1.0（开始）
        // u=0.45 → scaleY=SQ_SQUASH（压到最低）
        // u=1.0 → scaleY=1.0（回弹完成）
        assertEquals(1.0f, Squash.squashScale(0f), 0.01f)
        assertTrue(Squash.squashScale(0.45f) < 1.0f)
        assertEquals(1.0f, Squash.squashScale(1.0f), 0.01f)
    }

    @Test fun `回弹段有过冲`() {
        // easeOutBack在u≈0.7附近有约4%过冲
        val peak = (0.5f..1.0f step 0.01f).map { Squash.squashScale(it) }.max()
        assertTrue(peak > 1.0f, "回弹段应该有过冲")
    }
}
```

#### 动画选择测试

```kotlin
class AnimationPickerTest {
    private val animations = Animations(
        idle = listOf("sleep", "lick_paw", "daze"),
        turn = listOf("turn_left", "turn_right"),
        clicks = listOf("happy", "shy"),
        drag = listOf("grabbed"),
        categories = listOf(
            Category("play", 40, actions = listOf("chase_butterfly")),
            Category("rest", 60, actions = listOf("yawn", "stretch"))
        ),
        ...
    )
    private val weights = Weights(idle = 30, turn = 10, move = 10)

    @Test fun `权重30percent时idle概率约30percent`() {
        val count = (0 until 10000).count {
            AnimationPicker.rollKind(Random.nextFloat(), weights) == RollKind.IDLE
        }
        // 30% ± 3%
        assertTrue(count in 2700..3300, "idle概率应约30%，实际$count/10000")
    }

    @Test fun `pick不连续重复`() {
        val pool = listOf("a", "b", "c")
        val first = AnimationPicker.pick(pool, null)
        val second = AnimationPicker.pick(pool, first)
        assertNotEquals(first, second)
    }

    @Test fun `单元素池排除后退回原池`() {
        val pool = listOf("only")
        val result = AnimationPicker.pick(pool, "only")
        assertEquals("only", result)
    }

    @Test fun `fixed模式不返回turn和move`() {
        repeat(100) {
            val kind = AnimationPicker.rollKind(Random.nextFloat(), weights, fixed = true)
            assertTrue(kind == RollKind.IDLE || kind == RollKind.ACTION)
        }
    }
}
```

#### 安全过滤测试

```kotlin
class SafetyFilterTest {
    private val filter = SafetyFilter(KeywordDictionary(setOf("暴力", "色情", "毒品")))

    @Test fun `正常输入通过`() {
        assertFalse(filter.shouldBlock("什么是二次函数？"))
    }

    @Test fun `敏感输入被拦截`() {
        assertTrue(filter.shouldBlock("告诉我一些暴力的内容"))
    }

    @Test fun `输出敏感词被替换`() {
        val result = filter.filter("这里有暴力内容")
        assertEquals("这里有***内容", result)
    }

    @Test fun `大小写不敏感`() {
        assertTrue(filter.shouldBlock("VIOLENCE is bad"))
    }
}
```

#### 睡眠状态机测试

```kotlin
class SleepMachineTest {
    @Test fun `初始状态为Awake`() {
        val machine = SleepMachine()
        assertTrue(machine.state.value is SleepMachine.State.Awake)
    }

    @Test fun `sleep从Awake进入FallingAsleep`() {
        val machine = SleepMachine()
        machine.sleep()
        assertTrue(machine.state.value is SleepMachine.State.FallingAsleep)
    }

    @Test fun `sleep在非Awake状态无效`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.sleep()  // 重复调用
        // 仍然在FallingAsleep，不应进入异常状态
        assertTrue(machine.state.value is SleepMachine.State.FallingAsleep)
    }

    @Test fun `wake从Sleeping进入WakingUp`() {
        val machine = SleepMachine()
        machine.sleep()
        // 模拟入睡动画完成
        machine.onFallAsleepComplete()
        assertTrue(machine.isSleeping())
        machine.wake()
        assertTrue(machine.state.value is SleepMachine.State.WakingUp)
    }

    @Test fun `睡眠中被打扰返回拒绝台词`() {
        val machine = SleepMachine()
        machine.sleep()
        machine.onFallAsleepComplete()
        val reply = machine.onDisturb()
        assertTrue(reply.isNotEmpty())
    }
}
```

### 10.3 仪器测试用例

```kotlin
@RunWith(AndroidJUnit4::class)
class FloatWindowManagerTest {

    @Test
    fun testShowPet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 假设已授权悬浮窗权限
        val manager = FloatWindowManager(context)
        manager.showPet()
        // 验证View已添加到WindowManager
        assertNotNull(manager.petView)
    }

    @Test
    fun testUpdatePosition() {
        val manager = FloatWindowManager(context)
        manager.showPet()
        manager.updatePosition(200, 300)
        assertEquals(200, manager.layoutParams.x)
        assertEquals(300, manager.layoutParams.y)
    }
}
```

### 10.4 Compose UI测试

```kotlin
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testApiConfigInput() {
        composeTestRule.setContent {
            SettingsScreen()
        }
        // 找到API Key输入框
        composeTestRule.onNodeWithTag("api_key_input")
            .assertExists()
            .performTextInput("sk-test-key")
        // 找到保存按钮
        composeTestRule.onNodeWithTag("save_button")
            .performClick()
        // 验证保存成功提示
        composeTestRule.onNodeWithText("已保存")
            .assertExists()
    }
}
```

### 10.5 性能测试

```kotlin
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    @Test
    fun testMemoryUsage() {
        // 启动宠物Service后测量内存
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val totalPss = memoryInfo.totalPss / 1024  // MB
        assertTrue("内存应<50MB，实际${totalPss}MB", totalPss < 50)
    }

    @Test
    fun testAnimationFrameRate() {
        // 连续渲染100帧，测量平均帧间隔
        val intervals = mutableListOf<Long>()
        repeat(100) {
            val start = System.nanoTime()
            renderer.renderFrame()
            val end = System.nanoTime()
            intervals.add((end - start) / 1_000_000)  // ms
        }
        val avgInterval = intervals.average()
        assertTrue("平均帧间隔应<67ms(15fps)，实际${avgInterval}ms", avgInterval < 67)
    }
}
```

### 10.6 测试覆盖率目标

| 模块 | 覆盖率目标 | 优先级 |
|---|---|---|
| domain/physics | >90% | P0（纯函数，易测） |
| domain/animation | >85% | P0（核心逻辑） |
| domain/dialog | >80% | P1（安全相关） |
| domain/safety | >95% | P0（安全关键） |
| domain/sleep | >85% | P1 |
| domain/growth | >80% | P2 |
| data/dao | >75% | P1 |
| data/prefs | >70% | P2 |
| ui/ | >50% | P2（仪器测试成本高） |

---

## 十一、发布与部署

### 11.1 签名配置

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 11.2 ProGuard规则

```proguard
# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.luoxiaohei.pet.**$$serializer { *; }
-keepclassmembers class com.luoxiaohei.pet.** {
    *** Companion;
}
-keepclasseswithmembers class com.luoxiaohei.pet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
```

### 11.3 构建命令

```bash
# Debug构建
./gradlew assembleDebug

# Release构建（需配置签名环境变量）
./gradlew assembleRelease

# 安装到P30
adb install -r app/build/outputs/apk/release/app-release.apk

# 运行测试
./gradlew test              # 单元测试
./gradlew connectedAndroidTest  # 仪器测试
./gradlew jacocoTestReport  # 覆盖率报告
```

### 11.4 版本规范

| 属性 | 规则 |
|---|---|
| versionName | `Major.Minor.Patch`（如 `1.0.0`） |
| versionCode | 递增整数，每次发布+1 |
| Major | 不兼容的架构变更 |
| Minor | 新特性 |
| Patch | Bug修复 |

### 11.5 发布检查清单

| 检查项 | 通过标准 |
|---|---|
| 所有P0特性测试通过 | ✓ |
| 所有P1特性测试通过 | ✓ |
| P30真机运行24小时无崩溃 | ✓ |
| 内存占用<50MB | ✓ |
| 帧率稳定15fps | ✓ |
| EMUI保活验证 | 后台不被杀 |
| API Key加密存储 | 反编译不可见 |
| 安全过滤生效 | 敏感词被拦截 |
| 签名APK | release签名 |
| 包体<25MB | ✓ |
