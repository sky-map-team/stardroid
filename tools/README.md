# Data Generation Tools

Standalone utilities for converting star catalogs and astronomical data into the binary protocol buffer format used by the Sky Map app at runtime.

These tools transform raw catalog data (star positions, Messier objects, etc.) into compact binary files that are bundled as assets in the APK. See [docs/design/datageneration.md](../docs/design/datageneration.md) for the full pipeline design.

## Quick Start

It's easiest to use the `build_skymap.sh` script in the parent directory, but if you want to understand the steps:

## Manual Procedure

The following procedure will regenerate the binary star data:

1. Build the utilities with `./gradlew assemble installDist` from the stardroid root directory.
2. Gradle's application plugin generates the wrong classpath for the utilities. Fix it by going into `tools/build/install/datagen/bin/datagen` and removing the APK from the classpath. Leave the root installation directory `$APP_HOME/lib`.
3. Convert the star and Messier data files to text protocol buffers with `./generate.sh` from the tools directory.
4. Run `./binary.sh` from the tools directory to convert the ASCII proto bufs to binary ones (and put them in the right directory: `app/src/main/assets/`).
