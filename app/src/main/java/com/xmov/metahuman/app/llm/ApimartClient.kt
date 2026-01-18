package com.xmov.metahuman.app.llm

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * APIMart（OpenAI 兼容）最小客户端
 */
class ApimartClient {

    private val TAG = "ApimartClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * OpenAI 兼容 chat/completions
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
                put("stream", false)  // 禁用流式响应
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
                Log.d(TAG, "原始响应: $respBody")

                if (!resp.isSuccessful) {
                    return Result.failure(RuntimeException("LLM HTTP ${resp.code}: $respBody"))
                }

                val obj = JSONObject(respBody)
                Log.d(TAG, "解析的根对象: $obj")

                // 支持 APIMart 格式：{code, data} 包装层
                // data 可能是对象、JSON 字符串，或直接是内容字符串
                var content: String? = null

                if (obj.has("data") && obj.has("code")) {
                    val data = obj.get("data")
                    Log.d(TAG, "data 字段类型: ${data.javaClass.name}")
                    Log.d(TAG, "data 字段值: $data")

                    when (data) {
                        is String -> {
                            // data 是字符串，可能是 JSON 字符串或直接内容
                            try {
                                // 尝试解析为 JSON
                                val jsonData = JSONObject(data)
                                val choices = jsonData.optJSONArray("choices")
                                val extracted = extractContentFromChoices(choices)
                                if (!extracted.isNullOrBlank()) {
                                    content = extracted
                                } else {
                                    // 如果解析成功但没有可提取的 choices，data 本身可能就是 content
                                    content = data
                                }
                            } catch (e: Exception) {
                                // 解析失败，data 就是纯文本内容
                                content = data
                            }
                        }
                        is JSONObject -> {
                            // data 已经是对象
                            val extracted = extractContentFromChoices(data.optJSONArray("choices"))
                            if (!extracted.isNullOrBlank()) content = extracted
                        }
                        else -> {
                            // 其他类型，作为兜底处理标准 OpenAI 格式
                            val extracted = extractContentFromChoices(obj.optJSONArray("choices"))
                            if (!extracted.isNullOrBlank()) content = extracted
                        }
                    }
                } else {
                    // 标准 OpenAI 格式
                    val extracted = extractContentFromChoices(obj.optJSONArray("choices") ?: JSONArray())
                    if (!extracted.isNullOrBlank()) content = extracted
                }

                Log.d(TAG, "最终提取的 content: ${content?.take(200)}...")

                if (content.isNullOrBlank()) {
                    return Result.failure(RuntimeException("LLM 未返回 content"))
                }

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 可选：列出模型（若服务端支持 /v1/models）
     */
    fun listModels(
        apiKey: String,
        baseUrl: String
    ): Result<List<String>> {
        return try {
            val url = baseUrl.trimEnd('/') + "/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(RuntimeException("Models HTTP ${resp.code}: $respBody"))
                }

                val obj = JSONObject(respBody)
                val data = obj.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    list.add(data.getJSONObject(i).optString("id"))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 从 choices 数组中尽量提取文本 content（支持元素为 JSONObject 或 String）
    private fun extractContentFromChoices(choices: JSONArray?): String? {
        if (choices == null || choices.length() == 0) return null
        try {
            val firstItem = choices.get(0)
            return when (firstItem) {
                is JSONObject -> {
                    val message = firstItem.optJSONObject("message")
                    message?.optString("content") ?: firstItem.optString("text")
                }
                is String -> firstItem
                else -> {
                    // 尝试将其字符串化并解析
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
            Log.w(TAG, "extractContentFromChoices failed", e)
            return null
        }
    }
}
