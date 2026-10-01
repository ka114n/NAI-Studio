# Project development and backup instructions

- After completing a development task and its appropriate verification, commit the source changes and push them to the private `backup` remote (`ka114n/NAI-Studio-backup`). This is standing user authorization. Check that the destination remains private before pushing.
- Keep backups minimal and recoverable: source code, Gradle wrappers, required assets, tests, configuration templates, and concise change/verification notes. Use Git commits for incremental history.
- Do not create Releases or upload installation packages as part of routine backup. The public `origin` repository is updated only when the user explicitly requests publication.
- Never back up signing private keys, credentials, API tokens, machine-local SDK configuration, user settings, generated images, conversation history, dependency caches, or build output.
- A failed build or incomplete task must be identified as such in backup notes; never label unverified work as verified.
- Apply cross-platform changes to both platform source trees when required. Do not discard other ongoing changes.
- Do not call Astra without an explicit user instruction. If the user requests Luna delegation, use xhigh reasoning.

## Build verification

Windows: from `desktop/naistudio-desktop`, run `..\gradlew.bat test desktopInput --no-daemon` with JDK 17.

Android: from `phone/naistudio`, configure the local SDK and run `gradlew.bat :app:testLocalDebugUnitTest :app:assembleLocalRelease --no-daemon` with JDK 17. The assemble task increments the local version iteration; distinguish the built version from the next build number.

Android release signing keys must stay outside the checkout. A Git backup alone does not restore the private signing identity; the owner must retain its separate secure backup.
