---
name: skymap.add-object
description: Add a new deep-sky object or virtual object to Sky Map v2's catalog from a Wikipedia URL or user-supplied data, or add a new searchable alias/name to an existing object. Handles source-data CSV/JSON files (no protobuf/XML — v2 uses a Room-backed catalog generated from source-data/). Trigger on "add object to v2", "add nebula/galaxy/cluster to v2", "add <object name> to stardroid-v2", "add <name> as an alias for <object>", etc. ARGUMENTS — "[wikipedia_url_or_object_name]"
---

# Sky Map v2: Add Catalog Object

Add a new deep-sky or virtual object to v2's `source-data/` tree. This is a different pipeline
from v1's `skymap.add-object` skill: v2 has no `celestial_objects.xml`, no
`celestial_info_cards.xml`, no `object_info.json`, and no protobuf regeneration step. Instead
`source-data/` (CSV + JSON, English + per-locale) is compiled into a bundled Room database
(`skymap.db`) automatically at build time by the `:data:generateCatalogDb` Gradle task — see
`source-data/README.md` and `docs/design/catalog-and-schema.md`.

Work from `stardroid-v2/`.

## Files to Edit (in order)

1. `source-data/dso.csv` — positional catalog (skip for virtual/map-less objects — see below)
2. `source-data/names/en.csv` — primary English name(s)
3. `source-data/names/universal.csv` — catalog designations that should match in every locale
   (M31, NGC 224, etc.)
4. `source-data/info_cards/en.json` — info card text (English; other locales are a separate
   translation pass, not part of this skill — see AGENTS.md on translation timing)
5. `source-data/objects.json` — image reference + metadata overlay (see-also links, parent, type
   override for virtual objects)

## Step 1 — Gather Astronomical Data

If `$ARGUMENTS` contains a Wikipedia URL, fetch that page and extract:

| Field | Notes |
|---|---|
| Primary name + aliases | Primary → `names/en.csv` (`is_primary=1`); catalog aliases (NGC/IC/M/Caldwell numbers) → `names/universal.csv` (`is_primary=0`) |
| RA (decimal degrees) | Convert from h m s: `(h + m/60 + s/3600) * 15` |
| Dec (decimal degrees) | Convert from °ʹʺ: `±(d + m/60 + s/3600)` |
| Magnitude | Search a second source if not on Wikipedia; ask the user if still unknown |
| Angular size (arcmin) | Search a second source if not on Wikipedia; ask the user if still unknown. Currently unused by the app but retained in the schema — still fill it in. |
| Type | Hierarchical code from `source-data/types.json`, e.g. `nebula.planetary`, `galaxy.spiral`, `cluster.globular`. Add a new `types.json` entry only if nothing existing fits — ask the user first. |
| Distance (light-years) | For info card |
| Physical size / mass | For info card `size`/`mass` strings |
| Any catalog numbers | NGC, IC, Messier, Caldwell — go in `designations` (dso.csv) and `names/universal.csv` |

**Never use placeholder or default values** for magnitude or angular size. If a value cannot be
found after searching a second source, ask the user before proceeding.

## Step 2 — Derive the Object ID

Namespaced, stable, permanent (never renamed once added — decision in `source-data/README.md`):
`dso/<slug>`, where `<slug>` is the primary name lowercased, spaces → underscores, special chars
stripped. Example: `Exposed Cranium Nebula` → `dso/exposed_cranium_nebula`.

Unlike v1, there is **no digit-prefix rule** to worry about — ids are free-form strings, not
Android resource names. `47 Tucanae` can be `dso/47_tucanae` directly.

Use the same id consistently across all four files below.

## Step 3 — Edit dso.csv

Header: `id,ra_deg,dec_deg,magnitude,type,size_arcmin,designations,names`

Append a row (order doesn't matter, but keep roughly alphabetical/grouped with neighbors for
diff-friendliness):

```
dso/exposed_cranium_nebula,142.03422,-49.6107,13.0,nebula.planetary,1.5,PMR 1,Exposed Cranium Nebula
```

