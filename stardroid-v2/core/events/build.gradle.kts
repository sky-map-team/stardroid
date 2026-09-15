plugins {
    id("skymap.pure-kotlin")
}

dependencies {
    // MeteorShower, LatLong, Ephemeris, Instant all appear in the engine's public API.
    api(project(":core:math"))
    api(project(":core:astronomy"))
    api(project(":core:catalog"))
}
