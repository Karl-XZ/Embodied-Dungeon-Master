package com.xmov.metahuman.app.trpg

import com.xmov.metahuman.app.AppSettings
import com.xmov.metahuman.app.llm.ApimartClient
import com.xmov.metahuman.app.llm.LlmTextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * AI 剧本生成器 - 根据主题和游戏类型生成剧本
 */
class AIScriptGenerator {

    /**
     * 直接使用内置剧本（本地生成）
     * @param theme 剧本主题
     * @param gameType 游戏类型
     * @param sceneCount 场景数量
     * @return 生成的 StoryTree
     */
    suspend fun generateBuiltinScript(
        theme: String,
        gameType: GameType,
        sceneCount: Int = 5
    ): Result<StoryTree> = withContext(Dispatchers.IO) {
        android.util.Log.d("AIScriptGenerator", "使用内置剧本生成: gameType=$gameType, theme=$theme, sceneCount=$sceneCount")
        try {
            val storyTree = when (gameType) {
                GameType.JUBENSHA -> generateJubenshaScript(theme, sceneCount)
                GameType.PAOTUAN -> generatePaotuanScript(theme, sceneCount)
                GameType.HAITANG -> generateHaitangScript(theme, sceneCount)
            }
            android.util.Log.d("AIScriptGenerator", "✅ 内置剧本生成成功: ${storyTree.title}")
            Result.success(storyTree)
        } catch (e: Exception) {
            android.util.Log.e("AIScriptGenerator", "❌ 内置剧本生成失败", e)
            Result.failure(e)
        }
    }

    /**
     * 生成剧本
     * @param theme 剧本主题（例如："古堡谜案"、"时空穿越"）
     * @param gameType 游戏类型
     * @param sceneCount 场景数量
     * @return 生成的 StoryTree
     */
    suspend fun generateScript(
        theme: String,
        gameType: GameType,
        sceneCount: Int = 5,
        useFallback: Boolean = false  // 新增参数：是否允许使用内置剧本作为兜底
    ): Result<StoryTree> = withContext(Dispatchers.IO) {
        android.util.Log.d("AIScriptGenerator", "开始生成剧本: gameType=$gameType, theme=$theme, sceneCount=$sceneCount, useFallback=$useFallback")

        // 真实接入：优先走 APIMart/OpenAI-Compatible LLM 生成结构化 JSON，再解析为 StoryTree。
        if (!AppSettings.hasLlmConfig()) {
            android.util.Log.w("AIScriptGenerator", "未配置大模型 API Key")
            return@withContext Result.failure(IllegalStateException("未配置大模型 API Key，请先在设置中填写 APIMart API Key / BaseURL / Model"))
        }

        android.util.Log.d("AIScriptGenerator", "已配置LLM，尝试使用大模型生成...")
        val llmResult = generateByLlm(theme, gameType, sceneCount)

        if (llmResult.isSuccess) {
            android.util.Log.d("AIScriptGenerator", "✅ 大模型生成成功: ${llmResult.getOrNull()?.title}")
            return@withContext llmResult
        }

        // 大模型生成失败，只有当 useFallback=true 时才使用本地生成
        if (!useFallback) {
            val error = llmResult.exceptionOrNull()
            android.util.Log.e("AIScriptGenerator", "❌ 大模型生成失败，且不允许使用内置剧本: ${error?.message}")
            return@withContext Result.failure(error ?: Exception("AI生成失败"))
        }

        android.util.Log.w("AIScriptGenerator", "❌ 大模型生成失败，降级使用本地剧本生成...")

        // 兜底（保留原本的本地随机生成逻辑，便于离线演示/调试）
        try {
            val storyTree = when (gameType) {
                GameType.JUBENSHA -> generateJubenshaScript(theme, sceneCount)
                GameType.PAOTUAN -> generatePaotuanScript(theme, sceneCount)
                GameType.HAITANG -> generateHaitangScript(theme, sceneCount)
            }
            android.util.Log.d("AIScriptGenerator", "✅ 本地生成成功: ${storyTree.title}")
            Result.success(storyTree)
        } catch (e: Exception) {
            android.util.Log.e("AIScriptGenerator", "❌ 本地生成也失败", e)
            Result.failure(e)
        }
    }

