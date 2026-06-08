#!/usr/bin/env bash
set -euo pipefail

# Downloads a mod and its dependencies from CurseForge.
# Usage: download-cf.sh <project_json_path>
# Requires: CF_API_KEY environment variable

PROJECT_FILE="$1"
NAME=$(basename "$PROJECT_FILE" .json)
CF_ID=$(jq -r '.cf_id' "$PROJECT_FILE")
DEPS=$(jq -c '.dependencies // []' "$PROJECT_FILE")

mkdir -p mods

download_cf_file() {
  local mod_id="$1"
  local label="$2"

  # Query for latest file matching game version
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -H "x-api-key: $CF_API_KEY" -H "Accept: application/json" \
    "https://api.curseforge.com/v1/mods/${mod_id}/files?gameVersion=1.21.1&pageSize=1")
  http_code=$(echo "$response" | tail -1)
  response=$(echo "$response" | sed '$d')

  if [ "$http_code" != "200" ]; then
    echo "::error::API request failed for ${label} (mod ID ${mod_id}) - HTTP ${http_code}"
    echo "$response"
    return 1
  fi

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
echo "$DEPS" | jq -r '.[] | "\(.cf_id) \(.name)"' | while read -r dep_id dep_name; do
  download_cf_file "$dep_id" "$dep_name" || true
done
