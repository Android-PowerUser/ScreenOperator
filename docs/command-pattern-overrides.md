# Command pattern overrides (remote-updatable command syntax)

`CommandParser.kt` recognizes the action commands an AI model emits (`click("...")`,
`tapAtCoordinates(x, y)`, `scrollDown()`, ...) using a fixed set of built-in regular
expressions. Until now, supporting a new model that phrases an existing action slightly
differently (e.g. `Click('...')` instead of `click("...")`) required patching
`CommandParser.kt` and shipping a new app release.

`command-patterns.json` (this file, fetched by the WebView relative to `index.html`) lets
you add such alternate spellings without an app release. It is optional — if the file is
missing or invalid, the app silently falls back to the built-in patterns only.

## Format

A JSON array of override objects:

```json
[
  {
    "id": "clickBtnCapitalized",
    "commandType": "CLICK_BUTTON",
    "regex": "(?i)\\bClick\\([\"']([^\"']+)[\"']"
  }
]
```

- `id` — any string, used only for logging.
- `commandType` — must be one of the values below. Unknown values are skipped (logged),
  not an error.
- `regex` — a Kotlin/Java regular expression. **It must capture the same groups, in the
  same order, as the built-in pattern for that `commandType`** (see `CommandParser.kt`),
  since the existing, compiled-in builder function reads `match.groupValues[...]` to
  construct the command. If the group count doesn't match, that particular match is
  skipped (logged), nothing crashes.

## Safety boundary

An override can only attach a new regex to an **existing** `commandType` — it can never
introduce a new kind of action and can never run custom code. What each action is allowed
to do on the device (tap, scroll, open an app, run a Termux command, ...) is always
decided by the same native, compiled-in code; this mechanism only changes which *text*
triggers that pre-existing action. Adding a genuinely new action still requires a native
code change.

## Available `commandType` values

`CLICK_BUTTON`, `LONG_CLICK_BUTTON`, `TAP_COORDINATES`, `TAKE_SCREENSHOT`, `COMPLETED`,
`WAIT`, `PRESS_HOME`, `PRESS_BACK`, `SHOW_RECENT_APPS`, `SCROLL_DOWN`, `SCROLL_UP`,
`SCROLL_LEFT`, `SCROLL_RIGHT`, `SCROLL_DOWN_FROM_COORDINATES`, `SCROLL_UP_FROM_COORDINATES`,
`SCROLL_LEFT_FROM_COORDINATES`, `SCROLL_RIGHT_FROM_COORDINATES`, `OPEN_APP`, `WRITE_TEXT`,
`USE_HIGH_REASONING_MODEL`, `USE_LOW_REASONING_MODEL`, `PRESS_ENTER_KEY`, `RETRIEVE`,
`TERMUX_COMMAND`.

## How it gets applied

1. The WebView loads `index.html` and fires `window.onAndroidReady()`.
2. That handler fetches `command-patterns.json` (relative to the WebView's base URL) and
   passes its raw text to `Android.setCommandPatternOverrides(json)`.
3. The native `CommandParser` installs the parsed overrides and the bridge persists the
   raw JSON via `CommandPatternOverridesPreferences`, so it's restored on the next app
   start (in `PhotoReasoningApplication.onCreate()`) even before the WebView reloads it.

To add support for a new model's command syntax: edit `command-patterns.json` in this
repo and commit — no new app version needed.
