plugins {
    id("skymap.pure-kmp")
}

// Fakes of the shared modules' interfaces, for tests on both sides of the :app/:shared line —
// the :shared modules' own commonTest suites and :app's JVM unit tests. As :core:testing does
// for assertions; never a production dependency.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:settings"))
            api(project(":core:catalog"))
        }
    }
}
