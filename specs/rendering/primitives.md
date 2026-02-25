# Rendering Primitives

Sky Map uses four primitive types to represent visual elements.

## Primitive Types

| Primitive | Use Case | OpenGL Technique |
|-----------|----------|------------------|
| Point | Stars, planets | Point sprites |
| Line | Constellations, grids | Line strips |
| Label | Object names | Textured quads |
| Image | Deep-sky photos | Textured quads |

## PointPrimitive

Represents point-like objects (stars, planets).

### Structure

```java
public class PointPrimitive {
    // Position on celestial sphere (unit vector)
    private GeocentricCoordinates coordinates;

    // Visual properties
    private int color;      // ARGB format
    private int size;       // Size in pixels
    private Shape shape;    // Point shape
}
```

### Shape Types

```java
public enum Shape {
    CIRCLE,              // Default star shape
    STAR,                // 4-pointed star
    ELLIPTICAL_GALAXY,   // Ellipse icon
    SPIRAL_GALAXY,       // Spiral icon
    IRREGULAR_GALAXY,    // Irregular shape
    LENTICULAR_GALAXY,   // Lens shape
    GLOBULAR_CLUSTER,    // Dense cluster
    OPEN_CLUSTER,        // Sparse cluster
    NEBULA,              // Cloud shape
    HUBBLE_DEEP_FIELD    // Special icon
}
```

### Rendering

Points rendered as OpenGL point sprites:

```java
// OpenGL ES 2.0 point rendering
gl.glEnable(GL10.GL_POINT_SPRITE_OES);
gl.glTexEnvi(GL10.GL_POINT_SPRITE_OES,
             GL10.GL_COORD_REPLACE_OES, GL10.GL_TRUE);

gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer);
gl.glPointSizePointerOES(GL10.GL_FLOAT, 0, sizeBuffer);

gl.glDrawArrays(GL10.GL_POINTS, 0, pointCount);
```

### Size Scaling

Point size adjusted based on:
- Star magnitude (brighter = larger)
- Zoom level (closer = larger)
- Screen density (higher DPI = larger)

```java
float scaledSize = baseSize * zoomFactor * screenDensity;
```

## LinePrimitive

Represents lines (constellation outlines, grid lines).

### Structure

```java
public class LinePrimitive {
    // Endpoints (unit vectors on celestial sphere)
    private GeocentricCoordinates start;
    private GeocentricCoordinates end;

    // Visual properties
    private int color;       // ARGB format
    private float lineWidth; // Width in pixels
}
```

### Line Groups

Related lines grouped for efficiency:

```java
public class LineGroup {
    private List<GeocentricCoordinates> vertices;
    private int color;
    private float lineWidth;

    // Rendered as GL_LINE_STRIP
    public List<GeocentricCoordinates> getVertices() {
        return vertices;
    }
}
```

### Rendering

Lines rendered as OpenGL line strips:

```java
gl.glLineWidth(lineWidth);
gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
gl.glColor4f(r, g, b, a);
gl.glDrawArrays(GL10.GL_LINE_STRIP, 0, vertexCount);
```

### Great Circle Interpolation

Long lines curved along great circles:

```java
// Interpolate points along great circle arc
List<GeocentricCoordinates> interpolate(
    GeocentricCoordinates start,
    GeocentricCoordinates end,
    int segments
) {
    List<GeocentricCoordinates> points = new ArrayList<>();
    for (int i = 0; i <= segments; i++) {
        float t = i / (float) segments;
        points.add(slerp(start, end, t));  // Spherical linear interpolation
    }
    return points;
}
```

## TextPrimitive

Represents text labels for object names.

### Structure

```java
public class TextPrimitive {
    // Text content
    private String text;

    // Position (unit vector on celestial sphere)
    private GeocentricCoordinates coordinates;

    // Visual properties
    private int color;         // ARGB format
    private int fontSize;      // Size enum (SMALL, MEDIUM, LARGE)
    private float offset;      // Offset from associated object
}
```

### Font Sizes

```java
public enum FontSize {
    SMALL(12),    // Faint stars
    MEDIUM(16),   // Normal objects
    LARGE(20);    // Major objects

    private final int sizeInSp;
}
```

### Rendering

Labels rendered as textured quads:

1. **Text Rasterization**: Convert text to bitmap
2. **Texture Upload**: Upload bitmap to texture atlas
3. **Quad Generation**: Create quad vertices
4. **Rendering**: Draw textured quads

```java
// Generate label texture
Bitmap labelBitmap = renderTextToBitmap(text, fontSize, color);
int textureId = textureManager.uploadTexture(labelBitmap);

// Create quad at label position
float[] quadVertices = createBillboardQuad(coordinates, labelWidth, labelHeight);

// Render with texture
gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
```

### Texture Atlas

Labels batched into texture atlas for efficiency:

```
┌─────────────────────────────────────────┐
│ Polaris │ Sirius │ Vega   │ Deneb     │
├─────────┼────────┼────────┼───────────┤
│ Mars    │ Jupiter│ Saturn │ Venus     │
├─────────┼────────┼────────┼───────────┤
│ Orion   │ Ursa M.│ Cassi. │ Scorpius  │
└─────────┴────────┴────────┴───────────┘
```

## ImagePrimitive

Represents images (deep-sky object photographs).

### Structure

```java
public class ImagePrimitive {
    // Position (unit vector on celestial sphere)
    private GeocentricCoordinates coordinates;

    // Image source
    private int resourceId;      // Drawable resource ID

    // Display properties
    private float scale;         // Display scale factor
    private float rotationAngle; // Orientation in degrees
}
```

### Rendering

Images rendered as textured, oriented quads:

```java
// Load and bind texture
int textureId = textureManager.loadTexture(resourceId);
gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);

// Create oriented quad
float[] quadVertices = createOrientedQuad(
    coordinates,
    scale,
    rotationAngle
);

// Draw with alpha blending
gl.glEnable(GL10.GL_BLEND);
gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
```

### Billboarding

Image quads face the camera (billboard effect):

```java
// Calculate billboard orientation
Vector3 up = calculateUpVector(cameraPosition, objectPosition);
Vector3 right = calculateRightVector(cameraPosition, objectPosition);

// Create quad vertices
float halfWidth = imageWidth * scale / 2;
float halfHeight = imageHeight * scale / 2;

vertices[0] = position + (-right - up) * halfWidth;  // Bottom-left
vertices[1] = position + (right - up) * halfWidth;   // Bottom-right
vertices[2] = position + (-right + up) * halfHeight; // Top-left
vertices[3] = position + (right + up) * halfHeight;  // Top-right
```

## Coordinate System

All primitives use geocentric coordinates (unit vectors):

```java
public class GeocentricCoordinates {
    public float x, y, z;  // Unit vector (x² + y² + z² = 1)

    public static GeocentricCoordinates fromRaDec(float ra, float dec) {
        float raRad = (float) Math.toRadians(ra * 15);  // RA in hours → degrees → radians
        float decRad = (float) Math.toRadians(dec);

        return new GeocentricCoordinates(
            (float) (Math.cos(decRad) * Math.cos(raRad)),
            (float) (Math.cos(decRad) * Math.sin(raRad)),
            (float) Math.sin(decRad)
        );
    }
}
```

## Primitive Lifecycle

```
Creation → Registration → Update → Rendering → Disposal
    │           │            │          │           │
    ▼           ▼            ▼          ▼           ▼
 Layer     Controller   Controller  SkyRenderer  Layer
initialize() queueAdd() queueUpdate()  draw()   cleanup()
```
