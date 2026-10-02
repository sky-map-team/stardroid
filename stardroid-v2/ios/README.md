# iOS

The iOS side of Sky Map v2. Shared code is Kotlin Multiplatform in the Gradle build one level
up; what lives here is the thin native shell that hosts it.

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
