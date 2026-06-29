# Operational tuning overrides (remote-updatable retry/cooldown timing)

A handful of low-level mechanism parameters control *how patiently/quickly* the app does
something - never *what* it does. Previously hardcoded, now tunable without a native release
via `operational-tuning-overrides.json` (this file, fetched by the WebView relative to
`index.html`, same pattern as `command-patterns.json`).

## Format

Every field is optional; omitted fields keep their built-in default (shown below, matching
this app's original hardcoded values):

```json
{
  "mistralMinIntervalMsDefault": 1500,
  "mistralMinIntervalMsFastModels": 420,
  "mistralMaxServerDelayMs": 5000,
  "mistralCancelCheckIntervalMs": 100,
  "modelDownloadMaxRetries": 3,
  "modelDownloadRetryDelayMs": 3000,
  "modelDownloadProgressUpdateIntervalMs": 500,
  "termuxProcessCompletedPrompt": "[Process completed - press Enter]"
}
```

- `mistralMinIntervalMsDefault` / `mistralMinIntervalMsFastModels` — minimum cooldown between
  requests on the same Mistral API key, in milliseconds. The "fast models" tier applies to
  Mistral Medium 3.1/3.5 specifically (they tolerate a shorter cooldown); everything else uses
  the default tier. Negative values are ignored (fall back to the current value).
- `mistralMaxServerDelayMs` — upper bound on how long the app will honor a server-provided
  `Retry-After`/rate-limit-reset header before proceeding anyway.
- `mistralCancelCheckIntervalMs` — how often (ms) the app checks for user cancellation while
  waiting out a cooldown. Lower = more responsive cancellation, slightly more CPU wake-ups.
- `modelDownloadMaxRetries` / `modelDownloadRetryDelayMs` — how many times an offline-model
  download retries on a network error, and how long it waits between attempts.
- `modelDownloadProgressUpdateIntervalMs` — how often the download progress notification
  updates.
- `termuxProcessCompletedPrompt` — the exact marker line Termux:Task appends after a command
  finishes, which the app strips out of the output it shows/forwards. If a Termux:Task update
  ever changes this exact string, fix it here instead of waiting for an app release.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `operational-tuning-overrides.json` and
   passes it to `Android.setOperationalTuningOverrides(json)`.
2. `OperationalTuningConfig` installs the parsed policy; the bridge persists the raw JSON via
   `OperationalTuningOverridesPreferences` so it's restored on the next app start.
3. `MistralRequestCoordinator`, `ModelDownloadManager`, and `TermuxOutputPreferences` all read
   `OperationalTuningConfig.current()` live (re-checked on every request/attempt/parse) instead
   of a hardcoded constant.

To retune any of these: edit `operational-tuning-overrides.json` in this repo and commit - no
new app version needed.
