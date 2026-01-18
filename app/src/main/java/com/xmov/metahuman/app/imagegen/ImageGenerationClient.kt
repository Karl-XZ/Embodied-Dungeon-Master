package com.xmov.metahuman.app.imagegen

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    suspend fun generateImage(
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
        // APIMart 的 /v1/images/generations 是异步接口：先返回 task_id，再用 /v1/tasks/{task_id} 查询结果
        // 为了避免 “解析请求失败”，这里尽量只发送官方文档明确支持的字段：model / prompt / size / n / image_urls / mask_url / resolution
        return withContext(Dispatchers.IO) {
            try {
                val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
                val url = normalizedBaseUrl.trimEnd('/') + "/images/generations"

                val size = chooseSupportedSizeForModel(model, width, height)

                val json = JSONObject().apply {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)

                    // Gemini 系列文档里经常带 resolution，可选。不给也能跑，但这里给一个默认值更稳。
                    if (model.contains("gemini", ignoreCase = true)) {
                        put("resolution", "1K")
                    }

                    // 兼容：如果调用方把参考图 URL 放进 prompt（或其他地方）就不在这里猜了。
                    // 如需图生图/编辑，请在上层传入 image_urls / mask_url。
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                Log.d(TAG, "发送图片生成请求(model=$model,size=$size)")

                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    Log.d(TAG, "图片生成响应: $respBody")

                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("图片生成失败 HTTP ${resp.code}: $respBody"))
                    }

                    val obj = try {
                        JSONObject(respBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON解析失败", e)
                        return@withContext Result.failure(RuntimeException("响应格式错误: ${e.message}\n$respBody"))
                    }

                    // APIMart 的错误格式：{"message":"...","success":false}
                    if (obj.optBoolean("success", true) == false) {
                        val msg = obj.optString("message").ifBlank { "Request failed" }
                        val errorMsg = "图片生成失败: $msg\n完整响应: $respBody"
                        Log.e(TAG, errorMsg)
                        return@withContext Result.failure(RuntimeException(errorMsg))
                    }

                    val code = obj.optInt("code", 200)
                    if (obj.has("code") && code != 200) {
                        val msg = obj.optString("message").ifBlank { "Request failed" }
                        val errorMsg = "图片生成失败(code=$code): $msg\n完整响应: $respBody"
                        Log.e(TAG, errorMsg)
                        return@withContext Result.failure(RuntimeException(errorMsg))
                    }

                    // APIMart 成功提交：{ code:200, data:[{status:'submitted', task_id:'...'}] }
                    val dataAny = obj.opt("data")
                    val dataArray: JSONArray? = when (dataAny) {
                        is JSONArray -> dataAny
                        is JSONObject -> dataAny.optJSONArray("data")
                        is String -> runCatching { JSONArray(dataAny) }.getOrNull() ?: runCatching { JSONObject(dataAny).optJSONArray("data") }.getOrNull()
                        else -> null
                    }

                    if (dataArray != null && dataArray.length() > 0) {
                        val first = dataArray.opt(0)
                        if (first is JSONObject) {
                            val status = first.optString("status")
                            val taskId = first.optString("task_id")
                            if (status.equals("submitted", ignoreCase = true) && taskId.isNotBlank()) {
                                Log.d(TAG, "检测到异步任务: task_id=$taskId")
                                return@withContext pollTaskResult(normalizedBaseUrl, apiKey, taskId, width, height, model)
                            }
                        }

                        // 兼容：如果某些服务直接返回图片数组
                        return@withContext parseStandardDataArray(dataArray, width, height, model, respBody)
                    }

                    // 兼容：少数服务直接返回 url 字段
                    val directUrl = obj.optString("url").takeIf { it.isNotBlank() }
                    if (!directUrl.isNullOrBlank()) {
                        return@withContext Result.success(
                            ImageGenerationResult(
                                imageUrl = directUrl,
                                base64Data = null,
                                revisedPrompt = obj.optString("revised_prompt").takeIf { it.isNotBlank() },
                                width = width,
                                height = height,
                                model = model
                            )
                        )
                    }

                    val errorMsg = "响应中没有data数组，且不识别的响应格式。完整响应:\n$respBody"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "图片生成异常", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 允许 baseUrl 配置为：
     * - https://api.apimart.ai/v1
     * - https://api.apimart.ai/v1/images/generations
     * 等。这里统一归一化到 v1 层。
     */
    private fun normalizeBaseUrl(baseUrl: String): String {
        var b = baseUrl.trim().trimEnd('/')

        // 允许直接填到 endpoint 级别
        if (b.endsWith("/images/generations")) b = b.removeSuffix("/images/generations")
        if (b.endsWith("/images/edits")) b = b.removeSuffix("/images/edits")
        if (b.contains("/v1/")) {
            // 已经带 v1，不动
            b = b.substring(0, b.indexOf("/v1/") + 3)
        }

        // 确保落在 /v1 层（APIMart 文档统一是 /v1/...）
        if (!b.endsWith("/v1")) {
            b = b + "/v1"
        }

        return b
    }

    /**
     * 轮询任务结果
     */
    private suspend fun pollTaskResult(
        baseUrl: String,
        apiKey: String,
        taskId: String,
        width: Int,
        height: Int,
        model: String
    ): Result<ImageGenerationResult> {
        // 参考 APIMart 文档：GET /v1/tasks/{task_id}?language=zh
        val maxAttempts = 60
        val pollInterval = 2000L
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

        repeat(maxAttempts) { attempt ->
            delay(pollInterval)

            try {
                val request = Request.Builder()
                    .url("${normalizedBaseUrl.trimEnd('/')}/tasks/$taskId?language=zh")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    Log.d(TAG, "轮询任务结果 (尝试 ${attempt + 1}/$maxAttempts): $respBody")

                    if (!resp.isSuccessful) {
                        Log.w(TAG, "轮询任务失败: HTTP ${resp.code}")
                        return@repeat
                    }

                    val obj = JSONObject(respBody)
                    val code = obj.optInt("code", -1)
                    if (code != 200) {
                        return@repeat
                    }

                    val data = obj.optJSONObject("data") ?: return@repeat
                    val status = data.optString("status")

                    when (status) {
                        "completed" -> {
                            // Image task: data.result.images[0].url[0]
                            val result = data.optJSONObject("result")
                            val images = result?.optJSONArray("images")
                            val firstImg = images?.optJSONObject(0)

                            val urlArr = firstImg?.optJSONArray("url")
                            val imageUrl = urlArr?.optString(0).takeIf { !it.isNullOrBlank() }

                            if (!imageUrl.isNullOrBlank()) {
                                Log.d(TAG, "任务完成: $imageUrl")
                                return Result.success(
                                    ImageGenerationResult(
                                        imageUrl = imageUrl,
                                        base64Data = null,
                                        revisedPrompt = null,
                                        width = width,
                                        height = height,
                                        model = model
                                    )
                                )
                            }

                            // 兜底：有些模型可能返回 b64
                            val b64 = firstImg?.optString("b64_json").takeIf { !it.isNullOrBlank() }
                            if (!b64.isNullOrBlank()) {
                                return Result.success(
                                    ImageGenerationResult(
                                        imageUrl = null,
                                        base64Data = b64,
                                        revisedPrompt = null,
                                        width = width,
                                        height = height,
                                        model = model
                                    )
                                )
                            }
                        }
                        "failed" -> {
                            val errObj = data.optJSONObject("error")
                            val msg = errObj?.optString("message").takeIf { !it.isNullOrBlank() } ?: "任务失败"
                            Log.e(TAG, "任务失败: $msg")
                            return Result.failure(RuntimeException("图片生成任务失败: $msg"))
                        }
                        else -> {
                            // pending / processing / cancelled
                            Log.d(TAG, "任务状态: $status, 继续轮询...")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "轮询任务异常", e)
            }
        }

        return Result.failure(RuntimeException("图片生成超时: 轮询${maxAttempts}次后仍未完成"))
    }


    /**
     * 解析标准 data 数组格式
     */
    private fun parseStandardDataArray(
        dataArray: JSONArray,
        width: Int,
        height: Int,
        model: String,
        respBody: String
    ): Result<ImageGenerationResult> {
        if (dataArray.length() == 0) {
            return Result.failure(RuntimeException("未返回图片数据: data数组为空"))
        }

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
            return Result.failure(RuntimeException("图片返回格式不支持: data[0]=$first\n完整响应: $respBody"))
        }

        val result = ImageGenerationResult(
            imageUrl = imageUrl,
            base64Data = base64Data,
            revisedPrompt = revisedPrompt,
            width = width,
            height = height,
            model = model
        )

        return Result.success(result)
    }

    private fun chooseSupportedSizeForModel(model: String, width: Int, height: Int): String {
        // APIMart 的 size 用“比例字符串”。为了减少服务端解析失败，只在已知可用的比例里选一个“最接近”的。
        val w = if (width <= 0) 1 else width
        val h = if (height <= 0) 1 else height
        val ratio = w.toDouble() / h.toDouble()

        val supported = when {
            // 文档里 GPT-4o-image 只写了 1:1 / 2:3 / 3:2
            model.contains("gpt-4o", ignoreCase = true) -> listOf("1:1", "2:3", "3:2")
            else -> listOf("1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9")
        }

        fun parse(r: String): Double {
            val parts = r.split(":")
            val a = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
            val b = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
            return a / b
        }

        var best = supported.first()
        var bestDiff = 1e9
        for (s in supported) {
            val d = kotlin.math.abs(parse(s) - ratio)
            if (d < bestDiff) {
                bestDiff = d
                best = s
            }
        }
        return best
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
    suspend fun editImage(
        apiKey: String,
        baseUrl: String,
        imageBase64: String,
        prompt: String,
        model: String = "stable-diffusion-xl"
    ): Result<ImageGenerationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val url = normalizeBaseUrl(baseUrl).trimEnd('/') + "/images/edits"

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
                    return@withContext Result.failure(RuntimeException("图片编辑失败: $respBody"))
                }

                val obj = JSONObject(respBody)
                val dataArray = obj.optJSONArray("data") ?: JSONArray()
                if (dataArray.length() == 0) {
                    return@withContext Result.failure(RuntimeException("未返回编辑后图片"))
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
                    return@withContext Result.failure(RuntimeException("图片编辑返回格式不支持: data[0]=$first"))
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
