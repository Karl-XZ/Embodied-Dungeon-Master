# TRPG DM数字主持系统 - 新增功能说明

## 已实现的高级功能

### 1. 🖼️ 自动图片生成

**实现方式**: APIMart 的生图功能（Stable Diffusion XL / DALL-E 3）

**功能说明**:
- 根据线索描述自动生成线索卡图片
- 根据场景描述自动生成场景 CG
- 支持批量生成，带进度回调
- 图片缓存管理

**使用方法**:
```kotlin
val imageGenerator = GameImageGenerator(context)

// 生成线索图
val result = imageGenerator.generateClueImage(clue, gameType)

// 生成场景图
val result = imageGenerator.generateSceneImage(scene, gameType)

// 批量生成
val results = imageGenerator.generateCluesImagesBatch(clues, gameType) { current, total ->
    println("进度: $current/$total")
}
```

**配置**: 在功能设置中启用图片生成，选择 AI 模型

---

### 2. 🏃 玩家动作识别与惩罚系统

**实现方式**:
- **MediaPipe Pose**: 端侧姿态检测
- **手机摄像头**: CameraX 实时捕捉
- **手机陀螺仪**: 检测点头、摇头、举起设备等动作

**支持的动作**:
- 下蹲 (Squat)
- 跳跃 (Jump)
- 举手 (Raise Hands)
- 闪避 (Dodge)
- 蹲伏 (Crouch)
- 摇晃设备 (Shake)
- 点头 (Nod)
- 摇头 (Shake Head)

**惩罚系统**:
根据骰点失败结果，玩家需执行相应动作：
- 闪避失败 → 做 5 个深蹲
- 跳跃失败 → 原地跳 10 次
- 下蹲失败 → 保持蹲姿 30 秒
- 等等...

**使用方法**:
```kotlin
// 启动动作识别
featureManager.startPoseDetection { action, confidence ->
    println("检测到动作: $action, 置信度: $confidence")
}

// 检测并执行惩罚
val punishment = featureManager.detectAndPunish(diceResult, gameType)
if (punishment != null) {
    println("惩罚: ${punishment.description}")
    if (punishment.isCounted) {
        println("需要重复 ${punishment.count} 次")
    }
    if (punishment.isTimed) {
        println("需要持续 ${punishment.duration} 秒")
    }
}
```

**配置**: 在功能设置中启用动作识别和惩罚系统

---

### 3. 😊 玩家情绪状态识别

**实现方式**: 通义千问 VL (Qwen-VL) API 拍照识别

**识别内容**:
- 情绪类型 (积极/中性/消极/兴奋/沮丧/紧张/放松)
- 注意力水平 (高度/中度/低度/分心)
- 精力状态 (高/中/低)
- 疲劳程度 (无/轻度/中度/重度)
- 压力水平 (无/轻度/中度/重度)
- 投入度 (完全投入/部分投入/不投入)

**使用方法**:
```kotlin
val emotionDetector = EmotionDetector(context)

// 检测情绪
val emotionState = emotionDetector.detectEmotion(imageBitmap, currentGameState)

// 格式化为 LLM 输入
val emotionText = emotionDetector.formatForLLM(emotionState)
// 示例输出:
// 【玩家情绪状态】
// 情绪：积极
// 注意力：高度
// 精力：高
// 疲劳度：无
// 压力：无
// 投入度：完全投入
// DM建议：玩家状态良好，可以推进复杂剧情
```

**陀螺仪监听**:
```kotlin
// 监听点头、摇头等动作
gyroscopeMonitor.detectedAction.collect { action ->
    when (action) {
        GyroAction.NOD -> println("玩家表示同意")
        GyroAction.SHAKE_HEAD -> println("玩家表示不同意")
        GyroAction.LIFT -> println("玩家请求发言")
        // ...
    }
}
```

**配置**: 在功能设置中启用情绪识别

---

### 4. 🌐 网络多人同步

**实现方式**: Socket.IO 实时同步

