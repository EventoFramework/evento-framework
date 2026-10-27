#!/usr/bin/env bash
# The full matrix: every system, every scenario, on a fresh stack each time. Records the machine,
# the date and the commit in results/ENV.txt (and results-private/ENV.txt).
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"

env_file() {
  {
    echo "date:     $(date -u +%Y-%m-%dT%H:%MZ)"
    echo "commit:   $(git -C "$here" rev-parse --short HEAD)$(git -C "$here" diff --quiet || echo ' (+ local changes)')"
    echo "cpu:      $(docker run --rm alpine:3.20 sh -c "grep -m1 'model name' /proc/cpuinfo | cut -d: -f2" | sed 's/^ *//') ($(docker info --format '{{.NCPU}}') logical CPUs)"
    echo "memory:   $(docker info --format '{{.MemTotal}}' | awk '{printf "%.1f GiB", $1/1073741824}') for Docker"
    echo "docker:   $(docker info --format '{{.ServerVersion}} on {{.OperatingSystem}}, kernel {{.KernelVersion}}')"
    echo "pinning:  infrastructure (PostgreSQL 16, evento-server, Axon Server) on CPUs 0-3, applications on CPUs 4-7"
    echo "per level: ${BENCH_WARMUP_S:-20} s warm-up, ${BENCH_DURATION_S:-60} s measured"
  } > "$1"
}
mkdir -p "$here/results" "$here/results-private"
env_file "$here/results/ENV.txt"
cp "$here/results/ENV.txt" "$here/results-private/ENV.txt"

for system in ${SYSTEMS:-evento-cpes evento-apes axon-jpa axon-jpa-pool64 axonserver}; do
  "$here/scripts/run.sh" "$system" ${SCENARIOS:-create spread hot}
done
