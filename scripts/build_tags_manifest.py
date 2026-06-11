#!/usr/bin/env python3
"""Build maps from tag resource locations to exported tag paths."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, resource_manifest, write_json


def main() -> int:
    output_dir = output_arg()
    manifest_path = manifest_arg(output_dir, "tags.json")

    log(f"Building tags manifest for {output_dir}...")

    output_root = output_dir.resolve()
    tags_root = output_root / "tags" / "minecraft"

    manifest = {}
    if tags_root.is_dir():
        for registry_root in sorted(path for path in tags_root.iterdir() if path.is_dir()):
            registry = registry_root.relative_to(tags_root).as_posix()
            manifest[registry] = resource_manifest(output_root, registry_root)

    write_json(manifest_path, manifest, sort_keys=True)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
