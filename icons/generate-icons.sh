#!/bin/bash
# Generate all required icon sizes from the source SVG.
# Usage: ./generate-icons.sh [source.svg]

set -euo pipefail

SOURCE="${1:-variant4a-compact.svg}"
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/generated"

rm -rf "$OUT"
mkdir -p "$OUT/mipmap-mdpi" "$OUT/mipmap-hdpi" "$OUT/mipmap-xhdpi" \
         "$OUT/mipmap-xxhdpi" "$OUT/mipmap-xxxhdpi" "$OUT/playstore" "$OUT/toodledo"

declare -A SIZES=(
  ["mipmap-mdpi"]=48
  ["mipmap-hdpi"]=72
  ["mipmap-xhdpi"]=96
  ["mipmap-xxhdpi"]=144
  ["mipmap-xxxhdpi"]=192
  ["playstore"]=512
  ["toodledo"]=64
)

for folder in "${!SIZES[@]}"; do
  size="${SIZES[$folder]}"
  echo "$folder: ${size}x${size}"
  inkscape "$DIR/$SOURCE" --export-type=png \
    --export-filename="$OUT/$folder/ic_launcher.png" \
    -w "$size" -h "$size" 2>/dev/null
done

echo "Done. Output in $OUT/"
