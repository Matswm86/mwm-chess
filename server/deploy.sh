#!/usr/bin/env bash
# Deploy the online-room server to https://chess.mwmai.no. Run from your
# workstation; it needs SSH to the VPS as a user with sudo.
#
#   ./deploy.sh                       # mats@204.168.244.173
#   VPS=user@host ./deploy.sh
set -euo pipefail

VPS="${VPS:-mats@204.168.244.173}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Tests"
(cd "$HERE" && python3 -m unittest test_server -q)

echo "==> Upload to $VPS"
ssh "$VPS" 'mkdir -p ~/services/mwm-chess ~/.config/systemd/user'
rsync -az "$HERE/server.py" "$HERE/deploy/chess.caddy" "$HERE/deploy/install_remote.sh" "$VPS:services/mwm-chess/"
rsync -az "$HERE/deploy/mwm-chess.service" "$VPS:.config/systemd/user/"

echo "==> Install on the VPS"
ssh "$VPS" 'bash ~/services/mwm-chess/install_remote.sh'

echo "==> Check https://chess.mwmai.no (the first request fetches the TLS certificate)"
for _ in $(seq 1 15); do
  if curl -fsS https://chess.mwmai.no/api/health; then
    echo
    echo "Live: https://chess.mwmai.no"
    exit 0
  fi
  sleep 2
done
echo "The site did not answer yet. Check: ssh $VPS 'journalctl -u caddy -n 50 --no-pager'" >&2
exit 1
