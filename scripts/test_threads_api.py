#!/usr/bin/env python3
"""
Test both the old RapidAPI and the new Scrape Creators API for Threads.
Run on the server to confirm RapidAPI failure and Scrape Creators success.

Usage:
    python3 scripts/test_threads_api.py
"""

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DOTENV_PATH = REPO_ROOT / ".env"

USERNAME = "btibor91"


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or key in os.environ:
            continue
        if value and len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        os.environ[key] = value


def http_get(url: str, headers: dict) -> tuple[int, str]:
    request = urllib.request.Request(url=url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode("utf-8", errors="replace")
            return response.status, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, body
    except Exception as exc:
        return 0, str(exc)


def test_rapidapi():
    """Test the old RapidAPI endpoint."""
    key = os.environ.get("APP_TWITTER_RAPIDAPI_KEY", "")
    if not key:
        print("  SKIP: APP_TWITTER_RAPIDAPI_KEY not set")
        return False

    url = f"https://threads-api4.p.rapidapi.com/api/user/info?username={USERNAME}"
    print(f"  GET {url}")
    status, body = http_get(url, {
        "Accept": "application/json",
        "x-rapidapi-host": "threads-api4.p.rapidapi.com",
        "x-rapidapi-key": key,
    })
    print(f"  Status: {status}")
    print(f"  Response: {body[:300]}")
    return 200 <= status < 300


def test_scrapecreators():
    """Test the Scrape Creators Threads API endpoint."""
    key = os.environ.get("APP_THREADS_SCRAPECREATORS_KEY", "")
    if not key:
        print("  SKIP: APP_THREADS_SCRAPECREATORS_KEY not set")
        return False

    url = f"https://api.scrapecreators.com/v1/threads/user/posts?handle={USERNAME}"
    print(f"  GET {url}")
    status, body = http_get(url, {
        "Accept": "application/json",
        "x-api-key": key,
    })
    print(f"  Status: {status}")
    try:
        data = json.loads(body)
        print(f"  success: {data.get('success')}")
        posts = data.get('posts', [])
        print(f"  posts count: {len(posts)}")
        if data.get('success') and posts:
            first = posts[0]
            print(f"  first post: @{first.get('user', {}).get('username')} - {first.get('caption', {}).get('text', '')[:80]}...")
            return True
    except json.JSONDecodeError:
        print(f"  Response: {body[:300]}")
    return 200 <= status < 300


def test_scrapecreators_twitter():
    """Test the Scrape Creators Twitter API endpoint."""
    key = os.environ.get("APP_THREADS_SCRAPECREATORS_KEY", "")
    if not key:
        print("  SKIP: APP_THREADS_SCRAPECREATORS_KEY not set")
        return False

    url = f"https://api.scrapecreators.com/v1/twitter/user-tweets?handle=testingcatalog"
    print(f"  GET {url}")
    status, body = http_get(url, {
        "Accept": "application/json",
        "x-api-key": key,
    })
    print(f"  Status: {status}")
    try:
        data = json.loads(body)
        tweets = data.get("tweets", [])
        print(f"  tweets count: {len(tweets)}")
        if tweets:
            first = tweets[0]
            screen_name = first.get("core", {}).get("user_results", {}).get("result", {}).get("legacy", {}).get("screen_name", "?")
            text = first.get("legacy", {}).get("full_text", "")[:80]
            print(f"  first tweet: @{screen_name} - {text}...")
            return True
    except json.JSONDecodeError:
        print(f"  Response: {body[:300]}")
    return 200 <= status < 300


if __name__ == "__main__":
    load_dotenv(DOTENV_PATH)

    print("=" * 60)
    print("TEST 1: RapidAPI Threads (threads-api4.p.rapidapi.com)")
    print("=" * 60)
    rapidapi_ok = test_rapidapi()

    print()
    print("=" * 60)
    print("TEST 2: Scrape Creators Threads (api.scrapecreators.com)")
    print("=" * 60)
    scrape_threads_ok = test_scrapecreators()

    print()
    print("=" * 60)
    print("TEST 3: Scrape Creators Twitter (api.scrapecreators.com)")
    print("=" * 60)
    scrape_twitter_ok = test_scrapecreators_twitter()

    print()
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"  RapidAPI Threads:         {'✅ OK' if rapidapi_ok else '❌ FAILED'}")
    print(f"  Scrape Creators Threads:  {'✅ OK' if scrape_threads_ok else '❌ FAILED'}")
    print(f"  Scrape Creators Twitter:  {'✅ OK' if scrape_twitter_ok else '❌ FAILED'}")

    sys.exit(0 if (scrape_threads_ok and scrape_twitter_ok) else 1)
