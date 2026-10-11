# Optional dictionary download — 1.2.26

The Android and Windows installers no longer include `danbooru.sqlite.gz`. Only the small manifest and upstream license remain packaged. The recoverable source retains the input asset for rebuilding the dictionary and website.

Public data repository: https://github.com/ka114n/NAI-Studio-dictionary

The application pins commit `c5e42ce0633cb8895a0fed9d5962703855683af1` and the decompressed SHA-256 in `assets/dictionary/danbooru-manifest.json`. This repository contains dictionary data and its license only; the public application repository and Releases are unchanged.

## User behavior

- Startup, codex loading and prompt translation cards never download the main dictionary.
- Opening the dictionary or clicking Translate without an installed main dictionary offers Download / Not now. The prompt states about 38 MB download, 79 MB extracted and 330,745 entries including the search index.
- Choosing Not now leaves custom dictionary entries available. When initiated by Translate, it continues through the existing configured online translation service without downloading a dictionary.
- Downloading shows percentage progress. Failed network requests, storage writes or SHA-256 validation leave the dictionary inactive and allow retry.
- A successful download resumes the requested translation. Existing dictionaries extracted by previous versions are reused at the same path and need no download.
- The database is kept under the application files directory: `tagcodex-cache/dictionary/danbooru-4af75e2bcad072b9.sqlite`. Restarting does not download it again. Clearing app data or deleting the dictionary requires a new user-approved download.

## Implementation and recovery

`TagLexiconAsset.prepare()` opens local data only and checks its size/checksum before opening SQL. The repository's explicit `download()` is called only from the confirmation action. Downloads use bounded timeouts and sizes, stream to a temporary file and activate only after validation; failures remove temporary files. The indexed dictionary is never expanded into JVM maps.

To update the hosted dataset, publish the compressed database, manifest and license together, then update the pinned commit URL, download byte count and bundled manifest in both shared model trees. Do not publish source backups or signing material to the data repository.

## Verification

2026-10-08: both final builds passed (`desktopInput`, `:app:assembleLocalRelease`). Two intermediate Android attempts required a Gradle packaging exclusion adjustment and a missing progress-indicator import; these failed attempts were not delivered.

Windows 1.2.26 was packaged and installed in the existing `windows` directory: all 235 copied files matched SHA-256. Its main JAR is 6,270,001 bytes and contains the manifest but no compressed database.

Android 1.2.26 (versionCode 24196764) was signed with the original release identity and verified. The signed APK is 2,211,822 bytes (about 2.2 MB); the database is absent and its manifest is retained. No authorized USB phone was connected, so the APK was delivered without installing it. The next Android build iteration is 27.

The data repository's Git blob hash matches the local compressed dictionary. Both platform dictionary model copies are identical.

User instruction: no unit tests or Android/Windows interactive tests. No live application download test was run.
