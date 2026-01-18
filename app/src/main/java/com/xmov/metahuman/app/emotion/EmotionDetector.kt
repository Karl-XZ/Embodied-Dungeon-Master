package com.xmov.metahuman.app.emotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.xmov.metahuman.app.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 玩家情绪状态检测器
 * 使用通义千问VL（Qwen-VL）API拍照识别玩家当前状态
 */
class EmotionDetector(private val context: Context) {

    private val TAG = "EmotionDetector"
    private val qwenVlClient = QwenVlClient()

    /**
     * 检测玩家情绪状态
     * @param imageBitmap 玩家照片
     * @param currentGameState 当前游戏状态（作为上下文）
     * @return 情绪状态分析结果
     */
    suspend fun detectEmotion(
        imageBitmap: Bitmap,
        currentGameState: String = ""
    ): Result<EmotionState> = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.getQwenVlApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未配置 Qwen-VL API Key"))
        }

        // 将 Bitmap 转换为 Base64（调整大小以节省流量）
        val base64Image = bitmapToBase64(resizeBitmap(imageBitmap, 512))

        // 构建提示词
        val prompt = buildEmotionPrompt(currentGameState)

        try {
            // 构建多模态消息
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", """
你是一个专业的情绪识别分析师。观察玩家照片，分析其当前的情绪状态、注意力集中程度、疲劳程度等。

输出格式（必须是JSON）：
{
  "mood": "积极/中性/消极/兴奋/沮丧/紧张/放松",
  "attention": "高度/中度/低度/分心",
  "energy": "高/中/低",
  "fatigue": "无/轻度/中度/重度",
  "stress": "无/轻度/中度/重度",
  "engagement": "完全投入/部分投入/不投入",
  "recommendation": "对DM的建议（简短）"
}
""".trimIndent())
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    })
                })
            }

            val baseUrl = AppSettings.getQwenVlBaseUrl()
            val model = AppSettings.getQwenVlModel()

            Log.d(TAG, "发送情绪识别请求: model=$model, baseUrl=$baseUrl")

            val response = qwenVlClient.chatCompletions(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                temperature = 0.3,
                maxTokens = 500
            ).getOrElse { return@withContext Result.failure(it) }

            Log.d(TAG, "情绪识别响应: $response")

            // 解析响应
            val emotionState = parseEmotionResponse(response)
            Result.success(emotionState)
        } catch (e: Exception) {
            Log.e(TAG, "情绪识别失败", e)
            Result.failure(e)
        }
    }

    /**
     * 构建情绪识别提示词
     */
    private fun buildEmotionPrompt(currentGameState: String): String {
        val gameContext = if (currentGameState.isNotEmpty()) {
            "当前游戏状态：$currentGameState"
        } else {
            ""
        }

        return """
请分析照片中玩家的情绪状态。

$gameContext

请特别关注：
1. 面部表情（微笑、皱眉等）
2. 眼神状态（专注、疲倦等）
3. 身体姿态（放松、紧张等）
4. 整体精神状态

只输出JSON，不要其他解释。
""".trimIndent()
    }

    /**
     * 解析情绪响应
     */
    private fun parseEmotionResponse(response: String): EmotionState {
        return try {
            // 提取JSON（可能包含在代码块中）
            val jsonText = extractJson(response)

            val json = JSONObject(jsonText)
            EmotionState(
                mood = json.optString("mood", "中性"),
                attention = json.optString("attention", "中度"),
                energy = json.optString("energy", "中"),
                fatigue = json.optString("fatigue", "无"),
                stress = json.optString("stress", "无"),
                engagement = json.optString("engagement", "部分投入"),
                recommendation = json.optString("recommendation", ""),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析情绪响应失败", e)
            EmotionState(
                mood = "中性",
                attention = "中度",
                energy = "中",
                fatigue = "无",
                stress = "无",
                engagement = "部分投入",
                recommendation = "",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 提取JSON（处理代码块）
     */
    private fun extractJson(text: String): String {
        val trimmed = text.trim()

        // 移除可能的代码块标记
        if (trimmed.startsWith("```json")) {
            return trimmed.substring(7).trimEnd('`').trim()
        }
        if (trimmed.startsWith("```")) {
            return trimmed.substring(3).trimEnd('`').trim()
        }

        // 尝试找到第一个 { 和最后一个 }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')

        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }

        return trimmed
    }

    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /**
     * 调整图片大小（用于API调用）
     */
    fun resizeBitmap(bitmap: Bitmap, maxSize: Int = 512): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        val matrix = Matrix()
        matrix.postScale(ratio, ratio)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * 将情绪状态转换为LLM输入的一部分
     */
    fun formatForLLM(emotion: EmotionState): String {
        return """
【玩家情绪状态】
情绪：${emotion.mood}
注意力：${emotion.attention}
精力：${emotion.energy}
疲劳度：${emotion.fatigue}
压力：${emotion.stress}
投入度：${emotion.engagement}
DM建议：${emotion.recommendation}
""".trimIndent()
    }
}

/**
 * 玩家情绪状态
 */
data class EmotionState(
    val mood: MoodType,
    val attention: AttentionLevel,
    val energy: EnergyLevel,
    val fatigue: FatigueLevel,
    val stress: StressLevel,
    val engagement: EngagementLevel,
    val recommendation: String,
    val timestamp: Long
) {
    constructor(
        mood: String,
        attention: String,
        energy: String,
        fatigue: String,
        stress: String,
        engagement: String,
        recommendation: String,
        timestamp: Long
    ) : this(
        mood = MoodType.from(mood),
        attention = AttentionLevel.from(attention),
        energy = EnergyLevel.from(energy),
        fatigue = FatigueLevel.from(fatigue),
        stress = StressLevel.from(stress),
        engagement = EngagementLevel.from(engagement),
        recommendation = recommendation,
        timestamp = timestamp
    )
}

/**
 * 情绪类型
 */
enum class MoodType {
    POSITIVE, NEUTRAL, NEGATIVE, EXCITED, FRUSTRATED, TENSE, RELAXED;

    companion object {
        fun from(value: String): MoodType {
            return when (value) {
                "积极" -> POSITIVE
                "兴奋" -> EXCITED
                "放松" -> RELAXED
                "消极" -> NEGATIVE
                "沮丧" -> FRUSTRATED
                "紧张" -> TENSE
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 注意力水平
 */
enum class AttentionLevel {
    HIGH, MEDIUM, LOW, DISTRACTED;

    companion object {
        fun from(value: String): AttentionLevel {
            return when (value) {
                "高度" -> HIGH
                "中度" -> MEDIUM
                "低度" -> LOW
                "分心" -> DISTRACTED
                else -> MEDIUM
            }
        }
    }
}

/**
 * 精力水平
 */
enum class EnergyLevel {
    HIGH, MEDIUM, LOW;

    companion object {
        fun from(value: String): EnergyLevel {
            return when (value) {
                "高" -> HIGH
                "中" -> MEDIUM
                "低" -> LOW
                else -> MEDIUM
            }
        }
    }
}

/**
 * 疲劳程度
 */
enum class FatigueLevel {
    NONE, MILD, MODERATE, SEVERE;

    companion object {
        fun from(value: String): FatigueLevel {
            return when (value) {
                "无" -> NONE
                "轻度" -> MILD
                "中度" -> MODERATE
                "重度" -> SEVERE
                else -> NONE
            }
        }
    }
}

/**
 * 压力水平
 */
enum class StressLevel {
    NONE, MILD, MODERATE, SEVERE;

    companion object {
        fun from(value: String): StressLevel {
            return when (value) {
                "无" -> NONE
                "轻度" -> MILD
                "中度" -> MODERATE
                "重度" -> SEVERE
                else -> NONE
            }
        }
    }
}

/**
 * 投入度
 */
enum class EngagementLevel {
    FULL, PARTIAL, NONE;

    companion object {
        fun from(value: String): EngagementLevel {
            return when (value) {
                "完全投入" -> FULL
                "部分投入" -> PARTIAL
                "不投入" -> NONE
                else -> PARTIAL
            }
        }
    }
}
