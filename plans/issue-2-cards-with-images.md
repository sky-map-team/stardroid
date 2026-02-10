# Issue 2: Enhance Info Cards with Images

## Summary

Add images to the educational info card dialog. Prioritize reusing images already bundled in the app (21 Hubble/space agency images from the gallery, plus planet/moon icons).

## Current State

- `ObjectInfoDialogFragment` displays text-only cards via `object_info_card.xml`
- The app already bundles **21 high-quality astronomical images** in `app/src/main/res/drawable/`:
  - 8 planets: `messenger_11_07_39.jpg`, `hubble_venus_clouds_tops.jpg`, `hubble_mars.jpg`, `hubble_jupiter.jpg`, `hubble_saturn.jpg`, `hubble_uranus.jpg`, `hubble_neptune.jpg`, `nh_pluto_in_false_color.jpg`
  - 5 nebulae: `hubble_m1.jpg`, `hubble_m16.jpg`, `hubble_m57.jpg`, `hubble_orion.jpg`, `hubble_catseyenebula.jpg`
  - 5 galaxies: `kennett_m31.jpg`, `hubble_m51a.jpg`, `hubble_m101.jpg`, `hubble_m104.jpg`, `hubble_ultra_deep_field.jpg`
  - 3 clusters: `hubble_m13.jpg`, `hubble_m45.jpg`, `hubble_omegacentauri.jpg`
- Additionally, planet PNG icons exist: `sun.png`, `mercury.png`, `venus.png`, `mars.png`, `jupiter.png`, `saturn.png`, `uranus.png`, `neptune.png`, `pluto.png`
- Moon phase images: `moon0.png` through `moon7.png`
- `ObjectInfo` data class has **no image field**
- `object_info.json` has **no image reference**
- `HardcodedGallery.kt` already maps these images to search terms (reusable mapping logic)

## Acceptance Criteria

- [ ] `ObjectInfo` supports an optional image resource ID
- [ ] `object_info.json` supports an optional `imageKey` field per object
- [ ] Info card layout includes an `ImageView` that shows only when an image is available
- [ ] All 21 gallery images are mapped to their corresponding objects
- [ ] Planet icons are mapped for Sun and Moon
- [ ] Objects without images display cards normally (no blank space or broken layout)
- [ ] Night mode (red overlay) works correctly with images
- [ ] All existing tests pass

## Technical Approach

### Step 1: Add `imageResId` to `ObjectInfo` Data Model

**File: `app/src/main/java/.../education/ObjectInfo.kt`**

```kotlin
@Parcelize
data class ObjectInfo(
    val id: String,
    val name: String,
    val description: String,
    val funFact: String,
    val type: ObjectType = ObjectType.STAR,
    val distance: String? = null,
    val size: String? = null,
    val mass: String? = null,
    val spectralClass: String? = null,
    val magnitude: String? = null,
    val imageResId: Int? = null          // <-- NEW
) : Parcelable
```

**File: `app/src/main/java/.../education/ObjectInfoRegistry.kt`**

Add `imageKey` to `ObjectInfoEntry`:
```kotlin
internal data class ObjectInfoEntry(
    val nameKey: String,
    val descriptionKey: String,
    val funFactKey: String,
    val type: String = "star",
    val distanceKey: String? = null,
    val sizeKey: String? = null,
    val massKey: String? = null,
    val spectralClass: String? = null,
    val magnitude: String? = null,
    val imageKey: String? = null         // <-- NEW
)
```

In `parseJson()`, add:
```kotlin
imageKey = obj.optString("imageKey", null)
```

In `getInfo()`, resolve to resource ID:
```kotlin
val imageResId = entry.imageKey?.let {
    resources.getIdentifier(it, "drawable", packageName).takeIf { id -> id != 0 }
}
```

### Step 2: Update Info Card Layout

**File: `app/src/main/res/layout/object_info_card.xml`**

Add `ImageView` between name and description:
```xml
<ImageView
    android:id="@+id/object_info_image"
    android:layout_width="match_parent"
    android:layout_height="180dp"
    android:layout_marginTop="12dp"
    android:scaleType="centerCrop"
    android:adjustViewBounds="true"
    android:visibility="gone" />
```

