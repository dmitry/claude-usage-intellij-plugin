# Claude & Codex Usage IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/29946-claude-code-usage.svg)](https://plugins.jetbrains.com/plugin/29946-claude-code-usage)

A simple IntelliJ plugin that displays Claude and Codex subscription usage in separate IDE status-bar widgets.

> **Note:** This plugin shows subscription quota windows. It does not track API-key billing, costs, or conversation history.

![Status Bar Widget](preview/status.png)

## Features

- Separate Claude and Codex status-bar progress indicators, each with its own popup and refresh button
- Bundled Claude and Codex favicons instead of provider-name text in the compact indicators
- Real-time usage percentages with automatic refresh every minute (configurable)
- Color-coded progress indicator (green → yellow → red)
- Time until quota reset
- Claude popup with all reported usage tiers and credit spend
- Codex popup with every quota bucket and window reported by the installed CLI
- Dark and light theme support
- Error state display with actionable hints (missing credentials, auth expired, network/proxy issues)
- Last fetch timestamp in the popup
- Works behind a corporate proxy (auto-detected, see [Proxy support](#proxy-support))
- Configurable settings (Settings → Tools → Claude & Codex Usage):
  - Independently enable the Claude and Codex widgets
  - Optional Codex executable path (empty auto-detects `codex` and shows the resolved path)
  - Status bar quota display (5-hour, 7-day, or 7-day Sonnet)
  - Auto-refresh interval (1–60 minutes, default 1)
  - Warning color thresholds (yellow/red)
  - Custom credentials file path
  - Proxy URL override
  - Reset to defaults button

![Popup Details](preview/details.png)

## Requirements

- IntelliJ IDEA 2024.1+ (works with all JetBrains IDEs: WebStorm, PyCharm, RubyMine, etc.)
- Claude Code CLI installed and authenticated when the Claude widget is enabled
- Codex CLI installed and signed in with ChatGPT when the Codex widget is enabled

The plugin reads credentials from:

- **macOS:** the system Keychain entry `Claude Code-credentials` (where Claude Code CLI now stores them by default). On first run, macOS will show a one-time access dialog — click **Always Allow** for your IDE so the plugin can read the entry without further prompts. Falls back to `~/.claude/.credentials.json` if the keychain entry is missing.
- **Linux / Windows:** `~/.claude/.credentials.json` (created when you sign in to Claude Code CLI).

The credentials path and the macOS Keychain toggle are both configurable in **Settings -> Tools -> Claude & Codex Usage**.

For Codex, the plugin starts the installed CLI's local `app-server`, requests `account/rateLimits/read`, then closes the process. Authentication and token refresh remain owned by Codex; this plugin does not read or store `~/.codex/auth.json`. See the [official Codex app-server documentation](https://learn.chatgpt.com/docs/app-server).

JetBrains IDEs launched from Finder, the Dock, or a desktop menu can inherit a much smaller `PATH` than your terminal. The plugin repairs the child process `PATH` automatically using the detected Codex executable directory, its resolved symlink directory, common CLI install directories, and the IDE's existing `PATH`. This lets npm/Homebrew and version-manager launchers find their adjacent Node.js runtime without requiring the IDE itself to inherit your shell setup. Settings shows the executable that was detected; use the Codex executable field only when auto-detection cannot find your installation.

Codex launch errors are read from a bounded stderr buffer and converted to fixed, non-secret guidance for a missing runtime, an outdated CLI, authentication, timeout, and general request failures. Raw stderr is never displayed in the widget.

> **Note (SSH):** the macOS Keychain is locked over SSH — same limitation as the official CLI. Either run `security unlock-keychain` first or fall back to the file path.

## Proxy support

The plugin makes a single HTTPS request to `api.anthropic.com`. Behind a corporate proxy it picks up the proxy automatically, in this order:

1. **Proxy URL** in **Settings → Tools → Claude & Codex Usage** (manual override)
2. `HTTPS_PROXY` / `HTTP_PROXY` in the `env` block of `~/.claude/settings.json` — the same block the Claude Code CLI reads:
   ```json
   { "env": { "HTTPS_PROXY": "http://proxy.corp:3128" } }
   ```
3. The `HTTPS_PROXY` / `HTTP_PROXY` environment variables of the IDE process

The settings panel shows which proxy was auto-detected. Supported formats:

- `http://proxy.corp:3128` (or bare `proxy.corp:3128`, port defaults to 80)
- `http://user:pass@proxy.corp:3128` for proxies requiring basic authentication
- `socks5://proxy.corp:1080` for SOCKS proxies (credentials in SOCKS URLs are ignored — the JDK does not support them per connection)
- `none` (or `direct`) to skip auto-detection entirely and always connect directly, even when `HTTPS_PROXY` is set

An unparseable Proxy URL is rejected when you apply the settings, so a typo can never silently downgrade the plugin to a direct connection. The proxy is resolved per request, so changes take effect on the next refresh without restarting the IDE.

If the proxy rejects the request or cannot be reached, the widget shows **Claude: proxy err** with a hint in the popup instead of a generic offline state.

> **Note:** for authenticated proxies the JDK disables basic authentication over HTTPS `CONNECT` tunnels by default. JetBrains IDEs ship with this restriction lifted; on a custom JVM add `-Djdk.http.auth.tunneling.disabledSchemes=""` to your IDE's VM options if the proxy returns 407.

## Development

### Prerequisites

- JDK 17+
- Gradle 8.5+ (wrapper included)

### Build

```bash
./gradlew buildPlugin
```

Output: `build/distributions/claude-usage-intellij-plugin-x.x.x.zip`

### Run in sandbox IDE

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin installed.

### Clean build

```bash
./gradlew clean buildPlugin
```

## Installation

### From JetBrains Marketplace (recommended)

1. In your IDE: Settings → Plugins → Marketplace
2. Search for "Claude Code Usage"
3. Click Install

Or install directly: [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/29946-claude-code-usage)

### From source

1. Build the plugin: `./gradlew buildPlugin`
2. In your IDE: Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select `build/distributions/claude-usage-intellij-plugin-x.x.x.zip`
4. Restart the IDE

## Usage

Once installed, enable the provider widgets you use under **Settings → Tools → Claude & Codex Usage**. Each status-bar indicator opens its own quota popup.

## Roadmap / Feature Ideas

- [ ] Usage history graph over time
- [ ] Export usage statistics

## Contributing

Contributions welcome! Please open an issue first to discuss proposed changes.

## License

MIT
