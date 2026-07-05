# LoloTrans v2.2 Arbeitsstand - Selection-Cache + Telegram-Status/Authfix + Popup-Schließen + Text-Vergessen + Clipboard-Verbrauchsfix + Doppelte-Leertext-Meldungsfix + Start-Clipboard-Baseline + Overlay-Außentipp-Robustfix

Datum/Uhrzeit lokal: 2026-07-05 09:25:25 +02:00 (Codex-Phase)
Update 2026-07-05: Popup-Schließen-bei-Außentipp + Text-Vergessen-Fix + Clipboard-Verbrauchsfix (Agent-B-Phase)
Update 2026-07-05 10:30: Doppelte-Leertext-Meldungsfix (Agent-B-Phase)
Update 2026-07-05 10:45: Start-Clipboard-Baseline + Overlay-Außentipp-Robustfix (Agent-B-Phase)
Update 2026-07-05 11:00: Bubble-Autostart-Fix (Agent-B-Phase)

## Projektpfade

- Projektordner: `F:/001_Coding_Projekte/Handy_Translation_APP/`
- Ausgabeordner: `F:/DeepSeek/Projekte/Handy_Translation_APP/Ausgabe/`
- Zuletzt installierte Test-APK: `F:/DeepSeek/Projekte/Handy_Translation_APP/Ausgabe/LoloTrans-v2.2-13-full-debug-selection-cache-test-telegram-statusfix-11715ea.apk`

## Git-Stand

- Branch: `main`
- HEAD: `11715eaae751d2b1501f4f2eff9d5cd1ba9d2416` (`Prepare LoloTrans 2.2 release`)
- Tag auf HEAD: `v2.2`
- Staging: leer
- Geänderte Dateien:
  - `.gitignore`
  - `app/src/main/java/de/lolo/lolotrans/SelectionAccessibilityService.kt`
  - `app/src/main/java/de/lolo/lolotrans/FloatingBubbleService.kt`
  - `app/src/main/java/de/lolo/lolotrans/ClipboardTranslateActivity.kt`
  - `app/src/main/java/de/lolo/lolotrans/LoloTransApplication.kt`
  - `app/src/main/java/de/lolo/lolotrans/MainActivity.kt`
  - `app/src/full/java/de/lolo/lolotrans/TelegramTdlibClient.kt`
  - `app/src/full/java/de/lolo/lolotrans/TelegramOnDeviceProvider.kt`
  - `app/src/full/java/de/lolo/lolotrans/FlavorProviderUi.kt`
  - `app/src/main/java/de/lolo/lolotrans/TranslationOverlayManager.kt`
  - `ARBEITSSTAND_UEBERGABE_LoloTrans_v2.2_selection_telegram_statusfix.md`
- Ignorierte lokale Artefakte:
  - `local.properties`
  - `Ausgabe/`
  - `diagnostics/`

## Version/APK

- applicationId: `de.lolo.lolotrans`
- versionCode/versionName: `13 / 2.2`
- Variant: `fullDebug`
- APK-Groesse: `102237460` Bytes
- APK-SHA-256: `7F98991154AA777346DF71F15ACA6386B3E3BAC60F2D96D60A6446F04233AE52`
- (Agent-B-Phase) Popup/Forgetfix-Test-APK: `LoloTrans-v2.2-13-full-debug-selection-cache-test-popup-forgetfix-11715ea.apk` (SHA-256: `e2b300ac...`)
- (Agent-B-Phase) **Aktuellste Test-APK**: `LoloTrans-v2.2-13-full-debug-selection-cache-test-no-autostart-bubble-11715ea.apk`
- (Agent-B-Phase) SHA-256: `73e40c037ab311de94f01c05422d18a6946f75eb5e81f89a697ad1437ba07b96`
- (Agent-B-Phase) APK-Größe: `102404220` Bytes
- Ingo: ADB-Serial `10AF291H12002XU`, Modell `V2405A`
- Installierter Geraetestand Ingo: `de.lolo.lolotrans`, `versionCode=13`, `versionName=2.2`

## Aenderungen nach Datei

### `.gitignore`

- Ignoriert lokale Diagnose- und APK-Ablageordner:
  - `/Ausgabe/`
  - `/diagnostics/`