Key design decisions:
- `visibility="gone"` - no space reserved when no image exists
- `scaleType="centerCrop"` - fills the area cleanly
- Fixed height `180dp` - consistent card proportions
- Positioned after the title, before description

### Step 3: Update Dialog Fragment

**File: `app/src/main/java/.../activities/dialogs/ObjectInfoDialogFragment.kt`**

After populating the name field, add:
```kotlin
if (info.imageResId != null && info.imageResId != 0) {
    val imageView = view.findViewById<ImageView>(R.id.object_info_image)
    imageView.setImageResource(info.imageResId)
    imageView.visibility = View.VISIBLE
}
```

### Step 4: Add Image Mappings to `object_info.json`

Objects that already have images in the app:

| Object ID | imageKey | Source |
|-----------|----------|--------|
| `sun` | `sun` | sun.png icon |
| `moon` | `moon0` | Full moon icon |
| `mercury` | `messenger_11_07_39` | MESSENGER spacecraft |
| `venus` | `hubble_venus_clouds_tops` | Hubble |
| `mars` | `hubble_mars` | Hubble |
| `jupiter` | `hubble_jupiter` | Hubble |
| `saturn` | `hubble_saturn` | Hubble |
| `uranus` | `hubble_uranus` | Hubble |
| `neptune` | `hubble_neptune` | Hubble |
| `pluto` | `nh_pluto_in_false_color` | New Horizons |
| `m1` | `hubble_m1` | Crab Nebula |
| `m13` | `hubble_m13` | Hercules GC |
| `m16` | `hubble_m16` | Eagle Nebula |
| `m31` | `kennett_m31` | Andromeda Galaxy |
| `m45` | `hubble_m45` | Pleiades |
| `m51` | `hubble_m51a` | Whirlpool Galaxy |
| `m57` | `hubble_m57` | Ring Nebula |
| `m101` | `hubble_m101` | Pinwheel Galaxy |
| `m104` | `hubble_m104` | Sombrero Galaxy |
| `m42` | `hubble_orion` | Orion Nebula |

**Total: 20 objects get images with zero new assets**

Example JSON entry:
```json
"mars": {
  "nameKey": "mars",
  "descriptionKey": "object_info_mars_description",
  "funFactKey": "object_info_mars_funfact",
  "type": "planet",
  "distanceKey": "object_info_mars_distance",
  "sizeKey": "object_info_mars_size",
  "massKey": "object_info_mars_mass",
  "imageKey": "hubble_mars"
}
```

## Key Files

| File | Change |
|------|--------|
| `app/src/main/java/.../education/ObjectInfo.kt` | Add `imageResId` field |
| `app/src/main/java/.../education/ObjectInfoRegistry.kt` | Parse `imageKey`, resolve to resource ID |
| `app/src/main/res/layout/object_info_card.xml` | Add `ImageView` element |
| `app/src/main/java/.../activities/dialogs/ObjectInfoDialogFragment.kt` | Populate image |
| `app/src/main/assets/object_info.json` | Add `imageKey` to 20 objects |
| `app/src/test/java/.../education/ObjectInfoRegistryTest.kt` | Test image resolution |
| `app/src/test/java/.../education/ObjectInfoTest.kt` | Test Parcelable with image field |

## Verification

```bash
# Run unit tests
./gradlew testGmsDebugUnitTest

# Build to catch resource/layout errors
./gradlew assembleGmsDebug
```

Manual testing:
1. Tap Mars -> card shows Hubble Mars image at top, then description below
2. Tap Orion Nebula (M42) -> card shows Hubble Orion image
3. Tap a star with no image (e.g., Sirius) -> card displays normally without image, no blank gap
4. Tap Sun -> card shows Sun icon
5. Scroll through card -> image stays at top, content scrolls beneath
6. Enable night mode -> verify image is not broken by red overlay

## Future Enhancements (out of scope)

- Add more images for stars, constellations (requires new assets or downloads)
- Thumbnail caching for performance
- Image attribution/credit text below image
- Pinch-to-zoom on the card image

## Dependencies

- Builds on Issue 1 (more objects = more candidates for images)
- No code dependencies - can be developed independently
