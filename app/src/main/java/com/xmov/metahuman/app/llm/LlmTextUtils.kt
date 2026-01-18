package com.xmov.metahuman.app.llm

/**
 * 从 LLM 输出里尽量提取 JSON（避免模型输出 ```json ...``` 或夹杂解释）
 */
object LlmTextUtils {

    /**
     * 提取第一个形似 JSON 的对象（从第一个 '{' 到最后一个 '}'）
     */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