**功能**:
- 房间加入/离开同步
- 游戏状态实时同步
- 聊天消息同步
- 骰点结果同步
- 玩家在线状态

**使用方法**:
```kotlin
// 连接服务器
networkSyncManager.connect("https://your-server.com")

// 设置回调
networkSyncManager.setNetworkCallbacks(
    onConnected = { println("已连接") },
    onDisconnected = { println("已断开") },
    onGameStateChanged = { gameState -> updateUI(gameState) },
    onPlayerJoined = { player -> showJoinMessage(player) },
    onChatMessage = { message -> displayChat(message) }
)

// 发送消息
networkSyncManager.sendChatMessage(roomId, playerId, playerName, "大家好！")

// 同步游戏状态
networkSyncManager.syncGameState(roomId, gameState)
```

**配置**: 在功能设置中设置 Socket.IO 服务器地址

---

### 5. 🤖 Agent 服务端

**实现方式**: LLM 驱动的智能服务

**功能**:
- 剧本智能解析：自然语言 → 结构化剧情树
- 剧情树自动生成：根据主题生成完整剧本
- Fail-Safe 策略自动生成：防止玩家跑偏

**使用方法**:
```kotlin
val agentService = AgentService()

// 解析剧本文本
val storyTree = agentService.parseScriptText(scriptText, GameType.JUBENSHA)

// 自动生成剧情树
val storyTree = agentService.generateStoryTree(
    theme = "古堡谋杀案",
    gameType = GameType.JUBENSHA,
    sceneCount = 5,
    complexity = Complexity.MEDIUM
)

// 生成 Fail-Safe 策略
val updated = agentService.generateFailSafePolicies(storyTree)
```

---

### 6. 🎭 角色系统

**功能**:
- 角色创建与自定义
- 属性系统 (力量/敏捷/体质/智力/智慧/魅力)
- 技能系统与升级
- 物品/背包系统
- 经验值与等级提升

**数据模型**:
```kotlin
data class Character(
    val id: String,
    val name: String,
    val role: String,
    val level: Int,
    val experience: Int,
    val stats: Stats,        // 属性
    val skills: List<Skill>,  // 技能
    val inventory: List<Item>, // 背包
    val playerId: String
)
```

**使用方法**:
```kotlin
val characterManager = CharacterManager(context)

// 创建角色
val character = characterManager.createCharacter(
    playerId = "player_123",
    name = "勇者小明",
    role = "warrior",
    avatar = "avatar_url"
)

// 添加经验
characterManager.addExperience(character.id, 100)

// 升级技能
characterManager.upgradeSkill(character.id, "skill_1")

// 使用物品
val (updatedChar, effect) = characterManager.useItem(character.id, "item_1")
```

---

### 7. 🏅 成就系统

**功能**:
- 多类别成就 (剧情/战斗/探索/社交/收集/挑战)
- 成就进度追踪
- 成就解锁奖励
- 实时成就通知

**默认成就**:
| 成就 | 类型 | 描述 |
|------|------|------|
| 初出茅庐 | 剧情 | 完成第一个剧本 |
| 真相大白 | 剧情 | 成功解开3个剧本杀谜题 |
| 史诗冒险 | 剧情 | 完成10个跑团剧本 |
| 大成功 | 战斗 | 掷出10次大成功 |
| 战神 | 战斗 | 赢得50场战斗 |
| 完美主义 | 挑战 | 在单局游戏中收集所有线索 |
| 无伤通关 | 挑战 | 在不失败的情况下完成剧本 |

**使用方法**:
```kotlin
val achievementManager = AchievementManager(context)

// 初始化
achievementManager.initialize()

// 更新进度
val (achievement, progress) = achievementManager.updateProgress(
    playerId = "player_123",
    achievementId = "story_1",
    increment = 1
)

// 检查是否解锁
val isUnlocked = achievementManager.isUnlocked("player_123", "story_1")

// 获取玩家成就
achievementManager.getPlayerAchievements("player_123").collect { achievements ->
    displayAchievements(achievements)
}
```

