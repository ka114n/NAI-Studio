"""Fetch ranked character reference images using Danbooru's public JSON API.

Python 3.10+, standard library only. See docs/DANBOORU_CHARACTER_PREVIEWS.md.
Generated images/state stay outside the source checkout by default.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import math
from pathlib import Path
import re
import sqlite3
import sys
import time
from contextlib import closing
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


BASE_URL = "https://safebooru.donmai.us"
IMAGE_TYPES = {"jpg", "jpeg", "png", "webp"}
RATINGS = {"general": "g"}
SEARCH_METATAGS = set("""user approver commenter comm noter noteupdater artcomm commentaryupdater
    flagger appealer upvote downvote fav ordvote ordfav favgroup ordfavgroup reacted pool
    ordpool note comment commentary id rating source status filetype disapproved parent child
    search embedded md5 pixelhash width height mpixels ratio score upvotes downvotes favcount
    filesize date age order limit tagcount pixiv_id pixiv unaliased exif duration random is has ai
    gentags arttags copytags chartags metatags""".split())


class FetchError(Exception):
    pass


class StopBatch(FetchError):
    """Do not repeat a global access/network failure across thousands of tags."""


class RedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urlparse(newurl).scheme != "https":
            raise FetchError("Refusing a non-HTTPS redirect")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected and urlparse(req.full_url).hostname != urlparse(newurl).hostname:
            redirected.remove_header("Authorization")
        return redirected


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def tag_name(value):
    value = re.sub(r"\s+", "_", value.strip().lower())
    if not value or value in {"and", "or"} or any(c in value for c in "*~{}\"\r\n") or value.startswith("-"):
        raise ValueError("Enter one exact English tag, without search operators")
    # Colons inside parentheses are legitimate, e.g. 2b_(nier:automata).
    prefix = value.split("(", 1)[0].split(":", 1)[0]
    if ":" in value.split("(", 1)[0] and (prefix in SEARCH_METATAGS or prefix.endswith("_count")):
        raise ValueError("Use exact tags, not search metatags")
    return value


def filter_tags(values):
    """Repeated arguments or comma-separated tags; spaces belong to one tag."""
    return sorted({tag_name(part) for value in values or []
                   for part in re.split(r"[,，]", value) if part.strip()})


def tag_conditions(opts):
    required = set(opts.include_tag)
    if opts.solo:
        required.add("solo")
    return sorted(required), sorted(set(opts.exclude_tag))


def build_query(opts, character, copyright=""):
    parts = [character, "order:" + opts.sort]
    parts.append("rating:general")
    if copyright and copyright != character:
        parts.append(copyright)
    if opts.filter_mode == "server":
        required, excluded = tag_conditions(opts)
        parts.extend(t for t in required if t != character)
        parts.extend("-" + t for t in excluded)
    return " ".join(parts)


def copyright_filter_reason(post, copyrights):
    if copyrights and not (set(copyrights) & set(str(post.get("tag_string_copyright") or "").lower().split())):
        return "not tagged with a selected copyright"
    return None


def discover_characters(client, opts):
    """Read actual character tags, including names without a franchise suffix."""
    discovered = {}
    for copyright in opts.copyright:
        cursor = 1
        page_count = 0
        while not opts.discovery_pages or page_count < opts.discovery_pages:
            posts = client.posts(f"{copyright} rating:general order:id_desc", cursor, limit=200)
            page_count += 1
            if not posts:
                break
            ids = [p["id"] for p in posts if isinstance(p.get("id"), int) and p["id"] > 0]
            if not ids:
                raise StopBatch("Invalid post IDs during copyright discovery")
            for post in posts:
                if (post.get("rating") != "g" or post.get("is_deleted") or post.get("is_banned")
                        or copyright_filter_reason(post, [copyright])):
                    continue
                for character in str(post.get("tag_string_character") or "").split():
                    discovered.setdefault(tag_name(character), ("", 0))
            print(f"  Discover {copyright}: page {page_count}, characters so far={len(discovered)}", flush=True)
            if len(posts) < 200:
                break
            next_cursor = "b" + str(min(ids))
            if next_cursor == cursor:
                raise StopBatch("Copyright discovery cursor did not advance")
            cursor = next_cursor
        else:
            print(f"  Discovery window reached for {copyright}; use --discovery-pages 0 to scan all accessible posts.", flush=True)
    return [(name, *discovered[name]) for name in sorted(discovered)]


def candidate_posts(client, opts, character):
    if not opts.copyright:
        for page in range(1, opts.pages + 1):
            posts = client.posts(build_query(opts, character), page)
            yield from posts
            if len(posts) < opts.candidates:
                break
        return
    # Query each work separately: OR without an ever-growing account tag count.
    candidates = {}
    for copyright in opts.copyright:
        query = build_query(opts, character, copyright)
        for page in range(1, opts.pages + 1):
            posts = client.posts(query, page)
            for post in posts:
                if isinstance(post.get("id"), int):
                    candidates.setdefault(post["id"], post)
            if len(posts) < opts.candidates:
                break
    field = {"score": "score", "upvotes": "up_score", "favcount": "fav_count"}[opts.sort]
    yield from sorted(candidates.values(), key=lambda p: (int(p.get(field) or 0), p["id"]), reverse=True)


def tag_filter_reason(post, required, excluded):
    tags = set()
    for key in ("tag_string", "tag_string_general", "tag_string_artist", "tag_string_character",
                "tag_string_copyright", "tag_string_meta"):
        tags.update(str(post.get(key) or "").lower().split())
    missing = sorted(set(required) - tags)
    blocked = sorted(set(excluded) & tags)
    if missing:
        return "missing required tags: " + ", ".join(missing)
    if blocked:
        return "excluded tags present: " + ", ".join(blocked)
    return None


def profile_name(opts):
    profile = f"safebooru-{opts.sort}-{opts.rating}-{'solo' if opts.solo else 'any'}"
    required, excluded = tag_conditions(opts)
    if opts.include_tag or excluded or opts.copyright or (required and opts.filter_mode != "server"):
        conditions = {"required": required, "excluded": excluded, "mode": opts.filter_mode,
                      "copyrights": opts.copyright, "endpoint": BASE_URL}
        signature = hashlib.sha256(json.dumps(conditions, sort_keys=True).encode()).hexdigest()[:16]
        profile += "-filters-" + signature
    return profile


def character_list(opts, client):
    result = {}
    explicit = set()
    if opts.dictionary:
        with closing(sqlite3.connect(opts.dictionary.resolve().as_uri() + "?mode=ro", uri=True)) as db:
            query = ("SELECT name,cn_name,post_count FROM tags "
                     "WHERE category=4 AND post_count>=? ORDER BY post_count DESC,name")
            params = [opts.min_posts]
            if opts.max_characters and not opts.copyright:
                query += " LIMIT ?"
                params.append(opts.max_characters)
            rows = db.execute(query, params)
            unsupported = 0
            for name, chinese, count in rows:
                try:
                    key = tag_name(name)
                except ValueError:
                    unsupported += 1
                    continue
                result[key] = (chinese or "", int(count or 0))
            if unsupported:
                print(f"Skipped {unsupported} dictionary tags containing unsupported search syntax", flush=True)
    if opts.tags_file:
        for line in opts.tags_file.read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.lstrip().startswith("#"):
                parts = line.split("\t", 1)
                key = tag_name(parts[0])
                explicit.add(key)
                result[key] = (parts[1].strip() if len(parts) > 1 else "", 0)
    for value in opts.tag or []:
        key = tag_name(value)
        explicit.add(key)
        result.setdefault(key, ("", 0))
    if opts.copyright:
        if not (opts.tag or opts.dictionary or opts.tags_file):
            rows = discover_characters(client, opts)
        elif opts.dictionary:
            discovered = {row[0] for row in discover_characters(client, opts)}
            rows = [(name, *details) for name, details in result.items() if name in discovered or name in explicit]
        else:
            rows = [(name, *details) for name, details in result.items()]
    else:
        rows = [(name, *details) for name, details in result.items()]
    return rows[:opts.max_characters] if opts.max_characters else rows


class Client:
    def __init__(self, opts):
        self.opts = opts
        self.next_request = 0.0
        self.auth = None
        self.opener = build_opener(RedirectHandler())
        login = os.environ.get("DANBOORU_LOGIN", "")
        key = os.environ.get("DANBOORU_API_KEY", "")
        if bool(login) != bool(key):
            raise ValueError("DANBOORU_LOGIN and DANBOORU_API_KEY must be set together")
        if login and key:
            self.auth = "Basic " + base64.b64encode(f"{login}:{key}".encode()).decode()

    def open(self, url, api=False):
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.username or parsed.password:
            raise FetchError("Invalid HTTPS URL")
        # Never send account credentials to an image CDN.
        headers = {"User-Agent": self.opts.user_agent,
                   "Accept": "application/json" if api else "image/*"}
        if api and parsed.hostname != urlparse(BASE_URL).hostname:
            raise FetchError("Refusing an API request outside the configured safe site")
        if api and self.auth:
            headers["Authorization"] = self.auth
        for attempt in range(self.opts.retries + 1):
            time.sleep(max(0.0, self.next_request - time.monotonic()))
            self.next_request = time.monotonic() + self.opts.interval
            try:
                return self.opener.open(Request(url, headers=headers), timeout=self.opts.timeout)
            except HTTPError as error:
                status = error.code
                retry_after = error.headers.get("Retry-After", "")
                error.close()
                if status in (401, 403) and api:
                    raise StopBatch(f"API HTTP {status}: check account permissions or network access") from None
                if status in (400, 422, 424) and api:
                    raise StopBatch(f"API HTTP {status}: invalid search or account tag limit; check tags or use --filter-mode local") from None
                if status != 429 and status not in (500, 502, 503, 504):
                    raise FetchError(f"HTTP {status}") from None
                if attempt == self.opts.retries:
                    raise StopBatch(f"HTTP {status}: retries exhausted; resume later") from None
                delay = min(60.0, 2.0 ** (attempt + 1))
                if retry_after:
                    try:
                        delay = max(delay, float(retry_after))
                    except ValueError:
                        try:
                            delay = max(delay, parsedate_to_datetime(retry_after).timestamp() - time.time())
                        except (ValueError, TypeError, OverflowError):
                            pass
                print(f"  HTTP {status}, retry in {delay:.0f}s", flush=True)
                time.sleep(max(0.0, delay))
            except (URLError, TimeoutError, OSError):
                if attempt == self.opts.retries:
                    raise StopBatch("Network request failed; check VPN/proxy and resume later") from None
                time.sleep(min(30.0, 2.0 ** (attempt + 1)))
        raise FetchError("Request failed")

    def posts(self, query, page, limit=None):
        url = BASE_URL + "/posts.json?" + urlencode(
            {"tags": query, "limit": limit or self.opts.candidates, "page": page})
        with self.open(url, api=True) as response:
            raw = response.read(8 * 1024 * 1024 + 1)
            if len(raw) > 8 * 1024 * 1024:
                raise StopBatch("API response too large")
            try:
                posts = json.loads(raw)
            except (ValueError, UnicodeError):
                raise StopBatch("API returned non-JSON content; check network access") from None
        if not isinstance(posts, list) or any(not isinstance(p, dict) for p in posts):
            raise StopBatch("Unexpected API response; stopping batch")
        return posts

    def download(self, post, destination):
        url = post["file_url"]
        partial = destination.with_suffix(destination.suffix + ".part")
        total = 0
        sha256 = hashlib.sha256()
        md5 = hashlib.md5()
        try:
            with self.open(url) as response, partial.open("wb") as output:
                content_type = response.headers.get("Content-Type", "").split(";", 1)[0]
                if not content_type.startswith("image/"):
                    raise FetchError("Download is not an image")
                while chunk := response.read(128 * 1024):
                    total += len(chunk)
                    if total > self.opts.max_mb * 1024 * 1024:
                        raise FetchError("Image exceeds --max-mb")
                    sha256.update(chunk)
                    md5.update(chunk)
                    output.write(chunk)
            if not total:
                raise FetchError("Empty image")
            if post.get("md5") and md5.hexdigest() != post["md5"].lower():
                raise FetchError("Image MD5 mismatch")
            partial.replace(destination)
            return sha256.hexdigest(), total
        except (URLError, TimeoutError):
            raise StopBatch("Image connection interrupted; resume later") from None
        finally:
            partial.unlink(missing_ok=True)


def open_state(directory):
    db = sqlite3.connect(directory / "download-state.sqlite")
    db.execute("""CREATE TABLE IF NOT EXISTS downloads (
        character TEXT NOT NULL, profile TEXT NOT NULL, status TEXT NOT NULL,
        file TEXT, sha256 TEXT, metadata TEXT NOT NULL, updated_at TEXT NOT NULL,
        PRIMARY KEY(character,profile))""")
    db.commit()
    return db


def record(db, tag, profile, status, metadata, file="", sha256=""):
    db.execute("INSERT OR REPLACE INTO downloads VALUES(?,?,?,?,?,?,?)",
               (tag, profile, status, file, sha256,
                json.dumps(metadata, ensure_ascii=False), timestamp()))
    db.commit()


def completed(db, tag, profile, directory, retry_missing):
    row = db.execute("SELECT status,file,sha256 FROM downloads WHERE character=? AND profile=?",
                     (tag, profile)).fetchone()
    if not row:
        return False
    if row[0] == "missing":
        return not retry_missing
    if row[0] != "ok":
        return False
    image = directory / row[1]
    metadata = image.with_suffix(image.suffix + ".json")
    if not image.is_file() or not metadata.is_file():
        return False
    with image.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest() if sys.version_info >= (3, 11) else hash_stream(stream)
    return digest == row[2]


def hash_stream(stream):
    digest = hashlib.sha256()
    while chunk := stream.read(128 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def fetch_character(client, opts, tag, chinese, dictionary_count, folder):
    queries = [build_query(opts, tag, work) for work in opts.copyright] or [build_query(opts, tag)]
    query = queries[0] if len(queries) == 1 else " OR ".join(f"({q})" for q in queries)
    required, excluded = tag_conditions(opts)
    conditions = {"required_tags": required, "excluded_tags": excluded, "filter_mode": opts.filter_mode,
                  "selected_copyrights": opts.copyright, "queries": queries, "api_base_url": BASE_URL}
    if tag in excluded:
        return None, "", {"character": tag, "chinese": chinese, "query": query, **conditions,
                          "reason": "Character tag itself is excluded", "skipped_candidates": []}
    rejected = []
    rank = 0
    seen = set()
    for post in candidate_posts(client, opts, tag):
        if post.get("id") in seen:
            continue
        seen.add(post.get("id"))
        rank += 1
        reason = None
        extension = str(post.get("file_ext", "")).lower()
        if not isinstance(post.get("id"), int):
            reason = "invalid post id"
        elif post.get("is_deleted") or post.get("is_banned"):
            reason = "deleted or banned"
        elif extension not in IMAGE_TYPES:
            reason = "not a supported static image"
        elif not post.get("file_url"):
            reason = "no accessible original URL"
        elif post.get("rating") != "g":
            reason = "rating mismatch"
        elif tag not in str(post.get("tag_string_character") or "").lower().split():
            reason = "character tag missing"
        else:
            reason = (copyright_filter_reason(post, opts.copyright)
                      or tag_filter_reason(post, required, excluded))
        if reason:
            rejected.append({"id": post.get("id"), "reason": reason})
            continue
        # ASCII, bounded, collision resistant filenames on Windows.
        prefix = re.sub(r"[^a-z0-9_-]", "_", tag)[:65].rstrip("_") or "character"
        suffix = hashlib.sha256(tag.encode()).hexdigest()[:12]
        image = folder / f"{prefix}-{suffix}-{post['id']}.{extension}"
        try:
            digest, size = client.download(post, image)
        except StopBatch:
            raise
        except FetchError as error:
            rejected.append({"id": post["id"], "reason": str(error)})
            continue
        metadata = {
            "character": tag, "chinese": chinese, "dictionary_post_count": dictionary_count,
            "post_id": post["id"], "post_url": f"{BASE_URL}/posts/{post['id']}",
            "file_url": post["file_url"], "source": post.get("source", ""),
            "score": post.get("score"), "up_score": post.get("up_score"),
            "down_score": post.get("down_score"), "fav_count": post.get("fav_count"),
            "rating": post.get("rating"), "width": post.get("image_width"),
            "height": post.get("image_height"), "artist_tags": post.get("tag_string_artist", ""),
            "copyright_tags": post.get("tag_string_copyright", ""),
            "character_tags": post.get("tag_string_character", ""),
            "general_tags": post.get("tag_string_general", ""),
            "query": query, "candidate_rank": rank, "skipped_candidates": rejected,
            **conditions,
            "downloaded_at": timestamp(), "sha256": digest, "bytes": size,
        }
        sidecar = image.with_suffix(image.suffix + ".json")
        partial = sidecar.with_suffix(sidecar.suffix + ".part")
        partial.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        partial.replace(sidecar)
        return image, digest, metadata
    return None, "", {"character": tag, "chinese": chinese, "query": query,
                      **conditions,
                      "reason": "No accessible matching static image in candidate window",
                      "skipped_candidates": rejected}


def parse_args():
    parser = argparse.ArgumentParser(description="Download one highest-ranked accessible Safebooru general-rated image per character")
    parser.add_argument("--tag", action="append", help="Exact English character tag; repeatable")
    parser.add_argument("--dictionary", type=Path, help="Read category=4 from tag.sqlite or the indexed main dictionary")
    parser.add_argument("--tags-file", type=Path, help="UTF-8, one English tag per line; optional TAB Chinese name")
    project_root = Path(__file__).resolve().parents[1]
    if project_root.name.lower() == "source":
        project_root = project_root.parent
    default_output = project_root / "datasets" / "danbooru-character-previews"
    parser.add_argument("--output", type=Path, default=default_output)
    parser.add_argument("--max-characters", type=int, default=0, help="Limit selected characters; 0 means all")
    parser.add_argument("--min-posts", type=int, default=1, help="Minimum dictionary post_count in batch mode")
    parser.add_argument("--sort", choices=("score", "upvotes", "favcount"), default="score")
    parser.add_argument("--rating", choices=tuple(RATINGS), default="general", help="Safe site only: general (g)")
    parser.add_argument("--copyright", "--work", action="append", default=[],
                        help="Only posts from these exact English copyright tags (ANY); repeatable or comma-separated")
    parser.add_argument("--discovery-pages", type=int, default=50,
                        help="For work-only/dictionary work batches: discovery pages per work (200 posts/page); 0 scans all")
    parser.add_argument("--solo", action="store_true", help="Only search posts tagged solo")
    parser.add_argument("--include-tag", action="append", default=[],
                        help="Require every listed tag; repeatable or comma-separated")
    parser.add_argument("--exclude-tag", action="append", default=[],
                        help="Reject posts with any listed tag; repeatable or comma-separated")
    parser.add_argument("--filter-mode", choices=("server", "local"), default="server",
                        help="server: filter API search; local: filter accessible candidate metadata before download")
    parser.add_argument("--candidates", type=int, default=50, help="Candidate posts per page (1..200)")
    parser.add_argument("--pages", type=int, default=2, help="Maximum candidate pages per character per selected work")
    parser.add_argument("--interval", type=float, default=1.2, help="Minimum seconds between requests (>=1)")
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--max-mb", type=int, default=50, help="Maximum original image download size")
    parser.add_argument("--refresh", action="store_true", help="Requery already completed characters")
    parser.add_argument("--retry-missing", action="store_true", help="Requery previously missing characters")
    parser.add_argument("--user-agent", default="NAI-Studio-CharacterPreview/1.0",
                        help="Unique client identifier; append your Danbooru user ID if available")
    opts = parser.parse_args()
    try:
        opts.include_tag = filter_tags(opts.include_tag)
        opts.exclude_tag = filter_tags(opts.exclude_tag)
        opts.copyright = filter_tags(opts.copyright)
    except ValueError as error:
        parser.error(str(error))
    required, excluded = tag_conditions(opts)
    conflicts = set(required) & set(excluded)
    if conflicts:
        parser.error("Tags cannot be both required and excluded: " + ", ".join(sorted(conflicts)))
    if opts.copyright and set(opts.copyright) <= set(excluded):
        parser.error("All selected copyrights are excluded")
    if not (opts.tag or opts.dictionary or opts.tags_file or opts.copyright):
        parser.error("Provide --tag, --dictionary, --tags-file or --copyright")
    if (opts.max_characters < 0 or opts.min_posts < 0 or not 1 <= opts.candidates <= 200
            or opts.pages < 1 or opts.discovery_pages < 0 or opts.interval < 1 or opts.timeout <= 0
            or opts.retries < 0 or opts.max_mb <= 0
            or not math.isfinite(opts.interval) or not math.isfinite(opts.timeout)):
        parser.error("Invalid range for a numeric option (interval must be >=1)")
    return opts


def main():
    opts = parse_args()
    try:
        client = Client(opts)
        print(f"Source: {BASE_URL} | general (g) images only", flush=True)
        if opts.copyright:
            print("Selected copyrights (ANY): " + ", ".join(opts.copyright), flush=True)
        characters = character_list(opts, client)
        directory = opts.output.resolve()
        directory.mkdir(parents=True, exist_ok=True)
        profile = profile_name(opts)
        folder = directory / profile
        folder.mkdir(exist_ok=True)
        good = skipped = missing = errors = 0
        print(f"Characters: {len(characters)} | profile: {profile} | output: {directory}", flush=True)
        required, excluded = tag_conditions(opts)
        if required or excluded:
            print(f"Required tags (ALL): {', '.join(required) or '(none)'} | Excluded tags (ANY): {', '.join(excluded) or '(none)'} | mode: {opts.filter_mode}", flush=True)
        with open_state(directory) as db:
            for index, (tag, chinese, count) in enumerate(characters, 1):
                if not opts.refresh and completed(db, tag, profile, directory, opts.retry_missing):
                    skipped += 1
                    continue
                print(f"[{index}/{len(characters)}] {tag}", flush=True)
                try:
                    image, digest, metadata = fetch_character(client, opts, tag, chinese, count, folder)
                    if image:
                        record(db, tag, profile, "ok", metadata, image.relative_to(directory).as_posix(), digest)
                        good += 1
                        print(f"  saved post {metadata['post_id']} score={metadata['score']} rank={metadata['candidate_rank']}", flush=True)
                    else:
                        record(db, tag, profile, "missing", metadata)
                        missing += 1
                        print("  no matching accessible image", flush=True)
                except StopBatch as error:
                    record(db, tag, profile, "error", {"error": str(error)})
                    print(f"Stopped: {error}. Run the same command to resume.", file=sys.stderr)
                    return 2
                except FetchError as error:
                    record(db, tag, profile, "error", {"error": str(error)})
                    errors += 1
                    print(f"  error: {error}", flush=True)
        print(f"Done: downloaded={good}, resumed/skipped={skipped}, missing={missing}, errors={errors}")
        return 1 if errors else 0
    except KeyboardInterrupt:
        print("\nInterrupted. Run the same command to resume.", file=sys.stderr)
        return 130
    except (OSError, ValueError, sqlite3.Error, FetchError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
