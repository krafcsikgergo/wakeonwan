# Keep kotlinx.serialization classes and annotations
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep @Serializable classes
-keepnames @kotlinx.serialization.Serializable class **
-keep @kotlinx.serialization.Serializable class ** {
    *;
}

# Keep all serializers
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all data classes that might be serialized
-keep class hu.krafcsikgergo.wakeonwan.sender.** { *; }
-keep class hu.krafcsikgergo.wakeonwan.common.** { *; }
