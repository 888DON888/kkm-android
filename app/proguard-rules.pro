# ─── Kotlin ───────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── Hilt / Dagger ───────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# ─── Room ─────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# ─── SQLCipher ────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ─── Retrofit / Gson ──────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature,*Annotation*
-keep class kz.kkm.data.remote.dto.** { *; }
-keep class kz.kkm.data.remote.*Dto { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── Domain models (never obfuscate, used for DB serialization) ─
-keep class kz.kkm.domain.model.** { *; }
-keep class kz.kkm.data.local.entity.** { *; }

# ─── ZXing ────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ─── ML Kit ───────────────────────────────────────────────────
-dontwarn com.google.mlkit.**

# ─── Security / Biometric ─────────────────────────────────────
-dontwarn androidx.biometric.**
-dontwarn androidx.security.crypto.**

# ─── Crash-safe reflection for Compose ───────────────────────
-keepclassmembers class androidx.compose.** { *; }
