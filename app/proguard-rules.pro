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

# Keep application class
-keep public class * extends android.app.Application

# Keep all classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Coil image loading
-keep class io.coil3.** { *; }

# Keep data classes and sealed classes
-keep class com.neel.grepshot.data.** { *; }
-keep class com.neel.grepshot.domain.** { *; }

# Keep service classes
-keep class com.neel.grepshot.service.** { *; }

# Preserve line numbers for debugging crashes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep serialization info
-keepattributes *Annotation*

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optimize enums
-optimizations !code/simplification/enum

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }