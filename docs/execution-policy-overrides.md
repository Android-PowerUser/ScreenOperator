# Execution policy overrides (remote-updatable per-message command limit)

When an AI model writes several commands in a single response (e.g. `click("Settings")`
`scrollDown()` `tapAtCoordinates(50%, 80%)` all in one message), the app has always executed
*all* of them before taking the next screenshot. A model that sends too many actions at once
without checking the screen in between tends to act on stale assumptions about what's on
screen and go off the rails.

`execution-policy-overrides.json` (this file, fetched by the WebView relative to `index.html`,
just like `command-patterns.json`) lets you cap how many commands from a single response are
actually executed, and customize the feedback text that's sent back to the model - together
with the next screenshot and its screen elements - explaining that the rest were dropped. It
is optional and opt-in: if the file is missing, invalid, or doesn't set
`maxCommandsPerMessage`, the app falls back to the original, unlimited behavior.

## Format

A JSON object (not an array, unlike the other override files):

```json
{
  "maxCommandsPerMessage": 2,
  "truncationFeedbackTemplate": "Note: this response contained {total} commands, but only the first {executed} were executed because more than {limit} commands were sent in a single message without an intermediate screenshot. Please send at most {limit} commands per message, then wait for the next screenshot before continuing."
}
```

- `maxCommandsPerMessage` — integer. `0`, a negative number, or omitting the field means
  *unlimited* (the original behavior). Any positive value caps execution to that many
  commands, counted in the order the model wrote them in that single response.
- `truncationFeedbackTemplate` — optional string sent back to the model (prepended to the
  next screenshot's screen-elements message) whenever truncation actually happened. Supports
  three placeholders, replaced before sending:
  - `{total}` — how many commands the model sent in that response.
  - `{executed}` — how many of them were actually executed (i.e. `maxCommandsPerMessage`,
    when truncation happened).
  - `{limit}` — the configured `maxCommandsPerMessage` value.

  If omitted, a sensible English default is used (see `ExecutionPolicyConfig.kt`). You can set
  this to any wording/language you like, e.g. to match your system prompt's language.

## What counts toward the limit

Every command the parser recognizes in the response counts, in the order it appears in the
text — including `completed()` and `takeScreenshot()` if the model happens to write them
early. If `completed()` itself gets cut off by the limit, the task is **not** marked completed
that turn (the model has to send it again once it's actually done sending fewer commands per
message); a fresh screenshot is still taken either way so the model gets a clean view of the
current screen plus the truncation note.

## How it gets applied

1. The WebView loads `index.html` and fires `window.onAndroidReady()`.
2. That handler fetches `execution-policy-overrides.json` (relative to the WebView's base
   URL) and passes its raw text to `Android.setExecutionPolicyOverrides(json)`.
3. The native `ExecutionPolicyConfig` installs the parsed policy and the bridge persists the
   raw JSON via `ExecutionPolicyOverridesPreferences`, so it's restored on the next app start
   (in `PhotoReasoningApplication.onCreate()`) even before the WebView reloads it.
4. The limit is enforced in two places so it also applies to commands executed incrementally
   while the model's response is still streaming in, not just after it finishes:
   - `PhotoReasoningViewModel.processCommandsIncrementally()` (live, during streaming)
   - `PhotoReasoningViewModel.processCommands()` (final pass after streaming ends)
   Both consult the same `ExecutionPolicyConfig.current()` value, via the small, unit-tested
   `CommandExecutionLimiter` helper.
5. If truncation happened, the formatted feedback text is merged into
   `pendingRetrievedInfoForNextScreenshot`, which is what gets prepended to the screen-elements
   text sent along with the very next screenshot.

To change the limit or the wording: edit `execution-policy-overrides.json` in this repo and
commit — no new app version needed.
