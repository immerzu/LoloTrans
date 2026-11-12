# LoloTrans v2.2 Arbeitsstand - Accessibility-Overlay, Selection-Cache und Yandex-Grenze

Stand: 2026-07-21 11:57:46 +02:00

Dieses Dokument beschreibt den tatsaechlichen lokalen Arbeitsstand. Es ersetzt die
fruehere, inzwischen veraltete Aussage, der Arbeitsbaum sei sauber.

## 1. Projekt und Git

- Projekt: `F:/001_Coding_Projekte/LoloTrans/`
- Branch: `main`
- Basis-HEAD: `218eb5b9b36798744f822137352e68d6e051316f` (darauf folgt der Commit dieses Umbaus)
- HEAD-Titel: `Fix selection cache, Telegram auth, clipboard dedup, overlay dismiss & bubble autostart`
- Release-Tag `v2.2`: zeigt auf `11715eaae751d2b1501f4f2eff9d5cd1ba9d2416`
- Version: `versionCode=13`, `versionName=2.2`
- Staging: leer
- Arbeitsbaum: absichtlich geaendert, nicht committen oder zuruecksetzen ohne neue User-Freigabe
- Es wurde in dieser Phase kein Commit, Tag, Push oder Upload ausgefuehrt.
- Remotes existieren fuer `github` und `gitlab`. Keine Remote-Aktion ohne expliziten Auftrag;
  insbesondere wurde kein GitLab-Schritt ausgefuehrt.

### Sichtbare geaenderte Dateien

1. `ARBEITSSTAND_UEBERGABE_LoloTrans_v2.2_selection_telegram_statusfix.md`
2. `app/src/main/AndroidManifest.xml`
3. `app/src/main/java/de/lolo/lolotrans/AccessibilityStatus.kt`
4. `app/src/main/java/de/lolo/lolotrans/ClipboardTranslateActivity.kt`
5. `app/src/main/java/de/lolo/lolotrans/FloatingBubbleService.kt`
6. `app/src/main/java/de/lolo/lolotrans/LoloTransApplication.kt`
7. `app/src/main/java/de/lolo/lolotrans/MainActivity.kt`
8. `app/src/main/java/de/lolo/lolotrans/SelectionAccessibilityService.kt`
9. `app/src/main/java/de/lolo/lolotrans/TranslationOverlayManager.kt`
10. `app/src/main/res/values-de/strings.xml`
11. `app/src/main/res/values/strings.xml`
12. `app/src/main/res/xml/selection_accessibility_service.xml`
13. `memory/FACT.md`

`memory/FACT.md` enthaelt eine separate lokale Aktualisierung zu Version und Ausgabeordner.
Diese Datei wurde im zuletzt bearbeiteten Funktionsschritt nicht geaendert und muss vor
einem spaeteren Staging separat bewertet werden.

### Ignorierte lokale Dateien und Ordner

- `local.properties`: vorhanden, durch `.gitignore` ignoriert, nie stagen oder committen
- `/Ausgabe/`: lokale APK-/DEX-/Ablageartefakte, nicht committen
- `/diagnostics/`: lokale Screenshots, Logcat- und Analyseartefakte, nicht committen
- Die Ignore-Regeln wurden mit `git check-ignore -v` verifiziert.

## 2. Committed Basis in HEAD 218eb5b9

Der aktuelle HEAD enthaelt bereits die frueheren Selection-/Clipboard-/Telegram-Fixes:

- Selection-Cache mit 30 Sekunden TTL und maximal 10.000 Zeichen
- Selection vor Clipboard beim Bubble-Klick
- Clipboard-Fallback und Schutz vor unmittelbarer Wiederverwendung
- Cache-Loeschung 2,5 Sekunden nach erfolgreicher Uebersetzung
- Telegram-Login/Auth-State-Verarbeitung im `full`-Flavor
- Telegram-Ready erst nach TDLib-Ready und Sessionvalidierung
- stabiler TDLib-Client fuer mehrere Uebersetzungen
- Telegram/TDLib nur im `full`-Flavor; `fdroid` und `github` bleiben TDLib-frei
- Versionierung 2.2 / 13 und Release-Metadaten

Telegram wurde nicht entfernt, deaktiviert oder als veraltet markiert.

## 3. Aktuelle uncommitted Aenderungen nach Datei

### `AndroidManifest.xml`

