# ScreenOperator – Sicherheitskritische Native-Teile: Analyse

## Was MUSS nativ bleiben – und warum

Jeder Punkt unten hat eine konkrete Bedrohung. Die Frage ist nicht "könnte man es ins WebView schieben?" sondern **"welcher Angriffsvektor würde sich öffnen?"**

---

## 1. Google Play Billing – die monetäre Grenze

**Wo:** `MainActivity.kt` Zeilen 1019–1150, `TrialManager.kt`

**Was passiert hier:**
- `BillingClient` (Google Play Billing Library) authentifiziert Käufe kryptografisch
- `PurchasesUpdatedListener` empfängt signierte Purchase-Tokens direkt von Google Play
- `handlePurchase()` verifiziert die Signatur und ruft `TrialManager.markAsPurchased()` auf
- `queryPurchasesAsync()` prüft bei jedem App-Start, ob das Abo noch aktiv ist

**Warum nativ:**
```
Angriffsvektor: WebView-JavaScript kann fetch() beliebig fälschen

Wenn Purchase-Verifikation im WebView läge:
1. JS könnte `Bridge.isPurchased = () => true` monkey-patchen
2. Ein `onTrialStateChanged(false, true, '')` Aufruf würde den Paywall unwiderruflich deaktivieren  
3. `TrialManager.markAsPurchased()` würde nie aufgerufen – der Kauf wäre nur JS-seitig "gekauft"

ABER: Selbst im aktuellen Setup ruft `TrialManager.markAsPurchased()` NICHT direkt
die JS-seitige Trial-Engine auf, sondern schreibt nach SharedPreferences. Dann ruft
MainActivity.updateTrialState() die JS-Funktion window.onTrialStateChanged() auf –
das ist ein ONE-WAY-Sync (nativ→JS), KEIN Zwei-Wege-Vertrauen.
```

**Sicherheitsgarantie:** `TrialManager.markAsPurchased(context)` wird **ausschließlich** von `PurchasesUpdatedListener` aufgerufen, der wiederum nur von Google Play Billing getriggert wird. Das WebView kann diesen Pfad nicht initiieren. `TrialManager.isPurchased()` liest direkt aus SharedPreferences — das JS kann nur lesen, nicht schreiben.

---

## 2. API-Key-Storage – Credential-Schutz

**Wo:** `ApiKeyManager.kt`

**Was passiert hier:**
- API-Keys werden in `SharedPreferences` (`api_key_prefs`, MODE_PRIVATE) gespeichert
- Zugriff via `@JavascriptInterface` (`getAllApiKeys`, `addApiKey`, `removeApiKey`)
- Die Keys sind im Klartext in SharedPreferences (KEINE Verschlüsselung!)

**Warum nativ (und das aktuelle Problem):**
```
Die Keys sind aktuell ZWAR über die Bridge im JS lesbar – das IST eine Schwachstelle.

WARUM das trotzdem nicht "komplett ins WebView" kann:
- SharedPreferences mit MODE_PRIVATE sind App-lokal, nicht via WebView-Dateizugriff erreichbar
- Ein `fetch()` aus dem WebView zu einem Drittanbieter MIT Key ist okay (das macht der JS-Code bereits)
- Aber: das SPEICHERN der Keys muss in einem Bereich liegen, den nur die App selbst sehen kann

Warum das aktuelle Setup trotz Bridge-Zugriff "akzeptabel" ist:
1. Das WebView lädt index.html von GitHub Pages – ein Supply-Chain-Angriff auf das Repo
   wäre nötig, um JS einzuschleusen, das Keys exfiltriert
2. Der Angreifer müsste das gesamte GitHub-Repo übernehmen (der User, nicht Arena/wir)
3. Es gibt kein `eval()` oder dynamisches Script-Loading im WebView
4. Die CSP (Content Security Policy) ist... nicht vorhanden. Das IST ein Risiko.

EMPFEHLUNG: API-Key-Storage verschlüsseln (Android Keystore) und Bridge-Methoden
so umbauen, dass das JS NUR `getApiKeyMasked(provider)` bekommt (erste 4 + letzte 4 Zeichen),
niemals den vollen Key. Der tatsächliche API-Call muss dann nativ erfolgen, oder der Key
wird nur temporär für den fetch() bereitgestellt.
```

---

## 3. Trial-Timing & Obfuscation – Manipulationsschutz

**Wo:** `TrialManager.kt`

**Was passiert hier:**
- Trial-Endzeit wird XOR-verschlüsselt in SharedPreferences gespeichert
- Schlüssel sind getarnt (`KEY_CFG_TS` = `"cfg_ts_val"`, Dateiname = `"AccessibilityService"`)
- `isPurchased()` checkt `"feature_access_granted"` Flag
- JS-Trial-Engine läuft parallel, liest/schreibt ABER via Bridge in die SELBEN SharedPreferences

**Warum das nativ bleiben muss:**
```
Das JS hat KEINEN direkten SharedPreferences-Zugriff – es geht alles durch
WebViewBridge.kt → TrialManager (Kotlin). Das ist die Sicherheitsgrenze.

Wenn Trial-Logik IM WebView läge:
1. User öffnet DevTools → `localStorage.setItem('trialEndTime', '9999999999999')` → Trial ewig
2. Oder: `Bridge.setTrialEndTime()` wird einfach nie aufgerufen, JS managed alles in localStorage

Aktuelle Verteidigung (mehrschichtig):
1. XOR-Obfuscation in SharedPreferences (erschwert manuelles Editieren)
2. Getarnte Key-Namen (erschwert Auffinden)
3. KEINE Trial-Logik im JS, die ohne native Bestätigung funktioniert
4. Bei jedem App-Start: TrialManager.getTrialState() mit internetzeit-Validierung

ABER: Das aktuelle Setup hat eine Schwachstelle:
- `setTrialUtcEndTimeForBridge()` und `setConfirmedExpiredForBridge()` sind via
  @JavascriptInterface erreichbar
- Wenn jemand `Android.setTrialEndTime(9999999999999)` aufruft, schreibt das direkt
  in die XOR-verschlüsselten SharedPreferences
- Die JS-Trial-Engine (index.html) könnte das theoretisch tun

Der Schutz dagegen ist NUR, dass das JS von GitHub Pages kommt (Supply-Chain-Vertrauen).
```

