# Character autocomplete and exact translation — 1.2.27

The user clarified that only clicking Translate should lose fuzzy dictionary completion. General dictionary search remains fuzzy; bilingual card behavior and dictionary download consent are preserved.

## Translation

- Both Baidu and the Chinese LLM dictionary pipeline now use `PromptTranslate.Params()` with `fuzzyMatching = false`, including accounts whose old setting was enabled.
- Full Chinese aliases and full English tags can still match their exact dictionary entries. Unmatched text continues through the configured online translator; this change does not control that service's generated wording.
- The translation fuzzy-match switch is removed from the profile page. The legacy serialized setting remains for compatibility with existing bilingual-card behavior and is no longer consulted by Translate.

## Character-section picker

- The character-section Character Codex sheet gets a local Chinese/English character search input. Typing shows a dropdown with at most 24 matching English tags and Chinese labels; the dropdown scrolls above its 280 dp height limit.
- SQL filters category 4 before ranking/limiting. Custom aliases participate only when their English tag is classified as a character in the main dictionary. Copyrights, artists and general tags are excluded from this autocomplete.
- Query work runs on IO after 150 ms debounce. Each request captures its query and dictionary; canceled or previous-query results are not shown for the new input. The dropdown does not take keyboard focus.
- Choosing a candidate creates a character section with the Chinese label as its name and the full English tag as its prompt, persists through the normal character change path and closes the picker. The existing section count limit applies. Comic mode uses the existing active-panel list.
- Existing image-card selection remains available. The character-section picker stays in character mode, without the copyright mode switch. Other codex entry points retain their existing browsing modes.
- The main dictionary is optional. When it is absent, the dropdown offers the existing user-approved download flow; it does not automatically download anything. Existing installed dictionaries are reused.

## Verification

Unit tests and Android/Windows interactive tests were skipped per the owner's standing instruction.

- Windows `desktopInput` and Android `:app:assembleLocalRelease` completed successfully with JDK 17.
- Windows app-image 1.2.27 was copied to the sibling `windows` delivery directory; all 235 files matched their package SHA-256 hashes.
- Android release APK 1.2.27 (versionCode 24231659, 2,211,822 bytes) was signed and its original release certificate verified. No authorized USB phone was detected, so installation was not performed. The next Android build iteration is 28.
- Both packages exclude `danbooru.sqlite.gz` and retain its download manifest; the shared dictionary model copies are identical.
- Public application repository and Releases remain unchanged. Source changes are backed up only to the private backup remote.
