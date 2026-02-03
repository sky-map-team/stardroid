# AR/Vulkan Target Architecture

## Purpose

Defines the **target architecture** for AR mode and Vulkan rendering, the modern, high-performance future state of Stardroid's rendering pipeline.

## Vision Statement

**Stardroid AR:** Point your phone at the sky and see constellations, planets, and stars overlaid on the real world in their correct positions, with full 3D depth and spatial understanding.

## AR Mode Architecture

### User Experience

**Map Mode (Current):**
- Black background, white stars
- 2D sky map on screen
- Touch/drag to explore

**AR Mode (Future):**
- Camera feed as background
- Celestial objects as 3D overlays
- Walk around to see different parts of sky
- Constellations as spatial overhead structures

### Technical Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     AR Layer (ARCore)                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Camera Background (real world)                       │  │
│  │  - Device camera feed                                  │  │
│  │  - Detected planes (ground, tabletop)                 │  │
│  │  - Light estimation (realistic lighting)               │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Celestial Overlays (3D)                              │  │
│  │  - Stars (glowing spheres in correct positions)       │  │
│  │  - Planets (textured spheres with proper phase)       │  │
│  │  - Constellations (line art in 3D space)              │  │
│  │  - Labels (floating text anchored in 3D)              │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              Core Domain (Shared with Map Mode)            │
│  - Coordinate transforms (same math!)                     │
│  - Celestial object positions (same data!)                 │
│  - Sensor fusion (ARCore provides, we use)                 │
└─────────────────────────────────────────────────────────────┘
```

### ARCore Integration

### Tracking Modes

**ARCore provides:**
- **Motion Tracking:** 6DOF pose of device
- **Plane Detection:** Horizontal/vertical surfaces
- **Light Estimation:** Environmental lighting
- **Hit Testing:** Raycast against real world

**Stardroid uses:**
- **Motion Tracking:** Device orientation/position
- **Light Estimation:** Realistic planet brightness
- **Environmental Understanding:** Horizon detection (optional)

### Coordinate System Alignment

**Challenge:** ARCore uses "world origin" (where device was when AR started), but astronomy uses celestial coordinates.

**Solution:** GPS location bridges the gap:

```kotlin
data class ARWorld(
    val origin: Vector3,           // ARCore origin (device position at AR start)
    val gpsLocation: LatLon,        // GPS location of origin
    val altitude: Double            // Altitude above sea level
)

fun arToWorld(arPosition: Vector3, arWorld: ARWorld): LatLonAlt {
    // Convert AR position to GPS offset
    // Apply altitude correction
    // Return geodetic coordinates
}

fun celestialToAR(ra: Float, dec: Float, observer: LatLonAlt): Vector3 {
    // Convert RA/Dec to horizontal coordinates
    // Apply altitude correction
    // Place in AR 3D space relative to observer
}
```

**Key insight:** AR mode and map mode use the **same coordinate transform math**, just different reference frames.

## Vulkan Architecture

### Design Goals

1. **Performance:** Render 100,000+ stars at 60 FPS
2. **Efficiency:** Minimize CPU overhead, maximize GPU utilization
3. **Flexibility:** Easy to add new object types, effects
4. **Compatibility:** Fallback to OpenGL for older devices

### Vulkan Pipeline Design

### Compute Shader for Stars

**Current (OpenGL):**
- CPU batches stars by magnitude
- glDrawArrays() for each batch
- ~10-20 draw calls per frame

**Future (Vulkan):**
- One compute shader dispatch
- Process all stars in parallel
- Generate geometry on GPU
- Zero CPU-to-GPU synchronization

**Compute Shader Pseudocode:**
```glsl
layout(local_size_x = 256) in;

struct Star {
    vec3 position;    // Camera space
    float magnitude;  // Brightness
    vec3 color;
};

layout(binding = 0) buffer StarData {
    Star stars[];
};

layout(binding = 1) buffer VisibilityBuffer {
    uint visible[];  // Which stars are visible
};

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (!visible[idx]) return;

    Star s = stars[idx];

    // Calculate point size based on magnitude
    float size = calculatePointSize(s.magnitude);

    // Render to framebuffer attachment
    vec2 screenPos = projectToScreen(s.position);
    renderPoint(screenPos, size, s.color);
}
```

### Descriptor Set Layout

```kotlin
// Set 0: Per-frame data (changed every frame)
DescriptorSet(
    binding = 0: UniformBuffer(viewMatrix),           // Camera view
    binding = 1: UniformBuffer(projectionMatrix),    // Perspective
    binding = 2: UniformBuffer(lightingConfig)       // Night mode
)

// Set 1: Star data (immutable after load)
DescriptorSet(
    binding = 0: StorageBuffer(starPositions),       // Read-only
    binding = 1: StorageBuffer(starMagnitudes),      // Read-only
    binding = 2: StorageBuffer(starColors)           // Read-only
)
```

### Render Graph

```
┌──────────────────────────────────────────────────────────┐
│  vulkanFrameBegin()                                     │
├──────────────────────────────────────────────────────────┤
│  1. Acquire next image                                  │
│  2. Update view matrix uniform                           │
│  3. Submit compute shader for stars                      │
│  4. Submit graphics pipeline for lines (constellations)  │
│  5. Submit graphics pipeline for text (labels)          │
│  6. Submit graphics pipeline for images (planets)        │
│  7. Present image                                        │
│  vulkanFrameEnd()                                        │
└──────────────────────────────────────────────────────────┘
```

## Performance Targets

### Frame Rate
- **Target:** 60 FPS stable
- **Minimum:** 30 FPS acceptable
- **Measurement:** Frame time < 16.67ms for 60 FPS

### Object Counts
- **Stars:** ~100,000 visible at once
- **Planets:** ~8 (major planets)
- **Constellations:** ~88 (all IAU constellations)
- **Labels:** ~50 (major stars, planets)

### Memory Budget
- **Vertex data:** ~50MB (stars)
- **Textures:** ~20MB (planets, text)
- **Uniform buffers:** ~1MB per frame
- **Framebuffer:** ~16MB (1920x1080x4 bytes)

## Vulkan-Specific Optimizations

### Push Constants

For frequently changing data (view matrix):

```kotlin
vkCmdPushConstants(
    commandBuffer = commandBuffer,
    layoutFlag = VK_SHADER_STAGE_VERTEX_BIT,
    size = 64,  // sizeof(mat4)
    values = viewMatrixBuffer
)
```

**Benefit:** Faster than uniform buffer updates

### Secondary Command Buffers

Pre-recorded command sequences:

```kotlin
// Recorded once
val drawStarsCmdBuffer = recordDrawStarsCommand()

