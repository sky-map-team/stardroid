# Contributing to Sky Map

First off, thank you for considering contributing to Sky Map! It's a labor of love maintained in our
spare time by a couple of ex-Googlers here in Pittsburgh, and community contributions help keep it
alive. However...

> We are not accepting *most* PRs at the moment.

__Why?__ We're in the midst of a major rewrite and adding more features will just move the goalposts!
When this is completely we'll gladly accept help.

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
- Android SDK (API level 26–35)
- Java 17 toolchain
- A `local.properties` file in the project root containing:

```
sdk.dir=<path to your Android SDK>
```

Android Studio can create this file for you.

### Project Structure

You should see the following directories:

- **app/** — Application source
- **datamodel/** — Protocol buffer definitions for astronomical objects
- **tools/** — Source for generating binary data used by the app

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a detailed architecture overview.

## Building

### Quick Build (Debug APK)

```bash
./gradlew :app:assembleGmsDebug
```

The APK can be found in `app/build/outputs/apk/`.

### Full Build (Including Data Generation)

```bash
./build_skymap.sh
```

Pass `--quick` to skip data regeneration, or `--fdroid` to build the F-Droid variant.

### Build Flavors

- **gms** — Includes Google Play Services (Analytics, Location)
- **fdroid** — Pure open source, no Google dependencies

Always specify the flavor when building: `assembleGmsDebug` not `assembleDebug`.

### Building a Release APK

> **Note:** Sky Map team only

Create a file in the `app` directory called `no-checkin.properties` with appropriate values:

```
store-pwd=
key-pwd=
analytics-key=
```

and overwrite `google-services.json` with the correct file. Then:

```bash
./gradlew :app:assembleGms
```

## Running Tests

### Unit Tests

```bash
./gradlew :app:test
```

### Instrumented Tests

Requires a connected device or emulator:

```bash
./gradlew :app:connectedAndroidTest
```

## Deploying to a Device

Plug your phone in and run:

```bash
./deploy.sh       # Release build
./deploy.sh -d    # Debug build
./undeploy.sh     # Remove the app
```

## Submitting Changes

1. Fork the repository and create a branch from `master`.
2. Make your changes, keeping commits focused and atomic.
3. Run the unit tests to make sure you didn't break anything.
4. If you have multiple commits, please combine them into one by squashing.
5. Open a Pull Request with a clear description of what you changed and why.

## Coding Style

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) (or try to):

- 100 character line wrap
- Do **not** prefix member variables with `m` (unlike common Android convention)
- Java 17 toolchain features are available

## Translations

Translation files live under `app/src/main/res/values-<language>/`. If you'd like to contribute a new translation or fix an existing one, those PRs are very welcome. See the existing language directories for the format.

## Deploying to the Google Play Store

> **Note:** Sky Map team only

We use `fastlane` to manage updates to the Play Store. Available commands can be seen by running:

```bash
bundle exec fastlane
```

or by reading [fastlane/README.md](fastlane/README.md).

Before deploying a new release you will most likely want to update the `whatsnew` text and the
list of contributors. Both these tasks can be done by Claude - see the Claude skills under
`.claude/`.
