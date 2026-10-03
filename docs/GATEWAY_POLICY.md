# Gateway feature permissions

Image-to-image and inpainting on Android and desktop are submitted to the configured server. NAI Studio does not maintain a hardcoded gateway action blacklist. NAI Gate and its upstream decide feature access, Key permissions, model permissions, budgets and parameter limits.

The third-party server address must still be configured before submitting. An empty gateway address must not silently fall back to NovelAI. Existing local input validation, queue handling and rate-limit handling remain in place.

HTTP errors preserve top-level official messages and nested NAI Gate `error.message`, including 401, 402 and 403 policy rejections. When no message is supplied, existing status-specific fallbacks remain available.

Verification (2026-10-03):
- Desktop: `../gradlew.bat test desktopInput --no-daemon` passed, including `NaiApiGatewayErrorTest`.
- Android: `./gradlew.bat :app:testLocalDebugUnitTest :app:assembleLocalRelease --no-daemon --no-parallel` passed, including `NaiApiGatewayErrorTest`; local verification build 1.1.147 (next iteration 148). The initial parallel compilation failed; a standalone compile and the subsequent verification run passed.
- No live gateway generation was performed; server authorization and upstream billing were not exercised.

## Third-party credentials and quota UI (2026-10-03)

Both platforms label third-party credentials as API keys, with matching verification/clear messages. The quota section follows the credential controls and provides manual refresh, loading and unavailable states. It displays the server-provided V5 remaining count and Key monthly Anlas budget/permissions; it does not present the gateway's simulated official subscription tier as an actual NovelAI subscription. Unlimited monthly Key budgets remain subject to other server limits. The current NAI Gate compatibility endpoint does not expose every daily quota.

Desktop endpoint editing now binds to `thirdPartyBaseUrl`, matching the request path instead of editing the official `imageBaseUrl`. Address edits debounce quota queries, and switching server/credentials cancels outdated quota requests and clears old results.

Android unit tests and local Release build 1.1.148 passed (next iteration 149). Desktop `test desktopInput` verification passed after the endpoint binding fix. No live server/credential request was tested.