- Entfernt `SYSTEM_ALERT_WINDOW`.
- Entfernt Benachrichtigungs- und Foreground-Service-Berechtigungen fuer die Bubble.
- Entfernt `foregroundServiceType="specialUse"` und dessen Property.
- Ziel: keine Android-Systemmeldung `LoloTrans wird ueber anderen Apps angezeigt` und keine
  dauerhafte Bubble-Service-Benachrichtigung.

### `AccessibilityStatus.kt`

- Liest den Secure-Settings-Status defensiv mit Exception-Schutz.
- Normalisiert vollstaendige und relative Service-Klassennamen.
- Ziel: weniger falsche Anzeige `Zugriff auf markierten Text inaktiv` nach Rueckkehr in die App.

### `ClipboardTranslateActivity.kt`

- Verwendet fuer das Ergebnisfenster den aktiven Accessibility-Service als Overlay-Host.
- Faellt auf das bestehende Dialogergebnis zurueck, wenn kein verbundener Host existiert.
- Clipboard-Fallback und Selection-Cache-Loeschung bleiben erhalten.

### `FloatingBubbleService.kt`

- Bubble und Papierkorb verwenden `TYPE_ACCESSIBILITY_OVERLAY` statt
  `TYPE_APPLICATION_OVERLAY`.
- Verwendet `WindowManager` und Context des verbundenen Accessibility-Service.
- Wartet beim Start kurz auf einen gueltigen Accessibility-Overlay-Host.
- Kann nach Neuverbindung des Accessibility-Service an den neuen Host angehaengt werden.
- Entfernt Foreground-Notification, NotificationChannel und Overlay-Permission-Pruefung.
- Start/Stop und Clipboard-Fallback bleiben bestehen.

### `LoloTransApplication.kt`

- Bubble-Restore prueft nicht mehr `Settings.canDrawOverlays`.
- Restore bleibt an gespeichertes `bubbleEnabled` und aktuell aktiven Accessibility-Service gebunden.

### `MainActivity.kt`

- Entfernt die alte System-Overlay-Berechtigungsabfrage und deren Einstellungslink.
- Bubble-Start benoetigt nur noch den aktuell aktiven Accessibility-Service.
- Pollt den Accessibility-Status nach `onStart` kurz nach, um OEM-/Lifecycle-Verzoegerungen
  in der UI aufzufangen.
- Die persistente Information `accessibilityApprovedOnce` bleibt nur UI-/Hinweisinformation;
  sie ersetzt nicht den aktuellen Service-Status.

### `SelectionAccessibilityService.kt`

- Stellt `AccessibilityOverlayHost` fuer Bubble und Ergebnisfenster bereit.
- Setzt `connected=true` erst in `onServiceConnected`, nicht bereits in `onCreate`.
- Fordert bei verbundener Instanz eine Bubble-Neuanbindung an, wenn `bubbleEnabled=true` ist.
- Verarbeitet fuer Texterkennung nur `TYPE_VIEW_TEXT_SELECTION_CHANGED`.
- Scannt nur den aktiven Baum, iterativ und auf 250 Knoten begrenzt; kein rekursiver Scan aller
  Fenster. Dies reduziert Freeze-/ANR-Risiko bei grossen Browser-Accessibility-Baeumen.
- Vergleicht Event-, Source- und Root-Kandidaten und speichert den laengsten Kandidaten.
- Die fruehere Heuristik `laengeren gecachten Text behalten, wenn er den neuen enthaelt` wurde
  entfernt. Grund: Bei manueller Griff-Anpassung blieb sonst ein zu grosser Zwischenstand haengen.
- Logs enthalten nur technische Metadaten und Laengen, keine ausgewaehlten Textinhalte.
- TTL bleibt 30 Sekunden, Textgrenze 10.000 Zeichen, Erfolgsclear 2,5 Sekunden.

### `TranslationOverlayManager.kt`

- Ergebnisfenster verwendet `TYPE_ACCESSIBILITY_OVERLAY`.
- Vorhandenes Schliessen, Aussentipp-Handling und Overlay-Stacking-Verhalten bleiben erhalten.

### Ressourcen

- `permission_status` heisst jetzt `Bedienungshilfe` bzw. `Accessibility service`.
- Accessibility-Service abonniert nur Selection-, Window-State- und Windows-Changed-Events;
  Focus- und Window-Content-Events wurden entfernt, um Ereignislast zu reduzieren.

