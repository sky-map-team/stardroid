CLASSPATH="\
build/classes/main:\
../app/build/intermediates/classes/release:\
libs/protobuf-java-2.6.1.jar\
"

DATA_DIR=data

java -cp $CLASSPATH \
com.google.android.stardroid.data.ConstellationProtoWriter $DATA_DIR/constellation_names_and_lines.kml $DATA_DIR/constellations

java -cp $CLASSPATH \
com.google.android.stardroid.data.MessierProtoWriter $DATA_DIR/messier.csv $DATA_DIR/messier

java -cp $CLASSPATH \
com.google.android.stardroid.data.StellarProtoWriter $DATA_DIR/stardata_names.txt $DATA_DIR/stars

