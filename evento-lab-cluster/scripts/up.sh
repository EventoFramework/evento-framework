#!/usr/bin/env bash
# Starts the lab (or a subset of services) and waits until every started service is healthy.
#
#   scripts/up.sh                    full topology
#   scripts/up.sh es-pg-eu lb ...    only these services (plus their depends_on)
#
# env: LAB_TAG        image tag (default dev)
#      BUS_VIA_LB     true -> app JVMs dial their cell VIP bus-<cell>:3030 (nginx stream,
#                     primary = own server, backup = the others) instead of the direct list
#      REPLICA        true -> also start cell-core-eu-b (compose profile 'replica', S2)
#      COLD_START     true -> no health chain between the three servers (S10)
#      UP_TIMEOUT     seconds to wait for health (default 900)
#      REPAIR_CATALOG false -> skip repair-catalog.sh after a full start (G8 workaround)
#      BUS_ADDR_EU / BUS_ADDR_AM / BUS_ADDR_APAC, CONSUMER_CHECKPOINT_MODE,
#      WAN_LATENCY_SCALE, WAN_JITTER_MS, LAB_JAVA_OPTS  are passed through to compose.
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

if [ "${BUS_VIA_LB:-false}" = "true" ]; then
  export BUS_ADDR_EU="bus-eu:3030" BUS_ADDR_AM="bus-am:3030" BUS_ADDR_APAC="bus-apac:3030"
  log "BUS_VIA_LB=true: app JVMs use the per-cell bus VIPs"
fi

args=()
[ "${REPLICA:-false}" = "true" ] && args+=(--profile replica)
files=(-f "$DOCKER_DIR/compose.yaml")
[ "${COLD_START:-false}" = "true" ] && files+=(-f "$DOCKER_DIR/compose.cold-start.yaml")

log "docker compose up (LAB_TAG=$LAB_TAG) ${*:-<all services>}"
docker compose -p "$COMPOSE_PROJECT" "${files[@]}" ${args[@]+"${args[@]}"} up -d \
  --wait --wait-timeout "${UP_TIMEOUT:-900}" "$@"

docker compose -p "$COMPOSE_PROJECT" "${files[@]}" ${args[@]+"${args[@]}"} ps \
  --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'

# G8 workaround: a first registration can lose the core__bundle insert race and leave the
# instance's consumers out of the catalog; restart the affected JVMs (see repair-catalog.sh).
if [ $# -eq 0 ] && [ "${REPAIR_CATALOG:-true}" = "true" ]; then
  "$(dirname "$0")/repair-catalog.sh" || log "warning: catalog still incomplete (G8), see output above"
fi
