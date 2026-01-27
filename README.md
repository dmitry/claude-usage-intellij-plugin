# Claude Usage IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/29946-claude-code-usage.svg)](https://plugins.jetbrains.com/plugin/29946-claude-code-usage)

A simple IntelliJ plugin that displays your Claude subscription usage in the IDE status bar.

> **Note:** This plugin shows Claude subscription usage limits only (5-hour, 7-day quotas). It does not track API token usage, costs, or conversation history.

![Status Bar Widget](preview/status.png)

## Features

- Real-time usage percentage in the status bar
- Color-coded progress indicator (green → yellow → red)
- Time until quota reset
- Detailed popup with all usage tiers
- Direct link to Claude settings

![Popup Details](preview/details.png)

## Requirements

- IntelliJ IDEA 2023.1+ (works with all JetBrains IDEs: WebStorm, PyCharm, RubyMine, etc.)
- Claude Code CLI installed and authenticated

The plugin reads credentials from `~/.claude/.credentials.json` which is created when you sign in to Claude Code CLI.

## Development

### Prerequisites

- JDK 17+
- Gradle 8.5+ (wrapper included)

### Build

```bash
./gradlew buildPlugin
```

Output: `build/distributions/claude-usage-intellij-plugin-0.1.0.zip`

### Run in sandbox IDE

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin installed.

### Clean build

```bash
./gradlew clean buildPlugin
```

### Project structure

```
src/main/kotlin/com/github/dmitry/claudeusage/
├── ClaudeCredentialsReader.kt   # Reads OAuth token from ~/.claude/
├── ClaudeUsageService.kt        # API client, caching, refresh logic
├── ClaudeUsageStatusBarWidget.kt # Status bar UI component
└── UsageResponse.kt             # Data classes for API response

src/main/resources/META-INF/
└── plugin.xml                   # Plugin descriptor
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
3. Select `build/distributions/claude-usage-intellij-plugin-0.1.0.zip`
4. Restart the IDE

## Usage

Once installed, you'll see your usage in the status bar (bottom right). Click to see detailed breakdown of all quota tiers.

## Roadmap / Feature Ideas

- [ ] Notifications when approaching usage limits (80%, 90%, 100%)
- [ ] Usage history graph over time
- [ ] Multiple account support
- [ ] Configurable refresh interval
- [ ] Token refresh handling when OAuth expires
- [ ] Settings panel for customization
- [ ] Export usage statistics

## Contributing

Contributions welcome! Please open an issue first to discuss proposed changes.

## License

MIT
