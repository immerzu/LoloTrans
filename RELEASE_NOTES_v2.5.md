# LoloTrans v2.5

Bringt den Kernweg der App zurück: Text kopieren, Bubble antippen, Übersetzung erscheint.

## Fehlerbehebungen

- **Zwischenablage hat wieder Vorrang.** Seit v2.3 lief der Bildschirm-Scan vor der
  Zwischenablage. Der Dienst kann ab Android 10 die Zwischenablage nicht lesen, deshalb griff
  fast immer der Scan und lieferte Bild-Artefakte (z. B. „87" aus einem Foto) statt des
  kopierten Textes. Jetzt gilt: markierter Text → Zwischenablage → Bildschirm-Scan.
- **Zwischenablage liest die fokussierte Activity.** Sie hat Fensterfokus und damit lesbaren
  Zugriff; der Service reicht den Fall nur noch weiter.
- **Bildschirm-Scan als letzter Ausweg.** Er läuft nur, wenn die Zwischenablage leer ist, und
  verwirft Treffer unter 3 Zeichen.
- **Telegram-Anmeldung sichtbar.** Die Rückmeldung (Code angefordert, Fehler, Erfolg) steht direkt
  unter dem Status statt am Kartenende; Fehler erscheinen zusätzlich als Toast.
- **Tastatur verdeckt keine Schaltflächen mehr.** `imePadding()` im Einstellungsbildschirm,
  „Fertig" schließt die Tastatur und gibt den Fokus frei.

## Dokumentation

- README: Datenschutz je Build-Variante getrennt (`full` on-device, `fdroid`/`github` senden an
  den eingestellten Dienst).
- Neues Übergabedokument, ältere Übergaben als historisch markiert.
- `.gitlab-ci.yml` baut nur noch den `fdroid`-Flavor (der `full`-Flavor enthält Telegram-Zugangswerte
  und darf kein CI-Artefakt werden).

## Bekannte Grenzen

- Markieren ohne Kopieren liefert in WebViews (z. B. RSS-Reader) keine Auswahl; der verlässliche
  Weg ist kopieren → Bubble antippen.
- Der Bildschirm-Scan kann kräftige blaue Bildflächen weiterhin als Markierung deuten; Treffer
  unter 3 Zeichen werden verworfen.
- ML Kit erkennt nur lateinische Schrift.

## APKs

- `fdroid`-Flavor: LibreTranslate, keine Google-Bibliotheken.
- `github`-Flavor: LibreTranslate und FreeTranslations.
- Der `full`-Flavor (ML Kit, Telegram) wird nicht veröffentlicht.
