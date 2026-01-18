package com.xmov.metahuman.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xmov.metahuman.app.XmlContentStreamer.createStreamingSegments
import com.xmov.metahuman.sdk.IAvatarListener
import com.xmov.metahuman.sdk.IXmovAvatar
import com.xmov.metahuman.sdk.data.InitConfig
import com.xmov.metahuman.sdk.data.SDKMessage
import com.xmov.metahuman.sdk.data.SDKNetworkInfo
import com.xmov.metahuman.sdk.data.SDKStatus
import com.xmov.metahuman.sdk.impl.data.IRawEventFrameData
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import d.b

class MainActivity : AppCompatActivity() {
    var container: FrameLayout? = null
    var avatarLayout: FrameLayout? = null

    private var fpsUpdateHandler: Handler? = null
    private var fpsUpdateRunnable: Runnable? = null

    private var initBtn: Button? = null
    private var releaseBtn: Button? = null
    private var speakBtn: Button? = null
    private var idleBtn: Button? = null
    private var listenBtn: Button? = null
    private var thinkBtn: Button? = null
    private var interactiveBtn: Button? = null

    private var offlineBtn: Button? = null

    private var reconnectBtn: Button? = null

    private var subtitleSwitchBtn: Button? = null
    private var preCacheBtn: Button? = null
    private var subtitleTv: TextView? = null
    private var sessionTv: TextView? = null
    private var frameIndexTv: TextView? = null
    private var debugMsgRcyView: RecyclerView? = null
    private var debugMsgAdapter: SimpleTextAdapter? = null
    private val debugMsgList: MutableList<String?> = LinkedList<String?>()


    private val mainHandler = Handler(Looper.getMainLooper())
    private var mFrameIndex = 0
    private val fpsDisplayEnabled = true

    private var xmovAvatar: IXmovAvatar? = null
    private var appId: String? = null
    private var appSecret: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        handleIntentData()
        container = findViewById(android.R.id.content)
        avatarLayout = findViewById(R.id.avatar_layout)
        GlideBackgroundLoader.loadLayoutBackground(this, findViewById(R.id.cl_root), R.drawable.bg_texture)

        initBtn = findViewById(R.id.init_btn)
        releaseBtn = findViewById(R.id.release_btn)
        speakBtn = findViewById(R.id.speak_btn)
        offlineBtn = findViewById(R.id.offline_btn)
        reconnectBtn = findViewById(R.id.reconnect_btn)
        listenBtn = findViewById(R.id.listen_btn)
        thinkBtn = findViewById(R.id.think_btn)
        interactiveBtn = findViewById(R.id.interactive_btn)
        preCacheBtn = findViewById(R.id.pre_cache_btn)
        subtitleSwitchBtn = findViewById(R.id.subtitle_btn)
        subtitleTv = findViewById(R.id.subtitle_tv)
        sessionTv = findViewById(R.id.session_tv)
        frameIndexTv = findViewById(R.id.frameIndex_tv)
        debugMsgRcyView = findViewById(R.id.debug_msg_tv)
        debugMsgAdapter = SimpleTextAdapter(debugMsgList)
        debugMsgRcyView?.setAdapter(debugMsgAdapter)
        debugMsgRcyView?.setLayoutManager(
            LinearLayoutManager(
                this,
                LinearLayoutManager.VERTICAL,
                false
            )
        )

        preCacheBtn?.setOnClickListener {
           preCache()
        }

        initBtn?.setOnClickListener { v: View? -> xmovInit() }

        releaseBtn?.setOnClickListener { v: View? ->
            xmovAvatar?.destroy()
            xmovAvatar = null
        }

        speakBtn?.setOnClickListener { v: View? ->
            speak()
            //audioInputSpeak()
        }

        idleBtn?.setOnClickListener { xmovAvatar?.idle() }


        listenBtn?.setOnClickListener { xmovAvatar?.listen() }


        thinkBtn?.setOnClickListener { xmovAvatar?.think() }

        offlineBtn?.setOnClickListener { xmovAvatar?.switchModel(true) }

        reconnectBtn?.setOnClickListener {
            xmovAvatar?.switchModel(false)
        }

        interactiveBtn?.setOnClickListener { xmovAvatar?.interactiveidle() }

        subtitleSwitchBtn?.tag = true
        subtitleSwitchBtn?.setOnClickListener {
            val tag = subtitleSwitchBtn?.tag
            var isOpen = false
            if (tag is Boolean) {
                isOpen = !tag
            }

            subtitleTv?.visibility = if (isOpen) View.VISIBLE else View.GONE
            subtitleTv?.text = ""

            subtitleSwitchBtn?.tag = isOpen
            subtitleSwitchBtn?.text = if (isOpen) "字幕已开启" else "字幕已关闭"
        }

