package com.xmov.metahuman.app

/**
 * 对话日志数据类
 */
data class ChatLog(
    val speaker: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType = LogType.PLAYER
)

enum class LogType {
    PLAYER,       // 玩家
    DM,           // DM/数字人
    SYSTEM,       // 系统消息
    ACTION        // 动作结果
}