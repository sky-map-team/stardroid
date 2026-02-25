# Vulkan Demo Bootstrap Specification

## Purpose

Defines the minimal Android Vulkan application that serves as the foundation for the Stardroid Awakening project. This is the "hello world" that proves Vulkan works on target devices before investing in the full rendering pipeline.

## Goal

**Render a colored triangle on an Android device using Vulkan and Kotlin.**

This simple goal validates:
- Android project structure works
- Build tooling is correctly configured
- Vulkan initialization succeeds on target devices
- Basic rendering pipeline functions

## Acceptance Criteria

- [ ] Project builds successfully with `./gradlew assembleDebug`
- [ ] APK installs on Android 8.0+ device with Vulkan 1.1 support
- [ ] App displays a rotating colored triangle on screen
- [ ] No crashes or validation errors in logcat
- [ ] Frame rate stable at 60 FPS

## Project Structure

```
stardroidawakening/
├── app/
│   ├── build.gradle.kts           # App module build config
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── kotlin/
│           │   └── com/stardroid/awakening/
│           │       ├── MainActivity.kt
│           │       ├── VulkanSurfaceView.kt
│           │       └── vulkan/
│           │           ├── VulkanRenderer.kt
│           │           ├── VulkanContext.kt
│           │           └── VulkanPipeline.kt
│           ├── cpp/
│           │   ├── CMakeLists.txt
│           │   ├── vulkan_wrapper.cpp
│           │   └── vulkan_wrapper.h
│           ├── shaders/
│           │   ├── triangle.vert
│           │   └── triangle.frag
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               └── values/
│                   ├── strings.xml
│                   └── themes.xml
├── build.gradle.kts               # Root build config
├── settings.gradle.kts            # Module settings
├── gradle.properties              # Build properties
└── gradle/
    └── libs.versions.toml         # Version catalog
```

## Build Configuration

### gradle/libs.versions.toml

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
coreKtx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### build.gradle.kts (root)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StardroidAwakening"
include(":app")
```

### app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.stardroid.awakening"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stardroid.awakening"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
```

### gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

## Vulkan Architecture

### Initialization Sequence

```
1. Create VkInstance
   ├── Enable validation layers (debug builds)
   └── Enable required extensions (VK_KHR_surface, VK_KHR_android_surface)

2. Create VkSurfaceKHR from Android Surface

3. Select VkPhysicalDevice
   ├── Enumerate available devices
   └── Select device with graphics + present queue support

4. Create VkDevice
   ├── Enable required device extensions (VK_KHR_swapchain)
   └── Create graphics queue

5. Create Swapchain
   ├── Query surface capabilities
   ├── Select format (BGRA8_UNORM preferred)
   ├── Select present mode (FIFO for vsync, MAILBOX for low latency)
   └── Create swapchain images and views

6. Create Render Pass
   └── Single color attachment (swapchain format)

7. Create Graphics Pipeline
   ├── Load SPIR-V shaders
   ├── Configure vertex input (position, color)
   ├── Configure viewport and scissor
   └── Create pipeline layout

8. Create Command Buffers
   └── One per swapchain image

9. Create Synchronization Objects
   ├── Semaphores: imageAvailable, renderFinished
   └── Fences: inFlight (per frame)
```

### Render Loop

```
Per Frame:
1. Wait for previous frame fence
2. Acquire next swapchain image
3. Reset command buffer
4. Record commands:
   a. Begin render pass
   b. Bind pipeline
   c. Bind vertex buffer
   d. Update push constants (rotation angle)
   e. Draw triangle
   f. End render pass
5. Submit command buffer
6. Present image
```

## Shader Specifications

### triangle.vert

```glsl
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

layout(push_constant) uniform PushConstants {
    mat4 transform;
} pc;

void main() {
    gl_Position = pc.transform * vec4(inPosition, 0.0, 1.0);
    fragColor = inColor;
}
```

### triangle.frag

```glsl
#version 450

layout(location = 0) in vec3 fragColor;
layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
```

### Shader Compilation

Shaders must be compiled to SPIR-V at build time:

