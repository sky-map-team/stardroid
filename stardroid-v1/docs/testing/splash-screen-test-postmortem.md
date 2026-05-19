# Splash Screen Test Post-mortem

This document records why `SplashScreenActivityTest` was abandoned and replaced with
`StartUpTest`, and what was learned about Espresso, UiAutomator, and CI emulator images in the
process. The source material is PR #881 (abandoned) and PR #883 (merged).

## Background

`SplashScreenActivityTest` was an Espresso-based instrumented test covering the new-user
startup path: EULA dialog → splash fade animation → warm welcome slides → What's New dialog.
It was written before Android 15 (API 35) introduced mandatory edge-to-edge enforcement and
before the project upgraded its CI target to API 36.

## What broke and why

### 1. Espresso requires window focus — AOSP API 36 emulators don't provide it

Espresso's `perform()` and `check()` methods assert that the target window has focus before
dispatching any action. On Android 15+ (API 35+) the OS enforces edge-to-edge layout, which
prevents `AlertDialog` windows from acquiring focus in the normal way. On the AOSP system
images used in CI, this extended to *all* windows: `has-window-focus=false` was set even on
regular activity windows. The result was `RootViewWithoutFocusException` on every Espresso
call, including `inRoot(isDialog())` and plain `onView(...)`.

This is **not** a problem with Espresso itself, and it is **not** a problem on production
devices or on Google-image emulators. It is specific to the bare AOSP system images (`default`
/ `aosp_atd` target in `android-emulator-runner`).

### 2. Choreographer requires window focus — animations never fired

`View.startAnimation()` depends on Choreographer to deliver vsync callbacks. Without window
focus, Choreographer never fires, so `AlphaAnimation.onAnimationEnd` is never called, so
`proceedToNextActivity()` is never reached, so `WarmWelcomeActivity` never starts.

This was masked on real devices and Google-image emulators where vsync works normally. The CI
AOSP image exposed it.

### 3. UiAutomator's accessibility tree is incomplete on API 35+

After Espresso was replaced with UiAutomator, a second problem appeared: `WebView` nodes are
not reliably present in the Android accessibility tree on API 35+. `By.res("eula_webview")`
and `By.res("whatsnew_webview")` never matched even when the dialogs were visible. Similarly,
`android:id/button1` and `android:id/button2` (the standard positive/negative button IDs for
`AlertDialog`) are absent on the AOSP API 36 image, which uses a Material Design 3
`AlertDialog` with different internal IDs.

UiAutomator is the correct tool for cross-process interactions (system permission dialogs,
notifications, the launcher). For in-app `DialogFragment`s driven by your own `FragmentManager`
it adds unnecessary fragility.

### 4. The custom animation module created an unsolvable timing race

To work around the Choreographer problem, `SplashScreenTestModule` was introduced to inject a
zero-duration `ImmediateFadeoutAnimation` via Hilt's `@TestInstallIn`. The intent was that
`onAnimationEnd` would fire immediately, letting `proceedToNextActivity()` complete before
Espresso/UiAutomator tried to interact with the next screen.

This caused a different failure: if the animation fired during `setAnimationListener()` (which
is called inside `onCreate`), `finish()` was called before the test body ran at all —
"Cannot run onActivity since Activity has been destroyed already". Deferring the fire to
`setStartTime()` (called by `View.startAnimation`) via `Handler.post` avoided that, but
introduced a new race: whether the posted callback executed before or after
`waitForIdleSync()` returned depended on scheduler timing that varied between API levels and
emulator speeds. The interaction between test-thread timing, main-looper state, and animation
callbacks could not be made deterministic in this structure.

### 5. ViewPager2 slide transitions are asynchronous on API 36

Even with animations disabled, `ViewPager2` fragment transitions are asynchronous. The
welcome slide fragments (`welcome_slide_2_root`, `welcome_slide_3_root`) were not yet in the
view hierarchy when Espresso or UiAutomator checked for them after a swipe, causing the checks
to fail intermittently.

### 6. A pre-existing Hilt build error blocked the whole test suite

`ExperimentConfigTestModule` used `@TestInstallIn(replaces = ExperimentConfigImpl::class)`,
pointing at an implementation class rather than a `@Module`. Hilt requires `replaces` to
name a `@Module`-annotated class. This was a compile-time error that silently blocked all
instrumented tests from building. The fix was to extract a dedicated `ExperimentConfigModule`
and point `replaces` at that.

