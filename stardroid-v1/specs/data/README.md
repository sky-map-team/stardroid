# Data Sources Overview

This section documents the astronomical data used by Sky Map.

## Contents

| Specification | Description |
|--------------|-------------|
| [Catalogs](catalogs.md) | Star, constellation, and Messier catalogs |
| [Ephemeris](ephemeris.md) | Solar system position calculations |
| [ISS Tracking](iss-tracking.md) | International Space Station real-time tracking |
| [Protocol Buffers Schema](protobuf-schema.md) | Protocol Buffers schema and data pipeline |

## Data Source Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     BUNDLED DATA (Assets)                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  stars.binary        ~2MB    ~100k stars               │   │
│  │  constellations.binary ~50KB  88 constellations         │   │
│  │  messier.binary      ~20KB   ~116 deep-sky objects      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     COMPUTED DATA (Runtime)                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Solar system positions    Ephemeris calculations       │   │
│  │  Grid lines               Mathematical generation       │   │
│  │  Horizon/zenith           User location + time          │   │
│  │  Meteor shower radiants   Hardcoded dates + positions   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     NETWORK DATA (Real-time)                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  ISS position             TLE orbital elements          │   │
│  │  Satellite positions      NORAD two-line elements       │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Pipeline

### Build Time

```
Raw Catalogs (CSV / hand-authored ASCII)
       │
       ▼
┌─────────────────┐
│  tools/         │
│  generate.sh    │  ──► ASCII proto text (*.ascii)
│  binary.sh      │  ──► Protocol Buffers binary (*.binary)
└─────────────────┘
       │
       ▼
app/src/main/assets/*.binary
```

### Runtime

```
Binary Assets (*.binary)
       │
       ▼
AbstractFileBasedLayer.initialize()
       │
       ▼
AstronomicalSourcesProto.parseFrom(bytes)
       │
       ▼
List<ProtobufAstronomicalSource>
       │
       ▼
Renderables (points, lines, labels, images)
```

## Data Characteristics

### Bundled Data

| Dataset | Update Frequency | Size | Accuracy |
|---------|------------------|------|----------|
| Stars | Per app release | ~2 MB | Fixed positions |
| Constellations | Rarely | ~50 KB | Cultural lines |
| Messier | Per app release | ~20 KB | Fixed positions |

### Computed Data

| Dataset | Update Frequency | Computation |
|---------|------------------|-------------|
| Planets | Per frame | Ephemeris equations |
| Moon | Per frame | Lunar theory |
| Sun | Per frame | Solar position |
| Grids | On zoom/pan | Mathematical |
| Horizon | On location change | Spherical geometry |

### Network Data

| Dataset | Update Frequency | Source |
|---------|------------------|--------|
| ISS | 60 seconds | TLE APIs |
| Satellites | 60 seconds | Celestrak |

## Data Accuracy

### Positional Accuracy

| Object Type | Accuracy | Notes |
|-------------|----------|-------|
| Stars | < 1 arc-second | Hipparcos precision |
| Planets | < 1 arc-minute | Ephemeris approximations |
| Moon | < 1 arc-minute | Simplified lunar theory |
| ISS | < 1 degree | TLE propagation |

### Temporal Validity

| Dataset | Valid Range |
|---------|-------------|
| Stars | Indefinite (no proper motion) |
| Ephemeris | ±100 years with good accuracy |
| ISS TLE | ~2 weeks from fetch |

## Key Files

| File | Purpose |
|------|---------|
| `datamodel/src/main/proto/source.proto` | Protocol Buffers schema |
| `app/src/main/assets/stars.binary` | Compiled star catalog |
| `app/src/main/assets/constellations.binary` | Compiled constellation data |
| `app/src/main/assets/messier.binary` | Compiled deep-sky object data |
| `Planet.kt` | Ephemeris calculations |
| `IssLayer.kt` | ISS tracking |
