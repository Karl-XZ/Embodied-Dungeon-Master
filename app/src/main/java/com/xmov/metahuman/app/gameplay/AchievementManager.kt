package com.xmov.metahuman.app.gameplay

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 成就管理器 - 简化版本
 */
class AchievementManager(private val context: Context) {
    
    private val database = AchievementDatabase.create(context)
    private val dao = database.achievementDao()
    
    suspend fun initialize() {
        // 初始化一些默认成就
        val defaultAchievements = listOf(
            Achievement(
                id = "first_game",
                name = "初次体验",
                description = "完成第一场游戏",
                icon = null,
                category = AchievementCategory.STORY,
                difficulty = AchievementDifficulty.EASY,
                maxProgress = 1,
                rewardXp = 100,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.insertAll(defaultAchievements)
    }
    
    suspend fun updateProgress(playerId: String, achievementId: String, increment: Int = 1) {
        val existing = dao.getPlayerAchievementProgress(playerId, achievementId)
        if (existing != null) {
            val updated = existing.copy(
                currentProgress = existing.currentProgress + increment,
                lastUpdatedAt = System.currentTimeMillis()
            )
            dao.updateProgress(updated)
        } else {
            val newProgress = AchievementProgress(
                id = "progress_${playerId}_${achievementId}",
                achievementId = achievementId,
                playerId = playerId,
                currentProgress = increment,
                isUnlocked = false,
                unlockedAt = null,
                lastUpdatedAt = System.currentTimeMillis()
            )
            dao.insertProgress(newProgress)
        }
    }
    
    fun getPlayerAchievements(playerId: String): Flow<List<AchievementProgress>> {
        return dao.getPlayerProgress(playerId)
    }
    
    fun getAllAchievements(): Flow<List<Achievement>> {
        return dao.getAll()
    }
}