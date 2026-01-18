package com.xmov.metahuman.app.gameplay

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 角色系统 - 简化版本
 */
data class Character(
    val id: String,
    val name: String,
    val description: String,
    val level: Int = 1,
    val experience: Int = 0,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val strength: Int = 10,
    val dexterity: Int = 10,
    val intelligence: Int = 10,
    val charisma: Int = 10,
    val skills: Map<String, Int> = emptyMap(),
    val inventory: List<String> = emptyList(),
    val backstory: String = "",
    val portrait: String? = null,
    val playerId: String,
    val createdAt: Long,
    val lastUpdated: Long
)

/**
 * 角色数据访问对象 - 简化版本
 */
interface CharacterDao {
    fun getAll(): Flow<List<Character>>
    suspend fun getById(id: String): Character?
    fun getByPlayerId(playerId: String): Flow<List<Character>>
    suspend fun insert(character: Character)
    suspend fun insertAll(characters: List<Character>)
    suspend fun update(character: Character)
    suspend fun delete(character: Character)
    suspend fun deleteById(id: String)
}

/**
 * 角色数据库 - 简化版本
 */
abstract class CharacterDatabase {
    abstract fun characterDao(): CharacterDao

    companion object {
        fun create(context: Context): CharacterDatabase {
            return object : CharacterDatabase() {
                override fun characterDao(): CharacterDao {
                    return object : CharacterDao {
                        private val characters = mutableListOf<Character>()
                        
                        override fun getAll(): Flow<List<Character>> = MutableStateFlow(characters)
                        override suspend fun getById(id: String): Character? = characters.find { it.id == id }
                        override fun getByPlayerId(playerId: String): Flow<List<Character>> = MutableStateFlow(characters.filter { it.playerId == playerId })
                        override suspend fun insert(character: Character) { characters.add(character) }
                        override suspend fun insertAll(characters: List<Character>) { this.characters.addAll(characters) }
                        override suspend fun update(character: Character) {
                            val index = characters.indexOfFirst { it.id == character.id }
                            if (index >= 0) characters[index] = character
                        }
                        override suspend fun delete(character: Character) { characters.remove(character) }
                        override suspend fun deleteById(id: String) { characters.removeAll { it.id == id } }
                    }
                }
            }
        }
    }
}