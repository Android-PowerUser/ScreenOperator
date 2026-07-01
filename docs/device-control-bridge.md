# Device control bridge methods (every native gesture/navigation capability, exposed to JS)

## The gap this closes

Every accessibility-service capability the app has (tap, scroll, navigate, open an app, type
text, run a Termux command, ...) already existed natively, but only as something the AI's own
command text could trigger via `CommandParser` → `Command.*` → `ScreenOperatorAccessibilityService
.executeSingleCommand()`. None of it was reachable from JavaScript - a `custom-action-types.json`
handler could define a brand-new AI-facing command, but had no `Android.*` bridge method to
actually *do* anything with it beyond `showToast` (display only). The example in
`docs/custom-action-types.md` even referenced `Android.tapAtCoordinates(...)` as if it already
existed - it didn't; that was an aspirational example, now made real by this change.

## How it's implemented

`ScreenOperatorAccessibilityService` already has a public, static entry point:
```kotlin
ScreenOperatorAccessibilityService.executeCommand(command: Command)
```
This is the exact same function that processes a command the AI wrote. Every new bridge method
below just constructs the matching `Command` and hands it to that function - none of the actual
gesture/geometry/safety logic is reimplemented in `WebViewBridge.kt`. Any future change to that
shared execution path automatically applies to both AI-emitted and JS-triggered commands.

One difference worth knowing: the per-message command limit from `ExecutionPolicyConfig`
(`docs/execution-policy-overrides.md`) only applies to commands parsed from the AI's response
text. A `window.onCustomAction` handler calling these bridge methods directly - e.g. in a loop -
is not subject to that cap, since it isn't going through `CommandParser` at all.

## Full method list

All available as `Bridge.<method>(...)` in `index.html`, calling the matching `Android.<method>`:

| Bridge method | Mirrors AI command |
|---|---|
| `tapByText(text)` | `click("text")` |
| `longTapByText(text)` | `longClick("text")` |
| `tapAtCoordinates(x, y)` | `tapAtCoordinates(x, y)` |
| `pressHome()` | `home()` |
| `pressBack()` | `back()` |
| `showRecentApps()` | `recentApps()` |
| `pressEnterKey()` | `Enter()` |
| `writeText(text)` | `writeText("text")` |
| `scrollDown()` / `scrollUp()` / `scrollLeft()` / `scrollRight()` | `scrollDown()` etc. |
| `scrollDownFromCoordinates(x, y, distance, durationMs)` (+ Up/Left/Right) | `scrollDown(x, y, distance, duration)` etc. |
| `openAppByNameOrPackage(appNameOrPackage)` | `openApp("name")` |
| `runTermuxCommand(command)` | `Termux("command")` |
| `waitSeconds(seconds)` | `Wait(seconds)` |
| `requestScreenshot()` | `takeScreenshot()` |
| `markCompleted()` | `completed()` |
| `pinchGesture(centerX, centerY, startDistance, endDistance, durationMs)` | `pinch(centerX, centerY, startDistance, endDistance, durationMs)` |
| `copyToClipboard(text)` | `copyToClipboard("text")` |
| `getClipboardText()` → `string` | *(no AI-command equivalent - read-only helper, see below)* |

`x`/`y`/`distance` accept the same formats the AI's commands do (pixels, or a percentage string
like `"50%"`) - they're passed straight through to the same geometry resolver.

## Clipboard: a permission-free action, both write and read

`copyToClipboard(text)` is the first of a broader category worth calling out explicitly:
capabilities that need **no additional Android permission** (clipboard access is granted to
every app by default, unlike e.g. contacts or location). These are good candidates to keep
adding here and to `custom-action-types.json`-driven AI commands, since they carry none of the
runtime-permission-prompt friction other device features do.

- `Bridge.copyToClipboard(text)` / AI command `copyToClipboard("text")` writes `text` to the
  system clipboard. It goes through `Command.CopyToClipboard` → `executeCommand()`, exactly like
  every other entry in the table above, so it is queued and logged the same way.
- `Bridge.getClipboardText()` reads the current clipboard content back into JS and returns it
  directly (a plain `string`, not a `Command`) - clipboard reads are synchronous by nature and
  the queued `executeCommand()` pipeline has no return channel back to JS, so this one talks to
  `ClipboardManager` directly instead. Note Android 10+ restricts clipboard reads to the
  foreground/default-IME app in some cases; if the app isn't focused this may return `""`
  instead of throwing.

Any other no-new-permission action (e.g. reading installed-app labels, toggling flashlight-free
settings, etc.) should follow the same split: a write/trigger action goes through
`Command.*` + `executeCommand()` like the table above; a value that must be returned
synchronously to JS is read directly in `WebViewBridge.kt`, same as `copyToClipboard` /
`getClipboardText()` and the existing `getSystemMessage()` / `getSelectedModelId()` methods.

## What's deliberately not exposed this way

- **`Retrieve` (the AI's database-retrieval mechanism)** isn't a device/accessibility action at
  all - it's handled during prompt construction in `PhotoReasoningViewModel`, reading from the
  in-app guide database. It doesn't fit the "construct a `Command`, call `executeCommand`"
  pattern these methods use, and exposing database retrieval to an arbitrary JS handler is a
  different kind of capability than screen control. Not covered here.
- **`UseHighReasoningModel` / `UseLowReasoningModel`** (the AI's model-switching commands) are
  redundant with the already-existing `setSelectedModel(id)` bridge method, which is more
  general (any model, not just two hardcoded ones) - use that instead.
- **`WebViewCustomAction` itself** obviously isn't exposed back to JS - that would be circular.

## Example: a custom "double tap" action type

```json
// custom-action-types.json
[
  { "id": "DOUBLE_TAP", "regex": "(?i)\\bdoubleTap\\(\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*\\)" }
]
```

```javascript
// index.html or a loaded script
window.onCustomAction = function(id, groups) {
  if (id === 'DOUBLE_TAP') {
    Bridge.tapAtCoordinates(groups[0], groups[1]);
    setTimeout(() => Bridge.tapAtCoordinates(groups[0], groups[1]), 150);
  }
};
```

Remember to also mention the new command in the system prompt (`DEFAULT_SYSTEM_MSG` in
`index.html`, or your own saved system message) - the AI only uses commands it's told about.
