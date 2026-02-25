# Search Indexing - Fast Object Lookup

## Purpose

Defines **search functionality** and **data structures** for finding celestial objects by name or position.

## Search Overview

| Search Type | Input | Output | Algorithm |
|------------|-------|--------|----------|
| Prefix search | Text ("Sirius", "And") | List of matching objects | Trie index |
| Position search | RA/Dec coordinates | Object at location | Spatial hash |
| Fuzzy search | Text ("Siri") | Best matching objects | Levenshtein distance |
| Category filter | Object type (STAR, PLANET) | Filtered list | Set filtering |

## Prefix Search

### Data Structure: Trie

**Purpose:** Fast autocomplete for "starts-with" queries

**Structure:**
```
Root
├─ "s"
│  ├─ "i"
│  │  └─ "r" → Sirius
│  └─ "a" → Saturn
├─ "a"
│  ├─ "l" → Aldebaran
│  └─ "n" → Andromeda
└─ "m"
   ├─ "o" → Moon
   └─ "a" → Mars
```

**Node Data:**
```kotlin
data class TrieNode(
    val children: Map<Char, TrieNode> = mapOf(),
    val starIds: List<Int> = emptyList()
)
```

**Algorithm:**

**Search:**
```kotlin
fun searchPrefix(prefix: String): List<CelestialObject> {
    var node = root
    for (char in prefix.lowercase()) {
        node = node.children[char] ?: return emptyList()
    }
    return node.starIds.map { starId -> catalog.getById(starId) }
}
```

**Complexity:** O(m) where m = prefix length

**Insert:**
```kotlin
fun insert(name: String, starId: Int) {
    var node = root
    for (char in name.lowercase()) {
        node = node.children.getOrPut(char) { TrieNode() }
    }
    node.starIds = (node.starIds + starId).distinct()
}
```

**Memory:** ~500KB for all star names

## Position Search

### Problem

User taps on sky - what celestial object is at that location?

### Data Structure: Spatial Hash

**Purpose:** Divide sky into grid cells, assign objects to cells

**Grid Definition:**
```
RA bins: 360 bins (1° each) × 24 bins (hourly)
Dec bins: 180 bins (1° each)
```

**Data Structure:**
```kotlin
data class SpatialHash(
    val raBins: Map<Int, Set<Int>> = mapOf(),     // RA → star IDs
    val decBins: Map<Int, Set<Int>> = mapOf(),    // Dec → star IDs
) {
    fun insert(star: CelestialObject) {
        val raBin = (star.ra * 24 / 360).toInt()  // Hour bin
        val decBin = (star.dec + 90).toInt()      // Degree bin
        raBins.getOrPut(raBin) { mutableSetOf() }.add(star.id)
        decBins.getOrPut(decBin) { mutableSetOf() }.add(star.id)
    }

    fun query(ra: Float, dec: Float, radius: Float): Set<Int> {
        // Get objects in nearby bins
        val raBin = (ra * 24 / 360).toInt()
        val decBin = (dec + 90).toInt()
        val nearby = mutableSetOf<Int>()
        // Add same bin
        nearby.addAll(raBins[raBin] ?: emptySet())
        // Add adjacent bins for radius
        for (dr in -radiusBin..radiusBin) {
            nearby.addAll(raBins[raBin + dr] ?: emptySet())
        }
        return nearby
    }
}
```

**Algorithm:**
1. Convert tap position to RA/Dec
2. Lookup spatial hash
3. Return objects in matching cell (plus neighbors for radius)

**Complexity:**
- **Build:** O(n) where n = number of stars
- **Query:** O(k) where k = objects in cell (usually 1-10)

## Category Filtering

### Problem

User wants to see only planets, or only stars, etc.

### Implementation

**Simple filter:**
```kotlin
fun filterByType(type: ObjectType): List<CelestialObject> {
    return allObjects.filter { it.type == type }
}
```

**Layer-based filtering:**
```kotlin
// Each layer manages its own objects
val starsLayer = layers.find { it is StarsLayer }
starsLayer.enabled = userPref
```

**UI:** Checkbox list for each layer type

## Fuzzy Search (Future)

### Purpose

Handle typos, partial matches ("Siri" → "Sirius")

### Algorithm: Levenshtein Distance

**Distance:** Number of insertions/deletions/substitutions

**Implementation:**
```kotlin
fun fuzzySearch(query: String, threshold: Int = 2): List<CelestialObject> {
    return allObjects.map { obj ->
        val distance = levenshtein(query, obj.name)
        obj to distance
    }
    .filter { (_, distance) -> distance <= threshold }
    .sortedBy { (_, distance) -> distance }
    .map { (obj, _) -> obj }
}
```

**Complexity:** O(n × m²) where n = objects, m = query length
**Optimization:** Precompute trigrams for large catalogs

## Search Performance Targets

### Requirements

| Operation | Target | Notes |
|-----------|--------|-------|
| Prefix search | < 50ms | ~100K objects |
| Position search | < 20ms | Point-to-sky query |
| Category filter | < 10ms | Set filtering |
| Fuzzy search | < 200ms | Computed on background thread |

### Optimization Strategies

**Lazy loading:** Don't build indexes until search needed

**Caching:** Cache search results for common queries

**Background thread:** Run fuzzy search on worker thread, post results to UI

**Debouncing:** Don't search on every keystroke; wait 100ms after last keystroke

## Search UI Pattern

### Integration with Activities

**DynamicStarMapActivity:**
```kotlin
fun onSearchQuery(query: String) {
    viewModel.search(query).observe(this) { results ->
        if (results.isEmpty()) {
            showNoResultsDialog()
        } else if (results.size == 1) {
            navigateToObject(results.first())
        } else {
            showMultipleResultsDialog(results)
        }
    }
}
```

**Search results dialog:**
- Single result → Navigate immediately
- Multiple results → Show bottom sheet for selection

## Search Result Ranking

### Ranking Algorithm

**Factors:**
1. **Exact match:** Highest priority
2. **Prefix match:** Second priority
3. **Fuzzy match:** Third priority
4. **Magnitude:** Brighter objects ranked higher
5. **Proximity:** Closer to current view (position search)

**Score formula:**
```
score = w1×match_type + w2×brightness + w3×proximity

where:
- match_type: 100 for exact, 80 for prefix, 50 for fuzzy
- brightness: (10 - magnitude) × 10
- proximity: 100 - angular_distance_from_center
```

## Related Specifications

- [README.md](README.md) - Core domain overview
- [data-models.md](data-models.md) - Celestial object model
- [../features/search.md](../features/search.md) - Search feature specification
