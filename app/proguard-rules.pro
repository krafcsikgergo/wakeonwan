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

# Keep kotlinx.serialization classes and annotations
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep @Serializable classes
-keepnames @kotlinx.serialization.Serializable class **
-keep @kotlinx.serialization.Serializable class ** {
    *;
}

# Keep ServerData class and its properties
-keep class hu.krafcsikgergo.wakeonwan.receiver.ServerData { *; }
-keep class hu.krafcsikgergo.wakeonwan.services.ServerData { *; }

# Keep all serializers
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all data classes that might be serialized
-keep class hu.krafcsikgergo.wakeonwan.** { *; }