# Infinite canvas: fixed crop with automatic workflow mask

Current behavior (2026-10-03): Android and desktop use the selection rectangle as the entire crop and request. There is no outer context band, context slider, or context keyboard shortcut. Legacy `infiniteContext` settings remain readable for preferences compatibility but do not affect this pipeline. Ordinary mask editing keeps its own focused-inpaint context controls.

## Pipeline

1. Choose a fixed preset: 1024×1024, 832×1216, or 1216×832. All dimensions are 64-aligned and total area is at most 1,048,576 pixels. Requests cap steps at 28; this meets the size/steps envelope, but the upstream service/account still determines whether generation is free.
2. Place/move the frame across an existing image edge. Desktop click/drag places a frame; drag inside moves it, space/middle-button pans. Mobile double-tap places a frame, dragging inside moves it, dragging outside pans, and two fingers zoom/pan. There are no resize handles or arbitrary width/height inputs. Selecting another preset preserves the center. Old saved frames migrate to the nearest preset around their center.
3. Grow the document bounds to include the frame, preserving pixels and origin. Crop exactly the frame at original resolution: crop size equals request size with no rescaling or outside context.
4. Build a binary mask from transparent/missing pixels in that crop. Encode missing pixels as opaque white only for image transport; opaque white image content remains reference. Fully occupied frames are rejected. A frame without original reference is rejected when an existing image is present; an initially empty canvas can generate its first image.
5. Dilate the blank mask by the configured mask-expansion radius. This CAN enter existing pixels inside the crop. Apply existing latent mask alignment; union the original blank footprint back into the result so thin missing slivers cannot be lost. Do not clip the expanded mask back to blank-only pixels.
6. Submit image + automatic mask through the existing inpaint API with strength 1.0, configured noise and the chosen inpaint model. No manual mask or extra context is required.
7. When original-image blending is enabled, compute the existing workflow-style alpha (latent dilation and feather, or hard mask when edge protection is disabled), composite generated/original pixels, and force original blank pixels to alpha 255 so white transport padding does not leak into the result. Expansion/soft edges can replace/blend the original seam inside the frame. With blending disabled, paste the full generated crop.
8. Paste only the crop rectangle back into the document; pixels outside it remain untouched. Existing canvas snapshots, undo/redo, immediate display and mobile composite-image storage remain in use.

This supersedes build 1.1.155's strict blank-only protection. Source, UI labels and regressions were updated on both platforms. Tests cover exact request/crop geometry, legacy migration, fixed-size gestures and moves, dilation into original content, blending, thin gaps, and paste boundaries. Verification results are recorded below. No paid generation is used by automated verification; actual model continuity requires a user generation.

## Verification and local delivery

Final desktop `test desktopInput` passed: 555 tests, 6 skipped, zero failures/errors. Final Android `:app:testLocalDebugUnitTest :app:assembleLocalRelease --no-parallel` passed: 543 tests, zero failures/errors. Android 1.1.157 built; next iteration is 158. Formal release certificate verified. Desktop verification runtime refreshed from the compiled JAR and its hash matched. Android APK is in the sibling android directory, outside Git; attempted installation returned `INSTALL_FAILED_ABORTED: User rejected permissions`, so installation is not claimed complete.

One earlier full desktop run hit a Compose runtime NPE in the unrelated comic smoke test; subsequent standalone full runs passed. Existing deprecation/build warnings remain. No paid/API generation or model-quality validation was performed. Public origin/Releases remain unchanged; this task is backed up only to the private backup remote.

The infinite-canvas button's cost estimate now uses the same capped steps (28) and strength (1.0) as the actual request.
