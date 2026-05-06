# Changelog

## [0.3.0] - 2026-05-06

### Added
- macOS Keychain support — Claude Code on macOS now stores credentials in the Keychain instead of `~/.claude/.credentials.json`; the plugin reads them via the `security` CLI (same approach the CLI itself uses)
- New "Use macOS Keychain" setting (default on, macOS only) — falls back to the credentials file when the keychain entry is missing
- Distinct error states for keychain access denied, prompt timed out, and other keychain failures, with actionable hints in the popup ("Open Keychain Access > Always Allow")

### Notes
- On first read after install, macOS will show a one-time prompt; click **Always Allow** so the plugin can access the `Claude Code-credentials` entry without further interruptions
- Over SSH the macOS Keychain is locked — same limitation as the official CLI

## [0.2.4] - 2026-05-06

### Fixed
- Parse error when API returns `resets_at: null` for unused quotas (e.g. `seven_day_sonnet`) — the widget no longer falls back to "Claude: error" when usage is otherwise valid
- Manual refresh button stayed disabled for the full auto-refresh interval; now has its own 5-second cooldown decoupled from the scheduler

### Added
- Configurable auto-refresh interval setting (1–60 minutes, default 1 minute)

### Changed
- Reset-time hint hidden in popup and status bar when the API reports no reset time
- Max backoff on errors increased to 60 minutes

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
