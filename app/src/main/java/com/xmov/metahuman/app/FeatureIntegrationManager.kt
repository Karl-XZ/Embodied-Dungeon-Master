package com.xmov.metahuman.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.xmov.metahuman.app.agent.AgentService
import com.xmov.metahuman.app.emotion.EmotionDetector
import com.xmov.metahuman.app.emotion.GyroscopeMonitor
import com.xmov.metahuman.app.gameplay.AchievementManager
import com.xmov.metahuman.app.gameplay.CharacterManager
import com.xmov.metahuman.app.imagegen.GameImageGenerator
import com.xmov.metahuman.app.imagegen.ImageGenerationClient
import com.xmov.metahuman.app.interaction.InteractionCheckSystem
import com.xmov.metahuman.app.network.NetworkSyncManager
import com.xmov.metahuman.app.pose.PoseCameraManager
import com.xmov.metahuman.app.pose.PoseDetector
import com.xmov.metahuman.app.pose.PunishmentSystem
import com.xmov.metahuman.app.social.SocialManager
import com.xmov.metahuman.app.trpg.*
import com.xmov.metahuman.app.utils.ErrorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 功能集成管理器 - 统一管理所有新功能
 */
class FeatureIntegrationManager(private val context: Context) {

    private val TAG = "FeatureIntegration"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 各功能模块
    private val imageGenerator = GameImageGenerator(context)
    private val imageGenerationClient = ImageGenerationClient()
    private val poseDetector = PoseDetector(context)
    private val poseCameraManager = PoseCameraManager(context, context as androidx.lifecycle.LifecycleOwner)
    private val punishmentSystem = PunishmentSystem()
    private val emotionDetector = EmotionDetector(context)
    private val gyroscopeMonitor = GyroscopeMonitor(context)
    private val interactionCheckSystem = InteractionCheckSystem(context, poseCameraManager)
    private val networkSyncManager = NetworkSyncManager()
    private val agentService = AgentService()
    private val characterManager = CharacterManager(context)
    private val achievementManager = AchievementManager(context)
    private val socialManager = SocialManager(context)

    // 拍照识别状态
    private val _photoRecognitionState = MutableStateFlow<PhotoRecognitionState?>(null)
    val photoRecognitionState: StateFlow<PhotoRecognitionState?> = _photoRecognitionState.asStateFlow()

    private fun ensurePoseDetectorReady(): Boolean {
        val initialized = poseDetector.init()
        if (initialized) {
            poseCameraManager.setPoseDetector(poseDetector)
        }
        return initialized
    }

    private var initialized = false
    private var roomId: String? = null

    /**
     * 设置当前房间ID
     */
    fun setCurrentRoom(roomId: String) {
        this.roomId = roomId
    }

    /**
     * 初始化所有功能
     */
    suspend fun initialize() {
        if (initialized) return

        try {
            // 初始化基础模块
            if (AppSettings.isPoseDetectionEnabled()) {
                poseDetector.init()
                poseCameraManager.setPoseDetector(poseDetector)
            }

            if (AppSettings.isEmotionDetectionEnabled()) {
                gyroscopeMonitor.init()
            }

            agentService.init()

            // 初始化数据库
            achievementManager.initialize()

            initialized = true
            Log.d(TAG, "All features initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize features", e)
        }
    }

    // ========== 图片生成功能 ==========

    /**
     * 设置 API Key
     */
    fun setApiKey(apiKey: String) {
        // GameImageGenerator 直接从 AppSettings 获取 API Key，无需单独设置
    }

    /**
     * 生成场景图片
     */
    suspend fun generateSceneImage(
        scene: StoryNode,
        gameType: GameType
    ): String? {
        if (!AppSettings.hasLlmConfig() || !AppSettings.isImageGenEnabled()) {
            Log.w(TAG, "Image generation disabled or not configured")
            return null
        }

        return imageGenerator.generateSceneImage(scene, gameType)
            .getOrNull()
    }

