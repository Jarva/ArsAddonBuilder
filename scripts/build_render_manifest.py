#!/usr/bin/env python3
"""Build maps from resource locations to exported render paths."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, resource_manifest, write_json


def scan_render_type(output_root, render_type: str) -> dict[str, str]:
    return resource_manifest(output_root, output_root / "renders" / render_type, "*")


def main() -> int:
    output_dir = output_arg()
    manifest_path = manifest_arg(output_dir, "render.json")

    log(f"Building render manifest for {output_dir}...")

    output_root = output_dir.resolve()
    manifest = {
        "item": scan_render_type(output_root, "item"),
        "entity": scan_render_type(output_root, "entity"),
    }

    write_json(manifest_path, manifest, sort_keys=True)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
