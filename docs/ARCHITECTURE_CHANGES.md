# Architektur-Änderungen: WebView-gesteuerte Modell-Definitionen

## Problem
`setSelectedModel(id)` in `WebViewBridge.kt` rief `ModelOption.valueOf(id)` auf. Da `PUTER_LING_3_FLASH` nicht in der nativen ModelOption-Enum existierte, flog eine `IllegalArgumentException` - die Auswahl wurde weder gespeichert noch aktiviert. Beim nächsten Laden blieb das vorherige Modell aktiv.

## Lösung
Die Architektur wurde so umgebaut, dass das Hinzufügen neuer Modelle **niemals native Codeänderungen** erfordert, sondern nur WebView-Änderungen (custom-models.json).

## Änderungen

### 1. WebViewBridge.kt
**`setSelectedModel(id)`**: Prüft jetzt **zuerst** CustomModelRegistry (JSON-definierte Modelle), dann fällt auf die Enum zurück.
```kotlin
// Custom models (JSON-defined) ALWAYS take precedence
val customModel = CustomModelRegistry.findById(id)
if (customModel != null) {
    // Activate custom model
    return
}
// Fallback: try built-in ModelOption enum (legacy path)
try {
    val model = ModelOption.valueOf(id)
    // ...
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "unknown model id '$id'")
}
```

**`getGenerationSettings()` / `saveGenerationSettings()`**: Gleiche Priorität - CustomModelRegistry zuerst.

### 2. GenerativeAiViewModelFactory.kt
**`loadModelPreference()`**: Stellt beim Start persistierte Custom-Model-IDs wieder her.

**`PUTER_LING_3_FLASH` entfernt**: Das Modell existiert nicht mehr in der Enum, sondern nur noch in `custom-models.json`.

### 3. CustomModelConfig.kt
**`apiProvider`-Feld hinzugefügt**: Optional. Wenn gesetzt, nutzt das Modell einen nativen API-Client statt JS fetch(). (Wird aktuell nicht benötigt, da alle Online-Modelle über JS geroutet werden, aber für zukünftige Erweiterungen.)

**Validierung angepasst**: `endpoint` ist nur noch erforderlich, wenn `apiProvider` nicht gesetzt ist.

### 4. ScreenCaptureApiClients.kt
**`supportsScreenshot`-Lookup**: Prüft jetzt CustomModelRegistry **vor** der Enum.
```kotlin
val supportsScreenshot = CustomModelRegistry.getModels()
    .find { it.modelName == modelName }?.supportsScreenshot
    ?: currentModelOption?.supportsScreenshot
    ?: true
```

### 5. custom-models.json
**`PUTER_LING_3_FLASH` hinzugefügt**:
```json
{
  "id": "PUTER_LING_3_FLASH",
  "displayName": "Ling 3.0 Flash (Puter)",
  "endpoint": "https://api.puter.com/v1/chat/completions",
  "modelName": "inclusionai/ling-3.0-flash",
  "supportsScreenshot": false,
  "supportsTopK": false,
  "stream": true
}
```

## Neue Architektur

### Priorität bei Modell-Auswahl
1. **CustomModelRegistry** (JSON-definierte Modelle aus WebView) - **HÖCHSTE PRIORITÄT**
2. **ModelOption Enum** (kompilierte Modelle) - Fallback für Legacy

### Modell-Typen
1. **Offline-Modelle** (Gemma, Qwen offline): Native LiteRT
2. **Live-Modelle** (Gemini Live): Native LiveApiManager
3. **Alle anderen Online-Modelle**: JavaScript (WebView)
   - Built-in (Enum): `reasonWithBuiltInModelViaJs()`
   - Custom (JSON): `reasonWithCustomJsModel()`

### Vorteile
✅ **Keine nativen Codeänderungen** für neue Modelle  
✅ **WebView ist Single Source of Truth** für Modell-Definitionen  
✅ **Sofortige Updates** ohne App-Release möglich  
✅ **Abwärtskompatibel**: Bestehende Enum-Modelle funktionieren weiterhin  

## Beispiel: Neues Modell hinzufügen

### Vorher (alte Architektur)
1. `PUTER_LING_3_FLASH` zur Enum in `GenerativeAiViewModelFactory.kt` hinzufügen
2. Native Codeänderung → App-Release erforderlich

### Nachher (neue Architektur)
1. Eintrag zu `custom-models.json` hinzufügen:
```json
{
  "id": "NEUES_MODELL",
  "displayName": "Neues Modell (Provider)",
  "endpoint": "https://api.provider.com/v1/chat/completions",
  "modelName": "provider/model-name",
  "supportsScreenshot": true,
  "supportsTopK": false,
  "stream": true
}
```
2. **Fertig!** Keine nativen Codeänderungen, kein App-Release.

## Testing
- ✅ `PUTER_LING_3_FLASH` aus Enum entfernt
- ✅ `PUTER_LING_3_FLASH` zu `custom-models.json` hinzugefügt
- ✅ `setSelectedModel()` prüft CustomModelRegistry zuerst
- ✅ `supportsScreenshot`-Lookup prüft CustomModelRegistry zuerst
- ✅ `loadModelPreference()` stellt Custom-Model-IDs wieder her

## Hinweise
- Die nativen API-Clients (`callPuterApi`, `callMistralApi`, `callGroqApi`) werden aktuell **nicht mehr verwendet** für Online-Modelle (alles geht über JS)
- Die Änderungen dort sind **defensive Maßnahmen** für zukünftige Erweiterungen
- Das `apiProvider`-Feld in `CustomModelDefinition` ist vorbereitet für zukünftige native Routing-Szenarien
