package com.xmov.metahuman.app.trpg

/**
 * 剧情树数据结构
 * 节点=场景，边=转场条件
 */
data class StoryTree(
    val id: String,
    val title: String,
    val gameType: GameType,
    val rootNodeId: String,
    val nodes: Map<String, StoryNode>,
    val edges: Map<String, TransitionEdge>,
    val globalClues: List<Clue>,
    val failSafePolicies: List<FailSafePolicy>
)

/**
 * 游戏模式
 */
enum class GameType {
    JUBENSHA,   // 剧本杀
    PAOTUAN,    // 跑团
    HAITANG     // 海龟汤
}

/**
 * 剧情节点（场景）
 */
data class StoryNode(
    val id: String,
    val description: String,
    val narrativeText: String,        // DM朗读的描述文本
    val objectives: List<Objective>,  // 场景目标
    val allowedActions: List<ActionType>, // 允许的玩家动作
    val cluePool: CluePool,           // 可发放线索池
    val diceRules: DiceRule?,         // 骰子规则
    val imageUrl: String?,            // 场景插画URL
    val isCritical: Boolean = false   // 是否为关键转折点
)

/**
 * 转场边（场景之间的连接）
 */
data class TransitionEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val condition: TransitionCondition, // 转场条件
    val priority: Int = 1,            // 优先级，用于选择最优路径
    val isFailSafe: Boolean = false   // 是否为纠偏路径
)

/**
 * 转场条件
 */
data class TransitionCondition(
    val type: ConditionType,
    val requiredClues: List<String> = emptyList(),
    val requiredDiceResult: DiceRequirement? = null,
    val requiredDecision: String? = null,
    val narrativeTrigger: String? = null
)

enum class ConditionType {
    CLUE_FOUND,        // 找到特定线索
    DICE_SUCCESS,      // 骰点成功
    DECISION_MADE,     // 特定决策
    NARRATIVE_INPUT,   // 玩家输入
    AUTOMATIC,         // 自动触发
    TIME_ELAPSED       // 时间流逝
}

/**
 * 场景目标
 */
data class Objective(
    val id: String,
    val description: String,
    val isOptional: Boolean = false,
    val completionConditions: List<String>
)

/**
 * 玩家动作类型
 */
enum class ActionType {
    SEARCH,           // 搜查
    TALK,             // 对话
    INVESTIGATE,      // 调查
    DICE_ROLL,        // 骰点
    MOVE,             // 移动
    USE_ITEM,         // 使用物品
    ATTACK,           // 攻击
    CUSTOM            // 自定义动作
}

/**
 * 线索池
 */
data class CluePool(
    val publicClues: List<Clue>,   // 公开线索
    val privateClues: List<Clue>   // 私密线索（玩家专属）
)

/**
 * 线索
 */
data class Clue(
    val id: String,
    val name: String,
    val description: String,
    val importance: ClueImportance,
    val targetPlayers: List<String> = emptyList(), // 目标玩家（私密线索）
    val isDistributed: Boolean = false,
    val imageUrl: String? = null
)

enum class ClueImportance {
    CRITICAL,  // 关键线索
    IMPORTANT, // 重要线索
    NORMAL,    // 普通线索
    MINOR      // 次要线索
}

/**
 * 骰子规则
 */
data class DiceRule(
    val ruleType: DiceRuleType,
    val diceType: DiceType,
    val difficulty: Int,
    val successThreshold: Int,
    val criticalSuccess: Int? = null,
    val criticalFailure: Int? = null,
    val modifiers: List<DiceModifier> = emptyList()
)

enum class DiceRuleType {
    D20_SYSTEM,       // D20系统
    D100_SYSTEM,      // D100系统（跑团）
    STORY_POINT,      // 故事点系统
    CUSTOM            // 自定义规则
}

enum class DiceType {
    D4, D6, D8, D10, D12, D20, D100
}

data class DiceModifier(
    val name: String,
    val value: Int,
    val source: String
)

data class DiceRequirement(
    val minResult: Int,
    val maxResult: Int,
    val mustSucceed: Boolean = true
)

/**
 * Fail-Safe纠偏策略
 */
data class FailSafePolicy(
    val id: String,
    val triggerCondition: String,       // 触发条件描述
    val targetNodeId: String,           // 纠偏目标节点
    val narrativeGuide: String,         // DM引导话术
    val autoExecute: Boolean = false    // 是否自动执行
)

/**
 * 游戏状态
 */
data class GameState(
    val roomId: String,
    val currentSceneId: String,
    val players: List<Player>,
    val distributedClues: List<Clue>,
    val diceHistory: List<DiceRoll>,
    val sceneHistory: List<SceneVisit>,
    val isActive: Boolean = true
)

/**
 * 玩家
 */
data class Player(
    val id: String,
    val name: String,
    val role: PlayerRole,
    val characterName: String? = null,
    val ownedClues: List<Clue> = emptyList(),
    val isHost: Boolean = false,
    val isOnline: Boolean = true
)

enum class PlayerRole {
    HOST,      // 房主
    PLAYER,    // 普通玩家
    OBSERVER   // 观察者
}

/**
 * 骰点记录
 */
data class DiceRoll(
    val id: String,
    val playerId: String,
    val playerName: String,
    val diceType: DiceType,
    val result: Int,
    val modifiers: List<DiceModifier>,
    val isSuccess: Boolean,
    val isCritical: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 场景访问记录
 */
data class SceneVisit(
    val sceneId: String,
    val sceneTitle: String,
    val visitTime: Long,
    val stayDuration: Long,
    val decisions: List<String>,
    val cluesReceived: List<String>
)

/**
 * 多人房间
 */
data class GameRoom(
    val roomId: String,
    val roomName: String,
    val gameType: GameType,
    val hostId: String,
    val storyTreeId: String,
    val maxPlayers: Int,
    val currentPlayers: Int,
    val status: RoomStatus,
    val createdAt: Long,
    val password: String? = null
)

enum class RoomStatus {
    LOBBY,      // 大厅等待中
    PLAYING,    // 游戏中
    PAUSED,     // 暂停
    ENDED       // 已结束
}

/**
 * 游戏复盘报告
 */
data class GameReview(
    val roomId: String,
    val roomName: String,
    val gameType: GameType,
    val duration: Long,
    val players: List<PlayerReview>,
    val storyPath: List<ScenePathNode>,
    val keyDecisions: List<KeyDecision>,
    val clueDistribution: List<ClueDistribution>,
    val diceStatistics: DiceStatistics,
    val endTime: Long
)

data class PlayerReview(
    val playerId: String,
    val playerName: String,
    val scenesVisited: Int,
    val cluesCollected: Int,
    val diceRolls: Int,
    val successRate: Float
)

data class ScenePathNode(
    val sceneId: String,
    val sceneTitle: String,
    val entryTime: Long,
    val exitTime: Long,
    val decisions: List<String>
)

data class KeyDecision(
    val sceneId: String,
    val sceneTitle: String,
    val decision: String,
    val playerId: String,
    val playerName: String,
    val impact: String,
    val timestamp: Long
)

data class ClueDistribution(
    val clueId: String,
    val clueName: String,
    val recipients: List<String>,
    val distributionTime: Long
)

data class DiceStatistics(
    val totalRolls: Int,
    val successCount: Int,
    val failureCount: Int,
    val criticalSuccesses: Int,
    val criticalFailures: Int,
    val averageResult: Float
)
