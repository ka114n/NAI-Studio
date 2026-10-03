# Gateway feature permissions

Image-to-image and inpainting on Android and desktop are submitted to the configured server. NAI Studio does not maintain a hardcoded gateway action blacklist. NAI Gate and its upstream decide feature access, Key permissions, model permissions, budgets and parameter limits.

The third-party server address must still be configured before submitting. An empty gateway address must not silently fall back to NovelAI. Existing local input validation, queue handling and rate-limit handling remain in place.

HTTP errors preserve top-level official messages and nested NAI Gate `error.message`, including 401, 402 and 403 policy rejections. When no message is supplied, existing status-specific fallbacks remain available.

Verification (2026-10-03):
- Desktop: `../gradlew.bat test desktopInput --no-daemon` passed, including `NaiApiGatewayErrorTest`.
- Android: `./gradlew.bat :app:testLocalDebugUnitTest :app:assembleLocalRelease --no-daemon --no-parallel` passed, including `NaiApiGatewayErrorTest`; local verification build 1.1.147 (next iteration 148). The initial parallel compilation failed; a standalone compile and the subsequent verification run passed.
- No live gateway generation was performed; server authorization and upstream billing were not exercised.
