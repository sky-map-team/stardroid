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

Each star entry contains:

```fbs
table PointElement {
    color: uint32;                      // ARGB color from spectral type
    size: int32;                        // Size based on magnitude
    location: GeocentricCoordinates;    // Position on celestial sphere
    shape: Shape = Circle;              // CIRCLE for stars
}

table LabelElement {
    text: string;                       // Star name
    location: GeocentricCoordinates;    // Position
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

```fbs
table LineElement {
    color: uint32;
    line_width: float = 1.5;
    vertices: [GeocentricCoordinates];
}

table LabelElement {
    text: string;                       // "Orion", "Ursa Major", etc.
    location: GeocentricCoordinates;
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

Deep-sky objects cataloged by Charles Messier.

| Property | Value |
|----------|-------|
| Objects | ~110 |
| Types | Galaxies, nebulae, clusters |
| Magnitude range | 1.6 to 10+ |

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

| Shape | Objects | Example |
|-------|---------|---------|
| EllipticalGalaxy | ~10 | M32, M49 |
| SpiralGalaxy | ~30 | M31, M51, M101 |
| IrregularGalaxy | ~5 | M82 |
| GlobularCluster | ~30 | M13, M22 |
| OpenCluster | ~25 | M45 (Pleiades), M44 |
| Nebula | ~10 | M42 (Orion), M1 (Crab) |

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

# Generate JSON intermediate format
./generate.sh

# Convert to FlatBuffers binary
./binary.sh
```

### Generator Classes

| Class | Input | Output |
|-------|-------|--------|
| `StellarJsonWriter` | Star CSV | stars.json |
| `ConstellationJsonWriter` | Line data | constellations.json |
| `MessierJsonWriter` | Messier CSV | messier.json |
| `JsonToFlatBufferConverter` | *.json | *.bin |

### Output Location

```
app/src/main/assets/
├── stars.bin
├── constellations.bin
└── messier.bin
```

## Runtime Loading

### AbstractFileBasedLayer

```kotlin
abstract class AbstractFileBasedLayer(
    private val context: Context,
    private val assetFilename: String
) : AbstractRenderablesLayer() {

    override fun initialize() {
        val assets = context.assets
        val stream = assets.open(assetFilename)

        // Zero-copy FlatBuffer access
        val bytes = stream.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        val sources = AstronomicalSources.getRootAsAstronomicalSources(buffer)

        // Iterate without allocating objects
        for (i in 0 until sources.sourcesLength) {
            val source = sources.sources(i)!!
            addRenderable(FlatBufferAstronomicalSource(source))
        }

        stream.close()
    }
}
```

### Memory Usage

| Catalog | On Disk | In-Memory |
|---------|---------|-----------|
| Stars | ~2 MB | ~2 MB (buffer only) |
| Constellations | ~50 KB | ~50 KB |
| Messier | ~20 KB | ~20 KB |

**Note:** FlatBuffers uses zero-copy access, so in-memory size equals file size. No additional object allocation required.
