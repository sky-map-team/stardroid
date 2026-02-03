# Rendering Abstraction Layer

## Purpose

Defines the **graphics abstraction layer** that separates the core domain from rendering implementation. This enables:
- Swapping OpenGL for Vulkan without touching astronomy code
- Adding AR as a new rendering backend
- Supporting future rendering technologies (WebGPU, Metal, etc.)

## Key Insight

**The core domain speaks in "celestial objects", not "vertices and shaders".**

## Core Interfaces

### RendererInterface

The primary interface between domain and rendering:

```kotlin
interface RendererInterface {
    // Lifecycle
    fun initialize(surface: Surface, width: Int, height: Int)
    fun resize(width: Int, height: Int)
    fun render(frame: RenderFrame)
    fun release()

    // Frame submission
    fun submitFrame(objects: List<CelestialObject>, view: CameraView)
    fun clearFrame()
}
```

### RenderFrame

A frame's worth of rendering data:

```kotlin
data class RenderFrame(
    val timestamp: Long,
    val view: CameraView,
    val objects: List<RenderObject>,
    val overlay: List<OverlayPrimitive>,
    val lighting: LightingConfig
)
```

### RenderObject

A celestial object ready for rendering:

```kotlin
data class RenderObject(
    val id: String,
    val position: Vector3,        // 3D position in camera space
    val magnitude: Float,         // Brightness
    val color: Color,             // Display color
    val primitive: GraphicPrimitive
)
```

### GraphicPrimitive

Types of graphical primitives:

```kotlin
enum class GraphicPrimitive {
    POINT,              // Stars (dots)
    LINE,               // Constellation lines
    TEXT,               // Labels
    IMAGE,              // Planet images, deep sky objects
    POLYGON,            // Constellation boundaries (AR mode)
    PATH                // Planet orbits, trails
}
```

### CameraView

Defines the viewing perspective:

```kotlin
data class CameraView(
    val eye: Vector3,              // Camera position
    val direction: Vector3,        // View direction
    val up: Vector3,               // Up vector
    val fov: Float,                // Field of view (degrees)
    val near: Float,               // Near clipping plane
    val far: Float                 // Far clipping plane
)
```

## Renderer Implementations

### OpenGLRenderer (Current)

```kotlin
class OpenGLRenderer : RendererInterface {
    // Uses OpenGL ES 2.0
    // Fixed function pipeline (for compatibility)
    // Point sprites for stars
    // Line strips for constellations
    // Textures for labels, images
}
```

**Current Implementation:**
- `SkyRenderer` - Main OpenGL renderer
- `RendererObjectManager` - Manages primitive objects
- `PointObjectManager` - Stars (points)
- `LineObjectManager` - Lines, grid
- `LabelObjectManager` - Text labels
- `ImageObjectManager` - Images

### VulkanRenderer (Future)

```kotlin
class VulkanRenderer : RendererInterface {
    // Vulkan API (compute shader for stars)
    // Descriptor sets for material bindings
    // Command buffers for frame submission
    // Uniform buffers for view matrices
}
```

**Design Considerations:**
- Compute shader for star rendering (massive parallelism)
- Push constants for dynamic data (sensor updates)
- Async compute for constellation line generation
- Secondary command buffers for overlays

### ARRenderer (Future)

```kotlin
class ARRenderer : RendererInterface {
    // ARCore integration
    // Camera feed as background
    // Celestial objects as 3D overlays
    // Constellations as spatial overlays
    // Pinching to zoom in/out of AR space
}
```

**AR-Specific Considerations:**
- **Background:** Camera feed (opaque), not black sky
- **Depth:** Objects have real depth, can be occluded
- **Scale:** 1:1 scale with real world (unlike map mode)
- **Coordinate Transform:** ARCore world → Celestial coordinates

## Object Managers

### ManagerInterface

```kotlin
interface ManagerInterface<T : GraphicPrimitive> {
    fun add(obj: T): Int
    fun update(id: Int, obj: T)
    fun remove(id: Int)
    fun clear()
    fun prepare(renderer: RendererInterface)
    fun render(renderer: RendererInterface)
}
```

### Example: PointObjectManager

```kotlin
class PointObjectManager : ManagerInterface<PointPrimitive> {
    private val points = mutableMapOf<Int, PointPrimitive>()

    override fun render(renderer: RendererInterface) {
        // OpenGL: glDrawArrays(GL_POINTS, ...)
        // Vulkan: vkCmdDraw for point vertices
        // AR: place anchor at position, attach sphere
    }
}
```

