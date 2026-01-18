package com.xmov.metahuman.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xmov.metahuman.app.emotion.EmotionState
import com.xmov.metahuman.app.imagegen.SceneImageType
import com.xmov.metahuman.app.pose.Action as PoseAction
import com.xmov.metahuman.app.trpg.*
import com.xmov.metahuman.app.utils.ErrorDialog
import com.xmov.metahuman.sdk.IAvatarListener
import com.xmov.metahuman.sdk.IXmovAvatar
import com.xmov.metahuman.sdk.data.InitConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 游戏主界面 - 集成数字人 AI 和 TRPG 功能
 */
class GameActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_AUDIO_PERMISSION = 201
        private const val REQUEST_CODE_CAMERA_PERMISSION = 202

        fun start(context: android.content.Context, roomId: String, playerId: String, isHost: Boolean) {
            val intent = Intent(context, GameActivity::class.java)
            intent.putExtra("room_id", roomId)
            intent.putExtra("player_id", playerId)
            intent.putExtra("is_host", isHost)
            context.startActivity(intent)
        }
    }

    private val TAG = "GameActivity"
    private lateinit var xmovAvatar: IXmovAvatar

    private var roomId: String = ""
    private var playerId: String = ""
    private var isHost: Boolean = false
    private var appId: String? = null
    private var appSecret: String? = null

    private val roomManager = RoomManagerProvider.instance
    private var gameEngine: GameEngine? = null

    // 新功能集成管理器
    private val featureIntegrationManager by lazy {
        FeatureIntegrationManager(this)
    }

    // 语音识别
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // 玩家情绪状态
    private var currentEmotionState: EmotionState? = null

    private var pendingInteractionCheckRequest: InteractionCheckRequest? = null
    private var pendingPhotoRecognitionRequest: PhotoRecognitionRequest? = null

    // UI组件
    private lateinit var avatarLayout: View
    private lateinit var tvSceneTitle: TextView
    private lateinit var tvSceneDescription: TextView
    private lateinit var ivSceneImage: android.widget.ImageView
    private lateinit var etPlayerInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnVoiceInput: Button
    private lateinit var btnActionSearch: Button
    private lateinit var btnActionTalk: Button
    private lateinit var btnActionInvestigate: Button
    private lateinit var btnActionDice: Button
    private lateinit var btnClues: Button
    private lateinit var btnPlayers: Button
    private lateinit var btnEndGame: Button
    private lateinit var tvClues: TextView

    // 对话日志
    private lateinit var rvChatLog: RecyclerView
    private lateinit var btnToggleLog: Button
    private lateinit var chatLogAdapter: ChatLogAdapter
    private var isLogVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        handleIntentData()
        initUI()
        initAvatar()
        initGame()
    }

    private fun handleIntentData() {
        val extras = intent.extras
        if (extras != null) {
            roomId = extras.getString("room_id") ?: ""
            playerId = extras.getString("player_id") ?: ""
            isHost = extras.getBoolean("is_host", false)
            appId = extras.getString("app_id")
            appSecret = extras.getString("app_secret")
        }
    }

    private fun initUI() {
        avatarLayout = findViewById(R.id.avatar_layout)
        tvSceneTitle = findViewById(R.id.tv_scene_title)
        tvSceneDescription = findViewById(R.id.tv_scene_description)
        ivSceneImage = findViewById(R.id.iv_scene_image)
        etPlayerInput = findViewById(R.id.et_player_input)
        btnSend = findViewById(R.id.btn_send)
        btnVoiceInput = findViewById(R.id.btn_voice_input)
        btnActionSearch = findViewById(R.id.btn_action_search)
        btnActionTalk = findViewById(R.id.btn_action_talk)
        btnActionInvestigate = findViewById(R.id.btn_action_investigate)
        btnActionDice = findViewById(R.id.btn_action_dice)
        btnClues = findViewById(R.id.btn_clues)
        btnPlayers = findViewById(R.id.btn_players)
        btnEndGame = findViewById(R.id.btn_end_game)
        tvClues = findViewById(R.id.tv_clues)
        rvChatLog = findViewById(R.id.rv_chat_log)
        btnToggleLog = findViewById(R.id.btn_toggle_log)

        // 只有房主显示结束游戏按钮
        btnEndGame.visibility = if (isHost) View.VISIBLE else View.GONE

        // 初始化对话日志
        chatLogAdapter = ChatLogAdapter()
        rvChatLog.layoutManager = LinearLayoutManager(this)
        rvChatLog.adapter = chatLogAdapter

        // 按钮监听
        btnSend.setOnClickListener { handleSendInput() }
        btnVoiceInput.setOnClickListener { handleVoiceInput() }
        btnActionSearch.setOnClickListener { handleAction(ActionType.SEARCH) }
        btnActionTalk.setOnClickListener { handleAction(ActionType.TALK) }
        btnActionInvestigate.setOnClickListener { handleAction(ActionType.INVESTIGATE) }
        btnActionDice.setOnClickListener { handleAction(ActionType.DICE_ROLL) }
        btnClues.setOnClickListener { showCluesDialog() }
        btnPlayers.setOnClickListener { showPlayersDialog() }
        btnEndGame.setOnClickListener { confirmEndGame() }

        // 切换对话日志显示
        btnToggleLog.setOnClickListener {
            isLogVisible = !isLogVisible
            rvChatLog.visibility = if (isLogVisible) View.VISIBLE else View.GONE
            btnToggleLog.text = if (isLogVisible) "📝 隐藏记录" else "📝 对话记录"
        }

        // 初始化语音识别
        initSpeechRecognizer()
    }

    private fun initAvatar() {
        if (appId.isNullOrEmpty() || appSecret.isNullOrEmpty()) {
            Log.w(TAG, "Xmov appId or appSecret is null, skipping avatar initialization")
            return
        }

        val initConfig = InitConfig()
        initConfig.gatewayServer = "https://nebula-agent.xingyun3d.com/user/v1/ttsa/session"
        initConfig.appId = appId
        initConfig.appSecret = appSecret

        xmovAvatar = IXmovAvatar.newInstance()
        xmovAvatar.init(this, avatarLayout as android.widget.FrameLayout, initConfig, object : IAvatarListener {
            override fun onInitEvent(code: Int, message: String?) {
                // 初始化完成
                updateScene()
            }

            override fun onWidgetEvent(widgetData: com.xmov.metahuman.sdk.impl.data.IRawEventFrameData?) {}
            override fun onNetworkInfo(sdkNetworkInfo: com.xmov.metahuman.sdk.data.SDKNetworkInfo?) {}
            override fun onMessage(sdkMessage: com.xmov.metahuman.sdk.data.SDKMessage?) {}
            override fun onStateChange(state: String?) {}
            override fun onStatusChange(status: com.xmov.metahuman.sdk.data.SDKStatus?) {}
            override fun onStateRenderChange(state: String?, duration: Long) {}
            override fun onVoiceStateChange(status: String?) {}
            override fun onDebugInfo(debugInfo: JSONObject) {}
            override fun onReconnectEvent(code: Int, message: String?) {}
            override fun onOfflineEvent() {}
        })
    }

    private fun initGame() {
        gameEngine = roomManager.getGameEngine(roomId)

        // 初始化新功能
        initFeatures()

        // 监听互动检定结果（在 gameEngine 初始化后）
        gameEngine?.let {
            featureIntegrationManager.setInteractionCheckListener(it)
        }

        // 监听游戏状态变化
        lifecycleScope.launch {
            gameEngine?.gameState?.collect { state ->
                updateScene()
                updateUI(state)
            }
        }
    }

    private fun initFeatures() {
        // 设置房间ID
        featureIntegrationManager.setCurrentRoom(roomId)

        // 初始化图片生成
        if (AppSettings.isImageGenEnabled()) {
            lifecycleScope.launch {
                featureIntegrationManager.setApiKey(AppSettings.getLlmApiKey() ?: "")
            }
        }

        // 初始化动作识别和惩罚系统
        if (AppSettings.isPoseDetectionEnabled() && AppSettings.isPunishmentEnabled()) {
            lifecycleScope.launch {
                featureIntegrationManager.startPoseDetection(
                    onFailure = { action ->
                        lifecycleScope.launch {
                            featureIntegrationManager.handlePunishmentAction(action)
                        }
                    }
                )
            }
        }

        // 初始化互动检定系统
        lifecycleScope.launch {
            val initialized = featureIntegrationManager.initInteractionCheck()
            Log.d("GameActivity", "Interaction check initialized: $initialized")
        }

        // 初始化网络多人同步
        lifecycleScope.launch {
            featureIntegrationManager.connectToServer(AppSettings.getSocketServerUrl())
        }

        // 设置网络同步回调
        featureIntegrationManager.setOnGameStateChanged { roomId, state ->
            if (roomId == this.roomId) {
                runOnUiThread {
                    updateScene()
                    updateUI(state)
                }
            }
        }

        featureIntegrationManager.setOnPlayerJoined { roomId, player ->
            if (roomId == this.roomId) {
                addChatLog(ChatLog(
                    speaker = "系统",
                    content = "${player.name} 加入了游戏",
                    type = LogType.SYSTEM
                ))
            }
        }

        featureIntegrationManager.setOnChatMessage { roomId, playerId, message ->
            if (roomId == this.roomId && playerId != this.playerId) {
                addChatLog(ChatLog(
                    speaker = "玩家",
                    content = message,
                    type = LogType.PLAYER
                ))
            }
        }
    }

    private fun updateScene() {
        // 这个方法里会更新 UI / 使用 Glide，必须在主线程执行
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { updateScene() }
            return
        }

        val currentScene = gameEngine?.getCurrentScene()
        if (currentScene != null) {
            tvSceneTitle.text = currentScene.description
            tvSceneDescription.text = currentScene.narrativeText

            // 添加到对话日志
            addChatLog(ChatLog(
                speaker = "DM",
                content = currentScene.narrativeText,
                type = LogType.DM
            ))

            // 数字人朗读场景描述
            if (::xmovAvatar.isInitialized) {
                xmovAvatar.speak(currentScene.narrativeText, isStart = true, isEnd = true)
            }

            // 加载场景插画或自动生成
            if (currentScene.imageUrl != null) {
                Glide.with(this).load(currentScene.imageUrl).into(ivSceneImage)
                ivSceneImage.visibility = View.VISIBLE
            } else if (AppSettings.isImageGenEnabled()) {
                // 自动生成场景图片
                lifecycleScope.launch {
                    val imageUrl = withContext(Dispatchers.IO) {
                        featureIntegrationManager.generateSceneImage(
                            scene = currentScene,
                            gameType = gameEngine?.gameType ?: GameType.PAOTUAN
                        )
                    }
                    imageUrl?.let {
                        Glide.with(this@GameActivity).load(it).into(ivSceneImage)
                        ivSceneImage.visibility = View.VISIBLE
                    }
                }
            } else {
                ivSceneImage.visibility = View.GONE
            }

            // 检测玩家情绪状态（如果启用）
            if (AppSettings.isEmotionDetectionEnabled()) {
                detectPlayerEmotion()
            }
        }
    }

    private fun updateUI(state: GameState) {
        // 更新线索显示
        val playerClues = state.players.find { it.id == playerId }?.ownedClues ?: emptyList()
        val cluesText = "已获线索: ${playerClues.size}\n" +
                playerClues.joinToString("\n") { "• ${it.name}" }
        tvClues.text = cluesText
    }

    private fun handleSendInput() {
        val input = etPlayerInput.text.toString().trim()
        if (input.isEmpty()) return

        // 检查是否是互动检定请求（仅跑团模式支持）
        val engine = gameEngine ?: return
        val interactionCheckRequest = engine.detectInteractionCheckRequest(input)

        if (interactionCheckRequest != null) {
            // 处理互动检定
            handleInteractionCheckRequest(interactionCheckRequest)
            etPlayerInput.text.clear()
            return
        }

        // 检查是否是图片生成请求
        val imageGenRequest = engine.detectImageGenerationRequest(input)
        if (imageGenRequest != null) {
            handleImageGenerationRequest(imageGenRequest, engine)
            etPlayerInput.text.clear()
            return
        }

        // 检查是否是拍照识别请求
        val photoRecRequest = engine.detectPhotoRecognitionRequest(input)
        if (photoRecRequest != null) {
            handlePhotoRecognitionRequest(photoRecRequest, engine)
            etPlayerInput.text.clear()
            return
        }

        // 同步输入到网络
        featureIntegrationManager.sendChatMessage(roomId, playerId, input)

        // 添加玩家发言到日志
        addChatLog(ChatLog(
            speaker = "玩家",
            content = input,
            type = LogType.PLAYER
        ))

        lifecycleScope.launch {
            // handlePlayerInput 里会做 LLM / 网络请求；必须放到 IO
            val result = withContext(Dispatchers.IO) {
                roomManager.handlePlayerInput(
                    roomId, playerId, ActionType.TALK, input
                )
            }

            result.getOrNull()?.let { actionResult ->
                // 数字人回应
                if (actionResult.narration != null && ::xmovAvatar.isInitialized) {
                    xmovAvatar.speak(actionResult.narration, isStart = true, isEnd = true)
                    addChatLog(ChatLog(
                        speaker = "DM",
                        content = actionResult.narration,
                        type = LogType.DM
                    ))
                }

                // 显示获得线索
                if (actionResult.cluesReceived.isNotEmpty()) {
                    val clueText = "获得线索: " + actionResult.cluesReceived.joinToString(", ") { it.name }
                    addChatLog(ChatLog(
                        speaker = "系统",
                        content = clueText,
                        type = LogType.SYSTEM
                    ))
                    showCluesReceived(actionResult.cluesReceived)

                    // 自动生成线索图片
                    if (AppSettings.isImageGenEnabled()) {
                        actionResult.cluesReceived.forEach { clue ->
                            lifecycleScope.launch {
                                val imageUrl = withContext(Dispatchers.IO) {
                                    featureIntegrationManager.generateClueImage(
                                        clue,
                                        gameEngine?.gameType ?: GameType.PAOTUAN
                                    )
                                }
                                imageUrl?.let {
                                    // 这里可以把图片显示到某个 UI 里（当前 demo 暂未展示）
                                }
                            }
                        }
                    }
                }

                // 显示骰点结果
                if (actionResult.diceResult != null) {
                    val diceText = "骰点: ${actionResult.diceResult.value} - ${if (actionResult.diceResult.isSuccess) "成功" else "失败"}"
                    addChatLog(ChatLog(
                        speaker = "动作",
                        content = diceText,
                        type = LogType.ACTION
                    ))
                    showDiceResult(actionResult.diceResult)

                    // 同步骰点结果到网络
                    featureIntegrationManager.syncDiceRoll(
                        roomId, playerId, "Player", actionResult.diceResult.value, actionResult.diceResult.isSuccess
                    )

                    // 骰点失败触发惩罚系统
                    if (AppSettings.isPunishmentEnabled() && !actionResult.diceResult.isSuccess) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val action = featureIntegrationManager.getRandomPunishmentAction()
                                featureIntegrationManager.handlePunishmentAction(action)
                            }
                        }
                    }
                }
            }

            etPlayerInput.text.clear()
        }
    }

    private fun handleAction(actionType: ActionType) {
        val actionName = when (actionType) {
            ActionType.SEARCH -> "搜索"
            ActionType.TALK -> "对话"
            ActionType.INVESTIGATE -> "调查"
            ActionType.DICE_ROLL -> "骰点"
            else -> actionType.name
        }

        addChatLog(ChatLog(
            speaker = "动作",
            content = "执行动作: $actionName",
            type = LogType.ACTION
        ))

        lifecycleScope.launch {
            val result = roomManager.handlePlayerInput(
                roomId, playerId, actionType, ""
            )

            result.getOrNull()?.let { actionResult ->
                if (actionResult.narration != null) {
                    if (::xmovAvatar.isInitialized) {
                        xmovAvatar.speak(actionResult.narration, isStart = true, isEnd = true)
                    }
                    addChatLog(ChatLog(
                        speaker = "DM",
                        content = actionResult.narration,
                        type = LogType.DM
                    ))
                }

                if (actionResult.cluesReceived.isNotEmpty()) {
                    val clueText = "获得线索: " + actionResult.cluesReceived.joinToString(", ") { it.name }
                    addChatLog(ChatLog(
                        speaker = "系统",
                        content = clueText,
                        type = LogType.SYSTEM
                    ))
                    showCluesReceived(actionResult.cluesReceived)
                }

                if (actionResult.diceResult != null) {
                    val diceText = "骰点结果: ${actionResult.diceResult.value} - ${if (actionResult.diceResult.isSuccess) "成功" else "失败"}"
                    addChatLog(ChatLog(
                        speaker = "动作",
                        content = diceText,
                        type = LogType.ACTION
                    ))
                    showDiceResult(actionResult.diceResult)
                }
            }
        }
    }

    private fun showCluesReceived(clues: List<Clue>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("获得新线索！")
            .setMessage(clues.joinToString("\n\n") { "${it.name}\n${it.description}" })
            .setPositiveButton("确定", null)
            .create()
        dialog.show()
    }

    private fun showDiceResult(result: DiceRollResult) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("骰点结果")
            .setMessage("点数: ${result.value}\n" +
                    "结果: ${if (result.isSuccess) "成功 ✓" else "失败 ✗"}\n" +
                    if (result.isCritical) "大成功/大失败！🎉" else "")
            .setPositiveButton("确定", null)
            .create()
        dialog.show()
    }

    private fun showCluesDialog() {
        val state = gameEngine?.gameState?.value ?: return
        val playerClues = state.players.find { it.id == playerId }?.ownedClues ?: emptyList()

        val dialog = AlertDialog.Builder(this)
            .setTitle("我的线索 (${playerClues.size})")
            .setMessage(if (playerClues.isEmpty()) "暂无线索" else
                playerClues.joinToString("\n\n") { clue ->
                    "【${clue.name}】(${clue.importance})\n${clue.description}"
                })
            .setPositiveButton("确定", null)
            .create()
        dialog.show()
    }

    private fun showPlayersDialog() {
        val state = gameEngine?.gameState?.value ?: return

        val dialog = AlertDialog.Builder(this)
            .setTitle("房间成员 (${state.players.size})")
            .setMessage(state.players.joinToString("\n") { player ->
                "${player.name} ${if (player.isHost) "[房主]" else ""} - " +
                        if (player.isOnline) "😊在线" else "😶离线"
            })
            .setPositiveButton("确定", null)
            .create()
        dialog.show()
    }

    private fun confirmEndGame() {
        AlertDialog.Builder(this)
            .setTitle("结束游戏")
            .setMessage("确定要结束游戏并生成复盘报告吗？")
            .setPositiveButton("确定") { _, _ ->
                endGame()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun endGame() {
        lifecycleScope.launch {
            val result = roomManager.endGame(roomId)
            result.fold(
                onSuccess = { review ->
                    showGameReview(review)
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@GameActivity,
                        "结束游戏失败: " + error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // 断开网络同步
            featureIntegrationManager.disconnectFromServer()

            // 停止动作识别
            featureIntegrationManager.stopPoseDetection()
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                btnVoiceInput.text = "🔴"
                Toast.makeText(this@GameActivity, "开始语音输入...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                btnVoiceInput.text = "🎤"
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到语音"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    else -> "未知错误"
                }
                Toast.makeText(this@GameActivity, "语音识别错误: $errorMessage", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                btnVoiceInput.text = "🎤"

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    etPlayerInput.setText(recognizedText)
                    Toast.makeText(this@GameActivity, "识别成功", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun handleVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_AUDIO_PERMISSION
            )
            return
        }

        if (isListening) {
            speechRecognizer?.stopListening()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun requiresCameraForInteraction(action: String): Boolean {
        val isGyro = action.contains("转", ignoreCase = true) ||
            action.contains("晃", ignoreCase = true)
        return !isGyro
    }

    /**
     * 处理互动检定请求
     */
    private fun handleInteractionCheckRequest(request: InteractionCheckRequest) {
        val engine = gameEngine ?: return

        addChatLog(ChatLog(
            speaker = "系统",
            content = "互动检定请求：${request.action} (难度: ${request.difficulty})",
            type = LogType.ACTION
        ))

        lifecycleScope.launch {
            if (requiresCameraForInteraction(request.action) &&
                ContextCompat.checkSelfPermission(
                    this@GameActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingInteractionCheckRequest = request
                ActivityCompat.requestPermissions(
                    this@GameActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CODE_CAMERA_PERMISSION
                )
                return@launch
            }

            // 启动互动检定
            val started = featureIntegrationManager.startInteractionCheck(request, engine)

            if (started) {
                runOnUiThread {
                    showInteractionCheckDialog(request)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this@GameActivity,
                        "互动检定启动失败，将使用骰子判定",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 显示互动检定对话框
     */
    private fun showInteractionCheckDialog(request: InteractionCheckRequest) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("🎲 互动检定")
            .setMessage("请执行动作：${request.action}\n\n难度等级：${"★".repeat(request.difficulty)}\n\n系统将识别你的动作...")
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                featureIntegrationManager.cancelInteractionCheck()
                gameEngine?.cancelInteractionCheck()
            }
            .create()

        dialog.show()

        // 监听检定结果
        lifecycleScope.launch {
            val result = featureIntegrationManager.getInteractionCheckResultFlow()
                .filterNotNull()
                .first()
            runOnUiThread {
                dialog.dismiss()
                showInteractionCheckResult(result)
            }
        }
    }

    /**
     * 显示互动检定结果
     */
    private fun showInteractionCheckResult(result: com.xmov.metahuman.app.interaction.CheckResult) {
        val engine = gameEngine ?: return

        lifecycleScope.launch {
            // 获取待处理的检定请求
            val pendingCheck = engine.pendingInteractionCheck.value ?: return@launch

            // 使用 LLM 格式化结果
            val formattedResult = withContext(Dispatchers.IO) {
                engine.formatInteractionCheckResult(
                    action = pendingCheck.action,
                    success = result.success,
                    detectedActions = result.actions
                ).getOrNull() ?: (if (result.success) "检定成功！" else "检定失败。")
            }

            runOnUiThread {
                val title = if (result.success) "✓ 检定成功" else "✗ 检定失败"
                val message = buildString {
                    append("检定动作：${pendingCheck.action}\n")
                    append("检测到的动作：${result.actions.joinToString(", ")}\n")
                    append("DM：$formattedResult")
                }

                AlertDialog.Builder(this@GameActivity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ ->
                        addChatLog(ChatLog(
                            speaker = "系统",
                            content = formattedResult,
                            type = LogType.ACTION
                        ))

                        // 数字人朗读结果
                        if (::xmovAvatar.isInitialized) {
                            xmovAvatar.speak(formattedResult, isStart = true, isEnd = true)
                        }
                    }
                    .show()
            }
        }
    }

    /**
     * 处理图片生成请求
     */
    private fun handleImageGenerationRequest(request: ImageGenerationRequest, engine: com.xmov.metahuman.app.trpg.GameEngine) {
        addChatLog(ChatLog(
            speaker = "系统",
            content = "正在生成图片...",
            type = LogType.ACTION
        ))

        lifecycleScope.launch {
            // 这里会涉及 LLM / 网络请求：放到 IO，避免 NetworkOnMainThreadException
            val finalPrompt = withContext(Dispatchers.IO) {
                val processedPromptResult = engine.processImagePromptWithLLM(request.fullInput)
                processedPromptResult.getOrNull() ?: request.description
            }

            val imageUrl = withContext(Dispatchers.IO) {
                featureIntegrationManager.generateCustomImage(finalPrompt, engine.gameType)
            }

            imageUrl?.let { url ->
                // 使用 LLM 格式化结果
                val formattedResult = withContext(Dispatchers.IO) {
                    engine.formatImageGenerationResult(url, request.fullInput)
                }.getOrNull()
                    ?: "图片已生成。"

                runOnUiThread {
                    Glide.with(this@GameActivity).load(url).into(ivSceneImage)
                    ivSceneImage.visibility = View.VISIBLE

                    addChatLog(ChatLog(
                        speaker = "系统",
                        content = "✓ $formattedResult",
                        type = LogType.ACTION
                    ))

                    // 数字人朗读结果
                    if (::xmovAvatar.isInitialized) {
                        xmovAvatar.speak(formattedResult, isStart = true, isEnd = true)
                    }
                }
            } ?: runOnUiThread {
                Toast.makeText(
                    this@GameActivity,
                    "图片生成失败，请检查配置",
                    Toast.LENGTH_SHORT
                ).show()
                addChatLog(ChatLog(
                    speaker = "系统",
                    content = "✗ 图片生成失败",
                    type = LogType.SYSTEM
                ))
            }
        }
    }

    /**
     * 处理拍照识别请求
     */
    private fun handlePhotoRecognitionRequest(request: PhotoRecognitionRequest, engine: com.xmov.metahuman.app.trpg.GameEngine) {
        addChatLog(ChatLog(
            speaker = "系统",
            content = "正在识别：${request.target}",
            type = LogType.ACTION
        ))

        lifecycleScope.launch {
            // 检查并请求摄像头权限
            if (ContextCompat.checkSelfPermission(
                    this@GameActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread {
                    ActivityCompat.requestPermissions(
                        this@GameActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CODE_CAMERA_PERMISSION
                    )
                }
                pendingPhotoRecognitionRequest = request
                return@launch
            }

            // 启动拍照识别
            val started = featureIntegrationManager.startPhotoRecognition(request.target, engine)

            if (started) {
                runOnUiThread {
                    showPhotoRecognitionDialog(request)
                }
            } else {
                // 开发模式下显示错误弹窗（FeatureIntegrationManager 内部已处理）
                // 普通模式下显示 Toast
                runOnUiThread {
                    Toast.makeText(
                        this@GameActivity,
                        "拍照识别启动失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 显示拍照识别对话框
     */
    private fun showPhotoRecognitionDialog(request: PhotoRecognitionRequest) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("📸 拍照识别")
            .setMessage("请将识别对象（${request.target}）对准摄像头\n\n系统将自动拍照并识别...")
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                featureIntegrationManager.stopPhotoRecognition()
            }
            .create()

        dialog.show()

        // 延迟后执行识别
        lifecycleScope.launch {
            delay(3000)  // 等待3秒让用户对准

            val result = withContext(Dispatchers.IO) {
                featureIntegrationManager.captureAndRecognize(request.target)
            }

            runOnUiThread {
                dialog.dismiss()
                showPhotoRecognitionResult(result)
            }
        }
    }

    /**
     * 显示拍照识别结果
     */
    private fun showPhotoRecognitionResult(result: PhotoRecognitionResult?) {
        featureIntegrationManager.stopPhotoRecognition()
        result ?: return

        lifecycleScope.launch {
            // 使用 LLM 格式化结果
            val engine = gameEngine ?: return@launch
            val formattedResult = withContext(Dispatchers.IO) {
                engine.formatPhotoRecognitionResult(
                    target = result.target,
                    detectedObject = result.detectedObject,
                    isMatch = result.isMatch,
                    confidence = result.confidence
                ).getOrNull() ?: (if (result.isMatch) "识别成功！" else "识别失败。")
            }

            runOnUiThread {
                val title = if (result.isMatch) "✓ 识别成功" else "✗ 识别失败"
                val message = buildString {
                    append("识别目标：${result.target}\n")
                    append("检测到：${result.detectedObject}\n")
                    append("置信度：${String.format("%.1f%%", result.confidence * 100)}\n")
                    append("DM：$formattedResult")
                }

                AlertDialog.Builder(this@GameActivity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ ->
                        // 显示格式化后的结果
                        addChatLog(ChatLog(
                            speaker = "系统",
                            content = formattedResult,
                            type = LogType.SYSTEM
                        ))

                        // 数字人朗读结果
                        if (::xmovAvatar.isInitialized) {
                            xmovAvatar.speak(formattedResult, isStart = true, isEnd = true)
                        }

                        // 如果识别成功，可以作为线索记录
                        if (result.isMatch) {
                            addChatLog(ChatLog(
                                speaker = "系统",
                                content = "已确认：${result.detectedObject}",
                                type = LogType.SYSTEM
                            ))
                        }
                    }
                    .show()
            }
        }
    }

    private fun addChatLog(log: ChatLog) {
        runOnUiThread {
            chatLogAdapter.addLog(log)
            rvChatLog.scrollToPosition(chatLogAdapter.itemCount - 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleVoiceInput()
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val pendingInteraction = pendingInteractionCheckRequest
                pendingInteractionCheckRequest = null
                if (pendingInteraction != null) {
                    handleInteractionCheckRequest(pendingInteraction)
                    return
                }

                val pendingPhoto = pendingPhotoRecognitionRequest
                pendingPhotoRecognitionRequest = null
                if (pendingPhoto != null) {
                    val engine = gameEngine ?: return
                    handlePhotoRecognitionRequest(pendingPhoto, engine)
                    return
                }

                Toast.makeText(this, "摄像头权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "互动检定需要摄像头权限", Toast.LENGTH_SHORT).show()
                featureIntegrationManager.cancelInteractionCheck()
                gameEngine?.cancelInteractionCheck()
            }
        }
    }

    private fun showGameReview(review: GameReview) {
        val reviewText = buildString {
            append("=== 游戏复盘 ===\n\n")
            append("游戏类型: ${review.gameType}\n")
            append("游戏时长: ${review.duration / 60000} 分钟\n\n")
            append("=== 玩家表现 ===\n")
            review.players.forEach { player ->
                append("\n${player.playerName}:\n")
                append("  访问场景: ${player.scenesVisited}\n")
                append("  收集线索: ${player.cluesCollected}\n")
                append("  骰点次数: ${player.diceRolls}\n")
                append("  成功率: ${player.successRate}%\n")
            }
            append("\n=== 骰点统计 ===\n")
            append("总次数: ${review.diceStatistics.totalRolls}\n")
            append("成功: ${review.diceStatistics.successCount}\n")
            append("失败: ${review.diceStatistics.failureCount}\n")
            append("大成功: ${review.diceStatistics.criticalSuccesses}\n")
            append("大失败: ${review.diceStatistics.criticalFailures}\n")
        }

        AlertDialog.Builder(this)
            .setTitle("游戏复盘")
            .setMessage(reviewText)
            .setPositiveButton("确定") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::xmovAvatar.isInitialized) {
            xmovAvatar.onResume()
        }
        // 恢复动作识别
        featureIntegrationManager.resumePoseDetection()
    }

    override fun onPause() {
        super.onPause()
        if (::xmovAvatar.isInitialized) {
            xmovAvatar.onPause()
        }
        // 暂停动作识别
        featureIntegrationManager.pausePoseDetection()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::xmovAvatar.isInitialized) {
            xmovAvatar.destroy()
        }
        // 断开网络同步
        featureIntegrationManager.disconnectFromServer()
        // 停止动作识别
        featureIntegrationManager.stopPoseDetection()
    }

    private fun detectPlayerEmotion() {
        lifecycleScope.launch {
            val gameStateText = gameEngine?.gameState?.value?.let {
                "当前场景: ${it.currentSceneId}"
            } ?: "游戏进行中"

            val emotion = featureIntegrationManager.detectPlayerEmotion(gameStateText)
            currentEmotionState = emotion
            // 将情绪状态添加到对话日志
            emotion?.let {
                addChatLog(ChatLog(
                    speaker = "系统",
                    content = "情绪状态: ${it.mood}, 注意力: ${it.attention}",
                    type = LogType.SYSTEM
                ))
            }
        }
    }
}
