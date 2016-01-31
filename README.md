This is the source repository for Sky Map. You should see the following
three directories:
 * app: Application source
 * test: Testing source (woefully out of date)
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

*Note - at present the star, constellation and messier layers are disabled due to crashing issues related to the string resource ids being out of sync.  Fix soon.*

## Running tests

    ./gradlew app:connectedAndroidTest
