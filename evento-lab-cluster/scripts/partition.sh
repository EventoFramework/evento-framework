#!/usr/bin/env bash
# Cuts a cell off from the rest of the world (or heals it) by detaching the WAN proxy -- and
# optionally the lb -- from that cell's network. Existing TCP sessions are not reset: the
# peers see a silent partition, exactly what S7 needs (G22, G25).
#
#   scripts/partition.sh <eu|am|apac> on  [--with-lb]
#   scripts/partition.sh <eu|am|apac> off
#
# --with-lb also removes the lb from the cell network: HTTP ingress to the cell's gateways
# and its bus VIP go away too. Without it the cell's own bus VIP keeps working.
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

cell="${1:?usage: $0 <eu|am|apac> <on|off> [--with-lb]}"
action="${2:?usage: $0 <eu|am|apac> <on|off> [--with-lb]}"
with_lb="${3:-}"
net="net-$cell"
case "$cell" in
  eu) lb_ip=172.28.1.10 ;;
  am) lb_ip=172.28.2.10 ;;
  apac) lb_ip=172.28.3.10 ;;
  *) echo "unknown cell $cell" >&2; exit 2 ;;
esac

attached() { docker inspect -f "{{if index .NetworkSettings.Networks \"$net\"}}yes{{end}}" "$1" 2>/dev/null; }

case "$action" in
  on)
    [ "$(attached toxiproxy)" = yes ] && docker network disconnect "$net" toxiproxy
    if [ "$with_lb" = "--with-lb" ] && [ "$(attached lb)" = yes ]; then docker network disconnect "$net" lb; fi
    log "cell $cell partitioned"
    ;;
  off)
    [ "$(attached toxiproxy)" = yes ] || docker network connect --alias "toxi-$cell" "$net" toxiproxy
    if [ "$(attached lb)" != yes ]; then
      docker network connect --ip "$lb_ip" --alias "bus-$cell" "$net" lb
      # nginx bound the VIP address at start-up; restart so it listens on it again.
      docker restart lb >/dev/null
    fi
    log "cell $cell healed"
    ;;
  *) echo "action must be on|off" >&2; exit 2 ;;
esac