    private suspend fun generateByLlm(theme: String, gameType: GameType, sceneCount: Int): Result<StoryTree> {
        return try {
            val apiKey = AppSettings.getLlmApiKey().orEmpty()
            val baseUrl = AppSettings.getLlmBaseUrl()
            val model = AppSettings.getLlmModel()
            val temperature = AppSettings.getLlmTemperature().toDouble()

            android.util.Log.d("AIScriptGenerator", "LLM配置: baseUrl=$baseUrl, model=$model, temperature=$temperature")

            val systemPrompt = buildSystemPrompt(gameType)
            val userPrompt = buildUserPrompt(theme, gameType, sceneCount)

            android.util.Log.d("AIScriptGenerator", "System Prompt: ${systemPrompt.take(100)}...")
            android.util.Log.d("AIScriptGenerator", "User Prompt: ${userPrompt.take(100)}...")

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            android.util.Log.d("AIScriptGenerator", "发送LLM请求...")
            val content = ApimartClient().chatCompletions(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = 2500
            ).getOrElse { return Result.failure(it) }

            android.util.Log.d("AIScriptGenerator", "LLM原始响应: ${content.take(500)}...")

            val jsonText = LlmTextUtils.extractJsonObject(content)
                ?: return Result.failure(IllegalArgumentException("LLM 未输出可解析的 JSON：$content"))

            android.util.Log.d("AIScriptGenerator", "提取的JSON: ${jsonText.take(300)}...")

            // 复用 ScriptParser 的 JSON 解析
            val parser = ScriptParser()
            val parseResult = parser.parseJsonContent(jsonText, gameType)
            
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("解析失败"))
            }
            
            val storyTree = parseResult.getOrThrow()
            android.util.Log.d("AIScriptGenerator", "LLM剧本解析成功: nodes=${storyTree.nodes.size}, edges=${storyTree.edges.size}")

