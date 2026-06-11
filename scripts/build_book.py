#!/usr/bin/env python3
"""Build an index of exported book categories and entries."""

from __future__ import annotations

import json
from pathlib import Path

from manifest_utils import log, manifest_arg, output_arg, relative_path, write_json


def scan_docs(output_root: Path, kind: str) -> list[str]:
    wiki_root = output_root / "wiki"
    if not wiki_root.is_dir():
        return []

    return [
        relative_path(path, output_root)
        for path in sorted(wiki_root.glob(f"*/{kind}/*.json"))
        if path.is_file()
    ]


def main() -> int:
    output_dir = output_arg()
    book_path = manifest_arg(output_dir, "book.json")

    log(f"Building book index for {output_dir}...")

    output_root = output_dir.resolve()
    book = {
        "categories": scan_docs(output_root, "categories"),
        "entries": scan_docs(output_root, "entries"),
    }

    write_json(book_path, book)
    log(f"Wrote {book_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
