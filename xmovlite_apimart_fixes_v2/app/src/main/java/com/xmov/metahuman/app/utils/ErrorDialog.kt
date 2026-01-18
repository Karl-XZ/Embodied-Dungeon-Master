package com.xmov.metahuman.app.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    private const val TAG = "ErrorDialog"

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun showToastFallback(context: Context, title: String, errorMessage: String) {
        try {
            Toast.makeText(context, "$title: $errorMessage", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            // ignore
        }
    }

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
        // 1) 确保在主线程弹窗；否则会导致“闪退/崩溃”（例如网络/IO 线程里调用）
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                showError(context, title, errorMessage, stackTrace)
            }
            return
        }

        // 2) Dialog 必须使用 Activity Context，否则可能 BadTokenException
        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.e(TAG, "No valid Activity context, fallback to toast. title=$title")
            Log.e(TAG, "error=$errorMessage")
            if (!stackTrace.isNullOrBlank()) Log.e(TAG, stackTrace)
            showToastFallback(context, title, errorMessage)
            return
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("⚠️ $title")

        // 创建可滚动的错误信息
        val scrollView = ScrollView(activity)
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val errorTextView = TextView(activity).apply {
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
            copyToClipboard(activity, errorMessage, stackTrace)
        }

        try {
            builder.show()
        } catch (e: Exception) {
            // 兜底：即便弹窗失败也不要让 App 崩溃
            Log.e(TAG, "Failed to show dialog", e)
            showToastFallback(activity, title, errorMessage)
        }
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
