from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


def output_arg(default: str = "output") -> Path:
    return Path(sys.argv[1]) if len(sys.argv) > 1 else Path(default)


def manifest_arg(output_dir: Path, default_name: str) -> Path:
    return Path(sys.argv[2]) if len(sys.argv) > 2 else output_dir / "manifests" / default_name


def write_json(path: Path, data: Any, *, sort_keys: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=sort_keys) + "\n", encoding="utf-8")


def relative_path(path: Path, output_root: Path) -> str:
    return path.relative_to(output_root).as_posix()


def resource_location(path: Path, root: Path) -> str:
    relative = path.relative_to(root)
    namespace = relative.parts[0]
    resource_path = relative.with_suffix("").as_posix().split("/", 1)[1]
    return f"{namespace}:{resource_path}"


def resource_manifest(output_root: Path, root: Path, pattern: str = "*.json") -> dict[str, str]:
    if not root.is_dir():
        return {}

    return {
        resource_location(path, root): relative_path(path, output_root)
        for path in sorted(root.rglob(pattern))
        if path.is_file()
    }


def log(message: str) -> None:
    print(message, file=sys.stderr)
