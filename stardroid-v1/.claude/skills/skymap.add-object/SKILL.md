---
name: skymap.add-object
description: Add a new deep-sky object or special object to Sky Map's catalog from a Wikipedia URL or user-supplied data. Handles all four required files. Trigger on "add object", "add nebula/galaxy/cluster", "add <object name> to Sky Map", etc. ARGUMENTS: "[wikipedia_url_or_object_name]"
---

# Sky Map: Add Catalog Object

Add a new deep-sky or special object to Sky Map's data files from a Wikipedia page or supplied
astronomical data.

## Files to Edit (in order)

1. `stardroid-v1/tools/data/deep_sky_objects.csv` — source catalog
2. `stardroid-v1/app/src/main/res/values/celestial_objects.xml` — searchable name strings
3. `stardroid-v1/app/src/main/res/values/celestial_info_cards.xml` — info card text
4. `stardroid-v1/app/src/main/assets/object_info.json` — info card metadata + image references

## Step 1 — Gather Astronomical Data

If `$ARGUMENTS` contains a Wikipedia URL, fetch that page and extract:

| Field | Notes |
|---|---|
| Primary name + aliases | Pipe-separated in CSV; all need strings in celestial_objects.xml |
| RA (decimal hours) | Convert from h m s: `h + m/60 + s/3600` |
| Dec (decimal degrees) | Convert from °ʹʺ: `±(d + m/60 + s/3600)` |
| Magnitude | Search a second source if not on Wikipedia; ask the user if still unknown |
| Angular size (arcmin) | Search a second source if not on Wikipedia; estimate from scale bars only if a scale bar with units is visible in an image; ask the user if still unknown |
| Type | Planetary Nebula / Diffuse Nebula / Galaxy / Globular Cluster / etc. |
| Constellation | |
| Distance (light-years) | |
| Physical size | For info card size string |
| Any catalog numbers | NGC, IC, etc. for the NGC# column |

**If the fetch returns 403 or is blocked by robots.txt**, stop and ask the user to download the
page and paste or save the content locally.

**Never use placeholder or default values** for magnitude or angular size. If a value cannot be
found after searching a second source, ask the user before proceeding.

## Step 2 — Derive Resource Keys

The primary key = first name converted: spaces → underscores, lowercase, strip special chars.
Example: `Exposed Cranium Nebula` → `exposed_cranium_nebula`

Each alias also gets its own key. Example: `PMR 1` → `pmr_1`

**Digit-prefix rule:** Android resource names cannot start with a digit. `AbstractAsciiProtoWriter`
applies the same rule: if the generated key starts with a digit, it is prefixed with `n`.
Example: `47 Tucanae` → `n47_tucanae`, `47 Tuc` → `n47_tuc`.
Apply this prefix consistently in all four files — `celestial_objects.xml` string names,
`celestial_info_cards.xml` string names, and all keys/key-references in `object_info.json`.

## Step 3 — Edit deep_sky_objects.csv

Append a row at the end of the file (before any blank trailing line). Format:

```
Object,Type,RA (h),DEC (deg),Magnitude,Size (arcminutes),NGC#,Constellation,Detailed Type,Common Name
```

- Use the exact same type string in both Type and Detailed Type columns for non-Messier objects.
- NGC# column: use the NGC/IC number if one exists, otherwise the primary catalog ID (e.g. `PMR 1`).
- Common Name: the most recognisable display name.

Example:
```
Exposed Cranium Nebula|PMR 1,Planetary Nebula,9.47813,-49.6107,13.0,1.5,PMR 1,Vela,Planetary Nebula,Exposed Cranium Nebula
```

## Step 4 — Edit celestial_objects.xml

Add name strings just before `</resources>`. One `<string>` per name/alias:

```xml
    <!-- <Common Name> -->
    <string name="<primary_key>" translation_description="<what it is>"><Display Name></string>
    <string name="<alias_key>" translation_description="<what it is>"><Alias></string>
```

