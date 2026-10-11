# Character search and editing — 1.2.29

This supersedes the positional fuzzy matching behavior described for 1.2.28 in `CHARACTER_PROMPT_TOOLS.md`.

## Behavior

- Both platforms show a standalone character search below the character-section cards and above the Character Codex button, including when there are no cards. It searches Chinese names and English tags in the main dictionary's character category only; clicking a candidate creates an expanded character section and clears the query.
- Character prompt fields no longer run dictionary searches or display candidate menus. Their per-field tools and undo history remain available, and new cards still open automatically.
- Character positive and negative translation both use the normal exact dictionary lookup for every tag, regardless of position or category. Partial role/appearance completion and the additional appearance taxonomy are removed. Unmatched terms continue through the configured translation service; this does not constrain that service's generated wording.
- The codex dialog restores its normal character/copyright browsing, filters and image-card search. Entering it no longer clears its query or forces character view. Selecting a character image card still creates a section.
- General dictionary fuzzy search is unchanged. The main dictionary remains optional and download requires the user's choice.

## Android keyboard layout

The role drawer formerly measured its fields directly in a bounded, non-scrollable column. When the IME reduced available height, later children (especially the negative field) received insufficient vertical space. Its content now has a bounded scrolling viewport that measures the cards at their full content height; the title remains outside that viewport.

Character editing keeps `TextFieldValue` selection/composition state. The shared reference field exposes text layout for this overload, and a bring-into-view requester outside the editor's internal scroll node follows the active caret, including after an IME height or text layout change. It accounts for the field's existing decoration padding. No global sheet insets or unrelated screen layouts are changed.

## Verification

Unit and Android/Windows interactive tests were skipped per the owner's standing instruction. Public application origin and Releases are unchanged.

- Windows `desktopInput` passed (1 minute 20 seconds). The complete 1.2.29 application image was copied to the sibling `windows` delivery directory; all 235 files matched their package SHA-256 hashes.
- Android `:app:assembleLocalRelease` passed (2 minutes 43 seconds). The signed APK is 1.2.29, versionCode 24234072, 2,211,822 bytes; its original release certificate was verified. No authorized USB phone was detected, so it was not installed. The next Android build iteration is 30.
- Both packages exclude the dictionary database payload and retain its optional-download manifest. Shared SQL dictionary model copies are identical.
