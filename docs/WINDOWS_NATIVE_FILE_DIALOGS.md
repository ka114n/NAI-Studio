# Windows native file dialogs

Windows image/file imports, save-as operations and output-folder selection use the Shell Common Item Dialog (IFileOpenDialog/IFileSaveDialog). This covers backup, image, font and settings file pickers through DesktopUiHost. Non-Windows routes retain AWT/Swing; Windows native failure returns null with an error toast rather than opening a Java picker. Cancel never launches another dialog.

Each native dialog runs on a dedicated COM STA thread. The main window remains its owner; successful picks update the shared last-directory preference. Save dialogs retain suggested filenames and overwrite confirmation. Native filters include supported extensions plus All files; specific image and ZIP MIME types are mapped correctly. Native string/filter buffers remain reachable until the modal dialog closes, and COM interfaces are released on every exit path.

References: Microsoft IFileDialog and IFileDialog::SetFileTypes documentation. The native configuration smoke test creates and configures all three dialog modes on Windows without displaying a UI. It checks COM creation, filter marshaling, filesystem/single-selection options and save/open/folder flags. Manual visual selection/cancellation remains to be verified in the running app.

Verification (2026-10-04): `test desktopInput --no-daemon` passed (562 tests, 6 skipped, zero failures/errors). All three new Windows dialog tests passed, including native COM configuration for open/save/folder without showing UI. The desktop runtime JAR was updated after this build. Phone sources are unchanged.
