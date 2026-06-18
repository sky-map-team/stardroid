# Research: Gallery Rewrite

## Existing Gallery (to be deleted)

**Decision**: Delete all old gallery code outright — no migration path needed.

**What exists:**
- `HardcodedGallery.kt` — 20 hardcoded items (8 planets + 12 DSOs), backed by `GalleryImage`
  data class with `assetPath` field pointing to `assets/celestial_images/...`
- `ImageGalleryActivity.java` — uses the deprecated Android `Gallery` widget (horizontal scroll,
  `BaseAdapter`); loads images asynchronously via `AssetImageLoader`
- `ImageDisplayActivity.java` — full-screen image + Back + Search buttons; launches
  `DynamicStarMapActivity` with `Intent.ACTION_SEARCH`
- 4 Hilt2 DI files: 2 modules + 2 components
- 3 XML layouts: `imagegallery.xml`, `imagedisplaypanel.xml`, `imagedisplay.xml`

**Why delete entirely**: The Android `Gallery` widget is deprecated; the hardcoded list is
redundant with `object_info.json`; the full-screen image activity is replaced by the existing
info card. Keeping any of this code adds maintenance cost with no user benefit.

**Rationale**: Full replacement; simpler than adapting.

## Available Images

**Decision**: Source the gallery from `ObjectInfoRegistry` (objects where `imagePath != null`).

**What exists:**
- 214 objects in `object_info.json` with an `imageKey` field
- Images stored as WebP/JPEG in `assets/celestial_images/{category}/`
- Breakdown by type:

| Type | Count | Notes |
|------|-------|-------|
| constellation | 89 | Wide-field star-pattern images |
| star | 64 | Mostly ESO/Hubble stellar images |
| galaxy | 23 | DSO — photogenic |
| cluster | 18 | DSO — photogenic |
| nebula | 11 | DSO — photogenic |
| planet | 7 | Solar system — photogenic |
| moon | 1 | The Moon |
| dwarf_planet | 1 | Pluto |
| **Total** | **214** | |

**Curation option**: If 214 items is deemed too many, filter to DSO + planets + moon
(≈ 61 items) by checking `ObjectInfo.type`. This is a one-line predicate change.
The plan defaults to showing all 214 and leaves the curation decision to the team during
implementation review.

**Rationale**: `object_info.json` is the canonical image registry; no separate hardcoded list needed.

## Image Loading

**Decision**: Retain `AssetImageLoader` unchanged.

**What exists:**
- `AssetImageLoader.kt` — async loader, 2-thread pool, 8 MB LRU cache (RGB_565), returns
  `ImageLoadHandle` for cancellation
- Cache sizing: 8 MB ÷ (120 dp × 180 dp × 2 bytes at ~3× density = 120×3 × 180×3 × 2 ≈ 388 KB
  worst-case per full-res bitmap) → cache holds ~20 full-res bitmaps. However at thumbnail display
  size (inSampleSize decoding or ImageView scaling), the decoded bitmap is the full asset size.
  Actual asset sizes are 480×800 px = 750 KB per bitmap at ARGB_8888, ~375 KB at RGB_565.
  8 MB holds ~21 full-res bitmaps. For a scrolling list this is acceptable — RecyclerView keeps
  only ~15–20 ViewHolders alive, and the cache evicts cold items automatically.
- **Cancel on recycle**: `GalleryAdapter` MUST call `handle.cancel()` in `onViewRecycled()` and
  `onViewDetachedFromWindow()` to prevent image-swap artifacts.

**Alternatives considered**: Glide/Coil (third-party image libraries) — rejected per Constitution
  Principle V (no new dependencies without justification; `AssetImageLoader` already solves the
  problem).

## Info Card Integration

**Decision**: Reuse `ObjectInfoDialogFragment` as-is except for the button label and callback.

**What exists:**
- `ObjectInfoDialogFragment.kt` — `AlertDialog` fragment taking `ObjectInfo` as argument
- Single positive button: `android.R.string.ok` → `dialog.dismiss()`
- Already supports image display, credit, description, scientific data, fun fact
- `ImageExpandDialogFragment` handles full-screen image expansion on image tap — retained unchanged

**Change required:**
- Rename positive button label from `android.R.string.ok` to a new string `action_find_in_sky_map`
  (e.g. "Find")
- Add `OnFindClickedListener` interface to the fragment; hosting activity implements it
- In `DynamicStarMapActivity`: Find = search for `info.name` (same as existing tap flow) + dismiss
- In `ImageGalleryActivity`: Find = launch `DynamicStarMapActivity` with `Intent.ACTION_SEARCH`
  and `SearchManager.QUERY = info.name` + finish

**Why not a separate "Find" button alongside OK**: The user explicitly wants one button that is
"Find", not two buttons. Simpler layout, cleaner UX.

**Rationale**: Surgical change to existing fragment; no layout overhaul needed.

## Grid Layout

**Decision**: 3-column `GridLayoutManager`.

**Rationale**: The info card images are 480×800 px (portrait 3:5 ratio). On a typical 360 dp
screen with 4 dp margins:
- 3 columns → ~116 dp wide, ~193 dp tall — shows 3 rows (~9 thumbnails) without scrolling
- 2 columns → ~174 dp wide, ~290 dp tall — shows only ~2 rows (~4 thumbnails) at a time

3 columns gives a better browsing overview. The title fits as a single centered line below each
thumbnail (truncated with ellipsis if needed). This matches the existing spec mockup.

## Hilt DI

**Decision**: Ensure `ImageGalleryActivity` is annotated with `@AndroidEntryPoint` and has a corresponding `ImageGalleryActivityModule` installed in `ActivityComponent`.

`ImageGalleryActivity` needs:
- `ObjectInfoRegistry` (already in `ApplicationComponent` scope)
- `ObjectInfoDialogFragment` (instantiated manually)
- No `AstronomerModel` or rendering dependencies

The new module is smaller than the old `ImageGalleryActivityModule`.

## Search Integration

**Decision**: Reuse the same `Intent.ACTION_SEARCH` pattern from `ImageDisplayActivity.doSearch()`.

`DynamicStarMapActivity` already handles `Intent.ACTION_SEARCH` with `SearchManager.QUERY` extra.
Before launching the search, the layers for the relevant object type should be enabled in
preferences (the old `doSearch()` enabled Stars + Deep Sky Objects + Planets layers — same logic
applies). This can be extracted to a shared helper or duplicated in `ImageGalleryActivity`.

**Alternatives considered**: A result-contract approach (`ActivityResultLauncher`) — not needed;
one-way navigation from gallery to star map is sufficient.
