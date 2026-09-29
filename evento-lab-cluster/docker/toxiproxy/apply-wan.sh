#!/bin/sh
# Applies (or removes) the baseline WAN latency toxics on every cross-cell proxy.
#
#   apply-wan.sh          (re)apply the baseline RTT matrix (idempotent)
#   apply-wan.sh off      remove the baseline toxics (LAN speed between cells)
#   apply-wan.sh show     print every proxy with its toxics
#
# Runs both inside the one-shot 'toxiproxy-init' container (nginx:alpine, busybox sh + curl)
# and from the host under Git Bash / macOS / Linux: TOXIPROXY_URL defaults to the published API.
#
# Baseline RTT matrix (round trip, split half per direction, +-jitter per direction):
#   EU <-> AM     80 ms   (40 ms each way, jitter 5)
#   EU <-> APAC  150 ms   (75 ms each way, jitter 5)
#   AM <-> APAC  170 ms   (85 ms each way, jitter 5)
# WAN_LATENCY_SCALE (integer percent, default 100) scales the matrix, e.g. 200 doubles it.
#
# The baseline toxics are always named 'wan-latency-up' / 'wan-latency-down'. Chaos scenarios
# must add their own toxics under other names and remove only those, so that "heal" never
# drops the WAN baseline; re-running this script restores the baseline in any case.
set -eu

TOXIPROXY_URL="${TOXIPROXY_URL:-http://localhost:8474}"
JITTER_MS="${WAN_JITTER_MS:-5}"
SCALE="${WAN_LATENCY_SCALE:-100}"
MODE="${1:-apply}"

one_way_ms() {
  case "$1-$2" in
    eu-am|am-eu)     base=40 ;;
    eu-apac|apac-eu) base=75 ;;
    am-apac|apac-am) base=85 ;;
    *) echo "unknown cell pair $1-$2" >&2; exit 2 ;;
  esac
  echo $(( base * SCALE / 100 ))
}

# Wait for the API (the init container starts as soon as toxiproxy is healthy, but the
# script is also used from the host right after 'up').
i=0
until curl -fsS "$TOXIPROXY_URL/version" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then echo "toxiproxy API not reachable at $TOXIPROXY_URL" >&2; exit 1; fi
  sleep 1
done

if [ "$MODE" = "show" ]; then
  curl -fsS "$TOXIPROXY_URL/proxies"
  echo
  exit 0
fi

for src in eu am apac; do
  for dst in eu am apac; do
    [ "$src" = "$dst" ] && continue
    ms=$(one_way_ms "$src" "$dst")
    for svc in es-pg app-pg evento; do
      proxy="$src-to-$svc-$dst"
      for stream in up down; do
        # Shell redirection, not 'curl -o /dev/null': a native Windows curl under Git Bash
        # (MSYS_NO_PATHCONV=1) cannot open /dev/null.
        curl -s -X DELETE "$TOXIPROXY_URL/proxies/$proxy/toxics/wan-latency-$stream" >/dev/null 2>&1 || true
      done
      if [ "$MODE" = "off" ]; then
        echo "proxy=$proxy wan=off"
        continue
      fi
      for stream in up down; do
        if [ "$stream" = up ]; then dir=upstream; else dir=downstream; fi
        curl -fsS -X POST "$TOXIPROXY_URL/proxies/$proxy/toxics" \
          -H 'Content-Type: application/json' \
          -d "{\"name\":\"wan-latency-$stream\",\"type\":\"latency\",\"stream\":\"$dir\",\"toxicity\":1.0,\"attributes\":{\"latency\":$ms,\"jitter\":$JITTER_MS}}" \
          >/dev/null
      done
      echo "proxy=$proxy one_way_ms=$ms jitter_ms=$JITTER_MS"
    done
  done
done
