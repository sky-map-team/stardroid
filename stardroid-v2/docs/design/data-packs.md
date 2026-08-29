# Downloadable Data Packs: Format, Storage, Delivery

**Status: PROPOSED** (D79) — design only; no code changes accompany this document.

Builds on [data-layer.md](data-layer.md) (Room over FlatBuffers, "grow via downloads after
install") and the pack substrate that has shipped since slice 4: every catalog row carries a
`pack_id`, `PackDao.applyPack`/`removePack` are transactional, cross-pack references may
dangle by design (D33), and the type vocabulary is data so packs can introduce subtypes the
app has never seen (D32). This document designs the piece that doesn't exist yet: how a pack
gets from a server into that substrate, and what leaves the core APK to make room.

## Motivation

The bundled image assets dominate app size:

| Asset group | Size | Files | Notes |
|---|---|---|---|
| `celestial_images/deep_sky_objects` | 4.6 MB | 74 | Messier/NGC photos (Hubble, Webb, NOIRLab) |
| `celestial_images/constellations` | 2.5 MB | 89 | IAU charts — core UX (tap a constellation) |
| `celestial_images/stars` | 1.9 MB | ~63 | DSS2 star-field photos — near-identical framing |
| `celestial_images/planets` | 1.1 MB | ~15 | Core UX |
| `skymap.db` (bundled catalog) | 7.4 MB | 1 | After D61 (3,186 stars + ~10k names) |

The star photos are the weakest content per byte — most are an indistinguishable DSS2 field
with a bright dot — and D47 already promised that catalog `image_ref`s beyond the bundled set
"arrive with downloadable packs". Separately, D61 left a ready-made first *catalog* pack: the
2,812 filtered-out faint stars and their pruned names sitting in `source-data/`.

D19's budget (catalog + assets ≤ ~3 MB over v1) is the standing pressure: every future
content addition either fits the budget or ships as a pack.

## Pack format

A pack is a single versioned **zip** file:

```
starphotos-1.zip
├── manifest.json     # identity, integrity, licensing
├── rows.json         # OPTIONAL: catalog rows, applied via PackDao.applyPack
└── images/           # sidecar files, copied to filesDir (never into the DB)
    └── eso_sirius.webp …
```

`manifest.json`:

```json
{
  "pack_id": "starphotos",
  "version": 3,
  "min_schema_version": 1,
  "title": {"en": "Star photos"},
  "size_installed": 1988466,
  "license": "ESO DSS2, CC BY 4.0",
  "source_url": "https://github.com/sky-map-team/stardroid",
  "sha256": {"rows.json": "…", "images/eso_sirius.webp": "…"}
}
```

- `rows.json` carries the same entity set the generator emits today (objects, names, types,
  links, cards, figures, showers — any subset), tagged with the pack's `pack_id`. It is
  produced by the **same generator pipeline** as `skymap.db` (`data/generator` reading a
  `source-data/`-shaped tree), fulfilling D34's "same task later produces downloadable pack
  files". It is **optional**: an image-only pack (star photos) omits it entirely and ships
  just `manifest.json` + `images/` — see the override semantics below.
- **Images are files, not blobs.** The DB stores `image_ref` strings; bytes live on disk.
- `min_schema_version` gates installation: an old app refuses a pack written for a newer
  schema rather than failing halfway through.

**Image-ref override semantics.** `celestial_object.id` is the table's sole `@PrimaryKey` —
`pack_id` is an indexed column, not part of the key — and `applyPack` inserts objects with
abort-on-conflict. Two packs cannot hold rows for the same object id: the second
`applyPack` throws `SQLiteConstraintException` and rolls back, which
`PackReplacementTest.failedApplyRollsBackLeavingPriorStateIntact` already asserts. So an
overriding pack must *not* ship a duplicate row, and the duplicate-row scheme an earlier
draft of this document proposed is off the table. Two consequences:

- **Image-only packs carry no `celestial_object` rows at all.** The override happens in the
  file layer, not the DB: `image_ref` is a key, and `CelestialImageResolver` (below) already
  searches installed packs before bundled assets. A star-photos pack ships `images/` plus a
  manifest and touches the catalog only if it also adds *new* objects. This is strictly
  simpler than the row-duplication scheme — no reader-side dedup, and no change to any query.
- **Packs own disjoint id ranges.** Object ids stay globally unique across packs (the
  existing `pack_id`-prefixed convention); a pack that genuinely needs to *replace* a core
  object's catalog fields is out of scope here and would need a real merge/override story
  (deferred below).

Because no query ever sees two rows for one id, the joins on `object_id` in `CatalogDao`
(`searchNameRows`, `galleryRows`, `linkRows`, `layerObjectRows`, `meteorShowerRows`) keep
their current fan-out — one row per (object, candidate-name) pair, collapsed by the
repository's existing `groupBy { it.id }` + `pickByChain` locale fallback. No SQL changes and
no new Kotlin-side dedup. `removePack` stays trivially correct: a pack's rows are exactly the
rows carrying its `pack_id`.

## Storage and install

```
filesDir/packs/<pack_id>/<version>/images/…
filesDir/packs/<pack_id>/<version>/rows.json   # if present; kept for core-pack refresh
```

Atomic install sequence:

1. Download the zip via `setDestinationInExternalFilesDir(context, null, "packs/<id>-<v>.zip")`
   (resumable; see Delivery), then move it into `cacheDir`. `DownloadManager` is a separate
   system process and writes to app-scoped *external* paths, not to internal `cacheDir`
   directly; on minSdk 29 that path needs no storage permission, and the move is a rename
   when both live on the same volume, a stream-copy otherwise. If external storage is
   unavailable (`getExternalFilesDir` returns null — removable media ejected), the install
   fails cleanly and is retried later rather than falling back to a permissioned path.
2. **Verify the zip's own sha256 against the pack index** — the index is the trust root,
   fetched over TLS, and the only integrity claim not carried inside the artifact being
   checked. Only then open it and verify each `manifest.json` `sha256` against the unpacked
   entries. The manifest's hashes guard against truncation and partial unpacking; they cannot
   establish authenticity, because an attacker who rewrites the zip rewrites the manifest with
   it. A zip failing the index hash is deleted without being opened.
3. Unpack `images/` to `filesDir/packs/<id>/<version>.tmp/`, then rename to `<version>/`.
4. If the pack has a `rows.json`, `applyPack(rows)` in one transaction (replaces any older
   version's rows — `applyPack` already deletes-then-inserts by `pack_id`). An image-only
   pack still records its `PackEntity` row so version tracking and `removePack` work
   uniformly.
5. Retain `rows.json` alongside `images/` (the core-pack refresh below re-applies it), then
   delete older `<version>` directories and the cached zip.

A crash at any step leaves either the previous pack state or a complete new one (the rename +
transaction are the two commit points; a `.tmp` directory or cached zip is garbage-collected
on next launch). Uninstall = `removePack(id)` + delete the pack directory.

## The image-resolver seam (prerequisite refactor, PR-able now)

Three UI sites hardcode `"celestial_images/$imageRef"` asset paths today
(`ObjectInfoUi.kt`, `ImageExpandOverlay.kt`, `GalleryUi.kt`). Before any download code
exists, introduce an injected resolver (Hilt, D59):

```kotlin
interface CelestialImageResolver {
    /** Coil-loadable model for an image_ref: pack file, bundled asset, or null. */
    fun resolve(imageRef: String): Any?
}
```

Resolution order: installed packs (newest version first) → bundled assets → null
(caller shows the existing placeholder). This is the only app-code seam the feature needs;
everything else is new code behind it.

## Delivery

| Channel | gms flavor | fdroid flavor |
|---|---|---|
| Chosen | **Plain HTTPS + system `DownloadManager`** | Same |
| Alternative considered | Play Asset Delivery (on-demand packs) | Bundle everything (status quo) |

One mechanism for both flavors: a static, versioned **pack index** (JSON listing available
packs, versions, sizes, URLs, sha256) fetched over HTTPS, with pack zips hosted alongside it
(GitHub Releases of the stardroid repo is sufficient at launch scale; the index URL is a
single constant to change later). Downloads go through the system `DownloadManager`:

- No new dependencies — the repo currently has zero networking code, and this keeps it that
  way (no OkHttp/Retrofit/Ktor).
- Resumable, survives process death, respects metered/roaming settings out of the box.
- Play Asset Delivery was rejected: it is gms-only (fdroid would need a second mechanism
  anyway), caps flexibility (packs tied to app releases), and its on-demand API is heavier
  than the requirement.

F-Droid policy note: downloading *data* over HTTPS is acceptable (no code is executed); the
index and zips must be served over TLS and the app must function fully without them.

**Core-pack refresh on app update (related bug, fix with this feature):** Room's
`createFromAsset` copies the bundled DB only when no database file exists, so an app update
with a newer bundled catalog silently keeps serving the old rows (observed during D61
verification). The fix belongs to this feature's substrate: bump the `pack.version` of the
builtin `core` row at generation time, and on startup compare the installed core version
against the bundled asset's (stored in a preference written at copy time); when the asset is
newer, refresh. Note the asset is a prebuilt SQLite file, not a `rows.json` pack, so
`applyPack` does not accept it directly. Two cases:

- **No non-builtin packs installed** (the overwhelmingly common case, and the only one until
  phase 1 ships): close the database, `deleteDatabase(DATABASE_NAME)`, and reopen so Room
  re-copies from the asset. Deleting a live database file risks corrupting open connections,
  so the close must precede the delete and the refresh must run before the first query of the
  session — this is exactly the discipline `SkyMapDatabaseFactory.createWithRecovery` already
  follows for the D24/G11 corrupt-DB path, and the refresh check belongs in the same
  single-entry place for that reason.
- **Packs installed:** the same delete + re-copy, followed by re-applying each installed
  pack's cached `rows.json` (retained in `filesDir/packs/<id>/<version>/` for this purpose —
  the install sequence's step 5 must therefore keep `rows.json`, not just `images/`).
  `ATTACH DATABASE` + `INSERT OR REPLACE ... WHERE pack_id = 'core'` is the alternative; it
  avoids re-applying packs but adds a raw-SQL path that has to track every table the
  generator writes, and it silently skips core rows *deleted* since the last release. Re-copy
  + re-apply is preferred: it reuses the tested `applyPack` transaction and makes the bundled
  asset unambiguously the source of truth for core.

Either way the refresh is a single guarded step at startup before the DB is handed out, so no
other connection is open when the file is replaced.

## What leaves the core APK

- **Phase 1 — `stars/` (1.9 MB):** becomes the optional "Star photos" pack. Only the *image
  files* leave the APK; the star `image_ref`s stay in `objects.json` and the catalog, so the
  pack is pure `images/` with no `rows.json` at all. `CelestialImageResolver` returns null for
  those refs until the pack is installed, and info cards show the existing no-image layout —
  the same path already taken by any ref whose asset is missing. Installing the pack makes
  them resolve; `removePack` makes them stop. No catalog write is involved in either
  direction, which is what makes phase 1 shippable without any of the deferred override
  machinery.
- **Phase 2 (candidate) — non-Messier `deep_sky_objects/`:** revisit once the expanded DSO
  catalog exists; Messier photos stay bundled (they anchor the gallery).
- **Stays bundled:** constellations (tap-a-constellation is core UX), planets (rendering
  textures + hero images), all of `skymap.db`.

## Budget accounting (vs D19)

| Change | APK delta |
|---|---|
| D61 catalog (already shipped) | +0.15 MB |
| Phase 1: stars/ → pack | −1.9 MB |
| Net after phase 1 | **−1.75 MB** |

## Out of scope (deferred)

- **Pack-management UI** (browse/install/remove screen; a Settings entry point suffices for
  phase 1's single pack).
- **Translation packs** (data-layer.md's English-base + per-locale plan; same substrate).
- **Expanded star catalog pack** (the D61 leftovers: mag ≥ 5.6 stars + their names —
  ready-made content, needs only the pack build task).
- **Delta updates** (packs are small enough to re-download whole).
- **Cross-pack field overrides** (a pack replacing another pack's `celestial_object` fields).
  Blocked by the single-column `@PrimaryKey` on `celestial_object.id`; would need a composite
  `(pack_id, id)` key plus precedence resolution in every `object_id` join, and phase 1 needs
  none of it — image overrides resolve in the file layer instead (see Pack format).
- **Comet/transient-object packs with refreshing orbital elements** (data-layer.md
  requirement; needs an ephemeris-row story first).