        initFpsDisplay()
    }

    private fun handleIntentData() {
        val extras = intent.extras
        if (extras != null) {
            appId = extras.getString("app_id")
            appSecret = extras.getString("app_secret")
        }
    }

    override fun onResume() {
        super.onResume()
        xmovAvatar?.onResume()
    }

    override fun onPause() {
        super.onPause()
        xmovAvatar?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        xmovAvatar?.destroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
    @SuppressLint("SetTextI18n")
    private fun initFpsDisplay() {

        // 初始化FPS更新处理器
        fpsUpdateHandler = Handler(Looper.getMainLooper())
        fpsUpdateRunnable = object : Runnable {
            override fun run() {
                updateFrameIndex()
                if (fpsDisplayEnabled) {
                    fpsUpdateHandler?.postDelayed(this, 1000) // 每500ms更新一次
                }
            }
        }

        // 开始FPS更新
        if (fpsDisplayEnabled) {
            fpsUpdateRunnable?.let {
                fpsUpdateHandler?.post(it)
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun updateFrameIndex() {
        frameIndexTv?.text = "当前帧：$mFrameIndex"
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun preCache() {
        if (appId.isNullOrEmpty() || appSecret.isNullOrEmpty()) {
            toast("appId 或 appSecret 为空，无法预缓存")
            return
        }
        
        IXmovAvatar.newInstance().preCache(
            this,
            appId!!,
            appSecret!!,
            "https://nebula-agent.xingyun3d.com/user/v1/ttsa/precache"
        ) {
            toast("预缓存完成")
        }
    }

    /**
     * 数字人初始化
     */
    private fun xmovInit() {
        if (appId.isNullOrEmpty() || appSecret.isNullOrEmpty()) {
            toast("appId 或 appSecret 为空，无法初始化")
            return
        }
        val initConfig = InitConfig()
        //initConfig.gatewayServer = "https://pre-nebula-agent.xingyun3d.com/user/v1/ttsa/session"
        initConfig.gatewayServer = "https://nebula-agent.xingyun3d.com/user/v1/ttsa/session"
        val config = createConfigJson()
        var jsonObj: JSONObject
        try {
            jsonObj = JSONObject(config)
            initConfig.appId = appId
            initConfig.appSecret = appSecret
            initConfig.config = jsonObj.optJSONObject("config")?.toString()
        } catch (e: JSONException) {
            throw e
        }

        Log.d(TAG, "xmov_sdk init Config=$jsonObj")

        xmovAvatar?.destroy()
        xmovAvatar = IXmovAvatar.newInstance()

        xmovAvatar?.init(this, avatarLayout!!, initConfig, object : IAvatarListener {
            override fun onInitEvent(code: Int, message: String?) {
                Log.d(TAG, "onInitEvent code:$code,message:$message")
                toast("onInitEvent code:$code,message:$message")
            }

            override fun onWidgetEvent(widgetData: IRawEventFrameData?) {
                Log.d(TAG, "onWidgetEvent widgetData:$widgetData")
                handleWidgetData(widgetData)
            }

            override fun onNetworkInfo(sdkNetworkInfo: SDKNetworkInfo?) {
                val msg = "onNetworkInfo $sdkNetworkInfo"
                Log.d(TAG, msg)
                toast(msg)
            }

            override fun onMessage(sdkMessage: SDKMessage?) {
                val msg = "onMessage $sdkMessage"
                Log.d(TAG, msg)
                toast(msg)
            }

            override fun onStateChange(state: String?) {
                Log.d(TAG, "onStateChange $state")
                toast("onStateChange $state")
            }

            override fun onStatusChange(status: SDKStatus?) {
                Log.d(TAG, "onStatusChange $status")
            }

            override fun onStateRenderChange(state: String?, duration: Long) {
                Log.d(TAG, "onStateRenderChange state：$state,duration:$duration")
            }

            override fun onVoiceStateChange(status: String?) {
                Log.d(TAG, "onVoiceStateChange $status")
                toast("onVoiceStateChange $status")
            }

            override fun onDebugInfo(debugInfo: JSONObject) {
                handleDebugInfo(debugInfo)
            }

            override fun onReconnectEvent(code: Int, message: String?) {
                toast("重连：code=$code message=$message")
            }

            override fun onOfflineEvent() {
                toast("进入离线状态")
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun handleDebugInfo(debugInfo: JSONObject) {
        try {
            val type = debugInfo.getString("type")
            if (TextUtils.equals(type, "session")) {
                val sessionId = debugInfo.getString("session_id")
                Log.d(TAG, "sid:$sessionId")
                runOnUiThread { sessionTv?.text = "sid:$sessionId" }
            } else if (TextUtils.equals(type, "frame")) {
                val index = debugInfo.getInt("frame_index")
                mFrameIndex = index
            } else if (TextUtils.equals(type, "sdk_message")) {
                val showMsg = debugInfo.getString("message")
                runOnUiThread {
                    debugMsgList.add(0, showMsg)
                    debugMsgAdapter?.notifyItemInserted(0)
                    debugMsgRcyView?.smoothScrollToPosition(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleWidgetData(widgetData: IRawEventFrameData?) {
        if (widgetData != null && widgetData.event != null) {
            widgetData.event?.let { array ->
                for (i in 0..<array.length()) {
                    try {
                        val item = array.get(i)
                        when (item) {
                            is JSONObject -> handleWidgetData(item)
                            is String -> {
                                // 兼容：有些 SDK 事件数组里直接塞字符串（例如字幕文本）
                                // 之前强制 getJSONObject(i) 会出现：
                                // ValueXXX at 0 of type java.lang.String cannot be converted to JSONObject
                                showSubtitle(item)
                            }
                            else -> {
                                // 尝试当作 JSON 字符串解析
                                val s = item?.toString().orEmpty()
                                try {
                                    handleWidgetData(JSONObject(s))
                                } catch (_: Exception) {
                                    // ignore
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "handleWidgetData ex：$e")
                    }
                }
            }
        }
    }

    private fun handleWidgetData(event: JSONObject) {
        var type: String?
        try {
            type = event.getString("type")
            //字幕
            if (TextUtils.equals(type, "subtitle_on")) {
                showSubtitle(event.getString("text"))
            } else if (type == "subtitle_off") {
                hideSubtitle()
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showSubtitle(message: String?) {
        runOnUiThread { subtitleTv?.text = message }
    }

    private fun hideSubtitle() {
        showSubtitle("")
    }

    fun createConfigJson(): String {
        val reader = assets.open("demo_configs.json").reader(Charsets.UTF_8)
        val looksConfigs = try {
            JsonParser.parseReader(reader).asJsonObject
        } catch (_: Exception) {
            Log.e(TAG, "parse demo look configs error")
            null
        }

        return looksConfigs?.get("config").toString()
    }

    private fun createSpeakContent(): String {
        return assets.open("demo_speak_value.txt").reader(Charsets.UTF_8).readText()
    }

    private fun speak() {
        val view = layoutInflater.inflate(R.layout.dialog_speak, null)
        val inputEdit = view.findViewById<EditText>(R.id.speak_edit)
        val speakAll = view.findViewById<TextView>(R.id.speak_all)
        val speakPart = view.findViewById<TextView>(R.id.speak_part)
        val speakTcl = view.findViewById<TextView>(R.id.speak_tcl)
        inputEdit.setText(createSpeakContent())

        val dialog = AlertDialog.Builder(this).setTitle("")
            .setIcon(null)
            .setView(view)
            .show()

        speakAll.setOnClickListener {
            val content = inputEdit.getText().toString()
            xmovAvatar?.speak(content, isStart = true, isEnd = true)
            dialog.dismiss()
        }

        speakPart.setOnClickListener {
            val contents =
                createStreamingSegments(inputEdit.getText().toString())
            Log.d(TAG, "speak_content partSize=" + contents.size)
            for (i in contents.indices) {
                Log.d(TAG, "speak_content index=" + i + ",content=" + contents[i])
            }
            for (i in contents.indices) {
                val isStart = i == 0
                val isEnd = i == contents.size - 1
                val content = contents[i]
                mainHandler.postDelayed({ xmovAvatar?.speak(content, isStart, isEnd) }, 100)
            }
            dialog.dismiss()
        }

        speakTcl.setOnClickListener {
            val tclConfigs = JsonUtils.readAssetsJsonArray(this,"MockAudioInputsData.json")
            tclConfigs?.let { contents->
                for (i in 0 until contents.length()) {
                    val isStart = i == 0
                    val isEnd = i == contents.length() - 1
                    val content = contents.getJSONObject(i)
                    val ssl = content.optString("ssml")
                    mainHandler.postDelayed({ xmovAvatar?.speak(ssl, isStart, isEnd,content) }, 100)
                }
            }

            dialog.dismiss()
        }
    }

    /**
     * 自己控制音频数据输入
     */
    private fun audioInputSpeak() {
        val tclConfigs = JsonUtils.readAssetsJsonArray(this,"MockAudioInputsData.json")
        tclConfigs?.let { contents->
            for (i in 0 until contents.length()) {
                val isStart = i == 0
                val isEnd = i == contents.length() - 1
                val content = contents.getJSONObject(i)
                val ssl = content.optString("ssml")
                mainHandler.postDelayed({ xmovAvatar?.speak(ssl, isStart, isEnd,content) }, 100)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        fun start(context: Context, bundle: Bundle? = null) {
            val intent = Intent(context,MainActivity::class.java)
            bundle?.let {
                intent.putExtras(it)
            }
            context.startActivity(intent)
        }
    }
}