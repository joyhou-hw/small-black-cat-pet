# 罗小黑桌面宠物 — dsh-pet 复用分析与技术映射

> **文档版本**：v1.0
> **日期**：2026-11-05
> **基于**：dsh-pet 源码分析（已克隆至 `d:\workspace\261105-bigbulefish\dsh-pet`）

---

## 一、dsh-pet 架构分析

### 1.1 项目概况

| 属性 | 值 |
|---|---|
| 仓库 | https://github.com/PC2005-cloud/dsh-pet |
| 技术栈 | TypeScript + React（浏览器端）+ Electron（桌面端） |
| 架构分层 | host / client / shared 三层 |
| 动画格式 | WebM（VP9-Alpha），640×360，24fps |
| 动画数量 | 106 段 |
| 许可证 | MIT |

### 1.2 三层架构

```
dsh-pet/src/
├── host/       # 宿主侧（DSH插件环境）
│   ├── plugin/ # 插件注册、配置管理
│   ├── config/ # 配置验证、保存、常量
│   ├── anim.ts # 动画素材路由
│   └── desktop-pets.ts  # 桌面宠物管理
├── client/     # 客户端（浏览器/桌面渲染）
│   ├── pet/    # 宠物核心
│   │   ├── animation/  # 动画链（双缓冲换源、素材加载、播完决策）
│   │   ├── card/       # 宠物卡片（React组件、状态机、睡眠机）
│   │   └── motion/     # 物理交互（拖拽、飞行、碰撞、挤压）
│   ├── chat/  # 对话系统
│   ├── settings/ # 设置页
│   └── baifan/ # "吃白饭"小游戏
├── shared/     # 共享层（纯函数，两端共用）
│   ├── physics/  # 物理引擎（弹簧、挤压、抛掷、碰撞）
│   ├── pickers.ts # 随机选择逻辑（权重掷骰、池抽取）
│   ├── types.ts   # 类型模型（与config.jsonc同构）
│   └── constants.ts
└── standalone/  # 独立运行模式（不依赖DSH）
```

### 1.3 核心可复用模块

#### A. 动画调度引擎（shared/pickers.ts）

dsh-pet 的动画选择是纯函数设计，**与平台无关**，可直接移植到 Kotlin：

| 函数 | 作用 | 复用价值 |
|---|---|---|
| `rollKind(roll, weights, opts)` | 按权重掷骰：idle/turn/move/action | ★★★ 核心调度 |
| `pick(pool, exclude)` | 从池中等概率抽取，避开连续重复 | ★★★ 动画选择 |
| `pickWeightedCategory(categories, facing)` | 按权重选分类，支持镜像过滤 | ★★☆ 分类动画 |
| `pickSleepSet(sets)` | 随机选一套睡觉动作（入/循环/醒） | ★★☆ 睡眠系统 |
| `pickSlot(slot, exclude)` | 事件档位取值（单候选/多候选） | ★★☆ 事件触发 |
| `nextWorkStatusAnim(pool, current)` | 工作状态轮换 | ★☆☆ 学习状态联动 |

**掷骰逻辑**（可直接翻译为 Kotlin）：
```
权重分配：idle(10) + turn(5) + move(5) = 20%，剩余 80% 归 action
roll ∈ [0,1)
  roll < idle/100    → 待机
  roll < (idle+turn)/100 → 转向
  roll < (idle+turn+move)/100 → 移动
  其余 → 分类动作
```

#### B. 物理引擎（shared/physics/）

dsh-pet 的物理是**纯函数 + 常量**设计，无 DOM/React 依赖：

| 模块 | 作用 | 复用价值 |
|---|---|---|
| `constants.ts` | 重力=1400px/s²、弹性=0.78、摩擦=2.5/s | ★★★ 直接用数值 |
| `squash.ts` | 落地挤压曲线（easeOutBack回弹4%过冲） | ★★★ Q弹手感 |
| `spring.ts` | 拖拽弹簧（K=200, C=30, 过阻尼跟手） | ★★★ 拖拽手感 |
| `throw.ts` | 抛掷物理（重力飞行、边界反弹、步进积分） | ★★☆ 甩猫玩法 |
| `release.ts` | 松手初速估算（轨迹窗口+峰值权重） | ★★☆ 甩猫手感 |
| `collide.ts` | 多宠物碰撞检测 | ☆☆☆ 单宠物不需要 |

**关键物理参数**：
```
重力: 1400 px/s²
弹性: 0.78 (每次反弹保留78%速度)
摩擦: 2.5 /s (接触面切向衰减)
弹簧K: 200 (拖拽跟手刚度)
弹簧C: 30 (阻尼, 过阻尼不overshoot)
挤压软速度: 200 px/s (低于此不压扁)
挤压硬速度: 1200 px/s (达到此压到最大0.4)
```

#### C. 动画链状态机（client/pet/animation/）

