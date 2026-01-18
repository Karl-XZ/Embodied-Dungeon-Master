package com.xmov.metahuman.app.trpg

import android.util.Log
import com.xmov.metahuman.app.AppSettings
import com.xmov.metahuman.app.llm.ApimartClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * DM Orchestrator - 游戏引擎核心
 * 负责驱动整个游戏流程、状态管理、规则裁决
 */
class GameEngine(
    private val storyTree: StoryTree,
    private val roomId: String
) {
    private val TAG = "GameEngine"

    private val _gameState = MutableStateFlow(
        GameState(
            roomId = roomId,
            currentSceneId = storyTree.rootNodeId,
            players = emptyList(),
            distributedClues = emptyList(),
            diceHistory = emptyList(),
            sceneHistory = emptyList(),
            isActive = true
        )
    )
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    /**
     * 获取游戏类型
     */
    val gameType: GameType get() = storyTree.gameType

    private val gameScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentSceneEntryTime = System.currentTimeMillis()

    // 当前场景内累计获得的线索（用于在离开场景时写入 sceneHistory）
    private val currentSceneClueIds = mutableSetOf<String>()

    // 轻量对话历史（用于 LLM 生成更连贯的 DM 回应）
    private val dialogueHistory = ArrayDeque<Pair<String, String>>() // role->content

    // 当前等待互动鉴定
    private val _pendingInteractionCheck = MutableStateFlow<PendingInteractionCheck?>(null)
    val pendingInteractionCheck: StateFlow<PendingInteractionCheck?> = _pendingInteractionCheck.asStateFlow()

    // 互动鉴定结果
    private val _interactionCheckResult = MutableStateFlow<InteractionCheckResult?>(null)
    val interactionCheckResult: StateFlow<InteractionCheckResult?> = _interactionCheckResult.asStateFlow()

    // 核心模块
    private val rulesArbiter = RulesArbiter()
    private val clueMaster = ClueMaster(storyTree)
    private val sceneDirector = SceneDirector(storyTree)

    private val llmClient = ApimartClient()

    /**
     * 玩家输入处理
     */
    suspend fun handlePlayerInput(
        playerId: String,
        actionType: ActionType,
        actionData: String
    ): ActionResult {
        Log.d(TAG, "Player $playerId action: $actionType")

        val currentState = _gameState.value
        val currentScene = storyTree.nodes[currentState.currentSceneId]
            ?: return ActionResult(
                success = false,
                message = "当前场景不存在",
                narration = null
            )

        // 1. 验证动作是否允许
        if (actionType !in currentScene.allowedActions) {
            return ActionResult(
                success = false,
                message = "当前场景不允许此动作",
                narration = null
            )
        }

        // 2. 根据动作类型处理
        return when (actionType) {
            ActionType.SEARCH -> handleSearch(playerId, actionData, currentScene)
            ActionType.TALK -> handleTalk(playerId, actionData, currentScene)
            ActionType.INVESTIGATE -> handleInvestigate(playerId, actionData, currentScene)
            ActionType.DICE_ROLL -> handleDiceRoll(playerId, actionData, currentScene)
            ActionType.MOVE -> handleMove(playerId, actionData, currentScene)
            ActionType.USE_ITEM -> handleUseItem(playerId, actionData, currentScene)
            ActionType.ATTACK -> handleAttack(playerId, actionData, currentScene)
            ActionType.CUSTOM -> handleCustomAction(playerId, actionData, currentScene)
        }
    }

    /**
     * 搜查动作
     */
    private suspend fun handleSearch(
        playerId: String,
        target: String,
        scene: StoryNode
    ): ActionResult {
        val narration = "你仔细搜查了$target..."

        // 检查线索池
        val newClues = clueMaster.distributeClues(
            scene = scene,
            playerId = playerId,
            triggerCondition = "SEARCH_$target"
        )

        updateDistributedClues(playerId, newClues)

        // 线索触发转场
        val nextSceneByClue = if (newClues.isNotEmpty()) {
            sceneDirector.evaluateTransition(
                currentSceneId = scene.id,
                conditionType = ConditionType.CLUE_FOUND,
                playerClueIds = getPlayerClueIds(playerId)
            )
        } else null

        if (nextSceneByClue != null && nextSceneByClue != scene.id) {
            transitionToScene(nextSceneByClue, decisions = listOf("搜查：$target"))
        }

        return ActionResult(
            success = true,
            message = if (newClues.isNotEmpty()) "发现了 ${newClues.size} 条线索！" else "没有发现什么",
            narration = narration,
            cluesReceived = newClues
        )
    }

    /**
     * 对话动作
     */
    private suspend fun handleTalk(
        playerId: String,
        dialogue: String,
        scene: StoryNode
    ): ActionResult {
        // 记录玩家输入
        pushDialogue("player", dialogue)

        // 若玩家显式给出决策指令，则优先尝试 DECISION_MADE 转场
        val decision = extractDecision(dialogue)
        val nextSceneByDecision = if (decision != null) {
            sceneDirector.evaluateTransition(
                currentSceneId = scene.id,
                conditionType = ConditionType.DECISION_MADE,
                decision = decision,
                playerClueIds = getPlayerClueIds(playerId)
            )
        } else null

        // 判断是否触发特定剧情分支
        val nextScene = sceneDirector.evaluateTransition(
            currentSceneId = scene.id,
            playerInput = dialogue,
            conditionType = ConditionType.NARRATIVE_INPUT
        )

        // 生成DM的回应，而不是简单重复玩家的话
        val dmResponse = generateDMResponse(playerId, dialogue, scene)
        pushDialogue("dm", dmResponse)

        // 兼容 AUTOMATIC 线性剧本：当玩家输入“继续/下一步/下一幕”等时，触发自动转场
        val autoKeywords = listOf("继续", "下一步", "下一幕", "下一段", "推进")
        val nextSceneAuto = if (nextScene == null && autoKeywords.any { dialogue.contains(it) }) {
            sceneDirector.evaluateTransition(
                currentSceneId = scene.id,
                conditionType = ConditionType.AUTOMATIC,
                playerClueIds = getPlayerClueIds(playerId)
            )
        } else null

        val finalNext = nextSceneByDecision ?: nextScene ?: nextSceneAuto

        if (finalNext != null && finalNext != scene.id) {
            transitionToScene(finalNext, listOf("对话：$dialogue"))
            return ActionResult(
                success = true,
                message = "剧情推进到下一场景",
                narration = dmResponse
            )
        }

        return ActionResult(
            success = true,
            message = "DM听取了你的发言",
            narration = dmResponse
        )
    }

    /**
     * 生成DM回应
     */
    private suspend fun generateDMResponse(playerId: String, playerInput: String, scene: StoryNode): String {
        // 若未配置 LLM，则兜底为本地规则回应
        if (!AppSettings.hasLlmConfig()) {
            return localRuleDmResponse(playerInput, scene)
        }

        val apiKey = AppSettings.getLlmApiKey().orEmpty()
        val baseUrl = AppSettings.getLlmBaseUrl()
        val model = AppSettings.getLlmModel()
        val temperature = AppSettings.getLlmTemperature().toDouble()

        val system = buildDmSystemPrompt(storyTree.gameType)
        val user = buildDmUserPrompt(playerId, playerInput, scene)

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            // 附加少量历史上下文（最多 6 轮）
            val history = dialogueHistory.takeLast(12)
            for (turn in history) {
                put(JSONObject().put("role", if (turn.first == "dm") "assistant" else "user").put("content", turn.second))
            }
            put(JSONObject().put("role", "user").put("content", user))
        }

        val res = withContext(Dispatchers.IO) {
            llmClient.chatCompletions(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = 250
            )
        }

        return res.getOrElse {
            Log.w(TAG, "LLM DM 生成失败，使用本地兜底：${it.message}")
            localRuleDmResponse(playerInput, scene)
        }.trim().take(400)
    }

    private fun buildDmSystemPrompt(gameType: GameType): String {
        val mode = when (gameType) {
            GameType.JUBENSHA -> "剧本杀悬疑推理"
            GameType.PAOTUAN -> "跑团奇幻冒险"
            GameType.HAITANG -> "海龟汤（只能回答：是/不是/无关）"
        }
        return """
你是一个专业 DM（主持人），正在主持一局：$mode。

输出规则：
1) 只输出纯文本中文，不要 Markdown，不要编号列表，不要表情符号。
2) 语气自然，像主持人现场回应。
3) 长度：剧本杀/跑团 2~4 句；海龟汤只输出“是”或“不是”或“无关”，可加最多 1 句简短提示（不泄露谜底）。
4) 不要捏造玩家已经获得但上下文未提供的关键事实。
""".trim()
    }

    private fun buildDmUserPrompt(playerId: String, playerInput: String, scene: StoryNode): String {
        val player = _gameState.value.players.find { it.id == playerId }
        val clueBrief = player?.ownedClues?.takeLast(8)?.joinToString("；") { "${it.name}:${it.description.take(18)}" } ?: "无"
        val objectives = if (scene.objectives.isNotEmpty()) scene.objectives.joinToString("；") { it.description } else "无"
        return """
当前场景：${scene.description}
场景叙事（DM朗读背景）：${scene.narrativeText.take(280)}
场景目标：$objectives
玩家已知线索：$clueBrief

玩家输入：$playerInput

请给出 DM 的回应，并引导玩家进行下一步（如：继续对话/调查/搜查/投骰/移动）。
""".trim()
    }

    private fun localRuleDmResponse(playerInput: String, scene: StoryNode): String {
        val gameType = storyTree.gameType
        return when (gameType) {
            GameType.JUBENSHA -> {
                when {
                    playerInput.contains("谁") || playerInput.contains("凶手") ->
                        "关于凶手的线索需要你通过调查来发现。你可以继续搜查或询问在场人物。"
                    playerInput.contains("为什么") || playerInput.contains("原因") ->
                        "真相往往藏在细节里。你可以尝试调查现场，或让大家复述各自的不在场证明。"
                    playerInput.contains("线索") || playerInput.contains("证据") ->
                        "把注意力放在可疑物品与时间线。你也许该再搜查一次关键区域。"
                    else ->
                        "我听到了你的想法。先把你刚才的疑点落到一次具体行动上：搜查、调查或询问，都可以。"
                }
            }
            GameType.PAOTUAN -> {
                when {
                    playerInput.contains("攻击") || playerInput.contains("战斗") ->
                        "你准备发起攻击。请进行一次骰点判定，看看战局如何展开。"
                    playerInput.contains("探索") || playerInput.contains("查看") ->
                        "你开始仔细观察四周。也许一个搜查或调查能带来收获。"
                    else ->
                        "好的，按你的想法行动吧。你可以选择调查、搜查或移动到更关键的位置。"
                }
            }
            GameType.HAITANG -> {
                // 海龟汤兜底保持简短
                when {
                    playerInput.contains("吗") || playerInput.endsWith("?") || playerInput.endsWith("？") -> "无关"
                    else -> "无关"
                }
            }
        }
    }

    /**
     * 调查动作
     */
    private suspend fun handleInvestigate(
        playerId: String,
        target: String,
        scene: StoryNode
    ): ActionResult {
        val narration = "你仔细调查了${target}的细节..."

        // 可能需要骰点判定
        if (scene.diceRules != null) {
            val diceResult = rulesArbiter.rollDice(
                diceType = scene.diceRules.diceType,
                difficulty = scene.diceRules.difficulty,
                modifiers = emptyList()
            )

            if (diceResult.isSuccess) {
                val clues = clueMaster.distributeClues(
                    scene = scene,
                    playerId = playerId,
                    triggerCondition = "INVESTIGATE_SUCCESS"
                )
                updateDistributedClues(playerId, clues)

                // 线索触发转场
                val nextSceneByClue = if (clues.isNotEmpty()) {
                    sceneDirector.evaluateTransition(
                        currentSceneId = scene.id,
                        conditionType = ConditionType.CLUE_FOUND,
                        playerClueIds = getPlayerClueIds(playerId)
                    )
                } else null

                if (nextSceneByClue != null && nextSceneByClue != scene.id) {
                    transitionToScene(nextSceneByClue, listOf("调查成功：$target"))
                }
                return ActionResult(
                    success = true,
                    message = "调查成功！发现了关键信息",
                    narration = narration,
                    diceResult = diceResult,
                    cluesReceived = clues
                )
            } else {
                return ActionResult(
                    success = false,
                    message = "调查未能发现更多信息",
                    narration = narration,
                    diceResult = diceResult
                )
            }
        }

        return ActionResult(
            success = true,
            message = "调查完成",
            narration = narration
        )
    }

    /**
     * 骰点动作
     */
    private suspend fun handleDiceRoll(
        playerId: String,
        diceTypeStr: String,
        scene: StoryScene
    ): ActionResult {
        val diceType = when (diceTypeStr.uppercase()) {
            "D20" -> DiceType.D20
            "D100" -> DiceType.D100
            "D6" -> DiceType.D6
            else -> DiceType.D20
        }

        val rule = scene.diceRules ?: DiceRule(
            ruleType = DiceRuleType.STORY_POINT,
            diceType = diceType,
            difficulty = 10,
            successThreshold = 11
        )

        val diceResult = rulesArbiter.rollDice(
            diceType = rule.diceType,
            difficulty = rule.difficulty,
            modifiers = rule.modifiers
        )

        // 记录骰点
        addDiceHistory(
            playerId = playerId,
            playerName = getPlayerName(playerId),
            diceType = rule.diceType,
            result = diceResult
        )

        // 检查是否触发转场
        val nextScene = sceneDirector.evaluateTransition(
            currentSceneId = scene.id,
            diceResult = diceResult,
            conditionType = ConditionType.DICE_SUCCESS,
            playerClueIds = getPlayerClueIds(playerId)
        )

        if (nextScene != null && nextScene != scene.id) {
            transitionToScene(nextScene, listOf("骰点：${diceResult.value}"))
        }

        return ActionResult(
            success = diceResult.isSuccess,
            message = if (diceResult.isSuccess) "骰点成功！" else "骰点失败",
            narration = "你投出了 ${diceResult.value} 点",
            diceResult = diceResult
        )
    }

    /**
     * 移动动作
     */
    private suspend fun handleMove(
        playerId: String,
        destination: String,
        scene: StoryNode
    ): ActionResult {
        // 检查转场边
        val nextSceneId = sceneDirector.findTransitionByDescription(
            currentSceneId = scene.id,
            description = destination
        )

        if (nextSceneId != null) {
            transitionToScene(nextSceneId, listOf("移动到：${destination}"))
            return ActionResult(
                success = true,
                message = "进入新场景",
                narration = "你向${destination}移动..."
            )
        }

        return ActionResult(
            success = false,
            message = "无法移动到该位置",
            narration = null
        )
    }

    /**
     * 使用物品
     */
    private suspend fun handleUseItem(
        playerId: String,
        itemName: String,
        scene: StoryNode
    ): ActionResult {
        return ActionResult(
            success = true,
            message = "使用了 $itemName",
            narration = "你拿出了 $itemName..."
        )
    }

    /**
     * 攻击动作
     */
    private suspend fun handleAttack(
        playerId: String,
        target: String,
        scene: StoryNode
    ): ActionResult {
        return ActionResult(
            success = true,
            message = "发起了攻击",
            narration = "你向 $target 发起了攻击！"
        )
    }

    /**
     * 自定义动作
     */
    private suspend fun handleCustomAction(
        playerId: String,
        action: String,
        scene: StoryNode
    ): ActionResult {
        // Fail-Safe 检查
        val failSafe = evaluateFailSafe(action, scene)

        if (failSafe != null) {
            return ActionResult(
                success = false,
                message = "动作已被限制",
                narration = failSafe.narrativeGuide,
                failSafe = failSafe
            )
        }

        // 允许通过自定义动作触发决策转场，例如："决策:accuse_p1" / "decision:accuse_..."
        val decision = extractDecision(action)
        val nextByDecision = if (decision != null) {
            sceneDirector.evaluateTransition(
                currentSceneId = scene.id,
                conditionType = ConditionType.DECISION_MADE,
                decision = decision,
                playerClueIds = getPlayerClueIds(playerId)
            )
        } else null

        if (nextByDecision != null && nextByDecision != scene.id) {
            transitionToScene(nextByDecision, listOf("自定义动作决策：$decision"))
            return ActionResult(
                success = true,
                message = "决策生效，剧情推进",
                narration = action
            )
        }

        return ActionResult(
            success = true,
            message = "执行自定义动作",
            narration = action
        )
    }

    /**
     * Fail-Safe 评估
     */
    private fun evaluateFailSafe(
        action: String,
        scene: StoryNode
    ): FailSafePolicy? {
        // 检查是否触发 Fail-Safe
        return storyTree.failSafePolicies.firstOrNull { policy ->
            // 简化的触发条件判断
            action.contains(policy.triggerCondition) ||
            scene.id.contains(policy.triggerCondition)
        }
    }

    /**
     * 场景转场
     */
    private fun transitionToScene(
        nextSceneId: String,
        decisions: List<String>
    ) {
        val currentState = _gameState.value
        val sceneVisitDuration = System.currentTimeMillis() - currentSceneEntryTime

        // 记录场景访问
        val sceneVisit = SceneVisit(
            sceneId = currentState.currentSceneId,
            sceneTitle = storyTree.nodes[currentState.currentSceneId]?.description ?: "未知场景",
            visitTime = currentSceneEntryTime,
            stayDuration = sceneVisitDuration,
            decisions = decisions,
            cluesReceived = currentSceneClueIds.toList()
        )

        _gameState.value = currentState.copy(
            currentSceneId = nextSceneId,
            sceneHistory = currentState.sceneHistory + sceneVisit
        )

        currentSceneEntryTime = System.currentTimeMillis()
        currentSceneClueIds.clear()

        Log.d(TAG, "Transition to scene: $nextSceneId")
    }

    /**
     * 更新已发放线索
     */
    private fun updateDistributedClues(newClues: List<Clue>) {
        // 旧签名保留：不更新玩家，仅更新全局发放表（兼容旧调用）
        val currentState = _gameState.value
        val merged = (currentState.distributedClues + newClues)
            .distinctBy { it.id }
        _gameState.value = currentState.copy(distributedClues = merged)
    }

    private fun updateDistributedClues(playerId: String, newClues: List<Clue>) {
        if (newClues.isEmpty()) return
        val currentState = _gameState.value

        // 更新全局已发放线索
        val mergedGlobal = (currentState.distributedClues + newClues).distinctBy { it.id }

        // 更新玩家已拥有线索
        val updatedPlayers = currentState.players.map { p ->
            if (p.id != playerId) return@map p
            val mergedOwned = (p.ownedClues + newClues).distinctBy { it.id }
            p.copy(ownedClues = mergedOwned)
        }

        // 当前场景线索记录
        newClues.forEach { currentSceneClueIds.add(it.id) }

        _gameState.value = currentState.copy(
            distributedClues = mergedGlobal,
            players = updatedPlayers
        )
    }

    private fun getPlayerClueIds(playerId: String): Set<String> {
        val player = _gameState.value.players.find { it.id == playerId }
        return player?.ownedClues?.map { it.id }?.toSet() ?: emptySet()
    }

    /**
     * 从玩家输入中提取“决策”指令。
     *
     * 支持格式示例：
     * - 决策:accuse_xxx
     * - decision:accuse_xxx
     * - 选择:xxx
     * - accuse_xxx（直接输入 requiredDecision）
     */
    private fun extractDecision(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null

        // 允许直接输入 requiredDecision（常见：accuse_...）
        if (t.startsWith("accuse_", ignoreCase = true)) {
            return t
        }

        val prefixes = listOf(
            "决策:", "决策：",
            "decision:", "decision：",
            "选择:", "选择：",
            "choice:", "choice："
        )

        for (p in prefixes) {
            if (t.startsWith(p, ignoreCase = true)) {
                val v = t.substring(p.length).trim()
                return v.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun pushDialogue(role: String, content: String) {
        dialogueHistory.addLast(role to content)
        while (dialogueHistory.size > 12) {
            dialogueHistory.removeFirst()
        }
    }

    /**
     * 添加骰点记录
     */
    private fun addDiceHistory(
        playerId: String,
        playerName: String,
        diceType: DiceType,
        result: DiceRollResult
    ) {
        val diceRoll = DiceRoll(
            id = "dice_${System.currentTimeMillis()}",
            playerId = playerId,
            playerName = playerName,
            diceType = diceType,
            result = result.value,
            modifiers = emptyList(),
            isSuccess = result.isSuccess,
            isCritical = result.isCritical,
            timestamp = System.currentTimeMillis()
        )

        val currentState = _gameState.value
        _gameState.value = currentState.copy(
            diceHistory = currentState.diceHistory + diceRoll
        )
    }

    /**
     * 获取当前场景
     */
    fun getCurrentScene(): StoryNode? {
        return storyTree.nodes[_gameState.value.currentSceneId]
    }

    /**
     * 检测玩家输入是否包含互动鉴定请求
     */
    fun detectInteractionCheckRequest(playerInput: String): InteractionCheckRequest? {
        val input = playerInput.trim()

        // 跑团模式下支持互动鉴定
        if (gameType != GameType.PAOTUAN) return null

        // 只识别固定关键词
        if (!input.contains("互动鉴定", ignoreCase = true)) return null

        // 提取目标动作（移除关键词后剩余的部分）
        val action = input
            .replace(Regex("互动鉴定|我要互动鉴定|是"), "")
            .trim()
            .takeIf { it.isNotBlank() } ?: "挑战"

        // 确定难度
        val difficulty = extractDifficulty(input)

        return InteractionCheckRequest(
            action = action,
            difficulty = difficulty,
            fullInput = input
        )
    }

    /**
     * 检测玩家输入是否包含图片生成请求
     */
    fun detectImageGenerationRequest(playerInput: String): ImageGenerationRequest? {
        val input = playerInput.trim()

        // 只识别固定关键词
        if (!input.contains("图片生成", ignoreCase = true)) return null

        // 提取描述内容（移除关键词后剩余的部分）
        val description = input
            .replace(Regex("图片生成|是"), "")
            .trim()

        return ImageGenerationRequest(
            description = description,
            type = ImageGenerationType.CUSTOM,
            sceneId = null,
            fullInput = input
        )
    }

    /**
     * 检测玩家输入是否包含拍照识别请求
     */
    fun detectPhotoRecognitionRequest(playerInput: String): PhotoRecognitionRequest? {
        val input = playerInput.trim()

        // 只识别固定关键词
        if (!input.contains("拍照识别", ignoreCase = true)) return null

        // 提取识别目标（移除关键词后剩余的部分）
        val target = input
            .replace(Regex("拍照识别|是"), "")
            .trim()

        return PhotoRecognitionRequest(
            target = if (target.isNotBlank()) target else "物品",
            fullInput = input
        )
    }

    /**
     * 提取鉴定的目标动作
     */
    private fun extractActionForCheck(input: String): String {
        // 移除请求关键词
        val cleaned = input
            .replace(Regex("我要|互动鉴定|动作鉴定|物理鉴定|动作挑战|鉴定|挑战"), "")
            .trim()

        return if (cleaned.isNotBlank()) cleaned else "挑战"
    }

    /**
     * 提取鉴定难度
     */
    private fun extractDifficulty(input: String): Int {
        return when {
            input.contains("简单", ignoreCase = true) -> 1
            input.contains("容易", ignoreCase = true) -> 1
            input.contains("普通", ignoreCase = true) -> 3
            input.contains("中等", ignoreCase = true) -> 3
            input.contains("困难", ignoreCase = true) -> 5
            input.contains("很难", ignoreCase = true) -> 7
            input.contains("极难", ignoreCase = true) -> 9
            input.contains("不可能", ignoreCase = true) -> 10
            else -> 3  // 默认中等难度
        }
    }

    /**
     * 发起互动鉴定
     */
    fun startInteractionCheck(playerId: String, request: InteractionCheckRequest) {
        val scene = getCurrentScene() ?: return

        // 生成鉴定ID
        val checkId = "interaction_${System.currentTimeMillis()}"

        val pendingCheck = PendingInteractionCheck(
            checkId = checkId,
            playerId = playerId,
            action = request.action,
            difficulty = request.difficulty,
            sceneId = scene.id,
            timestamp = System.currentTimeMillis()
        )

        _pendingInteractionCheck.value = pendingCheck
        Log.d(TAG, "Started interaction check: $pendingCheck")
    }

    /**
     * 处理互动鉴定结果
     */
    fun handleInteractionCheckResult(checkId: String, success: Boolean, detectedActions: List<String>) {
        val pendingCheck = _pendingInteractionCheck.value
        if (pendingCheck?.checkId != checkId) {
            Log.w(TAG, "Interaction check ID mismatch: expected $checkId, got ${pendingCheck?.checkId}")
            return
        }

        val result = InteractionCheckResult(
            checkId = checkId,
            playerId = pendingCheck.playerId,
            action = pendingCheck.action,
            difficulty = pendingCheck.difficulty,
            success = success,
            detectedActions = detectedActions,
            timestamp = System.currentTimeMillis()
        )

        _interactionCheckResult.value = result
        _pendingInteractionCheck.value = null

        Log.d(TAG, "Interaction check result: $result")

        // 根据结果处理游戏逻辑
        if (success) {
            handleInteractionSuccess(pendingCheck.playerId, pendingCheck.action, pendingCheck.sceneId)
        } else {
            handleInteractionFailure(pendingCheck.playerId, pendingCheck.action, pendingCheck.sceneId)
        }
    }

    /**
     * 互动鉴定成功
     */
    private fun handleInteractionSuccess(playerId: String, action: String, sceneId: String) {
        val scene = storyTree.nodes[sceneId] ?: return

        // 分配线索（如果适用）
        val newClues = clueMaster.distributeClues(
            scene = scene,
            playerId = playerId,
            triggerCondition = "INTERACTION_SUCCESS"
        )

        updateDistributedClues(playerId, newClues)

        // 触发转场
        val nextScene = sceneDirector.evaluateTransition(
            currentSceneId = sceneId,
            conditionType = ConditionType.DICE_SUCCESS,
            playerClueIds = getPlayerClueIds(playerId)
        )

        if (nextScene != null && nextScene != sceneId) {
            transitionToScene(nextScene, listOf("互动鉴定成功：$action"))
        }
    }

    /**
     * 互动鉴定失败
     */
    private fun handleInteractionFailure(playerId: String, action: String, sceneId: String) {
        val scene = storyTree.nodes[sceneId] ?: return

        // 尝试使用骰子作为后备
        val diceResult = rulesArbiter.rollDice(
            diceType = scene.diceRules?.diceType ?: DiceType.D20,
            difficulty = scene.diceRules?.difficulty ?: 10,
            modifiers = emptyList()
        )

        addDiceHistory(
            playerId,
            getPlayerName(playerId),
            scene.diceRules?.diceType ?: DiceType.D20,
            diceResult
        )

        if (diceResult.isSuccess) {
            Log.d(TAG, "Interaction failed, dice roll succeeded as fallback")
            handleInteractionSuccess(playerId, action, sceneId)
        } else {
            Log.d(TAG, "Both interaction and dice roll failed")
            // 失败后果由调用方处理
        }
    }

    /**
     * 取消互动鉴定
     */
    fun cancelInteractionCheck() {
        _pendingInteractionCheck.value = null
        _interactionCheckResult.value = null
    }

    /**
     * 获取玩家名称
     */
    private fun getPlayerName(playerId: String): String {
        return _gameState.value.players.find { it.id == playerId }?.name ?: "未知玩家"
    }

    /**
     * 使用 LLM 处理图片生成 Prompt
     * 将用户的原始输入转换为适合图片生成的详细描述
     */
    suspend fun processImagePromptWithLLM(userInput: String): Result<String> {
        val currentScene = getCurrentScene()
        val sceneContext = currentScene?.narrativeText ?: currentScene?.description ?: "未知场景"

        val prompt = """
            你是一个 TRPG 游戏的图像生成助手。玩家想要生成一张图片，请根据以下信息生成一个详细的图片生成 Prompt。

            游戏类型: ${gameType.name}
            当前场景描述: $sceneContext
            玩家输入: $userInput

            请只输出图片生成的 Prompt，不要包含其他文字。Prompt 应该：
            1. 描述场景的视觉元素（人物、环境、物体、光影等）
            2. 根据游戏类型添加风格描述
               - JUBENSHA: 悬疑推理风格，暗调氛围，电影感，高清细节
               - PAOTUAN: 奇幻冒险风格，魔法世界，史诗感，高清细节
               - HAITANG: 悬疑解谜风格，神秘氛围，引人思考，高清细节
            3. 保持简洁但描述充分
        """.trimIndent()

        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val apiKey = AppSettings.getLlmApiKey().orEmpty()
            val baseUrl = AppSettings.getLlmBaseUrl()
            val model = AppSettings.getLlmModel()

            withContext(Dispatchers.IO) {
            llmClient.chatCompletions(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                temperature = 0.7
            )
        }.map { response ->
                response.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 处理图片 Prompt 失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM 格式化图片生成结果
     */
    suspend fun formatImageGenerationResult(imageUrl: String, originalInput: String): Result<String> {
        val currentScene = getCurrentScene()
        val sceneContext = currentScene?.narrativeText ?: currentScene?.description ?: "未知场景"

        val prompt = """
            玩家请求生成了一张图片，请以 DM（游戏主持人）的口吻给出一个简短、自然的回应。

            游戏类型: ${gameType.name}
            当前场景: $sceneContext
            玩家输入: $originalInput

            请只输出回应文本（30字以内），例如：
            - "图片已生成，你可以看看。"
            - "这里展示了你描述的场景。"
            - "你看，就是这样。"
        """.trimIndent()

        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val apiKey = AppSettings.getLlmApiKey().orEmpty()
            val baseUrl = AppSettings.getLlmBaseUrl()
            val model = AppSettings.getLlmModel()

            withContext(Dispatchers.IO) {
            llmClient.chatCompletions(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                temperature = 0.8
            )
        }.map { response ->
                response.trim().take(30)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 格式化图片结果失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM 格式化互动鉴定结果
     */
    suspend fun formatInteractionCheckResult(
        action: String,
        success: Boolean,
        detectedActions: List<String>?
    ): Result<String> {
        val currentScene = getCurrentScene()
        val sceneContext = currentScene?.narrativeText ?: currentScene?.description ?: "未知场景"

        val detectedText = detectedActions?.joinToString(", ") ?: "未检测到动作"

        // 根据游戏类型生成不同的提示
        val successPrompt = when (gameType) {
            GameType.JUBENSHA -> """
                玩家进行了"${action}"的鉴定，鉴定成功。
                作为剧本杀 DM，你需要：
                1. 肯定玩家的成功
                2. 给出奖励（如获得线索、揭示关键信息）
                3. 适当推进剧情
                请给出一个简短的 DM 回应（50字以内）。
            """.trimIndent()

            GameType.PAOTUAN -> """
                玩家进行了"${action}"的鉴定，鉴定成功。
                作为跑团 DM，你需要：
                1. 赞赏玩家的动作
                2. 描述成功带来的积极后果
                3. 推进冒险剧情发展
                请给出一个简短的 DM 回应（50字以内）。
            """.trimIndent()

            GameType.HAITANG -> """
                玩家进行了"${action}"的鉴定，鉴定成功。
                作为海龟汤 DM，你需要：
                1. 确认玩家成功
                2. 可能给出一条有用的提示或线索
                请给出一个简短的 DM 回应（40字以内）。
            """.trimIndent()
        }

        val failurePrompt = when (gameType) {
            GameType.JUBENSHA -> """
                玩家进行了"${action}"的鉴定，鉴定失败。
                作为剧本杀 DM，你需要：
                1. 告知玩家失败
                2. 描述失败带来的不利后果（如错过线索、时间损失）
                请给出一个简短的 DM 回应（50字以内）。
            """.trimIndent()

            GameType.PAOTUAN -> """
                玩家进行了"${action}"的鉴定，鉴定失败。
                作为跑团 DM，你需要：
                1. 告知玩家失败
                2. 描述失败带来的负面后果（如受伤、遭遇敌人）
                请给出一个简短的 DM 回应（50字以内）。
            """.trimIndent()

            GameType.HAITANG -> """
                玩家进行了"${action}"的鉴定，鉴定失败。
                作为海龟汤 DM，你需要：
                1. 告知玩家失败
                2. 可能给出一个负面提示或让玩家继续猜测
                请给出一个简短的 DM 回应（40字以内）。
            """.trimIndent()
        }

        val prompt = """
            游戏类型: ${gameType.name}
            当前场景: $sceneContext
            检测到的动作: $detectedText

            ${if (success) successPrompt else failurePrompt}

            注意：只输出 DM 回应，不要包含其他文字。
        """.trimIndent()

        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val apiKey = AppSettings.getLlmApiKey().orEmpty()
            val baseUrl = AppSettings.getLlmBaseUrl()
            val model = AppSettings.getLlmModel()

            withContext(Dispatchers.IO) {
                llmClient.chatCompletions(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    messages = messages,
                    temperature = 0.8
                )
            }.map { response ->
                response.trim().take(60)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 格式化互动鉴定结果失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM 格式化拍照识别结果
     */
    suspend fun formatPhotoRecognitionResult(
        target: String,
        detectedObject: String,
        isMatch: Boolean,
        confidence: Float
    ): Result<String> {
        val currentScene = getCurrentScene()
        val sceneContext = currentScene?.narrativeText ?: currentScene?.description ?: "未知场景"

        // 如果识别失败（未检测到物品），直接返回固定文案
        if (detectedObject.isBlank() || detectedObject == "未检测到物体" || detectedObject == "未知") {
            return Result.success("玩家什么都没拿出来。")
        }

        val confidenceText = String.format("%.0f%%", confidence * 100)

        // 根据游戏类型生成不同的提示（只在识别成功时调用）
        val prompt = when (gameType) {
            GameType.JUBENSHA -> """
                玩家拿出来了一件物品进行拍照识别。
                玩家说目标识别物体是：${target}
                实际检测到的物体是：${detectedObject}
                识别置信度：${confidenceText}

                作为剧本杀 DM，请根据当前场景和这个物品判断：
                1. 如果物品与案件线索相关，可以暗示这个线索的重要性
                2. 如果物品不相关，可以告知玩家这没有用处
                3. 给出简短的 DM 回应（60字以内）

                当前场景：${sceneContext}
            """.trimIndent()

            GameType.PAOTUAN -> """
                玩家拿出来了一件物品进行拍照识别。
                玩家说目标识别物体是：${target}
                实际检测到的物体是：${detectedObject}
                识别置信度：${confidenceText}

                作为跑团 DM，请根据当前场景和这个物品描述：
                1. 描述 NPC 对这个物品的反应
                2. 或者描述这个物品在场景中的作用
                3. 给出简短的 DM 回应（60字以内）

                当前场景：${sceneContext}
            """.trimIndent()

            GameType.HAITANG -> """
                玩家拿出来了一件物品进行拍照识别。
                玩家说目标识别物体是：${target}
                实际检测到的物体是：${detectedObject}
                识别置信度：${confidenceText}

                作为海龟汤 DM，请判断：
                1. 这个物品是否与谜题相关
                2. 给出提示或者确认这个物品的意义
                3. 给出简短的 DM 回应（50字以内）

                当前场景：${sceneContext}
            """.trimIndent()
        }

        return try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val apiKey = AppSettings.getLlmApiKey().orEmpty()
            val baseUrl = AppSettings.getLlmBaseUrl()
            val model = AppSettings.getLlmModel()

            withContext(Dispatchers.IO) {
                llmClient.chatCompletions(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    messages = messages,
                    temperature = 0.8
                )
            }.map { response ->
                response.trim().take(70)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 格式化拍照识别结果失败", e)
            Result.failure(e)
        }
    }

    /**
     * 添加玩家
     */
    fun addPlayer(player: Player) {
        val currentState = _gameState.value
        _gameState.value = currentState.copy(
            players = currentState.players + player
        )
    }

    /**
     * 移除玩家
     */
    fun removePlayer(playerId: String) {
        val currentState = _gameState.value
        _gameState.value = currentState.copy(
            players = currentState.players.filter { it.id != playerId }
        )
    }

    /**
     * 生成游戏复盘报告
     */
    fun generateReview(): GameReview {
        val state = _gameState.value
        val players = state.players

        return GameReview(
            roomId = roomId,
            roomName = "TRPG游戏-$roomId",
            gameType = storyTree.gameType,
            duration = if (state.sceneHistory.isNotEmpty()) {
                state.sceneHistory.firstOrNull()?.visitTime?.let {
                    System.currentTimeMillis() - it
                } ?: 0L
            } else 0L,
            players = players.map { player ->
                PlayerReview(
                    playerId = player.id,
                    playerName = player.name,
                    scenesVisited = state.sceneHistory.distinctBy { it.sceneId }.size,
                    cluesCollected = player.ownedClues.size,
                    diceRolls = state.diceHistory.count { it.playerId == player.id },
                    successRate = calculateSuccessRate(player.id)
                )
            },
            storyPath = state.sceneHistory.map { visit ->
                ScenePathNode(
                    sceneId = visit.sceneId,
                    sceneTitle = visit.sceneTitle,
                    entryTime = visit.visitTime,
                    exitTime = visit.visitTime + visit.stayDuration,
                    decisions = visit.decisions
                )
            },
            keyDecisions = extractKeyDecisions(),
            clueDistribution = state.distributedClues.map { clue ->
                ClueDistribution(
                    clueId = clue.id,
                    clueName = clue.name,
                    recipients = clue.targetPlayers,
                    distributionTime = System.currentTimeMillis()
                )
            },
            diceStatistics = calculateDiceStatistics(),
            endTime = System.currentTimeMillis()
        )
    }

    private fun calculateSuccessRate(playerId: String): Float {
        val state = _gameState.value
        val playerRolls = state.diceHistory.filter { it.playerId == playerId }
        if (playerRolls.isEmpty()) return 0f

        val successCount = playerRolls.count { it.isSuccess }
        return (successCount.toFloat() / playerRolls.size) * 100f
    }

    private fun extractKeyDecisions(): List<KeyDecision> {
        // 从关键转折点提取决策
        return storyTree.nodes.filter { it.value.isCritical }.map { (id, node) ->
            val visit = _gameState.value.sceneHistory.find { it.sceneId == id }
            KeyDecision(
                sceneId = id,
                sceneTitle = node.description,
                decision = "关键决策",
                playerId = "",
                playerName = "",
                impact = "影响剧情走向",
                timestamp = visit?.visitTime ?: System.currentTimeMillis()
            )
        }
    }

    private fun calculateDiceStatistics(): DiceStatistics {
        val rolls = _gameState.value.diceHistory
        return DiceStatistics(
            totalRolls = rolls.size,
            successCount = rolls.count { it.isSuccess },
            failureCount = rolls.count { !it.isSuccess },
            criticalSuccesses = rolls.count { it.isCritical && it.isSuccess },
            criticalFailures = rolls.count { it.isCritical && !it.isSuccess },
            averageResult = if (rolls.isNotEmpty()) rolls.map { it.result }.average().toFloat() else 0f
        )
    }

    /**
     * 清理资源
     */
    fun destroy() {
        gameScope.cancel()
    }
}

// 辅助类型别名
typealias StoryScene = StoryNode

/**
 * 动作结果
 */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val narration: String?,
    val diceResult: DiceRollResult? = null,
    val cluesReceived: List<Clue> = emptyList(),
    val failSafe: FailSafePolicy? = null
)

/**
 * 骰点结果
 */
data class DiceRollResult(
    val value: Int,
    val isSuccess: Boolean,
    val isCritical: Boolean
)

/**
 * 互动鉴定请求
 */
data class InteractionCheckRequest(
    val action: String,
    val difficulty: Int,
    val fullInput: String
)

/**
 * 待处理的互动鉴定
 */
data class PendingInteractionCheck(
    val checkId: String,
    val playerId: String,
    val action: String,
    val difficulty: Int,
    val sceneId: String,
    val timestamp: Long
)

/**
 * 互动鉴定结果
 */
data class InteractionCheckResult(
    val checkId: String,
    val playerId: String,
    val action: String,
    val difficulty: Int,
    val success: Boolean,
    val detectedActions: List<String>,
    val timestamp: Long
)

/**
 * 图片生成请求
 */
data class ImageGenerationRequest(
    val description: String,
    val type: ImageGenerationType,
    val sceneId: String?,
    val fullInput: String
)

/**
 * 图片生成类型
 */
enum class ImageGenerationType {
    SCENE,    // 场景图
    CLUE,      // 线索图
    CUSTOM     // 自定义描述
}

/**
 * 拍照识别请求
 */
data class PhotoRecognitionRequest(
    val target: String,
    val fullInput: String
)

/**
 * 规则裁决器
 */
class RulesArbiter {
    fun rollDice(
        diceType: DiceType,
        difficulty: Int,
        modifiers: List<DiceModifier>
    ): DiceRollResult {
        val maxValue = when (diceType) {
            DiceType.D4 -> 4
            DiceType.D6 -> 6
            DiceType.D8 -> 8
            DiceType.D10 -> 10
            DiceType.D12 -> 12
            DiceType.D20 -> 20
            DiceType.D100 -> 100
        }

        val roll = (1..maxValue).random()
        val modifierValue = modifiers.sumOf { it.value }
        val finalResult = roll + modifierValue

        val isSuccess = finalResult >= difficulty
        val isCritical = roll == maxValue || roll == 1

        return DiceRollResult(
            value = finalResult,
            isSuccess = isSuccess,
            isCritical = isCritical
        )
    }
}

/**
 * 线索管理器
 */
class ClueMaster(private val storyTree: StoryTree) {
    /**
     * 已发放线索去重（避免每次都 copy(isDistributed=true) 但不落地，导致重复发放）
     */
    private val distributedIds = mutableSetOf<String>()

    /**
     * 发放线索：从【当前场景线索池 + 全局线索】中筛选，按玩家可见性和触发条件做轻量匹配。
     */
    fun distributeClues(
        scene: StoryNode,
        playerId: String,
        triggerCondition: String
    ): List<Clue> {
        val all = (scene.cluePool.publicClues + scene.cluePool.privateClues + storyTree.globalClues)

        val candidates = all.filter { clue ->
            clue.id !in distributedIds &&
            !clue.isDistributed &&
            (clue.targetPlayers.isEmpty() || playerId in clue.targetPlayers)
        }

        if (candidates.isEmpty()) return emptyList()

        // 从触发条件里提取关键词（例如 SEARCH_桌子 / INVESTIGATE_成功 等）
        val keyword = triggerCondition
            .substringAfter('_', "")
            .trim()
            .takeIf { it.isNotBlank() }

        val matched = if (keyword != null) {
            val k = keyword.lowercase()
            candidates.filter { c ->
                c.name.lowercase().contains(k) || c.description.lowercase().contains(k)
            }
        } else emptyList()

        // 优先发放匹配关键词的线索，否则按重要性优先级发放
        val pool = (matched.ifEmpty { candidates })
            .sortedWith(compareByDescending<Clue> { it.importance }.thenBy { it.id })

        // 每次动作最多发放 2 条线索（避免刷屏）
        val picked = pool.take(2).map { it.copy(isDistributed = true) }
        picked.forEach { distributedIds.add(it.id) }
        return picked
    }

    fun markDistributed(clues: List<Clue>) {
        clues.forEach { distributedIds.add(it.id) }
    }
}

/**
 * 场景导演
 */
class SceneDirector(private val storyTree: StoryTree) {
    /**
     * 评估转场：按照 edge.priority（数字越小越优先）+ isFailSafe（纠偏路径最后）选择匹配的边。
     */
    fun evaluateTransition(
        currentSceneId: String,
        playerInput: String? = null,
        diceResult: DiceRollResult? = null,
        conditionType: ConditionType,
        playerClueIds: Set<String> = emptySet(),
        decision: String? = null,
        elapsedMs: Long? = null
    ): String? {
        val edges = storyTree.edges.values
            .filter { it.fromNodeId == currentSceneId }
            .sortedWith(compareBy<TransitionEdge> { it.isFailSafe }.thenBy { it.priority })

        val matched = edges.firstOrNull { edge ->
            val cond = edge.condition
            if (cond.type != conditionType) return@firstOrNull false

            when (cond.type) {
                ConditionType.AUTOMATIC -> true

                ConditionType.NARRATIVE_INPUT -> {
                    val trigger = cond.narrativeTrigger
                    !trigger.isNullOrBlank() && (playerInput?.contains(trigger) == true)
                }

                ConditionType.DICE_SUCCESS -> {
                    val r = diceResult ?: return@firstOrNull false
                    val req = cond.requiredDiceResult
                    if (req != null) {
                        r.value in req.minResult..req.maxResult
                    } else {
                        r.isSuccess
                    }
                }

                ConditionType.CLUE_FOUND -> {
                    // requiredClues 为空：只要玩家已有任意线索即可触发；否则要求全部满足
                    if (cond.requiredClues.isEmpty()) {
                        playerClueIds.isNotEmpty()
                    } else {
                        cond.requiredClues.all { it in playerClueIds }
                    }
                }

                ConditionType.DECISION_MADE -> {
                    val req = cond.requiredDecision
                    !req.isNullOrBlank() && decision == req
                }

                ConditionType.TIME_ELAPSED -> {
                    // 若未在 condition 中提供明确时间，这里仅做最小实现：
                    // - elapsedMs 非空且 narrativeTrigger 形如 "5000"（毫秒）时，比较是否达到。
                    val e = elapsedMs ?: return@firstOrNull false
                    val ms = cond.narrativeTrigger?.trim()?.toLongOrNull() ?: return@firstOrNull false
                    e >= ms
                }
            }
        }

        return matched?.toNodeId
    }

    fun findTransitionByDescription(
        currentSceneId: String,
        description: String
    ): String? {
        val edges = storyTree.edges.values.filter { it.fromNodeId == currentSceneId }
        return edges.firstOrNull { edge ->
            storyTree.nodes[edge.toNodeId]?.description?.contains(description) == true
        }?.toNodeId
    }
}
