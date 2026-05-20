# LoloTrans

**App-ID:** de.lolo.lolotrans

LoloTrans ist eine Android-Übersetzungs-App mit Floating-Bubble, automatischer Quellspracherkennung und On-Device-Übersetzung per ML Kit.

## Lizenz

Apache License 2.0

## Autor

immerzu (immerzu46@gmail.com)

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

## Build

```bash
./gradlew assembleDebug
```

Die APK liegt dann unter `app/build/outputs/apk/debug/app-debug.apk`.

## Quellcode

- GitHub: https://github.com/immerzu/LoloTrans
- GitLab: https://gitlab.com/immerzu46/LoloTrans
