#!/usr/bin/env python3

"""
Fetch recent Threads posts for a fixed list of accounts.

Uses the Scrape Creators API (api.scrapecreators.com) to fetch posts.
Each account only needs a `username` and `category`.

Usage:
    python3 scripts/fetch_threads_accounts.py
    python3 scripts/fetch_threads_accounts.py --all --include-raw
    python3 scripts/fetch_threads_accounts.py --output /tmp/threads.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_CONFIG_PATH = SCRIPT_DIR / "threads_accounts.json"
DEFAULT_CACHE_DIR = SCRIPT_DIR / ".cache"
DEFAULT_OUTPUT_PATH = SCRIPT_DIR / "output" / "threads_posts_latest.json"
DEFAULT_ERROR_OUTPUT_PATH = SCRIPT_DIR / "output" / "threads_posts_errors.json"
DOTENV_PATH = REPO_ROOT / ".env"

# Scrape Creators API
SCRAPECREATORS_BASE_URL = "https://api.scrapecreators.com"
SCRAPECREATORS_POSTS_PATH = "/v1/threads/user/posts"
REQUEST_TIMEOUT_SECONDS = 30

SEEN_POST_IDS_PATH = DEFAULT_CACHE_DIR / "threads_seen_post_ids.json"

MEDIA_TYPE_LABELS = {
    1: "image",
    2: "video",
    8: "carousel",
    19: "text",
}


@dataclass(frozen=True)
class AccountConfig:
    username: str
    category: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch recent Threads posts into a normalized JSON file."
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="Path to account config JSON file.",
    )
    parser.add_argument(
        "--output",
        default=str(DEFAULT_OUTPUT_PATH),
        help="Path to merged JSON output file.",
    )
    parser.add_argument(
        "--error-output",
        default=str(DEFAULT_ERROR_OUTPUT_PATH),
        help="Path to JSON error report file.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Include posts that have already been seen in previous runs.",
    )
    parser.add_argument(
        "--include-raw",
        action="store_true",
        help="Include the raw post payload for each normalized item.",
    )
    parser.add_argument(
        "--max-posts-per-user",
        type=int,
        default=0,
        help="Optional limit per user after filtering. 0 means no limit.",
    )
    parser.add_argument(
        "--skip-replies",
        action="store_true",
        default=True,
        help="Skip reply posts. Enabled by default.",
    )
    parser.add_argument(
        "--no-skip-replies",
        action="store_false",
        dest="skip_replies",
        help="Keep reply posts in the output.",
    )
    return parser.parse_args()


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


def get_api_key() -> str:
    load_dotenv(DOTENV_PATH)
    key = os.environ.get("APP_THREADS_SCRAPECREATORS_KEY")
    if not key:
        raise RuntimeError(
            "Missing Scrape Creators API key. Set APP_THREADS_SCRAPECREATORS_KEY "
            "in the environment or .env file."
        )
    return key


def read_json_file(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return default


def write_json_file(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def load_accounts(path: Path) -> list[AccountConfig]:
    raw = read_json_file(path, default=[])
    accounts: list[AccountConfig] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        username = str(item.get("username", "")).strip().lstrip("@")
        category = str(item.get("category", "UNCATEGORIZED")).strip().upper()
        if username:
            accounts.append(AccountConfig(username=username, category=category))
    if not accounts:
        raise RuntimeError(f"No valid accounts found in {path}")
    return accounts


def scrapecreators_get(username: str, api_key: str) -> Any:
    """Fetch posts for a Threads user via Scrape Creators API."""
    params = urllib.parse.urlencode({"handle": username})
    url = f"{SCRAPECREATORS_BASE_URL}{SCRAPECREATORS_POSTS_PATH}?{params}"
    request = urllib.request.Request(
        url=url,
        headers={
            "Accept": "application/json",
            "User-Agent": "curl/8.5.0",
            "x-api-key": api_key,
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            body = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} for {url}: {body[:300]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Network error for {url}: {exc}") from exc

    try:
        payload = json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Non-JSON response for {url}: {body[:300]}") from exc

    if isinstance(payload, dict) and not payload.get("success", True):
        raise RuntimeError(
            f"API error for {url}: {payload.get('error') or payload.get('message') or payload}"
        )
    return payload


def as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def isoformat_from_timestamp(timestamp: int | None) -> str | None:
    if not timestamp:
        return None
    return datetime.fromtimestamp(timestamp, tz=timezone.utc).isoformat()


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    return "\n".join(part.rstrip() for part in value.strip().splitlines()).strip()


def extract_text(post: dict[str, Any]) -> str:
    caption_text = normalize_text(as_dict(post.get("caption")).get("text"))
    if caption_text:
        return caption_text
    return ""


def summarize_text(text: str, limit: int = 255) -> str:
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    return compact[: limit - 1].rstrip() + "…"


def build_post_url(username: str, code: str | None) -> str | None:
    if not code:
        return None
    return f"https://www.threads.com/@{username}/post/{code}"


def select_thumbnail_url(post: dict[str, Any]) -> str | None:
    """Try to extract a thumbnail URL from the post."""
    # image_versions2 (if present)
    image_candidates = as_list(as_dict(post.get("image_versions2")).get("candidates"))
    if image_candidates:
        return image_candidates[0].get("url")

    # carousel_media (if present)
    carousel_media = as_list(post.get("carousel_media"))
    for item in carousel_media:
        candidates = as_list(as_dict(item.get("image_versions2")).get("candidates"))
        if candidates:
            return candidates[0].get("url")

    # Fall back to user profile pic
    return as_dict(post.get("user")).get("profile_pic_url")


def normalize_post(
    *,
    account: AccountConfig,
    post: dict[str, Any],
    include_raw: bool,
) -> dict[str, Any] | None:
    post_id = str(post.get("pk") or post.get("id") or "").strip()
    if not post_id:
        return None

    post_user = as_dict(post.get("user"))
    post_username = str(post_user.get("username") or account.username).strip()
    if post_username.lower() != account.username.lower():
        return None

    text = extract_text(post)
    if not text:
        return None

    post_code = post.get("code")
    # Prefer the url field from the API if available
    source_url = post.get("url") or build_post_url(post_username, post_code)

    normalized = {
        "platform": "threads",
        "category": account.category,
        "source_name": f"Threads @{post_username}",
        "username": post_username,
        "user_id": str(post_user.get("pk") or post_user.get("id") or ""),
        "post_id": post_id,
        "post_code": post_code,
        "source_url": source_url,
        "text": text,
        "title": summarize_text(text, limit=160),
        "summary": summarize_text(text, limit=255),
        "published_at": isoformat_from_timestamp(post.get("taken_at")),
        "published_at_unix": post.get("taken_at"),
        "like_count": post.get("like_count"),
        "thumbnail_url": select_thumbnail_url(post),
    }
    if include_raw:
        normalized["raw"] = post
    return normalized


def fetch_posts_for_account(
    *,
    account: AccountConfig,
    api_key: str,
    include_raw: bool,
    max_posts_per_user: int,
    seen_post_ids: set[str],
    include_seen: bool,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    payload = scrapecreators_get(account.username, api_key)
    posts = as_list(payload.get("posts"))

    items: list[dict[str, Any]] = []
    skipped_as_seen = 0
    skipped_without_text = 0
    dedupe_guard: set[str] = set()

    for post in posts:
        if not isinstance(post, dict):
            continue
        normalized = normalize_post(
            account=account,
            post=post,
            include_raw=include_raw,
        )
        if normalized is None:
            skipped_without_text += 1
            continue
        post_id = normalized["post_id"]
        if post_id in dedupe_guard:
            continue
        dedupe_guard.add(post_id)
        if not include_seen and post_id in seen_post_ids:
            skipped_as_seen += 1
            continue
        items.append(normalized)
        if max_posts_per_user > 0 and len(items) >= max_posts_per_user:
            break

    credits_remaining = payload.get("credits_remaining")
    stats = {
        "username": account.username,
        "category": account.category,
        "fetched": len(items),
        "skipped_as_seen": skipped_as_seen,
        "skipped_without_text": skipped_without_text,
        "credits_remaining": credits_remaining,
    }
    return items, stats


def build_output_payload(
    *,
    accounts: list[AccountConfig],
    items: list[dict[str, Any]],
    user_stats: list[dict[str, Any]],
    errors: list[dict[str, str]],
    include_seen: bool,
) -> dict[str, Any]:
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "provider": "api.scrapecreators.com",
        "mode": "all" if include_seen else "new_only",
        "account_count": len(accounts),
        "item_count": len(items),
        "error_count": len(errors),
        "accounts": [account.__dict__ for account in accounts],
        "per_account": user_stats,
        "errors": errors,
        "items": items,
    }


def main() -> int:
    args = parse_args()
    api_key = get_api_key()
    accounts = load_accounts(Path(args.config))

    seen_payload = read_json_file(SEEN_POST_IDS_PATH, default={})
    seen_post_ids = set(seen_payload.get("seen_post_ids", []))

    items: list[dict[str, Any]] = []
    user_stats: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []

    for account in accounts:
        print(f"Fetching Threads posts for @{account.username} ({account.category})...")
        try:
            fetched_items, stats = fetch_posts_for_account(
                account=account,
                api_key=api_key,
                include_raw=args.include_raw,
                max_posts_per_user=args.max_posts_per_user,
                seen_post_ids=seen_post_ids,
                include_seen=args.all,
            )
        except Exception as exc:  # noqa: BLE001 - keep per-account failures isolated.
            errors.append({"username": account.username, "error": str(exc)})
            print(f"  ERROR: {exc}")
            continue

        items.extend(fetched_items)
        user_stats.append(stats)
        credits_info = f" | credits: {stats['credits_remaining']}" if stats.get("credits_remaining") is not None else ""
        print(
            "  saved to output: "
            f"{stats['fetched']} | "
            f"seen skipped: {stats['skipped_as_seen']}"
            f"{credits_info}"
        )

    items.sort(
        key=lambda item: item.get("published_at_unix") or 0,
        reverse=True,
    )

    output_payload = build_output_payload(
        accounts=accounts,
        items=items,
        user_stats=user_stats,
        errors=errors,
        include_seen=args.all,
    )
    write_json_file(Path(args.output), output_payload)
    write_json_file(Path(args.error_output), {"generated_at": output_payload["generated_at"], "errors": errors})

    if items:
        seen_post_ids.update(item["post_id"] for item in items)
    write_json_file(
        SEEN_POST_IDS_PATH,
        {
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "seen_post_ids": sorted(seen_post_ids),
        },
    )

    print()
    print(f"Done. Items written: {len(items)}")
    print(f"Output file: {args.output}")
    print(f"Error report: {args.error_output}")
    if errors:
        print(f"Accounts with errors: {len(errors)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
