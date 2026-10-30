# iOS

The iOS side of Sky Map v2. Shared code is Kotlin Multiplatform in the Gradle build one level
up; what lives here is the thin native shell that hosts it.

## SkyMap — the app

Sky Map for iOS (iOS port phase 4). Everything but the Swift shell is Kotlin: `app-ios` (the
`SkyMapKit` framework — the app graph, the iOS platform edges and the Compose Multiplatform
root, D134) over the shared modules. A build phase runs Gradle for the framework and for the
catalog (`:data:generateCatalogDb`), which the bundle carries with the renderer's images.

```bash
cd ios/SkyMap && xcodegen                              # generates SkyMap.xcodeproj
xcodebuild -project ios/SkyMap/SkyMap.xcodeproj -scheme SkyMap \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ios/SkyMap/build build
```

On a device, add `-allowProvisioningUpdates` (the phone unlocked, so Xcode can mount its
developer disk image). Signing is automatic under the Penterakt team (`DEVELOPMENT_TEAM` in
`project.yml`). Debug builds use the bundle ID `io.penterakt.skymap.dev`: a development build
registers its bundle ID to the signing team, and bundle IDs are unique across Apple, so a dev
build must never claim the App Store app's. Release builds (archives) use `io.penterakt.skymap`.

`MapGestureTests` (the scheme's `test`) drags, flings and pinches the map, capturing the sky
after each as attachments (`xcrun xcresulttool export attachments --path <result bundle>`).

### Analytics

Usage analytics is Firebase (GA4, the same property as Android), behind the shared `Analytics`
edge that `FirebaseAnalyticsAdapter.swift` implements. It is on by default with an opt-out in
Settings, as on Android's gms flavor. Download `GoogleService-Info.plist` from the Firebase
console and put it in `ios/SkyMap/` (gitignored); the build bundles it when present. Without it
Firebase stays unconfigured and every call is a no-op. Register both bundle IDs (`.dev` and the
App Store one) in the console, so development events don't land in the production stream. The
`WithoutAdIdSupport` variant is linked, so the advertising ID is never read.

## RendererHarness

The Metal backend (`:render:metal`, `docs/design/render-metal.md`) drawing the shared seeded test
scene: the iOS counterpart of Android's `RendererTestActivity`. Drag to look around, pinch to
zoom, and tap to cycle night sky → daytime sky → twilight → night mode.

The Swift is only the app shell. The view, draw loop and gestures are Kotlin in
`render/metal-harness`, built into the `SkyMapHarness` framework by a build phase that runs
Gradle.

```bash
brew install xcodegen                 # once
cd ios/RendererHarness && xcodegen    # generates RendererHarness.xcodeproj (not checked in)
open RendererHarness.xcodeproj        # then Run on a simulator or device
```

From the command line, for a simulator:

```bash
xcodebuild -project ios/RendererHarness/RendererHarness.xcodeproj -scheme RendererHarness \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath ios/RendererHarness/build build
```

Running on a device needs your signing team set on the target in Xcode.
