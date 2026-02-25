# Search Feature

Sky Map provides search functionality to find and navigate to celestial objects by name.

## Overview

Users can search for stars, planets, constellations, and deep-sky objects. Found objects are automatically centered in the view.

## User Interface

### Search Invocation

- Toolbar search icon
- Android search button (hardware)
- Voice search (on supported devices)

### Search Flow

```
┌─────────────────────────────────────────┐
│  🔍 Type to search...                   │
│  ─────────────────────────────────────  │
│    Mars                                 │
│    Markab                               │
│    Menkalinan                           │
│    Messier 31 (Andromeda Galaxy)        │
└─────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────┐
│  [Star map centers on selected object]  │
│                                         │
│              ★ Mars                     │
│              ↑                          │
│         (highlighted)                   │
└─────────────────────────────────────────┘
```

## Search System

### Content Provider

`SearchTermsProvider` implements Android's SearchManager contract:

```java
public class SearchTermsProvider extends ContentProvider {
    @Override
    public Cursor query(Uri uri, String[] projection,
                       String selection, String[] selectionArgs,
                       String sortOrder) {
        String query = uri.getLastPathSegment();
        return searchLayers(query);
    }
}
```

### Search Configuration

Defined in `res/xml/searchable.xml`:

```xml
<searchable
    android:label="@string/app_name"
    android:hint="@string/search_hint"
    android:searchSuggestAuthority="com.google.android.stardroid.searchprovider"
    android:searchSuggestIntentAction="android.intent.action.SEARCH"
    android:voiceSearchMode="showVoiceSearchButton|launchRecognizer" />
```

## Search Algorithm

### Name Matching

1. **Exact Match**: Full name comparison (case-insensitive)
2. **Prefix Match**: Starts-with matching for autocomplete
3. **Alternate Names**: Check aliases (e.g., "M31" = "Andromeda Galaxy")

```kotlin
// Layer search implementation
fun searchByObjectName(name: String): List<SearchResult> {
    return sources
        .filter { it.names.any { n -> n.equals(name, ignoreCase = true) } }
        .map { SearchResult(it.displayName, it) }
}

fun getObjectNamesMatchingPrefix(prefix: String): Set<String> {
    return sources
        .flatMap { it.names }
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .toSet()
}
```

### Layer Traversal

Search queries all visible layers:

```kotlin
// LayerManager.searchByObjectName
fun searchByObjectName(name: String): List<SearchResult> {
    return layers
        .filter { it.isVisible }
        .flatMap { it.searchByObjectName(name) }
}
```

## Search Results

### SearchResult Data Class

```kotlin
data class SearchResult(
    val capitalizedName: String,      // Display name
    val renderable: AstronomicalRenderable  // The found object
)
```

### Result Handling

| Results | Action |
|---------|--------|
| 1 result | Auto-center and zoom to object |
| 2+ results | Show selection dialog |
| 0 results | Show "not found" dialog |

### Single Result

```java
// TeleportingController
public void teleportTo(SearchResult result) {
    GeocentricCoordinates target = result.getCoordinates();
    animateToPosition(target);
    highlightObject(result);
}
```

### Multiple Results Dialog

```
┌─────────────────────────────────────┐
│  Multiple matches found             │
│  ─────────────────────────────────  │
│  ○ Sirius (Star)                    │
│  ○ Sirius A (Star)                  │
│  ○ Sirius B (White dwarf)           │
│                                     │
│           [Cancel]   [OK]           │
└─────────────────────────────────────┘
```

## Searchable Objects

### By Layer

| Layer | Searchable Objects |
|-------|-------------------|
| Stars | Named stars (Sirius, Polaris, etc.) |
| Constellations | Constellation names (Orion, etc.) |
| Solar System | Sun, Moon, planets, ISS |
| Messier | M1-M110, alternate names |
| Meteor Showers | Perseids, Leonids, etc. |

### Name Formats

Objects may have multiple searchable names:

| Object | Names |
|--------|-------|
| Andromeda Galaxy | "Andromeda", "M31", "Messier 31", "NGC 224" |
| Polaris | "Polaris", "North Star", "α Ursae Minoris" |
| Mars | "Mars", "The Red Planet" |

## Navigation Animation

When search completes, the view animates to the target:

### Animation Sequence

1. Calculate current and target positions
2. Determine optimal rotation path
3. Animate rotation over ~500ms
4. Optionally adjust zoom level
5. Highlight target object

```java
// MapMover
public void animateTo(GeocentricCoordinates target) {
    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(ANIMATION_DURATION);
    animator.addUpdateListener(animation -> {
        float t = (float) animation.getAnimatedValue();
        GeocentricCoordinates current = interpolate(start, target, t);
        model.setPointing(current);
    });
    animator.start();
}
```

## Voice Search

On supported devices, voice input triggers search:

1. User activates voice search
2. Speech recognized and converted to text
3. Text used as search query
4. Results displayed as normal

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `SearchTermsProvider` | Android ContentProvider for search |
| `LayerManager` | Coordinates search across layers |
| `TeleportingController` | Handles navigation to results |
| `MapMover` | Animation controller |
| `MultipleSearchResultsDialogFragment` | Result selection UI |
| `NoSearchResultsDialogFragment` | Empty result UI |
