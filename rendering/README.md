# Rendering System Overview

This section documents Sky Map's OpenGL ES 2.0 rendering pipeline.

## Contents

| Specification | Description |
|--------------|-------------|
| [Pipeline](pipeline.md) | Layer → Primitives → OpenGL flow |
| [Primitives](primitives.md) | Points, lines, labels, images |
| [Managers](managers.md) | Object managers and rendering |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                          Layers                                  │
│   ┌─────────┐ ┌─────────────┐ ┌─────────┐ ┌─────────────────┐  │
│   │ Stars   │ │Constellations│ │ Planets │ │ Grid/Horizon   │  │
│   └────┬────┘ └──────┬──────┘ └────┬────┘ └────────┬────────┘  │
│        │             │             │               │            │
│        └─────────────┴─────────────┴───────────────┘            │
│                              │                                   │
│                              ▼                                   │
│              ┌───────────────────────────────┐                  │
│              │    AstronomicalSource         │                  │
│              │    (per celestial object)     │                  │
│              └───────────────┬───────────────┘                  │
└──────────────────────────────┼──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Primitives                                 │
│   ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐      │
│   │  Points   │ │   Lines   │ │  Labels   │ │  Images   │      │
│   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘      │
│         │             │             │             │              │
│         └─────────────┴─────────────┴─────────────┘              │
│                              │                                   │
│                              ▼                                   │
│              ┌───────────────────────────────┐                  │
│              │     RendererController        │                  │
│              │     (queues updates)          │                  │
│              └───────────────┬───────────────┘                  │
└──────────────────────────────┼──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Object Managers                              │
│   ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐      │
│   │PointMgr   │ │ LineMgr   │ │ LabelMgr  │ │ ImageMgr  │      │
│   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘      │
│         │             │             │             │              │
│         └─────────────┴─────────────┴─────────────┘              │
│                              │                                   │
│                              ▼                                   │
│              ┌───────────────────────────────┐                  │
│              │         SkyRenderer           │                  │
│              │      (OpenGL ES 2.0)          │                  │
│              └───────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### SkyRenderer

Main `GLSurfaceView.Renderer` implementation:
- Manages OpenGL context and state
- Applies view transformation matrices
- Coordinates object manager rendering
- Handles texture loading via `TextureManager`

### RendererController

Queues updates from layers:
- Thread-safe update queuing
- Batches updates for efficiency
- Supports three update types: `Reset`, `UpdatePositions`, `UpdateImages`

### Object Managers

Specialized renderers for each primitive type:
- `PointObjectManager` - Stars and planets (point sprites)
- `PolyLineObjectManager` - Constellation lines and grids
- `LabelObjectManager` - Text labels
- `ImageObjectManager` - Deep-sky object images

### TextureManager

Handles texture loading and caching:
- Loads images from resources
- Manages OpenGL texture IDs
- Implements texture atlas for labels

## Coordinate Systems

### Celestial Coordinates

Right Ascension (RA) and Declination (Dec):
- RA: 0-24 hours (0-360°)
- Dec: -90° to +90°

### Geocentric Coordinates

Unit vectors on celestial sphere:
- x, y, z components
- Magnitude = 1.0

### View Coordinates

After applying phone orientation transform:
- Camera at origin
- Looking along -Z axis
- Y axis up

### Screen Coordinates

2D pixel positions:
- Origin at screen center
- X right, Y up (OpenGL convention)

## Rendering Order

Objects rendered back-to-front by depth:

| Depth | Content |
|-------|---------|
| 0 | Sky gradient background |
| 10 | Grid lines |
| 20 | Horizon |
| 30 | Constellation lines |
| 40 | Stars (points) |
| 50 | Deep-sky images |
| 60 | Planet icons |
| 70 | ISS icon |
| 100 | All labels |

## Performance Considerations

### Optimization Strategies

1. **Frustum Culling**: Only render visible objects
2. **LOD**: Reduce detail at lower zoom levels
3. **Batching**: Group similar primitives
4. **Texture Atlas**: Single texture for all labels
5. **VBOs**: Vertex buffer objects for geometry

### Frame Rate Target

- Goal: 60 FPS smooth rendering
- Typical load: 5,000-10,000 primitives
- Bottleneck: Usually fill rate (labels/images)

## Key Files

| File | Purpose |
|------|---------|
| `SkyRenderer.kt` | Main OpenGL renderer |
| `RendererController.kt` | Update queue management |
| `PointObjectManager.kt` | Star/planet rendering |
| `PolyLineObjectManager.kt` | Line rendering |
| `LabelObjectManager.kt` | Text rendering |
| `ImageObjectManager.kt` | Image rendering |
| `TextureManager.kt` | Texture loading |
