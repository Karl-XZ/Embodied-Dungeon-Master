# TRPG DM数字主持系统 - 完整项目文档

## 项目概述

TRPG DM数字主持系统是一个基于XmovLiteAvatar Android数字人SDK开发的智能桌上角色扮演游戏主持平台。该系统将传统TRPG游戏与现代AI技术深度融合，提供沉浸式的数字化游戏体验。

### 核心特性
- 🎭 **数字人DM**：基于XmovLiteAvatar SDK的3D数字主持人
- 🎲 **三种游戏模式**：剧本杀、跑团、海龟汤
- 🤖 **AI驱动**：智能剧情生成、图片生成、情绪识别
- 🏃 **多模态交互**：动作识别、语音输入、情绪检测
- 🌐 **多人协作**：实时同步、网络对战
- 🎯 **游戏化系统**：角色系统、成就系统、社交功能

---

## 系统架构

### 技术栈
```
Frontend: Android (Kotlin)
UI Framework: AndroidX + Material Design
3D Engine: XmovLiteAvatar SDK
AI Services: MediaPipe, Qwen-VL, APIMart
Network: Socket.IO, OkHttp
Database: Room Database
Concurrency: Kotlin Coroutines
```

### 模块架构
```
app/src/main/java/com/xmov/metahuman/app/
├── trpg/                    # TRPG核心引擎
├── imagegen/                # AI图片生成
├── pose/                    # 动作识别与惩罚
├── emotion/                 # 情绪识别
├── network/                 # 网络同步
├── agent/                   # Agent服务端
├── gameplay/                # 角色与成就系统
├── social/                  # 社交功能
├── llm/                     # LLM集成
└── FeatureIntegrationManager.kt  # 功能集成管理器
```

---

## 核心功能详解

## 1. TRPG游戏引擎

### 1.1 剧情树系统 (StoryTree)

剧情树是游戏的核心数据结构，采用节点-边模型：

#### 数据结构
```kotlin
data class StoryTree(
    val id: String,                    // 剧情树ID
    val title: String,                 // 剧本标题
    val gameType: GameType,            // 游戏类型
    val rootNodeId: String,            // 根节点ID
    val nodes: Map<String, StoryNode>, // 场景节点
    val edges: Map<String, TransitionEdge>, // 转场边
    val globalClues: List<Clue>,       // 全局线索
    val failSafePolicies: List<FailSafePolicy> // 纠偏策略
)
```

#### 游戏类型
```kotlin
enum class GameType {
    JUBENSHA,   // 剧本杀：悬疑推理，搜集线索
    PAOTUAN,    // 跑团：奇幻冒险，骰点判定
    HAITANG     // 海龟汤：提问推理，是非问答
}
```

#### 场景节点
```kotlin
data class StoryNode(
    val id: String,                      // 场景ID
    val description: String,             // 场景标题
    val narrativeText: String,           // DM朗读文本
    val objectives: List<Objective>,     // 场景目标
    val allowedActions: List<ActionType>, // 允许的动作
    val cluePool: CluePool,              // 线索池
    val diceRules: DiceRule?,            // 骰子规则
    val imageUrl: String?,               // 场景插画
    val isCritical: Boolean = false      // 是否关键转折点
)
```

#### 转场条件
```kotlin
data class TransitionEdge(
    val id: String,
    val fromNodeId: String,              // 起始场景
    val toNodeId: String,                // 目标场景
    val condition: TransitionCondition,  // 转场条件
    val priority: Int = 1,               // 优先级
    val isFailSafe: Boolean = false      // 是否纠偏路径
)

enum class ConditionType {
    CLUE_FOUND,        // 找到特定线索
    DICE_SUCCESS,      // 骰点成功
    DECISION_MADE,     // 特定决策
    NARRATIVE_INPUT,   // 玩家输入
    AUTOMATIC,         // 自动触发
    TIME_ELAPSED       // 时间流逝
}
```

### 1.2 游戏引擎 (GameEngine)

游戏引擎是DM Orchestrator的核心，负责驱动整个游戏流程。

#### 初始化
```kotlin
val gameEngine = GameEngine(storyTree, roomId)

// 添加玩家
gameEngine.addPlayer(Player(
    id = "player_001",
    name = "玩家A",
    role = PlayerRole.PLAYER,
    isHost = false
))
```

#### 处理玩家输入
```kotlin
suspend fun handlePlayerInput(
    playerId: String,
    actionType: ActionType,
    actionData: String
): ActionResult

// 使用示例
val result = gameEngine.handlePlayerInput(
    playerId = "player_001",
    actionType = ActionType.SEARCH,
    actionData = "搜查书桌"
)
```

#### 动作类型
```kotlin
enum class ActionType {
    SEARCH,           // 搜查：寻找线索
    TALK,             // 对话：与DM交互
    INVESTIGATE,      // 调查：深入调查（可能需要骰点）
    DICE_ROLL,        // 骰点：进行判定
    MOVE,             // 移动：场景转换
    USE_ITEM,         // 使用物品
    ATTACK,           // 攻击
    CUSTOM            // 自定义动作
}
```

#### 动作结果
```kotlin
data class ActionResult(
    val success: Boolean,                // 是否成功
    val message: String,                 // 结果消息
    val narration: String?,              // DM叙述
    val diceResult: DiceRollResult? = null, // 骰点结果
    val cluesReceived: List<Clue> = emptyList(), // 获得线索
    val failSafe: FailSafePolicy? = null // 触发的纠偏策略
)
```

### 1.3 房间管理系统 (RoomManager)

#### 创建房间
```kotlin
val roomManager = RoomManagerProvider.instance

val room = roomManager.createRoom(
    roomName = "古堡迷案",
    gameType = GameType.JUBENSHA,
    hostId = "host_001",
    storyTree = storyTree,
    maxPlayers = 6,
    password = "123456"
)
```

