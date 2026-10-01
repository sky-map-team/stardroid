// Convention for multiplatform modules whose shared code also needs the Android SDK on its Android
// side — :data, whose Room database is built from an APK asset with a Context. It is
// skymap.kmp-base's iOS targets plus AGP's Android target (com.android.kotlin.multiplatform.library)
// in place of skymap.pure-kmp's JVM one. commonMain still sees neither the Android SDK nor the JDK;
// androidMain sees both, as an ordinary Android library's sources do.
plugins {
    id("skymap.kmp-base")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    android {
        compileSdk = 36
        // See skymap.android-app.gradle.kts: 28 is best-effort (D9, revised), not a supported
        // floor. Code may assume API 29+ without guards.
        minSdk = 28
    }
}
