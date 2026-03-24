# Sky Map Constitution

## Core Principles

### I. Sensor-First Architecture

The Sky Map display MUST be driven by real-time device sensor data flowing through `AstronomerModel`.
Manual drag/rotate control exists as a fallback only; it MUST NOT become the primary input path.

- Coordinate transformations MUST pass through `AstronomerModel`'s zenith/North matrix pipeline;
  no layer or activity may compute celestial coordinates independently.
- Resources with an `onResume`/`onPause` lifecycle (e.g. `MediaPlayer`, file handles) MUST use
  unscoped `@Provides` — Dagger caches scoped instances permanently, causing stale-object bugs.
- Sensor fallback order (rotation vector → legacy accel+mag) MUST be preserved; do not remove
  legacy paths while older devices are still supported (minSdk 26).
- Backward compatibility with older Android API levels MUST be maintained where feasible, given
  the app's long history and user base. API-level-gated features MUST use runtime checks, not
  compile-time exclusion, unless the feature is fundamentally impossible on older APIs.

**Rationale**: The entire product value is accurate, real-time sky alignment. Any deviation here
silently mis-identifies celestial objects for users.

### II. Layer Modularity

Sky content MUST be organized as independent, self-contained rendering layers inheriting from the
`AbstractLayer` hierarchy.

- Each layer MUST be independently enable/disable-able without affecting other layers.
- A new layer MUST register itself through the Dagger DI graph (`AbstractDynamicStarMapModule`) and
  MUST NOT directly reference other layers at runtime.
- Layer data MUST flow only via the established pipeline:
  `AbstractFileBasedLayer → ProtobufAstronomicalSource → Primitives → RendererController`.
- No layer may hold a reference to `SkyRenderer` directly; all rendering commands go through
  `RendererController`.

**Rationale**: Modularity allows features (ISS tracking, meteor showers, grids) to be added,
removed, or disabled in fdroid/gms flavors without cross-layer regressions.

### III. Flavor Purity

The `fdroid` build flavor MUST remain completely free of Google Play Services dependencies.

- All Google-specific code (Analytics, GMS Location, Crashlytics) MUST live exclusively in
  `gms`-flavored source sets.
- Shared code MUST depend only on interfaces; flavor-specific implementations are injected via
  Dagger at compile time.
- CI MUST build and pass tests for BOTH `assembleGmsDebug` and `assembleFdroidDebug`; a green
  `gms`-only build is not sufficient for merging.
- No new `com.google.*` import may appear in `main/` source sets; all such imports belong in
  `gms/` source sets.

**Rationale**: Sky Map is distributed on F-Droid as a Google-free alternative. Any Google leakage
breaks F-Droid compliance and violates user trust.

### IV. Test Discipline

Sensor math, coordinate transforms, astronomical calculation logic, and new utility functions MUST
have unit test coverage.

- New astronomical algorithms (ephemeris, coordinate conversion, search indexing) MUST be
  accompanied by unit tests using JUnit 4 + Truth assertions.
- Every new utility function MUST have a corresponding unit test.
- Tests MUST NOT mock the astronomical math — verify real outputs against known ephemeris values.
- UI and integration tests use Espresso; they MUST NOT depend on live network calls or real device
  sensors (use fakes/stubs at the DI boundary).
- Robolectric is acceptable for Android-framework-touching unit tests where Espresso is overkill.
- Tests MUST be written (and verified to fail) before implementing the behaviour they cover when
  working on new algorithmic logic.

**Rationale**: Incorrect sky positions silently give users wrong information. Sensor math bugs are
hard to reproduce manually; only automated tests catch them reliably across device variants.

### V. Simplicity & Minimal Dependencies

Sky Map is a community-maintained app. Complexity MUST be justified; the minimum sufficient
solution is always preferred.

- No new third-party library may be added without a clear, documented rationale. Prefer Android
  framework APIs and existing dependencies.
- The Material Design dependency MUST remain limited to the `gms` flavor or explicitly tested
  against the app's `FullscreenTheme` (AppCompat-only). Do not use Material widgets or attributes
  that require `?attr/colorOnSurface` without first verifying theme compatibility.
- YAGNI applies: do not build abstractions, helpers, or configuration points for hypothetical
  future requirements.
- Color values MUST be declared in `colors.xml` and referenced via `R.color.*`; no hardcoded
  hex integers in Java/Kotlin.
- Keeping the app as small, simple, and fast as possible is more important than adding features.

**Rationale**: A small, stable dependency footprint reduces maintenance burden for volunteer
maintainers and keeps both gms and fdroid flavors buildable without proprietary toolchains.

### VI. Performance

