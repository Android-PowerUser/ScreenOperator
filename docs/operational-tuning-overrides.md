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
  "termuxProcessCompletedPrompt": "[Process completed - press Enter]",
  "retrievalHeaderPrefix": "Retrieved information ["
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
- `retrievalHeaderPrefix` — the prefix the app uses both to *write* a "tool result" block into
  the prompt (e.g. retrieved file contents) and to later *recognize* in chat history that a
  given piece of information has already been retrieved (so it isn't fetched/inserted twice).
  Both the writer and the reader (`PhotoReasoningTextPolicies.formatRetrievalResultForPrompt` /
  `isHeadingAlreadyRetrievedInChat`) read this same live value, so changing it never desyncs
  the two - this marker is purely internal app bookkeeping, the AI model never needs to
  recognize or reproduce it itself.

## A marker that is deliberately *not* here: "Screen elements:"

The app also has a `"Screen elements:"` marker (`ScreenOperatorAccessibilityService.kt` writes
it, `PhotoReasoningScreenElementHistoryPolicy.kt` reads it). That one is **not** remote-
configurable, on purpose, and for a different reason than the billing logic: unlike
`retrievalHeaderPrefix`, this string isn't purely internal - the AI model itself is expected
(presumably via the system prompt) to recognize and continue using exactly this label when
referring to on-screen elements in its own responses. Making it remote-configurable would mean
the native "what does a screen-elements section look like" pattern and the model's own
prompt-driven expectation of that pattern could silently drift apart - the model would keep
writing `"Screen elements:"` while the app, under a stale override, looked for something else.
That's a quieter, harder-to-debug failure than most other overrides in this app, since nothing
would error - the screen-element history trimming would simply stop firing. If you need to
change this string, do it as a coordinated native code + system prompt change instead.

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
