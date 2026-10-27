#!/bin/sh
# Adds HA_RTT_MS of round trip (half upstream, half downstream) on every cross-AZ proxy of
# compose.ha.yaml; HA_RTT_MS=0 adds nothing. Runs once in the toxiproxy-init container.
set -eu
API="${TOXIPROXY_URL:-http://toxiproxy:8474}"
RTT="${HA_RTT_MS:-0}"
until curl -fsS "$API/version" >/dev/null 2>&1; do sleep 1; done
[ "$RTT" -gt 0 ] || { echo "no AZ latency"; exit 0; }
ONE_WAY=$((RTT / 2))
for proxy in evento-b-to-pg-a pg-b-replication axon-b-to-pg-a; do
  for stream in upstream downstream; do
    curl -fsS -X POST "$API/proxies/$proxy/toxics" -H 'Content-Type: application/json' \
      -d "{\"name\":\"az-$stream\",\"type\":\"latency\",\"stream\":\"$stream\",\"attributes\":{\"latency\":$ONE_WAY,\"jitter\":0}}" >/dev/null
  done
  echo "$proxy: ${RTT} ms round trip"
done
