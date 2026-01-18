package com.xmov.metahuman.app.emotion

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 阿里云百炼 Qwen-VL (视觉大模型) 客户端
 * 用于多模态图像识别，如情绪识别、物体识别等
 *
 * API 文档: https://help.aliyun.com/zh/model-studio/get-api-key
 * 模型列表: https://help.aliyun.com/zh/model-studio/models
 */
class QwenVlClient {

    private val TAG = "QwenVlClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * OpenAI 兼容模式的 chat/completions 调用
     *
     * @param apiKey 阿里云百炼 API Key
     * @param baseUrl 基础URL（默认北京地域：https://dashscope.aliyuncs.com/compatible-mode/v1）
     * @param model 模型名称，如 "qwen3-vl-plus"
     * @param messages 消息数组，支持多模态内容（图片+文本）
     * @param temperature 温度参数（0-1）
     * @param maxTokens 最大输出token数
     */
    fun chatCompletions(
        apiKey: String,
        baseUrl: String,
        model: String,
        messages: JSONArray,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ): Result<String> {
        return try {
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val json = JSONObject().apply {
                put("model", model)
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
                Log.d(TAG, "Qwen-VL 响应: $respBody")

                if (!resp.isSuccessful) {
                    return Result.failure(RuntimeException("Qwen-VL HTTP ${resp.code}: $respBody"))
                }

                val obj = JSONObject(respBody)
                val choices = obj.optJSONArray("choices") ?: JSONArray()
                if (choices.length() == 0) {
                    return Result.failure(RuntimeException("Qwen-VL 返回空 choices"))
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
                    Log.w(TAG, "Qwen-VL choices parse failed", e)
                }

                if (content.isNullOrBlank()) {
                    return Result.failure(RuntimeException("Qwen-VL 未返回 content"))
                }

                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Qwen-VL API 调用失败", e)
            Result.failure(e)
        }
    }
}