#### 加入房间
```kotlin
val result = roomManager.joinRoom(
    roomId = "room_123",
    playerId = "player_002",
    playerName = "玩家B",
    password = "123456"
)
```

#### 游戏控制
```kotlin
// 开始游戏
roomManager.startGame(roomId)

// 结束游戏并生成复盘
val review = roomManager.endGame(roomId).getOrNull()
```

---

## 2. AI图片生成系统

### 2.1 图片生成客户端 (ImageGenerationClient)

基于APIMart API实现的图片生成功能。

#### 初始化
```kotlin
val imageClient = ImageGenerationClient()
imageClient.setApiKey("your_apimart_key")
```

#### 生成图片
```kotlin
suspend fun generateImage(
    prompt: String,
    model: String = "stable-diffusion-xl",
    size: String = "1024x1024",
    quality: String = "standard"
): Result<String>

// 使用示例
val result = imageClient.generateImage(
    prompt = "古老的城堡，月圆之夜，神秘氛围",
    model = "stable-diffusion-xl"
)
```

#### 支持的模型
```kotlin
object ImageModels {
    const val STABLE_DIFFUSION_XL = "stable-diffusion-xl"
    const val DALLE_3 = "dall-e-3"
    const val MIDJOURNEY = "midjourney"
}
```

### 2.2 游戏图片生成器 (GameImageGenerator)

专门为TRPG游戏优化的图片生成器。

#### 场景图片生成
```kotlin
suspend fun generateSceneImage(
    sceneDescription: String,
    sceneNarrative: String,
    imageType: SceneImageType
): Result<String>

enum class SceneImageType {
    SCENE_CG,        // 场景CG
    BACKGROUND,      // 背景图
    ILLUSTRATION     // 插画
}

// 使用示例
val imageUrl = gameImageGenerator.generateSceneImage(
    sceneDescription = "古堡大厅",
    sceneNarrative = "月光透过彩色玻璃窗洒在大理石地板上...",
    imageType = SceneImageType.SCENE_CG
).getOrNull()
```

#### 线索图片生成
```kotlin
suspend fun generateClueImage(clue: Clue): Result<String>

// 使用示例
val clue = Clue(
    id = "clue_001",
    name = "血迹斑斑的匕首",
    description = "一把古老的匕首，刀刃上有干涸的血迹",
    importance = ClueImportance.CRITICAL
)

val imageUrl = gameImageGenerator.generateClueImage(clue).getOrNull()
```

#### 批量生成
```kotlin
suspend fun generateCluesImagesBatch(
    clues: List<Clue>,
    gameType: GameType,
    onProgress: (Int, Int) -> Unit
): Map<String, String>

// 使用示例
val results = gameImageGenerator.generateCluesImagesBatch(
    clues = cluesList,
    gameType = GameType.JUBENSHA
) { current, total ->
    println("进度: $current/$total")
}
```

---

## 3. 动作识别与惩罚系统

### 3.1 姿态检测 (PoseDetector)

基于MediaPipe Pose实现的实时姿态检测。

#### 初始化
```kotlin
val poseDetector = PoseDetector(context)
poseDetector.init()
```

#### 检测动作
```kotlin
fun detectPose(
    image: Bitmap,
    onResult: (PoseResult) -> Unit
)

data class PoseResult(
    val action: PoseAction,
    val confidence: Float,
    val landmarks: List<PoseLandmark>
)

enum class PoseAction {
    SQUAT,          // 下蹲
    JUMP,           // 跳跃
    RAISE_HANDS,    // 举手
    DODGE,          // 闪避
    CROUCH,         // 蹲伏
    STAND,          // 站立
    UNKNOWN         // 未知动作
}
```

### 3.2 相机管理 (PoseCameraManager)

集成CameraX的相机管理器。

#### 启动相机
```kotlin
val cameraManager = PoseCameraManager(context, lifecycleOwner)

cameraManager.setPoseDetectionListener { action, confidence ->
    Log.d("Pose", "检测到动作: $action, 置信度: $confidence")
}

val success = cameraManager.startCamera()
```

#### 相机控制
```kotlin
// 停止相机
cameraManager.stopCamera()

// 暂停检测
cameraManager.pauseDetection()

// 恢复检测
cameraManager.resumeDetection()

// 释放资源
cameraManager.release()
```

### 3.3 惩罚系统 (PunishmentSystem)

基于游戏失败结果的动作惩罚系统。

#### 惩罚动作定义
```kotlin
data class PunishmentAction(
    val action: PoseAction,
    val description: String,
    val count: Int? = null,        // 重复次数
    val duration: Long? = null,    // 持续时间(毫秒)
    val isCounted: Boolean = false, // 是否计数型
    val isTimed: Boolean = false   // 是否计时型
)
```

#### 执行惩罚
```kotlin
suspend fun executePunishment(
    action: PoseAction,
    failReason: String,
    gameType: GameType
): PunishmentResult

// 使用示例
val result = punishmentSystem.executePunishment(
    action = PoseAction.SQUAT,
    failReason = "闪避失败",
    gameType = GameType.PAOTUAN
)
```

#### 惩罚规则配置
```kotlin
// 默认惩罚规则
val punishmentRules = mapOf(
    PoseAction.SQUAT to PunishmentAction(
        action = PoseAction.SQUAT,
        description = "做5个深蹲",
        count = 5,
        isCounted = true
    ),
    PoseAction.JUMP to PunishmentAction(
        action = PoseAction.JUMP,
        description = "原地跳10次",
        count = 10,
        isCounted = true
    ),
    PoseAction.CROUCH to PunishmentAction(
        action = PoseAction.CROUCH,
        description = "保持蹲姿30秒",
        duration = 30000,
        isTimed = true
    )
)
```

