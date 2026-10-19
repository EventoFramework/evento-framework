#!/usr/bin/env bash
# Runs one system on a fresh stack for each scenario and appends the results.
#
#   scripts/run.sh <system> [scenario ...]
#
# system: evento-cpes | evento-apes | axon-jpa | axon-jpa-pool64   (results in results/, publishable)
#         axonserver                              (results in results-private/, never published:
#                                                  Axoniq Terms of Service, see the README)
# scenario: create | spread | hot (default: all three)
# Environment: BENCH_CONCURRENCY, BENCH_WARMUP_S, BENCH_DURATION_S, BENCH_AGGREGATES (compose.yaml).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
system="${1:?system: evento-cpes | evento-apes | axon-jpa | axonserver}"; shift || true
scenarios=("$@"); [ ${#scenarios[@]} -eq 0 ] && scenarios=(create spread hot)

compose() { docker compose -f "$here/docker/compose.yaml" "$@"; }
mkdir -p "$here/results" "$here/results-private"

for scenario in "${scenarios[@]}"; do
  export BENCH_SCENARIO="$scenario" BENCH_RUN="$(date +%s)"
  echo "== $system $scenario"
  compose --profile '*' down -v --remove-orphans >/dev/null 2>&1 || true
  case "$system" in
    evento-cpes|evento-apes)
      export EVENTO_ES_MODE="$( [ "$system" = evento-cpes ] && echo CPES || echo APES )"
      compose --profile evento up -d --wait postgres
      compose --profile evento up -d --wait evento-server evento-handler
      compose --profile evento-load run --rm evento-load
      ;;
    axon-jpa|axon-jpa-pool64)
      if [ "$system" = axon-jpa-pool64 ]; then export DB_POOL=64 AXON_SYSTEM=axon5-jpa-pool64; else export DB_POOL=10 AXON_SYSTEM=axon5-jpa; fi
      compose --profile axon-jpa up -d --wait postgres
      compose --profile axon-jpa-load run --rm axon-jpa
      ;;
    axonserver)
      compose --profile axonserver up -d axonserver
      until docker compose -f "$here/docker/compose.yaml" exec -T axonserver bash -c "echo > /dev/tcp/localhost/8124" 2>/dev/null; do sleep 2; done
      compose --profile axonserver up -d axon-handler
      compose --profile axonserver-load run --rm axon-server-load
      ;;
    *) echo "unknown system $system" >&2; exit 2 ;;
  esac
  compose --profile '*' down -v --remove-orphans >/dev/null 2>&1 || true
done
