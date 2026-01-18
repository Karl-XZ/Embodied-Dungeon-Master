package com.xmov.metahuman.app

object TextExtractor {
    /**
     * 提取XML中的纯文本内容
     */
    fun extractTextFromXml(xmlContent: String): String {
        // 移除所有XML标签，只保留文本内容
        var textOnly = xmlContent.replace("<[^>]+>".toRegex(), " ")

        // 合并多个空格为单个空格
        textOnly = textOnly.replace("\\s+".toRegex(), " ")

        return textOnly.trim { it <= ' ' }
    }

    /**
     * 将文本按标点符号分割成流式文本
     */
    fun splitToStreamingText(text: String?): MutableList<String?> {
        val segments: MutableList<String?> = ArrayList<String?>()

        if (text == null || text.isEmpty()) {
            return segments
        }

        // 使用正则表达式按标点符号分割，但保留标点符号
        // 匹配标点符号：。！？；，.!?;以及换行符
        val regex = "(?<=[。！？；，.!?\\n])"
        val parts = text.split(regex.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        for (part in parts) {
            val segment = part.trim { it <= ' ' }
            if (!segment.isEmpty()) {
                segments.add(segment)
            }
        }

        return segments
    }

    /**
     * 完整的文本提取和分割流程
     */
    fun processXmlToStreamingText(xmlContent: String): MutableList<String?> {
        // 1. 提取纯文本
        val text = extractTextFromXml(xmlContent)

        // 2. 分割成流式文本
        return splitToStreamingText(text)
    }
}