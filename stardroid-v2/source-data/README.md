# source-data/

Human-readable source of truth for the bundled catalog database. The
`:data:generateCatalogDb` Gradle task compiles this tree into
`skymap.db` (a build artifact, never checked in) — see
[docs/design/catalog-and-schema.md](../docs/design/catalog-and-schema.md).
Keeping the sources readable and version-controlled is what keeps the
generated database reproducible (an F-Droid requirement).

Everything here was harvested once from the v1 app's data files by
[tools/harvest-v1/harvest.py](../tools/harvest-v1/harvest.py); from now on
this tree is edited directly and v1 is not consulted again.

## Files

| File | Contents |
|---|---|
| `stars.csv` | Star catalog: `id, ra_deg, dec_deg, magnitude, names`. Positions are J2000 degrees; magnitudes are true visual magnitudes from v1's raw `stardata_names.txt` (richer than v1's baked protos). The `names` column is informational; localized names live in `names/`. |
| `dso.csv` | Deep-sky objects: `id, ra_deg, dec_deg, magnitude, type, size_arcmin, designations, names`. `type` is a hierarchical code from `types.json`; `designations` are universal catalog labels (M31, NGC 224) that match in every locale; `size_arcmin` is retained for future use (not yet loaded). |
| `constellations/iau.json` | IAU constellation stick figures: per constellation the search/label positions and polyline `strokes` (J2000 degrees). Alternative cultures would be sibling files. |
| `objects.json` | Object registry beyond the positional catalogs: solar-system bodies and planetary moons (position-less — the ephemeris positions them), meteor showers (fixed radiant `ra`/`dec`, a `search_fov`, and an `activity` window — `from`/`peak`/`to` as year-agnostic `"MM-DD"` dates that must not cross a year boundary, plus `peak_zhr`, the peak zenithal hourly rate — which places them in the meteor-showers layer), plus per-object metadata overlays (info-card image key, "see also" links, parent object). |
| `names/{tag}.csv` | Localized names per BCP-47 tag: `object_id, name, is_primary`. `universal.csv` is the `''` locale — designations that match every request locale. Non-English files only carry objects whose names differ from English, but then carry the full row set so locale fallback stays whole-object. Map labels use *primary* names only (secondary rows — Bayer/Flamsteed designations — are search-only), so any object that should be labeled on the map must carry exactly one `is_primary=1` row in its display locale chain. **`universal.csv` holds designations only — never English prose.** Its rows join every locale untranslated (the pipeline excludes the file), so a name with an English word in it must live in `en.csv` or it can never be translated for anyone; mirror `dso.csv`'s own `designations` (`M8`, `NGC 6523`) versus `names` (`Lagoon Nebula`) split when deciding where a row belongs (D108). |
| `info_cards/{tag}.json` | Localized info-card text per locale (description, fun fact, distance/size/mass display strings) plus non-localized fields (spectral class, image credit, search subtext) copied into each locale so fallback rows are self-contained (whole-row fallback, decision D33). |
| `types.json` | The hierarchical object-type vocabulary (`galaxy.spiral` → parent `galaxy`) with localized display names. Types are data, not code: packs may add subtypes (decision D32/D33). |

## Identifiers

Stable object ids are namespaced (`star/sirius`, `dso/m31`,
`constellation/orion`, `planet/jupiter`, `moon/io`, `shower/perseids`).
`planet/<body>` is the join key to `SolarSystemBody` for all
ephemeris-positioned bodies, including `planet/sun` and `planet/moon`.
Unnamed stars carry positional J-designation ids (`star/j064459-164236`).
Ids are permanent: never renamed, only added.

## Licensing

Scientific data (star positions/magnitudes, DSO catalog, constellation
figures) is factual/public-domain material inherited from v1's data files.
Star proper names in `names/*.csv` come from the IAU Working Group on Star
Names (WGSN) Catalog of Star Names (CC BY 4.0, credited on the in-app Help
screen); Bayer/Flamsteed designations come from the Yale Bright Star
Catalogue, 5th ed. (public-domain catalog data). Both were merged by
`tools/star-names/augment_names.py`. Card text and translations were written
for Sky Map and are covered by the repository's GPLv3 code license. No
branding assets live here.
