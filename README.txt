This is the source repository for Sky Map. You should see the following
three directories:
 * app: Application source
 * test: Testing source (woefully out of date)
 * tools: Source for generating binary data used by the app.

To build SkyMap, you need to have install 'ant' and have downloaded a
version of the Android SDK. Then, change to the app directory and create a
local.properties file containing the location of your Android SDK. It
should have a single line that looks like this:
sdk.dir=<Path to your SDK>

Executing 'ant clean debug' will build the binary. It will be located in
the bin directory and will be named Stardroid-debug.apk. Make sure that
your device can handle binaries that don't come from the market, and you
should be good to go.

