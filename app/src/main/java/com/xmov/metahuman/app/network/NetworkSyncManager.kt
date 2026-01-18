package com.xmov.metahuman.app.network

import android.util.Log
import com.xmov.metahuman.app.trpg.*
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * 网络同步管理器
 * 基于 Socket.IO 实现实时多人游戏同步
 */
class NetworkSyncManager {

    private val TAG = "NetworkSyncManager"

    private var socket: Socket? = null
    private val gson = Gson()

    // 回调接口
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onGameStateChangedCallback: ((GameState) -> Unit)? = null
    private var onPlayerJoinedCallback: ((Player) -> Unit)? = null
    private var onPlayerLeftCallback: ((Player) -> Unit)? = null
    private var onChatMessageCallback: ((ChatMessage) -> Unit)? = null
    private var onDiceRolledCallback: ((DiceRoll) -> Unit)? = null

    /**
     * 连接到服务器
     */
    fun connect(serverUrl: String): Boolean {
        return try {
            disconnect() // 先断开现有连接

            socket = IO.socket(serverUrl).apply {
                on(Socket.EVENT_CONNECT) { onConnected() }
                on(Socket.EVENT_DISCONNECT) { onDisconnected() }
                on("error") { onError(it) }
                on("game_state_update") { onGameStateUpdate(it) }
                on("player_joined") { onPlayerJoined(it) }
                on("player_left") { onPlayerLeft(it) }
                on("chat_message") { onChatMessage(it) }
                on("dice_rolled") { onDiceRolled(it) }
            }

            socket?.connect()
            Log.d(TAG, "Connecting to server: $serverUrl")
            true
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Failed to connect to server", e)
            false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        Log.d(TAG, "Disconnected from server")
    }

    /**
     * 连接成功回调
     */
    private fun onConnected() {
        Log.d(TAG, "Connected to server")
        onConnectedCallback?.invoke()
    }

    /**
     * 断开连接回调
     */
    private fun onDisconnected() {
        Log.d(TAG, "Disconnected from server")
        onDisconnectedCallback?.invoke()
    }

    /**
     * 错误回调
     */
    private fun onError(args: Array<Any>) {
        Log.e(TAG, "Socket error: ${args.contentToString()}")
    }

    /**
     * 游戏状态更新
     */
    private fun onGameStateUpdate(args: Array<Any>) {
        if (args.isEmpty()) return

        try {
            val json = args[0] as JSONObject
            val gameState = gson.fromJson(json.toString(), GameState::class.java)
            onGameStateChangedCallback?.invoke(gameState)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse game state update", e)
        }
    }

    /**
     * 玩家加入
     */
    private fun onPlayerJoined(args: Array<Any>) {
        if (args.isEmpty()) return

        try {
            val json = args[0] as JSONObject
            val player = gson.fromJson(json.toString(), Player::class.java)
            onPlayerJoinedCallback?.invoke(player)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse player joined", e)
        }
    }

    /**
     * 玩家离开
     */
    private fun onPlayerLeft(args: Array<Any>) {
        if (args.isEmpty()) return

        try {
            val json = args[0] as JSONObject
            val player = gson.fromJson(json.toString(), Player::class.java)
            onPlayerLeftCallback?.invoke(player)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse player left", e)
        }
    }

    /**
     * 聊天消息
     */
    private fun onChatMessage(args: Array<Any>) {
        if (args.isEmpty()) return

        try {
            val json = args[0] as JSONObject
            val message = gson.fromJson(json.toString(), ChatMessage::class.java)
            onChatMessageCallback?.invoke(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chat message", e)
        }
    }

    /**
     * 骰点结果
     */
    private fun onDiceRolled(args: Array<Any>) {
        if (args.isEmpty()) return

        try {
            val json = args[0] as JSONObject
            val diceRoll = gson.fromJson(json.toString(), DiceRoll::class.java)
            onDiceRolledCallback?.invoke(diceRoll)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dice roll", e)
        }
    }

    // ========== 发送消息 ==========

    /**
     * 加入房间
     */
    fun joinRoom(roomId: String, player: Player) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("player", gson.toJson(player))
        }
        socket?.emit("join_room", data)
    }

    /**
     * 离开房间
     */
    fun leaveRoom(roomId: String, playerId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("playerId", playerId)
        }
        socket?.emit("leave_room", data)
    }

    /**
     * 发送玩家输入
     */
    fun sendPlayerInput(
        roomId: String,
        playerId: String,
        actionType: ActionType,
        actionData: String
    ) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("playerId", playerId)
            put("actionType", actionType.name)
            put("actionData", actionData)
            put("timestamp", System.currentTimeMillis())
        }
        socket?.emit("player_input", data)
    }

    /**
     * 发送聊天消息
     */
    fun sendChatMessage(
        roomId: String,
        playerId: String,
        playerName: String,
        message: String,
        messageType: MessageType = MessageType.PLAYER
    ) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("message", gson.toJson(ChatMessage(
                playerId = playerId,
                playerName = playerName,
                content = message,
                type = messageType,
                timestamp = System.currentTimeMillis()
            )))
        }
        socket?.emit("chat_message", data)
    }

    /**
     * 发送骰点结果
     */
    fun sendDiceRoll(roomId: String, diceRoll: DiceRoll) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("diceRoll", gson.toJson(diceRoll))
        }
        socket?.emit("dice_rolled", data)
    }

    /**
     * 开始游戏
     */
    fun startGame(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("timestamp", System.currentTimeMillis())
        }
        socket?.emit("start_game", data)
    }

    /**
     * 结束游戏
     */
    fun endGame(roomId: String, gameReview: GameReview) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("gameReview", gson.toJson(gameReview))
        }
        socket?.emit("end_game", data)
    }

    /**
     * 同步游戏状态
     */
    fun syncGameState(roomId: String, gameState: GameState) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("gameState", gson.toJson(gameState))
        }
        socket?.emit("sync_game_state", data)
    }

    // ========== 设置回调 ==========

    fun setOnConnected(callback: () -> Unit) {
        onConnectedCallback = callback
    }

    fun setOnDisconnected(callback: () -> Unit) {
        onDisconnectedCallback = callback
    }

    fun setOnGameStateChanged(callback: (GameState) -> Unit) {
        onGameStateChangedCallback = callback
    }

    fun setOnPlayerJoined(callback: (Player) -> Unit) {
        onPlayerJoinedCallback = callback
    }

    fun setOnPlayerLeft(callback: (Player) -> Unit) {
        onPlayerLeftCallback = callback
    }

    fun setOnChatMessage(callback: (ChatMessage) -> Unit) {
        onChatMessageCallback = callback
    }

    fun setOnDiceRolled(callback: (DiceRoll) -> Unit) {
        onDiceRolledCallback = callback
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return socket?.connected() == true
    }
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val playerId: String,
    val playerName: String,
    val content: String,
    val type: MessageType,
    val timestamp: Long
)

/**
 * 消息类型
 */
enum class MessageType {
    PLAYER,      // 玩家消息
    DM,          // DM消息
    SYSTEM,      // 系统消息
    ACTION       // 动作消息
}
