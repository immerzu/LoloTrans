# AGENT_STATUS — LoloTrans

Laufender Arbeitsstand. Bei Sitzungsbeginn oder Kontextverlust zuerst diese Datei lesen,
dann `memory/README.md`, `memory/FACT.md` und das jüngste `HANDOVER_*.md`.

**Marker:** `[V]` verifiziert (Beleg/Log/Test vorhanden) · `[S]` aus Code/Doku belegt, nicht neu
getestet · `[M]` Messwert/Ausgabe · `[?]` offen oder unsicher.

Stand: 2026-11-22

## Gesamtziel

LoloTrans soll markierten oder kopierten Text zuverlässig übersetzen, wenn die Bubble angetippt
wird — on-device (ML Kit, `full`) oder über einen konfigurierten Dienst (`fdroid`, `github`).
Der Projektstand soll sauber, konsistent und nachvollziehbar dokumentiert sein.

## Aktueller Auftrag

Projektpflege, Bereinigung, Synchronisierung und Release 2.5 — **abgeschlossen**.
Freigaben erteilt (22.11.2026); F-Droid-Veröffentlichung ausdrücklich **nicht** gewünscht.

## Erledigt

- **Bubble-Pfad korrigiert** `[V]`: Zwischenablage vor Bildschirm-Scan; Clipboard liest die
  fokussierte `ClipboardTranslateActivity`. Belegt auf ingo: 86 Zeichen → korrekte Übersetzung.
- **Telegram-Anmeldung sichtbar** `[V]`: Meldung unter dem Status, Fehler als Toast, `imePadding`.
  Belegt auf bernd mit ungültiger Nummer (`PHONE_NUMBER_INVALID` sichtbar).
- **README** `[S]`: Datenschutz je Flavor getrennt; Aussage „keine Internet-Übertragung" entfernt.
- **Doku** `[S]`: `HANDOVER_20261122.md` neu; `HANDOVER_20261112.md` und der v2.2-Arbeitsbericht
  als historisch markiert; `IZZYONDROID_SUBMISSION.md` als überholt markiert.
- **FACT** `[S]`: Version 16/2.5, Übersetzungsweg, F-Droid-Stand.
- **CI** `[S]`: `.gitlab-ci.yml` baut/lintet nur noch `fdroid` (kein Full-Artefakt).
- **Aufräumen** `[M]`: 13 Diagnosedateien aus der Wurzel nach `diagnostics/archiv-2026-06-02/`
  verschoben; nichts gelöscht.
- **Release 2.5** `[V]`: `versionCode 16`, Commit `afa57e2e`, Tag `v2.5`,
  https://github.com/immerzu/LoloTrans/releases/tag/v2.5 mit `fdroid`- und `github`-Debug-APK.
- **Builds** `[M]`: `assembleFullDebug`, `assembleFdroidDebug`, `assembleGithubDebug` erfolgreich;
  `lintFdroidDebug` 0 Fehler, 85 Warnungen.

## Offene Aufgaben

1. **Signierte Release-APK** `[?]` — nur `~/.android/debug.keystore` vorhanden. Eine echte
   Release-Signatur braucht einen Projekt-Keystore und eine Gradle-Signaturkonfiguration
   (Freigabe erforderlich).
2. **Scan-Fehltreffer** `[?]` — kräftige blaue Bildflächen gelten weiterhin als Markierung;
   nur die Mindestlänge (3 Zeichen) filtert grobe Artefakte.
3. **WebView-Auswahl** `[?]` — Markieren ohne Kopieren liefert keine Auswahl; der verlässliche
   Weg ist kopieren → Bubble antippen.
4. **F-Droid** `[S]` — fdroiddata steht bei 2.2/13; 2.3/14, 2.4/15 und 2.5/16 sind nicht
   eingereicht. Keine Einreichung gewünscht.
5. **GitLab-Spiegel** `[?]` — veraltet; Entscheidung stilllegen oder pflegen steht aus.

## Entscheidungen

- Zwischenablage hat Vorrang vor dem Bildschirm-Scan (Nutzeranweisung 2026-11-22).
- Der `full`-Flavor (ML Kit, Telegram) bleibt lokal: kein Release, kein Upload, kein CI-Artefakt.
- Keine F-Droid-Veröffentlichung.
- Gerätetests nur auf alice/bernd; ingo nur auf Anweisung; Rotationssperre unantastbar.

## Nächster konkreter Schritt

Kein Schritt läuft. Auf Auftrag warten; wenn keiner kommt, ist Punkt 2 (Scan härten) oder
Punkt 1 (Release-Signatur) der sinnvollste nächste Eingriff — beide brauchen eine Freigabe.

## Belege

- Commits `6a0fdaf3` (Fixes + Doku), `afa57e2e` (Version 2.5).
- Release-Assets: `LoloTrans-v2.5-fdroid-debug.apk`, `LoloTrans-v2.5-github-debug.apk`.
- Lokale APKs: `Ausgabe/LoloTrans-v2.5-{full,fdroid,github}-debug-20261122.apk`.
- Test-Logs: `%TEMP%\ingo_clip_*.log`, `%TEMP%\fix_toast.png`, `%TEMP%\ingo_clip_ok.png`.
