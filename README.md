# 🎭 TRPG DM 数字主持系统


[![Android](https://img.shields.io/badge/Android-24%2B+-7C4A9F?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-success.svg)]()

一款基于 魔珐星云具身智能数字人 SDK 的智能 TRPG（桌上角色扮演游戏）主持系统，融合 AI 剧本生成、动作识别、情绪检测等前沿技术，提供沉浸式数字化游戏体验。

[功能介绍](#-核心特性) • [快速开始](#-快速开始) • [文档](#-文档) • [贡献](#-贡献)

---

## 📋 目录

- [✨ 核心特性](#-核心特性)
- [🎮 支持的游戏模式](#-支持的游戏模式)
- [🏗️ 系统架构](#️-系统架构)
- [📸 项目截图](#-项目截图)
- [🚀 快速开始](#-快速开始)
- [🔧 开发环境配置](#️-开发环境配置)
- [📦 依赖说明](#-依赖说明)
- [⚙️ 配置说明](#️-配置说明)
- [🎯 功能使用指南](#-功能使用指南)
- [🌐 API 接口说明](#-api-接口说明)
- [🐛 故障排除](#-故障排除)
- [📝 开发文档](#-开发文档)
- [🤝 贡献](#-贡献)
- [📄 许可证](#-许可证)

---

## ✨ 核心特性

| 功能 | 描述 | 技术实现 |
|------|------|----------|
| 🤖 **数字人 DM** | 3D 数字人实时渲染、语音合成、表情控制 | 魔珐星云具身智能数字人 SDK |
| 🎲 **三种游戏模式** | 剧本杀、跑团（D&D）、海龟汤 | AI 剧本生成引擎 |
| 🖼️ **AI 图片生成** | 自动生成场景 CG、线索卡、角色形象 | APIMart (Stable Diffusion XL) |
| 🏃 **动作识别与惩罚** | 实时姿态检测（下蹲/跳跃/举手等）+ 骰点失败惩罚 | MediaPipe + CameraX |
| 😊 **情绪识别** | 检测玩家情绪状态（积极/紧张/疲劳等），影响 DM 回应 | 通义千问 VL (Qwen-VL) |
| 🌐 **多人实时同步** | 房间管理、游戏状态同步、聊天消息 | Socket.IO |
| 🎭 **角色系统** | 角色创建、属性配置（力量/敏捷/智力等）、技能升级 | Room Database |
| 🏅 **成就系统** | 多类别成就追踪（剧情/战斗/探索/社交等） | Room Database |
| 👥 **社交功能** | 好友系统、房间收藏、游戏记录、复盘分享 | Room Database |
| 🎙️ **语音输入** | 语音转文字，支持自然语言交互 | Android SpeechRecognizer |
| 📜 **剧本导入** | 支持 JSON/Markdown/PDF 格式剧本导入 | 自定义解析器 |

---

## 🎮 支持的游戏模式

### 🕵️ 剧本杀
- **玩法**：悬疑推理、搜集线索、投票指认凶手
- **AI 支持**：自动生成悬疑剧本、线索图片
- **特色功能**：
  - 多角色视角切换
  - 线索搜集与推理
  - 投票机制
  - DM 自动推进剧情

### ⚔️ 跑团
- **玩法**：奇幻冒险、D20 骰点判定、开放世界探索
- **AI 支持**：自动生成奇幻剧本、场景 CG
- **特色功能**：
  - 多职业角色系统
  - 技能树升级
  - 实时战斗判定
  - DM 生成随机事件

### 🐢 海龟汤
- **玩法**：情境推理、是非问答、逐步揭开真相
- **AI 支持**：自动生成谜题剧本、提示生成
- **特色功能**：
  - 问题/答案验证
  - 渐进式提示系统
  - DM 引导推理

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Android Application Layer                    │
├─────────────────────────────────────────────────────────────────────────┤
│                        UI Layer (Jetpack Compose)               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ GameActivity │  │ MenuActivity │  │ Settings     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
├─────────────────────────────────────────────────────────────────────────┤
│                    Feature Integration Manager                   │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │    Image    │     Pose     │   Emotion    │   Network    │ │
│  │   Generation  │  Detection   │  Detection   │   Sync       │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                      TRPG Game Engine                        │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │   Story      │   Player     │   Dice       │   Scene      │ │
│  │   Tree       │   Manager    │   Arbiter    │   Director   │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                     AI Services Layer                        │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │  LLM Client  │   Image Gen   │   Emotion    │   Agent      │ │
│  │  (APIMart)  │  (APIMart)   │  (Qwen-VL)   │   Service     │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                    External Services                        │
│  ┌──────────────┬──────────────┬──────────────┬──────────────┐ │
│  │ Xmov Avatar  │  Socket.IO   │  APIMart     │  Qwen-VL     │ │
│  │    SDK       │   Server     │   API        │   API        │ │
│  └──────────────┴──────────────┴──────────────┴──────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| **UI 框架** | AndroidX, Material Design, CameraX, Glide |
| **开发语言** | Kotlin 2.0.21, Java 11 |
| **3D 渲染** | XmovLiteAvatar SDK (OpenGL ES 3.0) |
| **AI 视觉** | MediaPipe (姿态识别), Qwen-VL (情绪识别) |
| **AI 生成** | APIMart (图片生成), LLM (剧本生成) |
| **网络通信** | Socket.IO, OkHttp |
| **数据存储** | Room Database, DataStore Preferences |
| **并发处理** | Kotlin Coroutines, Flow |
| **依赖管理** | Gradle KTS, Version Catalog |

---

## 🚀 快速开始

### 前置要求

- **Android Studio**: Flamingo | 2022.2.1 或更高版本
- **JDK**: 11 或更高版本
- **Android SDK**: API 24 (Android 7.0) 或更高
- **Gradle**: 8.0 或更高版本

### 导入项目

1. 打开 Android Studio
2. 选择 `File → Open`
3. 选择项目根目录
4. 等待 Gradle 同步完成

### 运行项目

```bash
# 使用 Gradle Wrapper
./gradlew assembleDebug

# 安装到连接的设备
./gradlew installDebug

# 或使用 Android Studio 的 Run 按钮
```

### 首次运行

1. 启动应用后，进入 **功能设置** 界面
2. 配置 **数字人**（AppId / AppSecret）
3. 配置 **AI 服务**（LLM API Key、APIMart API Key）
4. 选择游戏模式，开始体验！

---

## 🔧 开发环境配置

### 1. 签名配置

项目包含预配置的签名文件

> ⚠️ **注意**: 生产环境请使用自己的签名文件

### 2. MediaPipe 模型配置

下载并放置姿态识别模型文件：

```bash
# 下载 Lite 模型（推荐）
wget https://github.com/google/mediapipe/releases/download/v0.10.14/pose_landmarker_lite.task

# 放置到 assets 目录
cp pose_landmarker_lite.task app/src/main/assets/
```

### 3. API 密钥配置

在应用内或通过代码配置：

```kotlin
// Xmov 数字人配置
AppSettings.setXmovAppId("your_app_id")
AppSettings.setXmovAppSecret("your_app_secret")

// LLM 配置
AppSettings.setLlmApiKey("your_llm_api_key")
AppSettings.setLlmBaseUrl("https://api.apimart.ai/v1")
AppSettings.setLlmModel("gemini-2.5-flash-exp")

// 图片生成配置
AppSettings.setImageGenEnabled(true)
AppSettings.setImageGenModel("stable-diffusion-xl")
```

---

## 📦 依赖说明

### 核心依赖

```kotlin
// AndroidX 核心
implementation("androidx.core:core-ktx:1.17.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.recyclerview:recyclerview:1.3.2")

// Material Design
implementation("com.google.android.material:material:1.12.0")

// 生命周期
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

// 协程
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

// 存储
implementation("androidx.datastore:datastore-preferences:1.0.0")
```

### 网络依赖

```kotlin
// OkHttp
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Socket.IO
implementation("io.socket:socket.io-client:2.1.0")

// MessagePack (Socket.IO 二进制协议)
implementation("org.msgpack:msgpack-core:0.9.3")

// Gson (JSON 解析)
implementation("com.google.code.gson:gson:2.13.1")
```

### 视觉 AI 依赖

```kotlin
// MediaPipe Pose
implementation("com.google.mediapipe:tasks-vision:0.10.14")

// CameraX
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")

// Glide (图片加载)
implementation("com.github.bumptech.glide:glide:4.16.0")
kapt("com.github.bumptech.glide:compiler:4.16.0")
```

### 3D 渲染依赖

```kotlin
// Xmov 数字人 SDK
implementation(files("libs/xmovdigitalhuman-v0.0.1.aar"))

// 向量数学库
implementation("javax.vecmath:vecmath:1.5.2")
```

### 其他依赖

```kotlin
// PDF 解析
implementation("com.tom-roush:pdfbox-android:2.0.27.0")

// WorkManager (后台任务)
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

---

## ⚙️ 配置说明

### 应用配置项

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `xmov_app_id` | Xmov 数字人 AppId | - |
| `xmov_app_secret` | Xmov 数字人 AppSecret | - |
| `llm_api_key` | LLM API Key | - |
| `llm_base_url` | LLM 基础 URL | `https://api.apimart.ai/v1` |
| `llm_model` | LLM 模型 | `gemini-2.5-flash-exp` |
| `image_gen_enabled` | 图片生成启用 | `true` |
| `image_gen_model` | 图片生成模型 | `stable-diffusion-xl` |
| `pose_detection_enabled` | 姿态识别启用 | `false` |
| `punishment_enabled` | 惩罚系统启用 | `true` |
| `emotion_detection_enabled` | 情绪识别启用 | `false` |
| `socket_server_url` | Socket.IO 服务器地址 | `http://localhost:3000` |

### 权限配置

`AndroidManifest.xml` 中声明的权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

---

## 🎯 功能使用指南

### 1. 数字人 DM 集成

```kotlin
// 初始化数字人
val initConfig = InitConfig().apply {
    gatewayServer = "https://nebula-agent.xingyun3d.com/user/v1/ttsa/session"
    appId = "your_app_id"
    appSecret = "your_app_secret"
}

val xmovAvatar = IXmovAvatar.newInstance()
xmovAvatar.init(this, avatarLayout, initConfig, object : IAvatarListener {
    override fun onInitEvent(code: Int, message: String?) {
        // 初始化完成
    }
    // ... 其他回调
})

// 让数字人说话
xmovAvatar.speak("欢迎来到这个神秘的世界！", isStart = true, isEnd = true)
```

### 2. AI 图片生成

```kotlin
val imageClient = ImageGenerationClient()

// 生成场景图片
val result = imageClient.generateImage(
    apiKey = "your_apimart_key",
    baseUrl = "https://api.apimart.ai/v1",
    prompt = "古老的城堡，月圆之夜，神秘氛围，电影感",
    model = "stable-diffusion-xl",
    width = 1024,
    height = 768
)

result.onSuccess { imageResult ->
    val imageUrl = imageResult.imageUrl
    // 使用 Glide 加载显示
    Glide.with(context).load(imageUrl).into(imageView)
}
```

### 3. 动作识别与惩罚

```kotlin
val poseDetector = PoseDetector(context)
poseDetector.init()

val cameraManager = PoseCameraManager(context, lifecycleOwner)
cameraManager.setPoseDetector(poseDetector)

// 启动相机
cameraManager.startCamera()

// 监听动作
cameraManager.setPoseDetectionListener { action, confidence ->
    when (action) {
        PoseAction.SQUAT -> println("检测到下蹲")
        PoseAction.JUMP -> println("检测到跳跃")
        PoseAction.RAISE_HANDS -> println("检测到举手")
        // ... 其他动作
    }
}

// 惩罚系统（骰点失败时）
val punishment = PunishmentSystem()
val result = punishment.executePunishment(
    action = PoseAction.SQUAT,
    failReason = "闪避失败",
    gameType = GameType.PAOTUAN
)
// 显示惩罚提示
showPunishmentDialog(result.description)
```

### 4. 情绪识别

```kotlin
val emotionDetector = EmotionDetector(context)

// 检测情绪
val emotion = emotionDetector.detectEmotion(
    image = cameraBitmap,
    gameContext = "当前正在进行紧张的调查"
).getOrNull()

// 格式化为 LLM 输入
val emotionText = emotionDetector.formatForLLM(emotion)
// 示例输出:
// 【玩家情绪状态】
// 情绪：紧张
// 注意力：高度
// 精力：中等
// 疲劳度：无
// DM建议：玩家状态紧张，可以适当放缓节奏
```

### 5. 多人实时同步

```kotlin
val networkManager = NetworkSyncManager()

// 连接服务器
val success = networkManager.connect("https://your-server.com")

// 设置事件回调
networkManager.setNetworkCallbacks(
    onConnected = { println("已连接") },
    onGameStateChanged = { state -> updateUI(state) },
    onPlayerJoined = { player -> showMessage("${player.name} 加入了游戏") },
    onChatMessage = { message -> displayChat(message) }
)

// 发送聊天消息
networkManager.sendChatMessage(roomId, playerId, playerName, "大家好！")

// 同步游戏状态
networkManager.syncGameState(roomId, gameState)
```

### 6. 游戏引擎使用

```kotlin
// 创建游戏引擎
val gameEngine = GameEngine(storyTree, roomId)

// 添加玩家
gameEngine.addPlayer(Player(
    id = "player_001",
    name = "勇者小明",
    role = PlayerRole.PLAYER,
    isHost = false
))

// 处理玩家输入
val result = gameEngine.handlePlayerInput(
    playerId = "player_001",
    actionType = ActionType.SEARCH,
    actionData = "搜查书桌"
)

result.onSuccess { actionResult ->
    // 显示 DM 叙述
    showNarration(actionResult.narration)
    
    // 显示获得的线索
    actionResult.cluesReceived.forEach { clue ->
        showClue(clue)
    }
    
    // 显示骰点结果
    actionResult.diceResult?.let { dice ->
        showDiceResult(dice)
    }
}
```

---

## 🌐 API 接口说明

### 核心 API 快速参考

| 模块 | 类 | 主要方法 |
|------|------|----------|
| **游戏引擎** | `GameEngine` | `handlePlayerInput()`, `getCurrentScene()`, `startGame()` |
| **房间管理** | `RoomManager` | `createRoom()`, `joinRoom()`, `endGame()` |
| **图片生成** | `ImageGenerationClient` | `generateImage()`, `editImage()` |
| **动作识别** | `PoseDetector` | `detectPose()`, `init()` |
| **情绪识别** | `EmotionDetector` | `detectEmotion()`, `formatForLLM()` |
| **网络同步** | `NetworkSyncManager` | `connect()`, `syncGameState()`, `sendChatMessage()` |
| **角色系统** | `CharacterManager` | `createCharacter()`, `addExperience()`, `useItem()` |
| **成就系统** | `AchievementManager` | `updateProgress()`, `isUnlocked()`, `getPlayerAchievements()` |
| **社交功能** | `SocialManager` | `sendFriendRequest()`, `recordGame()`, `generateShareContent()` |

### 详细 API 文档

详细的 API 文档和代码示例，请参考：
- [COMPLETE_PROJECT_DOCUMENTATION.md](COMPLETE_PROJECT_DOCUMENTATION.md) - 完整项目文档
- [FEATURES_README.md](FEATURES_README.md) - 功能特性说明
- [TRPG_DM_README.md](TRPG_DM_README.md) - TRPG 专用文档

---

## 🐛 故障排除

### 常见问题

#### ❌ 编译失败

**问题**: Kotlin 版本不兼容错误

**解决方案**:
```kotlin
// build.gradle.kts (项目根目录)
kotlin {
    jvmToolchain(17)
}
```

#### ❌ MediaPipe 模型加载失败

**问题**: `Asset not found: pose_landmarker_lite.task`

**解决方案**:
```bash
# 下载模型文件
wget https://github.com/google/mediapipe/releases/download/v0.10.14/pose_landmarker_lite.task

# 放置到正确位置
mkdir -p app/src/main/assets/
cp pose_landmarker_lite.task app/src/main/assets/
```

#### ❌ 相机权限被拒绝

**问题**: `Permission Denial: starting Intent requires android.permission.CAMERA`

**解决方案**:
```kotlin
// 在运行时检查并请求权限
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST_CODE
    )
}
```

#### ❌ 数字人初始化失败

**问题**: `XmovAvatar init failed`

**解决方案**:
- 检查 AppId 和 AppSecret 是否正确配置
- 确认网络连接正常
- 查看日志获取详细错误信息

#### ❌ LLM API 调用失败

**问题**: `API key invalid or quota exceeded`

**解决方案**:
- 检查 API Key 是否正确
- 确认账户余额充足
- 检查 Base URL 是否正确

### 日志调试

```kotlin
// 启用 Debug 模式
AppSettings.setDebugModeEnabled(true)

// 查看 Logcat
adb logcat | grep "XmovAvatar"
adb logcat | grep "GameEngine"
adb logcat | grep "ImageGen"
```

---

## 📝 开发文档

### 项目结构

```
app/src/main/java/com/xmov/metahuman/app/
├── trpg/                    # TRPG 核心引擎
│   ├── GameEngine.kt          # 游戏引擎主类
│   ├── StoryTree.kt           # 剧情树结构
│   ├── ScriptParser.kt        # 剧本解析器
│   ├── AIScriptGenerator.kt    # AI 剧本生成
│   ├── RulesArbiter.kt        # 骰子规则仲裁
│   ├── SceneDirector.kt        # 场景导演
│   └── ClueMaster.kt          # 线索管理
├── imagegen/                # AI 图片生成
│   ├── ImageGenerationClient.kt # APIMart 客户端
│   └── GameImageGenerator.kt    # 游戏图片生成器
├── pose/                    # 动作识别与惩罚
│   ├── PoseDetector.kt         # MediaPipe 姿态检测
│   ├── PoseCameraManager.kt    # 相机管理
│   ├── PunishmentSystem.kt     # 惩罚系统
│   └── Action.kt              # 动作枚举
├── emotion/                 # 情绪识别
│   ├── EmotionDetector.kt      # Qwen-VL 情绪识别
│   ├── EmotionState.kt        # 情绪状态数据
│   └── GyroscopeMonitor.kt     # 陀螺仪监听
├── interaction/              # 互动鉴定系统
│   ├── InteractionCheckSystem.kt # 互动鉴定
│   └── InteractionCheckResult.kt # 鉴定结果
├── network/                 # 网络同步
│   └── NetworkSyncManager.kt # Socket.IO 客户端
├── agent/                   # Agent 服务
│   └── AgentService.kt         # Agent 服务封装
├── gameplay/                # 角色与成就
│   ├── CharacterManager.kt      # 角色管理
│   └── AchievementManager.kt    # 成就管理
├── social/                  # 社交功能
│   └── SocialManager.kt        # 社交管理
├── llm/                     # LLM 集成
│   └── LLMClient.kt            # LLM 客户端
├── utils/                   # 工具类
│   ├── ErrorDialog.kt          # 错误对话框
│   └── ImageDialog.kt          # 图片弹窗
├── GameActivity.kt           # 游戏主界面
├── MenuActivity.kt           # 主菜单
├── SettingsActivity.kt        # 设置界面
├── AppSettings.kt           # 应用配置
└── MainApplication.kt        # 应用入口
```

### 代码规范

- **命名**: 使用驼峰命名法 (camelCase)
- **注释**: 使用 KDoc 格式的代码注释
- **异常**: 使用 `Result<T>` 封装返回值
- **协程**: 使用 `lifecycleScope` 或 `viewModelScope`

---

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

### 贡献流程

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 代码规范

- 遵循项目的代码风格
- 为新功能添加测试
- 更新相关文档
- 提交前运行代码检查

### 报告问题

使用 [GitHub Issues](../../issues) 报告 Bug 或提出功能请求，请提供：

- 详细的问题描述
- 复现步骤
- 设备信息（Android 版本、设备型号）
- 相关日志截图

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

## 🙏 致谢

感谢以下开源项目和技术的支持：

- [魔珐星云数字人 SDK](https://www.xingyun3d.com/) - 3D 数字人渲染
- [Google MediaPipe](https://google.github.io/mediapipe/) - 姿态识别
- [Socket.IO](https://socket.io/) - 实时通信
- [Glide](https://github.com/bumptech/glide) - 图片加载
- [CameraX](https://developer.android.com/training/camerax) - 相机管理
- [Kotlin](https://kotlinlang.org/) - 开发语言

---


**如果这个项目对你有帮助，请给它一个 Star ⭐**

[⬆ 返回顶部](#-xmovliteavatar-trpg-dm-数字主持系统)

