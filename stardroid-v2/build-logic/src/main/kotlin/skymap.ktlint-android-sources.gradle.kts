/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// ktlint-gradle only registers source-set tasks when `org.jetbrains.kotlin.android` is applied,
// which AGP's built-in Kotlin never does, so Android modules would lint only their .kts scripts
// (#1054). Run the same ktlint version over src/**/*.kt directly and hook it into the
// plugin's ktlintCheck / ktlintFormat.

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

val ktlintVersion = "1.0.1"

val ktlintCli by configurations.creating

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:$ktlintVersion")
}

fun registerKtlintSources(name: String, format: Boolean) =
    tasks.register<JavaExec>(name) {
        group = if (format) "formatting" else "verification"
        description = "Runs ktlint over src/**/*.kt (not covered by the ktlint plugin here)."
        classpath = ktlintCli
        mainClass.set("com.pinterest.ktlint.Main")
        workingDir = projectDir
        args(
            listOfNotNull(
                "--relative",
                "--log-level=error",
                "-F".takeIf { format },
                "src/**/*.kt",
            ),
        )
    }

val checkSources = registerKtlintSources("ktlintSourcesCheck", format = false)
val formatSources = registerKtlintSources("ktlintSourcesFormat", format = true)

tasks.named("ktlintCheck") { dependsOn(checkSources) }
tasks.named("check") { dependsOn(checkSources) }
tasks.named("ktlintFormat") { dependsOn(formatSources) }