---

## 4. 情绪识别系统

### 4.1 情绪检测器 (EmotionDetector)

基于通义千问VL API的情绪识别功能。

#### 情绪状态定义
```kotlin
data class EmotionState(
    val emotion: String,        // 情绪类型
    val attention: String,      // 注意力水平
    val energy: String,         // 精力状态
    val fatigue: String,        // 疲劳程度
    val stress: String,         // 压力水平
    val engagement: String,     // 投入度
    val confidence: Float,      // 识别置信度
    val timestamp: Long         // 时间戳
)
```

#### 检测情绪
```kotlin
suspend fun detectEmotion(
    image: Bitmap,
    gameContext: String
): Result<EmotionState>

// 使用示例
val emotion = emotionDetector.detectEmotion(
    image = cameraBitmap,
    gameContext = "当前正在进行剧本杀推理阶段"
).getOrNull()
```

#### 格式化为LLM输入
```kotlin
fun formatForLLM(emotion: EmotionState): String

// 输出示例:
// 【玩家情绪状态】
// 情绪：积极
// 注意力：高度
// 精力：高
// 疲劳度：无
// 压力：无
// 投入度：完全投入
// DM建议：玩家状态良好，可以推进复杂剧情
```

### 4.2 陀螺仪监听 (GyroscopeMonitor)

检测手机陀螺仪变化，识别点头、摇头等动作。

#### 陀螺仪动作
```kotlin
enum class GyroAction {
    NOD,           // 点头（表示同意）
    SHAKE_HEAD,    // 摇头（表示不同意）
    LIFT,          // 举起设备（请求发言）
    SHAKE,         // 摇晃设备（表示兴奋）
    TILT_LEFT,     // 向左倾斜
    TILT_RIGHT,    // 向右倾斜
    UNKNOWN        // 未知动作
}
```

#### 启动监听
```kotlin
val gyroMonitor = GyroscopeMonitor(context)

gyroMonitor.detectedAction.collect { action ->
    when (action) {
        GyroAction.NOD -> println("玩家表示同意")
        GyroAction.SHAKE_HEAD -> println("玩家表示不同意")
        GyroAction.LIFT -> println("玩家请求发言")
        else -> {}
    }
}

gyroMonitor.startListening()
```

---

## 5. 网络多人同步

### 5.1 网络同步管理器 (NetworkSyncManager)

基于Socket.IO的实时多人同步系统。

#### 连接服务器
```kotlin
val networkManager = NetworkSyncManager()

val success = networkManager.connect("https://your-server.com")
```

#### 设置事件回调
```kotlin
networkManager.setNetworkCallbacks(
    onConnected = { 
        println("已连接到服务器") 
    },
    onDisconnected = { 
        println("与服务器断开连接") 
    },
    onGameStateChanged = { gameState ->
        updateUI(gameState)
    },
    onPlayerJoined = { player ->
        showMessage("${player.name} 加入了游戏")
    },
    onPlayerLeft = { player ->
        showMessage("${player.name} 离开了游戏")
    },
    onChatMessage = { message ->
        displayChatMessage(message)
    },
    onDiceRolled = { diceRoll ->
        displayDiceResult(diceRoll)
    }
)
```

#### 同步游戏数据
```kotlin
// 同步游戏状态
networkManager.syncGameState(roomId, gameState)

// 发送聊天消息
networkManager.sendChatMessage(roomId, playerId, playerName, "大家好！")

// 同步骰点结果
networkManager.syncDiceRoll(roomId, playerId, result, isSuccess)

// 加入房间
networkManager.joinRoom(roomId, playerId, playerName)

// 离开房间
networkManager.leaveRoom(roomId, playerId)
```

#### 网络消息格式
```kotlin
data class ChatMessage(
    val roomId: String,
    val playerId: String,
    val playerName: String,
    val message: String,
    val timestamp: Long
)

data class DiceRollMessage(
    val roomId: String,
    val playerId: String,
    val playerName: String,
    val diceType: String,
    val result: Int,
    val isSuccess: Boolean,
    val timestamp: Long
)
```

---

## 6. Agent服务端

### 6.1 Agent服务客户端 (AgentServiceClient)

与LLM驱动的Agent服务端通信。

#### 初始化
```kotlin
val agentClient = AgentServiceClient()
agentClient.setBaseUrl("https://your-agent-server.com")
agentClient.setApiKey("your_llm_api_key")
```

#### 解析剧本文本
```kotlin
suspend fun parseScript(
    scriptText: String,
    gameType: GameType
): Result<StoryTree>

// 使用示例
val storyTree = agentClient.parseScript(
    scriptText = "# 场景一：午夜的古堡\n这是一个月圆之夜...",
    gameType = GameType.JUBENSHA
).getOrNull()
```

#### 生成剧情树
```kotlin
suspend fun generateStoryTree(
    theme: String,
    gameType: GameType,
    sceneCount: Int,
    complexity: Complexity
): Result<StoryTree>

enum class Complexity {
    SIMPLE,    // 简单：3-5个场景
    MEDIUM,    // 中等：5-8个场景
    COMPLEX    // 复杂：8-12个场景
}

// 使用示例
val storyTree = agentClient.generateStoryTree(
    theme = "古堡谋杀案",
    gameType = GameType.JUBENSHA,
    sceneCount = 6,
    complexity = Complexity.MEDIUM
).getOrNull()
```

#### 生成Fail-Safe策略
```kotlin
suspend fun generateFailSafePolicies(
    storyTree: StoryTree
): Result<StoryTree>

// 使用示例
val updatedTree = agentClient.generateFailSafePolicies(storyTree).getOrNull()
```

### 6.2 Agent服务封装 (AgentService)

