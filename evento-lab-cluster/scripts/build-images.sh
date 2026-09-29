#!/usr/bin/env bash
# Builds the two lab images from freshly built boot jars.
#
#   scripts/build-images.sh [all|server|app]      (default all)
#
# env: LAB_TAG (default dev)            image tag: evento-lab-cluster/{server,app}:$LAB_TAG
#      GRADLE_ARGS (default --offline)  extra gradle flags; set to "" to allow downloads
#      SKIP_GRADLE=true                 reuse the jars already in build/libs
#
# Jars are picked by exact name (L1: never glob build/libs/*.jar, it holds stale -plain jars):
#   evento-server/build/libs/evento-server-<eventoVersion>.jar
#   evento-lab-cluster/evento-lab-cluster-app/build/libs/evento-lab-cluster-app.jar
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

what="${1:-all}"
GRADLE_ARGS="${GRADLE_ARGS---offline}"

version="$(sed -n "s/^[[:space:]]*eventoVersion[[:space:]]*=[[:space:]]*['\"]\([^'\"]*\)['\"].*/\1/p" "$REPO_DIR/build.gradle" | head -1)"
[ -n "$version" ] || { echo "cannot read eventoVersion from build.gradle" >&2; exit 1; }

server_jar="$REPO_DIR/evento-server/build/libs/evento-server-$version.jar"
app_jar="$LAB_DIR/evento-lab-cluster-app/build/libs/evento-lab-cluster-app.jar"

tasks=()
case "$what" in
  all)    tasks=(:evento-server:bootJar :evento-lab-cluster:evento-lab-cluster-app:bootJar) ;;
  server) tasks=(:evento-server:bootJar) ;;
  app)    tasks=(:evento-lab-cluster:evento-lab-cluster-app:bootJar) ;;
  *) echo "usage: $0 [all|server|app]" >&2; exit 2 ;;
esac

if [ "${SKIP_GRADLE:-false}" != "true" ]; then
  ensure_jdk25
  log "gradle ${tasks[*]} (JAVA_HOME=${JAVA_HOME:-unset})"
  # shellcheck disable=SC2086
  gradlew "${tasks[@]}" -x test $GRADLE_ARGS
fi

build_image() {
  local name="$1" jar="$2"
  [ -f "$jar" ] || { echo "missing jar: $jar" >&2; exit 1; }
  local ctx="$DOCKER_DIR/build/context-$name"
  rm -rf "$ctx"
  mkdir -p "$ctx"
  cp "$jar" "$ctx/"
  cp "$DOCKER_DIR/healthcheck.sh" "$ctx/healthcheck.sh"
  # CRLF would break the shebang if the checkout converted line endings.
  sed -i 's/\r$//' "$ctx/healthcheck.sh"
  log "docker build evento-lab-cluster/$name:$LAB_TAG from $(basename "$jar")"
  docker build -f "$DOCKER_DIR/Dockerfile" --build-arg "JAR=$(basename "$jar")" \
    -t "evento-lab-cluster/$name:$LAB_TAG" "$ctx"
}

case "$what" in
  all)    build_image server "$server_jar"; build_image app "$app_jar" ;;
  server) build_image server "$server_jar" ;;
  app)    build_image app "$app_jar" ;;
esac

docker images --filter "reference=evento-lab-cluster/*:$LAB_TAG"
