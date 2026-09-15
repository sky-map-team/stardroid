# Detailed Design: Build, Modules, and CI Tooling

**Status: IMPLEMENTED** (slice 1, D20; this doc refreshed 2026-08-02 to match the as-built
graph). Records the build scaffolding the other increments assume (G13) and the CI guardrails
for the architecture (D20) and performance (D19).

## Module graph

Ten Gradle modules, following the dependency rule (arrows inward only; see
[high-level-architecture.md](high-level-architecture.md)):

```
:app            android-app    → :render:api, :render:gles1, :data, :core:*
:render:gles1   android-library→ :render:api
:render:api     pure-kotlin    → :core:math
:data           android-library→ :core:catalog, :core:astronomy, :core:math
:data:generator pure-kotlin    → :core:catalog (build-time JVM tool; sqlite-jdbc,
                                 kotlinx-serialization)
:core:events    pure-kotlin    → :core:catalog, :core:astronomy, :core:math
:core:catalog   pure-kotlin    → :core:astronomy, :core:math
:core:astronomy pure-kotlin    → :core:math
:core:math      pure-kotlin    → (nothing)
:konsist        pure-kotlin    → (nothing; test-only architecture gate)
```

Plus build-only: `build-logic/` (convention plugins, JVM, via `includeBuild`). The catalog
generator originally sketched here as `:tools:catalog-gen` landed as `:data:generator` (D34):
`:data` wires it through a dedicated `catalogGenerator` configuration and a
`GenerateCatalogDbTask` whose output is registered as a per-variant generated asset.

## Convention plugins (`build-logic/`)

Four plugins keep module build scripts to a few lines and make module *kind* a declaration,
not a copy-paste of config. They are the structural half of D20.

| Plugin | Applies | Used by |
|---|---|---|
| `skymap.pure-kotlin` | `kotlin("jvm")`, JUnit5/Truth, **no Android plugin** | `:core:*`, `:render:api`, `:data:generator`, `:konsist` |
| `skymap.android-library` | `com.android.library` + Kotlin, common Android config | `:render:gles1`, `:data` |
| `skymap.android-app` | `com.android.application` + Kotlin + Compose + Hilt + flavors | `:app` |
| `skymap.android-room` | KSP + Room, checked-in exported schema (`data/schemas/`) | `:data` |

Because `skymap.pure-kotlin` never puts the Android SDK on the classpath, `import android.*` in
a pure module is a **compile error** — the primary, structural guarantee that the pure/Android
boundary holds (D20 layer 1). A pure module that needs Android has applied the wrong plugin,
which is the visible mistake.

Shared versions live in a Gradle **version catalog** (`gradle/libs.versions.toml`): Kotlin,
AGP, Compose BOM, Hilt, Room, DataStore, Glance + WorkManager (widgets, D69),
kotlinx-datetime/serialization/coroutines, Coil, Konsist, Truth, Turbine (Flow testing).
(Robolectric and androidx-benchmark, listed in earlier drafts, are not in use — see the
performance-gate note below.)

**SDK levels.** v2 uses `minSdk 29`, `compileSdk 36`, `targetSdk 36`, set once in the
`android-library` / `android-app` convention plugins. This is a deliberate raise over v1's
`minSdk 26` (decision D9): sub-Android-10 devices are ~1.6% of installs and
keep v1 via in-place upgrade. v1's "SDK 26–36" range does not apply to v2 — see the note in the
repo-root `AGENTS.md`.

## Build flavors (D3)

`:app` carries the `gms` / `fdroid` product flavors from v1. Flavor-specific source sets supply
the analytics, location, and (future) attestation implementations behind interfaces; the
`fdroid` flavor pulls no Play Services / Firebase. No proprietary SDK appears in any pure or
`fdroid`-path module.

## CI guardrails

### Architecture enforcement (D20, layer 2)

