#!/usr/bin/env python3
"""Build the root manifest."""

from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

from manifest_utils import log, relative_path, write_json


VERSION = 1


def add_manifest_path(manifest: dict, key: str, output_root: Path, path: Path) -> None:
    if path.is_file():
        manifest[key] = relative_path(path, output_root)


def main() -> int:
    output_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("output")
    manifest_path = Path(sys.argv[2]) if len(sys.argv) > 2 else output_dir / "manifest.json"

    log(f"Building manifest for {output_dir}...")

    output_root = output_dir.resolve()
    manifests_root = output_root / "manifests"
    manifest = {
        "version": VERSION,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    add_manifest_path(manifest, "book", output_root, manifests_root / "book.json")
    add_manifest_path(manifest, "projects", output_root, manifests_root / "projects.json")
    add_manifest_path(manifest, "recipes", output_root, manifests_root / "recipes.json")
    add_manifest_path(manifest, "renders", output_root, manifests_root / "render.json")
    add_manifest_path(manifest, "tags", output_root, manifests_root / "tags.json")
    add_manifest_path(manifest, "glyphs", output_root, manifests_root / "glyphs.json")
    add_manifest_path(manifest, "lang", output_root, manifests_root / "lang.json")
    add_manifest_path(manifest, "tomes", output_root, manifests_root / "tomes.json")

    write_json(manifest_path, manifest)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
