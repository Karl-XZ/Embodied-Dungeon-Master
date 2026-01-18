package com.xmov.metahuman.app

import android.content.Context
import android.content.SharedPreferences

/**
 * App 级别配置存储（SharedPreferences）
 *
 * - Xmov 数字人 SDK：appId/appSecret
 * - LLM（APIMart/OpenAI-Compatible）：apiKey/baseUrl/model
 */
object AppSettings {
    private const val PREF_NAME = "metahuman_settings"

    private const val KEY_XMOV_APP_ID = "xmov_app_id"
    private const val KEY_XMOV_APP_SECRET = "xmov_app_secret"

    private const val KEY_LLM_API_KEY = "llm_api_key"
    private const val KEY_LLM_BASE_URL = "llm_base_url"
    private const val KEY_LLM_MODEL = "llm_model"
    private const val KEY_LLM_TEMPERATURE = "llm_temperature"

    private const val DEFAULT_BASE_URL = "https://api.apimart.ai/v1"
    private const val DEFAULT_MODEL = "gpt-4o"
    private const val DEFAULT_TEMPERATURE = 0.7f

    // 图片生成配置
    private const val KEY_IMAGE_GEN_ENABLED = "image_gen_enabled"
    private const val KEY_IMAGE_GEN_MODEL = "image_gen_model"

    // 动作识别配置
    private const val KEY_POSE_DETECTION_ENABLED = "pose_detection_enabled"
    private const val KEY_PUNISHMENT_ENABLED = "punishment_enabled"

    // 情绪识别配置
    private const val KEY_EMOTION_DETECTION_ENABLED = "emotion_detection_enabled"
    private const val KEY_QWEN_VL_API_KEY = "qwen_vl_api_key"
    private const val KEY_QWEN_VL_BASE_URL = "qwen_vl_base_url"
    private const val KEY_QWEN_VL_MODEL = "qwen_vl_model"

    // 网络同步配置
    private const val KEY_SOCKET_SERVER_URL = "socket_server_url"

    // 开发模式配置
    private const val KEY_DEBUG_MODE_ENABLED = "debug_mode_enabled"

    // 默认配置
    private const val DEFAULT_XMOV_APP_ID = "9e366289805f4fd7ad9a6879bf64c698"
    private const val DEFAULT_XMOV_APP_SECRET = "fca83e091ace40d59acb2c812e469499"
    private const val DEFAULT_LLM_API_KEY = "sk-84Mk1vNLjpdCfrCDIpvpU3VD098bUtTLNIrNGxLqb67MXZSD"
    private const val DEFAULT_SOCKET_SERVER_URL = "http://localhost:3000"

    // Qwen-VL API 默认配置（阿里云百炼）
    private const val DEFAULT_QWEN_VL_API_KEY = "sk-f4fa1e490f78469eb4433266814d28d2"
    private const val DEFAULT_QWEN_VL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    private const val DEFAULT_QWEN_VL_MODEL = "qwen3-vl-plus"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureInit() {
        check(::prefs.isInitialized) {
            "AppSettings not initialized. Call AppSettings.init() in Application."
        }
    }

    fun setXmovAppId(value: String) {
        ensureInit()
        prefs.edit().putString(KEY_XMOV_APP_ID, value).apply()
    }

    fun getXmovAppId(): String? {
        ensureInit()
        return prefs.getString(KEY_XMOV_APP_ID, DEFAULT_XMOV_APP_ID)
    }

    fun setXmovAppSecret(value: String) {
        ensureInit()
        prefs.edit().putString(KEY_XMOV_APP_SECRET, value).apply()
    }

    fun getXmovAppSecret(): String? {
        ensureInit()
        return prefs.getString(KEY_XMOV_APP_SECRET, DEFAULT_XMOV_APP_SECRET)
    }

    fun setLlmApiKey(value: String) {
        ensureInit()
        prefs.edit().putString(KEY_LLM_API_KEY, value).apply()
    }

    fun getLlmApiKey(): String? {
        ensureInit()
        return prefs.getString(KEY_LLM_API_KEY, DEFAULT_LLM_API_KEY)
    }

    fun setLlmBaseUrl(value: String) {
        ensureInit()
        val v = value.trim().trimEnd('/')
        prefs.edit().putString(KEY_LLM_BASE_URL, v).apply()
    }

