#!/usr/bin/env python3
"""Build a map from recipe resource locations to exported recipe paths."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, resource_manifest, write_json


def main() -> int:
    output_dir = output_arg()
    manifest_path = manifest_arg(output_dir, "recipes.json")

    log(f"Building recipe manifest for {output_dir}...")

    output_root = output_dir.resolve()
    manifest = resource_manifest(output_root, output_root / "recipes")

    write_json(manifest_path, manifest, sort_keys=True)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
