#!/bin/bash
# Downloads and processes IAU constellation chart images for 48 constellations
# Source: IAU/Sky & Telescope (CC BY 4.0)
# Output: 480x800 portrait JPEG at quality 75%

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/assets/celestial_images/constellations"
TEMP_DIR=$(mktemp -d /tmp/constellations_XXXXXX)
BASE_URL="https://iauarchive.eso.org/static/public/constellations/gif"

mkdir -p "$OUTPUT_DIR"

process_constellation() {
    local code="$1"
    local output="$2"
    local temp_file="$TEMP_DIR/${code}.gif"

    echo "Downloading: $code → $output"
    if ! curl -sL --max-time 60 -o "$temp_file" "$BASE_URL/${code}.gif"; then
        echo "  FAILED to download $code"
        return 1
    fi

    if ! file "$temp_file" | grep -q "GIF"; then
        echo "  ERROR: Not a GIF image for $code"
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

echo "=== Downloading 48 constellation chart images ==="
echo ""

process_constellation "AND" "iau_andromeda.jpg"
process_constellation "AQR" "iau_aquarius.jpg"
process_constellation "AQL" "iau_aquila.jpg"
process_constellation "ARI" "iau_aries.jpg"
process_constellation "AUR" "iau_auriga.jpg"
process_constellation "BOO" "iau_bootes.jpg"
process_constellation "CNC" "iau_cancer.jpg"
process_constellation "CMA" "iau_canis_major.jpg"
process_constellation "CMI" "iau_canis_minor.jpg"
process_constellation "CAP" "iau_capricornus.jpg"
process_constellation "CAR" "iau_carina.jpg"
process_constellation "CAS" "iau_cassiopeia.jpg"
process_constellation "CEN" "iau_centaurus.jpg"
process_constellation "CEP" "iau_cepheus.jpg"
process_constellation "CET" "iau_cetus.jpg"
process_constellation "CRB" "iau_corona_borealis.jpg"
process_constellation "CRV" "iau_corvus.jpg"
process_constellation "CRU" "iau_crux.jpg"
process_constellation "CYG" "iau_cygnus.jpg"
process_constellation "DEL" "iau_delphinus.jpg"
process_constellation "DRA" "iau_draco.jpg"
process_constellation "ERI" "iau_eridanus.jpg"
process_constellation "GEM" "iau_gemini.jpg"
process_constellation "GRU" "iau_grus.jpg"
process_constellation "HER" "iau_hercules.jpg"
process_constellation "HYA" "iau_hydra.jpg"
process_constellation "LEO" "iau_leo.jpg"
process_constellation "LEP" "iau_lepus.jpg"
process_constellation "LIB" "iau_libra.jpg"
process_constellation "LYR" "iau_lyra.jpg"
process_constellation "MON" "iau_monoceros.jpg"
process_constellation "OPH" "iau_ophiuchus.jpg"
process_constellation "ORI" "iau_orion.jpg"
process_constellation "PAV" "iau_pavo.jpg"
process_constellation "PEG" "iau_pegasus.jpg"
process_constellation "PER" "iau_perseus.jpg"
process_constellation "PHE" "iau_phoenix.jpg"
process_constellation "PSC" "iau_pisces.jpg"
process_constellation "PUP" "iau_puppis.jpg"
process_constellation "SGE" "iau_sagitta.jpg"
process_constellation "SGR" "iau_sagittarius.jpg"
process_constellation "SCO" "iau_scorpius.jpg"
process_constellation "TAU" "iau_taurus.jpg"
process_constellation "TRI" "iau_triangulum.jpg"
process_constellation "UMA" "iau_ursa_major.jpg"
process_constellation "UMI" "iau_ursa_minor.jpg"
process_constellation "VEL" "iau_vela.jpg"
process_constellation "VIR" "iau_virgo.jpg"

rm -rf "$TEMP_DIR"
echo ""
echo "=== All done! ==="
echo "Images saved to: $OUTPUT_DIR"
ls "$OUTPUT_DIR"/*.jpg | wc -l
echo " total JPEG files in constellations directory"
