package com.xmov.metahuman.app.interaction

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.xmov.metahuman.app.AppSettings
import com.xmov.metahuman.app.emotion.GyroscopeMonitor
import com.xmov.metahuman.app.pose.Action as PoseAction
import com.xmov.metahuman.app.pose.PoseCameraManager
import com.xmov.metahuman.app.utils.ErrorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 互动鉴定系统
 * 玩家通过动作（陀螺仪/摄像头）替代骰点判定
 */
class InteractionCheckSystem(
    private val context: Context,
    private val poseCameraManager: PoseCameraManager
) {

    private val TAG = "InteractionCheckSystem"
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val gyroMonitor = GyroscopeMonitor(context)

    // 当前鉴定状态
    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState.asStateFlow()

    // 鉴定结果
    private val _checkResult = MutableStateFlow<CheckResult?>(null)
    val checkResult: StateFlow<CheckResult?> = _checkResult.asStateFlow()

    // 当前鉴定配置
    private var currentCheckConfig: CheckConfig? = null

    private var checkJob: Job? = null

    // 重试次数
    private var retryCount = 0
    private val maxRetryCount = 1

    /**
     * 检查权限
     */
    fun checkPermissions(): PermissionStatus {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        return when {
            hasCamera && hasRecordAudio -> PermissionStatus.ALL_GRANTED
            hasCamera -> PermissionStatus.CAMERA_ONLY
            hasRecordAudio -> PermissionStatus.AUDIO_ONLY
            else -> PermissionStatus.NONE
        }
    }

    /**
     * 初始化
     */
    suspend fun init(): Boolean {
        val gyroInitialized = gyroMonitor.init()
        Log.d(TAG, "Gyroscope initialized: $gyroInitialized")
        return gyroInitialized
    }

    /**
     * 启动互动鉴定
     * @param checkType 鉴定类型
     * @param difficulty 难度 (1-10)
     * @param targetAction 目标动作描述
     */
    suspend fun startInteractionCheck(
        checkType: InteractionCheckType,
        difficulty: Int,
        targetAction: String,
        isRetry: Boolean = false
    ): Boolean {
        if (_checkState.value is CheckState.Checking) {
            Log.w(TAG, "Already checking, cannot start new check")
            return false
        }

        // 如果不是重试，重置重试次数
        if (!isRetry) {
            retryCount = 0
        }

        // 检查权限
        val permissionStatus = checkPermissions()
        if (permissionStatus == PermissionStatus.NONE) {
            val error = "缺少必要的传感器权限"
            _checkState.value = CheckState.Error(error)
            if (AppSettings.isDebugModeEnabled()) {
                Handler(Looper.getMainLooper()).post {
                    ErrorDialog.showError(context, title = "互动鉴定失败", errorMessage = error)
                }
            }
            return false
        }

        val config = CheckConfig(
            type = checkType,
            difficulty = difficulty,
            targetAction = targetAction,
            permissionStatus = permissionStatus
        )
        currentCheckConfig = config

        _checkState.value = CheckState.Checking(config)
        _checkResult.value = null

        Log.d(TAG, "Starting interaction check: type=$checkType, difficulty=$difficulty, action=$targetAction")

        // 启动对应的检测
        when (checkType) {
            InteractionCheckType.GYROSCOPE -> startGyroscopeCheck(config)
            InteractionCheckType.POSE -> startPoseCheck(config)
            InteractionCheckType.HYBRID -> startHybridCheck(config)
        }

        return true
    }

    /**
     * 启动陀螺仪检测
     */
    private suspend fun startGyroscopeCheck(config: CheckConfig) {
        gyroMonitor.startListening()

        // 记录本次检测开始时间
        var checkStartTime = System.currentTimeMillis()

        // 监听动作
        checkJob = scope.launch {
            var detectedActions = mutableListOf<String>()
            var successCount = 0
            var failCount = 0

            while (_checkState.value is CheckState.Checking) {
                // 超时检查 - 10秒（从本次检测开始时间计算）
                if (System.currentTimeMillis() - checkStartTime > 10000) {
                    gyroMonitor.stopListening()
                    checkJob?.cancel()

                    // 询问是否重试
                    if (retryCount < maxRetryCount) {
                        Handler(Looper.getMainLooper()).post {
                            showRetryDialog(config, "10秒内未能识别到动作，是否重新尝试？")
                        }
                        return@launch
                    } else {
                        // 最终失败
                        val result = CheckResult(
                            success = false,
                            method = CheckMethod.GYROSCOPE,
                            actions = emptyList(),
                            timestamp = System.currentTimeMillis()
                        )
                        _checkResult.value = result
                        _checkState.value = CheckState.Failed(result)
                        Log.d(TAG, "互动鉴定最终失败（超时）")
                        return@launch
                    }
                }

                // 检测动作
                val action = gyroMonitor.detectedAction.value
                if (action != null && !detectedActions.contains(action.name)) {
                    detectedActions.add(action.name)
                    Log.d(TAG, "Detected gyroscope action: $action")

                    // 根据鉴定类型判断成功/失败
                    val isSuccessful = evaluateGyroAction(action, config.targetAction, config.difficulty)

                    if (isSuccessful) {
                        successCount++
                    } else {
                        failCount++
                    }

                    // 检查是否完成
                    if (successCount >= config.difficulty) {
                        completeCheck(success = true, method = CheckMethod.GYROSCOPE, detectedActions)
                        gyroMonitor.stopListening()
                        break
                    } else if (failCount >= 3) {
                        completeCheck(success = false, method = CheckMethod.GYROSCOPE, detectedActions)
                        gyroMonitor.stopListening()
                        break
                    }

                    delay(500)
                }

                delay(100)
            }
        }
    }

    /**
     * 启动姿态检测
     */
    private suspend fun startPoseCheck(config: CheckConfig) {
        if (config.permissionStatus != PermissionStatus.ALL_GRANTED &&
            config.permissionStatus != PermissionStatus.CAMERA_ONLY) {
            val error = "缺少摄像头权限，无法进行姿态鉴定"
            _checkState.value = CheckState.Error(error)
            if (AppSettings.isDebugModeEnabled()) {
                Handler(Looper.getMainLooper()).post {
                    ErrorDialog.showError(context, title = "互动鉴定失败", errorMessage = error)
                }
            }
            return
        }

        // 启动摄像头
        val cameraStarted = poseCameraManager.startCamera()
        if (!cameraStarted) {
            val error = "摄像头启动失败"
            _checkState.value = CheckState.Error(error)
            if (AppSettings.isDebugModeEnabled()) {
                Handler(Looper.getMainLooper()).post {
                    ErrorDialog.showError(context, title = "互动鉴定失败", errorMessage = error)
                }
            }
            return
        }

        // 记录本次检测开始时间
        var checkStartTime = System.currentTimeMillis()

        checkJob = scope.launch {
            var successCount = 0
            var failCount = 0

            while (_checkState.value is CheckState.Checking) {
                // 超时检查 - 10秒（从本次检测开始时间计算）
                if (System.currentTimeMillis() - checkStartTime > 10000) {
                    poseCameraManager.stopCamera()
                    checkJob?.cancel()

                    // 询问是否重试
                    if (retryCount < maxRetryCount) {
                        Handler(Looper.getMainLooper()).post {
                            showRetryDialog(config, "10秒内未能识别到动作，是否重新尝试？")
                        }
                        return@launch
                    } else {
                        // 最终失败
                        val result = CheckResult(
                            success = false,
                            method = CheckMethod.POSE,
                            actions = emptyList(),
                            timestamp = System.currentTimeMillis()
                        )
                        _checkResult.value = result
                        _checkState.value = CheckState.Failed(result)
                        Log.d(TAG, "互动鉴定最终失败（超时）")
                        return@launch
                    }
                }

                // 检测姿态
                val pose = poseCameraManager.detectedPose.value
                val action = pose?.action

                if (action != null) {
                    val isSuccessful = evaluatePoseAction(action, config.targetAction, config.difficulty)

                    if (isSuccessful) {
                        successCount++
                    } else {
                        failCount++
                    }

                    Log.d(TAG, "Pose detection: action=$action, success=$isSuccessful")

                    if (successCount >= config.difficulty) {
                        completeCheck(success = true, method = CheckMethod.POSE, listOf(action.name))
                        poseCameraManager.stopCamera()
                        break
                    } else if (failCount >= 3) {
                        completeCheck(success = false, method = CheckMethod.POSE, listOf(action.name))
                        poseCameraManager.stopCamera()
                        break
                    }

                    delay(1000)
                }

                delay(100)
            }
        }
    }

    /**
     * 启动混合检测（陀螺仪+姿态）
     */
    private suspend fun startHybridCheck(config: CheckConfig) {
        if (config.permissionStatus != PermissionStatus.ALL_GRANTED) {
            Log.w(TAG, "Not all permissions, falling back to gyroscope only")
            startGyroscopeCheck(config)
            return
        }

        gyroMonitor.startListening()
        poseCameraManager.startCamera()

        // 记录本次检测开始时间
        var checkStartTime = System.currentTimeMillis()

        checkJob = scope.launch {
            var gyroSuccess = 0
            var poseSuccess = 0
            var failCount = 0

            while (_checkState.value is CheckState.Checking) {
                // 超时检查 - 10秒（从本次检测开始时间计算）
                if (System.currentTimeMillis() - checkStartTime > 10000) {
                    gyroMonitor.stopListening()
                    poseCameraManager.stopCamera()
                    checkJob?.cancel()

                    // 询问是否重试
                    if (retryCount < maxRetryCount) {
                        Handler(Looper.getMainLooper()).post {
                            showRetryDialog(config, "10秒内未能识别到动作，是否重新尝试？")
                        }
                        return@launch
                    } else {
                        // 最终失败
                        val result = CheckResult(
                            success = false,
                            method = CheckMethod.HYBRID,
                            actions = emptyList(),
                            timestamp = System.currentTimeMillis()
                        )
                        _checkResult.value = result
                        _checkState.value = CheckState.Failed(result)
                        Log.d(TAG, "互动鉴定最终失败（超时）")
                        return@launch
                    }
                }

                // 检测陀螺仪动作
                val gyroAction = gyroMonitor.detectedAction.value
                if (gyroAction != null) {
                    if (evaluateGyroAction(gyroAction, config.targetAction, config.difficulty)) {
                        gyroSuccess++
                        delay(1000)
                    }
                }

                // 检测姿态
                val pose = poseCameraManager.detectedPose.value
                val poseAction = pose?.action
                if (poseAction != null) {
                    if (evaluatePoseAction(poseAction, config.targetAction, config.difficulty)) {
                        poseSuccess++
                        delay(1000)
                    }
                }

                Log.d(TAG, "Hybrid check: gyroSuccess=$gyroSuccess, poseSuccess=$poseSuccess")

                // 检查是否完成
                if (gyroSuccess >= 1 && poseSuccess >= 1) {
                    completeCheck(success = true, method = CheckMethod.HYBRID, listOf("gyroscope", "pose"))
                    gyroMonitor.stopListening()
                    poseCameraManager.stopCamera()
                    break
                } else if (failCount >= 5) {
                    completeCheck(success = false, method = CheckMethod.HYBRID, listOf("failed"))
                    gyroMonitor.stopListening()
                    poseCameraManager.stopCamera()
                    break
                }

                delay(100)
            }
        }
    }

    /**
     * 评估陀螺仪动作是否符合要求
     */
    private fun evaluateGyroAction(
        action: com.xmov.metahuman.app.emotion.GyroAction,
        targetAction: String,
        difficulty: Int
    ): Boolean {
        return when {
            // 跳跃类动作
            targetAction.contains("跳") || targetAction.contains("跃") ->
                action == com.xmov.metahuman.app.emotion.GyroAction.SHAKE

            // 确认类动作
            targetAction.contains("确认") || targetAction.contains("同意") || targetAction.contains("是") ->
                action == com.xmov.metahuman.app.emotion.GyroAction.NOD

            // 否定类动作
            targetAction.contains("拒绝") || targetAction.contains("不同意") || targetAction.contains("否") ->
                action == com.xmov.metahuman.app.emotion.GyroAction.SHAKE_HEAD

            // 攀爬/举起类
            targetAction.contains("攀") || targetAction.contains("爬") || targetAction.contains("举") ||
            targetAction.contains("抬高") || targetAction.contains("起跳") ->
                action == com.xmov.metahuman.app.emotion.GyroAction.LIFT

            // 下蹲/下降类
            targetAction.contains("蹲") || targetAction.contains("下") || targetAction.contains("降") ||
            targetAction.contains("趴") ->
                action == com.xmov.metahuman.app.emotion.GyroAction.LOWER

            // 默认：任何动作都算尝试
            else -> true
        }
    }

    /**
     * 评估姿态动作是否符合要求
     */
    private fun evaluatePoseAction(
        action: PoseAction,
        targetAction: String,
        difficulty: Int
    ): Boolean {
        return when {
            // 跳跃/起跳
            targetAction.contains("跳") || targetAction.contains("跃") ||
            targetAction.contains("起跳") -> action == PoseAction.JUMP

            // 举手/挥舞
            targetAction.contains("手") || targetAction.contains("挥") ||
            targetAction.contains("举") -> action == PoseAction.RAISE_HANDS

            // 深蹲
            targetAction.contains("蹲") || targetAction.contains("弯腰") -> action == PoseAction.SQUAT

            // 站立/直立
            targetAction.contains("站") || targetAction.contains("立") -> action == PoseAction.IDLE

            // 默认：任何检测到的动作都算尝试
            else -> true
        }
    }

    /**
     * 完成鉴定
     */
    private fun completeCheck(
        success: Boolean,
        method: CheckMethod,
        detectedActions: List<String>
    ) {
        val result = CheckResult(
            success = success,
            method = method,
            actions = detectedActions,
            timestamp = System.currentTimeMillis()
        )

        _checkResult.value = result
        _checkState.value = if (success) {
            CheckState.Completed(result)
        } else {
            CheckState.Failed(result)
        }

        Log.d(TAG, "Check completed: success=$success, method=$method, actions=$detectedActions")
    }

    /**
     * 取消鉴定
     */
    fun cancelCheck() {
        checkJob?.cancel()
        gyroMonitor.stopListening()
        poseCameraManager.stopCamera()
        _checkState.value = CheckState.Idle
        _checkResult.value = null
        retryCount = 0
        Log.d(TAG, "Check cancelled")
    }

    /**
     * 显示重试对话框
     */
    private fun showRetryDialog(config: CheckConfig, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("互动鉴定超时")
            .setMessage(message)
            .setPositiveButton("重新尝试") { _, _ ->
                scope.launch {
                    retryCount++
                    startInteractionCheck(config.type, config.difficulty, config.targetAction, isRetry = true)
                }
            }
            .setNegativeButton("放弃") { _, _ ->
                // 放弃，标记为失败
                val result = CheckResult(
                    success = false,
                    method = when (config.type) {
                        InteractionCheckType.GYROSCOPE -> CheckMethod.GYROSCOPE
                        InteractionCheckType.POSE -> CheckMethod.POSE
                        InteractionCheckType.HYBRID -> CheckMethod.HYBRID
                    },
                    actions = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
                _checkResult.value = result
                _checkState.value = CheckState.Failed(result)
                retryCount = 0
                Log.d(TAG, "用户放弃互动鉴定")
            }
            .setCancelable(true)
            .show()
    }

    /**
     * 释放资源
     */
    fun release() {
        cancelCheck()
        gyroMonitor.release()
    }
}

/**
 * 鉴定类型
 */
enum class InteractionCheckType {
    GYROSCOPE,  // 仅陀螺仪
    POSE,       // 仅姿态
    HYBRID      // 混合
}

/**
 * 检查状态
 */
sealed class CheckState {
    data object Idle : CheckState()
    data class Checking(val config: CheckConfig) : CheckState()
    data class Completed(val result: CheckResult) : CheckState()
    data class Failed(val result: CheckResult) : CheckState()
    data class Error(val message: String) : CheckState()
}

/**
 * 鉴定配置
 */
data class CheckConfig(
    val type: InteractionCheckType,
    val difficulty: Int,
    val targetAction: String,
    val permissionStatus: PermissionStatus
)

/**
 * 鉴定结果
 */
data class CheckResult(
    val success: Boolean,
    val method: CheckMethod,
    val actions: List<String>,
    val timestamp: Long
)

/**
 * 鉴定方法
 */
enum class CheckMethod {
    GYROSCOPE,
    POSE,
    HYBRID
}

/**
 * 权限状态
 */
enum class PermissionStatus {
    ALL_GRANTED,
    CAMERA_ONLY,
    AUDIO_ONLY,
    NONE
}
