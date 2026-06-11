#!/usr/bin/env python3
"""Build a map from language codes to exported language file paths."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, relative_path, write_json

def main() -> int:
    output_dir = output_arg()
    manifest_path = manifest_arg(output_dir, "lang.json")

    log(f"Building language manifest for {output_dir}...")

    output_root = output_dir.resolve()
    lang_root = output_root / "lang"

    manifest = {}
    if lang_root.is_dir():
        manifest = {
            path.stem: relative_path(path, output_root)
            for path in sorted(lang_root.glob("*.json"))
            if path.is_file()
        }

    write_json(manifest_path, manifest, sort_keys=True)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
