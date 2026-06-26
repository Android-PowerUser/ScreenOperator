# Custom models (genuinely new models/providers via JSON, no app release)

Every model that ships with the app is a compiled-in `ModelOption` (see
`GenerativeAiViewModelFactory.kt`), and the native Kotlin networking code for each provider
(Puter, Mistral, Groq, Cerebras, ...) is part of the APK. Adding a *new provider*, or a model
whose request/response shape doesn't match an existing provider's code, normally requires a
Kotlin change and a new app release.

`custom-models.json` (this file, fetched by the WebView relative to `index.html`) lets you add
such a model anyway, with zero app release - **as long as its API is an OpenAI-compatible
chat-completions endpoint reachable via `fetch()` from the WebView (i.e. it supports CORS for
browser-style requests - verify this per provider; if it doesn't, this mechanism can't be used
for it).**

The key architectural difference from every built-in model: the actual HTTP request is made by
JavaScript, directly in the WebView (`window.onCustomModelRequest` in `index.html`), not by
native networking code. Native code only assembles the context (system message, history, user
text, images) and hands it to JS; JS calls the provider and reports the result back.

## Format

A JSON array of model definitions:

```json
[
  {
    "id": "MY_NEW_MODEL",
    "displayName": "My New Model",
    "endpoint": "https://api.example.com/v1/chat/completions",
    "modelName": "example/my-model-name",
    "apiKeyHeader": "Authorization",
    "apiKeyPrefix": "Bearer ",
    "supportsScreenshot": true,
    "supportsTopK": false,
    "stream": true
  }
]
```

- `id` (required) - any unique string. Becomes the model's identity everywhere (selection,
  per-model API key storage, ...). Must not collide with a built-in `ModelOption` name.
- `displayName` - shown in the model picker. Defaults to `id`.
- `endpoint` (required) - must be `https://`.
- `modelName` (required) - sent as the `model` field in the request body.
- `apiKeyHeader` / `apiKeyPrefix` - how the API key is attached, e.g. the defaults produce an
  `Authorization: Bearer <key>` header. Some providers use a different header (e.g. `x-api-key`
  with no prefix) - set these accordingly.
- `supportsScreenshot` - if true, the current screenshot(s) are included as
  `image_url` content parts (base64 data URIs), OpenAI vision-style.
- `supportsTopK` - whether the Top-K slider is shown for this model (not currently sent in the
  request - see Limitations).
- `stream` - whether to expect Server-Sent-Events streaming (`data: {...}` lines,
  OpenAI-style `choices[0].delta.content`) or a single JSON response
  (`choices[0].message.content`).

## Setting the API key

Custom models are not tied to the existing per-provider API key storage
(`ApiKeyManager`/`ApiProvider`), since they aren't a fixed enum. Use the bridge directly (e.g.
from a small addition to the API-key UI, or from the browser console while developing):

```js
Bridge.setCustomModelApiKey('MY_NEW_MODEL', 'sk-...');
```

The key is stored locally on-device (`CustomModelPreferences`) and is **not** part of
`custom-models.json` - never put real secrets in the repo.

## How it gets applied

1. The WebView fetches `custom-models.json` on `window.onAndroidReady()`, merges the entries
   into the model picker, and pushes the raw JSON to
   `Android.setCustomModelOverrides(json)`.
2. Selecting a custom model calls `Android.setSelectedModel(id)` as usual; since `id` isn't a
   `ModelOption`, the bridge falls back to `CustomModelRegistry` and activates it there instead.
3. On the next turn, `PhotoReasoningViewModel.reason()` sees a custom model is active and calls
   `reasonWithCustomJsModel(...)`, which builds a JSON payload (system message, db entries,
   history, user text, images, endpoint/auth config) and emits it on
   `customModelRequestEvents`.
4. `MainActivity` forwards that payload to `window.onCustomModelRequest(payloadJson)` in the
   WebView, which performs the actual `fetch()` call and streams results back via
   `Bridge.onCustomModelPartialResponse` / `onCustomModelFinalResponse` / `onCustomModelError`.
