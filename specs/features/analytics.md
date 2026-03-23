# Sky Map Analytics Specification

> **Status**: Current as of March 2026 (Firebase Analytics, GMS flavor only)
> **Related**: [analytics.md](analytics.md) (original design doc)

# To be implemented
This section describes a revamp of the analytics infrastructure. The section titled Overview
describes the current situation. As work progresses on the features that section should be
updated as we go.

## 1. Ensure that events are logged with a consistent naming pattern
In the GA4 UI we want to be able to easily identify bespoke Sky Map GA events from ones that are provided
"for free" by Google Analytics. Ensure a clear distinction between 'user level properties' and session events.
In general we would like to be able to slice by user level properties in the GA4 UI.

Acceptance criteria: all Sky Map-specific events have a common naming pattern (e.g. the _ev suffix).

#### Implementation Plan

An audit of `AnalyticsInterface.java` shows the convention is already largely in place:
- All custom events use the `_ev` suffix (e.g. `session_length_ev`, `layer_toggled_ev`).
- All user properties use the `_prop` suffix (e.g. `new_user_prop`, `device_sensors_prop`).
- The `search` event intentionally uses Firebase's recommended event name and should remain as-is.

One cleanup is needed: `TOGGLED_MANUAL_MODE_LABEL` is named as a "label" but is actually an event
name. It will be renamed to `MANUAL_MODE_TOGGLED_EVENT` when the associated event firing is fixed
in Section 4.

**Commit:** Rename `TOGGLED_MANUAL_MODE_LABEL` → `MANUAL_MODE_TOGGLED_EVENT` in
`AnalyticsInterface.java` and update all references (currently none, since the event is never
fired). Purely a naming/documentation change.

**Status: DONE** — `AnalyticsInterface.java` and `DynamicStarMapActivity.java` updated.

---

## 2. Update the preference value whitelist to be a blacklist
This was added to ensure we don't inadvertently log PII such as a user's address but history
has shown that this is unnecessarily conservative. Review all current
preferences, flag any that could be a privacy concern, and add those to a blacklist instead.

Acceptance criteria: the whitelist is gone and instead there is a minimal blacklist for events
of genuine concern.

#### Implementation Plan

**Pre-existing bug:** The current whitelist in `PreferenceChangeAnalyticsTracker.kt` is:
```kotlin
setOf("sensor_speed", "sensor_damping, lightmode")
```
This is only **two** strings — `"sensor_damping, lightmode"` is a single string, so `lightmode`
is never actually whitelisted. The blacklist approach fixes this naturally.

**Preference audit** (all keys in `preference_screen.xml` and internal keys):

| Key | Type | PII risk | Action |
|-----|------|----------|--------|
| `location` | String (user-entered place name) | **Yes** | Blacklist |
| `latitude` | String (user-entered decimal) | **Yes** | Blacklist |
| `longitude` | String (user-entered decimal) | **Yes** | Blacklist |
| `force_gps` | boolean | No | Log |
| `no_auto_locate` | boolean | No | Log |
| `use_magnetic_correction` | boolean | No | Log |
| `manual_compass_adjustment` | float (degrees offset) | No | Log |
| `show_object_info_on_tap2` | boolean | No | Log |
| `show_object_info_auto_mode` | boolean | No | Log |
| `auto_level_horizon` | boolean | No | Log |
| `auto_dimness` | enum string | No | Log |
| `font_size` | enum string | No | Log |
| `show_messier_images` | boolean | No | Log |
| `disable_gyro` | boolean | No | Log |
| `sensor_speed` | enum string | No | Log |
| `sensor_damping` | enum string | No | Log |
| `reverse_magnetic_z` | boolean | No | Log |
| `viewing_direction` | enum string | No | Log |
| `sound_effects` | boolean | No | Log |
| `enable_analytics` | boolean | No | Log |
| `lightmode` | string | No | Log |

**Files to change:**
- `PreferenceChangeAnalyticsTracker.kt`: replace `stringPreferenceWhiteList` with a
  `blacklist` set containing `"location"`, `"latitude"`, `"longitude"`. In
  `getPreferenceAsString()`, mask blacklisted keys to `"REDACTED"` (more informative
  than `"PII"`). Remove the whitelist check from the string branch — all string values
  are now logged unless blacklisted.
- `AnalyticsInterface.java`: replace the whitelist constant comment with a blacklist
  comment documenting the three keys and the rationale.

**Commit:** Isolated commit touching only `PreferenceChangeAnalyticsTracker.kt` and
`AnalyticsInterface.java`.

