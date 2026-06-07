#!/usr/bin/env bash
set -euo pipefail

# Downloads a mod and its dependencies from CurseForge.
# Usage: download-cf.sh <cf_id> <name> <dependencies_json>
# Requires: CF_API_KEY environment variable

CF_ID="$1"
NAME="$2"
DEPS_JSON="$3"

mkdir -p mods

download_cf_file() {
  local mod_id="$1"
  local label="$2"

  # Query for latest file matching game version and NeoForge loader
  local response
  response=$(curl -sf -H "x-api-key: $CF_API_KEY" \
    "https://api.curseforge.com/v1/mods/${mod_id}/files?gameVersion=1.21.1&modLoaderType=6&pageSize=1")

  local file_id filename download_url
  file_id=$(echo "$response" | jq -r '.data[0].id')
  filename=$(echo "$response" | jq -r '.data[0].fileName')
  download_url=$(echo "$response" | jq -r '.data[0].downloadUrl')

  if [ "$file_id" = "null" ] || [ -z "$file_id" ]; then
    echo "::warning::No file found for ${label} (mod ID ${mod_id})"
    return 1
  fi

  if [ "$download_url" = "null" ] || [ -z "$download_url" ]; then
    echo "::error::No direct download URL for ${label} (mod ID ${mod_id}) - direct downloads must be enabled"
    return 1
  fi

  echo "Downloading ${label}: ${filename} (file ID ${file_id})"
  curl -sfL -o "mods/${filename}" "$download_url"
  echo "Downloaded ${filename}"
}

# Download the main mod
download_cf_file "$CF_ID" "$NAME"

# Download dependencies
DEP_COUNT=$(echo "$DEPS_JSON" | jq 'length')
for i in $(seq 0 $((DEP_COUNT - 1))); do
  dep_id=$(echo "$DEPS_JSON" | jq -r ".[$i].cf_id")
  dep_name=$(echo "$DEPS_JSON" | jq -r ".[$i].name")
  download_cf_file "$dep_id" "$dep_name" || true
done
