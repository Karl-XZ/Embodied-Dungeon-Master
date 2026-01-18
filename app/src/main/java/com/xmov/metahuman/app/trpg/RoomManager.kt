package com.xmov.metahuman.app.trpg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 多人房间管理器
 */
class RoomManager {
    private val rooms = mutableMapOf<String, GameRoom>()
    private val roomEngines = mutableMapOf<String, GameEngine>()

    /**
     * 创建房间
     */
    fun createRoom(
        roomName: String,
        gameType: GameType,
        hostId: String,
        storyTree: StoryTree,
        maxPlayers: Int = 6,
        password: String? = null
    ): GameRoom {
        val roomId = generateRoomId()

        val room = GameRoom(
            roomId = roomId,
            roomName = roomName,
            gameType = gameType,
            hostId = hostId,
            storyTreeId = storyTree.id,
            maxPlayers = maxPlayers,
            currentPlayers = 1,
            status = RoomStatus.LOBBY,
            createdAt = System.currentTimeMillis(),
            password = password
        )

        rooms[roomId] = room

        // 创建游戏引擎
        val engine = GameEngine(storyTree, roomId)
        roomEngines[roomId] = engine

        // 添加房主
        engine.addPlayer(
            Player(
                id = hostId,
                name = "房主",
                role = PlayerRole.HOST,
                isHost = true,
                isOnline = true
            )
        )

        return room
    }

    /**
     * 加入房间
     */
    fun joinRoom(
        roomId: String,
        playerId: String,
        playerName: String,
        password: String? = null
    ): Result<GameRoom> {
        val room = rooms[roomId]
            ?: return Result.failure(Exception("房间不存在"))

        // 验证密码
        if (room.password != null && room.password != password) {
            return Result.failure(Exception("密码错误"))
        }

        // 检查人数限制
        if (room.currentPlayers >= room.maxPlayers) {
            return Result.failure(Exception("房间已满"))
        }

        // 获取游戏引擎
        val engine = roomEngines[roomId]
            ?: return Result.failure(Exception("游戏引擎不存在"))

        // 检查玩家是否已存在
        val existingPlayer = engine.gameState.value.players.find { it.id == playerId }
        if (existingPlayer != null) {
            return Result.failure(Exception("玩家已存在"))
        }

        // 添加玩家
        engine.addPlayer(
            Player(
                id = playerId,
                name = playerName,
                role = PlayerRole.PLAYER,
                isHost = false,
                isOnline = true
            )
        )

        // 更新房间人数
        val updatedRoom = room.copy(currentPlayers = room.currentPlayers + 1)
        rooms[roomId] = updatedRoom

        return Result.success(updatedRoom)
    }

    /**
     * 离开房间
     */
    fun leaveRoom(roomId: String, playerId: String): Result<Unit> {
        val room = rooms[roomId]
            ?: return Result.failure(Exception("房间不存在"))

        val engine = roomEngines[roomId]
            ?: return Result.failure(Exception("游戏引擎不存在"))

        // 移除玩家
        engine.removePlayer(playerId)

        // 如果是房主离开，转移房主或关闭房间
        if (playerId == room.hostId) {
            val remainingPlayers = engine.gameState.value.players
            if (remainingPlayers.isNotEmpty()) {
                // 转移房主
                val newHost = remainingPlayers[0]
                val newRoom = room.copy(hostId = newHost.id)
                rooms[roomId] = newRoom
            } else {
                // 关闭房间
                rooms.remove(roomId)
                engine.destroy()
                roomEngines.remove(roomId)
                return Result.success(Unit)
            }
        }

        // 更新房间人数
        rooms[roomId] = room.copy(currentPlayers = room.currentPlayers - 1)

        return Result.success(Unit)
    }

    /**
     * 开始游戏
     */
    fun startGame(roomId: String): Result<Unit> {
        val room = rooms[roomId]
            ?: return Result.failure(Exception("房间不存在"))

        if (room.status != RoomStatus.LOBBY) {
            return Result.failure(Exception("游戏已开始或已结束"))
        }

        rooms[roomId] = room.copy(status = RoomStatus.PLAYING)
        return Result.success(Unit)
    }

    /**
     * 结束游戏
     */
    fun endGame(roomId: String): Result<GameReview> {
        val room = rooms[roomId]
            ?: return Result.failure(Exception("房间不存在"))

        val engine = roomEngines[roomId]
            ?: return Result.failure(Exception("游戏引擎不存在"))

        // 生成复盘报告
        val review = engine.generateReview()

        rooms[roomId] = room.copy(status = RoomStatus.ENDED)

        return Result.success(review)
    }

    /**
     * 获取房间
     */
    fun getRoom(roomId: String): GameRoom? {
        return rooms[roomId]
    }

    /**
     * 获取房间列表
     */
    fun getRooms(gameType: GameType? = null): List<GameRoom> {
        return if (gameType != null) {
            rooms.values.filter { it.gameType == gameType }
        } else {
            rooms.values.toList()
        }
    }

    /**
     * 获取游戏引擎
     */
    fun getGameEngine(roomId: String): GameEngine? {
        return roomEngines[roomId]
    }

    /**
     * 处理玩家输入
     */
    suspend fun handlePlayerInput(
        roomId: String,
        playerId: String,
        actionType: ActionType,
        actionData: String
    ): Result<ActionResult> {
        val engine = roomEngines[roomId]
            ?: return Result.failure(Exception("游戏引擎不存在"))

        val result = engine.handlePlayerInput(playerId, actionType, actionData)
        return Result.success(result)
    }

    /**
     * 生成房间ID
     */
    private fun generateRoomId(): String {
        return "room_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}

// 单例
object RoomManagerProvider {
    val instance: RoomManager by lazy { RoomManager() }
}
