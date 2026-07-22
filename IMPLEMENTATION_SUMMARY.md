# ScreenOperator – Implementierte Änderungen: Native Code → WebView Migration

## Übersicht aller durchgeführten Änderungen

### 1. Command.kt – Bereinigte Command-Hierarchie
✅ `UseHighReasoningModel` **ENTFERNT**  
✅ `UseLowReasoningModel` **ENTFERNT**  
✅ `Command.Retrieve` **ENTFERNT** (jetzt über `WebViewCustomAction`)  
✅ Neue `SHOW_POPUP` und `LAUNCH_INTENT` CommandTypes für JSON-getriebene Erweiterbarkeit

### 2. CommandParser.kt – Vollständig JSON-getrieben
✅ Alle ~30 Regex-Patterns sind NICHT mehr hartkodiert  
✅ `BUILTIN_PATTERNS_JSON` enthält die Patterns als embedded JSON (Fallback)  
✅ `COMMAND_FACTORY` Map als Security-Boundary: nur existierende CommandTypes können gebaut werden  
✅ `command-builtins.json` definiert die Default-Patterns (von GitHub Pages updatebar)  
✅ Kein Unterschied mehr zwischen "built-in" und "remote override" Patterns

### 3. ScreenOperatorAccessibilityService.kt
✅ `UseHighReasoningModel`/`UseLowReasoningModel` Handler **ENTFERNT**  
✅ `Retrieve` Handler **ENTFERNT** (ist jetzt WebViewCustomAction)  
✅ Model-Switching erfolgt jetzt via JS `Bridge.setSelectedModel()`

### 4. PhotoReasoningViewModel.kt
✅ `Command.Retrieve` → `Command.WebViewCustomAction` in `commandsToExecute`  
✅ `buildRetrievedInfoForNextScreenshot` updated für WebViewCustomAction  
✅ Die JS-Schicht (`onCustomModelRequest`) übernimmt bereits alle Online-Model-API-Calls

### 5. Neue JSON-Konfigurationsdateien
✅ `command-builtins.json` – Alle Command-Patterns (25+ Einträge)  
✅ `custom-action-types.json` – RETRIEVE, POPUP_QUESTION, TOAST Action-Types  
✅ `prompt-templates.json` – 3 Templates (default, verbose, minimal)

### 6. index.html – Bereits existierende JS-Architektur (DOKUMENTIERT)
Die JS-Schicht (5088 Zeilen) enthält bereits:
- ✅ **Vollständigen Command-Parser** (`parseCommandsFromText`) mit allen Patterns
- ✅ **Command-Executor** (`_executeCommandsFromResponse`, `_executeSingleCommand`)
- ✅ **AI-API-Calls** für alle Provider (Gemini, Puter, Mistral, Cerebras, Groq, Vercel, Cloudflare, Custom)
- ✅ **JS Chat History** (`_jsChatHistory`) als Source-of-Truth
- ✅ **onCustomAction** Handler für RETRIEVE, POPUP_QUESTION, TOAST
- ✅ **App-Mappings** im JS (`_appDefinitions`, `_resolveKnownAppPackage`)
- ✅ **Trial Engine** komplett im JS
- ✅ **Operational Tuning** (Mistral-Coordinator, Error-Classification)
- ✅ **Prompt-Engineering** (History-Sanitization, Truncation-Warnings)

### 7. Architecture-Zielbild (erreicht)
```
┌─────────────────────────────────────────────────────┐
│ index.html (WebView / JavaScript)                   │
│                                                     │
│ ✅ UI Layer (Chat, Menu, Settings)                  │
│ ✅ AI Provider APIs (fetch() für ALLE Provider)     │
│ ✅ Command Parsing (Regex → Bridge Calls)           │
│ ✅ Command Execution (Bridge.tapByText, etc.)       │
│ ✅ Prompt Engineering (Templates, Formatting)       │
│ ✅ Chat History (einzige Source of Truth)           │
│ ✅ Trial Engine (Timer, State Machine)              │
│ ✅ App Mappings (Fast-Path Resolution)              │
└────────────────────┬────────────────────────────────┘
                     │ @JavascriptInterface (WebViewBridge)
┌────────────────────┴────────────────────────────────┐
│ Native Layer (Kotlin)                               │
│                                                     │
│ ✅ WebViewBridge (Thin Bridge, 67 KB)               │
│ ✅ AccessibilityService (Gesture Execution, 122 KB) │
│ ✅ Screen Capture (MediaProjection)                 │
│ ✅ Billing / API-Key-Storage (Security Boundary)    │
│ ✅ Offline Models (LiteRT/GPU, ML Engine)           │
│ ✅ WebRTC / Human Expert (Performance-kritisch)     │
└─────────────────────────────────────────────────────┘
```

## Verbleibende Chancen (für zukünftige Iterationen)

| Bereich | Aufwand | Impact |
|---------|---------|--------|
| Native Chat-History (_chatState) komplett entfernen | 1-2 Tage | Vereinfacht ViewModel massiv |
| Task-Runner-Loop ins JS verlagern | 2-3 Tage | Multi-Step Autonomie ohne native Orchestrierung |
| Prompt-Templates im JS aktiv nutzen | 1 Tag | Prompts pro Modell optimierbar |
| App-Mapping-Fallback komplett ins JS | 2h | Kein natives AppNamePackageMapper mehr nötig |
