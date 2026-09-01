# stardroid-v2 — Agent Notes

Context for AI coding assistants working in `stardroid-v2/`, the active rewrite (Kotlin only,
GPLv3). See the repository root [AGENTS.md](../AGENTS.md) for branching, worktrees, and other
cross-cutting conventions.

v2 deliberately raises the floor to **minSdk 29** (compileSdk/targetSdk 36) — sub-Android-10
devices are ~1.6% of installs. Do not "correct" v2's minSdk back to 26.

## Code Style

Every new `.kt` file must begin with this GPLv3 short header:

```
/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
```

Follow the [Google Kotlin Style Guide](https://developer.android.com/kotlin/style-guide):

- 100 character line wrap
- No `m`-prefix on properties — this convention is obsolete
- Prefer idiomatic Kotlin (`val`, data classes, extension functions, expression bodies) over
  Java-style translations

**Linting:** `./gradlew ktlintCheck` enforces the 100-character limit and Google Kotlin Style
Guide across all v2 modules. Run `./gradlew ktlintFormat` to auto-fix most violations before
committing. Inline trailing comments (`foo, // note`) inside argument/parameter lists are not
allowed by ktlint — place comments on their own line above the element instead.

## Licensing

The v2 rewrite uses a **split-license** structure to keep the code open-source and F-Droid
compatible while protecting branding assets from copycat clones.

| Artifact | License | Owner |
|---|---|---|
| All `.kt` source code in this module | GPLv3 (+ a section 7 app-store permission) | Penterakt LLC and contributors |
| Functional resources — strings, translations, themes, layouts, UI artwork | GPLv3 | Penterakt LLC and contributors |
| Brand assets enumerated in `ASSET-LICENSES.txt` `[arr]` | All Rights Reserved | Penterakt LLC |
| Assets inherited from v1 (`[apache-v1]`) | Apache-2.0 | Varies — see `NOTICE.md` |
| Scientific data and imagery (`[third-party]`) | Its own terms — public domain **or CC BY 4.0** | N/A |

**Rules for new assets and code:**
- Every new `.kt` file must carry the GPLv3 short header shown in the Code Style section above.
- **Every new asset must be classified in `ASSET-LICENSES.txt` before it can
  merge.** `tools/check_asset_licenses.py` runs in CI and fails on anything unclassified.
  Put the file wherever it belongs functionally, then add its path to the right section:
  `[arr]` (you drew it *and* it is brand identity), `[gpl]` (you drew it, functional),
  `[apache-v1]` (inherited from v1), `[third-party]` (external).
- **Do not put an asset in `[arr]` unless it is unambiguously our own brand artwork.** The
  previous rule reserved two whole directories, which asserted ownership over 2,285
  community-contributed translations and every string resource — an over-claim that also
  contradicted the GPLv3 grant, since those files are Corresponding Source.
- Do **not** place scientific datasets or third-party assets in `[arr]`.
- There is no "branding directory". Asset location carries no licence meaning at all — put a
  file where it belongs functionally, and let `ASSET-LICENSES.txt` say what it is.
- `LICENSE.md` is the authoritative notice; `NOTICE.md` records Apache-2.0
  ancestry; `CLA.md` (repo root) governs contributions.

Exactly two documents govern asset licensing: `LICENSE.md` (the terms) and
`ASSET-LICENSES.txt` (the list). `NOTICE.md` records the Apache-2.0 ancestry.
Do not add a third notice — per-directory notices are what let the old claim drift.

## Key Files

- `docs/design/` — per-area design docs; `docs/README.md` tracks implementation status.
- `core/math`, `core/astronomy` — pure Kotlin modules (no Android SDK on the classpath).
- `render/api` — pure renderer contract + shared projection; `render/gles1` — a GLES1
  backend written to match v1's rendering behaviour (an independent implementation, not a
  port; see `NOTICE.md`).
- `app/` — the Android app shell (currently the dev test-scene activity and perf gate).
- `konsist/` — architecture-gate tests enforcing the pure/Android module boundary (D20).
- `build-logic/` — Gradle convention plugins (`skymap.pure-kotlin`, `skymap.android-*`).

## Translations

v2's translation pipeline is now live (`.tmconfig.toml` + the `tm` CLI, same tool as v1) —
this supersedes the root `AGENTS.md` note that translations happen later via a separate
pipeline; that note was written before this was wired up.

- `tm languages` — coverage summary per locale. `tm coverage <locale>` — per-file detail.
- `.tmconfig.toml`'s `primary_languages` list is the 28 **core** languages v2 ships
  officially. `ca` (Catalan), `hu` (Hungarian) and `ru` (Russian) exist as partial/legacy
  locale directories but are **intentionally excluded** from core — do not treat their low
  coverage as a bug or try to backfill them.
- Any change to translatable content (`whatsnew.xml`, `strings.xml`, `credits.xml`,
  `help.xml`, `eula.xml`, the fastlane changelog, or catalog `source-data/` content covered
  by `.tmconfig.toml`'s `[[sources]]`) goes stale in every locale's translation the moment
  the English source changes. Before finishing any such change — and always as part of a
  release — run:
  ```bash
  tm translate --all-primary --include-stale
  ```
  This is normally a handful of strings (the ones you just touched, plus anything newly
  added since the last run), not a large backlog. Verify with `tm languages` afterward —
  all 28 primary locales should read 100% coverage, 0 stale.

## Testing

`./gradlew check` from the module root runs unit tests (JUnit 5 + Truth), ktlint, and the
Konsist architecture gate — it must pass before any commit. Instrumented tests
(`./gradlew connectedDebugAndroidTest`, including the D19 renderer perf smoke gate) need an
emulator or device; CI runs both suites on every PR (`.github/workflows/android.yml`). Pure
modules must stay testable without Android.
