---
name: skymap.analytics
description: Pull and interpret Sky Map GA4 analytics via the analytics-mcp tools — daily health check, iOS/Android trends by day and country, satellite-fetch health, top searches, support-sheet usage, plus deeper dives (funnels, retention, versions, sensors, widgets, announcements). Trigger on "how are our analytics", "check GA4", "iOS usage trend", "satellite requests", "top searches", "is anyone opening the support sheet", "analytics report", or similar.
---

# Sky Map GA4 analytics

Read-only. Uses the `mcp__analytics-mcp__*` tools (load via ToolSearch:
`select:mcp__analytics-mcp__run_report,mcp__analytics-mcp__run_realtime_report,mcp__analytics-mcp__run_funnel_report,mcp__analytics-mcp__get_custom_dimensions_and_metrics`).
If they are missing or return auth errors, tell the user — don't try to work around it.

- **Property:** `242522567` (Firebase project `sky-map-1286`, account "Sky Map"). Property time
  zone is `America/New_York`: "yesterday" is in that zone.
- **Event/param names:** `shared/model/src/commonMain/kotlin/com/google/android/stardroid/analytics/AnalyticsEvents.kt`
  is the source of truth. Grep it before querying an event rather than guessing.
- `platform` values are `Android`, `iOS`, `web` (web is noise — ignore unless asked). fdroid
  builds send nothing, so numbers are gms-only.

## Gotchas (learned the hard way)

