# Rendering Pipeline

This document describes the complete rendering pipeline from astronomical data to screen pixels.

## Pipeline Overview

```
┌────────────────────────────────────────────────────────────────────┐
│  1. DATA SOURCES                                                    │
│     Binary protobuf files, ephemeris calculations, network data    │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  2. LAYERS                                                          │
│     Load/compute astronomical objects, provide renderables         │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  3. ASTRONOMICAL SOURCES                                            │
│     Logical objects with positions and visual representations      │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  4. PRIMITIVES                                                      │
│     Points, lines, labels, images with coordinates and properties  │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  5. RENDERER CONTROLLER                                             │
│     Queue updates, manage object managers                          │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  6. OBJECT MANAGERS                                                 │
│     Convert primitives to OpenGL vertex data                       │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  7. SKY RENDERER                                                    │
│     Apply transforms, execute OpenGL draw calls                    │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  8. SCREEN                                                          │
│     Final rendered frame                                           │
└────────────────────────────────────────────────────────────────────┘
```

## Stage 1: Data Sources

### File-Based Data

Binary protobuf files loaded from assets:

```java
// Asset loading
InputStream stream = assetManager.open("stars.binary");
AstronomicalSourcesProto sources = AstronomicalSourcesProto.parseFrom(stream);
```

### Computed Data

Ephemeris calculations for solar system:

```java
// Planet position calculation
double julianDate = TimeUtils.toJulianDate(currentTime);
GeocentricCoordinates position = planet.calculatePosition(julianDate);
```

### Network Data

Real-time data from internet:

```java
// ISS TLE fetch
String tleData = httpClient.get("https://api.wheretheiss.at/...");
OrbitalElements elements = TleParser.parse(tleData);
```

## Stage 2: Layers

Layers organize data into logical groups:

```java
public interface Layer {
    void initialize();
    void registerWithRenderer(RendererController controller);
    List<? extends AstronomicalRenderable> getRenderables();
}
```

### Layer Types

| Type | Example | Data Source |
|------|---------|-------------|
| File-based | StarsLayer | Binary protobuf |
| Computed | GridLayer | Mathematical generation |
| Network | IssLayer | TLE orbital data |

## Stage 3: Astronomical Sources

Each source represents a celestial object:

```java
public interface AstronomicalSource {
    GeocentricCoordinates getSearchLocation();
    List<String> getNames();

    List<PointPrimitive> getPoints();
    List<LinePrimitive> getLines();
    List<TextPrimitive> getLabels();
    List<ImagePrimitive> getImages();
}
```

### ProtobufAstronomicalSource

Wraps protobuf data:

```java
public class ProtobufAstronomicalSource implements AstronomicalSource {
    private final AstronomicalSourceProto proto;

    @Override
    public List<PointPrimitive> getPoints() {
        return proto.getPointList().stream()
            .map(this::toPointPrimitive)
            .collect(toList());
    }
}
```

## Stage 4: Primitives

Four primitive types represent visual elements:

### PointPrimitive

```java
public class PointPrimitive {
    GeocentricCoordinates coordinates;  // Position on celestial sphere
    int color;                          // ARGB color
    int size;                           // Point size in pixels
    Shape shape;                        // CIRCLE, STAR, etc.
}
```

### LinePrimitive

```java
public class LinePrimitive {
    GeocentricCoordinates start;
    GeocentricCoordinates end;
    int color;
    float lineWidth;
}
```

### TextPrimitive

```java
public class TextPrimitive {
    String text;
    GeocentricCoordinates coordinates;
    int color;
    int fontSize;
    float offset;  // Offset from associated point
}
```

### ImagePrimitive

```java
public class ImagePrimitive {
    GeocentricCoordinates coordinates;
    int resourceId;          // Drawable resource
    float scale;             // Display scale
    float rotationAngle;     // Orientation
}
```

## Stage 5: Renderer Controller

Manages update queue and object managers:

```java
public class RendererController {
    private final Queue<RenderUpdate> updateQueue;
    private final Map<Class<?>, RendererObjectManager> managers;

    public void queueUpdate(UpdateType type, List<? extends Primitive> primitives) {
        updateQueue.add(new RenderUpdate(type, primitives));
    }

    public void processUpdates() {
        while (!updateQueue.isEmpty()) {
            RenderUpdate update = updateQueue.poll();
            dispatchToManager(update);
        }
    }
}
```

### Update Types

```java
public enum UpdateType {
    Reset,           // Complete data reload
    UpdatePositions, // Recalculate positions only
    UpdateImages     // Reload textures only
}
```

## Stage 6: Object Managers

Convert primitives to OpenGL-ready data:

```java
public abstract class RendererObjectManager {
    protected FloatBuffer vertexBuffer;
    protected FloatBuffer colorBuffer;
    protected int vertexCount;

    public abstract void updateObjects(List<? extends Primitive> primitives);
    public abstract void draw(GL10 gl);
}
```

### PointObjectManager

Renders stars and planets as point sprites:

```java
public class PointObjectManager extends RendererObjectManager {
    @Override
    public void updateObjects(List<PointPrimitive> points) {
        vertexCount = points.size();
        vertexBuffer = allocateBuffer(vertexCount * 3);  // x, y, z
        colorBuffer = allocateBuffer(vertexCount * 4);   // r, g, b, a
        sizeBuffer = allocateBuffer(vertexCount);        // point size

        for (PointPrimitive p : points) {
            vertexBuffer.put(p.coordinates.x);
            vertexBuffer.put(p.coordinates.y);
            vertexBuffer.put(p.coordinates.z);
            // ... colors and sizes
        }
    }
}
```

## Stage 7: Sky Renderer

Main OpenGL renderer:

```java
public class SkyRenderer implements GLSurfaceView.Renderer {
    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear buffers
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        // Apply view transformation
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadMatrixf(viewMatrix, 0);

        // Process pending updates
        rendererController.processUpdates();

        // Draw each manager in depth order
        for (RendererObjectManager manager : sortedManagers) {
            manager.draw(gl);
        }
    }
}
```

### Transformation Pipeline

```
Model Coordinates (celestial sphere)
         │
         ▼
    [View Matrix]  ← Phone orientation transform
         │
         ▼
View Coordinates (camera space)
         │
         ▼
    [Projection Matrix]  ← Field of view, aspect ratio
         │
         ▼
Clip Coordinates
         │
         ▼
    [Viewport Transform]
         │
         ▼
Screen Coordinates (pixels)
```

## Frame Timing

Typical frame breakdown:

| Phase | Time (ms) | Notes |
|-------|-----------|-------|
| Update queue processing | 0.5-2 | Depends on update count |
| Vertex buffer updates | 1-3 | When data changes |
| Draw calls | 5-10 | Depends on primitive count |
| Buffer swap | 1-2 | VSync wait |
| **Total** | **8-16** | Target: <16ms for 60 FPS |

## Update Triggers

| Event | Update Type | Affected Managers |
|-------|-------------|-------------------|
| Time change | UpdatePositions | Planets, grids |
| Location change | UpdatePositions | Horizon, grids |
| Layer toggle | Reset | Toggled layer |
| Zoom change | None | Only view matrix |
| Orientation change | None | Only view matrix |