高级Agent服务封装，提供更便捷的API。

#### 智能剧本解析
```kotlin
suspend fun parseScriptText(
    scriptText: String,
    gameType: GameType
): Result<StoryTree>

// 支持多种格式
// 1. JSON格式（完整结构化）
// 2. Markdown格式（# 场景标题）
// 3. 纯文本格式（自然语言描述）
```

#### 自动生成完整剧本
```kotlin
suspend fun generateCompleteScript(
    theme: String,
    gameType: GameType,
    playerCount: Int,
    difficulty: Difficulty
): Result<StoryTree>

enum class Difficulty {
    BEGINNER,     // 新手：简单线索，明确提示
    INTERMEDIATE, // 中级：中等难度，部分隐藏线索
    EXPERT        // 专家：复杂推理，多重线索
}
```

---

## 7. 角色系统

### 7.1 角色管理器 (CharacterManager)

基于Room数据库的角色系统。

#### 角色数据模型
```kotlin
data class Character(
    val id: String,
    val playerId: String,
    val name: String,
    val role: String,           // 职业
    val level: Int,
    val experience: Int,
    val stats: Stats,           // 属性
    val skills: List<Skill>,    // 技能
    val inventory: List<Item>,  // 背包
    val avatar: String?,        // 头像URL
    val createdAt: Long
)

data class Stats(
    val strength: Int,      // 力量
    val dexterity: Int,     // 敏捷
    val constitution: Int,  // 体质
    val intelligence: Int,  // 智力
    val wisdom: Int,        // 智慧
    val charisma: Int       // 魅力
)
```

#### 创建角色
```kotlin
suspend fun createCharacter(
    playerId: String,
    name: String,
    role: String,
    avatar: String?
): Character

// 使用示例
val character = characterManager.createCharacter(
    playerId = "player_001",
    name = "勇者小明",
    role = "warrior",
    avatar = "https://example.com/avatar.jpg"
)
```

#### 角色操作
```kotlin
// 添加经验值
suspend fun addExperience(characterId: String, amount: Int)

// 升级技能
suspend fun upgradeSkill(characterId: String, skillId: String)

// 使用物品
suspend fun useItem(characterId: String, itemId: String): Pair<Character, ItemEffect>

// 获取玩家角色列表
fun getPlayerCharacters(playerId: String): Flow<List<Character>>
```

#### 技能系统
```kotlin
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val level: Int,
    val maxLevel: Int,
    val category: SkillCategory,
    val effects: List<SkillEffect>
)

enum class SkillCategory {
    COMBAT,      // 战斗技能
    SOCIAL,      // 社交技能
    EXPLORATION, // 探索技能
    CRAFTING,    // 制作技能
    MAGIC        // 魔法技能
}
```

---

## 8. 成就系统

### 8.1 成就管理器 (AchievementManager)

多类别成就追踪系统。

#### 成就数据模型
```kotlin
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val maxProgress: Int,
    val rewards: List<Reward>,
    val isHidden: Boolean = false,
    val icon: String?
)

enum class AchievementCategory {
    STORY,       // 剧情成就
    COMBAT,      // 战斗成就
    EXPLORATION, // 探索成就
    SOCIAL,      // 社交成就
    COLLECTION,  // 收集成就
    CHALLENGE    // 挑战成就
}
```

#### 默认成就列表
```kotlin
val defaultAchievements = listOf(
    Achievement(
        id = "story_1",
        name = "初出茅庐",
        description = "完成第一个剧本",
        category = AchievementCategory.STORY,
        maxProgress = 1
    ),
    Achievement(
        id = "story_2",
        name = "真相大白",
        description = "成功解开3个剧本杀谜题",
        category = AchievementCategory.STORY,
        maxProgress = 3
    ),
    Achievement(
        id = "combat_1",
        name = "大成功",
        description = "掷出10次大成功",
        category = AchievementCategory.COMBAT,
        maxProgress = 10
    )
)
```

#### 成就操作
```kotlin
// 更新成就进度
suspend fun updateProgress(
    playerId: String,
    achievementId: String,
    increment: Int = 1
): Pair<Achievement?, Int>

// 检查成就是否解锁
suspend fun isUnlocked(playerId: String, achievementId: String): Boolean

// 获取玩家成就
fun getPlayerAchievements(playerId: String): Flow<List<PlayerAchievement>>

// 获取所有成就
fun getAllAchievements(): Flow<List<Achievement>>
```

#### 成就通知
```kotlin
data class AchievementUnlocked(
    val achievement: Achievement,
    val playerId: String,
    val timestamp: Long
)

// 监听成就解锁
achievementManager.achievementUnlocked.collect { event ->
    showAchievementNotification(event.achievement)
}
```

---

## 9. 社交功能

### 9.1 社交管理器 (SocialManager)

完整的社交功能系统。

#### 好友系统
```kotlin
data class Friend(
    val id: String,
    val playerId: String,
    val friendId: String,
    val friendName: String,
    val status: FriendStatus,
    val addedAt: Long
)

enum class FriendStatus {
    PENDING,    // 待确认
    ACCEPTED,   // 已接受
    BLOCKED     // 已屏蔽
}

// 发送好友请求
suspend fun sendFriendRequest(
    fromPlayerId: String,
    fromPlayerName: String,
    toPlayerId: String,
    message: String? = null
)

// 接受好友请求
suspend fun acceptFriendRequest(requestId: String)

// 获取好友列表
fun getFriends(playerId: String): Flow<List<Friend>>
```

#### 房间收藏
```kotlin
data class FavoriteRoom(
    val id: String,
    val playerId: String,
    val roomId: String,
    val roomName: String,
    val gameType: String,
    val hostName: String,
    val favoritedAt: Long
)

// 收藏房间
suspend fun favoriteRoom(
    playerId: String,
    roomId: String,
    roomName: String,
    gameType: String,
    hostName: String
)

// 获取收藏房间
fun getFavoriteRooms(playerId: String): Flow<List<FavoriteRoom>>
```