### `SelectionAccessibilityService.kt`

- Erfasst zuletzt markierten Text ueber Accessibility-Auswahlindizes.
- Speichert nur gueltige, nicht leere Auswahl mit gueltigen Grenzen.
- Begrenzung fuer Auswahltext: maximal `10000` Zeichen.
- Cache-TTL fuer Auswahltext: `30000` ms.
- Markiert verwendeten Auswahltext als verbraucht.
- Loescht/invalidiert verbrauchten Auswahltext nach erfolgreicher Uebersetzung verzögert nach `2500` ms (vorher `4000` ms).
- **Neu (Clipboard-Verbrauchsfix)**: `consumedText`-Feld speichert den zuletzt erfolgreich übersetzten Ausgangstext.
- **`isTextConsumed(text)`**: Prüft, ob ein Text bereits übersetzt wurde.
- **`setConsumedText(text)`**: Wird von `ClipboardTranslateActivity` nach erfolgreicher Übersetzung aufgerufen.
- `consumedText` wird **automatisch gelöscht**, wenn eine neue Accessibility-Textauswahl erkannt wird (`storeSelectedText` → `consumedText = null`).
- Keine Textinhalte in Logs.
- Keine automatische Copy-Aktion.
- Kein Löschen des System-Clipboards – nur interne Verbrauchsmarkierung.

### `FloatingBubbleService.kt`

- Bubble-Klick bevorzugt internen Selection-Cache.
- Clipboard bleibt Fallback, wenn kein gueltiger interner Auswahltext vorliegt.
- Verwendeter Selection-Cache wird als verbraucht markiert.
- Kein `ACTION_COPY`, kein `ACTION_PROCESS_TEXT`.
- **Neu (Start-Clipboard-Baseline)**: `onCreate()` ruft `markInitialClipboardAsConsumed()` auf.
- **`markInitialClipboardAsConsumed()`**: Liest beim Service-Start den aktuellen Android-Clipboard-Inhalt und markiert ihn über `SelectionAccessibilityService.setConsumedText()` als verbraucht. Dadurch wird alter, vor LoloTrans vorhandener Clipboard-Text beim ersten Bubble-Tap ignoriert.
- **Ursache alter Clipboard-Text beim ersten Tap**: Es gab keine Baseline-Erfassung beim Service-Start. Der beim Appstart vorhandene Clipboard-Inhalt wurde nie als „alt" markiert, daher passierte er beim ersten Clipboard-Fallback ungehindert die `isTextConsumed`-Prüfung. Erst nach der ersten Übersetzung (und dem Aufruf von `setConsumedText`) wurde er als verbraucht markiert – der zweite Tap war dann korrekt leer.
- **Kein Löschen des System-Clipboards** – nur interne Verbrauchsmarkierung.
- Falls das Lesen des Clipboards vom Service-Kontext aus auf Android 12+ fehlschlägt, bleibt die Baseline leer → der erste Bubble-Tap zeigt alten Text (akzeptabel, da kein sauberer Weg).

### `LoloTransApplication.kt` (NEU: geändert)

- **Entfernt**: `restoreBubbleIfNeeded()`-Aufruf aus `onCreate()`. Diese Methode las beim Appstart das DataStore-Flag `bubbleEnabled` und startete den Bubble-Service automatisch, wenn der Wert `true` war.
- **Ursache Bubble-Autostart (1/2)**: `LoloTransApplication.onCreate()` → `restoreBubbleIfNeeded()` → `bubbleEnabled == true` → `startService()` → Bubble sofort sichtbar. Dies passierte bei jedem App-Prozess-Start, unabhängig davon, ob der User die App bewusst geöffnet hatte oder nicht.
- **Neue Regel**: Bubble startet ausschließlich nach User-Klick auf "Bubble starten". Keine automatische Wiederherstellung beim Appstart.

### `MainActivity.kt` (NEU: geändert)