            Result.success(storyTree)
        } catch (e: Exception) {
            android.util.Log.e("AIScriptGenerator", "LLM生成异常", e)
            Result.failure(e)
        }
    }

    private fun buildSystemPrompt(gameType: GameType): String {
        val mode = when (gameType) {
            GameType.JUBENSHA -> "剧本杀（悬疑推理）"
            GameType.PAOTUAN -> "跑团（奇幻冒险）"
            GameType.HAITANG -> "海龟汤（水平思考谜题）"
        }

        val modeInstructions = when (gameType) {
            GameType.JUBENSHA -> """
剧本杀模式特别要求：
- 需要多个角色（4-6人），其中一个是凶手
- 需要明确的案件现场、时间线、作案手法
- 玩家通过收集线索、询问角色来推理出凶手
- 最终场景需要让玩家指控凶手（DECISION_MADE）
- allowedActions：SEARCH,TALK,INVESTIGATE,DICE_ROLL
"""
            GameType.PAOTUAN -> """
跑团模式特别要求：
- 奇幻冒险背景，玩家是冒险者
- 需要战斗、探索、解谜等元素
- 使用 D20 骰子系统（D20_SYSTEM）
- allowedActions：SEARCH,TALK,INVESTIGATE,DICE_ROLL,MOVE,USE_ITEM,ATTACK
- 可以有分支剧情选择
"""
            GameType.HAITANG -> """
海龟汤模式特别要求：
- 这是一个水平思考谜题游戏，不是剧本杀
- 只需要 1 个主场景（scene_main）和 1 个提示场景（scene_hint）
- scene_main 的 narrativeText 是谜题描述（汤面），简短清晰（30-80字）
- 玩家通过提问（是非题）来找出真相（汤底）
- 通过输入"提示"触发 scene_hint 获取关键提示
- 通过输入正确答案或"继续"触发 success/fail 结局
- 绝对不要 diceRules（海龟汤不使用骰点）
- allowedActions：只有 TALK（用于提问）
- 不需要 globalClues（线索就是玩家的问题和答案）
- 谜题示例："一个人从高楼跳下来，但毫发无损，为什么？"（答案：他只是从一楼跳下来）
"""
        }

        return """
你是一个专业 TRPG 剧本设计师，现在要为 Android 客户端生成【可机器解析】的剧情树 JSON。

游戏模式：$mode。

严格要求：
1) 只输出一个 JSON 对象，不要输出解释、不要用 Markdown 代码块。
2) JSON 必须包含字段：id,title,rootNodeId,nodes,edges,globalClues,failSafePolicies。
3) nodes 是数组，每个节点必须包含：id,description,narrativeText,objectives,allowedActions,cluePool,diceRules,imageUrl,isCritical。
4) edges 是数组，每条边包含：id,fromNodeId,toNodeId,condition,priority,isFailSafe。
5) condition.type 只能使用：AUTOMATIC / NARRATIVE_INPUT / DICE_SUCCESS / CLUE_FOUND / DECISION_MADE。
6) allowedActions 可用值：SEARCH,TALK,INVESTIGATE,DICE_ROLL,MOVE,USE_ITEM,ATTACK,CUSTOM（按模式合理选择）。
7) 所有 id 字段用英文/数字/下划线，建议 scene_1, scene_2..., edge_1_2...；rootNodeId 必须指向存在的节点。

$modeInstructions
""".trim()
    }

    private fun buildUserPrompt(theme: String, gameType: GameType, sceneCount: Int): String {
        val actions = when (gameType) {
            GameType.JUBENSHA -> "SEARCH,TALK,INVESTIGATE,DICE_ROLL"
            GameType.PAOTUAN -> "SEARCH,TALK,INVESTIGATE,DICE_ROLL,MOVE,USE_ITEM,ATTACK"
            GameType.HAITANG -> "TALK"
        }

        val specialInstructions = when (gameType) {
            GameType.HAITANG -> """
海龟汤特殊要求：
- 只需要 4 个场景：scene_main（主谜题）、scene_hint（提示）、scene_success（猜对）、scene_fail（猜错）
- scene_main 的 narrativeText 只需是谜题描述（汤面），30-80字，简短清晰
- 例如：场景描述为"谜题"，narrativeText为"一个人从高楼跳下来，但毫发无损，为什么？"
- 提示通过 NARRATIVE_INPUT "提示" 触发，回到主场景通过 "继续" 触发
- 不需要 diceRules，海龟汤不使用骰点
- 不需要 globalClues
- 不需要多场景，核心是谜题本身
- 允许的动作只有 TALK（用于提问）
"""
            GameType.JUBENSHA -> """
剧本杀特殊要求：
- 需要 $sceneCount 个场景：开场介绍、案发现场调查、询问角色、线索分析、最终推理
- 每个场景 narrativeText 80~200 字
- 需要 4-6 个角色，明确谁是凶手
- 全局线索 3~6 条，指向凶手
- 最终场景需要 DECISION_MADE 触发成功/失败结局
"""
            GameType.PAOTUAN -> """
跑团特殊要求：
- 需要 $sceneCount 个场景：冒险开始、遭遇事件、战斗/解谜、最终决战
- 每个场景 narrativeText 80~200 字
- 可以有分支剧情选择（DECISION_MADE）
- 使用 D20 骰子系统
- 全局线索 3~6 条，帮助玩家探索
"""
        }

        val sceneStructure = when (gameType) {
            GameType.HAITANG -> " rootNodeId 必须是 scene_main"
            else -> " 以 scene_1 为 rootNodeId，主线连接到 scene_$sceneCount"
        }

        return """
主题：$theme
场景结构：$sceneStructure

$specialInstructions

请生成一个剧情树：
- 结构上：$sceneStructure
- 内容上：每个场景 narrativeText 描述清晰、可由 DM 朗读。
- 互动上：每个场景 allowedActions 从 [$actions] 中合理选择。
- 线索上：${if (gameType != GameType.HAITANG) "globalClues 给 3~6 条线索。" else "不需要 globalClues。"}
- 转场上：主线优先使用 NARRATIVE_INPUT，narrativeTrigger 建议统一用"继续"；关键处可用 DICE_SUCCESS 或 CLUE_FOUND 或 DECISION_MADE。

再次强调：只输出 JSON。
""".trim()
    }

    /**
     * 生成剧本杀剧本
     */
    private fun generateJubenshaScript(theme: String, sceneCount: Int): StoryTree {
        val storyId = "story_jubensha_" + System.currentTimeMillis()
        val nodes = mutableMapOf<String, StoryNode>()
        val edges = mutableMapOf<String, TransitionEdge>()
        val globalClues = mutableListOf<Clue>()

        // 生成角色
        val characters = generateCharacters(theme, 4..6)
        val murderer = characters.random()

        // 生成场景
        var previousSceneId = ""
        for (i in 1..sceneCount) {
            val sceneId = "scene_$i"
            val isLastScene = i == sceneCount
            val isCritical = i == sceneCount - 1

            val scene = generateJubenshaScene(
                sceneId = sceneId,
                theme = theme,
                sceneIndex = i,
                isLastScene = isLastScene,
                isCritical = isCritical,
                characters = characters,
                murderer = murderer
            )

            nodes[sceneId] = scene

            // 创建转场边
            if (i > 1) {
                val edgeId = "edge_" + (i-1) + "_$i"
                edges[edgeId] = TransitionEdge(
                    id = edgeId,
                    fromNodeId = "scene_" + (i-1),
                    toNodeId = sceneId,
                    condition = TransitionCondition(
                        type = if (isCritical) ConditionType.DICE_SUCCESS else ConditionType.AUTOMATIC
                    ),
                    priority = 1
                )
            }

            // 最后一场景的特殊转场
            if (isLastScene) {
                // 成功结局
                edges["edge_success"] = TransitionEdge(
                    id = "edge_success",
                    fromNodeId = sceneId,
                    toNodeId = "scene_end_success",
                    condition = TransitionCondition(
                        type = ConditionType.DECISION_MADE,
                        requiredDecision = "accuse_" + murderer
                    ),
                    priority = 1
                )

                // 失败结局
                edges["edge_fail"] = TransitionEdge(
                    id = "edge_fail",
                    fromNodeId = sceneId,
                    toNodeId = "scene_end_fail",
                    condition = TransitionCondition(
                        type = ConditionType.DECISION_MADE,
                        requiredDecision = "accuse_wrong"
                    ),
                    priority = 2,
                    isFailSafe = true
                )
            }

            previousSceneId = sceneId
        }

        // 添加结局场景
        nodes["scene_end_success"] = StoryNode(
            id = "scene_end_success",
            description = "真相大白",
            narrativeText = "恭喜！你成功找出了真凶！\n凶手就是：" + murderer + "\n真相是：" + generateTruth(theme, murderer),
            objectives = emptyList(),
            allowedActions = emptyList(),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = true
        )

        nodes["scene_end_fail"] = StoryNode(
            id = "scene_end_fail",
            description = "错误指控",
            narrativeText = "很遗憾，你指控了错误的人。真正的凶手逍遥法外...\n真相是：" + generateTruth(theme, murderer),
            objectives = emptyList(),
            allowedActions = emptyList(),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = true
        )

        // 生成全局线索
        globalClues.addAll(generateJubenshaClues(theme, murderer, characters))

        // 添加线索到场景
        distributeCluesToScenes(nodes, globalClues, sceneCount)

        return StoryTree(
            id = storyId,
            title = "剧本杀：" + theme,
            gameType = GameType.JUBENSHA,
            rootNodeId = "scene_1",
            nodes = nodes,
            edges = edges,
            globalClues = globalClues,
            failSafePolicies = generateFailSafePolicies(theme)
        )
    }

    /**
     * 生成跑团剧本
     */
    private fun generatePaotuanScript(theme: String, sceneCount: Int): StoryTree {
        val storyId = "story_paotuan_" + System.currentTimeMillis()
        val nodes = mutableMapOf<String, StoryNode>()
        val edges = mutableMapOf<String, TransitionEdge>()
        val globalClues = mutableListOf<Clue>()

        // 生成场景
        for (i in 1..sceneCount) {
            val sceneId = "scene_$i"
            val isLastScene = i == sceneCount
            val isCritical = i % 2 == 0

            val scene = generatePaotuanScene(
                sceneId = sceneId,
                theme = theme,
                sceneIndex = i,
                isLastScene = isLastScene,
                isCritical = isCritical
            )

            nodes[sceneId] = scene

            // 创建转场边
            if (i > 1) {
                val edgeId = "edge_" + (i-1) + "_$i"
                edges[edgeId] = TransitionEdge(
                    id = edgeId,
                    fromNodeId = "scene_" + (i-1),
                    toNodeId = sceneId,
                    condition = TransitionCondition(
                        type = ConditionType.NARRATIVE_INPUT,
                        narrativeTrigger = "继续"
                    ),
                    priority = 1
                )
            }

            // 多分支选择
            if (!isLastScene) {
                edges["edge_" + i + "_branch_a"] = TransitionEdge(
                    id = "edge_" + i + "_branch_a",
                    fromNodeId = sceneId,
                    toNodeId = "scene_" + i + "_a",
                    condition = TransitionCondition(
                        type = ConditionType.DECISION_MADE,
                        requiredDecision = "选择A"
                    ),
                    priority = 1
                )
                edges["edge_" + i + "_branch_b"] = TransitionEdge(
                    id = "edge_" + i + "_branch_b",
                    fromNodeId = sceneId,
                    toNodeId = "scene_" + i + "_b",
                    condition = TransitionCondition(
                        type = ConditionType.DECISION_MADE,
                        requiredDecision = "选择B"
                    ),
                    priority = 2
                )
            }
        }

        // 生成全局线索
        globalClues.addAll(generatePaotuanClues(theme, sceneCount))

        // 添加线索到场景
        distributeCluesToScenes(nodes, globalClues, sceneCount)

        return StoryTree(
            id = storyId,
            title = "跑团：" + theme,
            gameType = GameType.PAOTUAN,
            rootNodeId = "scene_1",
            nodes = nodes,
            edges = edges,
            globalClues = globalClues,
            failSafePolicies = generateFailSafePolicies(theme)
        )
    }

    /**
     * 生成海龟汤剧本
     */
    private fun generateHaitangScript(theme: String, sceneCount: Int): StoryTree {
        val storyId = "story_haitang_" + System.currentTimeMillis()
        val nodes = mutableMapOf<String, StoryNode>()
        val edges = mutableMapOf<String, TransitionEdge>()
        val globalClues = mutableListOf<Clue>()

        // 生成谜题
        val (question, answer) = generateHaitangPuzzle(theme)

        // 主场景
        nodes["scene_main"] = StoryNode(
            id = "scene_main",
            description = "谜题",
            narrativeText = "谜题：" + question + "\n\n请通过提问来找出真相！你可以问任意是非题，我会回答\"是\"、\"否\"或\"无关\"。\n\n输入\"提示\"可以获得一个重要提示。",
            objectives = listOf(
                Objective(id = "solve_puzzle", description = "猜出谜底", isOptional = false, completionConditions = listOf("correct_answer"))
            ),
            allowedActions = listOf(ActionType.TALK),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,  // 海龟汤不需要骰点
            imageUrl = null,
            isCritical = false
        )

        // 提示场景（通过输入"提示"获取）
        nodes["scene_hint"] = StoryNode(
            id = "scene_hint",
            description = "获得提示",
            narrativeText = "你获得了一个重要提示：" + generateHaitangHint(answer) + "\n\n你可以继续提问，或者直接猜测答案。",
            objectives = emptyList(),
            allowedActions = listOf(ActionType.TALK),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = false
        )

        // 成功结局
        nodes["scene_success"] = StoryNode(
            id = "scene_success",
            description = "猜对了！",
            narrativeText = "恭喜你猜对了！\n\n答案是：" + answer,
            objectives = emptyList(),
            allowedActions = emptyList(),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = true
        )

        // 失败结局
        nodes["scene_fail"] = StoryNode(
            id = "scene_fail",
            description = "猜错了...",
            narrativeText = "很遗憾，你猜错了。\n\n正确答案是：" + answer,
            objectives = emptyList(),
            allowedActions = emptyList(),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = true
        )

        // 创建转场边
        edges["edge_hint"] = TransitionEdge(
            id = "edge_hint",
            fromNodeId = "scene_main",
            toNodeId = "scene_hint",
            condition = TransitionCondition(
                type = ConditionType.NARRATIVE_INPUT,
                narrativeTrigger = "提示"
            ),
            priority = 1
        )

        edges["edge_back_to_main"] = TransitionEdge(
            id = "edge_back_to_main",
            fromNodeId = "scene_hint",
            toNodeId = "scene_main",
            condition = TransitionCondition(
                type = ConditionType.NARRATIVE_INPUT,
                narrativeTrigger = "继续"
            ),
            priority = 1
        )

        edges["edge_success"] = TransitionEdge(
            id = "edge_success",
            fromNodeId = "scene_main",
            toNodeId = "scene_success",
            condition = TransitionCondition(
                type = ConditionType.DECISION_MADE,
                requiredDecision = "correct_answer"
            ),
            priority = 1
        )

        edges["edge_fail"] = TransitionEdge(
            id = "edge_fail",
            fromNodeId = "scene_main",
            toNodeId = "scene_fail",
            condition = TransitionCondition(
                type = ConditionType.DECISION_MADE,
                requiredDecision = "wrong_answer"
            ),
            priority = 2,
            isFailSafe = true
        )

        return StoryTree(
            id = storyId,
            title = "海龟汤：" + theme,
            gameType = GameType.HAITANG,
            rootNodeId = "scene_main",
            nodes = nodes,
            edges = edges,
            globalClues = globalClues,
            failSafePolicies = emptyList()  // 海龟汤不需要失败回退策略
        )
    }

    // ==================== 辅助生成函数 ====================

    private fun generateCharacters(theme: String, count: IntRange): List<String> {
        val characterNames = listOf(
            "张三", "李四", "王五", "赵六", "孙七", "周八",
            "管家老王", "女佣小红", "保安小李", "厨师老刘"
        )
        return characterNames.shuffled().take(Random.nextInt(count.first, count.last + 1))
    }

    private fun generateJubenshaScene(
        sceneId: String,
        theme: String,
        sceneIndex: Int,
        isLastScene: Boolean,
        isCritical: Boolean,
        characters: List<String>,
        murderer: String
    ): StoryNode {
        // 根据用户输入的theme生成更个性化的场景描述
        val descriptions = when (sceneIndex) {
            1 -> "欢迎来到【" + theme + "】。今天晚上，所有相关人员齐聚一堂，气氛看似融洽，但暗流涌动。"
            2 -> "第二天早晨，一个惊人的消息传开：" + characters.random() + "被发现死在了现场！这和【" + theme + "】事件有关吗？"
            3 -> "警察已经到达现场开始调查，每个人都声称自己有不在场证明。但这【" + theme + "】背后的真相究竟是什么？"
            4 -> "通过调查，发现每个人的证词都有漏洞。关于【" + theme + "】的更多线索浮出水面，但谁在说谎？"
            5 -> "最后的推理时刻到了！所有线索指向一个真相，这起【" + theme + "】案件的关键人物是谁？"
            else -> "调查继续进行中，关于【" + theme + "】的真相越来越近..."
        }

        return StoryNode(
            id = sceneId,
            description = "场景 " + sceneIndex + " - " + theme,
            narrativeText = descriptions,
            objectives = if (isLastScene) {
                listOf(
                    Objective(
                        id = "find_murderer",
                        description = "找出【" + theme + "】的真凶",
                        isOptional = false,
                        completionConditions = listOf("accuse_" + murderer)
                    )
                )
            } else {
                listOf(
                    Objective(
                        id = "investigate_" + sceneIndex,
                        description = "调查【" + theme + "】现场",
                        isOptional = false,
                        completionConditions = listOf("find_clue")
                    )
                )
            },
            allowedActions = listOf(ActionType.SEARCH, ActionType.TALK, ActionType.INVESTIGATE, ActionType.DICE_ROLL),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = if (isCritical) {
                DiceRule(
                    ruleType = DiceRuleType.D20_SYSTEM,
                    diceType = DiceType.D20,
                    difficulty = 12,
                    successThreshold = 13
                )
            } else {
                null
            },
            imageUrl = null,
            isCritical = isCritical
        )
    }

    private fun generatePaotuanScene(
        sceneId: String,
        theme: String,
        sceneIndex: Int,
        isLastScene: Boolean,
        isCritical: Boolean
    ): StoryNode {
        val narratives = listOf(
            "你来到了" + theme + "的起点，前方有两条路...",
            "路上遇到了一群怪物，战斗一触即发！",
            "发现了一个神秘的山洞，里面散发着奇异的光芒。",
            "村民们请求你帮助他们解决一个问题。",
            "最终，你到达了目的地..."
        )

        val narrativeText = narratives.getOrNull(sceneIndex - 1) ?: "继续你的冒险..."

        return StoryNode(
            id = sceneId,
            description = "场景 " + sceneIndex,
            narrativeText = narrativeText,
            objectives = if (isLastScene) {
                listOf(Objective(id = "complete_quest", description = "完成任务", isOptional = false, completionConditions = emptyList()))
            } else {
                emptyList()
            },
            allowedActions = listOf(ActionType.TALK, ActionType.INVESTIGATE, ActionType.ATTACK, ActionType.MOVE),
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = DiceRule(
                ruleType = DiceRuleType.D20_SYSTEM,
                diceType = DiceType.D20,
                difficulty = 10,
                successThreshold = 11
            ),
            imageUrl = null,
            isCritical = isCritical
        )
    }

    private fun generateHaitangPuzzle(theme: String): Pair<String, String> {
        val puzzles = listOf(
            "一个人从高楼跳下来，但毫发无损，为什么？" to "他只是从一楼跳下来",
            "一个人走进酒吧，要一杯水，服务员拿枪指着他，他说谢谢，为什么？" to "他在打嗝",
            "两个人在沙滩上，一个死了，一个活着，为什么？" to "他们是两座雕像",
            "一个男子在沙漠中行走，发现一具尸体，他捡起地上的骨头就跑，为什么？" to "他以为自己也是沙雕，想逃跑"
        )
        return puzzles.random()
    }

    private fun generateHaitangHint(answer: String): String {
        return "答案是关于" + answer.take(2) + "的..."
    }

    private fun generateJubenshaClues(theme: String, murderer: String, characters: List<String>): List<Clue> {
        return listOf(
            Clue("clue_1", "血迹", "现场发现了血迹，血型与" + murderer + "相符", ClueImportance.CRITICAL),
            Clue("clue_2", "指纹", "凶器上的指纹属于" + murderer, ClueImportance.IMPORTANT),
            Clue("clue_3", "时间", "案发时" + murderer + "不在场证明可疑", ClueImportance.IMPORTANT),
            Clue("clue_4", "动机", murderer + " 有作案动机", ClueImportance.NORMAL),
            Clue("clue_5", "目击", "有人看到" + murderer + " 在案发现场附近", ClueImportance.NORMAL)
        )
    }

    private fun generatePaotuanClues(theme: String, sceneCount: Int): List<Clue> {
        return (1..sceneCount).map { i ->
            Clue(
                id = "clue_$i",
                name = "线索 $i",
                description = "关于" + theme + "的第" + i + "条线索...",
                importance = if (i <= 2) ClueImportance.IMPORTANT else ClueImportance.NORMAL
            )
        }
    }

    private fun distributeCluesToScenes(
        nodes: MutableMap<String, StoryNode>,
        clues: List<Clue>,
        sceneCount: Int
    ) {
        val scenesWithoutEnd = nodes.values.filter { !it.description.contains("结局") && !it.description.contains("大") }
        clues.forEachIndexed { index, clue ->
            val targetSceneIndex = (index % scenesWithoutEnd.size)
            val scene = scenesWithoutEnd[targetSceneIndex]
            scene?.let {
                val updatedClues = it.cluePool.publicClues + clue
                nodes[scene.id] = it.copy(cluePool = CluePool(updatedClues, it.cluePool.privateClues))
            }
        }
    }

    private fun generateTruth(theme: String, murderer: String): String {
        return murderer + " 因为长期受到不公正待遇，在一时冲动下犯下了罪行。"
    }

    private fun generateFailSafePolicies(theme: String): List<FailSafePolicy> {
        return listOf(
            FailSafePolicy(
                id = "fail_safe_1",
                triggerCondition = "玩家严重偏离剧情",
                targetNodeId = "scene_1",
                narrativeGuide = "请回到主线剧情，继续调查...",
                autoExecute = false
            )
        )
    }
}
