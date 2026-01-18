package com.xmov.metahuman.app.gameplay

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 成就系统 - 简化版本（不使用Room数据库）
 */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String?,
    val category: AchievementCategory,
    val difficulty: AchievementDifficulty,
    val maxProgress: Int,
    val currentProgress: Int = 0,
    val isUnlocked: Boolean = false,
    val rewardXp: Int = 0,
    val rewardItems: List<String> = emptyList(),
    val unlockedAt: Long? = null,
    val createdAt: Long
)

/**
 * 成就进度记录
 */
data class AchievementProgress(
    val id: String,
    val achievementId: String,
    val playerId: String,
    val currentProgress: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long?,
    val lastUpdatedAt: Long
)

/**
 * 成就类别
 */
enum class AchievementCategory {
    STORY,          // 剧情
    COMBAT,         // 战斗
    EXPLORATION,    // 探索
    SOCIAL,         // 社交
    COLLECTION,     // 收集
    CHALLENGE       // 挑战
}

/**
 * 成就难度
 */
enum class AchievementDifficulty {
    EASY,       // 简单
    NORMAL,     // 普通
    HARD,       // 困难
    EXPERT,     // 专家
    LEGENDARY   // 传说
}

/**
 * 成就数据访问对象 - 简化版本
 */
interface AchievementDao {
    fun getAll(): Flow<List<Achievement>>
    fun getUnlocked(): Flow<List<Achievement>>
    suspend fun getById(id: String): Achievement?
    fun getByCategory(category: AchievementCategory): Flow<List<Achievement>>
    suspend fun insert(achievement: Achievement)
    suspend fun insertAll(achievements: List<Achievement>)
    suspend fun update(achievement: Achievement)
    fun getPlayerProgress(playerId: String): Flow<List<AchievementProgress>>
    suspend fun getPlayerAchievementProgress(playerId: String, achievementId: String): AchievementProgress?
    suspend fun insertProgress(progress: AchievementProgress)
    suspend fun updateProgress(progress: AchievementProgress)
    fun getUnlockedByPlayer(playerId: String): Flow<List<AchievementProgress>>
}

/**
 * 成就数据库 - 简化版本
 */
abstract class AchievementDatabase {
    abstract fun achievementDao(): AchievementDao

    companion object {
        private const val DATABASE_NAME = "trpg_achievements.db"

        fun create(context: Context): AchievementDatabase {
            // 返回一个简单的内存实现
            return object : AchievementDatabase() {
                override fun achievementDao(): AchievementDao {
                    return object : AchievementDao {
                        private val achievements = mutableListOf<Achievement>()
                        private val progress = mutableListOf<AchievementProgress>()
                        
                        override fun getAll(): Flow<List<Achievement>> = MutableStateFlow(achievements)
                        override fun getUnlocked(): Flow<List<Achievement>> = MutableStateFlow(achievements.filter { it.isUnlocked })
                        override suspend fun getById(id: String): Achievement? = achievements.find { it.id == id }
                        override fun getByCategory(category: AchievementCategory): Flow<List<Achievement>> = MutableStateFlow(achievements.filter { it.category == category })
                        override suspend fun insert(achievement: Achievement) { achievements.add(achievement) }
                        override suspend fun insertAll(achievements: List<Achievement>) { this.achievements.addAll(achievements) }
                        override suspend fun update(achievement: Achievement) { 
                            val index = achievements.indexOfFirst { it.id == achievement.id }
                            if (index >= 0) achievements[index] = achievement
                        }
                        override fun getPlayerProgress(playerId: String): Flow<List<AchievementProgress>> = MutableStateFlow(progress.filter { it.playerId == playerId })
                        override suspend fun getPlayerAchievementProgress(playerId: String, achievementId: String): AchievementProgress? = progress.find { it.playerId == playerId && it.achievementId == achievementId }
                        override suspend fun insertProgress(progress: AchievementProgress) { this.progress.add(progress) }
                        override suspend fun updateProgress(progress: AchievementProgress) {
                            val index = this.progress.indexOfFirst { it.id == progress.id }
                            if (index >= 0) this.progress[index] = progress
                        }
                        override fun getUnlockedByPlayer(playerId: String): Flow<List<AchievementProgress>> = MutableStateFlow(progress.filter { it.playerId == playerId && it.isUnlocked })
                    }
                }
            }
        }
    }
}