# Keep generic signatures and annotations for Reflection (Retrofit/Gson)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, AnnotationDefault

# Retrofit rules
-keep interface retrofit2.** { *; }
-keep @retrofit2.http.* interface * { *; }
-dontwarn retrofit2.**

# Critical for Coroutines + Retrofit
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowoptimization class retrofit2.Response

# Keep the API interfaces specifically
-keep interface com.jpd.finsync.api.** { *; }

# Gson rules
-keep class com.google.gson.** { *; }
-keep class com.jpd.finsync.model.** { *; }
-keepclassmembers class com.jpd.finsync.model.** { <fields>; }
-keepclassmembernames class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
