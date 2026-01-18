package com.xmov.metahuman.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xmov.metahuman.app.trpg.GameRoom

/**
 * 房间列表适配器
 */
class RoomAdapter : ListAdapter<GameRoom, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {

    var selectedRoom: GameRoom? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = getItem(position)
        holder.bind(room, selectedRoom == room)
    }

    class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvGameType: TextView = itemView.findViewById(R.id.tv_game_type)
        private val tvPlayers: TextView = itemView.findViewById(R.id.tv_players)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

        fun bind(room: GameRoom, isSelected: Boolean) {
            tvRoomName.text = room.roomName
            tvGameType.text = when (room.gameType) {
                com.xmov.metahuman.app.trpg.GameType.JUBENSHA -> "剧本杀"
                com.xmov.metahuman.app.trpg.GameType.PAOTUAN -> "跑团"
                com.xmov.metahuman.app.trpg.GameType.HAITANG -> "海龟汤"
            }
            tvPlayers.text = "${room.currentPlayers}/${room.maxPlayers} 人"
            tvStatus.text = when (room.status) {
                com.xmov.metahuman.app.trpg.RoomStatus.LOBBY -> "等待中"
                com.xmov.metahuman.app.trpg.RoomStatus.PLAYING -> "游戏中"
                com.xmov.metahuman.app.trpg.RoomStatus.PAUSED -> "已暂停"
                com.xmov.metahuman.app.trpg.RoomStatus.ENDED -> "已结束"
            }

            itemView.isSelected = isSelected
            itemView.background = if (isSelected) {
                itemView.context.getDrawable(android.R.color.holo_blue_light)
            } else {
                null
            }

            itemView.setOnClickListener {
                (itemView.parent as RecyclerView).adapter.let { adapter ->
                    if (adapter is RoomAdapter) {
                        adapter.selectRoom(room)
                    }
                }
            }
        }
    }

    fun selectRoom(room: GameRoom) {
        selectedRoom = room
        notifyDataSetChanged()
    }

    class RoomDiffCallback : DiffUtil.ItemCallback<GameRoom>() {
        override fun areItemsTheSame(oldItem: GameRoom, newItem: GameRoom): Boolean {
            return oldItem.roomId == newItem.roomId
        }

        override fun areContentsTheSame(oldItem: GameRoom, newItem: GameRoom): Boolean {
            return oldItem == newItem
        }
    }
}
