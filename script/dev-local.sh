#!/usr/bin/env bash
# Builds a local Docker image for evento-server and publishes all client
# libraries to Maven Local so they can be consumed from another project.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="$ROOT/gradlew"

export JAVA_HOME
JAVA_HOME="$(/usr/libexec/java_home -v 25)"

VERSION="$(grep '^version ' "$ROOT/build.gradle" | awk -F"'" '{print $2}')"
IMAGE="eventoframework/evento-server:${VERSION}-local"

echo "==> [1/3] Building evento-server bootJar (version ${VERSION})..."
"$GRADLEW" -p "$ROOT" :evento-server:bootJar -x test

echo ""
echo "==> [2/3] Building Docker image ${IMAGE}..."
docker build \
  -t "$IMAGE" \
  -f "$ROOT/docker/images/evento-server/Dockerfile.local" \
  "$ROOT"

echo ""
echo "==> [3/3] Publishing libraries to Maven Local..."
"$GRADLEW" -p "$ROOT" \
  :evento-common:publishToMavenLocal \
  :evento-transport-api:publishToMavenLocal \
  :evento-transport-netty:publishToMavenLocal \
  :evento-bundle:publishToMavenLocal \
  ':evento-consumer-state-store:evento-consumer-state-store-jdbc-v2:publishToMavenLocal' \
  -x test

echo ""
echo "========================================"
echo "Done."
echo "  Docker image : ${IMAGE}"
echo "  Maven Local  : ~/.m2/repository/com/eventoframework/*/${VERSION}/"
echo "========================================"
