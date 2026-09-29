#!/usr/bin/env bash
# Builds the lab images from freshly built boot jars: the evento-server and one image per bundle.
#
#   scripts/build-images.sh [all|server|bundles|<bundle>...]      (default all)
#       bundles: order-command order-query order-saga order-observer order-gateway
#
# env: LAB_TAG (default dev)            image tag: evento-lab-cluster/<name>:$LAB_TAG
#      FRAMEWORK_REF (default: unset)   build against the framework of another git ref (e.g.
#                                       e7b823fe, the framework before the cluster fixes): a
#                                       temporary git worktree is created at that ref, the
#                                       CURRENT evento-lab-cluster folder and its settings.gradle
#                                       include lines are copied into it, and every jar is built
#                                       there. The lab source is the same either way; framework
#                                       features it cannot find are reported at run time
#                                       (FrameworkFeatures, /whoami).
#      KEEP_FRAMEWORK_WORKTREE=true     keep that worktree afterwards (for inspection)
#      GRADLE_ARGS (default --offline)  extra gradle flags; set to "" to allow downloads
#      SKIP_GRADLE=true                 reuse the jars already built
#
#   FRAMEWORK_REF=e7b823fe LAB_TAG=before scripts/build-images.sh     # 'before' images
#   LAB_TAG=after scripts/build-images.sh                             # working tree = 'after'
#
# Jars are picked by exact name (L1: never glob build/libs/*.jar, it holds stale -plain jars):
#   evento-server/build/libs/evento-server-<eventoVersion>.jar
#   evento-lab-cluster/<bundle>/build/libs/<bundle>.jar
set -euo pipefail
. "$(dirname "$0")/_lib.sh"

BUNDLES=(order-command order-query order-saga order-observer order-gateway)
GRADLE_ARGS="${GRADLE_ARGS---offline}"

want_server=false
want=()
if [ $# -eq 0 ]; then set -- all; fi
for a in "$@"; do
  case "$a" in
    all)     want_server=true; want+=("${BUNDLES[@]}") ;;
    server)  want_server=true ;;
    bundles) want+=("${BUNDLES[@]}") ;;
    order-command|order-query|order-saga|order-observer|order-gateway) want+=("$a") ;;
    *) echo "usage: $0 [all|server|bundles|<bundle>...]" >&2; exit 2 ;;
  esac
done

# Where to build: the working tree, or a temporary worktree at FRAMEWORK_REF.
BUILD_DIR="$REPO_DIR"
if [ -n "${FRAMEWORK_REF:-}" ]; then
  BUILD_DIR="$(cd "$REPO_DIR/.." && { pwd -W 2>/dev/null || pwd; })/.evento-lab-fw-$(echo "$FRAMEWORK_REF" | tr -c 'A-Za-z0-9._-' '_')"
  if [ -d "$BUILD_DIR" ]; then
    git -C "$REPO_DIR" worktree remove --force "$BUILD_DIR" >/dev/null 2>&1 || rm -rf "$BUILD_DIR"
  fi
  log "framework at $FRAMEWORK_REF: temporary worktree $BUILD_DIR"
  git -C "$REPO_DIR" worktree add --detach "$BUILD_DIR" "$FRAMEWORK_REF" >/dev/null
  if [ "${KEEP_FRAMEWORK_WORKTREE:-false}" != "true" ]; then
    trap 'git -C "$REPO_DIR" worktree remove --force "$BUILD_DIR" >/dev/null 2>&1 || true' EXIT
  fi
  # the lab as it is now, without build outputs or results
  rm -rf "$BUILD_DIR/evento-lab-cluster"
  mkdir -p "$BUILD_DIR/evento-lab-cluster"
  (cd "$LAB_DIR" && tar cf - --exclude='./*/build' --exclude='./docker/build' --exclude='./results' --exclude='./.gradle' .) \
    | (cd "$BUILD_DIR/evento-lab-cluster" && tar xf -)
  # the ref's settings.gradle, with its evento-lab-cluster includes replaced by the current ones
  grep -v "^include 'evento-lab-cluster:" "$BUILD_DIR/settings.gradle" > "$BUILD_DIR/settings.gradle.tmp"
  grep "^include 'evento-lab-cluster:" "$REPO_DIR/settings.gradle" >> "$BUILD_DIR/settings.gradle.tmp"
  mv "$BUILD_DIR/settings.gradle.tmp" "$BUILD_DIR/settings.gradle"
  log "framework $(git -C "$BUILD_DIR" log -1 --format='%h %s') + current evento-lab-cluster"
fi

version="$(sed -n "s/^[[:space:]]*eventoVersion[[:space:]]*=[[:space:]]*['\"]\([^'\"]*\)['\"].*/\1/p" "$BUILD_DIR/build.gradle" | head -1)"
[ -n "$version" ] || { echo "cannot read eventoVersion from $BUILD_DIR/build.gradle" >&2; exit 1; }
server_jar="$BUILD_DIR/evento-server/build/libs/evento-server-$version.jar"

tasks=()
$want_server && tasks+=(:evento-server:bootJar)
for b in ${want[@]+"${want[@]}"}; do tasks+=(":evento-lab-cluster:$b:bootJar"); done

if [ "${SKIP_GRADLE:-false}" != "true" ]; then
  ensure_jdk25
  log "gradle ${tasks[*]} in $BUILD_DIR (JAVA_HOME=${JAVA_HOME:-unset})"
  # shellcheck disable=SC2086
  (cd "$BUILD_DIR" && ./gradlew "${tasks[@]}" -x test $GRADLE_ARGS)
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
    --label "evento.lab.framework-ref=${FRAMEWORK_REF:-working-tree}" \
    -t "evento-lab-cluster/$name:$LAB_TAG" "$ctx"
}

$want_server && build_image server "$server_jar"
for b in ${want[@]+"${want[@]}"}; do
  build_image "$b" "$BUILD_DIR/evento-lab-cluster/$b/build/libs/$b.jar"
done

docker images --filter "reference=evento-lab-cluster/*:$LAB_TAG"
