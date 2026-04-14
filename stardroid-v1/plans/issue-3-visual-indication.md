# Issue 3: Visual Indication of Objects with Info Cards

## Summary

Modify the sky map to visually differentiate labels of objects that have info cards available, so users know they can tap for more information.

## Maintainer Note

> *"This probably won't be needed if we're going to provide cards for pretty much everything that has a label."*
> -- John

**Recommendation**: Implement this **after** Issue 1 is complete. If all labeled objects end up having info cards, this visual indicator becomes unnecessary. Evaluate the gap after Issue 1 is done before investing effort here.

## Current State

- All labels are rendered identically via `LabelObjectManager` using `TextPrimitive`
- `TextPrimitive` properties: `label` (String), `color` (int ARGB), `fontSize` (int), `offset` (float)
- Labels are rendered as **OpenGL texture quads** - no underline, no bold/italic, no text decorations at the GL level
- Colors already encode object type semantics (white for stars, colored for constellations, etc.)
- `ObjectInfoRegistry.hasInfo(objectId)` exists and can check if a card is available
- The rendering pipeline: `TextPrimitive` -> `LabelObjectManager` -> GL texture -> screen

## Acceptance Criteria

- [ ] Labels of objects with info cards are visually distinguishable from those without
- [ ] The differentiation is subtle (not distracting) but noticeable
- [ ] Works in both normal mode and night (red) mode
- [ ] Controlled by the same preference as info cards (`SHOW_OBJECT_INFO_PREF_KEY`)
- [ ] No performance regression (label rendering is hot path)
- [ ] All existing tests pass

## Approach Analysis

### Option A: Color Shift (Recommended)

Objects with info cards get a subtly modified color (brighter with a slight blue/cyan tint).

**Pros:**
- Works within existing OpenGL pipeline, no rendering architecture changes
- Subtle and professional
- Minimal code changes

**Cons:**
- Users may not immediately notice the difference
- Blue tint may conflict with night mode (red overlay cancels blue)

**Implementation:**
- Modify label color at `TextPrimitive` creation time
- Apply a brightening + tint function when `ObjectInfoRegistry.hasInfo()` is true

### Option B: Suffix Indicator

Append a small symbol to the label text: `"Orion ℹ"` or `"Orion ·"`

**Pros:**
- Very visible
- Zero rendering changes

**Cons:**
- Adds visual clutter
- Unicode character support varies across Android versions
- Changes label width (may cause overlaps)
- Doesn't feel "native"

### Option C: Font Size Variation

Objects with info cards get a slightly larger font (+2px).

**Pros:**
- Works within existing pipeline

**Cons:**
- Increases label overlap probability
- Hard to perceive as "tappable" indicator
- May look like a bug rather than intentional design

## Recommended: Option A (Color Shift)

### Technical Approach

#### Step 1: Make `ObjectInfoRegistry` Available to Renderable Constructors

The `ObjectInfoRegistry` needs to be accessible where `TextPrimitive` objects are created:

**For protobuf-based objects** (`ProtobufAstronomicalRenderable`):
- Currently created in `AbstractFileBasedLayer.initializeAstroSources()`
- `ProtobufAstronomicalRenderable` constructor receives protobuf data + resources
- Need to pass `ObjectInfoRegistry` through the layer -> renderable chain

**For planets** (`PlanetSource`):
- Created in `PlanetSource.kt` with Dagger injection
- Can inject `ObjectInfoRegistry` directly

**Injection path:**
```
ApplicationComponent
  └─ ObjectInfoRegistry (singleton)
       ├─> AbstractFileBasedLayer (inject via constructor)
       │     └─> ProtobufAstronomicalRenderable (pass as parameter)
       └─> PlanetSource (inject via constructor)
```

#### Step 2: Add Color Helper

**File: `app/src/main/java/.../education/ObjectInfoRegistry.kt`**

```kotlin
/**
 * Returns a modified color for objects that have info cards.
 * Brightens the color and adds a subtle blue/cyan tint to indicate
 * the object is "tappable" for more information.
 */
fun getInfoAwareColor(objectId: String, baseColor: Int): Int {
    if (!hasInfo(objectId)) return baseColor
    val r = min(255, Color.red(baseColor) + 25)
    val g = min(255, Color.green(baseColor) + 25)
    val b = min(255, Color.blue(baseColor) + 45)
    return Color.argb(Color.alpha(baseColor), r, g, b)
}
```

The shift values (25, 25, 45) add a noticeable but not jarring brightness with a subtle cool tint.

#### Step 3: Apply at TextPrimitive Creation

**File: `app/src/main/java/.../renderables/proto/ProtobufAstronomicalRenderable.kt`**

When creating `TextPrimitive` from `LabelElementProto`, look up the object's name and apply:

```kotlin
// In the label creation loop:
val labelColor = if (objectInfoRegistry != null) {
    objectInfoRegistry.getInfoAwareColor(objectName, proto.color)
} else {
    proto.color
}
val textPrimitive = TextPrimitive(coords, labelText, labelColor, offset, fontSize)
```

**File: Planet label creation** (wherever planet labels are built):
Same pattern - check `hasInfo()` and modify color.

#### Step 4: Gate Behind Preference

Only apply the color modification when info cards are enabled:

```kotlin
val shouldHighlight = sharedPreferences.getBoolean(
    SHOW_OBJECT_INFO_PREF_KEY, true
)
val color = if (shouldHighlight) {
    objectInfoRegistry.getInfoAwareColor(objectId, baseColor)
} else {
    baseColor
}
```

## Key Files

| File | Change |
|------|--------|
| `app/src/main/java/.../education/ObjectInfoRegistry.kt` | Add `getInfoAwareColor()` helper |
| `app/src/main/java/.../renderables/proto/ProtobufAstronomicalRenderable.kt` | Apply color shift to labels |
| `app/src/main/java/.../layers/AbstractFileBasedLayer.kt` | Pass `ObjectInfoRegistry` to renderables |
| `app/src/main/java/.../space/PlanetSource.kt` | Apply color shift to planet labels |
| Dagger modules (as needed) | Provide `ObjectInfoRegistry` injection |
| `app/src/test/java/.../education/ObjectInfoRegistryTest.kt` | Test `getInfoAwareColor()` |

## Verification

```bash
# Run unit tests
./gradlew testGmsDebugUnitTest

# Build
./gradlew assembleGmsDebug
```

Manual testing:
1. Open app with info cards enabled
2. Compare label colors: objects with info cards should look slightly brighter/cooler
3. Tap a highlighted label -> info card appears (confirms correlation)
4. Disable info cards in settings -> all labels revert to base colors
5. Enable night mode -> verify labels still distinguishable (red mode)
6. Scroll across the sky -> no performance issues with many labels on screen

## Performance Considerations

- `hasInfo()` is a `HashMap.containsKey()` call - O(1), negligible cost
- Color calculation is simple integer math - negligible cost
- Label textures are regenerated only on `UpdateType.Reset`, not every frame
- **No performance impact expected**

## Alternative: Do Nothing

If Issue 1 successfully covers every labeled object, this issue becomes moot. The maintainer himself suggested this might not be needed. Consider closing this issue if Issue 1 achieves full coverage.

## Dependencies

- Should be evaluated **after** Issue 1 (More Cards) is complete
- No hard code dependency on Issues 1 or 2
