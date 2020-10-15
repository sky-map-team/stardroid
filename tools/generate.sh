DATA_DIR=data
TOOL=build/install/datagen/bin/datagen

# Note, constellation data is already in proto form.
$TOOL GenMessier $DATA_DIR/messier.csv $DATA_DIR/messier
$TOOL GenStars $DATA_DIR/stardata_names.txt $DATA_DIR/stars
