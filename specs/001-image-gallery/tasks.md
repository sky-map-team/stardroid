---
description: "Task list for Gallery Rewrite"
---

# Tasks: Gallery Rewrite

**Input**: Design documents from `specs/001-image-gallery/`
**Prerequisites**: plan.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅
**Note**: No spec.md exists; user stories derived from plan.md and feature description.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1/US2/US3 label for user-story phases

---

## Phase 1: Teardown (Remove old gallery)

**Purpose**: Eliminate all old gallery code so the codebase compiles cleanly against the new
structure. Remove references first, then delete dead files.

- [ ] T001 [P] Remove `ImageGalleryActivity` and `ImageDisplayActivity` DI component entries from `app/src/main/java/com/google/android/stardroid/inject/ApplicationComponent.kt`
- [ ] T002 [P] Remove `ImageDisplayActivity` `<activity>` entry from `app/src/main/AndroidManifest.xml`; update `ImageGalleryActivity` entry to reference new Kotlin class in `.activities` package (package attribute already correct; verify and leave placeholder for the new class)
- [ ] T003 [P] Remove the `Intent(this, ImageGalleryActivity::class.java)` launch block from `app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java` (~line 530); replace with a `// TODO: launch new ImageGalleryActivity (wired in T013)` comment
- [ ] T004 [P] Delete `app/src/main/java/com/google/android/stardroid/gallery/Gallery.kt`, `GalleryImage.kt`, `GalleryFactory.kt`, `HardcodedGallery.kt`
- [ ] T005 [P] Delete `app/src/main/java/com/google/android/stardroid/activities/ImageGalleryActivity.java`, `ImageDisplayActivity.java`, `ImageGalleryActivityModule.java`, `ImageGalleryActivityComponent.java`, `ImageDisplayActivityModule.java`, `ImageDisplayActivityComponent.java`
- [ ] T006 [P] Delete `app/src/main/res/layout/imagegallery.xml`, `imagedisplaypanel.xml`, `imagedisplay.xml`

**Checkpoint**: `./gradlew assembleGmsDebug` compiles without errors. Gallery menu entry does nothing (placeholder TODO in DynamicStarMapActivity).

---

## Phase 2: User Story 1 — Gallery Grid (Priority: P1) 🎯 MVP

**Goal**: A 3-column RecyclerView grid displays all 214 objects-with-images as thumbnails with
titles. Images load asynchronously; loads are cancelled on ViewHolder recycle.

**Independent Test**: Open gallery from menu → full-screen RecyclerView grid appears with
recognisable astronomical thumbnails and object names. Smooth scroll, no OOM, no image-swap glitch.

### Implementation for User Story 1

- [ ] T007 [P] [US1] Add `getAllWithImages(): List<ObjectInfo>` method to `app/src/main/java/com/google/android/stardroid/education/ObjectInfoRegistry.kt` that returns all registry entries where `imagePath != null`, sorted by display name
- [ ] T008 [P] [US1] Create `app/src/main/res/layout/gallery_thumbnail_item.xml`: `LinearLayout` (vertical, wrap_content height) containing an `ImageView` (match_parent width, 0dp height with `layout_weight` or fixed 180dp, `centerCrop` scaleType, black background) and a `TextView` (centered, 12sp, 2dp padding, single line with ellipsis) for the object name
- [ ] T009 [P] [US1] Create `app/src/main/res/layout/activity_image_gallery.xml`: full-screen `RecyclerView` with id `gallery_grid`; dark/black background to suit the space aesthetic
- [ ] T010 [US1] Create `app/src/main/java/com/google/android/stardroid/gallery/GalleryAdapter.kt`: `RecyclerView.Adapter<GalleryAdapter.ViewHolder>` taking `List<ObjectInfo>` and an `onItemClick: (ObjectInfo) -> Unit` lambda; in `onBindViewHolder` load thumbnail via `AssetImageLoader.loadBitmapAsync` storing the handle; in `onViewRecycled` and `onViewDetachedFromWindow` cancel the handle and clear the ImageView; apply night-mode color filter via `NightModeHelper` if night mode is active
- [ ] T011 [P] [US1] Create `app/src/main/java/com/google/android/stardroid/activities/ImageGalleryActivityModule.kt`: Dagger `@Module` providing `@PerActivity`-scoped `ObjectInfoDialogFragment` and `ImageExpandDialogFragment`, following the pattern in `AbstractDynamicStarMapModule`
- [ ] T012 [P] [US1] Create `app/src/main/java/com/google/android/stardroid/activities/ImageGalleryActivityComponent.kt`: Dagger `@PerActivity`-scoped component that depends on `ApplicationComponent`, includes `ImageGalleryActivityModule`, and exposes `inject(ImageGalleryActivity)` and the `ObjectInfoDialogFragment.ActivityComponent` sub-interface
- [ ] T013 [US1] Create `app/src/main/java/com/google/android/stardroid/activities/ImageGalleryActivity.kt`: `AppCompatActivity` that injects `ObjectInfoRegistry`; in `onCreate` calls `registry.getAllWithImages()`, sets `RecyclerView` with `GridLayoutManager(this, 3)` and `GalleryAdapter`; add `ImageGalleryActivityComponent` to `ApplicationComponent.kt`; replace the TODO in `DynamicStarMapActivity.java` (T003) with a real launch Intent
- [ ] T014 [P] [US1] Write unit test `app/src/test/java/com/google/android/stardroid/gallery/GalleryItemsTest.kt`: verify `ObjectInfoRegistry.getAllWithImages()` returns a non-empty list; verify every returned item has a non-null non-blank `imagePath`; verify count matches expected range (> 50)

