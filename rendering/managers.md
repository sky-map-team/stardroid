# Object Managers

Object managers convert primitives into OpenGL draw calls.

## Manager Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    RendererObjectManager (abstract)              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  • vertexBuffer: FloatBuffer                            │   │
│  │  • colorBuffer: FloatBuffer                             │   │
│  │  • textureManager: TextureManager                       │   │
│  │  • depthOrder: int                                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │  + updateObjects(primitives)                            │   │
│  │  + draw(gl)                                             │   │
│  │  + reload(gl, fullReload)                               │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│PointObjectMgr  │ │PolyLineObjectMgr│ │LabelObjectMgr   │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │
          ▼
┌─────────────────┐
│ImageObjectMgr   │
└─────────────────┘
```

## RendererObjectManager

Base class for all object managers.

### Structure

```java
public abstract class RendererObjectManager {
    // Vertex data buffers
    protected FloatBuffer vertexBuffer;
    protected FloatBuffer colorBuffer;
    protected int vertexCount;

    // Rendering properties
    protected int depthOrder;
    protected boolean enabled = true;

    // Resources
    protected TextureManager textureManager;
    protected Resources resources;

    // Abstract methods
    public abstract void updateObjects(List<? extends Primitive> primitives);
    public abstract void draw(GL10 gl);
    public abstract void reload(GL10 gl, boolean fullReload);
}
```

### Update Flow

```java
// Called from RendererController on GL thread
public void queueForUpdate(UpdateType type) {
    switch (type) {
        case Reset:
            clearBuffers();
            updateObjects(currentPrimitives);
            break;
        case UpdatePositions:
            updatePositionsOnly();
            break;
        case UpdateImages:
            reloadTextures();
            break;
    }
}
```

## PointObjectManager

Renders stars and planets as point sprites.

### Implementation

```java
public class PointObjectManager extends RendererObjectManager {
    private FloatBuffer sizeBuffer;
    private int pointSpriteTexture;

    @Override
    public void updateObjects(List<PointPrimitive> points) {
        vertexCount = points.size();

        // Allocate buffers
        vertexBuffer = allocateFloatBuffer(vertexCount * 3);  // xyz
        colorBuffer = allocateFloatBuffer(vertexCount * 4);   // rgba
        sizeBuffer = allocateFloatBuffer(vertexCount);        // size

        for (PointPrimitive p : points) {
            // Position
            vertexBuffer.put(p.getX());
            vertexBuffer.put(p.getY());
            vertexBuffer.put(p.getZ());

            // Color (convert ARGB to RGBA float)
            colorBuffer.put(Color.red(p.color) / 255f);
            colorBuffer.put(Color.green(p.color) / 255f);
            colorBuffer.put(Color.blue(p.color) / 255f);
            colorBuffer.put(Color.alpha(p.color) / 255f);

            // Size
            sizeBuffer.put(p.size * screenDensity);
        }

        // Rewind buffers
        vertexBuffer.rewind();
        colorBuffer.rewind();
        sizeBuffer.rewind();
    }

    @Override
    public void draw(GL10 gl) {
        if (vertexCount == 0) return;

        // Enable point sprites
        gl.glEnable(GL10.GL_POINT_SPRITE_OES);
        gl.glTexEnvi(GL10.GL_POINT_SPRITE_OES,
                     GL10.GL_COORD_REPLACE_OES, GL10.GL_TRUE);

        // Bind point texture
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, pointSpriteTexture);

        // Set up vertex arrays
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer);

        // Point size array (extension)
        ((GL11) gl).glPointSizePointerOES(GL10.GL_FLOAT, 0, sizeBuffer);

        // Draw all points
        gl.glDrawArrays(GL10.GL_POINTS, 0, vertexCount);

        // Cleanup
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisable(GL10.GL_POINT_SPRITE_OES);
    }
}
```

### Point Sprite Texture

Circular gradient for smooth star appearance:

```java
private Bitmap createPointTexture(int size) {
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    float center = size / 2f;
    RadialGradient gradient = new RadialGradient(
        center, center, center,
        Color.WHITE, Color.TRANSPARENT,
        Shader.TileMode.CLAMP
    );

    Paint paint = new Paint();
    paint.setShader(gradient);
    canvas.drawCircle(center, center, center, paint);

    return bitmap;
}
```

## PolyLineObjectManager

Renders constellation lines and grids.

### Implementation

```java
public class PolyLineObjectManager extends RendererObjectManager {
    private List<LineGroup> lineGroups;

