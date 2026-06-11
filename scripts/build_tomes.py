#!/usr/bin/env python3
"""Build a list of caster tome recipe paths."""

from __future__ import annotations

import json

from manifest_utils import log, manifest_arg, output_arg, relative_path, write_json

def main() -> int:
    output_dir = output_arg()
    tomes_path = manifest_arg(output_dir, "tomes.json")

    log(f"Building tome index for {output_dir}...")

    output_root = output_dir.resolve()
    recipes_root = output_root / "recipes"

    tomes = []
    if recipes_root.is_dir():
        for path in sorted(recipes_root.rglob("*.json")):
            data = json.loads(path.read_text(encoding="utf-8"))
            if data.get("type") == "ars_nouveau:caster_tome":
                tomes.append(relative_path(path, output_root))

    write_json(tomes_path, tomes)
    log(f"Wrote {tomes_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
