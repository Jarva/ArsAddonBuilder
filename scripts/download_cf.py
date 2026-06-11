#!/usr/bin/env python3
"""Download a CurseForge mod and its configured dependencies."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlencode


GAME_VERSION = "1.21.1"
MOD_LOADER_TYPE = 6


def github_error(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)


def github_warning(message: str) -> None:
    print(f"::warning::{message}", file=sys.stderr)


def load_project(path: Path) -> dict:
    with path.open(encoding="utf-8") as file:
        return json.load(file)


def request_json(url: str, api_key: str) -> tuple[int, dict | None, str]:
    request = urllib.request.Request(
        url,
        headers={
            "x-api-key": api_key,
            "Accept": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body), body
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(body)
        except json.JSONDecodeError:
            parsed = None
        return error.code, parsed, body


def download_file(url: str, destination: Path) -> None:
    request = urllib.request.Request(url)
    with urllib.request.urlopen(request, timeout=120) as response:
        destination.write_bytes(response.read())


def latest_file_for_mod(mod_id: int, api_key: str) -> tuple[int, dict | None, str]:
    query = urlencode(
        {
            "gameVersion": GAME_VERSION,
            "modLoaderType": MOD_LOADER_TYPE,
            "pageSize": 1,
        }
    )
    url = f"https://api.curseforge.com/v1/mods/{mod_id}/files?{query}"
    return request_json(url, api_key)


def download_cf_file(mod_id: int, label: str, api_key: str, mods_dir: Path) -> bool:
    status, response, raw_body = latest_file_for_mod(mod_id, api_key)
    if status != 200:
        github_error(f"API request failed for {label} (mod ID {mod_id}) - HTTP {status}")
        if raw_body:
            print(raw_body)
        return False

    data = (response or {}).get("data") or []
    if not data:
        github_warning(f"No file found for {label} (mod ID {mod_id})")
        return False

    file_info = data[0]
    file_id = file_info.get("id")
    filename = file_info.get("fileName")
    download_url = file_info.get("downloadUrl")

    if not file_id:
        github_warning(f"No file found for {label} (mod ID {mod_id})")
        return False

    if not download_url:
        github_error(
            f"No direct download URL for {label} (mod ID {mod_id}) - direct downloads must be enabled"
        )
        return False

    if not filename:
        github_error(f"No filename returned for {label} (mod ID {mod_id})")
        return False

    print(f"Downloading {label}: {filename} (file ID {file_id})")
    download_file(download_url, mods_dir / filename)
    print(f"Downloaded {filename}")
    return True


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: download_cf.py <project_json_path>", file=sys.stderr)
        return 2

    api_key = os.environ.get("CF_API_KEY")
    if not api_key:
        github_error("CF_API_KEY environment variable is required")
        return 1

    project_path = Path(sys.argv[1])
    project = load_project(project_path)
    name = project_path.stem
    cf_id = project.get("cf_id")
    dependencies = project.get("dependencies") or []

    if not cf_id:
        github_error(f"{project_path} does not define cf_id")
        return 1

    mods_dir = Path("mods")
    mods_dir.mkdir(exist_ok=True)

    if not download_cf_file(int(cf_id), name, api_key, mods_dir):
        return 1

    for dependency in dependencies:
        dep_id = dependency.get("cf_id")
        dep_name = dependency.get("name") or str(dep_id)
        if not dep_id:
            github_warning(f"Skipping dependency without cf_id in {project_path}")
            continue
        download_cf_file(int(dep_id), dep_name, api_key, mods_dir)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
