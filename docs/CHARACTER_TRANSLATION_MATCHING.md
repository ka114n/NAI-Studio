# Character-only fuzzy translation — 2026-10-07

The user requested that partial Chinese translation use the dictionary's character
category only. Initial delivery was Windows and Android 1.2.22; the character-name
ranking correction described below is delivered in 1.2.23.

## Behavior

- Exact Chinese translation remains available for every category and custom alias.
- When the existing fuzzy-matching preference is enabled and no exact label matches,
  fuzzy translation queries only Danbooru category **4 (character names)**.
- The SQL category predicate is applied before sorting and the 256-result limit.
  Artists, copyrights, general attributes and metadata cannot fill the candidate
  limit or be selected as partial-name results.
- Custom aliases participate in fuzzy translation only if their English tag is
  present in the main dictionary as category 4. Edits to such a character keep that
  eligibility. Unclassified custom tags remain available for exact translation.
- Deleted entries and replaced built-in labels remain excluded. There is no legacy
  or unrestricted alias fallback after character-only fuzzy lookup fails.
- From 1.2.23, ambiguous built-in character-name candidates prefer a prefix match,
  then descending post count, then shorter labels and stable lexical order. Custom
  aliases retain their existing priority over the built-in dictionary.
- Unmatched text continues to the configured translation service. That service can
  still translate general descriptions; only the local fuzzy dictionary step is
  category-restricted.
- The same policy applies to Chinese-to-English conversion in bilingual prompt
  cards. Dictionary-tool search continues to search every category by substring.
- The preference is still off by default; existing saved choice is retained. Its
  label and help text now describe character-name matching explicitly.

The dictionary asset and its 330,745 rows are unchanged. No database regeneration,
downloads, settings migration or user-record deletion is required.

## Delivery verification

Per the standing user instruction, do not run either platform's tests or interactive
acceptance. Required delivery checks are compilation, packaging, resource integrity,
copy hashes, APK release signature and USB installation if connected.

Final delivery checks completed:

- Windows `desktopInput` and jpackage app-image succeeded. The full image (235 files)
  was copied to the canonical `windows` directory with per-file SHA-256 equality.
  The launcher and in-app version are 1.2.22.
- Android `assembleLocalRelease` succeeded: 1.2.22 / versionCode 24152477; the next
  stored build iteration is 23. APK size is 40,542,254 bytes.
- The original release certificate was verified:
  `75ba9b19444a13e73bbdb5b1261dd4813a395790b5a2837bd95718ea584d7669`.
- USB `adb install -r` succeeded; package-manager output confirms 1.2.22 / 24152477.
- Both delivered archives contain the unchanged main database, manifest and MIT
  license with matching source hashes; neither includes the deleted legacy TSV.
- Windows launcher SHA-256:
  `4282fc77d0b44b66ab330d31003cf9c26898a07e860d1c64dffc9f3f8244ef50`.
- Windows main JAR SHA-256:
  `99b72bd1d199419b3b77540f3abdb4298e3c9feb56e251fe836cbf43711b79fe`.
- Android APK SHA-256:
  `32dcc5846e43e7269d1a5105585b67f4ee1f938ede26fda7901e5b3286c86946`.
- `git diff --check` passed. No tests or interactive acceptance were run.
- Source and recovery notes are backed up to the private backup remote. Public
  origin and Releases are unchanged. Generated packages, user data and signing
  secrets are excluded from the Git backup.

## 1.2.23: canonical character ranking and switch location

The user reported that translating 阿米娅 produced the unqualified `amiya` and asked
where to enable character matching. The preference is in **My → LLM API**, a card
collapsed by default: click its status row to expand it; the character-name switch
is directly below the translation-service selector. The default remains off, and
no stored user choice is changed by this release. With the LLM service and this
preference off, the existing ordinary-translation route bypasses local matching.

Read-only inspection of the supplied snapshot confirmed category-4
`amiya (arknights)` → 阿米娅（明日方舟）, with post count 12,266. It also exposed a separate
ranking issue: `amiya aranha` → 阿米娅·阿兰尼亚 (204 posts) was ranked first because its
Chinese label is shorter. Fuzzy character lookup now ranks prefixes first, then
post counts, before length and lexical tie breakers. This selects the main Arknights
character from the snapshot when entering 阿米娅, with matching enabled and no user
override/deletion. More specific costume/class text still constrains the substring
query. Exact matching, custom-override priority, category restriction and dictionary
tool search are unchanged. This does not hard-code Amiya or a franchise suffix.

These findings are from source and data inspection, without reproducing the user's
API request or UI flow. Final 1.2.23 delivery checks:

- Both compilation/package builds succeeded. Windows app-image version is 1.2.23;
  all 235 deployed files match the image by SHA-256.
- Android versionName 1.2.23, versionCode 24153399; next build iteration is 24.
  APK size is 40,542,254 bytes; the original release certificate is unchanged.
- USB installation succeeded and package-manager output confirms that version.
- Both archives' main dictionary, manifest and license match source assets by hash;
  the old TSV remains absent.
- Windows launcher SHA-256:
  `c1bfa2cbf47ecba6fcf4caffc9d4e8a348459af6a01c5dd4304884064d734418`.
- Windows main JAR SHA-256:
  `f8b8a1cb5c30b3b9c742e916b3515c4540e5c8cf5c1ecd491618f0a809641034`.
- Android signed APK SHA-256:
  `0420da0d93a44b2e742b6f05fb2a4ef84ced07a0d38ae4f1ae33428be67cf0d9`.
- `git diff --check` passed. No tests or UI/API reproduction were run, per the
  standing user instruction. Source and recovery notes are privately backed up;
  public origin and Releases are unchanged.