5. Those bridge calls feed into the exact same chat-bubble/command-processing/chat-history
   pipeline every other model already uses (`replaceAiMessageText`, `processCommandsIncrementally`,
   `finalizeAiMessage`, `processCommands`, `saveChatHistory`) - command syntax recognition,
   accessibility execution, and persistence all behave identically regardless of which model
   produced the text.
6. The selection (and the model list) is persisted via `CustomModelPreferences`, so it survives
   app restarts even before the WebView re-fetches `custom-models.json`.

## Generation settings (temperature / top-p / top-k)

Persisted exactly the same way as for every built-in model: `GenerationSettingsPreferences`
is keyed by an arbitrary string, not by the `ModelOption` enum, so it already worked for any
id - `WebViewBridge.getGenerationSettings`/`saveGenerationSettings` just needed to resolve a
custom model's `id` instead of requiring `ModelOption.valueOf()` to succeed. The existing
settings UI (sliders) work unchanged; `reasonWithCustomJsModel` loads the saved values and
sends them as `temperature`/`top_p` (and `top_k`, only if `supportsTopK` is true) in the
request body.

## Images

Handled the same way as for every built-in model: the current turn's screenshot(s)/attached
image(s) are JPEG-compressed and base64-encoded (same `PuterApiClient.bitmapToBase64DataUri`
helper every other model uses) and sent as OpenAI-style `image_url` content parts - only if
`supportsScreenshot` is true. Like every other model, only the *current* turn's image(s) are
sent; history messages are text-only (this matches existing app behavior, not a limitation
specific to custom models).

This required one additional native fix beyond `reasonWithCustomJsModel` itself: the
autonomous "take a screenshot after each command, then continue" loop
(`ScreenOperatorAccessibilityService.executeTakeScreenshotCommand`) decided whether to capture
a *real* screenshot or just text screen-info by checking the stale, native
`GenerativeAiViewModelFactory.getCurrentModel().supportsScreenshot` - which doesn't know about
the active custom model at all. It now checks `CustomModelRegistry.getActiveModel()` first.
Without this fix, a custom vision model would never receive real screenshots during
autonomous operation (only the very first, explicitly-sent message would include an image).

## Image-*generating* models are not supported

This only covers models that *receive* images (vision input) - not models that *produce* an
image as their response (e.g. a text-to-image / "Bildmodell" in that sense). That is not
possible today, for either custom or built-in models:

- The request shape for image generation (e.g. an `/v1/images/generations`-style endpoint)
  is fundamentally different from the chat-completions shape `window.onCustomModelRequest`
  sends - there's no equivalent of "messages in, image out".
- There is no rendering path for it either: `addModelBubble()` in `index.html` always
  HTML-escapes the model's response as plain text - there is no markdown/image rendering for
  an AI's response anywhere in the app (only user-attached images get an `<img>` thumbnail).

Adding this would need a new request/response branch in `window.onCustomModelRequest` (or a
parallel function) *and* a new way to render an image as part of a chat bubble - meaningfully
more work than a `custom-models.json` entry, not something this mechanism enables on its own.

## Safety / scope notes

- This only works for providers whose endpoint allows being called via `fetch()` from this
  page's origin (CORS). Many AI APIs are explicitly meant to be called this way (e.g. ones
  marketed for client-side/browser use); others actively block it. Test before relying on it.
- The model's API key is necessarily visible to JavaScript running in the WebView in order to
  set the auth header - this is consistent with the existing bridge (`Bridge.getAllApiKeys()`
  already exposes raw built-in-provider keys to JS for the key-management UI), not a new
  category of exposure.
- The `endpoint` URL in `custom-models.json` is where the system message, database entries,
  chat history, the current message, and (if `supportsScreenshot`) images and the API key all
  get sent. Treat changes to this file with the same care as code - a malicious or compromised
  `endpoint` value is a real data-exfiltration path, not just a "wrong model" bug.
- Adding a genuinely new *action/command kind* (not just a new model) is out of scope here -
  see `docs/command-pattern-overrides.md` for what's possible there.
