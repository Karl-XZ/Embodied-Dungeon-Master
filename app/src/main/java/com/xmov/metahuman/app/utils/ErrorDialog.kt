package com.xmov.metahuman.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * 错误对话框工具
 * 用于开发模式下显示可滚动、可复制的错误信息
 */
object ErrorDialog {

    /**
     * 显示错误对话框
     * @param context 上下文
     * @param title 对话框标题
     * @param errorMessage 错误信息
     * @param stackTrace 堆栈跟踪（可选）
     */
    fun showError(
        context: Context,
        title: String = "错误",
        errorMessage: String,
        stackTrace: String? = null
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("⚠️ $title")

        // 创建可滚动的错误信息
        val scrollView = ScrollView(context)
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val errorTextView = TextView(context).apply {
            val fullMessage = if (stackTrace != null) {
                "错误信息:\n$errorMessage\n\n堆栈跟踪:\n$stackTrace"
            } else {
                errorMessage
            }
            text = fullMessage
            textSize = 13f
            setPadding(40, 30, 40, 30)
            setLineSpacing(1.5f, 1f)
        }

        scrollView.addView(errorTextView)
        builder.setView(scrollView)

        builder.setPositiveButton("确定", null)

        builder.setNeutralButton("复制错误") { _, _ ->
            copyToClipboard(context, errorMessage, stackTrace)
        }

        builder.show()
    }

    /**
     * 复制错误信息到剪贴板
     */
    private fun copyToClipboard(
        context: Context,
        errorMessage: String,
        stackTrace: String? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fullMessage = if (stackTrace != null) {
            "错误信息: $errorMessage\n\n堆栈跟踪:\n$stackTrace"
        } else {
            errorMessage
        }

        val clip = ClipData.newPlainText("错误信息", fullMessage)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}
