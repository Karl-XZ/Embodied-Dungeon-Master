package com.xmov.metahuman.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 对话日志适配器
 */
class ChatLogAdapter : RecyclerView.Adapter<ChatLogAdapter.ViewHolder>() {

    private val logs = mutableListOf<ChatLog>()

    fun addLog(log: ChatLog) {
        logs.add(log)
        notifyItemInserted(logs.size - 1)
    }

    fun clearLogs() {
        logs.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.bind(log)
    }

    override fun getItemCount(): Int = logs.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSpeaker: TextView = itemView.findViewById(R.id.tv_speaker)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_content)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)

        fun bind(log: ChatLog) {
            tvSpeaker.text = log.speaker
            tvContent.text = log.content
            
            // 根据类型设置不同的颜色
            val colorRes = when (log.type) {
                LogType.DM -> android.R.color.holo_blue_dark
                LogType.SYSTEM -> android.R.color.darker_gray
                LogType.ACTION -> android.R.color.holo_orange_dark
                LogType.PLAYER -> android.R.color.holo_green_dark
            }
            tvSpeaker.setTextColor(itemView.context.getColor(colorRes))
            
            tvTime.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(log.timestamp))
        }
    }
}
