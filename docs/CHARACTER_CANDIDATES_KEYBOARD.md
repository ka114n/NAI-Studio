# Android character candidates — 1.2.30

The character search's non-focusable popup used a separate Android window and could overlap the IME. Android candidates now expand as an inline, bordered list inside the role drawer's scrolling content. The ordinary drawer/window clipping keeps candidates above the keyboard.

The list height is bounded to one third of the actual visible window frame (80–240 dp) and scrolls internally. Measuring the visible frame avoids subtracting the IME twice when Android has already resized the dialog. A bring-into-view request follows changes in keyboard height or candidate-panel size. The search input retains focus; picking a candidate still creates an expanded character section and clears the query. Each new query resets candidate scrolling and retains the existing stale-result guard, debounce and character-only matching.

Desktop behavior and version remain 1.2.29. Android version is 1.2.30. Unit and interactive tests were skipped per the owner's standing instruction. Public origin and Releases remain unchanged.

Android `:app:assembleLocalRelease` passed in 2 minutes 8 seconds. The delivered APK is versionCode 24234662, 2,211,822 bytes, signed with the original release certificate (verified). No authorized USB phone was detected, so installation was not performed. The next Android build iteration is 31. The package retains the optional-download dictionary manifest and excludes the database payload.
