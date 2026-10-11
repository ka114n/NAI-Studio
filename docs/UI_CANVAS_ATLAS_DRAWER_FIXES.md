# Canvas, atlas paging and popup outline fixes (2026-10-04)

## Current behavior

- Android normal preview no longer draws the image-present checkerboard overlay. Desktop normal preview disables its background grid; canvas color remains in use. Infinite-canvas and comic backgrounds are unchanged.
- Both AnimaDex screens use an explicit remembered grid state and return to item 0 when a new successful result revision arrives. The revision advances for character and series results, including equal response lists. Existing results remain in place while loading; failed searches do not advance the successful-result revision.
- Original bottom-drawer backdrop blur on both platforms and Android navigation-drawer blur/scrim are restored. The duplicate popup-sized outline above Director/AnimaDex sheets was traced to an outer border in Android's shared RefModalBottomSheet wrapper, not backdrop blur. Removing this outer border prevents drawing at the unshifted origin. Shape, colors, handle, dismissal and content remain; ordinary alert-dialog borders are unchanged.

Material3 1.3.2 source confirms that the caller modifier precedes internal sheet positioning (draggableAnchors). Both reported popup entry points use the shared wrapper. Source checks confirm restored blur expressions match the pre-cleanup version and the border removal is limited to bottom sheets. Verification results follow below; no on-device visual verification is claimed. Public origin/Releases are unchanged.

## Verification

Desktop: 555 tests, 6 skipped, zero failures/errors.
Android: 543 tests, 0 skipped, zero failures/errors.
Final desktop test/desktopInput and Android local unit tests/release assembly passed. An earlier desktop run failed an unchanged paper-brush performance ratio threshold; the following standalone full run passed, without changing that test or its threshold. Android 1.1.159 formally signed; next iteration 160. Desktop local runtime refreshed and hash verified. Phone was disconnected at delivery, so installation and visual confirmation remain pending.
