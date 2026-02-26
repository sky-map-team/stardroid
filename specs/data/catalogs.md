# Astronomical Catalogs

Sky Map uses pre-compiled catalogs for stars, constellations, and deep-sky objects.

## Star Catalog

### Source: Hipparcos

The star catalog is derived from the Hipparcos space mission data.

| Property | Value |
|----------|-------|
| Source | ESA Hipparcos Catalogue |
| Stars | ~100,000 |
| Magnitude limit | ~6.5 (naked-eye visible) |
| Position accuracy | < 1 milliarcsecond |

### Star Data Fields

Each star entry contains (from `datamodel/src/main/proto/source.proto`):

```proto
message PointElementProto {
    optional GeocentricCoordinatesProto location = 1;
    optional uint32 color = 2;          // ARGB color from spectral type
    optional int32 size = 3;            // Size based on magnitude
    optional Shape shape = 4;           // CIRCLE for stars
}

message LabelElementProto {
    optional GeocentricCoordinatesProto location = 1;
    optional string strings_str_id = 6; // Android string resource ID for name
}
```

### Star Colors

Star colors derived from spectral classification:

| Spectral Type | Color | Example |
|---------------|-------|---------|
| O, B | Blue-white | Rigel, Spica |
| A | White | Sirius, Vega |
| F | Yellow-white | Procyon |
| G | Yellow | Sun, Alpha Centauri |
| K | Orange | Arcturus, Aldebaran |
| M | Red | Betelgeuse, Antares |

### Star Magnitudes

Size mapping from apparent magnitude:

| Magnitude | Brightness | Example | Display Size |
|-----------|------------|---------|--------------|
| -1 to 0 | Brightest | Sirius | Large |
| 0 to 1 | Very bright | Vega | Large |
| 1 to 2 | Bright | Polaris | Medium |
| 2 to 3 | Moderate | North Star | Medium |
| 3 to 4 | Dim | Many stars | Small |
| 4 to 6 | Faint | Naked eye limit | Small |

### Named Stars

Stars with common names are labeled:

| Name | Designation | Constellation |
|------|-------------|---------------|
| Sirius | α Canis Majoris | Canis Major |
| Canopus | α Carinae | Carina |
| Arcturus | α Boötis | Boötes |
| Vega | α Lyrae | Lyra |
| Capella | α Aurigae | Auriga |
| Rigel | β Orionis | Orion |
| Procyon | α Canis Minoris | Canis Minor |
| Betelgeuse | α Orionis | Orion |
| ... | ... | ... |

## Constellation Catalog

### Source: Stellarium

Constellation lines from the Stellarium open-source planetarium.

| Property | Value |
|----------|-------|
| Constellations | 88 (IAU standard) |
| Lines | Star-to-star connections |
| Labels | Latin names |

### Constellation Data Fields

```proto
message LineElementProto {
    optional uint32 color = 1;
    optional float line_width = 2;      // default 1.5
    repeated GeocentricCoordinatesProto vertex = 3;
}

message LabelElementProto {
    optional GeocentricCoordinatesProto location = 1;
    optional string strings_str_id = 6; // "Orion", "Ursa Major", etc.
}
```

### Constellation List

All 88 IAU constellations included:

| Constellation | Latin Name | Visibility |
|---------------|------------|------------|
| Andromeda | Andromeda | Northern |
| Aquarius | Aquarius | Equatorial |
| Aquila | Aquila | Equatorial |
| ... | ... | ... |
| Orion | Orion | Equatorial |
| Ursa Major | Ursa Major | Northern |
| Ursa Minor | Ursa Minor | Northern |
| ... | ... | ... |

## Messier Catalog

### Source: Messier Catalog

Deep-sky objects cataloged by Charles Messier, plus a small number of additional notable objects added manually to `tools/data/messier.csv`.

| Property | Value |
|----------|-------|
| Objects | ~116 (110 Messier + 6 extras) |
| Types | Galaxies, nebulae, clusters, and other notable objects |
| Magnitude range | 1.0 to 20+ |

#### Extra objects in `messier.csv` (non-Messier)

These objects are appended at the end of `tools/data/messier.csv` and processed identically to Messier objects:

| Entry | Common name | Type | Notes |
|-------|-------------|------|-------|
| NGC6543 | Cat's Eye Nebula | Planetary Nebula | |
| NGC5139 | Omega Centauri | Globular Cluster | |
| V838 Mon | V838 Monocerotis | Other | Variable star/nova |
| HDF | Hubble Deep Field | Other | |
| T CrB | T Coronae Borealis / Blaze Star | Other | Recurrent nova, added for issue #499 |
| Eta Carinae Nebula | Carina Nebula / NGC3372 | Diffuse Nebula | Added for issue #125 |

### Messier Data Fields

