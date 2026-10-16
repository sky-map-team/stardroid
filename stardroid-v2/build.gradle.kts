// Root project. Module configuration lives in the build-logic convention plugins
// (skymap.pure-kotlin / skymap.android-library / skymap.android-app) and per-module
// build files. See docs/design/build-and-tooling.md.

// Fail configuration if an Android module stops running ktlint over src/**/*.kt (#1133).
// The ktlint plugin alone skips those sources there (#1054).
gradle.projectsEvaluated {
    listOf(":app", ":render:gles1").forEach { path ->
        val checkTask = project(path).tasks.getByName("check")
        val wired =
            checkTask.taskDependencies.getDependencies(checkTask).any { it.name == "ktlintSourcesCheck" }
        check(wired) { "$path: check no longer depends on ktlintSourcesCheck (see #1054)" }
    }
}
