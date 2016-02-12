This is the source repository for Sky Map. You should see the following
two directories:
 * app: Application source
 * tools: Source for generating binary data used by the app.

To build SkyMap, you can use Android Developer Studio or Gradle.  Begin by
by creating a `local.properties` file containing the location of your
Android installation:

    sdk.dir=<path to your SDK>

Android Developer Studio can create this for you.

## Building a debug apk

From the root directory execute

    ./gradlew assembleDebug

The apk can be found in `app/build/outputs/apk/`.

## Building a release apk
(Sky Map team only)

Set the following environment variables:

    export KEYPWD=<the key password>
    export KSTOREPWD=<the key store password>

From the root directory execute

    ./gradlew assemble

or

    ./gradlew assembleRelease

The apk can be found in `app/build/outputs/apk/`.


## Running tests

    ./gradlew app:connectedAndroidTest

## Regenerating the star data files

The data files need munging to take into account the string ID files in the generated `R` file.  Information on
how to do this is in the tools directory.  If you update any strings in Sky Map it's quite likely you'll
have to regenerate the star data files or the app will crash or put incorrect labels on things.
