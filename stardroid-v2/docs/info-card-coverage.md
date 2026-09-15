# Info-card coverage

Which labelled objects still lack an info card, and why it matters. Written after the
audit that produced the Tier 1 / Messier card batch; refresh the numbers with the query
at the end rather than trusting them indefinitely.

## Why coverage matters

A missing card is not merely a thinner card — it removes the object from tap-to-identify
entirely. `ObjectInfoViewModel.candidates()` intersects the tap-candidate set with
`repo.infoCardObjectIds()` (`SELECT DISTINCT object_id FROM info_card`), so an object with
no card row is not a hit candidate at all. The label still draws; tapping it does nothing.

Search and see-also chips do **not** filter on card presence, so an uncarded object stays
reachable by name — it just opens a sparse card with no description or fun fact.

Net effect of a missing card:

| Path | Uncarded object |
|---|---|
| Map label | drawn |
| Tap the label | **nothing happens** |
| Search by name | works, opens a sparse card |
| See-also chip | works, opens a sparse card |

## Counting rules

Two rules matter, and getting either wrong inflates the gap:

1. **"Labelled" means a primary name in the locale chain.** `CatalogLayers.label()` returns
   early unless `obj.nameIsPrimary`. Secondary rows (Bayer/Flamsteed designations) are
   search-only and never labelled, so they are not part of the gap.
2. **Count against the shipped database, not `source-data/`.** The generator drops stars at
   or above `BUNDLED_STAR_MAX_MAGNITUDE` (5.6) from the bundled pack, along with their names
   and cards. `source-data/stars.csv` holds 5,998 stars; only 3,186 ship. Auditing the CSVs
   directly overstates the gap by counting objects the app never draws.

Card text for filtered-out stars is still worth writing — it travels with the future
expanded pack and becomes live when the cutoff rises. It just does not count as a gap today.

## Current state

Figures below are the shipped `skymap.db` and assume PR #96 (Tier 1 + Messier cards) has
merged.

| Layer | Labelled | Carded | Missing |
|---|---:|---:|---:|
| Stars | 324 | 77 | **247** |
| Deep sky | 135 | 135 | 0 |
| Constellations | 89 | 89 | 0 |
| Solar system<sup>†</sup> | 28 | 28 | 0 |
| Meteor showers | 10 | 10 | 0 |
| **Total** | **586** | **339** | **247** |

<sup>†</sup> Planets and moons carry no `layer_kind` in `celestial_object` — the ephemeris
positions them, so they are not a catalog layer.

Every non-star category is fully covered. The entire remaining gap is stars.

### What a user actually hits

The label declutterer only draws labels brighter than
`LabelDeclutterer.magnitudeThreshold(fov)` — `4.0 + max(0, 45 - fov) / 10` with the
constants as shipped — so the gap that matters depends on zoom:

| View | Label magnitude limit | Star labels shown | ...lacking cards |
|---|---:|---:|---:|
| Default (45° FOV) | 4.0 | 245 | **169** |
| Zoomed in (≤20° FOV) | 6.5+ | 324 | **247** |

At the default zoom roughly two in three labelled stars still do nothing when tapped.

## Remaining work, by priority

### Magnitude 2–3 — 61 stars

The highest-value batch: all are labelled at default zoom and most are well-known.

Alsephina, Menkent, Tiaki, Almach, Naos, Aspidiske, Suhail, Sadr, Mintaka, Mizar, Dschubba,
Larawag, Aludra, Aljanah, Markeb, Zosma, Acrab, Gienah, Ascella, Zubeneschamali, Sheratan,
Phact, Mahasim, Kraz, Ruchbah, Muphrid, Hassaleh, Lesath, Kaus Media, Tarazed, Yed Prior,
Athebyne, Hatysa, Zubenelgenubi, Cebalrai, Kornephoros, Rasalgethi, Cursa, Imai, Rastaban,
Nihal, Paikauhale, Kaus Borealis, Tureis, Fawaris, Tejat, Deneb Algedi, Acamar, Albaldah,
Gomeisa, Cor Caroli, Fang, Alniyat, Sadalsuud, Matar, Algorab, Sadalmelik, Zaurak,
Tianguan, Alnasl, Okab

