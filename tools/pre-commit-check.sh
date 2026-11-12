#!/bin/sh
# Blockiert Commits, die Telegram-Zugangsdaten oder local.properties enthalten.
# Aufruf: .git/hooks/pre-commit (oder manuell: sh tools/pre-commit-check.sh)
#
# Geprueft werden ausschliesslich die zum Commit vorgemerkten Aenderungen.

blocked=0

# 1. local.properties niemals committen
if git diff --cached --name-only | grep -qE '(^|/)local\.properties$'; then
  echo "ABBRUCH: local.properties darf nicht committet werden." >&2
  blocked=1
fi

# 2. Echte Telegram-Zugangsdaten in vorgemerkten Zeilen
#    API-Hash: 32 Hex-Zeichen; API-ID: mindestens 5-stellige Zahl
if git diff --cached -U0 | grep -E '^\+' | grep -qE \
   'TELEGRAM_API_HASH[^A-Za-z0-9]*[0-9a-fA-F]{32}|TELEGRAM_API_ID[^A-Za-z0-9]*[0-9]{5,}'; then
  echo "ABBRUCH: Telegram-Zugangsdaten im Commit gefunden." >&2
  echo "Diese Werte gehoeren ausschliesslich in local.properties (gitignored)." >&2
  blocked=1
fi

if [ "$blocked" -eq 0 ]; then
  echo "pre-commit: keine Telegram-Zugangsdaten gefunden."
fi
exit "$blocked"
