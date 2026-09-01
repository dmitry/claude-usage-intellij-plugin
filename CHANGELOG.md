# Changelog

## [0.3.2-local.6] - 2026-08-31

### Fixed
- Codex now launches from Finder/Dock-started JetBrains IDEs whose GUI `PATH` omits Homebrew, npm, nvm, or other CLI runtime directories
- Settings shows the auto-detected Codex executable, while the existing path field remains available as an override
- Missing Node.js, an unlaunchable or outdated Codex CLI, authentication failures, and timeouts now have distinct actionable states instead of a generic error
- Codex stderr is drained into a bounded buffer and mapped to fixed diagnostics without exposing raw command output in the UI

## [0.3.2-local.5] - 2026-08-31

### Added
- Separate Codex status-bar progress widget and Codex-only detail popup
- Codex quota retrieval through the installed CLI's documented local app-server protocol
- Independent Claude and Codex widget toggles plus an optional Codex executable path
- Bundled Claude and Codex favicons in the compact status indicators

### Changed
- Settings page renamed to **Claude & Codex Usage**
- Claude and Codex refresh, errors, popups, and cached state are independent

## [0.3.2] - 2026-07-23

### Added
- Proxy support for the usage API request — auto-detects `HTTPS_PROXY`/`HTTP_PROXY` from `~/.claude/settings.json` (the `env` block Claude Code CLI reads), falling back to the `HTTPS_PROXY`/`HTTP_PROXY` environment variables (thanks [@zeroday98](https://github.com/zeroday98), [#4](https://github.com/h2a/claude-usage-intellij-plugin/pull/4))
- New "Proxy URL" setting to override the auto-detected proxy (supports `http://user:pass@host:port` for authenticated proxies); leave empty to keep auto-detecting, and the settings panel shows which proxy was detected
- `socks://`, `socks4://` and `socks5://` proxy URLs are recognised as SOCKS proxies (default port 1080) instead of being treated as HTTP proxies
- Dedicated "proxy error" state — HTTP 407, a failed CONNECT tunnel or an unreachable proxy host is now reported as a proxy problem with a hint in the popup
- `none` (or `direct`) as the Proxy URL disables auto-detection and forces a direct connection even when `HTTPS_PROXY` is set
- The Proxy URL is validated when settings are applied, so a malformed value is rejected instead of silently falling back to a direct connection, instead of a generic "offline"

### Changed
- Proxy credentials are applied per connection instead of replacing the JVM-wide default `Authenticator`, so the plugin no longer interferes with other IDE network requests while a refresh is in flight

## [0.3.1] - 2026-06-29

### Added
- Live usage popup — the refresh button shows a countdown until it can be used again, and usage rows update in place when fresh data arrives, so you can wait and refresh again without closing the popup
- Optional IDE notification when the status bar quota crosses the yellow or red threshold (new "Show IDE notification when usage crosses a warning threshold" setting, on by default)
- Settings button in the usage popup (next to Refresh) for quick access to plugin settings

### Fixed
- Black/empty usage popup on the macOS Metal rendering pipeline (reported on WebStorm 2026.1 / macOS 26) — the popup content now paints an explicit opaque background and forces a window repaint once shown, instead of leaving the uncleared GPU surface visible as a fully black window

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
