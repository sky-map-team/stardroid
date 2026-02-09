# Open Issues Analysis - Sky Map (stardroid)

**Analysis date:** 2026-02-07
**Last revised:** 2026-02-08
**Total open issues:** 98 (as of revision date)
**Repository:** https://github.com/sky-map-team/stardroid

---

## Legend

| Status | Meaning |
|--------|---------|
| CLOSE | Can be closed (resolved, obsolete, duplicate, or invalid) |
| KEEP | Should remain open (valid bug or relevant feature request) |
| DISCUSS | Needs more information or maintainer decision |

---

## 1. Issues that can be CLOSED

### 1.1 Already implemented / resolved in the codebase

| Issue | Title | Reason to close |
|-------|-------|-----------------|
| [#318](https://github.com/sky-map-team/stardroid/issues/318) | Add international space station | **Already implemented.** `IssLayer.kt` exists and tracks the ISS in real time with NASA data. |
| [#478](https://github.com/sky-map-team/stardroid/issues/478) | Feature: Iss tracking | **Already implemented.** Functional duplicate of #318. Full ISS tracking in `IssLayer.kt`. |
| [#357](https://github.com/sky-map-team/stardroid/issues/357) | Um, where's the moon? | **Already implemented.** `Moon.kt` calculates lunar position with Astronomical Almanac ephemerides. 8 phases rendered. |
| [#225](https://github.com/sky-map-team/stardroid/issues/225) | Errors with FloatMath functions | **Resolved.** `FloatMath` has been removed from the main app. Only exists in `tools/` as a wrapper for `Math.*`. |
| [#472](https://github.com/sky-map-team/stardroid/issues/472) | Labels upside-down on screen | **Fixed in v1.10.11.** Root cause was a sign error in the sensor fallback path. Fix in PR #585. |
| [#291](https://github.com/sky-map-team/stardroid/issues/291) | Reverse geocoding blocks UI thread | **Resolved.** `LocationController.java` now runs geocoding on a background thread with `runOnUiThread()` callback. Fixed in v1.10.9 (PR #525). |
| [#269](https://github.com/sky-map-team/stardroid/issues/269) | App doesn't warn when location is disabled | **Resolved.** `getSwitchOnGPSDialog()` in `LocationController.java` shows an AlertDialog offering to enable GPS. `LocationPermissionDeniedDialogFragment` added in v1.10.11 for denied permissions. |
| [#306](https://github.com/sky-map-team/stardroid/issues/306) | Location Permission handling | **Resolved.** Location permission UX was significantly reworked in v1.10.11 (PR #563) with a proper dialog offering Grant/Manual/Later options. Permission mismatch also fixed in v1.10.10 (PR #562). |
| [#265](https://github.com/sky-map-team/stardroid/issues/265) | Updating the .gitignore file | **Resolved.** `.gitignore` has been comprehensively updated with Android, IDE, secrets, OS, and project-specific patterns. |

### 1.2 Obsolete / Outdated

| Issue | Title | Reason to close |
|-------|-------|-----------------|
| [#510](https://github.com/sky-map-team/stardroid/issues/510) | Add comet C/2023 A3 (Tsuchinshan-ATLAS) | **Obsolete.** Comet is no longer visible (Sep-Oct 2024 event). Comet infrastructure exists in `CometsLayer.kt`. |
| [#499](https://github.com/sky-map-team/stardroid/issues/499) | Upcoming recurrent nova of T Corona Borealis | **Obsolete.** Jun 2024 request about a temporary astronomical event. |
| [#485](https://github.com/sky-map-team/stardroid/issues/485) | Add Comet Nishimura | **Obsolete.** Comet Nishimura was visible in Sep 2023. Already passed. |
| [#471](https://github.com/sky-map-team/stardroid/issues/471) | K2 comet | **Obsolete.** Jul 2022 request about a temporary comet. |
| [#178](https://github.com/sky-map-team/stardroid/issues/178) | Add Kepler Space Telescope (fixed position!) | **Obsolete.** Kepler Space Telescope was decommissioned in Oct 2018. No longer in orbit. |
| [#309](https://github.com/sky-map-team/stardroid/issues/309) | Migrate LGTM.com installation from OAuth to GitHub App | **Obsolete.** LGTM.com was discontinued and merged into GitHub Code Scanning. Service no longer exists. |
| [#224](https://github.com/sky-map-team/stardroid/issues/224) | Missing gradle dependencies and classpaths. Build error | **Obsolete.** Refers to `android-15-release-1.8.0` branch. Build system has been completely reworked since then. |
| [#226](https://github.com/sky-map-team/stardroid/issues/226) | Gradle fail while deploying the App in local device | **Obsolete.** Old build error (2019) related to dependency versions long since superseded. |
| [#200](https://github.com/sky-map-team/stardroid/issues/200) | Sky Map doesn't work on Fairphone 2 with Lineage 14.1 | **Duplicate** of #201. Same author, same content. |
| [#201](https://github.com/sky-map-team/stardroid/issues/201) | Sky Map doesn't work on Fairphone 2 with Lineage 14.1 | **Obsolete.** Lineage 14.1 = Android 7.1. App now requires minSdk 26 (Android 8.0). Device no longer supported. |
| [#184](https://github.com/sky-map-team/stardroid/issues/184) | Galaxy J5 AutoMode | **Obsolete.** Samsung Galaxy J5 (2015/2016) runs Android well below minSdk 26. No longer supported. |
| [#509](https://github.com/sky-map-team/stardroid/issues/509) | My issues sky map | **Invalid.** No body, no description, no comments. |
| [#475](https://github.com/sky-map-team/stardroid/issues/475) | UI - when will the UI be updated? (Chinese) | **Obsolete.** Generic question from 2022 with no actionable bug report or proposal. |
| [#316](https://github.com/sky-map-team/stardroid/issues/316) | Time Complexity Can be reduced | **Invalid/vague.** Description contains no actionable technical information. Does not identify specific code or complexity. |
| [#402](https://github.com/sky-map-team/stardroid/issues/402) | Where is the IOS version? | **Not an issue.** Question answered (no iOS version exists). 9 comments. No action needed. |
| [#304](https://github.com/sky-map-team/stardroid/issues/304) | Will not calibrate or find location | **Obsolete.** Vague report from 2019 with no device/version. Calibration and location handling have been significantly reworked since. |

### 1.3 Functional duplicates

| Issue | Title | Duplicate of |
|-------|-------|-------------|
| [#483](https://github.com/sky-map-team/stardroid/issues/483) | Add camera underlay | Duplicate of [#463](https://github.com/sky-map-team/stardroid/issues/463) (Add a camera AR mode) |
| [#596](https://github.com/sky-map-team/stardroid/issues/596) | Phone Mapping For Camera | Duplicate of [#463](https://github.com/sky-map-team/stardroid/issues/463) (Add a camera AR mode) |
| [#543](https://github.com/sky-map-team/stardroid/issues/543) | Add telescope control feature | Duplicate/related to [#398](https://github.com/sky-map-team/stardroid/issues/398) (Sky Map fork with telescope control) |
| [#252](https://github.com/sky-map-team/stardroid/issues/252) | UI glitch in the search screen | Duplicate of [#348](https://github.com/sky-map-team/stardroid/issues/348) (Overflow menu button overlaps cancel search) |
| [#235](https://github.com/sky-map-team/stardroid/issues/235) | While time travelling, close button is hard to hit | Duplicate of [#275](https://github.com/sky-map-team/stardroid/issues/275) (App bar overlap in time travel) |
| [#568](https://github.com/sky-map-team/stardroid/issues/568) | Automating localization | Duplicate of [#464](https://github.com/sky-map-team/stardroid/issues/464) (Investigate using a service for managing localization) |
| [#181](https://github.com/sky-map-team/stardroid/issues/181) | Star names missing | Duplicate of [#506](https://github.com/sky-map-team/stardroid/issues/506) (More star names). Same request, #506 is more recent and actionable. |

---

## 2. Issues that should be KEPT

### 2.1 Valid and reproducible bugs

| Issue | Title | Priority | Notes |
|-------|-------|----------|-------|
| ~~[#595](https://github.com/sky-map-team/stardroid/issues/595)~~ | ~~Buttons overlap last setting item (Android 16)~~ | ~~High~~ | **CLOSED** (2026-02-06). |
| [#569](https://github.com/sky-map-team/stardroid/issues/569) | Lit side of moon is at wrong angle | Medium | Lunar rendering bug. Requires investigation of phase calculation. |
| [#545](https://github.com/sky-map-team/stardroid/issues/545) | Inaccurate North/South | Medium | Sensor calibration bug. Pixel 7 Pro, modern device. |
| [#536](https://github.com/sky-map-team/stardroid/issues/536) | Space Map Not Moving | Medium | Sensor issue. 5 comments with discussion. |
| [#533](https://github.com/sky-map-team/stardroid/issues/533) | Over sensitive / jittery | Medium | 17 comments. Recurring sensor smoothing problem. |
| [#513](https://github.com/sky-map-team/stardroid/issues/513) | ZFold6 viewing in internal screen | Medium | Orientation bug on foldable. Relevant modern device. |
| [#473](https://github.com/sky-map-team/stardroid/issues/473) | Flickering calibration screen | Low | Visual bug on calibration screen. |
| [#349](https://github.com/sky-map-team/stardroid/issues/349) | Night Mode leaves navigation bar bright white | Low | UI bug in night mode. |
| [#348](https://github.com/sky-map-team/stardroid/issues/348) | Overflow menu overlaps cancel search button | Low | UI overlap bug. |
| [#329](https://github.com/sky-map-team/stardroid/issues/329) | Ursa Major appears distorted | Low | Constellation data possibly incorrect. |
| [#315](https://github.com/sky-map-team/stardroid/issues/315) | Size issue in satellite of Neptune | Low | Bug in Neptune satellite data. |
| [#312](https://github.com/sky-map-team/stardroid/issues/312) | Pluto can't be aligned with other planets | Low | Possible error in Pluto position calculation. |
| [#277](https://github.com/sky-map-team/stardroid/issues/277) | TimeTravel dialog Display Crash/Error | Low | Crash in time travel UI. |
| [#275](https://github.com/sky-map-team/stardroid/issues/275) | App bar overlap in time travel | Low | UI overlap bug. |
| [#191](https://github.com/sky-map-team/stardroid/issues/191) | Adding more starnames causes white boxes | Low | Label rendering bug when adding many names. |
| [#190](https://github.com/sky-map-team/stardroid/issues/190) | No magnetic field sensor crash | Low | Crash on startup without magnetic sensor. |

### 2.2 Relevant feature requests

| Issue | Title | Priority | Notes |
|-------|-------|----------|-------|
| [#602](https://github.com/sky-map-team/stardroid/issues/602) | Reassess permissions and privacy | High | New issue. Review of app permissions and privacy practices. |
| [#512](https://github.com/sky-map-team/stardroid/issues/512) | Adaptive icon | Medium | Simple visual improvement. Modern Android standard. |
| [#506](https://github.com/sky-map-team/stardroid/issues/506) | More star names | Medium | Clear and actionable feature request. |
| [#500](https://github.com/sky-map-team/stardroid/issues/500) | Dynamic Shortcuts for recent searches | Medium | Good UX improvement. Modern Android API. |
| [#487](https://github.com/sky-map-team/stardroid/issues/487) | Filter stars not visible to naked eye | Medium | Magnitude filtering. Related to #220. |
| [#482](https://github.com/sky-map-team/stardroid/issues/482) | Material You 3 | Medium | Significant visual modernization. App still uses Theme.Holo. |
| [#404](https://github.com/sky-map-team/stardroid/issues/404) | Save manual locations | Medium | Good UX improvement for frequent users. |
| [#382](https://github.com/sky-map-team/stardroid/issues/382) | Major asteroids (Ceres, Vesta, etc.) | Medium | Natural extension of solar system objects. |
| [#327](https://github.com/sky-map-team/stardroid/issues/327) | Search by coordinates | Low | Useful search improvement for astronomers. |
| [#325](https://github.com/sky-map-team/stardroid/issues/325) | Celestial information (Wikipedia tap) | Low | Contextual information on object tap. Partially addressed by info cards (v1.10.11). |
| [#274](https://github.com/sky-map-team/stardroid/issues/274) | Add new events to time travel | Low | Eclipses and astronomical events in time travel. |
| [#220](https://github.com/sky-map-team/stardroid/issues/220) | Allow filtering by star magnitude | Low | Related to #487. |
| [#249](https://github.com/sky-map-team/stardroid/issues/249) | Constellation pictures hard to visualize | Low | Improvement in constellation rendering. |

### 2.3 Architecture improvements (maintainer roadmap)

| Issue | Title | Notes |
|-------|-------|-------|
| [#460](https://github.com/sky-map-team/stardroid/issues/460) | Complete modernization of Activities | Foundation for several other improvements. Migrate to AppCompatActivity + Material. |
| [#461](https://github.com/sky-map-team/stardroid/issues/461) | Convert from vanilla Dagger to Hilt | Blocked by #460. Would significantly simplify DI. |
| [#462](https://github.com/sky-map-team/stardroid/issues/462) | Modernize what's new and EULA dialogs | Blocked by #460 and #461. |
| [#463](https://github.com/sky-map-team/stardroid/issues/463) | Add camera AR mode | Blocked by #460. Feature desired by many users. |
| [#465](https://github.com/sky-map-team/stardroid/issues/465) | Add auto-calibration | Blocked by #463. Research project. |
| [#464](https://github.com/sky-map-team/stardroid/issues/464) | Service for managing localization | Consolidate translations. Crowdin suggested. |
| [#466](https://github.com/sky-map-team/stardroid/issues/466) | Re-do realistic horizon mode | Advanced visualization feature. |
| [#467](https://github.com/sky-map-team/stardroid/issues/467) | Add basic on-touch information | Name, rise/set times on tap. Partially addressed by info cards (v1.10.11). |
| [#468](https://github.com/sky-map-team/stardroid/issues/468) | Design feeds for interesting events | Notifications for meteor showers, ISS transits, etc. |

---

## 3. Issues to DISCUSS

| Issue | Title | Question |
|-------|-------|----------|
| [#599](https://github.com/sky-map-team/stardroid/issues/599) | Issue triage summary and proposed cleanup | New meta-issue about issue triage. Coordinate with this document. |
| [#532](https://github.com/sky-map-team/stardroid/issues/532) | Catalan Translation | Catalan educational card translations were added in v1.10.11. Check if general UI strings are also needed or if this can be closed. |
| [#477](https://github.com/sky-map-team/stardroid/issues/477) | App DPI too high / font size | Variable font size was added in v1.10.9. Check if this fully addresses the complaint or if further DPI scaling is needed. |
| [#398](https://github.com/sky-map-team/stardroid/issues/398) | Sky Map fork with telescope control | Not an issue/FR. It's a notice about an existing fork. Close or convert to discussion? |
| [#434](https://github.com/sky-map-team/stardroid/issues/434) | Kotlin? Material styles? | Contribution proposal. Kotlin partially adopted. Discuss scope. |
| [#396](https://github.com/sky-map-team/stardroid/issues/396) | A display issue | Layout bug with screenshot. From 2020, check if still reproducible. |
| [#360](https://github.com/sky-map-team/stardroid/issues/360) | Traditional Chinese Google Play description | External issue (Google Play Store listing), not code. Store descriptions were reworked in v1.10.11. |
| [#355](https://github.com/sky-map-team/stardroid/issues/355) | Calibration dialog localization | Calibration dialog was updated in v1.10.4 with translatable text (PR #453). Check if localization is now sufficient or still needed. |
| [#297](https://github.com/sky-map-team/stardroid/issues/297) | Abnormal behavior when Gyroscope disabled | 6 comments. Deprecated orientation sensor. Check if `SensorOrientationController` handles this. |
| [#296](https://github.com/sky-map-team/stardroid/issues/296) | build_skymap.sh as part of gradle | 6 comments. Build system has evolved significantly. Build script was cleaned up in v1.10.11. Verify if still relevant. |
| [#294](https://github.com/sky-map-team/stardroid/issues/294) | Fix needed for PlanetsLayer | From 2019. Check if PlanetsLayer still has the described issue. |
| [#290](https://github.com/sky-map-team/stardroid/issues/290) | Icons required in main.xml | Request for Terms of Service icon. Check if already added. |
| [#287](https://github.com/sky-map-team/stardroid/issues/287) | Fix RealClock for SystemClock | `RealClock.java` still exists and uses `System.currentTimeMillis()`. Valid suggestion to switch to `SystemClock.elapsedRealtime()` but low priority. |
| [#286](https://github.com/sky-map-team/stardroid/issues/286) | Remove Abstract Controller | `AbstractController.java` still exists. Valid issue but requires careful refactoring. Blocked by #460. |
| [#285](https://github.com/sky-map-team/stardroid/issues/285) | Injecting everything in ControllerGroup | Related to #286. Incomplete DI in controllers. |
| [#284](https://github.com/sky-map-team/stardroid/issues/284) | Location Workflow in AbstractGooglePlayServicesChecker | `AbstractGooglePlayServicesChecker.java` still exists. Location permission handling was reworked but this class remains. |
| [#257](https://github.com/sky-map-team/stardroid/issues/257) | App doesn't show proper view | Orientation bug from 2019. Check if still reproducible. |
| [#244](https://github.com/sky-map-team/stardroid/issues/244) | Lat Long prefs reset place name | Preference logic. Check current state. |
| [#240](https://github.com/sky-map-team/stardroid/issues/240) | Location Suggestions in Settings | 4 comments. UX improvement. Feasible? |
| [#233](https://github.com/sky-map-team/stardroid/issues/233) | Check place name before closing dialog | Input validation. Check if already implemented. |
| [#232](https://github.com/sky-map-team/stardroid/issues/232) | Dot invalid in Lat/Long manual input | Numeric input validation. |
| [#231](https://github.com/sky-map-team/stardroid/issues/231) | Different toolbar titles per screen | UI improvement. Check current state. |
| [#234](https://github.com/sky-map-team/stardroid/issues/234) | Tell what the left icons do | Tooltips/descriptions for icons. |
| [#227](https://github.com/sky-map-team/stardroid/issues/227) | Replace deprecated Gallery with RecyclerView | **Confirmed still needed.** `ImageGalleryActivity.java` still uses deprecated `android.widget.Gallery`. |
| [#210](https://github.com/sky-map-team/stardroid/issues/210) | Menu items in black text | Theme bug. May be resolved with Material migration (#460). |
| [#188](https://github.com/sky-map-team/stardroid/issues/188) | No check for Geomagnetic Rotation Vector Sensor | Sensor smoothing. Related to #474 and #533. |
| [#474](https://github.com/sky-map-team/stardroid/issues/474) | Smoothing when gyro is enabled | Related to #533 (over sensitive). |
| [#537](https://github.com/sky-map-team/stardroid/issues/537) | CardBoard VR | Niche feature request. Low priority. |
| [#480](https://github.com/sky-map-team/stardroid/issues/480) | Fixing version name | Issue with Obtainium and version names. Niche. |
| [#470](https://github.com/sky-map-team/stardroid/issues/470) | Matariki star cluster | Add cultural star cluster (Pleiades/Matariki). |
| [#490](https://github.com/sky-map-team/stardroid/issues/490) | Unable to decode location | Geocoding bug. Geocoding was moved to background thread in v1.10.9 and permission handling reworked in v1.10.11. Check if this specific error still occurs. |

---

## Statistical Summary

| Category | Count |
|----------|-------|
| **CLOSE (already implemented/resolved)** | 9 |
| **CLOSE (obsolete/outdated)** | 16 |
| **CLOSE (duplicates)** | 7 |
| **KEEP (bugs)** | 16 (1 already closed: #595) |
| **KEEP (features)** | 14 |
| **KEEP (architecture/roadmap)** | 9 |
| **DISCUSS** | 31 |
| **Total accounted for** | 102 |

> **Note:** 98 issues are currently open. Some issues in the CLOSE category have not been closed yet.
> Issues [#594](https://github.com/sky-map-team/stardroid/issues/594) was closed since the original analysis.
> Issues [#599](https://github.com/sky-map-team/stardroid/issues/599) and [#602](https://github.com/sky-map-team/stardroid/issues/602) were opened after the original analysis.

---

## Recommendations

1. **Immediate action:** Close the 32 issues marked as CLOSE with explanatory comments referencing the specific version or PR that resolved them.
2. **Triage:** Review the 31 DISCUSS issues to verify if they are still reproducible in the current codebase. Priority should be given to issues with code-verified status (e.g., #227 confirmed still relevant, #287 confirmed `RealClock` still exists).
3. **Consolidate duplicates:** Sensor-related issues ([#188](https://github.com/sky-map-team/stardroid/issues/188), [#474](https://github.com/sky-map-team/stardroid/issues/474), [#533](https://github.com/sky-map-team/stardroid/issues/533), [#545](https://github.com/sky-map-team/stardroid/issues/545)) could be consolidated into a single "sensor smoothing/calibration" issue.
4. **Roadmap:** Issues #460-#468 form a coherent modernization roadmap. Consider creating a GitHub Project or Milestone to track progress.
5. **Clean up old issues (2019):** Many issues from #220-#296 were created during a contribution sprint in April 2019. Several have been verified as still open but many may be outdated. Prioritize verification.
