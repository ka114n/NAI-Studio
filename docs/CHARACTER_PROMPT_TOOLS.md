# Character prompt controls — 1.2.28

Historical notes for 1.2.28. Version 1.2.29 moves character search to a standalone section input and restores exact character translation; see `CHARACTER_SEARCH_PLACEMENT.md` for current behavior.

## Behavior

- Adding a blank character, a dictionary candidate or an image-codex character creates an expanded card. Existing cards retain their saved expansion state.
- Both character prompt fields have the normal delete, undo, redo, history, translate and optimize toolbar. Android edits now pass through `setCharacterPrompt`, preserving each field's undo history as on Windows.
- Only the first logical tag in a character's positive prompt searches category 4 (characters). All subsequent positive tags and all negative tags search appearance tags from category 0. The tag index spans lines and includes individual tags inside grouped weight wrappers.
- Input candidates follow the caret rather than the end of the prompt. Chinese IME composition and nonempty selections do not trigger candidates. Queries run on IO after a 150 ms debounce, with up to 24 candidates and a 240 dp scrolling menu. Selecting a candidate replaces the active tag body, preserves its weight punctuation and other text, and positions the caret after the new tag.
- The same scoped matching is used by the character-field Translate action. General prompt translation remains exact; generic dictionary search remains fuzzy. Comic panel prompts keep their normal matching behavior.
- Danbooru's five categories do not include an appearance subtype. `CharacterPromptMatching` therefore applies a conservative, explicit word taxonomy for hair, eyes, facial/body features, clothing, accessories and expressions. SQL applies category and appearance filtering before LIMIT; custom dictionary aliases use the same classification. This taxonomy may omit unusual appearance tags and can be extended without changing the downloaded dictionary.
- Scoped matching affects dictionary lookup; unmatched text still follows the configured online translation service. Existing exact user aliases, deleted/overridden entries and optional dictionary download consent remain respected.
- Translation planning runs on IO, and weight-group numbering is local to each request. Mixed-language backend batches select translation direction per batch.

## Build and delivery

Tests were skipped according to the owner's standing instruction. No public application repository or Release changes are included.

- Windows `desktopInput` passed (1 minute 1 second), and the complete 1.2.28 application image was delivered to the sibling `windows` directory. All 235 copied files matched their package SHA-256 hashes.
- Android `:app:assembleLocalRelease` passed (2 minutes 34 seconds) after completing the phone's missing character-field routing. The signed APK is 1.2.28, versionCode 24232866, 2,228,206 bytes. Its original release certificate was verified. No authorized USB phone was detected, so it was not installed. The next Android build iteration is 29.
- Both packages exclude the dictionary database payload and retain its optional-download manifest. Shared SQL dictionary model copies are identical.
