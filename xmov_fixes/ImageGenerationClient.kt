package com.xmov.metahuman.app.imagegen

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * APIMart 图片生成客户端
 * 用于生成线索图、场景CG等
 */
class ImageGenerationClient {

    private val TAG = "ImageGenClient"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 生成图片
     * @param apiKey API Key
     * @param baseUrl Base URL (默认 APIMart)
     * @param prompt 提示词
     * @param model 模型名称 (如 stable-diffusion, dall-e-3 等)
     * @param width 图片宽度
     * @param height 图片高度
     * @return 图片URL或Base64数据
     */
    fun generateImage(
        apiKey: String,
        baseUrl: String = "https://api.apimart.ai/v1",
        prompt: String,
        model: String = "gemini-2.5-flash-image-preview",
        width: Int = 1024,
        height: Int = 1024,
        steps: Int = 30,
        guidanceScale: Float = 7.5f,
        seed: Int? = null
    ): Result<ImageGenerationResult> {
        return try {
            val url = baseUrl.trimEnd('/') + "/images/generations"
            val size = buildSize(width, height)

            val json = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("n", 1)
                // APIMart 格式：比例格式 "1:1" 而非 "1024x1024"
                put("size", size)
                put("response_format", "url") // 或 "b64_json"

                // Stable Diffusion 特定参数（可选）
                if (model.contains("stable", ignoreCase = true)) {
                    put("steps", steps)
                    put("guidance_scale", guidanceScale)
                    seed?.let { put("seed", it) }
                }
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            Log.d(TAG, "发送图片生成请求: $prompt")

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                Log.d(TAG, "图片生成响应: $respBody")

                if (!resp.isSuccessful) {
                    return Result.failure(RuntimeException("图片生成失败 HTTP ${resp.code}: $respBody"))
                }

                val obj = JSONObject(respBody)

                // 支持 APIMart 格式：{code, data} 包装层
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

                val dataArray = dataObj.optJSONArray("data") ?: JSONArray()
                if (dataArray.length() == 0) {
                    return Result.failure(RuntimeException("未返回图片数据"))
                }

                // 不同服务商返回的 data[i] 可能是 JSONObject（OpenAI 风格）或 String（直接返回 URL / Base64）
                val first = dataArray.get(0)
                var imageUrl: String? = null
                var base64Data: String? = null
                var revisedPrompt: String? = null

                when (first) {
                    is JSONObject -> {
                        imageUrl = first.optString("url").takeIf { it.isNotBlank() }
                        base64Data = first.optString("b64_json").takeIf { it.isNotBlank() }
                        revisedPrompt = first.optString("revised_prompt").takeIf { it.isNotBlank() }
                    }
                    is String -> {
                        if (first.startsWith("http", ignoreCase = true)) imageUrl = first
                        else base64Data = first
                    }
                    else -> {
                        val s = first.toString()
                        if (s.startsWith("http", ignoreCase = true)) imageUrl = s
                        else base64Data = s
                    }
                }

                if (imageUrl.isNullOrBlank() && base64Data.isNullOrBlank()) {
                    return Result.failure(RuntimeException("图片返回格式不支持: data[0]=$first"))
                }

                val result = ImageGenerationResult(
                    imageUrl = imageUrl,
                    base64Data = base64Data,
                    revisedPrompt = revisedPrompt,
                    width = width,
                    height = height,
                    model = model
                )

                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "图片生成异常", e)
            Result.failure(e)
        }
    }

    private fun buildSize(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "1:1"
        if (width == height) return "1:1"
        val gcd = gcd(width, height)
        val w = width / gcd
        val h = height / gcd
        return "${w}:${h}"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }

    /**
     * 批量生成图片（用于多个线索）
     */
    suspend fun generateImagesBatch(
        apiKey: String,
        baseUrl: String,
        prompts: List<String>,
        model: String,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<Result<ImageGenerationResult>> {
        val results = mutableListOf<Result<ImageGenerationResult>>()
        prompts.forEachIndexed { index, prompt ->
            onProgress(index + 1, prompts.size, prompt)
            val result = generateImage(apiKey, baseUrl, prompt, model)
            results.add(result)
        }
        return results
    }

    /**
     * 编辑图片（用于线索卡等）
     */
    fun editImage(
        apiKey: String,
        baseUrl: String,
        imageBase64: String,
        prompt: String,
        model: String = "stable-diffusion-xl"
    ): Result<ImageGenerationResult> {
        return try {
            val url = baseUrl.trimEnd('/') + "/images/edits"

            val json = JSONObject().apply {
                put("model", model)
                put("image", imageBase64)
                put("prompt", prompt)
                put("n", 1)
                put("size", "1024x1024")
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
                    return Result.failure(RuntimeException("图片编辑失败: $respBody"))
                }

                val obj = JSONObject(respBody)
                val dataArray = obj.optJSONArray("data") ?: JSONArray()
                if (dataArray.length() == 0) {
                    return Result.failure(RuntimeException("未返回编辑后图片"))
                }

                val first = dataArray.get(0)
                var imageUrl: String? = null
                var base64Data: String? = null

                when (first) {
                    is JSONObject -> {
                        imageUrl = first.optString("url").takeIf { it.isNotBlank() }
                        base64Data = first.optString("b64_json").takeIf { it.isNotBlank() }
                    }
                    is String -> {
                        if (first.startsWith("http", ignoreCase = true)) imageUrl = first
                        else base64Data = first
                    }
                    else -> {
                        val s = first.toString()
                        if (s.startsWith("http", ignoreCase = true)) imageUrl = s
                        else base64Data = s
                    }
                }

                if (imageUrl.isNullOrBlank() && base64Data.isNullOrBlank()) {
                    return Result.failure(RuntimeException("图片编辑返回格式不支持: data[0]=$first"))
                }

                Result.success(
                    ImageGenerationResult(
                        imageUrl = imageUrl,
                        base64Data = base64Data,
                        revisedPrompt = null,
                        width = 1024,
                        height = 1024,
                        model = model
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 图片生成结果
 */
data class ImageGenerationResult(
    val imageUrl: String?,
    val base64Data: String?,
    val revisedPrompt: String?,
    val width: Int,
    val height: Int,
    val model: String
) {
    /**
     * 是否有可用图片
     */
    val hasImage: Boolean
        get() = imageUrl != null || base64Data != null

    /**
     * 获取图片数据（URL或Base64）
     */
    val imageData: String?
        get() = imageUrl ?: base64Data
}