Escape apostrophes as `\'`. No hardcoded colour integers.

## Step 5 — Edit celestial_info_cards.xml

Insert four strings **before** the `<!-- ==================== CONSTELLATIONS ====================`
comment. Use this template:

```xml
    <!-- <Common Name> (<Catalog ID>) -->
    <string name="object_info_<primary_key>_description" translation_description="Description of <Name> shown in the info card. Translate this text."><2-3 sentence description></string>
    <string name="object_info_<primary_key>_funfact" translation_description="Fun fact about <Name> shown in the info card. Translate this text."><1-2 sentence fun fact></string>
    <string name="object_info_<primary_key>_distance" translation_description="Distance to <Name> shown in the info card. Translate units if appropriate for your locale."><distance string></string>
    <string name="object_info_<primary_key>_size" translation_description="Physical size of <Name> shown in the info card. Translate units if appropriate for your locale."><size string></string>
```

**Description**: 2–3 sentences. Cover what it is, where it is, and what makes it notable.
**Fun fact**: 1–2 sentences. Something surprising, record-breaking, or historically significant.
**Distance**: human-readable with units, e.g. `~5,000 light-years`.
**Size**: physical extent, e.g. `~2 light-years across` or orbital/mass info for compact objects.

## Step 6 — Edit object_info.json

Insert a new entry **after the last non-constellation object** (currently after <last_non_constellation_object_key>) and
**before** the constellation entries. Use this template:

```json
    "<primary_key>": {
      "nameKey": "<primary_key>",
      "descriptionKey": "object_info_<primary_key>_description",
      "funFactKey": "object_info_<primary_key>_funfact",
      "type": "<type>",
      "distanceKey": "object_info_<primary_key>_distance",
      "sizeKey": "object_info_<primary_key>_size",
      "magnitude": "<magnitude as string, e.g. \"4.0\">"
    },
```

Valid type values: nebula (for nebulae/remnants), galaxy, star, black_hole, cluster (for open/globular clusters), constellation.

If an image is available (see Step 7), also add:
```json
      "imageKey": "deep_sky_objects/<image_name>.webp",
      "imageCredit": "<Credit string>"
```

## Adding a Virtual Object (map-less)

A **virtual object** has an info card, appears in search, and shows in the gallery — but is **not
rendered on the sky map**. It is associated with a renderable parent (e.g. the Galilean moons
→ Jupiter). Searching for a virtual object navigates the map to the parent and opens the virtual
object's info card.

Virtual objects skip Steps 3 and 8 entirely (no CSV row, no protobuf regeneration).

### Files to edit

| Step | File | What to add |
|------|------|-------------|
| 1 | `celestial_objects.xml` | Name string(s) — same as normal |
| 2 | `celestial_info_cards.xml` | Info card strings — same as normal |
| 3 | `object_info.json` | Entry with `"parentObjectId"` set to the parent's key |
| 4 *(optional)* | `celestial_images/` | Image asset — same as normal |

### object_info.json template

```json
"<primary_key>": {
  "nameKey": "<primary_key>",
  "descriptionKey": "object_info_<primary_key>_description",
  "funFactKey": "object_info_<primary_key>_funfact",
  "type": "moon",
  "distanceKey": "object_info_<primary_key>_distance",
  "sizeKey": "object_info_<primary_key>_size",
  "parentObjectId": "<parent_key>",
  "searchSubtext": "<subtitle shown below object name in search dropdown>"
},
```

- `parentObjectId` is the `object_info.json` key of the renderable parent (e.g. `"jupiter"`).
- No `magnitude` field is needed for virtual objects.
- To link parent ↔ child in the info card "See Also" section, add `"seeAlso"` arrays to both:

```json
"jupiter": {
  ...
  "seeAlso": ["io", "europa", "ganymede", "callisto"]
},
"io": {
  ...
  "parentObjectId": "jupiter",
  "seeAlso": ["jupiter", "europa", "ganymede", "callisto"]
},
```

