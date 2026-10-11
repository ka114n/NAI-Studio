# Desktop progressive preview (1.2.17)

## Causes and changes

Ordinary viewing previously recalculated a decode-size bucket on wheel zoom. FileImage decoded each new bucket, leaving the older, enlarged raster visible until the new one was ready. DesktopImageIo also encoded its decoded BufferedImage to PNG and decoded that PNG again to build a Compose image. PreviewCard immediately initialized the shared infinite document on the UI thread, including full image decoding and pixel copies even when ordinary mode did not need editing pixels.

ProgressiveFileImage now decodes a small, subsampled preview on Dispatchers.IO, keeps it visible, and preloads detail once in the background. Its producer is keyed by the image path, not zoom or viewport dimensions. Wheel zoom uses the same bitmap; it does not start decoding or clear the preview. A separate one-image cache, keyed by path, file length and modification time, permits immediate reuse when returning to the same image. A mutex serializes its heavy decode passes. Cancellation after switching images is propagated and obsolete results cannot update the new image.

The detailed raster is limited to 33,554,432 pixels (128 MiB ARGB). A 6048 x 3456 source therefore loads at its full native resolution. Larger images are subsampled before allocating the raster; this is a bounded full-image viewer, not a region/tile viewer. The 128 MiB is a retained-raster budget, not a total-process memory cap: decoder intermediates and GPU textures also occupy memory. Original files are never rewritten. Detail below the initial preview resolution still needs its initial background decode; no universal zero-latency claim is made.

DesktopImageIo now transfers decoded pixels directly to Skia, removing the PNG encode/decode round trip. Its general sampled decoder requests ImageReader subsampling before filtered final scaling, so gallery and tool previews also avoid unnecessary full-resolution decoding. Native color extraction keeps ARGB/alpha handling consistent with the existing composeImageOf implementation.

Ordinary mode no longer calls syncSharedCanvas merely to view a file. Existing matching edited pixels can still be displayed. Infinite mode initializes the editing document when entered and synchronizes again if its working image changes. Infinite editing, crop bounds and original file retention are unchanged; this task does not make infinite-document initialization asynchronous. Streaming previews, fit/pan/zoom, mask overlays and generated/composited output remain connected to their existing state.

Only the desktop source tree changes. Android remains 1.2.15. Per the user's standing instruction, no unit, performance, interaction or visual tests are run. Compilation and package/deployment verification are recorded separately; perceived smoothness and timing have not been measured.

## Delivery evidence (2026-10-06)

JDK 17 desktopInput completed successfully on the first compilation attempt. jpackage created the 1.2.17 Windows app image. The canonical Windows executable, configuration and complete desktop JAR were replaced only after checking that no running canonical app processes remained, using normal window close if needed. All three deployed files match their package SHA-256 hashes. The executable reports FileVersion and ProductVersion 1.2.17. No application launch or interactive acceptance check was performed.

The private incremental backup contains only source and recovery notes; generated packages, caches, user data and secrets are excluded. The public repository and Releases are unchanged.