## 4. Fachliche Ergebnisse und Tests

### Build

Nach der letzten Selection-Cache-Aenderung wurden am 2026-07-21 alle Varianten gebaut:

- `assembleFdroidDebug`: erfolgreich
- `assembleFullDebug`: erfolgreich
- `assembleGithubDebug`: erfolgreich
- Kombinierter Gradle-Lauf: `BUILD SUCCESSFUL`

### Aktuelle lokale Full-Debug-APK

- Pfad: `app/build/outputs/apk/full/debug/app-full-debug.apk`
- Variant: `fullDebug`
- applicationId: `de.lolo.lolotrans`
- versionCode/versionName: `13 / 2.2`
- Groesse: `103061567` Bytes
- SHA-256: `CA2FE70EB3EE1A46C0D9464C31F1ECA3955E16802CDA4539AFE86CF3F2C8FC9E`
- Buildzeitstempel: 2026-07-21 11:35:16 lokal

### Ingo

- ADB-Serial: `10AF291H12002XU`
- Modell: `V2405A`
- Die obige APK wurde erfolgreich per `adb install --no-streaming -r` installiert.
- Zuletzt verifiziert: Paket `de.lolo.lolotrans`, Version `13 / 2.2`.
- App-Daten wurden dabei nicht geloescht.
- Accessibility-Service und Bubble waren nach manueller Reaktivierung gebunden/aktiv.
- Bubble-Fenster wurde als `TYPE_ACCESSIBILITY_OVERLAY` (`type=2032`) verifiziert.
- Keine aktive LoloTrans-AlertWindow-Benachrichtigung; ein alter geloeschter Channel kann in
  `dumpsys notification` weiterhin als historischer Eintrag erscheinen.
- Beim Erstellen dieses Dokuments war Ingo nicht mehr per ADB erreichbar. Der Geraetestand ist
  daher der zuletzt verifizierte Stand, keine aktuelle Live-Pruefung.

### Freeze-/Neustart-Beobachtung

- Die teuren Accessibility-Baumsuchen wurden begrenzt und auf Selection-Events reduziert.
- Frueherer Smoke-Test: mehrere normale App-Schliessen/-Oeffnen-Zyklen ohne ANR oder Crash.
- Ein vollstaendiger Langzeittest gegen sporadisches Einfrieren steht noch aus.
- Auf dem Vivo-Geraet wurde beobachtet, dass ein kompletter Geraeteneustart den LoloTrans-
  Accessibility-Service aus der aktiven Liste entfernen kann. Die App darf diese geschuetzte
  Android-Einstellung nicht selbst wieder einschalten; der User musste sie manuell aktivieren.
- Nach normalem App-Neustart soll die Berechtigung erhalten bleiben, solange Android/OEM den
  Service nicht deaktiviert.

## 5. Yandex Browser: bekannte und akzeptierte Grenze

- Getestete Yandex-Version: `26.6.4.110`.
- Yandex zeigt beim Markieren ein eigenes dunkles Bottom-Sheet namens
  `Kontextbezogene Suche von Yandex`.
- Offizielle Einstellungen und `browser://flags` bieten in dieser Version keinen belastbaren
  Hauptschalter zum Abschalten. `Contextual Search with credentials for debug` ist nur ein
  Debug-Credentials-Flag; `Touch To Search Lab` deaktiviert das Panel nicht.
- Yandex-Flags wurden vom User zurueckgesetzt und Yandex neu gestartet; das Verhalten blieb.
- ADB-Beobachtung eines Auswahlvorgangs: Yandex meldete nacheinander verschiedene Selection-
  Laengen (unter anderem 8, 135 und 14 Zeichen). Das Yandex-Suchpanel kann damit den von
  LoloTrans erwarteten Auswahlzustand veraendern oder die Uebergabe verhindern.
- Letzter User-Test nach manueller Accessibility-Reaktivierung: kein Text wurde von LoloTrans
  uebersetzt; das Yandex-Suchpanel schaltete sich dazwischen.
- User-Entscheidung: Entwicklung fuer diesen Yandex-Sonderfall abbrechen und aktuellen Stand
  unveraendert lassen. In anderen Apps/Fenstern scheint die Auswahl laut User zu funktionieren.
