# Indexed bilingual dictionary — 2026-10-07

## Source and packaged data

The user supplied `tag.sqlite` from the ffdkj Danbooru Chinese/English translation
table. This snapshot contains **330,745 rows**, all with a nonempty translation:
52,860 general tags, 153,319 artists, 20,129 copyrights, 103,691 characters and
746 metadata tags. These are snapshot counts, not the current upstream release.

Upstream: https://github.com/ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table

The upstream MIT license is retained as `assets/dictionary/ffdkj-LICENSE.txt`.
These are lookup labels, not prompts automatically inserted into generation.
No content category was filtered from the supplied dictionary.

- Input SHA-256: `e6dbf268d76ffe5ea3cda7afc979ed735e479d1ab0b063848bd803f63f896e0d`
- Indexed database SHA-256: `4af75e2bcad072b9c46875ef60c0b32aa3487ebb911745b99a1e4238218c183b`
- Indexed database: 78,815,232 bytes; compressed asset: 38,334,560 bytes.
- Both build trees use the single required asset directory `assets/dictionary`.

## Lookup and compatibility

Android uses its native read-only SQLite API. Windows uses sqlite-jdbc 3.46.1.0
and a Java runtime that includes `java.sql`. The SQL database is not expanded into
330,745 JVM map entries. English names normalize underscores to spaces, preserve
punctuation and use a locale-independent lowercase key. Chinese labels are retained.

Exact lookups use B-tree indexes. Substring lookup uses a prebuilt, contentless
FTS4 index of Unicode single codepoints and adjacent pairs, followed by a literal
substring check. SQL parameters carry user input. English and Chinese searches
return both the tag and its translations. Native ranking favors exact/prefix
matches and then upstream post counts. Short Chinese fuzzy translation remains
controlled by the existing fuzzy-translation preference. From 1.2.22 it is limited
to character names (category 4); see `CHARACTER_TRANSLATION_MATCHING.md`.

The UI initially shows 100 matches and loads another 100 on request, up to 2,000;
broader searches then ask for a more specific query. SQL candidate lists are bounded
at 10,000, Chinese translation candidates at 256, and the English exact cache at
512. Only custom aliases use the existing in-memory overlay search index.
That snapshot is prepared on a background dispatcher on both platforms.

Following the user's final instruction, the app uses **only this main dictionary**
plus user-authored additions/edits/deletions. User aliases take precedence. Editing
replaces the original labels for the affected tag; deleting hides it from all layers.
Settings and backup formats for these changes are unchanged. The old English TSV
is deleted from both builds and its startup loader is removed. Previously downloaded
Chinese translation shards and derived JSON indexes are neither read nor fetched.
On successful main-dictionary initialization, obsolete dictionary cache files matching
`tag_zh_*.json` or `translation_index_*.json` are removed non-recursively, best effort;
settings, custom entries and codex documents are preserved. The codex's Chinese labels
now use the same main dictionary and user edits. The old loader-only repository path
remains for compatibility with legacy unit fixtures, without a production call path.
The source database and packaged asset remain read-only. User caches are not committed.

The user reported that `w` displayed no matches despite the main count being visible.
Read-only inspection of the generated index found `w`, `white hair`, `wings`, etc.;
the single-letter FTS term is present. The UI previously had two search inputs, one
for the full dictionary and one restricted to custom entries, with identical empty
messages. It also showed an empty result during debounce/query execution. The final
UI has one full-dictionary substring search input, shares it with custom management,
and distinguishes searching, query failure and a completed empty result. A rename
duplicate check now covers the SQL main dictionary as well as custom entries.
No interactive reproduction was performed, so the user's original empty-result
symptom has not been confirmed end to end by this delivery.

At first startup the packaged gzip is extracted in the background under the
application's `tagcodex-cache/dictionary` directory. Length and SHA-256 are checked
before activation. Later launches reuse the checksum-named database, validate its
schema and avoid extraction. Searching this main dictionary does not require a
download or an online translation service. The dictionary alone adds about 38 MB
to packages and about 79 MB of extracted data. It is pinned to this snapshot;
there is no automatic upstream replacement.

## Recovery and regeneration

Keep all three files in `assets/dictionary`, `tools/build_danbooru_dictionary.py`,
both `SqlTagLexicon.kt` implementations and the build resource-directory settings
when recovering from Git. Generated packages and intermediate databases are excluded.

To rebuild with a new authorized snapshot, use Python with **FTS4 support**:

```text
python tools/build_danbooru_dictionary.py INPUT.sqlite NEW-WORK.sqlite
```

The input is opened read-only. The work path must not already exist. Normalized
duplicates select the highest post-count record; the manifest records source and
retained counts. The builder writes the compressed database and manifest into the
asset directory. Review and update the upstream license if the source changes.

