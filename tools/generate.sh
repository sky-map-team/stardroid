CLASSPATH="\
build/classes/main:\
../app/build/intermediates/classes/release:\
libs/protobuf-java-2.6.1.jar\
"

java -cp $CLASSPATH \
com.google.android.stardroid.data.ConstellationProtoWriter data/constellation_names_and_lines.kml constellations

java -cp $CLASSPATH \
com.google.android.stardroid.data.MessierProtoWriter data/messier.csv messier

java -cp $CLASSPATH \
com.google.android.stardroid.data.StellarProtoWriter data/stardata_names.txt stars