- Ein Folge-Agent darf keinen Yandex-spezifischen Workaround implementieren, solange der User
  dies nicht erneut ausdruecklich beauftragt.

## 6. Noch offene Tests

Vor einem Commit mindestens:

1. Selection ohne Kopieren in einer einfachen Text-App testen.
2. Selection ohne Kopieren in mindestens einem Browser ohne Yandex-Kontextsuche testen.
3. Nach erfolgreicher Uebersetzung 3 Sekunden warten; alter Selection-Cache darf nicht erneut
   verwendet werden.
4. Clipboard-Fallback mit bewusst neu kopiertem Text testen.
5. Ohne Selection und ohne neuen Clipboard-Text das bisherige Kein-Text-Verhalten pruefen.
6. Erste und zweite Telegram-Uebersetzung nacheinander im `full`-Flavor pruefen.
7. App mehrfach normal schliessen/oeffnen und auf Freeze/ANR sowie Berechtigungsanzeige achten.
8. Optionaler Geraeteneustart-Test: dokumentieren, ob Vivo den Accessibility-Service erneut
   deaktiviert. Das ist OEM-/Systemverhalten und nicht programmgesteuert heilbar.

Yandex ist fuer Selection-ohne-Kopieren derzeit explizit nicht freigegeben.

## 7. Risiken und Review-Punkte

- Accessibility-Auswahl ist grundsaetzlich davon abhaengig, welche Indizes und Knoten die
  Ziel-App bereitstellt.
- Die Auswahl der laengsten Kandidaten aus Event/Source/Root sollte in mehreren Apps manuell
  gegengeprueft werden.
- Die entfernte `keep longer cached selection`-Heuristik behebt Griff-Zwischenstaende, kann aber
  fremde Folgeevents wie Yandex nicht allgemein unterscheiden.
- Der 250-Knoten-Grenzwert schuetzt vor sehr grossen Baeumen, kann aber tiefe Auswahlknoten
  verpassen. In Yandex wurde dieser Grenzwert im Log erreicht.
- `TYPE_ACCESSIBILITY_OVERLAY` beseitigt die System-Overlay-Kachel, bindet Bubble und Ergebnis
  aber zwingend an einen verbundenen Accessibility-Service.
- Vollstaendige Persistenz einer Accessibility-Freigabe ueber OEM-/Geraeteneustarts kann eine
  normale App nicht erzwingen.
- Das aktuelle Debug-Logging ist technisch und textfrei, aber relativ ausfuehrlich; vor Release
  kann eine gezielte Reduktion sinnvoll sein.

## 8. Sicherheits- und Bedienregeln

- Niemals Inhalte aus `local.properties` ausgeben, stagen oder committen.
- Keine Telegram-API-ID, API-Hashes, Telefonnummern, Login-Codes, Tokens oder Passwoerter
  in Logs, Diffs oder Dokumentation aufnehmen.
- `Ausgabe/` und `diagnostics/` niemals stagen.
- Keine App-Daten, TDLib-Datenbank oder Telegram-Session ohne ausdrueckliche User-Freigabe loeschen.
- Telegram im `full`-Flavor erhalten; nicht entfernen, deaktivieren oder als veraltet markieren.
- Keine Gradle-, Flavor-, Versionierungs-, F-Droid- oder Release-Metadaten nebenbei aendern.
- Kein Staging, Commit, Tag, Push oder Upload ohne ausdrueckliche Freigabe.
- Push-Ziel darf bei spaeterer Freigabe nur explizit `github` sein; kein GitLab-/F-Droid-Upload.
- Auf Ingo niemals `adb shell monkey` verwenden. Der User berichtet, dass Monkey die
  Bildschirmausrichtung bzw. Anzeige verdreht. Nur gezielte ADB-Befehle verwenden.

## 9. Empfohlener naechster sicherer Schritt

1. Keine weitere Yandex-Entwicklung beginnen.
2. Arbeitsbaum read-only gegen dieses Dokument pruefen.
3. Die offenen manuellen Nicht-Yandex-Tests auf Ingo ausfuehren.
4. Danach Code-Review der zwoelf geaenderten Projekt-/Ressourcendateien; `memory/FACT.md`
   separat entscheiden.
5. Erst nach erfolgreichem Test und User-Freigabe gezielt commitwuerdige Dateien stagen.
6. Kein Commit, Tag oder Push automatisch ausfuehren.
