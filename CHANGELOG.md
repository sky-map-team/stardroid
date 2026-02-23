# Changelog

All notable changes to Sky Map are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.11.1] - 2026-02-23

### Added
- **Credits dialog** — new entry in the main menu lists sponsors and contributors, rendered in the
  same WebView as Help with full night-mode support
- **Manual compass offset** — a new setting lets users nudge the compass heading by a fixed number
  of degrees, providing a workaround when a device's magnetometer is consistently biased
  (addresses [#632](https://github.com/sky-map-team/stardroid/issues/632))
- **New Time Travel events** — Perseids, Leonids, Geminids, New Moon, and other notable 2026
  celestial events added to the quick-pick list
  (addresses [#274](https://github.com/sky-map-team/stardroid/issues/274))
- Time Travel events can now automatically set the search target to the event's sky location
- **Dynamic Time Travel button** — shows "Start from Now" when the dialog opens (making it obvious
  you're about to start time travel from the current moment), and switches to "Go!" once a specific
  time or event is selected
- **Info cards in automatic mode** — an optional preference now allows the educational info cards
  to appear in automatic (sensor) mode, not just manual mode
  (addresses [#594](https://github.com/sky-map-team/stardroid/issues/594))
- **Compass accuracy troubleshooting** section added to the help text, explaining hard/soft iron
  distortions, what the figure-8 calibration actually does, and why a calibrated compass can still
  drift
- CONTRIBUTING.md with build instructions, coding style, and submission workflow
- SECURITY.md with vulnerability reporting policy
- CHANGELOG.md following Keep a Changelog format
- docs/ARCHITECTURE.md with module structure, rendering pipeline, and Mermaid diagram
- docs/design/README.md index for design documents
- GitHub issue templates (bug report and feature request) as YAML forms
- Changelog update instructions in CONTRIBUTING.md and PR template

### Changed
- Menu items reordered to better reflect typical usage frequency; Time Travel icon promoted to the
  action bar so it's always one tap away
- Calibration dialog text rewritten for clearer guidance on when and how to calibrate
- Korean translations restored
- Play Store builds now upload AAB bundles instead of APKs, enabling Play Asset Delivery
  (addresses [#618](https://github.com/sky-map-team/stardroid/issues/618))
- Unnecessary permissions removed from the F-Droid variant; incomplete ISS tracking layer disabled
  pending a full implementation
  (addresses [#602](https://github.com/sky-map-team/stardroid/issues/602),
  [#535](https://github.com/sky-map-team/stardroid/issues/535))
- CI configuration overhauled: Gradle dependency caching added, emulator tests run on a supported
  API level, and flaky retry logic improved
- Migrated design documents from `designdocs/` to `docs/design/`
- Enhanced tools/README.md with module overview
- Modernized PR template with type-of-change checklist
- Updated CLAUDE.md reference to `docs/design/sensors.md`

### Removed
- Old single-file `.github/ISSUE_TEMPLATE.md` (replaced by YAML forms)

### Fixed
- `NullPointerException` crash in `VertexBuffer.addPoint`
- `IllegalStateException` when a dialog was shown after the activity had already saved its state
- `ConcurrentModificationException` in `PolyLineObjectManager` during concurrent rendering updates
- Moon angle calculation now correctly accounts for the Earth's axial tilt, so the lit side of the
  Moon is shown at the right angle
  (fixes [#569](https://github.com/sky-map-team/stardroid/issues/569))
- Several Messier catalog entries were misclassified, causing objects such as open clusters to
  render as featureless points; catalog data and fallback type logic corrected
- Navigation buttons overlapping layer controls on Android 15+ devices using three-button
  navigation, caused by the new mandatory edge-to-edge rendering
  (fixes [#595](https://github.com/sky-map-team/stardroid/issues/595))

## [1.11.0] - 2026-02-04

_Venus release._

### Changed
- Info cards now enabled by default for all users
- Info card preference key renamed to fix issue with alpha testers
- Updated dialog framework for improved stability
- Archived store listing experiments

### Fixed
- Crash caused by missing custom base class in activities
- Broken HTML in store listing text
- Flaky connected tests by upgrading dialog framework
- Import cleanup across multiple files
- Test stability improvements

## [1.10.11] - 2026-02-03

_Venus release._

### Added
- Educational info cards — tap objects in manual mode for fun facts and scientific data
- Educational card translations: Swedish, Danish, Catalan, Hungarian, Indonesian, Hindi, Thai
- Portuguese translations for additional stars, constellations, and Messier objects
- Polish translation update (@Aga-C)
- Russian translations restored (previously overwritten by Ukrainian)
- French translation via Play Store experiments
- Automatic store description translations for missing languages
- Restored Google Analytics with usage tracking for info cards
- Sensor dataflow design documentation

### Changed
- Location permissions dialog now shows a clear prompt instead of the old toast
- Help page restyled with night mode support
- Non-English locales updated to new help format
- Store description simplified and reformatted
- ObjectInfo made Parcelable to survive configuration changes
- Fastlane configuration for Play Store deployment
- Acknowledgments updated to include open source contributors

### Removed
- Unused `LocationPermissionRationaleFragment` and related strings
- Google copyright notices from newly created files

### Fixed
- Upside-down labels caused by sign error in sensor fallback path
- Greek translation corrections (@VasilisKos)
- HTML issues in translated store descriptions
- Layout issues making it hard to close the search box
- Flaky `SplashScreenActivityTest`

## [1.10.10] - 2026-01-20

_Mercury release._

### Added
- New Slovak translation — strings, arrays, help, celestial objects (@MilanSL)
- Updated Portuguese translations (@hugomg)
- Messier object icon rendering with user preference toggle
- Font size preference now takes effect immediately without restart
- Strings for showing Messier images
- CLAUDE.md documentation
- Russian translations restored (overwritten by Ukrainian in earlier commit)

### Changed
- Culled unused languages
- Korean language support removed due to KISA regulatory burden
- Target SDK updated to comply with latest Play Store policy
- Edge-to-edge UI fixes for Android 15 enforcement
- Settings activity flattened to work with new insets handling
- Landing page URL updated to custom domain

### Fixed
- Greek translation corrections (@VasilisKos)
- Permission requests now match the manifest (location dialog silently failed)

## [1.10.9] - 2025-01-27

### Added
- Variable font size support with multiple size options
- Font size preview in preference dialog
- Greek translation updates (@VasilisKos)
- Norwegian translation updates
- Troubleshooting documentation

### Changed
- Improved night mode with options: classic dimming, very dim, or system default
- Location work moved to background thread (most common ANR cause)
- Upgraded just about everything — Gradle files rewritten, dependencies updated
- CI configuration updates for Java 17

### Fixed
- Long label rendering issues
- Location bug causing diagnostics activity crash (NPE)
- Broken badges in README
- Links in help text updated from HTTP to HTTPS

## [1.10.4] - 2024-11-30

### Added
- Telescope/pointer mode — view sky as sighted along phone's edge
- Updated calibration dialog with new video and translatable text
- Norwegian translation updates
- Updated Polish translation (@Aga-C)
- Updated Japanese translation
- Spanish translation updates (@VegaDark)
- Updated Turkish translation
- North American eclipse event with NASA path image
- ISS tracking layer

### Changed
- Sensor code updated from deprecated `SensorListener` to `SensorEventListener`
- Major Kotlin conversion — application module, search, touch, utils packages
- Dependency updates and warning fixes
- Ephemeris code refactored
- Planet calculations and related math refactored
- Layer code simplified
- Sources code refactored
- Play Store mandatory compliance updates

### Fixed
- Search control bar moved out of time travel layout
- Sync issues and NPE crash on start

## [1.10.0] - 2021-12-21

_Comet Leonard release._

### Added
- Comet Leonard tracking with visibility dates and search support
- Farsi (Persian) translation
- Buy Me a Coffee button in README
- GitHub Actions CI workflow for unit tests
- Cirrus CI configuration
- Basic instrumentation tests
- F-Droid metadata

### Changed
- Major refactoring and partial conversion to Kotlin
- Removal of significant duplicated code
- Big package restructuring
- Updated Polish translation (@Aga-C)
- Spanish translation updates
- Greek translation updates
- Updated EULA and What's New text

## [1.9.7] - 2020-12-19

### Added
- Persian translations

### Changed
- Norwegian translation updates
- Upgraded Gradle plugin version

### Fixed
- Reverted broken `SensorEventListener` change that caused issues

## [1.9.6] - 2020-12-14

### Added
- Chinese translation (Traditional and Simplified) including Jupiter-Saturn conjunction
- Spanish translation updates
- Great Conjunction added to Time Travel
- Czech/Slovak translation updates
- Play Store listing descriptions

### Changed
- Revamped data file generation pipeline, breaking the circular dependency that made Sky Map hard to build
- Simplified build script
- Optimized resources and asset sizes
- Updated protocol buffer version library
- Upgraded plugins and Gradle wrapper
- Converted tests to Truth and Kotlin
- ISS tracking layer code

### Fixed
- Jupiter/Saturn ordering
- Crash from out-of-sync data files
- Crash from version number stored as long
- Connected test reliability on Travis
- Unescaped apostrophes in EULA

## [1.9.5] - 2020-09-23

### Added
- Welsh language support (thanks Cymrodor)
- Norwegian translation updates (credit: FTNo)
- Declination line labels
- Restored Firebase Analytics

### Changed
- Declination lines now 10 degrees apart instead of 9
- Shortened the End User License Agreement
- Made the calibration dialog less aggressive and self-closing
- Moved time travel dialog controls to the bottom to avoid clashing with the Android UI
- Migrated to AndroidX
- Upgraded to target SDK 30
- Upgraded Gradle configurations and dependencies
- Protobuf loaded from Maven instead of bundled JARs

### Removed
- Temporary data hack for Neowise
- Obsolete translations causing build warnings

## [1.9.4] - 2020-07-17

_Neowise release._

### Added
- Comet Neowise location tracking

[Unreleased]: https://github.com/sky-map-team/stardroid/compare/v1.11.0...HEAD
[1.11.0]: https://github.com/sky-map-team/stardroid/compare/v1.10.11...v1.11.0
[1.10.11]: https://github.com/sky-map-team/stardroid/compare/v1.10.10...v1.10.11
[1.10.10]: https://github.com/sky-map-team/stardroid/compare/1.10.9...v1.10.10
[1.10.9]: https://github.com/sky-map-team/stardroid/compare/1.10.4...1.10.9
[1.10.4]: https://github.com/sky-map-team/stardroid/compare/1.10.0-RC3...1.10.4
[1.10.0]: https://github.com/sky-map-team/stardroid/compare/1.9.7-RC1...1.10.0-RC3
[1.9.7]: https://github.com/sky-map-team/stardroid/compare/1.9.6-RC6...1.9.7-RC1
[1.9.6]: https://github.com/sky-map-team/stardroid/compare/1.9.5-RC3b...1.9.6-RC6
[1.9.5]: https://github.com/sky-map-team/stardroid/compare/1.9.4-neowisehack...1.9.5-RC3b
[1.9.4]: https://github.com/sky-map-team/stardroid/releases/tag/1.9.4-neowisehack
