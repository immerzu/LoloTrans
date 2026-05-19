# Build-Ausgabe und Versionierung

- **Ausgabeordner**: `F:\DeepSeek\Projekte\Handy_Translation_APP\Ausgabe`
- Neue APK-Versionen werden dort mit Versionsnummer abgelegt (z. B. `LoloTrans-v1.0.apk`, `LoloTrans-v1.1.apk`, `LoloTrans-v1.2.apk`, `LoloTrans-v1.3.apk`, `LoloTrans-v1.4.apk`, `LoloTrans-v1.5.apk`)
- Vor jedem Release die APK aus `app/build/outputs/apk/debug/app-debug.apk` dorthin kopieren
- Versionierung: `versionCode` und `versionName` in `app/build.gradle.kts` vor Release hochzählen
- **Jede neue Ausgabe bekommt eine hochgezählte Version**
- Aktueller Stand: versionCode=6, versionName="1.5"