#### 游戏记录
```kotlin
data class GameRecord(
    val id: String,
    val playerId: String,
    val roomId: String,
    val roomName: String,
    val gameType: String,
    val duration: Long,
    val playerCount: Int,
    val result: GameResult,
    val score: Int,
    val xpEarned: Int,
    val achievements: List<String>,
    val review: String?,
    val playedAt: Long
)

enum class GameResult {
    VICTORY,    // 胜利
    DEFEAT,     // 失败
    DRAW,       // 平局
    ABANDONED   // 中途退出
}

// 记录游戏
suspend fun recordGame(
    playerId: String,
    roomId: String,
    roomName: String,
    gameType: String,
    duration: Long,
    playerCount: Int,
    result: GameResult,
    score: Int,
    xpEarned: Int,
    achievements: List<String> = emptyList(),
    review: String? = null
)
```

#### 复盘分享
```kotlin
fun generateShareContent(record: GameRecord): String

// 生成示例:
// 🏆 刚刚完成了一局《古堡迷案》
// 🎮 游戏类型：JUBENSHA
// ⏱️ 游戏时长：60 分钟
// 👥 玩家人数：4
// 📊 得分：100
// ✨ 获得经验：200
// 🏅 解锁成就：2
// #TRPG #桌游 #JUBENSHA
```

---

## 10. 功能集成管理器

### 10.1 FeatureIntegrationManager

统一管理所有功能的集成入口。

#### 初始化
```kotlin
val featureManager = FeatureIntegrationManager(context)

// 初始化所有功能
lifecycleScope.launch {
    featureManager.initialize()
}
```

#### 图片生成集成
```kotlin
// 设置API Key
featureManager.setApiKey("your_api_key")

// 生成场景图片
val imageUrl = featureManager.generateSceneImage(
    sceneDescription = "古堡大厅",
    sceneNarrative = "月光洒在大理石地板上...",
    imageType = SceneImageType.SCENE_CG
)

// 批量生成游戏图片
featureManager.generateGameImages(
    storyTreeId = "tree_123",
    sceneIds = listOf("scene_1", "scene_2"),
    clueIds = listOf("clue_1", "clue_2")
) { current, total, desc ->
    println("[$current/$total] $desc")
}
```

#### 动作识别集成
```kotlin
// 启动动作识别
featureManager.startPoseDetection { action ->
    lifecycleScope.launch {
        featureManager.handlePunishmentAction(action)
    }
}

// 检测并执行惩罚
val punishment = featureManager.detectAndPunish(diceResult, gameType)
```

#### 情绪识别集成
```kotlin
// 检测玩家情绪
val emotion = featureManager.detectPlayerEmotion(imageBitmap, gameState)

// 格式化为LLM输入
val emotionText = featureManager.formatEmotionForLLM(emotion)
```

#### 网络同步集成
```kotlin
// 连接服务器
featureManager.connectToServer("https://your-server.com")

// 设置回调
featureManager.setOnGameStateChanged { roomId, state ->
    updateUI(state)
}

featureManager.setOnPlayerJoined { roomId, player ->
    showMessage("${player.name} 加入了游戏")
}
```

#### Agent服务集成
```kotlin
// 解析剧本
val storyTree = featureManager.parseScriptText(scriptText, gameType)

// 生成剧情树
val storyTree = featureManager.generateStoryTree(
    theme = "古堡谋杀案",
    gameType = GameType.JUBENSHA
)
```

#### 角色系统集成
```kotlin
// 创建角色
val character = featureManager.createCharacter(
    playerId = "player_001",
    name = "勇者小明",
    role = "warrior",
    avatar = null
)

// 添加经验
featureManager.addExperience(character.id, 100)
```

#### 成就系统集成
```kotlin
// 更新成就进度
featureManager.updateAchievementProgress(
    playerId = "player_001",
    achievementId = "story_1"
)

// 获取玩家成就
featureManager.getPlayerAchievements("player_001").collect { achievements ->
    displayAchievements(achievements)
}
```

#### 社交功能集成
```kotlin
// 记录游戏
featureManager.recordGame(
    playerId = "player_001",
    roomId = "room_123",
    roomName = "古堡迷案",
    gameType = "JUBENSHA",
    duration = 3600000,
    playerCount = 4,
    result = GameResult.VICTORY,
    score = 100,
    xpEarned = 200
)

// 生成分享内容
val shareContent = featureManager.generateShareContent(record)
```

---

## 配置与设置

### 应用设置 (AppSettings)

#### 基础配置
```kotlin
// Xmov数字人配置
AppSettings.setXmovAppId("your_app_id")
AppSettings.setXmovAppSecret("your_app_secret")

// LLM配置
AppSettings.setLlmApiKey("your_llm_api_key")
AppSettings.setLlmBaseUrl("https://api.openai.com/v1")
AppSettings.setLlmModel("gpt-3.5-turbo")
AppSettings.setLlmTemperature(0.7f)
```

#### 功能开关
```kotlin
// 图片生成
AppSettings.setImageGenEnabled(true)
AppSettings.setImageGenModel("stable-diffusion-xl")

// 动作识别
AppSettings.setPoseDetectionEnabled(true)
AppSettings.setPunishmentEnabled(true)

// 情绪识别
AppSettings.setEmotionDetectionEnabled(true)

// 网络同步
AppSettings.setSocketServerUrl("https://your-socket-server.com")
```

