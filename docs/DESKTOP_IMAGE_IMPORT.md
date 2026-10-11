# Desktop image import and profile layout (1.2.16)

## Import initialization

An oversized image such as 6048 × 3456 was capped to a 4096 × 3456 infinite-canvas document, then passed to InfiniteCanvasPixels.blit. That function deliberately rejects a destination smaller than its source, so importing or opening the shared canvas could throw “新画布比老画布小”. Initialization now uses ImageEditOps.resizeCanvas to copy the bounded working region without resampling. Expansion still uses the strict blit function, retaining its protection against silently clipping an existing document.

The original imported file remains intact. Normal mode shows the complete original image when an unedited source exceeds the working-canvas limit. Infinite mode still has the existing 4096-pixel side limit; its initial working copy crops excess right/bottom pixels at their original resolution. This is not a change to the canvas limit or an automatic downscale of the source.

## Drop destinations and previews

DesktopImageImport.kt provides region-specific Compose native file-drop targets. The common canvas content region, across ordinary/infinite/comic modes, calls importDroppedImage. The metadata tool body calls selectMetadataImage, and the reverse tool body calls selectReverseImage. Text drags are ignored; unsupported files report the existing image-format message. Other sidebar or page areas do not import to the canvas. The former whole-window target and its opaque full-window overlay have been removed; the active region gets a dashed accent outline.

Metadata and reverse prompting always show a 220 dp tall dashed preview frame, including their empty state. The frame also opens the existing native Windows picker when clicked. Images use ContentScale.Fit, centered with internal padding and rounded clipping; landscape, portrait and oversized images stay entirely within the frame. Preview decoding is bounded to 1024 pixels; the tool keeps its original input file. Existing metadata import/copy actions, reverse modes, local GPU/online selection and result actions remain available.

## Profile layout

StudioButton used a fillMaxWidth child even inside horizontal page actions. This consumed the title/identity column's space and wrapped its text one character per line. The component now exposes fullWidth with its old default retained. Profile export and refresh/login actions use content width; compact layouts still pass an explicit full-width modifier. The page container applies its 1120 dp maximum before filling available width so the content cap actually takes effect.

Account/API configuration, quota refresh, overview, all backup import/export flows, translation options and LLM presets remain wired to their existing handlers. This change affects desktop source only; Android delivery remains 1.2.15.

Per AGENTS.md and the user's instruction, no unit/UI/interaction tests or visual acceptance checks are run. Compilation, package creation and deployment hashes confirm deliverability, not interactive validation.

## Delivery evidence (2026-10-06)

The final JDK 17 desktopInput build succeeded. Earlier compilation attempts found a leftover closing brace and direct access to private canvas state; both were corrected before the successful build, and those failed builds were not delivered. jpackage created the 1.2.16 Windows app image. The running application exited normally before its executable, configuration and complete desktop JAR were replaced in the canonical windows directory. All three deployed files matched their package SHA-256 hashes; executable FileVersion and ProductVersion both report 1.2.16. The application was not launched for interactive validation.

Only required source and these recovery notes are included in the incremental private backup. Packages, caches, user data and secrets are excluded. The public repository and Releases are unchanged.
