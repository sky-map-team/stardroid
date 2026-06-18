DATA_DIR=data
TOOL=build/install/datagen/bin/datagen

# Note, constellation data is already in proto form.
$TOOL GenDeepSkyObjects $DATA_DIR/deep_sky_objects.csv $DATA_DIR/deep_sky_objects
$TOOL GenStars $DATA_DIR/stardata_names.txt $DATA_DIR/stars
