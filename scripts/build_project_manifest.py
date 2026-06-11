#!/usr/bin/env python3
"""Build a list of project configuration files."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, relative_path, write_json

def main() -> int:
    output_dir = output_arg()
    manifest_path = manifest_arg(output_dir, "projects.json")

    log(f"Building project manifest for {output_dir}...")

    output_root = output_dir.resolve()
    projects_dir = output_root / "projects"
    projects = [
        relative_path(path, output_root)
        for path in sorted(projects_dir.glob("*.json"))
        if path.is_file()
    ]

    write_json(manifest_path, projects)
    log(f"Wrote {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
