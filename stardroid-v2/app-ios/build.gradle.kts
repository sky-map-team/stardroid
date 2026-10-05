import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// The iOS app's Kotlin side (iOS port phase 4): the framework the Xcode app (ios/SkyMap) links,
// holding the app graph, the iOS platform edges — Core Motion, Core Location, the bundle — and
// the Compose Multiplatform UI root (D134). Everything below it is shared with Android.
plugins {
    id("skymap.ios-compose")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SkyMapKit"
            isStatic = true
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared:viewmodels"))
            implementation(project(":shared:ui"))
            implementation(project(":shared:layers"))
            implementation(project(":shared:model"))
            implementation(project(":data"))
            implementation(project(":render:metal"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
        }
    }
}