// Submitted every frame
vkCmdExecuteCommands(commandBuffer, drawStarsCmdBuffer)
```

**Use cases:**
- Star rendering (same geometry, different view matrix)
- Constellation lines (static geometry)
- Grid overlay (static geometry)

### Async Compute

Compute shader runs in parallel with graphics:

```kotlin
vkCmdBeginRenderPass(...)
    // Graphics pipeline: lines, text, images
vkCmdNextSubpass(...)
    // Compute shader: star positions for next frame
vkCmdEndRenderPass(...)
```

**Benefit:** Hide compute latency behind graphics work

## AR-Specific Architecture

### ARCore Session Management

```kotlin
class ARSessionManager {
    private lateinit var session: Session
    private lateinit var config: Config

    fun initialize() {
        session = Session(context)
        config = Config(session).apply {
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        }
        session.configure(config)
    }

    fun updateFrame(): Frame {
        session.setCameraTextureName(intTextureId)
        return session.update()
    }
}
```

### AR Rendering Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│  1. ARCore update()                                         │
│     - Get camera pose (position, orientation)              │
│     - Update light estimation                               │
│     - Get detected planes                                   │
├─────────────────────────────────────────────────────────────┤
│  2. Background: Camera feed                                 │
│     - Render camera background using ARCore                 │
│     - Apply light estimation to background                  │
├─────────────────────────────────────────────────────────────┤
│  3. Celestial Objects (3D)                                  │
│     - Transform celestial coords to AR world                │
│     - Render as 3D objects at correct positions             │
│     - Apply depth testing for occlusion                      │
├─────────────────────────────────────────────────────────────┤
│  4. Overlays (2D in 3D space)                              │
│     - Labels floating at star positions                     │
│     - Constellation lines in 3D                              │
│     - Grid overlay (optional)                                │
└─────────────────────────────────────────────────────────────┘
```

### Depth Handling

**Occlusion:** Real-world objects should block celestial objects

```kotlin
// Acquire depth from ARCore
val depthImage = frame.acquireDepthImage()

// Use for depth testing
vkCmdBindDepthStencilInfo(
    depthTestEnable = VK_TRUE,
    depthWriteEnable = VK_TRUE,
    depthCompareOp = VK_COMPARE_OP_LESS
)

// Celestial objects behind real objects won't render
```

### Scale Considerations

**Map Mode:** Entire sky visible on screen (fish-eye like)
- Stars at "infinity" effectively
- No real depth, just angular positions

**AR Mode:** Real-world scale matters
- Stars at true distances (invisible with naked eye)
- **Solution:** Exaggerate distances, keep correct relative positions
- Planets at true distances (also invisible)
- **Solution:** Scale planets to be visible (~10m away)

## Device Requirements

### Vulkan Mode
- **Android 8.0+** (API 26+)
- **Vulkan 1.1+** support
- **GPU:** Adreno 540+, Mali-G72+, or equivalent

### AR Mode
- **Android 7.0+** (API 24+)
- **ARCore-compatible device**
- **Camera:** RGB camera with good low-light performance
- **Sensors:** Gyroscope required for smooth tracking

## Fallback Strategy

```kotlin
fun createBestRenderer(): RendererInterface {
    val supportsAR = checkARCoreSupport()
    val supportsVulkan = checkVulkanSupport()

    return when {
        supportsAR -> ARRenderer()
        supportsVulkan -> VulkanRenderer()
        else -> OpenGLRenderer()
    }
}
```

**User preference:** Allow manual selection in settings

## Development Phases

### Phase 1: Vulkan Backend
- Implement VulkanRenderer
- Star compute shader
- Basic graphics pipeline (points, lines)
- Performance benchmarking

### Phase 2: AR Integration
- ARCore integration
- Camera background
- Celestial overlays in 3D
- Coordinate alignment

### Phase 3: AR Polish
- Depth handling
- Light estimation
- Smooth transitions between AR and map modes
- Performance optimization

## Testing Strategy

### Vulkan Tests
- Compute shader correctness (compare output to OpenGL)
- Frame rate benchmarks on target devices
- Memory leak detection (Vulkan is manual)
- Compatibility testing across GPUs

### AR Tests
- Coordinate alignment (verify objects match real sky)
- Performance on ARCore devices
- Battery drain measurement
- Occlusion accuracy

### User Acceptance
- Beta testing with astronomy community
- Compare AR vs map mode accuracy
- Survey user preferences

## Related Specifications

- [core-domain.md](core-domain.md) - Coordinate transforms used by AR
- [rendering-abstraction.md](rendering-abstraction.md) - Renderer interface
- [../features/ar-mode.md](../features/ar-mode.md) - AR feature specification
- [migration-roadmap.md](migration-roadmap.md) - How to get there
