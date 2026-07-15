# ProGuard / R8 rules for the release build.
# R8 shrinking + resource shrinking are enabled in build.gradle.kts.
# Most third-party libs below ship their own consumer rules, but because this
# app leans on native (JNI) and reflection-heavy libraries, we keep them
# explicitly so an aggressive R8 pass can't strip something loaded at runtime.

# --- Keep line numbers for readable release crash reports ------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- SceneView + Filament (native OpenGL renderer, accessed via JNI) --------
# Filament's Java classes are called from native code; do not touch them.
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.android.filament.**
-dontwarn io.github.sceneview.**

# --- ML Kit face detection --------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-dontwarn com.google.mlkit.**

# --- osmdroid ---------------------------------------------------------------
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- Coil (image loading) ---------------------------------------------------
-dontwarn coil.**

# --- General safety nets ----------------------------------------------------
# Keep every JNI entry point regardless of which library declares it.
-keepclasseswithmembernames class * {
    native <methods>;
}
# Keep enum internals (valueOf/values used reflectively by some libs).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
