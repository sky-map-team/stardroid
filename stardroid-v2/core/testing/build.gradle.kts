plugins {
    id("skymap.pure-kmp")
}

// Test support shared by the pure modules' commonTest suites (a Truth-shaped assertion API over
// kotlin.test). Depended on by the skymap.pure-kmp convention, never by production code.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(kotlin("test"))
        }
    }
}
