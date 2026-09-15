plugins {
    id("skymap.android-library")
}

android {
    namespace = "com.google.android.stardroid.render.gles1"
}

dependencies {
    api(project(":render:api"))

    // skymap.android-library doesn't give modules the test stack for free the way
    // skymap.pure-kotlin does; added here so the GL-free logic (StellarStyler,
    // GreatCircleSubdivision) gets plain JUnit5 unit tests.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
