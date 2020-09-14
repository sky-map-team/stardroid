It's easiest to use the `build_skymap.sh` script in the parent directory, but if you want
to understand the steps:

The following procedure will regenerate the binary star data:
  1.  Build the utilities with `./gradlew assemble installDist` from the stardroid directory.
  1.  Gradle's application plugin generates the wrong classpath for the utilities.  Fix it
  by going into `tools/build/install/datagen/bin/datagen` and removing the apk from the classpath.
  Leave the root installation directory `$APP_HOME/lib`.
  1.  Convert the star and messier data files to text protocol buffers with `./generate.sh` from the tools directory.
  1.  Finally run `./binary.sh` from the tools directory to convert the ascii proto bufs to binary ones (and put them in the right directory).
