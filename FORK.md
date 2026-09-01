# Local hardened fork

Fork of [h2a/claude-usage-intellij-plugin](https://github.com/h2a/claude-usage-intellij-plugin)
(forked at upstream `770c800`, v0.3.2) carrying local security fixes. Built and installed from disk
rather than from the JetBrains Marketplace.

## Why this fork exists

An audit of upstream v0.3.2 — both the git source and the published Marketplace binary — found no
credential exfiltration. The access token goes to exactly one endpoint,
`https://api.anthropic.com/api/oauth/usage`, and all four bundled dependency jars are bit-identical
to Maven Central. What it did find were a handful of local-disclosure defects and a trust-on-update
gap, fixed here.

## Divergence from upstream

| Area | Change | Why |
|---|---|---|
| `Secrets.kt` (new) | `safeMessage()` / `redactUrlCredentials()` | Central redaction helpers, free of IntelliJ imports so they are unit-testable. |
| `ClaudeCredentialsReader.kt` | Parse failures log the exception *type*, not its message. `Result.Success.toString()` redacted. | kotlinx-serialization 1.6.2 appends a `JSON input: …` excerpt to decoding exceptions. The input is the credentials blob, so `LOG.warn("… ${e.message}")` wrote the OAuth access token into `idea.log` — a file routinely attached to bug reports. Proven by `CredentialLeakTest`. |
| `UsageResponse.kt` | `OAuthCredentials.toString()` redacted | The generated data-class `toString()` printed `accessToken` and `refreshToken` verbatim. Nothing logged it upstream, but any future `LOG.info("$credentials")` would. |
| `ClaudeProxyResolver.kt` | Parse failures log a redacted URL; `~/.claude/settings.json` failures log the exception type only. Pure parsing extracted to `ProxyUrlParser.kt`. | `URISyntaxException` echoes the offending URL, which may hold a proxy password. That settings file's `env` block routinely holds API keys. |
| `ProxyCredentialStore.kt` (new) + `ClaudeUsageSettingsConfigurable.kt` | Proxy credentials go to the IDE password safe; only scheme/host/port is persisted. | Upstream stored whatever was typed in the Proxy URL field — including `user:password@` — as plain text in `ClaudeCodeUsage.xml` under the IDE config directory. |
| `ClaudeUsageSettingsComponent.kt` | Auto-detected proxy URL is masked in the UI; added a note about password-safe storage. | The label rendered `user:pass` on screen, where it lands in screenshots. |
| `ClaudeUsageStatusBarWidget.kt`, `ClaudeUsageService.kt` | Keychain hint states the `Allow` vs `Always Allow` trade-off instead of just recommending `Always Allow`. | `Always Allow` grants the IDE — and every plugin running inside it — persistent silent read on the Claude token. That is a security decision the user should make knowingly. |
| `plugin.xml`, `gradle.properties` | Plugin id `…claudeusage.local`, name `Claude & Codex Usage (local build)`, version `0.3.2-local.N` | A distinct id stops the IDE matching this build against the Marketplace listing, so a future upstream release can never silently replace these fixes. Settings carry over because the `@State` storage name is unchanged. |
| `build.gradle.kts`, `src/test/` | JUnit 5 + a test suite (upstream has none) | The redaction is exactly the kind of property that regresses silently. |

## Features added

**A separate Codex usage widget and popup.**

Codex stays independent from the Claude implementation: it has its own status-bar factory, cached
state, refresh cooldown, errors and popup. The status bar uses the fullest quota window returned by
Codex; its popup shows every returned bucket/window. Both provider pills use bundled official
favicons instead of provider-name text.

Codex authentication stays with the installed CLI. The plugin launches `codex app-server --stdio`,
performs the documented JSONL handshake, calls `account/rateLimits/read`, and closes the child
process. It never parses or stores `~/.codex/auth.json`. The request is bounded by a timeout and the
process is closed on success and failure. Its child `PATH` includes the detected executable and
resolved-symlink directories before common install directories and the IDE's inherited `PATH`, so a
GUI-launched JetBrains IDE does not strand npm/Homebrew or version-manager runtimes. Bounded stderr
capture distinguishes a missing runtime and outdated CLI without putting raw output in the widget.

| Area | Change |
|---|---|
| `CodexAppServerProtocol.kt`, `CodexAppServerClient.kt` | Tested JSONL handshake, rate-limit request, GUI `PATH` repair, safe stderr diagnostics, timeout and child-process cleanup. |
| `CodexUsageResponse.kt` | Models both the backward-compatible single bucket and the multi-bucket response without duplicating rows. |
| `CodexUsageService.kt`, `CodexUsageState.kt` | Independent scheduling, cache, cooldown, backoff and listeners. |
| `CodexUsageStatusBarWidget.kt` | Separate favicon progress pill and Codex-only live popup. |
| `UsageProviderIcons.kt`, `resources/icons/` | Bundled 16 px Claude and Codex favicons; no runtime image downloads. |
| `ClaudeUsageSettings*` | Independent provider toggles, auto-detected Codex path feedback and an optional executable override. |

**Usage credits and the model-scoped weekly limit** (matching the official `/usage` view).

Upstream already fetched both and threw them away. `extra_usage.monthly_limit` / `used_credits` were
parsed and never read, and the model named only `five_hour` / `seven_day` / `seven_day_sonnet` — so on
a current account, where every `seven_day_*` field is null, the model-scoped weekly row ("Weekly ·
Fable") was dropped entirely. `Json { ignoreUnknownKeys = true }` made both losses silent.

| Area | Change |
|---|---|
| `UsageResponse.kt` | Models the API's self-describing `limits[]` and modern `spend{}` blocks. Rows render from `limits[]` when present, falling back to the named fields. An unrecognised limit `kind` is humanised, not dropped. |
| `CreditsFormatter.kt` (new) | `$0.00 of $1,000.00` for the popup, `$0.00 / $1,000.00` for the status bar. Formats in the viewer's locale with the payload's currency swapped in. |
| `QuotaTiers.kt` (new) | Single source of truth for tier ids, previously duplicated across three files. Adds `Weekly (model-scoped)` and `Usage credits`. |
| `ClaudeUsageStatusBarWidget.kt` | Credits row; `createUsageRow` gained an optional `valueText` so spend can sit where the percentage normally does. |
| `ClaudeUsageService.kt` | Threshold notifications route through `utilizationForTier`, so they cover credits too. |

**The status bar switches to credit spend when the displayed window is maxed.**

At 100% the percentage is pinned and says nothing more, but that is exactly when the interesting
number changes: past the limit, work bills to extra-usage credits. So the pill swaps `100% • 1h 5m`
for `$20.74 of $1,000.00 • 1h 5m` — the countdown stays, because it says when the overflow ends.

| Area | Change |
|---|---|
| `UsageResponse.kt` | `creditsOverflowFor(tier)` — true when the tier is at ≥100% *and* credits are on. Built on `utilizationForTier`, so it covers the modern `limits[]` payload and the legacy named fields alike, and is unit-tested without a running IDE. |
| `ClaudeUsageStatusBarWidget.kt` | `StatusBarDisplay` carries a `BarScale`, so the fill colour follows what the number *means* rather than its magnitude: quota percentages stay green → yellow → red, credits get their own amber → orange → red. The popup's credits row uses the same scale. |

Two deliberate choices:

- **Only the selected tier flips the pill.** Another limit maxing out does not, so the pill never
  changes based on a number it was not showing.
- **No spend figures means no switch.** If credits are on but the payload reports no amounts, the
  honest pinned `100%` is kept rather than an invented `$0.00`.

The trigger is `>= 100.0`, and the boundary is the feature — one percent early and the pill hides a
window that still has headroom. `UsageResponseTest` pins 99 / 100 / 104.

**A chime on both switches** (new "Play a sound when usage switches to or from credit spend" setting,
on by default). Both transitions land while you are looking at your editor rather than the status bar,
which is the whole point of a sound.

| Area | Change |
|---|---|
| `UsageSound.kt` (new) | Two distinct sounds via `afplay`: Submarine's low note for *starting* to bill to credits, Glass's bright one for coming back off it. One tone for both events would still leave you checking which way it went. Falls back to `Toolkit.beep()` off macOS — the AWT beep alone is silenced by the macOS "Play user interface sound effects" preference, which would make the setting look broken. Never throws; a chime must not break a refresh. |
| `ClaudeUsageService.kt` | `maybeChimeOnCreditsSwitch` tracks the previous overflow state in an `AtomicReference` next to `previousUtilization`, and fires on either edge. Records that state whether or not the sound is enabled, so arming the setting cannot fire on an earlier transition; a first reading and a failed fetch both stay silent. |

Note that a just-reset, unused window reports `percent: 0` with `resets_at: null` — there is genuinely
no countdown yet, so the pill reads a bare `0%` until the next request opens a new window, after which
it reads `15% • 4h 45m` as usual. Same `resets_at: null` case 0.2.4 fixed for parsing, not a regression.

**Popup trimmed.** The "View on claude.ai" link and the last-fetch clock were removed from the popup
footer, leaving just Settings and Refresh. The link opened a browser from a widget whose whole point
is not needing one, and the clock restated what the refresh button's own cooldown tooltip already
says. `USAGE_URL`, `createLinkLabel()` and the `BrowserUtil` import went with them, so the plugin no
longer opens external URLs at all.

Two traps worth remembering, both pinned by `UsagePayloadTest` against a captured real response:

- **`monthly_limit` is in minor units.** A $1,000 cap arrives as `100000` with `decimal_places: 2`.
  Treating it as dollars renders `$100,000.00`.
- **Currency is not USD.** The payload carries `currency` (`CAD` here). Formatting with a fixed US
  locale renders `CA$1,000.00`; using the viewer's locale gives the `$1,000.00` the official view shows.

Tier ids are persisted in `ClaudeCodeUsage.xml` as `statusBarQuotaTier`, so they are ids as well as
labels — renaming one silently drops that user to the 5-hour tier. Only ever add.

## Not fixed (accepted, upstream-inherent)

- **Undocumented endpoint + spoofed User-Agent.** The plugin calls an internal OAuth usage endpoint
  with `User-Agent: claude-code/2.1.131`. Inherent to what the plugin does; it may break or be
  rate-limited without notice.
- **`credentialsFilePath` is an unrestricted path read.** It is your own setting in your own IDE, so
  it is not a privilege boundary.
- **Default 1-minute refresh** (≈1440 token transmissions/day) is unchanged. Raise it in
  *Settings → Tools → Claude & Codex Usage* if you want less traffic.

## Build and install

```fish
set -x JAVA_HOME /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew test buildPlugin
```

The installable zip lands in `build/distributions/`. In WebStorm:
**Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip, restart.

Keep **auto-update disabled** for this plugin (the distinct plugin id already prevents Marketplace
matching) and re-audit before merging anything from `upstream`.

## Syncing with upstream

```fish
git fetch upstream
git log --oneline HEAD..upstream/main   # review every incoming commit before merging
git merge upstream/main
```

Re-run `./gradlew test` after any merge — the tests in `src/test/kotlin` encode the fixes above.
