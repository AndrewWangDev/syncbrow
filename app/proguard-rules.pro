# Proguard / R8 Configuration for SyncBrow

# Preserve line numbers and annotations for debugging and reflection
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Room Persistence Library ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.syncbrow.tool.data.** { *; }
-dontwarn androidx.room.paging.**

# --- OkHttp & DNS over HTTPS ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keepclassmembers class * extends okhttp3.dnsoverhttps.DnsOverHttps { *; }

# --- Android WebKit & WebView ---
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn androidx.webkit.**

# --- CameraX & ML Kit Barcode Scanning ---
-keep class androidx.camera.core.** { *; }
-keep class com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.**

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Jetpack ViewModel & Lifecycle ---
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