    /**
     * 生成线索图片
     */
    suspend fun generateClueImage(clue: Clue, gameType: GameType): String? {
        if (!AppSettings.hasLlmConfig() || !AppSettings.isImageGenEnabled()) {
            Log.w(TAG, "Image generation disabled or not configured")
            return null
        }

        return imageGenerator.generateClueImage(clue, gameType)
            .getOrNull()
    }

    /**
     * 生成自定义图片
     */
    suspend fun generateCustomImage(
        description: String,
        gameType: GameType
    ): Result<String?> {
        if (!AppSettings.hasLlmConfig() || !AppSettings.isImageGenEnabled()) {
            Log.w(TAG, "Image generation disabled or not configured")
            return Result.failure(RuntimeException("图片生成未启用或未配置"))
        }

        val apiKey = AppSettings.getLlmApiKey().orEmpty()
        if (apiKey.isEmpty()) {
            Log.w(TAG, "API Key not configured")
            return Result.failure(RuntimeException("API Key 未配置"))
        }

        val prompt = when (gameType) {
            GameType.JUBENSHA -> {
                "剧本杀场景:$description，悬疑推理风格，黑暗氛围，电影感，高细节"
            }
            GameType.PAOTUAN -> {
                "跑团场景:$description，奇幻冒险风格，魔法世界，史诗感，高细节"
            }
            GameType.HAITANG -> {
                "海龟汤场景:$description，悬疑解谜风格，神秘氛围，引人深思，高细节"
            }
        }

        return imageGenerationClient.generateImage(
            apiKey = apiKey,
            baseUrl = AppSettings.getLlmBaseUrl(),
            prompt = prompt,
            model = AppSettings.getImageGenModel(),
            width = 1024,
            height = 768
        ).map { result ->
            // 有的服务只返回 b64_json，没有 url。这里统一把 base64 落到本地缓存，返回文件路径给 UI。
            result.imageUrl
                ?: result.base64Data?.let { saveBase64ImageToCache(it, "custom") }
        }
    }

