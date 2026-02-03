# Data Models - Celestial Objects & Catalogs

## Purpose

Defines **data structures and schemas** for celestial objects (stars, planets, constellations) and catalog formats.

## Celestial Object Model

### Core Representation

```kotlin
/**
 * A celestial object that can be displayed in the sky map.
 */
data class CelestialObject(
    // Identification
    val id: String,           // Unique identifier (e.g., "sirius", "jupiter")
    val name: String,         // Human-readable name
    val searchNames: List<String> = emptyList(),  // Alternate names for search

    // Position (J2000 epoch)
    val ra: Float,           // Right Ascension in hours (0-24)
    val dec: Float,          // Declination in degrees (-90 to +90)

    // Appearance
    val magnitude: Float,    // Apparent magnitude (brightness, lower = brighter)
    val color: Color,         // Display color (for rendering)

    // Physical properties
    val size: Float,         // Angular size in degrees (for images)
    val type: ObjectType,    // STAR, PLANET, NEBULA, GALAXY, etc.

    // Proper motion (annual change in position)
    val properMotionRA: Float? = null,  // Δα (arcsec/year)
    val properMotionDec: Float? = null,  // Δδ (arcsec/year)

    // Epoch for RA/Dec (default is J2000)
    val epoch: Float = 2000f,

    // Additional data
    val overridePosition: RaDec? = null,  // Planets: calculated position
    val imageInfo: ImageInfo? = null      // Image objects
) {
    fun getApparentPosition(time: Long): RaDec {
        // Apply precession and proper motion
        return if (overridePosition != null) {
            overridePosition
        } else {
            applyPrecession(this, time)
        }
    }
}

enum class ObjectType {
    STAR, PLANET, MOON, SUN,
    NEBULA, GALAXY, CLUSTER,
    CONSTELLATION, ASTERISM,
    UNKNOWN
}

data class ImageInfo(
    val resourceId: Int,   // Android drawable resource
    val attribution: String // Credit for image
)

data class RaDec(
    val ra: Float,  // Right Ascension (hours or degrees, 0-360)
    val dec: Float // Declination (degrees, -90 to +90)
)
```

## Catalog Data Formats

### Star Catalog Format

**Source:** Hipparcos/Tycho-2 catalogs (~250,000 stars)

**Binary Format:** FlatBuffers (`.bin` files)

**Schema:** (`source.fbs`)
```fbs
table Star {
    id: uint32;
    name: string;
    ra_j2000: float;      // RA (hours, J2000)
    dec_j2000: float;     // Dec (degrees, J2000)
    magnitude: float;     // Apparent magnitude
    color_index: float;   // B-V color index
    proper_motion_ra: float;   // arcsec/year (optional)
    proper_motion_dec: float;  // arcsec/year (optional)
}
```

**File Structure:**
- `stars.bin` - All stars down to magnitude 6.5 ( Hipparcos)
- `bright_stars.bin` - Bright stars only (subset for older devices)

**Loading:**
```kotlin
val buffer = context.assets.open("stars.bin").use { it.readBytes() }
val byteBuffer = ByteBuffer.wrap(buffer)
val sources = AstronomicalSources.getRootAsAstronomicalSources(byteBuffer)
val stars = FlatBufferAstronomicalSource(sources).createObjects()
```

### Constellation Catalog

**Data:**
- **Boundaries:** Polygon vertices (RA/Dec)
- **Lines:** Star pairs that form constellation stick figures
- **Names:** IAU constellation names

**Schema:**
```fbs
table Line {
    start_star: string;  // Start star ID
    end_star: string;    // End star ID
}

table Constellation {
    id: string;           // e.g., "ori"
    name: string;         // e.g., "Orion"
    boundary: [float];    // Polygon vertices (ra1, dec1, ra2, dec2, ...)
    lines: [Line];        // Connecting lines
    stars: [string];      // Star IDs in constellation
}
```

### Messier Catalog

**Data:** 110 deep-sky objects (nebulae, clusters, galaxies)

**Schema:**
```fbs
table MessierObject {
    number: uint32;        // M1, M2, etc.
    name: string;          // Common name
    ra: float;             // J2000 RA
    dec: float;            // J2000 Dec
    size: float;           // Angular size (degrees)
    type: string;          // NEBULA, CLUSTER, etc.
    constellation: string; // Parent constellation
    image_id: uint32;      // Android resource
}
```

### Solar System Data

**Planets:** Calculated from ephemeris, not catalog

**Ephemeris Format:** VSOP87 theory (French equivalent to DE405)

**Algorithm:** See `../data/ephemeris.md`

## Data Storage

