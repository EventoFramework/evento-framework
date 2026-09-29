#!/usr/bin/env bash
# Baseline WAN latency on the toxiproxy links, from the host.
#
#   scripts/wan.sh [apply|off|show]      (default apply)
#
# env: TOXIPROXY_URL (default http://localhost:8474), WAN_LATENCY_SCALE (percent, default 100),
#      WAN_JITTER_MS (default 5)
set -euo pipefail
. "$(dirname "$0")/_lib.sh"
exec sh "$DOCKER_DIR/toxiproxy/apply-wan.sh" "${1:-apply}"