---

## 4. AccessibilityService – Plattform-Berechtigung

**Wo:** `ScreenOperatorAccessibilityService.kt` (2.835 Zeilen)

**Was passiert hier:**
- `performGlobalAction(GLOBAL_ACTION_HOME)` – System-Navigation
- `dispatchGesture()` – Screen-Taps, Swipes, Pinch
- `findAndClickButtonByText()` / `writeText()` – UI-Interaktion via AccessibilityTree
- `startActivity()` – App-Launch aus AccessibilityService-Kontext

**Warum nativ (PLATTFORM-ZWANG, keine Wahl):**
```
Android erlaubt NICHT:
- dispatchGesture() aus JavaScript/webview
- AccessibilityNodeInfo-Zugriff aus WebView
- GLOBAL_ACTION_* aus Nicht-System-Apps
- startActivity() ohne FLAG_ACTIVITY_NEW_TASK aus Hintergrund

All das BRAUCHT einen AccessibilityService, der vom User explizit in den
Einstellungen aktiviert wurde. Ohne diesen Service kann die App nur
Bildschirmfotos machen, aber nicht interagieren.

Die WebView-Bridge (Bridge.tapByText(), Bridge.tapAtCoordinates(), etc.)
ruft ALLE diese Methoden über ScreenOperatorAccessibilityService.executeCommand()
auf. Das JS hat KEINEN direkten Gesture-Zugriff – es geht IMMER durch den
AccessibilityService.

SICHERHEIT: Der AccessibilityService ist die ultimative Sandbox für alle
Geräte-Interaktionen. Kein JS-Code kann diese Grenze umgehen.
```

---

## 5. Offline-Modelle (LiteRT / GPU) – Native ML-Engine

**Wo:** `PhotoReasoningViewModel.kt` → `initializeOfflineModel()`, `LlmInference`, `Engine`

**Was passiert hier:**
- Lädt `.litertlm` Modelle via LiteRT-LM (`com.google.ai.edge.litertlm.Engine`)
- GPU/CPU-Backend-Selektion über `EngineConfig`
- Speichert Model-Dateien in `getExternalFilesDir(null)`

**Warum nativ:**
```
Diese Libraries sind NATIVE (.so) binaries:
- liblitertlm_jni.so
- libmediapipe_tasks_vision_jni.so

Sie greifen direkt auf GPU-Treiber (OpenGL ES / Vulkan) und NPU-Hardware zu.
Kein WebView-JavaScript kann native .so-Dateien laden oder GPU-Buffer managen.

Es gibt WebGPU/WebNN im Browser, aber für Android-WebView ist das nicht
produktionsreif für LLM-Inferenz.
```

---

## Zusammenfassung: Die 5 Sicherheitsgrenzen

| Nummer | Bereich | Bedrohung | Native-Begründung |
|--------|---------|-----------|-------------------|
| 1 | **Google Play Billing** | Kauf-Fälschung | `PurchasesUpdatedListener` kryptografisch signiert – nicht fälschbar via WebView |
| 2 | **API-Key-Storage** | Credential-Diebstahl | `SharedPreferences MODE_PRIVATE` – App-isoliert. ⚠️ Bridge exposed Keys im Klartext → verbessern! |
| 3 | **Trial-Timing** | Trial-Manipulation | XOR-Obfuscation + SharedPreferences. ⚠️ Bridge lässt JS schreiben → auf Read-Only für kritische Keys reduzieren |
| 4 | **AccessibilityService** | Unautorisierte Gerätesteuerung | Android Platform-Requirement – kein WebView-Zugriff auf Gesture-API |
| 5 | **Offline-Modelle (LiteRT)** | N/A (technisch unmöglich) | Native .so binaries, GPU-Zugriff, kein WebView-Pendant |

## Konkrete Verbesserungsvorschläge

### 🔴 SOFORT: API-Key-Bridge entschärfen
```kotlin
// WebViewBridge.kt – STATT getAllApiKeys (zeigt Klartext):
@JavascriptInterface
fun getApiKeyCount(providerName: String): Int { ... } // nur Anzahl

@JavascriptInterface  
fun getApiKeyLabel(providerName: String, index: Int): String {
    // Nur "sk-abc...xyz12" (erste 5 + letzte 4)
}
// Der ECHTE Key wird nur temporär für den API-Call bereitgestellt,
// nicht persistent im JS gespeichert.
```

### 🟡 EMPFEHLUNG: Trial-Bridge auf Read-Only reduzieren
```kotlin
// TrialManager.kt – ENTFERNEN:
fun setTrialUtcEndTimeForBridge(...)  // ← JS sollte das NIE direkt setzen dürfen
fun setConfirmedExpiredForBridge(...) // ← nur nativ nach Internetzeit-Check

// BEHALTEN (Read-Only):
fun getTrialUtcEndTimeForBridge(...)  // JS liest nur
fun isPurchased(...)                 // JS liest nur
```

### 🟢 LANGFRISTIG: API-Key-Verschlüsselung mit Android Keystore
Statt SharedPreferences-Klartext → `EncryptedSharedPreferences` (AndroidX Security) oder direkten Android Keystore für symmetrische Verschlüsselung der Keys.
