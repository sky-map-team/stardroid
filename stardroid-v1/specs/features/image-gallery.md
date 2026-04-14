# Image Gallery Feature

Sky Map includes a gallery of astronomical photographs linked to deep-sky objects.

## Overview

The image gallery allows users to browse photographs of galaxies, nebulae, and other celestial objects. Selecting an image navigates to that object in the star map.

## User Interface

### Gallery Grid

```
┌────────────────────────────────────────┐
│  [←]  Image Gallery                    │
├────────────────────────────────────────┤
│  ┌────────┐  ┌────────┐  ┌────────┐   │
│  │ M31    │  │ M42    │  │ M45    │   │
│  │[image] │  │[image] │  │[image] │   │
│  │Androme.│  │Orion N.│  │Pleiades│   │
│  └────────┘  └────────┘  └────────┘   │
│  ┌────────┐  ┌────────┐  ┌────────┐   │
│  │ M51    │  │ M57    │  │ M104   │   │
│  │[image] │  │[image] │  │[image] │   │
│  │Whirlp. │  │Ring N. │  │Sombrero│   │
│  └────────┘  └────────┘  └────────┘   │
│                                        │
│            [More images...]            │
└────────────────────────────────────────┘
```

### Full Image View

```
┌────────────────────────────────────────┐
│  [←]  M31 - Andromeda Galaxy          │
├────────────────────────────────────────┤
│                                        │
│                                        │
│           [Full-size image]            │
│                                        │
│                                        │
├────────────────────────────────────────┤
│  The Andromeda Galaxy is a spiral     │
│  galaxy approximately 2.5 million     │
│  light-years from Earth...            │
│                                        │
│         [Find in Sky Map]             │
└────────────────────────────────────────┘
```

## Implementation

### ImageGalleryActivity

Displays a grid of image thumbnails:

```kotlin
class ImageGalleryActivity : Activity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_gallery)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = GalleryAdapter(getGalleryItems()) { item ->
            onImageSelected(item)
        }
        recyclerView.adapter = adapter
    }

    private fun onImageSelected(item: GalleryItem) {
        val intent = Intent(this, ImageDisplayActivity::class.java)
        intent.putExtra(EXTRA_IMAGE_ID, item.id)
        startActivity(intent)
    }
}
```

### ImageDisplayActivity

Shows full-size image with details:

```kotlin
class ImageDisplayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_display)

        val imageId = intent.getIntExtra(EXTRA_IMAGE_ID, -1)
        val item = getGalleryItem(imageId)

        imageView.setImageResource(item.imageResource)
        titleText.text = item.title
        descriptionText.text = item.description

        findButton.setOnClickListener {
            searchForObject(item.searchName)
        }
    }

    private fun searchForObject(name: String) {
        val intent = Intent(this, DynamicStarMapActivity::class.java)
        intent.action = Intent.ACTION_SEARCH
        intent.putExtra(SearchManager.QUERY, name)
        startActivity(intent)
    }
}
```

## Gallery Data

### GalleryItem

```kotlin
data class GalleryItem(
    val id: Int,
    val title: String,
    val searchName: String,
    val imageResource: Int,
    val thumbnailResource: Int,
    val description: String
)
```

### Available Images

| Object | Name | Type |
|--------|------|------|
| M31 | Andromeda Galaxy | Spiral galaxy |
| M42 | Orion Nebula | Emission nebula |
| M45 | Pleiades | Open cluster |
| M51 | Whirlpool Galaxy | Spiral galaxy |
| M57 | Ring Nebula | Planetary nebula |
| M104 | Sombrero Galaxy | Spiral galaxy |
| M1 | Crab Nebula | Supernova remnant |
| M13 | Hercules Cluster | Globular cluster |
| M27 | Dumbbell Nebula | Planetary nebula |
| M33 | Triangulum Galaxy | Spiral galaxy |

### Image Resources

Images stored in `res/drawable/`:

```
drawable/
├── gallery_m31.jpg         # Full-size image
├── gallery_m31_thumb.jpg   # Thumbnail
├── gallery_m42.jpg
├── gallery_m42_thumb.jpg
└── ...
```

## Integration with Star Map

### Finding Objects

When user taps "Find in Sky Map":

1. Create search intent with object name
2. Launch `DynamicStarMapActivity`
3. Activity receives `ACTION_SEARCH` intent
4. Search system finds object in Messier layer
5. View animates to object position

```kotlin
// Search intent creation
val searchIntent = Intent(context, DynamicStarMapActivity::class.java).apply {
    action = Intent.ACTION_SEARCH
    putExtra(SearchManager.QUERY, "M31")
}
startActivity(searchIntent)
```

### Messier Images in Star Map

When `show_messier_images` preference is enabled, Messier layer displays small images at object positions:

```kotlin
// In MessierLayer
override fun getRenderables(): List<ImagePrimitive> {
    if (showImages) {
        return sources.mapNotNull { source ->
            source.imageResource?.let { res ->
                ImagePrimitive(
                    coordinates = source.coordinates,
                    imageResource = res,
                    scale = source.imageScale
                )
            }
        }
    }
    return emptyList()
}
```

## Gallery Navigation

### Entry Points

1. **Menu**: Settings → Image Gallery
2. **Object Info**: View image button in object info dialog
3. **Deep Link**: Intent with gallery action

### Back Navigation

- From gallery grid → Previous activity
- From image display → Gallery grid
- "Find in Sky Map" → Star map (new task)

## Image Attribution

Images sourced from public domain and Creative Commons sources:
- NASA/ESA Hubble Space Telescope
- ESO (European Southern Observatory)
- Amateur astronomers (with permission)

Attribution displayed in image details.

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `ImageGalleryActivity` | Gallery grid UI |
| `ImageDisplayActivity` | Full image view |
| `GalleryAdapter` | RecyclerView adapter |
| `GalleryItem` | Image data model |