```fbs
table PointElement {
    color: uint32;
    size: int32;
    location: GeocentricCoordinates;
    shape: Shape;                       // Galaxy type, cluster, nebula
}

table LabelElement {
    text: string;                       // "M31", "Andromeda Galaxy"
    location: GeocentricCoordinates;
}
```

### Messier Object Types

The `Type` column in `messier.csv` maps to a `Shape` enum value in the generated proto:

| CSV Type | Shape | Examples |
|----------|-------|---------|
| Galaxy | GALAXY (subtype from Detailed Type) | M31, M51 |
| Globular Cluster | GLOBULAR_CLUSTER | M13, M22 |
| Open Cluster | OPEN_CLUSTER | M45 (Pleiades), M44 |
| Diffuse Nebula | DIFFUSE_NEBULA | M42 (Orion), Eta Carinae Nebula |
| Planetary Nebula | PLANETARY_NEBULA | M57, NGC6543 |
| Supernova Remnant | SUPERNOVA_REMNANT | M1 (Crab) |
| Other | OTHER | V838 Mon, HDF, T CrB |

### CSV Format for `messier.csv`

Column order: `Object,Type,RA (h),DEC (deg),Magnitude,Size (arcminutes),NGC#,Constellation,Detailed Type,Common Name`

- **Object**: Primary name, optionally followed by `|`-separated aliases (e.g. `M31|andromeda galaxy`). Names use **spaces not underscores** — `AbstractAsciiProtoWriter.rKeysFromName()` converts spaces to underscores to produce Android string resource IDs.
- **RA**: Right ascension in **decimal hours** (not degrees).
- **DEC**: Declination in **decimal degrees**.

#### Coordinate precision notes

- For point sources (stars, novae): use coordinates of the object itself.
- For extended nebulae: use the **geometric centre of the nebula**, not the embedded star. Example: Eta Carinae Nebula uses RA 10h 45m 08.5s / Dec −59° 52' 04" (nebula centre), not Eta Carinae the star (Dec −59° 41').

#### Adding a new object — checklist

1. Append a row to `tools/data/messier.csv`.
2. Add all name/alias string resources to `app/src/main/res/values/celestial_objects.xml`.
3. Add info-card strings to `app/src/main/res/values/celestial_info_cards.xml` (keys: `object_info_<key>_description`, `_funfact`, `_distance`, `_size`).
4. Add a JSON entry to `app/src/main/assets/object_info.json`.
5. Rebuild: from the project root run `./gradlew clean :tools:installDist`, then from `tools/` run `./generate.sh && ./binary.sh`.
   - The `installDist` step generates `tools/build/install/datagen/bin/datagen` with a broken classpath; `build_skymap.sh` fixes it automatically with `sed`.

### Notable Messier Objects

| Catalog # | Name | Type | Magnitude |
|-----------|------|------|-----------|
| M1 | Crab Nebula | Supernova remnant | 8.4 |
| M13 | Hercules Cluster | Globular cluster | 5.8 |
| M31 | Andromeda Galaxy | Spiral galaxy | 3.4 |
| M42 | Orion Nebula | Emission nebula | 4.0 |
| M45 | Pleiades | Open cluster | 1.6 |
| M51 | Whirlpool Galaxy | Spiral galaxy | 8.4 |
| M57 | Ring Nebula | Planetary nebula | 8.8 |
| M104 | Sombrero Galaxy | Spiral galaxy | 8.0 |

## Data Generation

### Tool Pipeline

```bash
# From project root
cd tools

# Generate ASCII proto text from source catalogs
./generate.sh

# Convert ASCII proto text to binary proto and copy to app/src/main/assets/
./binary.sh
```

### Generator Classes

| Class | Input | Output |
|-------|-------|--------|
| `StellarAsciiProtoWriter` | `stardata_names.txt` | `stars.ascii` |
| `MessierAsciiProtoWriter` | `messier.csv` | `messier.ascii` |
| `AsciiToBinaryProtoWriter` | `*.ascii` | `*.binary` |

Constellations are already hand-authored in `tools/data/constellations.ascii` and do not go through a CSV stage.

### Output Location

```
app/src/main/assets/
├── stars.binary
├── constellations.binary
└── messier.binary
```

## Runtime Loading

### AbstractFileBasedLayer

```kotlin
abstract class AbstractFileBasedLayer(
    private val context: Context,
    private val assetFilename: String
) : AbstractRenderablesLayer() {

    override fun initialize() {
        val stream = context.assets.open(assetFilename)
        val sources = AstronomicalSourcesProto.parseFrom(stream)
        stream.close()

        for (source in sources.sourceList) {
            addRenderable(ProtobufAstronomicalSource(source))
        }
    }
}
```

### Memory Usage

| Catalog | On Disk | In-Memory |
|---------|---------|-----------|
| Stars | ~2 MB | ~2 MB (parsed objects) |
| Constellations | ~50 KB | ~50 KB |
| Messier | ~20 KB | ~20 KB |
