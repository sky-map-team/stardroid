# Detailed Design: iOS Feature Parity — Flags, Satellites, Notifications, Widgets

**Status: PROPOSED.** Nothing here is built. Decision numbers are unassigned. Written while iOS
2.0.0 (build 6) waits on App Review, so the work can start the day it is approved.

iOS 2.0.0 ships the sky, search, time travel, info cards, settings and tips. It does not ship
the four things Android has behind experiment flags: remote flags themselves, satellites,
notifications and home-screen widgets (port plan phase 6). This doc designs all four, in the
order they should be built, and names what is shared with Android and what is new.

It extends [satellite-tracking.md](satellite-tracking.md),
[widgets-and-notifications.md](widgets-and-notifications.md) and
[remote-announcements.md](remote-announcements.md); read those for the Android designs. Anything
not restated here is unchanged.

## 1. Principles

- **iOS is a design input, not a lowest common denominator.** Where iOS can do something better
  (exact local notifications, a native countdown text, a timeline of widget entries), use it.
  Where it cannot (no pin-a-widget API, no hidden widget), say so and design around it.
- **Share the maths and the policy, not the plumbing.** The planners and models are common
  code. WorkManager, Glance and `NotificationManager` each get an iOS counterpart in Swift or
  `iosMain`.
- **Android behaviour does not change.** Every extraction is a mechanical move, reviewed
  slice by slice, like phase 3.
- **Ship dark where the platform allows it.** Notifications and satellites can sit behind a
  flag. Widgets cannot be hidden on iOS and are not flagged (§6.3).

## 2. Build order and releases

| Order | Slice | Release | Why this position |
|---|---|---|---|
| 1 | Experiment flags (§3) | 2.0.1 | The rest are gated by it; small |
| 2 | Satellites (§4) | 2.0.1 | Mostly shared code already; feeds notifications |
| 3 | Notifications (§5) | 2.0.2 or 2.1 | Needs the shared planner extracted and a scheduler |
| 4 | Widgets (§6) | 2.1 | A new Xcode target and a second framework |

Notifications and widgets are separate releases (notifications behind a flag), so a slip in one does not
hold up the other.

## 3. Experiment flags

### What exists