| 设计 | 说明 | 复用价值 |
|---|---|---|
| A/B双缓冲换源 | 交叉淡入，切换无空白帧 | ★★★ 动画切换 |
| 世代号(generation) | 丢弃迟到加载，避免错乱 | ★★☆ 异步安全 |
| 播完决策(cues) | 播完后决定：继续循环/切下一个/冻结 | ★★★ 状态机 |
| 素材地址拼装 | 素材根+动画名+扩展名 | ★★☆ 素材管理 |

#### D. 配置系统（shared/types.ts + config.jsonc）

dsh-pet 的配置结构非常成熟，可直接参考：

| 配置段 | 说明 | 复用价值 |
|---|---|---|
| `pets[]` | 多宠物实例（id/size/position/display） | ★★☆ |
| `animations` | 动画池（idle/turn/clicks/drag/moves/categories/events/sleep） | ★★★ |
| `animationWeights` | 权重（idle/turn/move） | ★★★ |
| `physics` | 物理参数（gravity/restitution/friction/throwPower） | ★★★ |
| `whisperPrompt` | 人设系统提示词 | ★★★ AI人设 |
| `sleepRefuseTexts/StartTexts/EndTexts` | 睡眠台词 | ★★☆ |
| `chatMemoryRounds` | 对话记忆轮数 | ★★★ |
| `memes` | 表情包映射 | ★☆☆ |
| `font` | 字体配置 | ★★☆ |

#### E. 睡眠系统（client/pet/card/sleep-machine.ts）

三段接力睡眠设计非常适合宠物：
```
入睡动画(start) → 循环动画(loop, 无限) → 醒来动画(end)
```
- 睡着时完全安静：不自动动画、不碎碎念、不被打扰
- 被打扰时只回一句"我在睡觉"
- 可配多套睡姿（蹲着睡、趴着睡）

#### F. 素材生成链（scripts/）

完整可复现的素材制作流程：

```
AI视频生成（绿幕mp4）
  → step01: 水印遮罩填充
  → step02: 绿幕抠像 → 透明webm（或PR手工抠像）
  → step03: 归一化（统一2160×1215，人物居中脚底对齐）
  → step04: 转码640×360播放变体
```

---

## 二、复用映射：dsh-pet → 罗小黑 Android

### 2.1 直接复用（纯逻辑，翻译为 Kotlin）

| dsh-pet 模块 | TypeScript → Kotlin 映射 | 改动量 |
|---|---|---|
| `shared/pickers.ts` | `AnimationPicker.kt` | 纯函数，直接翻译 |
| `shared/physics/constants.ts` | `PhysicsConstants.kt` | 常量值直接搬 |
| `shared/physics/squash.ts` | `Squash.kt` | 纯数学函数 |
| `shared/physics/spring.ts` | `Spring.kt` | 纯数学函数 |
| `shared/physics/throw.ts` | `ThrowPhysics.kt` | 纯数学函数 |
| `shared/physics/release.ts` | `ReleaseEstimator.kt` | 纯数学函数 |
| `shared/types.ts` | `Config.kt` (data class) | 类型映射 |

### 2.2 参考设计（思路复用，重写实现）

| dsh-pet 设计 | 罗小黑 Android 对应 | 说明 |
|---|---|---|
| A/B双缓冲video换源 | SurfaceView + 双Bitmap | Android用Bitmap替代video元素 |
| 世代号丢弃迟到加载 | generation计数器 | 同思路 |
| config.jsonc配置 | animations.json + persona.json | 简化配置结构 |
| 睡眠三段接力 | SleepMachine.kt | 直接复用状态机设计 |
| 人设系统提示词 | SystemPrompt.kt | 换为罗小黑人设 |
| 对话记忆轮数 | ChatHistoryStore.kt | 同思路 |
| 物理参数可配置 | PhysicsParams.kt | 同参数集 |

### 2.3 不复用（平台/环境差异）

| dsh-pet 模块 | 原因 |
|---|---|
| host/（DSH宿主层） | Android不依赖DSH |
| Electron桌面运行时 | Android用WindowManager |
| React组件 | Android用Compose/SurfaceView |
| WebM视频解码 | Android用WebP逐帧（省电） |
| 工作状态联动 | 替换为学习状态联动 |
| 余额系统 | 不需要 |
| DSH会话事件 | 替换为手机使用检测 |
| 表情包/配图 | 简化，不作为核心功能 |

### 2.4 新增（dsh-pet没有的能力）

| 能力 | 说明 |
|---|---|
| 手机使用检测 | UsageStatsManager + 屏幕广播 |
| 学习模式 | 时间段控制 + 动画过滤 |
| 每日报告 | 使用统计 + 猫语化输出 |
| 养成系统 | 好感度/等级/小鱼干 |
| OCR作业辅助 | 拍照识别 + AI解题引导 |
| 息屏提醒 | 离开检测 + 回来欢迎 |
| 内容安全过滤 | 未成年人保护 |
| EMUI保活 | 华为特有权限引导 |

---

## 三、配置结构映射

