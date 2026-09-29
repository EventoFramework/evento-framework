#!/usr/bin/env bash
# Lab workaround for G8 (discovery / consumer-registration insert race, see FINDINGS.md).
#
# When a bundle registers for the first time on an empty catalog, the server runs two passes in
# parallel on different bus threads and under different lock keys: AutoDiscovery
# ("DISCOVERY:<bundleId>") and ConsumerService ("CONSUMER_DISCOVERY:<instanceId>"). Both
# find-then-insert core__bundle / core__component, and the loser aborts on 'duplicate key':
#   * ConsumerService loses -> the instance has no core__consumer rows;
#   * AutoDiscovery loses   -> the bundle has no core__handler rows (the component row is the
#                              "ephemeral" one ConsumerService created, path NULL).
# A plain restart does not help: the old session's NodeLeft unregisters the single-instance
# bundle (rows deleted), so the new session races on an empty catalog again.
#
# The repair therefore makes the second registration happen while the rows exist and the
# bundle stays live:
#   1. start a helper twin of the JVM (same role/env, instance id <container>-helper) and wait
#      until it is healthy: its discovery finds the component and inserts the handler rows;
#      bundle ownership moves to the helper;
#   2. restart the JVM: its NodeLeft no longer unregisters the bundle (owned by the helper, and
#      live), and its re-join registers consumers against existing rows;
#   3. remove the helper: its NodeLeft only clears the helper's own consumer rows.
#
#   scripts/repair-catalog.sh            check + repair (max REPAIR_PASSES passes, default 3;
#                                        REPAIR_PARALLEL JVMs at a time, default 4)
#   scripts/repair-catalog.sh --check    only report, exit 1 if something is missing
#
# Called by up.sh after a full-topology start (REPAIR_CATALOG=false disables it). Every step is
# printed with the 'event=lab_catalog_repair' prefix, so the G8 hit rate is visible.
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

check_only=false
[ "${1:-}" = "--check" ] && check_only=true

# container -> "<consumer rows its instance must own> [bundle ids that must own handler rows]".
# One bundle per JVM, instance id = container name; one consumer row per consumer engine
# (component x context of EVENTO_CONTEXTS). Gateways own no handler and no consumer.
declare -A expected=(
  [order-command-eu]="0 order-command"
  [order-command-am]="0 order-command"
  [order-command-apac]="0 order-command"
  [order-query-eu]="2 order-query"
  [order-query-am]="2 order-query"
  [order-query-cina]="1 order-query"
  [order-query-apac]="2 order-query"
  [order-query-global-eu]="1 order-query"
  [order-query-global-apac]="1 order-query"
  [order-query-wildcard]="1 order-query"
  [order-saga-eu]="2"
  [order-saga-am]="2"
  [order-saga-apac]="3"
  [order-observer]="1"
)
if [ "$(docker inspect -f '{{.State.Status}}' order-command-eu-b 2>/dev/null || true)" = "running" ]; then
  expected[order-command-eu-b]="0 order-command"
fi

# The ES primary: es-pg-eu unless a scenario promoted another node.
es_primary() {
  local n
  for n in es-pg-eu es-pg-am es-pg-apac; do
    if [ "$(docker exec "$n" psql -U postgres -d evento -tAc 'select pg_is_in_recovery()' 2>/dev/null)" = "f" ]; then
      echo "$n"; return 0
    fi
  done
  return 1
}

sql() { docker exec "$pg" psql -U postgres -d evento -tAc "$1"; }