#### 检查配置状态
```kotlin
// 检查是否有LLM配置
val hasLlmConfig = AppSettings.hasLlmConfig()

// 检查是否有Xmov配置
val hasXmovConfig = AppSettings.hasXmovConfig()

// 获取配置值
val apiKey = AppSettings.getLlmApiKey()
val baseUrl = AppSettings.getLlmBaseUrl()
val model = AppSettings.getLlmModel()
```

---

## 权限要求

### AndroidManifest.xml配置
```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 相机权限（动作识别） -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 录音权限（语音识别） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 相机特性 -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

<!-- OpenGL ES 3.0（数字人渲染） -->
<uses-feature
    android:glEsVersion="0x00030000"
    android:required="true" />
```

### 运行时权限请求
```kotlin
// 相机权限
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST_CODE
    )
}

// 录音权限
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        AUDIO_PERMISSION_REQUEST_CODE
    )
}
```

---

## 部署与配置

### MediaPipe模型设置

1. **下载模型文件**
   ```bash
   # 从GitHub Releases下载
   wget https://github.com/google/mediapipe/releases/download/v0.10.14/pose_landmarker_lite.task
   ```

2. **放置模型文件**
   ```
   app/src/main/assets/pose_landmarker_lite.task
   ```

3. **模型选择**
   - `pose_landmarker_lite.task` (10MB) - 推荐，实时性能好
   - `pose_landmarker_full.task` (50MB) - 高精度
   - `pose_landmarker_heavy.task` (90MB) - 最高精度

### Socket.IO服务器部署

#### 服务器端实现示例 (Node.js)
```javascript
const express = require('express');
const http = require('http');
const socketIo = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// 房间管理
const rooms = new Map();

io.on('connection', (socket) => {
    console.log('用户连接:', socket.id);

    // 加入房间
    socket.on('join_room', (data) => {
        const { roomId, playerId, playerName } = data;
        socket.join(roomId);
        
        if (!rooms.has(roomId)) {
            rooms.set(roomId, new Set());
        }
        rooms.get(roomId).add({ playerId, playerName, socketId: socket.id });
        
        // 通知其他玩家
        socket.to(roomId).emit('player_joined', { playerId, playerName });
    });

    // 同步游戏状态
    socket.on('sync_game_state', (data) => {
        const { roomId, gameState } = data;
        socket.to(roomId).emit('game_state_changed', gameState);
    });

    // 聊天消息
    socket.on('chat_message', (data) => {
        const { roomId, playerId, playerName, message } = data;
        io.to(roomId).emit('chat_message', {
            playerId,
            playerName,
            message,
            timestamp: Date.now()
        });
    });

    // 骰点同步
    socket.on('dice_roll', (data) => {
        const { roomId, playerId, playerName, result, isSuccess } = data;
        io.to(roomId).emit('dice_rolled', {
            playerId,
            playerName,
            result,
            isSuccess,
            timestamp: Date.now()
        });
    });

    // 断开连接
    socket.on('disconnect', () => {
        console.log('用户断开连接:', socket.id);
        // 清理房间数据
        for (const [roomId, players] of rooms.entries()) {
            const playerArray = Array.from(players);
            const updatedPlayers = playerArray.filter(p => p.socketId !== socket.id);
            if (updatedPlayers.length === 0) {
                rooms.delete(roomId);
            } else {
                rooms.set(roomId, new Set(updatedPlayers));
            }
        }
    });
});

server.listen(3000, () => {
    console.log('Socket.IO服务器运行在端口3000');
});
```

### Agent服务端部署

#### FastAPI实现示例 (Python)
```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import openai
import json

app = FastAPI()

class ScriptParseRequest(BaseModel):
    script_text: str
    game_type: str

class StoryGenerateRequest(BaseModel):
    theme: str
    game_type: str
    scene_count: int
    complexity: str

@app.post("/parse_script")
async def parse_script(request: ScriptParseRequest):
    try:
        # 使用LLM解析剧本文本
        response = openai.ChatCompletion.create(
            model="gpt-3.5-turbo",
            messages=[
                {
                    "role": "system",
                    "content": "你是一个TRPG剧本解析专家，将自然语言剧本转换为结构化的JSON格式。"
                },
                {
                    "role": "user",
                    "content": f"请将以下{request.game_type}剧本解析为JSON格式：\n{request.script_text}"
                }
            ],
            temperature=0.3
        )
        
        story_tree = json.loads(response.choices[0].message.content)
        return {"success": True, "story_tree": story_tree}
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/generate_story")
async def generate_story(request: StoryGenerateRequest):
    try:
        # 使用LLM生成完整剧情树
        prompt = f"""
        创建一个{request.game_type}类型的TRPG剧本，主题是"{request.theme}"。
        要求：
        - 场景数量：{request.scene_count}
        - 复杂度：{request.complexity}
        - 包含完整的场景节点、转场条件、线索池
        - 输出标准JSON格式
        """
        
        response = openai.ChatCompletion.create(
            model="gpt-4",
            messages=[
                {
                    "role": "system",
                    "content": "你是一个专业的TRPG剧本创作大师。"
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            temperature=0.7
        )
        
        story_tree = json.loads(response.choices[0].message.content)
        return {"success": True, "story_tree": story_tree}
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

---

## 使用示例

### 完整游戏流程示例

```kotlin
class GameFlowExample {
    
