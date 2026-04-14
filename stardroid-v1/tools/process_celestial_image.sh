#!/bin/bash
# Downloads and processes celestial images to the standard format
# used by Sky Map info cards (480x800 portrait JPEG, quality 75%).
#
# Usage:
#   ./process_celestial_image.sh <source_url> <output_filename>
#
# Example:
#   ./process_celestial_image.sh "https://example.com/star.jpg" "eso_sirius.jpg"
#
# The processed image is saved to app/src/main/assets/celestial_images/

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: $0 <source_url> <output_filename>"
    echo "Example: $0 \"https://example.com/star.jpg\" \"eso_sirius.jpg\""
    exit 1
fi

SOURCE_URL="$1"
OUTPUT_NAME="$2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/assets/celestial_images"
TEMP_FILE=$(mktemp /tmp/celestial_XXXXXX)

echo "Downloading: $SOURCE_URL"
wget -q -O "$TEMP_FILE" "$SOURCE_URL"

echo "Processing: $OUTPUT_NAME (480x800, JPEG q75%)"
convert "$TEMP_FILE" \
    -resize 480x800^ \
    -gravity center \
    -extent 480x800 \
    -quality 75 \
    "$OUTPUT_DIR/$OUTPUT_NAME"

rm -f "$TEMP_FILE"

FILESIZE=$(stat -c%s "$OUTPUT_DIR/$OUTPUT_NAME" 2>/dev/null || stat -f%z "$OUTPUT_DIR/$OUTPUT_NAME")
echo "Done: $OUTPUT_DIR/$OUTPUT_NAME ($(( FILESIZE / 1024 )) KB)"
