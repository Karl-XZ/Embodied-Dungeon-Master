package com.xmov.metahuman.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xmov.metahuman.app.trpg.AIScriptGenerator
import com.xmov.metahuman.app.trpg.GameType
import com.xmov.metahuman.app.trpg.RoomManagerProvider
import com.xmov.metahuman.app.trpg.ScriptParser
import com.xmov.metahuman.app.trpg.StoryTree
import kotlinx.coroutines.launch
import java.io.File

/**
 * 房间创建界面
 */
class RoomCreationActivity : AppCompatActivity() {

    private val FILE_SELECT_CODE = 101
    private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 102

    private var selectedGameType: GameType = GameType.JUBENSHA
    private var selectedStoryTree: StoryTree? = null
    private var selectedFileUri: Uri? = null

    private var appId: String? = null
    private var appSecret: String? = null

    private val aiScriptGenerator = AIScriptGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_creation)

        handleIntentData()

        // 获取游戏类型
        val gameTypeStr = intent.getStringExtra("game_type")
        if (gameTypeStr != null) {
            selectedGameType = try {
                GameType.valueOf(gameTypeStr)
            } catch (e: Exception) {
                GameType.JUBENSHA
            }
        }

        setupUI()
    }

    private fun handleIntentData() {
        val extras = intent.extras
        if (extras != null) {
            appId = extras.getString("app_id")
            appSecret = extras.getString("app_secret")
        }

        if (appId.isNullOrBlank()) appId = AppSettings.getXmovAppId()
        if (appSecret.isNullOrBlank()) appSecret = AppSettings.getXmovAppSecret()
    }

    private fun setupUI() {
        val etRoomName = findViewById<EditText>(R.id.et_room_name)
        val etMaxPlayers = findViewById<EditText>(R.id.et_max_players)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val etScriptTheme = findViewById<EditText>(R.id.et_script_theme)
        val btnUploadScript = findViewById<Button>(R.id.btn_upload_script)
        val btnAIGenerate = findViewById<Button>(R.id.btn_ai_generate)
        val btnBuiltinScript = findViewById<Button>(R.id.btn_builtin_script)
        val btnCreateRoom = findViewById<Button>(R.id.btn_create_room)
        val btnBack = findViewById<Button>(R.id.btn_back)

        // 设置标题
        val gameTypeName = when (selectedGameType) {
            GameType.JUBENSHA -> "剧本杀"
            GameType.PAOTUAN -> "跑团"
            GameType.HAITANG -> "海龟汤"
        }
        title = "创建${gameTypeName}房间"

        // 默认值
        etRoomName.setText("${gameTypeName}_房间_${System.currentTimeMillis()}")
        etMaxPlayers.setText("6")

        // 上传剧本按钮
        btnUploadScript.setOnClickListener {
            requestStoragePermissionAndSelectFile()
        }

        // AI 生成剧本按钮
        btnAIGenerate.setOnClickListener {
            val theme = etScriptTheme.text.toString().trim()
            if (theme.isEmpty()) {
                Toast.makeText(this, "请输入剧本主题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 检查是否已配置大模型（APIMart / OpenAI-Compatible）
            if (!AppSettings.hasLlmConfig()) {
                AlertDialog.Builder(this)
                    .setTitle("未配置大模型")
                    .setMessage("AI生成需要先配置大模型 API Key。请先在设置中填写 APIMart API Key / BaseURL / Model。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return@setOnClickListener
            }

            generateScriptWithAI(theme)
        }

        // 使用内置剧本按钮
        btnBuiltinScript.setOnClickListener {
            val theme = etScriptTheme.text.toString().trim()
            if (theme.isEmpty()) {
                Toast.makeText(this, "请输入剧本主题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateBuiltinScript(theme)
        }

        // 创建房间按钮
        btnCreateRoom.setOnClickListener {
            val roomName = etRoomName.text.toString().trim()
            val maxPlayers = etMaxPlayers.text.toString().toIntOrNull() ?: 6
            val password = etPassword.text.toString().trim().ifEmpty { null }

            if (roomName.isEmpty()) {
                Toast.makeText(this, "请输入房间名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedStoryTree == null) {
                Toast.makeText(this, "请先上传剧本文件或使用 AI 生成剧本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createRoom(roomName, maxPlayers, password)
        }

        // 返回按钮
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun requestStoragePermissionAndSelectFile() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        } else {
            selectFile()
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "text/plain",
                "application/zip",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/pdf"
            ))
        }
        startActivityForResult(intent, FILE_SELECT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
                parseScriptFile(uri)
            }
        }
    }

    private fun parseScriptFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 将Uri转为临时文件
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "temp_script.${getFileExtension(uri)}")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 解析剧本
                val parser = ScriptParser()
                val result = parser.parseScript(tempFile, selectedGameType)

                result.fold(
                    onSuccess = { storyTree ->
                        selectedStoryTree = storyTree
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomCreationActivity,
                                "剧本解析成功: ${storyTree.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                            findViewById<Button>(R.id.btn_upload_script).text =
                                "✓ ${storyTree.title}"
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomCreationActivity,
                                "剧本解析失败: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@RoomCreationActivity,
                        "文件读取失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getFileExtension(uri: Uri): String {
        val mimeType = contentResolver.getType(uri)
        return when (mimeType) {
            "application/json" -> "json"
            "text/plain" -> "txt"
            "application/zip" -> "zip"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/pdf" -> "pdf"
            else -> "json"
        }
    }

    /**
     * 使用内置剧本
     */
    private fun generateBuiltinScript(theme: String) {
        lifecycleScope.launch {
            Toast.makeText(
                this@RoomCreationActivity,
                "正在生成内置剧本，请稍候...",
                Toast.LENGTH_SHORT
            ).show()

            try {
                val result = aiScriptGenerator.generateBuiltinScript(
                    theme = theme,
                    gameType = selectedGameType,
                    sceneCount = 5
                )

                result.fold(
                    onSuccess = { storyTree ->
                        selectedStoryTree = storyTree
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomCreationActivity,
                                "内置剧本生成成功: " + storyTree.title,
                                Toast.LENGTH_SHORT
                            ).show()
                            findViewById<Button>(R.id.btn_upload_script).text =
                                "✓ " + storyTree.title + " (内置)"
                            findViewById<Button>(R.id.btn_builtin_script).text = "✓ 已生成"
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomCreationActivity,
                                "内置剧本生成失败: " + error.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@RoomCreationActivity,
                        "生成失败: " + e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 使用 AI 生成剧本
     */
    private fun generateScriptWithAI(theme: String) {
        lifecycleScope.launch {
            Toast.makeText(
                this@RoomCreationActivity,
                "AI 正在生成剧本，请稍候...",
                Toast.LENGTH_SHORT
            ).show()

            try {
                val result = aiScriptGenerator.generateScript(
                    theme = theme,
                    gameType = selectedGameType,
                    sceneCount = 5,
                    useFallback = false  // 不自动使用内置剧本
                )

                result.fold(
                    onSuccess = { storyTree ->
                        selectedStoryTree = storyTree
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomCreationActivity,
                                "AI 剧本生成成功: " + storyTree.title,
                                Toast.LENGTH_SHORT
                            ).show()
                            findViewById<Button>(R.id.btn_upload_script).text =
                                "✓ " + storyTree.title + " (AI生成)"
                            findViewById<Button>(R.id.btn_ai_generate).text = "✓ 已生成"
                            findViewById<EditText>(R.id.et_script_theme).setText(theme)
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            AlertDialog.Builder(this@RoomCreationActivity)
                                .setTitle("AI 生成失败")
                                .setMessage("剧本生成失败：${error.message}\n\n建议：\n1. 检查大模型配置\n2. 尝试使用内置剧本\n3. 上传自定义剧本文件")
                                .setPositiveButton("使用内置剧本") { _, _ ->
                                    generateBuiltinScript(theme)
                                }
                                .setNegativeButton("重试", null)
                                .show()
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this@RoomCreationActivity)
                        .setTitle("生成失败")
                        .setMessage("发生错误：${e.message}\n\n建议：\n1. 检查大模型配置\n2. 尝试使用内置剧本\n3. 上传自定义剧本文件")
                        .setPositiveButton("使用内置剧本") { _, _ ->
                            generateBuiltinScript(theme)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun createRoom(roomName: String, maxPlayers: Int, password: String?) {
        val roomManager = RoomManagerProvider.instance

        if (selectedStoryTree == null) {
            Toast.makeText(this, "请先选择剧本（上传文件或AI生成）", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("RoomCreation", "使用剧本: ${selectedStoryTree!!.title}")
        android.util.Log.d("RoomCreation", "场景数量: ${selectedStoryTree!!.nodes.size}")
        android.util.Log.d("RoomCreation", "转场边数量: ${selectedStoryTree!!.edges.size}")
        android.util.Log.d("RoomCreation", "全局线索数量: ${selectedStoryTree!!.globalClues.size}")

        selectedStoryTree?.let { storyTree ->
            val userId = "user_${System.currentTimeMillis()}"
            val room = roomManager.createRoom(
                roomName = roomName,
                gameType = selectedGameType,
                hostId = userId,
                storyTree = storyTree,
                maxPlayers = maxPlayers,
                password = password
            )

            android.util.Log.d("RoomCreation", "房间创建成功: ${room.roomId}")

            Toast.makeText(this, "房间创建成功: ${room.roomId}", Toast.LENGTH_SHORT).show()

            // 跳转到游戏界面
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("room_id", room.roomId)
            intent.putExtra("player_id", userId)
            intent.putExtra("is_host", true)
            intent.putExtra("app_id", appId)
            intent.putExtra("app_secret", appSecret)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectFile()
            } else {
                Toast.makeText(this, "需要存储权限才能上传剧本", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
