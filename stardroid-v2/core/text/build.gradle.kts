plugins {
    id("skymap.pure-kmp")
}

// The one interface through which shared logic asks for translated text. Each platform maps the
// ids to its own resources. See docs/design/ios-platform-surfaces.md section 10, decision 4.

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
