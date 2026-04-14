# Data Model: Gallery Rewrite

## Overview

No new persistent data model is introduced. The gallery derives its item list directly from
`ObjectInfoRegistry` at runtime. The only new runtime type is `GalleryItem`, a lightweight view
model that wraps the fields needed by the grid adapter.

## GalleryItem (new — view model only)

```kotlin
data class GalleryItem(
    val id: String,          // object_info.json key, e.g. "m31", "orion"
    val name: String,        // Localised display name, e.g. "Andromeda Galaxy"
    val imagePath: String,   // Relative asset path, e.g. "celestial_images/deep_sky_objects/m31.webp"
    val imageCredit: String  // Attribution string, e.g. "NASA/ESA/Hubble"
)
```

**Population**: Derived by filtering `ObjectInfoRegistry.getAllObjects()` to entries where
`imagePath != null`, then mapping to `GalleryItem`. Optional secondary filter by `ObjectInfo.type`
for curating to a subset.

**Not stored**: No Room database, no SharedPreferences, no files. Gallery items are constructed
fresh each time `ImageGalleryActivity` starts (the registry is already in memory as a singleton).

## Existing types used unchanged

### ObjectInfo (education/ObjectInfo.kt)

```kotlin
data class ObjectInfo(
    val id: String,
    val name: String,
    val description: String,
    val funFact: String?,
    val type: ObjectType,
    val distance: String?,
    val size: String?,
    val mass: String?,
    val spectralClass: String?,
    val magnitude: String?,
    val imagePath: String?,   // null if no image available
    val imageCredit: String?
) : Parcelable
```

Used as the argument to `ObjectInfoDialogFragment`. Passed via Bundle (already Parcelable).

### ObjectType (education/ObjectInfoRegistry.kt)

```kotlin
enum class ObjectType {
    PLANET, STAR, MOON, DWARF_PLANET, NEBULA, GALAXY, CLUSTER, CONSTELLATION
}
```

Can be used to filter gallery items by type (e.g. exclude `CONSTELLATION` if desired).

## Data flow

```
object_info.json (asset)
    ↓  loaded once at startup
ObjectInfoRegistry (singleton, Application scope)
    ↓  getAllObjects().filter { it.imagePath != null }
List<GalleryItem>  (constructed in ImageGalleryActivity.onCreate)
    ↓  passed to
GalleryAdapter (RecyclerView.Adapter)
    ↓  per ViewHolder, async
AssetImageLoader  (LRU cache, 2-thread pool)
    ↓  bitmap callback
ImageView in gallery_thumbnail_item.xml
    ↓  user tap
ObjectInfoDialogFragment (existing, receives ObjectInfo)
    ↓  user taps "Find"
OnFindClickedListener (implemented by hosting Activity)
```

## ObjectInfoDialogFragment callback interface (new)

```kotlin
// Added to ObjectInfoDialogFragment
interface OnFindClickedListener {
    fun onFindClicked(info: ObjectInfo)
}
```

**DynamicStarMapActivity** implements `OnFindClickedListener`:
- Calls existing search infrastructure (same as `ObjectInfoTapHandler` flow)
- Dismisses the dialog

**ImageGalleryActivity** implements `OnFindClickedListener`:
- Ensures relevant map layers are enabled in preferences
- Fires `Intent(this, DynamicStarMapActivity::class.java)` with
  `action = Intent.ACTION_SEARCH` and `putExtra(SearchManager.QUERY, info.name)`
- Calls `startActivity(intent)`