**Checkpoint**: Gallery opens from the star-map menu, shows a grid of thumbnails with titles, scrolls smoothly, compiles in both gms and fdroid flavors.

---

## Phase 3: User Story 2 — Tap Thumbnail to Info Card (Priority: P2)

**Goal**: Tapping any thumbnail opens the `ObjectInfoDialogFragment` info card for that object.
Tapping outside the dialog (or the back button) dismisses it.

**Independent Test**: Tap a thumbnail → info card appears showing image, name, description, and
scientific data for the tapped object. Tap outside → dismisses. Back button → dismisses.

### Implementation for User Story 2

- [ ] T015 [US2] Wire thumbnail tap in `ImageGalleryActivity.kt`: implement `OnFindClickedListener` (stub — logs `info.name` for now; real search wired in T020); pass it to `GalleryAdapter` via `onItemClick` lambda that calls `objectInfoDialogFragment.show(info)` after setting the listener; handle the dialog show/dismiss lifecycle correctly (check `isAdded` / `isStateSaved` before showing)
- [ ] T016 [US2] Verify `ObjectInfoDialogFragment` is dismissible by tapping outside: confirm `setCanceledOnTouchOutside(true)` (or the AlertDialog equivalent `setCancelable(true)`) is set in `app/src/main/java/com/google/android/stardroid/activities/dialogs/ObjectInfoDialogFragment.kt`; add it if missing

**Checkpoint**: Tap a thumbnail → info card dialog appears with correct content; tapping outside dismisses it; "OK" button dismisses it; no crash.

---

## Phase 4: User Story 3 — Find Button (Priority: P3)

**Goal**: The info card's "OK" button is replaced by a "Find" button. Tapping "Find" from the
gallery navigates to the star map and searches for the object. Tapping "Find" from within the star
map triggers the existing search behaviour.

**Independent Test**: Open gallery → tap thumbnail → info card appears with "Find" button (not
"OK"). Tap "Find" → star map opens, navigates to and highlights the object. From the star map,
tapping an object in the sky → info card → "Find" → map centres on the object.

### Implementation for User Story 3

- [ ] T017 [P] [US3] Add string resource `action_find_in_sky_map` with value `"Find"` to `app/src/main/res/values/strings.xml` (do not add to translatable strings; add to `notranslate-strings.xml` if that convention is used in the project, otherwise add directly to `strings.xml`)
- [ ] T018 [US3] Modify `app/src/main/java/com/google/android/stardroid/activities/dialogs/ObjectInfoDialogFragment.kt`: (a) add `interface OnFindClickedListener { fun onFindClicked(info: ObjectInfo) }` as a nested interface; (b) change the `setPositiveButton` label from `android.R.string.ok` to `R.string.action_find_in_sky_map`; (c) in the positive button click handler, call `(activity as? OnFindClickedListener)?.onFindClicked(info)` before dismissing
- [ ] T019 [US3] Implement `OnFindClickedListener` in `ImageGalleryActivity.kt`: in `onFindClicked(info)` enable the Stars, Deep Sky Objects, and Planets layer visibility prefs (mirror the logic from the deleted `ImageDisplayActivity.doSearch()`), then start `DynamicStarMapActivity` with `Intent.ACTION_SEARCH` and `SearchManager.QUERY = info.name`
- [ ] T020 [US3] Implement `OnFindClickedListener` in `app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java`: in `onFindClicked(info)` call the existing search method (same code path triggered by `ObjectInfoTapHandler`) using `info.name` as the query; the dialog dismisses itself after calling the listener

