# LoloTrans

**App-ID:** de.lolo.lolotrans

LoloTrans ist eine Android-Übersetzungs-App mit Floating-Bubble, automatischer Quellspracherkennung und On-Device-Übersetzung per ML Kit.

## Lizenz

Apache License 2.0

## Autor

immerzu (kesselflickerei@gmx.de)

## Releases

- GitHub: https://github.com/immerzu/LoloTrans/releases

## Funktionen

- Floating-Bubble für schnelle Übersetzung aus der Zwischenablage
- Quellsprache automatisch erkennen (ML Kit Language Identification)
- On-Device-Übersetzung (ML Kit Translation) — keine Server-Anfrage, volle Privatsphäre
- Übersetzung in einem frei verschiebbaren Overlay-Fenster
- Bubble-Größen: XS / S / M / L / XL
- Bubble verschieben und per Drag-to-Trash schließen
- Übersetzung kopieren / erneut übersetzen
- Über 17 Sprachen
- Benachrichtigung optional, standardmäßig aus
- Schwarz/Weiß-Design im LOLO-SOFT-Stil

## Datenschutz

- **Keine heimliche Clipboard-Überwachung.** Die App liest die Zwischenablage nur, wenn du aktiv die Bubble antippst.
- **Keine Internet-Übertragung.** Die Übersetzung läuft komplett auf dem Gerät (ML Kit On-Device Translation).
- **Keine Tracker / Analytics / Werbung.** Die App enthält keinerlei Tracking-Bibliotheken.
- **Kein externer Server.** Es werden keine Daten an externe Dienste gesendet.
- **Internet-Zugriff** wird nur benötigt, um beim ersten Gebrauch eines Sprachpaares das ML-Kit-Sprachmodell herunterzuladen.
- Android kann unabhängig eine Systemmeldung „Über anderen Apps anzeigen" einblenden — diese stammt von Android/SystemUI, nicht von LoloTrans.

## Hinweise

- Beim ersten Übersetzen eines Sprachpaares lädt ML Kit das benötigte Sprachmodell herunter. Danach laufen Übersetzungen schneller und vollständig lokal.
- Die App benötigt die Overlay-Berechtigung (SYSTEM_ALERT_WINDOW), um die Bubble und das Übersetzungsfenster über anderen Apps anzuzeigen.
- Die App verwendet **Google ML Kit** als Übersetzungs-Engine. ML Kit ist ein proprietäres Google-SDK.
- **Offizielles F-Droid** ist wegen Google ML Kit aktuell nicht geeignet. **IzzyOnDroid** ist die realistischere Alternative, da dort GitHub-Release-APKs genutzt werden.

## Build-Varianten

Das Projekt bietet drei Build-Flavors:

### full (Standard)
- Enthält Google ML Kit für On-Device-Übersetzung
- Übersetzung läuft komplett lokal nach Modell-Download
- Telegram ist nur im full-Flavor verfügbar. Für Telegram müssen echte Werte lokal in `local.properties` gesetzt werden; `local.properties` darf nicht committed werden. Ohne diese Werte baut der full-Flavor, Telegram meldet zur Laufzeit aber fehlende Zugangsdaten. Siehe `local.properties.example`.
- APK ca. 77 MB (ML Kit Native-Libraries)

```bash
./gradlew assembleFullDebug
```

### fdroid (F-Droid)
- Enthält KEIN Google ML Kit und KEINE Google Play Services
- Enthält wie der github-Flavor Telegram/TDLib nicht als nutzbaren Provider
- Nutzt einen LibreTranslate-kompatiblen Server für Übersetzungen
- Nutzer muss Server-URL in den Einstellungen konfigurieren
- Text wird zur Übersetzung an den konfigurierten Server gesendet
- APK ca. 11 MB

```bash
./gradlew assembleFdroidDebug
```

### github
- Enthält KEIN Google ML Kit und KEINE Google Play Services
- Enthält wie der fdroid-Flavor Telegram/TDLib nicht als nutzbaren Provider
- Nutzt LibreTranslate und den FreeTranslations-Provider

```bash
./gradlew assembleGithubDebug
```

### Telegram lokal konfigurieren

Telegram-Zugangsdaten werden nicht im Repository gespeichert. Kopiere `local.properties.example` nach `local.properties` und trage dort deine eigenen Werte ein:

```properties
TELEGRAM_API_ID=0
TELEGRAM_API_HASH=
```

Die Platzhalterwerte sind nicht nutzbar. Echte Werte gehören nur in die lokale `local.properties`.

## Build

```bash
./gradlew assembleFullDebug    # Full-Build mit ML Kit
./gradlew assembleFdroidDebug  # F-Droid-Build ohne ML Kit
```

## Quellcode

- GitHub: https://github.com/immerzu/LoloTrans
- GitLab: https://gitlab.com/immerzu46/LoloTrans
