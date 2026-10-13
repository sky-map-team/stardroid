plugins {
    id("skymap.pure-kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:math"))
        }
    }
}
