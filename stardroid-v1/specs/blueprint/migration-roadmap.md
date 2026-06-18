# Migration Roadmap - Current to Target Architecture

> **FUTURE STATE SPECIFICATION** — This is a planning document for a potential future migration to Vulkan/AR rendering. It does not reflect the current state of the codebase. Phases 1–4 described here have not been implemented.

## Purpose

Provides a **proposed step-by-step migration path** from the current OpenGL implementation to a target AR/Vulkan-capable architecture, while maintaining backward compatibility and minimizing disruption.

## Migration Philosophy

**Principles:**
1. **Evolutionary, not revolutionary** - Incremental changes, each tested and stable
2. **Never break existing functionality** - Current features must keep working
3. **Enable new capabilities opt-in** - Users choose when to adopt new features
4. **Parallel implementation** - New code alongside old, gradual cutover
5. **Fallback always available** - If new tech doesn't work, use old

## Migration Phases Overview

```
Current → Phase 1 → Phase 2 → Phase 3 → Target
(OpenGL) (Abstract) (Vulkan) (AR)     (AR/Vulkan)
   │         │         │        │           │
   │         │         │        │           └─ Full AR mode
   │         │         │        └─ Vulkan backend + OpenGL fallback
   │         │         └─ Material 3 UI
   │         └─ Rendering abstraction layer
   └─ Working OpenGL app
```

## Phase 0: Prerequisites (Current State)

**Goal:** Ensure codebase is ready for migration

**Tasks:**
- ✅ Complete Hilt migration (DI cleanup)
- ✅ Modernize build system (Gradle 9.1, Kotlin 2.0.20)
- ✅ Fix AndroidX migration issues
- ⏳ Add comprehensive unit tests for core domain
- ⏳ Add integration tests for rendering

**Acceptance Criteria:**
- All tests pass
- Build is reproducible
- No deprecated APIs used (except where necessary)

**Estimated Effort:** 1 week (mostly testing work)

## Phase 1: Rendering Abstraction Layer

**Goal:** Extract rendering interfaces so OpenGL is one implementation

**Duration:** 3-4 weeks

### Step 1.1: Define Core Interfaces (1 week)

**Create interfaces:**
- `RendererInterface` - Main rendering contract
- `ManagerInterface<T>` - Object manager contract
- `ShaderInterface` - Shader abstraction
- `TextureManager` - Texture loading abstraction
- `BufferManager` - Buffer management abstraction

**Files to create:**
```
com/google/android/stardroid/renderer/
├── RendererInterface.kt
├── managers/
│   ├── ManagerInterface.kt
│   ├── PointObjectManager.kt (refactor existing)
│   ├── LineObjectManager.kt (refactor existing)
│   ├── LabelObjectManager.kt (refactor existing)
│   └── ImageObjectManager.kt (refactor existing)
├── shaders/
│   └── ShaderInterface.kt
└── resources/
    ├── TextureManager.kt
    └── BufferManager.kt
```

**Testing:**
- Unit tests for each interface
- Mock implementations for testing
- Integration tests with existing OpenGL code

### Step 1.2: Refactor OpenGL Code (1-2 weeks)

**Refactor existing classes to implement interfaces:**
- `SkyRenderer` → Implements `RendererInterface`
- Object managers → Implement `ManagerInterface<T>`
- Keep existing OpenGL implementation unchanged
- Just extract to interfaces

**Key changes:**
```java
// Before
public class SkyRenderer {
    public void render(OpenGLState gl, List<CelestialObject> objects) { ... }
}

// After
public class SkyRenderer implements RendererInterface {
    @Override
    public void submitFrame(List<CelestialObject> objects, CameraView view) { ... }
}
```

**No functional changes** - just interface extraction

### Step 1.3: Add Factory Pattern (1 week)

**Renderer factory based on capabilities:**
```kotlin
object RendererFactory {
    fun createRenderer(context: Context): RendererInterface {
        return OpenGLRenderer(context)  // Only implementation for now
    }
}
```

**Benefits:**
- Easy to add VulkanRenderer later
- Runtime renderer selection
- Testable with mock renderers

**Acceptance Criteria:**
- All existing tests pass
- App looks and behaves identically
- New interfaces documented
- Code compiles without warnings

**Risk Assessment:** **Low**
- No functional changes
- Pure refactoring
- Can revert if needed

## Phase 2: Material 3 UI Modernization

**Goal:** Modernize UI layer with Material Design 3

**Duration:** 2-3 weeks

### Step 2.1: Theme System (3 days)

**Tasks:**
- Add Material Components dependency
- Create Material 3 theme in `themes.xml`
- Implement dynamic color extraction
- Update `styles.xml` to reference Material 3

**Key files:**
- `build.gradle` - Add `com.google.android.material:material:1.12.0`
- `res/values/themes.xml` - Create with Material 3 theme
- `com/google/android/stardroid/ui/DynamicColorExtractor.kt`

