# Desktop tools sidebar (1.2.14)

The generation workspace has a dedicated right-hand tools sidebar, beside the canvas and existing left prompt panel. It uses the sidebar surface and borders. An always visible 68 dp rail lists dictionary, character encyclopedia, style codex, metadata and reverse prompting. Selecting a tool opens the sidebar body and reuses the existing ToolContent implementation. Grid tools retain bounded scrolling; other tools retain their existing scroll container.

The sidebar initially opens, and its collapse control persists tools.dock.collapsed. Collapsing leaves only the tool rail. The left divider adjusts expanded width and persists tools.dock.width; width is constrained to 240–520 dp and adjusted to available window space. Resizing derives width from pointer root coordinates and the drag-start width rather than the previous composed frame. Width is read at measurement/placement time, avoiding whole-shell recomposition for every pointer event. GenerateScreen gets its actual remaining constraints; the balance chip stays at the canvas corner.

Expanded and collapsed desktop sidebars omit the Tools navigation item. The old tools page/menu shortcut redirects to the generation page; the legacy openToolWindow entry point selects a tool and requests the generation page. The old floating tool layer is no longer rendered. Other navigation indices and shortcuts retain their existing numbering.

The docked prompt panel omits the redundant Prompt/Storyboard caption-button row. Its positive/negative switch now matches the phone workflow: positive shows style and positive fields, negative replaces this area with its independent negative field. Locks, histories, action bars, role/storyboard controls and the parameter edge trigger/drawer remain available.

Build and delivery details for the current release are recorded in TAG_DICTIONARY_AND_CARDS.md. No desktop tests or interaction/visual validation are run under the user's direct build-and-deliver instruction; responsiveness changes are implementation changes, not measured performance claims.

Recovery: use the canonical AGENTS.md build instructions. A fresh complete application jar and matching launcher/configuration should be deployed together; generated app images and installed packages are excluded from Git. Private signing keys remain outside the checkout. This source snapshot is backed up only to the private backup remote; public origin and Releases are unchanged.
