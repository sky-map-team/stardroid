import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("skymap.ios-library")
}

// The iOS renderer harness (RendererHarness): the Metal backend drawing the shared TestScene in an
// MTKView — the counterpart of Android's RendererTestActivity. Packaged as the SkyMapHarness
// framework that ios/RendererHarness links; its Xcode build runs
// :render:metal-harness:embedAndSignAppleFrameworkForXcode. A development tool, not shipped code.
kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SkyMapHarness"
            isStatic = true
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":render:metal"))
            implementation(project(":render:testscene"))
        }
    }
}
