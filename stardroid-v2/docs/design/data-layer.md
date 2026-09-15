# Data Layer: Catalog Storage, Localization, Downloads

**Status: APPROVED** (D15, 2026-06-12) — Room over FlatBuffers, bundle-all-locales-initially
delivery, and the clone-moat strategy below.

## Requirements

- A few thousand objects today; tens of thousands later, plus per-object images and info-card
  text.
- All user-visible text must be localizable (~40 locales), without v1's hack of compiling
  object names into Android string resources via the `tools/` pipeline.
- Ship a small prepackaged catalog; grow it via downloads after install (also: corrections,
  transient objects like comets with refreshing orbital elements).
- The app must stay small and fast (North Star). Rendering needs the hot data (positions,
  magnitudes, colors) in memory every frame regardless of the storage choice.
- Future features that bear on the choice: filter by magnitude, alternative cultural
  constellation sets, richer info cards integrated with the datamodel.

## Room vs FlatBuffers

| Concern | Room (SQLite) | FlatBuffers |
|---|---|---|
| Zero-copy bulk load | No — but ~50k rows loads in tens of ms; trivial at our scale | Yes — its headline strength, which we don't need at <10MB |
| Incremental downloads / corrections | Natural (`INSERT`/`UPDATE`, transactions) | Poor — files are immutable blobs; updates mean shipping replacement files and managing a file set |
| Queries (search, magnitude filter, per-culture constellation sets) | SQL + indices, free | Build-it-yourself in memory |
| Localized text | A `translations` table keyed by (object, locale) with fallback chain | Parallel string tables per locale, hand-rolled |
| Relational data (object ↔ names ↔ images ↔ info cards) | Native | Hand-rolled |
| Prepackaged shipping | `createFromAsset()` is a first-class Room feature | Bundle files in assets |
| KMP portability | Room is KMP-capable since 2.7; SQLDelight is an alternative if Room KMP disappoints | FlatBuffers has multi-language support but the surrounding logic is all custom |
| Tooling/debuggability | Database Inspector, plain SQLite tools | Custom dump tools (v1's situation again) |

**Recommendation: Room.** FlatBuffers optimizes a problem we don't have (zero-copy load of
multi-hundred-MB assets) and gives up the things we do need (mutation, query, relations,
localization). At "tens of thousands of objects" SQLite is small and fast. The renderer never
queries the DB per frame: at startup (and on catalog change) one query loads the render-hot
columns into compact arrays/`FloatBuffer`s held by the catalog repository. If profiling ever
shows startup DB load to be slow, a derived binary cache can be added behind the same
repository interface without touching callers.

## Schema sketch (illustrative, not final)

```
object        (id, type, ra, dec, magnitude, color, layer_kind, source_pack, ...)
object_name   (object_id, locale, name, is_primary)        -- localized, searchable
info_card     (object_id, locale, title, body, image_ref)
line_figure   (id, layer_kind, culture, ...)               -- constellations etc.
line_vertex   (figure_id, seq, object_id | ra/dec)
pack          (id, version, source_url, installed_at)      -- provenance for downloads
```

Locale fallback at query time: exact locale → language → English. Downloads ship rows
(including translations), not code, so new catalogs and corrections need no app update.

## Localization delivery: avoiding shipping every locale to every phone

App Bundle locale splits only work for Android *resources*, not for assets or a database —
there is no per-locale targeting for asset packs. So the options are:

1. **Bundle all locales in the prepackaged DB** — acceptable only while the catalog is small.
   Sizing: most objects (stars) carry untranslated catalog designations; translated proper
   names are ~500 objects × ~40 locales × ~30 bytes ≈ <1 MB. Info-card text dominates: at
   v1's scale (~100 objects with cards) ~40 locales is roughly 1–2 MB.
2. **English base bundled + per-locale translation packs downloaded** from our server on first
   run (tens of KB per locale), stored as ordinary rows in the same tables.

**Proposal:** start with (1) — it keeps the initial port free of networking and costs ~1–2 MB —
and move to (2) when the download infrastructure lands and the catalog grows. The schema
(translations as rows keyed by locale) supports both without change; (2) also means a user
changing device language just fetches a pack rather than needing an app update.

## Server as a moat (clones) while keeping F-Droid viable