**Status: DONE** — `PreferenceChangeAnalyticsTracker.kt` updated: whitelist removed, blacklist
(`location`, `latitude`, `longitude`) added, pre-existing comma-typo bug fixed, masked value
changed from `"PII"` to `"REDACTED"`.

---

## 3. Update the GA implementation to modern standards.
Check dependencies and APIs and see if any need to be updated.

#### Implementation Plan

- Bump `firebase-bom` in `app/build.gradle` to the latest stable version (currently `33.7.0`).
- Review all other GMS dependencies in `app/build.gradle` for available updates (google-services
  plugin, play-services-location, etc.).
- Check for any deprecated Firebase Analytics API calls in `Analytics.java`
  (e.g. `getAppInstanceId()` → `getAppInstanceId()` is still current; verify at time of
  implementation).

**Commit:** Isolated build-file-only change. No functional or behavioural changes. Must be
tested with a GMS debug build to confirm analytics still initialises correctly.

**Status: DEFERRED** — version bump attempted but reverted due to build issues. Revisit separately.

---

## 4. Gaps & Suggested Improvements
Review the following gaps and implement changes where appropriate.  Make each improvement a separate
git change so that it can be reviewed in isolation. For each suggested analysis I want you to ensure
that the correct data is logged and provide instructions on how to build the analysis in GA4 since
in some cases events will need to be registered. Make sure you consider how easy it is to slice
the data in GA4. For instance for many metrics (like session length) it is essential that we
can slice by app version.

### Missing events

