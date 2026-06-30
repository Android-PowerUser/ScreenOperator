# Custom action types (remote-updatable, entirely new action kinds)

`CommandParser.kt` recognises the action commands an AI model emits by matching them
against a fixed set of built-in regular expressions that map to compiled `Command`
subclasses. Adding a genuinely *new* action (not just an alternate spelling of an existing
one) has always required a native code change and a new app release — until now.

`custom-action-types.json` (fetched by the WebView relative to `index.html`, just like
`command-patterns.json`) lets you define completely new action types without a native app
release. When the command parser finds a match the native accessibility service emits a
`Command.WebViewCustomAction` and calls back into JavaScript:

```
window.onCustomAction(id, groups[])
```

The JavaScript handler can then invoke any existing `Android.*` bridge method to carry
out the actual work (click, tap, scroll, Termux command, …).

## Format

A JSON array of action type objects:

```json
[
  {
    "id": "PINCH_ZOOM",
    "regex": "(?i)\\bpinchZoom\\(\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*,\\s*([\\d.]+)\\s*\\)"
  },
  {
    "id": "SWIPE_GESTURE",
    "regex": "(?i)\\bswipe\\(\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*\\)"
  }
]
```

- `id`    — unique name passed to `window.onCustomAction` as the first argument.  
  Only used for identification; the native side logs it and passes it to JS as-is.
- `regex` — Kotlin/Java regular expression.  
  Capture groups are forwarded to `window.onCustomAction` as the `groups` array
  (`groups[0]` = first capture group, `groups[1]` = second, …).  
  Entries with an invalid regex are skipped (logged) rather than crashing the app.

## JavaScript handler

Override `window.onCustomAction` (before or after `onAndroidReady` fires) to implement
the actual behaviour:

```javascript
window.onCustomAction = function(id, groups) {
  switch (id) {
    case 'PINCH_ZOOM':
      // groups[0]=centerX, groups[1]=centerY, groups[2]=scale
      // Use existing bridge methods to implement the gesture:
      Bridge.tapAtCoordinates(groups[0], groups[1]);
      break;

    case 'SWIPE_GESTURE':
      // groups[0]=startX, groups[1]=startY, groups[2]=endX, groups[3]=endY
      // e.g. drive a scrollDown via coordinate-based bridge call
      break;

    default:
      console.warn('[ScreenOperator] Unknown custom action id:', id);
  }
};
```

The default `window.onCustomAction` (defined in `index.html`) is a no-op logger so that
existing sessions without a custom handler do not throw errors.

## Available bridge methods for implementing a custom action

Before this, the only bridge method an `onCustomAction` handler could realistically use to
*affect the device* was none at all - `showToast` (display only) was the first bridge method
added. See `docs/device-control-bridge.md` for the full, now-complete list: every native
gesture/navigation capability the AI's own built-in commands use (tap, long-tap, coordinate
tap, scroll in all four directions/forms, home/back/recent-apps, open app, write text, press
enter, run a Termux command, wait, request a screenshot, mark the task completed) is callable
from JavaScript via `Bridge.<method>(...)`, going through the exact same execution path as an
AI-emitted command.

## How it gets applied

1. The WebView loads `index.html` and fires `window.onAndroidReady()`.
2. That handler fetches `custom-action-types.json` (relative to the WebView's base URL) and
   passes its raw text to `Android.setCustomActionTypes(json)`.
3. The native `CommandParser` installs the parsed entries.  The raw JSON is also persisted
   via `CustomActionTypePreferences`, so action types are restored on the next app start
   (in `PhotoReasoningApplication.onCreate()`) even before the WebView reloads.
4. When `CommandParser.parseCommands()` matches one of the installed patterns it emits a
   `Command.WebViewCustomAction(id, groups)`.
5. `ScreenOperatorAccessibilityService.executeSingleCommand()` handles this command by
   calling `window.onCustomAction(id, groups)` back in the WebView.
6. The JS handler uses existing `Android.*` bridge methods to perform the action.

## Difference from `command-patterns.json`

| File | Purpose |
|---|---|
| `command-patterns.json` | Alternate *regexes* for **existing** built-in actions (e.g. a new model spells `click` differently). The command builder and execution logic are unchanged. |
| `custom-action-types.json` | Entirely **new** action kinds. Both the regex and the execution logic are defined in the WebView/JS layer; no native code change is needed. |

## Adding a new action type

1. Add an entry to `custom-action-types.json` (new `id` + `regex`).
2. Add (or update) the corresponding `case` in `window.onCustomAction` in a JS file loaded
   alongside `index.html`, or in `index.html` itself.
3. Commit and push — the app picks up the change on the next WebView reload, no new APK
   needed.
