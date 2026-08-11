# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 2.0.0](https://keepachangelog.com/en/2.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial Minecraft 1.20.1 development template for Fabric and Forge.
- EMI 1.1.24+1.20.1 compile and development-runtime dependencies.
- Recipe Forest support for multiple duplicate recipe roots, with paging, selection, deletion, and a configurable root grid.
- Combined material costs, leftovers, crafting progress, and batch controls across every Forest root.
- Persistent search and named tree bookmarks integrated into EMI's Favorites sidebar.
- English and Korean user interfaces.

### Changed

- Left-clicking Recipe Tree now adds the recipe to the Forest; Shift-clicking replaces the Forest and opens a Solo tree.
- EMI compatibility is limited to versions 1.1.13 through 1.1.24 for Minecraft 1.20.1 and validated before mixins apply.

### Fixed

- Amount labels render after item icons so stack counts and remainders are no longer obscured.
