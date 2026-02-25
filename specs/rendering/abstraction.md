# Rendering Abstraction Layer

> **FUTURE STATE SPECIFICATION** — This document specifies a possible planned `RendererInterface` abstraction that does not yet exist. The current code uses OpenGL ES directly via `SkyRenderer`. This spec describes the first step toward a pluggable renderer architecture.

This document specifies the graphics abstraction layer that would separate domain logic from rendering implementation, enabling multiple backends (Vulkan, OpenGL, AR).

## Purpose

The abstraction layer provides:
- Backend independence - swap Vulkan for OpenGL without touching domain code
- Testability - mock renderer for unit tests
- Future extensibility - AR rendering, WebGPU, etc.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       Domain Layer                               │
│     Astronomical sources, coordinate transforms, layers         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RendererInterface                              │
│     initialize, resize, release, beginFrame, endFrame, draw     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│ VulkanRenderer │  │ OpenGLRenderer │  │   ARRenderer   │
│   (Current)    │  │    (Future)    │  │    (Future)    │
└────────────────┘  └────────────────┘  └────────────────┘
```

## Core Interface

### RendererInterface

Primary interface between domain and rendering:

```kotlin
package com.stardroid.awakening.renderer

import android.view.Surface

/**
 * Core interface for rendering backends.
 *
 * The domain layer speaks to this interface, not to specific
 * graphics APIs. Implementations handle Vulkan, OpenGL, or AR.
 */
interface RendererInterface {
    // Lifecycle
    fun initialize(surface: Surface, width: Int, height: Int): Boolean
    fun resize(width: Int, height: Int)
    fun release()

    // Frame rendering
    fun beginFrame(): Boolean  // Returns false if frame should be skipped
    fun endFrame()

    // Drawing
    fun draw(batch: DrawBatch)

    // View configuration
    fun setViewMatrix(matrix: FloatArray)       // 4x4 column-major
    fun setProjectionMatrix(matrix: FloatArray) // 4x4 column-major

    // State queries
    val isInitialized: Boolean
    val frameNumber: Long
}
```

### Primitive Types

```kotlin
package com.stardroid.awakening.renderer

/** Types of graphical primitives */
enum class PrimitiveType {
    POINTS,      // Stars (variable size dots)
    LINES,       // Constellation lines, grids
    TRIANGLES,   // Filled shapes
    TEXT,        // Labels (future)
    IMAGE        // Textures (future)
}
```

### Vertex Format

```kotlin
/**
 * Vertex with position and color.
 *
 * Layout: x, y, z, r, g, b, a (7 floats per vertex)
 */
data class Vertex(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f
) {
    companion object {
        const val COMPONENTS = 7
        const val STRIDE_BYTES = COMPONENTS * 4
    }
}
```

### Draw Batch

```kotlin
/**
 * A batch of primitives to draw.
 *
 * Vertices are packed as [x,y,z,r,g,b,a, x,y,z,r,g,b,a, ...]
 */
data class DrawBatch(
    val type: PrimitiveType,
    val vertices: FloatArray,
    val vertexCount: Int,
    val transform: FloatArray? = null  // Optional 4x4 model matrix
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBatch) return false
        return type == other.type &&
               vertices.contentEquals(other.vertices) &&
               vertexCount == other.vertexCount &&
               transform.contentEquals(other.transform)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + vertexCount
        result = 31 * result + (transform?.contentHashCode() ?: 0)
        return result
    }
}
```

## Matrix Utilities

Helper functions for common matrix operations:

```kotlin
package com.stardroid.awakening.renderer

object Matrix {
    /** Create 4x4 identity matrix */
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** Create perspective projection matrix */
    fun perspective(
        fovY: Float,      // Vertical FOV in degrees
        aspect: Float,    // Width / height
        near: Float,      // Near plane
        far: Float        // Far plane
    ): FloatArray

    /** Create rotation matrix around Z axis */
    fun rotateZ(angleDegrees: Float): FloatArray