Background: Play Store clones repackage Sky Map; open data downloads would let them feed off
our server. With an open-source client there is no perfect technical lock, but the realistic
cloning vector (low-effort Play repackagers) can be addressed in layers:

1. **License split.** The bundled starter catalog stays freely licensed (it derives from
   public catalogs — Hipparcos etc.), keeping the F-Droid build 100% FOSS. The *downloaded*
   value-added datasets (curation, imagery, info-card content, transient-object feeds) are
   licensed for use in official Sky Map builds only. Because this data is fetched at runtime
   and never bundled, it does not affect F-Droid's build-from-source review. Crucially, the
   license is the practical moat: a clone that bundles or proxies our data gives us a clean
   DMCA/rights complaint to Google Play — historically more effective than technical measures.
2. **Attestation for gms builds.** The gms flavor obtains download tokens via Play Integrity:
   the server checks package name, signing certificate, and Play-install verdict. Repackaged
   clones fail all three. (Play Integrity is Google-proprietary, hence gms-only.)
3. **Open-but-throttled access for fdroid builds.** The fdroid flavor uses the same API
   without attestation, identified by client version, subject to rate limiting and abuse
   monitoring. fdroid users are few and are not the cloning vector; if an endpoint is abused
   at scale it can be rotated in an app update. F-Droid may tag the app with the `NonFreeNet`
   anti-feature (dependence on a non-free network service) — that is a label, not a rejection,
   and is common for apps with first-party servers.
4. **Graceful degradation.** The app must remain fully functional on the bundled catalog with
   no server access (offline, blocked, or post-shutdown) — which the small-bundled-catalog
   design guarantees anyway.

## Prepackaged database and F-Droid

The shipped DB is generated at build time by a Gradle task from human-readable source data
(CSV/JSON checked into the repo), then bundled via `createFromAsset()`. This serves two
purposes:

1. Replaces the v1 `tools/` pipeline (D7) with something inspectable and reproducible.
2. **F-Droid flag:** F-Droid requires everything to be built from source; an opaque checked-in
   `.db` (or `.binary`, as in v1) blob risks rejection or extra scrutiny. Generating the DB
   from in-repo text data during the build avoids this entirely. The same applies to any
   future prebuilt asset.

Other F-Droid watch-items (none fatal):
- No Play Services / Firebase in the `fdroid` flavor (same split as v1: LocationManager
  instead of fused location; analytics no-op).
- Catalog downloads must come from a plain HTTPS endpoint with FOSS client code (OkHttp/Ktor),
  no proprietary SDKs. Hosting our own data is fine.

## What loads into memory

Per visible layer, the repository exposes immutable snapshots (data classes over primitive
arrays) sized for rendering: geocentric unit vectors, magnitude, color, label refs. Info-card
text, images, and search live behind queries and are never resident wholesale.

**Pure vs. Android array types (G3/G12):** the snapshot types exposed by the pure `:core:catalog`
interfaces use only `DoubleArray`/`FloatArray` — KMP-safe, no `java.nio`. `FloatBuffer` (and any
other `java.nio`) is confined to the Android `:data`/`:render` modules at the GL upload boundary.
For the faithful port the stars layer maps these arrays into `List<PointPrimitive>` (D22); the
columnar `StellarPointBatch` render path is a deferred, additive optimization for the bulk
catalog, at which point the repository's columnar arrays feed the batch array-to-array.

## Progressive (magnitude-tiered) loading (D19)

To meet the first-frame budget (D19) without waiting for the whole catalog, the stars query
returns in magnitude tiers, each an additional `submit` to the stars layer (the renderer just
receives a fuller scene — no special support needed):

| Tier | Cut | ~count | When |
|---|---|---|---|
| 0 "instant" | mag ≤ 4 | ~500 | before first frame; may be a tiny dedicated table/asset loaded ahead of full-DB warmup |
| 1 "default" | mag ≤ 5 (default filter) | ~few thousand | streams in right after first frame |
| 2+ "on demand" | fainter | tens of thousands (future) | when the user raises the magnitude filter or zooms in |

Tier 0 is what the first-frame target is measured against; Tiers 1+ arrive asynchronously on the
catalog's IO dispatcher. This also bounds the live point count to a few thousand by default,
de-risking the no-point-culling stance (see [high-level-architecture.md](high-level-architecture.md)
culling, D13).
