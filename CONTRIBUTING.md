# Contributing to Sky Map

First off, thank you for considering contributing to Sky Map! It's a labor of love maintained in our
spare time by a couple of ex-Googlers here in Pittsburgh, and community contributions help keep it
alive. However...

> We are not accepting *most* PRs at the moment.

__Why?__ We're in the midst of a major rewrite and adding more features will just move the goalposts!
When this is complete we'll gladly accept help.

## Before You Start

**Please [email us](mailto:skymapdevs@gmail.com) (or file a feature request) before embarking on any major changes or
feature additions.** We may have a different vision for the direction of the app and it would be a
pity to do work that we can't accept and would be wasted.

Bug fixes, dependency upgrades, and translation contributions are generally welcome without prior discussion.

## A Note on Response Times

It is likely we'll be slow to respond to emails and PR requests. Depending on what else is going on it might be days, it might be months. I do apologize for that — life is busy. Sometimes the reply might be simply to point you at this documentation, which will seem very ungrateful and unfriendly.

Thanks for your contributions! They're definitely appreciated even if our slowness to respond might make it seem otherwise.

## Types of Contributions

Despite the temporary moratorium on new features, we're always grateful for:

- **Bug fixes** — Simple, focused, few line fixes are very easy for us to approve.
- **Dependency upgrades** — Keeping things up to date is always welcome.
- **Translations** — We're particularly grateful for fixed or new translations since we've lost the
100% coverage of non-English languages that we once had.
- **Documentation** — Improvements to docs, README, comments, etc.
- **Feature additions** — Please email us first (see above).

**Pro-tip:** Small, focused PRs are easier for us to approve!
If your PR does too much it might get stalled because even if 90% of it is welcome there might
be 10% that we're not happy with. So keep them small if you can.
Plus, we'll be able to review them faster.

## Development Setup

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 17
- Android SDK with compileSdk 36; **minSdk 29** (a deliberate raise over v1's 26 — sub-Android-10
  devices are ~1.6% of installs)
- A `local.properties` file in the project root containing:

```
sdk.dir=<path to your Android SDK>
```

Android Studio can create this file for you.

### Project Structure

Active development happens in `stardroid-v2/`:

- **app/** — the Android app shell
- **core/math**, **core/astronomy** — pure Kotlin modules (no Android SDK on the classpath)
- **render/api**, **render/gles1** — renderer contract and GLES1 backend
- **konsist/** — architecture-gate tests enforcing the pure/Android module boundary

See [stardroid-v2/README.md](stardroid-v2/README.md) and
[stardroid-v2/AGENTS.md](stardroid-v2/AGENTS.md) for a full module layout and architecture
overview. `stardroid-v1/` holds the legacy app, retained as the reference for existing behavior;
see [stardroid-v1/AGENTS.md](stardroid-v1/AGENTS.md) if you're working there instead.

## Building

All commands below should be run from within the `stardroid-v2` directory. Always specify a
**flavor** — there is no plain `assembleDebug`:

```bash
./gradlew :app:installFdroidDebug    # pure open source, no Google dependencies
./gradlew :app:installGmsDebug       # adds Play Services (Analytics, fused location)
```

`gms` *release* builds additionally need `no-checkin.properties`, which is not in the repo.
F-Droid builds need nothing extra.

## Running Tests

```bash
./gradlew check                      # unit tests (JUnit 5 + Truth), ktlint, architecture gate
./gradlew connectedDebugAndroidTest  # instrumented; needs a device or emulator
./gradlew ktlintFormat               # auto-fix most style violations
```

`check` must pass before any commit. CI runs both suites on every PR.

## Deploying to a Device

Plug your phone in and run:

```bash
./install.sh          # Release build
./install.sh -d       # Debug build
./install.sh --fdroid # F-Droid build (add -d for debug)
./uninstall.sh         # Remove the app
```

## Submitting Changes

1. Fork the repository and create a branch from `master`.
2. Make your changes, keeping commits focused and atomic.
3. Run the unit tests to make sure you didn't break anything.
4. If you have multiple commits, please combine them into one by squashing.
5. Open a Pull Request with a clear description of what you changed and why.
6. Accept the Contributor License Agreement when the bot asks (once only, see below).

## Contributor License Agreement

Sky Map is distributed through several app-store channels whose terms are not all compatible with
a single licence text. To keep offering the [additional permission](stardroid-v2/LICENSE.md) that
allows distribution through those channels, we need contributors to grant us the rights to do so.

So before your first pull request can be merged, please read and accept the
[Contributor License Agreement](CLA.md). A bot will prompt you on the PR; accepting is a single
comment, and you only need to do it once ever.

**You keep the copyright in your contributions.** The CLA grants us a licence to use and
sublicense them — it does not transfer ownership, and it does not restrict what you do with your
own work.

If your employer owns the IP you create, please make sure you have permission to contribute
before signing. Questions: [skymapdevs@gmail.com](mailto:skymapdevs@gmail.com).

## Coding Style

- **`stardroid-v2/` (Kotlin, active development):** follow the
  [Google Kotlin Style Guide](https://developer.android.com/kotlin/style-guide); `./gradlew
  ktlintCheck` enforces it in CI. See [stardroid-v2/AGENTS.md](stardroid-v2/AGENTS.md) for the
  full conventions, including the required GPLv3 file header.
- **`stardroid-v1/` (Java, legacy):** follow the
  [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). See
  [stardroid-v1/AGENTS.md](stardroid-v1/AGENTS.md) for the full conventions.

Both: 100 character line wrap, no `m`-prefix on member variables/properties.

## Translations

Translation files live under `<module>/app/src/main/res/values-<language>/` (`stardroid-v2/` for
current development, `stardroid-v1/` for the legacy app). If you'd like to contribute a new
translation or fix an existing one, those PRs are very welcome. See the existing language
directories for the format.

## Deploying to the Google Play Store

> **Note:** Sky Map team only

We use `fastlane` to manage updates to the Play Store. From within `stardroid-v2/`, available
commands can be seen by running:

```bash
bundle exec fastlane
```

or by reading [stardroid-v2/fastlane/README.md](stardroid-v2/fastlane/README.md).

Before deploying a new release you will most likely want to update the `whatsnew` text and the
list of contributors. Both these tasks can be done by Claude - see the Claude skills under
`.claude/`.
