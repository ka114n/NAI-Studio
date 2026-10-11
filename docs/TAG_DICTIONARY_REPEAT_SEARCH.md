# Dictionary repeat-search input and request isolation

Both Android `TagDictionaryUi.kt` and the desktop shared copy used the same
String-only query field and search producer pattern. The field retained the old
term without selecting it on re-entry. Typing another search could append to the
previous term, making the combined text fail to match. The user supplied
`阿米娅` as the example; the primary dictionary contains `amiya (arknights)` and
multiple Amiya variants. The exact second input/gesture was not specified.

Both platform UI copies now use `TextFieldValue` for the search field. On a new
focus, a keyed effect selects the existing query after the focus/pointer update;
typing replaces it without manually clearing. Moving the cursor while the field
is already focused still permits editing. Copy/apply prepares the previous text
for replacement and clears search focus. The IME Search action reissues the query
and prepares the next input. Live, debounced search remains available.

The search producer is explicitly keyed by query, dictionary, result limit and
search revision. It captures the query/dictionary/limit before suspension, cancels
the previous producer when keys change, and keeps cancellation separate from
search failures. Results record their query; a stale result or empty state is not
shown for a different query while its producer starts. Clearing the field resets
results and hides the fixed-height search area as before. Editing/deleting custom
entries continues to rebuild the dictionary and refresh the keyed search.

This is a UI/input lifecycle fix; the dictionary, translation settings, search
matching rules, ranking and user custom entries are unchanged. No website or
server capture job is modified.

## Verification and delivery

Delivery target: Windows and Android 1.2.25. A 1.2.24 intermediate package was not
delivered; the final rebuild includes selection after the pointer focus update.
2026-10-08: both final compilation/package builds succeeded. Jpackage produced
Windows 1.2.25; all 235 app-image files were copied to `NAI-Studio/windows` and
SHA-256 verified, with executable/product versions both 1.2.25. Android
`assembleLocalRelease` produced 1.2.25 / versionCode 24193119, signed with the
existing release identity and certificate verified. The APK is
`NAI-Studio/android/NAI-Studio-Android-1.2.25.apk`; the next iteration is 26.
No USB device was detected, so no phone installation is claimed. Both final
compiled UI class sets contain the deferred focus-selection state. Source
comparison confirms the same query producer and result guard on both platforms.
Following the standing user instruction, no Android/Windows unit tests or UI
interaction tests are run. Source comparison and readonly dictionary inspection
are not claimed as reproduction on either device.
