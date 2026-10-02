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

// Compose Material 3 1.9 depends on kotlinx-datetime 0.7, where Instant and Clock moved to
// kotlin.time and the kotlinx.datetime classes the shared modules are compiled against (0.6.1)
// no longer exist, so the framework link fails ("IrClassSymbolImpl is unbound ... Instant").
// 0.7.1-0.6.x-compat is the 0.7 API with those classes kept, published for exactly this. It is
// pinned here only: Android does not use Compose Multiplatform and stays on 0.6.1. Moving the
// whole build to 0.7 (kotlin.time.Instant) is its own change.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-datetime")
        ) {
            useVersion("0.7.1-0.6.x-compat")
            because("kotlinx.datetime.Instant for the 0.6-compiled shared modules")
        }
    }
}
