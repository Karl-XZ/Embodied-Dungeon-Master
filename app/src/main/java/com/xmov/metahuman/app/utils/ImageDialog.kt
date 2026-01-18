package com.xmov.metahuman.app.utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.xmov.metahuman.app.R

/**
 * 图片弹窗对话框
 * 用于显示生成的图片，可以关闭
 */
class ImageDialog(private val context: Context) {

    private var dialog: Dialog? = null

    /**
     * 显示图片对话框
     * @param imageUrl 图片 URL 或本地文件路径
     */
    fun show(imageUrl: String) {
        dismiss()

        dialog = Dialog(context).apply {
            setContentView(R.layout.dialog_image)

            // 设置对话框大小
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 获取组件
            val imageView = findViewById<AppCompatImageView>(R.id.iv_image)
            val btnClose = findViewById<Button>(R.id.btn_close)

            // 加载图片
            Glide.with(context)
                .load(imageUrl)
                .fitCenter()
                .into(imageView)

            // 关闭按钮
            btnClose.setOnClickListener {
                dismiss()
            }

            // 点击图片也可以关闭
            imageView.setOnClickListener {
                dismiss()
            }

            // 点击外部区域也可以关闭
            setCanceledOnTouchOutside(true)

            show()
        }
    }

    /**
     * 关闭对话框
     */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /**
     * 判断对话框是否显示
     */
    fun isShowing(): Boolean {
        return dialog?.isShowing == true
    }
}
