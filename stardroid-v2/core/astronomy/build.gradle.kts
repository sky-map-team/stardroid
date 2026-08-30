plugins {
    id("skymap.pure-kotlin")
}

dependencies {
    api(project(":core:math"))
    // Instant is the time currency throughout :core:astronomy's public API (julianDay, validRange,
    // ephemeris queries), so it leaks to consumers — hence `api`, not `implementation`.
    api(libs.kotlinx.datetime)
}