Real-time sensor-to-rendering latency MUST be minimized; the sky display MUST feel instantaneous
as the user moves their device.

- Coordinate transformation math (horizontal ↔ equatorial) MUST use numerically efficient
  implementations. Prefer precomputed matrices and in-place operations over allocating new objects
  per frame.
- All celestial calculations MUST follow standard astronomical algorithms as described in Jean
  Meeus's *Astronomical Algorithms*. Deviations require explicit documentation of the source and
  the accepted accuracy trade-off.
- Work that does not need to run on the main thread MUST be dispatched off it; the main/UI thread
  is reserved for rendering and user input only.
- No blocking I/O (file reads, network calls) may occur on the main thread.

**Rationale**: Perceptible lag between device motion and sky movement breaks the core illusion of
the app. Even small latency regressions degrade the experience for all users.

## Android Code Standards

- Follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html):
  100-character line wrap, no `m` prefix on member variables, Java 17 toolchain features.
- New code MUST be written in Kotlin; existing Java code MUST NOT be migrated unless there is a
  clear, separate justification.
- New files MUST NOT include copyright headers.
- Android resource strings MUST escape apostrophes with a backslash (`\'`).
- Use XML-based layouts. The app has not been migrated to Jetpack Compose and its UI predates
  Material Design; the retro look MUST be preserved.
- Status-state colors MUST use the two-tier naming scheme (`status_good`, `status_ok`,
  `status_warning`, `status_bad`, `status_absent`) with corresponding `night_status_*` night-mode
  variants; day/night pairs MUST share the same semantic meaning (brighter night = better status).
- Dialog fragments MUST follow the `AbstractDynamicStarMapModule` pattern:
  `@Provides @PerActivity` method + `ActivityComponent` sub-interface +
  injection in `DynamicStarMapActivity`.
- New public API methods (public methods on public classes) MUST have a Javadoc (Java) or KDoc
  (Kotlin) header. Existing methods are exempt unless they are being modified.
- `Any` (Kotlin) or raw `Object` (Java) MUST NOT be used where a specific interface or class is
  applicable. Strict typing is required for all new code.
- Code comments on non-obvious astronomical logic MUST explain *why* a specific constant or
  algorithm is used (the "astronomer-developer" tone): cite the source, unit, and epoch where
  relevant.
- Data-processing and catalog-scraping scripts live in `tools/` and MAY be written in Python where
  appropriate; Java remains the primary language for tools that integrate with the Gradle build.

## Data Pipeline Integrity

The canonical data path is:

```
Raw catalogs → tools/Main.java → ASCII protobuf → binary protobuf → app/src/main/assets/
```

- Star catalog and ephemeris data assets MUST be treated as immutable at runtime; no code path
  may modify them after they have been generated and committed.
- `source.proto` schema changes MUST be backward compatible or accompanied by a full regeneration
  of all binary asset files committed in the same PR.
- Coordinate values in `tools/data/*.csv` files use decimal hours (RA) and decimal degrees (Dec);
  comments in those files MUST document the source and epoch.
- Extended-object coordinates MUST reference the object centre (nebula centroid, galaxy nucleus),
  not an embedded star or catalogue star position.
- After any catalog or schema change, a full data-generation run (`./gradlew generateData`) MUST
  be executed and the regenerated assets committed before the PR is merged.

## Governance

This constitution supersedes all other development practices documented in this repository. When
this document conflicts with inline code comments, README notes, or informal conventions, this
document takes precedence.

**Spec-first (AI collaboration)**: Before implementing any new feature, a specification document
MUST be created via `/speckit.specify` and reviewed before coding begins. Implementation tasks
are generated from the approved spec via `/speckit.tasks`. AI assistants MUST NOT write
implementation code for a new feature without an approved spec in `specs/`.

**Amendment procedure**:
1. Open a PR with the proposed change to `.specify/memory/constitution.md`.
2. State the motivation, affected principles, and any migration plan.
3. Bump the version according to semantic versioning:
   - MAJOR: backward-incompatible removal or redefinition of a principle.
   - MINOR: new principle or section added, or materially expanded guidance.
   - PATCH: clarifications, wording fixes, non-semantic refinements.
4. Update dependent templates (plan, spec, tasks) in the same PR if the change affects them.
5. Obtain at least one maintainer review before merging.

**Compliance**: All PRs and code reviews MUST verify that changes comply with the active
principles above. Complexity violations require a documented justification in the PR description
referencing the specific principle.

Runtime development guidance lives in `AGENTS.md` (project root) and `docs/ARCHITECTURE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-03-24 | **Last Amended**: 2026-03-24