A Konsist test (the `:konsist` module — an ordinary JVM unit test that fails the build on
violation; `konsist/src/test/kotlin/.../ArchitectureTest.kt`) asserts the invariants that the
compiler alone can't fully cover:

- a non-vacuity guard (the path regex actually matches files, so the gate can't silently
  rot);
- no type in a pure module imports `android.*` / `androidx.*` (plus a catch for
  `*.android.*` artifacts like coroutines-android);
- the module dependency arrows point inward only, via an in-project import **allow-list**
  (`math`/`astronomy`/`catalog`/`render.api`), because `:app`'s namespace is the package root
  and a denylist would miss it.

*Known gap (2026-08-02):* the path regex and allow-list predate `:core:events`, so that pure
module is currently outside the gate; extend both when next touched. The `:konsist` build file
registers every `**/*.kt` as a task input because Konsist scans the filesystem and would
otherwise sit UP-TO-DATE and silently skip.

Layer 3 — a KMP-readiness denylist of JVM-only packages inside core logic (e.g. `java.nio.*`)
— is **deferred** until KMP work begins; it is noise before then (D20). `java.nio.FloatBuffer`
legitimately lives in `:data`/`:render:gles1` (the GL upload boundary), not in pure modules.

### Reproducible catalog DB (D3, [catalog-and-schema.md](catalog-and-schema.md))

`:data:generator` produces `skymap.db` deterministically (fixed page size, no timestamps,
stable row order). The generator's JVM test suite (`CatalogDbGeneratorTest`) runs against the
**real** checked-in Room schema and `source-data/` and asserts byte-identical output across
runs, identity-hash/user_version match with the exported schema, FTS diacritic folding, and
content spot checks — the F-Droid build-from-source guarantee, enforced per commit via
`check`. The generated DB is a build artifact, never checked in.

### Performance gate (D19) — as built

The androidx-benchmark module sketched here was not built. What exists instead:

- `testscene/RendererTestActivity` — a dev harness driving the real catalog or a seeded
  ~100k-point synthetic scene, doubling as the v1/v2 screenshot-comparison rig (D31). It
  lives in `app/src/debug/`, with its own debug manifest, so it never ships in a release
  APK or AAB (audit-2026-08 H1); `androidTest` builds against the debug variant, so
  `RendererPerfTest` still sees it.
- `RendererPerfTest` (`:app` androidTest) — 2 s warmup, 5 s measurement. **In CI it is a
  smoke gate** (≥ 5 frames on the software-rendered AVD); the real D19 target (≥ 30 fps at
  100k points on a Pixel 3a) is a release-time manual check, and the test WARNs rather than
  fails below target.

The trigger logic stands: missing the pan/zoom target on real hardware is the cue to add a
backend region index before relying further on D13's no-point-culling stance.

### CI wiring

The workflow lives at the **monorepo root** (`.github/workflows/android.yml`, working
directory `stardroid-v2/`): a `check` job (unit tests, ktlint, Konsist, generator gate) plus
connected instrumented tests on API 34 and 35 emulators for every PR.

## Testing toolchain (summary; per-area detail lives in each design doc)

| Layer | Tools |
|---|---|
| Pure modules (`:core:*`, `:render:api`) | JUnit5, Truth, kotlin property tests; golden fixtures vs. v1 (D6) |
| Flow behavior | Turbine |
| `:data` (Room) | **instrumented** tests against real SQLite (FTS4 + Room invalidation need it) + fixture pack; generator golden test on the JVM |
| `:app` ViewModels | pure JVM with fakes (the point of the decomposition) |
| Compose screens | not yet built — coverage is at the ViewModel layer; Compose UI tests remain the plan for critical paths |
| Architecture | Konsist (D20) |
| Performance | `RendererPerfTest` smoke gate in CI; real D19 budget checked manually at release (see above) |
| Renderer visual | dev-only test activity + v1/v2 screenshot comparison (intentional D12 diffs noted) |