`ExperimentConfig` is already the shared contract (`shared/model`): `isEnabled`,
`lastFetchTimeMillis`, `canFetch`, `fetchNow`. iOS binds a `FlagSourceExperimentConfig` over
`IosDefaults` (`ExperimentConfig.Static` minus `NOTIFICATIONS`, which iOS doesn't deliver).

### The change

A Swift `FirebaseRemoteConfigAdapter`, written like `FirebaseAnalyticsAdapter` and passed into
the Kotlin framework at startup, implementing a small `RemoteConfigSource` interface that
`app-ios` wraps into an `ExperimentConfig`. The Kotlin side owns the defaults and the
`Experiment` enum, so a flag added on Android needs no Swift change.

- **SDK:** add the `FirebaseRemoteConfig` SPM product beside `FirebaseAnalyticsWithoutAdIdSupport`.
- **Defaults:** shipped in code, not a plist. iOS defaults are *conservative*: a flag is `true`
  only if the iOS surface exists. Today that is everything `IosDefaults` enables.
- **Same project, per-platform values.** Use the existing Firebase project and the same keys.
  Remote Config conditions on **Platform = iOS** let a flag be on for Android and off for iOS
  (or the reverse) without forking keys. The `skymap.push-announcement` skill needs to learn
  conditions before any iOS-specific rollout.
- **Fetch:** 12 h minimum interval, the same as Android. Activate on next launch, not mid-session,
  so a screen never changes under the user. `fetchNow` backs the Diagnostics button.
- **Failure:** any error leaves the shipped defaults in force. Nothing blocks startup.
- **Kill switch behaviour** matches Android: a flip reaches clients on their next fetch.

### As built (2.0.1)

`FlagSource` and `FlagSourceExperimentConfig` are in `shared/model`; `FirebaseRemoteConfigAdapter`
is the Swift side. Three choices beyond the design above:

- **Frozen at launch.** Flags are read once when the app starts, and a fetch is activated and
  stored for the next launch, so Diagnostics' "Fetch now" updates the fetch time but not the
  values shown until a relaunch. The first frame waits (up to 1.5 s) for Firebase to load its
  stored values.
- **A server-controlled allow-list.** Only flags with an iOS surface (`ServerControlled` in
  `IosExperiments.kt`) listen to the server. The key names are shared with Android, so
  Android turning on camera AR or notifications must not turn them on here. Add a flag to the list
  in the change that gives it an iOS surface.
- **Same keys, so conditions matter.** `satellites_enabled` is already true for everyone, so a
  build that honours it turns satellites on at its first fetch. To ship satellites dark, add a
  Platform = iOS condition that is false first.

### Privacy

Remote Config sends an installation ID and the usual request metadata to Google. The policy
already names Remote Config; check it covers iOS, the privacy manifest
(`PrivacyInfo.xcprivacy`) and the App Store privacy answers, and update the `Last Updated` line.
Do this in the same change (see `AGENTS.md` → Privacy). The policy's Remote Config line is
labelled "Google Play version" and says the usage-statistics switch does not turn it off. **Decided:**
iOS does the same, with no opt-out, because the flags and announcements need it. Extend the policy
line to iOS.

### Tests

Common tests cover the wrapper with a fake source. A debug-only Diagnostics row shows each
flag's value and the last fetch time, as on Android.

## 4. Satellites

### What is already shared

Almost all of it. In common code today: `CelesTrakClient` (interface), `SatelliteFetchPolicy`
(the circuit breaker), `TleStore` over a `TextFiles` interface, `SatelliteElementsRepository`,
`satelliteElementsFlow`, `SatelliteLayer`, and the SGP4 and pass maths in `core/astronomy`. An
iOS `DirectoryTextFiles` already exists in `data/src/iosMain`. The iOS graph currently passes
`satellitesEnabled = false` and `satelliteElements = emptyFlow()`.

### What is new

1. **`UrlSessionCelesTrakClient`** (`data/src/iosMain`). One `NSURLSession` GET with the same
   User-Agent as Android. Requirements carried over from the usage policy:
   - **No retries inside the client.** `NSURLSession` does not retry a completed request, but
     set `waitsForConnectivity = false` and a short timeout so it cannot hang around.
   - **Do not follow redirects.** `NSURLSession` follows them by default; the delegate must
     answer `willPerformHTTPRedirection` with `nil` so a 301 is seen and treated as terminal.
   - **Honour `If-Modified-Since`** and map 304 to `NotModified`.
   - The interface is synchronous, so bridge with a semaphore on a background dispatcher, not
     the main thread.
2. **A refresh scheduler** (§4.1).
3. **Wiring:** `satellitesEnabled` from the `SATELLITES` flag, the elements flow from the
   repository, the info-card pass row, the layer rail icon and the Help text.
4. **Strings:** remove "iOS has no satellite data" copy, and the Satellites and Pass/Peak
   alerts exclusions in iOS Help once the feature is live.

### As built (2.0.1)

`UrlSessionCelesTrakClient` (`data/iosMain`) with `fetchOutcomeFor` tested on the iOS target;
`StaleSatelliteRefresh` and `trackSatelliteFetchResult` in `shared/viewmodels` (shared with
Android's worker for the analytics events, so both report refusals the same way); `IosSatellites`
in `app-ios` ties them to the graph. Differences from the design:

- **No `BGAppRefreshTask` yet.** The foreground fetch alone keeps data fresh for anyone who opens
  the app, and a background task needs the graph to be a process singleton first (#1243). Follow-up.
- **The fetch needs the Satellites layer on** as well as the flag and consent, which is what the
  privacy policy promises.
- **No iOS Diagnostics satellite row yet** (Android's is in `DiagnosticsRoute`).

### 4.1 Refreshing the element sets

Android uses a 12 h periodic job and **never fetches on app open**, so request volume stays
spread out (satellite-tracking.md → thundering herd). iOS cannot promise a 12 h job:
`BGAppRefreshTask` runs when the system decides, often rarely for a little-used app. Two
consequences:

- **Background refresh is best effort.** Register `BGAppRefreshTask` with identifier
  `io.penterakt.skymap.satellite-refresh`, add it to `BGTaskSchedulerPermittedIdentifiers`, and
  enable the `fetch` background mode. On each run: check `SatelliteFetchPolicy.mayFetch`,
  fetch, then submit the next request with `earliestBeginDate` = 12 h + 0–60 min of jitter.
  Transport failures reschedule on the normal period and never loop.
- **A stale-only foreground fetch, with per-install jitter. Decided.** If the cache is empty or
  older than 3 days plus a per-install random offset of 0–24 h (chosen once and stored), a fetch
  on app open is allowed, still through `mayFetch` (so the 2 h minimum and the breaker apply).
  This departs from "never on open", but the trigger is staleness, and the offset scatters
  installs across the day. A delay after launch would not help, since the app may close first.
  Without it a user whose background refresh never runs sees an empty state forever.

The worker-result mapping (`satelliteWorkAction`) moves from `app/` to `:data` so both
platforms use one definition: HTTP errors succeed (never retry), transport failures retry on
the normal schedule.

### 4.2 Pass alerts

Android fires 10 minutes before AOS with `setAndAllowWhileIdle`, because Doze can delay it. iOS
local notifications with a calendar trigger fire on time, so the lead can be the originally
wanted **6 minutes** (a named constant with this reasoning in its comment). Pass alerts are
part of the notification work (§5); until then the layer ships with the pass row and no alert.

- Schedule only the best pass per night, as on Android.
- **Cancel** every pending pass alert when the elements are older than 10 days, because
  precise times are suppressed past that age.
- Replan whenever new elements arrive, the location changes or the app foregrounds.

### 4.3 Privacy and review

- `celestrak.org` is named in the policy, but the line is labelled "Satellite data (Android)".
  Extend it to iOS. The request sends no identifier beyond the User-Agent and IP.
- `PrivacyInfo.xcprivacy`: declare any file-timestamp or `UserDefaults` API reasons the new
  code uses.
- App Review: satellites are a normal feature; no extra notes needed. Ship with the flag on in
  Remote Config only after the build is approved.

### 4.4 Tests

Existing common tests cover the policy, store and repository. Add `iosTest` coverage for the
client's redirect and 304 handling against a local stub, and a manual device check using
Xcode's `_simulateLaunchForTaskWithIdentifier` for the background task.

## 5. Notifications

### 5.1 The model difference

Android posts from a WorkManager job when the time comes. iOS cannot reliably run code at a
given time, but it **can deliver a local notification at an exact time with no code running**.
So iOS **plans ahead**:

- A shared planner turns the next few nights' events into `PlannedNotification(kind, fireAt,
  title, body, deepLink)` values.
- A thin iOS scheduler replaces all pending requests with the planner's output
  (`UNUserNotificationCenter`), keyed by `kind + date` so re-planning is idempotent.
- Text is fixed at schedule time. That is fine: every notification is computed offline from the
  ephemeris and catalog.
- iOS keeps at most 64 pending local notifications, which is plenty for a 3–5 day horizon.
- **Replan on:** app foreground, a settings change, a location change, new satellite elements,
  and each `BGAppRefreshTask` run.

The horizon is short on purpose. A plan made on Monday for Friday goes stale if the user
travels, and a short horizon limits how long it can be wrong.

### 5.2 What changes in shared code

Today the notification logic is in `app/` and mixes event selection with Android's builders.
Split it:

| Piece | Home |
|---|---|
| Event scan and ranking (`TonightSky`, `SkyEvent`) | `:core:events` (already common) |
| Frequency rules, quiet hours, suppression threshold | new `shared/surfaces` (common) |
| `PlannedNotification` and the planner | `shared/surfaces` |
| Text: ids through the shared `TextSource` (§10, decision 4) and the formatting (`highlightLine`, `cardinal`) | `shared/surfaces` |
| Posting (channels, builders, WorkManager, alarms) | `app/` (unchanged behaviour) |
| Posting on iOS | Swift scheduler over `UNUserNotificationCenter` |

The Android scheduler then consumes the same planner output, so both platforms apply identical
frequency rules. That is the largest Android-side cost of the whole project, and it needs its
own slice-by-slice review.

### 5.3 Kinds, permission and settings

Same kinds as Android, same defaults (all **off**): shower peak, tonight digest, satellite pass
alert, and (later) eclipse alerts. iOS has no per-kind channels, so the per-kind switches live
**in the app**, as Settings → Notifications and the in-layer toggles (`LayerParameter.Toggle`
is already shared). The system setting is all-or-nothing per app.

- **Ask for permission at opt-in, not at launch.** Call `requestAuthorization` when the user
  first turns a kind on, so the prompt follows the user's own action. **Provisional
  authorization is rejected:** it skips the prompt but delivers silently to Notification Centre
  only, which defeats a six-minute pass alert.
- Use `threadIdentifier` per kind so they group sensibly.
- Drop the `NOTIFICATIONS` exclusion from `IosDefaults` and add the notifications to
  `permissions_notice_ios` when they ship.
- **Interruption level:** `.active` by default. `.timeSensitive` for pass alerts needs the
  Time Sensitive Notifications capability and a justification; start without it.
- **Deep links:** notifications carry a URL for the object or "tonight" view. Needs an iOS URL
  scheme or universal link handler; share-object-card.md plans universal links, so build the
  handler once for both.

### 5.4 Announcements

Remote announcements need to learn about a message while the app is closed. On iOS that means
a `BGAppRefreshTask` fetch, which is unreliable, or a push, which is out of scope. **Defer**
announcements to after the widget work. The widget banner and the launch interstitial (both
passive/foreground) can ship earlier since they need no background run.

### 5.5 Tests

The planner is common code with golden tests on frozen events: a quiet night yields nothing, a
shower peak yields one, and the one-per-kind-per-day rule holds. The iOS scheduler gets a fake
`UNUserNotificationCenter` wrapper for idempotence tests.

## 6. Widgets

### 6.1 What WidgetKit changes

Glance code cannot be reused: a widget is a separate extension target written in SwiftUI, in
its own process with a tight memory limit (roughly 30 MB), that never runs the Compose UI.

- **A second Kotlin framework, `SkyMapWidgetKit`,** exporting only `core/astronomy`,
  `core/events` and `core/math` (and the `shared/surfaces` models). No Compose, no Metal, no
  database. The extension is its own process, so the two frameworks never share a runtime.
  Measure its size and memory early (risk 2).
- **Shared data through an App Group** (`group.io.penterakt.skymap`): the saved location and the
  relevant settings. The main app writes them; the extension reads them.
  `Settings.savedLocation` moves to a store both processes can read, which on iOS means
  `UserDefaults(suiteName:)` for these few keys.
- **The astronomy runs in the extension.** Passing precomputed results would go stale when the
  app is not opened for days.

### 6.2 Timelines instead of a 12 h refresh

WidgetKit lets a provider hand back many future entries. Because the maths is offline and cheap:

- Generate entries for the next 24–48 h at sensible steps (hourly for the moon, at event
  boundaries for tonight) and ask to reload at the end.
- The moon's rise and set times and phase change smoothly, so hourly entries are accurate.
- The countdown widget uses SwiftUI's `Text(date, style: .timer)`, which counts down on its
  own with no reloads at all.
- The system budgets reloads; the app calls `WidgetCenter.reloadAllTimelines()` when location or
  settings change.

### 6.3 No flag

Widgets have launched on Android and their experiment flags are being removed, so iOS has no
widget flag either. iOS cannot hide an installed extension from the gallery anyway. The
extension ships when all three families are complete, in a release that mentions them, and App
Review sees working widgets. There is no remote kill switch; a bad widget is fixed by an update.

### 6.4 The widgets

| Android | iOS family | Notes |
|---|---|---|
| Moon (2×2, compact 1×1) | `systemSmall`, plus `accessoryCircular` and `accessoryInline` | Lock Screen families are iOS 16+; the deployment target is 15, so guard with `#available` |
| Tonight digest | `systemMedium` (and `accessoryRectangular`) | Ranked events from `TonightSky`; ISS pass joins here after §4 |
| Countdown | `systemSmall` | Native timer text |

- **Moon disc:** drawn with SwiftUI `Path` from (`illuminatedFraction`, `waxing`, `mirrored`),
  the same maths as `MoonDiscRenderer`. No image assets, so nothing new to classify in
  `ASSET-LICENSES.txt`.
- **Colours:** brand navy surface and Star Gold for the figure only, as in the Android design.
  The palette is a small shared table, so it is copied rather than extracted, and a test on each
  side pins the values.
- **Tap:** `widgetURL` deep-links to the object (same URL handler as notifications).
- **Strings:** the extension is a separate bundle and needs its own copy of the translated
  strings, generated by the same `.strings` export planned for phase 7.

### 6.5 Discovery

iOS has no "pin this widget" call. The Moon promo row on the info card becomes **instructions**
("Touch and hold the Home Screen, tap +, search Sky Map"). `WidgetCenter.getCurrentConfigurations`
shows whether one is already placed, so hide the promo then. The widgets sheet text is
platform-specific.

### 6.6 Tests

Models and timeline generation are common code with golden tests. SwiftUI previews and an
`XCUITest` snapshot cover the layouts. The extension's memory is checked in Instruments on the
iPhone 13.

## 7. Shared groundwork, once

Built in slice 1 or 2 and reused after:

1. **`group.io.penterakt.skymap` App Group** and the small shared key store (needed by widgets
   and by notification planning on a cold start).
2. **URL handling** (`skymap://` scheme and, with share-object-card, universal links).
3. **`BGTaskScheduler` registration** in the Swift shell, with a Kotlin entry point per task.
4. **`shared/surfaces`** module and the Android re-wiring on top of it.

## 8. Privacy checklist (same change as each slice)

Per `AGENTS.md`, update `PRIVACY_POLICY.md`, `eula.xml`/`TERMS.md` where relevant, the iOS
`PrivacyInfo.xcprivacy`, and the App Store privacy answers, and say so in the PR:

- Flags: Remote Config on iOS.
- Satellites: the CelesTrak request from iOS.
- Notifications: no new data, but the permission and what the app does with the location to
  schedule.
- Widgets: the App Group shares the saved location between the app and extension on the device
  only.

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `BGAppRefreshTask` rarely runs for light users | Satellite data and plans go stale | Stale-only foreground fetch (§4.1); replan on foreground |
| Second Kotlin framework exceeds the extension's memory limit | Widgets crash or are killed | Spike first: a hello-world extension linking `SkyMapWidgetKit`, measured on device |
| Two Kotlin/Native frameworks per app grow the download | Larger IPA | The extension links only the pure modules; measure |
| iOS cannot hide a widget and has no flag | A half-finished widget is user-visible | Do not release the extension until all three families are complete |
| Android refactor for `shared/surfaces` regresses notifications | Wrong or missing alerts on the larger platform | Move code with golden tests unchanged; slice-by-slice review; no behaviour changes in the same PR |
| 64 pending notification cap | Plans silently truncated | Short horizon; planner caps and logs |

## 10. Decisions and open questions

Decided 2026-11-03/04:

1. **No Remote Config opt-out on iOS**, matching Android and the policy. The flags and
   announcements need it.
2. **Stale-only foreground fetch with a per-install random offset** (§4.1).
3. **Explicit notification prompt at first opt-in**, not provisional authorization (§5.3).

4. **Strings: one `TextSource` for all non-UI text.** Shared logic never reads resources and
   there is no interface per feature (`LayerStrings` and a `NotificationStrings` would only
   multiply). One interface in a low-level shared module, with an enum of ids:

   ```kotlin
   enum class TextId { NOTIF_PASS_TITLE, NOTIF_PASS_TEXT, LAYER_NORTH, /* ... */ }

   fun interface TextSource {
       fun text(id: TextId, vararg args: Any): String
   }
   ```

   - Each platform has one implementation, a `when (id)` mapping ids to resources. Android maps
     to `R.string.x` and iOS to `Res.string.x`. The `when` is exhaustive, so an id with no
     mapping fails to compile.
   - Tests use one fake that returns the id name and its args.
   - The locale stays under the app's control: logic takes a `TextSource` and never reads the
     system locale. It follows the in-app language setting, and a locale change makes it
     re-emit, as `LayerStrings` does now.
   - The strings behind the ids that iOS needs (`channel_*`, `notif_*`, now Android-only in
     `app/res`) move into `shared/ui`'s `values/strings.xml`, where `tm` translates them once
     for both platforms. Android's implementation reads the same copy.
   - `LayerStrings` folds into `TextSource` later, which removes `EnglishLayerStrings` and
     `ResourceLayerStrings` and gives iOS translated layer labels. Nothing forces that now.
   - The widget extension is Swift and cannot read Compose resources. It gets `.strings`
     generated from the same `values-*/` XML (the phase 7 export), the one new mechanism.
   - Text is localised when it is scheduled, so a locale change triggers a replan.
   - Rejected: shared logic calling `getString(Res.string.x)` directly, from a small
     `shared/strings` module. Fewer parts, but harder to test, and the logic would depend on
     resource lookup and the system locale.

Open:

5. Eclipse alerts: in the first notifications release or later?

## 11. Verification

- Each slice: `./gradlew check` plus the iOS simulator tests, and a TestFlight build to the
  external group submitted **early** for Beta App Review (see the port plan's launch blockers).
- Satellites: a device check of the background task and a real CelesTrak fetch, once, from a
  debug build.
- Notifications: schedule on device, confirm exact delivery with the app force-quit.
- Widgets: place each family on the iPhone 13, in light, dark and Lock Screen, and compare with
  the Android mockups in [mockups/stickiness.html](mockups/stickiness.html).
