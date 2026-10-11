# Numeric-ending tags in weights

2026-10-09 · Windows / Android 1.2.31

When a weighted tag ends in a digit, translation reconstruction and bilingual-card serialization now insert a space before the closing `::`. For example, `2::arist:ratatatat74::` becomes `2::arist:ratatatat74 ::`. This prevents the image-generation model from reading the tag's trailing digits as another weight opener.

The shared `PromptWeight.closingSuffix` helper is used by the dictionary/Baidu/LLM translation renderer, card weight edits, card reordering/renaming, and card display labels. Translation writeback also inserts the separator at recognised closing markers in free-form LLM output. Existing separating whitespace is retained; repeated formatting does not add more spaces. Weight openers and unweighted tags are not changed. Translation undo still restores the original text.

Verification: Windows `desktopInput` and Android `:app:assembleLocalRelease` both passed with JDK 17. Windows app image 1.2.31 was packaged and copied to the canonical `windows` folder (235 files verified by SHA-256). Android 1.2.31 (versionCode 24306960) was signed and verified against the original release certificate. The optional dictionary database remains excluded from both packages. No authorised USB phone was connected, so APK installation was not performed. Per the owner's standing instruction, unit and interactive platform tests were not run.

Delivery: `C:/Users/kallan/Desktop/NAI-Studio/windows/NAI Studio.exe` and `C:/Users/kallan/Desktop/NAI-Studio/android/NAI-Studio-Android-1.2.31.apk`. Generated packages and signing secrets remain outside Git. Source is backed up only to the private backup remote; the public repository and Releases are unchanged.
