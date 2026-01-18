package com.xmov.metahuman.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.let

object GlideBackgroundLoader {

    /**
     * 使用Glide加载图片作为LinearLayout背景，保持比例不变形
     * @param context 上下文
     * @param layout 目标布局
     * @param imageSource 图片来源（支持网络URL、本地路径、资源ID等）
     * @param placeholder 加载中占位图（可选）
     * @param errorImage 加载失败图片（可选）
     */
    fun loadLayoutBackground(
        context: Context,
        layout: ViewGroup,
        imageSource: Any,
        placeholder: Int? = null,
        errorImage: Int? = null
    ) {
        // 监听布局测量完成，确保获取正确尺寸
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 移除监听器，避免重复调用
                layout.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 获取布局宽高
                val layoutWidth = layout.width
                val layoutHeight = layout.height

                // 尺寸无效时直接返回
                if (layoutWidth <= 0 || layoutHeight <= 0) return

                // 配置Glide请求选项
                val requestOptions = RequestOptions()
                    .centerCrop() // 按比例缩放，裁剪超出部分
                    .override(layoutWidth, layoutHeight) // 指定目标尺寸

                // 添加占位图（如果有）
                placeholder?.let { requestOptions.placeholder(it) }
                // 添加错误图（如果有）
                errorImage?.let { requestOptions.error(it) }

                // 加载图片并设置为背景
                Glide.with(context)
                    .load(imageSource)
                    .apply(requestOptions)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            // 设置为布局背景
                            layout.background = resource
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // 加载被清除时的回调（可选处理）
                        }
                    })
            }
        }

        // 添加布局监听
        layout.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }
}