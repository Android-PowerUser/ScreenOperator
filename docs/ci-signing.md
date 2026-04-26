# CI Signing für Release-Builds

Die Module `app` und `humanoperator` erwarten für Release-Tasks eine Signing-Konfiguration über Umgebungsvariablen.

## Benötigte CI-Secrets

- `ANDROID_KEYSTORE_PATH`: Absoluter oder relativ zum Projekt auflösbarer Pfad zur Keystore-Datei.
- `ANDROID_KEY_ALIAS`: Alias des Release-Keys.
- `ANDROID_KEYSTORE_PASSWORD`: Passwort der Keystore-Datei.
- `ANDROID_KEY_PASSWORD`: Passwort des Keys.

## Verhalten bei fehlenden Variablen

- Für **Release-Tasks** (Taskname enthält `release`) wird der Build mit einer klaren Fehlermeldung abgebrochen, wenn eine der Variablen fehlt.
- Für Nicht-Release-Tasks bleibt die Signing-Config ungesetzt, damit lokale Debug-Builds weiter funktionieren.

## Wichtiger Hinweis zu Firebase

`google-services.json` bleibt unverändert versioniert und ist **nicht** Teil der Signing-Logik.
