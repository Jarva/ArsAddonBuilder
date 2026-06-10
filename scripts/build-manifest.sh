#!/usr/bin/env bash
set -euo pipefail

# Build a JSON manifest for generated files.
# Usage: build-manifest.sh [output_dir] [manifest_path]

OUTPUT_DIR="${1:-output}"
MANIFEST_PATH="${2:-manifest.json}"
VERSION=1
GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

mkdir -p "$(dirname "$MANIFEST_PATH")"

if stat -c '%s %n' /dev/null >/dev/null 2>&1; then
	stat_file() {
		xargs -0 stat -c '%s %n'
	}
else
	stat_file() {
		xargs -0 stat -f '%z %N'
	}
fi

hash_file() {
	if command -v sha256sum >/dev/null 2>&1 && sha256sum -z /dev/null >/dev/null 2>&1; then
		xargs -0 -n 128 sha256sum -z
	else
		xargs -0 -n 128 shasum -a 256
	fi
}

FILES="$(mktemp)"
SIZES="$(mktemp)"
HASHES="$(mktemp)"
trap 'rm -f "$FILES" "$SIZES" "$HASHES"' EXIT

printf 'Building manifest for %s...\n' "$OUTPUT_DIR" >&2

(
	cd "$OUTPUT_DIR"

	find . -type f -print0 |
		LC_ALL=C sort -z |
		while IFS= read -r -d '' file; do
			printf '%s\0' "${file#./}"
		done >"$FILES"

	stat_file <"$FILES" >"$SIZES"
	hash_file <"$FILES" >"$HASHES"
)

jq -n \
	--argjson version "$VERSION" \
	--arg generatedAt "$GENERATED_AT" \
	--rawfile sizes "$SIZES" \
	--rawfile hashes "$HASHES" \
	'($sizes
		| split("\n")[:-1]
		| map(capture("^(?<size>[0-9]+) (?<path>.*)$"))
		| map({(.path): (.size | tonumber)})
		| add) as $sizesByPath
	| ($hashes
		| gsub("\n"; "\u0000")
		| split("\u0000")[:-1]
		| map(capture("^(?<sha256>[0-9a-f]{64}) [ *](?<path>.*)$"))
		| sort_by(.path)) as $files
	| {
		version: $version,
		generatedAt: $generatedAt,
		files: ($files | map({
			path: .path,
			size: $sizesByPath[.path],
			sha256: .sha256
		}))
	}' \
	>"$MANIFEST_PATH"

printf 'Wrote %s\n' "$MANIFEST_PATH" >&2
