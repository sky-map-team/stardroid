import org.jlleitschuh.gradle.ktlint.KtlintExtension

// ktlint for every module, through one convention so the engine version is set in one place.
//
// The engine is pinned to 1.0.1, the version the build has always run (ktlint-gradle 12's
// default): moving to ktlint-gradle 14 — whose 14.1 lints Android modules that use AGP 9's
// built-in Kotlin, which 12 silently skipped (D124) — should change *what* is linted, not the
// rules. A newer engine is its own change.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

extensions.configure<KtlintExtension> {
    version.set("1.0.1")
}
