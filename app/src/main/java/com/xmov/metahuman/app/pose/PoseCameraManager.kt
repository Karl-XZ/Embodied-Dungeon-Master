package com.xmov.metahuman.app.pose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机管理器 - 用于姿态检测
 */
class PoseCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val TAG = "PoseCameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private var poseDetector: PoseDetector? = null
    private var poseDetectionListener: ((Action, Float) -> Unit)? = null
    private val _detectedPose = MutableStateFlow<PoseResult?>(null)
    val detectedPose: StateFlow<PoseResult?> = _detectedPose.asStateFlow()

    private var _isPaused = false

    /**
     * 设置姿态检测监听器
     */
    fun setPoseDetectionListener(listener: (Action, Float) -> Unit) {
        this.poseDetectionListener = listener
    }

    /**
     * 启动相机
     */
    suspend fun startCamera(): Boolean {
        return try {
            cameraProvider = ProcessCameraProvider.getInstance(context).get()

            // 预览
            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .build()

            // 图像分析
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // 选择后置摄像头
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // 绑定到生命周期
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            _isCameraActive.value = true
            Log.d(TAG, "Camera started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera", e)
            false
        }
    }

    /**
     * 停止相机
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        _isCameraActive.value = false
        Log.d(TAG, "Camera stopped")
    }

    /**
     * 处理图像帧
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            if (_isPaused) {
                imageProxy.close()
                return
            }

            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                _latestFrame.value = bitmap

                // 姿态检测
                poseDetector?.detectPose(bitmap)?.let { result ->
                    _detectedPose.value = result

                    // 调用监听器
                    poseDetectionListener?.invoke(result.action, result.confidence)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 暂停检测
     */
    fun pauseDetection() {
        _isPaused = true
        Log.d(TAG, "Pose detection paused")
    }

    /**
     * 恢复检测
     */
    fun resumeDetection() {
        _isPaused = false
        Log.d(TAG, "Pose detection resumed")
    }

    /**
     * ImageProxy 转 Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val yuvImage = YuvImage(
            bytes,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, outputStream)
        val jpegBytes = outputStream.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // 旋转图像（根据相机方向）
        if (bitmap != null && imageProxy.imageInfo.rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )
        }

        return bitmap
    }

    /**
     * 设置姿态检测器
     */
    fun setPoseDetector(detector: PoseDetector) {
        this.poseDetector = detector
    }

    /**
     * 检查相机权限
     */
    fun checkCameraPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 释放资源
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}

/**
 * 惩罚系统
 */
class PunishmentSystem {
    private val TAG = "PunishmentSystem"

    private val randomActions = listOf(
        Action.SQUAT,
        Action.JUMP,
        Action.RAISE_HANDS,
        Action.DODGE,
        Action.CROUCH
    )

    /**
     * 获取随机惩罚动作
     */
    fun getRandomPunishmentAction(): Action {
        return randomActions.random()
    }

    /**
     * 根据动作执行惩罚
     */
    fun executePunishment(
        action: Action,
        failReason: String,
        gameType: com.xmov.metahuman.app.trpg.GameType
    ): PunishmentResult {
        val punishment = when (gameType) {
            com.xmov.metahuman.app.trpg.GameType.PAOTUAN -> getPunishmentForTRPG(action)
            com.xmov.metahuman.app.trpg.GameType.JUBENSHA -> getPunishmentForJubensha(action)
            com.xmov.metahuman.app.trpg.GameType.HAITANG -> getPunishmentForHaitang(action)
        }

        return PunishmentResult(
            action = punishment.action,
            count = punishment.count,
            duration = punishment.duration,
            description = buildDescription(failReason, punishment)
        )
    }

    /**
     * 跑团惩罚
     */
    private fun getPunishmentForTRPG(action: Action): PunishmentAction {
        return when (action) {
            Action.DODGE -> PunishmentAction("闪避失败", "做5个深蹲", 5, 0)
            Action.JUMP -> PunishmentAction("跳跃失败", "原地跳10次", 10, 0)
            Action.SQUAT -> PunishmentAction("下蹲失败", "保持蹲姿30秒", 0, 30)
            Action.RAISE_HANDS -> PunishmentAction("施法失败", "高举双手15秒", 0, 15)
            Action.CROUCH -> PunishmentAction("潜行失败", "做5个俯卧撑", 5, 0)
            Action.IDLE -> PunishmentAction("无动作", "深呼吸10次", 10, 0)
        }
    }

    /**
     * 剧本杀惩罚
     */
    private fun getPunishmentForJubensha(action: Action): PunishmentAction {
        return when (action) {
            Action.RAISE_HANDS -> PunishmentAction("指控失败", "自我反思30秒", 0, 30)
            else -> PunishmentAction("判断失误", "深呼吸5次", 5, 0)
        }
    }

    /**
     * 海龟汤惩罚
     */
    private fun getPunishmentForHaitang(action: Action): PunishmentAction {
        return PunishmentAction("猜错答案", "拍手10次", 10, 0)
    }

    private fun buildDescription(reason: String, punishment: PunishmentAction): String {
        return "$reason！惩罚：${punishment.description}"
    }
}

/**
 * 惩罚动作
 */
data class PunishmentAction(
    val action: String,
    val description: String,
    val count: Int,      // 重复次数（如深蹲次数）
    val duration: Long   // 持续时间（秒）
)

/**
 * 惩罚结果
 */
data class PunishmentResult(
    val action: String,
    val count: Int,
    val duration: Long,
    val description: String
) {
    /**
     * 需要计时
     */
    val isTimed: Boolean
        get() = duration > 0

    /**
     * 需要计数
     */
    val isCounted: Boolean
        get() = count > 0
}