### UX behaviour

- **Search**: typing "Io" surfaces it as a suggestion; selecting it flies the map to Jupiter and
  opens Io's info card with the label "Looking for: Io".
- **Gallery**: virtual objects appear alongside renderable objects (if they have an image or info).
- **Map tap**: tapping Jupiter shows Jupiter's card with the "See Also" links to the moons.
- **See Also**: tapping a linked name in any info card opens that object's card.

---

## Step 7 — Image (optional but recommended)

Look for a freely licensed image from one of these sources:
- **NASA** (nasa.gov, hubblesite.org, webb.nasa.gov) — public domain, use freely
- **ESA/Hubble or ESA/Webb** (esahubble.org, esawebb.org) — CC BY 4.0, use with attribution
- **ESO** (eso.org) — CC BY 4.0, use with attribution
- **Wikipedia** (wikipedia.org) — CC BY-SA 4.0, use with attribution
- Images with less restrictive licenses than specified are OK.
- Avoid images with unclear or restrictive licenses

If a suitable image is found, invoke the **`skymap.celestial-image`** skill, passing:

```
<image_url_or_local_path> deep_sky_objects/<source_agency>_<primary_key> [crop x1,y1,x2,y2]
```

For example: `https://example.nasa.gov/image.jpg deep_sky_objects/webb_exposed_cranium_nebula 0,0,1000,1027`

The `skymap.celestial-image` skill handles downloading, cropping, resizing to 480×800 WebP, and
deploying to the assets directory. It will also report the `imageKey` to use.

After it completes, add `imageKey` and `imageCredit` to the `object_info.json` entry. Credit
format follows the source:
- NASA: `"NASA/JPL"` or `"NASA/ESA/Hubble"` etc.
- ESA/Webb: `"NASA, ESA, CSA, STScI/<Processor Name>"` (exact credit from the image page)
- ESO: `"ESO/<Photographer Name>"`

## Step 8 — Regenerate Protobuf Data

Editing `deep_sky_objects.csv` does not take effect until the binary protobuf assets are
regenerated. Run from `stardroid-v1/tools/`:

```bash
./generate.sh   # ASCII protocol buffers from CSV
./binary.sh     # Binary assets → app/src/main/assets/
```

Or use the full build script (from `stardroid-v1/`) which includes data generation:

```bash
./build_skymap.sh
```

The `skymap.build` skill covers all build and data-generation commands in detail.

## Step 9 — Verify

Quickly confirm:
- [ ] CSV row added with correct decimal RA/Dec
- [ ] All name aliases have string resources in celestial_objects.xml
- [ ] All four info card strings present in celestial_info_cards.xml
- [ ] object_info.json entry present with correct keys
- [ ] If image added: file exists at `app/src/main/assets/celestial_images/deep_sky_objects/<name>.webp`
- [ ] Protobuf data regenerated (`generate.sh` + `binary.sh` or `build_skymap.sh`)

## Reference — Coordinate Conversion

```
RA:  h m s  →  h + m/60 + s/3600   (decimal hours)
Dec: D° M' S"  →  ±(D + M/60 + S/3600)   (decimal degrees, negative = south)
```

Example: RA 9h 28m 41.28s → 9 + 28/60 + 41.28/3600 = **9.47813**
Example: Dec −49° 36′ 38.46″ → −(49 + 36/60 + 38.46/3600) = **−49.6107**

## Reference — Existing Object Patterns

| Object | Key | Type |
|---|---|---|
| Crab Nebula (M1) | `m1` | `nebula` |
| Willman 1 | `willman_1` | `galaxy` |
| Sagittarius A* | `sgr_a_star` | `black_hole` |
| Cygnus X-1 | `cygnus_x_1` | `black_hole` |
| Exposed Cranium Nebula | `exposed_cranium_nebula` | `nebula` |
