package com.xmov.metahuman.app.trpg

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * PDF 文本提取（基于 pdfbox-android）。
 * 注意：需要在 Application 中初始化 PDFBoxResourceLoader。
 */
object PdfTextExtractor {

    fun extract(file: File): String = file.inputStream().use { extract(it) }

    fun extract(bytes: ByteArray): String = extract(ByteArrayInputStream(bytes))

    fun extract(inputStream: InputStream): String {
        PDDocument.load(inputStream).use { doc ->
            val stripper = PDFTextStripper()
            return stripper.getText(doc).trim()
        }
    }
}