    /** Multiply two 4x4 matrices: result = a * b */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray
}
```

## Package Structure

```
app/src/main/kotlin/com/stardroid/awakening/
├── renderer/
│   ├── RendererInterface.kt    # Core interface
│   ├── Primitive.kt            # PrimitiveType, Vertex, DrawBatch
│   └── Matrix.kt               # Matrix helper utilities
└── vulkan/
    ├── VulkanRenderer.kt       # Implements RendererInterface
    └── VulkanSurfaceView.kt    # Surface lifecycle management
```

## VulkanRenderer Implementation

The existing `VulkanRenderer` will be refactored to implement `RendererInterface`:

```kotlin
class VulkanRenderer : RendererInterface {
    private var nativeContext: Long = 0
    private var _frameNumber: Long = 0

    override fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        nativeContext = nativeInit(surface)
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
        }
        return nativeContext != 0L
    }

    override fun beginFrame(): Boolean {
        if (nativeContext == 0L) return false
        return nativeBeginFrame(nativeContext)
    }

    override fun endFrame() {
        if (nativeContext != 0L) {
            nativeEndFrame(nativeContext)
            _frameNumber++
        }
    }

    override fun draw(batch: DrawBatch) {
        if (nativeContext != 0L) {
            nativeDraw(
                nativeContext,
                batch.type.ordinal,
                batch.vertices,
                batch.vertexCount,
                batch.transform
            )
        }
    }

    // ... other methods

    // JNI declarations
    private external fun nativeInit(surface: Surface): Long
    private external fun nativeBeginFrame(context: Long): Boolean
    private external fun nativeEndFrame(context: Long)
    private external fun nativeDraw(
        context: Long,
        primitiveType: Int,
        vertices: FloatArray,
        vertexCount: Int,
        transform: FloatArray?
    )
    // ...
}
```

## Native C++ Changes

The native layer needs new JNI entry points:

```cpp
// New JNI methods
JNIEXPORT jboolean JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeBeginFrame(
    JNIEnv* env, jobject obj, jlong context);

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeEndFrame(
    JNIEnv* env, jobject obj, jlong context);

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeDraw(
    JNIEnv* env, jobject obj, jlong context,
    jint primitiveType, jfloatArray vertices, jint vertexCount,
    jfloatArray transform);

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeSetViewMatrix(
    JNIEnv* env, jobject obj, jlong context, jfloatArray matrix);

JNIEXPORT void JNICALL
Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeSetProjectionMatrix(
    JNIEnv* env, jobject obj, jlong context, jfloatArray matrix);
```

## Rendering Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  Per Frame                                                       │
│                                                                  │
│  1. renderer.beginFrame()                                        │
│     └── Acquire swapchain image, begin command buffer           │
│                                                                  │
│  2. renderer.setViewMatrix(viewMatrix)                          │
│     renderer.setProjectionMatrix(projMatrix)                    │
│     └── Update uniform buffers / push constants                 │
│                                                                  │
│  3. renderer.draw(starsBatch)                                   │
│     renderer.draw(linesBatch)                                   │
│     renderer.draw(...)                                          │
│     └── Record draw commands into command buffer                │
│                                                                  │
│  4. renderer.endFrame()                                          │
│     └── End command buffer, submit, present                     │
└─────────────────────────────────────────────────────────────────┘
```

## Migration Strategy

### Phase 1: Interface Definition (Current)
- Create `RendererInterface`, `Primitive.kt`, `Matrix.kt`
- Refactor `VulkanRenderer` to implement interface
- Update `VulkanSurfaceView` to use new API
- Demo: Same triangle rendered through new abstraction

### Phase 2: Dynamic Vertex Buffers
- Implement `nativeDraw()` with dynamic vertex data
- Support multiple draw calls per frame
- Demo: Multiple triangles with different transforms

### Phase 3: Multiple Primitive Types
- Add point rendering (stars)
- Add line rendering (constellations)
- Update shaders for each primitive type

## Testing

### Unit Tests
- Matrix multiplication correctness
- Vertex packing/unpacking
- DrawBatch equality

### Integration Tests
- Renderer lifecycle (init → render → release)
- Frame sequencing (begin → draw → end)
- Resize handling

## Related Specifications

- [pipeline.md](pipeline.md) - Current rendering pipeline
- [primitives.md](primitives.md) - Primitive type details
- [../blueprint/rendering-abstraction.md](../blueprint/rendering-abstraction.md) - Target architecture
