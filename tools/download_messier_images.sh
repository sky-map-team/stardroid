#!/bin/bash
# Downloads and processes images for 37 Messier objects
# Sources: NASA/ESA/Hubble, NOAO, Gemini/NOIRLab
# Output: 480x800 portrait JPEG at quality 75%

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/assets/celestial_images/messier"
TEMP_DIR=$(mktemp -d /tmp/messier_XXXXXX)

mkdir -p "$OUTPUT_DIR"

process_image() {
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

echo "=== Downloading 37 Messier object images ==="
echo ""

# Nebulae (6)
process_image "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/nebulae/emission/Hubble_M8_ACS_1_flat_FINAL.jpg" "hubble_m8.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2017/10/m17-field2-f110wf160w-a1-final-vers1.jpg" "hubble_m17.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/trifid-neb-full_jpg-jpg.webp" "hubble_m20.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/opo0306a-jpg.webp" "hubble_m27.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m78_0-jpg.webp" "hubble_m78.jpg"
process_image "https://storage.noirlab.edu/media/archives/images/screen/geminiann10008a.jpg" "gemini_m97.jpg"

# Galaxies (15)
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m32_acs_field2_3b_flat_gapfill_final-jpg.webp" "hubble_m32.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/heic1901a-jpg.webp" "hubble_m33.jpg"
process_image "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/galaxies/elliptical/Hubble_M49_potw1911a.tif?w=2048&fm=jpg" "hubble_m49.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/black-eye-galaxy-print-jpg.webp" "hubble_m64.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1352a-1-jpg.webp" "hubble_m65.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/heic1006a-jpg.webp" "hubble_m66.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m74-xlarge_web-jpg.webp" "hubble_m74.jpg"
process_image "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/galaxies/spiral/Hubble_M77_2025_potw2515a.jpg" "hubble_m77.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m81-print-jpg.webp" "hubble_m81.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m82-jpg.webp" "hubble_m82.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m83-jpg.webp" "hubble_m83.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m87-full_jpg-jpg.webp" "hubble_m87.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/hubble_friday_102315-jpg.webp" "hubble_m94.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/06/hubble-m109-wfc3-2flat-jpg.webp" "hubble_m109.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m110.png" "hubble_m110.jpg"

# Clusters (16)
process_image "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/stars/globular-clusters/Hubble_M2_potw1913a.jpg" "hubble_m2.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m3-jpg.webp" "hubble_m3.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1236a-jpg.webp" "hubble_m4.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1118a-jpg.webp" "hubble_m5.jpg"
process_image "https://commons.wikimedia.org/wiki/Special:FilePath/M6,_NGC_6405;_Butterfly_Cluster_(noao-02637).jpg" "noao_m6.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/06/hubble-m7-wfc3-2-flat-final-jpg.webp" "hubble_m7.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1225a-jpg.webp" "hubble_m10.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/m11-jpg.webp" "hubble_m11.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1113a-jpg.webp" "hubble_m12.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/hubble_m14_wfc3_1flat_cont_final-jpg.webp" "hubble_m14.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/heic1321a-jpg.webp" "hubble_m15.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1514a-jpg.webp" "hubble_m22.jpg"
process_image "https://assets.science.nasa.gov/dynamicimage/assets/science/missions/hubble/stars/open-clusters/Hubble_M35_WFPC2ok_flat_FINAL1.jpg" "hubble_m35.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2024/02/m44-acs-1-color-2-final.jpg" "hubble_m44.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1140a-jpg.webp" "hubble_m53.jpg"
process_image "https://science.nasa.gov/wp-content/uploads/2023/04/potw1449a_0-jpg.webp" "hubble_m92.jpg"

rm -rf "$TEMP_DIR"
echo ""
echo "=== All done! ==="
echo "Images saved to: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"/*.jpg | wc -l
echo " total JPEG files in messier directory"
