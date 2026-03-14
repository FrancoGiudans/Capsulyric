# ProGuard rules for Debug builds
# Enables shrinking but DISABLES obfuscation to preserve stack traces and keep it debuggable.

-dontobfuscate
-dontoptimize
-dontwarn **
-keepattributes SourceFile,LineNumberTable

# Standard shrinking rules
-keep class com.example.islandlyrics.** { *; }
-keep interface com.example.islandlyrics.** { *; }

# Keep everything else to be safe, R8 will still strip unused classes and members
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.backup.BackupAgentHelper
-keep class * extends android.preference.Preference
-keep class * extends androidx.core.app.ComponentActivity
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.room.RoomDatabase

# Compose specific
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep everything that might be accessed via reflection in debug
-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Keep the rules from the main file too
-include proguard-rules.pro
