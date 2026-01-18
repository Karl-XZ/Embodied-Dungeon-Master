# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#XLog
#-keep class com.tencent.mars.** {
#  public protected private *;
#}

# 如果使用了 单类注入，即不定义接口实现 IProvider，需添加下面规则，保护实现
# -keep class * implements com.alibaba.android.arouter.facade.template.IProvider

# https://github.com/suming77/SumTea_Android/issues/2
-keep class org.webrtc.** { *; }
-keepclasseswithmembernames class * { # 保持 native 方法不被混淆
native <methods>;
}
-dontwarn okhttp3.internal.connection.StreamAllocation
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn dalvik.system.VMStack
-keep public class com.xmov.ai.core.** { *; }
# 保留注解和反射相关的属性（防止泛型/注解信息丢失）
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

# 保留所有带有特定方法名的方法（适用于全局有多个inflate方法的场景）
-keepclassmembers class * {
    # 保留所有名为inflate、参数包含LayoutInflater的方法
    public ***inflate(android.view.LayoutInflater, ...);
    public static ***inflate(android.view.LayoutInflater, ...);
}

# 保留所有继承自View的类（避免自定义View的方法被混淆）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...); # 保留setter方法（避免属性动画/数据绑定反射失败）
}

# 保留所有 ViewBinding 类的 inflate 方法（包括所有重载）
-keepclassmembers class **.databinding.*Binding {
    public static ***inflate(android.view.LayoutInflater);
    public static ***inflate(android.view.LayoutInflater, android.view.ViewGroup);
    public static ***inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# 保持原始 XML 布局的类名
-keep class com.xmov.ai.demo.databinding.** { *; }

# 1. 保留 msgpack 核心库类不被混淆（避免库内部反射失败）
-keep class org.msgpack.** { *; }
-keep interface org.msgpack.** { *; }

# 2. 保留 msgpack 涉及的注解和泛型信息（序列化依赖）
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# 5. 保留枚举类型（msgpack 处理枚举时需要保留名称）
-keep enum * {
    public static **[] values();
    public static** valueOf(java.lang.String);
}

# 6. 保留数组类型（避免基本类型数组被混淆）
-keepclassmembers class * {
    public static <fields>;
}

-dontwarn sun.nio.ch.DirectBuffer
-keepattributes Signature

# ---- PDFBox (pdfbox-android) ----
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