    @Override
    public void updateObjects(List<LinePrimitive> lines) {
        // Group lines by color and width
        lineGroups = groupLines(lines);

        // Build vertex buffer for all lines
        int totalVertices = calculateTotalVertices(lineGroups);
        vertexBuffer = allocateFloatBuffer(totalVertices * 3);

        for (LineGroup group : lineGroups) {
            for (GeocentricCoordinates vertex : group.vertices) {
                vertexBuffer.put(vertex.x);
                vertexBuffer.put(vertex.y);
                vertexBuffer.put(vertex.z);
            }
        }
        vertexBuffer.rewind();
    }

    @Override
    public void draw(GL10 gl) {
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);

        int offset = 0;
        for (LineGroup group : lineGroups) {
            // Set line properties
            gl.glLineWidth(group.lineWidth);
            gl.glColor4f(
                Color.red(group.color) / 255f,
                Color.green(group.color) / 255f,
                Color.blue(group.color) / 255f,
                Color.alpha(group.color) / 255f
            );

            // Draw line strip
            gl.glDrawArrays(GL10.GL_LINE_STRIP, offset, group.vertices.size());
            offset += group.vertices.size();
        }

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    }
}
```

## LabelObjectManager

Renders text labels as textured quads.

### Implementation

```java
public class LabelObjectManager extends RendererObjectManager {
    private TextureAtlas textureAtlas;
    private List<LabelQuad> labelQuads;

    @Override
    public void updateObjects(List<TextPrimitive> labels) {
        labelQuads = new ArrayList<>();

        for (TextPrimitive label : labels) {
            // Render text to bitmap
            Bitmap textBitmap = renderText(label.text, label.fontSize, label.color);

            // Add to texture atlas
            AtlasRegion region = textureAtlas.addBitmap(textBitmap);

            // Create quad for this label
            labelQuads.add(new LabelQuad(
                label.coordinates,
                region,
                textBitmap.getWidth(),
                textBitmap.getHeight()
            ));
        }

        // Upload atlas texture
        textureAtlas.upload();

        // Build vertex buffers
        buildVertexBuffers();
    }

    @Override
    public void draw(GL10 gl) {
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureAtlas.getTextureId());

        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texCoordBuffer);

        // Draw all quads (2 triangles each)
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, labelQuads.size() * 6);

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisable(GL10.GL_BLEND);
    }

    private Bitmap renderText(String text, int fontSize, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(fontSize);
        paint.setColor(color);

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        Bitmap bitmap = Bitmap.createBitmap(
            bounds.width() + 4,
            bounds.height() + 4,
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(text, 2, bounds.height() + 2, paint);

        return bitmap;
    }
}
```

## ImageObjectManager

Renders astronomical images as textured quads.

### Implementation

```java
public class ImageObjectManager extends RendererObjectManager {
    private Map<Integer, Integer> textureIds;  // resourceId → GL texture ID
    private List<ImageQuad> imageQuads;

    @Override
    public void updateObjects(List<ImagePrimitive> images) {
        imageQuads = new ArrayList<>();

        for (ImagePrimitive image : images) {
            // Load texture if not cached
            if (!textureIds.containsKey(image.resourceId)) {
                int textureId = loadTexture(image.resourceId);
                textureIds.put(image.resourceId, textureId);
            }

            // Create oriented quad
            imageQuads.add(new ImageQuad(
                image.coordinates,
                textureIds.get(image.resourceId),
                image.scale,
                image.rotationAngle
            ));
        }

        buildVertexBuffers();
    }

    @Override
    public void draw(GL10 gl) {
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        // Draw each image (different textures)
        for (int i = 0; i < imageQuads.size(); i++) {
            ImageQuad quad = imageQuads.get(i);

            gl.glBindTexture(GL10.GL_TEXTURE_2D, quad.textureId);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, quad.vertexBuffer);
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, quad.texCoordBuffer);

            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
        }

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisable(GL10.GL_BLEND);
    }
}
```

## TextureManager

Manages texture loading and caching.

```java
public class TextureManager {
    private final Map<Integer, Integer> textureCache;
    private final Resources resources;

    public int loadTexture(int resourceId) {
        if (textureCache.containsKey(resourceId)) {
            return textureCache.get(resourceId);
        }

        Bitmap bitmap = BitmapFactory.decodeResource(resources, resourceId);
        int textureId = uploadTexture(bitmap);
        bitmap.recycle();

        textureCache.put(resourceId, textureId);
        return textureId;
    }

    private int uploadTexture(Bitmap bitmap) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        return textures[0];
    }
}
```

## Depth Ordering

Managers sorted by depth order for correct rendering:

| Manager | Depth | Reason |
|---------|-------|--------|
| SkyGradientManager | 0 | Background |
| PolyLineObjectManager | 10-30 | Grids, constellations |
| PointObjectManager | 40-70 | Stars, planets |
| ImageObjectManager | 50 | Deep-sky images |
| LabelObjectManager | 100 | Labels on top |
