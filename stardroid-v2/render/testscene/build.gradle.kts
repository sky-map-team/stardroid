plugins {
    id("skymap.pure-kmp")
}

// The seeded synthetic scene every renderer harness draws (TestScene). Debug/test-only: :app
// takes it as a debugImplementation, and :render:metal only in its tests and harness.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":render:api"))
        }
    }
}
