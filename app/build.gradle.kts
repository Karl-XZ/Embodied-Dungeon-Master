import com.android.build.gradle.api.ApkVariantOutput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.xmov.metahuman.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("../XmovDemo.jks")
            storePassword = "123456"
            keyAlias = "xmov"
            keyPassword = "123456"
        }
    }

    defaultConfig {
        applicationId = "com.xmov.metahuman.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }


    //  APK 文件名自定义（对应原 applicationVariants 逻辑）
    applicationVariants.all {
        outputs.all {
            // 安全转换为 APK 输出（过滤 App Bundle 等非 APK 类型）
            val apkOutput = this as? ApkVariantOutput ?: return@all

            // 获取渠道名、构建类型（通过 @all 明确引用外部 variant 作用域）
            val flavorName = "LiteSDKOpenDemo"

            // 生成上海时区的时间戳
            val timeFormat = SimpleDateFormat("yyyyMMddHHmm").apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }
            val timeStamp = timeFormat.format(Date())

            // 拼接文件名：[渠道名]_[时间戳].apk（保持原命名逻辑）
            val apkFileName = "${flavorName}_${timeStamp}.apk"

            // 设置输出文件名
            apkOutput.outputFileName = apkFileName
        }
    }
}

dependencies {
    implementation(libs.androidx.preference)
    val depsLibs =rootProject.extra["depsLibs"] as Map<*, *>

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.vecmath)

    implementation(libs.core.ktx)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(depsLibs["glide"]!!)
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.0")
    implementation("org.msgpack:msgpack-core:0.9.3")
    implementation("javax.vecmath:vecmath:1.5.2")
    implementation(files("libs/xmovdigitalhuman-v0.0.1.aar"))

    // TRPG 相关依赖
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.cardview:cardview:1.0.0")

    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // PDF 剧本文本提取
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ========== 新增功能依赖 ==========

    // MediaPipe Pose (动作识别)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // CameraX (摄像头)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 图像加载
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Room 数据库 (暂时注释掉，存在版本兼容性问题)
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // ksp("androidx.room:room-compiler:2.6.1")
    
    // Kotlin metadata 支持
    // implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")

    // WorkManager (后台任务)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (轻量级存储)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}