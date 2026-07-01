# The `toast("message")` AI command (a worked example)

This documents a concrete instance of the `custom-action-types.json` mechanism
(`docs/custom-action-types.md`): a brand-new command, `toast("message")`, that an AI model can
emit to show the user a short on-screen message - implemented entirely through the existing
WebView/JSON override system, no native app release required for the command itself.

It ships **enabled by default** in this repo (see "What's already wired up" below) - if you
never touch the related files, the AI can already use `toast("...")` today.

## What's already wired up

1. **`custom-action-types.json`** (repo root) contains:
   ```json
   [
     {
       "id": "TOAST",
       "regex": "(?i)\\btoast\\(\\s*[\"']([^\"']+)[\"']\\s*\\)"
     }
   ]
   ```
   This tells the native command parser to recognize `toast("...")` (or `toast('...')`, case-
   insensitive) and forward the captured message to the WebView.

2. **`index.html`'s default `window.onCustomAction` handler** has a built-in `case` for the
   `TOAST` id that calls `Bridge.showToast(groups[0], false)`.

3. **`Bridge.showToast(message, isLong)`** (in `index.html`'s `Bridge` object) calls the new
   native bridge method `Android.showToast(message, isLong)`.

4. **`WebViewBridge.showToast(message: String, isLong: Boolean)`** (new native bridge method)
   shows a real Android `Toast`, dispatched onto the UI thread via `runOnUiThread` (required -
   `Toast.makeText(...).show()` must run on the main thread, and JS bridge calls arrive on a
   WebView-internal thread). The message is truncated to 500 characters and a blank message is
   silently ignored, so a malformed or oversized AI-emitted message can't crash or spam the UI.

5. **The default system prompt** (`DEFAULT_SYSTEM_MSG` in `index.html`) now mentions
   `toast("message")` in its command list, so the AI model actually knows the command exists.
   If you've saved a custom system message that predates this, add a line like *"To show the
   user a brief on-screen message, use `toast("message")`."* to it yourself - existing saved
   system messages are never silently modified.

## End-to-end flow

```
AI response contains: toast("Found the settings menu")
        │
        ▼
CommandParser matches the TOAST regex (custom-action-types.json)
        │
        ▼
Command.WebViewCustomAction("TOAST", ["Found the settings menu"])
        │
        ▼
ScreenOperatorAccessibilityService calls window.onCustomAction("TOAST", [...])
        │
        ▼
index.html's onCustomAction handler calls Bridge.showToast(message, false)
        │
        ▼
Android.showToast(message, false)  (WebViewBridge.kt, new)
        │
        ▼
Toast.makeText(context, message, LENGTH_SHORT).show()  (on the UI thread)
```

## Customizing it

- **Disable it**: remove the `TOAST` entry from `custom-action-types.json` (or replace the file
  with `[]`) and remove the `toast(...)` line from the system prompt.
- **Change the syntax**: edit the `regex` field - e.g. to also accept `showMessage("...")`.
- **Make toasts long by default**: change the `false` in `Bridge.showToast(groups[0] || '', false)`
  (in `index.html`) to `true`, or extend the regex/handler to let the AI choose
  (`toast("message", "long")`) and pass that through as the second argument.
- **Add more built-in custom actions this way**: this is a template - any new AI command that
  can be implemented by calling existing `Android.*` bridge methods can be added the same way,
  entirely through `custom-action-types.json` + a `window.onCustomAction` case, without a native
  release. A command needing genuinely new native capability (e.g. a new kind of gesture)
  still needs an actual new bridge method like `showToast` was here - see
  `docs/custom-action-types.md`'s "Difference from `command-patterns.json`" section.
