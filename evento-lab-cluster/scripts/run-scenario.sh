#!/usr/bin/env bash
# Runs chaos scenarios against the running lab (start it first with scripts/up.sh).
#
#   scripts/run-scenario.sh S00BaselineTest [label]
#   scripts/run-scenario.sh all [label]
#
# Results land in evento-lab-cluster/results/<scenario>/<label>/{summary.json,report.md};
# label defaults to $EVENTO_LAB_LABEL or "run" (use e.g. "before" / "after" around a fix).
# env: GRADLE_ARGS (default --offline)
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

scenario="${1:?usage: $0 <ScenarioClass|all> [label]}"
export EVENTO_LAB_LABEL="${2:-${EVENTO_LAB_LABEL:-run}}"
export EVENTO_LAB_CLUSTER=true
GRADLE_ARGS="${GRADLE_ARGS---offline}"

filter=()
if [ "$scenario" != "all" ]; then
  case "$scenario" in
    *.*) filter=(--tests "$scenario") ;;      # fully qualified name or pattern
    *)   filter=(--tests "*.$scenario") ;;
  esac
fi

ensure_jdk25
log "scenario=$scenario label=$EVENTO_LAB_LABEL"
# --rerun: the env vars above are not task inputs, so Gradle would otherwise report the
# test task UP-TO-DATE and skip it.
# shellcheck disable=SC2086
gradlew :evento-lab-cluster:evento-lab-cluster-chaos:test ${filter[@]+"${filter[@]}"} --rerun $GRADLE_ARGS