### dsh-pet config.jsonc → 罗小黑 config.json

```json
{
  "pet": {
    "id": "luoxiaohei",
    "name": "罗小黑",
    "size": 320,
    "position": { "corner": "bottom-right", "marginX": 16, "marginY": 100 }
  },

  "persona": {
    "name": "罗小黑",
    "species": "猫妖",
    "age": "看起来很小，但其实活了很久",
    "personality": ["好奇", "调皮", "偶尔傲娇", "忠诚", "贪吃"],
    "callUser": "小主人",
    "speechStyle": "口语化、简短、带猫的语气词（喵~、哼、呜）",
    "background": "（见罗小黑世界观文档）"
  },

  "animations": {
    "idle": ["sleep.webp", "lick_paw.webp", "daze.webp"],
    "turn": ["turn_left.webp", "turn_right.webp"],
    "clicks": ["happy.webp", "shy.webp", "angry.webp", "curious.webp"],
    "drag": ["grabbed.webp", "struggle.webp"],
    "moves": {
      "default": { "minDist": 80, "maxDist": 200, "margin": 16, "leadSec": 1, "tailSec": 1 },
      "actions": [
        { "name": "walk_calm.webp" },
        { "name": "walk_fast.webp" },
        { "name": "trot.webp" }
      ]
    },
    "categories": [
      { "id": "play", "weight": 40, "actions": ["chase_butterfly.webp", "yarn_ball.webp", "paw_screen.webp"] },
      { "id": "eat", "weight": 20, "actions": ["eat_fish.webp", "drink_milk.webp", "snack.webp"] },
      { "id": "groom", "weight": 15, "actions": ["lick_fur.webp", "wash_face.webp", "scratch_ear.webp"] },
      { "id": "rest", "weight": 25, "actions": ["yawn.webp", "stretch.webp", "shake.webp"] }
    ],
    "study": {
      "reading": ["read_book.webp", "think.webp", "write.webp", "page_turn.webp"],
      "tired": ["sleep_on_desk.webp", "drowsy.webp"]
    },
    "sleep": [
      {
        "start": ["sleep_start.webp"],
        "loop": ["sleep_loop.webp"],
        "end": ["sleep_end.webp"]
      }
    ],
    "events": {
      "studyStart": ["reading.webp"],
      "studyEnd": ["celebrate.webp"],
      "mealTime": ["eat_fish.webp", "drink_milk.webp"],
      "lateNight": ["yawn.webp", "sleep_on_desk.webp"],
      "return": ["happy_run.webp"],
      "lowBattery": ["worried.webp"]
    }
  },

  "animationWeights": {
    "idle": 30,
    "turn": 10,
    "move": 10
  },

  "physics": {
    "gravity": 1400,
    "restitution": 0.78,
    "groundFriction": 2.5,
    "ceilingBounce": true,
    "throwPower": 0.8,
    "hapticEnabled": true,
    "tiltGravity": true
  },

  "schedule": {
    "eventIntervalSec": 40,
    "idleIntervalSec": 8,
    "studyAnimationIntervalSec": 120,
    "whisperIntervalSec": 300
  },

  "sleepRefuseTexts": ["喵...别烦我，在睡觉...", "呼...呼...（装没听见）"],
  "sleepStartTexts": ["困了...先睡一会儿喵", "那我去睡啦，晚安喵~"],
  "sleepEndTexts": ["唔...醒了喵", "睡饱啦！小主人~"],

  "chatMemoryRounds": 10,

  "studyMode": {
    "enabled": false,
    "startTime": "19:00",
    "endTime": "21:00",
    "quietAnimationsOnly": true,
    "reduceEventFrequency": true
  },

  "safety": {
    "blockKeywords": [],
    "educationMode": true,
    "maxReplyLength": 50,
    "noDirectAnswers": true
  }
}
```

---

## 四、物理参数对比

| 参数 | dsh-pet（桌面） | 罗小黑（手机） | 调整原因 |
|---|---|---|---|
| 重力 | 1400 px/s² | 1400 px/s² | 手机屏幕像素密度更高，数值接近 |
| 弹性 | 0.78 | 0.78 | 直接复用 |
| 摩擦 | 2.5 /s | 2.5 /s | 直接复用 |
| 弹簧K | 200 | 200 | 直接复用 |
| 弹簧C | 30 | 30 | 直接复用 |
| 挤压软速度 | 200 px/s | 150 px/s | 手机屏幕小，降低阈值 |
| 挤压硬速度 | 1200 px/s | 800 px/s | 手机屏幕小，降低阈值 |
| 最大挤压 | 0.4 | 0.4 | 直接复用 |
| 抛掷力度 | 1.0 | 0.8 | 手机屏幕小，降低力度避免飞太远 |
| 宠物尺寸 | 420px | 320px | 手机屏幕小 |
| 动画分辨率 | 640×360 | 320×320 | 手机省内存 |
| 帧率 | 24fps | 15fps | 手机省电 |
