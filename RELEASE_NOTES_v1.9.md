# LoloTrans v1.9

Autor: immerzu  
Lizenz: Apache License 2.0

## Änderungen seit v1.8

- Neuer F-Droid-kompatibler Build-Flavor `fdroid`.
- Google ML Kit ist nur noch im `full`-Flavor enthalten.
- `fdroid`-Flavor enthält kein Google ML Kit, keine Google Play Services und keine Firebase-Abhängigkeiten.
- Neuer LibreTranslate-kompatibler Übersetzungsprovider für den F-Droid-Build.
- Übersetzungsdienst-Auswahl in den Einstellungen ergänzt.
- LibreTranslate-Server-URL und optionaler API-Key konfigurierbar.
- GitLab CI baut und lintet jetzt beide Flavors explizit.
- F-Droid-Metadaten aktualisiert.

## Hinweise

Der `full`-Build behält die bisherige ML-Kit-Funktionalität.

Der `fdroid`-Build verwendet LibreTranslate-kompatible Server. Dabei wird der zu übersetzende Text an den eingestellten Server gesendet.

## APKs

- Full debug APK: enthält ML Kit
- F-Droid debug APK: ohne ML Kit / Play Services / Firebase