### File Locations

**Assets (read-only):**
- `app/src/main/assets/stars.bin` - Binary star catalog
- `app/src/main/assets/constellations.bin` - Constellation data
- `app/src/main/assets/messier.bin` - Messier objects

**Runtime:**
- Loaded into `LayerManager` at app start
- Each layer manages its own catalog data
- Data cached in memory (not reloaded from disk)

### Memory Layout

```
LayerManager in memory:
├── StarsLayer
│   └── FlatBufferAstronomicalSource
│       └── ~2MB buffer (zero-copy access)
├── ConstellationsLayer
│   ├── Boundary polygons (~50KB)
│   └── Connecting lines (~100KB)
├── MessierLayer
│   └── 110 objects (~20KB)
└── SolarSystemLayer
    └── Ephemeris calculator (~1KB code + data)
```

**Total memory:** ~5MB for all layers (FlatBuffers zero-copy)

## Data Loading Pipeline

### Build Time (Tools Module)

**Source:** Text catalog files (Hipparcos, Tycho-2)

**Tool:** `tools/Main.kt`

**Process:**
1. Parse text catalog files
2. Create FlatBuffer tables
3. Write `.json` (human-readable intermediate)
4. Convert to `.bin` (binary) using flatc compiler

**Commands:**
```bash
cd tools
./generate.sh  # Creates JSON intermediate files
./binary.sh    # Converts to binary in app/src/main/assets/
```

### Runtime Loading

**Each layer extends `AbstractFileBasedLayer`:**

```kotlin
abstract class AbstractFileBasedLayer(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val assetPath: String
) : AbstractLayer {

    protected fun loadFlatBuffer(): AstronomicalSources {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes)
        return AstronomicalSources.getRootAsAstronomicalSources(buffer)
    }
}
```

**Lazy Loading:**
- Catalogs loaded on first use
- Buffer retained for zero-copy access
- Memory usage: ~5MB (FlatBuffers efficient)

## Search-Optimized Data

### Prefix Store Structure

**Purpose:** Enable fast "starts-with" searches

**Data:** Alternate names for stars (common names, Bayer letters, Flamsteed numbers)

**Example:**
```
Star: α Ursae Majoris (Sirius)
  - "sirius" (common name)
  - "alpha uma" (Bayer)
  - "50 cancri" (Flamsteed)
  - "9 cancri" (HR number)
```

**Storage:** Trie data structure for O(prefix length) lookup

**Schema:**
```fbs
table PrefixEntry {
    prefix: string;           // e.g., "si"
    star_ids: [uint32];       // Matching star IDs
}

table PrefixIndex {
    entries: [PrefixEntry];
}
```

### Object Index

**Purpose:** Enable "point at sky" search (what object is at RA/Dec?)

**Data Structure:** Spatial hash (RA/Dec grid)

**Algorithm:** Divide sky into RA/Dec bins, assign objects to bins

```kotlin
data class SpatialIndex(
    val raBins: Map<Int, List<Int>>,  // RA bins → star IDs
    val decBins: Map<Int, List<Int>>  // Dec bins → star IDs
)
```

**Usage:** Query by RA/Dec to find nearby objects

## Data Integrity

### Validation

**Catalog validation:**
- RA in [0, 360) or [0, 24h) hours
- Dec in [-90, +90] degrees
- Magnitude in [-1.5, 10] (reasonable range)

**Duplicate detection:**
- Star IDs unique within catalog
- Cross-catalog deduplication (Sirius appears in multiple catalogs)

### Versioning

**Catalog format version:** FlatBuffers file_identifier ("STAR")

**Compatibility:** Old catalogs work with new code (forward compatibility)

## Performance Considerations

### Catalog Size

**Trade-offs:**
- **Full catalog:** ~250K stars = ~10MB
- **Bright stars only:** ~10K stars = ~400KB
- **Decision:** Include full catalog, modern devices have sufficient memory

### Loading Time

**FlatBuffers loading:** ~0ms (zero-copy, instant access)
**Layer initialization:** ~50ms per layer
**Total startup:** ~500ms for all layers (acceptable)

### Memory Footprint

**Static:** ~50MB for all catalogs (fixed)
**Dynamic:** ~5MB for rendering state
**Peak:** ~10MB (efficient with FlatBuffers)

## Related Specifications

- [README.md](README.md) - Core domain overview
- [algorithms.md](algorithms.md) - Coordinate transforms
- [../data/catalogs.md](../data/catalogs.md) - Catalog sources and formats
- [../data/flatbuffers-schema.md](../data/flatbuffers-schema.md) - FlatBuffers schema details
