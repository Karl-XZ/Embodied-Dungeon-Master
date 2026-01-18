package com.xmov.metahuman.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xmov.metahuman.app.trpg.GameType
import com.xmov.metahuman.app.trpg.RoomManagerProvider

/**
 * 加入房间界面
 */
class JoinRoomActivity : AppCompatActivity() {

    private val roomAdapter = RoomAdapter()

    private var appId: String? = null
    private var appSecret: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_room)

        handleIntentData()
        setupUI()
        loadRooms()
    }

    private fun handleIntentData() {
        val extras = intent.extras
        if (extras != null) {
            appId = extras.getString("app_id")
            appSecret = extras.getString("app_secret")
        }
    }

    private fun setupUI() {
        val rvRooms = findViewById<RecyclerView>(R.id.rv_rooms)
        val btnJoin = findViewById<Button>(R.id.btn_join)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh)
        val btnBack = findViewById<Button>(R.id.btn_back)

        rvRooms.layoutManager = LinearLayoutManager(this)
        rvRooms.adapter = roomAdapter

        btnJoin.setOnClickListener {
            joinSelectedRoom()
        }

        btnRefresh.setOnClickListener {
            loadRooms()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadRooms() {
        val roomManager = RoomManagerProvider.instance
        val rooms = roomManager.getRooms()

        roomAdapter.submitList(rooms)
        findViewById<TextView>(R.id.tv_room_count).text = "共 ${rooms.size} 个房间"
    }

    private fun joinSelectedRoom() {
        val selectedRoom = roomAdapter.selectedRoom
        if (selectedRoom == null) {
            Toast.makeText(this, "请先选择一个房间", Toast.LENGTH_SHORT).show()
            return
        }

        val roomManager = RoomManagerProvider.instance
        val playerName = "玩家_${System.currentTimeMillis()}"
        val userId = "user_${System.currentTimeMillis()}"

        val result = roomManager.joinRoom(
            selectedRoom.roomId,
            userId,
            playerName,
            null
        )

        result.fold(
            onSuccess = { room ->
                Toast.makeText(this, "加入房间成功", Toast.LENGTH_SHORT).show()

                // 跳转到游戏界面
                GameActivity.start(this, room.roomId, userId, isHost = false)
            },
            onFailure = { error ->
                Toast.makeText(this, "加入房间失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