### Step 2.2: Splash Screen (1 week)

**Tasks:**
- Create astronomy-themed splash with starfield animation
- Implement `StarfieldView` with twinkling animation
- Update `SplashScreenActivity` with animation logic

**Key files:**
- `res/layout/splash_material3.xml`
- `com/stardroid/awakening/ui/StarfieldView.kt`
- `SplashScreenActivity.kt` - Add animation handling

### Step 2.3: Bottom Sheet Dialogs (1 week)

**Tasks:**
- Convert EULA to bottom sheet with native views
- Convert What's New to bottom sheet
- Replace WebView HTML with TextViews

**Key files:**
- `res/layout/eula_bottom_sheet.xml`
- `res/layout/whats_new_bottom_sheet.xml`
- `com/google/android/stardroid/activities/dialogs/EulaBottomSheetFragment.kt`
- `com/google/android/stardroid/activities/dialogs/WhatsNewBottomSheetFragment.kt`

**Acceptance Criteria:**
- Modern Material 3 appearance
- All dialogs functional
- Night mode preserved
- Accessibility improvements

**Risk Assessment:** **Low-Medium**
- UI changes visible to users
- Backward compatible (can revert splash/dialogs)
- Test thoroughly on different screen sizes

## Phase 3: Vulkan Backend

**Goal:** Add Vulkan as optional rendering backend

**Duration:** 6-8 weeks

### Step 3.1: Vulkan Setup (1 week)

**Tasks:**
- Add Vulkan dependencies to build.gradle
- Create Vulkan renderer skeleton
- Implement Vulkan initialization
- Add device capability detection

**Key files:**
- `build.gradle` - Add Vulkan validation layers
- `com/google/android/stardroid/renderer/VulkanRenderer.kt`
- `com/google/android/stardroid/renderer/vulkan/*`

**Dependencies:**
```gradle
implementation 'com.google.vulkan:vulkan-android:1.4.0'
```

### Step 3.2: Star Compute Shader (2 weeks)

**Tasks:**
- Design compute shader for star rendering
- Implement buffer management (star data)
- Create descriptor set layouts
- Add render graph for compute → graphics

**Key shaders:**
- `shaders/stars.comp.glsl` - Star rendering compute shader
- `shaders/vert.glsl` - Vertex shader (lines, text)
- `shaders/frag.glsl` - Fragment shader

### Step 3.3: Graphics Pipeline (2 weeks)

**Tasks:**
- Implement Vulkan graphics pipeline
- Add line rendering (constellations)
- Add text rendering (labels)
- Add image rendering (planets)

**Key files:**
- `com/google/android/stardroid/renderer/vulkan/VulkanPipeline.kt`
- `com/google/android/stardroid/renderer/vulkan/VulkanCommandBuffer.kt`

### Step 3.4: Runtime Selection (1 week)

**Tasks:**
- Update `RendererFactory` to detect Vulkan support
- Add settings preference for renderer selection
- Implement fallback to OpenGL if Vulkan fails
- Add performance metrics collection

**Implementation:**
```kotlin
object RendererFactory {
    fun createRenderer(context: Context): RendererInterface {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val userChoice = prefs.getString("renderer", "auto")

        return when {
            userChoice == "vulkan" && hasVulkanSupport() -> VulkanRenderer(context)
            userChoice == "opengl" -> OpenGLRenderer(context)
            hasVulkanSupport() -> VulkanRenderer(context)  // Auto: prefer Vulkan
            else -> OpenGLRenderer(context)
        }
    }
}
```

### Step 3.5: Performance Optimization (2 weeks)

**Tasks:**
- Profile Vulkan renderer
- Optimize compute shader
- Add secondary command buffers
- Implement frustum culling
- Add LOD for distant objects

**Benchmarks:**
- Target: 60 FPS on Adreno 540+ devices
- Memory: < 100MB GPU memory
- Battery: < 5% per hour use

**Acceptance Criteria:**
- Vulkan renderer matches OpenGL visual quality
- Performance improvement: 1.5-2x faster
- Fallback works seamlessly
- No regressions in OpenGL mode

**Risk Assessment:** **Medium**
- Vulkan is complex, easy to get wrong
- Device compatibility issues
- Need extensive testing
- Have OpenGL fallback

## Phase 4: AR Mode

**Goal:** Add AR mode as optional feature

**Duration:** 8-12 weeks

### Step 4.1: ARCore Integration (2 weeks)

**Tasks:**
- Add ARCore dependency
- Implement AR session management
- Add camera permission handling
- Create AR background rendering

**Dependencies:**
```gradle
implementation 'com.google.ar:core:1.42.0'
```

**Key files:**
- `com/google/android/stardroid/ar/ARSessionManager.kt`
- `com/google/android/stardroid/ar/ARRenderer.kt`

