# Phone gallery export

The gallery toolbar now has an export icon beside batch delete. Select images and confirm export to open Android's system folder picker. The selected destination is used for this export only; automatic-save settings remain unchanged. Exporting a grouped upscale tile copies the displayed upscale, while deletion continues to target the original group.

The long-press image parameter card has a single-image Export action on the left below official upscale. It exports the image represented by that card. The inaccessible private file path is replaced with guidance to export to a system folder.

Export copies stored image bytes unchanged and keeps the private source. It does not add sidecar files, credentials or new metadata. Incomplete destination documents are removed where the document provider permits; failures are counted and other selected files continue. Canceling the folder picker does not export anything. No import action was added (the user corrected the requested label to Export).

Verification (2026-10-04): Android `:app:testLocalDebugUnitTest :app:assembleLocalRelease --no-daemon --no-parallel` passed: 546 tests, zero failures/errors/skips, including three new export tests. Release 1.1.160 was signed with the existing release identity and installed successfully on the connected phone. Next build iteration is 161. Real-device folder-provider and visual verification remain manual. Desktop sources were unchanged.