| Gap                                                                  | Suggested event | Suggested parameters | Rationale |
|----------------------------------------------------------------------|-----------------|---------------------|-----------|
| **Time Travel usage**                                                | `time_travel_used_ev` | `travel_event: String` | We track that the dialog was *opened* but not whether the user actually set a time or which event they chose. |
| **Manual mode entered/exited**                                       | `manual_mode_toggled_ev` | `enabled: boolean` | `TOGGLED_MANUAL_MODE_LABEL` constant exists but is never called. |
| **Gyroscope-vs-mag sensor path chosen**                              | extend `start_up_event_ev` | `sensor_path: "rotation_vector"\|"accel_mag"` | Knowing which sensor fusion path is active would help diagnose accuracy issues across the install base. Also take this opportunity to log a snapshot of key user settings (see Settings in Feature Utilisation below). |
| **Search with no results**                                           | `search_failed_ev` | `search_term: String` | `search_success=false` is logged on the `search` event, but a dedicated event is far easier to work with in GA4 Explore (no custom dimension required to filter). Keep `search_success=false` on `search` for success-rate calculations; add `search_failed_ev` alongside. |
| **Object searched-and-locked**                                       | `object_locked_ev` | `object_name: String`, `mode: "auto"\|"manual"` | Fire in `activateSearchTarget()`. In manual mode the view teleports immediately; in auto mode the user must point the phone. Distinguishing the two modes is valuable. |
| **Gallery image viewed**                                             | `gallery_image_viewed_ev` | `image_name: String` | We know the gallery was opened but not which images engaged users. |
| Skip this for now (we don't do it): **App rating / review prompted** | `review_prompt_shown_ev`, `review_prompt_outcome_ev` | `outcome: "rated"\|"dismissed"\|"later"` | If an in-app review prompt is ever added. |
| Skip for now: **Crash / ANR**                                        | (use Firebase Crashlytics) | — | There is no Crashlytics dependency; adding it would give symbolicated crash reports with zero custom instrumentation. Deferred. |
| **Night mode state at launch**                                       | extend `start_up_event_ev` | `night_mode_on: boolean` | Useful context for interpreting session lengths and object views. |
| Skip this for now (we don't have it) **Constellation art toggled**   | (already covered by `layer_toggled_ev`) | — | Confirm `source_provider.constellation_boundaries` etc. are all emitting this event. |

#### Implementation Plan — Missing Events

**4a. Fix `manual_mode_toggled_ev`**

The constant `MANUAL_MODE_TOGGLED_EVENT` (renamed from `TOGGLED_MANUAL_MODE_LABEL` in §1) exists
but `setAutoMode()` in `DynamicStarMapActivity.java` builds a Bundle and then never calls
`analytics.trackEvent()`.

- `AnalyticsInterface.java`: add `MANUAL_MODE_ENABLED = "enabled"`.
- `DynamicStarMapActivity.setAutoMode(boolean auto)`: call
  `analytics.trackEvent(MANUAL_MODE_TOGGLED_EVENT, b)` where `b` contains `enabled = auto`.

*GA4 setup:* Register `enabled` as an event-scoped custom dimension (Firebase Console →
Analytics → Custom definitions → Create custom dimension, Parameter name: `enabled`). Use
in Explore with `manual_mode_toggled_ev` as the event filter.

**Commit:** `DynamicStarMapActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — `MANUAL_MODE_ENABLED` constant added; `setAutoMode()` now calls
`analytics.trackEvent(MANUAL_MODE_TOGGLED_EVENT, ...)` with `enabled` boolean. Stale bundle
line (which was putting the event name as a menu item value) replaced with the correct call.

---

**4b. Extend `start_up_event_ev` — rename `hour`, add `day_of_week`, `night_mode_on`,
`sensor_path`, and settings snapshot**

`start_up_event_ev` currently fires in `StardroidApplication.setUpAnalytics()`, *before*
`performFeatureCheck()` runs. Since `sensor_path` is determined in `performFeatureCheck()`,
the startup event must be moved to fire at the end of `onCreate()`, after both methods have
run. Pass the sensor path back from `performFeatureCheck()` (return value or a field) so it
can be included in the bundle.

Parameters to add/change:

| Change | Constant | Notes |
|--------|----------|-------|
| Rename `hour` → `local_hour` | `START_EVENT_HOUR = "local_hour"` | Removes ambiguity about UTC vs local. **Breaking change for existing dashboards** — note when deploying. |
| Add `day_of_week: int (0–6)` | `START_EVENT_DAY_OF_WEEK = "day_of_week"` | 0 = Sunday. Useful for weekday vs weekend analysis. |
| Add `night_mode_on: boolean` | `START_EVENT_NIGHT_MODE = "night_mode_on"` | Read from `lightmode` preference. |
| Add `sensor_path: "rotation_vector"\|"accel_mag"` | `START_EVENT_SENSOR_PATH = "sensor_path"` | Determined by `performFeatureCheck()`. `rotation_vector` when the rotation-vector sensor path is active; `accel_mag` otherwise. |
| Add settings snapshot | see below | Key user settings at session start. |

Settings snapshot parameters (all on `start_up_event_ev`):

| Parameter | Preference key | Type |
|-----------|---------------|------|
| `disable_gyro` | `disable_gyro` | boolean |
| `sensor_speed` | `sensor_speed` | string (enum) |
| `sensor_damping` | `sensor_damping` | string (enum) |
| `auto_level_horizon` | `auto_level_horizon` | boolean |
| `no_auto_locate` | `no_auto_locate` | boolean |
| `show_object_info_on_tap2` | `show_object_info_on_tap2` | boolean |
| `sound_effects` | `sound_effects` | boolean |

These capture the stable user preferences that drive feature-utilisation questions (e.g. "what
fraction of sessions have gyro disabled?") without needing a separate event.

*GA4 setup:* Register each new parameter as an event-scoped custom dimension. The existing
`hour` custom dimension will need to be retired and replaced with `local_hour`. Use
`day_of_week` and `local_hour` together in an Explore pivot table for a stargazing
hour-of-week heatmap. Use settings snapshot params as breakdown dimensions on any engagement
metric.

**Commit:** `StardroidApplication.kt` + `AnalyticsInterface.java`. Refactor `onCreate()` to
fire `start_up_event_ev` after `performFeatureCheck()`.

**Status: DONE** — `AnalyticsInterface.java`: `START_EVENT_HOUR` renamed to `"local_hour"`;
added `START_EVENT_DAY_OF_WEEK`, `START_EVENT_NIGHT_MODE`, `START_EVENT_SENSOR_PATH`,
`SENSOR_PATH_ROTATION_VECTOR`, `SENSOR_PATH_ACCEL_MAG`. `StardroidApplication.kt`:
`trackEvent` call moved out of `setUpAnalytics()` into new `fireStartupEvent(sensorPath)`
method; `performFeatureCheck()` now returns the sensor path string; `onCreate()` calls
`fireStartupEvent()` after `performFeatureCheck()` so all parameters are available.

---

**4c. Add `time_travel_used_ev`**

The `TimeTravelDialog` presents a spinner of named events (`TimeTravelEvents.ALL`) as well as
free-form date/time pickers. Logging the chosen event name is more actionable in GA4 than a
raw delta in hours.

**Data model change:** Add an `analyticsKey: String` field to `TimeTravelEvent` (a
`data class` in `TimeTravelEvent.kt`). Assign a stable snake_case key to every entry in
`TimeTravelEvents.ALL`:

| Event | `analyticsKey` |
|-------|---------------|
| Hint/placeholder (pos 0) | *(never fired — user must press Go)* |
| Next sunset | `"next_sunset"` |
| Next sunrise | `"next_sunrise"` |
| Next full moon | `"next_full_moon"` |
| Next new moon | `"next_new_moon"` |
| Six-planet parade 2026 | `"six_planet_parade_2026"` |
| Lunar eclipse 2026 | `"lunar_eclipse_2026"` |
| … (all FIXED events) | derived from the string resource suffix |
| Custom date/time (user picked manually) | `"custom"` |
| Start from now | `"from_now"` |

**Wiring:** `TimeTravelDialog` already calls either `parentActivity.setTimeTravelMode(date,
searchTargetRes)` or `parentActivity.setTimeTravelModeFromNow()`. Add an overload / extra
parameter to pass the `analyticsKey` through. The dialog knows the current key: it is
`TimeTravelEvents.ALL[index].analyticsKey` when a popular event is selected, `"custom"` when
the user used the date/time pickers, and `"from_now"` for the no-args path.

Constants to add to `AnalyticsInterface.java`:
```
TIME_TRAVEL_USED_EVENT = "time_travel_used_ev"
TIME_TRAVEL_EVENT_KEY  = "travel_event"
```

*GA4 setup:* Register `travel_event` as an event-scoped custom dimension. In Explore: rows =
`travel_event`, metric = count of `time_travel_used_ev` → immediately shows which events are
most popular. `"custom"` in the results indicates users are exploring beyond the presets.

**Commit:** `TimeTravelEvent.kt` + `TimeTravelEvents.kt` (add analytics keys) +
`TimeTravelDialog.java` (pass key to activity) + `DynamicStarMapActivity.java` (fire event) +
`AnalyticsInterface.java`.

**Status: DONE** — `analyticsKey` field added to `TimeTravelEvent`; all entries in
`TimeTravelEvents.ALL` assigned stable snake_case keys; `TimeTravelDialog` tracks
`currentAnalyticsKey` (reset to `"from_now"` on open, set to `"custom"` on manual date/time
pick, set to event key on popular-event selection) and passes it to `setTimeTravelMode()`;
`setTimeTravelModeFromNow()` fires with `"from_now"`; `TIME_TRAVEL_USED_EVENT` and
`TIME_TRAVEL_EVENT_KEY` constants added to `AnalyticsInterface`.

---

**4d. Add `search_failed_ev`**

In `DynamicStarMapActivity.doSearchWithIntent()`, when `results.isEmpty()`, fire a new event
alongside the existing `search` event (which retains `search_success = false`):

```java
Bundle fail = new Bundle();
fail.putString(AnalyticsInterface.SEARCH_TERM, queryString);
analytics.trackEvent(AnalyticsInterface.SEARCH_FAILED_EVENT, fail);
```

Constants to add:
```
SEARCH_FAILED_EVENT = "search_failed_ev"
```
(reuse `SEARCH_TERM` for the parameter.)

*GA4 setup:* `search_failed_ev` appears in the Events dashboard directly — no custom
dimension needed for the event count. Register `search_term` as a custom dimension to see
which terms fail most. Build a funnelanalysis in Explore: step 1 = `search` event, step 2
= `search_failed_ev` (drop-off = success rate). For top-failed-terms: Explore → free-form,
rows = `search_term` custom dimension, filter by `search_failed_ev`, sort by count.

**Commit:** `DynamicStarMapActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — `SEARCH_FAILED_EVENT` constant added; `doSearchWithIntent()` fires
`search_failed_ev` with `search_term` when results are empty, alongside the existing `search`
event which retains `search_success=false`.

---

**4e. Add `object_locked_ev`**

Fire in `DynamicStarMapActivity.activateSearchTarget()`, which already reads `autoMode` from
preferences:

```java
Bundle b = new Bundle();
b.putString(AnalyticsInterface.OBJECT_LOCKED_NAME, searchTerm);
b.putString(AnalyticsInterface.OBJECT_LOCKED_MODE,
    autoMode ? AnalyticsInterface.OBJECT_LOCKED_MODE_AUTO
             : AnalyticsInterface.OBJECT_LOCKED_MODE_MANUAL);
analytics.trackEvent(AnalyticsInterface.OBJECT_LOCKED_EVENT, b);
```

Constants to add:
```
OBJECT_LOCKED_EVENT = "object_locked_ev"
OBJECT_LOCKED_NAME  = "object_name"
OBJECT_LOCKED_MODE  = "mode"
OBJECT_LOCKED_MODE_AUTO   = "auto"
OBJECT_LOCKED_MODE_MANUAL = "manual"
```

Note: `activateSearchTarget()` receives the capitalised display name but not a structured
`object_id`/`object_type`. This is sufficient for identifying popular search targets.
A future enhancement could pass the full `SearchResult` to include type information.

*GA4 setup:* Register `object_name` and `mode` as custom dimensions. Use in Explore to
rank most-locked objects; use `mode` as a secondary breakdown to see whether manual-mode
users search for different objects than auto-mode users.

**Commit:** `DynamicStarMapActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — `OBJECT_LOCKED_EVENT`, `OBJECT_LOCKED_NAME`, `OBJECT_LOCKED_MODE`,
`OBJECT_LOCKED_MODE_AUTO`, `OBJECT_LOCKED_MODE_MANUAL` constants added; event fired in
`activateSearchTarget()` with `object_name` and `mode` (`"auto"` or `"manual"`).

---

**4f. Add `gallery_image_viewed_ev`**

In `ImageGalleryActivity`, the `OnItemClickListener` is where the user taps an image to view it. Fire the event there with the image's asset path or display name.

Constants to add:
```
GALLERY_IMAGE_VIEWED_EVENT = "gallery_image_viewed_ev"
GALLERY_IMAGE_NAME = "image_name"
```

*GA4 setup:* Register `image_name` as a custom dimension. Use in Explore: rows =
`image_name`, metric = count of `gallery_image_viewed_ev` → directly shows which gallery
images drive the most engagement.

**Commit:** `ImageGalleryActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — `GALLERY_IMAGE_VIEWED_EVENT` and `GALLERY_IMAGE_NAME` constants added;
event fired in `showImage()` using `GalleryImage.searchTerm` as the image name (stable across
locales, consistent with object IDs used elsewhere in analytics).

---

### Parameter quality improvements

| Event | Issue | Suggestion |
|-------|-------|------------|
| `layer_toggled_ev` | `layer_name` uses internal pref key strings (e.g. `source_provider.constellations`) which are hard to read in the Firebase console | Map to human-readable names (`"constellations"`, `"messier"` …) or add a separate `layer_display_name` parameter. |
| `session_length_ev` | Raw seconds in an int parameter → hard to bucket in Explore | Consider also logging a `session_bucket` string (`"<1min"`, `"1-5min"`, `"5-15min"`, `"15-30min"`, `">30min"`) for easier funnel analysis. |
| `start_up_event_ev` | `hour` alone is ambiguous (is it UTC or local?) | Rename to `local_hour` or document clearly; also consider adding `day_of_week` (0–6) to distinguish weekday vs weekend stargazing. |
| `object_info_viewed_ev` | No position context | ~~Add `magnitude: float` and `visible_without_aid: boolean`~~ — skipped, magnitude not uniformly available across object types. |

#### Implementation Plan — Parameter Quality

**4g. `layer_toggled_ev` — human-readable `layer_name`**

Replace the raw pref key with a human-readable name in the event parameter. The toggle is
fired in `DynamicStarMapActivity.onOptionsItemSelected()`. Add a static mapping
(e.g. a `Map<String, String>` in `AnalyticsInterface.java` or a helper method) from internal
pref key to display name:

| Pref key | Display name |
|----------|-------------|
| `source_provider.0` | `stars` |
| `source_provider.1` | `constellations` |
| `source_provider.2` | `messier` |
| `source_provider.3` | `solar_system` |
| `source_provider.4` | `grid` |
| `source_provider.5` | `horizon` |
| `source_provider.6` | `meteor_showers` |

Fall back to the raw key for any unmapped value. This is a **breaking change** for the
`layer_name` dimension in existing dashboards.

*GA4 setup:* No new registration needed — `layer_name` is already a custom dimension. After
this change, historical data will show old keys; new data will show clean names. Update any
saved Explore reports to account for the rename.

**Commit:** `DynamicStarMapActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — Spec corrected (keys are numeric, not string names); `LAYER_NAME_MAP` and
static `layerDisplayName()` helper added to `AnalyticsInterface`; `DynamicStarMapActivity`
now passes the human-readable name to the event. Unmapped keys fall back to the raw pref key.

---

**4h. `session_length_ev` — add `session_bucket`**

In `DynamicStarMapActivity.onStop()`, compute and add a bucket string:

| Seconds | `session_bucket` value |
|---------|----------------------|
| < 60 | `"<1min"` |
| 60–299 | `"1-5min"` |
| 300–899 | `"5-15min"` |
| 900–1799 | `"15-30min"` |
| ≥ 1800 | `">30min"` |

Constants to add:
```
SESSION_BUCKET = "session_bucket"
```

*GA4 setup:* Register `session_bucket` as an event-scoped custom dimension. In Explore, use
`session_bucket` as a row dimension with `session_length_ev` as the event filter → gives an
instant session-length distribution without custom bucketing. Add `app_version` (Firebase
automatic dimension) as a secondary breakdown.

**Commit:** `DynamicStarMapActivity.java` + `AnalyticsInterface.java`.

**Status: DONE** — A `SessionBucketLength` enum and `getSessionLengthBucket()` method already
existed in `DynamicStarMapActivity` but were never wired into the bundle. `SESSION_BUCKET`
constant added to `AnalyticsInterface`; `bucket.name()` now included in the event. Bucket
values are `LESS_THAN_TEN_SECS`, `TEN_SECS_TO_THIRTY_SECS`, `THIRTY_SECS_TO_ONE_MIN`,
`ONE_MIN_TO_FIVE_MINS`, `MORE_THAN_FIVE_MINS`.

---

### User property improvements

| Property | Issue | Suggestion |
|----------|-------|------------|
| `DEVICE_SENSORS` | Single string is hard to filter in the Firebase console | Consider one boolean user property per key sensor (`has_gyro`, `has_rotation_vector`). Firebase supports up to 25 user properties. |
| Missing: **app version at install** | Firebase collects `app_version` automatically, but not the version the user first installed | Add `first_install_version_prop` set only when `NEW_USER = "true"` to track cohort retention across releases. |
| Missing: **language** | The original design doc listed language breakdown as a goal | Add `user_locale_prop` (e.g. `"en-US"`) set at startup. This directly addresses the translation investment question. |

#### Implementation Plan — User Properties

**4i. Individual sensor boolean properties**

In `StardroidApplication.performFeatureCheck()`, alongside the existing `device_sensors_prop`
string, set two new user properties:

```
HAS_GYRO             = "has_gyro_prop"           // "true"/"false"
HAS_ROTATION_VECTOR  = "has_rotation_vector_prop" // "true"/"false"
```

These are easier to use as filter conditions in GA4 than parsing the pipe-separated string.
Firebase user-property budget: currently 3 of 25 used; these add 2 more (5 of 25 total).

*GA4 setup:* User properties are automatically available as user-scoped dimensions in
Explore. Use `has_gyro_prop` as a segment comparator in the session-length analysis
(§ Engagement: "do users with gyroscopes stay longer?").

**Commit:** `StardroidApplication.kt` + `AnalyticsInterface.java`.

**Status: DONE** — `HAS_GYRO` and `HAS_ROTATION_VECTOR` constants added; set in
`performFeatureCheck()` after `reportedSensors` is populated, using `"true"`/`"false"`.
Firebase user-property budget: 5 of 25 used.

---

**4j. `first_install_version_prop`**

In `StardroidApplication.setUpAnalytics()`, after the `newUser` check:

```kotlin
if (newUser) {
    analytics.setUserProperty(AnalyticsInterface.FIRST_INSTALL_VERSION, versionName)
}
```

Constant to add:
```
FIRST_INSTALL_VERSION = "first_install_version_prop"
```

*GA4 setup:* Automatically available as a user-scoped dimension. In Explore, set
`first_install_version_prop` as a row dimension and `session_length_ev` or retention metric
as the metric → cohort retention by install version.

**Commit:** `StardroidApplication.kt` + `AnalyticsInterface.java`.

**Status: DONE** — `FIRST_INSTALL_VERSION` constant added; set in `setUpAnalytics()` when
`newUser == true`.

---

**4k. `user_locale_prop`**

In `StardroidApplication.setUpAnalytics()`:

```kotlin
analytics.setUserProperty(AnalyticsInterface.USER_LOCALE,
    Locale.getDefault().toLanguageTag())  // e.g. "en-US", "de-DE"
```

Constant to add:
```
USER_LOCALE = "user_locale_prop"
```

*GA4 setup:* Available as a user-scoped dimension. Use in Explore: rows =
`user_locale_prop`, metric = user count → immediately shows language breakdown. Cross with
`session_length_ev` to see whether translated users engage differently.

**Commit:** `StardroidApplication.kt` + `AnalyticsInterface.java`.

**Status: DONE** — `USER_LOCALE` constant added; set in `setUpAnalytics()` via
`Locale.getDefault().toLanguageTag()` (e.g. `"en-US"`, `"de-DE"`). Firebase user-property
budget: 7 of 25 used.

## Suggested Analyses

### Engagement
- **Session length distribution** by `device_sensors` user property: do users with gyroscopes
  stay longer (smoother experience)?
- **Hour-of-day launch histogram**: confirm the expected evening peak; use to time push
  notifications or release announcements.
- **Search success rate** (`search_success=true / total searches`): a falling rate indicates
  missing catalog entries. Cross-reference failed `search_term` values against the catalog
  roadmap.
- **Top searched terms** (failed only): direct input for which objects to add next. Already
  capturable from current data.

### Feature utilisation
- **Layer toggle frequency**: which layers do users turn off? Candidates for hiding behind an
  "advanced" toggle or removing entirely.
- **Menu item open rates**: compare `gallery_opened`, `time_travel_opened`, `credits_opened`
  to assess which features are actually discovered and used.
- **Object info card views by type**: are users engaging with Messier objects, planets, or
  bright stars most? Informs which educational content to expand.
- **Object info card views**: which objects are most popular and should be enhanced?
- **Settings**: we're less interested in setting change _events_ in most cases, but rather which settings people prefer. e.g. do most users have the 'disable gyro' setting enabled?

### Device & sensor health
- **`sensor_liar_prop` rate by device model**: Firebase's default `device_model` dimension
  plus this property would identify specific hardware with broken sensor reporting.
- **`no_sensors_warning_ev` rate**: is this a significant portion of the install base? If
  high, a manual mode first-run experience may be warranted.
- **Calibration event rate**: ratio of `calibration_auto_triggered_ev` to total sessions
  indicates how often compass accuracy degrades and whether the current threshold is too
  sensitive.

### Retention & conversion
- **TOS acceptance rate**: `TOS_accepted_ev / (TOS_accepted_ev + TOS_rejected_ev)`. A
  significant rejection rate may indicate the EULA is too alarming.
- **New vs returning user session length**: do new users engage as long as returning users?
  If not, investigate the first-run experience.
- **Preference change events over time**: users who customise the app (night mode, sensor
  damping) likely have higher retention — worth verifying with a cohort analysis.
- **App version**: we want to understand the upgrade path - for a user on a particular app version which app version did they upgrade from? We want to see app upgrades in real time as a version rolls out.


## N.
If necessary, update the privacy policy to clearly convey what data is collected and why.



---

## Overview

Sky Map uses **Firebase Analytics** (via the `firebase-analytics` BOM) in the GMS Play Store
flavor. The F-Droid flavor uses a compile-time no-op stub — no data is collected from those
users. Users on GMS can opt out via Settings → `enable_analytics` (default: on).

### Key files

| File | Role |
|------|------|
| `app/src/main/java/…/util/AnalyticsInterface.java` | Single source of truth for all constant names |
| `app/src/gms/java/…/util/Analytics.java` | Firebase implementation (GMS only) |
| `app/src/fdroid/java/…/util/Analytics.java` | No-op stub (F-Droid) |
| `app/src/main/java/…/util/PreferenceChangeAnalyticsTracker.kt` | Global preference-change listener |
| `app/src/main/java/…/StardroidApplication.kt` | App startup: user properties, start event |
| `app/src/main/java/…/ApplicationModule.kt` | Dagger singleton binding |

---

## User Properties

Set once at app startup in `StardroidApplication.kt` and persisted by Firebase.

| Property constant | Firebase name | Values | Purpose |
|---|---|---|---|
| `NEW_USER` | `new_user_prop` | `"true"` / `"false"` | Distinguishes first install from returning user. Derived from absence of `PREVIOUS_APP_VERSION_PREF` and old `read_tos` key. |
| `DEVICE_SENSORS` | `device_sensors_prop` | Pipe-separated subset of `accel`, `gyro`, `mag`, `rot`, or `none` | Records which motion sensors are present (e.g. `"accel\|mag\|rot"`). Used to understand feature supportability across the install base. |
| `SENSOR_LIAR` | `sensor_liar_prop` | `"true"` (set only on failure) | Flags devices that advertise a sensor but refuse listener registration. |

---

## Events

### `TOS_accepted_ev`
- **Trigger**: User taps Accept in the EULA dialog (`EulaDialogFragment`)
- **Parameters**: none
- **Notes**: Only fires on first launch per install.

### `TOS_rejected_ev`
- **Trigger**: User taps Decline or dismisses the EULA dialog
- **Parameters**: none
- **Notes**: App exits after rejection; this event confirms the exit path was reached.

### `start_up_event_ev`
- **Trigger**: Application `onCreate` completes analytics setup
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `hour` | int (0–23) | `21` |

- **Notes**: `hour` reflects the device's local time. Useful for understanding when users
  star-gaze (expected peak: late evening / night).

### `menu_item_pressed_ev`
- **Trigger**: User selects an item from the main star map menu
- **Parameters**:

  | Key | Type | Possible values |
  |-----|------|----------------|
  | `menu_item` | String | `search_requested`, `settings_opened`, `credits_opened`, `help_opened`, `night_mode`, `time_travel_opened`, `gallery_opened`, `TOS_opened`, `calibration_opened`, `diagnostics_opened` |

### `session_length_ev`
- **Trigger**: `DynamicStarMapActivity.onStop()` (user backgrounds or leaves the app)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `session_length` | int (seconds) | `342` |

- **Notes**: Measures time from `onStart` to `onStop`. Firebase also collects
  `engagement_time_msec` natively; this custom event gives finer-grained bucketing control.

### `search` *(matches Firebase recommended event name)*
- **Trigger**: User submits a search query in the search activity
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `search_term` | String | `"andromeda"` |
  | `search_success` | boolean | `true` |

### `object_info_viewed_ev`
- **Trigger**: User taps a celestial object and the educational info card is displayed
  (`ObjectInfoTapHandler`)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `object_id` | String | `"m31"`, `"mars"` |
  | `object_type` | String | `"MESSIER"`, `"BRIGHT_STAR"`, `"PLANET"` … |

### `layer_toggled_ev`
- **Trigger**: User enables or disables a sky layer from the menu
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `layer_name` | String | `"source_provider.constellations"` |
  | `layer_enabled` | boolean | `false` |

### `preference_button_toggled_ev`
- **Trigger**: User taps a quick-toggle button in the UI (distinct from going into Settings)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `preference_toggle_value` | String | `"night_mode:true"` |

- **Notes**: Value is the preference key and new state joined by `:`.

### `preference_change_ev`
- **Trigger**: *Any* `SharedPreferences` change (caught by a global listener in
  `PreferenceChangeAnalyticsTracker`)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `value` | String | `"sensor_speed:50"` or `"location:REDACTED"` |

- **Privacy**: All preference values are logged **except** `location`, `latitude`, and
  `longitude` which are masked to `"REDACTED"`. (Prior to §2 being implemented, only a
  whitelist of three keys were logged with real values; all others used `"PII"`.)

### `calibration_auto_triggered_ev`
- **Trigger**: `SensorAccuracyMonitor` detects compass accuracy has fallen below threshold
  and automatically opens the calibration screen
- **Parameters**: none

### `calibration_toast_shown_ev`
- **Trigger**: Compass accuracy is low, but the auto-calibration dialog is disabled, so a
  toast is shown instead
- **Parameters**: none

### `no_sensors_warning_ev`
- **Trigger**: Device lacks required sensors (accelerometer or magnetometer); warning
  dialog shown in `DynamicStarMapActivity`
- **Parameters**: none

---

## Architecture Notes

- All constants live in `AnalyticsInterface.java`; no magic strings elsewhere.
- Injected as a Dagger singleton; activities and fragments receive it via constructor injection.
- The GMS/F-Droid split is handled entirely at the flavor source-set level — the rest of the
  app is unaware of which implementation is active.
- Opt-out disables the Firebase SDK itself (not just the custom events), so automatic
  Firebase events (screen views, first open, etc.) are also suppressed.




---

## Privacy Considerations

1. **Consent timing**: analytics fires from the first launch before the user has seen the
   EULA. The [original design doc](analytics.md) flagged this as a known gap. Consider
   initialising Firebase Analytics only *after* TOS acceptance.
2. **Search terms**: `search_term` is logged in full. Searches are unlikely to contain PII,
   but this should be called out in the privacy policy.
3. **Preference value masking**: the current whitelist (`sensor_speed`, `sensor_damping`,
   `lightmode`) is conservative. Expanding it to non-PII keys (see above) is safe but
   should be reviewed before each change.
4. **F-Droid users**: receive zero analytics — this is correct and should not be changed.
