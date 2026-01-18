package com.xmov.metahuman.app.imagegen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.bumptech.glide.Glide
import com.xmov.metahuman.app.AppSettings
import com.xmov.metahuman.app.trpg.Clue
import com.xmov.metahuman.app.trpg.GameType
import com.xmov.metahuman.app.trpg.StoryNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 场景图片类型
 */
enum class SceneImageType {
    BACKGROUND,     // 背景图
    CHARACTER,      // 角色图
    ITEM,          // 物品图
    ATMOSPHERE     // 氛围图
}

/**
 * 游戏图片生成器
 * 根据线索、场景自动生成对应图片
 */
class GameImageGenerator(private val context: Context) {

    private val TAG = "GameImageGenerator"
    private val imageClient = ImageGenerationClient()

    /**
     * 图片缓存目录
     */
    private val cacheDir by lazy {
        File(context.cacheDir, "game_images").apply { mkdirs() }
    }

    /**
     * 为线索生成图片
     */
    suspend fun generateClueImage(
        clue: Clue,
        gameType: GameType
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.getLlmApiKey().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未配置 API Key"))
        }

        val prompt = buildCluePrompt(clue, gameType)
        Log.d(TAG, "生成线索图片: ${clue.name}, prompt: $prompt")

        val result = imageClient.generateImage(
            apiKey = apiKey,
            baseUrl = AppSettings.getLlmBaseUrl(),
            prompt = prompt,
            model = AppSettings.getImageGenModel(),
            width = 512,
            height = 512
        )

        result.fold(
            onSuccess = { imageResult ->
                val filePath = saveImageToCache(imageResult, "clue_${clue.id}")
                Result.success(filePath)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * 为场景生成CG
     */
    suspend fun generateSceneImage(
        scene: StoryNode,
        gameType: GameType
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.getLlmApiKey().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未配置 API Key"))
        }

        val prompt = buildScenePrompt(scene, gameType)
        Log.d(TAG, "生成场景图片: ${scene.description}, prompt: $prompt")

        val result = imageClient.generateImage(
            apiKey = apiKey,
            baseUrl = AppSettings.getLlmBaseUrl(),
            prompt = prompt,
            model = AppSettings.getImageGenModel(),
            width = 1024,
            height = 768
        )

        result.fold(
            onSuccess = { imageResult ->
                val filePath = saveImageToCache(imageResult, "scene_${scene.id}")
                Result.success(filePath)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * 批量为线索生成图片
     */
    suspend fun generateCluesImagesBatch(
        clues: List<Clue>,
        gameType: GameType,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()

        clues.forEachIndexed { index, clue ->
            onProgress(index + 1, clues.size)
            generateClueImage(clue, gameType)
                .onSuccess { filePath ->
                    results[clue.id] = filePath
                }
        }

        results
    }

    /**
     * 构建线索图片提示词
     */
    private fun buildCluePrompt(clue: Clue, gameType: GameType): String {
        val style = when (gameType) {
            GameType.JUBENSHA -> "mystery crime scene investigation, dark atmosphere, realistic"
            GameType.PAOTUAN -> "fantasy RPG item illustration, detailed, magical"
            GameType.HAITANG -> "minimalist icon style, clean, mystery"
        }

        return """
${clue.description}.

Style: $style.
Quality: high quality, detailed.
Aspect ratio: square.
No text overlay.
""".trimIndent()
    }

    /**
     * 构建场景图片提示词
     */
    private fun buildScenePrompt(scene: StoryNode, gameType: GameType): String {
        val style = when (gameType) {
            GameType.JUBENSHA -> "cinematic mystery scene, film noir style, dramatic lighting"
            GameType.PAOTUAN -> "epic fantasy landscape, detailed environment, atmospheric"
            GameType.HAITANG -> "abstract mystery atmosphere, mysterious mood"
        }

        return """
${scene.narrativeText.take(200)}.

Style: $style.
Quality: ultra high quality, 4K, detailed.
Aspect ratio: 16:9.
Cinematic composition.
""".trimIndent()
    }

    /**
     * 保存图片到缓存
     */
    private fun saveImageToCache(result: ImageGenerationResult, prefix: String): String {
        val fileName = "${prefix}_${UUID.randomUUID().toString().take(8)}.png"
        val file = File(cacheDir, fileName)

        return when {
            result.base64Data != null -> {
                // Base64 保存
                val imageBytes = Base64.decode(result.base64Data, Base64.DEFAULT)
                FileOutputStream(file).use { it.write(imageBytes) }
                file.absolutePath
            }
            result.imageUrl != null -> {
                // URL 下载
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(result.imageUrl)
                    .submit()
                    .get()

                FileOutputStream(file).use { it.write(bitmapToBytes(bitmap)) }
                file.absolutePath
            }
            else -> throw IllegalStateException("图片数据为空")
        }
    }

    /**
     * Bitmap 转 byte 数组
     */
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * 从缓存加载图片
     */
    fun loadCachedImage(clueId: String): Bitmap? {
        val files = cacheDir.listFiles { _, name -> name.startsWith("clue_$clueId") }
        return files?.firstOrNull()?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
