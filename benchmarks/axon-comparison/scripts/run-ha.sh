#!/usr/bin/env bash
# B2: one system on the HA, multi-AZ stack (docker/compose.ha.yaml), on a fresh stack for each
# scenario, with HA_RTT_MS of round trip between the AZs. Appends to results/results-ha.jsonl.
#
#   scripts/run-ha.sh <system> <rtt-ms> [scenario ...]
#
# system: evento-cpes | evento-apes | axon-jpa      rtt-ms: 0 or an even number (half each way)
# Environment: BENCH_CONCURRENCY (per load, two loads), BENCH_WARMUP_S, BENCH_DURATION_S.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
system="${1:?system: evento-cpes | evento-apes | axon-jpa}"
export HA_RTT_MS="${2:?round trip between the AZs, in ms}"
shift 2
scenarios=("$@"); [ ${#scenarios[@]} -eq 0 ] && scenarios=(create spread hot)

dc() { docker compose -f "$here/docker/compose.ha.yaml" "$@"; }
healthy() {
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "bench-ha-$1-1" 2>/dev/null)" = healthy ]; do sleep 2; done
}
mkdir -p "$here/results"

for scenario in "${scenarios[@]}"; do
  export BENCH_SCENARIO="$scenario" BENCH_RUN="$(date +%s)"
  echo "== $system rtt=${HA_RTT_MS}ms $scenario"
  dc --profile '*' down -v --remove-orphans >/dev/null 2>&1 || true
  case "$system" in
    evento-cpes|evento-apes)
      export EVENTO_ES_MODE="$( [ "$system" = evento-cpes ] && echo CPES || echo APES )"
      dc --profile evento up -d >/dev/null 2>&1
      healthy evento-a; healthy evento-b
      dc --profile evento-load up --no-deps --no-log-prefix load-a load-b 2>&1 | grep --line-buffered -E "cmd/s|^first|Exception in"
      ;;
    axon-jpa)
      dc --profile axon up -d >/dev/null 2>&1
      healthy pg-a; healthy pg-b
      # Hibernate creates Axon's tables: once, before two instances race to do it.
      dc --profile axon-load run --rm --no-deps -e BENCH_SCENARIO=create -e BENCH_CONCURRENCY=1 -e BENCH_WARMUP_S=0 \
        -e BENCH_DURATION_S=1 -e BENCH_LOADERS=1 -e BENCH_OUT=/tmp/schema.jsonl axon-a >/dev/null 2>&1
      dc --profile axon-load up --no-deps --no-log-prefix axon-a axon-b 2>&1 | grep --line-buffered -E "cmd/s|^first|APPLICATION FAILED"
      ;;
    *) echo "unknown system $system" >&2; exit 2 ;;
  esac
  dc --profile '*' down -v --remove-orphans >/dev/null 2>&1 || true
done