Note **Mintaka** — Orion's Belt is still split, with Alnitak and Alnilam carded but Mintaka
not. **Mizar** is likewise a conspicuous omission given Alcor sits beside it.

### Magnitude 3–4 — 107 stars

Still labelled at default zoom. Includes several famous doubles and variables: Albireo,
Sheliak, Thuban, Porrima, Algedi, Sulafat, Megrez.

### Magnitude 4–5 — 61 stars

Only labelled on zoom-in. Includes Alcor (mag 4.01 — the classic naked-eye test beside
Mizar, and both lack cards), the Pleiades members Merope and Taygeta, Keid and Aladfar.

### Magnitude 5–5.6 — 18 stars

Faintest shipped labels: Elkurud, Marsic, Situla, Pleione, Chalawan, Guniibuu, Cervantes,
Nahn, Revati, Veritate, Taiyi, Intercrus, La Superba, Tianyi, Celaeno, Polaris Australis,
Helvetios, Musica.

Several are notable despite being faint — Chalawan, Cervantes, Veritate and Helvetios are
all IAU-named exoplanet hosts, and La Superba is a striking deep-red carbon star.

## Known data issues

- **Gacrux magnitude.** The catalog carries 1.59; Wikipedia's infobox gives 1.64. Traced to
  v1's `stardata_names.txt`, so it is inherited rather than introduced by v2. Star
  magnitudes feed both rendering and the declutterer, so a sweep of catalog magnitudes
  against a modern source is its own piece of work.
- **Bare designations flagged primary.** Fixed for `star/n25` (now R Coronae Borealis) and
  three Gould-number stars (demoted to search-only). Two remain **intentionally** labelled
  because they are designations observers genuinely use: `p Eridani` and `ε Lyrae`.
- **`47 Tucanae` and `NGC 2903`** are labelled by catalogue designation, but those *are* the
  names those objects are known by. No action.

## Writing cards

Match the house style in `source-data/info_cards/en.json`:

- `description` — roughly 80–130 characters: what the object is and where it sits.
- `fun_fact` — roughly 80–140 characters, usually ending in `!`. One striking, verifiable
  fact.
- Numeric fields (`distance`, `size`, `mass`, `spectral_class`) are display strings with
  units. **Omit a field rather than estimating it** — the renderer skips nulls.
- `image_credit` only when an image is actually added.

Facts should be verifiable against a cited source, checked against the article's infobox
rather than its prose. Watch for two traps found during the last batch: Wikipedia URLs for
common star names are sometimes disambiguation pages (`Avior`, `Atria`), and at least one
article carried an unverifiable IAU name that is absent from the official IAU Catalog of
Star Names. Prefer the IAU CSN for proper names.

Cards for objects that share coordinates need care — `Toliman` is α Centauri B specifically
and sits at the same position as `star/rigil_kentaurus_a`, whose card covers the system.

After editing, run `./gradlew :data:generateCatalogDb` (validation rejects unknown object
ids) and update the hardcoded English card count in `CatalogDbGeneratorTest`.

## Refreshing these numbers

```sql
-- Labelled objects with no English info card, in the shipped DB.
SELECT COALESCE(o.layer_kind, 'solar system'), COUNT(*)
FROM celestial_object o
WHERE EXISTS (SELECT 1 FROM object_name n
              WHERE n.object_id = o.id AND n.is_primary = 1
                AND n.locale IN ('en', ''))
  AND NOT EXISTS (SELECT 1 FROM info_card c
                  WHERE c.object_id = o.id AND c.locale = 'en')
GROUP BY o.layer_kind;
```

Run against `data/build/generated/assets/generateCatalogDb/skymap.db` after
`./gradlew :data:generateCatalogDb`.
