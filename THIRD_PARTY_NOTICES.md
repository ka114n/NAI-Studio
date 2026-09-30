# Third-party notices and release checklist

The project-level MIT license applies only to code and materials that the project copyright holder is authorized to license. It does not override third-party licenses or establish redistribution rights for external data, assets, marks, services, or libraries.

## Data and assets to clear before publication

- **Tag dictionary:** both platform trees bundle a `dictionary.tsv` described in source comments as derived from an external Danbooru tag-index snapshot. It is tag lookup data (tag, category, and count), not an LLM/system prompt; the data includes some mature vocabulary. The dictionary loader remains in the code, but `tagSuggestions()` currently has no UI call site (desktop autocomplete was disabled after causing lag). The exact source revision, acquisition method, generator, attribution requirements, and redistribution terms are not established in this handoff. Do not publish the dictionary until those are verified. If rights are unclear, remove it or regenerate it from a source with documented redistribution terms.
- **Brand and icon assets:** verify authorship and permission for `brand/nai-icon.svg`, generated app icons, and any included logo/wordmark. Avoid implying official NovelAI affiliation.
- **Brush and test assets:** verify the origin and redistribution rights for the images and HTML samples under `desktop/naistudio-desktop/docs`.
- **Dependencies:** generate a dependency inventory/SBOM from the Gradle projects and include required license/notice files. The desktop build includes Compose Desktop/Material, OkHttp, org.json, JNA, lifecycle, coroutines, Stylus Compose, and KCEF/JCEF among other transitive components; Android has its own AndroidX/Compose dependency graph.

## Services and release behavior

- The app sends requests to user-configured image-generation and optional LLM services. Keep credentials user-supplied; never commit service keys or signing secrets.
- Public Android releases use a separately managed release signing key stored outside the repository. Historical debug builds may have a different signature.
- Separately from `dictionary.tsv`, the tag-codex screen can optionally show codex entries marked NSFW; parsing excludes them by default. State the intended audience/content policy and decide whether that option should remain available in the release.

This checklist is a technical inventory, not a legal clearance. Resolve rights and include upstream-required notices before public distribution.
