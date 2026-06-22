-keep class com.hchen.superlyricapi.** { *; }
-dontwarn android.os.ServiceManager

# Lyric Getter API (required to prevent obfuscation breaking the broadcast Parcelable)
-keep class cn.lyric.getter.api.data.** { *; }
-keep class cn.lyric.getter.api.API { *; }


# Kotlin Coroutines rules are supplied by kotlinx-coroutines-core/android consumer rules.

# Strip logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
