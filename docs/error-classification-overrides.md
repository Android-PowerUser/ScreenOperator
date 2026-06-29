# Error classification overrides (remote-updatable AI-provider error matching)

When an AI call fails, the app decides what to do next by matching substrings against the raw
error message text from the provider:

- **Quota/rate-limit error** (e.g. HTTP 429) → mark the current API key as failed, switch to
  the next configured key, and retry automatically (up to a small retry cap).
- **High-demand/overloaded error** (e.g. HTTP 503) → do *not* switch keys (the problem isn't
  the key), just tell the user the model is busy.

That wording is controlled by the AI provider (Google, OpenAI, ...), not by this app - and
providers do change their error message text over time. Previously, a wording change would
silently break this classification until the next app release. `error-classification-
overrides.json` (this file, fetched by the WebView relative to `index.html`, same pattern as
`command-patterns.json`) lets you add or replace the substring lists without one.

## Format

```json
{
  "quotaExceededSubstrings": ["exceeded your current quota", "code 429", "too many requests", "rate_limit"],
  "highDemandSubstrings": ["service unavailable (503)", "unavailable", "high demand", "overloaded"]
}
```

- Both fields are optional string arrays. Matching is **case-insensitive** for every entry
  (built-in and override alike).
- Omitting a field keeps that category's built-in defaults (shown above).
- An explicit empty array (`[]`) is honored as-is - i.e. you can deliberately disable a
  category (every message will be treated as "neither") rather than only being able to add to
  it.
- Providing the field replaces the built-in list for that category entirely (it doesn't merge
  with the built-ins) - include the original substrings too if you just want to *add* one.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `error-classification-overrides.json` and
   passes it to `Android.setErrorClassificationOverrides(json)`.
2. `ErrorClassificationConfig` installs the parsed policy; the bridge persists the raw JSON via
   `ErrorClassificationOverridesPreferences` so it's restored on the next app start.
3. `PhotoReasoningTextPolicies.isQuotaExceededError()` / `isHighDemandError()` - called right
   after any AI call fails - delegate to `ErrorClassificationConfig.current()`.

To fix a stale match: edit `error-classification-overrides.json` in this repo and commit - no
new app version needed.
