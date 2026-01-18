package com.xmov.metahuman.app.trpg

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipFile

/**
 * 剧本解析器
 * 支持解析 docx/pdf/txt 或结构化 zip，生成 StoryTree
 */
class ScriptParser {
    private val TAG = "ScriptParser"

    /**
     * 解析剧本文件
     */
    suspend fun parseScript(
        file: File,
        gameType: GameType
    ): Result<StoryTree> {
        return when (file.extension.lowercase()) {
            "json" -> parseJsonScript(file, gameType)
            "txt" -> parseTxtScript(file, gameType)
            "zip" -> parseZipScript(file, gameType)
            "docx" -> parseDocxScript(file, gameType)
            "pdf" -> parsePdfScript(file, gameType)
            else -> Result.failure(Exception("不支持的文件格式"))
        }
    }

    private fun parseDocxScript(file: File, gameType: GameType): Result<StoryTree> {
        return try {
            val text = DocxTextExtractor.extract(file)
            parseTxtContent(text, titleHint = file.nameWithoutExtension, gameType = gameType)
        } catch (e: Exception) {
            Log.e(TAG, "Parse DOCX script failed", e)
            Result.failure(e)
        }
    }

    private fun parsePdfScript(file: File, gameType: GameType): Result<StoryTree> {
        return try {
            val text = PdfTextExtractor.extract(file)
            parseTxtContent(text, titleHint = file.nameWithoutExtension, gameType = gameType)
        } catch (e: Exception) {
            Log.e(TAG, "Parse PDF script failed", e)
            Result.failure(e)
        }
    }

    /**
     * 解析 JSON 格式剧本
     */
    private fun parseJsonScript(file: File, gameType: GameType): Result<StoryTree> {
        return try {
            parseJsonContent(file.readText(), gameType)
        } catch (e: Exception) {
            Log.e(TAG, "Parse JSON script failed", e)
            Result.failure(e)
        }
    }