## What PR #883 did differently

`StartUpTest` replaced the entire file rather than patching it. Three decisions made it work:

**Drive dialogs through FragmentManager, not the UI layer.**
`ActivityScenario.onActivity { }` runs on the main thread with no window-focus requirement.
Finding a fragment by tag and calling `dialog.getButton(which).performClick()` is reliable
across all API levels and emulator images. It does not require the accessibility tree, vsync,
or window focus.

**No custom animation injection.**
Because the EULA is accepted by calling `dialog.getButton().performClick()` directly — not by
clicking a UI element that triggers a state machine — the splash animation fires *after* the
test interaction, not during it. Tests that need `WarmWelcomeActivity` to appear simply wait
with a generous `SPLASH_TIMEOUT_MS`; the real animation runs at normal speed.

**Track lifecycle transitions via callback, not polling.**
`ActivityLifecycleMonitorRegistry` callbacks run on the instrumentation thread synchronously
with each lifecycle event. Recording every `RESUMED` transition into a `ConcurrentHashMap`
lets the test thread observe a RESUMED window without blocking on `runOnMainSync` (which would
stall if the main looper is busy).

## Why tests stop at WarmWelcomeActivity in CI

`DynamicStarMapActivity` performs heavy work on or close to the main thread during startup:
loading binary protobuf star catalogues, constructing thousands of `Renderable` objects, and
initialising OpenGL via swiftshader (the software renderer CI uses). On the CI Nexus 6
emulator this saturates the main looper for several seconds after `onResume`. Two consequences:

1. The activity pauses quickly ("Activity pause timeout") before any test poll can observe it
   as RESUMED when using `runOnMainSync`.
2. `@After` teardown cannot finish the activity within the test runner's window because
   `Activity#finish()` only marks the activity as finishing — the actual lifecycle callbacks
   still dispatch on the saturated looper. `MonitoringInstrumentation` logs "Unstopped activity
   count: 2" until gradle's `onAfterAll` hook loses the emulator from adb entirely.

The two sky-map tests (`newUserCompletesOnboarding_reachesSkyMapAndRequestsLocation` and
`locationPermissionAlreadyGranted_reachesSkyMapWithoutRationale`) are retained in
`StartUpTest.java` but annotated `@LocalOnlyTest` so CI skips them. They pass on local API 34
and API 36 emulators. The comment block above those tests explains in detail what was tried and
what refactoring would let them re-join CI.

## Would a different emulator image have fixed PR #881?

Probably yes, for the window-focus and Choreographer issues. The AOSP system images
(`default` / `aosp_atd`) strip out parts of the Google system stack that normally handle focus
dispatch. Two alternatives would likely have provided working window focus:

- **`google_apis` images** — full Google Play Services, behave like real devices. Heavier
  and slower to boot; require accepting a license in `android-emulator-runner`.
- **`google_atd` images** — "Automated Test Device" images designed for CI, lean but include
  enough of the Google stack for correct focus. Generally the best CI trade-off.

Changing `target: default` to `target: google_atd` in `.github/workflows/android.yml` would
have been the minimal fix to unblock the Espresso approach. However, `StartUpTest`'s
`FragmentManager`-based approach is more robust: it does not depend on which emulator image CI
uses, and will keep working if the image is updated or replaced.

## Lessons

- **AOSP CI images are not representative.** Test against `google_atd` images if you rely on
  Espresso, window focus, vsync, or the accessibility tree. AOSP images are fine for
  unit/Robolectric tests and for testing code that doesn't touch the rendering stack.
- **Espresso is still the right tool for in-app UI testing** — just not on windowless AOSP
  emulators and not for `WebView` content.
- **UiAutomator belongs at process boundaries.** Use it for system dialogs, the launcher,
  notifications. For in-app `DialogFragment`s, driving through `FragmentManager` is simpler
  and more reliable.
- **Don't inject animation replacements to control test timing.** The interaction between
  animation callbacks, the main looper, and test-thread `waitForIdleSync()` is hard to reason
  about. Prefer structuring tests so the animation is irrelevant to the assertion, or wait for
  the resulting activity/fragment rather than waiting for the animation itself.
- **Extract `@Module`s so `@TestInstallIn` has something to replace.** Hilt's `replaces`
  requires a `@Module` class, not an implementation class. Keeping bindings in their own small
  modules makes them independently replaceable in tests.