1. **Only registered custom dimensions can be queried.** Run
   `get_custom_dimensions_and_metrics` if unsure. Currently registered event params include
   `support_action`, `http_code`, `circuit_opened`, `open_hours`, `search_term`, `search_success`,
   `menu_item`, `object_id`, `layer_name`, `session_bucket`, `sensor_path`, `local_hour`,
   `day_of_week`, `travel_event`, `image_name`, `value`, `enabled`; user prop `device_sensors_prop`.
   **Not registered** (so can't be split by): `search_query`, `widget`, `pin_source`,
   `pin_outcome`, `announcement_id`/`announcement_surface`, `fetch_outcome`, `error_type`,
   `navigation_mode`, `layer_enabled`, `slide_number`, `object_name`, `mode`, `new_version`.
   Event *counts* for those events still work. If a deep dive needs one, say so and suggest
   registering it in GA4 Admin → Custom definitions (the property is not editable by us via
   the API; the user does it in the UI).
2. **Some registered dimensions need a scope suffix**: `customEvent:search_term[search]`,
   `customEvent:menu_item[menu_item_pressed_ev]`, `customEvent:search_success[search]`,
   `customEvent:session_length[session_length_ev]`. Plain `customEvent:search_term` is a 400.
3. **New custom dimensions are not backfilled.** A big `(not set)` bucket for older dates means
   "before the dimension was registered", not a bug. Say so, and report only the dated tail.
4. **Counts of 1–3 are not trends.** iOS volume is small (hundreds of users/day); report
   absolute numbers beside percentages and don't over-read day-to-day noise.
5. Today's and (partly) yesterday's data is still processing — use `yesterday` as the end of
   ranges for trends, and `run_realtime_report` for "right now".
6. satellite_fetch_ok is sampled 1-in-100 (`SATELLITE_FETCH_OK_SAMPLE_ONE_IN`) — multiply by
   100 for an estimate of real successes. `satellite_fetch_failed` is unsampled.

7. **High cardinality is fine via the API.** `search_term` has ~1,400 distinct values/day and the
   Data API returns them all (no `(other)` row, `data_loss_from_other_row: false`). Use `limit`.
8. **Failed searches are a separate event.** They fire `search_failed_ev` (~20% of searches), not
   `search` with `search_success=false` (that filter returns nothing). Query
   `customEvent:search_term[search]` with `eventName == search_failed_ev` — the same dimension
   works there and gives the typed text (~5k distinct terms/week). That is the catalogue-gap list.
9. **Not every `search` is typed.** The Nov 2026 Voyager 1 announcement's action fires a search
   whose term is a coordinate label ("17h 15m, +12.0°", ~1/3 of all searches, one per user,
   Android 2.3.2). Exclude announcement-driven terms before reading "top searches".
   Known/expected failures (don't re-flag): "iss"/"space station" fail until satellites return
   (currently off after the CelesTrak 429 incident); "earth" now resolves (easter-egg message),
   so older failed counts for it are stale; Voyager is being added to the catalogue (Nov 2026).
10. **`satellite_fetch_ok` only exists in builds ≥ 2.4.0 / recent iOS** (added 2026-11-13, after
    Android 2.3.2). Zero rows on Android is expected until 2.4.0 ships.
11. `support_action_ev` spikes (1,656 events on 2026-11-17 vs ~70/day) exist — look at
    unique users before reading the count as engagement.
12. **A date × country query overflows `limit: 200`.** With ~84 iOS countries over 14 days the
    rows are cut off mid-range with no warning (the last days vanish). Never read a per-day
    table from a truncated result. Run the no-`date` top-N query for totals, and a separate
    date × country query filtered to the top ~7 countries (`in_list_filter`) for trends.
13. **`search_failed_ev` has two causes** (`SearchViewModel`): the query matched nothing (a
    catalogue gap), *or* a hit was picked but `resolveDirection` returned null (a real object
    with no position). Nothing in the event tells them apart. A known name (mercury, moon, sun)
    in the failed list means the second path: compare against the `search` count for the same
    term to get a failure rate before calling it a bug. 2026-11 baseline, all Android 2.3.2:
    mercury ~27% of searches failed, moon ~3%, sun ~4%; iOS ~0%. Suggest adding a `reason`
    param to the event if this comes up again.
14. **The Android onboarding funnel mixes new installs with v1→v2 upgraders.** In the week to
    2026-11-21 Android had ~71k users firing `upgraded_to_v2_ev` and ~72k firing
    `TOS_accepted_ev` (upgraders re-accept the terms), against only ~27.7k `first_open` users.
    So an unrestricted TOS → welcome funnel is mostly upgraders and reads ~2%. Always start
    the funnel with a `first_open` step to isolate new installs. iOS has no v1, so it is clean.
    Even restricted to `first_open`, only ~354 of ~23k Android new installs reach
    `warm_welcome_started_ev` *after* TOS, yet ~99k Android users fire it overall. That total
    equals upgraders (~71k) + new installs (~27.7k), so the event fires for everyone. Likely
    cause (unverified): `MainActivity` composes the EULA as an overlay *over* the nav host, so
    the welcome pager can fire `warmWelcomeStarted` before the EULA is accepted, which breaks an
    ordered TOS → welcome funnel. Menu replays are not the cause (`StartupViewModel` says they
    bypass these callbacks). For Android, compare unordered per-event user counts instead.
    `TOS_rejected_ev` fires from the Decline button **and any BACK press** on the EULA
    (`MainActivity` `declineEula`, which also `finish()`es). Android sees ~3.4–4.3k rejecting users
    a day, ~0.85 per new install, steady every day; iOS has no decline button, so no rows. This
    looks inflated (83% of new installs accept within the week); treat the count as unreliable.
    **Fixed in PR 1337** (merged 2026-11-22, not yet released; check the release date):
    `TOS_rejected_ev` fires only from the Decline button (BACK still exits, silently), and
    `warm_welcome_started_ev` / slide events only once the tour is visible. From the first
    release containing it, expect `TOS_rejected_ev` to fall sharply and the Android TOS →
    welcome funnel to become ordered and usable; the numbers above are the pre-fix baseline.
    iOS also does **not emit `start_up_event_ev`** (checked 2026-11-22), so end the iOS funnel
    at `warm_welcome_completed_ev`; missing iOS startup data is not a regression.

15. **Renderer usage.** User property `renderer_backend_prop` (`gles1`/`gles3`, Android, the
    backend actually running after any fallback) is set at each launch, from the first release
    after 2026-11-23 (PR 1364); a user who has not relaunched since has no value, so early
    counts undercount. Register it as a user-scoped custom dimension in GA4 first; it is not backfilled.
    Before then, the only signal is `preference_change_ev` with `customEvent:value` starting
    `renderer_backend:`, which counts switches only, not users on the default.

## Standard check-in (default when the user just says "how are our analytics")

Run these in parallel, then write a short narrative — numbers first, then one line of
interpretation each. Lead with anything that looks wrong.

**1. Headline, by platform and day (last 7–14 days)**
`dimensions: [date, platform]`, `metrics: [activeUsers, newUsers, sessions]`, order by date.
Show Android and iOS as small tables or sparklines; call out day-over-day and week-over-week
change. Android is ~45–50k DAU; iOS is hundreds and growing — judge each against its own baseline.

**2. iOS by day and country**
Run the **no-`date` query first**: `dimensions: [country]`, `metrics: [activeUsers, newUsers,
sessions, userEngagementDuration]`, filter `platform == iOS`, last 7 days, order by `activeUsers`
desc, `limit: 15`. Engagement per user = `userEngagementDuration / activeUsers`. Then the trend:
`dimensions: [date, country]`, `activeUsers`, with an `in_list_filter` on the top ~7 countries
(see gotcha 12 — an unfiltered date × country query truncates). Flag any country that jumped
(launch/press/featuring effect) or collapsed. 2026-11-14..20 baseline: US ~640 users (~80% of
iOS, 571 new), UK 77, India 40, Italy 37, Canada 36, Spain 35.

**3. Android health**
Same as 1 for Android plus: `dimensions: [date]`, `metrics: [crashFreeUsersRate]` is *not*
available here — crash data lives in Crashlytics, say so rather than guessing. Instead use
`session_length_ev` by `customEvent:session_bucket` (engagement shape) and
`appVersion` by `activeUsers` (version adoption of the latest release).

**4. Satellite fetches** (the CelesTrak-friendliness check; "hopefully requests have gone down")
- `eventName in [satellite_fetch_ok, satellite_fetch_failed, satellite_circuit_closed]`,
  `dimensions: [date, eventName]`, `metrics: [eventCount, activeUsers]`, last 14 days.
  Estimated successful fetches = `satellite_fetch_ok` × 100. Compare to the user count: fetches
  per active user per day should be roughly flat or falling; a rise means a retry loop.
- Failures by `customEvent:http_code` (date × code). Anything that isn't a small trickle of
  5xx/timeouts matters — **429/403 means CelesTrak is rate-limiting or blocking us**, escalate.
- `satellite_fetch_failed` split by `customEvent:circuit_opened` and the `open_hours` of
  `satellite_circuit_closed`: circuits opening means the backoff is working; many long-open
  circuits means a sustained outage.
- Cross-check with `tools/satellite-alerts/` (the alert, a Google Apps Script deployed by hand)
  if the user wants to know why it did or didn't fire: thresholds are at the top of `Code.gs`,
  blind spots in its README. `tools/satellite_alerts.py` (the GitHub Action) is deferred and
  unconfigured: its green runs check nothing. Don't suggest a GA4 custom insight for a single
  event: insight segments can't filter on event name or custom dimensions.

**5. Top searches**
`customEvent:search_term[search]` × `eventCount` where `eventName == search`, last 7 days,
limit 25. Then the same filtered on `customEvent:search_success[search] == false` for the
**failed searches** — those are the catalogue gaps (candidates for `skymap.add-object`).
Also `search_failed_ev` and `search_query_error_ev` counts per day; the latter should be ~0.
Compare iOS vs Android top terms if volume allows (`platform` dimension). For a suspicious
known name in the failed list, split `search_failed_ev` by `platform` × `appVersion` and compare
with the `search` count for the same term (gotcha 13).

**6. Support sheet (esp. iOS)**
- *Opens:* `eventName == menu_item_pressed_ev`, `customEvent:menu_item[menu_item_pressed_ev] ==
  support_opened`, by `date` × `platform`, with `eventCount` and `activeUsers`.
- *Actions taken:* `eventName == support_action_ev` by `customEvent:support_action`
  (`github_star`, `issues`, `share`, `bmac`, `tip`, `rate`) × `platform`. See gotcha 3 for `(not set)`.
- Conversion: users who took an action ÷ users who opened the sheet. Report "no evidence" if the
  iOS count is zero, but also check that iOS actually emits the event (compare to a sibling
  menu item such as `help_opened` on iOS before concluding "nobody uses it").

## Suggested extras (offer these; run on request or when the check-in looks interesting)

- **Onboarding funnel** — `run_funnel_report`: `TOS_accepted_ev` → `warm_welcome_started_ev` →
  `warm_welcome_slide_viewed_ev` → `warm_welcome_completed_ev` → `start_up_event_ev`; split by
  platform. Also `TOS_rejected_ev` and `warm_welcome_skipped_ev` rates. Biggest lever for iOS growth.
  See gotcha 14: begin with a `first_open` step, and on iOS stop at `warm_welcome_completed_ev`. Use
  `funnel_breakdown: {"breakdown_dimension": "platform"}`; each step's `activeUsers` is followed by
  the completion rate to the *next* step. 2026-11-14..20 iOS baseline: 1,056 accepted TOS → all
  1,056 started the welcome → 843 completed (80%); 201 skipped.
- **v1 → v2 conversion (trend)** — the question is how fast v1 users move to v2.
  - *Conversions per day:* `eventName == upgraded_to_v2_ev`, `dimensions: [date]`,
    `metrics: [activeUsers]`, last 28 days. Use **`activeUsers`, not `eventCount`**: the event
    fires on every arrival at the welcome while the tour is unfinished, so counts run ~1.3×
    users.
  - *Population:* Android `appVersion` beginning `1.` (`string_filter` `match_type: 2`) vs `2.`
    by `date`, `activeUsers`. Report v2 share = v2 / (v1 + v2) and the trend, not one day.
  - *Where they came from:* `app_update` by `customEvent:previous_app_version[app_update]`.
  - 2026-10-25..11-20 baseline: ~9–13k `upgraded_to_v2_ev` users/day (flat, ~10k typical),
    v1 DAU ~3.3–5k, v2 DAU ~31–48k (the ~10k/day conversions are far above v1 DAU because
    most v1 installs open the app only occasionally). 2026-10-29 was a rollout spike (~20k
    conversions, v1 DAU 7k, v2 DAU 76k).
  - **Step change expected once PR 1337 (merged 2026-11-22) is released.** Until then the
    event also fired under the EULA overlay,
    so it counted users who then declined or backed out, and re-counted them on each relaunch.
    Once the fix is out the daily number drops with no real change in behaviour. Mark the
    release date when quoting a trend across it; do not read the drop as lost conversions.
- **Retention / stickiness** — DAU/MAU (`active1DayUsers / active28DayUsers`) by platform, and
  a cohort view (`cohortSpec` isn't exposed by the MCP; approximate with `newUsers` this week vs
  `returning` via `activeUsers - newUsers`).
- **Release adoption & v1→v2 migration** — `appVersion` × `activeUsers` by day;
  `upgraded_to_v2_ev` per day (and `customEvent:previous_app_version[app_update]` on `app_update`).
  Shows how fast a new release rolls out and whether anything regressed after it.
- **Sensor health** — `start_up_event_ev` by `customEvent:sensor_path`
  (`rotation_vector` / `accel_mag` / `none`) and `no_sensors_warning_ev`,
  `calibration_toast_shown_ev`, `calibration_auto_triggered_ev` per day. A rising `none` or
  calibration rate after a release is a sensor-fusion regression signal. Split by device model
  (`mobileDeviceModel`) to find specific bad phones.
- **Feature adoption** — `menu_item_pressed_ev` by `menu_item` (gallery, time travel,
  widgets, diagnostics, night mode…), `layer_toggled_ev` by `customEvent:layer_name`,
  `time_travel_used_ev` by `customEvent:travel_event`, `object_info_viewed_ev` by
  `customEvent:object_id` (top info cards), `gallery_image_viewed_ev` by `customEvent:image_name`.
  Tells you what to invest in and which layers nobody toggles.
- **Widgets** — `widget_pin_requested_ev` vs `widget_added_ev` vs `widget_removed_ev` per day
  (see the caveat in `AnalyticsEvents.kt` about what each counts). Net adds = added − removed.
- **Announcements** — `announcement_shown_ev` vs `announcement_dismissed_ev` per day after a
  `skymap.push-announcement` publish: reach and dismiss rate (event counts only; id/surface aren't registered).
- **Session depth** — `session_length_ev` by `customEvent:session_bucket`, and
  `local_hour`/`day_of_week` on `start_up_event_ev` (when people stargaze — useful for
  timing announcements and notifications).
- **Time-of-day / geography for announcements** — country × `local_hour`, to pick when an
  aurora or eclipse push should land.
- **Experiment flags** — `experiment_fetch_requested_ev` counts (outcome isn't registered).
- **Nav mode (issue #1033)** — `start_up_event_ev` `navigation_mode` is unregistered; flag it if
  the user revisits that decision.
- **Real-time spot check** — `run_realtime_report` by `platform`/`country`/`eventName`
  after a release, an announcement or a press mention.
- **Period comparison** — pass two `date_ranges` (this week vs last week) to any report above
  to get deltas in one call.

## Presenting results

- Keep it short: a headline sentence, then per-section bullets/tables. Distinguish measured
  GA4 numbers from your own estimates (e.g. ×100 for sampled satellite successes).
- If the user wants a chart, use the `dataviz` skill / `mcp__easel__chart`; for something they
  will revisit, offer a dashboard artifact instead of re-pasting tables.
- End with 2–3 concrete follow-ups (things to investigate or ship), not a restatement.
- Don't put user-level or personal details in anything you publish; the data is aggregate
  only, keep it that way. Search terms are user-typed text — summarise, don't paste verbatim
  anything that looks personal.