- `designations`: pipe-separated catalog IDs (e.g. `M1|NGC 1952`), or the primary catalog ID if
  no common name exists.
- `names`: informational only (not used for display — real display names live in `names/*.csv`);
  keep it for readability/grep-ability.

**Skip this step for virtual (map-less) objects** — see the dedicated section below.

## Step 4 — Edit names/en.csv

Header: `object_id,name,is_primary`

```
dso/exposed_cranium_nebula,Exposed Cranium Nebula,1
```

Add one row per alias that should be searchable in English, `is_primary=0` for anything that
isn't the map label. If the name has non-English-locale variants you know about, that's a
separate translation task — don't invent them here.

## Step 5 — Edit names/universal.csv

Header: `object_id,name,is_primary`

Add one row per catalog designation that should search-match regardless of locale (these are
the `''` universal locale):

```
dso/exposed_cranium_nebula,PMR 1,0
```

## Step 6 — Edit info_cards/en.json

`source-data/info_cards/en.json` is `{"cards": {"<id>": {...}}}`. Insert a new entry:

```json
"dso/exposed_cranium_nebula": {
  "description": "<2-3 sentence description: what it is, where it is, what makes it notable>",
  "fun_fact": "<1-2 sentence surprising/record-breaking/historically significant fact>",
  "distance": "~5,000 light-years",
  "size": "~2 light-years across",
  "image_credit": "<Credit string — added once Step 8's image is deployed>"
}
```

Optional fields, add only when relevant:
- `mass` — for galaxies/black holes (e.g. `"1.5 trillion solar masses"`)
- `spectral_class` — for stars
- `search_subtext` — subtitle shown below the name in search results; mainly used for virtual
  objects (e.g. `"Moon of Jupiter"`)

Keep valid JSON — run `python3 -m json.tool source-data/info_cards/en.json >/dev/null` to
verify after editing.

## Step 7 — Edit objects.json

`source-data/objects.json` is `{"objects": {"<id>": {...}}}`. This is metadata *overlay* on top
of the positional catalog — only add fields you actually have:

```json
"dso/exposed_cranium_nebula": {
  "image_ref": "deep_sky_objects/pmr1_exposed_cranium_nebula.webp"
}
```

Add `image_ref` once Step 8 (image) has deployed the asset. `see_also` (array of related object
ids, reciprocal both ways) is optional — add it if there's an obvious related object (parent
planet for a moon, sibling galaxies, etc.).

## Adding an Alias to an Existing Object

Sometimes the ask isn't a new object at all — it's an additional searchable name for one that
already exists (e.g. a culturally significant name, a colloquial nickname). This is much
lighter than the full flow above: no `dso.csv`/`stars.csv` row, no info card, no `objects.json`
change, no image.

1. Find the object's existing id — grep `source-data/names/en.csv` or `dso.csv`/`stars.csv` for
   its current display name.
2. Add one row to `source-data/names/en.csv` (or `names/universal.csv` if the name is a catalog
   designation that should match in every locale, e.g. an NGC number): `<object_id>,<Name>,0`.
   Use `is_primary=0` — you're adding a search alias, not replacing the map label. Group it next
   to the object's other existing rows for diff-friendliness.
3. Don't invent translations for other locales — that's a separate task. Per `LocaleSpec`'s
   fallback chain (exact tag → language → English → universal), an English-only alias is still
   searchable from any locale that doesn't override that object's names.
4. Sanity-build and confirm the row landed: `./gradlew :data:generateCatalogDb`, then inspect
   the generated db (`data/build/generated/assets/generateCatalogDb/skymap.db`) — e.g.
   `sqlite3 <path> "SELECT * FROM object_name WHERE object_id='<object_id>';"`.
5. Run `./gradlew :data:test :data:generator:test ktlintCheck` before committing.

Example: adding "Matariki" (the Māori name for the Pleiades, and NZ's official holiday marking
the Māori new year) as a search alias for `dso/m45`:

```
dso/m45,Matariki,0
```

added next to the existing `dso/m45,Pleiades,1` / `dso/m45,Seven Sisters,0` rows in
`names/en.csv`.

## Adding a Virtual (Map-less) Object

