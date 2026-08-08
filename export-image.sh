#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACTS_DIR="$APP_DIR/artifacts"
PORTABLE_DIR="$ARTIFACTS_DIR/portable"
IMAGE_NAME="download-studio:1.0.0"
OUTPUT_FILE="${1:-$PORTABLE_DIR/download-studio-1.0.0.tar}"

mkdir -p "$(dirname "$OUTPUT_FILE")"

echo "Building $IMAGE_NAME for $(uname -m)…"
docker build --pull --file "$APP_DIR/Dockerfile" --tag "$IMAGE_NAME" "$APP_DIR"

echo "Exporting image to $OUTPUT_FILE…"
docker save --output "$OUTPUT_FILE" "$IMAGE_NAME"

CHECKSUM_FILE="${OUTPUT_FILE}.sha256"
(
    cd "$(dirname "$OUTPUT_FILE")"
    sha256sum "$(basename "$OUTPUT_FILE")" > "$(basename "$CHECKSUM_FILE")"
)

echo "Image ready: $OUTPUT_FILE"
cp "$APP_DIR/docker-compose.portable.yml" "$(dirname "$OUTPUT_FILE")/docker-compose.yml"
mkdir -p "$(dirname "$OUTPUT_FILE")/downloads"

echo "Checksum: $CHECKSUM_FILE"
echo "Compose: $(dirname "$OUTPUT_FILE")/docker-compose.yml"
echo "Downloads: $(dirname "$OUTPUT_FILE")/downloads"
