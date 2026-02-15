# ProGuard rules for VogonPoet

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# LWJGL
-dontwarn org.lwjgl.**
-keep class org.lwjgl.** { *; }

# kotlinx-coroutines
-dontwarn kotlinx.coroutines.**

# JSR305 (Nullable annotations)
-dontwarn javax.annotation.**

# Compose
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**

# Koin
-dontwarn org.koin.**

# Kotlin Serialization
-dontwarn kotlinx.serialization.**