    fun getLlmBaseUrl(): String {
        ensureInit()
        return prefs.getString(KEY_LLM_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setLlmModel(value: String) {
        ensureInit()
        prefs.edit().putString(KEY_LLM_MODEL, value.trim()).apply()
    }

    fun getLlmModel(): String {
        ensureInit()
        return prefs.getString(KEY_LLM_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setLlmTemperature(value: Float) {
        ensureInit()
        prefs.edit().putFloat(KEY_LLM_TEMPERATURE, value).apply()
    }

    fun getLlmTemperature(): Float {
        ensureInit()
        return prefs.getFloat(KEY_LLM_TEMPERATURE, DEFAULT_TEMPERATURE)
    }

    fun hasLlmConfig(): Boolean = !getLlmApiKey().isNullOrBlank()

    fun clearLlmApiKey() {
        ensureInit()
        prefs.edit().remove(KEY_LLM_API_KEY).apply()
    }

    // ========== 图片生成配置 ==========

    fun setImageGenEnabled(enabled: Boolean) {
        ensureInit()
        prefs.edit().putBoolean(KEY_IMAGE_GEN_ENABLED, enabled).apply()
    }

    fun isImageGenEnabled(): Boolean {
        ensureInit()
        return prefs.getBoolean(KEY_IMAGE_GEN_ENABLED, true)
    }

    fun setImageGenModel(model: String) {
        ensureInit()
        prefs.edit().putString(KEY_IMAGE_GEN_MODEL, model).apply()
    }

    fun getImageGenModel(): String {
        ensureInit()
        return prefs.getString(KEY_IMAGE_GEN_MODEL, "gemini-2.5-flash-image-preview") ?: "gemini-2.5-flash-image-preview"
    }

    // ========== 动作识别配置 ==========

    fun setPoseDetectionEnabled(enabled: Boolean) {
        ensureInit()
        prefs.edit().putBoolean(KEY_POSE_DETECTION_ENABLED, enabled).apply()
    }

    fun isPoseDetectionEnabled(): Boolean {
        ensureInit()
        return prefs.getBoolean(KEY_POSE_DETECTION_ENABLED, false)
    }

    fun setPunishmentEnabled(enabled: Boolean) {
        ensureInit()
        prefs.edit().putBoolean(KEY_PUNISHMENT_ENABLED, enabled).apply()
    }

    fun isPunishmentEnabled(): Boolean {
        ensureInit()
        return prefs.getBoolean(KEY_PUNISHMENT_ENABLED, true)
    }

    // ========== 情绪识别配置 ==========

    fun setEmotionDetectionEnabled(enabled: Boolean) {
        ensureInit()
        prefs.edit().putBoolean(KEY_EMOTION_DETECTION_ENABLED, enabled).apply()
    }

    fun isEmotionDetectionEnabled(): Boolean {
        ensureInit()
        return prefs.getBoolean(KEY_EMOTION_DETECTION_ENABLED, false)
    }

    fun setQwenVlApiKey(key: String) {
        ensureInit()
        prefs.edit().putString(KEY_QWEN_VL_API_KEY, key.trim()).apply()
    }

    fun getQwenVlApiKey(): String {
        ensureInit()
        return prefs.getString(KEY_QWEN_VL_API_KEY, DEFAULT_QWEN_VL_API_KEY) ?: DEFAULT_QWEN_VL_API_KEY
    }

    fun setQwenVlBaseUrl(url: String) {
        ensureInit()
        prefs.edit().putString(KEY_QWEN_VL_BASE_URL, url.trimEnd('/')).apply()
    }

    fun getQwenVlBaseUrl(): String {
        ensureInit()
        return prefs.getString(KEY_QWEN_VL_BASE_URL, DEFAULT_QWEN_VL_BASE_URL) ?: DEFAULT_QWEN_VL_BASE_URL
    }

    fun setQwenVlModel(model: String) {
        ensureInit()
        prefs.edit().putString(KEY_QWEN_VL_MODEL, model.trim()).apply()
    }

    fun getQwenVlModel(): String {
        ensureInit()
        return prefs.getString(KEY_QWEN_VL_MODEL, DEFAULT_QWEN_VL_MODEL) ?: DEFAULT_QWEN_VL_MODEL
    }

    fun hasQwenVlConfig(): Boolean = !getQwenVlApiKey().isNullOrBlank()

    // ========== 网络同步配置 ==========

    fun setSocketServerUrl(url: String) {
        ensureInit()
        prefs.edit().putString(KEY_SOCKET_SERVER_URL, url.trimEnd('/')).apply()
    }

    fun getSocketServerUrl(): String {
        ensureInit()
        return prefs.getString(KEY_SOCKET_SERVER_URL, DEFAULT_SOCKET_SERVER_URL) ?: DEFAULT_SOCKET_SERVER_URL
    }

    // ========== 开发模式配置 ==========

    fun setDebugModeEnabled(enabled: Boolean) {
        ensureInit()
        prefs.edit().putBoolean(KEY_DEBUG_MODE_ENABLED, enabled).apply()
    }

    fun isDebugModeEnabled(): Boolean {
        ensureInit()
        return prefs.getBoolean(KEY_DEBUG_MODE_ENABLED, false)
    }
}
