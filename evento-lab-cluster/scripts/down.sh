#!/usr/bin/env bash
# Stops the lab and removes containers, networks AND volumes (both Postgres clusters start
# empty next time). Covers the optional profiles too.
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

docker compose -p "$COMPOSE_PROJECT" -f "$DOCKER_DIR/compose.yaml" --profile replica --profile regional-observers \
  down -v --remove-orphans --timeout 10 "$@"
