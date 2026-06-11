#!/usr/bin/env python3
"""Build the root manifest list."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from manifest_utils import log, relative_path, write_json


def main() -> int:
    output_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("output")
    manifest_path = Path(sys.argv[2]) if len(sys.argv) > 2 else output_dir / "manifest.json"

    log(f"Building manifest for {output_dir}...")

    output_root = output_dir.resolve()
    manifests_root = output_root / "manifests"
    manifest = []
    if manifests_root.is_dir():
        manifest = [
            relative_path(path, output_root)
            for path in sorted(manifests_root.glob("*.json"))
            if path.is_file()
        ]

    write_json(manifest_path, manifest)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
