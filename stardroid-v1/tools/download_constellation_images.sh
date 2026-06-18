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

# Missing constellations (Phase 1, issue #645)
process_constellation "ANT" "iau_antlia.jpg"
process_constellation "APS" "iau_apus.jpg"
process_constellation "ARA" "iau_ara.jpg"
process_constellation "CAE" "iau_caelum.jpg"
process_constellation "CAM" "iau_camelopardalis.jpg"
process_constellation "CVN" "iau_canes_venatici.jpg"
process_constellation "CHA" "iau_chamaeleon.jpg"
process_constellation "CIR" "iau_circinus.jpg"
process_constellation "COL" "iau_columba.jpg"
process_constellation "COM" "iau_coma_berenices.jpg"
process_constellation "CRA" "iau_corona_australis.jpg"
process_constellation "CRT" "iau_crater.jpg"
process_constellation "DOR" "iau_dorado.jpg"
process_constellation "EQU" "iau_equuleus.jpg"
process_constellation "FOR" "iau_fornax.jpg"
process_constellation "HOR" "iau_horologium.jpg"
process_constellation "HYI" "iau_hydrus.jpg"
process_constellation "IND" "iau_indus.jpg"
process_constellation "LAC" "iau_lacerta.jpg"
process_constellation "LMI" "iau_leo_minor.jpg"
process_constellation "LUP" "iau_lupus.jpg"
process_constellation "LYN" "iau_lynx.jpg"
process_constellation "MEN" "iau_mensa.jpg"
process_constellation "MIC" "iau_microscopium.jpg"
process_constellation "MUS" "iau_musca.jpg"
process_constellation "NOR" "iau_norma.jpg"
process_constellation "OCT" "iau_octans.jpg"
process_constellation "PIC" "iau_pictor.jpg"
process_constellation "PSA" "iau_piscis_austrinus.jpg"
process_constellation "PYX" "iau_pyxis.jpg"
process_constellation "RET" "iau_reticulum.jpg"
process_constellation "SCL" "iau_sculptor.jpg"
process_constellation "SCT" "iau_scutum.jpg"
process_constellation "SER" "iau_serpens_caput.jpg"
process_constellation "SER" "iau_serpens_cauda.jpg"
process_constellation "SEX" "iau_sextans.jpg"
process_constellation "TEL" "iau_telescopium.jpg"
process_constellation "TRA" "iau_triangulum_australe.jpg"
process_constellation "TUC" "iau_tucana.jpg"
process_constellation "VOL" "iau_volans.jpg"
process_constellation "VUL" "iau_vulpecula.jpg"

rm -rf "$TEMP_DIR"
echo ""
echo "=== All done! ==="
echo "Images saved to: $OUTPUT_DIR"
ls "$OUTPUT_DIR"/*.jpg | wc -l
echo " total JPEG files in constellations directory"
