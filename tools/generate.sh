CLASSPATH=../../../../../../bin

java -cp $CLASSPATH com.google.android.stardroid.data.deprecated.BinaryStarWriter stardata_names.txt old_stars.bin
java -cp $CLASSPATH com.google.android.stardroid.data.deprecated.BinaryConstellationWriter constellation_names_and_lines.kml old_constellations.bin
java -cp $CLASSPATH com.google.android.stardroid.data.deprecated.BinaryMessierWriter messier.csv old_messier.bin

DATADIR=../../../../../../assets
cp old_stars.bin $DATADIR/stars.bin
cp old_constellations.bin $DATADIR/constellations.bin
cp old_messier.bin $DATADIR/messier.bin