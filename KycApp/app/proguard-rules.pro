# ProGuard / R8 keep rules for when minify is re-enabled later.
# Native ML stacks break if JNI entry points are stripped.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exception, SourceFile, LineNumberTable

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class cz.adaptech.** { *; }
-dontwarn com.googlecode.tesseract.android.**
-dontwarn com.googlecode.leptonica.android.**

-keep class com.example.kycapp.biometrics.** { *; }
-keep class com.example.kycapp.data.** { *; }
-keep class org.jnbis.** { *; }

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class androidx.compose.runtime.** { *; }
