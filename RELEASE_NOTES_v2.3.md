# LoloTrans v2.3

- Neues App-Icon: Zeichen freigestellt, mit Schatten, für helles und dunkles Theme.
- Langes Drücken auf die Bubble öffnet die Einstellungen; kurzes Tippen übersetzt weiterhin.
- Quellsprache steht beim ersten Start auf „automatisch".
- Texterkennung überarbeitet: Der markierte Knoten hat Vorrang, die Suche läuft zuerst über
  die Eltern-Kette, Zwischenstände beim Ziehen werden nicht mehr gespeichert.
- Zwischenablage wird beim Tippen mehrfach gelesen; Zugriffsfehler werden abgefangen.
  Ein bereits übersetzter Text gilt 60 Sekunden lang als verbraucht.
- Bildschirm-Scan als zusätzlicher Weg: Findet die Ziel-App die Auswahl nicht (WebView),
  sucht die App die markierte Fläche im Bildschirmfoto und liest sie per ML Kit.
  Nur im `full`-Flavor verfügbar; im `fdroid`-Flavor bleibt dieser Weg inaktiv.
- Der Bedienungshilfe-Dienst darf Bildschirmfotos aufnehmen.

Grenzen:
- ML Kit erkennt nur lateinische Schrift. Kyrillische Markierungen ohne Kopieren werden
  bewusst nicht übersetzt, statt einen verfälschten Text zu liefern.