For Windows, build `desktopInput` with JDK 17, then package the entire input directory
with jpackage and a runtime containing the existing desktop modules plus `java.sql`.
Deploy the complete app image, not only the main JAR: the JDBC dependency and new
runtime are required. Preserve user data outside the app image. Android uses
`:app:assembleLocalRelease`, then the owner's separately stored release signing key.

## Build and delivery notes

Final target: Windows and Android **1.2.21**. Android 1.2.18, 1.2.19 and 1.2.20 were
successful intermediate builds. 1.2.19 was installed, then superseded after a rename
validation fix and the user's instruction to remove the legacy dictionary. 1.2.20
was not delivered. Final 1.2.21 includes the single search input and query-state fix.

The first asset-builder invocation failed because the Miniconda Python environment
did not include FTS4. Rebuilding with the bundled FTS4-enabled Python succeeded.
The first Windows compile failed on missing snapshotFlow/collectLatest imports;
those imports were added before the final build. Neither failed output was delivered.
Packaging inspection found the old Windows runtime lacked `java.sql`; a new runtime
with that module was built and included in the final image.

Per the standing user instruction, no automated tests, UI tests, performance
measurements or interactive acceptance were run. Compilation, packaging, resource
checks, copy checksums, release-signature verification and optional USB installation
are delivery checks only, and do not establish measured startup/search performance.

Intermediate 1.2.19 delivery checks completed (superseded by final results below):

- Windows `desktopInput` compilation and jpackage app-image succeeded.
- The complete image (235 files) was copied to the canonical `windows` directory;
  each copied file's SHA-256 matches the new image. Launcher version is 1.2.19.
- The deployed runtime includes `java.sql` and `java.transaction.xa`; the app includes
  sqlite-jdbc 3.46.1.0 and its resolved dependencies.
- Android final `assembleLocalRelease` succeeded: versionName 1.2.19,
  versionCode 24150694. The next build iteration stored in Git is 20.
- APK release certificate SHA-256:
  `75ba9b19444a13e73bbdb5b1261dd4813a395790b5a2837bd95718ea584d7669`.
- USB `adb install -r` succeeded. Package-manager output confirms 1.2.19 / 24150694.
- Both final archives contain the exact compressed database, manifest and MIT
  license from the asset directory; their hashes match. The compressed asset SHA-256
  is `32857b3be2f1dd9bd73d24f4fb055fedf046ad6889375aaa66f0c94647bf56e9`.
- Windows launcher SHA-256:
  `9ee64e5389ac22ce2060442d6bed768d011f90587caa2f88f28522ff604f8e83`.
- Windows main JAR SHA-256:
  `93a5c23bcf8b25d1ed62dcafc6cb1cc119d79d79c5c07d55a877032e478940c1`.
- Android signed APK: 40,915,050 bytes; SHA-256:
  `e7550165f4651a8b4469261832e807d1c0c22799fec9b7b6cfda107aff38403e`.
- `git diff --check` passed. Source and required assets are backed up to the private
  backup remote; public origin and Releases are unchanged.

Final **1.2.21** delivery checks:

- Both compilation/package builds succeeded. The APK uses versionCode 24151570;
  the stored next-build iteration is 22. Existing deprecation warnings and a redundant
  compatibility-branch condition do not fail compilation.
- The final Windows app image was copied to `windows`; all 235 copied files match
  the new image by SHA-256. Launcher version is 1.2.21 and the JDBC dependency and
  runtime SQL modules remain included.
- Both final archives contain the exact main database, manifest and MIT license.
  Neither contains the removed `dictionary.tsv`.
- Final signed APK is 40,542,254 bytes and uses the same original release certificate.
  USB installation succeeded; package-manager output confirms 1.2.21 / 24151570.
- Windows launcher SHA-256:
  `650ed3a9fafc45156b96919e66e9c07a225ec2bf8248c14ce6453cf1c36d1d7e`.
- Windows main JAR SHA-256:
  `db5d7b41637f69e089e646e16ffe89be9bb6f35d0ff2f6b81ee8671540edab0e`.
- Android APK SHA-256:
  `b0d3dfbf1ed3acd3a4cb865de6c19672019d2bb0aaead1c30801da5d27165a9b`.
- No tests or interactive acceptance were run. Old-cache cleanup occurs on successful
  dictionary initialization at the next app startup; initialization was not forced
  as a delivery check. The reported `w` symptom was investigated through source and
  read-only index inspection, not a UI reproduction.
- Final source changes, asset removal and these notes are backed up to the private
  backup remote. Public origin and Releases are unchanged.
