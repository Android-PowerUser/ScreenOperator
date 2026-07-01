# Model identifier overrides (remote-updatable wire-level model names)

Every built-in model (`ModelOption` in `GenerativeAiViewModelFactory.kt`) has a compiled-in
`modelName` string that gets sent to the provider's API (Gemini's official SDK, or directly
in a Mistral/Groq/Puter/Cerebras request body). Provider-side model identifiers occasionally
change or get retired out from under the app — this is most common with Gemini preview
models (see the README: "Preview models will eventually be removed by Google and
unfortunately won't be redirected to finished equivalents. If this happens, please change the
API in the code."). Until now, fixing that always meant patching the enum and shipping a new
app release.

`model-identifier-overrides.json` (this file, fetched by the WebView relative to
`index.html`) lets you correct the identifier for an **existing** built-in model without an
app release. It is optional — if the file is missing or invalid, the app silently falls back
to the compiled-in identifiers only.

## Format

A JSON array of override objects:

```json
[
  { "id": "GEMINI_FLASH_LITE_PREVIEW", "modelName": "gemini-2.5-flash-lite" }
]
```

- `id` — must exactly match the name of an existing `ModelOption` enum entry (e.g.
  `GEMINI_FLASH_LITE_PREVIEW`, `MISTRAL_LARGE_3`, `GROQ_LLAMA_4_SCOUT_17B`, ...). Entries
  with an unknown `id` are skipped (logged), not an error.
- `modelName` — the replacement identifier string to send to the provider instead of the
  compiled-in one.

## Safety boundary

An override can only replace the identifier string sent for an **already existing**
`ModelOption`, and only at the specific call sites that were deliberately wired up to
consult it. It can never add a new provider, change which endpoint/SDK/code path handles
the request, change API-key/billing handling, or introduce any new capability — it only
swaps which model name is requested from the same, already-reviewed call site. To add a
genuinely new model/provider, use `custom-models.json` instead (see
`docs/custom-models.md`).

## Coverage

Applied wherever an existing built-in model's identifier is placed into an outgoing
request: the Gemini SDK calls in `GenerativeAiViewModelFactory.kt` and
`PhotoReasoningViewModel.reasonInRegularMode()` (this is what fixes a renamed/retired Gemini
preview model), the Live API model name, and the Mistral/Puter/Cerebras/Groq request bodies
in `PhotoReasoningViewModel.kt` and `ScreenCaptureApiClients.kt` (the latter is used by the
background autonomous-continuation service). It does **not** currently cover Cloudflare or
Vercel-routed models — no single, unambiguous request-building call site for those was found
when this mechanism was added; if you rely on overriding those, please open an issue.

Internal bookkeeping (per-model generation-settings keys, the displayed model name) keeps
using the original, compiled-in identifier even when an override is active, so existing
saved settings aren't affected by applying or removing an override.

## How it gets applied

1. The WebView loads `index.html` and fires `window.onAndroidReady()`.
2. That handler fetches `model-identifier-overrides.json` (relative to the WebView's base
   URL) and passes its raw text to `Android.setModelIdentifierOverrides(json)`.
3. The native `ModelIdentifierOverrides` object installs the parsed overrides and the
   bridge persists the raw JSON via `ModelIdentifierOverridePreferences`, so it's restored
   on the next app start (in `PhotoReasoningApplication.onCreate()`) even before the
   WebView reloads it.

To fix a broken/renamed built-in model identifier: edit `model-identifier-overrides.json`
in this repo and commit — no new app version needed.
