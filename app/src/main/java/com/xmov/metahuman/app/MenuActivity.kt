package com.xmov.metahuman.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.xmov.metahuman.app.trpg.GameType

/**
 * 主菜单界面 - 选择游戏模式
 */
class MenuActivity : AppCompatActivity() {

    private var appId: String? = null
    private var appSecret: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        loadConfigFromIntentOrPrefs()

        // 设置
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 功能设置
        findViewById<Button>(R.id.btn_feature_settings).setOnClickListener {
            startActivity(Intent(this, FeatureSettingsActivity::class.java))
        }

        // 选择模式
        findViewById<Button>(R.id.btn_jubensha).setOnClickListener {
            startRoomCreation(GameType.JUBENSHA)
        }

        findViewById<Button>(R.id.btn_paotuan).setOnClickListener {
            startRoomCreation(GameType.PAOTUAN)
        }

        findViewById<Button>(R.id.btn_haitang).setOnClickListener {
            startRoomCreation(GameType.HAITANG)
        }

        findViewById<Button>(R.id.btn_join_room).setOnClickListener {
            startJoinRoomActivity()
        }

        val info = buildString {
            append("TRPG DM数字主持 v1.0\n")
            append("数字人：")
            append(if (!appId.isNullOrBlank() && !appSecret.isNullOrBlank()) "已配置" else "未配置")
            append("  |  LLM：")
            append(if (AppSettings.hasLlmConfig()) "已配置" else "未配置")
        }

        findViewById<TextView>(R.id.tv_app_info).text = info
    }

    private fun loadConfigFromIntentOrPrefs() {
        val extras = intent.extras
        val fromIntentId = extras?.getString("app_id")
        val fromIntentSecret = extras?.getString("app_secret")

        appId = (fromIntentId?.takeIf { it.isNotBlank() } ?: AppSettings.getXmovAppId())
        appSecret = (fromIntentSecret?.takeIf { it.isNotBlank() } ?: AppSettings.getXmovAppSecret())

        // 写回 prefs（避免只靠 intent）
        appId?.let { if (it.isNotBlank()) AppSettings.setXmovAppId(it) }
        appSecret?.let { if (it.isNotBlank()) AppSettings.setXmovAppSecret(it) }
    }

    private fun startRoomCreation(gameType: GameType) {
        val intent = Intent(this, RoomCreationActivity::class.java)
        intent.putExtra("game_type", gameType.name)
        intent.putExtra("app_id", appId)
        intent.putExtra("app_secret", appSecret)
        startActivity(intent)
    }

    private fun startJoinRoomActivity() {
        val intent = Intent(this, JoinRoomActivity::class.java)
        intent.putExtra("app_id", appId)
        intent.putExtra("app_secret", appSecret)
        startActivity(intent)
    }
}
