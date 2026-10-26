// Root project. Module configuration lives in the build-logic convention plugins
// (skymap.pure-kotlin / skymap.android-library / skymap.android-app) and per-module
// build files. See docs/design/build-and-tooling.md.

// build-logic is an included build, so `./gradlew check` would not reach its tests (the shared
// strings' converter) on its own.
tasks.register("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
