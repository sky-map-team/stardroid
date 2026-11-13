# Background network audit

**Status:** reference/living. Written 2026-11-12 for #1276, part of the CelesTrak incident
(#1269). Code as of `master` at 9773db5e (Android 2.3.2, iOS 2.0.2). Nothing here changes code.

## Why

The satellite download scaled with *installs*, not use: its WorkManager job was scheduled from any
process start (a reboot is enough, because `PassAlertBootReceiver` is declared for every install),
so installs nobody opened still fetched every 12 hours, against a small third-party host. This
audit lists every request the app can make and asks the same questions of each. The download
itself is covered by #1272 (active users only) and #1277 (back-off), so it is only summarised.

## Inventory

"Unused installs" means: can it run for an install nobody opens, e.g. after a background-only
process start (reboot, widget update, a WorkManager wake-up)? "Scale" means whether the host is
built for install-scale traffic.

| # | Request | Host | Trigger | Frequency | Gates (default) | Unused installs? | Scale |
|---|---|---|---|---|---|---|---|
| 1 | Remote Config fetch at process start (Android gms) | Firebase | `RemoteConfigExperimentConfig` init, built by Hilt in `SkyMapApplication.onCreate` | Once per process start, client-throttled to 12 h | None. Flavor gms only. F-Droid has no server | **Yes**: any process start, including reboot and widget/worker wake-ups | Yes |
| 2 | Announcement refresh | Firebase (Remote Config) | `AnnouncementWorker`, periodic 12 h, `NetworkType.CONNECTED`, scheduled from `onCreate` | 12 h, and the SDK throttle makes it ≤1 fetch per 12 h, shared with #1 | `ANNOUNCEMENTS` flag (shipped default off) **and** `announcementsEnabled` (default on) | **Yes** once the flag is on: the job survives with no use | Yes |
| 3 | Remote Config fetch (iOS) | Firebase | `RootView` creation, i.e. the app is opened | Once per app open, SDK-throttled to 12 h | None | No: foreground only. A `BGAppRefresh` launch only reads stored values (`prepare`) and does not fetch | Yes |
| 4 | Firebase Analytics upload | Google (Firebase) | The SDK's own scheduler | SDK-controlled, batched | Settings opt-out (default on); policy covers pre-terms automatic events | **Yes**: SDK uploads from any process | Yes |
| 5 | Satellite download, Android | celestrak.org | `SatelliteRefreshWorker`, periodic 12 h, `CONNECTED`, 0–5 min jitter | ≥2 h per install via breaker; 12 h job | `SATELLITES` flag (default off, since 2.3.2), consent (gms default **true**, F-Droid false), Satellites layer (default off). All re-checked in the worker | Was **yes** (the incident). Now only if all three gates are on | **No** |
| 6 | Satellite download, iOS background | celestrak.org | `BGAppRefreshTask`, re-armed on every background transition and by the task itself | System-chosen, ~12 h earliest + 0–1 h jitter | Flag (server-controlled, default off), consent, layer, all checked in `refreshSatellitesInBackground` | Only for installs the OS still wakes, and gated as #5 | **No** |
| 7 | Satellite download, iOS app open | celestrak.org | Foreground **and** layer on | When cache is >3 days old plus a per-install offset (0–24 h) | Flag, consent, layer | No: foreground, layer on | **No** |
| 8 | Satellite refresh button | celestrak.org | User taps Refresh (Android and iOS) | Policy-limited (breaker, 2 h minimum) | Layers sheet card, flag | No: user present | **No** |
| 9 | Location map image | maps.geoapify.com | User opens the location sheet (Coil on Android, URL cache on iOS) | Per distinct rounded position; cached with no expiry | Needs the screen open; no flag | No: user present | **No**: shared API key in the app |
| 10 | Links (calibration video, store, website, privacy page) | youtube.com, getskymap.app, stardroid.app, ts.stardroid.app, privacy.getskymap.app, store pages | User taps; opens the system browser | n/a | n/a | No | n/a; not app traffic |
| 11 | Tips (iOS) | Apple | User makes a purchase | n/a | n/a | No | Yes |

**Checked and network-free:** widget refresh (`WidgetRefreshWorker`, 12 h and midnight), the
shower and digest planner and poster, the eclipse planner, the pass-alert alarm and its
receivers, `WidgetClockChangeReceiver`, `PassAlertBootReceiver` (`onReceive` is a no-op). They use
the offline ephemeris and the cached element sets. They matter here only because they, plus the
boot receiver, are the reasons the process starts on an install nobody is using. All of their
opt-ins default off and the widgets exist only if placed. There is no FCM, Crashlytics,
Performance or in-app-update SDK. The only dependencies on the network are `firebase-analytics`
and `firebase-config` (gms), Coil and the two CelesTrak clients.

## Findings

Severity is about risk to a small third party or to the project, not about a bug in a single run.

**1. Satellite download (high; tracked).** Rows 5-8. Fixes are in #1272 (download only for active
users) and #1277 (back-off on silent drops), with #1275 (faster kill switch) and #1273 (alerting).
Two points for those issues to keep in view: gms still ships `SATELLITE_DATA_DEFAULT = true`, so
consent is not a real gate there until the layer-off default and the flag carry it; and #1272's
"while a widget or pass alert needs the data" is a new background-network path. It should reuse
the same active-user gate rather than get its own schedule.