**Manager responsibilities:**
- **Culling:** Don't render off-screen objects
- **Level of Detail:** Simpler rendering for distant objects
- **Batching:** Group similar objects for efficiency

## Shader Abstraction

### ShaderInterface

```kotlin
interface ShaderInterface {
    val vertexShader: String
    val fragmentShader: String
    val uniforms: List<ShaderUniform>
    val attributes: List<ShaderAttribute>

    fun compile(): CompiledShader
}
```

### Shader Variants

**Star Shaders:**
- **OpenGL:** GLSL ES 1.00 (fixed function fallback)
- **Vulkan:** GLSL SPIR-V compute shader
- **AR:** ARCore-compatible shader with depth

**Text Shaders:**
- **OpenGL:** Texture atlas approach
- **Vulkan:** Signed distance field (SDF) text
- **AR:** Floating text labels in 3D space

## Resource Management

### TextureManager

```kotlin
interface TextureManager {
    fun loadTexture(id: String, path: String): TextureHandle
    fun releaseTexture(id: String)
    fun getTexture(id: String): TextureHandle?
}
```

**Texture Types:**
- Star sprites (multiple sizes for LOD)
- Planet textures
- Constellation boundary fills
- Text atlas for labels

### BufferManager

```kotlin
interface BufferManager {
    fun createVertexBuffer(size: Int): BufferHandle
    fun createIndexBuffer(size: Int): BufferHandle
    fun updateBuffer(handle: BufferHandle, data: FloatArray)
    fun releaseBuffer(handle: BufferHandle)
}
```

## Performance Optimizations

### Frustum Culling

Don't render objects outside the view:

```kotlin
fun isVisible(position: Vector3, view: CameraView): Boolean {
    // Transform position to camera space
    // Check against frustum planes
    // Return false if outside
}
```

### Level of Detail (LOD)

Simplify distant objects:

```kotlin
enum class LOD {
    HIGH,    // Near objects (full detail)
    MEDIUM,  // Medium distance
    LOW,     // Far objects (simplified)
}
```

### Instance Rendering

For many identical objects (stars):

```kotlin
interface InstancedRenderer {
    fun renderInstanced(primitive: GraphicPrimitive, instances: List<RenderObject>)
}
```

**Benefit:** Draw 10,000 stars with one draw call

## Animation Support

### AnimatedPrimitive

```kotlin
interface AnimatedPrimitive : GraphicPrimitive {
    fun updateAnimation(time: Long)
    fun isAnimating(): Boolean
}
```

**Use Cases:**
- Twinkling stars (alpha animation)
- Planet rotation (texture animation)
- Shooting stars (position animation)
- Time travel transitions

## AR-Specific Considerations

### ARCore Integration

```kotlin
interface ARRenderer : RendererInterface {
    fun setARSession(session: Session)
    fun setARConfig(config: Config)
    fun updateARFrame(frame: Frame)
}
```

### Coordinate Alignment

AR mode requires additional transforms:
- **AR World → GPS Location:** ARCore tracking to geodetic
- **Camera Pose → View Direction:** Where is camera looking in sky?
- **Ground Plane:** Horizon detection for occlusion

### Performance Targets

- **60 FPS** for smooth AR experience
- **Low latency** sensor-to-render pipeline
- **Battery efficient** - don't burn battery

## Migration Strategy

### Phase 1: Define Interfaces (Current)
- Create `RendererInterface`
- Refactor existing OpenGL code to implement it
- No functional changes, just extraction

### Phase 2: Add Vulkan Backend (Future)
- Implement `VulkanRenderer`
- Add runtime selection based on device capabilities
- Fallback to OpenGL if Vulkan unavailable

### Phase 3: Add AR Backend (Future)
- Implement `ARRenderer`
- Add AR mode toggle in UI
- Require ARCore-compatible devices

## Testing

### Unit Tests
- Interface contract verification
- Mock renderers for testing domain logic
- Shader compilation verification

### Integration Tests
- Render test scenes with known expected output
- Compare output between OpenGL and Vulkan renderers
- AR coordinate system validation

### Performance Tests
- Frame rate benchmarks
- Memory usage profiling
- Battery drain measurement

## Related Specifications

- [core-domain.md](core-domain.md) - Core domain that rendering visualizes
- [../rendering/pipeline.md](../rendering/pipeline.md) - Current OpenGL pipeline
- [ar-vulkan-target.md](ar-vulkan-target.md) - Target architecture details
