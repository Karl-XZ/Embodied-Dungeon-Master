package com.xmov.metahuman.app.agent

import android.util.Log
import com.xmov.metahuman.app.AppSettings
import com.xmov.metahuman.app.trpg.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Agent 服务端集成
 * 提供剧本智能解析、剧情树自动生成等高级功能
 */
class AgentService {

    private val TAG = "AgentService"
    private val client = AgentServiceClient()

    /**
     * 初始化
     */
    fun init() {
        client.configure(
            baseUrl = AppSettings.getLlmBaseUrl(),
            apiKey = AppSettings.getLlmApiKey().orEmpty()
        )
    }

    // ========== 剧本解析 ==========

    /**
     * 智能解析剧本文本
     */
    suspend fun parseScriptText(
        scriptText: String,
        gameType: GameType
    ): Result<StoryTree> = withContext(Dispatchers.IO) {
        val gameTypeStr = when (gameType) {
            GameType.JUBENSHA -> "JUBENSHA"
            GameType.PAOTUAN -> "PAOTUAN"
            GameType.HAITANG -> "HAITANG"
        }

        client.parseScriptFromText(scriptText, gameTypeStr)
            .fold(
                onSuccess = { parsed ->
                    val storyTree = convertToStoryTree(parsed, gameType)
                    Result.success(storyTree)
                },
                onFailure = { Result.failure(it) }
            )
    }

    // ========== 剧情树生成 ==========

    /**
     * 自动生成剧情树
     */
    suspend fun generateStoryTree(
        theme: String,
        gameType: GameType,
        sceneCount: Int = 5,
        complexity: Complexity = Complexity.MEDIUM
    ): Result<StoryTree> = withContext(Dispatchers.IO) {
        val gameTypeStr = when (gameType) {
            GameType.JUBENSHA -> "JUBENSHA"
            GameType.PAOTUAN -> "PAOTUAN"
            GameType.HAITANG -> "HAITANG"
        }

        client.generateStoryTree(theme, gameTypeStr, sceneCount, complexity)
            .fold(
                onSuccess = { generated ->
                    val storyTree = convertGeneratedToStoryTree(generated, gameType)
                    Result.success(storyTree)
                },
                onFailure = { Result.failure(it) }
            )
    }

    // ========== Fail-Safe 生成 ==========

    /**
     * 为现有剧情树生成 Fail-Safe 策略
     */
    suspend fun generateFailSafePolicies(storyTree: StoryTree): Result<StoryTree> {
        return withContext(Dispatchers.IO) {
            val treeJson = storyTreeToJson(storyTree)

            client.generateFailSafePolicies(treeJson)
                .fold(
                    onSuccess = { policies ->
                        val updated = storyTree.copy(
                            failSafePolicies = policies.map { it.toDomain() }
                        )
                        Result.success(updated)
                    },
                    onFailure = { Result.failure(it) }
                )
        }
    }

    // ========== 转换函数 ==========

    private fun convertToStoryTree(
        parsed: ParsedScript,
        gameType: GameType
    ): StoryTree {
        val treeId = "tree_${System.currentTimeMillis()}"

        val nodes = parsed.scenes.associate { scene ->
            scene.id to StoryNode(
                id = scene.id,
                description = scene.title,
                narrativeText = scene.narrativeText,
                objectives = emptyList(),
                allowedActions = scene.allowedActions.mapNotNull {
                    try { ActionType.valueOf(it.uppercase()) } catch (e: Exception) { null }
                },
                cluePool = CluePool(emptyList(), emptyList()),
                diceRules = null,
                imageUrl = null,
                isCritical = scene.isCritical
            )
        }

        val edges = parsed.transitions.mapIndexed { index, transition ->
            "edge_${index + 1}" to TransitionEdge(
                id = "edge_${index + 1}",
                fromNodeId = transition.from,
                toNodeId = transition.to,
                condition = TransitionCondition(
                    type = try {
                        ConditionType.valueOf(transition.condition.uppercase())
                    } catch (e: Exception) {
                        ConditionType.AUTOMATIC
                    },
                    narrativeTrigger = transition.trigger
                ),
                priority = 1
            )
        }.toMap()

        val globalClues = parsed.globalClues.map { clue ->
            Clue(
                id = clue.id,
                name = clue.name,
                description = clue.description,
                importance = try {
                    ClueImportance.valueOf(clue.importance.uppercase())
                } catch (e: Exception) {
                    ClueImportance.NORMAL
                }
            )
        }

        val failSafePolicies = parsed.failSafePolicies.map { it.toDomain() }

        return StoryTree(
            id = treeId,
            title = parsed.title,
            gameType = gameType,
            rootNodeId = parsed.scenes.firstOrNull()?.id ?: "scene_1",
            nodes = nodes,
            edges = edges,
            globalClues = globalClues,
            failSafePolicies = failSafePolicies
        )
    }

    private fun convertGeneratedToStoryTree(
        generated: GeneratedStoryTree,
        gameType: GameType
    ): StoryTree {
        val treeId = "tree_${System.currentTimeMillis()}"

        val nodes = generated.scenes.associate { scene ->
            scene.id to StoryNode(
                id = scene.id,
                description = scene.title,
                narrativeText = scene.narrativeText,
                objectives = emptyList(),
                allowedActions = scene.allowedActions.mapNotNull {
                    try { ActionType.valueOf(it.uppercase()) } catch (e: Exception) { null }
                },
                cluePool = CluePool(emptyList(), emptyList()),
                diceRules = null,
                imageUrl = null,
                isCritical = scene.isCritical
            )
        }

        val edges = generated.transitions.mapIndexed { index, transition ->
            "edge_${index + 1}" to TransitionEdge(
                id = "edge_${index + 1}",
                fromNodeId = transition.from,
                toNodeId = transition.to,
                condition = TransitionCondition(
                    type = try {
                        ConditionType.valueOf(transition.condition.uppercase())
                    } catch (e: Exception) {
                        ConditionType.AUTOMATIC
                    },
                    narrativeTrigger = transition.trigger
                ),
                priority = 1
            )
        }.toMap()

        val globalClues = generated.globalClues.map { clue ->
            Clue(
                id = clue.id,
                name = clue.name,
                description = clue.description,
                importance = try {
                    ClueImportance.valueOf(clue.importance.uppercase())
                } catch (e: Exception) {
                    ClueImportance.NORMAL
                }
            )
        }

        val failSafePolicies = generated.failSafePolicies.map { it.toDomain() }

        return StoryTree(
            id = treeId,
            title = generated.title,
            gameType = gameType,
            rootNodeId = generated.scenes.firstOrNull()?.id ?: "scene_1",
            nodes = nodes,
            edges = edges,
            globalClues = globalClues,
            failSafePolicies = failSafePolicies
        )
    }

    private fun storyTreeToJson(storyTree: StoryTree): String {
        return """
{
  "id": "${storyTree.id}",
  "title": "${storyTree.title}",
  "gameType": "${storyTree.gameType}",
  "rootNodeId": "${storyTree.rootNodeId}",
  "nodes": ${storyTree.nodes.size}个场景,
  "edges": ${storyTree.edges.size}条转场
}
""".trimIndent()
    }

    private fun FailSafePolicy.toDomain() = com.xmov.metahuman.app.trpg.FailSafePolicy(
        id = id,
        triggerCondition = triggerCondition,
        targetNodeId = targetScene,
        narrativeGuide = narrativeGuide,
        autoExecute = autoExecute
    )
}
