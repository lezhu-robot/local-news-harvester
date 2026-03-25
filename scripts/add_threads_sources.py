#!/usr/bin/env python3

"""
Batch add Threads sources to the local news harvester.

Usage:
    python3 scripts/add_threads_sources.py
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


BASE_URL_CANDIDATES = [
    "http://localhost:9090",
    "http://localhost:8080",
]

ENABLED = "true"
SOURCE_TYPE = "THREADS"
CONFIG_PATH = Path(__file__).resolve().parent / "threads_accounts.json"


def detect_base_url() -> str:
    for base_url in BASE_URL_CANDIDATES:
        request = urllib.request.Request(url=f"{base_url}/api/feeditems", method="GET")
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                if 200 <= response.status < 300:
                    return base_url
        except urllib.error.URLError:
            continue
    raise RuntimeError("Could not reach the backend. Checked: " + ", ".join(BASE_URL_CANDIDATES))


def pretty_body(body: str) -> str:
    try:
        return json.dumps(json.loads(body), ensure_ascii=False, indent=2)
    except json.JSONDecodeError:
        return body


def load_accounts() -> list[tuple[str, str, str]]:
    payload = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    result: list[tuple[str, str, str]] = []
    for item in payload:
        username = str(item.get("username", "")).strip().lstrip("@")
        category = str(item.get("category", "UNCATEGORIZED")).strip().upper()
        if username:
            result.append((f"Threads @{username}", f"https://www.threads.com/@{username}", category))
    return result


def add_feed(base_url: str, name: str, url: str, category: str) -> None:
    payload = urllib.parse.urlencode(
        {
            "name": name,
            "url": url,
            "sourceType": SOURCE_TYPE,
            "enabled": ENABLED,
            "category": category,
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        url=f"{base_url}/feeds/new",
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read().decode("utf-8", errors="replace")
            print(f"[{response.status}] {name}")
            print(pretty_body(body))
            print()
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(f"[HTTP {exc.code}] {name}")
        print(pretty_body(body))
        print()
    except urllib.error.URLError as exc:
        print(f"[NETWORK ERROR] {name}: {exc}")
        print()


def main() -> int:
    base_url = detect_base_url()
    print(f"Using backend: {base_url}")
    print()

    for name, url, category in load_accounts():
        print(f"Adding Threads source: {name}")
        add_feed(base_url, name, url, category)
    return 0


if __name__ == "__main__":
    sys.exit(main())
