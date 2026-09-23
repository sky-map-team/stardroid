plugins {
    id("skymap.pure-kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:math"))
            api(project(":core:astronomy"))
            // Flow is the repository interface's emission type, so it leaks to consumers — hence
            // `api`.
            api(libs.kotlinx.coroutines.core)
        }
    }
}
