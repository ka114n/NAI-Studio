# Desktop UiHost startup compatibility fix

The incremental desktop artifact contained `UiHost.backHandler(boolean, Function0, Composer, int)` but its concrete DesktopUiHost only provided the variant with an extra default-mask integer. Calling the interface on startup raised AbstractMethodError. Earlier UI smoke tests used substitute hosts and did not exercise the real implementation.

Removed the composable interface method's default Boolean and made existing StudioShell call sites pass `enabled = true` explicitly. Back handling behavior is unchanged. A clean rebuild removes stale generated Compose bridges. New tests check that the real host provides a concrete implementation for every compiled abstract interface method and compose its back handler through the UiHost interface using an offscreen Compose scene.

Verification (2026-10-04): `clean test desktopInput --no-daemon` passed (564 tests, 6 skipped, zero failures/errors). Both new host compatibility tests passed. javap confirms identical four-argument compiled backHandler signatures. The rebuilt runtime JAR is copied to the existing desktop application. Phone sources and public Releases are unchanged. Full application window startup remains a manual check after restarting the desktop app.
