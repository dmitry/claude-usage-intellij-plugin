# Changelog

## [0.2.0] - 2026-02-21

### Added
- Settings panel (Settings → Tools → Claude Code Usage)
  - Configurable status bar quota display (5-hour, 7-day, 7-day Sonnet)
  - Configurable warning color thresholds (yellow/red)
  - Custom credentials file path
  - Reset to defaults button
- Refresh button in usage popup
- Error state display with actionable hints
- Dark theme support
- Last fetch timestamp in popup

### Changed
- Platform version bumped to 2024.1+

### Fixed
- Widget visibility issues caused by missing EDT dispatch
- Widget not resizing when data changes
- Listener leak on widget dispose

## [0.1.0]

### Added
- Initial release
