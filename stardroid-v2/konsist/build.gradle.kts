plugins {
    id("skymap.pure-kotlin")
}

// Architecture-enforcement gate (D20). Scans the whole project; depends on no other module.
// JUnit5 comes from the skymap.pure-kotlin convention plugin; only Konsist is extra here.
dependencies {
    testImplementation(libs.konsist)
}

// Konsist scans the filesystem rather than declared Gradle dependencies, so Gradle can't infer
// that this gate depends on other modules' sources. Without explicit inputs the task can sit
// UP-TO-DATE and silently skip the check after a source change in another module. Declare every
// project Kotlin source as a task input so any such change re-runs the gate.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir) {
            include("**/*.kt")
            exclude("**/build/**")
            exclude("**/.gradle/**")
        },
    ).withPropertyName("allProjectKotlinSources")
}