**2. A reboot starts the process on every install (medium, structural).** `PassAlertBootReceiver`
is declared unconditionally, so `SkyMapApplication.onCreate` runs on every boot of every install,
and everything in it re-schedules. That is how rows 1, 2 and 5 reached unused installs. The
receiver is needed only for people with pass alerts. *Proposed fix:* enable the receiver with
`PackageManager.setComponentEnabledSetting` only while pass alerts are armed, and disable it
otherwise. This removes the main
background-only start. It is a behaviour change to the manifest, so it is proposed here, not made.

**3. Scheduling is not gated on terms or recent use (medium).** Announcement and satellite jobs
are scheduled from `onCreate` before the terms are accepted, and nothing records "last used".
*Proposed fix:* one shared "active user" signal (last foreground time, set after terms acceptance)
that any new non-Firebase network job must check. #1272 will need this for satellites. Build it
once there and use it for any future third-party host.

**4. Announcement worker keeps unused installs on the network (low).** Row 2. It is correct and
cheap, since it goes to Firebase and the SDK throttle caps it at one fetch per 12 h, but it is the
one job that makes an unused install keep calling out forever once `ANNOUNCEMENTS` is on. It also
doubles as the channel the kill switches arrive on, so that is arguably a feature. *Why fine:*
Firebase is built for this and the throttle comment in `RemoteConfigAnnouncementSource` says not
to lower it. *Guard:* it must never be pointed at a non-Firebase host without the finding-3 gate.

**5. iOS background task re-arms itself even when it has nothing to do (low).** Row 6. The gates
stop any request, so there is no network load, but the chain never ends and each wake loads the
shared graph. *Proposed fix:* skip `schedule()` when the stored flag or consent is off. Leave
it for #1272, which already touches this path.

**6. Geoapify key and quota (low-medium).** Row 9. Requests follow real use, not installs, are
cached with no expiry and rounded to ~110 m to limit distinct URLs, so volume is bounded by people
choosing a location. The risk is different: one key is shared by every install and its quota
belongs to a small provider. *Proposed fix (not code):* confirm the plan's request quota and alert
threshold in the Geoapify dashboard, and keep an eye on it after the iOS push.

**7. F-Droid has no remote kill switch (informational).** F-Droid builds make no background
request: Firebase is absent, satellite consent defaults off, and the layer starts off. The only
protection for them is the code default, so any future background feature that talks to a
non-Firebase host needs a conservative code default, not just a flag.

## Rule for new background network work

A request made without the user present should go to Firebase/Google, or else (1) be behind a
remote flag whose shipped default is off, (2) require an opt-in that defaults off, (3) require
recent use (finding 3), (4) be jittered and rate-limited per install, and (5) be sized against
installs, not users, with the host's operator told first.
