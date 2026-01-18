// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

extra["android"] = mapOf(
    "compileSdk" to 34,
    "buildToolsVersion" to "33.0.0",
    "minSdk" to 21,
    "targetSdk" to 33,
    "versionCode" to 1,
    "versionName" to "1.0.1",
    )

extra["depsLibs"] = mapOf(
    "coreKtx" to "androidx.core:core-ktx:1.7.0",
    "appcompat" to "androidx.appcompat:appcompat:1.3.0",
    "material" to "com.google.android.material:material:1.4.0",
    "constraintlayout" to "androidx.constraintlayout:constraintlayout:2.0.4",
    "liveDataKtx" to "androidx.lifecycle:lifecycle-livedata-ktx:2.3.1",
    "viewModelKtx" to "androidx.lifecycle:lifecycle-viewmodel-ktx:2.3.1",
    "lifecycleCommon" to "androidx.lifecycle:lifecycle-common:2.5.0-beta01",

    "junit" to "junit:junit:4.13.2",
    "extJunit" to "androidx.test.ext:junit:1.1.3",
    "espressoCore" to "androidx.test.espresso:espresso-core:3.4.0",
    //图片加载
    "glide" to "com.github.bumptech.glide:glide:4.15.0",
    "glideCompiler" to "com.github.bumptech.glide:compiler:4.15.0",
)