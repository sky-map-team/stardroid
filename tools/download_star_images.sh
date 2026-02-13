#!/bin/bash
# Downloads and processes images for 29 stars + Moon
# Stars: ESO Digitized Sky Survey 2 (red band) with color tinting
# Moon: NASA/GSFC/ASU LRO mosaic
# Output: 480x800 portrait JPEG at quality 75%

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/assets/celestial_images/stars"
TEMP_DIR=$(mktemp -d /tmp/stars_XXXXXX)

mkdir -p "$OUTPUT_DIR"

# Process a DSS2 star image: download, negate, color-tint, crop to portrait
process_dss2_star() {
    local star_name="$1"
    local output="$2"
    local fov_x="${3:-16}"
    local fov_y="${4:-27}"
    local temp_file="$TEMP_DIR/${output%.jpg}.gif"

    echo "Downloading DSS2: $star_name → $output"
    if ! curl -sL --max-time 60 -o "$temp_file" \
        "https://archive.eso.org/dss/dss/image?name=${star_name}&x=${fov_x}&y=${fov_y}&Sky-Survey=DSS2-red&mime-type=download-gif"; then
        echo "  FAILED to download $star_name"
        return 1
    fi

    # Check if we got an actual image (not an HTML error)
    if file "$temp_file" | grep -q "HTML"; then
        echo "  ERROR: DSS2 returned HTML error for $star_name, trying DSS1..."
        if ! curl -sL --max-time 60 -o "$temp_file" \
            "https://archive.eso.org/dss/dss/image?name=${star_name}&x=${fov_x}&y=${fov_y}&Sky-Survey=DSS1&mime-type=download-gif"; then
            echo "  FAILED to download $star_name (DSS1 fallback)"
            return 1
        fi
    fi

    echo "  Processing: negate, color-tint, 480x800, JPEG q75%"
    convert "$temp_file" \
        -negate -normalize \
        +level-colors "#080e20","#d8e4ff" \
        -resize 480x800^ \
        -gravity center \
        -extent 480x800 \
        -quality 75 \
        "$OUTPUT_DIR/$output"

    local size=$(stat -c%s "$OUTPUT_DIR/$output" 2>/dev/null || stat -f%z "$OUTPUT_DIR/$output")
    echo "  Done: $output ($(( size / 1024 )) KB)"
}

# Process a direct URL image
process_url_image() {
    local url="$1"
    local output="$2"
    local temp_file="$TEMP_DIR/$(basename "$output")"

    echo "Downloading: $output"
    if ! curl -sL --max-time 60 -o "$temp_file" "$url"; then
        echo "  FAILED to download $output"
        return 1
    fi

    echo "  Processing: 480x800, JPEG q75%"
    convert "$temp_file" \
        -resize 480x800^ \
        -gravity center \
        -extent 480x800 \
        -quality 75 \
        "$OUTPUT_DIR/$output"

    local size=$(stat -c%s "$OUTPUT_DIR/$output" 2>/dev/null || stat -f%z "$OUTPUT_DIR/$output")
    echo "  Done: $output ($(( size / 1024 )) KB)"
}

echo "=== Downloading 29 star images + Moon ==="
echo ""

# Stars - all from ESO DSS2 (field of view: 16x27 arcmin for portrait ratio)
# Group 1: Bright northern stars
process_dss2_star "Procyon"     "eso_procyon.jpg"
process_dss2_star "Deneb"       "eso_deneb.jpg"
process_dss2_star "Capella"     "eso_capella.jpg"
process_dss2_star "Altair"      "eso_altair.jpg"
process_dss2_star "Castor"      "eso_castor.jpg"
process_dss2_star "Pollux"      "eso_pollux.jpg"
process_dss2_star "Algol"       "eso_algol.jpg"
process_dss2_star "Dubhe"       "eso_dubhe.jpg"
process_dss2_star "Alioth"      "eso_alioth.jpg"
process_dss2_star "Mirach"      "eso_mirach.jpg"

# Group 2: Equatorial stars
process_dss2_star "Regulus"     "eso_regulus.jpg"
process_dss2_star "Spica"       "eso_spica.jpg"
process_dss2_star "Alnilam"     "eso_alnilam.jpg"
process_dss2_star "Alphard"     "eso_alphard.jpg"
process_dss2_star "Hamal"       "eso_hamal.jpg"
process_dss2_star "Rasalhague"  "eso_rasalhague.jpg"
process_dss2_star "Denebola"    "eso_denebola.jpg"
process_dss2_star "Alpheratz"   "eso_alpheratz.jpg"
process_dss2_star "Markab"      "eso_markab.jpg"
process_dss2_star "Enif"        "eso_enif.jpg"
process_dss2_star "Scheat"      "eso_scheat.jpg"
process_dss2_star "Nunki"       "eso_nunki.jpg"
process_dss2_star "Diphda"      "eso_diphda.jpg"
process_dss2_star "Fomalhaut"   "eso_fomalhaut.jpg"

# Group 3: Southern stars
process_dss2_star "Achernar"    "eso_achernar.jpg"
process_dss2_star "Hadar"       "eso_hadar.jpg"
process_dss2_star "Acrux"       "eso_acrux.jpg"
process_dss2_star "Adhara"      "eso_adhara.jpg"
process_dss2_star "Shaula"      "eso_shaula.jpg"

echo ""
echo "--- Moon ---"
# Moon: NASA/GSFC/ASU LRO full disk mosaic
process_url_image "https://svs.gsfc.nasa.gov/vis/a000000/a005000/a005001/moon_mosaic_print.jpg" "nasa_moon.jpg"

rm -rf "$TEMP_DIR"
echo ""
echo "=== All done! ==="
echo "Images saved to: $OUTPUT_DIR"
ls "$OUTPUT_DIR"/*.jpg | wc -l
echo " total JPEG files in stars directory"
