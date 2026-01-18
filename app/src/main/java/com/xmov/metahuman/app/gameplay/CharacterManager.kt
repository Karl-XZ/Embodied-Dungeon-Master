package com.xmov.metahuman.app.gameplay

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 角色管理器 - 简化版本
 */
class CharacterManager(private val context: Context) {
    
    private val database = CharacterDatabase.create(context)
    private val dao = database.characterDao()
    
    suspend fun createCharacter(
        playerId: String,
        name: String,
        role: String,
        avatar: String?
    ): Character {
        val character = Character(
            id = "char_${System.currentTimeMillis()}",
            name = name,
            description = "A $role character",
            playerId = playerId,
            portrait = avatar,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        dao.insert(character)
        return character
    }
    
    fun getPlayerCharacters(playerId: String): Flow<List<Character>> {
        return dao.getByPlayerId(playerId)
    }
    
    suspend fun addExperience(characterId: String, amount: Int) {
        val character = dao.getById(characterId)
        if (character != null) {
            val updated = character.copy(
                experience = character.experience + amount,
                lastUpdated = System.currentTimeMillis()
            )
            dao.update(updated)
        }
    }
}