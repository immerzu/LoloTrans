# LoloTrans v1.7

Autor: immerzu  
Lizenz: Apache License 2.0

## Änderungen seit v1.6

- Service-Benachrichtigung ist jetzt optional und standardmäßig deaktiviert.
- Beim normalen Bubble-Start erscheint keine eigene „LoloTrans — Bubble ist aktiv"-Benachrichtigung mehr.
- Die Bubble startet standardmäßig per normalem Service.
- Foreground-Service-Benachrichtigung kann optional aktiviert werden, falls ein Gerät sie benötigt.
- POST_NOTIFICATIONS wird beim normalen Bubble-Start nicht mehr abgefragt.
- GitLab-CI-Pipeline ergänzt:
  - build_debug
  - lint_debug
- GitLab-CI verwendet Android SDK Docker-Image `ghcr.io/cirruslabs/android-sdk:35`.
- Benachrichtigungs-SmallIcon für den optionalen Foreground-Service korrigiert.

## Hinweis

Android kann weiterhin eine eigene Systemmeldung „Über anderen Apps anzeigen" anzeigen. Diese stammt von Android/SystemUI, nicht von LoloTrans.
