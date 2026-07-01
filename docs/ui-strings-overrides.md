# UI string overrides (remote-updatable native Compose-screen text)

The app has two kinds of UI: the WebView's own menu/settings screen (`index.html` - already
fully self-contained and editable directly, nothing extra needed there), and Android's native
Jetpack Compose screens (the main chat screen, menus, dialogs, toasts) which run in a different
render engine `index.html` cannot reach directly.

`ui-strings-overrides.json` (this file, fetched by the WebView relative to `index.html`, same
pattern as `command-patterns.json`) lets you override individual native-screen strings by a
stable ID, without a native app release.

## Where the canonical list lives

`index.html` defines `DEFAULT_UI_STRINGS`, right next to `DEFAULT_SYSTEM_MSG` - a plain JS
object listing every recognized string ID and its current default English text, so the full
list lives in one place you can read and edit without needing to know Kotlin or dig through the
native source. **Editing a value there is documentation, not the live default** - the actual
fallback text the app uses is the literal hardcoded at each Kotlin call site
(`UiStringsConfig.get("some_id", "Some Text")`), so the two need to be kept in sync by hand if
you ever change a default (see "Keeping this in sync" below). Overriding it for real is done
through this `ui-strings-overrides.json` file, fetched and applied the same way every other
override in this app is.

## Format

A flat `{"id": "replacement text", ...}` map - only the IDs you want to change need to be
present; everything else keeps its built-in default:

```json
{
  "toast_model_already_downloaded": "Dieses Modell ist bereits heruntergeladen.",
  "chat_stop_button": "Stopp"
}
```

Some strings carry positional placeholders (`{0}`, `{1}`, ...) for dynamic content (an error
message, a file name, etc.) - e.g. `toast_file_share_error: "Error sharing file: {0}"`. Keep the
placeholder in your replacement text if you want that dynamic value to still appear:
`"Datei konnte nicht geteilt werden: {0}"`.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `ui-strings-overrides.json` and passes it
   to `Android.setUiStringsOverrides(json)`.
2. `UiStringsConfig` installs the parsed `{id: text}` map; the bridge persists the raw JSON via
   `UiStringsOverridesPreferences` so it's restored on the next app start.
3. Each call site reads `UiStringsConfig.get("some_id", "Some Text")` (or, for strings with
   dynamic content, `UiStringsConfig.get("some_id", "Some Text: {0}", dynamicValue)`) instead of
   a bare string literal. If `some_id` has no override installed, this returns the literal
   default exactly as before - so even with the override file totally absent, behavior is
   unchanged.

## Keeping this in sync

If you ever change one of these strings' *default* text in the Kotlin source (not via an
override - an actual code change), update the matching entry in `DEFAULT_UI_STRINGS` in
`index.html` too, so the documentation/editing-reference list shown there doesn't go stale. This
mirrors the trade-off `DEFAULT_SYSTEM_MSG` already makes: nothing breaks if you forget (each
call site's literal is still the real source of truth, regardless of what's in
`DEFAULT_UI_STRINGS`), but the comfort of having one editable list of everything is lost if it
drifts.
