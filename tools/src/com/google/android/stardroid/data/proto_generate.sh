CLASSPATH="\
../../../../../../bin:\
../../../../../../../app/bin:\
../../../../../../libs/protobuf-java-2.3.0.jar\
"

# TODO(serafini): I'm not 100% sure that this is doing the right thing.
# However, we are not currently using this script, so we can figure it
# out later.

java -cp $CLASSPATH \
com.google.android.stardroid.data.ConstellationProtoWriter constellation_names_and_lines.kml constellations

java -cp $CLASSPATH \
com.google.android.stardroid.data.MessierProtoWriter messier.csv messier

java -cp $CLASSPATH \
com.google.android.stardroid.data.StellarProtoWriter stardata_names.txt stars
