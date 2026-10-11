# Translation backend source audit

2026-10-09 · Both platforms

`AppState.translatePrompt` selects the backend from `settings.translateService`. The Baidu branch requires the saved APPID and secret, then runs `PromptTranslate.run` with a callback to `BaiduTranslate.translateLines`.

The pipeline matches the local dictionary first using exact matching. Only `Kind.NEED` pieces are batched for the callback. When every piece is a dictionary hit, the batch list is empty and Baidu is not contacted. The dictionary is therefore a priority layer, not an additional request made alongside Baidu. Pressing translate again without editing the last translated result restores the original text and also does not request a backend. A pending dictionary-download decision, missing Baidu configuration, locked prompt or ongoing operation can return before a request.

A weakness exists in `PromptTranslate.translateBatch`: `runCatching { backend(...) }.getOrNull()` hides backend exceptions. Failed requests and response line-count mismatches are split recursively; ultimately missing translations retain the source while `applyPromptResult` still shows translation completed. Log details count dictionary hits, translated pieces, misses and pipeline callback attempts, but do not retain the original error. These attempt counts also do not include the Baidu HTTP client's own retry. Therefore an unchanged unknown tag is insufficient evidence that Baidu was not called.

This is a source audit, not a diagnosis of a specific live request. No user credentials were read, no paid translation request was issued and no interactive tests were run. The missing input example and current platform/service selection are needed to distinguish an all-dictionary-hit result from an API failure in the reported case. Backend selection and dictionary-first behavior were not changed by the numeric-tag separator fix.
