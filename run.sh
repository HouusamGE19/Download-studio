#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$APP_DIR/frontend"
if [[ ! -d node_modules ]]; then
    echo "Installing Angular dependencies…"
    npm install
fi

echo "Building the Angular interface…"
npm run build

cd "$APP_DIR"
echo "Starting Download Studio at http://127.0.0.1:8080"
exec ./mvnw spring-boot:run