# Prints "<container>:<what is missing>" for every JVM whose catalog entries are incomplete.
missing_containers() {
  local c spec want bundles rows b h problems
  for c in "${!expected[@]}"; do
    spec="${expected[$c]}"; want="${spec%% *}"; bundles=""
    [ "$spec" != "$want" ] && bundles="${spec#* }"
    problems=""
    rows="$(sql "select count(*) from core__consumer where instance_id = '${c}'")"
    [ "${rows:-0}" -lt "$want" ] && problems+="consumers=${rows:-0}/$want,"
    for b in $bundles; do
      h="$(sql "select count(*) from core__handler h join core__component c
                on $handler_join where c.bundle_id = '$b'")"
      [ "${h:-0}" -eq 0 ] && problems+="handlers[$b]=0,"
    done
    [ -n "$problems" ] && echo "$c:${problems%,}"
  done
  return 0
}

wait_healthy() {
  local c="$1" i
  for i in $(seq 1 120); do
    [ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$c" 2>/dev/null)" = "healthy" ] && return 0
    sleep 2
  done
  log "event=lab_catalog_repair container=$c status=not_healthy"
  return 1
}

repair() {
  local c="$1" helper="$1-helper"
  docker rm -f "$helper" >/dev/null 2>&1 || true
  log "event=lab_catalog_repair container=$c step=helper_start"
  compose run -d --no-deps --name "$helper" -e "EVENTO_INSTANCE_ID=$helper" "$c" >/dev/null
  wait_healthy "$helper" || true
  sleep 3   # discovery runs asynchronously on the server after the handshake
  log "event=lab_catalog_repair container=$c step=restart"
  docker restart -t 10 "$c" >/dev/null
  wait_healthy "$c" || true
  sleep 3
  log "event=lab_catalog_repair container=$c step=helper_remove"
  docker rm -f "$helper" >/dev/null
  sleep 3   # let the helper's NodeLeft run before the next check
}

pg="$(es_primary)" || { echo "no event-store primary found" >&2; exit 2; }
# The catalog shape differs across the N1 fix: after it core__handler points at
# core__component.id ('<bundle>:<name>'), before it at the component name (the old primary key).
if [ "$(sql "select count(*) from information_schema.columns
             where table_name = 'core__handler' and column_name = 'component_id'")" = "1" ]; then
  handler_join="c.id = h.component_id"
else
  handler_join="c.component_name = h.component_component_name"
fi

# A query error must never read as "nothing missing" (the listing runs in a subshell, where
# errexit would silently end it): collect into a file and fail on a non-zero status.
list_missing() {
  local out
  out="$(mktemp)"
  missing_containers > "$out" || { rm -f "$out"; echo "catalog check query failed" >&2; exit 2; }
  mapfile -t missing < <(sort "$out")
  rm -f "$out"
}

# Consumer discovery runs asynchronously on the server after the handshake (seconds to tens of
# seconds after a JVM reports healthy), so a check right after 'up --wait' waits up to
# REPAIR_SETTLE_S (default 90) for the rows before calling anything missing.
settle="${REPAIR_SETTLE_S:-90}"
for i in $(seq 1 "$((settle / 5))"); do
  list_missing
  [ "${#missing[@]}" -eq 0 ] && break
  sleep 5
done

passes="${REPAIR_PASSES:-3}"
for pass in $(seq 1 "$passes"); do
  list_missing
  if [ "${#missing[@]}" -eq 0 ]; then
    log "event=lab_catalog_repair pass=$pass status=complete"
    exit 0
  fi
  log "event=lab_catalog_repair pass=$pass missing=${missing[*]}"
  if $check_only; then exit 1; fi
  # Repairs of different JVMs touch different bundles, so up to REPAIR_PARALLEL (4) run at
  # once; each needs one extra JVM (the helper) for about a minute.
  running=0
  for m in "${missing[@]}"; do
    repair "${m%%:*}" &
    running=$((running + 1))
    if [ "$running" -ge "${REPAIR_PARALLEL:-4}" ]; then wait -n || true; running=$((running - 1)); fi
  done
  wait || true
done
list_missing
if [ "${#missing[@]}" -ne 0 ]; then
  log "event=lab_catalog_repair status=incomplete missing=${missing[*]}"
  exit 1
fi
log "event=lab_catalog_repair status=complete"
