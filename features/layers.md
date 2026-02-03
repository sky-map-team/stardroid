# Layers Feature

Sky Map organizes celestial objects into toggleable layers, allowing users to customize what's displayed.

## Overview

Layers provide logical grouping of celestial objects. Users can enable/disable layers to reduce clutter or focus on specific object types.

## Layer List

### 12 Available Layers

| Layer | Type | Default | Description |
|-------|------|---------|-------------|
| Stars | File-based | On | Named stars from Hipparcos catalog |
| Constellations | File-based | On | Constellation lines and names |
| Messier Objects | File-based | On | Deep-sky objects (galaxies, nebulae, clusters) |
| Solar System | Computed | On | Sun, Moon, Mercury through Neptune |
| Meteor Showers | Computed | On | Annual meteor shower radiants |
| Grid | Computed | Off | Right ascension/declination grid |
| Horizon | Computed | On | Horizon line and cardinal directions |
| Ecliptic | Computed | Off | Sun's apparent path through zodiac |
| ISS | Network | Off | International Space Station |
| Comets | File-based | Off | Current visible comets |
| Sky Gradient | Computed | On | Day/night atmosphere gradient |
| Star of Bethlehem | Computed | Off | Easter egg layer |

## Layer Architecture

### Class Hierarchy

```
Layer (interface)
    │
    ├── AbstractLayer
    │       │
    │       ├── AbstractRenderablesLayer
    │       │       │
    │       │       ├── AbstractFileBasedLayer
    │       │       │       ├── StarsLayer
    │       │       │       ├── ConstellationsLayer
    │       │       │       └── MessierLayer
    │       │       │
    │       │       └── [Computed layers]
    │       │               ├── SolarSystemLayer
    │       │               ├── GridLayer
    │       │               ├── HorizonLayer
    │       │               └── EclipticLayer
    │       │
    │       └── IssLayer (network + computed)
    │
    └── SkyGradientLayer (special rendering)
```

### Layer Interface

```kotlin
interface Layer {
    val layerDepthOrder: Int
    val preferenceId: String
    val layerNameId: Int

    fun initialize()
    fun registerWithRenderer(controller: RendererController)
    fun setVisible(visible: Boolean)
    fun searchByObjectName(name: String): List<SearchResult>
    fun getObjectNamesMatchingPrefix(prefix: String): Set<String>
}
```

## Layer Types

### File-Based Layers

Load data from binary protobuf assets:

```kotlin
abstract class AbstractFileBasedLayer(
    private val assetManager: AssetManager,
    private val assetFilename: String
) : AbstractRenderablesLayer() {

    override fun initialize() {
        val proto = assetManager.open(assetFilename).use { stream ->
            AstronomicalSourcesProto.parseFrom(stream)
        }
        sources = proto.sourceList.map { ProtobufAstronomicalSource(it) }
    }
}
```

**Examples**:
- `StarsLayer` - loads `stars.binary`
- `ConstellationsLayer` - loads `constellations.binary`
- `MessierLayer` - loads `messier.binary`

### Computed Layers

Generate data programmatically:

```kotlin
class GridLayer : AbstractRenderablesLayer() {
    override fun initialize() {
        // Generate RA/Dec grid lines mathematically
        for (ra in 0 until 360 step 15) {
            addRaLine(ra)
        }
        for (dec in -90..90 step 15) {
            addDecLine(dec)
        }
    }
}
```

**Examples**:
- `SolarSystemLayer` - ephemeris calculations
- `GridLayer` - mathematical generation
- `HorizonLayer` - location-based calculation

### Network Layers

Fetch real-time data from internet:

```kotlin
class IssLayer : AbstractLayer() {
    private val updateExecutor = ScheduledThreadPoolExecutor(1)

    override fun initialize() {
        // Fetch TLE data periodically
        updateExecutor.scheduleAtFixedRate(
            { fetchTleData() },
            0, 60, TimeUnit.SECONDS
        )
    }

    private fun fetchTleData() {
        val tle = fetchFromNetwork("https://...")
        updatePosition(tle)
    }
}
```

## Layer Manager

`LayerManager` coordinates layer visibility:

```kotlin
class LayerManager(
    private val layers: List<Layer>,
    private val sharedPreferences: SharedPreferences
) {
    fun setLayerVisible(layer: Layer, visible: Boolean) {
        layer.setVisible(visible)
        sharedPreferences.edit()
            .putBoolean(layer.preferenceId, visible)
            .apply()
    }

    fun isLayerVisible(layer: Layer): Boolean {
        return sharedPreferences.getBoolean(layer.preferenceId, layer.defaultVisible)
    }
}
```

## Layer Details

### Stars Layer

| Property | Value |
|----------|-------|
| Source | Hipparcos catalog |
| Count | ~100,000 stars |
| Magnitude limit | ~6.5 |
| Data | Position, magnitude, color, name |

### Constellations Layer

| Property | Value |
|----------|-------|
| Source | Stellarium dataset |
| Count | 88 constellations |
| Data | Lines connecting stars, names |

### Messier Layer

| Property | Value |
|----------|-------|
| Source | Messier catalog |
| Count | ~110 objects |
| Types | Galaxies, nebulae, star clusters |
| Data | Position, type, name, image (optional) |

### Solar System Layer

| Property | Value |
|----------|-------|
| Objects | Sun, Moon, 8 planets |
| Calculation | JPL ephemeris algorithm |
| Update frequency | Per frame |

### Grid Layer

| Property | Value |
|----------|-------|
| RA lines | Every 15° (1 hour) |
| Dec lines | Every 15° |
| Labels | RA in hours, Dec in degrees |

### Horizon Layer

| Property | Value |
|----------|-------|
| Features | Horizon circle, N/S/E/W labels |
| Dependencies | User location, time |
| Updates | On location change |

### ISS Layer

| Property | Value |
|----------|-------|
| Data source | TLE orbital elements |
| Update frequency | 60 seconds |
| Accuracy | ~1 km position |

## Layer Preferences

Each layer has a preference key for persistence:

| Layer | Preference Key |
|-------|----------------|
| Stars | `source_provider.0` |
| Constellations | `source_provider.1` |
| Messier | `source_provider.2` |
| Planets | `source_provider.3` |
| Meteor Showers | `source_provider.4` |
| Grid | `source_provider.5` |
| Horizon | `source_provider.6` |

## Rendering Order

Layers rendered in depth order (lower = background):

| Depth | Layer |
|-------|-------|
| 0 | Sky gradient |
| 10 | Grid lines |
| 20 | Horizon |
| 30 | Constellations |
| 40 | Stars |
| 50 | Messier objects |
| 60 | Planets |
| 70 | ISS |
| 100 | Labels (all) |

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `Layer` | Interface for all layers |
| `AbstractLayer` | Base implementation |
| `AbstractFileBasedLayer` | Protobuf data loading |
| `LayerManager` | Visibility coordination |
| `RendererController` | Rendering registration |
