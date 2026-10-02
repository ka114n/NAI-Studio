# Private development backup

## 2026-10-02 checkpoint — Android 1.1.146 full migration backup

Android Profile now has a separate password-encrypted full saved-data export/import, including credentials, all typed preference stores, private saved files/images, referenced readable external originals, presets and conversation memory. Export performs encrypted-file readback and SHA-256 inventory verification. Restore validates before replacing data, remaps paths and re-encrypts secrets with the destination installation's Keystore. A private recovery journal supports rollback after interruption. Ordinary settings JSON/image backups are unchanged; unsaved edits and system permissions are excluded. Instructions: docs/FULL_MIGRATION_BACKUP.md.

Verification: final Android build 1.1.146, versionCode 23721049; 522 unit tests, zero failures/errors, one optional external-preset fixture skipped in this restricted session. The 28 full-backup archive/metadata tests passed. Final compilation used a temporary guarded Gradle init script to compile the sole generated BuildConfig.java against its necessary dependencies, avoiding Windows sandbox real-path failures on unrelated cached JARs; no compilation or test task was skipped. Both signing identities are held outside Git. Physical-device covering installation, SAF interaction and full migration have not been verified. Windows is unchanged. Public origin and Releases remain unchanged.

Private push is pending: this restricted session cannot read the GitHub CLI login configuration or authenticate the backup remote. Do not treat the local commit as an uploaded checkpoint until authenticated private-repository verification and push succeed.

## 2026-10-01 checkpoint — 1.1.143 application updater

Both platforms check stable GitHub releases at startup and from Settings/About, download with progress and verify asset size/SHA-256. Windows stages shipped files, uses a hidden helper after process exit, retains prior files and rolls back failed replacement. Android checks APK identity/signature/version before requesting system installation from a restricted private-cache FileProvider. User data remains outside the replacement transaction. Desktop version reporting is corrected to match 1.1.143.

Verification: Windows 538 tests (6 skipped), Android 494 tests, no failures/errors; Windows replacement and rollback transaction fixtures passed, live GitHub check/download/digest passed, Windows startup and Android release signing/version checks passed. Android installation UI was not exercised on a physical device. Complete update instructions are in docs/UPDATES.md. Local 1.1.143 verification builds do not constitute a public Release.

## 2026-10-01 local consolidation

The latest source checkout and complete Git history now reside at `C:/Users/kallan/Desktop/NAI-Studio/source`. Windows and Android 1.1.142 validation packages are in the same project root. Copies are verified before older duplicate desktop directories are removed. Signing files stay outside Desktop. Application code is unchanged by this documentation and workspace-location checkpoint.

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
