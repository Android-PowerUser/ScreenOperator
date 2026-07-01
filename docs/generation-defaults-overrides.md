# Generation defaults overrides (remote-updatable factory defaults)

The app lets users tune temperature/topP/topK per model in its settings UI; that's saved via
`GenerationSettingsPreferences` and always wins once a user has touched it. This file only
controls what a model's settings look like **before** the user has ever changed them - the
factory default.

`generation-defaults-overrides.json` (this file, fetched by the WebView relative to
`index.html`, same pattern as `command-patterns.json`) lets you ship a better out-of-the-box
default for new installs/never-customized models without a native app release.

## Format

```json
{ "temperature": 0.2, "topP": 0.9, "topK": 32 }
```

- `temperature` — 0.0-2.0. Out of range is ignored (keeps the current/built-in value).
- `topP` — 0.0-1.0. Out of range is ignored.
- `topK` — integer >= 1. Less than 1 is ignored.
- Missing/invalid/empty → unchanged behavior (temperature 0, topP 0, topK 1, matching the
  original hardcoded defaults).
- Each field is independent and optional; an out-of-range or missing field just keeps its
  current value rather than invalidating the whole payload.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `generation-defaults-overrides.json` and
   passes it to `Android.setGenerationDefaultsOverrides(json)`.
2. `GenerationDefaultsConfig` installs the parsed policy; the bridge persists the raw JSON via
   `GenerationDefaultsOverridesPreferences` so it's restored on the next app start.
3. `GenerationSettingsPreferences.loadSettings()` falls back to `GenerationDefaultsConfig.current()`
   instead of a hardcoded literal whenever a model has no saved per-model value yet.

A user's own saved settings (set via the app's UI, persisted per model) are read first and
always take precedence - this file can never silently override what a user has explicitly
chosen.
