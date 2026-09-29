#!/usr/bin/env bash
# Runs on the VPS, started by ../deploy.sh. Restarts the room server and adds
# the Caddy site block once.
set -euo pipefail

PORT=8796
SVC=mwm-chess
STAGE="$HOME/services/mwm-chess"
CADDYFILE=/etc/caddy/Caddyfile

echo "==> Room server ($SVC on 127.0.0.1:$PORT)"
if command -v ss >/dev/null && ss -ltn "sport = :$PORT" | grep -q LISTEN \
   && ! systemctl --user is-active --quiet "$SVC"; then
  echo "Port $PORT is taken by something else. Pick another port in mwm-chess.service and chess.caddy." >&2
  exit 1
fi
if [ "$(loginctl show-user "$USER" --property=Linger --value 2>/dev/null)" != "yes" ]; then
  sudo loginctl enable-linger "$USER"   # keep user services running after logout
fi
systemctl --user daemon-reload
systemctl --user enable "$SVC" >/dev/null 2>&1
systemctl --user restart "$SVC"
for _ in $(seq 1 20); do
  if curl -fsS "http://127.0.0.1:$PORT/api/health"; then echo; break; fi
  sleep 0.5
done
systemctl --user is-active --quiet "$SVC" || { journalctl --user -u "$SVC" -n 30 --no-pager; exit 1; }

echo "==> Caddy"
if sudo grep -rqs "chess.mwmai.no" /etc/caddy/; then
  echo "chess.mwmai.no is already in the Caddy config, left unchanged."
else
  backup="$CADDYFILE.bak-$(date +%Y%m%d-%H%M%S)"
  sudo cp -a "$CADDYFILE" "$backup"
  { echo; cat "$STAGE/chess.caddy"; } | sudo tee -a "$CADDYFILE" >/dev/null
  if ! out=$(sudo caddy validate --config "$CADDYFILE" --adapter caddyfile 2>&1); then
    sudo cp -a "$backup" "$CADDYFILE"
    echo "$out" | tail -20 >&2
    echo "Caddy rejected the new block, so the old Caddyfile is back in place." >&2
    exit 1
  fi
  sudo systemctl reload caddy
  echo "Added chess.mwmai.no to $CADDYFILE (backup at $backup)."
fi
