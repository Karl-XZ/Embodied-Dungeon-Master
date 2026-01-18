package com.xmov.metahuman.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xmov.metahuman.app.llm.ApimartClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var startBtn: Button
    private lateinit var btnTestLlm: Button

    private lateinit var appIdEdit: EditText
    private lateinit var appSecretEdit: EditText

    private lateinit var llmApiKeyEdit: EditText
    private lateinit var llmBaseUrlEdit: EditText
    private lateinit var llmModelEdit: EditText

    private val llmClient = ApimartClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        initView()
        bindSavedValues()
        setupActions()
    }

    private fun initView() {
        btnBack = findViewById(R.id.btn_back)
        startBtn = findViewById(R.id.start_btn)
        btnTestLlm = findViewById(R.id.btn_test_llm)

        appIdEdit = findViewById(R.id.app_id_edit)
        appSecretEdit = findViewById(R.id.app_secret_edit)

        llmApiKeyEdit = findViewById(R.id.llm_api_key_edit)
        llmBaseUrlEdit = findViewById(R.id.llm_base_url_edit)
        llmModelEdit = findViewById(R.id.llm_model_edit)
    }

    private fun bindSavedValues() {
        AppSettings.getXmovAppId()?.let { appIdEdit.setText(it) }
        AppSettings.getXmovAppSecret()?.let { appSecretEdit.setText(it) }

        AppSettings.getLlmApiKey()?.let { llmApiKeyEdit.setText(it) }
        llmBaseUrlEdit.setText(AppSettings.getLlmBaseUrl())
        llmModelEdit.setText(AppSettings.getLlmModel())
    }

    private fun setupActions() {
        // 顶部返回按钮（防止某些机型无系统返回键/沉浸模式下“出不来”）
        btnBack.setOnClickListener { finish() }

        startBtn.setOnClickListener {
            val appId = appIdEdit.text.toString().trim()
            val appSecret = appSecretEdit.text.toString().trim()

            val llmApiKey = llmApiKeyEdit.text.toString().trim()
            val llmBaseUrl = llmBaseUrlEdit.text.toString().trim()
            val llmModel = llmModelEdit.text.toString().trim()

            if (appId.isNotEmpty()) AppSettings.setXmovAppId(appId)
            if (appSecret.isNotEmpty()) AppSettings.setXmovAppSecret(appSecret)

            if (llmApiKey.isNotEmpty()) AppSettings.setLlmApiKey(llmApiKey)
            if (llmBaseUrl.isNotEmpty()) AppSettings.setLlmBaseUrl(llmBaseUrl)
            if (llmModel.isNotEmpty()) AppSettings.setLlmModel(llmModel)

            startMainActivity(
                appId = AppSettings.getXmovAppId().orEmpty(),
                appSecret = AppSettings.getXmovAppSecret().orEmpty()
            )
        }

        btnTestLlm.setOnClickListener {
            val apiKey = llmApiKeyEdit.text.toString().trim()
            val baseUrl = llmBaseUrlEdit.text.toString().trim().ifEmpty { AppSettings.getLlmBaseUrl() }
            val model = llmModelEdit.text.toString().trim().ifEmpty { AppSettings.getLlmModel() }

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请先填写 APIMart API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                Toast.makeText(this@SettingsActivity, "正在测试...", Toast.LENGTH_SHORT).show()

                val result = withContext(Dispatchers.IO) {
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", "You are a helpful assistant."))
                        put(JSONObject().put("role", "user").put("content", "回复我：OK"))
                    }
                    llmClient.chatCompletions(
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        messages = messages,
                        temperature = 0.0,
                        maxTokens = 32
                    )
                }

                result.fold(
                    onSuccess = { content ->
                        val show = if (content.length > 60) content.take(60) + "..." else content
                        Toast.makeText(this@SettingsActivity, "LLM 返回：$show", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@SettingsActivity, "测试失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun startMainActivity(appId: String, appSecret: String) {
        val intent = Intent(this, MenuActivity::class.java)
        intent.putExtra("app_id", appId)
        intent.putExtra("app_secret", appSecret)
        startActivity(intent)
        finish()
    }
}
