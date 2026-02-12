fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Run unit tests and linting

### android increment_version_code

```sh
[bundle exec] fastlane android increment_version_code
```

Increment version code

### android bump_version

```sh
[bundle exec] fastlane android bump_version
```

Set a specific version name (e.g., fastlane bump_version name:"1.5.0") The name must have no spaces.

### android build

```sh
[bundle exec] fastlane android build
```

Build release apk

### android internal

```sh
[bundle exec] fastlane android internal
```

Deploy to internal testing track

### android promote_to_alpha

```sh
[bundle exec] fastlane android promote_to_alpha
```

Promote Internal to Alpha (Closed Testing)

### android promote_to_beta

```sh
[bundle exec] fastlane android promote_to_beta
```

Promote Alpha to Beta (Open Testing @10%)

### android promote_to_production

```sh
[bundle exec] fastlane android promote_to_production
```

Promote Beta to Production @10%

### android alpha

```sh
[bundle exec] fastlane android alpha
```

Deploy to alpha track

### android beta

```sh
[bundle exec] fastlane android beta
```

Deploy to beta track

### android production

```sh
[bundle exec] fastlane android production
```

Deploy to production

### android screenshots

```sh
[bundle exec] fastlane android screenshots
```

Capture screenshots for Play Store. Specify --device_type=sevenInch for a tablet.

### android upload_metadata

```sh
[bundle exec] fastlane android upload_metadata
```

Upload store metadata. Options: whatsnew:true (include changelogs), imgs:true (include screenshots/images), all:true (everything), store:false (skip store metadata).

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
