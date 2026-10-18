# Proguard / R8 Rules for SkillExchange Android App (Proposed for release build - TASK-052)
# File này chứa các quy tắc giữ lại các lớp, annotation, reflection cần thiết khi bật minify/obfuscation.
# Để kích hoạt: đặt enable = true và cấu hình proguardFiles trong android/app/build.gradle.kts (release build).

# -----------------------------------------------------------------------------
# 1. Kotlin & Kotlinx Serialization
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-dontnote kotlinx.serialization.SerializationKt

# Giữ lại Companion và serializer được sinh tự động
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Giữ lại các trường trong lớp @Serializable
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Giữ LenientCodeSerializer và custom serializer
-keep class com.skillexchange.app.core.network.LenientCodeSerializer { *; }

# -----------------------------------------------------------------------------
# 2. DTOs & Models (Network & Persistence boundary)
# -----------------------------------------------------------------------------
-keep class com.skillexchange.app.data.remote.**.dto.** { *; }
-keep class com.skillexchange.app.domain.model.** { *; }
-keep class com.skillexchange.app.core.network.** { *; }

# -----------------------------------------------------------------------------
# 3. Ktor Client & Coroutines
# -----------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }

# -----------------------------------------------------------------------------
# 4. Koin Dependency Injection
# -----------------------------------------------------------------------------
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepclassmembers class * {
    @org.koin.core.annotation.* *;
}

# -----------------------------------------------------------------------------
# 5. Room Database
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * implements androidx.room.migration.Migration
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# -----------------------------------------------------------------------------
# 6. Firebase (FCM & Auth) & Google Play Services
# -----------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# -----------------------------------------------------------------------------
# 7. Coil Image Loader
# -----------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**

# -----------------------------------------------------------------------------
# 8. AndroidX & Jetpack Compose
# -----------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.security.crypto.** { *; }
