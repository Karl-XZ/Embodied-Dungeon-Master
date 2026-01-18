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
        return withContext(Dispatchers.IO) {
            try {
                val url = baseUrl.trimEnd('/') + "/images/generations"
                val size = buildSize(width, height)

                val json = JSONObject().apply {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)

                    // 说明：APIMart 的 /images/generations 接口并不一定支持 response_format 字段。
                    // 之前这里固定写入 response_format 会导致服务端返回：{"success":false,"message":"解析请求失败"}
                    // 因此先移除该字段，保持与官方示例 payload 更一致。

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
                    Log.d(TAG, "响应状态码: ${resp.code}")

                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("图片生成失败 HTTP ${resp.code}: $respBody"))
                    }

                    val obj = try {
                        JSONObject(respBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON解析失败", e)
                        Log.e(TAG, "原始响应: $respBody")
                        return@withContext Result.failure(RuntimeException("响应格式错误: ${e.message}"))
                    }

                    // ===== 先处理错误包装（服务端可能返回 success=false 或 code!=200） =====
                    // 例：{"message":"解析请求失败","success":false}
                    if (obj.has("success") && !obj.optBoolean("success", true)) {
                        val msg = obj.optString("message").ifBlank { "请求失败" }
                        return@withContext Result.failure(RuntimeException("图片生成失败: $msg\n$respBody"))
                    }

                    // 例：{"code":400,...}
                    if (obj.has("code")) {
                        val code = obj.optInt("code", 200)
                        if (code != 200 && code != 0) {
                            val msg = obj.optString("message").ifBlank { obj.optString("error") }
                                .ifBlank { "服务端返回错误 code=$code" }
                            return@withContext Result.failure(RuntimeException("图片生成失败: $msg\n$respBody"))
                        }
                    }

                    Log.d(TAG, "JSON对象键: ${obj.keys().asSequence().toList()}")

                    // 检查 APIMart 异步任务格式
                    val dataObj = if (obj.has("data") && obj.has("code")) {
                        Log.d(TAG, "检测到APIMart格式包装层")
                        val data = obj.get("data")
                        when (data) {
                            is String -> JSONObject(data)
                            is JSONObject -> data
                            else -> obj
                        }
                    } else {
                        Log.d(TAG, "直接使用原始JSON")
                        obj
                    }

                    val dataArray = dataObj.optJSONArray("data")
                    Log.d(TAG, "data数组: $dataArray")

                    if (dataArray != null && dataArray.length() > 0) {
                        val firstItem = dataArray.get(0)
                        if (firstItem is JSONObject) {
                            // 检查是否是异步任务
                            val status = firstItem.optString("status")
                            val taskId = firstItem.optString("task_id")

                            if (status == "submitted" && taskId.isNotEmpty()) {
                                Log.d(TAG, "检测到异步任务: task_id=$taskId")
                                // 轮询获取结果
                                return@withContext pollTaskResult(baseUrl, apiKey, taskId, width, height, model, prompt)
                            }
                        }

                        // 处理标准格式
                        return@withContext parseStandardDataArray(dataArray, width, height, model, respBody)
                    }

                    // 尝试其他响应格式
                    // 格式1: 直接返回 URL
                    if (dataObj.has("url")) {
                        val directUrl = dataObj.optString("url").takeIf { it.isNotBlank() }
                        if (!directUrl.isNullOrBlank()) {
                            Log.d(TAG, "检测到直接URL格式: $directUrl")
                            val result = ImageGenerationResult(
                                imageUrl = directUrl,
                                base64Data = null,
                                revisedPrompt = dataObj.optString("revised_prompt").takeIf { it.isNotBlank() },
                                width = width,
                                height = height,
                                model = model
                            )
                            return@withContext Result.success(result)
                        }
                    }

                    // 格式2: 直接返回 image 字段
                    if (dataObj.has("image")) {
                        val directImage = dataObj.optString("image").takeIf { it.isNotBlank() }
                        if (!directImage.isNullOrBlank()) {
                            Log.d(TAG, "检测到直接image格式: $directImage")
                            val result = ImageGenerationResult(
                                imageUrl = if (directImage.startsWith("http", ignoreCase = true)) directImage else null,
                                base64Data = if (!directImage.startsWith("http", ignoreCase = true)) directImage else null,
                                revisedPrompt = null,
                                width = width,
                                height = height,
                                model = model
                            )
                            return@withContext Result.success(result)
                        }
                    }

                    // 格式3: 直接返回 output 字段 (有些AI服务使用)
                    if (dataObj.has("output")) {
                        val output = dataObj.optJSONArray("output")
                        if (output != null && output.length() > 0) {
                            val firstOutput = output.get(0)
                            when (firstOutput) {
                                is String -> {
                                    if (firstOutput.startsWith("http", ignoreCase = true)) {
                                        Log.d(TAG, "检测到output数组URL格式: $firstOutput")
                                        val result = ImageGenerationResult(
                                            imageUrl = firstOutput,
                                            base64Data = null,
                                            revisedPrompt = null,
                                            width = width,
                                            height = height,
                                            model = model
                                        )
                                        return@withContext Result.success(result)
                                    }
                                }
                                is JSONObject -> {
                                    val url = firstOutput.optString("url").takeIf { it.isNotBlank() }
                                    val b64 = firstOutput.optString("b64_json").takeIf { it.isNotBlank() }
                                    if (!url.isNullOrBlank() || !b64.isNullOrBlank()) {
                                        Log.d(TAG, "检测到output数组对象格式")
                                        val result = ImageGenerationResult(
                                            imageUrl = url,
                                            base64Data = b64,
                                            revisedPrompt = firstOutput.optString("revised_prompt").takeIf { it.isNotBlank() },
                                            width = width,
                                            height = height,
                                            model = model
                                        )
                                        return@withContext Result.success(result)
                                    }
                                }
                            }
                        }
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
     * 轮询任务结果
     */
    private suspend fun pollTaskResult(
        baseUrl: String,
        apiKey: String,
        taskId: String,
        width: Int,
        height: Int,
        model: String,
        prompt: String
    ): Result<ImageGenerationResult> {
        val maxAttempts = 60 // 最多轮询60次 (2分钟)
        val pollInterval = 2000L // 每2秒轮询一次

        repeat(maxAttempts) { attempt ->
            delay(pollInterval)

            try {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/images/tasks/$taskId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    Log.d(TAG, "轮询任务结果 (尝试 ${attempt + 1}/$maxAttempts): $respBody")

                    if (!resp.isSuccessful) {
                        Log.w(TAG, "轮询任务失败: ${resp.code}")
                        return@repeat
                    }

                    val obj = JSONObject(respBody)
                    val code = obj.optInt("code", -1)

                    if (code == 200) {
                        val dataObj = obj.optJSONObject("data") ?: obj
                        val dataArray = dataObj.optJSONArray("data")

                        if (dataArray != null && dataArray.length() > 0) {
                            val firstItem = dataArray.get(0)
                            if (firstItem is JSONObject) {
                                val status = firstItem.optString("status")

                                when (status) {
                                    "completed" -> {
                                        val imageUrl = firstItem.optString("url").takeIf { it.isNotBlank() }
                                        val base64Data = firstItem.optString("b64_json").takeIf { it.isNotBlank() }

                                        if (!imageUrl.isNullOrBlank() || !base64Data.isNullOrBlank()) {
                                            Log.d(TAG, "任务完成: $imageUrl")
                                            return Result.success(ImageGenerationResult(
                                                imageUrl = imageUrl,
                                                base64Data = base64Data,
                                                revisedPrompt = null,
                                                width = width,
                                                height = height,
                                                model = model
                                            ))
                                        }
                                    }
                                    "failed" -> {
                                        val error = firstItem.optString("error", "任务失败")
                                        Log.e(TAG, "任务失败: $error")
                                        return Result.failure(RuntimeException("图片生成任务失败: $error"))
                                    }
                                    else -> {
                                        Log.d(TAG, "任务状态: $status, 继续轮询...")
                                    }
                                }
                            }
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

    /**
     * 批量生成图片（用于多个线索）
     */
    suspend fun generateImagesBatch(
        apiKey: String,
        baseUrl: String,
        prompts: List<String>,
        model: String,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<Result<ImageGenerationResult>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Result<ImageGenerationResult>>()
        prompts.forEachIndexed { index, prompt ->
            onProgress(index + 1, prompts.size, prompt)
            val result = generateImage(apiKey, baseUrl, prompt, model)
            results.add(result)
        }
        results
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
    val hasImage: Boolean
        get() = imageUrl != null || base64Data != null

    val imageData: String?
        get() = imageUrl ?: base64Data
}
