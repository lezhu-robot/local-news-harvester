#!/usr/bin/env python3

"""
Add the TestingCatalog Twitter/X account to the local news harvester.

Usage:
    python3 scripts/add_testingcatalog_twitter_source.py

All configuration is intentionally kept in this file so it can be run
directly without extra command-line arguments.
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request


# The script will try these backend addresses in order and use the first one
# that responds. This keeps the script one-click runnable for both local
# Spring Boot (`8080`) and Docker Compose (`9090`) setups.
BASE_URL_CANDIDATES = [
    "http://localhost:9090",
    "http://localhost:8080",
]

# Feed settings for this account.
FEED_NAME = "X @testingcatalog"
TWITTER_URL = "https://x.com/testingcatalog"
SOURCE_TYPE = "TWITTER"
ENABLED = "true"
CATEGORY = "AI"


def detect_base_url() -> str:
    """Pick the first reachable backend URL from the built-in candidates."""
    for base_url in BASE_URL_CANDIDATES:
        request = urllib.request.Request(
            url=f"{base_url}/api/feeditems",
            method="GET",
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                if 200 <= response.status < 300:
                    return base_url
        except urllib.error.URLError:
            continue
    raise RuntimeError(
        "Could not reach the backend. Checked: "
        + ", ".join(BASE_URL_CANDIDATES)
    )


def pretty_body(body: str) -> str:
    """Pretty-print JSON responses and fall back to raw text otherwise."""
    try:
        return json.dumps(json.loads(body), ensure_ascii=False, indent=2)
    except json.JSONDecodeError:
        return body


def add_feed(base_url: str) -> None:
    """Send one POST request to create the Twitter feed."""
    payload = urllib.parse.urlencode(
        {
            "name": FEED_NAME,
            "url": TWITTER_URL,
            "sourceType": SOURCE_TYPE,
            "enabled": ENABLED,
            "category": CATEGORY,
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
            print(f"[{response.status}] {FEED_NAME}")
            print(pretty_body(body))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(f"[HTTP {exc.code}] {FEED_NAME}")
        print(pretty_body(body))
    except urllib.error.URLError as exc:
        print(f"[NETWORK ERROR] {FEED_NAME}: {exc}")


def main() -> int:
    base_url = detect_base_url()
    print(f"Using backend: {base_url}")
    print(f"Adding Twitter source: {FEED_NAME}")
    add_feed(base_url)
    return 0


if __name__ == "__main__":
    sys.exit(main())
