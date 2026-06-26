# Offline model overrides (remote-updatable download URL/size/extra files)

The offline (on-device) models (`GEMMA_3N_E4B_IT`, `GEMMA_4_E4B_IT`, `QWEN3_5_4B_OFFLINE` in
`GenerativeAiViewModelFactory.kt`) have a compiled-in `downloadUrl`, a human-readable `size`
label, and (for multi-file packages) a list of `additionalDownloadUrls`. These files are
hosted on third-party services (currently Hugging Face) outside this project's control — a
moved/renamed/re-quantized file previously meant patching the enum and shipping a new app
release.

`offline-model-overrides.json` (this file, fetched by the WebView relative to `index.html`)
lets you correct this download metadata for an **existing** built-in offline model without
an app release. It is optional — if the file is missing or invalid, the app silently falls
back to the compiled-in metadata only.

## Format

A JSON array of override objects:

```json
[
  {
    "id": "QWEN3_5_4B_OFFLINE",
    "downloadUrl": "https://huggingface.co/.../model_multimodal.litertlm?download=true",
    "size": "6.3 GB",
    "additionalDownloadUrls": [
      "https://huggingface.co/.../tokenizer.json?download=true"
    ]
  }
]
```

- `id` — must exactly match the name of an existing offline `ModelOption` entry. Entries
  with an unknown/non-offline `id` are skipped (logged), not an error.
- `downloadUrl`, `size`, `additionalDownloadUrls` — all optional; any field you omit keeps
  using the compiled-in default. An entry with none of these set is skipped.

## Safety boundary

An override can only replace *where to download from* and *how big the download is
displayed as* for an **already existing** offline `ModelOption`. It cannot turn an online
model into an offline one, change the on-device inference runtime, or change which
filenames the inference engine looks for on disk — `offlineModelFilename`,
`offlineRequiredFilenames`, and `offlineAlternateModelFilenames` stay fixed in compiled code
on purpose, since they're tied to download-resume and on-disk validation logic. If a new
package needs different required filenames, that still needs a code change.

## How it gets applied

1. The WebView loads `index.html` and fires `window.onAndroidReady()`.
2. That handler fetches `offline-model-overrides.json` (relative to the WebView's base URL)
   and passes its raw text to `Android.setOfflineModelOverrides(json)`.
3. The native `OfflineModelOverrides` object installs the parsed overrides and the bridge
   persists the raw JSON via `OfflineModelOverridePreferences`, so it's restored on the next
   app start (in `PhotoReasoningApplication.onCreate()`) even before the WebView reloads it.
4. The model-selection dropdown's size label, the download dialog, and
   `ModelDownloadManager`'s download/resume/cancel logic all read through this override
   before falling back to the compiled-in fields.

To fix a moved/renamed offline model download link: edit `offline-model-overrides.json` in
this repo and commit — no new app version needed.