    /**
     * 直接解析 JSON 文本（用于 AI 生成 / ZIP 解包等场景）
     */
    fun parseJsonContent(content: String, gameType: GameType): Result<StoryTree> {
        return try {
            var jsonStr = content.trim()

            // 移除可能的 ```json 或 ``` 代码块标记
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7)
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3)
            }
            jsonStr = jsonStr.trimEnd('`').trim()

            var json = JSONObject(jsonStr)

            // 处理 APIMart 格式的 {code, data} 包装层
            if (json.has("data") && json.has("code")) {
                val data = json.get("data")
                when (data) {
                    is String -> json = JSONObject(data)  // data 是 JSON 字符串，再次解析
                    is JSONObject -> json = data          // data 已经是对象
                }
            }

            val treeId = json.optString("id", "tree_${System.currentTimeMillis()}")
            val title = json.optString("title", "未命名剧本")

            // 解析节点
            val nodesMap = mutableMapOf<String, StoryNode>()
            val nodesJson = json.optJSONArray("nodes")
            if (nodesJson != null) {
                for (i in 0 until nodesJson.length()) {
                    val item = nodesJson.get(i)
                    val node = when (item) {
                        is JSONObject -> parseStoryNode(item)
                        is String -> {
                            val id = "node_$i"
                            createDefaultScene(id, item, gameType)
                        }
                        else -> {
                            // 尝试把其它类型解析为 JSONObject，否则当做文本节点处理
                            try {
                                val maybeObj = JSONObject(item.toString())
                                parseStoryNode(maybeObj)
                            } catch (e: Exception) {
                                createDefaultScene("node_$i", item.toString(), gameType)
                            }
                        }
                    }
                    nodesMap[node.id] = node
                }
            }
            if (nodesMap.isEmpty()) {
                return Result.failure(IllegalArgumentException("JSON 剧本缺少 nodes"))
            }

            // 解析边
            val edgesMap = mutableMapOf<String, TransitionEdge>()
            val edgesJson = json.optJSONArray("edges")
            if (edgesJson != null) {
                for (i in 0 until edgesJson.length()) {
                    val item = edgesJson.get(i)
                    val edge = when (item) {
                        is JSONObject -> parseTransitionEdge(item)
                        is String -> {
                            // 尝试将字符串解析为 JSON 对象
                            try {
                                val edgeObj = JSONObject(item)
                                parseTransitionEdge(edgeObj)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse edge from string: $item", e)
                                null
                            }
                        }
                        else -> {
                            // 尝试转换为 JSON 对象
                            try {
                                val edgeObj = JSONObject(item.toString())
                                parseTransitionEdge(edgeObj)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse edge from object: $item", e)
                                null
                            }
                        }
                    }
                    edge?.let { edgesMap[edge.id] = it }
                }
            }

            // 解析全局线索（兼容字符串/对象数组）
            val globalClues = parseClues(json.optJSONArray("globalClues")).toMutableList()

            // 解析 Fail-Safe 策略
            val failSafePolicies = mutableListOf<FailSafePolicy>()
            val policiesJson = json.optJSONArray("failSafePolicies")
            if (policiesJson != null) {
                for (i in 0 until policiesJson.length()) {
                    val item = policiesJson.get(i)
                    val policy = when (item) {
                        is JSONObject -> parseFailSafePolicy(item)
                        is String -> {
                            // 尝试将字符串解析为 JSON 对象
                            try {
                                val policyObj = JSONObject(item)
                                parseFailSafePolicy(policyObj)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse policy from string: $item", e)
                                null
                            }
                        }
                        else -> {
                            // 尝试转换为 JSON 对象
                            try {
                                val policyObj = JSONObject(item.toString())
                                parseFailSafePolicy(policyObj)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse policy from object: $item", e)
                                null
                            }
                        }
                    }
                    policy?.let { failSafePolicies.add(it) }
                }
            }

            val root = json.optString("rootNodeId", nodesMap.keys.first())

            val storyTree = StoryTree(
                id = treeId,
                title = title,
                gameType = gameType,
                rootNodeId = root,
                nodes = nodesMap,
                edges = edgesMap,
                globalClues = globalClues,
                failSafePolicies = failSafePolicies
            )

            Result.success(storyTree)
        } catch (e: Exception) {
            Log.e(TAG, "Parse JSON content failed", e)
            Result.failure(e)
        }
    }


    /**
     * 解析 TXT 格式剧本（简化版）
     */
    private fun parseTxtScript(file: File, gameType: GameType): Result<StoryTree> {
        return try {
            parseTxtContent(file.readText(), titleHint = file.nameWithoutExtension, gameType = gameType)
        } catch (e: Exception) {
            Log.e(TAG, "Parse TXT script failed", e)
            Result.failure(e)
        }
    }

    /**
     * 将纯文本解析成线性 StoryTree（用于 txt/docx/pdf/zip 解包后的兜底解析）。
     *
     * 规则：
     * 1) 有 # 标题时按标题分段
     * 2) 否则按空行分段
     */
    private fun parseTxtContent(content: String, titleHint: String, gameType: GameType): Result<StoryTree> {
        val cleaned = content.replace("\r\n", "\n").trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("剧本文本为空"))
        }

        val treeId = "tree_${System.currentTimeMillis()}"
        val title = if (titleHint.isNotBlank()) titleHint else "未命名剧本"

        val nodesMap = linkedMapOf<String, StoryNode>()
        val edgesMap = linkedMapOf<String, TransitionEdge>()

        val hasHeadings = cleaned.lines().any { it.trim().startsWith("#") }

        val segments: List<String> = if (hasHeadings) {
            // 按 # 标题分段：标题行本身不作为内容
            val segs = mutableListOf<String>()
            val buf = StringBuilder()
            cleaned.lines().forEach { line ->
                if (line.trim().startsWith("#")) {
                    if (buf.isNotEmpty()) {
                        segs.add(buf.toString().trim())
                        buf.clear()
                    }
                } else {
                    buf.append(line).append("\n")
                }
            }
            if (buf.isNotEmpty()) segs.add(buf.toString().trim())
            segs
        } else {
            // 按空行分段
            cleaned.split(Regex("\n\\s*\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        }

        if (segments.isEmpty()) {
            return Result.failure(IllegalArgumentException("未能从文本中解析出场景段落"))
        }

        val nodeIdsInOrder = mutableListOf<String>()
        segments.forEachIndexed { index, seg ->
            val nodeId = "scene_$index"
            nodeIdsInOrder.add(nodeId)
            nodesMap[nodeId] = createDefaultScene(nodeId, seg, gameType)
        }

        for (i in 0 until nodeIdsInOrder.size - 1) {
            val edgeId = "edge_$i"
            edgesMap[edgeId] = TransitionEdge(
                id = edgeId,
                fromNodeId = nodeIdsInOrder[i],
                toNodeId = nodeIdsInOrder[i + 1],
                condition = TransitionCondition(type = ConditionType.AUTOMATIC),
                priority = 1,
                isFailSafe = false
            )
        }

        val storyTree = StoryTree(
            id = treeId,
            title = title,
            gameType = gameType,
            rootNodeId = nodeIdsInOrder.first(),
            nodes = nodesMap,
            edges = edgesMap,
            globalClues = emptyList(),
            failSafePolicies = emptyList()
        )

        return Result.success(storyTree)
    }

    /**
     * 解析 ZIP 格式剧本
     */
    private fun parseZipScript(file: File, gameType: GameType): Result<StoryTree> {
        return try {
            val zip = ZipFile(file)
            zip.use { zf ->
                // 优先级：json > txt > docx > pdf
                val targets = listOf("json", "txt", "docx", "pdf")
                val entries = mutableListOf<java.util.zip.ZipEntry>()
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (!e.isDirectory) entries.add(e)
                }

                val chosen = targets.firstNotNullOfOrNull { ext ->
                    entries.firstOrNull { it.name.lowercase().endsWith(".$ext") }
                } ?: return Result.failure(IllegalArgumentException("ZIP 内未找到可解析的剧本文件（json/txt/docx/pdf）"))

                val name = chosen.name.substringAfterLast('/').substringBeforeLast('.')
                val ext = chosen.name.substringAfterLast('.').lowercase()

                when (ext) {
                    "json" -> {
                        val jsonText = zf.getInputStream(chosen).bufferedReader().use { it.readText() }
                        parseJsonContent(jsonText, gameType)
                    }
                    "txt" -> {
                        val txt = zf.getInputStream(chosen).bufferedReader().use { it.readText() }
                        parseTxtContent(txt, titleHint = name, gameType = gameType)
                    }
                    "docx" -> {
                        val bytes = zf.getInputStream(chosen).use { it.readBytes() }
                        val txt = DocxTextExtractor.extract(bytes)
                        parseTxtContent(txt, titleHint = name, gameType = gameType)
                    }
                    "pdf" -> {
                        val bytes = zf.getInputStream(chosen).use { it.readBytes() }
                        val txt = PdfTextExtractor.extract(bytes)
                        parseTxtContent(txt, titleHint = name, gameType = gameType)
                    }
                    else -> Result.failure(IllegalArgumentException("不支持的 ZIP 内文件类型: $ext"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse ZIP script failed", e)
            Result.failure(e)
        }
    }

    /**
     * 解析故事节点
     */
    private fun parseStoryNode(json: JSONObject): StoryNode {
        return StoryNode(
            id = json.optString("id"),
            description = json.optString("description", ""),
            narrativeText = json.optString("narrativeText", json.optString("description", "")),
            objectives = parseObjectives(json.optJSONArray("objectives")),
            allowedActions = parseActionTypes(json.optJSONArray("allowedActions")),
            cluePool = parseCluePool(json.optJSONObject("cluePool")),
            diceRules = parseDiceRule(json.optJSONObject("diceRules")),
            imageUrl = json.optString("imageUrl", null),
            isCritical = json.optBoolean("isCritical", false)
        )
    }
    private fun parseObjectives(json: JSONArray?): List<Objective> {
        if (json == null) return emptyList()
        val list = mutableListOf<Objective>()

        for (i in 0 until json.length()) {
            val item = json.get(i)
            val objJson = when (item) {
                is JSONObject -> item
                is String -> JSONObject().apply { put("description", item) }
                else -> JSONObject().apply { put("description", item.toString()) }
            }

            val id = objJson.optString("id").ifBlank { "obj_$i" }
            val conditions = when (val cc = objJson.opt("completionConditions")) {
                is JSONArray -> parseStringList(cc)
                is String -> listOf(cc)
                else -> emptyList()
            }

            list.add(
                Objective(
                    id = id,
                    description = objJson.optString("description"),
                    isOptional = objJson.optBoolean("isOptional", false),
                    completionConditions = conditions
                )
            )
        }

        return list
    }



    private fun parseActionTypes(json: JSONArray?): List<ActionType> {
        if (json == null) return listOf(ActionType.SEARCH, ActionType.TALK, ActionType.DICE_ROLL)
        val list = mutableListOf<ActionType>()
        for (i in 0 until json.length()) {
            val typeStr = json.getString(i)
            try {
                list.add(ActionType.valueOf(typeStr.uppercase()))
            } catch (e: Exception) {
                Log.w(TAG, "Unknown action type: $typeStr")
            }
        }
        return list
    }

    private fun parseCluePool(json: JSONObject?): CluePool {
        if (json == null) {
            return CluePool(emptyList(), emptyList())
        }
        return CluePool(
            publicClues = parseClues(json.optJSONArray("publicClues")),
            privateClues = parseClues(json.optJSONArray("privateClues"))
        )
    }

    private fun parseClues(json: JSONArray?): List<Clue> {
        if (json == null) return emptyList()
        val list = mutableListOf<Clue>()
        for (i in 0 until json.length()) {
            val item = json.get(i)
            val clue = when (item) {
                is JSONObject -> parseClue(item)
                is String -> Clue(
                    id = "clue_$i",
                    name = item,
                    description = item,
                    importance = ClueImportance.NORMAL,
                    targetPlayers = emptyList(),
                    isDistributed = false,
                    imageUrl = null
                )
                else -> {
                    try {
                        val maybeObj = JSONObject(item.toString())
                        parseClue(maybeObj)
                    } catch (e: Exception) {
                        Clue(
                            id = "clue_$i",
                            name = item.toString(),
                            description = item.toString(),
                            importance = ClueImportance.NORMAL,
                            targetPlayers = emptyList(),
                            isDistributed = false,
                            imageUrl = null
                        )
                    }
                }
            }
            list.add(clue)
        }
        return list
    }

    private fun parseClue(json: JSONObject): Clue {
        return Clue(
            id = json.optString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            importance = try {
                ClueImportance.valueOf(json.optString("importance", "NORMAL").uppercase())
            } catch (e: Exception) {
                ClueImportance.NORMAL
            },
            targetPlayers = json.optJSONArray("targetPlayers")?.let { parseStringList(it) } ?: emptyList(),
            isDistributed = json.optBoolean("isDistributed", false),
            imageUrl = json.optString("imageUrl", null)
        )
    }

    private fun parseDiceRule(json: JSONObject?): DiceRule? {
        if (json == null) return null
        return DiceRule(
            ruleType = try {
                DiceRuleType.valueOf(json.optString("ruleType", "STORY_POINT").uppercase())
            } catch (e: Exception) {
                DiceRuleType.STORY_POINT
            },
            diceType = try {
                DiceType.valueOf(json.optString("diceType", "D20").uppercase())
            } catch (e: Exception) {
                DiceType.D20
            },
            difficulty = json.optInt("difficulty", 10),
            successThreshold = json.optInt("successThreshold", 11),
            criticalSuccess = json.optInt("criticalSuccess", 20).takeIf { it > 0 },
            criticalFailure = json.optInt("criticalFailure", 1).takeIf { it > 0 },
            modifiers = parseDiceModifiers(json.optJSONArray("modifiers"))
        )
    }

    private fun parseDiceModifiers(json: JSONArray?): List<DiceModifier> {
        if (json == null) return emptyList()
        val list = mutableListOf<DiceModifier>()
        for (i in 0 until json.length()) {
            val modJson = json.getJSONObject(i)
            list.add(
                DiceModifier(
                    name = modJson.optString("name"),
                    value = modJson.optInt("value", 0),
                    source = modJson.optString("source")
                )
            )
        }
        return list
    }

    private fun parseTransitionEdge(json: JSONObject): TransitionEdge {
        val conditionJson = json.getJSONObject("condition")
        return TransitionEdge(
            id = json.optString("id"),
            fromNodeId = json.optString("fromNodeId"),
            toNodeId = json.optString("toNodeId"),
            condition = TransitionCondition(
                type = try {
                    ConditionType.valueOf(conditionJson.optString("type", "AUTOMATIC").uppercase())
                } catch (e: Exception) {
                    ConditionType.AUTOMATIC
                },
                requiredClues = conditionJson.optJSONArray("requiredClues")
                    ?.let { parseStringList(it) } ?: emptyList(),
                requiredDiceResult = conditionJson.optJSONObject("requiredDiceResult")?.let {
                    DiceRequirement(
                        minResult = it.optInt("minResult", 1),
                        maxResult = it.optInt("maxResult", 20),
                        mustSucceed = it.optBoolean("mustSucceed", true)
                    )
                },
                requiredDecision = conditionJson.optString("requiredDecision", null),
                narrativeTrigger = conditionJson.optString("narrativeTrigger", null)
            ),
            priority = json.optInt("priority", 1),
            isFailSafe = json.optBoolean("isFailSafe", false)
        )
    }

    private fun parseFailSafePolicy(json: JSONObject): FailSafePolicy {
        return FailSafePolicy(
            id = json.optString("id"),
            triggerCondition = json.optString("triggerCondition"),
            targetNodeId = json.optString("targetNodeId"),
            narrativeGuide = json.optString("narrativeGuide"),
            autoExecute = json.optBoolean("autoExecute", false)
        )
    }

    private fun parseStringList(json: JSONArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until json.length()) {
            list.add(json.getString(i))
        }
        return list
    }

    private fun createDefaultScene(
        id: String,
        description: String,
        gameType: GameType
    ): StoryNode {
        return StoryNode(
            id = id,
            description = description,
            narrativeText = description,
            objectives = emptyList(),
            allowedActions = when (gameType) {
                GameType.JUBENSHA -> listOf(ActionType.SEARCH, ActionType.TALK, ActionType.INVESTIGATE)
                GameType.PAOTUAN -> listOf(ActionType.SEARCH, ActionType.DICE_ROLL, ActionType.ATTACK)
                GameType.HAITANG -> listOf(ActionType.SEARCH, ActionType.TALK)
            },
            cluePool = CluePool(emptyList(), emptyList()),
            diceRules = null,
            imageUrl = null,
            isCritical = false
        )
    }
}