A virtual object has an info card, appears in search, and can be linked from a related object's
"see also" — but is not itself rendered on the sky map (e.g. moons, positioned via their planet).

Skip Steps 3 (dso.csv) entirely — virtual objects still get `names/en.csv` and
`info_cards/en.json` entries, but `objects.json` carries the positioning/type metadata instead
of a catalog row:

```json
"moon/io": {
  "type": "moon",
  "parent": "planet/jupiter",
  "image_ref": "planets/galileo_io.webp",
  "see_also": ["planet/jupiter", "moon/europa", "moon/ganymede", "moon/callisto"]
}
```

- `type` here overrides/supplies the type for objects with no `dso.csv`/`stars.csv` row.
- `parent` is the id of the renderable object the map should navigate to when this object is
  searched or opened.
- Add the reciprocal `see_also` entry on the parent object too.
- Use the `moon/<name>` namespace for moons (matching existing convention); other virtual-object
  namespaces should follow the pattern of what they represent.

## Step 8 — Image (optional but recommended)

Same sourcing rules as v1 — look for a freely licensed image:
- **NASA** (nasa.gov, hubblesite.org, webb.nasa.gov) — public domain
- **ESA/Hubble or ESA/Webb** (esahubble.org, esawebb.org) — CC BY 4.0, attribute
- **ESO** (eso.org) — CC BY 4.0, attribute
- **Wikipedia** (wikipedia.org) — CC BY-SA 4.0, attribute
- Avoid images with unclear or more restrictive licenses.

Invoke the **`skymap.celestial-image`** skill (v2 version — same 480×800 WebP pipeline as v1,
same `app/src/main/assets/celestial_images/<category>/` destination) to process and deploy it.
After it completes, set `image_ref` (Step 7) to the returned path and add `image_credit` in the
info card (Step 6).

## Step 9 — Verify

- [ ] `dso.csv` row added with correct decimal-degree RA/Dec (or skipped for virtual objects)
- [ ] `names/en.csv` has a primary row; aliases added where relevant
- [ ] Catalog designations added to `names/universal.csv`
- [ ] `info_cards/en.json` entry present and the file is still valid JSON
- [ ] `objects.json` entry present with `image_ref` if an image was added
- [ ] If image added: file exists at `app/src/main/assets/celestial_images/<category>/<name>.webp`
- [ ] Sanity-build the catalog: `./gradlew :data:generateCatalogDb` (no manual step needed
      otherwise — it's wired into the normal build)

## Reference — Coordinate Conversion

```
RA:  h m s  →  (h + m/60 + s/3600) * 15   (decimal degrees — NOT decimal hours, unlike v1!)
Dec: D° M' S"  →  ±(D + M/60 + S/3600)     (decimal degrees, negative = south)
```

Example: RA 9h 28m 41.28s → (9 + 28/60 + 41.28/3600) × 15 = **142.1719**
Example: Dec −49° 36′ 38.46″ → −(49 + 36/60 + 38.46/3600) = **−49.6107**

**This differs from v1**, which stores RA in decimal hours — v2's `dso.csv`/`stars.csv` store RA
in decimal degrees throughout (`ra_deg` column). Don't reuse a v1 decimal-hours value directly.

## Reference — Existing Object Patterns

| Object | Id | Type |
|---|---|---|
| Crab Nebula (M1) | `dso/m1` | `nebula.supernova_remnant` |
| Cygnus X-1 | `dso/cygnus_x_1` | `black_hole` |
| Andromeda Galaxy (M31) | `dso/m31` | `galaxy.spiral` |
| Io (Jupiter's moon) | `moon/io` | `moon` (virtual, `parent: "planet/jupiter"`) |
| Orionids meteor shower | `shower/orionids` | `meteor_shower` (virtual, fixed `ra`/`dec`/`activity`) |

Valid top-level type roots (see `source-data/types.json` for the full hierarchy): `star`,
`planet`, `dwarf_planet`, `moon`, `constellation`, `galaxy`, `cluster`, `nebula`, `star_cloud`,
`asterism`, `black_hole`, `meteor_shower`, `other`.
