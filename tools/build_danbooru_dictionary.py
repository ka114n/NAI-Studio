"""Build the packaged, read-only lookup database from the user's ffdkj SQLite snapshot.

Usage: python tools/build_danbooru_dictionary.py INPUT.sqlite WORK.sqlite
WORK.sqlite is a generated intermediate outside Git; assets/dictionary contains delivery data.
"""
import argparse
import gzip
import hashlib
import json
import re
import sqlite3
from pathlib import Path


def normalize(text):
    return re.sub(r"\s+", " ", text.replace("_", " ").lower()).strip()


def grams(text):
    chars = list(text)
    single = {"g" + format(ord(c), "06x") for c in chars if not c.isspace()}
    pairs = {"g" + format(ord(a), "06x") + format(ord(b), "06x") for a, b in zip(chars, chars[1:])}
    return single | pairs


def main():
    args = argparse.ArgumentParser()
    args.add_argument("input", type=Path)
    args.add_argument("work", type=Path)
    opts = args.parse_args()
    if opts.work.exists():
        raise SystemExit("Choose a new intermediate file; refusing to overwrite it")
    source = sqlite3.connect(opts.input.resolve().as_uri() + "?mode=ro", uri=True)
    raw = source.execute("SELECT name,cn_name,category,post_count FROM tags").fetchall()
    rows = {}
    for name, chinese, category, count in raw:
        key = normalize(name or "")
        chinese = (chinese or "").strip()
        if not key or not chinese:
            continue
        row = (key, chinese, normalize(chinese), int(category), int(count or 0))
        if key not in rows or row[4] > rows[key][4]:
            rows[key] = row
    destination = sqlite3.connect(opts.work)
    destination.executescript("""
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA page_size=4096;
        CREATE TABLE tags(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE,
            cn_name TEXT NOT NULL, cn_key TEXT NOT NULL, category INTEGER NOT NULL,
            post_count INTEGER NOT NULL);
        CREATE INDEX tags_chinese ON tags(cn_key, post_count DESC);
        CREATE VIRTUAL TABLE tag_search USING fts4(grams,content='',matchinfo=fts3);
        CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
    """)
    for index, row in enumerate(sorted(rows.values()), 1):
        destination.execute("INSERT INTO tags VALUES(?,?,?,?,?,?)", (index, *row))
        tokens = " ".join(sorted(grams(row[0]) | grams(row[2])))
        destination.execute("INSERT INTO tag_search(docid,grams) VALUES(?,?)", (index, tokens))
        if index % 25000 == 0:
            destination.commit()
            print(f"Indexed {index}/{len(rows)}", flush=True)
    source_hash = hashlib.sha256(opts.input.read_bytes()).hexdigest()
    destination.executemany("INSERT INTO metadata VALUES(?,?)", [
        ("schema", "1"), ("source_sha256", source_hash), ("rows", str(len(rows)))])
    destination.commit()
    destination.execute("INSERT INTO tag_search(tag_search) VALUES('optimize')")
    destination.commit()
    destination.execute("VACUUM")
    destination.close()
    data = opts.work.read_bytes()
    asset_dir = Path(__file__).resolve().parents[1] / "assets/dictionary"
    asset_dir.mkdir(parents=True, exist_ok=True)
    with (asset_dir / "danbooru.sqlite.gz").open("wb") as file:
        with gzip.GzipFile(fileobj=file, mode="wb", filename="", mtime=0, compresslevel=9) as zipped:
            zipped.write(data)
    manifest = {"schema": 1, "rows": len(rows), "source_rows": len(raw),
        "source_sha256": source_hash, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
        "source": "https://github.com/ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table",
        "license": "MIT", "format": "SQLite FTS4 codepoint uni/bigram index"}
    (asset_dir / "danbooru-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest), flush=True)


if __name__ == "__main__":
    main()
