package com.xmov.metahuman.app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 功能设置界面
 * 用于配置各种高级功能的开关和参数
 */
class FeatureSettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var btnSave: Button

    // 图片生成设置
    private lateinit var cbImageGenEnabled: CheckBox
    private lateinit var spinnerImageModel: Spinner
    private val imageModels = arrayOf(
        "stable-diffusion-xl",
        "dall-e-3",
        "midjourney-v6",
        "gemini-2.5-flash-image-preview",
        "gemini-2.0-flash-exp-image-generation"
    )

    // 动作识别设置
    private lateinit var cbPoseDetectionEnabled: CheckBox
    private lateinit var cbPunishmentEnabled: CheckBox

    // 情绪识别设置
    private lateinit var cbEmotionDetectionEnabled: CheckBox
    private lateinit var etQwenVlApiKey: EditText
    private lateinit var etQwenVlBaseUrl: EditText
    private lateinit var etQwenVlModel: EditText

    // 网络同步设置
    private lateinit var etSocketServerUrl: EditText

    // Agent服务设置
    private lateinit var etAgentBaseUrl: EditText

    // 开发模式设置
    private lateinit var cbDebugModeEnabled: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_settings)

        initView()
        bindSavedValues()
        setupActions()
    }

    private fun initView() {
        btnBack = findViewById(R.id.btn_back)
        btnSave = findViewById(R.id.btn_save)

        cbImageGenEnabled = findViewById(R.id.cb_image_gen_enabled)
        spinnerImageModel = findViewById(R.id.spinner_image_model)

        cbPoseDetectionEnabled = findViewById(R.id.cb_pose_detection_enabled)
        cbPunishmentEnabled = findViewById(R.id.cb_punishment_enabled)

        cbEmotionDetectionEnabled = findViewById(R.id.cb_emotion_detection_enabled)

        etQwenVlApiKey = findViewById(R.id.et_qwen_vl_api_key)
        etQwenVlBaseUrl = findViewById(R.id.et_qwen_vl_base_url)
        etQwenVlModel = findViewById(R.id.et_qwen_vl_model)

        etSocketServerUrl = findViewById(R.id.et_socket_server_url)
        etAgentBaseUrl = findViewById(R.id.et_agent_base_url)
        cbDebugModeEnabled = findViewById(R.id.cb_debug_mode_enabled)

        // 设置 Spinner 适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, imageModels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerImageModel.adapter = adapter
    }

    private fun bindSavedValues() {
        cbImageGenEnabled.isChecked = AppSettings.isImageGenEnabled()
        val imageModel = AppSettings.getImageGenModel()
        val modelIndex = imageModels.indexOf(imageModel).takeIf { it >= 0 } ?: 0
        spinnerImageModel.setSelection(modelIndex)

        cbPoseDetectionEnabled.isChecked = AppSettings.isPoseDetectionEnabled()
        cbPunishmentEnabled.isChecked = AppSettings.isPunishmentEnabled()

        cbEmotionDetectionEnabled.isChecked = AppSettings.isEmotionDetectionEnabled()

        etQwenVlApiKey.setText(AppSettings.getQwenVlApiKey())
        etQwenVlBaseUrl.setText(AppSettings.getQwenVlBaseUrl())
        etQwenVlModel.setText(AppSettings.getQwenVlModel())

        etSocketServerUrl.setText(AppSettings.getSocketServerUrl())
        etAgentBaseUrl.setText(AppSettings.getLlmBaseUrl())

        cbDebugModeEnabled.isChecked = AppSettings.isDebugModeEnabled()
    }

    private fun setupActions() {
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        AppSettings.setImageGenEnabled(cbImageGenEnabled.isChecked)
        AppSettings.setImageGenModel(spinnerImageModel.selectedItem as String)

        AppSettings.setPoseDetectionEnabled(cbPoseDetectionEnabled.isChecked)
        AppSettings.setPunishmentEnabled(cbPunishmentEnabled.isChecked)

        AppSettings.setEmotionDetectionEnabled(cbEmotionDetectionEnabled.isChecked)

        val qwenVlApiKey = etQwenVlApiKey.text.toString().trim()
        if (qwenVlApiKey.isNotEmpty()) {
            AppSettings.setQwenVlApiKey(qwenVlApiKey)
        }

        val qwenVlBaseUrl = etQwenVlBaseUrl.text.toString().trim()
        if (qwenVlBaseUrl.isNotEmpty()) {
            AppSettings.setQwenVlBaseUrl(qwenVlBaseUrl)
        }

        val qwenVlModel = etQwenVlModel.text.toString().trim()
        if (qwenVlModel.isNotEmpty()) {
            AppSettings.setQwenVlModel(qwenVlModel)
        }

        val socketUrl = etSocketServerUrl.text.toString().trim()
        if (socketUrl.isNotEmpty()) {
            AppSettings.setSocketServerUrl(socketUrl)
        }

        val agentUrl = etAgentBaseUrl.text.toString().trim()
        if (agentUrl.isNotEmpty()) {
            AppSettings.setLlmBaseUrl(agentUrl)
        }

        AppSettings.setDebugModeEnabled(cbDebugModeEnabled.isChecked)
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, FeatureSettingsActivity::class.java)
            context.startActivity(intent)
        }
    }
}
