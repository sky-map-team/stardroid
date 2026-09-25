plugins {
    id("skymap.android-library")
}

android {
    namespace = "com.google.android.stardroid.render.gles3"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":render:api"))

    // As in :render:gles1 — skymap.android-library doesn't give modules the test stack for
    // free the way skymap.pure-kotlin does, so the GL-free logic gets it explicitly here.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)

    // The shader compile/link gate needs a real GL context, so it is instrumented rather than
    // a JVM test. It is the cheapest possible insurance against a class of black-screen field
    // failures that produce no log and no crash.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