    suspend fun completeGameFlow() {
        // 1. 初始化功能管理器
        val featureManager = FeatureIntegrationManager(context)
        featureManager.initialize()
        
        // 2. 创建剧情树
        val storyTree = featureManager.generateStoryTree(
            theme = "古堡谋杀案",
            gameType = GameType.JUBENSHA,
            sceneCount = 6,
            complexity = Complexity.MEDIUM
        ) ?: return
        
        // 3. 创建房间
        val roomManager = RoomManagerProvider.instance
        val room = roomManager.createRoom(
            roomName = "古堡迷案",
            gameType = GameType.JUBENSHA,
            hostId = "host_001",
            storyTree = storyTree,
            maxPlayers = 4
        )
        
        // 4. 玩家加入
        roomManager.joinRoom(room.roomId, "player_001", "玩家A")
        roomManager.joinRoom(room.roomId, "player_002", "玩家B")
        
        // 5. 开始游戏
        roomManager.startGame(room.roomId)
        
        // 6. 启动高级功能
        featureManager.setCurrentRoom(room.roomId)
        featureManager.connectToServer("https://your-server.com")
        featureManager.startPoseDetection { action ->
            // 处理动作识别
        }
        
        // 7. 游戏进行中
        val gameEngine = roomManager.getGameEngine(room.roomId)
        
        // 玩家执行搜查动作
        val searchResult = roomManager.handlePlayerInput(
            room.roomId,
            "player_001",
            ActionType.SEARCH,
            "搜查书桌"
        ).getOrNull()
        
        // 自动生成线索图片
        searchResult?.cluesReceived?.forEach { clue ->
            val imageUrl = featureManager.generateClueImage(clue)
            // 显示线索图片
        }
        
        // 8. 情绪检测
        val emotion = featureManager.detectPlayerEmotion(
            imageBitmap = cameraBitmap,
            currentGameState = "正在搜查阶段"
        )
        
        // 9. 骰点判定
        val diceResult = roomManager.handlePlayerInput(
            room.roomId,
            "player_001",
            ActionType.DICE_ROLL,
            "D20"
        ).getOrNull()
        
        // 骰点失败触发惩罚
        if (diceResult?.diceResult?.isSuccess == false) {
            val punishment = featureManager.detectAndPunish(
                diceResult.diceResult,
                GameType.JUBENSHA
            )
        }
        
        // 10. 更新成就
        featureManager.updateAchievementProgress(
            "player_001",
            "story_1"
        )
        
        // 11. 结束游戏
        val review = roomManager.endGame(room.roomId).getOrNull()
        
        // 12. 记录游戏
        featureManager.recordGame(
            playerId = "player_001",
            roomId = room.roomId,
            roomName = room.roomName,
            gameType = room.gameType.name,
            duration = review?.duration ?: 0,
            playerCount = 4,
            result = GameResult.VICTORY,
            score = 100,
            xpEarned = 200
        )
        
        // 13. 生成分享内容
        val shareContent = featureManager.generateShareContent(gameRecord)
    }
}
```

### 剧本格式示例

#### JSON格式剧本
```json
{
  "id": "ancient_castle_murder",
  "title": "古堡谋杀案",
  "gameType": "JUBENSHA",
  "rootNodeId": "scene_entrance",
  "nodes": {
    "scene_entrance": {
      "id": "scene_entrance",
      "description": "古堡大门",
      "narrativeText": "月圆之夜，你们来到了这座古老的城堡。厚重的橡木门在风中吱呀作响，门上的铁环已经锈迹斑斑。",
      "objectives": [
        {
          "id": "obj_enter_castle",
          "description": "进入古堡",
          "isOptional": false,
          "completionConditions": ["找到进入方法"]
        }
      ],
      "allowedActions": ["SEARCH", "INVESTIGATE", "TALK"],
      "cluePool": {
        "publicClues": [
          {
            "id": "clue_door_key",
            "name": "生锈的钥匙",
            "description": "一把古老的钥匙，似乎能打开某扇门",
            "importance": "IMPORTANT"
          }
        ],
        "privateClues": []
      },
      "diceRules": null,
      "imageUrl": null,
      "isCritical": false
    },
    "scene_hall": {
      "id": "scene_hall",
      "description": "古堡大厅",
      "narrativeText": "你们进入了宽敞的大厅。月光透过彩色玻璃窗洒在大理石地板上，形成斑驳的光影。大厅中央有一具尸体...",
      "objectives": [
        {
          "id": "obj_examine_body",
          "description": "检查尸体",
          "isOptional": false,
          "completionConditions": ["调查尸体"]
        }
      ],
      "allowedActions": ["SEARCH", "INVESTIGATE", "TALK"],
      "cluePool": {
        "publicClues": [
          {
            "id": "clue_bloody_knife",
            "name": "血迹斑斑的匕首",
            "description": "一把古老的匕首，刀刃上有干涸的血迹",
            "importance": "CRITICAL"
          }
        ],
        "privateClues": []
      },
      "diceRules": {
        "ruleType": "D20_SYSTEM",
        "diceType": "D20",
        "difficulty": 12,
        "successThreshold": 13
      },
      "imageUrl": null,
      "isCritical": true
    }
  },
  "edges": {
    "edge_entrance_to_hall": {
      "id": "edge_entrance_to_hall",
      "fromNodeId": "scene_entrance",
      "toNodeId": "scene_hall",
      "condition": {
        "type": "CLUE_FOUND",
        "requiredClues": ["clue_door_key"]
      },
      "priority": 1,
      "isFailSafe": false
    }
  },
  "globalClues": [],
  "failSafePolicies": [
    {
      "id": "failsafe_stuck_entrance",
      "triggerCondition": "玩家在入口停留超过10分钟",
      "targetNodeId": "scene_hall",
      "narrativeGuide": "突然，一阵风吹开了古堡的大门，你们别无选择只能进入...",
      "autoExecute": true
    }
  ]
}
```

#### Markdown格式剧本
```markdown
# 古堡谋杀案

## 场景一：古堡大门
月圆之夜，你们来到了这座古老的城堡。厚重的橡木门在风中吱呀作响。

**允许动作**: 搜查、调查、对话
**线索**: 生锈的钥匙

## 场景二：古堡大厅
你们进入了宽敞的大厅。月光透过彩色玻璃窗洒在大理石地板上，大厅中央有一具尸体...

