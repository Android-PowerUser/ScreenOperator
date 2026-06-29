# App mapping overrides (remote-updatable `openApp()` resolution)

`openApp("WhatsApp")` resolves the name the model wrote to an Android package name using a
built-in list (`AppMappings.kt`) of about 50 common apps, plus a fuzzy match against whatever
is actually installed on the device. Adding a new app to that built-in list - or fixing a wrong
package name - has always required a code change and a new release.

`app-mappings-overrides.json` (this file, fetched by the WebView relative to `index.html`, same
pattern as `command-patterns.json`) lets you add new app entries or retune the fuzzy-match
threshold without a native app release. Missing/invalid/empty means "no additions, built-in
threshold (70)" - i.e. unchanged behavior.

## Format

```json
{
  "matchThreshold": 70,
  "apps": [
    {
      "canonicalName": "myneatapp",
      "packageName": "com.example.myneatapp",
      "variations": ["my neat app", "neat app"],
      "aliasesForPackageLookup": []
    }
  ]
}
```

- `matchThreshold` — integer 0-100, the minimum fuzzy-match score (Levenshtein-distance based)
  required to resolve an app name against an *installed* app's label when there's no exact
  built-in/override match. Lower = more lenient (more false positives), higher = stricter (more
  "app not found" misses). Out-of-range or omitted falls back to the built-in default of `70`.
- `apps` — additional app definitions, each merged on top of the built-ins. An entry with a
  `canonicalName` matching a built-in (e.g. `"whatsapp"`) overrides that built-in's package name
  and aliases - handy for repointing a name at a fork or rebrand.
  - `canonicalName` (required) — what `openApp(...)` matches as the "main" name, lowercase.
  - `packageName` (required) — the Android package to launch.
  - `variations` (optional) — additional free-text names the model might use (e.g. "my neat
    app"); these resolve immediately, even before the in-memory app cache is next rebuilt.
  - `aliasesForPackageLookup` (optional) — short codes/abbreviations resolved the same way as
    `canonicalName`.

Entries missing `canonicalName` or `packageName` are skipped (logged, not thrown), so one bad
entry in the array doesn't break the rest of the payload.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `app-mappings-overrides.json` and passes
   it to `Android.setAppMappingOverrides(json)`.
2. `AppMappingOverridesConfig` installs the parsed policy; the bridge persists the raw JSON via
   `AppMappingOverridesPreferences` so it's restored on the next app start, before the WebView
   has reloaded it for the new session.
3. `AppMappings.appNameVariations` / `AppMappings.manualMappings` are computed live (not cached
   once at startup) by merging the built-in list with `AppMappingOverridesConfig.current().apps`,
   so `AppNamePackageMapper.getPackageName()` picks up a new override on the very next
   `openApp(...)` call.

To add or fix an app: edit `app-mappings-overrides.json` in this repo and commit - no new app
version needed.
