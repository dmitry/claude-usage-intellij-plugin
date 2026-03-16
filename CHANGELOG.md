# Changelog

## [0.2.3] - 2026-03-12

### Improved
- Refresh button disabled when last fetch was less than 1 minute ago
- Exponential backoff on all API errors, not just rate limiting
- Single shared fetch across all IntelliJ windows (already app-level service, now with cooldown guard)

## [0.2.2] - 2026-03-09

### Improved
- Countdown timer now shows days, hours and minutes (e.g. "2d 14h 1m") when reset time exceeds 24 hours

## [0.2.1] - 2026-03-05

### Fixed
- Usage API stopped working due to missing User-Agent header — updated to match Claude Code CLI

### Added
- Exponential backoff on rate limiting (doubles retry interval up to 16 minutes, resets on success)
- Dedicated rate-limited error state with user-friendly message

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
