package com.xmov.metahuman.app.social

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 社交管理器 - 简化版本
 */
class SocialManager(private val context: Context) {
    
    private val database = SocialDatabase.create(context)
    private val friendDao = database.friendDao()
    private val requestDao = database.friendRequestDao()
    private val recordDao = database.gameRecordDao()
    
    suspend fun sendFriendRequest(
        fromPlayerId: String,
        fromPlayerName: String,
        toPlayerId: String,
        message: String? = null
    ) {
        val request = FriendRequest(
            id = "req_${System.currentTimeMillis()}",
            fromPlayerId = fromPlayerId,
            fromPlayerName = fromPlayerName,
            toPlayerId = toPlayerId,
            message = message,
            status = RequestStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            respondedAt = null
        )
        requestDao.insert(request)
    }
    
    fun getFriends(playerId: String): Flow<List<Friend>> {
        return friendDao.getPlayerFriends(playerId)
    }
    
    suspend fun favoriteRoom(
        playerId: String,
        roomId: String,
        roomName: String,
        gameType: String,
        hostName: String
    ) {
        // 简化实现 - 可以扩展为收藏房间功能
    }
    
    fun getFavoriteRooms(playerId: String): Flow<List<GameRecord>> {
        return recordDao.getPlayerRecords(playerId)
    }
    
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
        achievements: List<String>,
        review: String? = null
    ) {
        val record = GameRecord(
            id = "record_${System.currentTimeMillis()}",
            playerId = playerId,
            playerName = "Player", // 简化
            roomId = roomId,
            roomName = roomName,
            gameType = gameType,
            duration = duration,
            score = score,
            achievements = achievements,
            completedAt = System.currentTimeMillis()
        )
        recordDao.insert(record)
    }
    
    fun generateShareContent(record: GameRecord): String {
        return "我刚完成了一场${record.gameType}游戏，得分${record.score}分！"
    }
    
    fun getGameRecords(playerId: String): Flow<List<GameRecord>> {
        return recordDao.getPlayerRecords(playerId)
    }
}