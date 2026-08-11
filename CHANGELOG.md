# Changelog

## [Unreleased]

### Added

- Recipe Forest support for multiple duplicate recipe roots, with paging, selection, and deletion.
- A right-side root browser that defaults to a reorderable list with batch controls and can switch to a configurable grid.
- Combined material costs, leftovers, and crafting progress across every Forest root.
- Configurable recipe-resolution scopes for the selected root, matching roots, or the whole Forest.
- A configurable item-hover shortcut, bound to F by default, for adding recipes directly to the Forest.
- Alt quantity displays using box, stack, and item icons or localized text, with configurable box grouping and capacity.
- Persistent search and named tree bookmarks integrated into EMI's Favorites sidebar.
- Recipe Forest and mod icons, plus English and Korean localization for the new controls and settings.

### Changed

- Left-clicking Recipe Tree now adds the recipe to the Forest; Shift-clicking replaces the Forest and opens a Solo tree.
- Recipe Forest uses EMI's native Recipe Tree button visuals, and the global Recipe Tree button opens the active Forest first.
- Recipe Forest layout, resolution, quantity, box-grouping, and shortcut settings are available from a modal in EMI's settings screen.
- EMI compatibility is limited to versions 1.1.13 through 1.1.24 for Minecraft 1.21.1 and validated before mixins apply.

### Fixed

- Amount labels render after item icons so stack counts and remainders are no longer obscured.
- Selecting a recipe from an aggregate total-cost item now applies the resolution to the configured Forest scope.
- Forest trees now use EMI's handled Recipe Tree screen, preventing Shift-hover crashes outside container screens.
