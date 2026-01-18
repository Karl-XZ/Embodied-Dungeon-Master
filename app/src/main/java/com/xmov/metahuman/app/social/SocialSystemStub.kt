package com.xmov.metahuman.app.social

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 社交系统 - 简化版本
 */
data class Friend(
    val id: String,
    val playerId: String,
    val friendPlayerId: String,
    val friendName: String,
    val friendAvatar: String?,
    val relationshipLevel: Int = 1,
    val lastInteraction: Long,
    val createdAt: Long
)

data class FriendRequest(
    val id: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String,
    val message: String?,
    val status: RequestStatus,
    val createdAt: Long,
    val respondedAt: Long?
)

data class GameRecord(
    val id: String,
    val playerId: String,
    val playerName: String,
    val roomId: String,
    val roomName: String,
    val gameType: String,
    val duration: Long,
    val score: Int,
    val achievements: List<String>,
    val completedAt: Long
)

data class PlayerMessage(
    val id: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val toPlayerId: String,
    val content: String,
    val messageType: MessageType,
    val isRead: Boolean = false,
    val createdAt: Long,
    val readAt: Long?
)

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}

enum class MessageType {
    TEXT,
    GAME_INVITE,
    ACHIEVEMENT_SHARE,
    SYSTEM
}

/**
 * 社交数据访问对象 - 简化版本
 */
interface FriendDao {
    fun getPlayerFriends(playerId: String): Flow<List<Friend>>
    suspend fun getFriend(playerId: String, friendId: String): Friend?
    suspend fun addFriend(friend: Friend)
    suspend fun updateFriend(friend: Friend)
    suspend fun removeFriend(friend: Friend)
    fun searchFriends(playerId: String, query: String): Flow<List<Friend>>
}

interface FriendRequestDao {
    fun getIncomingRequests(playerId: String): Flow<List<FriendRequest>>
    fun getOutgoingRequests(playerId: String): Flow<List<FriendRequest>>
    suspend fun insert(request: FriendRequest)
    suspend fun update(request: FriendRequest)
    fun getAllRequests(playerId: String): List<FriendRequest>
}

interface GameRecordDao {
    fun getPlayerRecords(playerId: String): Flow<List<GameRecord>>
    fun getRoomRecords(roomId: String): Flow<List<GameRecord>>
    suspend fun insert(record: GameRecord)
    suspend fun delete(id: String)
    suspend fun deletePlayerRecords(playerId: String)
}

interface MessageDao {
    fun getPlayerMessages(playerId: String): Flow<List<PlayerMessage>>
    fun getConversation(playerId: String, otherPlayerId: String): Flow<List<PlayerMessage>>
    suspend fun insert(message: PlayerMessage)
    suspend fun update(message: PlayerMessage)
    suspend fun delete(message: PlayerMessage)
    suspend fun markAsRead(messageId: String)
}

/**
 * 社交数据库 - 简化版本
 */
abstract class SocialDatabase {
    abstract fun friendDao(): FriendDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun create(context: Context): SocialDatabase {
            return object : SocialDatabase() {
                override fun friendDao(): FriendDao = object : FriendDao {
                    private val friends = mutableListOf<Friend>()
                    override fun getPlayerFriends(playerId: String): Flow<List<Friend>> = MutableStateFlow(friends.filter { it.playerId == playerId })
                    override suspend fun getFriend(playerId: String, friendId: String): Friend? = friends.find { it.playerId == playerId && it.friendPlayerId == friendId }
                    override suspend fun addFriend(friend: Friend) { friends.add(friend) }
                    override suspend fun updateFriend(friend: Friend) {
                        val index = friends.indexOfFirst { it.id == friend.id }
                        if (index >= 0) friends[index] = friend
                    }
                    override suspend fun removeFriend(friend: Friend) { friends.remove(friend) }
                    override fun searchFriends(playerId: String, query: String): Flow<List<Friend>> = MutableStateFlow(friends.filter { it.playerId == playerId && it.friendName.contains(query, ignoreCase = true) })
                }
                
                override fun friendRequestDao(): FriendRequestDao = object : FriendRequestDao {
                    private val requests = mutableListOf<FriendRequest>()
                    override fun getIncomingRequests(playerId: String): Flow<List<FriendRequest>> = MutableStateFlow(requests.filter { it.toPlayerId == playerId })
                    override fun getOutgoingRequests(playerId: String): Flow<List<FriendRequest>> = MutableStateFlow(requests.filter { it.fromPlayerId == playerId })
                    override suspend fun insert(request: FriendRequest) { requests.add(request) }
                    override suspend fun update(request: FriendRequest) {
                        val index = requests.indexOfFirst { it.id == request.id }
                        if (index >= 0) requests[index] = request
                    }
                    override fun getAllRequests(playerId: String): List<FriendRequest> = requests.filter { it.fromPlayerId == playerId || it.toPlayerId == playerId }
                }
                
                override fun gameRecordDao(): GameRecordDao = object : GameRecordDao {
                    private val records = mutableListOf<GameRecord>()
                    override fun getPlayerRecords(playerId: String): Flow<List<GameRecord>> = MutableStateFlow(records.filter { it.playerId == playerId })
                    override fun getRoomRecords(roomId: String): Flow<List<GameRecord>> = MutableStateFlow(records.filter { it.roomId == roomId })
                    override suspend fun insert(record: GameRecord) { records.add(record) }
                    override suspend fun delete(id: String) { records.removeAll { it.id == id } }
                    override suspend fun deletePlayerRecords(playerId: String) { records.removeAll { it.playerId == playerId } }
                }
                
                override fun messageDao(): MessageDao = object : MessageDao {
                    private val messages = mutableListOf<PlayerMessage>()
                    override fun getPlayerMessages(playerId: String): Flow<List<PlayerMessage>> = MutableStateFlow(messages.filter { it.toPlayerId == playerId })
                    override fun getConversation(playerId: String, otherPlayerId: String): Flow<List<PlayerMessage>> = MutableStateFlow(messages.filter { (it.fromPlayerId == playerId && it.toPlayerId == otherPlayerId) || (it.fromPlayerId == otherPlayerId && it.toPlayerId == playerId) })
                    override suspend fun insert(message: PlayerMessage) { messages.add(message) }
                    override suspend fun update(message: PlayerMessage) {
                        val index = messages.indexOfFirst { it.id == message.id }
                        if (index >= 0) messages[index] = message
                    }
                    override suspend fun delete(message: PlayerMessage) { messages.remove(message) }
                    override suspend fun markAsRead(messageId: String) {
                        val message = messages.find { it.id == messageId }
                        if (message != null) {
                            val index = messages.indexOf(message)
                            messages[index] = message.copy(isRead = true, readAt = System.currentTimeMillis())
                        }
                    }
                }
            }
        }
    }
}