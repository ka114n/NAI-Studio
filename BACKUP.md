# Private development backup

Repository: `ka114n/NAI-Studio-backup` (private).

The `backup` remote stores development checkpoints. The public `origin` repository and its existing Releases are separate publication destinations. Routine development backups do not publish a release.

Each completed task is stored as a Git commit containing the source changes and a verification note. Git preserves earlier snapshots and transfers incremental objects. Generated installation packages, build caches, user data, credentials and release private keys are excluded.

To restore, clone this private repository, check out the desired commit, install JDK 17 and the configured Android SDK, and follow README build instructions. Supply your own local API configuration. The project owner's signing key is backed up separately and must not be committed.

## 2026-10-01 checkpoint — 1.1.142

- Mobile prompt inputs follow the actual cursor rather than the last text line.
- Both platforms rename the artist-codex entry to 所长法典.
- Metadata-free output removes PNG text/EXIF and recognised NovelAI alpha/RGB steganography; invalid PNG processing fails rather than silently returning the original.
- Android disables automatic gallery and shared-folder copies when gallery saving is off; primary images remain private. New installations default to gallery saving off. Existing choices and previously exported files are retained.
- Mobile image details show the selectable private-original storage path.
- Windows: 529 tests, 6 skipped, zero failures/errors; app-image startup check passed.
- Android: 489 tests, zero failures/errors; release build and signing verification passed. No physical Android device interaction test was performed.

Local validation APK and Windows app image are deliberately excluded from this repository. The APK's release signature is managed outside Git.
