#!/usr/bin/env python3

"""
Fetch all feed sources, count articles from the recent time window, and print
the per-source article totals to the console.

Configuration is intentionally hardcoded below.
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timedelta, timezone
from typing import Any


# Hardcoded configuration
BASE_URL = "http://localhost:9090"
LOOKBACK_HOURS = 24
REQUEST_TIMEOUT_SECONDS = 30
ONLY_ENABLED_SOURCES = True
SORT_OUTPUT_BY_COUNT_DESC = True


def utc_iso_z(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def http_json(url: str, method: str = "GET", payload: dict[str, Any] | None = None) -> dict[str, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url=url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            body = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {url}: {body[:500]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error for {url}: {exc}") from exc

    try:
        return json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Non-JSON response for {url}: {body[:500]}") from exc


def fetch_sources() -> list[dict[str, Any]]:
    payload = http_json(f"{BASE_URL}/api/feeditems")
    sources = payload.get("data")
    if not isinstance(sources, list):
        raise RuntimeError("Unexpected /api/feeditems response: data is not a list")

    result: list[dict[str, Any]] = []
    for item in sources:
        if not isinstance(item, dict):
            continue
        if ONLY_ENABLED_SOURCES and not bool(item.get("enabled")):
            continue
        result.append(item)
    return result


def fetch_recent_articles(start_time: datetime, end_time: datetime) -> list[dict[str, Any]]:
    payload = http_json(
        f"{BASE_URL}/api/newsarticles/search",
        method="POST",
        payload={
            "startDateTime": utc_iso_z(start_time),
            "endDateTime": utc_iso_z(end_time),
            "sortOrder": "latest",
            "includeContent": False,
        },
    )
    articles = payload.get("data")
    if not isinstance(articles, list):
        raise RuntimeError("Unexpected /api/newsarticles/search response: data is not a list")
    return articles


def main() -> int:
    end_time = datetime.now(timezone.utc)
    start_time = end_time - timedelta(hours=LOOKBACK_HOURS)

    sources = fetch_sources()
    articles = fetch_recent_articles(start_time, end_time)

    article_counts = Counter()
    for article in articles:
        if not isinstance(article, dict):
            continue
        source_name = str(article.get("sourceName", "")).strip()
        if source_name:
            article_counts[source_name] += 1

    source_name_counts = Counter()
    for source in sources:
        name = str(source.get("name", "")).strip()
        if name:
            source_name_counts[name] += 1

    rows: list[tuple[int | None, str, str, str, bool, int, bool]] = []
    for source in sources:
        source_id_raw = source.get("id")
        source_id = int(source_id_raw) if isinstance(source_id_raw, int) else None
        name = str(source.get("name", "")).strip()
        source_type = str(source.get("sourceType", "")).strip()
        category = str(source.get("category", "")).strip()
        enabled = bool(source.get("enabled"))
        is_duplicate_name = source_name_counts.get(name, 0) > 1
        rows.append((source_id, name, source_type, category, enabled, article_counts.get(name, 0), is_duplicate_name))

    if SORT_OUTPUT_BY_COUNT_DESC:
        rows.sort(key=lambda row: (-row[5], row[1].lower(), row[0] or 0))
    else:
        rows.sort(key=lambda row: (row[1].lower(), row[0] or 0))

    print(f"Backend: {BASE_URL}")
    print(f"Window:  {utc_iso_z(start_time)} -> {utc_iso_z(end_time)}")
    print(f"Sources: {len(rows)}")
    print(f"Articles in window: {len(articles)}")
    print()
    print(f"{'COUNT':>5}  {'ID':>6}  {'EN':<2}  {'TYPE':<8}  {'CATEGORY':<14}  SOURCE")
    print("-" * 96)
    for source_id, name, source_type, category, enabled, count, is_duplicate_name in rows:
        enabled_label = "Y" if enabled else "N"
        source_label = name + (" [DUPLICATE_NAME]" if is_duplicate_name else "")
        source_id_label = str(source_id) if source_id is not None else "-"
        print(f"{count:>5}  {source_id_label:>6}  {enabled_label:<2}  {source_type:<8}  {category:<14}  {source_label}")

    zero_count_sources = sum(1 for _, _, _, _, _, count, _ in rows if count == 0)
    duplicate_name_sources = sum(1 for count in source_name_counts.values() if count > 1)
    print()
    print(f"Sources with 0 articles in the window: {zero_count_sources}")
    print(f"Duplicate source names: {duplicate_name_sources}")
    if duplicate_name_sources:
        print("Note: article counts are grouped by sourceName, so duplicate feed names will share the same count.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
