# LoloTrans v2.4

Behebt die Befunde aus dem Softwareaudit vom 12.11.2026.

## Fehlerbehebungen

- **LibreTranslate:** Die Vorprüfung fragt `/languages` jetzt per GET ab. Vorher wurde POST
  verwendet, was standardkonforme Server mit HTTP 405 ablehnen — die Übersetzung wurde dann
  gar nicht ausgeführt.
- **Telegram-Zugangswerte:** `TELEGRAM_API_ID` und `TELEGRAM_API_HASH` werden nur noch im
  `full`-Flavor in die BuildConfig geschrieben. Die Varianten `fdroid` und `github` enthalten
  sie nicht mehr.
- **Backup:** `allowBackup="false"`. API-Schlüssel und Telegram-Sitzungsdaten gelangen nicht
  mehr in Android-Sicherungen oder Geräteübertragungen.
- **Bildschirm-Texterkennung:** Die Pixelanalyse läuft nicht mehr auf dem Hauptthread,
  sondern in `Dispatchers.Default` — kein Hänger der Oberfläche bei großen Screenshots.
  Die temporär skalierte Bitmap wird freigegeben.
- **Screenshot-Fähigkeit:** `canTakeScreenshot` ist nur noch im `full`-Flavor gesetzt.
- **Bubble:** Eine vom System abgebrochene Geste (`ACTION_CANCEL`) löst keine Übersetzung
  und keine Papierkorb-Aktion mehr aus.
- **Zwischenablage:** Der Text wird auf 100.000 Zeichen begrenzt, Zugriffsfehler werden
  abgefangen, und ein einzelner fehlgeschlagener Leseversuch führt nicht mehr zu
  „kein Text erkannt".
- **Übersetzungsfenster:** Position wird gegen die tatsächlichen Bildschirmmaße begrenzt;
  auf sehr schmalen Displays kann die Erstellung nicht mehr fehlschlagen.
- **Ressourcen:** Übersetzungs-Ressourcen werden auch bei außerplanmäßigem Activity-Ende
  freigegeben.
- **Telegram:** Hängende Anfragen werden bei Zeitüberschreitung aus der Warteliste entfernt;
  eine leere Antwort gilt als Fehler statt als Übersetzung.
- **Sprachcodes:** `nb` und `nn` werden als unterstützt geführt.

## Werkzeuge

- `tools/make_icon.py` erzeugt die App-Icons für helles und dunkles Theme.
- `tools/pre-commit-check.sh` blockiert Commits mit Telegram-Zugangsdaten.