```cmake
# In CMakeLists.txt
find_program(GLSLC glslc HINTS $ENV{VULKAN_SDK}/bin)

add_custom_command(
    OUTPUT triangle_vert.spv
    COMMAND ${GLSLC} -fshader-stage=vertex
            ${CMAKE_SOURCE_DIR}/../shaders/triangle.vert
            -o ${CMAKE_BINARY_DIR}/triangle_vert.spv
    DEPENDS ${CMAKE_SOURCE_DIR}/../shaders/triangle.vert
)

add_custom_command(
    OUTPUT triangle_frag.spv
    COMMAND ${GLSLC} -fshader-stage=fragment
            ${CMAKE_SOURCE_DIR}/../shaders/triangle.frag
            -o ${CMAKE_BINARY_DIR}/triangle_frag.spv
    DEPENDS ${CMAKE_SOURCE_DIR}/../shaders/triangle.frag
)
```

## Native Code (C++)

### Why C++ for Vulkan?

While the app is primarily Kotlin, Vulkan initialization and rendering uses C++ because:
1. Vulkan SDK provides C headers, easier to use from C++
2. Better performance for render loop
3. More examples and resources available in C++
4. JNI overhead minimal (one call per frame)

### JNI Interface

```cpp
// vulkan_wrapper.h
extern "C" {
    JNIEXPORT jlong JNICALL
    Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeInit(
        JNIEnv* env, jobject obj, jobject surface);

    JNIEXPORT void JNICALL
    Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeRender(
        JNIEnv* env, jobject obj, jlong contextHandle, jfloat angle);

    JNIEXPORT void JNICALL
    Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeResize(
        JNIEnv* env, jobject obj, jlong contextHandle, jint width, jint height);

    JNIEXPORT void JNICALL
    Java_com_stardroid_awakening_vulkan_VulkanRenderer_nativeDestroy(
        JNIEnv* env, jobject obj, jlong contextHandle);
}
```

## Kotlin Components

### MainActivity.kt

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vulkanSurfaceView = VulkanSurfaceView(this)
        setContentView(vulkanSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        vulkanSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        vulkanSurfaceView.onPause()
    }
}
```

### VulkanSurfaceView.kt

```kotlin
class VulkanSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val renderer = VulkanRenderer()
    private var renderThread: Thread? = null
    private var rendering = false

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderer.init(holder.surface)
        startRenderLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderLoop()
        renderer.destroy()
    }

    private fun startRenderLoop() {
        rendering = true
        renderThread = Thread {
            var angle = 0f
            while (rendering) {
                renderer.render(angle)
                angle += 1f
                if (angle >= 360f) angle = 0f
            }
        }.also { it.start() }
    }

    private fun stopRenderLoop() {
        rendering = false
        renderThread?.join()
    }
}
```

### VulkanRenderer.kt

```kotlin
class VulkanRenderer {
    private var nativeContext: Long = 0

    init {
        System.loadLibrary("vulkan_wrapper")
    }

    fun init(surface: Surface) {
        nativeContext = nativeInit(surface)
    }

    fun render(angle: Float) {
        if (nativeContext != 0L) {
            nativeRender(nativeContext, angle)
        }
    }