    private fun saveBase64ImageToCache(base64Data: String, prefix: String): String? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val file = File(context.cacheDir, "${prefix}_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save base64 image", e)
            null
        }
    }

    /**
     * 为游戏生成图片
     */
    suspend fun generateGameImages(
        storyTreeId: String,
        sceneIds: List<String>,
        clueIds: List<String>,
        onProgress: (Int, Int, String) -> Unit
    ): Map<String, String> {
        if (!AppSettings.hasLlmConfig() || !AppSettings.isImageGenEnabled()) {
            Log.w(TAG, "Image generation disabled or not configured")
            return emptyMap()
        }

        val results = mutableMapOf<String, String>()
        var progress = 0
        val total = sceneIds.size + clueIds.size

        // 生成场景图片
        sceneIds.forEach { sceneId ->
            progress++
            onProgress(progress, total, "生成场景: $sceneId")
        }

        // 生成线索图片
        clueIds.forEach { clueId ->
            progress++
            onProgress(progress, total, "生成线索: $clueId")
        }

        return results
    }

    // ========== 动作识别与惩罚系统 ==========

    /**
     * 启动动作识别
     */
    suspend fun startPoseDetection(
        onFailure: (com.xmov.metahuman.app.pose.Action) -> Unit
    ): Boolean {
        if (!AppSettings.isPoseDetectionEnabled()) {
            Log.w(TAG, "Pose detection disabled")
            return false
        }

        if (!ensurePoseDetectorReady()) {
            Log.w(TAG, "Pose detector not ready")
            return false
        }

        poseCameraManager.setPoseDetectionListener { action, confidence ->
            Log.d(TAG, "Pose detected: $action (confidence: $confidence)")
        }

        return poseCameraManager.startCamera()
    }

    /**
     * 停止动作识别
     */
    fun stopPoseDetection() {
        poseCameraManager.stopCamera()
    }

    /**
     * 暂停动作识别
     */
    fun pausePoseDetection() {
        poseCameraManager.pauseDetection()
    }

    /**
     * 恢复动作识别
     */
    fun resumePoseDetection() {
        poseCameraManager.resumeDetection()
    }

    /**
     * 初始化互动检定系统
     */
    suspend fun initInteractionCheck(): Boolean {
        ensurePoseDetectorReady()
        return interactionCheckSystem.init()
    }

    /**
     * 启动互动检定
     */
    suspend fun startInteractionCheck(
        request: InteractionCheckRequest,
        gameEngine: GameEngine
    ): Boolean {
        ensurePoseDetectorReady()
        // 确定检定类型
        val checkType = when {
            request.action.contains("跳", ignoreCase = true) ||
            request.action.contains("跑", ignoreCase = true) ->
                com.xmov.metahuman.app.interaction.InteractionCheckType.POSE

            request.action.contains("转", ignoreCase = true) ||
            request.action.contains("晃", ignoreCase = true) ->
                com.xmov.metahuman.app.interaction.InteractionCheckType.GYROSCOPE

            else ->
                com.xmov.metahuman.app.interaction.InteractionCheckType.HYBRID
        }

        val started = interactionCheckSystem.startInteractionCheck(
            checkType = checkType,
            difficulty = request.difficulty,
            targetAction = request.action
        )

        if (started) {
            gameEngine.startInteractionCheck("local_player", request)
        }

        return started
    }

    /**
     * 获取互动检定状态
     */
    fun getInteractionCheckState() = interactionCheckSystem.checkState.value

    /**
     * 获取互动检定结果
     */
    fun getInteractionCheckResult() = interactionCheckSystem.checkResult.value

    fun getInteractionCheckResultFlow() = interactionCheckSystem.checkResult

    /**
     * 设置互动检定结果监听
     */
    fun setInteractionCheckListener(gameEngine: GameEngine) {
        interactionCheckSystem.checkResult.onEach { result ->
            result?.let {
                gameEngine.handleInteractionCheckResult(
                    checkId = "check_${it.timestamp}",
                    success = it.success,
                    detectedActions = it.actions
                )
            }
        }.launchIn(scope)
    }

    /**
     * 取消互动检定
     */
    fun cancelInteractionCheck() {
        interactionCheckSystem.cancelCheck()
    }

    /**
     * 获取随机惩罚动作
     */
    fun getRandomPunishmentAction(): com.xmov.metahuman.app.pose.Action {
        return punishmentSystem.getRandomPunishmentAction()
    }

    /**
     * 执行惩罚动作
     */
    suspend fun handlePunishmentAction(action: com.xmov.metahuman.app.pose.Action): com.xmov.metahuman.app.pose.PunishmentResult {
        return punishmentSystem.executePunishment(
            action = action,
            failReason = "骰点失败",
            gameType = GameType.PAOTUAN
        )
    }

    /**
     * 检测动作并执行惩罚
     */
    suspend fun detectAndPunish(
        diceResult: DiceRollResult?,
        gameType: GameType
    ): com.xmov.metahuman.app.pose.PunishmentResult? {
        if (!AppSettings.isPunishmentEnabled()) return null

        // 如果骰点失败，执行惩罚
        if (diceResult != null && !diceResult.isSuccess) {
            val poseResult = poseCameraManager.detectedPose.value
            if (poseResult != null) {
                return punishmentSystem.executePunishment(
                    action = poseResult.action,
                    failReason = "骰点失败",
                    gameType = gameType
                )
            }
        }

        return null
    }

    // ========== 情绪检测 ==========

    /**
     * 检测玩家情绪
     */
    suspend fun detectPlayerEmotion(
        currentGameState: String
    ): com.xmov.metahuman.app.emotion.EmotionState? {
        if (!AppSettings.isEmotionDetectionEnabled()) {
            Log.w(TAG, "Emotion detection disabled")
            return null
        }

        // 注意：这里需要从相机获取图像，暂时返回 null
        // 实际使用时需要从 PoseCameraManager 获取图像
        return null
    }

    suspend fun detectPlayerEmotion(
        imageBitmap: Bitmap,
        currentGameState: String
    ): com.xmov.metahuman.app.emotion.EmotionState? {
        if (!AppSettings.isEmotionDetectionEnabled()) {
            Log.w(TAG, "Emotion detection disabled")
            return null
        }

        return emotionDetector.detectEmotion(imageBitmap, currentGameState)
            .getOrNull()
    }

    /**
     * 获取情绪格式化文本（用于 LLM 输入）
     */
    fun formatEmotionForLLM(emotion: com.xmov.metahuman.app.emotion.EmotionState): String {
        return emotionDetector.formatForLLM(emotion)
    }

    /**
     * 启动陀螺仪监测
     */
    fun startGyroscopeMonitoring(
        onAction: (com.xmov.metahuman.app.emotion.GyroAction) -> Unit
    ) {
        if (!AppSettings.isEmotionDetectionEnabled()) return

        gyroscopeMonitor.detectedAction
            .onEach { action ->
                action?.let { onAction(it) }
            }
            .launchIn(scope)

        gyroscopeMonitor.startListening()
    }

    /**
     * 停止陀螺仪监测
     */
    fun stopGyroscopeMonitoring() {
        gyroscopeMonitor.stopListening()
    }

    // ========== 网络同步 ==========

    /**
     * 连接到多人游戏服务器
     */
    fun connectToServer(serverUrl: String): Boolean {
        return networkSyncManager.connect(serverUrl)
    }

    /**
     * 断开连接
     */
    fun disconnectFromServer() {
        networkSyncManager.disconnect()
    }

    /**
     * 同步游戏状态
     */
    fun syncGameState(
        roomId: String,
        gameState: GameState
    ) {
        networkSyncManager.syncGameState(roomId, gameState)
    }

    /**
     * 发送聊天消息
     */
    fun sendChatMessage(roomId: String, playerId: String, message: String) {
        networkSyncManager.sendChatMessage(roomId, playerId, "玩家", message)
    }

    /**
     * 同步骰点结果
     */
    fun syncDiceRoll(roomId: String, playerId: String, playerName: String, result: Int, isSuccess: Boolean) {
        val diceRoll = DiceRoll(
            id = "dice_${System.currentTimeMillis()}",
            playerId = playerId,
            playerName = playerName,
            diceType = DiceType.D20,
            result = result,
            modifiers = emptyList(),
            isSuccess = isSuccess,
            isCritical = result == 20,
            timestamp = System.currentTimeMillis()
        )
        networkSyncManager.sendDiceRoll(roomId, diceRoll)
    }

    /**
     * 设置网络事件回调
     */
    fun setNetworkCallbacks(
        onConnected: (() -> Unit)? = null,
        onDisconnected: (() -> Unit)? = null,
        onGameStateChanged: ((GameState) -> Unit)? = null,
        onPlayerJoined: ((Player) -> Unit)? = null,
        onPlayerLeft: ((Player) -> Unit)? = null,
        onChatMessage: ((com.xmov.metahuman.app.network.ChatMessage) -> Unit)? = null,
        onDiceRolled: ((DiceRoll) -> Unit)? = null
    ) {
        onConnected?.let { networkSyncManager.setOnConnected(it) }
        onDisconnected?.let { networkSyncManager.setOnDisconnected(it) }
        onGameStateChanged?.let { networkSyncManager.setOnGameStateChanged(it) }
        onPlayerJoined?.let { networkSyncManager.setOnPlayerJoined(it) }
        onPlayerLeft?.let { networkSyncManager.setOnPlayerLeft(it) }
        onChatMessage?.let { networkSyncManager.setOnChatMessage(it) }
        onDiceRolled?.let { networkSyncManager.setOnDiceRolled(it) }
    }

    /**
     * 简化的网络事件回调设置（用于 GameActivity）
     */
    fun setOnConnected(callback: () -> Unit) {
        networkSyncManager.setOnConnected(callback)
    }

    fun setOnDisconnected(callback: () -> Unit) {
        networkSyncManager.setOnDisconnected(callback)
    }

    fun setOnGameStateChanged(callback: (String, GameState) -> Unit) {
        networkSyncManager.setOnGameStateChanged { state ->
            callback(roomId ?: "", state)
        }
    }

    fun setOnPlayerJoined(callback: (String, Player) -> Unit) {
        networkSyncManager.setOnPlayerJoined { player ->
            callback(roomId ?: "", player)
        }
    }

    fun setOnPlayerLeft(callback: (String, Player) -> Unit) {
        networkSyncManager.setOnPlayerLeft { player ->
            callback(roomId ?: "", player)
        }
    }

    fun setOnChatMessage(callback: (String, String, String) -> Unit) {
        networkSyncManager.setOnChatMessage { msg ->
            callback(roomId ?: "", msg.playerId, msg.content)
        }
    }

    fun setOnDiceRolled(callback: (String, DiceRoll) -> Unit) {
        networkSyncManager.setOnDiceRolled { roll ->
            callback(roomId ?: "", roll)
        }
    }

    // ========== Agent 服务端 ==========

    /**
     * 解析剧本文本
     */
    suspend fun parseScriptText(
        scriptText: String,
        gameType: GameType
    ): StoryTree? {
        return agentService.parseScriptText(scriptText, gameType)
            .getOrNull()
    }

    /**
     * 自动生成剧情树
     */
    suspend fun generateStoryTree(
        theme: String,
        gameType: GameType,
        sceneCount: Int = 5,
        complexity: com.xmov.metahuman.app.agent.Complexity = com.xmov.metahuman.app.agent.Complexity.MEDIUM
    ): StoryTree? {
        return agentService.generateStoryTree(theme, gameType, sceneCount, complexity)
            .getOrNull()
    }

    /**
     * 生成 Fail-Safe 策略
     */
    suspend fun generateFailSafePolicies(
        storyTree: StoryTree
    ): StoryTree? {
        return agentService.generateFailSafePolicies(storyTree)
            .getOrNull()
    }

    // ========== 角色系统 ==========

    /**
     * 创建角色
     */
    suspend fun createCharacter(
        playerId: String,
        name: String,
        role: String,
        avatar: String?
    ): com.xmov.metahuman.app.gameplay.Character {
        return characterManager.createCharacter(playerId, name, role, avatar)
    }

    /**
     * 获取玩家角色
     */
    fun getPlayerCharacters(playerId: String) = characterManager.getPlayerCharacters(playerId)

    /**
     * 添加经验
     */
    suspend fun addExperience(characterId: String, amount: Int) {
        characterManager.addExperience(characterId, amount)
    }

    // ========== 成就系统 ==========

    /**
     * 更新成就进度
     */
    suspend fun updateAchievementProgress(
        playerId: String,
        achievementId: String,
        increment: Int = 1
    ) {
        achievementManager.updateProgress(playerId, achievementId, increment)
    }

    /**
     * 获取玩家成就
     */
    fun getPlayerAchievements(playerId: String) = achievementManager.getPlayerAchievements(playerId)

    /**
     * 获取所有成就
     */
    fun getAllAchievements() = achievementManager.getAllAchievements()

    // ========== 社交功能 ==========

    /**
     * 发送好友请求
     */
    suspend fun sendFriendRequest(
        fromPlayerId: String,
        fromPlayerName: String,
        toPlayerId: String,
        message: String? = null
    ) {
        socialManager.sendFriendRequest(fromPlayerId, fromPlayerName, toPlayerId, message)
    }

    /**
     * 获取好友列表
     */
    fun getFriends(playerId: String) = socialManager.getFriends(playerId)

    /**
     * 收藏房间
     */
    suspend fun favoriteRoom(
        playerId: String,
        roomId: String,
        roomName: String,
        gameType: String,
        hostName: String
    ) {
        socialManager.favoriteRoom(playerId, roomId, roomName, gameType, hostName)
    }

    /**
     * 获取收藏的房间
     */
    fun getFavoriteRooms(playerId: String) = socialManager.getFavoriteRooms(playerId)

    /**
     * 记录游戏
     */
    suspend fun recordGame(
        playerId: String,
        roomId: String,
        roomName: String,
        gameType: String,
        duration: Long,
        playerCount: Int,
        result: String,
        score: Int,
        xpEarned: Int,
        achievements: List<String> = emptyList(),
        review: String? = null
    ) {
        socialManager.recordGame(
            playerId, roomId, roomName, gameType,
            duration, playerCount, result, score, xpEarned, achievements, review
        )
    }

    /**
     * 生成分享内容
     */
    fun generateShareContent(record: com.xmov.metahuman.app.social.GameRecord): String {
        return socialManager.generateShareContent(record)
    }

    /**
     * 获取游戏记录
     */
    fun getGameRecords(playerId: String) = socialManager.getGameRecords(playerId)

    // ========== 拍照识别功能 ==========

    /**
     * 启动拍照识别
     */
    suspend fun startPhotoRecognition(
        target: String,
        gameEngine: GameEngine
    ): Boolean {
        ensurePoseDetectorReady()
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val error = "缺少摄像头权限"
            _photoRecognitionState.value = PhotoRecognitionState.Error(error)
            if (AppSettings.isDebugModeEnabled()) {
                ErrorDialog.showError(
                    context,
                    title = "拍照识别失败",
                    errorMessage = error
                )
            }
            return false
        }

        // 启动相机
        val cameraStarted = poseCameraManager.startCamera()
        if (!cameraStarted) {
            val error = "相机启动失败"
            _photoRecognitionState.value = PhotoRecognitionState.Error(error)
            if (AppSettings.isDebugModeEnabled()) {
                ErrorDialog.showError(
                    context,
                    title = "拍照识别失败",
                    errorMessage = error
                )
            }
            return false
        }

        _photoRecognitionState.value = PhotoRecognitionState.Capturing(
            target = target,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Started photo recognition for: $target")
        return true
    }

    /**
     * 捕获当前帧用于识别
     */
    suspend fun captureAndRecognize(
        target: String
    ): PhotoRecognitionResult? {
        // 获取当前帧（假设相机已经启动）
        val pose = poseCameraManager.detectedPose.value

        // 这里可以集成其他识别逻辑，比如物体识别、OCR 等
        // 暂时使用姿态检测结果作为识别结果
        val confidence = pose?.confidence ?: 0f
        val detectedAction = pose?.action?.name ?: "未知"

        // 模拟识别结果（实际需要接入视觉识别 API）
        val isMatch = when {
            target.contains("招", ignoreCase = true) && detectedAction == "RAISE_HANDS" -> true
            target.contains("蹲", ignoreCase = true) && detectedAction == "SQUAT" -> true
            target.contains("站", ignoreCase = true) && detectedAction == "IDLE" -> true
            target.contains("跳", ignoreCase = true) && detectedAction == "JUMP" -> true
            else -> confidence > 0.7f  // 默认认为检测到就是匹配
        }

        val result = PhotoRecognitionResult(
            target = target,
            detectedObject = detectedAction,
            isMatch = isMatch,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )

        _photoRecognitionState.value = PhotoRecognitionState.Completed(result)
        Log.d(TAG, "Photo recognition result: $result")

        return result
    }

    /**
     * 停止拍照识别
     */
    fun stopPhotoRecognition() {
        poseCameraManager.stopCamera()
        _photoRecognitionState.value = null
        Log.d(TAG, "Stopped photo recognition")
    }

    /**
     * 获取拍照识别状态
     */
    fun getPhotoRecognitionState() = _photoRecognitionState.value

    /**
     * 设置拍照识别监听器
     */
    fun setPhotoRecognitionListener(callback: (PhotoRecognitionResult) -> Unit) {
        scope.launch {
            _photoRecognitionState.collect { state ->
                if (state is PhotoRecognitionState.Completed) {
                    callback(state.result)
                }
            }
        }
    }

    /**
     * 释放所有资源
     */
    fun destroy() {
        poseDetector.destroy()
        Log.d(TAG, "FeatureIntegrationManager destroyed")
    }
}

/**
 * 拍照识别状态
 */
sealed class PhotoRecognitionState {
    data object Idle : PhotoRecognitionState()
    data class Capturing(
        val target: String,
        val timestamp: Long
    ) : PhotoRecognitionState()
    data class Completed(
        val result: PhotoRecognitionResult
    ) : PhotoRecognitionState()
    data class Error(
        val message: String
    ) : PhotoRecognitionState()
}

/**
 * 拍照识别结果
 */
data class PhotoRecognitionResult(
    val target: String,
    val detectedObject: String,
    val isMatch: Boolean,
    val confidence: Float,
    val timestamp: Long
)
