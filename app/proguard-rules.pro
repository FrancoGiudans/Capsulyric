-keep class com.hchen.superlyricapi.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Prevent stripping of internal coroutines classes
-keep class kotlin.coroutines.jvm.internal.** { *; }
-keep class kotlinx.coroutines.internal.** { *; }