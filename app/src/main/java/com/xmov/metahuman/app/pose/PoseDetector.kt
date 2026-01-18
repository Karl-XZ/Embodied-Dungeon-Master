package com.xmov.metahuman.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
// import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerOptions
import com.xmov.metahuman.app.trpg.GameType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MediaPipe 姿态检测器
 * 用于识别玩家动作（下蹲、跳跃、闪避等）
 */
class PoseDetector(private val context: Context) {

    private val TAG = "PoseDetector"

    private var poseLandmarker: PoseLandmarker? = null
    private val _detectedAction = MutableStateFlow<DetectedAction?>(null)
    val detectedAction: StateFlow<DetectedAction?> = _detectedAction.asStateFlow()

    private var lastActionTime = 0L
    private val actionCooldown = 1000L // 动作冷却时间

    /**
     * 初始化检测器
     */
    fun init(): Boolean {
        return try {
            // val baseOptions = BaseOptions.builder()
            //     .setModelAssetPath("pose_landmarker_lite.task")
            //     .build()

            // val options = PoseLandmarkerOptions.builder()
            //     .setBaseOptions(baseOptions)
            //     .setRunningMode(RunningMode.IMAGE)
            //     .setNumPoses(1)
            //     .setMinPoseDetectionConfidence(0.5f)
            //     .setMinPosePresenceConfidence(0.5f)
            //     .setMinTrackingConfidence(0.5f)
            //     .build()

            // poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "Pose detector initialized successfully (stub)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize pose detector", e)
            false
        }
    }

    /**
     * 检测图片中的姿态
     */
    fun detectPose(bitmap: Bitmap): PoseResult? {
        // 暂时返回null，MediaPipe集成待完善
        Log.w(TAG, "Pose detection not implemented yet")
        return null
    }

    /**
     * 分析姿态，识别动作
     */
    private fun analyzePose(landmarks: List<NormalizedLandmark>): PoseResult {
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2
        val kneeY = (landmarks[25].y() + landmarks[26].y()) / 2
        val ankleY = (landmarks[27].y() + landmarks[28].y()) / 2
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2

        val hipKneeDistance = kneeY - hipY
        val kneeAnkleDistance = ankleY - kneeY
        val shoulderHipDistance = hipY - shoulderY

        // 下蹲检测：膝盖高度接近臀部
        val isSquatting = hipKneeDistance < 0.15f && hipKneeDistance > 0f

        // 跳跃检测：臀部高度接近或高于肩膀（侧面视角）
        val isJumping = hipY < shoulderY * 0.9f

        // 举手检测：手腕高度高于肩膀
        val leftWristY = landmarks[15].y()
        val rightWristY = landmarks[16].y()
        val isRaisingHands = leftWristY < shoulderY * 0.9f && rightWristY < shoulderY * 0.9f

        // 侧闪检测：身体重心偏移
        val centerX = (landmarks[23].x() + landmarks[24].x()) / 2
        val isDodging = centerX < 0.3f || centerX > 0.7f

        // 蹲伏检测：膝盖高度接近踝关节
        val isCrouching = kneeAnkleDistance < 0.1f && kneeAnkleDistance > 0f

        val action = when {
            isSquatting -> Action.SQUAT
            isJumping -> Action.JUMP
            isRaisingHands -> Action.RAISE_HANDS
            isDodging -> Action.DODGE
            isCrouching -> Action.CROUCH
            else -> Action.IDLE
        }

        // 计算置信度（基于关键点可见性）
        val visibleLandmarks = landmarks.count { it.visibility().isPresent && it.visibility().get() > 0.5f }
        val confidence = visibleLandmarks.toFloat() / landmarks.size

        return PoseResult(
            action = action,
            confidence = confidence,
            hipY = hipY,
            kneeY = kneeY,
            ankleY = ankleY,
            shoulderY = shoulderY,
            boundingBox = calculateBoundingBox(landmarks)
        )
    }

    /**
     * 计算人体边界框
     */
    private fun calculateBoundingBox(landmarks: List<NormalizedLandmark>): RectF {
        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f

        landmarks.forEach { landmark ->
            minX = minOf(minX, landmark.x())
            maxX = maxOf(maxX, landmark.x())
            minY = minOf(minY, landmark.y())
            maxY = maxOf(maxY, landmark.y())
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * 检测特定动作是否在游戏中需要
     */
    fun detectRequiredAction(bitmap: Bitmap, requiredActions: List<Action>): Boolean {
        val result = detectPose(bitmap) ?: return false
        return result.action in requiredActions
    }

    /**
     * 获取动作奖励骰点数
     */
    fun getActionDiceBonus(action: Action, gameType: GameType): Int {
        return when (gameType) {
            GameType.PAOTUAN -> when (action) {
                Action.DODGE -> 2 // 闪避成功奖励骰
                Action.JUMP -> 1
                Action.SQUAT -> 1
                Action.RAISE_HANDS -> 0
                Action.CROUCH -> 1
                Action.IDLE -> 0
            }
            GameType.JUBENSHA -> when (action) {
                Action.RAISE_HANDS -> 1 // 举手发言
                Action.SQUAT -> 0
                Action.CROUCH -> 0
                else -> 0
            }
            GameType.HAITANG -> 0
        }
    }

    fun destroy() {
        // poseLandmarker?.close()
        // poseLandmarker = null
        Log.d(TAG, "Pose detector destroyed")
    }
}

/**
 * 检测到的动作
 */
data class DetectedAction(
    val action: Action,
    val confidence: Float,
    val timestamp: Long
)

/**
 * 动作类型
 */
enum class Action {
    IDLE,           // 静止
    SQUAT,          // 下蹲
    JUMP,           // 跳跃
    RAISE_HANDS,    // 举手
    DODGE,          // 闪避
    CROUCH          // 蹲伏
}

/**
 * 姿态检测结果
 */
data class PoseResult(
    val action: Action,
    val confidence: Float,
    val hipY: Float,
    val kneeY: Float,
    val ankleY: Float,
    val shoulderY: Float,
    val boundingBox: RectF
)
