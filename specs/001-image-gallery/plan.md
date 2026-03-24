# Implementation Plan: Gallery Rewrite

**Branch**: `001-image-gallery` | **Date**: 2026-03-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature description from user + codebase exploration

## Summary

Replace the existing horizontal-scroll gallery (backed by a hardcoded 20-item list) with a
RecyclerView grid of thumbnails derived from `ObjectInfoRegistry` (214 objects with images).
Tapping a thumbnail shows the existing `ObjectInfoDialogFragment` info card. The info card's
"OK" button becomes a "Find" button that navigates to the star map and initiates a search.

The old `ImageGalleryActivity`, `ImageDisplayActivity`, and all `gallery/` package classes are
deleted outright. The new gallery reuses `ObjectInfoRegistry`, `AssetImageLoader`, and
`ObjectInfoDialogFragment` unchanged (except the button label/callback).

## Technical Context

**Language/Version**: Kotlin (new code), targeting Java 17 toolchain
**Primary Dependencies**: AndroidX RecyclerView, existing `AssetImageLoader` (LRU cache),
  existing `ObjectInfoRegistry`, existing `ObjectInfoDialogFragment`
**Storage**: No new storage; images are pre-packaged assets in `assets/celestial_images/`
**Testing**: JUnit 4 + Truth (unit); Espresso (UI smoke test)
**Target Platform**: Android 8.0+ (minSdk 26), portrait + landscape
**Project Type**: Android mobile app — feature rewrite
**Performance Goals**: Smooth 60 fps scroll through 214 thumbnails; no OOM; initial render < 500ms
**Constraints**: Offline-only (all assets bundled); existing 8 MB LRU cache in `AssetImageLoader`
  holds ~285 thumbnails at 120 dp × 180 dp × RGB_565 — sufficient for the full list; async loads
  must be cancelled on ViewHolder recycle to prevent image-swap glitches
**Scale/Scope**: 214 images (89 constellations, 64 stars, 23 galaxies, 18 clusters, 11 nebulae,
  7 planets, 2 other). All included by default; curating to DSO + planets (~61 items) is a simple
  filter if the full list proves unwieldy.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked post-design below.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Sensor-First Architecture | ✅ Pass | Not touching sensor pipeline or AstronomerModel |
| II. Layer Modularity | ✅ Pass | Not touching rendering layers |
| III. Flavor Purity | ✅ Pass | No new Google dependencies; gallery exists in both flavors |
| IV. Test Discipline | ✅ Pass | Unit test required for gallery item population; see Phase 1 |
| V. Simplicity | ✅ Pass | Deleting ~8 old classes/layouts, reusing existing infrastructure |
| VI. Performance | ✅ Pass | RecyclerView + async load cancellation on recycle; LRU cache sufficient |

*Post-design re-check: All gates still pass. No complexity tracking required.*

## Project Structure

### Documentation (this feature)

```text
specs/001-image-gallery/
├── plan.md          # This file
├── research.md      # Phase 0 findings
├── data-model.md    # Phase 1 data model
├── quickstart.md    # Phase 1 build + test guide
└── tasks.md         # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

**Deleted** (old gallery — all 8 classes + 3 layouts + 2 manifest entries):

```text
app/src/main/java/com/google/android/stardroid/
├── gallery/
│   ├── Gallery.kt                         # DELETE
│   ├── GalleryImage.kt                    # DELETE
│   ├── GalleryFactory.kt                  # DELETE
│   └── HardcodedGallery.kt                # DELETE
└── activities/
    ├── ImageGalleryActivity.java           # DELETE (replaced by Kotlin version)
    ├── ImageDisplayActivity.java           # DELETE (replaced by info card)
    ├── ImageGalleryActivityModule.java     # DELETE
    ├── ImageGalleryActivityComponent.java  # DELETE
    ├── ImageDisplayActivityModule.java     # DELETE
    └── ImageDisplayActivityComponent.java  # DELETE

app/src/main/res/layout/
├── imagegallery.xml                        # DELETE
├── imagedisplaypanel.xml                   # DELETE
└── imagedisplay.xml                        # DELETE
```

**Created** (new gallery):

```text
app/src/main/java/com/google/android/stardroid/activities/
├── ImageGalleryActivity.kt                 # NEW — RecyclerView grid, Dagger-injected
├── ImageGalleryActivityModule.kt           # NEW — Dagger module
└── ImageGalleryActivityComponent.kt        # NEW — Dagger component

app/src/main/java/com/google/android/stardroid/gallery/
└── GalleryAdapter.kt                       # NEW — RecyclerView.Adapter with async thumbnail loading

app/src/main/res/layout/
├── activity_image_gallery.xml              # NEW — RecyclerView root layout
└── gallery_thumbnail_item.xml              # NEW — thumbnail card (image + title)
```

**Modified** (minimal changes):

```text
app/src/main/java/com/google/android/stardroid/
├── activities/dialogs/ObjectInfoDialogFragment.kt   # "OK" → "Find" button + search callback
├── inject/ApplicationComponent.kt                  # Remove old activity components; add new one
└── activities/DynamicStarMapActivity.java           # Remove ImageGalleryActivity import/launch;
                                                     # implement Find callback on info card

app/src/main/AndroidManifest.xml                    # Remove ImageDisplayActivity; update
                                                     # ImageGalleryActivity entry (new package)
app/src/test/java/.../gallery/GalleryItemsTest.kt   # NEW unit test
```

**Structure Decision**: Single Android app module. No new modules or build flavors needed.

## Complexity Tracking

*No constitution violations — table omitted.*
