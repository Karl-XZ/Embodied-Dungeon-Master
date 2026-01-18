package com.xmov.metahuman.app.agent

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Agent 服务端客户端
 * 用于剧本智能解析、剧情树自动生成等高级功能
 */
class AgentServiceClient {

    private val TAG = "AgentServiceClient"
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var baseUrl: String = ""
    private var apiKey: String = ""

    /**
     * 配置客户端
     */
    fun configure(baseUrl: String, apiKey: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.apiKey = apiKey
    }

    // ========== 剧本解析 ==========

    /**
     * 智能解析剧本（自然语言转结构化剧情树）
     */
    suspend fun parseScriptFromText(
        scriptText: String,
        gameType: String
    ): Result<ParsedScript> = parseFromLLM(scriptText, gameType)

    /**
     * 从LLM解析剧本
     */
    private suspend fun parseFromLLM(scriptText: String, gameType: String): Result<ParsedScript> {
        return try {
            val systemPrompt = buildParseSystemPrompt()
            val userPrompt = buildParseUserPrompt(scriptText, gameType)

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

            val content = sendLLMRequest(messages, maxTokens = 4000)
                .getOrElse { return Result.failure(it) }

            val jsonStr = extractJson(content)
                ?: return Result.failure(IllegalArgumentException("未找到有效的JSON输出"))

            val parsed = gson.fromJson(jsonStr, ParsedScript::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse script", e)
            Result.failure(e)
        }
    }

    private fun buildParseSystemPrompt(): String {
        return """
你是一个专业的TRPG剧本解析专家。将自然语言剧本转换为结构化的剧情树JSON。

输出格式：
{
  "title": "剧本标题",
  "gameType": "JUBENSHA|PAOTUAN|HAITANG",
  "scenes": [
    {
      "id": "scene_1",
      "title": "场景标题",
      "description": "场景描述",
      "narrativeText": "DM朗读的叙事文本",
      "allowedActions": ["SEARCH", "TALK", "DICE_ROLL"],
      "clues": ["线索1", "线索2"],
      "isCritical": false
    }
  ],
  "transitions": [
    {
      "from": "scene_1",
      "to": "scene_2",
      "condition": "AUTOMATIC|NARRATIVE_INPUT|DICE_SUCCESS|CLUE_FOUND",
      "trigger": "触发关键词"
    }
  ],
  "globalClues": [
    {
      "id": "clue_1",
      "name": "线索名称",
      "description": "线索描述",
      "importance": "CRITICAL|IMPORTANT|NORMAL|MINOR"
    }
  ],
  "failSafePolicies": [
    {
      "id": "fail_1",
      "triggerCondition": "触发条件描述",
      "targetScene": "scene_1",
      "narrativeGuide": "DM引导话术"
    }
  ]
}

要求：
1. 只输出JSON，不要其他解释
2. 场景ID按 scene_1, scene_2... 顺序编号
3. transitions定义场景之间的转换关系
4. 合理分配线索到各个场景
5. 设置合理的fail-safe策略
""".trimIndent()
    }

    private fun buildParseUserPrompt(scriptText: String, gameType: String): String {
        return """
请将以下剧本解析为结构化的剧情树：

游戏类型：$gameType

剧本内容：
${scriptText.take(3000)}

要求：
1. 提取所有场景及其描述
2. 为每个场景生成适合的DM朗读文本
3. 设计场景之间的转换关系
4. 提取或生成合理的线索
5. 设置Fail-Safe策略
""".trimIndent()
    }

    // ========== 剧情树生成 ==========

    /**
     * 自动生成剧情树
     */
    suspend fun generateStoryTree(
        theme: String,
        gameType: String,
        sceneCount: Int = 5,
        complexity: Complexity = Complexity.MEDIUM
    ): Result<GeneratedStoryTree> {
        return try {
            val systemPrompt = buildGenerationSystemPrompt()
            val userPrompt = buildGenerationUserPrompt(theme, gameType, sceneCount, complexity)

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

            val content = sendLLMRequest(messages, maxTokens = 5000)
                .getOrElse { return Result.failure(it) }

            val jsonStr = extractJson(content)
                ?: return Result.failure(IllegalArgumentException("未找到有效的JSON输出"))

            val parsed = gson.fromJson(jsonStr, GeneratedStoryTree::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate story tree", e)
            Result.failure(e)
        }
    }

    private fun buildGenerationSystemPrompt(): String {
        return """
你是一个专业的TRPG剧情设计师。根据主题自动生成完整的剧情树。

输出格式与parseScriptFromText相同，但要求：
1. 每个场景有丰富的描述和DM朗读文本
2. 设计多条分支路线
3. 线索设计要有层次感（关键、重要、普通、次要）
4. 转场条件多样化（对话、骰点、线索等）
5. 合理的Fail-Safe策略防止玩家跑偏
6. 至少有一个关键转折点场景
""".trimIndent()
    }

    private fun buildGenerationUserPrompt(
        theme: String,
        gameType: String,
        sceneCount: Int,
        complexity: Complexity
    ): String {
        val complexityDesc = when (complexity) {
            Complexity.SIMPLE -> "简单，线性剧情，少量分支"
            Complexity.MEDIUM -> "中等，多条分支，适度互动"
            Complexity.COMPLEX -> "复杂，网状剧情，大量选择和后果"
        }

        return """
请根据以下要求生成一个完整的TRPG剧情树：

主题：$theme
游戏类型：$gameType
场景数量：$sceneCount
复杂度：$complexityDesc

要求：
1. 剧情要紧扣主题，有悬念和吸引力
2. 场景之间有逻辑关联
3. 线索设计合理，能推动剧情发展
4. 提供多种结局可能
5. Fail-Safe策略要合理有效
""".trimIndent()
    }

    // ========== Fail-Safe 自动生成 ==========

    /**
     * 自动生成 Fail-Safe 策略
     */
    suspend fun generateFailSafePolicies(
        storyTree: String
    ): Result<List<FailSafePolicy>> {
        return try {
            val systemPrompt = """
你是一个TRPG剧情平衡专家。为给定的剧情树生成Fail-Safe策略。

输出格式：
[
  {
    "id": "fail_1",
    "triggerCondition": "触发条件描述（如：玩家在场景1停留超过5分钟）",
    "targetScene": "目标场景ID",
    "narrativeGuide": "DM引导话术，引导玩家回到主线",
    "autoExecute": false
  }
]

要求：
1. 识别可能让玩家跑偏的场景
2. 为每个风险点设计合理的回归策略
3. 引导话术要自然，不破坏沉浸感
""".trimIndent()

            val userPrompt = "为以下剧情树生成Fail-Safe策略：\n${storyTree.take(2000)}"

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

            val content = sendLLMRequest(messages, maxTokens = 2000)
                .getOrElse { return Result.failure(it) }

            val jsonStr = extractJson(content)
                ?: return Result.failure(IllegalArgumentException("未找到有效的JSON输出"))

            val policies = gson.fromJson(jsonStr, Array<FailSafePolicy>::class.java).toList()
            Result.success(policies)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate fail-safe policies", e)
            Result.failure(e)
        }
    }

    // ========== LLM 请求 ==========

    private suspend fun sendLLMRequest(
        messages: JSONArray,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ): Result<String> {
        return try {
            val url = "$baseUrl/chat/completions"

            val json = JSONObject().apply {
                put("model", "gpt-4o")
                put("messages", messages)
                put("temperature", temperature)
                put("stream", false)
                if (maxTokens != null) put("max_tokens", maxTokens)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(RuntimeException("LLM请求失败: $respBody"))
                }

                val obj = JSONObject(respBody)
                val dataObj = if (obj.has("data") && obj.has("code")) {
                    val data = obj.get("data")
                    when (data) {
                        is String -> JSONObject(data)
                        is JSONObject -> data
                        else -> obj
                    }
                } else {
                    obj
                }

                val choices = dataObj.optJSONArray("choices") ?: JSONArray()
                if (choices.length() == 0) {
                    return Result.failure(RuntimeException("LLM返回空内容"))
                }

                var content: String? = null
                try {
                    val firstItem = choices.get(0)
                    content = when (firstItem) {
                        is JSONObject -> {
                            val message = firstItem.optJSONObject("message")
                            message?.optString("content") ?: firstItem.optString("text")
                        }
                        is String -> firstItem
                        else -> {
                            try {
                                val maybe = JSONObject(firstItem.toString())
                                val message = maybe.optJSONObject("message")
                                message?.optString("content") ?: maybe.optString("text")
                            } catch (e: Exception) {
                                firstItem.toString()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore and let content be null
                }

                if (content.isNullOrBlank()) {
                    return Result.failure(RuntimeException("LLM未返回内容"))
                }

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJson(text: String): String? {
        val trimmed = text.trim()

        if (trimmed.startsWith("```json")) {
            return trimmed.substring(7).trimEnd('`').trim()
        }
        if (trimmed.startsWith("```")) {
            return trimmed.substring(3).trimEnd('`').trim()
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')

        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }

        return null
    }
}

// ========== 数据模型 ==========

/**
 * 解析后的剧本
 */
data class ParsedScript(
    val title: String,
    val gameType: String,
    val scenes: List<ParsedScene>,
    val transitions: List<ParsedTransition>,
    val globalClues: List<ParsedClue>,
    val failSafePolicies: List<FailSafePolicy>
)

/**
 * 解析的场景
 */
data class ParsedScene(
    val id: String,
    val title: String,
    val description: String,
    val narrativeText: String,
    val allowedActions: List<String>,
    val clues: List<String>,
    val isCritical: Boolean
)

/**
 * 解析的转场
 */
data class ParsedTransition(
    val from: String,
    val to: String,
    val condition: String,
    val trigger: String
)

/**
 * 解析的线索
 */
data class ParsedClue(
    val id: String,
    val name: String,
    val description: String,
    val importance: String
)

/**
 * 生成的剧情树
 */
data class GeneratedStoryTree(
    val title: String,
    val gameType: String,
    val scenes: List<ParsedScene>,
    val transitions: List<ParsedTransition>,
    val globalClues: List<ParsedClue>,
    val failSafePolicies: List<FailSafePolicy>,
    val alternativeEndings: List<Ending>
)

/**
 * 结局
 */
data class Ending(
    val id: String,
    val title: String,
    val description: String,
    val conditions: List<String>
)

/**
 * Fail-Safe 策略
 */
data class FailSafePolicy(
    val id: String,
    val triggerCondition: String,
    val targetScene: String,
    val narrativeGuide: String,
    val autoExecute: Boolean
)

/**
 * 复杂度
 */
enum class Complexity {
    SIMPLE, MEDIUM, COMPLEX
}
