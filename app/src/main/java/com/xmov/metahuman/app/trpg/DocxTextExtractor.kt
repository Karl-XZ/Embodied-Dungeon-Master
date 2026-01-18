package com.xmov.metahuman.app.trpg

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 轻量 DOCX 文本提取：直接读取 word/document.xml 并提取段落文本。
 * 不依赖 Apache POI，体积更小。
 */
object DocxTextExtractor {

    fun extract(file: File): String = file.inputStream().use { extract(it) }

    fun extract(bytes: ByteArray): String = extract(ByteArrayInputStream(bytes))

    fun extract(inputStream: InputStream): String {
        val zis = ZipInputStream(BufferedInputStream(inputStream))
        var documentXml: String? = null

        zis.use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "word/document.xml") {
                    documentXml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zip.nextEntry
            }
        }

        val xml = documentXml ?: return ""

        // 以段落为单位抽取 <w:t> 文本
        val paragraphRegex = Regex("<w:p[\\s\\S]*?</w:p>")
        val textRegex = Regex("<w:t[^>]*>([\\s\\S]*?)</w:t>")

        val sb = StringBuilder()
        paragraphRegex.findAll(xml).forEach { pMatch ->
            val p = pMatch.value
            val texts = textRegex.findAll(p).map { it.groupValues[1] }.toList()
            if (texts.isNotEmpty()) {
                sb.append(texts.joinToString(separator = "")).append("\n")
            }
        }

        return decodeXmlEntities(sb.toString()).trim()
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
