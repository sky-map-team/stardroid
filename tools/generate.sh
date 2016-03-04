DATA_DIR=data
TOOL=build/install/datagen/bin/datagen

$TOOL GenConstellations $DATA_DIR/constellation_names_and_lines.kml $DATA_DIR/constellations
$TOOL GenMessier $DATA_DIR/messier.csv $DATA_DIR/messier
$TOOL GenStars $DATA_DIR/stardata_names.txt $DATA_DIR/stars


