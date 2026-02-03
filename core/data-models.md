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
```
message Star {
    required uint32 id = 1;
    required string name = 2;
    required float ra_j2000 = 3;      // RA (hours, J2000)
    required float dec_j2000 = 4;     // Dec (degrees, J2000)
    required float magnitude = 5;    // Apparent magnitude
    required float color_index = 6;  // B-V color index
    optional float proper_motion_ra = 7;   // arcsec/year
    optional float proper_motion_dec = 8;  // arcsec/year
}
```

**File Structure:**
- `stars.bin` - All stars down to magnitude 6.5 ( Hipparcos)
- `bright_stars.bin` - Bright stars only (subset for older devices)

**Loading:**
```kotlin
val stars = ProtobufAstronomicalSource(
    context,
    "stars.bin",
    Stars.getDefaultInstance()
).createObjects()
```

### Constellation Catalog

**Data:**
- **Boundaries:** Polygon vertices (RA/Dec)
- **Lines:** Star pairs that form constellation stick figures
- **Names:** IAU constellation names

**Schema:**
```
message Constellation {
    required string id = 1;          // e.g., "ori"
    required string name = 2;        // e.g., "Orion"
    repeated float boundary = 3;     // Polygon vertices (ra1, dec1, ra2, dec2, ...)
    repeated Line lines = 4;         // Connecting lines
    repeated string stars = 5;       // Star IDs in constellation
}

message Line {
    required string start_star = 1;  // Start star ID
    required string end_star = 2;    // End star ID
}
```

### Messier Catalog

**Data:** 110 deep-sky objects (nebulae, clusters, galaxies)

**Schema:**
```
message MessierObject {
    required uint32 number = 1;      // M1, M2, etc.
    required string name = 2;        // Common name
    required float ra = 3;           // J2000 RA
    required float dec = 4;          // J2000 Dec
    required float size = 5;         // Angular size (degrees)
    required string type = 6;        // NEBULA, CLUSTER, etc.
    required string constellation = 7; // Parent constellation
    required uint32 image_id = 8;     // Android resource
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
│   └── ProtobufAstronomicalSource
│       └── ~10MB star data
├── ConstellationsLayer
│   ├── Boundary polygons (~50KB)
│   └── Connecting lines (~100KB)
├── MessierLayer
│   └── 110 objects (~20KB)
└── SolarSystemLayer
    └── Ephemeris calculator (~1KB code + data)
```

**Total memory:** ~50MB for all layers

## Data Loading Pipeline

### Build Time (Tools Module)

**Source:** Text catalog files (Hipparcos, Tycho-2)

**Tool:** `tools/Main.java`

**Process:**
1. Parse text catalog files
2. Create protocol buffer messages
3. Write `.ascii.bin` (human-readable)
4. Convert to `.bin` (binary) for runtime efficiency

**Commands:**
```bash
cd tools
./generate.sh  # Creates ASCII FlatBufferss
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

    protected fun loadProtobuf(): List<CelestialObject> {
        val inputStream = context.assets.open(assetPath)
        val catalog = Stars.parseFrom(inputStream)
        return catalog.createObjects()
    }
}
```

**Lazy Loading:**
- Catalogs loaded on first use
- Cached in memory for app lifetime
- Memory usage: ~50MB (acceptable)

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
```
message PrefixIndex {
    repeated PrefixEntry entries = 1;
}

message PrefixEntry {
    string prefix = 1;           // e.g., "si"
    repeated uint32 star_ids = 2;  // Matching star IDs
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

**Catalog format version:** Protocol buffer schema versioning

**Compatibility:** Old catalogs work with new code (forward compatibility)

## Performance Considerations

### Catalog Size

**Trade-offs:**
- **Full catalog:** ~250K stars = ~10MB
- **Bright stars only:** ~10K stars = ~400KB
- **Decision:** Include full catalog, modern devices have sufficient memory

### Loading Time

**Binary FlatBuffers parsing:** ~100-200ms on app startup
**Layer initialization:** ~50ms per layer
**Total startup:** ~500ms for all layers (acceptable)

### Memory Footprint

**Static:** ~50MB for all catalogs (fixed)
**Dynamic:** ~5MB for rendering state
**Peak:** ~60MB (acceptable for modern devices)

## Related Specifications

- [README.md](README.md) - Core domain overview
- [algorithms.md](algorithms.md) - Coordinate transforms
- [../data/catalogs.md](../data/catalogs.md) - Catalog sources and formats
- [../data/FlatBuffers-schema.md](../data/FlatBuffers-schema.md) - Protocol buffer schema details
