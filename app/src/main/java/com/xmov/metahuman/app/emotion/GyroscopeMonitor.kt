package com.xmov.metahuman.app.emotion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 陀螺仪/加速度计监控器
 * 用于检测玩家动作（如点头、摇头、晃动等）
 */
class GyroscopeMonitor(private val context: Context) : SensorEventListener {

    private val TAG = "GyroscopeMonitor"

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // 加速度传感器
    private var accelerometer: Sensor? = null
    private val _acceleration = MutableStateFlow(Vector3(0f, 0f, 0f))
    val acceleration: StateFlow<Vector3> = _acceleration.asStateFlow()

    // 陀螺仪传感器
    private var gyroscope: Sensor? = null
    private val _rotation = MutableStateFlow(Vector3(0f, 0f, 0f))
    val rotation: StateFlow<Vector3> = _rotation.asStateFlow()

    // 检测到的动作
    private val _detectedAction = MutableStateFlow<GyroAction?>(null)
    val detectedAction: StateFlow<GyroAction?> = _detectedAction.asStateFlow()

    // 历史数据（用于动作检测）
    private val accelerationHistory = ArrayDeque<Float>()
    private val rotationHistory = ArrayDeque<Float>()

    private var lastActionTime = 0L
    private val actionCooldown = 1000L

    // 阈值
    private val shakeThreshold = 12f      // 摇晃阈值
    private val nodThreshold = 5f         // 点头阈值
    private val shakeHeadThreshold = 8f  // 摇头阈值

    /**
     * 初始化传感器
     */
    fun init(): Boolean {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelerometer == null && gyroscope == null) {
            Log.w(TAG, "No sensors available")
            return false
        }

        Log.d(TAG, "Gyroscope monitor initialized")
        return true
    }

    /**
     * 开始监听
     */
    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.d(TAG, "Started listening to sensors")
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Stopped listening to sensors")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                _acceleration.value = Vector3(x, y, z)

                // 更新历史
                val magnitude = sqrt(x * x + y * y + z * z)
                accelerationHistory.addLast(magnitude)
                if (accelerationHistory.size > 50) {
                    accelerationHistory.removeFirst()
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                _rotation.value = Vector3(x, y, z)

                // 更新历史
                rotationHistory.addLast(abs(x) + abs(y) + abs(z))
                if (rotationHistory.size > 30) {
                    rotationHistory.removeFirst()
                }
            }
        }

        // 检测动作
        detectActions()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 检测动作
     */
    private fun detectActions() {
        val currentTime = System.currentTimeMillis()

        // 动作冷却
        if (currentTime - lastActionTime < actionCooldown) {
            return
        }

        val acc = _acceleration.value
        val rot = _rotation.value

        var detected: GyroAction? = null

        // 检测摇晃设备
        if (isShaking()) {
            detected = GyroAction.SHAKE
        }
        // 检测点头（设备前后晃动）
        else if (isNodding()) {
            detected = GyroAction.NOD
        }
        // 检测摇头（设备左右晃动）
        else if (isShakingHead()) {
            detected = GyroAction.SHAKE_HEAD
        }
        // 检测举起设备
        else if (isLifting()) {
            detected = GyroAction.LIFT
        }
        // 检测放下设备
        else if (isLowering()) {
            detected = GyroAction.LOWER
        }

        detected?.let {
            _detectedAction.value = it
            lastActionTime = currentTime
        }
    }

    /**
     * 检测摇晃
     */
    private fun isShaking(): Boolean {
        if (accelerationHistory.size < 30) return false

        val variance = calculateVariance(accelerationHistory.toList())
        return variance > shakeThreshold
    }

    /**
     * 检测点头
     */
    private fun isNodding(): Boolean {
        val acc = _acceleration.value
        return abs(acc.y) > nodThreshold && abs(acc.y) > abs(acc.x)
    }

    /**
     * 检测摇头
     */
    private fun isShakingHead(): Boolean {
        val rot = _rotation.value
        return abs(rot.z) > shakeHeadThreshold
    }

    /**
     * 检测举起设备
     */
    private fun isLifting(): Boolean {
        val acc = _acceleration.value
        return acc.z < -8f // 设备向上移动，Z轴加速度为负
    }

    /**
     * 检测放下设备
     */
    private fun isLowering(): Boolean {
        val acc = _acceleration.value
        return acc.z > 8f // 设备向下移动，Z轴加速度为正
    }

    /**
     * 计算方差
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f

        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()

        return variance
    }

    /**
     * 获取当前动作的游戏含义
     */
    fun getGameMeaning(action: GyroAction): GameActionMeaning {
        return when (action) {
            GyroAction.SHAKE -> GameActionMeaning("摇动手机", "表示否定/取消", "取消操作")
            GyroAction.NOD -> GameActionMeaning("点头", "表示肯定/同意", "确认操作")
            GyroAction.SHAKE_HEAD -> GameActionMeaning("摇头", "表示否定/不同意", "拒绝操作")
            GyroAction.LIFT -> GameActionMeaning("举起设备", "表示举手发言", "请求发言")
            GyroAction.LOWER -> GameActionMeaning("放下设备", "表示放弃", "放弃当前行动")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopListening()
    }
}

/**
 * 三维向量
 */
data class Vector3(val x: Float, val y: Float, val z: Float)

/**
 * 陀螺仪检测到的动作
 */
enum class GyroAction {
    SHAKE,       // 摇晃
    NOD,         // 点头
    SHAKE_HEAD,  // 摇头
    LIFT,        // 举起
    LOWER        // 放下
}

/**
 * 游戏动作含义
 */
data class GameActionMeaning(
    val actionName: String,
    val meaning: String,
    val gameInstruction: String
)