    fun resize(width: Int, height: Int) {
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
        }
    }

    fun destroy() {
        if (nativeContext != 0L) {
            nativeDestroy(nativeContext)
            nativeContext = 0
        }
    }

    private external fun nativeInit(surface: Surface): Long
    private external fun nativeRender(context: Long, angle: Float)
    private external fun nativeResize(context: Long, width: Int, height: Int)
    private external fun nativeDestroy(context: Long)
}
```

## Implementation Checklist

### Phase 1: Project Setup
- [ ] Create project directory structure
- [ ] Create gradle wrapper
- [ ] Create version catalog (libs.versions.toml)
- [ ] Create root build.gradle.kts
- [ ] Create settings.gradle.kts
- [ ] Create gradle.properties
- [ ] Create app/build.gradle.kts
- [ ] Create AndroidManifest.xml
- [ ] Verify project builds with `./gradlew assembleDebug`

### Phase 2: Basic Android App
- [ ] Create MainActivity.kt
- [ ] Create activity_main.xml layout
- [ ] Create themes.xml
- [ ] Create strings.xml
- [ ] Verify app launches on device (blank screen OK)

### Phase 3: Vulkan Surface Setup
- [ ] Create VulkanSurfaceView.kt
- [ ] Create VulkanRenderer.kt (JNI stubs)
- [ ] Update MainActivity to use VulkanSurfaceView
- [ ] Verify surface callbacks work

### Phase 4: Native Vulkan Initialization
- [ ] Create CMakeLists.txt
- [ ] Create vulkan_wrapper.cpp
- [ ] Implement VkInstance creation
- [ ] Implement VkSurfaceKHR creation
- [ ] Implement VkDevice selection and creation
- [ ] Verify initialization succeeds (logcat)

### Phase 5: Swapchain and Render Pass
- [ ] Implement swapchain creation
- [ ] Implement render pass creation
- [ ] Implement framebuffer creation
- [ ] Handle surface resize

### Phase 6: Graphics Pipeline
- [ ] Create triangle.vert shader
- [ ] Create triangle.frag shader
- [ ] Add shader compilation to CMake
- [ ] Load SPIR-V shaders in C++
- [ ] Create graphics pipeline

### Phase 7: Rendering
- [ ] Create vertex buffer (triangle vertices)
- [ ] Create command buffers
- [ ] Create synchronization objects
- [ ] Implement render loop
- [ ] Draw static triangle

### Phase 8: Animation
- [ ] Add push constants for transform
- [ ] Implement rotation matrix
- [ ] Animate triangle rotation

### Phase 9: Polish
- [ ] Add validation layer support (debug builds)
- [ ] Handle Vulkan errors gracefully
- [ ] Test on multiple devices
- [ ] Measure and verify 60 FPS

## Error Handling

### Vulkan Validation Layers

Enable in debug builds:
```cpp
const std::vector<const char*> validationLayers = {
    "VK_LAYER_KHRONOS_validation"
};

#ifdef NDEBUG
const bool enableValidationLayers = false;
#else
const bool enableValidationLayers = true;
#endif
```

### Device Compatibility Check

```kotlin
fun hasVulkanSupport(context: Context): Boolean {
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1)
}
```

### Graceful Fallback

If Vulkan initialization fails:
1. Log detailed error to logcat
2. Show user-friendly error message
3. (Future: Fall back to OpenGL ES)

## Testing

### Manual Testing Checklist

- [ ] App installs without errors
- [ ] App launches to triangle display
- [ ] Triangle rotates smoothly
- [ ] No visual artifacts
- [ ] No logcat errors/warnings
- [ ] App handles rotation (portrait/landscape)
- [ ] App handles backgrounding/resuming
- [ ] App handles minimize/restore

### Device Testing Matrix

| Device | Android | Vulkan | Status |
|--------|---------|--------|--------|
| Pixel 6 | 14 | 1.3 | TBD |
| Pixel 4a | 13 | 1.1 | TBD |
| Samsung S21 | 13 | 1.1 | TBD |

## Success Metrics

1. **Build Time**: < 2 minutes for clean build
2. **APK Size**: < 5 MB
3. **Startup Time**: < 1 second to triangle display
4. **Frame Rate**: Stable 60 FPS
5. **Battery**: < 5% per hour of active use

## Next Steps After Demo

Once the Vulkan demo is working:
1. **Rendering Abstraction**: Define RendererInterface per specs/blueprint/rendering-abstraction.md
2. **Star Points**: Replace triangle with point rendering for stars
3. **Compute Shader**: Implement star compute shader per specs/blueprint/ar-vulkan-target.md

## Related Specifications

- [../blueprint/ar-vulkan-target.md](../blueprint/ar-vulkan-target.md) - Target Vulkan architecture
- [../blueprint/rendering-abstraction.md](../blueprint/rendering-abstraction.md) - Renderer interface design
- [../blueprint/migration-roadmap.md](../blueprint/migration-roadmap.md) - Overall migration plan