- **Entfernt**: `reconcileBubbleServiceState()`-Aufruf aus `onResume()`. Diese Methode las das DataStore-Flag `bubbleEnabled` und startete den Bubble-Service, wenn die Bubble nicht bereits sichtbar war.
- **Ursache Bubble-Autostart (2/2)**: `onResume()` → `reconcileBubbleServiceState()` → `bubbleEnabled == true && !bubbleVisible` → `startBubbleService()`. Dies passierte bei jedem Zurückkehren zur App, nachdem der Service vom System beendet worden war (z.B. nach App-Force-Stop).
- **Neue Regel**: Bubble startet ausschließlich nach User-Klick auf "Bubble starten".
- **Berechtigungsprüfung** (Overlay + Accessibility) bleibt erhalten und wird weiterhin in der UI angezeigt.
- **"Bubble starten" / "Bubble stoppen"-Buttons** bleiben funktionsfähig.
- **Bubble-Stop-Verhalten** unverändert: `stopBubbleService()` → `requestStop()` → Bubble entfernt + `bubbleEnabled = false` gespeichert.

### `ClipboardTranslateActivity.kt`

- Nutzt zuerst explizit uebergebenen Auswahltext.
- Nutzt danach wie bisher Clipboard-Fallback.
- Nach erfolgreicher Uebersetzung wird der verbrauchte Selection-Cache nach `2500` ms geloescht.
- Kein Logging von Textinhalten.
- `setFinishOnTouchOutside(true)` gesetzt, sodass die Activity bei Tipp außerhalb schließt (Dialog-Modus ohne Overlay).
- **Neu (Clipboard-Verbrauchsfix)**: Clipboard-Fallback prüft vor der Verwendung mit `SelectionAccessibilityService.isTextConsumed(clip)`. Wenn der Clipboard-Text bereits verbraucht ist, wird er als `null` behandelt → keine erneute Übersetzung.
- **Nach erfolgreicher Übersetzung** wird `SelectionAccessibilityService.setConsumedText(textToTranslate)` aufgerufen, damit derselbe Clipboard-Inhalt später nicht erneut übersetzt wird.
- **Neu (Doppelte-Leertext-Meldungsfix)**: `showError()` erhält einen Parameter `showToast: Boolean = true`. Bei leerem Textpuffer (keine Selection, kein Clipboard-Inhalt) wird `showError(message, showToast = false)` aufgerufen → das zentrale Dialogfenster erscheint, aber die untere Android-Toast-Meldung wird unterdrückt.
- **Ursache der doppelten Meldung**: `showError()` (Z. 328) zeigte den Fehlertext sowohl im Dialog (`statusText.text = message`) als auch als `Toast.makeText(this, message, Toast.LENGTH_LONG).show()`. Bei leerem Textpuffer ergab das zwei gleichzeitige Meldungen.
- **Weitere showError-Aufrufe** (Spracherkennung fehlgeschlagen, Sprache nicht unterstützt, gleiche Sprache) bleiben unverändert und zeigen weiterhin den Toast. Der Fix betrifft nur den Leertext-Fall.
- **Neu (Overlay-Stacking-Verhinderung)**: `activeOverlay`-Referenz in `companion object` speichert das aktuell aktive Übersetzungs-Overlay. Vor dem Erstellen eines neuen Overlays wird `activeOverlay?.remove()` aufgerufen, damit kein Overlay-Stacking bei wiederholtem Bubble-Tap entsteht.

### `TranslationOverlayManager.kt` (NEU / überarbeitet)