---

### 8. 👥 社交功能

**功能**:
- 好友系统 (添加/删除/屏蔽)
- 好友请求管理
- 房间收藏
- 游戏记录
- 复盘分享

**使用方法**:
```kotlin
val socialManager = SocialManager(context)

// 好友系统
socialManager.sendFriendRequest("from_player", "Player A", "to_player", "一起玩吗？")
socialManager.acceptFriendRequest("request_id")
socialManager.getFriends("player_id").collect { friends -> displayFriends(friends) }

// 房间收藏
socialManager.favoriteRoom("player_id", "room_id", "古堡迷案", "JUBENSHA", "DM_小明")
socialManager.getFavoriteRooms("player_id").collect { favorites -> displayFavorites(favorites) }

// 游戏记录
socialManager.recordGame(
    playerId = "player_id",
    roomId = "room_id",
    roomName = "古堡迷案",
    gameType = "JUBENSHA",
    duration = 3600000,
    playerCount = 4,
    result = GameResult.VICTORY,
    score = 100,
    xpEarned = 200,
    achievements = listOf("story_1", "combat_1")
)

// 分享复盘
val shareContent = socialManager.generateShareContent(record)
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

## 功能集成管理器

**FeatureIntegrationManager** 统一管理所有功能：

```kotlin
val featureManager = FeatureIntegrationManager(context)

// 初始化所有功能
featureManager.initialize()

// 图片生成
featureManager.generateGameImages(storyTreeId, sceneIds, clueIds) { current, total, desc ->
    println("[$current/$total] $desc")
}

// 动作识别
featureManager.startPoseDetection { action, confidence ->
    println("检测到动作: $action")
}

// 情绪识别
val emotion = featureManager.detectPlayerEmotion(imageBitmap, gameState)
val emotionText = featureManager.formatEmotionForLLM(emotion)

// 网络同步
featureManager.connectToServer(AppSettings.getSocketServerUrl())

// Agent 服务
val storyTree = featureManager.generateStoryTree("主题", GameType.JUBENSHA)

// 角色系统
val character = featureManager.createCharacter("player_id", "角色名", "warrior", null)

// 成就系统
featureManager.updateAchievementProgress("player_id", "story_1")

// 社交功能
featureManager.recordGame(/*...*/)
val shareContent = featureManager.generateShareContent(record)
```

---

## 权限要求

在 `AndroidManifest.xml` 中添加：

```xml
<!-- 相机权限 (动作识别) -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 录音权限 (语音识别) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 相机特性 -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

---

## 配置说明

### 基础配置 (SettingsActivity)
- Xmov AppId / AppSecret (数字人)
- LLM API Key / Base URL / Model (AI 功能)

### 功能配置 (FeatureSettingsActivity)
- 图片生成：启用/模型选择
- 动作识别：启用/惩罚系统
- 情绪识别：启用
- 网络同步：Socket.IO 服务器地址
- Agent 服务：Base URL

---

## 后续扩展

### 待实现功能
1. **图像生成 API 集成优化**
   - 场景插画自动生成
   - 线索卡图片生成
   - 角色形象生成

2. **高级社交功能**
   - 公会系统
   - 排行榜
   - 社区活动
   - 好友对战

3. **AI 增强功能**
   - 实时语音对话（ASR + TTS + LLM）
   - 多模态交互（语音+手势）
   - 个性化 DM 语音风格

4. **游戏模式扩展**
   - 实时战斗模式
   - 合作任务模式
   - 竞技场模式

---

## 注意事项

1. **SDK 配置**: 必须配置有效的 AppId/AppSecret 才能使用数字人
2. **LLM 配置**: 必须配置有效的 API Key 才能使用 AI 功能
3. **权限**: 动作识别需要相机权限，语音识别需要录音权限
4. **网络**: 网络同步需要服务器支持
5. **性能**: 动作识别和情绪识别会消耗较多资源，建议在设置中按需启用