**允许动作**: 搜查、调查、对话
**线索**: 血迹斑斑的匕首
**骰点**: D20，难度12
```

---

## 故障排除

### 常见问题

#### 1. MediaPipe模型加载失败
```
错误: Failed to initialize pose detector: Asset not found
解决: 确保pose_landmarker_lite.task文件在app/src/main/assets/目录下
```

#### 2. 相机权限被拒绝
```
错误: Permission Denial: starting Intent requires android.permission.CAMERA
解决: 在设置中授予相机权限，或检查运行时权限请求代码
```

#### 3. 网络连接失败
```
错误: Socket.IO connection failed
解决: 检查服务器地址是否正确，确保服务器正在运行
```

#### 4. LLM API调用失败
```
错误: API key invalid or quota exceeded
解决: 检查API Key是否正确，确认账户余额充足
```

#### 5. 数字人初始化失败
```
错误: XmovAvatar init failed
解决: 检查AppId和AppSecret是否正确配置
```

### 性能优化建议

#### 1. 内存优化
```kotlin
// 及时释放资源
override fun onDestroy() {
    super.onDestroy()
    featureManager.destroy()
    gameEngine?.destroy()
}

// 使用图片缓存
Glide.with(context)
    .load(imageUrl)
    .diskCacheStrategy(DiskCacheStrategy.ALL)
    .into(imageView)
```

#### 2. 网络优化
```kotlin
// 使用连接池
val client = OkHttpClient.Builder()
    .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
    .build()

// 实现断线重连
networkManager.setReconnectPolicy(
    maxRetries = 3,
    retryInterval = 5000
)
```

#### 3. 动作识别优化
```kotlin
// 降低检测频率
val analysisConfig = ImageAnalysis.Builder()
    .setTargetResolution(Size(640, 480))
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()

// 后台暂停检测
override fun onPause() {
    super.onPause()
    featureManager.pausePoseDetection()
}
```

---

## 扩展开发

### 自定义游戏模式

```kotlin
// 1. 扩展GameType枚举
enum class GameType {
    JUBENSHA,
    PAOTUAN,
    HAITANG,
    CUSTOM_MODE  // 新增自定义模式
}

// 2. 实现自定义规则
class CustomGameRules : GameRules {
    override fun validateAction(action: ActionType, scene: StoryNode): Boolean {
        // 自定义动作验证逻辑
        return true
    }
    
    override fun processResult(result: ActionResult): ActionResult {
        // 自定义结果处理逻辑
        return result
    }
}

// 3. 注册自定义模式
GameModeRegistry.register(GameType.CUSTOM_MODE, CustomGameRules())
```

### 自定义AI模型集成

```kotlin
// 1. 实现AI服务接口
class CustomAIService : AIService {
    override suspend fun generateImage(prompt: String): Result<String> {
        // 自定义图片生成逻辑
        return Result.success("generated_image_url")
    }
    
    override suspend fun detectEmotion(image: Bitmap): Result<EmotionState> {
        // 自定义情绪识别逻辑
        return Result.success(EmotionState(...))
    }
}

// 2. 注册自定义服务
AIServiceRegistry.register("custom_ai", CustomAIService())

// 3. 在配置中使用
AppSettings.setAIService("custom_ai")
```

### 自定义成就系统

```kotlin
// 1. 定义自定义成就
val customAchievements = listOf(
    Achievement(
        id = "custom_achievement_1",
        name = "完美推理",
        description = "在不使用提示的情况下解开谜题",
        category = AchievementCategory.CHALLENGE,
        maxProgress = 1,
        validator = { gameState ->
            // 自定义验证逻辑
            gameState.hintsUsed == 0 && gameState.mysterysSolved > 0
        }
    )
)

// 2. 注册自定义成就
achievementManager.registerCustomAchievements(customAchievements)
```

---

## API参考

### 核心API总览

| 模块 | 主要类 | 核心方法 |
|------|--------|----------|
| 游戏引擎 | GameEngine | handlePlayerInput(), getCurrentScene() |
| 房间管理 | RoomManager | createRoom(), joinRoom(), startGame() |
| 图片生成 | GameImageGenerator | generateSceneImage(), generateClueImage() |
| 动作识别 | PoseDetector | detectPose(), init() |
| 情绪识别 | EmotionDetector | detectEmotion(), formatForLLM() |
| 网络同步 | NetworkSyncManager | connect(), syncGameState() |
| Agent服务 | AgentService | parseScriptText(), generateStoryTree() |
| 角色系统 | CharacterManager | createCharacter(), addExperience() |
| 成就系统 | AchievementManager | updateProgress(), getPlayerAchievements() |
| 社交功能 | SocialManager | sendFriendRequest(), recordGame() |
| 功能集成 | FeatureIntegrationManager | initialize(), 所有功能的统一接口 |

### 完整API文档

详细的API文档请参考各模块的源代码注释和接口定义。每个公共方法都包含完整的KDoc文档，说明参数、返回值和使用示例。

---

## 总结

TRPG DM数字主持系统是一个功能丰富、技术先进的游戏平台，集成了多种AI技术和现代移动开发最佳实践。通过本文档，开发者可以：

1. **理解系统架构**：掌握各模块的职责和交互方式
2. **使用核心功能**：通过详细的API说明快速集成功能
3. **自定义扩展**：基于开放的架构添加自定义功能
4. **部署配置**：正确配置各种依赖服务
5. **故障排除**：快速定位和解决常见问题

该系统展现了游戏行业AI Native的发展趋势，为TRPG爱好者提供了全新的数字化游戏体验。随着AI技术的不断发展，系统还有巨大的扩展潜力。

---

*文档版本: v1.0.0*  
*最后更新: 2026年1月*