### Step 4.2: Coordinate Alignment (2 weeks)

**Tasks:**
- Implement AR world → celestial coordinates transform
- Add GPS → AR world alignment
- Implement altitude correction
- Test coordinate accuracy

**Key algorithm:**
```kotlin
// See ar-vulkan-target.md for details
fun celestialToAR(ra: Float, dec: Float, observer: LatLonAlt): Vector3
```

### Step 4.3: 3D Celestial Objects (3 weeks)

**Tasks:**
- Render stars as 3D spheres with glow
- Render planets as textured spheres with phase
- Create constellation lines in 3D space
- Add floating labels in 3D

**Visual design:**
- Stars: Glowing spheres, size based on magnitude
- Planets: Textured spheres, proper rotation
- Constellations: Line art in 3D space above user

### Step 4.4: Depth and Occlusion (2 weeks)

**Tasks:**
- Integrate ARCore depth API
- Implement occlusion (real objects block stars)
- Add ground plane detection
- Handle edge cases (no depth available)

### Step 4.5: AR Polish (3 weeks)

**Tasks:**
- Smooth transitions between AR and map modes
- Performance optimization (battery, thermal)
- User education (how to use AR mode)
- Beta testing and feedback

**AR Mode UI:**
- Toggle button: Switch between map/AR
- Calibration UI: Help user align phone
- Info display: Show AR accuracy, tracking quality

**Acceptance Criteria:**
- AR mode works on ARCore-compatible devices
- Coordinate accuracy: ±2° (good enough for casual use)
- Performance: 30 FPS minimum, 60 FPS target
- Fallback to map mode on incompatible devices

**Risk Assessment:** **High**
- AR is complex, device-dependent
- Coordinate alignment tricky
- Battery drain concerns
- Need extensive real-world testing

## Migration Timeline

```
Phase 0: Prerequisites          [Week 1]
Phase 1: Rendering Abstraction   [Weeks 2-5]
Phase 2: Material 3 UI            [Weeks 6-8]
Phase 3: Vulkan Backend          [Weeks 9-16]
Phase 4: AR Mode                 [Weeks 17-28]

Total: ~28 weeks (7 months) with full-time effort
```

**Parallelization opportunities:**
- Phase 2 (UI) and Phase 3 (Vulkan) can overlap
- Phase 4 (AR) can start while Phase 3 completes

## Testing Strategy

### Per Phase Testing

**Phase 0 (Prerequisites):**
- Unit test coverage > 80%
- Integration tests for core domain
- Performance benchmarks established

**Phase 1 (Abstraction):**
- All existing tests still pass
- New interfaces have contract tests
- Mock renderers for testing

**Phase 2 (Material 3):**
- UI tests for new dialogs
- Accessibility tests
- Night mode regression tests
- Screen size compatibility tests

**Phase 3 (Vulkan):**
- Visual comparison tests (OpenGL vs Vulkan)
- Performance benchmarks
- Memory leak tests
- Device compatibility tests

**Phase 4 (AR):**
- Coordinate accuracy tests
- Frame rate tests
- Battery drain tests
- Real-world usability tests

### Continuous Integration

**Automated tests:**
- Run on every commit
- Cover critical paths
- Performance regression detection

**Manual tests:**
- Weekly smoke tests on real devices
- Beta testing for new features
- Community feedback loops

## Rollback Strategy

Each phase is independently revertible:

**Phase 1:** Revert interface extraction, keep old OpenGL code
**Phase 2:** Revert to old splash/dialogs, keep Material 3 theme
**Phase 3:** Disable Vulkan renderer, use OpenGL only
**Phase 4:** Remove AR mode, keep map mode

**Feature flags:**
```kotlin
// BuildConfig flags
val ENABLE_VULKAN = BuildConfig.ENABLE_VULKAN
val ENABLE_AR = BuildConfig.ENABLE_AR

// Runtime preferences
val useVulkan = prefs.getBoolean("use_vulkan", false)
val useAR = prefs.getBoolean("use_ar", false)
```

## Success Criteria

**Phase 1 Success:**
- Interfaces defined and documented
- OpenGL code refactored cleanly
- No functional regressions

**Phase 2 Success:**
- Modern Material 3 appearance
- All dialogs functional
- User satisfaction improved

**Phase 3 Success:**
- Vulkan renderer matches OpenGL quality
- Performance improved 1.5-2x
- Device compatibility > 90%

**Phase 4 Success:**
- AR mode works on compatible devices
- Coordinate accuracy acceptable
- Performance acceptable for battery

## Related Specifications

- [core-domain.md](core-domain.md) - Core domain remains stable through migration
- [rendering-abstraction.md](rendering-abstraction.md) - Interfaces defined in Phase 1
- [ar-vulkan-target.md](ar-vulkan-target.md) - Target architecture for Phases 3-4
