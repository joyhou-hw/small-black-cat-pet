# 罗小黑桌面宠物 (Luoxiaohei Pet)

面向华为P30 (Android 9) 的罗小黑主题桌面宠物应用，为初中女孩开发的AI陪伴宠物。

## 项目结构

```
small-black-cat-pet/
├── luoxiaohei-pet/          # Android 项目源码
│   ├── app/
│   │   ├── src/main/        # 主源码
│   │   │   ├── java/com/luoxiaohei/pet/
│   │   │   │   ├── data/          # 数据层 (Room DB, Config)
│   │   │   │   ├── domain/        # 领域层 (动画调度, 物理模拟, 睡眠状态机)
│   │   │   │   ├── service/       # 前台服务, 开机启动
│   │   │   │   ├── ui/            # 悬浮窗, 动画渲染, 气泡对话
│   │   │   │   ├── LuoxiaoheiApp.kt
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/               # 资源 (VectorDrawable素材, 主题, 字符串)
│   │   │   └── AndroidManifest.xml
│   │   ├── src/test/              # 单元测试
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
├── docs/                    # 开发文档
│   ├── luoxiaohei-requirements.md   # 需求分析
│   ├── luoxiaohei-design-spec.md    # 详细设计说明书
│   ├── luoxiaohei-architecture.md   # 架构设计
│   ├── luoxiaohei-dsh-pet-reuse.md  # dsh-pet代码复用分析
│   └── luoxiaohei-story.md          # 罗小黑世界观故事
└── README.md
```

## 开发环境

- JDK 17 (jdk-17.0.21+9)
- Gradle 8.2
- Android SDK (compileSdk 34, targetSdk 28, minSdk 28)
- Kotlin 1.9.10
- Android Gradle Plugin 8.1.0

## 架构概览

```
┌─────────────────────────────────────────┐
│         PetForegroundService            │
│  ┌───────────┐  ┌──────────────────┐   │
│  │ FloatWin  │  │ AnimationEngine  │   │
│  │ Manager   │  │  呼吸/眨眼/调度   │   │
│  │ 悬浮窗管理 │  │  世代号异步丢弃   │   │
│  └───────────┘  └──────────────────┘   │
│  ┌───────────┐  ┌──────────────────┐   │
│  │ BubbleView│  │ PhysicsSimulator │   │
│  │ 气泡对话   │  │ 弹簧拖拽/抛掷物理 │   │
│  └───────────┘  └──────────────────┘   │
│  ┌───────────────────────────────────┐ │
│  │       PetAnimationView            │ │
│  │  VectorDrawable渲染 + 8种动画状态  │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

## 动画状态

| 状态 | 说明 |
|------|------|
| IDLE | 待机呼吸 |
| HAPPY | 开心（点击回应） |
| SHY | 害羞（点击回应） |
| ANGRY | 傲娇（点击回应） |
| DRAGGING | 被拖拽中 |
| FALLING | 自由落体 |
| WALKING | 行走 |
| SLEEPING | 睡觉（Zzz效果） |

## 物理参数（移植自 dsh-pet）

| 参数 | 值 | 说明 |
|------|-----|------|
| SPRING_K | 200 | 拖拽弹簧刚度 |
| SPRING_C | 30 | 拖拽弹簧阻尼（过阻尼） |
| GRAVITY | 1400 px/s² | 抛掷重力 |
| RESTITUTION | 0.78 | 碰壁反弹恢复系数 |
| THROW_POWER | 0.8 | 抛掷力度（手机屏幕小，降于桌面端1.0） |

## 构建与测试

```bash
# 设置环境变量
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

# 构建 APK
gradle assembleDebug --no-daemon

# 运行单元测试
gradle testDebugUnitTest --no-daemon
```

## 开发里程碑

- **M1** ✅ 构建环境搭建 + Room数据库 + 单元测试 (22个测试通过)
- **M2** ✅ 核心功能开发：悬浮窗、动画渲染、调度引擎、物理模拟、气泡对话、Service整合
- **M3** 🔜 AI对话接入、学习辅导、使用统计、提醒功能

## 技术参考

桌面端宠物项目 [dsh-pet](https://github.com/dsh-mask/dsh-pet) 的物理引擎和动画调度逻辑移植到 Android 平台。

## License

MIT
