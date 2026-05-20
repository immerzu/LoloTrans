# LoloTrans v1.6

Autor: immerzu  
Lizenz: Apache License 2.0

## Änderungen seit v1.5

- Service-Benachrichtigung optional (standardmäßig AUS): Keine dauerhafte
  LoloTrans-Notification beim Bubble-Start. Der Service läuft als
  Hintergrunddienst, die System-Overlay-Anzeige bleibt als einzige
  Benachrichtigung sichtbar. Die Benachrichtigung kann in einer späteren
  Version bei Bedarf zugeschaltet werden.
- POST_NOTIFICATIONS-Berechtigung wird nicht mehr beim Bubble-Start angefragt.
- Benachrichtigungssymbol korrigiert: eigenes LoloTrans-SmallIcon
  (ic_notification_lolotrans) statt Android-Systemicon.
- Großes Notification-Icon auf das LoloTrans-Bubble-Symbol gesetzt.
- Floating-Bubble kann näher an den linken Bildschirmrand verschoben werden.
- Das sichtbare Bubble-Symbol wird innerhalb der Touchfläche per translationX
  positioniert.
- GitLab-CI-Pipeline eingerichtet (build_debug + lint_debug) mit Android-SDK-
  Docker-Image (ghcr.io/cirruslabs/android-sdk:35).
- Tap, Drag, Drag-to-Trash und Größen XS/S/M/L/XL bleiben unverändert.
- Übersetzungslogik, Clipboard-Logik und Auto-Spracherkennung bleiben
  unverändert.

## Hinweis

Beim ersten Übersetzen eines Sprachpaares lädt ML Kit das benötigte Sprachmodell herunter. Danach laufen Übersetzungen schneller und lokal.