**Checkpoint**: Full end-to-end flow works — gallery → thumbnail tap → info card → Find → star map
navigates to object. From the star map, tapping an object → info card → Find → map centres on it.

---

## Phase 5: Polish & Cross-Cutting

**Purpose**: Verify night mode, cleanup, and final validation.

- [ ] T021 Verify night mode in `ImageGalleryActivity.kt`: confirm `NightModeHelper` tints are applied to the gallery title/toolbar text (if any); confirm thumbnail images receive the night-mode multiply filter in `GalleryAdapter`; test visually with night mode enabled in settings
- [ ] T022 Run `quickstart.md` validation: build both `assembleGmsDebug` and `assembleFdroidDebug`; run `GalleryItemsTest`; verify `grep` checks for deleted class names return no results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Teardown (Phase 1)**: No dependencies — start immediately; T001–T003 in parallel, then T004–T006 in parallel
- **US1 (Phase 2)**: Depends on Teardown completion (clean compile needed)
- **US2 (Phase 3)**: Depends on US1 (grid must exist before wiring tap)
- **US3 (Phase 4)**: T017 (string resource) is independent of US1/US2 and can run any time; T018 (ObjectInfoDialogFragment) can be done in parallel with US1; T019/T020 depend on T018 interface + US1/US2 activities existing
- **Polish (Phase 5)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: After Teardown — no story dependencies
- **US2 (P2)**: After US1
- **US3 (P3)**: T017–T018 parallelisable with US1; T019–T020 after US1+US2+T018

### Parallel Opportunities

```bash
# Phase 1 — run together:
T001  app/src/main/java/.../inject/ApplicationComponent.kt
T002  app/src/main/AndroidManifest.xml
T003  app/src/main/java/.../activities/DynamicStarMapActivity.java

# Then in parallel:
T004  gallery/*.kt (delete)
T005  activities/Image*.java (delete)
T006  res/layout/image*.xml (delete)

# Phase 2 — independent US1 tasks (run together):
T007  education/ObjectInfoRegistry.kt
T008  res/layout/gallery_thumbnail_item.xml
T009  res/layout/activity_image_gallery.xml
T011  activities/ImageGalleryActivityModule.kt
T012  activities/ImageGalleryActivityComponent.kt
T014  test/.../GalleryItemsTest.kt
# Then:
T010  gallery/GalleryAdapter.kt  (after T008 layout exists)
T013  activities/ImageGalleryActivity.kt  (after T010, T011, T012)

# US3 T017–T018 can run in parallel with Phase 2 (Phase 3):
T017  res/values/strings.xml
T018  activities/dialogs/ObjectInfoDialogFragment.kt
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Teardown
2. Complete Phase 2: US1 (gallery grid)
3. **STOP and validate**: Grid shows thumbnails, scrolls smoothly, both flavors compile
4. Proceed to US2 + US3

### Incremental Delivery

1. Teardown → clean compile
2. US1 → gallery grid visible (thumbnail browsing works)
3. US2 → tap shows info card (read-only object info)
4. US3 → "Find" button completes the full flow
5. Polish → night mode + validation

---

## Notes

- `GalleryItem` data class from `data-model.md` is omitted in favour of using `ObjectInfo` directly in the adapter (Constitution V — avoid unnecessary abstractions)
- Curating to DSO + planets (~61 items) requires a one-line type filter in T007; leave for team decision during T013 review
- `ImageExpandDialogFragment` (full-screen image on tap) is already wired in `ObjectInfoDialogFragment` — no changes needed; it remains available in the info card
- Night mode color filter for thumbnails should use `NightModeHelper` consistent with how `ImageGalleryActivity.java` did it, not a custom implementation