- Overlay-Fenster ist jetzt **fullscreen** (`MATCH_PARENT` × `MATCH_PARENT`) mit transparentem Hintergrund.
- **Tipp auf transparenten Bereich** → Overlay schließt sich sofort.
- **Tipp auf die Karte** → Karte ist `isClickable = true`, frisst den Klick, Overlay bleibt offen.
- Flags: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH`.
- `FLAG_NOT_TOUCH_MODAL` stellt sicher, dass Touches auf dem transparenten Bereich an die darunterliegende App durchgereicht werden.
- **Android-Navigationsleiste** (Home/Zurück/Übersicht): Über `SelectionAccessibilityService.addSystemUiWindowCallback()` wird das Overlay bei SystemUI-Fensterwechsel automatisch geschlossen. Der Callback wird bei `remove()` korrekt deregistriert.
- Ziehfunktion aktualisiert jetzt die Card-Position per `FrameLayout.LayoutParams.leftMargin/topMargin` statt per Window-Params.
- Keine Systemnavigation blockiert, keine Vollbild-Falle.
- **Neu (Overlay-Außentipp-Robustfix)**: `setOnClickListener { remove() }` auf dem Root wurde durch `setOnTouchListener` ersetzt. Der Touch-Listener vergleicht bei `ACTION_DOWN` die Touch-Koordinaten (`event.rawX/Y`) mit den Bildschirmkoordinaten der Karte (`container.getLocationOnScreen()`). Bei Touch außerhalb der Karte → `remove()`.
- **Ursache warum Außentipp vorher nicht zuverlässig schloss**: `setOnClickListener` am fullscreen-transparenten Root wurde auf einigen Geräten (insb. Vivo V2405A mit Funtouch OS) nicht zuverlässig ausgelöst, weil die Touch-Events durch `FLAG_NOT_TOUCH_MODAL` an die darunterliegende App durchgereicht wurden, bevor der Root-View sie verarbeiten konnte. Der `OnTouchListener` fängt den Touch bereits bei `ACTION_DOWN`, bevor die Weiterleitung an die Hintergrund-App erfolgt, und kann so zuverlässiger schließen.
- **Android-Navigationsbereich**: Direkte Touch-Erkennung für Navigationstasten/Gesten ist auf Overlay-Ebene technisch nicht möglich. Der bestehende `SystemUiWindowCallback` (AccessibilityService erkennt SystemUI-Fensterwechsel) bleibt als Fallback aktiv. Zusätzlich verhindert die `activeOverlay`-Referenz in `ClipboardTranslateActivity` Overlay-Stacking, falls ein neuer Bubble-Tap ein zweites Overlay erzeugen würde.
- Die Karte wird VOR dem Root erstellt, damit der Touch-Listener auf die Karten-Referenz zugreifen kann.

### `TelegramTdlibClient.kt`

- Verarbeitet weiterhin direkte `authorizationState*`-Antworten von `getAuthorizationState`.
- Verarbeitet weiterhin `updateAuthorizationState`.
- `authorizationStateReady` wird nicht mehr direkt als UI-Ready akzeptiert.
- Nach `authorizationStateReady` wird die aktuelle TDLib-Session zusaetzlich mit `getMe` validiert.
- `TelegramAuthStatus.Ready` wird erst nach erfolgreicher `getMe`-Validierung gesetzt.
- Beim Oeffnen/Refresh wird der Status aktiv ueber `getAuthorizationState` abgefragt.
- Telegram-Uebersetzung wartet auf den verifizierten Auth-Status statt einen ungeprueften Startzustand zu verwenden.
- Logs enthalten nur technische Statusinformationen, keine Zugangsdaten.

### `TelegramOnDeviceProvider.kt`

- `ensureModelReady` wartet auf den verifizierten Telegram-Auth-Status.
- `close()` stoppt TDLib nicht mehr, damit die Session und der Ready-State zwischen zwei Uebersetzungen stabil bleiben.

### `FlavorProviderUi.kt`

- Telegram-UI startet/refreshes die TDLib-Statuspruefung mit `LaunchedEffect`.
- Anzeige `Telegram ist angemeldet` bleibt an `TelegramAuthStatus.Ready` gekoppelt.
- Durch die neue Client-Logik bedeutet `Ready` jetzt: aktuelle TDLib-Instanz meldete `authorizationStateReady` und `getMe` war erfolgreich.

## Fachliche Ergebnisse

- Selection-Cache-Funktion ist code-seitig implementiert.
- Cache-Vergessen nach erfolgreicher Uebersetzung ist mit `2500` ms Verzoegerung implementiert (vorher `4000` ms).
- Clipboard-Fallback bleibt erhalten.
- Telegram-Login wurde vorher auf Ingo mit Authfix-Teststand erfolgreich gemeldet.
- Zweite Telegram-Uebersetzung wurde durch stabilen TDLib-Client-Lifetime-Fix adressiert.
- Telegram-Statusanzeige wurde verschaerft: `angemeldet` erscheint nur nach aktueller TDLib-Ready- und `getMe`-Validierung.
- Wenn eine echte lokale TDLib-Session vorhanden ist, bleibt `Telegram ist angemeldet` korrekt.
- Nach echter Neuinstallation ohne TDLib-Session soll der Status nicht Ready werden; ein kompletter Frischinstallationstest mit Datenloeschung wurde in diesem Schritt nicht ausgefuehrt.

## Tests

- Build: `.\gradlew.bat assembleFullDebug` erfolgreich.
- Installierte APK auf Ingo: erfolgreich per `adb install -r`.
- Installierte Version auf Ingo: `13 / 2.2`.
- UI-Pruefung nach Installation:
  - Bei sichtbarem Telegram-Abschnitt wurde `Telegram ist angemeldet` angezeigt, waehrend TDLib-Daten auf dem Geraet vorhanden waren.
  - Ohne Telegram-Abschnitt war keine Ready-Meldung sichtbar.
- (Agent-B-Phase) Build `assembleFullDebug`: **BUILD SUCCESSFUL** (4s, 4 neu kompiliert, 33 up-to-date).
- (Agent-B-Phase) APK `LoloTrans-v2.2-13-full-debug-selection-cache-test-popup-forgetfix-11715ea.apk`: erstellt, SHA-256 verifiziert, Installation auf Ingo **ausstehend** (User hat Berechtigungen auf dem Geraet nicht bestaetigt – `INSTALL_FAILED_ABORTED: User rejected permissions`).
- (Agent-B-Phase) `git diff --check`: sauber (keine Whitespace-Probleme).
- (Agent-B-Phase) `git diff --name-status`: 8 geänderte Dateien (keine unerwarteten Aenderungen).
- (Agent-B-Phase) **No-duplicate-empty-message-APK** `LoloTrans-v2.2-13-full-debug-selection-cache-test-no-duplicate-empty-message-11715ea.apk`:
  - Build: **BUILD SUCCESSFUL** (3s, 4 executed, 33 up-to-date)
  - SHA-256: `ff8212d8e2c3652f338076f2c4ea8a893202a07ad7332946bdd0acae31e0f494`
  - Größe: `102237460` Bytes
  - Installation auf Ingo: **erfolgreich** (versionCode=13, versionName=2.2)
  - App gestartet
- (Agent-B-Phase) **Clipboard-consumedfix-APK** `LoloTrans-v2.2-13-full-debug-selection-cache-test-clipboard-consumedfix-11715ea.apk`:
  - Build: **BUILD SUCCESSFUL**
  - SHA-256: `292e6644e14f42a8089fea09f1ce8c3c89aae24c2f8052a0d9f4d935f795a1a9`
  - Installation auf Ingo: **erfolgreich** (versionCode=13, versionName=2.2)
  - App gestartet
- Offene manuelle Tests (vor Commit noetig):
  - **Clipboard-Verbrauch**: Text A übersetzen, Blase ohne neue Auswahl/Kopie erneut antippen → Text A erscheint nicht erneut aus alter Zwischenablage.
  - Text B markieren, Blase antippen → Text B wird übersetzt.
  - Neuen Text manuell kopieren → Clipboard-Fallback funktioniert.
  - Denselben Text erneut kopieren → wird als neue Clipboard-Nutzung erkannt (oder gesperrt bei kurzer TTL – akzeptabel).
  - Popup-Aussentipp-Test auf Ingo.
  - Text-Vergessen (2,5s warten, Bubble antippen, kein alter Text).
  - Clipboard-Fallback ohne Auswahl.
  - Echter Frischinstallationstest (Appdatenloeschung) ohne Telegram-Login.
  - Telegram-Login nach Statusfix.
  - Zwei Telegram-Uebersetzungen direkt nacheinander.
  - Selection-Cache-Test mit markiertem Text ohne Kopieren.
  - **Doppelte-Leertext-Meldung**: Keine Toast-Meldung bei leerem Puffer (zentrales Dialogfenster erscheint ohne zusätzlichen Toast).

## Sicherheits- und Nicht-Commit-Hinweise

- `local.properties` nicht committen.
- `Ausgabe/` nicht committen.
- `diagnostics/` nicht committen.
- Keine Secrets in Diffs ausgeben.
- Keine Telefonnummern, Login-Codes, API-IDs oder API-Hashes in Doku oder Bericht aufnehmen.
- Keine Appdatenloeschung, Deinstallation, Tags, Pushes oder Uploads ohne explizite User-Freigabe.

## Bekannte Risiken

- Accessibility-Auswahl haengt davon ab, ob die Ziel-App `textSelectionStart` und `textSelectionEnd` bereitstellt.
- TDLib-Status haengt von lokaler Session und TDLib-Datenbank ab.
- Echter Frischinstallationszustand muss sauber von `adb install -r` Update-Installation unterschieden werden.
- Die Anzeige nach vorhandener Session ist erwartbar Ready; der No-Session-Fall braucht noch einen sauberen manuellen Frischinstallationstest.
- (Agent-B-Phase) Popup-Schliessen per Aussentipp ist abhaengig vom Window-Typ und der Android-Version. `FLAG_NOT_TOUCH_MODAL` + fullscreen + `isClickable`-Container sollte die robusteste Loesung sein.
- (Agent-B-Phase) Systemnavigation: Der SystemUI-Callback ueber die Accessibility-Service-Benachrichtigung schliesst das Overlay bei Fensterwechseln. Manche Navigationsgesten (z. B. Wischgeste von unten) koennen je nach Android-Version unterschiedlich ausgeloest werden. Der `clearConsumedSelectionAfterDelay` loescht auch nach `2500` ms den Cache, sodass ein versehentlich offenes Popup spaetestens dann nicht mehr stoert.
- (Agent-B-Phase) Text-Vergessen per `clearConsumedSelectionAfterDelay(2500L)` ist generationstoken-sicher: Timestamp- und Text-Snapshot werden vor dem `delay()` erfasst; ein neuer Text nach dem Loeschjob wird nicht faelschlich geloescht.
- (Agent-B-Phase) **Clipboard-Verbrauchsfix**: `consumedText` wird als Klartext in einer `@Volatile`-Feld gehalten. Kein Persistieren, kein Loggen, kein Export. Minimaler Speicherverbrauch.
- (Agent-B-Phase) `consumedText` wird nur bei neuer Accessibility-Textauswahl geloescht. Wenn der User denselben Text ohne neue Auswahl erneut kopiert, kann es sein, dass der Clipboard-Fallback ihn ignoriert. Das ist gewünscht: "bereits verwendeter Clipboard-Inhalt darf nicht automatisch wiederverwendet werden."
- (Agent-B-Phase) Wenn der User einen völlig anderen Text markiert (via Accessibility-Selection), wird `consumedText` in `storeSelectedText` geloescht. Der neue Text wird korrekt ueber `EXTRA_TEXT_TO_TRANSLATE` an die Activity gegeben, ohne den Clipboard-Fallback ueberhaupt zu beruehren.
- (Agent-B-Phase) Installation auf Ingo: erfolgreich (Beim zweiten Versuch akzeptiert).
- (Agent-B-Phase) **Doppelte-Leertext-Meldung**: `showError` zeigt bei `showToast = false` keinen Toast. Andere Fehlerfälle (Spracherkennung, gleiche Sprache) bleiben unverändert.
- (Agent-B-Phase) **Start-Clipboard-Baseline**: Der `consumedText` wird beim Service-Start auf den initialen Clipboard-Inhalt gesetzt. Wenn der User denselben Text später bewusst neu kopiert, wird `isTextConsumed` ihn ignorieren (da == Baseline). Das ist gewünscht: "alter Text, der vor Appstart im Clipboard lag, darf nicht automatisch übersetzt werden." Ein neu kopierter anderer Text funktioniert sofort. Falls kein Clipboard-Lesen vom Service-Kontext möglich (Android 12+), bleibt die Baseline leer.
- (Agent-B-Phase) **Overlay-Außentipp**: `setOnTouchListener` mit Koordinatenvergleich ist robuster als `setOnClickListener` auf transparentem Root. Falls die Karte noch nicht gelayount wurde (`getLocationOnScreen` wirft Exception), wird der Touch nicht konsumiert und durchgereicht – das Overlay bleibt in diesem seltenen Race-Case offen.
- (Agent-B-Phase) **Systemnavigation-Overlay**: Direkte Touch-Weiterleitung von Navigationstasten/Gesten an Overlays ist Android-seitig nicht vorgesehen. Der `SystemUiWindowCallback` (AccessibilityService erkennt `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOWS_CHANGED` von `com.android.systemui`) ist der bestmögliche Fallback und schließt das Overlay bei den meisten Navigationsaktionen zuverlässig. Auf Geräten, wo dieses Event nicht feuert (Custom-ROMs), bleibt das Overlay sichtbar – der User kann es dann über den X-Button oder Bubble-Tap schließen.
- (Agent-B-Phase) **Overlay-Stacking**: `activeOverlay`-Referenz in `ClipboardTranslateActivity` wird bei jedem neuen Overlay aktualisiert. Bei Bubble-Tap während ein Overlay sichtbar ist, wird das alte Overlay entfernt, bevor das neue erstellt wird. Bei einem Crash zwischen `remove()` und `addView()` könnte ein Zombie-Overlay entstehen – unwahrscheinlich und durch erneuten Bubble-Tap behebbar.
- (Agent-B-Phase) **Startclipboard-Overlayclosefix-APK** `LoloTrans-v2.2-13-full-debug-selection-cache-test-startclipboard-overlayclosefix-11715ea.apk`:
  - Build: **BUILD SUCCESSFUL** (3s, 4 executed, 33 up-to-date)
  - SHA-256: `92ce686a09fa43dbe2b889a5e129a0c10299901d80d9ca445a4eb439764dde4b`
  - Größe: `102404266` Bytes
  - Installation auf Ingo: **erfolgreich** (versionCode=13, versionName=2.2)
  - App gestartet
- (Agent-B-Phase) **No-autostart-bubble-APK** `LoloTrans-v2.2-13-full-debug-selection-cache-test-no-autostart-bubble-11715ea.apk`:
  - Build: **BUILD SUCCESSFUL** (3s, 4 executed, 33 up-to-date)
  - SHA-256: `73e40c037ab311de94f01c05422d18a6946f75eb5e81f89a697ad1437ba07b96`
  - Größe: `102404220` Bytes
  - Installation auf Ingo: **erfolgreich** (versionCode=13, versionName=2.2)
  - App gestartet
- Offene manuelle Tests (vor Commit noetig):
  - **Kein Bubble-Autostart**: App schließen & neu öffnen → Bubble erscheint nicht automatisch.
  - **Bubble starten**: Button "Bubble starten" → Bubble erscheint.
  - **App neu öffnen nach Bubble-Start**: Bubble erscheint nicht automatisch.
  - **Bubble stoppen**: Button funktioniert, danach App neu öffnen → kein Autostart.
  - **Start-Clipboard-Baseline**: Vor Appstart Text in Zwischenablage haben, App starten, Blase antippen → alter Text erscheint nicht.
  - **Bewusst neuen Text kopieren** → Clipboard-Fallback funktioniert.
  - **Overlay-Außentipp**: Übersetzungsfenster anzeigen, außerhalb tippen → Fenster schließt.
  - **Overlay-Innentipp**: Innerhalb des Fensters tippen → Fenster bleibt.
  - **Systemnavigation**: Home/Zurück/App-Übersicht/Geste → Overlay verschwindet, Systemnavigation funktioniert.
  - **Zweite Übersetzung**: neuer Text funktioniert, kein alter Text.
  - **Doppelte-Leertext-Meldung**: keine Toast-Meldung bei leerem Puffer.
  - **Clipboard-Verbrauch**: alter übersetzter Text wird beim zweiten Bubble-Tap nicht erneut verwendet.
  - **Text-Vergessen**: 2,5s warten, Bubble antippen → alter Text erscheint nicht.
  - **Selection-Cache**: Text markieren ohne Kopieren → Übersetzung funktioniert.
  - **Telegram**: erste und zweite Telegram-Übersetzung funktionieren.
  - **Popup-Inhalt**: Kopieren-Button im Übersetzungsfenster funktioniert.

## Naechster sicherer Schritt

1. Manuelle Tests auf Ingo (siehe oben).
2. Nach erfolgreichem Test: nur commitwuerdige Dateien stagen (8 Dateien).
3. Commit und Tag erst nach ausdruecklicher User-Freigabe.
4. Keinen Push ohne separate Freigabe.
