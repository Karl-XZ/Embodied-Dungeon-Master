package com.xmov.metahuman.app

import java.util.regex.Pattern

object XmlContentStreamer {
    fun createStreamingSegments(xmlContent: String?): MutableList<String?> {
        val result: MutableList<String?> = ArrayList()

        try {
            // 验证输入格式
            if (!isValidXmlStructure(xmlContent)) {
                result.add(xmlContent)
                return result
            }

            // 提取三个主要部分
            val speakStart = extractSpeakStart(xmlContent!!)
            val textContent = extractTextContent(xmlContent)
            val speakEnd = extractSpeakEnd(xmlContent)

            if (speakStart.isEmpty() || speakEnd.isEmpty()) {
                result.add(xmlContent)
                return result
            }

            // 分割文本
            val textSegments = splitTextKeepPunctuation(textContent)

            // 构建每个分段
            for (segment in textSegments) {
                val segmentXml = StringBuilder()
                segmentXml.append(speakStart)
                    .append("\n")
                    .append(segment.trim { it <= ' ' })
                    .append("\n")
                    .append(speakEnd)
                result.add(segmentXml.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result.add(xmlContent)
        }

        return result
    }

    private fun isValidXmlStructure(xmlContent: String?): Boolean {
        return xmlContent != null &&
                xmlContent.contains("<speak") &&
                xmlContent.contains("</speak>") &&
                xmlContent.contains("</ue4event>") &&
                xmlContent.contains("<uievent>") &&
                xmlContent.contains("widget_close") // 确保有widget_close类型的uievent
    }

    private fun extractSpeakStart(xmlContent: String): String {
        // 提取从开始到ue4event结束标签
        val ue4End = xmlContent.indexOf("</ue4event>")
        if (ue4End == -1) return ""

        return xmlContent.substring(0, ue4End + "</ue4event>".length)
    }

    private fun extractTextContent(xmlContent: String): String {
        val ue4End = xmlContent.indexOf("</ue4event>")
        // 找到最后一个uievent标签（即widget_close）
        val lastWidgetStart = xmlContent.lastIndexOf("<uievent>")

        if (ue4End == -1 || lastWidgetStart == -1 || lastWidgetStart <= ue4End) {
            return ""
        }

        return xmlContent.substring(ue4End + "</ue4event>".length, lastWidgetStart)
            .trim { it <= ' ' }
    }

    private fun extractSpeakEnd(xmlContent: String): String {
        // 提取从最后一个uievent标签（widget_close）到结束
        val lastWidgetStart = xmlContent.lastIndexOf("<uievent>")
        if (lastWidgetStart == -1) return ""

        return xmlContent.substring(lastWidgetStart)
    }

    private fun splitTextKeepPunctuation(text: String?): MutableList<String> {
        val segments: MutableList<String> = ArrayList()

        if (text == null || text.trim { it <= ' ' }.isEmpty()) {
            return segments
        }

        // 更精确的分割规则：按句子分割，保留标点
        val pattern = Pattern.compile("[^，。！？；、\n\r]+[，。！？；、\n\r]?")
        val matcher = pattern.matcher(text)

        // 这个循环会在所有匹配项处理完后自动结束
        while (matcher.find()) {
            val segment = matcher.group().trim { it <= ' ' }
            if (!segment.isEmpty()) {
                segments.add(segment)
            }
        }

        // 如果没有匹配到任何分段，返回整个文本
        if (segments.isEmpty()) {
            segments.add(text)
        }

        return segments
    }
}